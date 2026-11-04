package com.huangder.lumibooks.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMirrorTest {

    private val direct = "https://github.com/a1242691089-source/wangyan/releases/download/v1/book.zip"

    @Test
    fun `candidates keep direct source first then proxies in fixed order`() {
        assertEquals(
            listOf(
                direct,
                "https://gh-proxy.com/$direct",
                "https://ghproxy.net/$direct"
            ),
            RemoteMirror.candidates(direct)
        )
    }

    @Test
    fun `candidates wrap raw github files as well`() {
        val raw = "https://raw.githubusercontent.com/a1242691089-source/wangyan/main/docs/app-config.json"
        assertEquals(3, RemoteMirror.candidates(raw).size)
        assertEquals(raw, RemoteMirror.candidates(raw).first())
    }

    @Test
    fun `candidates are idempotent for an already proxied url`() {
        val proxied = "https://gh-proxy.com/$direct"
        assertEquals(listOf(proxied), RemoteMirror.candidates(proxied))
    }

    @Test
    fun `candidates leave untrusted hosts untouched`() {
        val jsdelivr = "https://cdn.jsdelivr.net/gh/a1242691089-source/wangyan@main/docs/app.js"
        assertEquals(listOf(jsdelivr), RemoteMirror.candidates(jsdelivr))
        val evil = "https://evil.test/$direct"
        assertEquals(listOf(evil), RemoteMirror.candidates(evil))
    }

    @Test
    fun `candidates for lists try every direct source before any proxy`() {
        val raw = "https://raw.githubusercontent.com/a1242691089-source/wangyan/main/docs/f.ttf"
        val jsdelivr = "https://cdn.jsdelivr.net/gh/a1242691089-source/wangyan@main/docs/f.ttf"
        val ordered = RemoteMirror.candidates(listOf(raw, jsdelivr))
        assertEquals(raw, ordered[0])
        assertEquals(jsdelivr, ordered[1])
        assertTrue(ordered[2].startsWith("https://gh-proxy.com/"))
        assertEquals(4, ordered.size)
    }

    @Test
    fun `unwrap returns the inner url`() {
        assertEquals(direct, RemoteMirror.unwrap("https://gh-proxy.com/$direct"))
        assertEquals(direct, RemoteMirror.unwrap("https://ghproxy.net/$direct"))
    }

    @Test
    fun `unwrap keeps outer query out of the inner url`() {
        val raw = "https://raw.githubusercontent.com/a1242691089-source/wangyan/main/docs/app-config.json"
        assertEquals(raw, RemoteMirror.unwrap("https://gh-proxy.com/$raw?t=594123"))
        assertNull(RemoteMirror.unwrap("https://gh-proxy.com/$raw?t=594123", allowQuery = false))
    }

    @Test
    fun `unwrap rejects malformed or hostile proxies`() {
        val cases = listOf(
            "http://gh-proxy.com/$direct",                       // 外层非 https
            "https://gh-proxy.com.evil.test/$direct",            // host 前缀伪装
            "https://user@gh-proxy.com/$direct",                 // userInfo
            "https://gh-proxy.com:8443/$direct",                 // 非标准端口
            "https://gh-proxy.com/https://evil.test/x.zip",      // 内层不受信
            "https://gh-proxy.com/https://gh-proxy.com/$direct", // 嵌套反代
            "https://gh-proxy.com/https:/%2Fgithub.com/$direct", // 编码绕过
            "https://github.com/$direct"                         // 不是反代形态
        )
        cases.forEach { assertNull("should reject: $it", RemoteMirror.unwrap(it)) }
    }

    @Test
    fun `unwrap rejects dot segments`() {
        assertNull(
            RemoteMirror.unwrap(
                "https://gh-proxy.com/https://raw.githubusercontent.com/a1242691089-source/wangyan/../../evil.json"
            )
        )
    }

    @Test
    fun `isAllowed accepts trusted direct urls and valid proxies only`() {
        assertTrue(RemoteMirror.isAllowed(direct))
        assertTrue(RemoteMirror.isAllowed("https://gh-proxy.com/$direct"))
        assertFalse(RemoteMirror.isAllowed("https://evil.test/$direct"))
        assertFalse(RemoteMirror.isAllowed("https://gh-proxy.com/https://evil.test/x.zip"))
    }

    @Test
    fun `firstSuccess falls through to the next source`() {
        val attempts = mutableListOf<String>()
        val value = RemoteMirror.firstSuccess(direct) { url ->
            attempts += url
            if (attempts.size == 1) throw IllegalStateException("直连失败")
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(2, attempts.size)
        assertTrue(attempts[1].startsWith("https://gh-proxy.com/"))
    }

    @Test
    fun `firstSuccess rethrows the last failure when every source fails`() {
        val attempts = mutableListOf<String>()
        val error = runCatching {
            RemoteMirror.firstSuccess<Unit>(direct) { url ->
                attempts += url
                throw IllegalStateException(url)
            }
        }.exceptionOrNull()
        assertEquals(3, attempts.size)
        assertTrue(error is IllegalStateException)
        assertEquals(attempts.last(), error?.message)
    }

    @Test
    fun `firstSuccess stops immediately on MirrorAbort`() {
        var calls = 0
        val error = runCatching {
            RemoteMirror.firstSuccess<Unit>(direct) {
                calls++
                throw MirrorAbort("too large")
            }
        }.exceptionOrNull()
        assertEquals(1, calls)
        assertTrue(error is MirrorAbort)
    }
}
