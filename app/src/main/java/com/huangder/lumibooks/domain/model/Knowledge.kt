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
    val bookTitle: String?,
    /** Relations where this node is either end; 0 hides the badge on the card. */
    val relationCount: Int = 0
)

/** A book that owns at least one knowledge card; title is null once the book is gone. */
data class KnowledgeBookRef(val bookId: String, val title: String?)

/**
 * Edge types between knowledge nodes, direction source -> target. The stored
 * value is what lands in the database, mirroring the notes.type precedent:
 * adding a type later never needs a migration.
 */
enum class KnowledgeRelationType(val stored: String) {
    MENTIONS("mentions"),
    EXPLAINS("explains"),
    SUPPORTS("supports"),
    CONTRADICTS("contradicts"),
    EXTENDS("extends"),
    BELONGS_TO("belongs_to"),
    APPLIES_TO("applies_to"),
    EXEMPLIFIES("exemplifies"),
    RELATED_TO("related_to");

    companion object {
        /** Unknown or legacy values fall back to RELATED_TO so rows never disappear. */
        fun fromStored(value: String?): KnowledgeRelationType =
            entries.firstOrNull { it.stored == value } ?: RELATED_TO
    }
}

/** A directed edge; A -> B and B -> A are distinct rows. */
data class KnowledgeRelation(
    val id: Long = 0,
    val syncId: String = "",
    val sourceNodeId: Long,
    val targetNodeId: Long,
    val type: KnowledgeRelationType,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

/** A relation rendered from one node's point of view. */
data class KnowledgeRelationView(
    val relation: KnowledgeRelation,
    /** True when the node we are looking at is the source of the edge. */
    val isOutgoing: Boolean,
    val counterpartNodeId: Long,
    val counterpartTitle: String
)

/** Everything the node detail page needs, assembled from three flows. */
data class KnowledgeNodeDetail(
    val node: KnowledgeNode,
    val evidences: List<KnowledgeEvidence>,
    val relations: List<KnowledgeRelationView>
)

/** Target picker row when adding a relation. */
data class KnowledgeNodeSummary(
    val id: Long,
    val title: String,
    val thought: String
)
