package com.huangder.lumibooks.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 更新配置的跳转链接校验：反代只能当传输通道，不能把用户导向第三方。 */
class UpdateCheckerReleaseUrlTest {

    private val valid = "https://github.com/a1242691089-source/wangyan/releases/latest"

    @Test
    fun `trusted release urls are accepted`() {
        assertTrue(UpdateChecker.isTrustedReleaseUrl("https://github.com/a1242691089-source/wangyan/releases"))
        assertTrue(UpdateChecker.isTrustedReleaseUrl(valid))
        assertTrue(UpdateChecker.isTrustedReleaseUrl("https://github.com/a1242691089-source/wangyan/releases/tag/v1.0.1"))
    }

    @Test
    fun `untrusted release urls are rejected`() {
        val cases = listOf(
            "",
            "http://github.com/a1242691089-source/wangyan/releases/latest",
            "https://github.com.evil.test/a1242691089-source/wangyan/releases/latest",
            "https://github.com/a1242691089-source/other/releases/latest",
            "https://github.com/a1242691089-source/wangyan/releases-evil",
            "https://github.com/a1242691089-source/wangyan/releases/../../evil",
            "https://gh-proxy.com/https://github.com/a1242691089-source/wangyan/releases/latest",
            "javascript:alert(1)"
        )
        cases.forEach { assertFalse("should reject: $it", UpdateChecker.isTrustedReleaseUrl(it)) }
    }

    @Test
    fun `parseConfig reads a well formed config`() {
        val config = UpdateChecker.parseConfig(configJson(valid))
        assertEquals("1.0.1", config.latestVersion)
        assertEquals(2L, config.latestVersionCode)
        assertEquals(valid, config.releaseUrl)
        assertEquals("忘言 1.0.1", config.updateTitle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseConfig rejects a config pointing elsewhere`() {
        UpdateChecker.parseConfig(configJson("https://evil.test/wangyan-1.0.1.apk"))
    }

    private fun configJson(releaseUrl: String): JSONObject = JSONObject(
        """
        {
          "latest_version": "1.0.1",
          "latest_version_code": 2,
          "release_url": "$releaseUrl",
          "terms_version": 2,
          "privacy_version": 3,
          "update": {
            "latest_version_code": 2,
            "latest_version_name": "1.0.1",
            "download_url": "$releaseUrl",
            "title": "忘言 1.0.1",
            "message": "修复下载",
            "changelog": "· 修复 下载卡死",
            "force_update_below_version_code": 0
          },
          "notice": { "enabled": false }
        }
        """.trimIndent()
    )
}
