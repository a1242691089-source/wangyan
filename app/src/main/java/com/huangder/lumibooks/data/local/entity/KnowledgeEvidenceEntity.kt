package com.huangder.lumibooks.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A locatable excerpt backing a knowledge node. bookId deliberately has no
 * foreign key (same choice as notes/bookmarks): deleting a book must not
 * destroy knowledge that quotes it; the UI falls back to a missing-book label.
 * Positions follow the note conventions — chapterIndex/startPosition of -1
 * mean "saved without a selection".
 */
@Entity(
    tableName = "knowledge_evidence",
    indices = [
        Index(value = ["nodeId"]),
        Index(value = ["syncId"], unique = true)
    ],
    foreignKeys = [ForeignKey(
        entity = KnowledgeNodeEntity::class,
        parentColumns = ["id"],
        childColumns = ["nodeId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class KnowledgeEvidenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nodeId: Long,
    val syncId: String = "",
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String? = null,
    val startPosition: Int,
    val endPosition: Int,
    val startLocatorJson: String? = null,
    val endLocatorJson: String? = null,
    val selectedText: String,
    /** Optional link to the annotation row this evidence came from. */
    val noteSyncId: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)
