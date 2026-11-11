package com.moviehub.server.dto;

/**
 * 客户端在线检查更新清单，键名与
 * {@code MovieHubNetwork.checkAppUpdate} 期望的一致。
 */
public record VersionDto(
        String versionName,
        int versionCode,
        boolean forceUpdate,
        String title,
        String publishedAt,
        String apkUrl,
        String mirrorUrl,
        String releasePageUrl,
        String sourceVersion,
        String changelog) {
}
