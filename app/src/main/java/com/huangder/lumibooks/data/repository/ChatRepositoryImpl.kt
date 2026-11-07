package com.huangder.lumibooks.data.repository

import androidx.room.withTransaction
import com.huangder.lumibooks.data.local.dao.ChatDao
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.ChatMessageEntity
import com.huangder.lumibooks.data.local.entity.ChatSessionEntity
import com.huangder.lumibooks.domain.model.Citation
import com.huangder.lumibooks.domain.model.ChatMessage
import com.huangder.lumibooks.domain.model.ChatMessageStatus
import com.huangder.lumibooks.domain.model.ChatRole
import com.huangder.lumibooks.domain.model.ChatSession
import com.huangder.lumibooks.domain.model.QaSourceType
import com.huangder.lumibooks.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val database: AppDatabase
) : ChatRepository {

    override fun observeSessions(): Flow<List<ChatSession>> =
        chatDao.observeSessions().map { rows -> rows.map { it.toDomain() } }

    override fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        chatDao.observeMessages(sessionId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun recentTurns(sessionId: Long, limit: Int): List<ChatMessage> =
        chatDao.recentCompleted(sessionId, limit).map { it.toDomain() }.reversed()

    override suspend fun createSession(
        entryBookId: String?,
        firstQuestion: String,
        now: Long
    ): Long = withContext(Dispatchers.IO) {
        val title = firstQuestion.trim().replace(Regex("\\s+"), " ").take(TITLE_MAX_LENGTH)
            .ifBlank { " " }
        chatDao.insertSession(
            ChatSessionEntity(
                title = title,
                entryBookId = entryBookId?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
                lastMessageAt = now
            )
        )
    }

    override suspend fun beginTurn(sessionId: Long, question: String, now: Long): Long =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = ChatMessageEntity.ROLE_USER,
                        content = question,
                        createdAt = now
                    )
                )
                // Placeholder row: an honest "in flight" state that survives process death.
                chatDao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = ChatMessageEntity.ROLE_ASSISTANT,
                        content = "",
                        status = ChatMessageEntity.STATUS_PENDING,
                        createdAt = now
                    )
                ).also { chatDao.touchSession(sessionId, now) }
            }
        }

    override suspend fun completeAssistantMessage(
        messageId: Long,
        content: String,
        citations: List<Citation>,
        sourceCount: Int,
        now: Long
    ) = withContext(Dispatchers.IO) {
        chatDao.completeMessage(messageId, content, citationsToJson(citations), sourceCount)
        chatDao.sessionIdOf(messageId)?.let { chatDao.touchSession(it, now) }
        Unit
    }

    override suspend fun failAssistantMessage(messageId: Long, errorCode: String, now: Long) =
        withContext(Dispatchers.IO) {
            chatDao.failMessage(messageId, errorCode)
            chatDao.sessionIdOf(messageId)?.let { chatDao.touchSession(it, now) }
            Unit
        }

    override suspend fun pendingMessageId(sessionId: Long): Long? = withContext(Dispatchers.IO) {
        chatDao.pendingMessageId(sessionId)
    }

    override suspend fun renameSession(sessionId: Long, title: String, now: Long) =
        withContext(Dispatchers.IO) {
            chatDao.renameSession(sessionId, title.trim().take(TITLE_MAX_LENGTH), now)
        }

    override suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteSession(sessionId)
    }

    private fun ChatSessionEntity.toDomain() = ChatSession(
        id = id,
        title = title,
        entryBookId = entryBookId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastMessageAt = lastMessageAt
    )

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = ChatRole.fromStored(role),
        content = content,
        citations = citationsFromJson(citationsJson),
        sourceCount = sourceCount,
        status = ChatMessageStatus.fromStored(status),
        errorCode = errorCode,
        createdAt = createdAt
    )

    companion object {
        const val TITLE_MAX_LENGTH = 24

        fun citationsToJson(citations: List<Citation>): String = JSONArray().apply {
            citations.forEach { c ->
                put(JSONObject().apply {
                    put("n", c.n)
                    put("type", c.type.name)
                    put("bookId", c.bookId ?: JSONObject.NULL)
                    put("bookTitle", c.bookTitle ?: JSONObject.NULL)
                    put("chapterIndex", c.chapterIndex)
                    put("chapterTitle", c.chapterTitle ?: JSONObject.NULL)
                    put("startPosition", c.startPosition)
                    put("endPosition", c.endPosition)
                    put("quote", c.quote)
                    put("nodeId", c.nodeId ?: JSONObject.NULL)
                })
            }
        }.toString()

        fun citationsFromJson(raw: String?): List<Citation> = runCatching {
            val array = JSONArray(raw ?: return emptyList())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Citation(
                    n = o.optInt("n", 0),
                    type = runCatching { QaSourceType.valueOf(o.optString("type")) }
                        .getOrDefault(QaSourceType.BOOK_BODY),
                    bookId = o.optStringOrNull("bookId"),
                    bookTitle = o.optStringOrNull("bookTitle"),
                    chapterIndex = o.optInt("chapterIndex", -1),
                    chapterTitle = o.optStringOrNull("chapterTitle"),
                    startPosition = o.optInt("startPosition", -1),
                    endPosition = o.optInt("endPosition", -1),
                    quote = o.optString("quote", ""),
                    nodeId = if (o.isNull("nodeId")) null else o.optLong("nodeId")
                )
            }
        }.getOrDefault(emptyList())

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key, null)
    }
}
