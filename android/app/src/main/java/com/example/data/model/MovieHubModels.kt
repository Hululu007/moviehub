package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class VodSource(
    val id: String,
    val name: String,
    val api: String,
    val enabled: Boolean = true
)

data class VodEpisode(
    val title: String,
    val url: String
)

data class VodPlayLine(
    val flag: String,
    val episodes: List<VodEpisode>
)

data class VodSourceMatch(
    val sourceId: String,
    val sourceName: String,
    val vodId: String,
    val lines: List<VodPlayLine> = emptyList(),
    val pic: String = "",
    val remarks: String = "",
    val content: String = "",
    val year: String = "",
    val area: String = "",
    val actor: String = "",
    val director: String = "",
    val typeName: String = "",
    val latencyMs: Long = 0L
)

data class VodItem(
    val name: String,
    val pic: String = "",
    val remarks: String = "",
    val content: String = "",
    val year: String = "",
    val area: String = "",
    val actor: String = "",
    val director: String = "",
    val typeName: String = "",
    val source: String = "",
    val sourceName: String = "",
    val vodId: String = "",
    val lines: List<VodPlayLine> = emptyList(),
    val sources: List<VodSourceMatch> = emptyList()
) {
    val multiSourceCount: Int
        get() = if (sources.isNotEmpty()) sources.size else 1
}

data class DoubanSubject(
    val id: String,
    val title: String,
    val rate: Double,
    val year: String,
    val cover: String,
    val url: String = "",
    val subtitle: String = ""
)

data class CategoryNode(
    val id: Int,
    val pid: Int,
    val name: String,
    val count: Int = 0,
    val isParent: Boolean = false
)

data class CategoryGroup(
    val pid: Int,
    val name: String,
    val items: List<CategoryNode>
)

