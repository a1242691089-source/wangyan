package com.huangder.lumibooks.dictionary

import com.huangder.lumibooks.util.RemoteMirror
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 下载任务持久化：1.0.0 写下的 state.json 必须能被 1.0.1 原样复活。 */
class DictionaryTaskCodecTest {

    private val url = "https://github.com/a1242691089-source/wangyan/releases/download/v1/book.zip"
    private val proxies = RemoteMirror.candidates(url)

    private fun descriptor() = DictionaryDescriptor(
        id = "book", name = "书库", description = "", version = "1", source = "s",
        sourceUrl = "https://github.com/a1242691089-source/wangyan", license = "MIT",
        attribution = "", modifications = "", upstreamVersion = "1", downloadUrl = url,
        sizeBytes = 1024, installedBytes = 2048, sha256 = "a".repeat(64),
        filterVersion = 1, disclaimerVersion = 1
    )

    private fun legacyJson() = JSONObject()
        .put("descriptor", descriptor().toJson())
        .put("downloadId", 42L)
        .put("path", "C:/app/dictionary-downloads/abc.zip")
        .put("token", "abc-123")
        .put("confirmation", descriptor().confirmationKey)
        .put("error", JSONObject.NULL)

    @Test
    fun `legacy state without sources is migrated in place`() {
        val state = DictionaryTaskCodec.fromJson(legacyJson(), now = 5_000L)
        assertNotNull(state)
        assertEquals(42L, state!!.downloadId)
        assertEquals(0, state.sourceIndex)
        assertEquals(proxies, state.sources)
        assertEquals(0L, state.progressBytes)
        // 老格式没有时间戳：重置为 now，等于给新进程一个完整观察窗口
        assertEquals(5_000L, state.startedAt)
        assertEquals(5_000L, state.lastProgressAt)
    }

    @Test
    fun `new format round trips`() {
        val original = DictionaryTaskState(
            descriptor = descriptor(), downloadId = 7L, path = "C:/d/abc-1.zip", token = "abc-123",
            confirmation = descriptor().confirmationKey, sources = proxies, sourceIndex = 1,
            progressBytes = 4096L, startedAt = 900L, lastProgressAt = 950L
        )
        val restored = DictionaryTaskCodec.fromJson(DictionaryTaskCodec.toJson(original), now = 10_000L)
        assertEquals(original, restored)
    }

    @Test
    fun `untrusted sources are dropped and rebuilt`() {
        val json = legacyJson().put("sources", JSONArray(listOf("https://evil.test/book.zip")))
        val state = DictionaryTaskCodec.fromJson(json, now = 1_000L)!!
        assertEquals(proxies, state.sources)
    }

    @Test
    fun `source index is clamped into range`() {
        val original = DictionaryTaskState(
            descriptor = descriptor(), downloadId = 1L, path = "p", token = "t-1",
            confirmation = descriptor().confirmationKey, sources = proxies, sourceIndex = 0
        )
        val json = DictionaryTaskCodec.toJson(original).put("sourceIndex", 99)
        assertEquals(proxies.lastIndex, DictionaryTaskCodec.fromJson(json, now = 1_000L)!!.sourceIndex)
    }

    @Test
    fun `restart invalidates future timestamps`() {
        val json = legacyJson().put("startedAt", 9_999L).put("lastProgressAt", 9_999L)
        val state = DictionaryTaskCodec.fromJson(json, now = 1_000L)!!
        assertEquals(1_000L, state.startedAt)
        assertEquals(1_000L, state.lastProgressAt)
    }

    @Test
    fun `broken entries are rejected without throwing`() {
        assertNull(DictionaryTaskCodec.fromJson(JSONObject().put("downloadId", 1L), now = 1_000L))
        val missingToken = legacyJson()
        missingToken.remove("token")
        assertNull(DictionaryTaskCodec.fromJson(missingToken, now = 1_000L))
    }
}
