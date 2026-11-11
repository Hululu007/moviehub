package com.example.data.network

import android.net.Uri
import android.util.Base64
import com.example.data.model.CategoryNode
import com.example.data.model.CategoryTree
import com.example.data.model.DoubanSubject
import com.example.data.model.LiveTvChannel
import com.example.data.model.LxScriptSource
import com.example.data.model.MovieHubData
import com.example.data.model.MusicTrack
import com.example.data.model.ResolvedMusicUrl
import com.example.data.model.VodItem
import com.example.data.model.VodSource
import com.example.data.model.VodSourceMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Native Kotlin implementation of moviehub/server.js and moviehub/music.js APIs.
 */
object MovieHubNetwork {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"

    val sourceLatencyMap = ConcurrentHashMap<String, Long>()

    val okHttpClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        val highPerfDispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        OkHttpClient.Builder()
            .dispatcher(highPerfDispatcher)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val orig = chain.request()
                val host = orig.url.host.lowercase()
                val defaultRef = when {
                    host.contains("douban.com") || host.contains("doubanio.com") -> "https://m.douban.com/"
                    host.contains("163.com") || host.contains("126.net") -> "https://music.163.com/"
                    host.contains("qq.com") || host.contains("gtimg.cn") -> "https://y.qq.com/"
                    host.contains("kugou.com") -> "https://www.kugou.com/"
                    host.contains("kuwo.cn") -> "http://www.kuwo.cn/"
                    host.contains("migu.cn") -> "https://music.migu.cn/"
                    else -> "${orig.url.scheme}://${orig.url.host}/"
                }
                val reqBuilder = orig.newBuilder()
                    .header("User-Agent", orig.header("User-Agent") ?: USER_AGENT)
                if (orig.header("Referer") == null) {
                    reqBuilder.header("Referer", defaultRef)
                }
                chain.proceed(reqBuilder.build())
            }
            .build()
    }

    private data class CacheEntry<T>(val timestamp: Long, val data: T)
    private val doubanCache = ConcurrentHashMap<String, CacheEntry<List<DoubanSubject>>>()

    fun guessReferer(url: String): String {
        return try {
            val u = URI(url)
            "${u.scheme}://${u.Authority ?: u.host}/"
        } catch (e: Exception) {
            ""
        }
    }

    private val URI.Authority: String?
        get() = if (port > 0) "$host:$port" else host

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    suspend fun getString(url: String, referer: String? = null, retries: Int = 1): String =
        withContext(Dispatchers.IO) {
            var lastErr: Exception? = null
            for (attempt in 0..retries) {
                try {
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "*/*")
                    if (!referer.isNullOrBlank()) {
                        reqBuilder.header("Referer", referer)
                    }
                    okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                        val bodyStr = resp.body?.string() ?: ""
                        return@withContext bodyStr.removePrefix("\uFEFF").trim()
                    }
                } catch (e: Exception) {
                    lastErr = e
                    if (attempt < retries) delay(900)
                }
            }
            throw lastErr ?: IllegalStateException("Network request failed")
        }

    suspend fun getJsonObject(url: String, referer: String? = null, retries: Int = 1): JSONObject {
        val raw = getString(url, referer, retries)
        return JSONObject(raw)
    }

    private fun buildApiUrl(api: String, params: Map<String, String>): String {
        val sep = if (api.contains("?")) "&" else "?"
        val qs = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return if (qs.isEmpty()) api else "$api$sep$qs"
    }

    private fun mapVodJsonItem(v: JSONObject, s: VodSource, latencyMs: Long = 0L): VodItem {
        val playFrom = v.optString("vod_play_from")
        val playUrl = v.optString("vod_play_url")
        val lines = MovieHubData.parsePlayGroups(playFrom, playUrl)
        val name = v.optString("vod_name").trim()
        val pic = v.optString("vod_pic").trim()
        val remarks = v.optString("vod_remarks").trim()
        val content = MovieHubData.stripHtml(v.optString("vod_content"))
        val year = v.optString("vod_year").trim()
        val area = v.optString("vod_area").trim()
        val actor = v.optString("vod_actor").trim()
        val director = v.optString("vod_director").trim()
        val typeName = v.optString("type_name").trim()
        val vodId = v.opt("vod_id")?.toString() ?: ""
        val effectiveLatency = if (latencyMs > 0L) latencyMs else (sourceLatencyMap[s.id] ?: 0L)

        val match = VodSourceMatch(
            sourceId = s.id,
            sourceName = s.name,
            vodId = vodId,
            lines = lines,
            pic = pic,
            remarks = remarks,
            content = content,
            year = year,
            area = area,
            actor = actor,
            director = director,
            typeName = typeName,
            latencyMs = effectiveLatency
        )

        return VodItem(
            name = name,
            pic = pic,
            remarks = remarks,
            content = content,
            year = year,
            area = area,
            actor = actor,
            director = director,
            typeName = typeName,
            source = s.id,
            sourceName = s.name,
            vodId = vodId,
            lines = lines,
            sources = listOf(match)
        )
    }

    /* ─────────────────── 1. 豆瓣热榜 (Ported from handleDouban) ─────────────────── */

    suspend fun fetchDoubanHot(
        type: String = "movie",
        tag: String = "热门",
        limit: Int = 24,
        fallbackSources: List<VodSource> = MovieHubData.DEFAULT_VOD_SOURCES
    ): List<DoubanSubject> = withContext(Dispatchers.IO) {
        val key = "$type|$tag|$limit"
        val cached = doubanCache[key]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 15 * 60 * 1000L) {
            return@withContext cached.data
        }

        val items = java.util.Collections.synchronizedList(mutableListOf<DoubanSubject>())
        val seen = java.util.Collections.synchronizedSet(HashSet<String>())

        fun pushSubject(s: JSONObject?) {
            if (s == null) return
            val title = s.optString("title").trim()
            if (title.isEmpty() || MovieHubData.isBlockedCategoryOrTitle("", title)) return
            val normKey = MovieHubData.normalizeName(title).ifEmpty { title }
            if (!seen.add(normKey)) return
            val id = s.optString("id").ifEmpty { s.optString("uri").ifEmpty { title } }
            val coverObj = s.optJSONObject("cover")
            val picObj = s.optJSONObject("pic")
            val cover = coverObj?.optString("url")
                ?.ifEmpty { coverObj.optJSONObject("info")?.optString("url") ?: "" }
                ?.ifEmpty { picObj?.optString("normal") ?: picObj?.optString("large") ?: "" }
                ?: picObj?.optString("normal")
                ?: s.optString("cover")
            val ratingObj = s.optJSONObject("rating")
            val rate = ratingObj?.optDouble("value", 0.0) ?: s.optDouble("rate", 0.0)
            items.add(
                DoubanSubject(
                    id = id,
                    title = title,
                    rate = if (rate.isNaN()) 0.0 else rate,
                    year = s.optString("year"),
                    cover = cover,
                    url = s.optString("url"),
                    subtitle = s.optString("card_subtitle").ifEmpty { s.optString("episodes_info") }
                )
            )
        }

        val normType = when (type) {
            "tv", "short" -> "tv"
            "anime" -> if (tag.contains("剧场") || tag.contains("电影") || tag.contains("经典")) "movie" else "tv"
            "doc" -> "movie"
            else -> "movie"
        }
        val effectiveTag = when (type) {
            "anime" -> when (tag) {
                "热门", "新番", "热血", "奇幻", "日本动画" -> "日本动画"
                "剧场版", "经典动画" -> "动画"
                else -> "日本动画"
            }
            "doc" -> "纪录片"
            "short" -> "短剧"
            else -> tag
        }
        val pageOffset = when {
            type == "doc" && tag != "纪录片" -> (MovieHubData.DOUBAN_DOC_TAGS.indexOf(tag).coerceAtLeast(0)) * 8
            type == "anime" && tag !in listOf("日本动画", "剧场版") -> (MovieHubData.DOUBAN_ANIME_TAGS.indexOf(tag).coerceAtLeast(0)) * 8
            type == "short" && tag != "短剧" -> (MovieHubData.DOUBAN_SHORT_TAGS.indexOf(tag).coerceAtLeast(0)) * 6
            else -> 0
        }

        // Collection IDs on Douban Rexxar v2 that respond in <400ms
        val collectionId = when (type) {
            "doc" -> "tv_documentary"
            "anime" -> "tv_animation"
            "short" -> "tv_domestic"
            "tv" -> when (tag) {
                "国产剧" -> "tv_domestic"
                "美剧", "英剧" -> "tv_american"
                "韩剧" -> "tv_korean"
                "日剧" -> "tv_japanese"
                else -> "tv_hot"
            }
            else -> when (tag) {
                "豆瓣高分", "经典" -> "movie_top250"
                "科幻" -> "movie_scifi"
                else -> "movie_hot_gaia"
            }
        }

        // Run all fast endpoints in parallel with a 3.0s timeout so no tab ever hangs!
        coroutineScope {
            // 1. Fast j/search_subjects endpoint (skip for non-default short drama tags so VOD short drama category takes priority)
            val jobSearchSubjects = async(Dispatchers.IO) {
                if (type != "short") {
                    withTimeoutOrNull(3000L) {
                        try {
                            val u1 = "https://movie.douban.com/j/search_subjects?type=${enc(normType)}&tag=${enc(effectiveTag)}&page_limit=$limit&page_start=$pageOffset"
                            val json = getJsonObject(u1, "https://movie.douban.com/", retries = 0)
                            val subjects = json.optJSONArray("subjects") ?: JSONArray()
                            for (i in 0 until subjects.length()) {
                                pushSubject(subjects.optJSONObject(i))
                            }
                        } catch (_: Exception) {
                        }
                        if (type == "doc" && items.size < 12) {
                            try {
                                val u2 = "https://movie.douban.com/j/search_subjects?type=tv&tag=${enc("纪录片")}&page_limit=$limit&page_start=$pageOffset"
                                val json2 = getJsonObject(u2, "https://movie.douban.com/", retries = 0)
                                val subjects2 = json2.optJSONArray("subjects") ?: JSONArray()
                                for (i in 0 until subjects2.length()) {
                                    pushSubject(subjects2.optJSONObject(i))
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }

            // 2. Fast subject_collection endpoint
            val jobCollection = async(Dispatchers.IO) {
                if (type != "short") {
                    withTimeoutOrNull(3000L) {
                        try {
                            val colUrl = "https://m.douban.com/rexxar/api/v2/subject_collection/$collectionId/items?start=$pageOffset&count=$limit"
                            val json = getJsonObject(colUrl, "https://m.douban.com/", retries = 0)
                            val arr = json.optJSONArray("subject_collection_items") ?: JSONArray()
                            for (i in 0 until arr.length()) {
                                pushSubject(arr.optJSONObject(i))
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            // 3. Rexxar modules endpoint (for standard movie/tv tags)
            val jobModules = async(Dispatchers.IO) {
                if (type in listOf("movie", "tv")) {
                    withTimeoutOrNull(2800L) {
                        try {
                            val url = "https://m.douban.com/rexxar/api/v2/$normType/modules?type=${enc(normType)}&tag=${enc(effectiveTag)}&page_limit=$limit&page_start=$pageOffset"
                            val json = getJsonObject(url, "https://m.douban.com/", retries = 0)
                            val modules = json.optJSONArray("modules") ?: JSONArray()
                            for (i in 0 until modules.length()) {
                                val m = modules.optJSONObject(i) ?: continue
                                val d = m.optJSONObject("data") ?: continue
                                d.optJSONArray("subjects")?.let { arr ->
                                    for (j in 0 until arr.length()) pushSubject(arr.optJSONObject(j))
                                }
                                d.optJSONArray("subject_collection_boards")?.let { boards ->
                                    for (j in 0 until boards.length()) {
                                        val b = boards.optJSONObject(j)
                                        b?.optJSONArray("items")?.let { arr ->
                                            for (k in 0 until arr.length()) pushSubject(arr.optJSONObject(k))
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            // 4. Direct VOD Source Search & Short Drama Category Feed
            val jobVodDirect = async(Dispatchers.IO) {
                val searchKw = when {
                    type == "short" -> tag.ifEmpty { "短剧" }
                    type == "doc" && tag != "纪录片" -> tag
                    type == "doc" -> "纪录"
                    type == "anime" && tag == "国产动画" -> "动画"
                    else -> ""
                }
                if (searchKw.isNotEmpty()) {
                    withTimeoutOrNull(3000L) {
                        val fastSources = fallbackSources.filter { it.enabled }.take(4)
                        fastSources.map { src ->
                            async(Dispatchers.IO) {
                                // 4A. If type == "short" and tag is "短剧" or "爽剧", also fetch from the CMS native "短剧/爽剧" category!
                                if (type == "short" && tag in listOf("短剧", "爽剧")) {
                                    try {
                                        val listJson = getJsonObject(buildApiUrl(src.api, mapOf("ac" to "list", "pg" to "1")), src.api, retries = 0)
                                        val classArr = listJson.optJSONArray("class")
                                        var shortTypeId = 0
                                        if (classArr != null) {
                                            for (i in 0 until classArr.length()) {
                                                val c = classArr.optJSONObject(i) ?: continue
                                                val tName = c.optString("type_name")
                                                if (tName.contains("短剧") || tName.contains("爽剧") || tName.contains("微剧")) {
                                                    shortTypeId = c.optInt("type_id", 0)
                                                    if (shortTypeId > 0) break
                                                }
                                            }
                                        }
                                        if (shortTypeId > 0) {
                                            val catRes = browseCategory(src, typeId = shortTypeId.toString(), page = 1)
                                            catRes.items.forEach { vod ->
                                                val normKey = MovieHubData.normalizeName(vod.name).ifEmpty { vod.name }
                                                if (vod.pic.isNotEmpty() && seen.add(normKey)) {
                                                    items.add(
                                                        DoubanSubject(
                                                            id = "${src.id}_${vod.vodId}",
                                                            title = vod.name,
                                                            rate = 0.0,
                                                            year = vod.year.ifEmpty { vod.remarks },
                                                            cover = vod.pic,
                                                            subtitle = vod.remarks.ifEmpty { "竖屏短剧" }
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                }

                                // 4B. Keyword search across CMS source
                                try {
                                    val list = querySingleVodSource(src, searchKw)
                                    list.forEach { vod ->
                                        val normKey = MovieHubData.normalizeName(vod.name).ifEmpty { vod.name }
                                        if (vod.pic.isNotEmpty() && seen.add(normKey)) {
                                            items.add(
                                                DoubanSubject(
                                                    id = "${src.id}_${vod.vodId}",
                                                    title = vod.name,
                                                    rate = 0.0,
                                                    year = vod.year.ifEmpty { vod.remarks },
                                                    cover = vod.pic,
                                                    subtitle = vod.remarks.ifEmpty { vod.typeName }
                                                )
                                            )
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }.awaitAll()
                    }
                }
            }

            awaitAll(jobSearchSubjects, jobCollection, jobModules, jobVodDirect)
        }

        // If still empty, fallback to VOD sources so it NEVER fails
        if (items.isEmpty()) {
            val kw = when (type) {
                "short" -> "短剧"
                "doc" -> if (tag == "纪录片") "纪录" else tag
                "anime" -> "动漫"
                "tv" -> "剧"
                else -> "电影"
            }
            val fastSources = fallbackSources.filter { it.enabled }.take(3)
            coroutineScope {
                fastSources.map { src ->
                    async(Dispatchers.IO) {
                        withTimeoutOrNull(3000L) {
                            try {
                                val list = querySingleVodSource(src, kw)
                                list.forEach { vod ->
                                    val normKey = MovieHubData.normalizeName(vod.name).ifEmpty { vod.name }
                                    if (seen.add(normKey)) {
                                        items.add(
                                            DoubanSubject(
                                                id = "${src.id}_${vod.vodId}",
                                                title = vod.name,
                                                rate = 0.0,
                                                year = vod.year.ifEmpty { vod.remarks },
                                                cover = vod.pic,
                                                subtitle = vod.remarks.ifEmpty { vod.typeName }
                                            )
                                        )
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        if (items.isNotEmpty()) {
            val result = items.toList().take(limit + 6)
            doubanCache[key] = CacheEntry(System.currentTimeMillis(), result)
            return@withContext result
        }

        emptyList()
    }

    /* ─────────────────── 2. 最新入库与分类浏览 (Ported from handleLatest & handleBrowse) ─────────────────── */

    data class PaginatedVodResult(
        val srcId: String,
        val srcName: String,
        val page: Int,
        val pageCount: Int,
        val total: Int,
        val typeName: String = "",
        val items: List<VodItem>
    )

    suspend fun fetchLatestVod(
        sources: List<VodSource>,
        preferredSrcId: String = "",
        page: Int = 1
    ): PaginatedVodResult = withContext(Dispatchers.IO) {
        val enabled = sources.filter { it.enabled }.ifEmpty { MovieHubData.DEFAULT_VOD_SOURCES }
        val ordered = if (preferredSrcId.isNotEmpty()) {
            val hit = enabled.filter { it.id == preferredSrcId }
            hit + enabled.filter { it.id != preferredSrcId }
        } else {
            val pref = MovieHubData.PREFERRED_BROWSE_SOURCE_IDS.mapNotNull { id -> enabled.find { it.id == id } }
            pref + enabled.filter { s -> pref.none { it.id == s.id } }
        }

        var lastError: Exception? = null
        for (s in ordered.take(4)) {
            try {
                // Use ac=videolist so poster images (vod_pic) and play lines are returned immediately
                val url = buildApiUrl(s.api, mapOf("ac" to "videolist", "pg" to page.toString()))
                val j = getJsonObject(url, s.api, retries = 0)
                val listArr = j.optJSONArray("list") ?: JSONArray()
                val items = mutableListOf<VodItem>()
                for (i in 0 until listArr.length()) {
                    val obj = listArr.optJSONObject(i) ?: continue
                    val mapped = mapVodJsonItem(obj, s)
                    if (!MovieHubData.isBlockedCategoryOrTitle(mapped.typeName, mapped.name)) {
                        items.add(mapped)
                    }
                }
                if (items.isNotEmpty()) {
                    return@withContext PaginatedVodResult(
                        srcId = s.id,
                        srcName = s.name,
                        page = page,
                        pageCount = j.optInt("pagecount", 1),
                        total = j.optInt("total", items.size),
                        items = items
                    )
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("暂无可用片库源")
    }

    suspend fun browseCategory(
        source: VodSource,
        typeId: String = "",
        page: Int = 1,
        year: String = "",
        sortBy: String = ""
    ): PaginatedVodResult = withContext(Dispatchers.IO) {
        val params = mutableMapOf("ac" to "videolist", "pg" to page.toString())
        if (typeId.isNotEmpty()) params["t"] = typeId
        if (year.isNotEmpty()) params["year"] = year
        if (sortBy.isNotEmpty()) params["by"] = sortBy

        val url = buildApiUrl(source.api, params)
        val j = getJsonObject(url, source.api, retries = 1)
        val listArr = j.optJSONArray("list") ?: JSONArray()
        val items = mutableListOf<VodItem>()
        for (i in 0 until listArr.length()) {
            val obj = listArr.optJSONObject(i) ?: continue
            val mapped = mapVodJsonItem(obj, source)
            if (!MovieHubData.isBlockedCategoryOrTitle(mapped.typeName, mapped.name)) {
                items.add(mapped)
            }
        }
        val typeName = items.firstOrNull()?.typeName ?: ""
        PaginatedVodResult(
            srcId = source.id,
            srcName = source.name,
            page = page,
            pageCount = j.optInt("pagecount", 1).coerceAtLeast(1),
            total = j.optInt("total", items.size),
            typeName = typeName,
            items = items
        )
    }

    /* ─────────────────── 3. 多源聚合搜索与详情 (Ported from handleSearch & handleDetail) ─────────────────── */

    data class MultiSearchResult(
        val keyword: String,
        val total: Int,
        val hitSources: Int,
        val sourceCount: Int,
        val items: List<VodItem>
    )

    private suspend fun querySingleVodSource(s: VodSource, wd: String): List<VodItem> {
        val t0 = System.currentTimeMillis()
        var j = getJsonObject(buildApiUrl(s.api, mapOf("ac" to "detail", "wd" to wd)), s.api, retries = 0)
        var arr = j.optJSONArray("list") ?: JSONArray()
        if (arr.length() == 0) {
            j = getJsonObject(buildApiUrl(s.api, mapOf("ac" to "videolist", "wd" to wd)), s.api, retries = 0)
            arr = j.optJSONArray("list") ?: JSONArray()
        }
        val costMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
        sourceLatencyMap[s.id] = costMs
        val out = mutableListOf<VodItem>()
        for (i in 0 until minOf(arr.length(), 35)) {
            val obj = arr.optJSONObject(i) ?: continue
            val mapped = mapVodJsonItem(obj, s, latencyMs = costMs)
            if (mapped.lines.isNotEmpty() && !MovieHubData.isBlockedCategoryOrTitle(mapped.typeName, mapped.name)) {
                out.add(mapped)
            }
        }
        return out
    }

    /**
     * Fast-First Race Search: Fires all active VOD sources in parallel and returns the VERY FIRST
     * source that matches [keyword], without waiting for slower sources.
     */
    suspend fun searchVodFastFirst(
        keyword: String,
        allSources: List<VodSource> = MovieHubData.DEFAULT_VOD_SOURCES
    ): VodItem? = withContext(Dispatchers.IO) {
        val wd = keyword.trim()
        val normTarget = MovieHubData.normalizeName(wd)
        val activeSources = allSources.filter { it.enabled }.ifEmpty { MovieHubData.DEFAULT_VOD_SOURCES }
        val resultChannel = Channel<VodItem?>(Channel.UNLIMITED)

        coroutineScope {
            val jobs = activeSources.map { s ->
                launch(Dispatchers.IO) {
                    val found = withTimeoutOrNull(4500L) {
                        try {
                            val list = querySingleVodSource(s, wd)
                            list.find { MovieHubData.normalizeName(it.name) == normTarget }
                                ?: list.firstOrNull()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    resultChannel.send(found)
                }
            }

            var bestFallback: VodItem? = null
            repeat(activeSources.size) {
                val candidate = resultChannel.receive()
                if (candidate != null) {
                    if (MovieHubData.normalizeName(candidate.name) == normTarget) {
                        jobs.forEach { it.cancel() }
                        return@coroutineScope candidate
                    } else if (bestFallback == null) {
                        bestFallback = candidate
                    }
                }
            }
            bestFallback
        }
    }

    suspend fun searchVod(
        keyword: String,
        targetSrcId: String = "",
        allSources: List<VodSource> = MovieHubData.DEFAULT_VOD_SOURCES
    ): MultiSearchResult = coroutineScope {
        val wd = keyword.trim()
        val activeSources = if (targetSrcId.isNotEmpty()) {
            allSources.filter { it.id == targetSrcId }
        } else {
            allSources.filter { it.enabled }
        }

        val groups = activeSources.map { s ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(5200L) {
                    try {
                        querySingleVodSource(s, wd)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } ?: emptyList()
            }
        }.awaitAll()

        val merged = LinkedHashMap<String, VodItem>()
        var hitSources = 0
        for (items in groups) {
            if (items.isNotEmpty()) hitSources++
            for (it in items) {
                val key = MovieHubData.normalizeName(it.name).ifEmpty { it.name }
                val existing = merged[key]
                val firstMatch = it.sources.firstOrNull()
                val match = VodSourceMatch(
                    sourceId = it.source,
                    sourceName = it.sourceName,
                    vodId = it.vodId,
                    lines = it.lines,
                    pic = it.pic,
                    remarks = it.remarks,
                    content = it.content,
                    year = it.year,
                    area = it.area,
                    actor = it.actor,
                    director = it.director,
                    typeName = it.typeName,
                    latencyMs = firstMatch?.latencyMs ?: (sourceLatencyMap[it.source] ?: 0L)
                )
                if (existing == null) {
                    merged[key] = it.copy(sources = listOf(match))
                } else {
                    val updatedSources = if (existing.sources.any { m -> m.sourceId == match.sourceId && m.vodId == match.vodId }) {
                        existing.sources
                    } else {
                        (existing.sources + match).sortedBy { m -> if (m.latencyMs > 0L) m.latencyMs else 9999L }
                    }
                    merged[key] = existing.copy(
                        pic = existing.pic.ifEmpty { it.pic },
                        remarks = if (existing.remarks.isEmpty() || existing.remarks == "更新至") it.remarks else existing.remarks,
                        content = existing.content.ifEmpty { it.content },
                        year = existing.year.ifEmpty { it.year },
                        area = existing.area.ifEmpty { it.area },
                        actor = existing.actor.ifEmpty { it.actor },
                        director = existing.director.ifEmpty { it.director },
                        sources = updatedSources
                    )
                }
            }
        }

        val sortedItems = merged.values.sortedWith(
            compareByDescending<VodItem> { it.sources.size }
                .thenBy { it.sources.firstOrNull()?.latencyMs ?: 9999L }
                .thenBy { it.name }
        )

        MultiSearchResult(
            keyword = wd,
            total = sortedItems.size,
            hitSources = hitSources,
            sourceCount = activeSources.size,
            items = sortedItems
        )
    }

    /**
     * Probes the actual first-byte response latency (in ms) of a video stream (.m3u8 / .mp4) URL.
     */
    suspend fun probeStreamLatencyMs(streamUrl: String): Long = withContext(Dispatchers.IO) {
        if (!streamUrl.startsWith("http", ignoreCase = true)) return@withContext Long.MAX_VALUE
        val t0 = System.currentTimeMillis()
        val req = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", USER_AGENT)
            .header("Range", "bytes=0-511")
            .build()
        try {
            withTimeoutOrNull(3500L) {
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.code in 200..299) {
                        val buf = ByteArray(128)
                        resp.body?.byteStream()?.read(buf)
                        (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                    } else {
                        Long.MAX_VALUE
                    }
                }
            } ?: Long.MAX_VALUE
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    suspend fun fetchVodDetail(source: VodSource, vodId: String): VodItem = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val url = buildApiUrl(source.api, mapOf("ac" to "detail", "ids" to vodId))
        val j = getJsonObject(url, source.api, retries = 1)
        val costMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
        sourceLatencyMap[source.id] = costMs
        val arr = j.optJSONArray("list") ?: JSONArray()
        if (arr.length() == 0) throw IllegalStateException("影片不存在或已下架")
        mapVodJsonItem(arr.getJSONObject(0), source, latencyMs = costMs)
    }

    /* ─────────────────── 4. 分类树自动采样发现 (Ported from discoverTypes & fillParentNames) ─────────────────── */

    suspend fun discoverCategoryTree(
        source: VodSource,
        budgetMs: Long = 7500L
    ): CategoryTree = withContext(Dispatchers.IO) {
        val types = LinkedHashMap<Int, CategoryNode>()
        val deadline = System.currentTimeMillis() + budgetMs

        fun absorb(listArr: JSONArray?): Boolean {
            if (listArr == null || listArr.length() == 0) return false
            var added = false
            for (i in 0 until listArr.length()) {
                val v = listArr.optJSONObject(i) ?: continue
                val id = v.optInt("type_id", 0)
                val name = v.optString("type_name").trim()
                val pid = v.optInt("type_id_1", 0)
                if (id <= 0 || name.isEmpty() || MovieHubData.isBlockedCategoryOrTitle(name)) continue
                val existing = types[id]
                if (existing == null) {
                    types[id] = CategoryNode(id = id, pid = pid, name = name, count = 1)
                    added = true
                } else {
                    types[id] = existing.copy(count = existing.count + 1)
                }
            }
            return added
        }

        // Also check if top-level "class" array is provided by the CMS response
        var pagecount = 1
        try {
            val firstJson = getJsonObject(buildApiUrl(source.api, mapOf("ac" to "list", "pg" to "1")), source.api, retries = 0)
            val classArr = firstJson.optJSONArray("class")
            if (classArr != null && classArr.length() > 0) {
                for (i in 0 until classArr.length()) {
                    val c = classArr.optJSONObject(i) ?: continue
                    val id = c.optInt("type_id", 0)
                    val pid = c.optInt("type_pid", c.optInt("type_id_1", 0))
                    val name = c.optString("type_name").trim()
                    if (id > 0 && name.isNotEmpty() && !MovieHubData.isBlockedCategoryOrTitle(name)) {
                        types[id] = CategoryNode(id = id, pid = pid, name = name, count = 10)
                    }
                }
            }
            absorb(firstJson.optJSONArray("list"))
            pagecount = firstJson.optInt("pagecount", 1)
        } catch (_: Exception) {
        }

        if (types.size < 6) {
            // Stage 1: Sample videolist pages (ported from moviehub/server.js discoverTypes)
            var stale = 0
            for (pg in 1..20) {
                if (stale >= 2 || System.currentTimeMillis() > deadline) break
                try {
                    val j = getJsonObject(buildApiUrl(source.api, mapOf("ac" to "videolist", "pg" to pg.toString())), source.api, retries = 0)
                    if (pg == 1) pagecount = j.optInt("pagecount", 1)
                    val arr = j.optJSONArray("list")
                    if (arr == null || arr.length() == 0) break
                    stale = if (absorb(arr)) 0 else stale + 1
                } catch (_: Exception) {
                    break
                }
            }

            // Stage 2: Sample by discovered parent IDs
            val parentIds = types.values.map { it.pid }.filter { it > 0 }.distinct()
            for (pid in parentIds) {
                if (System.currentTimeMillis() > deadline) break
                var s2 = 0
                for (pg in 1..4) {
                    if (s2 >= 2 || System.currentTimeMillis() > deadline) break
                    try {
                        val j = getJsonObject(
                            buildApiUrl(source.api, mapOf("ac" to "videolist", "t" to pid.toString(), "pg" to pg.toString())),
                            source.api,
                            retries = 0
                        )
                        val arr = j.optJSONArray("list")
                        if (arr == null || arr.length() == 0) break
                        s2 = if (absorb(arr)) 0 else s2 + 1
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        // Fill parent category names (fillParentNames)
        val knownIds = types.keys.toSet()
        val missingPids = types.values.map { it.pid }.filter { it > 0 && !knownIds.contains(it) }.distinct()
        for (pid in missingPids) {
            if (System.currentTimeMillis() > deadline + 2000L) break
            try {
                val j = getJsonObject(
                    buildApiUrl(source.api, mapOf("ac" to "videolist", "t" to pid.toString(), "pg" to "1")),
                    source.api,
                    retries = 0
                )
                val first = j.optJSONArray("list")?.optJSONObject(0)
                val parentName = first?.optString("type_name")?.trim().orEmpty()
                if (parentName.isNotEmpty()) {
                    types[pid] = CategoryNode(id = pid, pid = 0, name = parentName, count = 0, isParent = true)
                }
            } catch (_: Exception) {
            }
        }

        MovieHubData.buildCategoryTree(
            srcId = source.id,
            srcName = source.name,
            types = types.values.toList(),
            version = 1,
            done = true
        )
    }

    /* ─────────────────── 5. 音乐五平台搜索、解析与歌词 (Multi-Tier Resilient Engine) ─────────────────── */

    suspend fun searchMusic(
        keyword: String,
        platform: String = "wy",
        page: Int = 1
    ): List<MusicTrack> = withContext(Dispatchers.IO) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return@withContext emptyList()

        val primary = try {
            when (platform) {
                "wy" -> searchWy(kw, page)
                "kw" -> searchKw(kw, page)
                "kg" -> searchKg(kw, page)
                "tx" -> searchTx(kw, page)
                "mg" -> searchMg(kw, page)
                else -> searchWy(kw, page)
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (primary.isNotEmpty()) return@withContext primary

        // Multi-source fallback chain if the primary platform was rate-limited or empty
        val fallbackOrder = listOf("kg", "wy", "kw", "itunes").filter { it != platform }
        for (fb in fallbackOrder) {
            val res = try {
                when (fb) {
                    "kg" -> searchKg(kw, page)
                    "wy" -> searchWy(kw, page)
                    "kw" -> searchKw(kw, page)
                    "itunes" -> searchItunes(kw, platform)
                    else -> emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
            if (res.isNotEmpty()) return@withContext res
        }
        emptyList()
    }

    private fun getStringWithHeaders(
        url: String,
        referer: String = "",
        cookie: String = ""
    ): String {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
        if (referer.isNotEmpty()) reqBuilder.header("Referer", referer)
        if (cookie.isNotEmpty()) reqBuilder.header("Cookie", cookie)
        okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    private suspend fun searchWy(kw: String, page: Int): List<MusicTrack> {
        val offset = (page - 1) * 20
        val wyCookie = "os=pc; appver=2.9.7; NMTID=00O8k2"

        // Endpoint 1: cloudsearch/pc
        try {
            val u = "https://music.163.com/api/cloudsearch/pc?s=${enc(kw)}&type=1&offset=$offset&limit=25"
            val raw = getStringWithHeaders(u, "https://music.163.com/", wyCookie)
            val j = JSONObject(raw)
            val songs = j.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
            val list = parseWySongs(songs)
            if (list.isNotEmpty()) return list
        } catch (_: Exception) {
        }

        // Endpoint 2: search/get/web
        try {
            val u2 = "https://music.163.com/api/search/get/web?csrf_token=&hlpretag=&hlposttag=&s=${enc(kw)}&type=1&offset=$offset&total=true&limit=25"
            val raw2 = getStringWithHeaders(u2, "https://music.163.com/", wyCookie)
            val j2 = JSONObject(raw2)
            val songs2 = j2.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
            val list2 = parseWySongs(songs2)
            if (list2.isNotEmpty()) return list2
        } catch (_: Exception) {
        }

        // Endpoint 3: Meting NetEase Search Mirror
        try {
            val u3 = "https://api.injahow.cn/meting/?server=netease&type=search&id=${enc(kw)}"
            val raw3 = getString(u3, "https://music.163.com/", retries = 0)
            val arr = JSONArray(raw3)
            val list3 = mutableListOf<MusicTrack>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val urlField = o.optString("url")
                val idFromUrl = Regex("id=(\\d+)").find(urlField)?.groupValues?.get(1)
                    ?: o.optString("id")
                list3.add(
                    MusicTrack(
                        id = idFromUrl,
                        platform = "wy",
                        name = o.optString("name").ifEmpty { o.optString("title") },
                        singer = o.optString("artist").ifEmpty { o.optString("author") },
                        pic = o.optString("pic").ifEmpty { o.optString("cover") },
                        directUrl = urlField.takeIf { it.startsWith("http") }.orEmpty()
                    )
                )
            }
            if (list3.isNotEmpty()) return list3
        } catch (_: Exception) {
        }

        return emptyList()
    }

    private fun parseWySongs(songs: JSONArray): List<MusicTrack> {
        val list = mutableListOf<MusicTrack>()
        for (i in 0 until songs.length()) {
            val s = songs.optJSONObject(i) ?: continue
            val idStr = s.opt("id")?.toString().orEmpty()
            if (idStr.isEmpty()) continue
            val ar = s.optJSONArray("ar") ?: s.optJSONArray("artists") ?: JSONArray()
            val singers = mutableListOf<String>()
            for (k in 0 until ar.length()) {
                val name = ar.optJSONObject(k)?.optString("name")?.trim().orEmpty()
                if (name.isNotEmpty()) singers.add(name)
            }
            val al = s.optJSONObject("al") ?: s.optJSONObject("album")
            val dtMs = s.optLong("dt", s.optLong("duration", 0L))
            val picUrl = al?.optString("picUrl").orEmpty().replace("http://", "https://")
            list.add(
                MusicTrack(
                    id = idStr,
                    platform = "wy",
                    name = MovieHubData.stripHtml(s.optString("name")),
                    singer = singers.joinToString(" / "),
                    album = MovieHubData.stripHtml(al?.optString("name")),
                    duration = (dtMs / 1000L).toInt(),
                    pic = picUrl
                )
            )
        }
        return list
    }

    private suspend fun searchKw(kw: String, page: Int): List<MusicTrack> {
        val pn = (page - 1)
        val u = "http://search.kuwo.cn/r.s?client=kt&all=${enc(kw)}&pn=$pn&rn=25&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val raw = getString(u, "http://www.kuwo.cn/", retries = 0)
        val j = try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject(raw.replace("'", "\""))
        }
        val abslist = j.optJSONArray("abslist") ?: JSONArray()
        val list = mutableListOf<MusicTrack>()
        for (i in 0 until abslist.length()) {
            val s = abslist.optJSONObject(i) ?: continue
            val rid = s.optString("MUSICRID").ifEmpty { s.optString("DC_TARGETID") }.ifEmpty { s.optString("SONGID") }.removePrefix("MUSIC_")
            if (rid.isEmpty()) continue
            val picShort = s.optString("web_albumpic_short")
            val pic = if (picShort.isNotEmpty()) "https://img2.kuwo.cn/star/albumcover/$picShort" else ""
            list.add(
                MusicTrack(
                    id = rid,
                    platform = "kw",
                    name = MovieHubData.stripHtml(s.optString("SONGNAME").ifEmpty { s.optString("NAME") }),
                    singer = MovieHubData.stripHtml(s.optString("ARTIST")),
                    album = MovieHubData.stripHtml(s.optString("ALBUM")),
                    duration = s.optString("DURATION").toIntOrNull() ?: 0,
                    pic = pic,
                    rid = rid
                )
            )
        }
        return list
    }

    private suspend fun searchKg(kw: String, page: Int): List<MusicTrack> {
        // Endpoint 1: KuGou Official Mobile CDN (fast & cookie-free)
        try {
            val u = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${enc(kw)}&page=$page&pagesize=25&showtype=1"
            val j = getJsonObject(u, "http://m.kugou.com/", retries = 0)
            val infos = j.optJSONObject("data")?.optJSONArray("info") ?: JSONArray()
            val list = mutableListOf<MusicTrack>()
            for (i in 0 until infos.length()) {
                val s = infos.optJSONObject(i) ?: continue
                val hash = s.optString("hash").ifEmpty { s.optString("320hash") }
                if (hash.isEmpty()) continue
                list.add(
                    MusicTrack(
                        id = hash,
                        platform = "kg",
                        name = MovieHubData.stripHtml(s.optString("songname").ifEmpty { s.optString("filename").substringAfter("-").trim() }),
                        singer = MovieHubData.stripHtml(s.optString("singername")),
                        album = MovieHubData.stripHtml(s.optString("album_name")),
                        duration = s.optInt("duration", 0),
                        hash = hash,
                        albumId = s.opt("album_id")?.toString().orEmpty()
                    )
                )
            }
            if (list.isNotEmpty()) return list
        } catch (_: Exception) {
        }

        // Endpoint 2: songsearch.kugou.com with kg_mid cookie
        val u2 = "https://songsearch.kugou.com/song_search_v2?keyword=${enc(kw)}&page=$page&pagesize=20&platform=WebFilter&tag=em&filter=2&iscorrection=1&privilege_filter=0&_=${System.currentTimeMillis()}"
        val raw2 = getStringWithHeaders(u2, "https://www.kugou.com/", "kg_mid=2333333333")
        val j2 = JSONObject(raw2)
        val lists = j2.optJSONObject("data")?.optJSONArray("lists") ?: JSONArray()
        val list2 = mutableListOf<MusicTrack>()
        for (i in 0 until lists.length()) {
            val s = lists.optJSONObject(i) ?: continue
            val hash = s.optString("FileHash")
            if (hash.isEmpty()) continue
            list2.add(
                MusicTrack(
                    id = hash,
                    platform = "kg",
                    name = MovieHubData.stripHtml(s.optString("SongName")),
                    singer = MovieHubData.stripHtml(s.optString("SingerName")),
                    album = MovieHubData.stripHtml(s.optString("AlbumName")),
                    duration = s.optInt("Duration", 0),
                    pic = s.optString("Image"),
                    hash = hash,
                    albumId = s.opt("AlbumID")?.toString().orEmpty()
                )
            )
        }
        return list2
    }

    private suspend fun searchTx(kw: String, page: Int = 1): List<MusicTrack> {
        try {
            val u = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?key=${enc(kw)}&format=json"
            val j = getJsonObject(u, "https://y.qq.com/", retries = 0)
            val itemlist = j.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("itemlist") ?: JSONArray()
            val list = mutableListOf<MusicTrack>()
            for (i in 0 until itemlist.length()) {
                val s = itemlist.optJSONObject(i) ?: continue
                val mid = s.optString("mid")
                val albummid = s.optString("albummid")
                val pic = if (albummid.isNotEmpty()) {
                    "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albummid}.jpg"
                } else ""
                list.add(
                    MusicTrack(
                        id = mid,
                        platform = "tx",
                        name = MovieHubData.stripHtml(s.optString("name")),
                        singer = MovieHubData.stripHtml(s.optString("singer")),
                        pic = pic
                    )
                )
            }
            if (list.isNotEmpty()) return list
        } catch (_: Exception) {
        }

        // Backup: client_search_cp
        val u2 = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.song&t=0&aggr=1&cr=1&catZhida=1&lossless=0&flag_qc=0&p=$page&n=20&w=${enc(kw)}&format=json"
        val j2 = getJsonObject(u2, "https://y.qq.com/", retries = 0)
        val songs = j2.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: JSONArray()
        val list2 = mutableListOf<MusicTrack>()
        for (i in 0 until songs.length()) {
            val s = songs.optJSONObject(i) ?: continue
            val mid = s.optString("mid")
            val singers = s.optJSONArray("singer") ?: JSONArray()
            val sNames = (0 until singers.length()).mapNotNull { singers.optJSONObject(it)?.optString("name") }
            val albumObj = s.optJSONObject("album")
            val albumMid = albumObj?.optString("mid").orEmpty()
            val pic = if (albumMid.isNotEmpty()) "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albumMid}.jpg" else ""
            list2.add(
                MusicTrack(
                    id = mid,
                    platform = "tx",
                    name = MovieHubData.stripHtml(s.optString("name")),
                    singer = sNames.joinToString(" / "),
                    album = MovieHubData.stripHtml(albumObj?.optString("name")),
                    duration = s.optInt("interval", 0),
                    pic = pic
                )
            )
        }
        return list2
    }

    private suspend fun searchMg(kw: String, page: Int): List<MusicTrack> {
        val u = "https://m.music.migu.cn/migu/remoting/scr_search_tag?rows=20&pgc=$page&keyword=${enc(kw)}&type=2"
        val j = getJsonObject(u, "https://m.music.migu.cn/", retries = 0)
        val arr = j.optJSONArray("musics") ?: j.optJSONArray("data") ?: JSONArray()
        val list = mutableListOf<MusicTrack>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val cid = s.optString("copyrightId").ifEmpty { s.optString("id") }
            list.add(
                MusicTrack(
                    id = cid,
                    platform = "mg",
                    name = MovieHubData.stripHtml(s.optString("songName")),
                    singer = MovieHubData.stripHtml(s.optString("singerName")),
                    album = MovieHubData.stripHtml(s.optString("albumName")),
                    pic = s.optString("cover"),
                    directUrl = s.optString("mp3").ifEmpty { s.optString("listenUrl") }
                )
            )
        }
        return list
    }

    private suspend fun searchItunes(kw: String, platformTag: String = "wy"): List<MusicTrack> {
        val u = "https://itunes.apple.com/search?term=${enc(kw)}&media=music&entity=song&country=CN&limit=25"
        val raw = getString(u, "https://music.apple.com/", retries = 0)
        val j = JSONObject(raw)
        val results = j.optJSONArray("results") ?: JSONArray()
        val list = mutableListOf<MusicTrack>()
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val preview = r.optString("previewUrl")
            if (!preview.startsWith("http")) continue
            val art = r.optString("artworkUrl100").replace("100x100bb", "300x300bb")
            list.add(
                MusicTrack(
                    id = r.opt("trackId")?.toString().orEmpty(),
                    platform = platformTag,
                    name = r.optString("trackName"),
                    singer = r.optString("artistName"),
                    album = r.optString("collectionName"),
                    duration = (r.optLong("trackTimeMillis", 0L) / 1000L).toInt(),
                    pic = art,
                    directUrl = preview
                )
            )
        }
        return list
    }

    /**
     * Fast non-blocking redirect probe: requests only bytes=0-1 so it never downloads the full MP3 on the resolver thread,
     * and checks that the final URL is a valid audio stream (not a /404 page).
     */
    private fun probePlayableAudioUrl(candidateUrl: String, referer: String): String? {
        if (!candidateUrl.startsWith("http", ignoreCase = true)) return null
        val req = Request.Builder()
            .url(candidateUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", referer)
            .header("Range", "bytes=0-1")
            .build()
        return try {
            okHttpClient.newCall(req).execute().use { resp ->
                val finalU = resp.request.url.toString()
                val ct = resp.header("Content-Type").orEmpty().lowercase()
                val isValidStatus = resp.code in 200..299
                val isNot404 = !finalU.contains("/404") && !finalU.endsWith("404")
                val looksLikeAudio = ct.contains("audio") ||
                    ct.contains("octet-stream") ||
                    ct.contains("mpeg") ||
                    ct.contains("mp4") ||
                    finalU.contains(".mp3", ignoreCase = true) ||
                    finalU.contains(".m4a", ignoreCase = true) ||
                    finalU.contains(".flac", ignoreCase = true)
                if (isValidStatus && isNot404 && looksLikeAudio) {
                    finalU
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun urlWy(id: String, quality: String): String {
        val wyCookie = "os=pc; appver=2.9.7; NMTID=00O8k2"
        val level = when (quality) {
            "flac" -> "lossless"
            "320k" -> "exhigh"
            else -> "standard"
        }
        val encodeType = if (quality == "flac") "flac" else "mp3"

        // 1. Official v1 player API with PC cookie
        try {
            val u = "https://interface.music.163.com/api/song/enhance/player/url/v1?ids=%5B${enc(id)}%5D&level=$level&encodeType=$encodeType"
            val raw = getStringWithHeaders(u, "https://music.163.com/", wyCookie)
            val j = JSONObject(raw)
            val d = j.optJSONArray("data")?.optJSONObject(0)
            val freeTrialInfo = d?.optJSONObject("freeTrialInfo")
            val url = d?.optString("url").orEmpty()
            if (url.startsWith("http", ignoreCase = true) && freeTrialInfo == null) {
                return url.replace("http://", "https://")
            }
        } catch (_: Exception) {
        }

        // 2. Enhance player url endpoint
        try {
            val br = if (quality == "128k") 128000 else 320000
            val u2 = "https://music.163.com/api/song/enhance/player/url?id=${enc(id)}&ids=%5B${enc(id)}%5D&br=$br"
            val raw2 = getStringWithHeaders(u2, "https://music.163.com/", wyCookie)
            val j2 = JSONObject(raw2)
            val d2 = j2.optJSONArray("data")?.optJSONObject(0)
            val url2 = d2?.optString("url").orEmpty()
            if (url2.startsWith("http", ignoreCase = true)) {
                return url2.replace("http://", "https://")
            }
        } catch (_: Exception) {
        }

        // 3. Fast Range probe on outer/url
        val outerUrl = "https://music.163.com/song/media/outer/url?id=${enc(id)}.mp3"
        probePlayableAudioUrl(outerUrl, "https://music.163.com/")?.let { return it }

        // 4. Meting API mirror probe
        val metingUrl = "https://api.injahow.cn/meting/?server=netease&type=url&id=${enc(id)}"
        probePlayableAudioUrl(metingUrl, "https://music.163.com/")?.let { return it }

        throw IllegalStateException("网易云音源受限")
    }

    private fun urlKg(hash: String, albumId: String = ""): String {
        if (hash.isEmpty()) throw IllegalStateException("酷狗 hash 为空")

        // 1. KuGou mobile getSongInfo API (cmd=playInfo)
        try {
            val u = "http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=${enc(hash)}"
            val raw = getStringWithHeaders(u, "http://m.kugou.com/")
            val j = JSONObject(raw)
            val direct = j.optString("url")
            if (direct.startsWith("http", ignoreCase = true)) return direct
            val backupArr = j.optJSONArray("backup_url")
            if (backupArr != null && backupArr.length() > 0) {
                val b0 = backupArr.optString(0)
                if (b0.startsWith("http", ignoreCase = true)) return b0
            }
        } catch (_: Exception) {
        }

        // 2. KuGou wwwapi getdata with cookie
        try {
            val u2 = "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&hash=${enc(hash)}&album_id=${enc(albumId)}&mid=2333333333"
            val raw2 = getStringWithHeaders(u2, "https://www.kugou.com/", "kg_mid=2333333333; kg_dfid=233333")
            val j2 = JSONObject(raw2)
            val data = j2.optJSONObject("data")
            val playUrl = data?.optString("play_url").orEmpty().ifEmpty { data?.optString("play_backup_url").orEmpty() }
            if (playUrl.startsWith("http", ignoreCase = true)) return playUrl
        } catch (_: Exception) {
        }

        throw IllegalStateException("酷狗直链受限")
    }

    private suspend fun urlKw(rid: String, quality: String): String {
        val cleanRid = rid.removePrefix("MUSIC_")
        if (cleanRid.isEmpty()) throw IllegalStateException("酷我 rid 为空")

        // 1. KuWo mobi convert_url_with_sign API
        try {
            val br = if (quality == "128k") "128kmp3" else "320kmp3"
            val u = "http://mobi.kuwo.cn/mobi.s?f=web&user=0&source=kwplayer_ar_5.1.0.0_B_jiakong_vh.apk&type=convert_url_with_sign&rid=${enc(cleanRid)}&br=$br"
            val raw = getString(u, "http://www.kuwo.cn/", retries = 0)
            if (raw.trimStart().startsWith("{")) {
                val j = JSONObject(raw)
                val dUrl = j.optJSONObject("data")?.optString("url").orEmpty()
                if (dUrl.startsWith("http", ignoreCase = true)) return dUrl
            } else {
                val urlLine = raw.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("url=", ignoreCase = true) }
                    ?.substringAfter("=")
                    ?.trim()
                if (!urlLine.isNullOrEmpty() && urlLine.startsWith("http", ignoreCase = true)) {
                    return urlLine
                }
            }
        } catch (_: Exception) {
        }

        // 2. KuWo antiserver fallback
        val fmt = if (quality == "flac") "flac" else "mp3"
        val u2 = "http://antiserver.kuwo.cn/anti.s?type=convert_url&format=$fmt&response=url&rid=MUSIC_${enc(cleanRid)}"
        val text = getString(u2, "http://www.kuwo.cn/", retries = 0).trim()
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            return text.lines().first().trim()
        }
        throw IllegalStateException("酷我直链受限")
    }

    /**
     * Multi-Source Smart Audio Stream Resolver:
     * 1. Direct URL if already present on the track
     * 2. Selected platform's multi-endpoint resolver (wy / kg / kw / mg)
     * 3. Cross-platform smart fallback: searches KuGou -> KuWo -> NetEase -> Apple Music AAC
     *    so any song can be played seamlessly even if one platform restricts it.
     */
    suspend fun resolveMusicUrl(
        track: MusicTrack,
        preferredQuality: String = "320k"
    ): ResolvedMusicUrl = withContext(Dispatchers.IO) {
        val qualities = listOf(preferredQuality, "320k", "128k").distinct()

        // 1. Direct stream URL if already present and valid
        if (track.directUrl.startsWith("http", ignoreCase = true)) {
            return@withContext ResolvedMusicUrl(
                url = track.directUrl,
                quality = preferredQuality,
                via = "${track.platform.uppercase()} 直链"
            )
        }

        // 2. Try current platform official endpoints
        for (q in qualities) {
            try {
                when (track.platform) {
                    "wy" -> {
                        val raw = urlWy(track.id, q)
                        return@withContext ResolvedMusicUrl(
                            url = raw,
                            quality = q,
                            via = "网易云 · ${q.uppercase()}"
                        )
                    }
                    "kg" -> {
                        val raw = urlKg(track.hash.ifEmpty { track.id }, track.albumId)
                        return@withContext ResolvedMusicUrl(
                            url = raw,
                            quality = q,
                            via = "酷狗音乐 · ${q.uppercase()}"
                        )
                    }
                    "kw" -> {
                        val raw = urlKw(track.rid.ifEmpty { track.id }, q)
                        return@withContext ResolvedMusicUrl(
                            url = raw,
                            quality = q,
                            via = "酷我音乐 · ${q.uppercase()}"
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        val searchQuery = "${track.name} ${track.singer.substringBefore("/")}".trim()

        // 3. Cross-platform Fallback A: KuGou Mobile PlayInfo
        if (track.platform != "kg") {
            try {
                val kgCandidates = searchKg(searchQuery, 1)
                for (cand in kgCandidates.take(4)) {
                    try {
                        val raw = urlKg(cand.hash.ifEmpty { cand.id }, cand.albumId)
                        return@withContext ResolvedMusicUrl(
                            url = raw,
                            quality = "320k",
                            via = "跨源解析 · 酷狗"
                        )
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 4. Cross-platform Fallback B: KuWo Mobi Sign
        if (track.platform != "kw") {
            try {
                val kwCandidates = searchKw(searchQuery, 1)
                for (cand in kwCandidates.take(4)) {
                    try {
                        val raw = urlKw(cand.id, "320k")
                        return@withContext ResolvedMusicUrl(
                            url = raw,
                            quality = "320k",
                            via = "跨源解析 · 酷我"
                        )
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 5. Cross-platform Fallback C: NetEase Cloud Music
        if (track.platform != "wy") {
            try {
                val wyCandidates = searchWy(searchQuery, 1)
                for (cand in wyCandidates.take(4)) {
                    if (cand.directUrl.startsWith("http")) {
                        return@withContext ResolvedMusicUrl(
                            url = cand.directUrl,
                            quality = "320k",
                            via = "跨源解析 · 网易云"
                        )
                    }
                    try {
                        val raw = urlWy(cand.id, "128k")
                        return@withContext ResolvedMusicUrl(
                            url = raw,
                            quality = "128k",
                            via = "跨源解析 · 网易云"
                        )
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 6. Cross-platform Fallback D: Apple Music / iTunes High-Bitrate AAC Stream
        try {
            val itunesCandidates = searchItunes(searchQuery, track.platform)
            val best = itunesCandidates.firstOrNull { it.directUrl.startsWith("http") }
            if (best != null) {
                return@withContext ResolvedMusicUrl(
                    url = best.directUrl,
                    quality = "256k AAC",
                    via = "跨源解析 · Apple Music"
                )
            }
        } catch (_: Exception) {
        }

        throw IllegalStateException("该曲目在全网音源均受版权限制，请尝试其他版本")
    }

    /* ─────────────────── 6. 歌词获取 (Ported from moviehub/music.js handleLyric) ─────────────────── */

    suspend fun fetchLyric(track: MusicTrack): String = withContext(Dispatchers.IO) {
        try {
            val lrc = when (track.platform) {
                "wy" -> {
                    val j = getJsonObject("https://music.163.com/api/song/lyric?id=${enc(track.id)}&lv=1&kv=1&tv=-1", "https://music.163.com/")
                    j.optJSONObject("lrc")?.optString("lyric").orEmpty()
                }
                "tx" -> {
                    val raw = getString(
                        "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=${enc(track.id)}&format=json&nobase64=1&songtype=0",
                        "https://y.qq.com/portal/player.html"
                    )
                    val clean = Regex("^\\w+\\((.*)\\)$", RegexOption.DOT_MATCHES_ALL)
                        .find(raw)?.groupValues?.get(1) ?: raw
                    val j = JSONObject(clean)
                    val lyricField = j.optString("lyric")
                    if (lyricField.isNotEmpty() && !lyricField.contains("[")) {
                        try {
                            String(Base64.decode(lyricField, Base64.DEFAULT), Charsets.UTF_8)
                        } catch (_: Exception) {
                            lyricField
                        }
                    } else {
                        lyricField
                    }
                }
                "kg" -> {
                    val hash = track.hash.ifEmpty { track.id }
                    val j = getJsonObject(
                        "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&hash=${enc(hash)}&album_id=${enc(track.albumId)}",
                        "https://www.kugou.com/"
                    )
                    j.optJSONObject("data")?.optString("lyrics").orEmpty()
                }
                "kw" -> {
                    val rid = track.rid.ifEmpty { track.id }.removePrefix("MUSIC_")
                    val j = getJsonObject("https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=${enc(rid)}", "https://www.kuwo.cn/")
                    val lrclist = j.optJSONObject("data")?.optJSONArray("lrclist") ?: JSONArray()
                    val sb = StringBuilder()
                    for (i in 0 until lrclist.length()) {
                        val lineObj = lrclist.optJSONObject(i) ?: continue
                        val sec = lineObj.optDouble("time", 0.0)
                        val min = (sec / 60).toInt()
                        val rem = sec - min * 60
                        val timeTag = String.format("[%02d:%05.2f]", min, rem)
                        sb.append(timeTag).append(lineObj.optString("lineLyric")).append("\n")
                    }
                    sb.toString()
                }
                "mg" -> {
                    val j = getJsonObject(
                        "https://music.migu.cn/v3/api/music/audioPlayer/getLyric?copyrightId=${enc(track.id)}",
                        "https://music.migu.cn/"
                    )
                    j.optString("lyric").ifEmpty { j.optString("data") }
                }
                else -> ""
            }
            if (lrc.isNotBlank()) return@withContext lrc

            // Fallback: fetch lyric from NetEase by song name + singer
            if (track.platform != "wy") {
                val wyMatch = searchWy("${track.name} ${track.singer.substringBefore("/")}", 1).firstOrNull()
                if (wyMatch != null) {
                    val j = getJsonObject("https://music.163.com/api/song/lyric?id=${enc(wyMatch.id)}&lv=1&kv=1&tv=-1", "https://music.163.com/")
                    return@withContext j.optJSONObject("lrc")?.optString("lyric").orEmpty()
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    /* ─────────────────── 7. 远程云端源站在线同步与节点测速 ─────────────────── */

    /** 自建源站（Render + TiDB），运维后台改的源在这里实时生效。 */
    const val PRIMARY_REMOTE_SOURCE_URL =
        "https://moviehub-source-server.onrender.com/api/sources.json"

    /** GitHub Raw 备选：自建源站不可达时回退，保证客户端不至于无源可用。 */
    const val FALLBACK_REMOTE_SOURCE_URL =
        "https://raw.githubusercontent.com/berse1kguts-pixel/moviehub/main/sources.json"

    const val DEFAULT_REMOTE_SOURCE_URL = PRIMARY_REMOTE_SOURCE_URL

    private const val FALLBACK_REPO_SERVER_JS_URL =
        "https://raw.githubusercontent.com/berse1kguts-pixel/moviehub/main/moviehub/server.js"

    private const val FALLBACK_REPO_LIVE_M3U_URL =
        "https://raw.githubusercontent.com/berse1kguts-pixel/moviehub/main/cn-tv-tested.m3u"

    data class RemoteSourceBundle(
        val version: String,
        val updatedAt: String,
        val syncedFromUrl: String,
        val vodSources: List<VodSource>,
        val liveChannels: List<LiveTvChannel>,
        val lxScripts: List<LxScriptSource>,
        val rawJson: String
    )

    private fun expandMirrorCandidates(rawUrl: String): List<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return emptyList()
        // 自建源站优先，失败再回退到 GitHub Raw 及其镜像
        if (trimmed.startsWith(PRIMARY_REMOTE_SOURCE_URL)) {
            return listOf(trimmed, FALLBACK_REMOTE_SOURCE_URL).distinct()
        }
        if (trimmed.startsWith("https://raw.githubusercontent.com/")) {
            // Convert https://raw.githubusercontent.com/owner/repo/branch/path
            // into multiple domestic CDN mirrors + jsDelivr + direct GitHub Raw
            val afterHost = trimmed.removePrefix("https://raw.githubusercontent.com/")
            val parts = afterHost.split("/", limit = 4)
            val jsDelivr = if (parts.size == 4) {
                "https://fastly.jsdelivr.net/gh/${parts[0]}/${parts[1]}@${parts[2]}/${parts[3]}"
            } else null
            return listOfNotNull(
                "https://ghfast.top/$trimmed",
                jsDelivr,
                "https://mirror.ghproxy.com/$trimmed",
                trimmed
            ).distinct()
        }
        return listOf(trimmed)
    }

    private suspend fun fetchFirstAvailableText(candidates: List<String>, timeoutMs: Long = 5500L): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            for (u in candidates) {
                val res = withTimeoutOrNull(timeoutMs) {
                    try {
                        val txt = getString(u, retries = 0)
                        if (txt.isNotBlank()) u to txt else null
                    } catch (_: Exception) {
                        null
                    }
                }
                if (res != null) return@withContext res
            }
            null
        }

    fun parseM3uContent(rawM3u: String): List<LiveTvChannel> {
        val channels = mutableListOf<LiveTvChannel>()
        var pendingName = ""
        var pendingGroup = "云端频道"
        val groupRegex = Regex("""group-title="([^"]+)"""")
        for (line in rawM3u.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                pendingGroup = groupRegex.find(trimmed)?.groupValues?.get(1)?.trim()
                    ?.ifEmpty { "云端频道" } ?: "云端频道"
                pendingName = trimmed.substringAfterLast(",").trim()
            } else if (!trimmed.startsWith("#") && (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true))) {
                if (pendingName.isNotEmpty()) {
                    channels.add(
                        LiveTvChannel(
                            id = "m3u_${channels.size}_${pendingName.hashCode()}",
                            name = pendingName,
                            group = pendingGroup,
                            url = trimmed
                        )
                    )
                    pendingName = ""
                }
            }
        }
        return channels
    }

    private fun parseVodSourcesFromServerJs(rawJs: String): List<VodSource> {
        val objRegex = Regex("""\{\s*id\s*:\s*['"]([^'"]+)['"]\s*,\s*name\s*:\s*['"]([^'"]+)['"]\s*,\s*api\s*:\s*['"]([^'"]+)['"]""")
        val list = mutableListOf<VodSource>()
        objRegex.findAll(rawJs).forEach { m ->
            val id = m.groupValues[1].trim()
            val name = m.groupValues[2].trim()
            val api = m.groupValues[3].trim()
            if (id.isNotEmpty() && name.isNotEmpty() && api.startsWith("http", ignoreCase = true)) {
                list.add(VodSource(id = id, name = name, api = api, enabled = true))
            }
        }
        return list
    }

    fun parseRemoteSourceBundleJson(rawJson: String, syncedFrom: String = "本地缓存"): RemoteSourceBundle? {
        return try {
            val j = JSONObject(rawJson)
            val version = j.optString("version", "1.0.0")
            val updatedAt = j.optString("updatedAt", "在线同步")

            val vodArr = j.optJSONArray("vodSources") ?: j.optJSONArray("sources") ?: JSONArray()
            val vodList = mutableListOf<VodSource>()
            for (i in 0 until vodArr.length()) {
                val o = vodArr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val name = o.optString("name").trim()
                val api = o.optString("api").trim()
                val enabled = o.optBoolean("enabled", true)
                if (id.isNotEmpty() && name.isNotEmpty() && api.startsWith("http", ignoreCase = true)) {
                    vodList.add(VodSource(id = id, name = name, api = api, enabled = enabled))
                }
            }

            val liveArr = j.optJSONArray("liveChannels") ?: JSONArray()
            val liveList = mutableListOf<LiveTvChannel>()
            for (i in 0 until liveArr.length()) {
                val o = liveArr.optJSONObject(i) ?: continue
                val id = o.optString("id").ifEmpty { "live_$i" }
                val name = o.optString("name").trim()
                val group = o.optString("group", "云端直播").trim()
                val url = o.optString("url").trim()
                if (name.isNotEmpty() && url.startsWith("http", ignoreCase = true)) {
                    liveList.add(LiveTvChannel(id = id, name = name, group = group, url = url))
                }
            }

            val lxArr = j.optJSONArray("lxMusicScripts") ?: JSONArray()
            val lxList = mutableListOf<LxScriptSource>()
            for (i in 0 until lxArr.length()) {
                val o = lxArr.optJSONObject(i) ?: continue
                val id = o.optString("id").ifEmpty { "remote_$i" }
                val name = o.optString("name").trim()
                val url = o.optString("url").trim()
                val enabled = o.optBoolean("enabled", true)
                if (name.isNotEmpty() && url.startsWith("http", ignoreCase = true)) {
                    lxList.add(LxScriptSource(id = id, name = name, url = url, enabled = enabled))
                }
            }

            if (vodList.isEmpty() && liveList.isEmpty() && lxList.isEmpty()) {
                null
            } else {
                RemoteSourceBundle(
                    version = version,
                    updatedAt = updatedAt,
                    syncedFromUrl = syncedFrom,
                    vodSources = vodList,
                    liveChannels = liveList,
                    lxScripts = lxList,
                    rawJson = rawJson
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches the latest VOD/Live/Music source configuration from the hardcoded remote source repository
     * (https://github.com/berse1kguts-pixel/moviehub) or a user-specified custom URL.
     * Supports both `sources.json` and direct fallback parsing of `moviehub/server.js` + `cn-tv-tested.m3u`.
     */
    suspend fun syncRemoteSources(customUrl: String = ""): RemoteSourceBundle = withContext(Dispatchers.IO) {
        val targetUrl = customUrl.trim().ifEmpty { DEFAULT_REMOTE_SOURCE_URL }
        val candidates = expandMirrorCandidates(targetUrl)

        // 1. Try fetching JSON bundle (`sources.json`)
        val jsonHit = fetchFirstAvailableText(candidates, timeoutMs = 5500L)
        if (jsonHit != null) {
            val (hitUrl, rawText) = jsonHit
            if (rawText.trimStart().startsWith("{")) {
                val parsed = parseRemoteSourceBundleJson(rawText, syncedFrom = hitUrl)
                if (parsed != null) {
                    // Check if remote JSON also references an external liveM3uUrl
                    val j = JSONObject(rawText)
                    val m3uUrl = j.optString("liveM3uUrl").trim()
                    if (parsed.liveChannels.isEmpty() && m3uUrl.startsWith("http", ignoreCase = true)) {
                        val m3uHit = fetchFirstAvailableText(expandMirrorCandidates(m3uUrl), timeoutMs = 5000L)
                        val m3uChannels = m3uHit?.second?.let { parseM3uContent(it) }.orEmpty()
                        if (m3uChannels.isNotEmpty()) {
                            return@withContext parsed.copy(liveChannels = m3uChannels)
                        }
                    }
                    return@withContext parsed
                }
            }
        }

        // 2. Fallback: Directly sync from `berse1kguts-pixel/moviehub`'s existing `moviehub/server.js` + `cn-tv-tested.m3u`
        val serverJsHit = fetchFirstAvailableText(expandMirrorCandidates(FALLBACK_REPO_SERVER_JS_URL), timeoutMs = 5500L)
        val m3uHit = fetchFirstAvailableText(expandMirrorCandidates(FALLBACK_REPO_LIVE_M3U_URL), timeoutMs = 5500L)

        val vodFromJs = serverJsHit?.second?.let { parseVodSourcesFromServerJs(it) }.orEmpty()
        val liveFromM3u = m3uHit?.second?.let { parseM3uContent(it) }.orEmpty()

        if (vodFromJs.isNotEmpty() || liveFromM3u.isNotEmpty()) {
            val finalVods = vodFromJs.ifEmpty { MovieHubData.DEFAULT_VOD_SOURCES }
            val synthesizedJson = JSONObject().apply {
                put("version", "1.0.0-repo")
                put("updatedAt", "GitHub 实时同步")
                val arr = JSONArray()
                finalVods.forEach { s ->
                    arr.put(JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("api", s.api)
                        put("enabled", s.enabled)
                    })
                }
                put("vodSources", arr)
            }.toString()

            return@withContext RemoteSourceBundle(
                version = "1.0.0-repo",
                updatedAt = "GitHub 仓库实时同步",
                syncedFromUrl = serverJsHit?.first ?: m3uHit?.first ?: DEFAULT_REMOTE_SOURCE_URL,
                vodSources = finalVods,
                liveChannels = liveFromM3u,
                lxScripts = emptyList(),
                rawJson = synthesizedJson
            )
        }

        throw IllegalStateException("远程源站连接超时，已使用本地缓存源")
    }

    /**
     * Concurrently pings all VOD sources, records their real HTTP response time in [sourceLatencyMap],
     * and returns the sources sorted from fastest to slowest so subsequent browsing/playback uses the fastest node first.
     */
    suspend fun speedTestVodSources(sources: List<VodSource>): List<VodSource> = coroutineScope {
        val measured = sources.map { s ->
            async(Dispatchers.IO) {
                if (!s.enabled) {
                    s to Long.MAX_VALUE
                } else {
                    val t0 = System.currentTimeMillis()
                    val probeUrl = buildApiUrl(s.api, mapOf("ac" to "list", "pg" to "1"))
                    val latency = withTimeoutOrNull(3800L) {
                        try {
                            val req = Request.Builder()
                                .url(probeUrl)
                                .header("User-Agent", USER_AGENT)
                                .build()
                            okHttpClient.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val buf = ByteArray(256)
                                    resp.body?.byteStream()?.read(buf)
                                    (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                                } else {
                                    8888L
                                }
                            }
                        } catch (_: Exception) {
                            9999L
                        }
                    } ?: 9999L
                    sourceLatencyMap[s.id] = latency
                    s to latency
                }
            }
        }.awaitAll()

        measured.sortedBy { it.second }.map { it.first }
    }

    /* ─────────────────── 8. 完整渠道状态深度检测 & 源探测查询工具 ─────────────────── */

    data class SourceHealthCheckResult(
        val sourceId: String,
        val sourceName: String,
        val channelType: String, // "VOD", "LIVE", "MUSIC"
        val apiUrl: String,
        val status: String, // "HEALTHY", "DEGRADED", "OFFLINE"
        val httpCode: Int,
        val apiLatencyMs: Long,
        val streamLatencyMs: Long,
        val vodTotalCount: Int,
        val sampleTitle: String,
        val detailMessage: String,
        val checkedAt: Long = System.currentTimeMillis()
    )

    data class SourceProbeSampleItem(
        val vodId: String,
        val title: String,
        val typeName: String,
        val remarks: String,
        val lineFlags: String,
        val firstEpisodeUrl: String
    )

    data class SourceProbeInspection(
        val apiUrl: String,
        val isReachable: Boolean,
        val apiLatencyMs: Long,
        val streamLatencyMs: Long,
        val totalVodCount: Int,
        val pageCount: Int,
        val sampleItems: List<SourceProbeSampleItem>,
        val errorMsg: String? = null,
        val suggestedJsonSnippet: String = ""
    )

    suspend fun checkSingleVodSourceDeep(s: VodSource): SourceHealthCheckResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val url = buildApiUrl(s.api, mapOf("ac" to "videolist", "pg" to "1"))
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            val (code, bodyStr) = withTimeoutOrNull(5000L) {
                okHttpClient.newCall(req).execute().use { resp ->
                    resp.code to (resp.body?.string()?.removePrefix("\uFEFF")?.trim().orEmpty())
                }
            } ?: return@withContext SourceHealthCheckResult(
                sourceId = s.id,
                sourceName = s.name,
                channelType = "VOD",
                apiUrl = s.api,
                status = "OFFLINE",
                httpCode = 0,
                apiLatencyMs = 5000L,
                streamLatencyMs = 0L,
                vodTotalCount = 0,
                sampleTitle = "",
                detailMessage = "接口请求超时 (>5000ms)"
            )

            val apiCost = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            sourceLatencyMap[s.id] = apiCost
            if (code !in 200..299 || bodyStr.isEmpty()) {
                return@withContext SourceHealthCheckResult(
                    sourceId = s.id,
                    sourceName = s.name,
                    channelType = "VOD",
                    apiUrl = s.api,
                    status = "OFFLINE",
                    httpCode = code,
                    apiLatencyMs = apiCost,
                    streamLatencyMs = 0L,
                    vodTotalCount = 0,
                    sampleTitle = "",
                    detailMessage = "HTTP 状态异常 ($code)"
                )
            }

            val j = JSONObject(bodyStr)
            val total = j.optInt("total", 0)
            val listArr = j.optJSONArray("list") ?: JSONArray()
            var sampleTitle = ""
            var sampleStreamUrl = ""
            if (listArr.length() > 0) {
                val firstObj = listArr.optJSONObject(0)
                if (firstObj != null) {
                    val mapped = mapVodJsonItem(firstObj, s, latencyMs = apiCost)
                    sampleTitle = mapped.name
                    sampleStreamUrl = mapped.lines.firstOrNull()?.episodes?.firstOrNull()?.url.orEmpty()
                }
            }

            val streamMs = if (sampleStreamUrl.startsWith("http", ignoreCase = true)) {
                val probed = probeStreamLatencyMs(sampleStreamUrl)
                if (probed < Long.MAX_VALUE) probed else 0L
            } else 0L

            val status = when {
                listArr.length() == 0 -> "DEGRADED"
                apiCost < 1500L && (streamMs in 1..2200L || sampleStreamUrl.isNotEmpty()) -> "HEALTHY"
                apiCost < 4000L -> "DEGRADED"
                else -> "OFFLINE"
            }
            val msg = buildString {
                append("片库 ${total} 部 · 接口 ${apiCost}ms")
                if (streamMs > 0L) append(" · 切片流 ${streamMs}ms")
                if (sampleTitle.isNotEmpty()) append(" · 抽样《$sampleTitle》")
            }

            SourceHealthCheckResult(
                sourceId = s.id,
                sourceName = s.name,
                channelType = "VOD",
                apiUrl = s.api,
                status = status,
                httpCode = code,
                apiLatencyMs = apiCost,
                streamLatencyMs = streamMs,
                vodTotalCount = total,
                sampleTitle = sampleTitle,
                detailMessage = msg
            )
        } catch (e: Exception) {
            SourceHealthCheckResult(
                sourceId = s.id,
                sourceName = s.name,
                channelType = "VOD",
                apiUrl = s.api,
                status = "OFFLINE",
                httpCode = 0,
                apiLatencyMs = 9999L,
                streamLatencyMs = 0L,
                vodTotalCount = 0,
                sampleTitle = "",
                detailMessage = "连接异常：${e.message ?: "未知错误"}"
            )
        }
    }

    suspend fun checkSingleLiveChannel(ch: LiveTvChannel): SourceHealthCheckResult = withContext(Dispatchers.IO) {
        val streamMs = probeStreamLatencyMs(ch.url)
        val ok = streamMs < Long.MAX_VALUE
        val status = when {
            !ok -> "OFFLINE"
            streamMs < 1200L -> "HEALTHY"
            else -> "DEGRADED"
        }
        SourceHealthCheckResult(
            sourceId = ch.id,
            sourceName = ch.name,
            channelType = "LIVE",
            apiUrl = ch.url,
            status = status,
            httpCode = if (ok) 200 else 0,
            apiLatencyMs = if (ok) streamMs else 9999L,
            streamLatencyMs = if (ok) streamMs else 0L,
            vodTotalCount = 1,
            sampleTitle = ch.group,
            detailMessage = if (ok) "直播流正常 (${ch.group}) · 握手 ${streamMs}ms" else "直播流连接超时或已失效"
        )
    }

    suspend fun checkSingleMusicPlatform(id: String, name: String): SourceHealthCheckResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            val list = withTimeoutOrNull(4500L) {
                searchMusic(id, "周杰伦", 1)
            }.orEmpty()
            val cost = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            if (list.isNotEmpty()) {
                SourceHealthCheckResult(
                    sourceId = "music_$id",
                    sourceName = name,
                    channelType = "MUSIC",
                    apiUrl = "music://$id/search",
                    status = if (cost < 1500L) "HEALTHY" else "DEGRADED",
                    httpCode = 200,
                    apiLatencyMs = cost,
                    streamLatencyMs = 0L,
                    vodTotalCount = list.size,
                    sampleTitle = list.first().name,
                    detailMessage = "搜歌正常 · 返回 ${list.size} 首 · 耗时 ${cost}ms"
                )
            } else {
                SourceHealthCheckResult(
                    sourceId = "music_$id",
                    sourceName = name,
                    channelType = "MUSIC",
                    apiUrl = "music://$id/search",
                    status = "OFFLINE",
                    httpCode = 0,
                    apiLatencyMs = cost,
                    streamLatencyMs = 0L,
                    vodTotalCount = 0,
                    sampleTitle = "",
                    detailMessage = "搜索结果为空"
                )
            }
        } catch (e: Exception) {
            SourceHealthCheckResult(
                sourceId = "music_$id",
                sourceName = name,
                channelType = "MUSIC",
                apiUrl = "music://$id/search",
                status = "OFFLINE",
                httpCode = 0,
                apiLatencyMs = 9999L,
                streamLatencyMs = 0L,
                vodTotalCount = 0,
                sampleTitle = "",
                detailMessage = "音源异常：${e.message}"
            )
        }
    }

    suspend fun runFullChannelDiagnostics(
        vodSources: List<VodSource>,
        liveChannels: List<LiveTvChannel>
    ): List<SourceHealthCheckResult> = coroutineScope {
        val vodJobs = vodSources.map { s ->
            async(Dispatchers.IO) { checkSingleVodSourceDeep(s) }
        }
        val liveSample = liveChannels.take(8).map { ch ->
            async(Dispatchers.IO) { checkSingleLiveChannel(ch) }
        }
        val musicJobs = MovieHubData.MUSIC_PLATFORMS.map { p ->
            async(Dispatchers.IO) { checkSingleMusicPlatform(p.key, p.name) }
        }
        (vodJobs + liveSample + musicJobs).awaitAll()
    }

    /**
     * Interactive Source Inspector ("查询源的方法"):
     * Probes any AppleCMS / MacCMS v10 API URL (`https://.../api.php/provide/vod/`) or searches it by keyword,
     * verifies both JSON structure and actual M3U8 video stream playability, and generates a ready-to-paste `sources.json` entry.
     */
    suspend fun inspectCustomSourceApi(
        rawApiUrl: String,
        keyword: String = ""
    ): SourceProbeInspection = withContext(Dispatchers.IO) {
        val cleanUrl = rawApiUrl.trim()
        if (!cleanUrl.startsWith("http", ignoreCase = true)) {
            return@withContext SourceProbeInspection(
                apiUrl = cleanUrl,
                isReachable = false,
                apiLatencyMs = 0L,
                streamLatencyMs = 0L,
                totalVodCount = 0,
                pageCount = 0,
                sampleItems = emptyList(),
                errorMsg = "请输入以 http:// 或 https:// 开头的采集站接口地址"
            )
        }

        val t0 = System.currentTimeMillis()
        try {
            val params = if (keyword.trim().isNotEmpty()) {
                mapOf("ac" to "detail", "wd" to keyword.trim())
            } else {
                mapOf("ac" to "videolist", "pg" to "1")
            }
            val targetUrl = buildApiUrl(cleanUrl, params)
            val j = getJsonObject(targetUrl, cleanUrl, retries = 0)
            val apiCost = (System.currentTimeMillis() - t0).coerceAtLeast(1L)

            val total = j.optInt("total", 0)
            val pageCount = j.optInt("pagecount", 1)
            val arr = j.optJSONArray("list") ?: JSONArray()
            val tempSource = VodSource(id = "probe", name = "探测源", api = cleanUrl)

            val samples = mutableListOf<SourceProbeSampleItem>()
            for (i in 0 until minOf(arr.length(), 8)) {
                val obj = arr.optJSONObject(i) ?: continue
                val mapped = mapVodJsonItem(obj, tempSource, latencyMs = apiCost)
                val firstUrl = mapped.lines.firstOrNull()?.episodes?.firstOrNull()?.url.orEmpty()
                val flags = mapped.lines.joinToString(" / ") { "${it.flag}(${it.episodes.size}集)" }
                samples.add(
                    SourceProbeSampleItem(
                        vodId = mapped.vodId,
                        title = mapped.name,
                        typeName = mapped.typeName,
                        remarks = mapped.remarks,
                        lineFlags = flags.ifEmpty { "无播放线路" },
                        firstEpisodeUrl = firstUrl
                    )
                )
            }

            val firstStream = samples.firstOrNull { it.firstEpisodeUrl.startsWith("http") }?.firstEpisodeUrl.orEmpty()
            val streamMs = if (firstStream.isNotEmpty()) {
                val ms = probeStreamLatencyMs(firstStream)
                if (ms < Long.MAX_VALUE) ms else 0L
            } else 0L

            val hostShort = try {
                URI(cleanUrl).host?.replace("www.", "")?.substringBefore(".") ?: "custom"
            } catch (_: Exception) {
                "custom"
            }
            val snippet = JSONObject().apply {
                put("id", hostShort)
                put("name", "新源_$hostShort")
                put("api", cleanUrl)
                put("enabled", true)
            }.toString(2)

            SourceProbeInspection(
                apiUrl = cleanUrl,
                isReachable = true,
                apiLatencyMs = apiCost,
                streamLatencyMs = streamMs,
                totalVodCount = total.coerceAtLeast(samples.size),
                pageCount = pageCount,
                sampleItems = samples,
                errorMsg = if (samples.isEmpty()) "接口连通但未返回影片列表" else null,
                suggestedJsonSnippet = snippet
            )
        } catch (e: Exception) {
            SourceProbeInspection(
                apiUrl = cleanUrl,
                isReachable = false,
                apiLatencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                streamLatencyMs = 0L,
                totalVodCount = 0,
                pageCount = 0,
                sampleItems = emptyList(),
                errorMsg = "探测失败：${e.message ?: "无法解析 JSON 或连接超时"}"
            )
        }
    }

    /* ─────────────────── 10. 软件在线检查更新与热更新中心 (Online App Update & Hot-Update) ─────────────────── */

    const val CURRENT_APP_VERSION_NAME = "2.5.0"
    const val CURRENT_APP_VERSION_CODE = 25
    /** 自建源站的更新清单，后台「版本更新」页保存后即时生效。 */
    const val DEFAULT_UPDATE_MANIFEST_URL =
        "https://moviehub-source-server.onrender.com/api/version.json"
    const val FALLBACK_UPDATE_MANIFEST_URL =
        "https://raw.githubusercontent.com/berse1kguts-pixel/moviehub/main/version.json"
    const val JSDELIVR_UPDATE_MANIFEST_URL =
        "https://cdn.jsdelivr.net/gh/berse1kguts-pixel/moviehub@main/version.json"
    const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/berse1kguts-pixel/moviehub/releases/latest"
    const val GITHUB_RELEASES_WEB_URL =
        "https://github.com/berse1kguts-pixel/moviehub/releases"

    data class AppUpdateCheckResult(
        val currentVersionName: String = CURRENT_APP_VERSION_NAME,
        val currentVersionCode: Int = CURRENT_APP_VERSION_CODE,
        val latestVersionName: String = CURRENT_APP_VERSION_NAME,
        val latestVersionCode: Int = CURRENT_APP_VERSION_CODE,
        val hasNewAppVersion: Boolean = false,
        val forceUpdate: Boolean = false,
        val releaseTitle: String = "观影中心 v$CURRENT_APP_VERSION_NAME",
        val releaseNotes: String = "",
        val publishedAt: String = "",
        val apkDownloadUrl: String = GITHUB_RELEASES_WEB_URL,
        val mirrorDownloadUrl: String = GITHUB_RELEASES_WEB_URL,
        val releasePageUrl: String = GITHUB_RELEASES_WEB_URL,
        val remoteSourceVersion: String = "",
        val checkLatencyMs: Long = 0L,
        val checkedEndpoint: String = "",
        val statusMessage: String = "当前已是最新版本"
    )

    private fun compareSemanticVersions(v1: String, v2: String): Int {
        val clean1 = v1.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        val clean2 = v2.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        val parts1 = clean1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private fun buildMirrorApkUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("https://github.com/", ignoreCase = true) ||
            trimmed.startsWith("https://raw.githubusercontent.com/", ignoreCase = true)
        ) {
            return "https://ghp.ci/$trimmed"
        }
        return trimmed
    }

    suspend fun checkAppUpdate(customManifestUrl: String = ""): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val manifestCandidates = buildList {
            if (customManifestUrl.isNotBlank() && customManifestUrl != DEFAULT_UPDATE_MANIFEST_URL) {
                add(customManifestUrl.trim())
            }
            // 自建源站优先，其余为备选镜像
            add(DEFAULT_UPDATE_MANIFEST_URL)
            add(FALLBACK_UPDATE_MANIFEST_URL)
            add(JSDELIVR_UPDATE_MANIFEST_URL)
        }.distinct()

        // 1. Try version.json manifest first (supports custom update server or GitHub repo version.json)
        for (url in manifestCandidates) {
            try {
                val raw = withTimeoutOrNull(3200L) { getString(url, retries = 0) }
                if (!raw.isNullOrBlank() && raw.trimStart().startsWith("{")) {
                    val j = JSONObject(raw)
                    val remoteVerName = j.optString("versionName")
                        .ifEmpty { j.optString("version") }
                        .ifEmpty { j.optString("tag_name") }
                        .removePrefix("v")
                        .removePrefix("V")
                        .ifEmpty { CURRENT_APP_VERSION_NAME }
                    val remoteVerCode = j.optInt("versionCode", 0)
                    val hasUpdate = (remoteVerCode > CURRENT_APP_VERSION_CODE) ||
                        (compareSemanticVersions(remoteVerName, CURRENT_APP_VERSION_NAME) > 0)
                    val rawApk = j.optString("apkUrl")
                        .ifEmpty { j.optString("downloadUrl") }
                        .ifEmpty { GITHUB_RELEASES_WEB_URL }
                    val mirrorApk = j.optString("mirrorUrl").ifEmpty { buildMirrorApkUrl(rawApk) }
                    val notes = j.optString("changelog")
                        .ifEmpty { j.optString("releaseNotes") }
                        .ifEmpty { j.optString("body") }
                        .ifEmpty { "1. 优化五大专区并发秒开与视频缓冲速度\n2. 新增清晰度切换与直屏短剧全屏自适应\n3. 支持云端源热更新与在线版本检测" }
                    val cost = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                    return@withContext AppUpdateCheckResult(
                        latestVersionName = remoteVerName,
                        latestVersionCode = if (remoteVerCode > 0) remoteVerCode else CURRENT_APP_VERSION_CODE,
                        hasNewAppVersion = hasUpdate,
                        forceUpdate = j.optBoolean("forceUpdate", false),
                        releaseTitle = j.optString("title").ifEmpty { "观影中心 v$remoteVerName" },
                        releaseNotes = notes,
                        publishedAt = j.optString("publishedAt").ifEmpty { j.optString("date", "最新构建") },
                        apkDownloadUrl = rawApk,
                        mirrorDownloadUrl = mirrorApk,
                        releasePageUrl = j.optString("releasePageUrl").ifEmpty { GITHUB_RELEASES_WEB_URL },
                        remoteSourceVersion = j.optString("sourceVersion", "云端同步就绪"),
                        checkLatencyMs = cost,
                        checkedEndpoint = url,
                        statusMessage = if (hasUpdate) {
                            "发现新版本 v$remoteVerName · 可一键在线更新"
                        } else {
                            "当前已是最新版本 v$CURRENT_APP_VERSION_NAME (${cost}ms)"
                        }
                    )
                }
            } catch (_: Exception) {
            }
        }

        // 2. Try GitHub Releases API (https://api.github.com/repos/berse1kguts-pixel/moviehub/releases/latest)
        try {
            val releaseRaw = withTimeoutOrNull(3500L) {
                getString(GITHUB_LATEST_RELEASE_API, "https://github.com/", retries = 0)
            }
            if (!releaseRaw.isNullOrBlank() && releaseRaw.trimStart().startsWith("{")) {
                val j = JSONObject(releaseRaw)
                val tagName = j.optString("tag_name").removePrefix("v").removePrefix("V")
                if (tagName.isNotEmpty()) {
                    val assets = j.optJSONArray("assets") ?: JSONArray()
                    var assetApkUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val dl = asset.optString("browser_download_url")
                        if (dl.endsWith(".apk", ignoreCase = true)) {
                            assetApkUrl = dl
                            break
                        }
                    }
                    val htmlUrl = j.optString("html_url").ifEmpty { GITHUB_RELEASES_WEB_URL }
                    val finalApkUrl = assetApkUrl.ifEmpty { htmlUrl }
                    val hasUpdate = compareSemanticVersions(tagName, CURRENT_APP_VERSION_NAME) > 0
                    val cost = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                    return@withContext AppUpdateCheckResult(
                        latestVersionName = tagName,
                        latestVersionCode = CURRENT_APP_VERSION_CODE + (if (hasUpdate) 1 else 0),
                        hasNewAppVersion = hasUpdate,
                        releaseTitle = j.optString("name").ifEmpty { "观影中心 v$tagName" },
                        releaseNotes = j.optString("body").ifEmpty { "GitHub Release 在线发布版本" },
                        publishedAt = j.optString("published_at").take(10),
                        apkDownloadUrl = finalApkUrl,
                        mirrorDownloadUrl = buildMirrorApkUrl(finalApkUrl),
                        releasePageUrl = htmlUrl,
                        remoteSourceVersion = "GitHub Release",
                        checkLatencyMs = cost,
                        checkedEndpoint = GITHUB_LATEST_RELEASE_API,
                        statusMessage = if (hasUpdate) {
                            "GitHub Release 发现新版本 v$tagName"
                        } else {
                            "当前已是最新版本 v$CURRENT_APP_VERSION_NAME (${cost}ms)"
                        }
                    )
                }
            }
        } catch (_: Exception) {
        }

        // 3. Fallback: Check remote sources.json on user's GitHub repo to verify online hot-update channel
        return@withContext try {
            val bundle = syncRemoteSources(DEFAULT_REMOTE_SOURCE_URL)
            val rootObj = try { JSONObject(bundle.rawJson) } catch (_: Exception) { JSONObject() }
            val embeddedAppVer = rootObj.optString("appVersion")
                .ifEmpty { rootObj.optString("versionName") }
                .removePrefix("v")
                .removePrefix("V")
            val hasUpdate = embeddedAppVer.isNotEmpty() &&
                compareSemanticVersions(embeddedAppVer, CURRENT_APP_VERSION_NAME) > 0
            val rawApk = rootObj.optString("apkUrl").ifEmpty { GITHUB_RELEASES_WEB_URL }
            val cost = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            AppUpdateCheckResult(
                latestVersionName = embeddedAppVer.ifEmpty { CURRENT_APP_VERSION_NAME },
                hasNewAppVersion = hasUpdate,
                releaseTitle = if (hasUpdate) "观影中心 v$embeddedAppVer" else "观影中心 v$CURRENT_APP_VERSION_NAME",
                releaseNotes = rootObj.optString("changelog").ifEmpty {
                    "• 远程源站 (${bundle.version}) 在线热更新通道正常\n" +
                        "• 已同步 ${bundle.vodSources.size} 个影视源 / ${bundle.liveChannels.size} 个直播源\n" +
                        "• 如需推送 APK 客户端升级，可在仓库根目录添加 version.json 或发布 GitHub Release"
                },
                publishedAt = "在线同步",
                apkDownloadUrl = rawApk,
                mirrorDownloadUrl = buildMirrorApkUrl(rawApk),
                releasePageUrl = GITHUB_RELEASES_WEB_URL,
                remoteSourceVersion = bundle.version,
                checkLatencyMs = cost,
                checkedEndpoint = DEFAULT_REMOTE_SOURCE_URL,
                statusMessage = if (hasUpdate) {
                    "发现新版本 v$embeddedAppVer · 可一键在线更新"
                } else {
                    "当前已是最新版 v$CURRENT_APP_VERSION_NAME · 云端热更新源已同步 (${cost}ms)"
                }
            )
        } catch (e: Exception) {
            val cost = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            AppUpdateCheckResult(
                checkLatencyMs = cost,
                checkedEndpoint = DEFAULT_UPDATE_MANIFEST_URL,
                releaseNotes = "当前客户端版本 v$CURRENT_APP_VERSION_NAME (Build $CURRENT_APP_VERSION_CODE)\n可通过 GitHub Releases 或自定义 version.json 在线发布更新。",
                statusMessage = "当前版本 v$CURRENT_APP_VERSION_NAME · 暂未检测到更高版本"
            )
        }
    }

    data class GitHubPublishResult(
        val success: Boolean,
        val path: String,
        val htmlUrl: String = "",
        val message: String
    )

    fun buildVersionManifestJson(
        versionName: String,
        versionCode: Int,
        apkUrl: String,
        changelog: String
    ): String {
        val cleanVer = versionName.trim().removePrefix("v").removePrefix("V").ifEmpty { CURRENT_APP_VERSION_NAME }
        val cleanApk = apkUrl.trim().ifEmpty {
            "https://github.com/berse1kguts-pixel/moviehub/releases/latest/download/moviehub-release.apk"
        }
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        return JSONObject().apply {
            put("versionName", cleanVer)
            put("versionCode", versionCode)
            put("forceUpdate", false)
            put("title", "观影中心 v$cleanVer 正式版")
            put("publishedAt", dateStr)
            put("apkUrl", cleanApk)
            put("mirrorUrl", buildMirrorApkUrl(cleanApk))
            put("releasePageUrl", GITHUB_RELEASES_WEB_URL)
            put("sourceVersion", dateStr.replace("-", "."))
            put("changelog", changelog.trim())
        }.toString(2)
    }

    fun buildRemoteSourcesJson(
        vodSources: List<VodSource>,
        liveChannels: List<LiveTvChannel>,
        lxScripts: List<LxScriptSource>
    ): String {
        val verStamp = java.text.SimpleDateFormat("yyyy.MM.dd-HHmm", java.util.Locale.getDefault())
            .format(java.util.Date())
        val vodArr = JSONArray()
        vodSources.forEach { s ->
            vodArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("api", s.api)
                    put("enabled", s.enabled)
                }
            )
        }
        val liveArr = JSONArray()
        liveChannels.forEach { ch ->
            liveArr.put(
                JSONObject().apply {
                    put("name", ch.name)
                    put("group", ch.group)
                    put("url", ch.url)
                }
            )
        }
        val lxArr = JSONArray()
        lxScripts.forEach { lx ->
            lxArr.put(
                JSONObject().apply {
                    put("id", lx.id)
                    put("name", lx.name)
                    put("url", lx.url)
                    put("enabled", lx.enabled)
                }
            )
        }
        return JSONObject().apply {
            put("version", verStamp)
            put("appVersion", CURRENT_APP_VERSION_NAME)
            put("vodSources", vodArr)
            put("liveChannels", liveArr)
            put("lxScripts", lxArr)
        }.toString(2)
    }

    /**
     * Directly commits/updates a file (`version.json` or `moviehub/sources.json`) to the user's GitHub repository
     * (`berse1kguts-pixel/moviehub`) via GitHub Contents REST API using a Personal Access Token.
     */
    suspend fun publishFileToGitHub(
        githubToken: String,
        repoFullName: String = "berse1kguts-pixel/moviehub",
        filePath: String,
        fileContent: String,
        commitMessage: String
    ): GitHubPublishResult = withContext(Dispatchers.IO) {
        val token = githubToken.trim()
        if (token.isEmpty()) {
            return@withContext GitHubPublishResult(
                success = false,
                path = filePath,
                message = "请先填写 GitHub Personal Access Token (ghp_...)"
            )
        }
        val cleanRepo = repoFullName.trim().removePrefix("https://github.com/").trim('/')
            .ifEmpty { "berse1kguts-pixel/moviehub" }
        val cleanPath = filePath.trim().trimStart('/')
        val apiUrl = "https://api.github.com/repos/$cleanRepo/contents/$cleanPath"

        try {
            // Step 1: Check if file already exists to get its current `sha`
            var existingSha: String? = null
            val getReq = Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MovieHub-Android-Updater")
                .get()
                .build()
            okHttpClient.newCall(getReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    if (bodyStr.startsWith("{")) {
                        existingSha = JSONObject(bodyStr).optString("sha").takeIf { it.isNotBlank() }
                    }
                }
            }

            // Step 2: PUT base64-encoded content to GitHub repository
            val encodedBase64 = Base64.encodeToString(
                fileContent.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            val payload = JSONObject().apply {
                put("message", commitMessage)
                put("content", encodedBase64)
                if (!existingSha.isNullOrBlank()) {
                    put("sha", existingSha)
                }
            }
            val putReq = Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MovieHub-Android-Updater")
                .put(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(putReq).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val htmlUrl = try {
                        JSONObject(respBody).optJSONObject("content")?.optString("html_url").orEmpty()
                    } catch (_: Exception) {
                        "https://github.com/$cleanRepo/blob/main/$cleanPath"
                    }
                    return@withContext GitHubPublishResult(
                        success = true,
                        path = cleanPath,
                        htmlUrl = htmlUrl,
                        message = "✅ 已成功直推到 GitHub 仓库 ($cleanRepo/$cleanPath)！"
                    )
                } else {
                    val errMsg = try {
                        JSONObject(respBody).optString("message").ifEmpty { "HTTP ${resp.code}" }
                    } catch (_: Exception) {
                        "HTTP ${resp.code}"
                    }
                    return@withContext GitHubPublishResult(
                        success = false,
                        path = cleanPath,
                        message = "上传失败 ($errMsg)，请检查 Token 是否勾选 repo 权限"
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext GitHubPublishResult(
                success = false,
                path = cleanPath,
                message = "网络请求失败：${e.message}"
            )
        }
    }
}
