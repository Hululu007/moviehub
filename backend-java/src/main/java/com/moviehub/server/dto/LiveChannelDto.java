package com.moviehub.server.dto;

/**
 * 直播频道条目，字段名与 Android 客户端 {@code liveChannels} 契约一致。
 */
public record LiveChannelDto(
        String id,
        String name,
        String group,
        String url,
        boolean enabled) {
}