data class CategoryTree(
    val srcId: String,
    val srcName: String,
    val version: Int = 1,
    val done: Boolean = true,
    val types: List<CategoryNode> = emptyList(),
    val groups: List<CategoryGroup> = emptyList(),
    val top: List<CategoryNode> = emptyList()
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("srcId", srcId)
        root.put("srcName", srcName)
        root.put("version", version)
        root.put("done", done)
        val typesArr = JSONArray()
        types.forEach { t ->
            typesArr.put(JSONObject().apply {
                put("id", t.id)
                put("pid", t.pid)
                put("name", t.name)
                put("n", t.count)
                put("isParent", t.isParent)
            })
        }
        root.put("types", typesArr)
        return root.toString()
    }

    companion object {
        fun fromJsonString(json: String): CategoryTree? {
            return try {
                val obj = JSONObject(json)
                val srcId = obj.optString("srcId")
                val srcName = obj.optString("srcName")
                val arr = obj.optJSONArray("types") ?: JSONArray()
                val types = mutableListOf<CategoryNode>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    types.add(
                        CategoryNode(
                            id = item.optInt("id"),
                            pid = item.optInt("pid"),
                            name = item.optString("name"),
                            count = item.optInt("n"),
                            isParent = item.optBoolean("isParent")
                        )
                    )
                }
                val cleanTypes = types.filterNot { MovieHubData.isBlockedCategoryOrTitle(it.name) }
                MovieHubData.buildCategoryTree(srcId, srcName, cleanTypes, done = true)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class LiveTvChannel(
    val id: String,
    val name: String,
    val group: String,
    val url: String,
    val backupUrls: List<String> = emptyList()
)

data class MusicPlatform(
    val key: String,
    val name: String,
    val search: Boolean,
    val url: Boolean,
    val note: String
)

data class LxScriptSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true
)

data class MusicTrack(
    val id: String,
    val platform: String,
    val name: String,
    val singer: String,
    val album: String = "",
    val duration: Int = 0,
    val pic: String = "",
    val hash: String = "",
    val albumId: String = "",
    val rid: String = "",
    val directUrl: String = ""
) {
    val uniqueKey: String
        get() = "${platform}_$id"
}

data class ResolvedMusicUrl(
    val url: String,
    val quality: String,
    val via: String
)

data class LrcLine(
    val timeSec: Double,
    val text: String
)

enum class PlayMode(val code: String, val label: String, val iconText: String) {
    LIST_LOOP("list", "列表循环", "🔁"),
    SINGLE_LOOP("one", "单曲循环", "🔂"),
    SHUFFLE("shuffle", "随机播放", "🔀");

    fun next(): PlayMode = when (this) {
        LIST_LOOP -> SINGLE_LOOP
        SINGLE_LOOP -> SHUFFLE
        SHUFFLE -> LIST_LOOP
    }

    companion object {
        fun fromCode(code: String): PlayMode = entries.find { it.code == code } ?: LIST_LOOP
    }
}

object MovieHubData {
    // 15 AppleCMS V10 VOD Sources from moviehub/sources.json
    val DEFAULT_VOD_SOURCES = listOf(
        VodSource("360zy", "360资源", "https://360zy.com/api.php/provide/vod/", true),
        VodSource("bfzy", "百度资源", "https://bfzyapi.com/api.php/provide/vod/", true),
        VodSource("ffzy", "非凡资源", "https://cj.ffzyapi.com/api.php/provide/vod/", true),
        VodSource("lzzy", "量子资源", "https://cj.lziapi.com/api.php/provide/vod/", true),
        VodSource("snzy", "索尼资源", "https://suoniapi.com/api.php/provide/vod/", true),
        VodSource("hhzy", "哈哈资源", "https://hhzyapi.com/api.php/provide/vod/", true),
        VodSource("ikunzy", "iKun资源", "https://ikunzyapi.com/api.php/provide/vod/", true),
        VodSource("jyzy", "金鹰资源", "https://jyzyapi.com/api.php/provide/vod/", true),
        VodSource("wjzy", "无尽资源", "https://api.wujinapi.net/api.php/provide/vod/", true),
        VodSource("mtzy", "MT资源", "https://mtzy.me/api.php/provide/vod/", true),
        VodSource("lzzy2", "量子备用", "https://lzizy1.com/api.php/provide/vod/", true),
        VodSource("subo", "速播资源", "https://subocaiji.com/api.php/provide/vod/", true),
        VodSource("hyzy", "虎牙资源", "https://www.huyaapi.com/api.php/provide/vod/", true),
        VodSource("ffzy2", "非凡备用", "http://ffzy1.tv/api.php/provide/vod/", true),
        VodSource("ttzy", "天堂资源", "http://caiji.dyttzyapi.com/api.php/provide/vod/", true)
    )

    val PREFERRED_BROWSE_SOURCE_IDS = listOf("bfzy", "ffzy", "lzzy", "snzy", "ikunzy", "360zy")

    val DOUBAN_MOVIE_TAGS = listOf("热门", "最新", "豆瓣高分", "冷门佳片", "经典", "科幻", "动作", "悬疑", "喜剧", "爱情")
    val DOUBAN_TV_TAGS = listOf("热门", "国产剧", "美剧", "英剧", "韩剧", "日剧", "港剧", "悬疑剧")
    val DOUBAN_ANIME_TAGS = listOf("日本动画", "国产动画", "剧场版", "新番", "经典动画", "热血", "奇幻")
    val DOUBAN_DOC_TAGS = listOf("纪录片", "自然", "历史", "人文", "BBC", "美食", "科技", "社会")
    val DOUBAN_SHORT_TAGS = listOf("短剧", "爽剧", "逆袭", "甜宠", "重生", "都市", "古装", "悬疑")

    data class VodChannelSection(
        val key: String,
        val title: String,
        val subtitle: String,
        val tags: List<String>
    )

    val VOD_MAJOR_CHANNELS = listOf(
        VodChannelSection("movie", "电影", "院线大片 · 高分佳作", DOUBAN_MOVIE_TAGS),
        VodChannelSection("tv", "电视剧", "热播剧集 · 全球同步", DOUBAN_TV_TAGS),
        VodChannelSection("anime", "动漫", "新番连载 · 动画剧场", DOUBAN_ANIME_TAGS),
        VodChannelSection("doc", "纪录片", "自然人文 · 真实纪实", DOUBAN_DOC_TAGS),
        VodChannelSection("short", "短剧", "竖屏爽剧 · 极速连播", DOUBAN_SHORT_TAGS)
    )

    private val BLOCKED_CATEGORY_KEYWORDS = listOf(
        "福利", "伦理", "写真", "美女", "热舞", "街拍", "主播", "三级",
        "理论", "情色", "成人", "里番", "同性", "激情", "有码", "无码",
        "中文字幕", "日韩精品", "国产精品", "欧美精品", "自拍", "偷拍",
        "诱惑", "性感", "大尺度", "18+", "禁片", "限制级", "伦理片", "福利片", "福利姬"
    )

    /**
     * Strictly filters out "福利" / adult / NSFW categories and items.
     */
    fun isBlockedCategoryOrTitle(typeName: String?, title: String? = null): Boolean {
        val t = typeName?.trim()?.lowercase().orEmpty()
        val n = title?.trim()?.lowercase().orEmpty()
        if (t.isEmpty() && n.isEmpty()) return false
        return BLOCKED_CATEGORY_KEYWORDS.any { kw ->
            t.contains(kw) || n.contains(kw)
        }
    }

    /**
     * Maps any CMS category name (`type_name`) into one of the 5 dedicated channels:
     * "movie" (电影), "tv" (电视剧), "anime" (动漫), "doc" (纪录片), "short" (短剧), or "other".
     */
    fun classifyVodChannel(typeName: String?, title: String? = null): String {
        val text = "${typeName.orEmpty()} ${title.orEmpty()}".lowercase()
        return when {
            text.contains("短剧") || text.contains("微短剧") || text.contains("爽剧") ||
                text.contains("竖屏") || text.contains("短片") || text.contains("skit") -> "short"
            text.contains("纪录") || text.contains("记录") || text.contains("纪实") ||
                text.contains("科教") || text.contains("自然") || text.contains("bbc") -> "doc"
            text.contains("动漫") || text.contains("动画") || text.contains("番剧") ||
                text.contains("新番") || text.contains("国漫") || text.contains("日漫") -> "anime"
            text.contains("剧") || text.contains("连续") || text.contains("电视") ||
                text.contains("美剧") || text.contains("韩剧") || text.contains("日剧") ||
                text.contains("国产剧") || text.contains("欧美剧") || text.contains("港剧") ||
                text.contains("台剧") || text.contains("泰剧") || text.contains("海外剧") -> "tv"
            text.contains("电影") || text.contains("片") || text.contains("动作") ||
                text.contains("喜剧") || text.contains("爱情") || text.contains("科幻") ||
                text.contains("恐怖") || text.contains("剧情") || text.contains("战争") ||
                text.contains("悬疑") || text.contains("犯罪") || text.contains("奇幻") ||
                text.contains("冒险") || text.contains("武侠") || text.contains("古装") -> "movie"
            else -> "other"
        }
    }

    fun isLikelyPortraitShortDrama(
        typeName: String?,
        title: String? = null,
        remarks: String? = null
    ): Boolean {
        val text = "${typeName.orEmpty()} ${title.orEmpty()} ${remarks.orEmpty()}".lowercase()
        return text.contains("短剧") ||
            text.contains("微短剧") ||
            text.contains("爽剧") ||
            text.contains("竖屏")
    }

    val CATEGORY_YEARS = listOf(
        "", "2026", "2025", "2024", "2023", "2022", "2021",
        "2020", "2019", "2018", "2015", "2010", "2005", "2000"
    )

    val CATEGORY_ORDERS = listOf(
        "" to "最新",
        "hits" to "最热",
        "score" to "评分",
        "up" to "最近更新"
    )

    val MUSIC_PLATFORMS = listOf(
        MusicPlatform("wy", "网易云音乐", search = true, url = true, note = "搜索/播放/歌词 全支持"),
        MusicPlatform("kw", "酷我音乐", search = true, url = true, note = "移动端搜索 + antiserver 直链"),
        MusicPlatform("kg", "酷狗音乐", search = true, url = false, note = "支持搜索与歌词，跨平台智能匹配播放"),
        MusicPlatform("tx", "QQ音乐", search = true, url = false, note = "支持搜索与歌词，跨平台智能匹配播放"),
        MusicPlatform("mg", "咪咕音乐", search = true, url = false, note = "支持搜索与歌词，跨平台智能匹配播放")
    )

    val DEFAULT_LX_SCRIPTS = listOf(
        LxScriptSource(
            id = "sixyin",
            name = "六音音源",
            url = "https://raw.githubusercontent.com/pdone/lx-music-source/main/sixyin/latest.js",
            enabled = true
        ),
        LxScriptSource(
            id = "huibq",
            name = "Huibq音源",
            url = "https://raw.githubusercontent.com/pdone/lx-music-source/main/huibq/latest.js",
            enabled = true
        ),
        LxScriptSource(
            id = "gdstudio",
            name = "GD音源（备用）",
            url = "https://raw.githubusercontent.com/wangxanshen/lx-music-source/main/gdstudio-source.js",
            enabled = false
        )
    )

    // 54 Tested Live TV Channels from cn-tv-tested.m3u in berse1kguts-pixel/moviehub
    val LIVE_TV_CHANNELS = listOf(
        LiveTvChannel("cctv1", "CCTV-1 综合", "央视", "http://36.136.38.87:9901/tsfile/live/0001_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv2", "CCTV-2 财经", "央视", "http://36.136.38.87:9901/tsfile/live/0002_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv3", "CCTV-3 综艺", "央视", "http://125.125.236.31:9901/tsfile/live/0003_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv4", "CCTV-4 中文国际", "央视", "http://36.136.38.87:9901/tsfile/live/0004_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv5", "CCTV-5 体育", "央视", "http://125.125.236.31:9901/tsfile/live/0005_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv5p", "CCTV-5+ 体育赛事", "央视", "http://125.125.236.31:9901/tsfile/live/0016_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv6", "CCTV-6 电影", "央视", "http://36.136.38.87:9901/tsfile/live/0006_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv7", "CCTV-7 国防军事", "央视", "http://36.136.38.87:9901/tsfile/live/0007_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv8", "CCTV-8 电视剧", "央视", "http://125.125.236.31:9901/tsfile/live/0008_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv9", "CCTV-9 纪录", "央视", "http://36.136.38.87:9901/tsfile/live/0009_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv10", "CCTV-10 科教", "央视", "http://36.136.38.87:9901/tsfile/live/0010_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv11", "CCTV-11 戏曲", "央视", "http://36.136.38.87:9901/tsfile/live/0011_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv12", "CCTV-12 社会与法", "央视", "http://36.136.38.87:9901/tsfile/live/0012_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv13", "CCTV-13 新闻", "央视", "http://36.136.38.87:9901/tsfile/live/0013_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv14", "CCTV-14 少儿", "央视", "http://36.136.38.87:9901/tsfile/live/0014_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cctv15", "CCTV-15 音乐", "央视", "http://36.136.38.87:9901/tsfile/live/0015_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("bjws", "北京卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0122_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("dfws", "东方卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0107_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("zjws", "浙江卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0124_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("jsws", "江苏卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0127_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("sdws", "山东卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0131_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("gdws", "广东卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0125_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("szws", "深圳卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0126_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("scws", "四川卫视", "卫视", "http://125.125.236.31:9901/tsfile/live/0123_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("tjws", "天津卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0135_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("dnws", "东南卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0137_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("ahws", "安徽卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0130_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("lnws", "辽宁卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0121_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("cqws", "重庆卫视", "卫视", "http://125.125.236.31:9901/tsfile/live/0142_1.m3u8?key=txiptv&playlive=1&authid=0"),
        LiveTvChannel("gzws", "贵州卫视", "卫视", "http://36.136.38.87:9901/tsfile/live/0120_1.m3u8?key=txiptv&playlive=1&authid=0")
    )

    private val BRACKET_REGEX = Regex("[（(【\\[\\{][^)）】\\]\\}]*[)）】\\]\\}]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val TAG_REGEX = Regex(
        "(4k|2160p|1080p|720p|hd|bd|国语|粤语|英语|中字|高清|超清|蓝光|无删减|完整版|正片|预告|纪录片)",
        RegexOption.IGNORE_CASE
    )
    private val HTML_TAG_REGEX = Regex("<[^>]*>")

    /** Ported from moviehub/server.js normalizeName() */
    fun normalizeName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(BRACKET_REGEX, "")
            .replace(WHITESPACE_REGEX, "")
            .replace(TAG_REGEX, "")
            .lowercase()
    }

    fun stripHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace(HTML_TAG_REGEX, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    /**
     * Ported from moviehub/server.js parsePlayGroups(playFrom, playUrl)
     * Format: 线路A$集1#集2#集3$$$线路B$集1#集2
     */
    fun parsePlayGroups(playFrom: String?, playUrl: String?): List<VodPlayLine> {
        if (playUrl.isNullOrBlank()) return emptyList()
        val fromFlags = playFrom?.split("$$$")?.map { it.trim() } ?: emptyList()
        val rawGroups = playUrl.split("$$$")
        val lines = mutableListOf<VodPlayLine>()
        rawGroups.forEachIndexed { idx, raw ->
            val g = raw.trim()
            if (g.isEmpty()) return@forEachIndexed
            var flag = fromFlags.getOrNull(idx)?.takeIf { it.isNotEmpty() } ?: "线路${idx + 1}"
            var listStr = g
            if (!g.startsWith("http://", ignoreCase = true) && !g.startsWith("https://", ignoreCase = true)) {
                val dollarIdx = g.indexOf('$')
                if (dollarIdx > 0 && !g.substring(0, dollarIdx).contains('#')) {
                    val maybeAfter = g.substring(dollarIdx + 1)
                    if (maybeAfter.contains('$') || maybeAfter.startsWith("http", ignoreCase = true)) {
                        // Check if prefix is line name vs episode title
                        val prefix = g.substring(0, dollarIdx).trim()
                        if (!maybeAfter.startsWith("http", ignoreCase = true)) {
                            flag = prefix.ifEmpty { flag }
                            listStr = maybeAfter
                        }
                    }
                }
            }
            val episodes = mutableListOf<VodEpisode>()
            for (seg in listStr.split('#')) {
                val s = seg.trim()
                if (s.isEmpty()) continue
                var epTitle = "第${episodes.size + 1}集"
                var u = s
                val j = s.lastIndexOf('$')
                if (j > 0) {
                    val maybe = s.substring(j + 1).trim()
                    if (maybe.startsWith("http://", ignoreCase = true) || maybe.startsWith("https://", ignoreCase = true)) {
                        epTitle = s.substring(0, j).trim().ifEmpty { epTitle }
                        u = maybe
                    }
                }
                if (u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)) {
                    episodes.add(VodEpisode(title = epTitle, url = u))
                }
            }
            if (episodes.isNotEmpty()) {
                // Prioritize m3u8 lines first
                lines.add(VodPlayLine(flag = flag, episodes = episodes))
            }
        }
        return lines.sortedByDescending { line ->
            line.episodes.any { it.url.contains(".m3u8", ignoreCase = true) }
        }
    }

    /** Ported from moviehub/server.js buildTree(types) — with 福利/NSFW stripped out */
    fun buildCategoryTree(
        srcId: String,
        srcName: String,
        types: List<CategoryNode>,
        version: Int = 2,
        done: Boolean = true
    ): CategoryTree {
        val cleanTypes = types.filterNot { isBlockedCategoryOrTitle(it.name) }
        val byPid = cleanTypes.groupBy { it.pid }
        val nameOf = { pid: Int ->
            cleanTypes.find { it.id == pid }?.name ?: when (pid) {
                1 -> "电影"
                2 -> "电视剧"
                3 -> "综艺"
                4 -> "动漫"
                else -> "分组$pid"
            }
        }
        val groups = byPid.entries
            .filter { it.key != 0 }
            .mapNotNull { (pid, list) ->
                val groupName = nameOf(pid)
                if (isBlockedCategoryOrTitle(groupName)) {
                    null
                } else {
                    val cleanItems = list.filterNot { isBlockedCategoryOrTitle(it.name) }
                        .sortedByDescending { it.count }
                    if (cleanItems.isEmpty()) null
                    else CategoryGroup(
                        pid = pid,
                        name = groupName,
                        items = cleanItems
                    )
                }
            }
            .sortedByDescending { it.items.size }
        val top = (byPid[0] ?: emptyList())
            .filterNot { isBlockedCategoryOrTitle(it.name) }
            .filter { node -> groups.none { it.pid == node.id } }
            .sortedByDescending { it.count }
        return CategoryTree(
            srcId = srcId,
            srcName = srcName,
            version = version,
            done = done,
            types = cleanTypes,
            groups = groups,
            top = top
        )
    }

    /** Ported from moviehub/public/music.js parseLrc(raw) */
    fun parseLrc(raw: String): List<LrcLine> {
        if (raw.isBlank()) return emptyList()
        val out = mutableListOf<LrcLine>()
        val timeRegex = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
        val karaokeRegex = Regex("<\\d+,\\d+,\\d+>")
        for (line in raw.lineSequence()) {
            val matches = timeRegex.findAll(line).toList()
            if (matches.isEmpty()) continue
            val text = line.replace(timeRegex, "").replace(karaokeRegex, "").trim()
            for (m in matches) {
                val min = m.groupValues[1].toDoubleOrNull() ?: 0.0
                val sec = m.groupValues[2].toDoubleOrNull() ?: 0.0
                out.add(LrcLine(timeSec = min * 60.0 + sec, text = text.ifEmpty { "♪" }))
            }
        }
        return out.sortedBy { it.timeSec }
    }
}
