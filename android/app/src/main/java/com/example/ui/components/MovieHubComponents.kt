package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.PlayMode
import com.example.data.network.MovieHubNetwork
import com.example.ui.MusicUiState
import com.example.ui.theme.MhBg
import com.example.ui.theme.MhBgSoft
import com.example.ui.theme.MhBorder
import com.example.ui.theme.MhCard
import com.example.ui.theme.MhCardHover
import com.example.ui.theme.MhGold
import com.example.ui.theme.MhMint
import com.example.ui.theme.MhSpotifyGreen
import com.example.ui.theme.MhText
import com.example.ui.theme.MhTextDim
import kotlin.math.abs

@Composable
fun rememberMovieHubImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .okHttpClient(MovieHubNetwork.okHttpClient)
            .crossfade(true)
            .build()
    }
}

/**
 * Clean, editorial section header without decorative clutter.
 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String = "",
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            ),
            color = MhText
        )
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MhTextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(bottom = 1.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MhTextDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Minimalist frameless poster card (Apple TV / Infuse style).
 */
@Composable
fun VodPosterCard(
    title: String,
    posterUrl: String,
    subtitle: String,
    badgeText: String = "",
    isMultiSourceBadge: Boolean = false,
    rateBadge: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageLoader = rememberMovieHubImageLoader()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MhCard)
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
        ) {
            if (posterUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(posterUrl)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        PosterMonogramFallback(title)
                    },
                    error = {
                        PosterMonogramFallback(title)
                    }
                )
            } else {
                PosterMonogramFallback(title)
            }

            // Subtle bottom gradient vignette for legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
            )

            if (badgeText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isMultiSourceBadge) MhMint else MhText.copy(alpha = 0.9f)
                    )
                }
            }

            if (rateBadge.isNotEmpty()) {
                Text(
                    text = rateBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MhGold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MhText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = MhTextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PosterMonogramFallback(title: String) {
    val firstChar = title.trim().firstOrNull()?.toString() ?: "影"
    val hash = abs(title.hashCode())
    val luma1 = 0.12f + (hash % 5) * 0.015f
    val luma2 = 0.08f + ((hash / 7) % 4) * 0.012f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(luma1, luma1, luma1 + 0.02f),
                        Color(luma2, luma2, luma2 + 0.01f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstChar,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Light
            ),
            color = MhTextDim.copy(alpha = 0.5f)
        )
    }
}

/**
 * Spotify-style Square Album Cover with rich procedural vinyl/gradient fallback when track.pic is empty.
 */
@Composable
fun TrackAlbumCover(
    title: String,
    picUrl: String,
    size: Dp = 46.dp,
    cornerRadius: Dp = 6.dp,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val imageLoader = rememberMovieHubImageLoader()
    val context = LocalContext.current
    val hash = abs(title.hashCode())
    val palettes = listOf(
        listOf(Color(0xFF1E3A2F), Color(0xFF0F1D18)),
        listOf(Color(0xFF23293E), Color(0xFF111420)),
        listOf(Color(0xFF3A242B), Color(0xFF1A1114)),
        listOf(Color(0xFF2E2A1F), Color(0xFF171510)),
        listOf(Color(0xFF282238), Color(0xFF14111C))
    )
    val gradColors = palettes[hash % palettes.size]
    val firstChar = title.trim().firstOrNull()?.toString() ?: "♪"

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(gradColors))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (picUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(picUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    AlbumMonogramContent(firstChar, size)
                },
                error = {
                    AlbumMonogramContent(firstChar, size)
                }
            )
        } else {
            AlbumMonogramContent(firstChar, size)
        }

        if (isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "正在播放",
                    tint = MhSpotifyGreen,
                    modifier = Modifier.size((size.value * 0.42f).dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumMonogramContent(firstChar: String, size: Dp) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Subtle vinyl ring
        Box(
            modifier = Modifier
                .size((size.value * 0.68f).dp)
                .clip(CircleShape)
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstChar,
                fontSize = (size.value * 0.34f).coerceIn(12f, 48f).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}

/**
 * Reusable minimalist capsule pill button.
 */
@Composable
fun MinimalPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MhText else MhBgSoft)
            .border(
                width = 0.5.dp,
                color = if (selected) MhText else MhBorder,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) MhBg else MhTextDim
        )
    }
}

