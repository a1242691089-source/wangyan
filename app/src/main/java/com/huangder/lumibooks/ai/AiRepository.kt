package com.huangder.lumibooks.ai

import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.translation.TranslationError
import com.huangder.lumibooks.translation.TranslationException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AiRepository @Inject constructor(
    private val store: DataStoreManager,
    private val tokens: AiTokenStore,
    private val client: LlmClient
) {
    private val mutex = Mutex()
    val configuration: Flow<AiConfiguration> = store.aiSettings.map { settings ->
        mutex.withLock { AiConfiguration(settings, tokens.hasToken(settings.credentialScope)) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** 独立于设置流读取当前生效配置（供未来的问答等消费方使用）。 */
    suspend fun currentConfiguration(): AiConfiguration = mutex.withLock {
        val settings = store.aiSettings.first()
        AiConfiguration(settings, tokens.hasToken(settings.credentialScope))
    }

    suspend fun chat(messages: List<AiMessage>, configuration: AiConfiguration): String {
        val token = withContext(Dispatchers.IO) { mutex.withLock {
            if (store.aiSettings.first() != configuration.settings)
                throw CancellationException("AI configuration changed")
            tokens.read(configuration.settings.credentialScope)
        } }
            ?: throw TranslationException(TranslationError.MISSING_KEY)
        return client.chat(configuration.settings, token, messages)
    }

    suspend fun save(settings: AiSettings, newToken: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalized = settings.normalized()
            val token = newToken.trim()
            if (token.any { it.isISOControl() }) throw TranslationException(TranslationError.CONFIGURATION)
            if (normalized.enabled) {
                validateAiSettings(normalized)
                if (token.isBlank() && !tokens.hasToken(normalized.credentialScope))
                    throw TranslationException(TranslationError.MISSING_KEY)
            }
            if (token.isNotBlank()) tokens.save(token, normalized.credentialScope)
            val previous = store.aiSettings.first()
            store.saveAiSettings(normalized.copy(credentialVersion =
                if (token.isNotBlank()) UUID.randomUUID().toString() else previous.credentialVersion))
        }
    }

    suspend fun test(settings: AiSettings, newToken: String): String {
        return client.chat(settings, resolveDraftToken(settings, newToken),
            listOf(AiMessage.user("Reply with the single word: OK")))
    }

    suspend fun models(settings: AiSettings, newToken: String): List<String> =
        client.models(settings, resolveDraftToken(settings, newToken))

    private suspend fun resolveDraftToken(settings: AiSettings, newToken: String): String {
        val token = newToken.trim().takeIf { it.isNotBlank() }
            ?: withContext(Dispatchers.IO) { mutex.withLock { tokens.read(settings.credentialScope) } }
            ?: throw TranslationException(TranslationError.MISSING_KEY)
        if (token.any { it.isISOControl() }) throw TranslationException(TranslationError.CONFIGURATION)
        return token
    }
}
