package com.moviehub.server.api;

import com.moviehub.server.dto.VersionDto;
import com.moviehub.server.service.SourceQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线更新清单。数据来自 {@code app_meta}，缺省时回退到内置默认值。
 */
@RestController
@RequestMapping
public class VersionController {

    private final SourceQueryService service;

    @Value("${moviehub.release-page-url:https://github.com/berse1kguts-pixel/moviehub/releases}")
    private String releasePageUrl;

    @Value("${moviehub.apk-url:https://github.com/berse1kguts-pixel/moviehub/releases/latest/download/moviehub-release.apk}")
    private String apkUrl;

    @Value("${moviehub.mirror-url:https://ghp.ci/https://github.com/berse1kguts-pixel/moviehub/releases/latest/download/moviehub-release.apk}")
    private String mirrorUrl;

    public VersionController(SourceQueryService service) {
        this.service = service;
    }

    @GetMapping({"/api/version.json", "/version.json"})
    public VersionDto version() {
        String name = service.readMeta("app_version_name", "2.5.0");
        int code = parseInt(service.readMeta("app_version_code", "25"), 25);
        return new VersionDto(
                name,
                code,
                "true".equalsIgnoreCase(service.readMeta("force_update", "false")),
                service.readMeta("release_title", "观影中心 v" + name + " 正式版"),
                service.readMeta("published_at", ""),
                service.readMeta("apk_url", apkUrl),
                service.readMeta("mirror_url", mirrorUrl),
                service.readMeta("release_page_url", releasePageUrl),
                service.readMeta("bundle_version", "2.0.0"),
                service.readMeta("changelog",
                        "1. 五大频道分区并发秒开\n2. 播放器清晰度切换与竖屏自适应\n3. 云端源热更新与在线版本检测"));
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
