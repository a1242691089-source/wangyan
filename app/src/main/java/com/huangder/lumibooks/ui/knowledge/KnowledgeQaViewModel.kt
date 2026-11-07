package com.huangder.lumibooks.ui.knowledge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.ai.AiConfiguration
import com.huangder.lumibooks.ai.AiRepository
import com.huangder.lumibooks.ai.qa.CitationParser
import com.huangder.lumibooks.ai.qa.QaAnswerer
import com.huangder.lumibooks.ai.qa.QaRequest
import com.huangder.lumibooks.ai.qa.QaTurn
import com.huangder.lumibooks.data.repository.ChatRepositoryImpl
import com.huangder.lumibooks.domain.model.ChatMessage
import com.huangder.lumibooks.domain.model.ChatMessageStatus
import com.huangder.lumibooks.domain.model.ChatRole
import com.huangder.lumibooks.domain.model.ChatSession
import com.huangder.lumibooks.domain.repository.ChatRepository
import com.huangder.lumibooks.translation.TranslationError
import com.huangder.lumibooks.translation.TranslationException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How stale a pending row may get before the UI calls it interrupted. */
private const val PENDING_TIMEOUT_MS = 2 * 60 * 1000L

data class QaMessageUi(
    val message: ChatMessage,
    /** Parsed renderable segments; null for user rows and rows without content. */
    val parsed: CitationParser.ParsedAnswer?,
    /** True when a pending row has been in flight too long. */
    val interrupted: Boolean
)

sealed interface QaNotice {
    data object NotConfigured : QaNotice
    data class Error(val error: TranslationError) : QaNotice
}

@HiltViewModel
class KnowledgeQaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val answerer: QaAnswerer,
    configurationSource: com.huangder.lumibooks.ai.AiConfigurationSource
) : ViewModel() {

    private val entryBookId: String? =
        savedStateHandle.get<String>("bookId")?.takeIf { it.isNotBlank() }

    val configuration: StateFlow<AiConfiguration?> =
        configurationSource.configuration.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessions: StateFlow<List<ChatSession>> =
        chatRepository.observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sessionId = MutableStateFlow<Long?>(null)

    val messages: StateFlow<List<QaMessageUi>> = sessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else chatRepository.observeMessages(id)
        }
        .map { rows -> rows.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _showSessions = MutableStateFlow(false)
    val showSessions: StateFlow<Boolean> = _showSessions.asStateFlow()

    private val _notice = MutableStateFlow<QaNotice?>(null)
    val notice: StateFlow<QaNotice?> = _notice.asStateFlow()

    private var inflight: Job? = null

    fun entryBook(): String? = entryBookId

    fun setInput(value: String) {
        _input.value = value
    }

    fun consumeNotice() {
        _notice.value = null
    }

    fun toggleSessions(visible: Boolean) {
        _showSessions.value = visible
    }

    fun startNewSession() {
        inflight?.cancel()
        sessionId.value = null
        _input.value = ""
        _showSessions.value = false
        _sending.value = false
    }

    fun selectSession(id: Long) {
        inflight?.cancel()
        sessionId.value = id
        _input.value = ""
        _showSessions.value = false
        _sending.value = false
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(id)
            if (sessionId.value == id) {
                sessionId.value = null
            }
        }
    }

    fun send() {
        val question = _input.value.trim()
        if (question.isEmpty() || _sending.value) return
        if (configuration.value?.enabled != true) {
            _notice.value = QaNotice.NotConfigured
            return
        }
        _sending.value = true
        inflight = viewModelScope.launch {
            val assistantMessageId: Long
            try {
                // History is read before this turn is written so it never
                // contains the question being asked.
                val currentId = sessionId.value
                val history = currentId?.let { chatRepository.recentTurns(it, limit = 12) }
                    .orEmpty().toTurns()
                val sid = currentId
                    ?: chatRepository.createSession(entryBookId, question, System.currentTimeMillis())
                        .also { sessionId.value = it }
                assistantMessageId = chatRepository.beginTurn(sid, question, System.currentTimeMillis())
                _input.value = ""
                val outcome = answerer.answer(
                    QaRequest(sessionId = sid, question = question, entryBookId = entryBookId, history = history)
                )
                chatRepository.completeAssistantMessage(
                    messageId = assistantMessageId,
                    content = outcome.answer,
                    citations = outcome.citations,
                    sourceCount = outcome.sourceCount,
                    now = System.currentTimeMillis()
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _sending.value = false
                throw cancelled
            } catch (failure: TranslationException) {
                failInflight(failure.error)
            } catch (failure: Exception) {
                failInflight(TranslationError.SERVICE)
            }
            _sending.value = false
        }
    }

    private suspend fun failInflight(error: TranslationError) {
        _notice.value = QaNotice.Error(error)
        val sid = sessionId.value ?: return
        val pendingId = chatRepository.pendingMessageId(sid) ?: return
        chatRepository.failAssistantMessage(pendingId, error.name, System.currentTimeMillis())
    }

    /** Completed question/answer pairs in chronological order. */
    private fun List<ChatMessage>.toTurns(): List<QaTurn> {
        val turns = ArrayList<QaTurn>()
        var question: String? = null
        forEach { message ->
            when (message.role) {
                ChatRole.USER -> question = message.content
                ChatRole.ASSISTANT -> {
                    val asked = question
                    question = null
                    if (asked != null && message.status == ChatMessageStatus.COMPLETE) {
                        turns.add(QaTurn(asked, message.content))
                    }
                }
            }
        }
        return turns
    }

    private fun ChatMessage.toUi(): QaMessageUi {
        val parsed = when {
            role != ChatRole.ASSISTANT || status != ChatMessageStatus.COMPLETE -> null
            else -> CitationParser.parse(content, sourceCount)
        }
        return QaMessageUi(
            message = this,
            parsed = parsed,
            interrupted = status == ChatMessageStatus.PENDING &&
                System.currentTimeMillis() - createdAt > PENDING_TIMEOUT_MS
        )
    }
}
