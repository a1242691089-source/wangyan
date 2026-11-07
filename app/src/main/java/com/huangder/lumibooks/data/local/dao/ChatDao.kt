package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.huangder.lumibooks.data.local.entity.ChatMessageEntity
import com.huangder.lumibooks.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageAt DESC, id DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun sessionById(id: Long): ChatSessionEntity?

    @Query("SELECT sessionId FROM chat_messages WHERE id = :messageId")
    suspend fun sessionIdOf(messageId: Long): Long?

    @Query(
        "SELECT id FROM chat_messages WHERE sessionId = :sessionId AND status = 'pending' " +
            "ORDER BY id DESC LIMIT 1"
    )
    suspend fun pendingMessageId(sessionId: Long): Long?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>>

    /** Prompt history: most recent completed messages, caller reverses. */
    @Query(
        "SELECT * FROM chat_messages WHERE sessionId = :sessionId AND status = 'complete' " +
            "AND role IN ('user', 'assistant') ORDER BY id DESC LIMIT :limit"
    )
    suspend fun recentCompleted(sessionId: Long, limit: Int): List<ChatMessageEntity>

    @Insert
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query(
        "UPDATE chat_messages SET content = :content, citationsJson = :citationsJson, " +
            "sourceCount = :sourceCount, status = 'complete' WHERE id = :id"
    )
    suspend fun completeMessage(id: Long, content: String, citationsJson: String, sourceCount: Int)

    @Query("UPDATE chat_messages SET status = 'failed', errorCode = :errorCode WHERE id = :id")
    suspend fun failMessage(id: Long, errorCode: String)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun renameSession(id: Long, title: String, now: Long)

    @Query("UPDATE chat_sessions SET updatedAt = :now, lastMessageAt = :now WHERE id = :id")
    suspend fun touchSession(id: Long, now: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}
