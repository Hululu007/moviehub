package com.moviehub.server.dto;

/**
 * 音源脚本条目，字段名与 Android 客户端 {@code lxMusicScripts} 契约一致。
 */
public record MusicScriptDto(
        String id,
        String name,
        String url,
        boolean enabled) {
}
