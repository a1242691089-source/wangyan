package com.huangder.lumibooks.ai.qa

import com.huangder.lumibooks.domain.model.QaSourceType

/** One retrieval term: an ASCII word or a Chinese bigram. */
data class QaTerm(val text: String, val weight: Float)

/** Tokenized user question. */
data class RetrievalQuery(
    val raw: String,
    val terms: List<QaTerm>
) {
    val isEmpty: Boolean get() = terms.isEmpty()
}

/** A candidate source before ranking; positions follow the note conventions (-1 = unanchored). */
data class SourceCandidate(
    val type: QaSourceType,
    val text: String,
    val bookId: String? = null,
    val bookTitle: String? = null,
    val chapterIndex: Int = -1,
    val chapterTitle: String? = null,
    val startPosition: Int = -1,
    val endPosition: Int = -1,
    /** Knowledge-node title (or note heading) that boosts title matches. */
    val titleHint: String? = null,
    /** Knowledge-node id when the source is a node thought. */
    val nodeId: Long? = null,
    val createdAt: Long = 0
)

/** Corpus statistics for BM25-lite scoring, computed over the candidate pool. */
data class CorpusStats(
    val docCount: Int,
    val avgLen: Double,
    val documentFrequency: Map<String, Int>
)

data class ScoredSource(
    val candidate: SourceCandidate,
    val score: Float
)

/** A context snippet handed to the model; [index] is its 1-based citation number. */
data class SourceSnippet(
    val index: Int,
    val candidate: SourceCandidate
)

/** One past question/answer pair replayed into the prompt. */
data class QaTurn(
    val question: String,
    val answer: String
)

data class QaRequest(
    val sessionId: Long,
    val question: String,
    val entryBookId: String?,
    /** Completed turns to replay, oldest first. */
    val history: List<QaTurn>
)

/** The engine's result for one turn: parsed answer plus what retrieval found. */
data class QaOutcome(
    val answer: String,
    val segments: List<CitationParser.AnswerSegment>,
    val citations: List<com.huangder.lumibooks.domain.model.Citation>,
    val sourceCount: Int
)
