package com.moviehub.server.dto;

/**
 * 片源条目，字段名与 Android 客户端 {@code sources.json} 契约一致。
 *
 * @param id      片源唯一标识（数据库 source_key）
 * @param name    显示名称
 * @param api     苹果CMS V10 采集接口
 * @param enabled 是否启用
 * @param latency 服务端最近测速耗时（毫秒），客户端可忽略
 * @param health  ONLINE / SLOW / OFFLINE
 */
public record VodSourceDto(
        String id,
        String name,
        String api,
        boolean enabled,
        int latency,
        String health) {
}
