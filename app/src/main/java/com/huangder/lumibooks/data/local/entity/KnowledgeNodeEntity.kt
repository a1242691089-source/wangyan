package com.huangder.lumibooks.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A knowledge node: the user's distilled understanding anchored to evidence
 * from books. V1 keeps the node itself free of book references — evidence
 * rows (1:N) carry the locatable excerpts, so one node can later accumulate
 * multiple sources. Sync fields mirror NoteEntity for a future sync protocol.
 */
@Entity(
    tableName = "knowledge_nodes",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class KnowledgeNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val syncId: String = "",
    /** Derived at write time from the first line of thought (24 chars); empty = pure excerpt. */
    val title: String = "",
    /** The user's own understanding; may be blank when only the excerpt is kept. */
    val thought: String = "",
    @ColumnInfo(defaultValue = "'[]'")
    val tagsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long = createdAt
)
