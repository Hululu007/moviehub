package com.moviehub.server.api;

import com.moviehub.server.dto.LiveChannelDto;
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

    @PostMapping("/sources")
    public ResponseEntity<Map<String, Object>> saveSources(
            @RequestBody SourceBundleDto body, HttpServletRequest request) {
        if (!authorized(request)) {
            return unauthorized();
        }
        try {
            int vod = query.upsertVodSources(body.vodSources());
            int live = query.upsertLiveChannels(body.liveChannels());
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "vodSources", vod,
                    "liveChannels", live,
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
