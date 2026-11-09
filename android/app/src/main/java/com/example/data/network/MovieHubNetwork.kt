package com.example.data.network

import android.net.Uri
import android.util.Base64
import com.example.data.model.CategoryNode
import com.example.data.model.CategoryTree
import com.example.data.model.DoubanSubject
import com.example.data.model.MovieHubData
import com.example.data.model.MusicTrack
import com.example.data.model.ResolvedMusicUrl
import com.example.data.model.VodItem
import com.example.data.model.VodSource
import com.example.data.model.VodSourceMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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

    val okHttpClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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

    private fun mapVodJsonItem(v: JSONObject, s: VodSource): VodItem {
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
            typeName = typeName
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
        limit: Int = 24
    ): List<DoubanSubject> = withContext(Dispatchers.IO) {
        val normType = if (type == "tv") "tv" else "movie"
        val key = "$normType|$tag|$limit"
        val cached = doubanCache[key]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 15 * 60 * 1000L) {
            return@withContext cached.data
        }

        val items = mutableListOf<DoubanSubject>()
        val seen = HashSet<String>()

        fun pushSubject(s: JSONObject?) {
            if (s == null) return
            val title = s.optString("title").trim()
            if (title.isEmpty()) return
            val id = s.optString("id").ifEmpty { s.optString("uri").ifEmpty { title } }
            if (!seen.add(id)) return
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

        try {
            // Try rexxar modules endpoint first (same as moviehub/server.js)
            val url = "https://m.douban.com/rexxar/api/v2/$normType/modules?type=${enc(normType)}&tag=${enc(tag)}&page_limit=$limit&page_start=0"
            val json = getJsonObject(url, "https://m.douban.com/", retries = 1)
            val modules = json.optJSONArray("modules") ?: JSONArray()
            for (i in 0 until modules.length()) {
                val m = modules.optJSONObject(i) ?: continue
                val d = m.optJSONObject("data") ?: continue
                d.optJSONArray("subjects")?.let { arr ->
                    for (j in 0 until arr.length()) pushSubject(arr.optJSONObject(j))
                }
                d.optJSONArray("selected_collections")?.let { cols ->
                    for (j in 0 until cols.length()) {
                        val c = cols.optJSONObject(j)
                        c?.optJSONArray("items")?.let { arr ->
                            for (k in 0 until arr.length()) pushSubject(arr.optJSONObject(k))
                        }
                    }
                }
                d.optJSONArray("subject_collection_boards")?.let { boards ->
                    for (j in 0 until boards.length()) {
                        val b = boards.optJSONObject(j)
                        b?.optJSONArray("items")?.let { arr ->
                            for (k in 0 until arr.length()) pushSubject(arr.optJSONObject(k))
                        }
                        b?.optJSONObject("subject_collection")?.optJSONArray("items")?.let { arr ->
                            for (k in 0 until arr.length()) pushSubject(arr.optJSONObject(k))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to Douban j/search_subjects endpoint if rexxar is blocked
            try {
                val fallbackUrl = "https://movie.douban.com/j/search_subjects?type=${enc(normType)}&tag=${enc(tag)}&page_limit=$limit&page_start=0"
                val json = getJsonObject(fallbackUrl, "https://movie.douban.com/", retries = 0)
                val subjects = json.optJSONArray("subjects") ?: JSONArray()
                for (i in 0 until subjects.length()) {
                    pushSubject(subjects.optJSONObject(i))
                }
            } catch (_: Exception) {
            }
        }

        if (items.isNotEmpty()) {
            val result = items.take(limit + 8)
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
                    items.add(mapVodJsonItem(obj, s))
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
            items.add(mapVodJsonItem(obj, source))
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
                withTimeoutOrNull(11000L) {
                    try {
                        var j = getJsonObject(buildApiUrl(s.api, mapOf("ac" to "detail", "wd" to wd)), s.api, retries = 0)
                        var arr = j.optJSONArray("list") ?: JSONArray()
                        if (arr.length() == 0) {
                            j = getJsonObject(buildApiUrl(s.api, mapOf("ac" to "videolist", "wd" to wd)), s.api, retries = 0)
                            arr = j.optJSONArray("list") ?: JSONArray()
                        }
                        val out = mutableListOf<VodItem>()
                        for (i in 0 until minOf(arr.length(), 35)) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val mapped = mapVodJsonItem(obj, s)
                            if (mapped.lines.isNotEmpty()) {
                                out.add(mapped)
                            }
                        }
                        out
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
                    typeName = it.typeName
                )
                if (existing == null) {
                    merged[key] = it.copy(sources = listOf(match))
                } else {
                    val updatedSources = if (existing.sources.any { m -> m.sourceId == match.sourceId && m.vodId == match.vodId }) {
                        existing.sources
                    } else {
                        existing.sources + match
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

    suspend fun fetchVodDetail(source: VodSource, vodId: String): VodItem = withContext(Dispatchers.IO) {
        val url = buildApiUrl(source.api, mapOf("ac" to "detail", "ids" to vodId))
        val j = getJsonObject(url, source.api, retries = 1)
        val arr = j.optJSONArray("list") ?: JSONArray()
        if (arr.length() == 0) throw IllegalStateException("影片不存在或已下架")
        mapVodJsonItem(arr.getJSONObject(0), source)
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
                if (id <= 0 || name.isEmpty()) continue
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
                    if (id > 0 && name.isNotEmpty()) {
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
}
