package com.huangder.lumibooks.ui.knowledge

import com.huangder.lumibooks.domain.model.KnowledgeBookRef
import com.huangder.lumibooks.domain.model.KnowledgeCard
import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNode
import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeFilterTest {
    private fun card(
        id: Long,
        tags: List<String>,
        bookId: String = "book",
        bookTitle: String? = "书"
    ) = KnowledgeCard(
        node = KnowledgeNode(id = id, syncId = "node-$id", thought = "理解$id", tags = tags, createdAt = id),
        evidence = KnowledgeEvidence(
            nodeId = id, bookId = bookId, chapterIndex = 0, startPosition = 0, endPosition = 1,
            selectedText = "原文$id", createdAt = id
        ),
        bookTitle = bookTitle
    )

    @Test
    fun nullTagKeepsAllCards() {
        val cards = listOf(card(1, listOf("庄子")), card(2, emptyList()))
        assertEquals(cards, filterKnowledgeCards(cards, null))
    }

    @Test
    fun tagFilterKeepsOnlyMatchingNodes() {
        val cards = listOf(card(1, listOf("庄子", "言意")), card(2, emptyList()), card(3, listOf("言意")))
        assertEquals(listOf("node-1", "node-3"), filterKnowledgeCards(cards, "言意").map { it.node.syncId })
    }

    @Test
    fun unknownTagYieldsEmptyList() {
        assertEquals(emptyList<KnowledgeCard>(), filterKnowledgeCards(listOf(card(1, listOf("a"))), "b"))
    }

    @Test
    fun bookFilterKeepsOnlyMatchingBookId() {
        val cards = listOf(
            card(1, emptyList(), bookId = "a"),
            card(2, emptyList(), bookId = "b"),
            card(3, emptyList(), bookId = "a")
        )
        assertEquals(
            listOf("node-1", "node-3"),
            filterKnowledgeCards(cards, tag = null, bookId = "a").map { it.node.syncId }
        )
    }

    @Test
    fun bookAndTagFiltersAreAnded() {
        val cards = listOf(
            card(1, listOf("言意"), bookId = "a"),
            card(2, emptyList(), bookId = "a"),
            card(3, listOf("言意"), bookId = "b")
        )
        assertEquals(
            listOf("node-1"),
            filterKnowledgeCards(cards, tag = "言意", bookId = "a").map { it.node.syncId }
        )
    }

    @Test
    fun nullTagAndNullBookKeepAllCards() {
        val cards = listOf(card(1, emptyList(), bookId = "a"), card(2, listOf("x"), bookId = "b"))
        assertEquals(cards, filterKnowledgeCards(cards, tag = null, bookId = null))
        // The old two-argument call still means "no filter at all".
        assertEquals(cards, filterKnowledgeCards(cards, null))
    }

    @Test
    fun unknownBookYieldsEmptyList() {
        assertEquals(
            emptyList<KnowledgeCard>(),
            filterKnowledgeCards(listOf(card(1, emptyList(), bookId = "a")), tag = null, bookId = "z")
        )
    }

    @Test
    fun deriveKnowledgeBooksDistinctsAndSortsByTitle() {
        val cards = listOf(
            card(1, emptyList(), bookId = "b", bookTitle = "庄子"),
            card(2, emptyList(), bookId = "a", bookTitle = "论语"),
            card(3, emptyList(), bookId = "b", bookTitle = "庄子"),
            card(4, emptyList(), bookId = "gone", bookTitle = null)
        )
        assertEquals(
            listOf(
                KnowledgeBookRef("a", "论语"),
                KnowledgeBookRef("b", "庄子"),
                KnowledgeBookRef("gone", null)
            ),
            deriveKnowledgeBooks(cards)
        )
    }
}
