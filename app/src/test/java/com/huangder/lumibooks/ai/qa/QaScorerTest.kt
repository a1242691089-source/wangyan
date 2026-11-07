package com.huangder.lumibooks.ai.qa

import com.huangder.lumibooks.domain.model.QaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QaScorerTest {
    private fun candidate(
        text: String,
        bookId: String? = "book-1",
        chapterIndex: Int = 0,
        titleHint: String? = null,
        type: QaSourceType = QaSourceType.BOOK_BODY
    ) = SourceCandidate(
        type = type, text = text, bookId = bookId, chapterIndex = chapterIndex,
        titleHint = titleHint, createdAt = 100
    )

    @Test
    fun shortDocumentWithSameHitsOutranksLongOne() {
        val query = QaTokenizer.query("忘言")
        val candidates = listOf(
            candidate("得意而忘言"),
            candidate("得鱼忘筌得意而忘言，" + "不相干的内容".repeat(80))
        )
        val stats = QaScorer.corpus(candidates, query)
        val scores = candidates.map { QaScorer.score(it, query, stats, weight = 1f, entryBookId = null) }
        assertTrue(scores[0] > scores[1])
    }

    @Test
    fun titleHitBoostsScore() {
        val query = QaTokenizer.query("忘言")
        val withTitle = candidate("这里也提到这个概念", titleHint = "忘言")
        val withoutTitle = candidate("这里也提到这个概念")
        val stats = QaScorer.corpus(listOf(withTitle, withoutTitle), query)
        val boosted = QaScorer.score(withTitle, query, stats, 1f, null)
        val plain = QaScorer.score(withoutTitle, query, stats, 1f, null)
        assertEquals(plain * 1.15f, boosted, 0.001f)
    }

    @Test
    fun entryBookGetsExtraWeight() {
        val query = QaTokenizer.query("忘言")
        val fromEntry = candidate("得意而忘言", bookId = "entry")
        val fromOther = candidate("得意而忘言", bookId = "other")
        val stats = QaScorer.corpus(listOf(fromEntry, fromOther), query)
        val entry = QaScorer.score(fromEntry, query, stats, 1f, "entry")
        val other = QaScorer.score(fromOther, query, stats, 1f, "entry")
        assertEquals(other * 1.2f, entry, 0.001f)
    }

    @Test
    fun typeWeightMultipliesBaseScore() {
        val query = QaTokenizer.query("忘言")
        val note = candidate("得意而忘言", type = QaSourceType.NOTE)
        val body = candidate("得意而忘言", type = QaSourceType.BOOK_BODY)
        val stats = QaScorer.corpus(listOf(note, body), query)
        val noteScore = QaScorer.score(note, query, stats, weight = 1.25f, entryBookId = null)
        val bodyScore = QaScorer.score(body, query, stats, weight = 1f, entryBookId = null)
        assertEquals(bodyScore * 1.25f, noteScore, 0.001f)
    }

    @Test
    fun noMatchScoresZeroAndEmptyQueryNeverNaN() {
        val query = QaTokenizer.query("量子力学")
        val stats = QaScorer.corpus(listOf(candidate("得意而忘言")), query)
        assertEquals(0f, QaScorer.score(candidate("得意而忘言"), query, stats, 1f, null), 0.0001f)
        assertTrue(QaScorer.corpus(emptyList(), query).docCount == 0)
        assertEquals(0f, QaScorer.score(candidate("任何"), QaTokenizer.query(""), stats, 1f, null), 0.0001f)
    }

    @Test
    fun diversityPenaltyShrinksWithPicks() {
        assertEquals(1f, QaScorer.diversityPenalty(0), 0.0001f)
        assertEquals(0.5f, QaScorer.diversityPenalty(2), 0.0001f)
        assertTrue(QaScorer.diversityPenalty(5) < QaScorer.diversityPenalty(3))
    }
}
