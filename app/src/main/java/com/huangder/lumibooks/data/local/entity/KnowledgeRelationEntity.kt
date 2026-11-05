package com.huangder.lumibooks.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A directed edge between two knowledge nodes (source -> target), typed by a
 * KnowledgeRelationType stored string. Both ends cascade with their node, same
 * choice as evidence: deleting a node must not orphan its edges. One row per
 * (source, target, type); A->B and B->A are distinct rows because most types
 * carry directional meaning. Sync fields mirror the other knowledge tables.
 */
@Entity(
    tableName = "knowledge_relations",
    indices = [
        Index(value = ["sourceNodeId"]),
        Index(value = ["targetNodeId"]),
        Index(value = ["sourceNodeId", "targetNodeId", "relationType"], unique = true),
        Index(value = ["syncId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceNodeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetNodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class KnowledgeRelationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceNodeId: Long,
    val targetNodeId: Long,
    val syncId: String = "",
    val relationType: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)
