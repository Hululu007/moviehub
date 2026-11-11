package com.moviehub.server.api;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 根路径与存活探针。Render 的 healthCheckPath 指向 {@code /healthz}。
 */
@RestController
@RequestMapping
public class RootController {

    @GetMapping({"/healthz", "/api/healthz"})
    public Map<String, Object> healthz() {
        return Map.of("status", "UP");
    }

    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of(
                "service", "moviehub-source-server",
                "endpoints", List.of(
                        "GET /api/sources.json",
                        "GET /api/live.json",
                        "GET /api/version.json",
                        "GET /api/stats",
                        "POST /api/admin/sources  (X-Admin-Key)",
                        "POST /api/admin/probe?url=  (X-Admin-Key)",
                        "POST /api/admin/probe-all  (X-Admin-Key)"));
    }
}
