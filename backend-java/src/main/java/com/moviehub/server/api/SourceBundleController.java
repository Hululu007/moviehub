package com.moviehub.server.api;

import com.moviehub.server.dto.SourceBundleDto;
import com.moviehub.server.service.SourceQueryService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端只读同步接口。
 *
 * <p>Android 默认从 {@code /api/sources.json} 拉取整包；保留 {@code /sources.json}
 * 兼容原 source-hub 的路径，便于把 Render 地址直接替换 GitHub Raw。
 */
@RestController
@RequestMapping
public class SourceBundleController {

    private final SourceQueryService service;

    public SourceBundleController(SourceQueryService service) {
        this.service = service;
    }

    @GetMapping({"/api/sources.json", "/sources.json"})
    public SourceBundleDto sources() {
        long t0 = System.currentTimeMillis();
        SourceBundleDto bundle = service.buildBundle();
        return new SourceBundleDto(
                bundle.version(),
                bundle.updatedAt(),
                bundle.vodSources(),
                bundle.liveChannels(),
                bundle.lxMusicScripts(),
                bundle.origin(),
                System.currentTimeMillis() - t0);
    }

    @GetMapping({"/api/live.json", "/live.json"})
    public Map<String, Object> live() {
        return Map.of(
                "version", service.readMeta("bundle_version", "2.0.0"),
                "liveChannels", service.queryLiveChannels());
    }

    @GetMapping("/api/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }
}
