package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MusicTrack
import com.example.data.model.PlayMode
import com.example.ui.MusicSubTab
import com.example.ui.MusicUiState
import com.example.ui.components.SectionHeader
import com.example.ui.components.TrackAlbumCover
import com.example.ui.components.formatDurationSec
import com.example.ui.theme.MhBg
import com.example.ui.theme.MhBgSoft
import com.example.ui.theme.MhBorder
import com.example.ui.theme.MhCard
import com.example.ui.theme.MhCardHover
import com.example.ui.theme.MhDanger
import com.example.ui.theme.MhSpotifyGreen
import com.example.ui.theme.MhText
import com.example.ui.theme.MhTextDim

private data class SpotifyQuickPick(
    val title: String,
    val subtitle: String,
    val accentColor: Color
)

private val QUICK_PICKS = listOf(
    SpotifyQuickPick("周杰伦", "华语经典", Color(0xFF285943)),
    SpotifyQuickPick("陈奕迅", "粤语金曲", Color(0xFF3E3B6B)),
    SpotifyQuickPick("林俊杰", "行走的CD", Color(0xFF234E68)),
    SpotifyQuickPick("邓紫棋", "实力女声", Color(0xFF632B3D)),
    SpotifyQuickPick("Taylor Swift", "欧美流行", Color(0xFF5A4427)),
    SpotifyQuickPick("坂本龙一", "纯音乐与电影原声", Color(0xFF2E3B3E))
)

@Composable
fun MusicCenterScreen(
    musicState: MusicUiState,
    playlistTracks: List<MusicTrack>,
    onSelectSubTab: (MusicSubTab) -> Unit,
    onSelectPlatform: (String) -> Unit,
    onSearchKeywordChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onQuickSearch: (String) -> Unit = {},
    onPlayTrack: (MusicTrack) -> Unit,
    onToggleTrackLiked: (MusicTrack) -> Unit = {},
    onAddTrackToPlaylist: (MusicTrack) -> Unit,
    onRemoveFromPlaylist: (String) -> Unit,
    onClearPlaylist: () -> Unit,
    onTogglePlayPause: () -> Unit = {},
    onCyclePlayMode: () -> Unit,
    onSelectQuality: (String) -> Unit,
    onOpenLyricsSheet: () -> Unit,
    onToggleLxScript: (String) -> Unit,
    onAddLxScript: (String, String) -> Unit,
    onSaveSpotifyClientId: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val likedKeys = remember(playlistTracks) { playlistTracks.map { it.uniqueKey }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MhBg)
    ) {
        // Spotify Ambient Top Header + Filter Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF173626),
                            Color(0xFF0E1B14),
                            MhBg
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Spotify-inspired circular emblem
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MhSpotifyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "音乐",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MhText
                )

                Spacer(modifier = Modifier.weight(1f))

                // Spotify Green & Charcoal Filter Pills
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        MusicSubTab.SEARCH to "探索",
                        MusicSubTab.PLAYLIST to "喜欢 (${playlistTracks.size})",
                        MusicSubTab.SOURCES_SPOTIFY to "音源"
                    ).forEach { (tab, title) ->
                        SpotifyFilterChip(
                            text = title,
                            selected = musicState.subTab == tab,
                            onClick = { onSelectSubTab(tab) },
                            modifier = Modifier.testTag("music_subtab_${tab.name.lowercase()}")
                        )
                    }
                }

                if (musicState.currentTrack != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onOpenLyricsSheet,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("open_lyrics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "打开全屏歌词",
                            tint = MhSpotifyGreen,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        when (musicState.subTab) {
            MusicSubTab.SEARCH -> {
                SpotifySearchAndExploreTab(
                    musicState = musicState,
                    likedKeys = likedKeys,
                    onSelectPlatform = onSelectPlatform,
                    onSearchKeywordChange = onSearchKeywordChange,
                    onExecuteSearch = {
                        focusManager.clearFocus()
                        onExecuteSearch()
                    },
                    onQuickSearch = { kw ->
                        focusManager.clearFocus()
                        onQuickSearch(kw)
                    },
                    onPlayTrack = onPlayTrack,
                    onToggleTrackLiked = onToggleTrackLiked,
                    onSelectQuality = onSelectQuality,
                    onOpenLyricsSheet = onOpenLyricsSheet
                )
            }
            MusicSubTab.PLAYLIST -> {
                SpotifyLikedSongsTab(
                    playlistTracks = playlistTracks,
                    currentTrack = musicState.currentTrack,
                    isPlaying = musicState.isPlaying,
                    playMode = musicState.playMode,
                    preferredQuality = musicState.preferredQuality,
                    onPlayTrack = onPlayTrack,
                    onTogglePlayPause = onTogglePlayPause,
                    onRemoveTrack = onRemoveFromPlaylist,
                    onClearPlaylist = onClearPlaylist,
                    onCyclePlayMode = onCyclePlayMode
                )
            }
            MusicSubTab.SOURCES_SPOTIFY -> {
                MusicSourcesAndSpotifyTabContent(
                    musicState = musicState,
                    onToggleLxScript = onToggleLxScript,
                    onAddLxScript = onAddLxScript,
                    onSaveSpotifyClientId = onSaveSpotifyClientId
                )
            }
        }
    }
}

