package com.huangder.lumibooks.dictionary

import android.app.DownloadManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.SystemClock
import android.util.AtomicFile
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.huangder.lumibooks.util.RemoteMirror
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = File(context.noBackupFilesDir, "dictionaries")
    private val stateFile = AtomicFile(File(root, "state.json"))
    private val mutex = Mutex()
    private val refreshMutex = Mutex()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val ready = CompletableDeferred<Unit>()
    private val started = AtomicBoolean(false)
    private var clearing = false
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).followRedirects(false).build()
    private val _state = MutableStateFlow(DictionaryState())
    val state: StateFlow<DictionaryState> = _state.asStateFlow()
    private var catalog = emptyList<DictionaryDescriptor>()
    private val installed = linkedMapOf<String, InstalledDictionary>()
    private val tasks = linkedMapOf<String, DictionaryTaskState>()
    private val progress = mutableMapOf<String, DictionaryDownload>()
    @Volatile private lateinit var policy: DictionaryFilterPolicy

    init {
        scope.launch {
            try {
                root.mkdirs()
                policy = DictionaryFilterPolicy.fromJson(JSONObject(context.assets.open("dictionaries/filter-policy.json").bufferedReader().use { it.readText() }))
                catalog = parseCatalog(context.assets.open("dictionaries/catalog.json").bufferedReader().use { it.readText() })
                runCatching { parseCatalog(File(root, "catalog.json").readText()) }.getOrNull()?.let { catalog = it }
                runCatching { DictionaryFilterPolicy.fromJson(JSONObject(File(root, "filter-policy.json").readText())) }
                    .getOrNull()?.takeIf { it.version >= policy.version }?.let { policy = it }
                if (stateFile.baseFile.exists()) {
                    val saved = runCatching { JSONObject(stateFile.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
                    if (saved == null) _state.value = _state.value.copy(error = "词典安装记录损坏，请重新下载词典或反馈")
                    saved?.optJSONArray("installed")?.objects()?.forEach { obj ->
                        runCatching {
                            val d = DictionaryDescriptor.fromJson(obj.getJSONObject("descriptor"))
                            val directory = obj.getString("directory")
                            require(directory.matches(Regex("[a-z0-9-]+")))
                            if (File(root, "$directory/dictionary.sqlite").isFile) installed[d.id] = InstalledDictionary(d, obj.getBoolean("enabled"), obj.getInt("order"), directory)
                        }
                    }
                    saved?.optJSONArray("tasks")?.objects()?.forEach { obj ->
                        runCatching {
                            val task = DictionaryTaskCodec.fromJson(obj, SystemClock.elapsedRealtime()) ?: return@forEach
                            require(task.confirmation == task.descriptor.confirmationKey && task.token.matches(Regex("[a-z0-9-]+")))
                            require(File(task.path).canonicalFile.parentFile == downloadDirectory().canonicalFile)
                            tasks[task.descriptor.id] = task
                        }
                    }
                }
                mutex.withLock { publish() }
                ready.complete(Unit)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!ready.isCompleted) ready.completeExceptionally(error)
                _state.value = _state.value.copy(error = "本地词典初始化失败，请反馈")
            }
        }
    }

    /** Start after Application.onCreate has injected the WorkManager factory. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try { ready.await() } catch (error: Exception) {
                if (error is CancellationException) throw error
                return@launch
            }
            while (true) {
                try {
                    if (mutex.withLock { tasks.isNotEmpty() }) reconcileDownloads()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    mutex.withLock { _state.value = _state.value.copy(error = "下载状态暂时不可用，将自动重试") }
                }
                delay(1500)
            }
        }
    }

    private fun parseCatalog(text: String): List<DictionaryDescriptor> {
        val json = JSONObject(text)
        require(json.getInt("formatVersion") == 1)
        val entries = json.getJSONArray("dictionaries").objects().map(DictionaryDescriptor::fromJson)
        require(entries.size <= 30 && entries.map { it.id }.distinct().size == entries.size)
        return entries
    }

    private fun persist() {
        val json = JSONObject().put("installed", JSONArray(installed.values.map {
            JSONObject().put("descriptor", it.descriptor.toJson()).put("enabled", it.enabled).put("order", it.order).put("directory", it.directory)
        })).put("tasks", JSONArray(tasks.values.map(DictionaryTaskCodec::toJson)))
        writeAtomic(stateFile, json.toString().toByteArray())
    }
    private fun publish() {
        val all = catalog + installed.values.map { it.descriptor }.filter { saved -> catalog.none { it.id == saved.id } }
        _state.value = _state.value.copy(items = all.map { d ->
            val task = tasks[d.id]
            DictionaryCatalogItem(d, installed[d.id], if (task?.error != null)
                DictionaryDownload(DictionaryDownloadPhase.FAILED, error = task.error, canRetry = task.confirmation == d.confirmationKey)
            else progress[d.id])
        }.sortedWith(compareBy<DictionaryCatalogItem> { it.installed == null }.thenBy { it.installed?.order ?: Int.MAX_VALUE }), filterVersion = policy.version)
    }

    /** Only called by an explicit settings refresh; lookup never touches the network. */
    suspend fun refreshCatalog() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
        ready.await()
        mutex.withLock { _state.value = _state.value.copy(refreshing = true, error = null) }
        try {
            val bytes = fetchSmall(CATALOG_URL, 512 * 1024)
            val parsed = parseCatalog(bytes.toString(Charsets.UTF_8))
            val filter = JSONObject(bytes.toString(Charsets.UTF_8)).optJSONObject("filter")
            var newPolicy: Pair<DictionaryFilterPolicy, ByteArray>? = null
            if (filter != null && filter.getInt("version") > policy.version) {
                require(isDictionaryDownloadUrl(filter.getString("url")))
                val policyBytes = fetchSmall(filter.getString("url"), 1024 * 1024)
                require(sha256(policyBytes) == filter.getString("sha256")) { "内容规则校验失败" }
                val next = DictionaryFilterPolicy.fromJson(JSONObject(policyBytes.toString(Charsets.UTF_8)))
                require(next.version == filter.getInt("version"))
                newPolicy = next to policyBytes
            }
            mutex.withLock {
                newPolicy?.let { (next, body) -> writeAtomic(AtomicFile(File(root, "filter-policy.json")), body); policy = next }
                writeAtomic(AtomicFile(File(root, "catalog.json")), bytes)
                catalog = parsed
                publish()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutex.withLock { _state.value = _state.value.copy(error = "目录或规则更新失败，继续使用本地版本") }
        } finally { mutex.withLock { _state.value = _state.value.copy(refreshing = false) } }
        }
    }

    /** catalog 与 filter-policy 的抓取：直连失败时按 RemoteMirror 的候选顺序兜底。 */
    private fun fetchSmall(url: String, maxBytes: Int): ByteArray =
        RemoteMirror.firstSuccess(url) { source -> fetchSmallFrom(source, maxBytes) }

    private fun fetchSmallFrom(url: String, maxBytes: Int): ByteArray {
        var next = url
        repeat(6) {
            require(RemoteMirror.isAllowed(next)) { "Untrusted dictionary source" }
            val uri = java.net.URI(next)
            client.newCall(Request.Builder().url(next).header("User-Agent", "LumiBooks-Dictionaries").build()).execute().use { response ->
                if (response.code in 300..399) {
                    next = uri.resolve(requireNotNull(response.header("Location"))).toString()
                } else {
                    require(response.isSuccessful) { "HTTP ${response.code}" }
                    return requireNotNull(response.body).byteStream().use { input ->
                        val bytes = input.readBytesBounded(maxBytes)
                        bytes
                    }
                }
            }
        }
        error("Too many redirects")
    }

    suspend fun download(descriptor: DictionaryDescriptor, confirmation: String) = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            check(!clearing) { "正在清理词典，请稍后重试" }
            require(confirmation == descriptor.confirmationKey) { "请确认下载说明" }
            val latest = catalog.firstOrNull { it.id == descriptor.id }
            require(latest?.confirmationKey == descriptor.confirmationKey && descriptor.available) { "词典版本已变化，请重新确认" }
            require(descriptor.filterVersion <= policy.version) { "请先刷新目录以更新内容规则" }
            if (tasks[descriptor.id]?.error == null && tasks.containsKey(descriptor.id)) return@withLock
            require(root.usableSpace > descriptor.installedBytes + 32L * 1024 * 1024) { "可用存储空间不足" }
            val directory = downloadDirectory()
            require(directory.usableSpace > descriptor.sizeBytes + 32L * 1024 * 1024) { "下载存储空间不足" }
            tasks.remove(descriptor.id)?.let { downloads.remove(it.downloadId); File(it.path).delete() }
            val sources = RemoteMirror.candidates(descriptor.downloadUrl)
            val token = UUID.randomUUID().toString()
            val archive = File(directory, "$token-0.zip")
            val id = downloads.enqueue(requestFor(descriptor, sources.first(), archive))
            val now = SystemClock.elapsedRealtime()
            tasks[descriptor.id] = DictionaryTaskState(
                descriptor = descriptor, downloadId = id, path = archive.path, token = token,
                confirmation = confirmation, sources = sources, sourceIndex = 0,
                startedAt = now, lastProgressAt = now
            )
            try { persist() } catch (error: Exception) { tasks.remove(descriptor.id); downloads.remove(id); throw error }
            progress[descriptor.id] = DictionaryDownload(DictionaryDownloadPhase.DOWNLOADING, total = descriptor.sizeBytes)
            publish()
            enqueueReconcile()
        }
    }

    suspend fun retry(id: String) {
        ready.await()
        val task = mutex.withLock { tasks[id] } ?: return
        download(task.descriptor, task.confirmation)
    }

    suspend fun cancel(id: String) = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            val task = tasks.remove(id)
            try { persist() } catch (error: Exception) {
                task?.let { tasks[id] = it }; throw error
            }
            progress.remove(id); publish()
            task?.let {
                WorkManager.getInstance(context).cancelUniqueWork("dictionary-install-${it.token}")
                downloads.remove(it.downloadId); File(it.path).delete()
            }
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        ready.await(); mutex.withLock {
            val old = installed[id] ?: return@withLock
            installed[id] = old.copy(enabled = enabled)
            try { persist() } catch (error: Exception) { installed[id] = old; throw error }
            publish()
        }
    }
    suspend fun move(id: String, delta: Int) = withContext(Dispatchers.IO) {
        ready.await(); mutex.withLock {
            val ordered = installed.values.sortedBy { it.order }.toMutableList()
            val index = ordered.indexOfFirst { it.descriptor.id == id }
            val target = index + delta
            if (index >= 0 && target in ordered.indices) {
                val old = installed.toMap()
                java.util.Collections.swap(ordered, index, target)
                ordered.forEachIndexed { i, it -> installed[it.descriptor.id] = it.copy(order = i) }
                try { persist() } catch (error: Exception) { installed.clear(); installed.putAll(old); throw error }
                publish()
            }
        }
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        cancel(id)
        locks.getOrPut(id) { Mutex() }.withLock {
            mutex.withLock {
                val old = installed.remove(id)
                try { persist() } catch (error: Exception) { old?.let { installed[id] = it }; throw error }
                publish()
                old?.let { File(root, it.directory).deleteRecursively() }
            }
        }
    }
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock { clearing = true }
        try {
            val ids = mutex.withLock { (tasks.keys + installed.keys).toSet() }
            ids.forEach { delete(it) }
            mutex.withLock {
                root.listFiles()?.filter { it.isDirectory && (it.name.startsWith("stage-") || it.name.startsWith("installed-")) }
                    ?.forEach { it.deleteRecursively() }
                context.getExternalFilesDir(null)?.let { File(it, "dictionary-downloads").deleteRecursively() }
            }
        } finally { mutex.withLock { clearing = false } }
    }
    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        ready.await()
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() } +
            (context.getExternalFilesDir(null)?.let { File(it, "dictionary-downloads").walkTopDown().filter { f -> f.isFile }.sumOf { f -> f.length() } } ?: 0L)
    }
    suspend fun licenseText(id: String): String = withContext(Dispatchers.IO) {
        ready.await()
        locks.getOrPut(id) { Mutex() }.withLock {
            val info = mutex.withLock { installed[id] } ?: return@withLock ""
            File(root, "${info.directory}/licenses.txt").readText()
        }
    }

    suspend fun lookup(query: String): DictionaryLookupResult = withContext(Dispatchers.IO) {
        ready.await()
        val key = normalizeDictionaryKey(query)
        if (key.isEmpty() || key.length > 256) return@withContext DictionaryLookupResult(filterVersion = policy.version)
        val activePolicy = policy
        val list = mutex.withLock { installed.values.filter { it.enabled }.sortedBy { it.order } }
        if (activePolicy.isQueryBlocked(key)) {
            return@withContext DictionaryLookupResult(
                enabledCount = list.size,
                filterVersion = activePolicy.version,
                queryBlocked = true
            )
        }
        val results = mutableListOf<DictionaryResult>()
        val failures = mutableListOf<String>()
        var filtered = false
        for (info in list) {
            currentCoroutineContext().ensureActive()
            try {
                locks.getOrPut(info.descriptor.id) { Mutex() }.withLock {
                    val current = mutex.withLock { installed[info.descriptor.id] } ?: return@withLock
                    if (!current.enabled) return@withLock
                    SQLiteDatabase.openDatabase(File(root, "${current.directory}/dictionary.sqlite").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        var dictionaryFiltered = false
                        db.rawQuery("SELECT 1 FROM blocked_keys WHERE hash=? LIMIT 1", arrayOf(sha256(key.toByteArray()))).use { dictionaryFiltered = it.moveToFirst() }
                        val entries = mutableListOf<DictionaryEntry>()
                        db.rawQuery("SELECT e.payload FROM aliases a JOIN entries e ON e.id=a.entry_id WHERE a.key=? ORDER BY e.id", arrayOf(key)).use { cursor ->
                            while (cursor.moveToNext()) {
                                currentCoroutineContext().ensureActive()
                                val result = activePolicy.apply(current.descriptor.id, DictionaryEntry.fromJson(JSONObject(cursor.getString(0))))
                                dictionaryFiltered = dictionaryFiltered || result.filtered
                                result.entry?.let(entries::add)
                            }
                        }
                        filtered = filtered || dictionaryFiltered
                        if (entries.isNotEmpty()) results += DictionaryResult(current.descriptor, entries, dictionaryFiltered)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failures += info.descriptor.name
            }
        }
        DictionaryLookupResult(results, filtered, failures, list.size, activePolicy.version)
    }

    suspend fun getEntry(dictionaryId: String, entryId: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        ready.await()
        locks.getOrPut(dictionaryId) { Mutex() }.withLock {
            val info = mutex.withLock { installed[dictionaryId] } ?: return@withLock null
            if (!info.enabled) return@withLock null
            SQLiteDatabase.openDatabase(File(root, "${info.directory}/dictionary.sqlite").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT payload FROM entries WHERE id=?", arrayOf(entryId)).use { cursor ->
                    if (!cursor.moveToFirst()) null else policy.apply(dictionaryId, DictionaryEntry.fromJson(JSONObject(cursor.getString(0)))).entry
                }
            }
        }
    }

    suspend fun reconcileDownloads(): Boolean = withContext(Dispatchers.IO) {
        ready.await()
        val snapshot = mutex.withLock { tasks.values.toList() }
        var pending = false
        for (task in snapshot) {
            if (task.error != null) continue
            try {
                downloads.query(DownloadManager.Query().setFilterById(task.downloadId)).use { cursor ->
                    if (cursor == null || !cursor.moveToFirst()) { fail(task, "下载任务已失效，请重试"); return@use }
                    val status = when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
                        DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                        DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                        DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
                        else -> DownloadStatus.FAILED
                    }
                    val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val now = SystemClock.elapsedRealtime()
                    when {
                        // 反代返回 200 + HTML 错误页时 DownloadManager 会一路写下去：超过声明大小即判该源不可用
                        status != DownloadStatus.SUCCESSFUL && bytes > task.descriptor.sizeBytes -> {
                            pending = true
                            switchOrFail(task, "下载内容异常", "下载内容与词库不符，请稍后重试")
                        }
                        status == DownloadStatus.SUCCESSFUL -> {
                            mutex.withLock { if (tasks[task.descriptor.id]?.token == task.token) { progress[task.descriptor.id] = DictionaryDownload(DictionaryDownloadPhase.INSTALLING); publish() } }
                            WorkManager.getInstance(context).enqueueUniqueWork("dictionary-install-${task.token}", ExistingWorkPolicy.KEEP,
                                OneTimeWorkRequestBuilder<DictionaryInstallWorker>().setInputData(workDataOf("dictionaryId" to task.descriptor.id, "token" to task.token)).build())
                        }
                        status == DownloadStatus.FAILED -> {
                            pending = true
                            switchOrFail(task, "下载失败（$reason）", "下载失败（$reason），所有下载源均不可用，请检查网络后重试")
                        }
                        // 1.0.0 的「0B 无反应」：PENDING / 长时间零增长都不再无限挂起
                        DictionaryDownloadWatchdog.shouldSwitch(
                            status, bytes, task.descriptor.sizeBytes, task.startedAt, task.lastProgressAt, now
                        ) -> {
                            pending = true
                            switchOrFail(task, "下载源无响应", "所有下载源均不可用，请检查网络后重试")
                        }
                        else -> {
                            pending = true
                            mutex.withLock {
                                if (tasks[task.descriptor.id]?.token == task.token) {
                                    if (bytes > task.progressBytes) { task.progressBytes = bytes; task.lastProgressAt = now }
                                    progress[task.descriptor.id] = DictionaryDownload(
                                        if (status == DownloadStatus.PAUSED) DictionaryDownloadPhase.PAUSED else DictionaryDownloadPhase.DOWNLOADING,
                                        bytes, task.descriptor.sizeBytes,
                                        // 已在备用源上时持续标注，避免换源后进度归零看起来像没反应
                                        note = if (task.sourceIndex > 0) "备用源 ${task.sourceIndex + 1}/${task.sources.size}" else null
                                    )
                                    publish()
                                }
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                fail(task, "无法读取下载状态，请重试")
            }
        }
        pending
    }

    /** 换源；没有备用源时清掉系统下载任务与半截文件并给出最终错误。 */
    private suspend fun switchOrFail(task: DictionaryTaskState, note: String, finalMessage: String) {
        if (switchSource(task, task.sourceIndex, note)) return
        downloads.remove(task.downloadId)
        File(task.path).delete()
        fail(task, finalMessage)
    }

    internal suspend fun install(id: String, token: String): Boolean = withContext(Dispatchers.IO) {
        ready.await()
        locks.getOrPut(id) { Mutex() }.withLock installLock@ {
            val task = mutex.withLock { tasks[id]?.takeIf { it.token == token } } ?: return@installLock true
            val stage = File(root, "stage-$token")
            try {
                stage.deleteRecursively()
                DictionaryPackageInstaller.extract(File(task.path), stage, task.descriptor)
                currentCoroutineContext().ensureActive()
                mutex.withLock commitLock@ {
                    if (tasks[id]?.token != token) return@commitLock
                    val finalName = "installed-$token"
                    val destination = File(root, finalName)
                    require(stage.renameTo(destination)) { "词库安装失败" }
                    val old = installed[id]
                    installed[id] = InstalledDictionary(task.descriptor, old?.enabled ?: true, old?.order ?: installed.size, finalName)
                    tasks.remove(id); progress.remove(id)
                    try { persist() } catch (error: Exception) {
                        if (old == null) installed.remove(id) else installed[id] = old
                        tasks[id] = task
                        destination.deleteRecursively()
                        throw error
                    }
                    publish()
                    old?.let { File(root, it.directory).deleteRecursively() }
                    downloads.remove(task.downloadId)
                    File(task.path).delete()
                }
                true
            } catch (error: DictionaryIntegrityException) {
                // 反代 200 返回 HTML 错误页 / 截断内容时，DownloadManager 眼里是「成功」，
                // 只有这里的大小与 sha256 校验能识破：还有备用源就换源重下（旧文件由 switchSource 清理）。
                if (!switchSource(task, task.sourceIndex, "下载内容校验失败")) {
                    fail(task, "所有下载源返回的文件均校验失败，请稍后重试")
                }
                false
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                fail(task, error.message?.take(140) ?: "词库安装失败，请重试")
                false
            } finally { stage.deleteRecursively() }
        }
    }

    private suspend fun fail(task: DictionaryTaskState, message: String) = mutex.withLock {
        tasks[task.descriptor.id]?.takeIf { it.token == task.token }?.let { it.error = message; persist(); publish() }
    }

    private fun requestFor(descriptor: DictionaryDescriptor, source: String, destination: File) =
        DownloadManager.Request(Uri.parse(source))
            .setTitle(descriptor.name).setDescription("LUMI 本地词典")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destination)).setAllowedOverRoaming(false)
            .addRequestHeader("User-Agent", "LumiBooks-Android")

    /**
     * 把任务推进到下一个下载源：删旧任务与半截文件 → 用新文件名重新入队（token 不变）。
     * 换文件名是为了彻底规避 DownloadManager 的 ERROR_FILE_ALREADY_EXISTS（旧文件删不掉时也不会卡死）。
     * @return true 已换源；false 没有备用源了，由调用方给最终错误
     */
    private suspend fun switchSource(task: DictionaryTaskState, expectedIndex: Int, note: String): Boolean =
        mutex.withLock {
            // 并发防线：scope 轮询与 Worker 可能同时进入，索引已变说明别人已经换过源
            val current = tasks[task.descriptor.id]
                ?.takeIf { it.token == task.token && it.sourceIndex == expectedIndex } ?: return@withLock true
            val next = current.sourceIndex + 1
            if (next >= current.sources.size) return@withLock false
            downloads.remove(current.downloadId)
            File(current.path).delete()
            val now = SystemClock.elapsedRealtime()
            val archive = File(downloadDirectory(), "${current.token}-$next.zip")
            val id = downloads.enqueue(requestFor(current.descriptor, current.sources[next], archive))
            current.downloadId = id
            current.path = archive.path
            current.sourceIndex = next
            current.progressBytes = 0L
            current.startedAt = now
            current.lastProgressAt = now
            current.error = null
            progress[current.descriptor.id] = DictionaryDownload(
                DictionaryDownloadPhase.DOWNLOADING,
                total = current.descriptor.sizeBytes,
                note = "$note，正在切换到备用源（${next + 1}/${current.sources.size}）"
            )
            runCatching { persist() }
            publish()
            enqueueReconcile()
            true
        }
    private fun downloadDirectory(): File = File(requireNotNull(context.getExternalFilesDir(null)), "dictionary-downloads").apply { mkdirs() }
    private fun enqueueReconcile() {
        WorkManager.getInstance(context).enqueueUniqueWork("dictionary-reconcile", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DictionaryReconcileWorker>().build())
    }
    companion object {
        const val CATALOG_URL = "https://raw.githubusercontent.com/a1242691089-source/wangyan/main/docs/dictionaries/catalog.json"
    }
}

internal fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
    val stream = file.startWrite()
    try { stream.write(bytes); file.finishWrite(stream) } catch (error: Exception) { file.failWrite(stream); throw error }
}
private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return out.toByteArray()
        require(out.size() + count <= limit) { "下载内容过大" }
        out.write(buffer, 0, count)
    }
}
