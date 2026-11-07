package com.huangder.lumibooks.data.repository

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.domain.model.Citation
import com.huangder.lumibooks.domain.model.ChatMessageStatus
import com.huangder.lumibooks.domain.model.QaSourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChatRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepositoryImpl(db.chatDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createSessionTruncatesTitleAndSortsByLastMessage() = runBlocking {
        val first = repository.createSession(null, "庄子的「忘言」到底想说什么？后面还有很长的补充", 100)
        val second = repository.createSession("book-1", "  多空格   归一  ", 200)
        val sessions = repository.observeSessions().first()
        assertEquals(2, sessions.size)
        // Newest activity sorts first: the second session leads.
        assertEquals(second, sessions.first().id)
        assertEquals("多空格 归一", sessions.first().title)
        assertEquals("book-1", sessions.first().entryBookId)
        // 23 chars — within the 24-char limit, so no truncation.
        assertEquals("庄子的「忘言」到底想说什么？后面还有很长的补充", sessions[1].title)
        assertEquals(23, sessions[1].title.length)
    }

    @Test
    fun beginTurnWritesUserAndPendingAssistantInOneStep() = runBlocking {
        val sessionId = repository.createSession(null, "问题", 100)
        repository.beginTurn(sessionId, "这本书讲什么？", 150)
        val messages = repository.observeMessages(sessionId).first()
        assertEquals(2, messages.size)
        assertEquals("这本书讲什么？", messages[0].content)
        assertEquals(com.huangder.lumibooks.domain.model.ChatRole.USER, messages[0].role)
        assertEquals("", messages[1].content)
        assertEquals(ChatMessageStatus.PENDING, messages[1].status)
        assertEquals(150, repository.observeSessions().first().single().lastMessageAt)
    }

    @Test
    fun completePersistsCitationsRoundTrip() = runBlocking {
        val sessionId = repository.createSession(null, "问题", 100)
        repository.beginTurn(sessionId, "问", 110)
        val pending = repository.observeMessages(sessionId).first().last()
        val citations = listOf(
            Citation(
                n = 1, type = QaSourceType.BOOK_BODY, bookId = "book-1", bookTitle = "《庄子》",
                chapterIndex = 3, chapterTitle = "养生主", startPosition = 42, endPosition = 90,
                quote = "含有「引号」与\n换行的逐字引文"
            ),
            Citation(
                n = 2, type = QaSourceType.KNOWLEDGE, nodeId = 7,
                chapterIndex = -1, startPosition = -1, quote = "知识证据"
            )
        )
        repository.completeAssistantMessage(pending.id, "回答正文 [1][2]", citations, sourceCount = 12, now = 200)
        val done = repository.observeMessages(sessionId).first().last()
        assertEquals(ChatMessageStatus.COMPLETE, done.status)
        assertEquals("回答正文 [1][2]", done.content)
        assertEquals(12, done.sourceCount)
        assertEquals(citations, done.citations)
        // Session activity bumps so the list re-sorts.
        assertEquals(200, repository.observeSessions().first().single().lastMessageAt)
    }

    @Test
    fun failRecordsErrorCodeAndKeepsStatusFailed() = runBlocking {
        val sessionId = repository.createSession(null, "问题", 100)
        repository.beginTurn(sessionId, "问", 110)
        val pending = repository.observeMessages(sessionId).first().last()
        repository.failAssistantMessage(pending.id, "UNAUTHORIZED", now = 150)
        val failed = repository.observeMessages(sessionId).first().last()
        assertEquals(ChatMessageStatus.FAILED, failed.status)
        assertEquals("UNAUTHORIZED", failed.errorCode)
    }

    @Test
    fun recentTurnsReturnsOldestFirstAndSkipsFailedAssistantRows() = runBlocking {
        val sessionId = repository.createSession(null, "问题", 100)
        repository.beginTurn(sessionId, "第一问", 110)
        val firstPending = repository.observeMessages(sessionId).first().last()
        repository.completeAssistantMessage(firstPending.id, "第一答", emptyList(), 0, 120)
        repository.beginTurn(sessionId, "第二问", 130)
        val secondPending = repository.observeMessages(sessionId).first().last()
        repository.failAssistantMessage(secondPending.id, "NETWORK", 140)
        val turns = repository.recentTurns(sessionId, limit = 6)
        // The failed turn's user question still replays (it is context); only
        // the failed assistant row is excluded.
        assertEquals(listOf("第一问", "第一答", "第二问"), turns.map { it.content })
    }

    @Test
    fun deleteSessionCascadesMessages() = runBlocking {
        val sessionId = repository.createSession(null, "问题", 100)
        repository.beginTurn(sessionId, "问", 110)
        repository.deleteSession(sessionId)
        assertTrue(repository.observeSessions().first().isEmpty())
        assertTrue(repository.observeMessages(sessionId).first().isEmpty())
    }

    @Test
    fun renameSessionTrimsAndLimits() = runBlocking {
        val sessionId = repository.createSession(null, "旧标题", 100)
        // 22 chars: within the limit, only whitespace is trimmed.
        repository.renameSession(sessionId, "  新标题加长版超过二十四个字符的部分会被截断掉  ", 120)
        assertEquals("新标题加长版超过二十四个字符的部分会被截断掉", repository.observeSessions().first().single().title)
    }
}
