package com.moviehub.server.service;

import com.moviehub.server.dto.VodSourceDto;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 定时探测各片源 API 响应耗时，更新 {@code vod_source.latency_ms} / {@code health_status}。
 * 探测定时表达式来自 {@code moviehub.auto-speed-test-cron}。
 */
@Service
public class SourceProbeService {

    private static final Logger log = LoggerFactory.getLogger(SourceProbeService.class);
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/144.0.0.0 Safari/537.36";

    private final SourceQueryService queryService;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SourceProbeService(SourceQueryService queryService) {
        this.queryService = queryService;
    }

    /** 返回 {@code {ok, status, latencyMs, error?}}，与 source-hub 的 /api/probe 语义一致。 */
    public Map<String, Object> probe(String targetUrl) {
        if (targetUrl == null || !(targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
            return Map.of("ok", false, "status", 400, "latencyMs", 0, "error", "Invalid URL");
        }
        String sep = targetUrl.contains("?") ? "&" : "?";
        String fullUrl = targetUrl + sep + "ac=list&pg=1";
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(fullUrl))
                    .timeout(Duration.ofMillis(4500))
                    .header("User-Agent", UA)
                    .header("Accept", "application/json,*/*")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body() == null ? "" : resp.body();
            boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 300
                    && (body.contains("\"list\"") || body.contains("\"class\"") || body.trim().startsWith("{"));
            return Map.of(
                    "ok", ok,
                    "status", resp.statusCode(),
                    "latencyMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            return Map.of(
                    "ok", false,
                    "status", 502,
                    "latencyMs", System.currentTimeMillis() - start,
                    "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** 并发探测所有启用片源并落库；数据库不可用时静默跳过。 */
    @Scheduled(cron = "${moviehub.auto-speed-test-cron:0 */10 * * * *}")
    public void probeAllScheduled() {
        try {
            List<VodSourceDto> sources = queryService.queryVodSources();
            sources.stream()
                    .filter(VodSourceDto::enabled)
                    .parallel()
                    .forEach(s -> {
                        Map<String, Object> r = probe(s.api());
                        boolean ok = Boolean.TRUE.equals(r.get("ok"));
                        int latency = ((Number) r.get("latencyMs")).intValue();
                        String health = !ok ? "OFFLINE" : latency > 3000 ? "SLOW" : "ONLINE";
                        try {
                            queryService.updateProbeResult(s.id(), latency, health);
                        } catch (Exception e) {
                            log.debug("写入测速结果失败 {}: {}", s.id(), e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.debug("定时测速跳过（数据库不可用）: {}", e.getMessage());
        }
    }
}
