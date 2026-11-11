package com.moviehub.server.api;

import com.moviehub.server.dto.LiveChannelDto;
import com.moviehub.server.dto.MusicScriptDto;
import com.moviehub.server.dto.SourceBundleDto;
import com.moviehub.server.dto.VersionDto;
import com.moviehub.server.dto.VodSourceDto;
import com.moviehub.server.service.AdminAuthService;
import com.moviehub.server.service.SourceProbeService;
import com.moviehub.server.service.SourceQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维写接口。所有 {@code /api/admin/**} 请求必须持有登录会话（见 {@link AdminAuthService}）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SourceQueryService query;
    private final SourceProbeService probe;
    private final AdminAuthService auth;

    public AdminController(
            SourceQueryService query,
            SourceProbeService probe,
            AdminAuthService auth) {
        this.query = query;
        this.probe = probe;
        this.auth = auth;
    }

    private boolean authorized(HttpServletRequest request) {
        return auth.isAuthenticated(request);
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        String reason = auth.isConfigured()
                ? "登录状态已失效，请重新登录"
                : "服务端未配置 ADMIN_PASSWORD，写接口已禁用";
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "error", reason));
    }

    /**
     * 全量保存源配置：提交的列表即最终状态。
     *
     * <p>请求体里出现的数组会被写入，未出现的数组保持不动；因此页面可以只提交点播源、
     * 也可以只提交直播频道或音源脚本。数组中未出现的条目会从库中删除。
     */
    @PostMapping("/sources")
    public ResponseEntity<Map<String, Object>> saveSources(
            @RequestBody SourceBundleDto body, HttpServletRequest request) {
        if (!authorized(request)) {
            return unauthorized();
        }
        try {
            int vod = 0, live = 0, music = 0, removed = 0;

            if (body.vodSources() != null) {
                vod = query.upsertVodSources(body.vodSources());
                removed += query.deleteVodSourcesNotIn(
                        body.vodSources().stream().map(VodSourceDto::id).filter(id -> id != null && !id.isBlank()).toList());
            }
            if (body.liveChannels() != null) {
                live = query.upsertLiveChannels(body.liveChannels());
                removed += query.deleteLiveChannelsNotIn(
                        body.liveChannels().stream().map(LiveChannelDto::id).filter(id -> id != null && !id.isBlank()).toList());
            }
            if (body.lxMusicScripts() != null) {
                music = query.upsertMusicScripts(body.lxMusicScripts());
                removed += query.deleteMusicScriptsNotIn(
                        body.lxMusicScripts().stream().map(MusicScriptDto::id).filter(id -> id != null && !id.isBlank()).toList());
            }

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "vodSources", vod,
                    "liveChannels", live,
                    "musicScripts", music,
                    "removed", removed,
                    "version", query.readMeta("bundle_version", "2.0.0")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("ok", false, "error", "写入失败: " + e.getMessage()));
        }
    }

    /** 保存客户端更新清单，写入 app_meta 的 app_* 键，客户端下次检查更新即可读到。 */
    @PostMapping("/version")
    public ResponseEntity<Map<String, Object>> saveVersion(
            @RequestBody VersionDto v, HttpServletRequest request) {
        if (!authorized(request)) {
            return unauthorized();
        }
        if (v == null || v.versionName() == null || v.versionName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "版本号不能为空"));
        }
        if (v.versionCode() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "versionCode 必须大于 0"));
        }
        try {
            query.writeMeta("app_version_name", v.versionName().trim());
            query.writeMeta("app_version_code", String.valueOf(v.versionCode()));
            query.writeMeta("force_update", String.valueOf(v.forceUpdate()));
            query.writeMeta("release_title", orEmpty(v.title()));
            query.writeMeta("published_at", orEmpty(v.publishedAt()));
            query.writeMeta("apk_url", orEmpty(v.apkUrl()));
            query.writeMeta("mirror_url", orEmpty(v.mirrorUrl()));
            query.writeMeta("release_page_url", orEmpty(v.releasePageUrl()));
            query.writeMeta("changelog", orEmpty(v.changelog()));
            return ResponseEntity.ok(Map.of(
                    "ok", true, "versionName", v.versionName(), "versionCode", v.versionCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("ok", false, "error", "保存失败: " + e.getMessage()));
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    @PostMapping("/probe")
    public ResponseEntity<Map<String, Object>> probeOne(
            @RequestParam("url") String url, HttpServletRequest request) {
        if (!authorized(request)) {
            return unauthorized();
        }
        return ResponseEntity.ok(probe.probe(url));
    }

    @GetMapping("/health")
    public Map<String, Object> adminHealth(HttpServletRequest request) {
        return Map.of(
                "ok", authorized(request),
                "loginConfigured", auth.isConfigured(),
                "db", query.stats());
    }

    /** 便于运维人工触发一次全量测速。 */
    @PostMapping("/probe-all")
    public ResponseEntity<Map<String, Object>> probeAll(HttpServletRequest request) {
        if (!authorized(request)) {
            return unauthorized();
        }
        probe.probeAllScheduled();
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
