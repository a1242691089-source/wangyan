package com.huangder.lumibooks.data.qa

import com.huangder.lumibooks.ai.qa.QaBudget
import com.huangder.lumibooks.ai.qa.QaScorer
import com.huangder.lumibooks.ai.qa.QaSnippetSource
import com.huangder.lumibooks.ai.qa.QaTokenizer
import com.huangder.lumibooks.ai.qa.RetrievalQuery
import com.huangder.lumibooks.ai.qa.ScoredSource
import com.huangder.lumibooks.ai.qa.SourceCandidate
import com.huangder.lumibooks.ai.qa.SourceSnippet
import com.huangder.lumibooks.data.local.dao.KnowledgeDao
import com.huangder.lumibooks.data.local.dao.NoteDao
import com.huangder.lumibooks.domain.model.QaSourceType
import com.huangder.lumibooks.domain.repository.BookRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Assembles the QA retrieval pool: knowledge evidence, user notes, node
 * thoughts (tiers A-C, all locatable user data) plus keyword windows from
 * book bodies (tier D, entry book first, then at most two more books that
 * scored on the user-data side). Every step degrades independently — a
 * failure in one tier must never fail the whole question.
 */
@Singleton
class QaSourceLoader @Inject constructor(
    private val knowledgeDao: KnowledgeDao,
    private val noteDao: NoteDao,
    private val bookRepository: BookRepository,
    private val bookBodyReader: BookBodyReader
) : QaSnippetSource {
    override suspend fun load(entryBookId: String?, query: RetrievalQuery): List<SourceSnippet> {
        if (query.isEmpty) return emptyList()
        val books = runCatching { bookRepository.getAllBooks().first() }.getOrDefault(emptyList())
        val titles = books.associate { it.id to it.title }
        val readableIds = books
            .filter { !it.isCloudOnly && !it.isMissing && !it.format.isRasterPageFormat }
            .map { it.id }
            .toSet()

        val candidates = ArrayList<SourceCandidate>()
        runCatching {
            knowledgeDao.evidenceSnapshotWithNode(SNAPSHOT_LIMIT).forEach { row ->
                val evidence = row.evidence
                candidates.add(
                    SourceCandidate(
                        type = QaSourceType.KNOWLEDGE,
                        text = evidence.selectedText,
                        bookId = evidence.bookId,
                        bookTitle = titles[evidence.bookId],
                        chapterIndex = evidence.chapterIndex,
                        chapterTitle = evidence.chapterTitle,
                        startPosition = evidence.startPosition,
                        endPosition = evidence.endPosition,
                        titleHint = row.nodeTitle,
                        nodeId = evidence.nodeId,
                        createdAt = evidence.createdAt
                    )
                )
                // Node thought text supplements the excerpt; only when it says
                // something beyond the title and is anchored via the node detail.
                val thought = row.nodeThought.trim()
                if (thought.length >= MIN_THOUGHT_LENGTH && thought != row.nodeTitle) {
                    candidates.add(
                        SourceCandidate(
                            type = QaSourceType.NODE_THOUGHT,
                            text = thought,
                            bookId = null,
                            chapterIndex = -1,
                            startPosition = -1,
                            endPosition = -1,
                            titleHint = row.nodeTitle,
                            nodeId = evidence.nodeId,
                            createdAt = evidence.createdAt
                        )
                    )
                }
            }
        }
        runCatching {
            noteDao.textNotesSnapshot(SNAPSHOT_LIMIT).forEach { note ->
                val parts = buildList {
                    if (note.selectedText.isNotBlank()) add(note.selectedText)
                    if (note.note.isNotBlank()) add("想法：${note.note}")
                }
                if (parts.isEmpty()) return@forEach
                candidates.add(
                    SourceCandidate(
                        type = QaSourceType.NOTE,
                        text = parts.joinToString("\n"),
                        bookId = note.bookId,
                        bookTitle = titles[note.bookId],
                        chapterIndex = note.chapterIndex,
                        startPosition = note.startPosition,
                        endPosition = note.endPosition,
                        createdAt = note.createdAt
                    )
                )
            }
        }

        val stats = QaScorer.corpus(candidates, query)
        val weights = mapOf(
            QaSourceType.KNOWLEDGE to 1.35f,
            QaSourceType.NOTE to 1.25f,
            QaSourceType.NODE_THOUGHT to 1.05f,
            QaSourceType.BOOK_BODY to 1.0f
        )
        val ranked = candidates
            .map { candidate ->
                ScoredSource(
                    candidate = candidate,
                    score = QaScorer.score(
                        candidate, query, stats,
                        weight = weights.getValue(candidate.type),
                        entryBookId = entryBookId
                    )
                )
            }
            .filter { it.score > 0f }
            .sortedWith(compareByDescending<ScoredSource> { it.score }.thenByDescending { it.candidate.createdAt })

        // Tier D: the entry book always scans (when readable), plus up to two
        // books the user-data side already pointed at.
        val bodyBookIds = LinkedHashSet<String>()
        entryBookId?.takeIf { it in readableIds }?.let { bodyBookIds.add(it) }
        ranked.take(8).forEach { scored ->
            val id = scored.candidate.bookId
            if (bodyBookIds.size >= 1 + MAX_EXTRA_BODY_BOOKS) return@forEach
            if (id != null && id in readableIds) bodyBookIds.add(id)
        }
        val perBookWindows = (MAX_BODY_WINDOWS / bodyBookIds.size.coerceAtLeast(1)).coerceAtLeast(2)
        val bodyCandidates = bodyBookIds.flatMap { bookId ->
            books.firstOrNull { it.id == bookId }?.let { book ->
                runCatching {
                    bookBodyReader.windows(book, query.terms, perBookWindows)
                }.getOrDefault(emptyList())
            }.orEmpty()
        }
        val reranked = (ranked + bodyCandidates.map { window ->
            ScoredSource(window, QaScorer.score(window, query, stats, 1f, entryBookId))
        })
            .filter { it.score > 0f }
            .sortedWith(compareByDescending<ScoredSource> { it.score }.thenByDescending { it.candidate.createdAt })
        return QaBudget.pack(reranked)
    }

    companion object {
        private const val SNAPSHOT_LIMIT = 2000
        private const val MIN_THOUGHT_LENGTH = 12
        private const val MAX_EXTRA_BODY_BOOKS = 2
        private const val MAX_BODY_WINDOWS = 12
    }
}
