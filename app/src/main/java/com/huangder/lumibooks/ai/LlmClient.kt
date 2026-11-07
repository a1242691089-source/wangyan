package com.huangder.lumibooks.ai

import com.huangder.lumibooks.translation.TranslationAuth
import com.huangder.lumibooks.translation.TranslationError
import com.huangder.lumibooks.translation.TranslationException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

internal fun aiEndpoint(settings: AiSettings, path: String = "chat/completions"): HttpUrl {
    val url = settings.normalized().baseUrl.toHttpUrlOrNull()
        ?: throw TranslationException(TranslationError.CONFIGURATION)
    if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null ||
        (url.scheme != "https" && !settings.allowHttp) || url.encodedPath.endsWith("/chat/completions")) {
        throw TranslationException(TranslationError.CONFIGURATION)
    }
    return url.newBuilder().addPathSegments(path).build()
}

internal fun validateAiSettings(settings: AiSettings) {
    aiEndpoint(settings)
    if (settings.model.isBlank()) throw TranslationException(TranslationError.CONFIGURATION)
}

internal fun aiPayload(settings: AiSettings, messages: List<AiMessage>): String = JSONObject().apply {
    put("model", settings.model)
    put("stream", false)
    put("messages", JSONArray().apply {
        messages.forEach { message ->
            put(JSONObject().put("role", message.role).put("content", message.content))
        }
    })
}.toString()

/**
 * 通用 LLM 客户端（V0.5a）：OpenAI 兼容的 chat/completions 与 models 接口。
 * 请求构造、错误映射与 TranslationHttpClient 同构（同一批服务商、同一组错误语义），
 * 但保持自包含——翻译是已发布功能，不为泛化而去动它的代码。
 */
@Singleton
class LlmClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // OkHttp may repeat a 503 with Retry-After: 0 even when connection retries are off.
            if (response.code == 503) response.newBuilder().removeHeader("Retry-After").build() else response
        }.build()

    /** 返回助手消息文本；reasoning-only 响应视为空。 */
    suspend fun chat(settings: AiSettings, token: String, messages: List<AiMessage>): String {
        validateAiSettings(settings)
        if (token.isBlank()) throw TranslationException(TranslationError.MISSING_KEY)
        if (messages.none { it.role == "user" || it.role == "assistant" }) {
            throw TranslationException(TranslationError.CONFIGURATION)
        }
        val normalized = settings.normalized()
        val request = authorizedRequest(normalized, token).url(aiEndpoint(normalized))
            .post(aiPayload(normalized, messages).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return execute(request) { root ->
            val content = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content") as? String
            content?.trim()?.takeIf(String::isNotEmpty) ?: throw TranslationException(TranslationError.EMPTY_RESPONSE)
        }
    }

    suspend fun models(settings: AiSettings, token: String): List<String> {
        if (token.isBlank()) throw TranslationException(TranslationError.MISSING_KEY)
        val normalized = settings.normalized()
        val request = authorizedRequest(normalized, token).url(aiEndpoint(normalized, "models"))
            .get().build()
        return execute(request) { root ->
            val data = root.optJSONArray("data") ?: throw TranslationException(TranslationError.EMPTY_RESPONSE)
            (0 until data.length()).mapNotNull { index ->
                (data.optJSONObject(index)?.opt("id") as? String)?.trim()?.takeIf(String::isNotEmpty)
            }.distinct().sorted().takeIf { it.isNotEmpty() } ?: throw TranslationException(TranslationError.EMPTY_RESPONSE)
        }
    }

    private fun authorizedRequest(settings: AiSettings, token: String) = Request.Builder()
        .header(if (settings.auth == TranslationAuth.API_KEY) "api-key" else "Authorization",
            if (settings.auth == TranslationAuth.API_KEY) token else "Bearer $token")

    private suspend fun <T> execute(request: Request, parse: (JSONObject) -> T): T {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(TranslationException(
                        if (e is InterruptedIOException) TranslationError.TIMEOUT else TranslationError.NETWORK))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw TranslationException(when (it.code) {
                                401, 403 -> TranslationError.UNAUTHORIZED
                                429 -> TranslationError.RATE_LIMITED
                                400, 404 -> TranslationError.MODEL_UNAVAILABLE
                                else -> TranslationError.SERVICE
                            })
                            parse(JSONObject(it.body?.string().orEmpty()))
                        }
                    }
                    if (continuation.isActive) result.fold(continuation::resume) { error ->
                        continuation.resumeWithException(if (error is TranslationException) error else
                            TranslationException(when (error) {
                                is InterruptedIOException -> TranslationError.TIMEOUT
                                is IOException -> TranslationError.NETWORK
                                else -> TranslationError.EMPTY_RESPONSE
                            }))
                    }
                }
            })
        }
    }
}
