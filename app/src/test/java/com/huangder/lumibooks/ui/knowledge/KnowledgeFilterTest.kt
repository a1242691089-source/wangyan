package com.huangder.lumibooks.ui.knowledge

import com.huangder.lumibooks.domain.model.KnowledgeCard
import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNode
import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeFilterTest {
    private fun card(id: Long, tags: List<String>) = KnowledgeCard(
        node = KnowledgeNode(id = id, syncId = "node-$id", thought = "理解$id", tags = tags, createdAt = id),
        evidence = KnowledgeEvidence(
            nodeId = id, bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 1,
            selectedText = "原文$id", createdAt = id
        ),
        bookTitle = "书"
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
}
