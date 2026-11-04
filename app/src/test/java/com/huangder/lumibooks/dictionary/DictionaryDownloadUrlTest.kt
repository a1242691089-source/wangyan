package com.huangder.lumibooks.dictionary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 词典下载地址白名单：允许受信反代兜底，但内层必须仍落在本仓库/上游的 release 路径。 */
class DictionaryDownloadUrlTest {

    private val fork = "https://github.com/a1242691089-source/wangyan/releases/download/v1/book.zip"
    private val upstream = "https://github.com/huangder/Lumi_Books/releases/download/v1/book.zip"

    @Test
    fun `direct release urls stay allowed`() {
        assertTrue(isDictionaryDownloadUrl(fork))
        assertTrue(isDictionaryDownloadUrl(upstream))
    }

    @Test
    fun `proxied release urls are allowed`() {
        assertTrue(isDictionaryDownloadUrl("https://gh-proxy.com/$fork"))
        assertTrue(isDictionaryDownloadUrl("https://ghproxy.net/$upstream"))
    }

    @Test
    fun `proxy cannot point outside the release download path`() {
        assertFalse(
            isDictionaryDownloadUrl(
                "https://gh-proxy.com/https://github.com/a1242691089-source/wangyan/releases/latest"
            )
        )
        assertFalse(
            isDictionaryDownloadUrl(
                "https://gh-proxy.com/https://github.com/a1242691089-source/wangyan/releases/download-evil/v1/book.zip"
            )
        )
        assertFalse(
            isDictionaryDownloadUrl(
                "https://gh-proxy.com/https://github.com/attacker/wangyan/releases/download/v1/book.zip"
            )
        )
    }

    @Test
    fun `proxied urls may not carry query parameters`() {
        assertFalse(isDictionaryDownloadUrl("https://gh-proxy.com/$fork?t=1"))
    }

    @Test
    fun `nested proxies and encoded path tricks are rejected`() {
        assertFalse(isDictionaryDownloadUrl("https://gh-proxy.com/https://gh-proxy.com/$fork"))
        assertFalse(isDictionaryDownloadUrl("https://gh-proxy.com/https:/%2Fgithub.com/$fork"))
        assertFalse(isDictionaryDownloadUrl("https://gh-proxy.com/https://evil.test/$fork"))
    }

    @Test
    fun `dot segments cannot bypass the prefix check`() {
        assertFalse(
            isDictionaryDownloadUrl(
                "https://gh-proxy.com/https://github.com/a1242691089-source/wangyan/releases/download/../../../evil/x.zip"
            )
        )
    }
}
