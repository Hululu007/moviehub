package com.moviehub.server.service;

import com.moviehub.server.dto.LiveChannelDto;
import com.moviehub.server.dto.MusicScriptDto;
import com.moviehub.server.dto.SourceBundleDto;
import com.moviehub.server.dto.VodSourceDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 从 MySQL / TiDB 读取片源、直播频道、音源脚本，并按 Android 客户端约定的
 * {@code sources.json} 结构组装整包。
 *
 * <p>数据库不可用或表为空时回退到 {@link DefaultSources}，保证客户端解析不会拿到空数组。
 */
@Service
public class SourceQueryService {

    private static final Logger log = LoggerFactory.getLogger(SourceQueryService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private volatile boolean dbAvailable = true;

    public SourceQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 组装客户端整包；任何数据库异常都回退到内置默认值。 */
    public SourceBundleDto buildBundle() {
        try {
            List<VodSourceDto> vod = queryVodSources();
            List<LiveChannelDto> live = queryLiveChannels();
            List<MusicScriptDto> scripts = queryMusicScripts();
            String version = readMeta("bundle_version", "2.0.0");
            String updatedAt = latestTimestamp();

            if (vod.isEmpty() && live.isEmpty() && scripts.isEmpty()) {
                // 表存在但为空：回退默认值，避免客户端解析成空包
                return DefaultSources.fallback(version, updatedAt, "database-empty");
            }
            dbAvailable = true;
            return new SourceBundleDto(version, updatedAt, vod, live, scripts, "database", 0);
        } catch (Exception e) {
            log.warn("读取数据库失败，返回内置默认源: {}", e.getMessage());
            dbAvailable = false;
            return DefaultSources.fallback(DefaultSources.DEFAULT_VERSION, now(), "database-unavailable");
        }
    }

    public List<VodSourceDto> queryVodSources() {
        return jdbc.query(
                "SELECT source_key, name, api_url, enabled, sort_order, latency_ms, health_status "
                        + "FROM vod_source ORDER BY sort_order ASC, id ASC",
                (rs, i) -> new VodSourceDto(
                        rs.getString("source_key"),
                        rs.getString("name"),
                        rs.getString("api_url"),
                        rs.getBoolean("enabled"),
                        rs.getInt("latency_ms"),
                        rs.getString("health_status")));
    }

    public List<LiveChannelDto> queryLiveChannels() {
        return jdbc.query(
                "SELECT channel_code, name, group_name, stream_url, enabled "
                        + "FROM live_channel ORDER BY sort_order ASC, id ASC",
                (rs, i) -> new LiveChannelDto(
                        rs.getString("channel_code"),
                        rs.getString("name"),
                        rs.getString("group_name"),
                        rs.getString("stream_url"),
                        rs.getBoolean("enabled")));
    }

    public List<MusicScriptDto> queryMusicScripts() {
        return jdbc.query(
                "SELECT script_key, name, script_url, enabled FROM music_script ORDER BY sort_order ASC, id ASC",
                (rs, i) -> new MusicScriptDto(
                        rs.getString("script_key"),
                        rs.getString("name"),
                        rs.getString("script_url"),
                        rs.getBoolean("enabled")));
    }

    public String readMeta(String key, String fallback) {
        try {
            List<String> rows = jdbc.queryForList(
                    "SELECT meta_value FROM app_meta WHERE meta_key = ?", String.class, key);
            return rows.isEmpty() || rows.get(0) == null ? fallback : rows.get(0);
        } catch (Exception e) {
            return fallback;
        }
    }

    public void writeMeta(String key, String value) {
        jdbc.update(
                "INSERT INTO app_meta (meta_key, meta_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)",
                key, value);
    }

    /** 批量覆盖片源：按 source_key upsert，未出现的条目保留。 */
    public int upsertVodSources(List<VodSourceDto> sources) {
        if (sources == null || sources.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (VodSourceDto s : sources) {
            if (s.id() == null || s.id().isBlank() || s.api() == null || s.api().isBlank()) {
                continue;
            }
            affected += jdbc.update(
                    "INSERT INTO vod_source (source_key, name, api_url, enabled, sort_order) "
                            + "VALUES (?, ?, ?, ?, 100) "
                            + "ON DUPLICATE KEY UPDATE name = VALUES(name), api_url = VALUES(api_url), "
                            + "enabled = VALUES(enabled)",
                    s.id(), s.name() == null ? s.id() : s.name(), s.api(), s.enabled());
        }
        touchBundleVersion();
        return affected;
    }

    public int upsertLiveChannels(List<LiveChannelDto> channels) {
        if (channels == null || channels.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (LiveChannelDto c : channels) {
            if (c.id() == null || c.id().isBlank() || c.url() == null || c.url().isBlank()) {
                continue;
            }
            affected += jdbc.update(
                    "INSERT INTO live_channel (channel_code, name, group_name, stream_url, enabled, sort_order) "
                            + "VALUES (?, ?, ?, ?, ?, 100) "
                            + "ON DUPLICATE KEY UPDATE name = VALUES(name), group_name = VALUES(group_name), "
                            + "stream_url = VALUES(stream_url), enabled = VALUES(enabled)",
                    c.id(), c.name() == null ? c.id() : c.name(),
                    c.group() == null ? "云端直播" : c.group(), c.url(), c.enabled());
        }
        touchBundleVersion();
        return affected;
    }

    public int upsertMusicScripts(List<MusicScriptDto> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (MusicScriptDto m : scripts) {
            if (m.id() == null || m.id().isBlank() || m.url() == null || m.url().isBlank()) {
                continue;
            }
            affected += jdbc.update(
                    "INSERT INTO music_script (script_key, name, script_url, enabled, sort_order) "
                            + "VALUES (?, ?, ?, ?, 100) "
                            + "ON DUPLICATE KEY UPDATE name = VALUES(name), script_url = VALUES(script_url), "
                            + "enabled = VALUES(enabled)",
                    m.id(), m.name() == null ? m.id() : m.name(), m.url(), m.enabled());
        }
        touchBundleVersion();
        return affected;
    }

    /**
     * 删除指定 key 之外的行，实现「保存即同步列表」语义。
     *
     * @param keys 需要保留的 key；为空时清空整表
     * @return 被删除的行数
     */
    public int deleteVodSourcesNotIn(List<String> keys) {
        int removed = keys == null || keys.isEmpty()
                ? jdbc.update("DELETE FROM vod_source")
                : jdbc.update(placeholders("DELETE FROM vod_source WHERE source_key NOT IN ", keys), keys.toArray());
        touchBundleVersion();
        return removed;
    }

    public int deleteLiveChannelsNotIn(List<String> keys) {
        int removed = keys == null || keys.isEmpty()
                ? jdbc.update("DELETE FROM live_channel")
                : jdbc.update(placeholders("DELETE FROM live_channel WHERE channel_code NOT IN ", keys), keys.toArray());
        touchBundleVersion();
        return removed;
    }

    public int deleteMusicScriptsNotIn(List<String> keys) {
        int removed = keys == null || keys.isEmpty()
                ? jdbc.update("DELETE FROM music_script")
                : jdbc.update(placeholders("DELETE FROM music_script WHERE script_key NOT IN ", keys), keys.toArray());
        touchBundleVersion();
        return removed;
    }

    private static String placeholders(String prefix, List<String> keys) {
        return prefix + "(" + String.join(",", java.util.Collections.nCopies(keys.size(), "?")) + ")";
    }

    public void updateProbeResult(String sourceKey, int latencyMs, String healthStatus) {
        jdbc.update(
                "UPDATE vod_source SET latency_ms = ?, health_status = ?, last_checked_at = NOW() "
                        + "WHERE source_key = ?",
                latencyMs, healthStatus, sourceKey);
    }

    /**
     * 每次写入后刷新 bundle_version，便于客户端判断远端是否有变更。
     *
     * <p>形如 {@code 2.0.0.20261111}：取基础版本的 major.minor.patch 前缀再拼当天日期，
     * 同一天多次保存只更新到日粒度。
     */
    private void touchBundleVersion() {
        String base = readMeta("bundle_version", DefaultSources.DEFAULT_VERSION);
        // 去掉此前追加的日期后缀，只保留 major.minor.patch
        String semver = base.split("\\.").length > 3
                ? String.join(".", base.split("\\.")[0], base.split("\\.")[1], base.split("\\.")[2])
                : base;
        if (semver.length() < 5 || !semver.matches("\\d+\\.\\d+\\.\\d+.*")) {
            semver = DefaultSources.DEFAULT_VERSION;
        }
        writeMeta("bundle_version", semver + "." + now().substring(0, 10).replace("-", ""));
    }

    /**
     * 返回最近一次写入的时间，已按会话时区（+08:00）格式化。
     *
     * <p>必须取字符串而不是 {@link Timestamp}：驱动会把 Timestamp 从会话时区转换到
     * <b>JVM 默认时区</b>，而 Render 容器是 UTC，转出来会再差 8 小时。
     * 直接用 {@code DATE_FORMAT} 由数据库格式化，结果与时区转换无关。
     */
    private String latestTimestamp() {
        try {
            String ts = jdbc.queryForObject(
                    "SELECT DATE_FORMAT(MAX(updated_at), '%Y-%m-%d %H:%i:%s') FROM ("
                            + "SELECT updated_at FROM vod_source "
                            + "UNION ALL SELECT updated_at FROM live_channel "
                            + "UNION ALL SELECT updated_at FROM music_script) t",
                    String.class);
            return ts == null || ts.isBlank() ? now() : ts;
        } catch (Exception e) {
            return now();
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TS);
    }

    public boolean isDbAvailable() {
        return dbAvailable;
    }

    /** 内置默认源（与 data.sql 保持一致），用于数据库不可用/空表时兜底。 */
    public static final class DefaultSources {

        public static final String DEFAULT_VERSION = "2.0.0";

        private static final List<VodSourceDto> VOD = new ArrayList<>(List.of(
                new VodSourceDto("ffzy", "非凡", "http://ffzy5.tv/api.php/provide/vod/", true, 0, "ONLINE"),
                new VodSourceDto("bfzy", "暴风", "https://bfzyapi.com/api.php/provide/vod/", true, 0, "ONLINE"),
                new VodSourceDto("lzi", "量子", "https://cj.lziapi.com/api.php/provide/vod/", true, 0, "ONLINE"),
                new VodSourceDto("tyyszy", "天涯", "https://tyyszy.com/api.php/provide/vod/", true, 0, "ONLINE"),
                new VodSourceDto("zy360", "360", "https://360zy.com/api.php/provide/vod/", true, 0, "ONLINE"),
                new VodSourceDto("wolong", "卧龙", "https://wolongzyw.com/api.php/provide/vod/", true, 0, "ONLINE"),
                new VodSourceDto("jisu", "极速", "https://jszyapi.com/api.php/provide/vod/", true, 0, "ONLINE"),
                new VodSourceDto("dbzy", "豆瓣源", "https://dbzy.tv/api.php/provide/vod/", true, 0, "ONLINE"),
                new VodSourceDto("mozhuazy", "魔爪", "https://mozhuazy.com/api.php/provide/vod/", true, 0, "ONLINE"),
                new VodSourceDto("mdzy", "魔都", "https://www.mdzyapi.com/api.php/provide/vod/", true, 0, "ONLINE")));

        private static final List<LiveChannelDto> LIVE = new ArrayList<>(List.of(
                new LiveChannelDto("cctv1", "CCTV-1 综合", "央视频道",
                        "https://live-play.cctvnews.cctv.com/cctv/merge_cctv1.m3u8", true),
                new LiveChannelDto("cctv2", "CCTV-2 财经", "央视频道",
                        "https://live-play.cctvnews.cctv.com/cctv/merge_cctv2.m3u8", true),
                new LiveChannelDto("cctv4", "CCTV-4 中文国际", "央视频道",
                        "https://live-play.cctvnews.cctv.com/cctv/merge_cctv4.m3u8", true),
                new LiveChannelDto("cctv13", "CCTV-13 新闻", "央视频道",
                        "https://live-play.cctvnews.cctv.com/cctv/merge_cctv13.m3u8", true),
                new LiveChannelDto("cgtn", "CGTN 国际英语", "国际频道",
                        "https://news.cgtn.com/resource/live/english/cgtn-news.m3u8", true),
                new LiveChannelDto("cgtn_doc", "CGTN 纪录", "国际频道",
                        "https://news.cgtn.com/resource/live/document/cgtn-doc.m3u8", true)));

        private static final List<MusicScriptDto> SCRIPTS = new ArrayList<>(List.of(
                new MusicScriptDto("huibq", "Huibq 音源",
                        "https://fastly.jsdelivr.net/gh/Huibq/keep-alive/render_api.js", true),
                new MusicScriptDto("lx_official_mirror", "洛雪聚合镜像源",
                        "https://fastly.jsdelivr.net/gh/pdone/lx-music-source/sixyin/latest.js", true),
                new MusicScriptDto("ikun_source", "iKun 公益无损源",
                        "https://fastly.jsdelivr.net/gh/ikun0014/lx-music-source/dist/lx-music-source.js", true)));

        private DefaultSources() {
        }

        static SourceBundleDto fallback(String version, String updatedAt, String origin) {
            return new SourceBundleDto(
                    version, updatedAt, new ArrayList<>(VOD), new ArrayList<>(LIVE), new ArrayList<>(SCRIPTS), origin, 0);
        }

        /** 供启动时播种使用；返回只读副本，避免调用方修改共享常量。 */
        public static List<VodSourceDto> vod() {
            return List.copyOf(VOD);
        }

        public static List<LiveChannelDto> live() {
            return List.copyOf(LIVE);
        }

        public static List<MusicScriptDto> scripts() {
            return List.copyOf(SCRIPTS);
        }
    }

    /** 供调试接口使用的统计。 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dbAvailable", dbAvailable);
        SourceBundleDto b = buildBundle();
        m.put("version", b.version());
        m.put("updatedAt", b.updatedAt());
        m.put("vodSources", b.vodSources().size());
        m.put("liveChannels", b.liveChannels().size());
        m.put("lxMusicScripts", b.lxMusicScripts().size());
        m.put("origin", b.origin());
        return m;
    }
}
