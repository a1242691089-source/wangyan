package com.huangder.lumibooks.domain.model

/** The user's distilled understanding; evidence rows carry the locatable excerpts. */
data class KnowledgeNode(
    val id: Long = 0,
    val syncId: String = "",
    val title: String = "",
    val thought: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

/** A locatable excerpt backing a [KnowledgeNode]; -1 positions mean "saved without a selection". */
data class KnowledgeEvidence(
    val id: Long = 0,
    val nodeId: Long = 0,
    val syncId: String = "",
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String? = null,
    val startPosition: Int,
    val endPosition: Int,
    val startLocatorJson: String? = null,
    val endLocatorJson: String? = null,
    val selectedText: String,
    val noteSyncId: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

/** Card-flow view model: newest evidence of a node joined with the book title. */
data class KnowledgeCard(
    val node: KnowledgeNode,
    val evidence: KnowledgeEvidence,
    /** Null when the book was removed from the library; the UI shows a fallback label. */
    val bookTitle: String?
)
