package com.moviehub.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 客户端同步整包。字段名必须与
 * {@code MovieHubNetwork.parseRemoteSourceBundleJson} 期望的键一致。
 *
 * @param origin 数据来源：database / database-empty / database-unavailable
 * @param latencyMs 本次查询耗时（毫秒）
 */
public record SourceBundleDto(
        String version,
        String updatedAt,
        List<VodSourceDto> vodSources,
        List<LiveChannelDto> liveChannels,
        List<MusicScriptDto> lxMusicScripts,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) String origin,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) long latencyMs) {
}
