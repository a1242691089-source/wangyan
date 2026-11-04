package com.huangder.lumibooks.dictionary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 卡死判定：PENDING / 零增长 / 慢源 都应该换源，而不是无限挂起。 */
class DictionaryDownloadWatchdogTest {

    private val total = 74L * 1024 * 1024

    private fun switch(
        status: DownloadStatus,
        bytes: Long = 0,
        startedAt: Long = 0,
        lastProgressAt: Long,
        now: Long
    ) = DictionaryDownloadWatchdog.shouldSwitch(status, bytes, total, startedAt, lastProgressAt, now)

    @Test
    fun `pending requests switch after thirty seconds`() {
        assertFalse(switch(DownloadStatus.PENDING, lastProgressAt = 0, now = 29_000))
        assertTrue(switch(DownloadStatus.PENDING, lastProgressAt = 0, now = 31_000))
    }

    @Test
    fun `running downloads switch only when bytes stop growing`() {
        // 每秒都有增长：不换源
        assertFalse(switch(DownloadStatus.RUNNING, bytes = 1_000_000, lastProgressAt = 99_000, now = 100_000))
        // 25 秒零增长：换源
        assertTrue(switch(DownloadStatus.RUNNING, bytes = 1_000_000, lastProgressAt = 60_000, now = 86_000))
    }

    @Test
    fun `paused downloads get a longer window`() {
        assertFalse(switch(DownloadStatus.PAUSED, lastProgressAt = 0, now = 44_000))
        assertTrue(switch(DownloadStatus.PAUSED, lastProgressAt = 0, now = 46_000))
    }

    @Test
    fun `slow sources under one percent are dropped`() {
        // 跑了 2 分钟只下到 600KB（远低于 1%）→ 换源
        assertTrue(switch(DownloadStatus.RUNNING, bytes = 600_000, startedAt = 0, lastProgressAt = 121_000, now = 121_000))
        // 同样时间下到 13MB（>1%）→ 继续等
        assertFalse(switch(DownloadStatus.RUNNING, bytes = 13L * 1024 * 1024, startedAt = 0, lastProgressAt = 121_000, now = 121_000))
    }

    @Test
    fun `slow rule is skipped when total size is unknown`() {
        assertFalse(
            DictionaryDownloadWatchdog.shouldSwitch(
                DownloadStatus.RUNNING, 0L, 0L, startedAt = 0, lastProgressAt = 121_000, now = 121_000
            )
        )
    }

    @Test
    fun `terminal states never switch`() {
        assertFalse(switch(DownloadStatus.SUCCESSFUL, lastProgressAt = 0, now = 999_000))
        assertFalse(switch(DownloadStatus.FAILED, lastProgressAt = 0, now = 999_000))
    }
}
