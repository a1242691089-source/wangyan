package com.huangder.lumibooks.ai.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaTokenizerTest {
    @Test
    fun chineseRunsBecomeSlidingBigrams() {
        val query = QaTokenizer.query("忘言")
        assertEquals(listOf("忘言"), query.terms.map { it.text })
        val longer = QaTokenizer.query("知识图谱")
        assertEquals(listOf("知识", "识图", "图谱"), longer.terms.map { it.text })
    }

    @Test
    fun asciiWordsOfTwoOrMoreCharactersAreKept() {
        val query = QaTokenizer.query("What is BM25 for retrieval")
        // Stopwords and single letters are dropped; case is lowered.
        assertEquals(listOf("bm25", "retrieval"), query.terms.map { it.text })
    }

    @Test
    fun interrogativeScaffoldingIsRemoved() {
        val query = QaTokenizer.query("这本书讲了什么")
        assertFalse(query.terms.any { it.text == "什么" })
        assertFalse(query.terms.any { it.text == "这本" })
        assertTrue(query.terms.isNotEmpty())
    }

    @Test
    fun termCountIsCapped() {
        // Distinct CJK characters → distinct bigrams, far beyond the cap.
        val text = (0x4e00 until 0x4e00 + 40).map { it.toChar() }.joinToString("")
        val query = QaTokenizer.query(text)
        assertEquals(24, query.terms.size)
    }

    @Test
    fun duplicatesAreRemoved() {
        val query = QaTokenizer.query("忘言，忘言")
        assertEquals(1, query.terms.size)
    }

    @Test
    fun countOccurrencesIsNonOverlappingAndCapped() {
        assertEquals(3, QaTokenizer.countOccurrences("忘言忘言忘言", "忘言"))
        assertEquals(2, QaTokenizer.countOccurrences("aaaa", "aa")) // non-overlapping
        assertEquals(4, QaTokenizer.countOccurrences("忘言".repeat(10), "忘言", cap = 4))
        assertEquals(0, QaTokenizer.countOccurrences("庄子", "老子"))
        assertEquals(0, QaTokenizer.countOccurrences("", "庄子"))
    }

    @Test
    fun emptyQuestionYieldsEmptyQuery() {
        assertTrue(QaTokenizer.query("").isEmpty)
        assertTrue(QaTokenizer.query("什么？如何？").isEmpty)
    }
}
