package com.huangder.lumibooks.ui.knowledge

import androidx.lifecycle.SavedStateHandle
import com.huangder.lumibooks.ai.AiConfiguration
import com.huangder.lumibooks.ai.AiConfigurationSource
import com.huangder.lumibooks.ai.AiSettings
import com.huangder.lumibooks.ai.qa.CitationParser
import com.huangder.lumibooks.ai.qa.QaAnswerer
import com.huangder.lumibooks.ai.qa.QaRequest
import com.huangder.lumibooks.ai.qa.QaOutcome
import com.huangder.lumibooks.domain.model.Citation
import com.huangder.lumibooks.domain.model.ChatMessage
import com.huangder.lumibooks.domain.model.ChatMessageStatus
import com.huangder.lumibooks.domain.model.ChatRole
import com.huangder.lumibooks.domain.model.ChatSession
import com.huangder.lumibooks.domain.repository.ChatRepository
import com.huangder.lumibooks.translation.TranslationError
import com.huangder.lumibooks.translation.TranslationException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeQaViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var chats: FakeChatRepository
    private lateinit var answerer: FakeAnswerer
    private lateinit var configSource: FakeConfigSource
    private lateinit var viewModel: KnowledgeQaViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        chats = FakeChatRepository()
        answerer = FakeAnswerer()
        configSource = FakeConfigSource()
        viewModel = KnowledgeQaViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to "book-1")),
            chatRepository = chats,
            answerer = answerer,
            configurationSource = configSource
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.startCollectors() {
        // stateIn(WhileSubscribed) flows only run when collected; eager
        // collectors on an unconfined dispatcher make them update in-test.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sessions.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.configuration.collect {} }
        // Let the configuration flow emit before any send() checks it.
        advanceUntilIdle()
    }

    @Test
    fun notConfiguredSendIsRejectedWithoutAnyDbWrite() = runTest(dispatcher) {
        startCollectors()
        configSource.enabled = false
        advanceUntilIdle() // let the disabled config propagate through stateIn
        viewModel.setInput("问题")
        viewModel.send()
        advanceUntilIdle()
        assertEquals(QaNotice.NotConfigured, viewModel.notice.value)
        assertTrue(chats.sessions.value.isEmpty())
    }

    @Test
    fun sendWritesTurnAndCompletesItWithCitations() = runTest(dispatcher) {
        startCollectors()
        viewModel.setInput("这本书讲什么？")
        viewModel.send()
        advanceUntilIdle()
        assertEquals(false, viewModel.sending.value)
        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertEquals(ChatRole.USER, messages[0].message.role)
        assertEquals("这本书讲什么？", messages[0].message.content)
        assertEquals(ChatRole.ASSISTANT, messages[1].message.role)
        assertEquals(ChatMessageStatus.COMPLETE, messages[1].message.status)
        assertEquals(1, messages[1].message.citations.size)
        assertEquals(1, messages[1].message.sourceCount)
        // Entry book flows into the request.
        assertEquals("book-1", answerer.lastRequest?.entryBookId)
        // The session title came from the first question.
        assertEquals("这本书讲什么？", chats.sessions.value.single().title)
        assertEquals("book-1", chats.sessions.value.single().entryBookId)
    }

    @Test
    fun failedAnswerMarksRowFailedWithErrorCode() = runTest(dispatcher) {
        startCollectors()
        answerer.failure = TranslationException(TranslationError.UNAUTHORIZED)
        viewModel.setInput("问题")
        viewModel.send()
        advanceUntilIdle()
        val assistant = viewModel.messages.value.last()
        assertEquals(ChatMessageStatus.FAILED, assistant.message.status)
        assertEquals("UNAUTHORIZED", assistant.message.errorCode)
        assertEquals(QaNotice.Error(TranslationError.UNAUTHORIZED), viewModel.notice.value)
    }

    @Test
    fun doubleSendOnlyFiresOnce() = runTest(dispatcher) {
        startCollectors()
        viewModel.setInput("问题")
        viewModel.send()
        viewModel.send()
        advanceUntilIdle()
        assertEquals(1, answerer.calls.get())
    }

    @Test
    fun startNewSessionClearsMessagesButKeepsHistory() = runTest(dispatcher) {
        startCollectors()
        viewModel.setInput("第一问")
        viewModel.send()
        advanceUntilIdle()
        viewModel.startNewSession()
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
        assertEquals(1, chats.sessions.value.size)
        // Sending again creates a second session, not a continuation.
        viewModel.setInput("第二问")
        viewModel.send()
        advanceUntilIdle()
        assertEquals(2, chats.sessions.value.size)
        assertEquals(0, answerer.lastRequest?.history.orEmpty().size)
    }

    @Test
    fun secondQuestionReplaysPriorTurnAsHistory() = runTest(dispatcher) {
        startCollectors()
        viewModel.setInput("第一问")
        viewModel.send()
        advanceUntilIdle()
        viewModel.setInput("第二问")
        viewModel.send()
        advanceUntilIdle()
        assertEquals(1, chats.sessions.value.size)
        val history = answerer.lastRequest?.history.orEmpty()
        assertEquals(1, history.size)
        assertEquals("第一问", history.single().question)
        assertEquals("第一答 [1]。", history.single().answer)
    }

    private class FakeConfigSource(startEnabled: Boolean = true) : AiConfigurationSource {
        private val state = MutableStateFlow(
            AiConfiguration(AiSettings(enabled = startEnabled), hasToken = startEnabled)
        )
        var enabled: Boolean
            get() = state.value.enabled && state.value.hasToken
            set(value) {
                state.value = AiConfiguration(AiSettings(enabled = value), hasToken = value)
            }
        override val configuration: Flow<AiConfiguration> = state
    }

    private class FakeAnswerer : QaAnswerer {
        var failure: TranslationException? = null
        val calls = AtomicLong(0)
        var lastRequest: QaRequest? = null

        override suspend fun answer(request: QaRequest): QaOutcome {
            calls.incrementAndGet()
            lastRequest = request
            failure?.let { throw it }
            return QaOutcome(
                answer = "第一答 [1]。",
                segments = listOf(
                    CitationParser.AnswerSegment.Plain("第一答 "),
                    CitationParser.AnswerSegment.Cite(1),
                    CitationParser.AnswerSegment.Plain("。")
                ),
                citations = listOf(
                    Citation(
                        n = 1, type = com.huangder.lumibooks.domain.model.QaSourceType.BOOK_BODY,
                        bookId = "book-1", bookTitle = "庄子", chapterIndex = 0,
                        startPosition = 0, endPosition = 5, quote = "引文"
                    )
                ),
                sourceCount = 1
            )
        }
    }

    /** Minimal in-memory ChatRepository; no Room needed for ViewModel logic. */
    private class FakeChatRepository : ChatRepository {
        val sessions = MutableStateFlow<List<ChatSession>>(emptyList())
        private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        private val ids = AtomicLong(0)

        override fun observeSessions(): Flow<List<ChatSession>> = sessions

        override fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
            MutableStateFlow(messages.value.filter { it.sessionId == sessionId })

        override suspend fun recentTurns(sessionId: Long, limit: Int): List<ChatMessage> =
            messages.value
                .filter { it.sessionId == sessionId && it.status == ChatMessageStatus.COMPLETE }
                .takeLast(limit)

        override suspend fun createSession(entryBookId: String?, firstQuestion: String, now: Long): Long {
            val id = ids.incrementAndGet()
            sessions.value = sessions.value + ChatSession(
                id = id,
                title = firstQuestion.trim().take(24),
                entryBookId = entryBookId,
                createdAt = now,
                lastMessageAt = now
            )
            return id
        }

        override suspend fun beginTurn(sessionId: Long, question: String, now: Long): Long {
            val userId = ids.incrementAndGet()
            val assistantId = ids.incrementAndGet()
            messages.value = messages.value + listOf(
                ChatMessage(id = userId, sessionId = sessionId, role = ChatRole.USER, content = question, status = ChatMessageStatus.COMPLETE, createdAt = now),
                ChatMessage(id = assistantId, sessionId = sessionId, role = ChatRole.ASSISTANT, content = "", status = ChatMessageStatus.PENDING, createdAt = now)
            )
            touch(sessionId, now)
            return assistantId
        }

        override suspend fun completeAssistantMessage(
            messageId: Long, content: String, citations: List<Citation>, sourceCount: Int, now: Long
        ) {
            replace(messageId) { it.copy(content = content, citations = citations, sourceCount = sourceCount, status = ChatMessageStatus.COMPLETE) }
        }

        override suspend fun failAssistantMessage(messageId: Long, errorCode: String, now: Long) {
            replace(messageId) { it.copy(status = ChatMessageStatus.FAILED, errorCode = errorCode) }
        }

        override suspend fun pendingMessageId(sessionId: Long): Long? =
            messages.value.lastOrNull {
                it.sessionId == sessionId && it.status == ChatMessageStatus.PENDING
            }?.id

        override suspend fun renameSession(sessionId: Long, title: String, now: Long) {
            sessions.value = sessions.value.map {
                if (it.id == sessionId) it.copy(title = title) else it
            }
        }

        override suspend fun deleteSession(sessionId: Long) {
            sessions.value = sessions.value.filterNot { it.id == sessionId }
            messages.value = messages.value.filterNot { it.sessionId == sessionId }
        }

        private fun replace(messageId: Long, transform: (ChatMessage) -> ChatMessage) {
            messages.value = messages.value.map { if (it.id == messageId) transform(it) else it }
        }

        private fun touch(sessionId: Long, now: Long) {
            sessions.value = sessions.value.map {
                if (it.id == sessionId) it.copy(lastMessageAt = now) else it
            }
        }
    }
}
