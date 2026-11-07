package com.huangder.lumibooks.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A knowledge-QA conversation (V0.5b). Purely local data: no syncId on
 * purpose — chat history never enters the sync protocol, and adding sync
 * fields later would be its own migration decision, not an oversight.
 * entryBookId has no foreign key (same choice as evidence/notes): deleting a
 * book must not destroy conversations; it only biases retrieval and labels.
 */
@Entity(
    tableName = "chat_sessions",
    indices = [
        Index(value = ["lastMessageAt"]),
        Index(value = ["entryBookId"])
    ]
)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** First question truncated; empty questions fall back to a default label. */
    val title: String,
    val entryBookId: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** Independent sort key for the session list; bumps on every message. */
    val lastMessageAt: Long = createdAt
)
