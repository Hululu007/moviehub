package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.network.LocalMediaProxyServer
import com.example.data.network.MovieHubNetwork
import com.example.ui.VodPlayerState
import com.example.ui.components.MinimalPill
import com.example.ui.components.SectionHeader
import com.example.ui.theme.MhBg
import com.example.ui.theme.MhBgSoft
import com.example.ui.theme.MhBorder
import com.example.ui.theme.MhCard
import com.example.ui.theme.MhCardHover
import com.example.ui.theme.MhDanger
import com.example.ui.theme.MhText
import com.example.ui.theme.MhTextDim
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

enum class VideoResizeMode(val label: String, val media3Mode: Int) {
    FIT("适应", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("裁剪", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("拉伸", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun VodDetailPlayerScreen(
    playerState: VodPlayerState,
    onBack: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectLine: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onNextEpisode: () -> Unit,
    onToggleEpisodesOrder: () -> Unit,
    onToggleEngine: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onAggregateSources: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onProgressUpdate: (Long, Long) -> Unit
) {
    val item = playerState.item ?: return
    val context = LocalContext.current
    val activity = context as? Activity

    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var useHorizontalEpisodes by remember { mutableStateOf(false) }

    fun setLandscape(landscape: Boolean) {
        isFullscreen = landscape
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        if (isFullscreen) {
            setLandscape(false)
        } else {
            onBack()
        }
    }

    val activeEpisode = playerState.activeEpisode
    val rawStreamUrl = activeEpisode?.url.orEmpty()
    val proxiedStreamUrl = remember(rawStreamUrl) {
        if (rawStreamUrl.isNotEmpty()) {
            LocalMediaProxyServer.getVideoProxyUrl(rawStreamUrl)
        } else ""
    }

    val activeMatch = playerState.activeSourceMatch
    val activeSourceName = activeMatch?.sourceName ?: item.sourceName.ifEmpty { "默认源" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MhBg)
    ) {
        // ─────────────────── 1. 沉浸式影院级播放器舞台 ───────────────────
        Box(
            modifier = if (isFullscreen) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            },
            contentAlignment = Alignment.Center
        ) {
            when {
                playerState.isLoadingDetail -> {
                    // Minimalist loading stage with subtle Top Back Button
                    Box(modifier = Modifier.fillMaxSize()) {
                        CinemaTopHeaderBar(
                            title = item.name,
                            subtitle = "正在解析片源…",
                            useWebViewHls = playerState.useWebViewHls,
                            resizeLabel = resizeMode.label,
                            onBack = {
                                if (isFullscreen) setLandscape(false) else onBack()
                            },
                            onCycleResize = {
                                val nextOrd = (resizeMode.ordinal + 1) % VideoResizeMode.entries.size
                                resizeMode = VideoResizeMode.entries[nextOrd]
                            },
                            onToggleEngine = onToggleEngine,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MhText,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在检索《${item.name}》最佳流媒体源…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MhTextDim
                            )
                        }
                    }
                }

                playerState.detailError != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CinemaTopHeaderBar(
                            title = item.name,
                            subtitle = activeSourceName,
                            useWebViewHls = playerState.useWebViewHls,
                            resizeLabel = resizeMode.label,
                            onBack = {
                                if (isFullscreen) setLandscape(false) else onBack()
                            },
                            onCycleResize = {},
                            onToggleEngine = onToggleEngine,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = playerState.detailError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MhDanger
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            MinimalPill(
                                text = "全网重新搜源",
                                selected = true,
                                onClick = onAggregateSources
                            )
                        }
                    }
                }

                rawStreamUrl.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CinemaTopHeaderBar(
                            title = item.name,
                            subtitle = activeSourceName,
                            useWebViewHls = playerState.useWebViewHls,
                            resizeLabel = resizeMode.label,
                            onBack = {
                                if (isFullscreen) setLandscape(false) else onBack()
                            },
                            onCycleResize = {},
                            onToggleEngine = onToggleEngine,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        Text(
                            text = "当前线路暂无播放地址，请尝试下方其他片源或线路",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MhTextDim,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                playerState.useWebViewHls -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WebViewHlsVideoPlayer(
                            streamUrl = proxiedStreamUrl,
                            rawUrl = rawStreamUrl,
                            modifier = Modifier.fillMaxSize()
                        )
                        CinemaTopHeaderBar(
                            title = item.name,
                            subtitle = "$activeSourceName · ${activeEpisode?.title.orEmpty()}",
                            useWebViewHls = true,
                            resizeLabel = resizeMode.label,
                            onBack = {
                                if (isFullscreen) setLandscape(false) else onBack()
                            },
                            onCycleResize = {},
                            onToggleEngine = onToggleEngine,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }

                else -> {
                    CinemaExoHlsVideoPlayer(
                        title = item.name,
                        subtitle = "$activeSourceName · ${activeEpisode?.title.orEmpty()}",
                        proxiedUrl = proxiedStreamUrl,
                        rawUrl = rawStreamUrl,
                        resumePositionSec = playerState.resumePositionSec,
                        playbackSpeed = playbackSpeed,
                        videoResizeMode = resizeMode,
                        isFullscreen = isFullscreen,
                        useWebViewHls = false,
                        onBack = {
                            if (isFullscreen) setLandscape(false) else onBack()
                        },
                        onToggleFullscreen = { setLandscape(!isFullscreen) },
                        onCycleResize = {
                            val nextOrd = (resizeMode.ordinal + 1) % VideoResizeMode.entries.size
                            resizeMode = VideoResizeMode.entries[nextOrd]
                            onShowToast("画面比例：${resizeMode.label}")
                        },
                        onChangeSpeed = { spd ->
                            playbackSpeed = spd
                        },
                        onToggleEngine = onToggleEngine,
                        onNextEpisode = onNextEpisode,
                        onProgressUpdate = onProgressUpdate,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ─────────────────── 2. 竖屏详情、控制条与选集工作台 ───────────────────
        if (!isFullscreen) {
            // Sleek Quick-Control & Utility Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MhBgSoft)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Speed pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "倍速",
                        style = MaterialTheme.typography.labelSmall,
                        color = MhTextDim,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                        val selected = playbackSpeed == spd
                        val label = if (spd == 1.0f) "1.0x" else "${spd}x"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) MhText else Color.Transparent)
                                .clickable { playbackSpeed = spd }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MhBg else MhTextDim
                            )
                        }
                    }
                }

                // Right quick utilities: Copy stream link + Next episode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (rawStreamUrl.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("stream_url", rawStreamUrl))
                                    onShowToast("已复制流媒体地址")
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制流地址",
                                tint = MhTextDim,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "复制流",
                                fontSize = 11.sp,
                                color = MhTextDim
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MhCardHover)
                            .clickable(onClick = onNextEpisode)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "下一集",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MhText
                        )
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "下一集",
                            tint = MhText,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MhBorder)

            // Editorial Content Sheet
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Editorial Film Dossier (Poster + Metadata + Expandable Synopsis)
                item {
                    val posterUrl = item.pic.ifEmpty { activeMatch?.pic.orEmpty() }
                    val year = item.year.ifEmpty { activeMatch?.year.orEmpty() }
                    val area = item.area.ifEmpty { activeMatch?.area.orEmpty() }
                    val typeName = item.typeName.ifEmpty { activeMatch?.typeName.orEmpty() }
                    val remarks = item.remarks.ifEmpty { activeMatch?.remarks.orEmpty() }
                    val director = item.director.ifEmpty { activeMatch?.director.orEmpty() }
                    val actor = item.actor.ifEmpty { activeMatch?.actor.orEmpty() }
                    val content = item.content.ifEmpty { activeMatch?.content.orEmpty() }
                        .replace(Regex("<[^>]*>"), "")
                        .trim()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Miniature Editorial Poster
                            if (posterUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = posterUrl,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(66.dp)
                                        .height(94.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MhCard)
                                        .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = (-0.3).sp
                                        ),
                                        color = MhText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isFavorite) MhText else MhBgSoft)
                                            .border(0.5.dp, if (isFavorite) MhText else MhBorder, RoundedCornerShape(999.dp))
                                            .clickable(onClick = onToggleFavorite)
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                            .testTag("toggle_favorite_vod"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = "收藏片单",
                                            tint = if (isFavorite) MhBg else MhText,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (isFavorite) "已收藏" else "片单",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isFavorite) MhBg else MhText
                                        )
                                    }
                                }

                                // Micro Metadata Badges
                                val badges = listOf(year, area, typeName, remarks).filter { it.isNotBlank() }
                                if (badges.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        badges.forEachIndexed { idx, tag ->
                                            val isHighlight = idx == badges.lastIndex && remarks.isNotBlank()
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isHighlight) MhCardHover else MhBgSoft)
                                                    .border(0.5.dp, MhBorder, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = tag,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isHighlight) FontWeight.Medium else FontWeight.Normal,
                                                    color = if (isHighlight) MhText else MhTextDim
                                                )
                                            }
                                        }
                                    }
                                }

                                // Currently playing indicator pill
                                activeEpisode?.let { ep ->
                                    Text(
                                        text = "当前播放：${ep.title}  ·  $activeSourceName",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MhTextDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (director.isNotBlank() || actor.isNotBlank()) {
                                    Text(
                                        text = listOfNotNull(
                                            director.takeIf { it.isNotBlank() }?.let { "导演：$it" },
                                            actor.takeIf { it.isNotBlank() }?.let { "主演：$it" }
                                        ).joinToString("   "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MhTextDim,
                                        maxLines = if (isSynopsisExpanded) 4 else 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Expandable Synopsis Box
                        if (content.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MhBgSoft)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                                    .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = content,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 12.5.sp,
                                        lineHeight = 19.sp
                                    ),
                                    color = MhTextDim,
                                    maxLines = if (isSynopsisExpanded) 20 else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = if (isSynopsisExpanded) "收起简介 ▴" else "展开简介 ▾",
                                        fontSize = 11.sp,
                                        color = MhText.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Multi-Source Switcher (多源聚合切换)
                val sources = item.sources
                if (sources.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "播放片源",
                            subtitle = if (playerState.isMatchingMoreSources) {
                                "正在聚合全网同名源…"
                            } else {
                                "${sources.size} 个可用源"
                            },
                            actionText = if (playerState.isMatchingMoreSources) null else "全网搜源",
                            onActionClick = if (playerState.isMatchingMoreSources) null else onAggregateSources
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sources.forEachIndexed { idx, srcMatch ->
                                val selected = idx == playerState.selectedSourceIndex
                                val epCount = srcMatch.lines.firstOrNull()?.episodes?.size ?: 0
                                val bg = if (selected) MhText else MhBgSoft
                                val fg = if (selected) MhBg else MhText
                                val subFg = if (selected) MhBg.copy(alpha = 0.7f) else MhTextDim

                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .border(
                                            0.5.dp,
                                            if (selected) MhText else MhBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSelectSource(idx) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (selected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(MhBg)
                                            )
                                        }
                                        Text(
                                            text = srcMatch.sourceName,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                            ),
                                            color = fg
                                        )
                                    }
                                    Text(
                                        text = if (srcMatch.remarks.isNotBlank()) {
                                            "${srcMatch.remarks} · ${epCount}集"
                                        } else {
                                            "共 $epCount 集"
                                        },
                                        fontSize = 10.5.sp,
                                        color = subFg,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Play Line Switcher (CDN 线路切换)
                val lines = playerState.activeLines
                if (lines.size > 1) {
                    item {
                        SectionHeader(
                            title = "CDN 线路",
                            subtitle = "${lines.size} 条线路"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            lines.forEachIndexed { idx, line ->
                                MinimalPill(
                                    text = "${line.flag} · ${line.episodes.size}",
                                    selected = idx == playerState.selectedLineIndex,
                                    onClick = { onSelectLine(idx) }
                                )
                            }
                        }
                    }
                }

                // 4. Smart Chunked & Dual-Mode Episode Selector (选集)
                val episodes = playerState.activeLine?.episodes ?: emptyList()
                if (episodes.isNotEmpty()) {
                    item {
                        val chunkSize = 24
                        val totalChunks = ceil(episodes.size / chunkSize.toDouble()).toInt().coerceAtLeast(1)
                        var selectedChunk by remember(episodes.size, playerState.selectedSourceIndex, playerState.selectedLineIndex) {
                            mutableIntStateOf((playerState.selectedEpisodeIndex / chunkSize).coerceIn(0, totalChunks - 1))
                        }

                        // Keep chunk in sync when active episode advances
                        LaunchedEffect(playerState.selectedEpisodeIndex) {
                            val targetChunk = (playerState.selectedEpisodeIndex / chunkSize).coerceIn(0, totalChunks - 1)
                            selectedChunk = targetChunk
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "选集",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = (-0.2).sp
                                    ),
                                    color = MhText
                                )
                                Text(
                                    text = "${playerState.selectedEpisodeIndex + 1} / ${episodes.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MhTextDim
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (useHorizontalEpisodes) "网格视图" else "横滑视图",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MhTextDim,
                                    modifier = Modifier.clickable {
                                        useHorizontalEpisodes = !useHorizontalEpisodes
                                    }
                                )
                                Text(
                                    text = if (playerState.episodesAscending) "正序 ⇅" else "倒序 ⇅",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MhTextDim,
                                    modifier = Modifier.clickable(onClick = onToggleEpisodesOrder)
                                )
                            }
                        }

                        // Range Tabs for multi-chunk series (> 24 episodes)
                        if (totalChunks > 1 && !useHorizontalEpisodes) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (c in 0 until totalChunks) {
                                    val startEp = c * chunkSize + 1
                                    val endEp = ((c + 1) * chunkSize).coerceAtMost(episodes.size)
                                    MinimalPill(
                                        text = "$startEp-$endEp",
                                        selected = selectedChunk == c,
                                        onClick = { selectedChunk = c }
                                    )
                                }
                            }
                        }

                        val indexedEpisodes = episodes.mapIndexed { idx, ep -> idx to ep }

                        if (useHorizontalEpisodes) {
                            val displayList = if (playerState.episodesAscending) {
                                indexedEpisodes
                            } else {
                                indexedEpisodes.reversed()
                            }
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayList, key = { it.first }) { (realIdx, ep) ->
                                    EpisodeTile(
                                        title = ep.title,
                                        isCurrent = realIdx == playerState.selectedEpisodeIndex,
                                        onClick = { onSelectEpisode(realIdx) },
                                        modifier = Modifier.width(96.dp)
                                    )
                                }
                            }
                        } else {
                            val chunkStart = selectedChunk * chunkSize
                            val chunkEnd = (chunkStart + chunkSize).coerceAtMost(indexedEpisodes.size)
                            val chunkSlice = indexedEpisodes.subList(chunkStart, chunkEnd)
                            val displayEpisodes = if (playerState.episodesAscending) {
                                chunkSlice
                            } else {
                                chunkSlice.reversed()
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 4
                            ) {
                                displayEpisodes.forEach { (realIdx, ep) ->
                                    EpisodeTile(
                                        title = ep.title,
                                        isCurrent = realIdx == playerState.selectedEpisodeIndex,
                                        onClick = { onSelectEpisode(realIdx) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }
}

@Composable
private fun EpisodeTile(
    title: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) MhText else MhBgSoft)
            .border(
                0.5.dp,
                if (isCurrent) MhText else MhBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isCurrent) {
                Text(
                    text = "▸",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MhBg
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isCurrent) MhBg else MhText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CinemaTopHeaderBar(
    title: String,
    subtitle: String,
    useWebViewHls: Boolean,
    resizeLabel: String,
    onBack: () -> Unit,
    onCycleResize: () -> Unit,
    onToggleEngine: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.78f),
                        Color.Black.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(38.dp)
                .testTag("vod_player_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!useWebViewHls) {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onCycleResize)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "画面比例",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = resizeLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .border(0.5.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
                .clickable(onClick = onToggleEngine)
                .padding(horizontal = 9.dp, vertical = 4.dp)
                .testTag("toggle_player_engine")
        ) {
            Text(
                text = if (useWebViewHls) "WebView" else "ExoPlayer",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CinemaExoHlsVideoPlayer(
    title: String,
    subtitle: String,
    proxiedUrl: String,
    rawUrl: String,
    resumePositionSec: Long,
    playbackSpeed: Float,
    videoResizeMode: VideoResizeMode,
    isFullscreen: Boolean,
    useWebViewHls: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onCycleResize: () -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onToggleEngine: () -> Unit,
    onNextEpisode: () -> Unit,
    onProgressUpdate: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var triedDirectFallback by remember(proxiedUrl) { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPosMs by remember { mutableLongStateOf(0L) }

    var showHud by remember { mutableStateOf(true) }
    var isHudLocked by remember { mutableStateOf(false) }
    var isHoldBoost2x by remember { mutableStateOf(false) }
    var seekDeltaLabel by remember { mutableStateOf<String?>(null) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember(context) {
        val dataSourceFactory = OkHttpDataSource.Factory(MovieHubNetwork.okHttpClient)
            .setUserAgent(MovieHubNetwork.USER_AGENT)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    val effectiveSpeed = if (isHoldBoost2x) 2.0f else playbackSpeed
    LaunchedEffect(effectiveSpeed) {
        exoPlayer.setPlaybackSpeed(effectiveSpeed)
    }

    LaunchedEffect(proxiedUrl) {
        if (proxiedUrl.isNotEmpty()) {
            triedDirectFallback = false
            isBuffering = true
            exoPlayer.setMediaItem(MediaItem.fromUri(proxiedUrl))
            exoPlayer.prepare()
            if (resumePositionSec > 3L) {
                exoPlayer.seekTo(resumePositionSec * 1000L)
            }
            exoPlayer.playWhenReady = true
        }
    }

    // Auto-hide HUD after 3.5 seconds when playing
    LaunchedEffect(showHud, isPlaying, isDraggingSlider) {
        if (showHud && isPlaying && !isDraggingSlider) {
            delay(3600)
            showHud = false
        }
    }

    // Auto-clear seek delta indicator
    LaunchedEffect(seekDeltaLabel) {
        if (seekDeltaLabel != null) {
            delay(650)
            seekDeltaLabel = null
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    onNextEpisode()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!triedDirectFallback && rawUrl.isNotEmpty() && rawUrl != proxiedUrl) {
                    triedDirectFallback = true
                    exoPlayer.setMediaItem(MediaItem.fromUri(rawUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Poll playback position smoothly every 500ms and save progress every 5s
    LaunchedEffect(exoPlayer, proxiedUrl) {
        var tick = 0
        while (isActive) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            bufferedPosMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            tick++
            if (tick % 10 == 0 && exoPlayer.isPlaying) {
                val posSec = (currentPosMs / 1000L).coerceAtLeast(0L)
                val durSec = (durationMs / 1000L).coerceAtLeast(0L)
                if (posSec > 0) {
                    onProgressUpdate(posSec, durSec)
                }
            }
            delay(500)
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        // 1. Underlying ExoPlayer Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    resizeMode = videoResizeMode.media3Mode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
                if (view.resizeMode != videoResizeMode.media3Mode) {
                    view.resizeMode = videoResizeMode.media3Mode
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Gesture Layer: Single-tap HUD, Double-tap -10s/+10s, Long-press 2.0x boost
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isHudLocked) {
                    detectTapGestures(
                        onTap = {
                            showHud = !showHud
                        },
                        onDoubleTap = { offset ->
                            if (isHudLocked) return@detectTapGestures
                            val width = size.width
                            when {
                                offset.x < width * 0.38f -> {
                                    val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                    seekDeltaLabel = "-10s"
                                }
                                offset.x > width * 0.62f -> {
                                    val maxDur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                    val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                    seekDeltaLabel = "+10s"
                                }
                                else -> {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            }
                        },
                        onPress = {
                            if (isHudLocked) return@detectTapGestures
                            val boostJob = coroutineScope.launch {
                                delay(350)
                                isHoldBoost2x = true
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                boostJob.cancel()
                                isHoldBoost2x = false
                            }
                        }
                    )
                }
                .pointerInput(isHudLocked) {
                    if (isHudLocked) return@pointerInput
                    var dragStartX = 0f
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val activity = context as? Activity
                    var brightnessAccumulator = (activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f)
                    var volumeFraction = audioManager?.let { am ->
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()
                    } ?: 0.5f

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            brightnessAccumulator = (activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: brightnessAccumulator)
                            audioManager?.let { am ->
                                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                volumeFraction = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()
                            }
                        },
                        onDragEnd = {
                            seekDeltaLabel = null
                        },
                        onDragCancel = {
                            seekDeltaLabel = null
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val height = size.height.coerceAtLeast(1)
                            val delta = -dragAmount / (height * 0.85f)
                            if (dragStartX < size.width * 0.5f) {
                                // Left half: Screen Brightness
                                brightnessAccumulator = (brightnessAccumulator + delta).coerceIn(0.05f, 1.0f)
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = brightnessAccumulator
                                    win.attributes = lp
                                }
                                seekDeltaLabel = "亮度 ${(brightnessAccumulator * 100).toInt()}%"
                            } else {
                                // Right half: Media Volume
                                volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
                                audioManager?.let { am ->
                                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val targetVol = (volumeFraction * max).toInt().coerceIn(0, max)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                }
                                seekDeltaLabel = "音量 ${(volumeFraction * 100).toInt()}%"
                            }
                        }
                    )
                }
        )

        // 3. Hold-to-2.0x Speed Pill Indicator
        AnimatedVisibility(
            visible = isHoldBoost2x,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "▸▸ 2.0x 倍速播放中",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // 4. Double-Tap Seek Feedback Pill (-10s / +10s)
        AnimatedVisibility(
            visible = seekDeltaLabel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = seekDeltaLabel.orEmpty(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // 5. Buffering Minimalist Spinner (when HUD is hidden or visible)
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.9f),
                strokeWidth = 1.8.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
            )
        }

        // 6. Cinema HUD Overlay
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isHudLocked) {
                    // Locked mode: only show Lock toggle button on left edge
                    IconButton(
                        onClick = { isHudLocked = false },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "解锁屏幕",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Top Gradient Header
                    CinemaTopHeaderBar(
                        title = title,
                        subtitle = subtitle,
                        useWebViewHls = useWebViewHls,
                        resizeLabel = videoResizeMode.label,
                        onBack = onBack,
                        onCycleResize = onCycleResize,
                        onToggleEngine = onToggleEngine,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // Left Edge Screen Lock Button
                    IconButton(
                        onClick = { isHudLocked = true },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 14.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "锁定屏幕",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Center Glass Transport Controls (-10s | Play/Pause | +10s)
                    if (!isBuffering) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "快退10秒",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val maxDur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                    val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "快进10秒",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Bottom Gradient Scrubber & Transport Bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.84f)
                                    )
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        val safeDuration = durationMs.coerceAtLeast(1L)
                        val sliderFraction = if (isDraggingSlider) {
                            dragSliderValue
                        } else {
                            (currentPosMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                        }

                        Slider(
                            value = sliderFraction,
                            onValueChange = { frac ->
                                isDraggingSlider = true
                                dragSliderValue = frac
                            },
                            onValueChangeFinished = {
                                val seekTarget = (dragSliderValue * safeDuration).toLong()
                                exoPlayer.seekTo(seekTarget)
                                currentPosMs = seekTarget
                                isDraggingSlider = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "播放/暂停",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        }
                                )

                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "下一集",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onNextEpisode
                                        )
                                )

                                val displayPosMs = if (isDraggingSlider) {
                                    (dragSliderValue * safeDuration).toLong()
                                } else {
                                    currentPosMs
                                }
                                Text(
                                    text = "${formatVideoDuration(displayPosMs)} / ${formatVideoDuration(durationMs)}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Skip Intro (+90s) quick pill inside HUD
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .clickable {
                                            val maxDur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                            val target = (exoPlayer.currentPosition + 90_000L).coerceAtMost(maxDur)
                                            exoPlayer.seekTo(target)
                                            currentPosMs = target
                                            seekDeltaLabel = "已跳过片头 +90s"
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "跳过片头",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                // Speed quick-cycle button inside HUD
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .clickable {
                                            val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)
                                            val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                                            onChangeSpeed(speeds[nextIdx])
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = onToggleFullscreen,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Ambient Hairline Progress Indicator when HUD is hidden
        if (!showHud && durationMs > 0L) {
            val progressFraction = (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val bufferedFraction = (bufferedPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFraction)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.25f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(MhText)
                )
            }
        }
    }
}

private fun formatVideoDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun NativeExoHlsVideoPlayer(
    proxiedUrl: String,
    rawUrl: String,
    resumePositionSec: Long,
    playbackSpeed: Float,
    onNextEpisode: () -> Unit,
    onProgressUpdate: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var triedDirectFallback by remember(proxiedUrl) { mutableStateOf(false) }

    val exoPlayer = remember(context) {
        val dataSourceFactory = OkHttpDataSource.Factory(MovieHubNetwork.okHttpClient)
            .setUserAgent(MovieHubNetwork.USER_AGENT)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(proxiedUrl) {
        if (proxiedUrl.isNotEmpty()) {
            triedDirectFallback = false
            exoPlayer.setMediaItem(MediaItem.fromUri(proxiedUrl))
            exoPlayer.prepare()
            if (resumePositionSec > 3L) {
                exoPlayer.seekTo(resumePositionSec * 1000L)
            }
            exoPlayer.playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onNextEpisode()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!triedDirectFallback && rawUrl.isNotEmpty() && rawUrl != proxiedUrl) {
                    triedDirectFallback = true
                    exoPlayer.setMediaItem(MediaItem.fromUri(rawUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, proxiedUrl) {
        while (isActive) {
            delay(5000)
            if (exoPlayer.isPlaying) {
                val posSec = (exoPlayer.currentPosition / 1000L).coerceAtLeast(0L)
                val durSec = (exoPlayer.duration / 1000L).coerceAtLeast(0L)
                if (posSec > 0) {
                    onProgressUpdate(posSec, durSec)
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            if (view.player != exoPlayer) {
                view.player = exoPlayer
            }
        },
        modifier = modifier
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewHlsVideoPlayer(
    streamUrl: String,
    rawUrl: String,
    modifier: Modifier = Modifier
) {
    val htmlContent = remember(streamUrl, rawUrl) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
          <style>
            html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
            video { width:100%; height:100%; object-fit:contain; background:#000; }
            video::-webkit-media-controls-picture-in-picture-button { display:none !important; }
          </style>
          <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
        </head>
        <body>
          <video id="v" controls autoplay playsinline disablePictureInPicture controlsList="nodownload noplaybackrate"></video>
          <script>
            const v = document.getElementById('v');
            const src = ${ org.json.JSONObject.quote(streamUrl.ifEmpty { rawUrl }) };
            if (window.Hls && Hls.isSupported()) {
              const hls = new Hls();
              hls.loadSource(src);
              hls.attachMedia(v);
              hls.on(Hls.Events.MANIFEST_PARSED, () => v.play().catch(()=>{}));
            } else {
              v.src = src;
              v.play().catch(()=>{});
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("http://127.0.0.1/", htmlContent, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}
