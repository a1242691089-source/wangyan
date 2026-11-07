package com.huangder.lumibooks.ai.qa

import com.huangder.lumibooks.ai.AiRepository
import com.huangder.lumibooks.domain.model.Citation
import com.huangder.lumibooks.translation.TranslationError
import com.huangder.lumibooks.translation.TranslationException
import javax.inject.Inject
import javax.inject.Singleton

/** Abstraction over the QA pipeline so UI tests can fake it. */
interface QaAnswerer {
    suspend fun answer(request: QaRequest): QaOutcome
}

private const val QUOTE_MAX_CHARS = 240

/** Maps cited snippet numbers back to persisted citations; unknown numbers are dropped. */
internal fun citationsFor(
    parsed: CitationParser.ParsedAnswer,
    snippets: List<SourceSnippet>
): List<Citation> {
    val byIndex = snippets.associateBy { it.index }
    return parsed.citedIndices.mapNotNull { n ->
        val snippet = byIndex[n] ?: return@mapNotNull null
        val candidate = snippet.candidate
        Citation(
            n = n,
            type = candidate.type,
            bookId = candidate.bookId,
            bookTitle = candidate.bookTitle,
            chapterIndex = candidate.chapterIndex,
            chapterTitle = candidate.chapterTitle,
            startPosition = candidate.startPosition,
            endPosition = candidate.endPosition,
            quote = candidate.text.take(QUOTE_MAX_CHARS),
            nodeId = candidate.nodeId
        )
    }
}

/**
 * One QA turn: retrieve, build the constrained prompt, call the model, parse
 * citations. Throws [TranslationException] with the shared error taxonomy —
 * the caller maps codes to localized strings.
 */
@Singleton
class QaEngine @Inject constructor(
    private val snippetSource: QaSnippetSource,
    private val aiRepository: AiRepository
) : QaAnswerer {

    override suspend fun answer(request: QaRequest): QaOutcome {
        val configuration = aiRepository.currentConfiguration()
        if (!configuration.enabled) {
            throw TranslationException(TranslationError.MISSING_KEY)
        }
        val query = QaTokenizer.query(request.question)
        val snippets = snippetSource.load(request.entryBookId, query)
        val history = QaBudget.historyLimit(request.history)
        val messages = QaPromptBuilder.build(request.question, snippets, history)
        val answer = aiRepository.chat(messages, configuration)
        val parsed = CitationParser.parse(answer, snippets.size)
        return QaOutcome(
            answer = answer,
            segments = parsed.segments,
            citations = citationsFor(parsed, snippets),
            sourceCount = snippets.size
        )
    }
}
