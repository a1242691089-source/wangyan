package com.huangder.lumibooks.dictionary

import com.huangder.lumibooks.util.RemoteMirror
import org.json.JSONArray
import org.json.JSONObject

/**
 * 词典下载任务的持久化模型。
 *
 * 1.0.1 新增的字段（sources / sourceIndex / 时间戳）全部用 opt* 读取：
 * 1.0.0 写下的 state.json 只有 descriptor/downloadId/path/token/confirmation/error，
 * 升级后必须原样复活，不能把进行中的下载丢掉重来。
 */
internal data class DictionaryTaskState(
    val descriptor: DictionaryDescriptor,
    var downloadId: Long,
    var path: String,
    val token: String,
    val confirmation: String,
    var error: String? = null,
    val sources: List<String> = listOf(descriptor.downloadUrl),
    var sourceIndex: Int = 0,
    var progressBytes: Long = 0L,
    /** 进度最后一次变化的时间（SystemClock.elapsedRealtime） */
    var lastProgressAt: Long = 0L,
    /** 当前源开始下载的时间（SystemClock.elapsedRealtime） */
    var startedAt: Long = 0L
) {
    val currentSource: String get() = sources[sourceIndex.coerceIn(0, sources.lastIndex.coerceAtLeast(0))]
}

internal object DictionaryTaskCodec {

    fun toJson(task: DictionaryTaskState): JSONObject = JSONObject()
        .put("descriptor", task.descriptor.toJson()).put("downloadId", task.downloadId)
        .put("path", task.path).put("token", task.token).put("confirmation", task.confirmation)
        .put("error", task.error)
        .put("sources", JSONArray(task.sources)).put("sourceIndex", task.sourceIndex)
        .put("progressBytes", task.progressBytes)
        .put("lastProgressAt", task.lastProgressAt).put("startedAt", task.startedAt)

    /**
     * @param now SystemClock.elapsedRealtime()：重启后旧时间戳会大于当前值，视为失效并重置为 now，
     *            相当于给新进程一个完整的观察窗口，避免升级/重启后被看门狗立刻换源。
     */
    fun fromJson(json: JSONObject, now: Long): DictionaryTaskState? = runCatching {
        val descriptor = DictionaryDescriptor.fromJson(json.getJSONObject("descriptor"))
        val persisted = json.optJSONArray("sources")
            ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }
            .orEmpty()
            .filter { isDictionaryDownloadUrl(it) }
        // 老格式没有 sources（或被篡改成非法地址）：按 1.0.0 的语义从 downloadUrl 重建候选，
        // downloadId 与半截文件继续有效，不重下。
        val sources = persisted.filter { isDictionaryDownloadUrl(it) }
            .ifEmpty { RemoteMirror.candidates(descriptor.downloadUrl).filter { isDictionaryDownloadUrl(it) } }
            .ifEmpty { listOf(descriptor.downloadUrl) }
        DictionaryTaskState(
            descriptor = descriptor,
            downloadId = json.getLong("downloadId"),
            path = json.getString("path"),
            token = json.getString("token"),
            confirmation = json.getString("confirmation"),
            error = json.optString("error").takeIf { !json.isNull("error") && it.isNotBlank() },
            sources = sources,
            sourceIndex = json.optInt("sourceIndex", 0).coerceIn(0, sources.lastIndex),
            progressBytes = json.optLong("progressBytes", 0L),
            lastProgressAt = json.optLong("lastProgressAt", 0L).takeIf { it in 1..now } ?: now,
            startedAt = json.optLong("startedAt", 0L).takeIf { it in 1..now } ?: now
        )
    }.getOrNull()
}