@Composable
private fun SpotifyFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MhSpotifyGreen else Color(0xFF232329))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.Black else Color.White
        )
    }
}

@Composable
private fun SpotifySearchAndExploreTab(
    musicState: MusicUiState,
    likedKeys: Set<String>,
    onSelectPlatform: (String) -> Unit,
    onSearchKeywordChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onQuickSearch: (String) -> Unit,
    onPlayTrack: (MusicTrack) -> Unit,
    onToggleTrackLiked: (MusicTrack) -> Unit,
    onSelectQuality: (String) -> Unit,
    onOpenLyricsSheet: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Spotify Search Input Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222228))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFFB3B3B3),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (musicState.searchKeyword.isEmpty()) {
                            Text(
                                text = "想听什么？搜索歌曲、歌手或专辑",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color(0xFFB3B3B3)
                            )
                        }
                        BasicTextField(
                            value = musicState.searchKeyword,
                            onValueChange = onSearchKeywordChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            ),
                            cursorBrush = SolidColor(MhSpotifyGreen),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("music_search_input")
                        )
                    }
                    if (musicState.searchKeyword.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除",
                            tint = Color(0xFFB3B3B3),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSearchKeywordChange("") }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MhSpotifyGreen)
                        .clickable(onClick = onExecuteSearch)
                        .padding(horizontal = 16.dp)
                        .testTag("music_search_submit"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "搜索",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        // 2. Spotify 2x3 Quick-Pick Grid (歌手电台速选)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_PICKS.chunked(2).forEach { rowPicks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPicks.forEach { pick ->
                            val isCurrentKw = musicState.searchKeyword == pick.title
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isCurrentKw) Color(0xFF28382F) else Color(0xFF1A1A20))
                                    .border(
                                        0.5.dp,
                                        if (isCurrentKw) MhSpotifyGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onQuickSearch(pick.title) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .fillMaxHeight()
                                        .background(pick.accentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pick.title.first().toString(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = pick.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCurrentKw) MhSpotifyGreen else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = pick.subtitle,
                                        fontSize = 10.sp,
                                        color = Color(0xFFB3B3B3),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Multi-Platform & Audio Quality Strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                musicState.platforms.forEach { p ->
                    val active = musicState.selectedPlatform == p.key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) MhSpotifyGreen else Color(0xFF1A1A20))
                            .clickable { onSelectPlatform(p.key) }
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = p.name,
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) Color.Black else Color(0xFFB3B3B3)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(14.dp)
                        .background(MhBorder)
                )

                listOf("flac" to "FLAC 无损", "320k" to "320K 极高", "128k" to "128K 标准").forEach { (q, label) ->
                    val active = musicState.preferredQuality == q
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) Color(0xFF1F382B) else Color(0xFF16161B))
                            .border(
                                0.5.dp,
                                if (active) MhSpotifyGreen else MhBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectQuality(q) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) MhSpotifyGreen else Color(0xFFB3B3B3)
                        )
                    }
                }
            }
        }

        // 4. Synchronized Lyric Banner (when playing)
        if (musicState.currentTrack != null && musicState.lyricLines.isNotEmpty()) {
            item {
                val curLine = musicState.lyricLines.lastOrNull { it.timeSec <= musicState.currentTimeSec }?.text
                    ?: musicState.lyricLines.first().text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF162920))
                        .border(0.5.dp, MhSpotifyGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { onOpenLyricsSheet() }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MhSpotifyGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = curLine,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "全屏词 ▸",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MhSpotifyGreen
                    )
                }
            }
        }

        // 5. Search Results List (Spotify Track Rows)
        when {
            musicState.isSearching -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MhSpotifyGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            musicState.searchError != null && musicState.searchResults.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = musicState.searchError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MhTextDim
                        )
                    }
                }
            }

            else -> {
                itemsIndexed(
                    musicState.searchResults,
                    key = { idx, t -> "${t.uniqueKey}_$idx" }
                ) { _, track ->
                    val isPlayingThis = musicState.currentTrack?.uniqueKey == track.uniqueKey
                    val isLiked = likedKeys.contains(track.uniqueKey)

                    SpotifyTrackRow(
                        track = track,
                        isPlayingThis = isPlayingThis,
                        isLiked = isLiked,
                        onPlay = { onPlayTrack(track) },
                        onToggleLike = { onToggleTrackLiked(track) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }
    }
}

@Composable
private fun SpotifyTrackRow(
    track: MusicTrack,
    isPlayingThis: Boolean,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlayingThis) Color(0xFF1A2821) else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Square Album Cover
        TrackAlbumCover(
            title = track.name,
            picUrl = track.pic,
            size = 46.dp,
            cornerRadius = 6.dp,
            isPlaying = isPlayingThis
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Subtitle with HQ Platform Tag
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isPlayingThis) MhSpotifyGreen else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Mini Spotify-style Explicit / HQ Source Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF2C2C34))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = track.platform.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4D4D8)
                    )
                }

                Text(
                    text = buildString {
                        append(track.singer.ifEmpty { "未知歌手" })
                        if (track.album.isNotEmpty()) append(" · ${track.album}")
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = Color(0xFFB3B3B3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (track.duration > 0) {
            Text(
                text = formatDurationSec(track.duration.toDouble()),
                fontSize = 11.5.sp,
                color = Color(0xFF8A8A95),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        // Spotify Green Heart Button
        IconButton(
            onClick = onToggleLike,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isLiked) "取消喜欢" else "加入喜欢",
                tint = if (isLiked) MhSpotifyGreen else Color(0xFFB3B3B3),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun SpotifyLikedSongsTab(
    playlistTracks: List<MusicTrack>,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    playMode: PlayMode,
    preferredQuality: String,
    onPlayTrack: (MusicTrack) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRemoveTrack: (String) -> Unit,
    onClearPlaylist: () -> Unit,
    onCyclePlayMode: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // Spotify Liked Songs Hero Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF205038),
                                Color(0xFF12281D),
                                MhBg
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Spotify Liked Songs Gradient Emblem
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF45108A), Color(0xFF1ED760))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "我喜欢的音乐",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "${playlistTracks.size} 首歌曲 · 跨平台智能音源 · ${preferredQuality.uppercase()}",
                                fontSize = 12.sp,
                                color = Color(0xFFB3B3B3)
                            )
                        }
                    }

                    // Action Row: Clear + Shuffle Mode + Iconic 52dp Spotify Green Play Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (playlistTracks.isNotEmpty()) {
                            Text(
                                text = "清空歌单",
                                fontSize = 12.sp,
                                color = Color(0xFFB3B3B3),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
                                    .clickable(onClick = onClearPlaylist)
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Shuffle / Mode button
                        Row(
                            modifier = Modifier
                                .padding(end = 14.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable(onClick = onCyclePlayMode)
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = playMode.label,
                                tint = if (playMode == PlayMode.SHUFFLE) MhSpotifyGreen else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = playMode.label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (playMode == PlayMode.SHUFFLE) MhSpotifyGreen else Color.White
                            )
                        }

                        // Spotify Giant Green Play/Pause Circle Button
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MhSpotifyGreen)
                                .clickable {
                                    if (currentTrack != null && playlistTracks.any { it.uniqueKey == currentTrack.uniqueKey }) {
                                        onTogglePlayPause()
                                    } else if (playlistTracks.isNotEmpty()) {
                                        onPlayTrack(playlistTracks.first())
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "播放歌单",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        if (playlistTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = MhTextDim,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "点击歌曲右侧的心形图标即可收藏到这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MhTextDim
                        )
                    }
                }
            }
        } else {
            itemsIndexed(playlistTracks, key = { _, t -> t.uniqueKey }) { _, track ->
                val isPlayingThis = currentTrack?.uniqueKey == track.uniqueKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPlayingThis) Color(0xFF1A2821) else Color.Transparent)
                        .clickable { onPlayTrack(track) }
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackAlbumCover(
                        title = track.name,
                        picUrl = track.pic,
                        size = 46.dp,
                        cornerRadius = 6.dp,
                        isPlaying = isPlayingThis
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.5.sp,
                                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isPlayingThis) MhSpotifyGreen else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${track.singer} · ${track.platform.uppercase()}",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = Color(0xFFB3B3B3),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onRemoveTrack(track.uniqueKey) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "移除",
                            tint = Color(0xFFB3B3B3),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicSourcesAndSpotifyTabContent(
    musicState: MusicUiState,
    onToggleLxScript: (String) -> Unit,
    onAddLxScript: (String, String) -> Unit,
    onSaveSpotifyClientId: (String) -> Unit
) {
    var newScriptName by remember { mutableStateOf("") }
    var newScriptUrl by remember { mutableStateOf("") }
    var spClientIdInput by remember(musicState.spotifyClientId) { mutableStateOf(musicState.spotifyClientId) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "扩展音源",
                subtitle = "洛雪自定义脚本"
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                musicState.lxScripts.forEach { script ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MhBgSoft)
                            .border(
                                0.5.dp,
                                if (script.enabled) MhSpotifyGreen.copy(alpha = 0.4f) else MhBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onToggleLxScript(script.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = script.enabled,
                            onCheckedChange = { onToggleLxScript(script.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MhSpotifyGreen,
                                checkmarkColor = Color.Black
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = script.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MhText
                            )
                            Text(
                                text = script.url,
                                style = MaterialTheme.typography.labelMedium,
                                color = MhTextDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    color = MhBgSoft,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "新增自定义音源",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MhText
                        )
                        OutlinedTextField(
                            value = newScriptName,
                            onValueChange = { newScriptName = it },
                            placeholder = { Text("音源名称", color = MhTextDim) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newScriptUrl,
                            onValueChange = { newScriptUrl = it },
                            placeholder = { Text("脚本 URL (.js)", color = MhTextDim) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SpotifyFilterChip(
                            text = "保存音源",
                            selected = true,
                            onClick = {
                                onAddLxScript(newScriptName, newScriptUrl)
                                newScriptName = ""
                                newScriptUrl = ""
                            }
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Spotify 连接",
                subtitle = "PKCE 授权配置"
            )
            Surface(
                color = MhBgSoft,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "在 developer.spotify.com/dashboard 创建应用并配置 Redirect URI (http://127.0.0.1:7123/spotify/callback)，填入 Client ID 即可启用。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MhTextDim
                    )
                    OutlinedTextField(
                        value = spClientIdInput,
                        onValueChange = { spClientIdInput = it },
                        placeholder = { Text("Spotify Client ID", color = MhTextDim) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SpotifyFilterChip(
                        text = "保存配置",
                        selected = true,
                        onClick = { onSaveSpotifyClientId(spClientIdInput) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}
