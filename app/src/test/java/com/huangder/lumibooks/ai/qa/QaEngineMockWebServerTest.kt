package com.huangder.lumibooks.ai.qa

import com.huangder.lumibooks.ai.AiMessage
import com.huangder.lumibooks.ai.AiSettings
import com.huangder.lumibooks.ai.LlmClient
import com.huangder.lumibooks.domain.model.QaSourceType
import com.huangder.lumibooks.translation.TranslationAuth
import com.huangder.lumibooks.translation.TranslationException
import com.huangder.lumibooks.translation.TranslationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end pipeline test over the wire: packed snippets go through
 * QaPromptBuilder into a real LlmClient against a MockWebServer, and the
 * response comes back through CitationParser/citationsFor. QaEngine itself is
 * a thin orchestration on top of exactly these steps.
 */
class QaEngineMockWebServerTest {
    private fun settings(server: MockWebServer) = AiSettings(
        enabled = true,
        provider = TranslationProvider.CUSTOM,
        baseUrl = server.url("/v1/").toString(),
        auth = TranslationAuth.BEARER,
        model = "test-model",
        allowHttp = true
    )

    private fun snippets(): List<SourceSnippet> = QaBudget.pack(
        listOf(
            ScoredSource(
                SourceCandidate(
                    type = QaSourceType.BOOK_BODY, text = "得意而忘言，得鱼而忘筌。",
                    bookId = "book-1", bookTitle = "庄子", chapterIndex = 2,
                    chapterTitle = "外物", startPosition = 10, endPosition = 30, createdAt = 100
                ),
                score = 10f
            )
        )
    )

    @Test
    fun requestCarriesConstraintsAndSnippetsAnswerParsesToCitations() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"书中提到「得意而忘言」[1]。〔推断〕这可能呼应了筌蹄之喻。"}}]}"""
                )
            )
            val snippets = snippets()
            val messages = QaPromptBuilder.build("忘言是什么意思？", snippets, emptyList())
            val answer = LlmClient().chat(settings(server), "token", messages)
            val parsed = CitationParser.parse(answer, snippets.size)
            val citations = citationsFor(parsed, snippets)

            val request = server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)!!
            val body = org.json.JSONObject(request.body.readUtf8())
            assertEquals("/v1/chat/completions", request.path)
            assertEquals(false, body.optBoolean("stream"))
            assertEquals("test-model", body.optString("model"))
            val sent = body.optJSONArray("messages")!!
            val system = sent.getJSONObject(0).optString("content")
            listOf(
                "只能使用片段中的信息", "标注来源编号", "提供的片段没有直接讲到这一点",
                "逐字照抄", "不要替作者下判断", "〔推断〕"
            ).forEach { constraint ->
                assertTrue("system prompt missing: $constraint", system.contains(constraint))
            }
            assertTrue(system.contains("[1]（书籍正文｜《庄子》第3章·外物）得意而忘言"))

            assertEquals(1, parsed.citedIndices.size)
            val citation = citations.single()
            assertEquals(1, citation.n)
            assertEquals("book-1", citation.bookId)
            assertEquals(2, citation.chapterIndex)
            assertEquals(10, citation.startPosition)
            assertEquals("得意而忘言，得鱼而忘筌。", citation.quote)
            // Inference sentence is separated for the UI.
            assertEquals(1, parsed.segments.count { it is CitationParser.AnswerSegment.Inference })
        }
    }

    @Test
    fun uncitedAnswerYieldsEmptyCitationsButSourceCountStays() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"提供的片段没有直接讲到这一点。"}}]}"""
                )
            )
            val snippets = snippets()
            val messages = QaPromptBuilder.build("量子纠缠？", snippets, emptyList())
            val answer = LlmClient().chat(settings(server), "token", messages)
            val parsed = CitationParser.parse(answer, snippets.size)
            assertTrue(citationsFor(parsed, snippets).isEmpty())
            // Retrieval DID find something; the model just did not cite it.
            assertEquals(1, snippets.size)
        }
    }

    @Test
    fun unauthorizedSurfacesAsTranslationException() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key"}"""))
            val messages = listOf(AiMessage.user("问"))
            try {
                LlmClient().chat(settings(server), "token", messages)
                throw AssertionError("expected TranslationException")
            } catch (expected: TranslationException) {
                assertEquals(com.huangder.lumibooks.translation.TranslationError.UNAUTHORIZED, expected.error)
            }
        }
    }
}