fun formatDurationSec(sec: Double): String {
    val total = sec.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

/**
 * Spotify-Inspired Floating Now-Playing Bar above bottom navigation.
 */
@Composable
fun PersistentMusicBottomBar(
    musicState: MusicUiState,
    isLiked: Boolean = false,
    onToggleLiked: () -> Unit = {},
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCycleMode: () -> Unit,
    onOpenLyricsSheet: () -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val track = musicState.currentTrack ?: return
    val progress = if (musicState.durationSec > 0) {
        (musicState.currentTimeSec / musicState.durationSec).toFloat().coerceIn(0f, 1f)
    } else 0f

    val currentLyricLine = remember(musicState.lyricLines, musicState.currentTimeSec) {
        musicState.lyricLines.lastOrNull { it.timeSec <= musicState.currentTimeSec }?.text
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .clickable { onOpenLyricsSheet() }
            .testTag("persistent_music_bar"),
        color = Color(0xFF1E1E24),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Square Album Cover
                Box(contentAlignment = Alignment.Center) {
                    TrackAlbumCover(
                        title = track.name,
                        picUrl = track.pic,
                        size = 38.dp,
                        cornerRadius = 5.dp,
                        isPlaying = false
                    )
                    if (musicState.isResolvingUrl) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MhSpotifyGreen,
                                strokeWidth = 1.8.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MhText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentLyricLine ?: track.singer.ifEmpty { "未知歌手" },
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = if (currentLyricLine != null) MhSpotifyGreen else Color(0xFFB3B3B3),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Spotify Heart Toggle
                IconButton(
                    onClick = onToggleLiked,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "取消喜欢" else "喜欢",
                        tint = if (isLiked) MhSpotifyGreen else Color(0xFFB3B3B3),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Play / Pause
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("music_bar_toggle")
                ) {
                    Icon(
                        imageVector = if (musicState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (musicState.isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Next Track
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("music_bar_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Close / Dismiss Bar completely
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("music_bar_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭音乐播放条",
                        tint = Color(0xFF8E8E98),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Spotify Bottom Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(MhSpotifyGreen)
                )
            }
        }
    }
}

/**
 * Spotify-Inspired Fullscreen Now Playing & Synchronized Lyrics Modal Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullLyricsModalSheet(
    musicState: MusicUiState,
    isLiked: Boolean = false,
    onToggleLiked: () -> Unit = {},
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleMode: () -> Unit,
    onSelectQuality: (String) -> Unit
) {
    val track = musicState.currentTrack ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var showFullLyricsMode by remember { mutableStateOf(false) }

    val activeLyricIndex = remember(musicState.lyricLines, musicState.currentTimeSec) {
        var idx = 0
        for (i in musicState.lyricLines.indices) {
            if (musicState.lyricLines[i].timeSec <= musicState.currentTimeSec) {
                idx = i
            }
        }
        idx
    }

    LaunchedEffect(activeLyricIndex, showFullLyricsMode) {
        if (musicState.lyricLines.isNotEmpty() && activeLyricIndex in musicState.lyricLines.indices) {
            listState.animateScrollToItem((activeLyricIndex - 2).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MhBg,
        contentColor = MhText,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1B3B2B),
                            Color(0xFF0E1D16),
                            MhBg
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Spotify Top Bar (Down Chevron | Source Label | Cover/Lyrics Toggle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "收起播放器",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "正在播放自",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                        Text(
                            text = musicState.resolvedVia.ifEmpty { track.platform.uppercase() },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Cover / Lyrics View Switcher Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { showFullLyricsMode = !showFullLyricsMode }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (showFullLyricsMode) "封面" else "词",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showFullLyricsMode) MhSpotifyGreen else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Center Stage: Album Artwork + Mini Lyric Preview OR Full Synchronized Lyrics Card
                if (!showFullLyricsMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(310.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        TrackAlbumCover(
                            title = track.name,
                            picUrl = track.pic,
                            size = 228.dp,
                            cornerRadius = 12.dp,
                            isPlaying = false,
                            modifier = Modifier.clickable { showFullLyricsMode = true }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Current + Next Synchronized Lyric Preview (tap to expand full lyrics)
                        val curLine = musicState.lyricLines.getOrNull(activeLyricIndex)?.text
                            ?: if (musicState.isLoadingLyric) "正在加载同步歌词…" else "轻触封面查看全屏歌词"
                        Text(
                            text = curLine,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MhSpotifyGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFullLyricsMode = true }
                        )
                    }
                } else {
                    // Spotify Lyrics Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(310.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF182C22))
                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            musicState.isLoadingLyric -> {
                                CircularProgressIndicator(
                                    color = MhSpotifyGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            musicState.lyricLines.isEmpty() -> {
                                Text(
                                    text = "暂无同步歌词",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MhTextDim
                                )
                            }
                            else -> {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(musicState.lyricLines) { index, line ->
                                        val isCurrent = index == activeLyricIndex
                                        Text(
                                            text = line.text,
                                            style = if (isCurrent) {
                                                MaterialTheme.typography.titleLarge.copy(
                                                    fontSize = 19.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 26.sp
                                                )
                                            } else {
                                                MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            },
                                            color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.42f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (musicState.durationSec > 0) {
                                                        onSeek((line.timeSec / musicState.durationSec).toFloat())
                                                    }
                                                }
                                                .padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 3. Spotify Track Title + Artist + Liked Heart + Audio Quality Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = buildString {
                                append(track.singer.ifEmpty { "未知歌手" })
                                if (track.album.isNotEmpty()) append(" · ${track.album}")
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = Color(0xFFB3B3B3),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Audio Quality Cycle Chip (FLAC / 320K / 128K)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                val list = listOf("flac", "320k", "128k")
                                val next = list[(list.indexOf(musicState.preferredQuality) + 1) % list.size]
                                onSelectQuality(next)
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = musicState.preferredQuality.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MhSpotifyGreen
                        )
                    }

                    IconButton(onClick = onToggleLiked) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "喜欢",
                            tint = if (isLiked) MhSpotifyGreen else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Spotify Scrubber & Timestamps
                val sliderVal = if (musicState.durationSec > 0) {
                    (musicState.currentTimeSec / musicState.durationSec).toFloat().coerceIn(0f, 1f)
                } else 0f

                Slider(
                    value = sliderVal,
                    onValueChange = onSeek,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MhSpotifyGreen,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationSec(musicState.currentTimeSec),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB3B3B3)
                    )
                    Text(
                        text = formatDurationSec(musicState.durationSec),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB3B3B3)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Spotify Transport Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle / Mode button
                    IconButton(onClick = onCycleMode) {
                        val isShuffle = musicState.playMode == PlayMode.SHUFFLE
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "随机播放",
                            tint = if (isShuffle) MhSpotifyGreen else Color(0xFFB3B3B3),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "上一首",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Spotify Iconic 64dp White Circle Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(onClick = onTogglePlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (musicState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "播放或暂停",
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "下一首",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Repeat mode button
                    IconButton(onClick = onCycleMode) {
                        val isSingle = musicState.playMode == PlayMode.SINGLE_LOOP
                        Icon(
                            imageVector = if (isSingle) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = musicState.playMode.label,
                            tint = if (isSingle) MhSpotifyGreen else Color(0xFFB3B3B3),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
