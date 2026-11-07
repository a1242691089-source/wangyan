package com.huangder.lumibooks.ai

import com.huangder.lumibooks.translation.TranslationAuth
import com.huangder.lumibooks.translation.TranslationError
import com.huangder.lumibooks.translation.TranslationException
import com.huangder.lumibooks.translation.TranslationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LlmClientTest {
    private fun settings(server: MockWebServer, auth: TranslationAuth = TranslationAuth.BEARER) = AiSettings(
        enabled = true, provider = TranslationProvider.CUSTOM, baseUrl = server.url("/v1/").toString(),
        auth = auth, model = "ai-model", allowHttp = true)

    private fun response(text: String = "好的") = MockResponse().setBody(
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message",
            JSONObject().put("content", text).put("reasoning_content", "private reasoning")))).toString())

    @Test fun chatSendsMessagesAndUsesBothAuthenticationStyles() = runBlocking {
        MockWebServer().use { server ->
            val client = LlmClient()
            for (auth in TranslationAuth.entries) {
                server.enqueue(response())
                val reply = client.chat(settings(server, auth), "test-key",
                    listOf(AiMessage.system("You are a reading assistant."), AiMessage.user("这本书讲了什么？")))
                assertEquals("好的", reply)
                val request = server.takeRequest(3, TimeUnit.SECONDS)!!
                assertEquals("/v1/chat/completions", request.path)
                assertEquals("POST", request.method)
                assertEquals(if (auth == TranslationAuth.BEARER) "Bearer test-key" else null,
                    request.getHeader("Authorization"))
                assertEquals(if (auth == TranslationAuth.API_KEY) "test-key" else null,
                    request.getHeader("api-key"))
                val body = JSONObject(request.body.readUtf8())
                assertEquals("ai-model", body.getString("model"))
                assertFalse(body.getBoolean("stream"))
                val messages = body.getJSONArray("messages")
                assertEquals(2, messages.length())
                assertEquals("system", messages.getJSONObject(0).getString("role"))
                assertEquals("这本书讲了什么？", messages.getJSONObject(1).getString("content"))
            }
        }
    }

    @Test fun chatRejectsSystemOnlyConversationAndBlankToken() {
        val client = LlmClient()
        val failure = runCatching { runBlocking {
            client.chat(AiSettings(baseUrl = "https://example.com/v1", model = "m"),
                "test-key", listOf(AiMessage.system("only system")))
        } }.exceptionOrNull()
        assertEquals(TranslationError.CONFIGURATION, (failure as TranslationException).error)
        val missingKey = runCatching { runBlocking {
            client.chat(AiSettings(baseUrl = "https://example.com/v1", model = "m"),
                " ", listOf(AiMessage.user("hi")))
        } }.exceptionOrNull()
        assertEquals(TranslationError.MISSING_KEY, (missingKey as TranslationException).error)
    }

    @Test fun mapsHttpFailuresWithoutAutomaticRetries() = runBlocking {
        MockWebServer().use { server ->
            val client = LlmClient()
            val cases = mapOf(401 to TranslationError.UNAUTHORIZED, 403 to TranslationError.UNAUTHORIZED,
                429 to TranslationError.RATE_LIMITED, 400 to TranslationError.MODEL_UNAVAILABLE,
                404 to TranslationError.MODEL_UNAVAILABLE, 500 to TranslationError.SERVICE,
                503 to TranslationError.SERVICE)
            cases.forEach { (code, expected) ->
                server.enqueue(MockResponse().setResponseCode(code).setBody("sensitive service details"))
                val failure = runCatching {
                    client.chat(settings(server), "test-key", listOf(AiMessage.user("hi")))
                }.exceptionOrNull()
                assertEquals(expected, (failure as TranslationException).error)
                assertFalse(failure.message.orEmpty().contains("sensitive"))
            }
            assertEquals(cases.size, server.requestCount)
        }
    }

    @Test fun rejectsEmptyMalformedAndReasoningOnlyResponses() = runBlocking {
        MockWebServer().use { server ->
            val client = LlmClient()
            for (body in listOf("{}", "not json", "{\"choices\":[]}",
                "{\"choices\":[{\"message\":{\"content\":null,\"reasoning_content\":\"thinking\"}}]}")) {
                server.enqueue(MockResponse().setBody(body))
                val error = runCatching {
                    client.chat(settings(server), "test-key", listOf(AiMessage.user("hi")))
                }.exceptionOrNull()
                assertEquals(TranslationError.EMPTY_RESPONSE, (error as TranslationException).error)
            }
        }
    }

    @Test fun serviceRetryAfterZeroDoesNotRepeatPaidRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(response("should not be requested"))
            val error = runCatching {
                LlmClient().chat(settings(server), "test-key", listOf(AiMessage.user("hi")))
            }.exceptionOrNull()
            assertEquals(TranslationError.SERVICE, (error as TranslationException).error)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun doesNotFollowRedirectOrLeakCredentialsToRedirectTarget() = runBlocking {
        MockWebServer().use { source -> MockWebServer().use { target ->
            source.enqueue(MockResponse().setResponseCode(307).setHeader("Location", target.url("/target")))
            val error = runCatching {
                LlmClient().chat(settings(source), "test-key", listOf(AiMessage.user("hi")))
            }.exceptionOrNull()
            assertEquals(TranslationError.SERVICE, (error as TranslationException).error)
            assertEquals(0, target.requestCount)
        } }
    }

    @Test fun cancellationInterruptsPendingNetworkRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val task = async(Dispatchers.IO) {
                LlmClient().chat(settings(server), "test-key", listOf(AiMessage.user("hi")))
            }
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            withTimeout(2_000) { task.cancelAndJoin() }
            assertTrue(task.isCancelled)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun endpointRequiresHttpsUnlessExplicitlyAllowedAndRejectsCredentialsOrFullCompletionPath() {
        for (url in listOf("http://example.com/v1", "https://user:pass@example.com/v1",
            "https://example.com/v1?api_key=secret", "https://example.com/v1#fragment",
            "https://example.com/v1/chat/completions")) {
            val failure = runCatching { aiEndpoint(AiSettings(baseUrl = url)) }.exceptionOrNull()
            assertEquals(TranslationError.CONFIGURATION, (failure as TranslationException).error)
        }
        assertEquals("http://localhost:1234/v1/chat/completions",
            aiEndpoint(AiSettings(baseUrl = "http://localhost:1234/v1", allowHttp = true)).toString())
    }

    @Test fun fetchesModelsWithoutRequiringModelNameAndUsesConfiguredAuthentication() = runBlocking {
        MockWebServer().use { server ->
            for (auth in TranslationAuth.entries) {
                server.enqueue(MockResponse().setBody(
                    "{\"data\":[{\"id\":\"model-b\"},{\"id\":\"model-a\"},{\"id\":\"model-b\"},{\"id\":\"\"},{}]}"))
                assertEquals(listOf("model-a", "model-b"),
                    LlmClient().models(settings(server, auth).copy(model = ""), "test-key"))
                val request = server.takeRequest(3, TimeUnit.SECONDS)!!
                assertEquals("GET", request.method)
                assertEquals("/v1/models", request.path)
                assertEquals(0L, request.bodySize)
                assertEquals(if (auth == TranslationAuth.API_KEY) "test-key" else "Bearer test-key",
                    request.getHeader(if (auth == TranslationAuth.API_KEY) "api-key" else "Authorization"))
            }
        }
    }

    @Test fun emptyOrUnsupportedModelListsReturnUsableErrors() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"data\":[]}"))
            val client = LlmClient()
            var error = runCatching { client.models(settings(server), "test-key") }.exceptionOrNull()
            assertEquals(TranslationError.EMPTY_RESPONSE, (error as TranslationException).error)
            server.enqueue(MockResponse().setResponseCode(404))
            error = runCatching { client.models(settings(server), "test-key") }.exceptionOrNull()
            assertEquals(TranslationError.MODEL_UNAVAILABLE, (error as TranslationException).error)
        }
    }
}
