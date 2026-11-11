package com.moviehub.server.api;

import com.moviehub.server.dto.LiveChannelDto;
import com.moviehub.server.dto.MusicScriptDto;
import com.moviehub.server.dto.SourceBundleDto;
import com.moviehub.server.dto.VodSourceDto;
import com.moviehub.server.service.SourceProbeService;
import com.moviehub.server.service.SourceQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维写接口。所有 {@code /api/admin/**} 请求必须携带与 {@code moviehub.admin-token}
 * 一致的 {@code X-Admin-Key} 头。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SourceQueryService query;
    private final SourceProbeService probe;
    private final String adminToken;

    public AdminController(
            SourceQueryService query,
            SourceProbeService probe,
            @Value("${moviehub.admin-token:}") String adminToken) {
        this.query = query;
        this.probe = probe;
        this.adminToken = adminToken == null ? "" : adminToken.trim();
    }

    /** 未配置 ADMIN_TOKEN 时拒绝所有写操作，避免公开部署被直接改写。 */
    private boolean authorized(HttpServletRequest request) {
        if (adminToken.isEmpty()) {
            return false;
        }
        String provided = request.getHeader("X-Admin-Key");
        return provided != null && provided.equals(adminToken);
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        String reason = adminToken.isEmpty()
                ? "服务端未配置 ADMIN_TOKEN，写接口已禁用"
                : "管理密钥 (X-Admin-Key) 不正确";
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
                "adminTokenConfigured", !adminToken.isEmpty(),
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
