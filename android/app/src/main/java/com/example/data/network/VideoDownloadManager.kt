package com.example.data.network

import android.content.Context
import android.net.Uri
import com.example.data.local.LocalDataRepository
import com.example.data.local.VideoDownloadEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-threaded HLS (.m3u8 -> local .ts segments + offline index.m3u8) and direct .mp4 Video Download Manager.
 * Stores all downloaded videos in app-specific external storage (`getExternalFilesDir("moviehub_downloads")`)
 * requiring zero runtime storage permissions and supporting full offline playback via ExoPlayer.
 */
object VideoDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun buildTaskId(sourceId: String, vodId: String, epIndex: Int, epTitle: String): String {
        val safeVod = vodId.ifEmpty { "vod" }
        val safeTitle = epTitle.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
        return "${sourceId}_${safeVod}_${epIndex}_$safeTitle"
    }

    fun getDownloadsRootDir(context: Context): File {
        val base = context.getExternalFilesDir("moviehub_downloads")
            ?: File(context.filesDir, "moviehub_downloads")
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun isTaskRunning(taskId: String): Boolean = activeJobs[taskId]?.isActive == true

    fun startOrResumeDownload(
        context: Context,
        repository: LocalDataRepository,
        entity: VideoDownloadEntity
    ) {
        val taskId = entity.taskId
        if (activeJobs[taskId]?.isActive == true) return

        val appContext = context.applicationContext
        val job = scope.launch {
            val taskDir = File(getDownloadsRootDir(appContext), taskId)
            if (!taskDir.exists()) taskDir.mkdirs()

            var currentState = entity.copy(
                status = "DOWNLOADING",
                errorMessage = ""
            )
            repository.upsertVideoDownload(currentState)

            try {
                val urlLower = entity.remoteUrl.substringBefore("?").lowercase()
                val isMp4 = urlLower.endsWith(".mp4") || urlLower.endsWith(".mkv") || urlLower.endsWith(".webm")
                if (isMp4) {
                    downloadDirectVideoFile(repository, currentState, taskDir)
                } else {
                    downloadHlsM3u8Stream(repository, currentState, taskDir)
                }
            } catch (ce: CancellationException) {
                val latest = repository.getVideoDownloadById(taskId) ?: currentState
                if (latest.status == "DOWNLOADING") {
                    repository.upsertVideoDownload(latest.copy(status = "PAUSED", speedBytesPerSec = 0L))
                }
                throw ce
            } catch (e: Exception) {
                val latest = repository.getVideoDownloadById(taskId) ?: currentState
                repository.upsertVideoDownload(
                    latest.copy(
                        status = "FAILED",
                        speedBytesPerSec = 0L,
                        errorMessage = e.message ?: "下载中断"
                    )
                )
            } finally {
                activeJobs.remove(taskId)
            }
        }
        activeJobs[taskId] = job
    }

    fun pauseDownload(repository: LocalDataRepository, taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        scope.launch {
            val existing = repository.getVideoDownloadById(taskId) ?: return@launch
            if (existing.status == "DOWNLOADING" || existing.status == "QUEUED") {
                repository.upsertVideoDownload(
                    existing.copy(status = "PAUSED", speedBytesPerSec = 0L)
                )
            }
        }
    }

    fun deleteDownload(context: Context, repository: LocalDataRepository, taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        val appContext = context.applicationContext
        scope.launch {
            repository.deleteVideoDownload(taskId)
            try {
                val taskDir = File(getDownloadsRootDir(appContext), taskId)
                if (taskDir.exists()) {
                    taskDir.deleteRecursively()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun clearAllDownloads(context: Context, repository: LocalDataRepository) {
        activeJobs.keys.toList().forEach { id ->
            activeJobs.remove(id)?.cancel()
        }
        val appContext = context.applicationContext
        scope.launch {
            repository.clearAllVideoDownloads()
            try {
                val root = getDownloadsRootDir(appContext)
                if (root.exists()) {
                    root.deleteRecursively()
                    root.mkdirs()
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun downloadDirectVideoFile(
        repository: LocalDataRepository,
        initial: VideoDownloadEntity,
        taskDir: File
    ) = withContext(Dispatchers.IO) {
        val outFile = File(taskDir, "video.mp4")
        val existingLen = if (outFile.exists()) outFile.length() else 0L
        val reqBuilder = Request.Builder()
            .url(initial.remoteUrl)
            .header("User-Agent", MovieHubNetwork.USER_AGENT)
        if (existingLen > 0L) {
            reqBuilder.header("Range", "bytes=$existingLen-")
        }

        MovieHubNetwork.okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            val append = resp.code == 206 && existingLen > 0L
            val contentLen = resp.body?.contentLength() ?: -1L
            val totalExpected = if (contentLen > 0) {
                if (append) existingLen + contentLen else contentLen
            } else -1L

            var downloaded = if (append) existingLen else 0L
            var lastReportTime = System.currentTimeMillis()
            var bytesSinceLastReport = 0L

            val input = resp.body?.byteStream() ?: throw IllegalStateException("空响应流")
            FileOutputStream(outFile, append).use { fos ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buf)
                    if (read <= 0) break
                    fos.write(buf, 0, read)
                    downloaded += read
                    bytesSinceLastReport += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastReportTime
                    if (elapsed >= 700L) {
                        val speed = (bytesSinceLastReport * 1000L) / elapsed.coerceAtLeast(1L)
                        val pct = if (totalExpected > 0L) {
                            ((downloaded * 100L) / totalExpected).toInt().coerceIn(1, 99)
                        } else 50
                        repository.upsertVideoDownload(
                            initial.copy(
                                status = "DOWNLOADING",
                                progressPercent = pct,
                                downloadedSegments = 1,
                                totalSegments = 1,
                                downloadedBytes = downloaded,
                                speedBytesPerSec = speed
                            )
                        )
                        lastReportTime = now
                        bytesSinceLastReport = 0L
                    }
                }
                fos.flush()
            }

            val finalUri = Uri.fromFile(outFile).toString()
            repository.upsertVideoDownload(
                initial.copy(
                    status = "COMPLETED",
                    localFilePath = finalUri,
                    progressPercent = 100,
                    downloadedSegments = 1,
                    totalSegments = 1,
                    downloadedBytes = downloaded,
                    speedBytesPerSec = 0L
                )
            )
        }
    }

    private data class HlsSegmentTask(
        val index: Int,
        val remoteUrl: String,
        val localFileName: String
    )

    private suspend fun downloadHlsM3u8Stream(
        repository: LocalDataRepository,
        initial: VideoDownloadEntity,
        taskDir: File
    ) = withContext(Dispatchers.IO) {
        // 1. Fetch master or media .m3u8 manifest
        var currentM3u8Url = initial.remoteUrl
        var m3u8Text = MovieHubNetwork.getString(currentM3u8Url, retries = 1)
        if (!m3u8Text.contains("#EXTM3U", ignoreCase = true)) {
            throw IllegalStateException("非标准 M3U8 清单格式")
        }

        // 2. If master playlist (#EXT-X-STREAM-INF), resolve nested media playlist
        if (m3u8Text.contains("#EXT-X-STREAM-INF", ignoreCase = true)) {
            val baseUri = try { URI(currentM3u8Url) } catch (_: Exception) { null }
            val subLine = m3u8Text.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            if (!subLine.isNullOrEmpty()) {
                currentM3u8Url = resolveUrl(baseUri, subLine)
                m3u8Text = MovieHubNetwork.getString(currentM3u8Url, retries = 1)
            }
        }

        val mediaBaseUri = try { URI(currentM3u8Url) } catch (_: Exception) { null }
        val keyUriRegex = Regex("""URI="([^"]+)"""")
        val segmentTasks = mutableListOf<HlsSegmentTask>()
        val keyTasks = mutableMapOf<String, String>() // remoteKeyUrl -> localKeyFileName
        val rewrittenLines = mutableListOf<String>()
        var segCounter = 0
        var keyCounter = 0

        for (rawLine in m3u8Text.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                rewrittenLines.add(rawLine)
                continue
            }
            if (trimmed.startsWith("#")) {
                val replacedTag = keyUriRegex.replace(trimmed) { match ->
                    val rawKeyUri = match.groupValues[1]
                    val absKeyUrl = resolveUrl(mediaBaseUri, rawKeyUri)
                    val localKeyName = keyTasks.getOrPut(absKeyUrl) {
                        keyCounter++
                        "key_$keyCounter.key"
                    }
                    val localKeyFile = File(taskDir, localKeyName)
                    """URI="${Uri.fromFile(localKeyFile)}""""
                }
                rewrittenLines.add(replacedTag)
            } else {
                val absSegUrl = resolveUrl(mediaBaseUri, trimmed)
                segCounter++
                val localSegName = String.format("seg_%05d.ts", segCounter)
                val localSegFile = File(taskDir, localSegName)
                segmentTasks.add(
                    HlsSegmentTask(
                        index = segCounter,
                        remoteUrl = absSegUrl,
                        localFileName = localSegName
                    )
                )
                rewrittenLines.add(Uri.fromFile(localSegFile).toString())
            }
        }

        if (segmentTasks.isEmpty()) {
            throw IllegalStateException("M3U8 清单未包含有效视频分片")
        }

        // 3. Download any AES-128 keys first
        for ((remoteKeyUrl, localKeyName) in keyTasks) {
            coroutineContext.ensureActive()
            val keyFile = File(taskDir, localKeyName)
            if (!keyFile.exists() || keyFile.length() == 0L) {
                downloadSmallBinary(remoteKeyUrl, keyFile)
            }
        }

        // 4. Download segments concurrently (4 parallel workers) with resume support
        val totalSegs = segmentTasks.size
        val completedSegs = AtomicInteger(0)
        val totalBytes = AtomicLong(0L)
        val bytesInWindow = AtomicLong(0L)
        val lastWindowStart = AtomicLong(System.currentTimeMillis())
        val currentSpeed = AtomicLong(0L)

        // Pre-count already downloaded segments (for resume support)
        segmentTasks.forEach { seg ->
            val f = File(taskDir, seg.localFileName)
            if (f.exists() && f.length() > 64L) {
                completedSegs.incrementAndGet()
                totalBytes.addAndGet(f.length())
            }
        }

        repository.upsertVideoDownload(
            initial.copy(
                status = "DOWNLOADING",
                totalSegments = totalSegs,
                downloadedSegments = completedSegs.get(),
                progressPercent = ((completedSegs.get() * 100) / totalSegs).coerceIn(0, 99),
                downloadedBytes = totalBytes.get()
            )
        )

        val semaphore = Semaphore(4)
        coroutineScope {
            segmentTasks.map { seg ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        coroutineContext.ensureActive()
                        val targetFile = File(taskDir, seg.localFileName)
                        if (!targetFile.exists() || targetFile.length() <= 64L) {
                            val bytesWritten = downloadSegmentWithRetry(seg.remoteUrl, targetFile)
                            totalBytes.addAndGet(bytesWritten)
                            val winBytes = bytesInWindow.addAndGet(bytesWritten)
                            val done = completedSegs.incrementAndGet()

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastWindowStart.get()
                            if (elapsed >= 650L || done == totalSegs) {
                                val spd = (winBytes * 1000L) / elapsed.coerceAtLeast(1L)
                                currentSpeed.set(spd)
                                lastWindowStart.set(now)
                                bytesInWindow.set(0L)
                                val pct = ((done * 100) / totalSegs).coerceIn(1, 99)
                                repository.upsertVideoDownload(
                                    initial.copy(
                                        status = "DOWNLOADING",
                                        progressPercent = pct,
                                        downloadedSegments = done,
                                        totalSegments = totalSegs,
                                        downloadedBytes = totalBytes.get(),
                                        speedBytesPerSec = spd
                                    )
                                )
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        // 5. Write local offline index.m3u8 manifest
        val localM3u8File = File(taskDir, "index.m3u8")
        localM3u8File.writeText(rewrittenLines.joinToString("\n"), Charsets.UTF_8)
        val localUriStr = Uri.fromFile(localM3u8File).toString()

        repository.upsertVideoDownload(
            initial.copy(
                status = "COMPLETED",
                localFilePath = localUriStr,
                progressPercent = 100,
                downloadedSegments = totalSegs,
                totalSegments = totalSegs,
                downloadedBytes = totalBytes.get(),
                speedBytesPerSec = 0L,
                errorMessage = ""
            )
        )
    }

    private fun downloadSmallBinary(url: String, targetFile: File) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", MovieHubNetwork.USER_AGENT)
            .build()
        MovieHubNetwork.okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("密钥下载失败 HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: throw IllegalStateException("密钥内容为空")
            targetFile.writeBytes(bytes)
        }
    }

    private fun downloadSegmentWithRetry(url: String, targetFile: File): Long {
        var lastErr: Exception? = null
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        for (attempt in 0..2) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", MovieHubNetwork.USER_AGENT)
                    .build()
                MovieHubNetwork.okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IllegalStateException("空分片")
                    var count = 0L
                    FileOutputStream(tempFile).use { fos ->
                        val buf = ByteArray(32 * 1024)
                        val stream = body.byteStream()
                        while (true) {
                            val r = stream.read(buf)
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                            count += r
                        }
                        fos.flush()
                    }
                    if (count <= 0L) throw IllegalStateException("分片字节为0")
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                    return count
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: IllegalStateException("分片下载失败")
    }

    private fun resolveUrl(baseUri: URI?, relOrAbs: String): String {
        if (relOrAbs.startsWith("http://", ignoreCase = true) ||
            relOrAbs.startsWith("https://", ignoreCase = true)
        ) {
            return relOrAbs
        }
        return try {
            baseUri?.resolve(relOrAbs)?.toString() ?: relOrAbs
        } catch (_: Exception) {
            relOrAbs
        }
    }
}
