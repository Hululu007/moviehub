package com.example.data.network

import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Embedded 127.0.0.1 HTTP Proxy ported from moviehub/server.js (/api/play) and moviehub/music.js (/api/mplay).
 * Handles:
 * 1. HLS (.m3u8) playlist fetching with spoofed Referer/User-Agent and relative TS/Key URI rewriting.
 * 2. Audio stream proxying with HTTP Range support for anti-leech music CDNs.
 */
object LocalMediaProxyServer {
    private const val TAG = "LocalMediaProxy"
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    var port: Int = 0
        private set

    fun ensureStarted() {
        if (port > 0 && serverSocket?.isClosed == false) return
        synchronized(this) {
            if (port > 0 && serverSocket?.isClosed == false) return
            try {
                val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = ss
                port = ss.localPort
                executor.execute {
                    while (!ss.isClosed) {
                        try {
                            val client = ss.accept()
                            executor.execute { handleClient(client) }
                        } catch (e: Exception) {
                            if (!ss.isClosed) {
                                Log.w(TAG, "Accept error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start local proxy server", e)
            }
        }
    }

    fun getVideoProxyUrl(rawUrl: String, referer: String = ""): String {
        ensureStarted()
        if (port <= 0 || !rawUrl.startsWith("http", ignoreCase = true)) return rawUrl
        val ref = referer.ifEmpty { MovieHubNetwork.guessReferer(rawUrl) }
        return "http://127.0.0.1:$port/play?url=${enc(rawUrl)}&ref=${enc(ref)}"
    }

    fun getAudioProxyUrl(rawUrl: String, referer: String = ""): String {
        ensureStarted()
        if (port <= 0 || !rawUrl.startsWith("http", ignoreCase = true)) return rawUrl
        val ref = referer.ifEmpty { MovieHubNetwork.guessReferer(rawUrl) }
        return "http://127.0.0.1:$port/mplay?u=${enc(rawUrl)}&ref=${enc(ref)}"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 20000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val pathAndQuery = parts[1]

            var rangeHeader: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(":").trim()
                }
            }

            val uri = Uri.parse("http://127.0.0.1$pathAndQuery")
            val path = uri.path ?: ""
            val out = sock.getOutputStream()

            when (path) {
                "/play" -> {
                    val targetUrl = uri.getQueryParameter("url") ?: ""
                    val ref = uri.getQueryParameter("ref") ?: ""
                    handlePlayProxy(targetUrl, ref, out)
                }
                "/mplay" -> {
                    val targetUrl = uri.getQueryParameter("u") ?: ""
                    val ref = uri.getQueryParameter("ref") ?: ""
                    handleAudioProxy(targetUrl, ref, rangeHeader, out)
                }
                else -> {
                    writeSimpleError(out, 404, "Not Found")
                }
            }
        }
    }

    private fun handlePlayProxy(targetUrl: String, ref: String, out: OutputStream) {
        if (!targetUrl.startsWith("http", ignoreCase = true)) {
            writeSimpleError(out, 400, "Invalid URL")
            return
        }
        val finalRef = ref.ifEmpty { MovieHubNetwork.guessReferer(targetUrl) }
        val req = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", MovieHubNetwork.USER_AGENT)
            .header("Accept", "*/*")
            .apply {
                if (finalRef.isNotEmpty()) header("Referer", finalRef)
            }
            .build()

        try {
            MovieHubNetwork.okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body ?: run {
                    writeSimpleError(out, 502, "Empty body")
                    return
                }
                val contentType = resp.header("Content-Type") ?: ""
                val finalUrlStr = resp.request.url.toString()
                val looksLikeM3u8ByUrl = contentType.contains("mpegurl", ignoreCase = true) ||
                    targetUrl.contains(".m3u8", ignoreCase = true) ||
                    finalUrlStr.contains(".m3u8", ignoreCase = true)

                if (looksLikeM3u8ByUrl) {
                    val bytes = body.bytes()
                    val head = String(bytes, 0, minOf(bytes.size, 32), Charsets.ISO_8859_1)
                    if (head.trimStart().startsWith("#EXTM3U")) {
                        val rewritten = rewriteM3u8(String(bytes, Charsets.UTF_8), finalUrlStr, finalRef)
                        val outBytes = rewritten.toByteArray(Charsets.UTF_8)
                        val headers = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: application/vnd.apple.mpegurl\r\n")
                            append("Content-Length: ${outBytes.size}\r\n")
                            append("Access-Control-Allow-Origin: *\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        out.write(headers.toByteArray(Charsets.UTF_8))
                        out.write(outBytes)
                        out.flush()
                        return
                    } else {
                        // Stream raw bytes
                        val headers = buildString {
                            append("HTTP/1.1 ${resp.code} OK\r\n")
                            append("Content-Type: ${contentType.ifEmpty { "video/mp2t" }}\r\n")
                            append("Content-Length: ${bytes.size}\r\n")
                            append("Access-Control-Allow-Origin: *\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        out.write(headers.toByteArray(Charsets.UTF_8))
                        out.write(bytes)
                        out.flush()
                        return
                    }
                }

                // Stream binary segment (.ts / .mp4 / key) directly with 64KB buffer
                val contentLen = body.contentLength()
                val headers = buildString {
                    append("HTTP/1.1 ${resp.code} OK\r\n")
                    append("Content-Type: ${contentType.ifEmpty { "video/mp2t" }}\r\n")
                    if (contentLen >= 0) append("Content-Length: $contentLen\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(headers.toByteArray(Charsets.UTF_8))
                body.byteStream().copyTo(out, 64 * 1024)
                out.flush()
            }
        } catch (e: Exception) {
            writeSimpleError(out, 502, e.message ?: "Proxy Error")
        }
    }

    private fun rewriteM3u8(rawText: String, baseUrlStr: String, ref: String): String {
        val baseUri = try { URI(baseUrlStr) } catch (e: Exception) { null }
        val refFinal = ref.ifEmpty { MovieHubNetwork.guessReferer(baseUrlStr) }
        val keyUriRegex = Regex("""URI="([^"]+)"""")

        return rawText.lineSequence().map { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                line
            } else if (trimmed.startsWith("#")) {
                keyUriRegex.replace(line) { matchResult ->
                    val uriVal = matchResult.groupValues[1]
                    val abs = resolveAbsoluteUrl(baseUri, uriVal)
                    """URI="${getVideoProxyUrl(abs, refFinal)}""""
                }
            } else {
                val abs = resolveAbsoluteUrl(baseUri, trimmed)
                // Only proxy nested .m3u8 manifests; let .ts/.mp4 segments stream directly from CDN for maximum throughput
                if (abs.substringBefore("?").endsWith(".m3u8", ignoreCase = true)) {
                    getVideoProxyUrl(abs, refFinal)
                } else {
                    abs
                }
            }
        }.joinToString("\n")
    }

    private fun resolveAbsoluteUrl(baseUri: URI?, relativeOrAbs: String): String {
        if (relativeOrAbs.startsWith("http://", ignoreCase = true) ||
            relativeOrAbs.startsWith("https://", ignoreCase = true)
        ) {
            return relativeOrAbs
        }
        return try {
            baseUri?.resolve(relativeOrAbs)?.toString() ?: relativeOrAbs
        } catch (e: Exception) {
            relativeOrAbs
        }
    }

    private fun handleAudioProxy(targetUrl: String, ref: String, rangeHeader: String?, out: OutputStream) {
        if (!targetUrl.startsWith("http", ignoreCase = true)) {
            writeSimpleError(out, 400, "Invalid audio URL")
            return
        }
        val finalRef = ref.ifEmpty { MovieHubNetwork.guessReferer(targetUrl) }
        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", MovieHubNetwork.USER_AGENT)
            .header("Accept", "*/*")
        if (finalRef.isNotEmpty()) reqBuilder.header("Referer", finalRef)
        if (!rangeHeader.isNullOrEmpty()) reqBuilder.header("Range", rangeHeader)

        try {
            MovieHubNetwork.okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body ?: run {
                    writeSimpleError(out, 502, "Empty audio body")
                    return
                }
                val code = resp.code
                val statusText = if (code == 206) "Partial Content" else "OK"
                val ct = resp.header("Content-Type") ?: guessAudioContentType(targetUrl)
                val cl = resp.header("Content-Length")
                val cr = resp.header("Content-Range")
                val headers = buildString {
                    append("HTTP/1.1 $code $statusText\r\n")
                    append("Content-Type: $ct\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    if (!cl.isNullOrEmpty()) append("Content-Length: $cl\r\n")
                    if (!cr.isNullOrEmpty()) append("Content-Range: $cr\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(headers.toByteArray(Charsets.UTF_8))
                body.byteStream().copyTo(out, 16 * 1024)
                out.flush()
            }
        } catch (e: Exception) {
            writeSimpleError(out, 502, e.message ?: "Audio Proxy Error")
        }
    }

    private fun guessAudioContentType(u: String): String {
        val lower = u.substringBefore("?").lowercase()
        return when {
            lower.endsWith(".flac") -> "audio/flac"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".ogg") || lower.endsWith(".oga") -> "audio/ogg"
            lower.endsWith(".wav") -> "audio/wav"
            else -> "audio/mpeg"
        }
    }

    private fun writeSimpleError(out: OutputStream, code: Int, msg: String) {
        try {
            val bytes = msg.toByteArray(Charsets.UTF_8)
            val resp = "HTTP/1.1 $code Error\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            out.write(resp.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        } catch (_: Exception) {
        }
    }
}
