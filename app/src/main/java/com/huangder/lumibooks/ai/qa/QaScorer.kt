package com.huangder.lumibooks.ai.qa

import kotlin.math.ln

/**
 * BM25-lite scoring over the retrieval pool. Pool sizes are small (tens to a
 * few hundred), so df/avgLen are recomputed per question — that keeps the
 * scorer a pure function and makes it trivially testable.
 */
object QaScorer {
    private const val K1 = 1.2f
    private const val B = 0.75f
    private const val IDF_FLOOR = 0.05f
    private const val TITLE_BOOST = 1.15f
    private const val ENTRY_BOOK_BOOST = 1.2f

    /** df is per query term: how many candidate documents contain it. */
    fun corpus(candidates: List<SourceCandidate>, query: RetrievalQuery): CorpusStats {
        if (candidates.isEmpty() || query.isEmpty) {
            return CorpusStats(candidates.size, 1.0, emptyMap())
        }
        val df = HashMap<String, Int>()
        var totalLen = 0.0
        candidates.forEach { candidate ->
            totalLen += candidate.text.length
            val lowered = candidate.text.lowercase()
            query.terms.forEach { term ->
                if (lowered.contains(term.text.lowercase())) df.merge(term.text, 1, Int::plus)
            }
        }
        return CorpusStats(
            docCount = candidates.size,
            avgLen = (totalLen / candidates.size).coerceAtLeast(1.0),
            documentFrequency = df
        )
    }

    fun score(
        candidate: SourceCandidate,
        query: RetrievalQuery,
        stats: CorpusStats,
        weight: Float,
        entryBookId: String?
    ): Float {
        if (query.isEmpty || stats.docCount == 0) return 0f
        val len = candidate.text.length.coerceAtLeast(1)
        val lowered = candidate.text.lowercase()
        var score = 0f
        query.terms.forEach { term ->
            val tf = QaTokenizer.countOccurrences(candidate.text, term.text).toFloat()
            if (tf == 0f) return@forEach
            val df = stats.documentFrequency[term.text.lowercase()] ?: 0
            val idf = ln(1.0 + (stats.docCount - df + 0.5) / (df + 0.5)).toFloat().coerceAtLeast(IDF_FLOOR)
            val normalization = 1f - B + B * (len / stats.avgLen).toFloat()
            score += term.weight * idf * (tf * (K1 + 1f)) / (tf + K1 * normalization)
        }
        if (score == 0f) return 0f
        // Title hits indicate a topical match the body may only imply.
        val titleHint = listOfNotNull(candidate.titleHint, candidate.chapterTitle)
        if (titleHint.any { hint -> query.terms.any { lowered(hint).contains(it.text.lowercase()) } }) {
            score *= TITLE_BOOST
        }
        if (entryBookId != null && candidate.bookId == entryBookId) {
            score *= ENTRY_BOOK_BOOST
        }
        return score * weight
    }

    /** Same-chapter dampening: k already-selected items from one chapter shrink later ones. */
    fun diversityPenalty(sameChapterAlreadyPicked: Int): Float =
        1f / (1f + 0.5f * sameChapterAlreadyPicked)

    private fun lowered(text: String?): String = text?.lowercase().orEmpty()
}
