package com.huangder.lumibooks.ui.knowledge

import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNodeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeRelationsDisplayTest {
    private fun evidence(
        chapterIndex: Int = 2,
        startPosition: Int = 120,
        bookId: String = "book-1"
    ) = KnowledgeEvidence(
        bookId = bookId,
        chapterIndex = chapterIndex,
        startPosition = startPosition,
        endPosition = startPosition + 4,
        selectedText = "得意而忘言",
        createdAt = 100
    )

    @Test
    fun evidenceJumpTargetCarriesChapterAndOffset() {
        assertEquals(2 to 120, evidenceJumpTarget(evidence()))
    }

    @Test
    fun evidenceJumpTargetRejectsUnanchoredEvidence() {
        assertNull(evidenceJumpTarget(evidence(chapterIndex = -1, startPosition = -1)))
        assertNull(evidenceJumpTarget(evidence(chapterIndex = -1, startPosition = 0)))
        assertNull(evidenceJumpTarget(evidence(chapterIndex = 0, startPosition = -1)))
    }

    @Test
    fun filterRelationTargetsExcludesCurrentNode() {
        val summaries = listOf(
            KnowledgeNodeSummary(id = 1, title = "甲", thought = ""),
            KnowledgeNodeSummary(id = 2, title = "乙", thought = "")
        )
        assertEquals(
            listOf(2L),
            filterRelationTargets(summaries, query = "", excludeNodeId = 1).map { it.id }
        )
    }

    @Test
    fun filterRelationTargetsMatchesTitleOrThoughtIgnoringCase() {
        val summaries = listOf(
            KnowledgeNodeSummary(id = 1, title = "言不尽意", thought = "庄子的命题"),
            KnowledgeNodeSummary(id = 2, title = "得意忘言", thought = "王弼的注解"),
            KnowledgeNodeSummary(id = 3, title = "无用之用", thought = "")
        )
        assertEquals(
            listOf(1L, 2L),
            filterRelationTargets(summaries, query = "言", excludeNodeId = 0).map { it.id }
        )
        assertEquals(
            listOf(2L),
            filterRelationTargets(summaries, query = "王弼", excludeNodeId = 0).map { it.id }
        )
    }

    @Test
    fun filterRelationTargetsTrimsQueryAndKeepsAllOnBlank() {
        val summaries = (1L..3L).map { KnowledgeNodeSummary(id = it, title = "节点$it", thought = "") }
        assertEquals(3, filterRelationTargets(summaries, query = "   ", excludeNodeId = 0).size)
        assertTrue(filterRelationTargets(summaries, query = " 节点2 ", excludeNodeId = 0).map { it.id } == listOf(2L))
    }
}
