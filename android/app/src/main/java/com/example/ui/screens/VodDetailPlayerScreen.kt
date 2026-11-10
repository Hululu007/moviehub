package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.VideoDownloadEntity
import com.example.data.model.MovieHubData
import com.example.data.model.VodEpisode
import com.example.data.model.VodPlayLine
import com.example.data.model.VodSourceMatch
import com.example.data.network.LocalMediaProxyServer
import com.example.data.network.MovieHubNetwork
import com.example.ui.VodPlayerState
import com.example.ui.components.MinimalPill
import com.example.ui.theme.MhBg
import com.example.ui.theme.MhBgSoft
import com.example.ui.theme.MhBorder
import com.example.ui.theme.MhCard
import com.example.ui.theme.MhCardHover
import com.example.ui.theme.MhDanger
import com.example.ui.theme.MhSpotifyGreen
import com.example.ui.theme.MhText
import com.example.ui.theme.MhTextDim
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VideoResizeMode(val label: String, val media3Mode: Int) {
    FIT("适应比例", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("铺满裁剪", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("全屏拉伸", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

enum class FullscreenOrientationStrategy(val label: String) {
    AUTO("按分辨率自适应"),
    PORTRAIT("强制直屏(竖屏)"),
    LANDSCAPE("强制横屏")
}

enum class VideoQualityPreset(
    val shortLabel: String,
    val badgeTitle: String,
    val subtitle: String,
    val maxWidth: Int,
    val maxHeight: Int
) {
    AUTO("自动", "自动画质", "ABR 智能自适应最高清晰度", Int.MAX_VALUE, Int.MAX_VALUE),
    UHD_4K("4K 臻彩", "4K 2160P", "超高清影院原盘画质", 3840, 2160),
    FHD_1080P("1080P 蓝光", "1080P 蓝光", "高码率全高清画质", 1920, 1080),
    HD_720P("720P 超清", "720P 超清", "高清流畅均衡模式", 1280, 720),
    SD_480P("480P 流畅", "480P 流畅", "移动网络省流极速模式", 854, 480)
}

enum class PlayerOverlayPanel {
    NONE,
    QUALITY,
    SPEED,
    EPISODES,
    MORE_SETTINGS
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
    onSpeedTestSources: () -> Unit = {},
    videoDownloads: List<VideoDownloadEntity> = emptyList(),
    onDownloadEpisodes: (List<Int>) -> Unit = {},
    onOpenDownloadCenter: () -> Unit = {},
    onOpenDiagnosticsCenter: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onProgressUpdate: (Long, Long) -> Unit
) {
    val item = playerState.item ?: return
    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current

    val activeMatch = playerState.activeSourceMatch
    val activeSourceName = activeMatch?.sourceName ?: item.sourceName.ifEmpty { "默认源" }
    val sources = item.sources
    val lines = playerState.activeLines
    val episodes = playerState.activeLine?.episodes ?: emptyList()

    val initialPortraitGuess = remember(item.name, item.typeName, item.remarks, activeMatch?.typeName, activeMatch?.remarks) {
        MovieHubData.isLikelyPortraitShortDrama(
            typeName = item.typeName.ifEmpty { activeMatch?.typeName },
            title = item.name,
            remarks = item.remarks.ifEmpty { activeMatch?.remarks }
        )
    }

    var isFullscreen by remember { mutableStateOf(false) }
    var didRotateLandscape by remember { mutableStateOf(false) }
    var isPortraitVideoStream by remember(item.vodId, item.name) { mutableStateOf(initialPortraitGuess) }
    var orientationStrategy by remember { mutableStateOf(FullscreenOrientationStrategy.AUTO) }

    val effectivePortraitMode = when (orientationStrategy) {
        FullscreenOrientationStrategy.AUTO -> isPortraitVideoStream
        FullscreenOrientationStrategy.PORTRAIT -> true
        FullscreenOrientationStrategy.LANDSCAPE -> false
    }

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var qualityPreset by remember { mutableStateOf(VideoQualityPreset.FHD_1080P) }
    var isVideoMirrored by remember { mutableStateOf(false) }
    var skipIntroSec by remember { mutableIntStateOf(0) } // 0, 30, 60, 90, 120
    var skipOutroSec by remember { mutableIntStateOf(0) } // 0, 60, 90, 120
    var sleepTimerMinutes by remember { mutableIntStateOf(0) } // 0 = off, -1 = end of ep, 15, 30, 60
    var activeOverlayPanel by remember { mutableStateOf(PlayerOverlayPanel.NONE) }
    var decodedResolutionLabel by remember { mutableStateOf("") }

    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var useHorizontalEpisodes by remember { mutableStateOf(false) }
    var showEpisodeDownloadDialog by remember { mutableStateOf(false) }
    var activeWorkspaceTab by remember { mutableIntStateOf(0) } // 0: 选集与片源, 1: 影片详情与播放设置

    // Right-swipe back gesture state
    var swipeBackOffsetPx by remember { mutableFloatStateOf(0f) }
    val swipeBackThresholdPx = remember(density) { with(density) { 88.dp.toPx() } }

    fun applyFullscreenState(fullscreen: Boolean, portraitStream: Boolean = effectivePortraitMode) {
        isFullscreen = fullscreen
        if (fullscreen) {
            if (portraitStream) {
                // Vertical short drama (height > width): keep phone upright in Portrait Fullscreen!
                if (didRotateLandscape) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    didRotateLandscape = false
                }
            } else {
                // Horizontal movie/TV (width >= height): rotate to Landscape Fullscreen
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                didRotateLandscape = true
            }
        } else {
            if (didRotateLandscape) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                didRotateLandscape = false
            }
        }
    }

    // Automatically re-sync screen orientation if video resolution is detected while already in fullscreen
    LaunchedEffect(isFullscreen, effectivePortraitMode) {
        if (isFullscreen) {
            applyFullscreenState(true, effectivePortraitMode)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (didRotateLandscape) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    fun triggerFastBack() {
        if (activeOverlayPanel != PlayerOverlayPanel.NONE) {
            activeOverlayPanel = PlayerOverlayPanel.NONE
            return
        }
        if (isFullscreen) {
            applyFullscreenState(false)
        } else {
            onBack()
        }
    }

    BackHandler {
        triggerFastBack()
    }

    val activeEpisode = playerState.activeEpisode
    val rawEpisodeUrl = activeEpisode?.url.orEmpty()
    val matchedOfflineDownload = remember(rawEpisodeUrl, videoDownloads) {
        if (rawEpisodeUrl.isEmpty()) null
        else videoDownloads.find {
            it.status == "COMPLETED" &&
                it.localFilePath.isNotEmpty() &&
                (it.remoteUrl == rawEpisodeUrl || it.localFilePath == rawEpisodeUrl)
        }
    }
    val rawStreamUrl = matchedOfflineDownload?.localFilePath?.ifEmpty { rawEpisodeUrl } ?: rawEpisodeUrl
    val proxiedStreamUrl = remember(rawStreamUrl) {
        if (rawStreamUrl.startsWith("http", ignoreCase = true)) {
            LocalMediaProxyServer.getVideoProxyUrl(rawStreamUrl)
        } else {
            rawStreamUrl
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MhBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MhBg)
        ) {
            // ─────────────────── 1. 旗舰级商业影院播放器舞台（支持直屏短剧/横屏宽幕自适应） ───────────────────
            Box(
                modifier = when {
                    isFullscreen -> Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                    effectivePortraitMode -> Modifier
                        .fillMaxWidth()
                        .aspectRatio(10f / 11.5f)
                        .background(Color.Black)
                    else -> Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                },
                contentAlignment = Alignment.Center
            ) {
                when {
                    playerState.isLoadingDetail -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CinemaTopHeaderBar(
                                title = item.name,
                                subtitle = "正在智能优选最快片源…",
                                qualityBadge = qualityPreset.shortLabel,
                                decodedResolution = "",
                                useWebViewHls = playerState.useWebViewHls,
                                resizeLabel = resizeMode.label,
                                onBack = ::triggerFastBack,
                                onOpenQuality = { activeOverlayPanel = PlayerOverlayPanel.QUALITY },
                                onCycleResize = {
                                    val nextOrd = (resizeMode.ordinal + 1) % VideoResizeMode.entries.size
                                    resizeMode = VideoResizeMode.entries[nextOrd]
                                },
                                onToggleEngine = onToggleEngine,
                                onOpenMoreSettings = { activeOverlayPanel = PlayerOverlayPanel.MORE_SETTINGS },
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MhSpotifyGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "正在并发检索《${item.name}》最高清流媒体源…",
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
                                qualityBadge = qualityPreset.shortLabel,
                                decodedResolution = "",
                                useWebViewHls = playerState.useWebViewHls,
                                resizeLabel = resizeMode.label,
                                onBack = ::triggerFastBack,
                                onOpenQuality = { activeOverlayPanel = PlayerOverlayPanel.QUALITY },
                                onCycleResize = {},
                                onToggleEngine = onToggleEngine,
                                onOpenMoreSettings = { activeOverlayPanel = PlayerOverlayPanel.MORE_SETTINGS },
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
                                qualityBadge = qualityPreset.shortLabel,
                                decodedResolution = "",
                                useWebViewHls = playerState.useWebViewHls,
                                resizeLabel = resizeMode.label,
                                onBack = ::triggerFastBack,
                                onOpenQuality = { activeOverlayPanel = PlayerOverlayPanel.QUALITY },
                                onCycleResize = {},
                                onToggleEngine = onToggleEngine,
                                onOpenMoreSettings = { activeOverlayPanel = PlayerOverlayPanel.MORE_SETTINGS },
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                            Text(
                                text = "当前线路暂无播放地址，请点击清晰度/换源切换其他片源",
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
                                qualityBadge = qualityPreset.shortLabel,
                                decodedResolution = decodedResolutionLabel,
                                useWebViewHls = true,
                                resizeLabel = resizeMode.label,
                                onBack = ::triggerFastBack,
                                onOpenQuality = { activeOverlayPanel = PlayerOverlayPanel.QUALITY },
                                onCycleResize = {},
                                onToggleEngine = onToggleEngine,
                                onOpenMoreSettings = { activeOverlayPanel = PlayerOverlayPanel.MORE_SETTINGS },
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
                            qualityPreset = qualityPreset,
                            isVideoMirrored = isVideoMirrored,
                            skipIntroSec = skipIntroSec,
                            skipOutroSec = skipOutroSec,
                            sleepTimerMinutes = sleepTimerMinutes,
                            isFullscreen = isFullscreen,
                            useWebViewHls = false,
                            activeOverlayPanel = activeOverlayPanel,
                            sources = sources,
                            selectedSourceIndex = playerState.selectedSourceIndex,
                            lines = lines,
                            selectedLineIndex = playerState.selectedLineIndex,
                            episodes = episodes,
                            selectedEpisodeIndex = playerState.selectedEpisodeIndex,
                            isSpeedTestingSources = playerState.isSpeedTestingSources,
                            onOpenOverlayPanel = { panel -> activeOverlayPanel = panel },
                            onCloseOverlayPanel = { activeOverlayPanel = PlayerOverlayPanel.NONE },
                            onSelectQualityPreset = { preset ->
                                qualityPreset = preset
                                onShowToast("已切换清晰度：${preset.badgeTitle}")
                            },
                            onSelectSource = { idx ->
                                onSelectSource(idx)
                                activeOverlayPanel = PlayerOverlayPanel.NONE
                            },
                            onSelectLine = { idx ->
                                onSelectLine(idx)
                                activeOverlayPanel = PlayerOverlayPanel.NONE
                            },
                            onSelectEpisode = { idx ->
                                onSelectEpisode(idx)
                                activeOverlayPanel = PlayerOverlayPanel.NONE
                            },
                            onSpeedTestSources = onSpeedTestSources,
                            onToggleMirror = {
                                isVideoMirrored = !isVideoMirrored
                                onShowToast(if (isVideoMirrored) "已开启画面水平镜像" else "已恢复正常画面方向")
                            },
                            onChangeSkipIntro = { sec ->
                                skipIntroSec = sec
                                onShowToast(if (sec > 0) "已设置自动跳过片头 ${sec}秒" else "已关闭自动跳过片头")
                            },
                            onChangeSkipOutro = { sec ->
                                skipOutroSec = sec
                                onShowToast(if (sec > 0) "已设置自动跳过片尾 ${sec}秒" else "已关闭自动跳过片尾")
                            },
                            onChangeSleepTimer = { mins ->
                                sleepTimerMinutes = mins
                                val label = when (mins) {
                                    0 -> "已关闭睡眠定时"
                                    -1 -> "将在播完本集后自动暂停"
                                    else -> "将在 ${mins} 分钟后自动暂停播放"
                                }
                                onShowToast(label)
                            },
                            onDecodedResolutionChanged = { resLabel, isPortrait ->
                                decodedResolutionLabel = resLabel
                                isPortraitVideoStream = isPortrait
                            },
                            isPortraitMode = effectivePortraitMode,
                            orientationStrategy = orientationStrategy,
                            onChangeOrientationStrategy = { strategy ->
                                orientationStrategy = strategy
                                onShowToast("全屏方向策略：${strategy.label}")
                            },
                            onSwipeRightBackProgress = { px ->
                                swipeBackOffsetPx = px
                            },
                            onBack = ::triggerFastBack,
                            onToggleFullscreen = {
                                val nextFull = !isFullscreen
                                applyFullscreenState(nextFull, effectivePortraitMode)
                            },
                            onCycleResize = {
                                val nextOrd = (resizeMode.ordinal + 1) % VideoResizeMode.entries.size
                                resizeMode = VideoResizeMode.entries[nextOrd]
                                onShowToast("画面比例：${resizeMode.label}")
                            },
                            onChangeSpeed = { spd ->
                                playbackSpeed = spd
                                onShowToast("播放倍速：${spd}x")
                            },
                            onToggleEngine = onToggleEngine,
                            onNextEpisode = onNextEpisode,
                            onProgressUpdate = onProgressUpdate,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // ─────────────────── 2. 竖屏旗舰交互工作台（支持右滑极速返回） ───────────────────
            if (!isFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            var startX = 0f
                            var draggingBack = false
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    startX = offset.x
                                    // Trigger swipe-right-to-go-back from left 75% of screen when swiping right
                                    draggingBack = startX < size.width * 0.72f
                                    swipeBackOffsetPx = 0f
                                },
                                onDragEnd = {
                                    if (draggingBack && swipeBackOffsetPx >= swipeBackThresholdPx) {
                                        swipeBackOffsetPx = 0f
                                        triggerFastBack()
                                    } else {
                                        swipeBackOffsetPx = 0f
                                    }
                                    draggingBack = false
                                },
                                onDragCancel = {
                                    swipeBackOffsetPx = 0f
                                    draggingBack = false
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (!draggingBack) return@detectHorizontalDragGestures
                                    val next = swipeBackOffsetPx + dragAmount
                                    if (next > 0f) {
                                        change.consume()
                                        swipeBackOffsetPx = next.coerceAtMost(swipeBackThresholdPx * 1.6f)
                                    }
                                }
                            )
                        }
                ) {
                    // 2A. 旗舰播控功能栏：清晰度切换 · 倍速 · 跳片头 · 下一集 · 离线缓存
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MhBgSoft)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Resolution / Quality Switcher Pill (换清晰度)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MhText)
                                .clickable { activeOverlayPanel = PlayerOverlayPanel.QUALITY }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("vod_quality_switcher_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HighQuality,
                                contentDescription = "换清晰度",
                                tint = MhBg,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (decodedResolutionLabel.isNotBlank()) {
                                    "${qualityPreset.shortLabel} · $decodedResolutionLabel ▾"
                                } else {
                                    "清晰度: ${qualityPreset.shortLabel} ▾"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MhBg
                            )
                        }

                        // 2. Portrait / Landscape Adaptive Pill (直屏/横屏自适应切换)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (effectivePortraitMode) MhSpotifyGreen.copy(alpha = 0.18f)
                                    else MhCardHover
                                )
                                .border(
                                    0.5.dp,
                                    if (effectivePortraitMode) MhSpotifyGreen.copy(alpha = 0.6f) else MhBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (!isFullscreen && effectivePortraitMode) {
                                        // One-tap enter vertical portrait fullscreen for short dramas!
                                        applyFullscreenState(true, true)
                                    } else {
                                        val entries = FullscreenOrientationStrategy.entries
                                        val next = entries[(entries.indexOf(orientationStrategy) + 1) % entries.size]
                                        orientationStrategy = next
                                        onShowToast("屏幕方向：${next.label}")
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("vod_orientation_adaptive_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (effectivePortraitMode) "📱 竖屏全屏" else "🖥 横屏全屏",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (effectivePortraitMode) MhSpotifyGreen else MhText
                            )
                        }

                        // 3. Speed Picker Pill (倍速)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MhCardHover)
                                .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                .clickable { activeOverlayPanel = PlayerOverlayPanel.SPEED }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "倍速",
                                tint = MhText,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (playbackSpeed == 1.0f) "倍速 1.0x ▾" else "⚡ ${playbackSpeed}x ▾",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (playbackSpeed == 1.0f) MhText else MhSpotifyGreen
                            )
                        }

                        // 3. Auto Skip Intro Pill (跳过片头设置)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MhCardHover)
                                .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    val options = listOf(0, 30, 60, 90, 120)
                                    val next = options[(options.indexOf(skipIntroSec) + 1) % options.size]
                                    skipIntroSec = next
                                    onShowToast(if (next > 0) "已开启自动跳过片头 ${next}秒" else "已关闭自动跳过片头")
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (skipIntroSec > 0) "跳片头 ${skipIntroSec}s" else "跳片头: 关",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (skipIntroSec > 0) MhSpotifyGreen else MhTextDim
                            )
                        }

                        // 4. Download / Offline Cache Pill
                        if (rawEpisodeUrl.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MhCardHover)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                    .clickable { showEpisodeDownloadDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("vod_download_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (matchedOfflineDownload != null) "✓ 已离线缓存" else "⬇ 离线下载",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MhSpotifyGreen
                                )
                            }
                        }

                        // 5. Next Episode Pill
                        if (episodes.size > 1) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MhCardHover)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onNextEpisode)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "下一集",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MhText
                                )
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "下一集",
                                    tint = MhText,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // 6. More Player Settings Pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MhCardHover)
                                .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                .clickable { activeOverlayPanel = PlayerOverlayPanel.MORE_SETTINGS }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚙ 画面/定时",
                                fontSize = 11.5.sp,
                                color = MhTextDim
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MhBorder)

                    // 2B. 商业级双栏工作台切换标签 (剧集与换源 | 简介与高级设置) + 右滑返回提示
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MhBg)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             listOf(
                                0 to "选集与片源 (${episodes.size})",
                                1 to "影片详情与高级设置"
                            ).forEach { (tabIdx, tabTitle) ->
                                val selected = activeWorkspaceTab == tabIdx
                                Column(
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { activeWorkspaceTab = tabIdx },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = tabTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 14.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (selected) MhText else MhTextDim
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(if (selected) 24.dp else 0.dp)
                                            .height(2.5.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (selected) MhSpotifyGreen else Color.Transparent)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "👉 右滑快速返回",
                            fontSize = 10.5.sp,
                            color = MhTextDim.copy(alpha = 0.75f)
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MhBorder)

                    // 2C. 主体内容区
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Compact Header Card (always visible at top)
                        item {
                            val posterUrl = item.pic.ifEmpty { activeMatch?.pic.orEmpty() }
                            val year = item.year.ifEmpty { activeMatch?.year.orEmpty() }
                            val area = item.area.ifEmpty { activeMatch?.area.orEmpty() }
                            val typeName = item.typeName.ifEmpty { activeMatch?.typeName.orEmpty() }
                            val remarks = item.remarks.ifEmpty { activeMatch?.remarks.orEmpty() }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (posterUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(52.dp)
                                            .height(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MhCard)
                                            .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MhText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (isFavorite) MhText else MhBgSoft)
                                                .border(
                                                    0.5.dp,
                                                    if (isFavorite) MhText else MhBorder,
                                                    RoundedCornerShape(999.dp)
                                                )
                                                .clickable(onClick = onToggleFavorite)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
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
                                                text = if (isFavorite) "已收藏" else "收藏",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isFavorite) MhBg else MhText
                                            )
                                        }
                                    }

                                    val badges = listOf(
                                        qualityPreset.shortLabel,
                                        decodedResolutionLabel,
                                        year,
                                        area,
                                        typeName,
                                        remarks
                                    ).filter { it.isNotBlank() }

                                    if (badges.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            badges.forEachIndexed { idx, tag ->
                                                val isQualityTag = idx == 0 || tag == decodedResolutionLabel
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (isQualityTag) MhSpotifyGreen.copy(alpha = 0.16f)
                                                            else MhBgSoft
                                                        )
                                                        .border(
                                                            0.5.dp,
                                                            if (isQualityTag) MhSpotifyGreen.copy(alpha = 0.45f) else MhBorder,
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = if (isQualityTag) FontWeight.SemiBold else FontWeight.Normal,
                                                        color = if (isQualityTag) MhSpotifyGreen else MhTextDim
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    activeEpisode?.let { ep ->
                                        Text(
                                            text = if (matchedOfflineDownload != null) {
                                                "正在播放：${ep.title} · ✓ 本地缓存直放"
                                            } else {
                                                "正在播放：${ep.title} · $activeSourceName"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (matchedOfflineDownload != null) MhSpotifyGreen else MhTextDim,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (activeWorkspaceTab == 0) {
                            // ── TAB 0: 选集 · 多源清晰度节点 · CDN 线路 ──
                            if (sources.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "片源与画质节点",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MhText
                                            )
                                            Text(
                                                text = when {
                                                    playerState.isMatchingMoreSources -> "正在聚合全网高清源…"
                                                    playerState.isSpeedTestingSources -> "正在并发测速选优…"
                                                    else -> "${sources.size} 个可用源"
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MhTextDim
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (sources.size > 1) {
                                                Text(
                                                    text = if (playerState.isSpeedTestingSources) "测速中…" else "⚡ 测速优选",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MhSpotifyGreen,
                                                    modifier = Modifier.clickable {
                                                        if (!playerState.isSpeedTestingSources) onSpeedTestSources()
                                                    }
                                                )
                                            }
                                            if (!playerState.isMatchingMoreSources) {
                                                Text(
                                                    text = "全网搜源",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MhTextDim,
                                                    modifier = Modifier.clickable(onClick = onAggregateSources)
                                                )
                                            }
                                        }
                                    }

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
                                            val subFg = if (selected) MhBg.copy(alpha = 0.72f) else MhTextDim
                                            val latency = if (srcMatch.latencyMs > 0L) {
                                                srcMatch.latencyMs
                                            } else {
                                                MovieHubNetwork.sourceLatencyMap[srcMatch.sourceId] ?: 0L
                                            }

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
                                                    .padding(horizontal = 13.dp, vertical = 8.dp),
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
                                                    if (latency in 1..8500) {
                                                        val pingColor = when {
                                                            selected -> MhBg.copy(alpha = 0.85f)
                                                            latency < 800L -> MhSpotifyGreen
                                                            latency < 2000L -> Color(0xFFF5A623)
                                                            else -> MhTextDim
                                                        }
                                                        Text(
                                                            text = "${latency}ms",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = pingColor
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = if (srcMatch.remarks.isNotBlank()) {
                                                        "${srcMatch.remarks} · ${epCount}集"
                                                    } else {
                                                        "高清蓝光 · 共 $epCount 集"
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

                            // CDN Line Switcher
                            if (lines.size > 1) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "CDN 线路:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MhTextDim
                                        )
                                        lines.forEachIndexed { idx, line ->
                                            MinimalPill(
                                                text = "${line.flag} (${line.episodes.size})",
                                                selected = idx == playerState.selectedLineIndex,
                                                onClick = { onSelectLine(idx) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Episode Selector Grid / Horizontal
                            if (episodes.isNotEmpty()) {
                                item {
                                    val chunkSize = 24
                                    val totalChunks = ceil(episodes.size / chunkSize.toDouble()).toInt().coerceAtLeast(1)
                                    var selectedChunk by remember(
                                        episodes.size,
                                        playerState.selectedSourceIndex,
                                        playerState.selectedLineIndex
                                    ) {
                                        mutableIntStateOf((playerState.selectedEpisodeIndex / chunkSize).coerceIn(0, totalChunks - 1))
                                    }

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
                                                text = "剧集选集",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MhText
                                            )
                                            Text(
                                                text = "正在播放第 ${playerState.selectedEpisodeIndex + 1} / ${episodes.size} 集",
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
                        } else {
                            // ── TAB 1: 影片详情 · 换清晰度 · 画面比例 · 片头片尾跳过 · 工具箱 ──
                            item {
                                val director = item.director.ifEmpty { activeMatch?.director.orEmpty() }
                                val actor = item.actor.ifEmpty { activeMatch?.actor.orEmpty() }
                                val content = item.content.ifEmpty { activeMatch?.content.orEmpty() }
                                    .replace(Regex("<[^>]*>"), "")
                                    .trim()

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize(),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Resolution & Playback Settings Card
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MhBgSoft)
                                            .border(0.5.dp, MhBorder, RoundedCornerShape(12.dp))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "画质与播放增强设置",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MhText
                                        )

                                        // Quality selector row
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "清晰度档位 (HLS 码率自适应切换)",
                                                fontSize = 11.5.sp,
                                                color = MhTextDim
                                            )
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                VideoQualityPreset.entries.forEach { preset ->
                                                    MinimalPill(
                                                        text = preset.shortLabel,
                                                        selected = qualityPreset == preset,
                                                        onClick = {
                                                            qualityPreset = preset
                                                            onShowToast("已切换清晰度：${preset.badgeTitle}")
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Aspect ratio & mirror
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "画面比例与镜像翻转",
                                                fontSize = 11.5.sp,
                                                color = MhTextDim
                                            )
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                VideoResizeMode.entries.forEach { mode ->
                                                    MinimalPill(
                                                        text = mode.label,
                                                        selected = resizeMode == mode,
                                                        onClick = { resizeMode = mode }
                                                    )
                                                }
                                                MinimalPill(
                                                    text = if (isVideoMirrored) "✓ 镜像翻转" else "水平镜像",
                                                    selected = isVideoMirrored,
                                                    onClick = { isVideoMirrored = !isVideoMirrored }
                                                )
                                            }
                                        }

                                        // Skip intro / outro
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "自动跳过片头时长",
                                                fontSize = 11.5.sp,
                                                color = MhTextDim
                                            )
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                listOf(0, 30, 60, 90, 120).forEach { sec ->
                                                    MinimalPill(
                                                        text = if (sec == 0) "不跳过" else "${sec}秒",
                                                        selected = skipIntroSec == sec,
                                                        onClick = { skipIntroSec = sec }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Utilities Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MinimalPill(
                                            text = "⬇ 批量缓存剧集",
                                            selected = true,
                                            onClick = { showEpisodeDownloadDialog = true }
                                        )
                                        MinimalPill(
                                            text = if (videoDownloads.isNotEmpty()) "缓存中心(${videoDownloads.size})" else "缓存中心",
                                            selected = false,
                                            onClick = onOpenDownloadCenter
                                        )
                                        MinimalPill(
                                            text = "🩺 渠道状态检测与查源",
                                            selected = false,
                                            onClick = onOpenDiagnosticsCenter
                                        )
                                        if (rawEpisodeUrl.isNotEmpty()) {
                                            MinimalPill(
                                                text = "复制流地址",
                                                selected = false,
                                                onClick = {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    cm?.setPrimaryClip(ClipData.newPlainText("stream_url", rawEpisodeUrl))
                                                    onShowToast("已复制流媒体地址")
                                                }
                                            )
                                        }
                                    }

                                    // Cast & Synopsis
                                    if (director.isNotBlank() || actor.isNotBlank() || content.isNotBlank()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MhBgSoft)
                                                .border(0.5.dp, MhBorder, RoundedCornerShape(12.dp))
                                                .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "演职员与剧情简介",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MhText
                                            )
                                            if (director.isNotBlank()) {
                                                Text(
                                                    text = "导演：$director",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MhTextDim
                                                )
                                            }
                                            if (actor.isNotBlank()) {
                                                Text(
                                                    text = "主演：$actor",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MhTextDim
                                                )
                                            }
                                            if (content.isNotBlank()) {
                                                Text(
                                                    text = content,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 12.5.sp,
                                                        lineHeight = 19.sp
                                                    ),
                                                    color = MhTextDim,
                                                    maxLines = if (isSynopsisExpanded) 30 else 4,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
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

        // ─────────────────── 3. 右滑返回视觉指示浮标 (Swipe-Right-to-Back Edge Pill) ───────────────────
        if (swipeBackOffsetPx > 12f) {
            val progress = (swipeBackOffsetPx / swipeBackThresholdPx).coerceIn(0f, 1.2f)
            val ready = progress >= 1f
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            x = (swipeBackOffsetPx * 0.42f).roundToInt().coerceAtMost(96),
                            y = 0
                        )
                    }
                    .clip(RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp))
                    .background(if (ready) MhSpotifyGreen else Color.Black.copy(alpha = 0.82f))
                    .border(
                        0.5.dp,
                        if (ready) MhSpotifyGreen else Color.White.copy(alpha = 0.28f),
                        RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "右滑返回",
                        tint = if (ready) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (ready) "松手立即返回" else "继续右滑返回",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ready) Color.Black else Color.White
                    )
                }
            }
        }
    }

    if (showEpisodeDownloadDialog) {
        val currentEpisodes = playerState.activeLine?.episodes.orEmpty()
        EpisodeBatchDownloadDialog(
            vodName = item.name,
            sourceName = activeSourceName,
            episodes = currentEpisodes,
            currentEpisodeIndex = playerState.selectedEpisodeIndex,
            existingDownloads = videoDownloads,
            sourceId = activeMatch?.sourceId ?: item.source,
            vodId = activeMatch?.vodId ?: item.vodId,
            onDismiss = { showEpisodeDownloadDialog = false },
            onConfirmDownloadEpisodes = { selectedIndices ->
                showEpisodeDownloadDialog = false
                onDownloadEpisodes(selectedIndices)
            },
            onOpenDownloadCenter = {
                showEpisodeDownloadDialog = false
                onOpenDownloadCenter()
            }
        )
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
    qualityBadge: String,
    decodedResolution: String,
    useWebViewHls: Boolean,
    resizeLabel: String,
    onBack: () -> Unit,
    onOpenQuality: () -> Unit,
    onCycleResize: () -> Unit,
    onToggleEngine: () -> Unit,
    onOpenMoreSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.82f),
                        Color.Black.copy(alpha = 0.42f),
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
                    text = if (decodedResolution.isNotBlank()) "$subtitle · $decodedResolution" else subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Top Quality Switcher Pill
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MhSpotifyGreen.copy(alpha = 0.22f))
                .border(0.5.dp, MhSpotifyGreen.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenQuality)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                text = qualityBadge,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MhSpotifyGreen
            )
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
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .border(0.5.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
                .clickable(onClick = onToggleEngine)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("toggle_player_engine")
        ) {
            Text(
                text = if (useWebViewHls) "WebView" else "硬解",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        IconButton(
            onClick = onOpenMoreSettings,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多播放设置",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
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
    qualityPreset: VideoQualityPreset,
    isVideoMirrored: Boolean,
    skipIntroSec: Int,
    skipOutroSec: Int,
    sleepTimerMinutes: Int,
    isFullscreen: Boolean,
    useWebViewHls: Boolean,
    activeOverlayPanel: PlayerOverlayPanel,
    sources: List<VodSourceMatch>,
    selectedSourceIndex: Int,
    lines: List<VodPlayLine>,
    selectedLineIndex: Int,
    episodes: List<VodEpisode>,
    selectedEpisodeIndex: Int,
    isSpeedTestingSources: Boolean,
    onOpenOverlayPanel: (PlayerOverlayPanel) -> Unit,
    onCloseOverlayPanel: () -> Unit,
    onSelectQualityPreset: (VideoQualityPreset) -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectLine: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSpeedTestSources: () -> Unit,
    onToggleMirror: () -> Unit,
    onChangeSkipIntro: (Int) -> Unit,
    onChangeSkipOutro: (Int) -> Unit,
    onChangeSleepTimer: (Int) -> Unit,
    onDecodedResolutionChanged: (String, Boolean) -> Unit,
    isPortraitMode: Boolean,
    orientationStrategy: FullscreenOrientationStrategy,
    onChangeOrientationStrategy: (FullscreenOrientationStrategy) -> Unit,
    onSwipeRightBackProgress: (Float) -> Unit,
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
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var triedProxyFallback by remember(rawUrl, proxiedUrl) { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPosMs by remember { mutableLongStateOf(0L) }
    var decodedResolution by remember { mutableStateOf("") }

    var showHud by remember { mutableStateOf(true) }
    var isHudLocked by remember { mutableStateOf(false) }
    var isHoldBoost3x by remember { mutableStateOf(false) }
    var seekDeltaLabel by remember { mutableStateOf<String?>(null) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    // Horizontal swipe-to-seek state
    var isHorizontalSeeking by remember { mutableStateOf(false) }
    var horizontalSeekTargetMs by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember(context) {
        val httpDataSourceFactory = OkHttpDataSource.Factory(MovieHubNetwork.okHttpClient)
            .setUserAgent(MovieHubNetwork.USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 18_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 1_600
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Apply HLS TrackSelectionParameters when qualityPreset changes
    LaunchedEffect(qualityPreset, exoPlayer) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(qualityPreset.maxWidth, qualityPreset.maxHeight)
            .build()
    }

    // Apply playback speed (or 3.0x hold boost)
    val effectiveSpeed = if (isHoldBoost3x) 3.0f else playbackSpeed
    LaunchedEffect(effectiveSpeed) {
        exoPlayer.setPlaybackSpeed(effectiveSpeed)
    }

    // Sleep timer countdown
    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            delay(sleepTimerMinutes * 60_000L)
            exoPlayer.pause()
            onChangeSleepTimer(0)
        }
    }

    val primaryStreamUrl = rawUrl.ifEmpty { proxiedUrl }
    LaunchedEffect(primaryStreamUrl) {
        if (primaryStreamUrl.isNotEmpty()) {
            triedProxyFallback = false
            isBuffering = true
            exoPlayer.setMediaItem(MediaItem.fromUri(primaryStreamUrl))
            exoPlayer.prepare()
            val startSec = when {
                resumePositionSec > 3L -> resumePositionSec
                skipIntroSec > 0 -> skipIntroSec.toLong()
                else -> 0L
            }
            if (startSec > 0L) {
                exoPlayer.seekTo(startSec * 1000L)
                if (resumePositionSec <= 3L && skipIntroSec > 0) {
                    seekDeltaLabel = "已自动跳过片头 ${skipIntroSec}s"
                }
            }
            exoPlayer.playWhenReady = true
        }
    }

    // Auto-hide HUD after 3.6 seconds when playing and no overlay drawer is open
    LaunchedEffect(showHud, isPlaying, isDraggingSlider, activeOverlayPanel) {
        if (showHud && isPlaying && !isDraggingSlider && activeOverlayPanel == PlayerOverlayPanel.NONE) {
            delay(3600)
            showHud = false
        }
    }

    // Auto-clear center feedback pill
    LaunchedEffect(seekDeltaLabel) {
        if (seekDeltaLabel != null && !isHorizontalSeeking) {
            delay(850)
            seekDeltaLabel = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.playWhenReady = false
                exoPlayer.pause()
            }
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val isPortrait = videoSize.height > videoSize.width
                    val orientTag = if (isPortrait) "竖屏" else "横屏"
                    val maxDim = maxOf(videoSize.width, videoSize.height)
                    val minDim = minOf(videoSize.width, videoSize.height)
                    val tag = when {
                        maxDim >= 3600 || minDim >= 2000 -> "4K $orientTag (${videoSize.width}×${videoSize.height})"
                        maxDim >= 1800 || minDim >= 1000 -> "1080P $orientTag (${videoSize.width}×${videoSize.height})"
                        maxDim >= 1200 || minDim >= 700 -> "720P $orientTag (${videoSize.width}×${videoSize.height})"
                        else -> "$orientTag ${videoSize.width}×${videoSize.height}"
                    }
                    decodedResolution = tag
                    onDecodedResolutionChanged(tag, isPortrait)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    if (sleepTimerMinutes == -1) {
                        exoPlayer.pause()
                        onChangeSleepTimer(0)
                    } else {
                        onNextEpisode()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!triedProxyFallback && proxiedUrl.isNotEmpty() && proxiedUrl != primaryStreamUrl) {
                    triedProxyFallback = true
                    exoPlayer.setMediaItem(MediaItem.fromUri(proxiedUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        exoPlayer.addListener(listener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.volume = 0f
            exoPlayer.playWhenReady = false
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Poll playback position every 400ms, handle auto-skip outro, and save watch history every 5s
    LaunchedEffect(exoPlayer, proxiedUrl, skipOutroSec) {
        var tick = 0
        while (isActive) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            bufferedPosMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)

            if (skipOutroSec > 0 && durationMs > (skipOutroSec + 60) * 1000L) {
                val remainingMs = durationMs - currentPosMs
                if (remainingMs in 500L..(skipOutroSec * 1000L)) {
                    seekDeltaLabel = "已自动跳过片尾，即将播放下一集"
                    onNextEpisode()
                    delay(1500)
                }
            }

            tick++
            if (tick % 12 == 0 && exoPlayer.isPlaying) {
                val posSec = (currentPosMs / 1000L).coerceAtLeast(0L)
                val durSec = (durationMs / 1000L).coerceAtLeast(0L)
                if (posSec > 0) {
                    onProgressUpdate(posSec, durSec)
                }
            }
            delay(400)
        }
    }

    val leftEdgeBackZonePx = remember(density) { with(density) { 42.dp.toPx() } }
    val swipeBackThresholdPx = remember(density) { with(density) { 88.dp.toPx() } }

    Box(modifier = modifier.background(Color.Black)) {
        // 1. ExoPlayer Surface (with optional mirror flip)
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
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = if (isVideoMirrored) -1f else 1f
                }
        )

        // 2. Multi-Gesture Layer:
        //    - Single tap: toggle HUD or close drawer
        //    - Double tap: left -10s, center Play/Pause, right +10s
        //    - Long press: 3.0x speed boost
        //    - Horizontal drag from left edge: Swipe-right-to-go-back (右滑返回)
        //    - Horizontal drag in center: Live scrubbing seek (滑动精准调节进度)
        //    - Vertical drag: Left = Brightness, Right = Volume
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isHudLocked, activeOverlayPanel) {
                    detectTapGestures(
                        onTap = {
                            if (activeOverlayPanel != PlayerOverlayPanel.NONE) {
                                onCloseOverlayPanel()
                            } else {
                                showHud = !showHud
                            }
                        },
                        onDoubleTap = { offset ->
                            if (isHudLocked) return@detectTapGestures
                            val width = size.width
                            when {
                                offset.x < width * 0.36f -> {
                                    val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                    seekDeltaLabel = "⏪ 快退 10秒"
                                }
                                offset.x > width * 0.64f -> {
                                    val maxDur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                    val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                    seekDeltaLabel = "⏩ 快进 10秒"
                                }
                                else -> {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                        seekDeltaLabel = "⏸ 已暂停"
                                    } else {
                                        exoPlayer.play()
                                        seekDeltaLabel = "▶ 继续播放"
                                    }
                                }
                            }
                        },
                        onPress = {
                            if (isHudLocked) return@detectTapGestures
                            val boostJob = coroutineScope.launch {
                                delay(320)
                                isHoldBoost3x = true
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                boostJob.cancel()
                                isHoldBoost3x = false
                            }
                        }
                    )
                }
                .pointerInput(isHudLocked) {
                    if (isHudLocked) return@pointerInput
                    var dragStartX = 0f
                    var isEdgeBackSwipe = false
                    var edgeSwipeAccumPx = 0f
                    var seekStartPosMs = 0L
                    var seekAccumDeltaMs = 0L

                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            isEdgeBackSwipe = dragStartX <= leftEdgeBackZonePx
                            edgeSwipeAccumPx = 0f
                            if (!isEdgeBackSwipe) {
                                isHorizontalSeeking = true
                                seekStartPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                                horizontalSeekTargetMs = seekStartPosMs
                                seekAccumDeltaMs = 0L
                            }
                        },
                        onDragEnd = {
                            if (isEdgeBackSwipe) {
                                val shouldGoBack = edgeSwipeAccumPx >= swipeBackThresholdPx
                                onSwipeRightBackProgress(0f)
                                isEdgeBackSwipe = false
                                if (shouldGoBack) {
                                    exoPlayer.pause()
                                    onBack()
                                }
                            } else if (isHorizontalSeeking) {
                                isHorizontalSeeking = false
                                if (abs(seekAccumDeltaMs) > 1000L) {
                                    exoPlayer.seekTo(horizontalSeekTargetMs)
                                    currentPosMs = horizontalSeekTargetMs
                                }
                                seekDeltaLabel = null
                            }
                        },
                        onDragCancel = {
                            onSwipeRightBackProgress(0f)
                            isEdgeBackSwipe = false
                            isHorizontalSeeking = false
                            seekDeltaLabel = null
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (isEdgeBackSwipe) {
                                edgeSwipeAccumPx = (edgeSwipeAccumPx + dragAmount).coerceAtLeast(0f)
                                onSwipeRightBackProgress(edgeSwipeAccumPx)
                            } else if (isHorizontalSeeking) {
                                val width = size.width.coerceAtLeast(1)
                                // Full width swipe = 180 seconds scrub range
                                val deltaMs = ((dragAmount / width.toFloat()) * 180_000f).toLong()
                                seekAccumDeltaMs += deltaMs
                                val maxDur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                horizontalSeekTargetMs = (seekStartPosMs + seekAccumDeltaMs).coerceIn(0L, maxDur)
                                val diffSec = (horizontalSeekTargetMs - seekStartPosMs) / 1000L
                                val sign = if (diffSec >= 0) "+${diffSec}s" else "${diffSec}s"
                                seekDeltaLabel = "${formatVideoDuration(horizontalSeekTargetMs)} / ${formatVideoDuration(durationMs)} ($sign)"
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
                                brightnessAccumulator = (brightnessAccumulator + delta).coerceIn(0.05f, 1.0f)
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = brightnessAccumulator
                                    win.attributes = lp
                                }
                                seekDeltaLabel = "☀ 亮度 ${(brightnessAccumulator * 100).toInt()}%"
                            } else {
                                volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
                                audioManager?.let { am ->
                                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val targetVol = (volumeFraction * max).toInt().coerceIn(0, max)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                }
                                seekDeltaLabel = "🔊 音量 ${(volumeFraction * 100).toInt()}%"
                            }
                        }
                    )
                }
        )

        // 3. Long-Press 3.0x Speed Boost Badge
        AnimatedVisibility(
            visible = isHoldBoost3x,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(0.5.dp, MhSpotifyGreen, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "⚡ 长按 3.0x 极速快进中",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MhSpotifyGreen
                )
            }
        }

        // 4. Center Seek / Brightness / Volume Feedback Pill
        AnimatedVisibility(
            visible = seekDeltaLabel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = seekDeltaLabel.orEmpty(),
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // 5. Buffering Spinner
        if (isBuffering && seekDeltaLabel == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MhSpotifyGreen,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "正在缓冲高清流…",
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }
        }

        // 6. Flagship Cinema HUD Overlay
        AnimatedVisibility(
            visible = showHud && activeOverlayPanel == PlayerOverlayPanel.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isHudLocked) {
                    IconButton(
                        onClick = { isHudLocked = false },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(0.5.dp, MhSpotifyGreen, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "解锁屏幕",
                            tint = MhSpotifyGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Top Gradient Bar
                    CinemaTopHeaderBar(
                        title = title,
                        subtitle = subtitle,
                        qualityBadge = qualityPreset.shortLabel,
                        decodedResolution = decodedResolution,
                        useWebViewHls = useWebViewHls,
                        resizeLabel = videoResizeMode.label,
                        onBack = {
                            exoPlayer.pause()
                            onBack()
                        },
                        onOpenQuality = { onOpenOverlayPanel(PlayerOverlayPanel.QUALITY) },
                        onCycleResize = onCycleResize,
                        onToggleEngine = onToggleEngine,
                        onOpenMoreSettings = { onOpenOverlayPanel(PlayerOverlayPanel.MORE_SETTINGS) },
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
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "锁定屏幕防误触",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Center Transport Controls (-10s | Play/Pause | +10s)
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
                                    seekDeltaLabel = "⏪ -10s"
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.24f), CircleShape)
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
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.62f))
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
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
                                    seekDeltaLabel = "⏩ +10s"
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.24f), CircleShape)
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

                    // Bottom Gradient Scrubber & Commercial Control Bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.88f)
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
                                thumbColor = MhSpotifyGreen,
                                activeTrackColor = MhSpotifyGreen,
                                inactiveTrackColor = Color.White.copy(alpha = 0.26f)
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
                            // Left: Play/Pause, Next, Time
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
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }

                            // Right: Skip Intro | Episodes | Quality (清晰度) | Speed (倍速) | Fullscreen
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Instant +90s Skip Intro Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .clickable {
                                            val maxDur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                            val target = (exoPlayer.currentPosition + 90_000L).coerceAtMost(maxDur)
                                            exoPlayer.seekTo(target)
                                            currentPosMs = target
                                            seekDeltaLabel = "已跳过片头 +90s"
                                        }
                                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = "+90s片头",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                // In-player Episodes Drawer Trigger
                                if (episodes.size > 1) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.White.copy(alpha = 0.16f))
                                            .clickable { onOpenOverlayPanel(PlayerOverlayPanel.EPISODES) }
                                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                                    ) {
                                        Text(
                                            text = "选集",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }

                                // In-player Quality / Resolution Drawer Trigger (换清晰度)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MhSpotifyGreen.copy(alpha = 0.25f))
                                        .border(0.5.dp, MhSpotifyGreen.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                                        .clickable { onOpenOverlayPanel(PlayerOverlayPanel.QUALITY) }
                                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = qualityPreset.shortLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MhSpotifyGreen
                                    )
                                }

                                // In-player Speed Drawer Trigger (倍速)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .clickable { onOpenOverlayPanel(PlayerOverlayPanel.SPEED) }
                                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = onToggleFullscreen,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "退出全屏" else "全屏播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. In-Player Commercial Side/Overlay Panels (清晰度与换源 | 倍速 | 选集 | 更多设置)
        AnimatedVisibility(
            visible = activeOverlayPanel != PlayerOverlayPanel.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCloseOverlayPanel
                    )
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(if (isFullscreen) 320.dp else 285.dp)
                        .background(Color(0xFF101216).copy(alpha = 0.96f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.14f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (activeOverlayPanel) {
                                PlayerOverlayPanel.QUALITY -> "切换清晰度与高清片源"
                                PlayerOverlayPanel.SPEED -> "播放倍速调节"
                                PlayerOverlayPanel.EPISODES -> "快速选集 (${selectedEpisodeIndex + 1}/${episodes.size})"
                                PlayerOverlayPanel.MORE_SETTINGS -> "播放增强与定时设置"
                                PlayerOverlayPanel.NONE -> ""
                            },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "关闭 ✕",
                            fontSize = 11.sp,
                            color = MhTextDim,
                            modifier = Modifier.clickable(onClick = onCloseOverlayPanel)
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))

                    when (activeOverlayPanel) {
                        PlayerOverlayPanel.QUALITY -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (decodedResolution.isNotBlank()) {
                                    item {
                                        Text(
                                            text = "当前实时解码分辨率：$decodedResolution",
                                            fontSize = 11.sp,
                                            color = MhSpotifyGreen,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                item {
                                    Text(
                                        text = "清晰度档位",
                                        fontSize = 11.sp,
                                        color = MhTextDim
                                    )
                                }

                                items(VideoQualityPreset.entries) { preset ->
                                    val selected = preset == qualityPreset
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (selected) MhSpotifyGreen.copy(alpha = 0.2f)
                                                else Color.White.copy(alpha = 0.06f)
                                            )
                                            .border(
                                                0.5.dp,
                                                if (selected) MhSpotifyGreen else Color.White.copy(alpha = 0.12f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                onSelectQualityPreset(preset)
                                                onCloseOverlayPanel()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = preset.badgeTitle,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (selected) MhSpotifyGreen else Color.White
                                            )
                                            Text(
                                                text = preset.subtitle,
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.65f)
                                            )
                                        }
                                        if (selected) {
                                            Text(
                                                text = "当前",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MhSpotifyGreen
                                            )
                                        }
                                    }
                                }

                                if (sources.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "切换高清片源节点 (${sources.size})",
                                                fontSize = 11.sp,
                                                color = MhTextDim
                                            )
                                            Text(
                                                text = if (isSpeedTestingSources) "测速中…" else "⚡ 测速优选",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MhSpotifyGreen,
                                                modifier = Modifier.clickable(onClick = onSpeedTestSources)
                                            )
                                        }
                                    }

                                    items(sources.size) { idx ->
                                        val src = sources[idx]
                                        val selected = idx == selectedSourceIndex
                                        val ping = if (src.latencyMs > 0L) src.latencyMs else (MovieHubNetwork.sourceLatencyMap[src.sourceId] ?: 0L)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (selected) Color.White.copy(alpha = 0.18f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                )
                                                .clickable { onSelectSource(idx) }
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = src.sourceName,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = Color.White
                                                )
                                                if (src.remarks.isNotBlank()) {
                                                    Text(
                                                        text = src.remarks,
                                                        fontSize = 10.sp,
                                                        color = Color.White.copy(alpha = 0.65f)
                                                    )
                                                }
                                            }
                                            if (ping in 1..8500) {
                                                Text(
                                                    text = "${ping}ms",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (ping < 800) MhSpotifyGreen else Color(0xFFF5A623)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (lines.size > 1) {
                                    item {
                                        Text(
                                            text = "切换 CDN 线路",
                                            fontSize = 11.sp,
                                            color = MhTextDim
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            lines.forEachIndexed { idx, line ->
                                                MinimalPill(
                                                    text = line.flag,
                                                    selected = idx == selectedLineIndex,
                                                    onClick = { onSelectLine(idx) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        PlayerOverlayPanel.SPEED -> {
                            val speeds = listOf(
                                0.5f to "0.5x 慢速细节",
                                0.75f to "0.75x 柔和慢放",
                                1.0f to "1.0x 原速播放",
                                1.25f to "1.25x 轻快追剧",
                                1.5f to "1.5x 高效刷剧",
                                2.0f to "2.0x 双倍速",
                                3.0f to "3.0x 极速浏览"
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(speeds) { (spd, desc) ->
                                    val selected = playbackSpeed == spd
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (selected) MhSpotifyGreen.copy(alpha = 0.22f)
                                                else Color.White.copy(alpha = 0.06f)
                                            )
                                            .border(
                                                0.5.dp,
                                                if (selected) MhSpotifyGreen else Color.White.copy(alpha = 0.12f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                onChangeSpeed(spd)
                                                onCloseOverlayPanel()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = desc,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) MhSpotifyGreen else Color.White
                                        )
                                        if (selected) {
                                            Text(
                                                text = "✓",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MhSpotifyGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        PlayerOverlayPanel.EPISODES -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(episodes.size) { idx ->
                                    val ep = episodes[idx]
                                    val isCurrent = idx == selectedEpisodeIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isCurrent) MhSpotifyGreen.copy(alpha = 0.22f)
                                                else Color.White.copy(alpha = 0.06f)
                                            )
                                            .clickable { onSelectEpisode(idx) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ep.title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MhSpotifyGreen else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCurrent) {
                                            Text(
                                                text = "正在播放",
                                                fontSize = 10.sp,
                                                color = MhSpotifyGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        PlayerOverlayPanel.MORE_SETTINGS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Text(
                                        text = "全屏方向策略 (${if (isPortraitMode) "当前：直屏竖屏" else "当前：宽幕横屏"})",
                                        fontSize = 11.sp,
                                        color = MhTextDim
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FullscreenOrientationStrategy.entries.forEach { strategy ->
                                            MinimalPill(
                                                text = strategy.label,
                                                selected = orientationStrategy == strategy,
                                                onClick = { onChangeOrientationStrategy(strategy) }
                                            )
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "画面比例与镜像",
                                        fontSize = 11.sp,
                                        color = MhTextDim
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MinimalPill(
                                            text = videoResizeMode.label,
                                            selected = true,
                                            onClick = onCycleResize
                                        )
                                        MinimalPill(
                                            text = if (isVideoMirrored) "✓ 镜像翻转" else "水平镜像",
                                            selected = isVideoMirrored,
                                            onClick = onToggleMirror
                                        )
                                    }
                                }

                                item {
                                    Text(
                                        text = "自动跳过片头",
                                        fontSize = 11.sp,
                                        color = MhTextDim
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(0, 30, 60, 90, 120).forEach { sec ->
                                            MinimalPill(
                                                text = if (sec == 0) "关闭" else "${sec}s",
                                                selected = skipIntroSec == sec,
                                                onClick = { onChangeSkipIntro(sec) }
                                            )
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "自动跳过片尾",
                                        fontSize = 11.sp,
                                        color = MhTextDim
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(0, 60, 90, 120).forEach { sec ->
                                            MinimalPill(
                                                text = if (sec == 0) "关闭" else "${sec}s",
                                                selected = skipOutroSec == sec,
                                                onClick = { onChangeSkipOutro(sec) }
                                            )
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "睡眠定时关闭",
                                        fontSize = 11.sp,
                                        color = MhTextDim
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            0 to "不开启",
                                            -1 to "播完本集",
                                            15 to "15分钟",
                                            30 to "30分钟",
                                            60 to "60分钟"
                                        ).forEach { (mins, label) ->
                                            MinimalPill(
                                                text = label,
                                                selected = sleepTimerMinutes == mins,
                                                onClick = { onChangeSleepTimer(mins) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        PlayerOverlayPanel.NONE -> Unit
                    }
                }
            }
        }

        // 8. Ambient Hairline Progress Indicator when HUD is hidden
        if (!showHud && durationMs > 0L) {
            val progressFraction = (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val bufferedFraction = (bufferedPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFraction)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.28f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(MhSpotifyGreen)
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
    var triedProxyFallback by remember(rawUrl, proxiedUrl) { mutableStateOf(false) }

    val exoPlayer = remember(context) {
        val httpDataSourceFactory = OkHttpDataSource.Factory(MovieHubNetwork.okHttpClient)
            .setUserAgent(MovieHubNetwork.USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 800, 1_600)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    val primaryUrl = rawUrl.ifEmpty { proxiedUrl }
    LaunchedEffect(primaryUrl) {
        if (primaryUrl.isNotEmpty()) {
            triedProxyFallback = false
            exoPlayer.setMediaItem(MediaItem.fromUri(primaryUrl))
            exoPlayer.prepare()
            if (resumePositionSec > 3L) {
                exoPlayer.seekTo(resumePositionSec * 1000L)
            }
            exoPlayer.playWhenReady = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.playWhenReady = false
                exoPlayer.pause()
            }
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onNextEpisode()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!triedProxyFallback && proxiedUrl.isNotEmpty() && proxiedUrl != primaryUrl) {
                    triedProxyFallback = true
                    exoPlayer.setMediaItem(MediaItem.fromUri(proxiedUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        exoPlayer.addListener(listener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.volume = 0f
            exoPlayer.playWhenReady = false
            exoPlayer.stop()
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                webViewRef?.evaluateJavascript("document.querySelectorAll('video').forEach(v => v.pause());", null)
                webViewRef?.onPause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                webViewRef?.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef?.let { wv ->
                wv.loadUrl("about:blank")
                wv.stopLoading()
                wv.destroy()
            }
            webViewRef = null
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef = this
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
            webViewRef = webView
            webView.loadDataWithBaseURL("http://127.0.0.1/", htmlContent, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}
