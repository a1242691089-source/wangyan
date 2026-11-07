package com.huangder.lumibooks.ai.qa

import com.huangder.lumibooks.domain.model.QaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QaBudgetTest {
    private fun scored(
        text: String,
        score: Float,
        bookId: String? = "book-1",
        chapterIndex: Int = 0
    ) = ScoredSource(
        candidate = SourceCandidate(
            type = QaSourceType.BOOK_BODY, text = text,
            bookId = bookId, chapterIndex = chapterIndex, createdAt = 100
        ),
        score = score
    )

    @Test
    fun packNumbersSequentiallyAndRespectsSnippetCap() {
        val ranked = (1..20).map { scored("片段$it", score = 100f - it) }
        val packed = QaBudget.pack(
            ranked,
            QaBudget.BudgetLimits(
                maxSnippets = 5, maxPerBook = 20, maxPerChapter = 20, snippetTokens = 100_000
            )
        )
        assertEquals(5, packed.size)
        assertEquals((1..5).toList(), packed.map { it.index })
        assertEquals("片段1", packed.first().candidate.text)
    }

    @Test
    fun oversizedSnippetIsSkippedNotTruncatedButLaterShortOnesFillIn() {
        val big = "长".repeat(600)
        val packed = QaBudget.pack(
            listOf(scored(big, 10f), scored("短片段", 5f)),
            QaBudget.BudgetLimits(maxSnippets = 5, snippetTokens = 100)
        )
        // The 600-char snippet exceeds the 100-token budget and is skipped; the
        // short one still gets in and takes index 1 (numbering is placement, not rank).
        assertEquals(listOf("短片段"), packed.map { it.candidate.text })
        assertEquals(1, packed.single().index)
    }

    @Test
    fun textIsTruncatedToSnippetMaxChars() {
        val packed = QaBudget.pack(
            listOf(scored("字".repeat(1000), 10f)),
            QaBudget.BudgetLimits(maxSnippets = 5, snippetTokens = 100_000, snippetMaxChars = 200)
        )
        assertEquals(200, packed.single().candidate.text.length)
    }

    @Test
    fun perBookAndPerChapterLimitsApply() {
        val ranked = listOf(
            scored("a", 10f, bookId = "b1", chapterIndex = 0),
            scored("b", 9f, bookId = "b1", chapterIndex = 0),
            scored("c", 8f, bookId = "b1", chapterIndex = 0), // 3rd from same chapter: dropped
            scored("d", 7f, bookId = "b1", chapterIndex = 1),
            scored("e", 6f, bookId = "b1", chapterIndex = 1), // per-chapter cap again
            scored("f", 5f, bookId = "b1", chapterIndex = 2),
            scored("g", 4f, bookId = "b2", chapterIndex = 0)
        )
        val packed = QaBudget.pack(
            ranked,
            QaBudget.BudgetLimits(
                maxSnippets = 10, maxPerBook = 4, maxPerChapter = 2, snippetTokens = 100_000
            )
        )
        // per-chapter=2 allows two items per chapter: a,b from chapter 0 and
        // d,e from chapter 1; f is the 5th from b1 (per-book=4 blocks it); b2 gets in.
        assertEquals(5, packed.size)
        assertEquals(1, packed.count { it.candidate.bookId == "b2" })
        assertEquals(listOf("a", "b", "d", "e", "g"), packed.map { it.candidate.text })
    }

    @Test
    fun zeroAndNegativeScoresNeverPack() {
        val packed = QaBudget.pack(
            listOf(scored("x", 0f), scored("y", -1f), scored("z", 1f)),
            QaBudget.BudgetLimits(snippetTokens = 100_000)
        )
        assertEquals(listOf("z"), packed.map { it.candidate.text })
    }

    @Test
    fun estimateTokensWeightsCjkAndAsciiDifferently() {
        val cjk = QaBudget.estimateTokens("庄")
        val ascii = QaBudget.estimateTokens("a")
        assertTrue("CJK should cost more than one ASCII char", cjk > ascii)
        // Pure CJK ≈ 1 token/char plus the rounding floor.
        assertEquals(4, QaBudget.estimateTokens("庄生子"))
        assertEquals(5, QaBudget.estimateTokens("庄庄子子"))
    }

    @Test
    fun historyLimitKeepsNewestTurnsWithinBudget() {
        val turns = (1..10).map { QaTurn("问$it", "答".repeat(80)) }
        val kept = QaBudget.historyLimit(turns, QaBudget.BudgetLimits(historyTokens = 400))
        assertTrue(kept.isNotEmpty())
        assertTrue(kept.size < turns.size)
        // Keeps the newest side (tail preserved, oldest evicted first).
        assertEquals(turns.last().question, kept.last().question)
    }

    @Test
    fun trimToTotalDropsLowestScoredTailAndKeepsNumberingContiguous() {
        val ranked = (1..6).map { scored("字".repeat(100), score = 100f - it) }
        val snippets = QaBudget.pack(ranked, QaBudget.BudgetLimits(snippetTokens = 100_000))
        val trimmed = QaBudget.trimToTotal(
            snippets,
            systemPrompt = "系统提示".repeat(500),
            history = emptyList(),
            question = "问",
            limits = QaBudget.BudgetLimits(totalTokens = 900, snippetTokens = 100_000)
        )
        assertTrue(trimmed.size < snippets.size)
        assertEquals((1..trimmed.size).toList(), trimmed.map { it.index })
    }
}
