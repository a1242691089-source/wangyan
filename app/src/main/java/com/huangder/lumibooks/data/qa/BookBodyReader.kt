package com.huangder.lumibooks.data.qa

import android.content.Context
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ai.qa.QaTerm
import com.huangder.lumibooks.ai.qa.SourceCandidate
import com.huangder.lumibooks.domain.model.QaSourceType
import com.huangder.lumibooks.util.parser.BookParser
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.huangder.lumibooks.util.parser.TxtEncoding
import com.huangder.lumibooks.util.parser.TxtParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Scans book body text for keyword windows (the QA retrieval layer's tier D).
 * Parsers are expensive to build and parse (a cold EPUB can take seconds), so
 * a tiny LRU keeps the last two books warm; each book's open/parse is
 * serialized behind its own mutex. Raster page formats (PDF/CBZ) have no
 * reflowable text and are skipped — retrieval for those books still has the
 * evidence/note tiers to fall back on.
 */
@Singleton
class BookBodyReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager
) {
    data class BodyWindow(
        val candidate: SourceCandidate
    )

    private class ParserEntry(
        val parser: BookParser,
        val chapterTitles: Map<Int, String>,
        val chapterCount: Int
    )

    private val cache = object : LinkedHashMap<String, ParserEntry>(2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ParserEntry>): Boolean {
            if (size <= CACHE_CAPACITY) return false
            runCatching { eldest.value.parser.close() }
            return true
        }
    }
    private val cacheMutex = Mutex()
    private val perBookMutexes = HashMap<String, Mutex>()

    suspend fun windows(book: Book, terms: List<QaTerm>, maxWindows: Int): List<SourceCandidate> {
        if (terms.isEmpty() || book.isCloudOnly || book.isMissing ||
            book.format.isRasterPageFormat || book.filePath.isBlank()
        ) return emptyList()
        return withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { scan(book, terms, maxWindows) }
        } ?: emptyList()
    }

    private suspend fun scan(book: Book, terms: List<QaTerm>, maxWindows: Int): List<SourceCandidate> {
        val entry = entryFor(book) ?: return emptyList()
        val loweredTerms = terms.map { it.text.lowercase() }.distinct()
        // Chapters are scanned in book order; hit density is handled by the
        // per-book/per-chapter caps in the budget packer.
        val scanChapters = (0 until minOf(entry.chapterCount, MAX_CHAPTERS)).toList()

        val windows = ArrayList<SourceCandidate>()
        for (chapterIndex in scanChapters) {
            if (windows.size >= maxWindows) break
            val text = runCatching { entry.parser.getChapterContent(chapterIndex).toString() }
                .getOrNull() ?: continue
            if (text.length < MIN_CHAPTER_LENGTH) continue
            val chapterTitle = entry.chapterTitles[chapterIndex]
            for (term in loweredTerms) {
                var from = 0
                while (windows.size < maxWindows) {
                    val index = text.indexOf(term, from, ignoreCase = true)
                    if (index < 0) break
                    val start = (index - BEFORE_CHARS).coerceAtLeast(0)
                    val end = (index + term.length + AFTER_CHARS).coerceAtMost(text.length)
                    if (!overlapsExisting(windows, chapterIndex, start, end)) {
                        windows.add(
                            SourceCandidate(
                                type = QaSourceType.BOOK_BODY,
                                text = text.substring(start, end),
                                bookId = book.id,
                                bookTitle = book.title,
                                chapterIndex = chapterIndex,
                                chapterTitle = chapterTitle,
                                startPosition = start,
                                endPosition = end,
                                createdAt = book.lastReadTime
                            )
                        )
                    }
                    from = index + term.length
                }
                if (windows.size >= maxWindows) break
            }
        }
        return windows
    }

    private fun overlapsExisting(
        windows: List<SourceCandidate>,
        chapterIndex: Int,
        start: Int,
        end: Int
    ): Boolean = windows.any { existing ->
        existing.chapterIndex == chapterIndex && run {
            val overlapStart = maxOf(existing.startPosition, start)
            val overlapEnd = minOf(existing.endPosition, end)
            val overlap = (overlapEnd - overlapStart).coerceAtLeast(0)
            val smaller = minOf(existing.endPosition - existing.startPosition, end - start).coerceAtLeast(1)
            overlap * 100 / smaller > OVERLAP_PERCENT
        }
    }

    private suspend fun entryFor(book: Book): ParserEntry? {
        perBookMutex(book.id).withLock {
            cacheMutex.withLock { cache[book.id] }?.let { return it }
            val parser = BookParserFactory.createParser(book.format, context).also { created ->
                if (created is TxtParser) {
                    created.selectedEncoding = TxtEncoding.fromStorage(
                        dataStoreManager.txtEncoding(book.id).first()
                    )
                    created.selectedTocRule = dataStoreManager.resolveTxtTocRule(book.id)
                }
            }
            // A failed parse must not leak the parser; retrieval degrades to
            // the evidence/note tiers instead of failing the question.
            val content = runCatching { parser.parse(book.filePath) }
                .onFailure { runCatching { parser.close() } }
                .getOrNull() ?: return null
            val entry = ParserEntry(
                parser = parser,
                chapterTitles = content.chapters.associate { it.index to it.title },
                chapterCount = parser.getChapterCount()
            )
            cacheMutex.withLock { cache[book.id] = entry }
            return entry
        }
    }

    @Synchronized
    private fun perBookMutex(bookId: String): Mutex =
        perBookMutexes.getOrPut(bookId) { Mutex() }

    companion object {
        private const val CACHE_CAPACITY = 2
        private const val SCAN_TIMEOUT_MS = 6_000L
        private const val MAX_CHAPTERS = 60
        private const val BEFORE_CHARS = 250
        private const val AFTER_CHARS = 350
        private const val OVERLAP_PERCENT = 60
        private const val MIN_CHAPTER_LENGTH = 40
    }
}
