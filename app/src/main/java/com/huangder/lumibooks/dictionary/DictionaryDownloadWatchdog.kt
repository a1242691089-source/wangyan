package com.huangder.lumibooks.dictionary

/** DownloadManager 状态的可测映射，避免单测依赖 android.app.DownloadManager 常量。 */
internal enum class DownloadStatus { PENDING, RUNNING, PAUSED, SUCCESSFUL, FAILED }

/**
 * 下载卡死判定。
 *
 * 1.0.0 的「0B 无反应」根因是 STATUS_PENDING 走 else 分支被当成 DOWNLOADING，
 * 进度永远停在 0 且永不报错。这里只做纯计算，时间全部由调用方传入。
 *
 * 阈值依据：本机实测直连被阻断时 0.13s 就失败（PENDING 30s 足够判死）；
 * 反代实测 111-256 KB/s，25s 无增长约等于丢掉 3-6MB，属于真停滞。
 */
internal object DictionaryDownloadWatchdog {
    const val PENDING_TIMEOUT_MS = 30_000L
    const val RUNNING_TIMEOUT_MS = 25_000L
    const val PAUSED_TIMEOUT_MS = 45_000L
    const val SLOW_CHECK_AFTER_MS = 120_000L
    const val SLOW_MIN_RATIO = 0.01

    fun shouldSwitch(
        status: DownloadStatus,
        bytes: Long,
        total: Long,
        startedAt: Long,
        lastProgressAt: Long,
        now: Long
    ): Boolean {
        val stalled = now - lastProgressAt
        return when (status) {
            DownloadStatus.PENDING -> stalled >= PENDING_TIMEOUT_MS
            DownloadStatus.PAUSED -> stalled >= PAUSED_TIMEOUT_MS
            DownloadStatus.RUNNING -> stalled >= RUNNING_TIMEOUT_MS ||
                (total > 0 && now - startedAt >= SLOW_CHECK_AFTER_MS && bytes < (total * SLOW_MIN_RATIO).toLong())
            DownloadStatus.SUCCESSFUL, DownloadStatus.FAILED -> false
        }
    }
}
