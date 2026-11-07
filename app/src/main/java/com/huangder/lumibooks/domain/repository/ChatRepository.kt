package com.huangder.lumibooks.domain.repository

import com.huangder.lumibooks.domain.model.Citation
import com.huangder.lumibooks.domain.model.ChatMessage
import com.huangder.lumibooks.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

/**
 * Local-only store for knowledge-QA conversations. Sessions and messages are
 * never synced; history exists so the user can revisit past answers and their
 * citations.
 */
interface ChatRepository {
    fun observeSessions(): Flow<List<ChatSession>>

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>>

    /** Recent completed turns (oldest first) for prompt replay. */
    suspend fun recentTurns(sessionId: Long, limit: Int): List<ChatMessage>

    /** Creates the session on first send; title is the truncated question. */
    suspend fun createSession(entryBookId: String?, firstQuestion: String, now: Long): Long

    /**
     * Writes the user turn and a pending assistant placeholder in one
     * transaction; returns the placeholder id to complete or fail later.
     */
    suspend fun beginTurn(sessionId: Long, question: String, now: Long): Long

    suspend fun completeAssistantMessage(
        messageId: Long,
        content: String,
        citations: List<Citation>,
        sourceCount: Int,
        now: Long
    )

    suspend fun failAssistantMessage(messageId: Long, errorCode: String, now: Long)

    /** The in-flight assistant placeholder of a session, if any. */
    suspend fun pendingMessageId(sessionId: Long): Long?

    suspend fun renameSession(sessionId: Long, title: String, now: Long)

    suspend fun deleteSession(sessionId: Long)
}
