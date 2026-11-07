package com.huangder.lumibooks.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One turn of a [ChatSessionEntity]. Assistant rows are written in two steps:
 * a pending placeholder when the question lands (so the UI and process death
 * both see an honest state), then completed or failed in place.
 *
 * sourceCount is the number of snippets actually handed to the model this
 * turn — the only trustworthy "did we even have evidence" signal:
 * citationsJson empty + sourceCount > 0 means the model skipped citations,
 * while sourceCount == 0 means retrieval found nothing.
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId", "createdAt"])],
    foreignKeys = [ForeignKey(
        entity = ChatSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    @ColumnInfo(defaultValue = "'[]'")
    val citationsJson: String = "[]",
    @ColumnInfo(defaultValue = "0")
    val sourceCount: Int = 0,
    @ColumnInfo(defaultValue = "'complete'")
    val status: String = STATUS_COMPLETE,
    /** TranslationError.name when status is failed; null otherwise. */
    val errorCode: String? = null,
    val createdAt: Long
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val STATUS_PENDING = "pending"
        const val STATUS_COMPLETE = "complete"
        const val STATUS_FAILED = "failed"
    }
}
