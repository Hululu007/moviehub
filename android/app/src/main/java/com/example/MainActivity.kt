package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainTab
import com.example.ui.MovieHubViewModel
import com.example.ui.components.FullLyricsModalSheet
import com.example.ui.components.PersistentMusicBottomBar
import com.example.ui.screens.AppUpdateCenterModalSheet
import com.example.ui.screens.CategoryBrowseScreen
import com.example.ui.screens.HomeVodScreen
import com.example.ui.screens.LiveTvScreen
import com.example.ui.screens.MusicCenterScreen
import com.example.ui.screens.SourceDiagnosticsModalSheet
import com.example.ui.screens.VideoDownloadCenterModalSheet
import com.example.ui.screens.VodDetailPlayerScreen
import com.example.ui.theme.MhBg
import com.example.ui.theme.MhBorder
import com.example.ui.theme.MhCardHover
import com.example.ui.theme.MhText
import com.example.ui.theme.MhTextDim
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MovieHubApp()
            }
        }
    }
}

private data class NavTabItem(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun MovieHubApp(viewModel: MovieHubViewModel = viewModel()) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val vodSources by viewModel.vodSources.collectAsStateWithLifecycle()
    val remoteSyncState by viewModel.remoteSyncState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSearchSrcId by viewModel.selectedSearchSrcId.collectAsStateWithLifecycle()

    val doubanType by viewModel.doubanType.collectAsStateWithLifecycle()
    val doubanTag by viewModel.doubanTag.collectAsStateWithLifecycle()
    val doubanItems by viewModel.doubanItems.collectAsStateWithLifecycle()
    val homeChannelBlocks by viewModel.homeChannelBlocks.collectAsStateWithLifecycle()
    val isLoadingDouban by viewModel.isLoadingDouban.collectAsStateWithLifecycle()
    val doubanError by viewModel.doubanError.collectAsStateWithLifecycle()

    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val favoriteVods by viewModel.favoriteVods.collectAsStateWithLifecycle()
    val recentVodSearches by viewModel.recentVodSearches.collectAsStateWithLifecycle()
    val latestVodItems by viewModel.latestVodItems.collectAsStateWithLifecycle()
    val latestSourceName by viewModel.latestSourceName.collectAsStateWithLifecycle()
    val isLoadingLatest by viewModel.isLoadingLatest.collectAsStateWithLifecycle()
    val latestError by viewModel.latestError.collectAsStateWithLifecycle()

    val isSearchOverlayOpen by viewModel.isSearchOverlayOpen.collectAsStateWithLifecycle()
    val isSearchingVod by viewModel.isSearchingVod.collectAsStateWithLifecycle()
    val vodSearchError by viewModel.vodSearchError.collectAsStateWithLifecycle()
    val vodSearchResult by viewModel.vodSearchResult.collectAsStateWithLifecycle()

    val vodPlayerState by viewModel.vodPlayerState.collectAsStateWithLifecycle()
    val categoryState by viewModel.categoryState.collectAsStateWithLifecycle()

    val liveChannels by viewModel.liveChannels.collectAsStateWithLifecycle()
    val selectedLiveGroup by viewModel.selectedLiveGroup.collectAsStateWithLifecycle()
    val activeLiveChannel by viewModel.activeLiveChannel.collectAsStateWithLifecycle()

    val musicState by viewModel.musicState.collectAsStateWithLifecycle()
    val playlistTracks by viewModel.playlistTracks.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val diagnosticLogs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()
    val videoDownloads by viewModel.videoDownloads.collectAsStateWithLifecycle()
    val showDiagnosticsSheet by viewModel.showDiagnosticsSheet.collectAsStateWithLifecycle()
    val showDownloadCenterSheet by viewModel.showDownloadCenterSheet.collectAsStateWithLifecycle()
    val showUpdateSheet by viewModel.showUpdateSheet.collectAsStateWithLifecycle()
    val appUpdateResult by viewModel.appUpdateResult.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val isPublishingToGitHub by viewModel.isPublishingToGitHub.collectAsStateWithLifecycle()
    val healthResults by viewModel.healthResults.collectAsStateWithLifecycle()
    val isRunningDiagnostics by viewModel.isRunningDiagnostics.collectAsStateWithLifecycle()
    val probeInspection by viewModel.probeInspection.collectAsStateWithLifecycle()
    val isProbingCustomSource by viewModel.isProbingCustomSource.collectAsStateWithLifecycle()
    val crossChannelQueryResult by viewModel.crossChannelQueryResult.collectAsStateWithLifecycle()
    val isCrossChannelSearching by viewModel.isCrossChannelSearching.collectAsStateWithLifecycle()

    val navItems = listOf(
        NavTabItem(MainTab.HOME, "影视", Icons.Filled.Movie, Icons.Outlined.Movie, "nav_tab_vod"),
        NavTabItem(MainTab.CATEGORY, "分类", Icons.Filled.GridView, Icons.Outlined.GridView, "nav_tab_category"),
        NavTabItem(MainTab.LIVE_TV, "直播", Icons.Filled.LiveTv, Icons.Outlined.LiveTv, "nav_tab_live"),
        NavTabItem(MainTab.MUSIC, "音乐", Icons.Filled.MusicNote, Icons.Outlined.MusicNote, "nav_tab_music")
    )

    val isCurrentMusicLiked = musicState.currentTrack?.let { cur ->
        playlistTracks.any { it.uniqueKey == cur.uniqueKey }
    } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MhBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (vodPlayerState.item != null) {
            val activeNormKey = com.example.data.model.MovieHubData.normalizeName(vodPlayerState.item!!.name)
                .ifEmpty { vodPlayerState.item!!.name }
            val isVodFavorite = favoriteVods.any { it.normKey == activeNormKey }

            VodDetailPlayerScreen(
                playerState = vodPlayerState,
                onBack = { viewModel.closeVodPlayer() },
                onSelectSource = { viewModel.selectVodSource(it) },
                onSelectLine = { viewModel.selectVodLine(it) },
                onSelectEpisode = { viewModel.selectVodEpisode(it) },
                onNextEpisode = { viewModel.playNextVodEpisode() },
                onToggleEpisodesOrder = { viewModel.toggleEpisodesOrder() },
                onToggleEngine = { viewModel.toggleVodEngineWebView() },
                isFavorite = isVodFavorite,
                onToggleFavorite = { viewModel.toggleFavoriteCurrentVod() },
                onAggregateSources = { viewModel.aggregateAllSourcesForCurrentVod(silent = false) },
                onSpeedTestSources = { viewModel.autoSwitchToFastestSource() },
                videoDownloads = videoDownloads,
                onDownloadEpisodes = { viewModel.downloadCurrentVodEpisodes(it) },
                onOpenDownloadCenter = { viewModel.toggleDownloadCenterSheet(true) },
                onOpenDiagnosticsCenter = { viewModel.toggleDiagnosticsSheet(true) },
                onShowToast = { viewModel.showToast(it) },
                onProgressUpdate = { pos, dur -> viewModel.updateVodWatchProgress(pos, dur) }
            )
        } else {
            Scaffold(
                containerColor = MhBg,
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MhBg)
                    ) {
                        if (currentTab == MainTab.MUSIC && musicState.currentTrack != null) {
                            PersistentMusicBottomBar(
                                musicState = musicState,
                                isLiked = isCurrentMusicLiked,
                                onToggleLiked = {
                                    musicState.currentTrack?.let { viewModel.toggleTrackLiked(it) }
                                },
                                onTogglePlay = { viewModel.toggleMusicPlayPause() },
                                onPrev = { viewModel.playPrevMusicTrack() },
                                onNext = { viewModel.playNextMusicTrack() },
                                onCycleMode = { viewModel.cycleMusicPlayMode() },
                                onOpenLyricsSheet = { viewModel.toggleFullLyricsSheet(true) },
                                onClose = { viewModel.stopAndCloseMusicPlayer() }
                            )
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = MhBorder)

                        // Minimalist Low-Profile Bottom Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(MhBg),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            navItems.forEach { item ->
                                val selected = currentTab == item.tab
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clickable { viewModel.selectTab(item.tab) }
                                        .testTag(item.testTag),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                        tint = if (selected) MhText else MhTextDim,
                                        modifier = Modifier.size(21.dp)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = item.label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) MhText else MhTextDim
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        MainTab.HOME -> {
                            HomeVodScreen(
                                searchQuery = searchQuery,
                                selectedSearchSrcId = selectedSearchSrcId,
                                vodSources = vodSources,
                                remoteSyncState = remoteSyncState,
                                doubanType = doubanType,
                                doubanTag = doubanTag,
                                doubanItems = doubanItems,
                                homeChannelBlocks = homeChannelBlocks,
                                isLoadingDouban = isLoadingDouban,
                                doubanError = doubanError,
                                watchHistory = watchHistory,
                                favoriteVods = favoriteVods,
                                recentSearches = recentVodSearches,
                                latestItems = latestVodItems,
                                latestSourceName = latestSourceName,
                                isLoadingLatest = isLoadingLatest,
                                latestError = latestError,
                                isSearchOverlayOpen = isSearchOverlayOpen,
                                isSearchingVod = isSearchingVod,
                                vodSearchError = vodSearchError,
                                vodSearchResult = vodSearchResult,
                                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                onSelectSearchSrc = { viewModel.updateSelectedSearchSrc(it) },
                                onExecuteSearch = { viewModel.executeVodSearch(it) },
                                onCloseSearchOverlay = { viewModel.closeSearchOverlay() },
                                onToggleSourceEnabled = { viewModel.toggleVodSourceEnabled(it) },
                                onSyncRemoteSources = { viewModel.syncRemoteSourcesNow(it, silentToast = false) },
                                onSelectDoubanType = { viewModel.selectDoubanType(it) },
                                onSelectDoubanTag = { viewModel.selectDoubanTag(it) },
                                onRetryDouban = { viewModel.loadDoubanHot() },
                                onOpenDoubanItem = { viewModel.openDoubanItem(it) },
                                onOpenVodItem = { viewModel.openVodItem(it) },
                                onResumeHistory = { viewModel.resumeFromWatchHistory(it) },
                                onOpenFavoriteVod = { viewModel.openFavoriteVod(it) },
                                onClearWatchHistory = { viewModel.clearAllWatchHistory() },
                                onClearRecentSearches = { viewModel.clearRecentVodSearches() },
                                onReloadLatest = { viewModel.loadLatestVod(it) },
                                onJumpToCategoryChannel = { viewModel.jumpToCategoryChannel(it) },
                                downloadCount = videoDownloads.size,
                                hasNewUpdate = appUpdateResult.hasNewAppVersion,
                                onOpenUpdateCenter = { viewModel.toggleUpdateSheet(true) },
                                onOpenDiagnosticsCenter = { viewModel.toggleDiagnosticsSheet(true) },
                                onOpenDownloadCenter = { viewModel.toggleDownloadCenterSheet(true) }
                            )
                        }
                        MainTab.CATEGORY -> {
                            CategoryBrowseScreen(
                                state = categoryState,
                                vodSources = vodSources,
                                onSelectSource = { srcId, force -> viewModel.loadCategoryTree(srcId, force) },
                                onSelectType = { id, name -> viewModel.selectCategoryType(id, name) },
                                onSelectYear = { viewModel.selectCategoryYear(it) },
                                onSelectSort = { viewModel.selectCategorySort(it) },
                                onChangePage = { viewModel.changeCategoryPage(it) },
                                onOpenVodItem = { viewModel.openVodItem(it) }
                            )
                        }
                        MainTab.LIVE_TV -> {
                            LiveTvScreen(
                                channels = liveChannels,
                                selectedGroup = selectedLiveGroup,
                                activeChannel = activeLiveChannel,
                                onSelectGroup = { viewModel.selectLiveGroup(it) },
                                onPlayChannel = { viewModel.playLiveChannel(it) },
                                onClosePlayer = { viewModel.closeLivePlayer() },
                                onAddChannel = { name, grp, url -> viewModel.addCustomLiveChannel(name, grp, url) }
                            )
                        }
                        MainTab.MUSIC -> {
                            MusicCenterScreen(
                                musicState = musicState,
                                playlistTracks = playlistTracks,
                                onSelectSubTab = { viewModel.selectMusicSubTab(it) },
                                onSelectPlatform = { viewModel.selectMusicPlatform(it) },
                                onSearchKeywordChange = { viewModel.updateMusicSearchKeyword(it) },
                                onExecuteSearch = { viewModel.searchMusic() },
                                onQuickSearch = { kw -> viewModel.searchMusic(kw) },
                                onPlayTrack = { viewModel.playMusicTrack(it) },
                                onToggleTrackLiked = { viewModel.toggleTrackLiked(it) },
                                onAddTrackToPlaylist = { viewModel.addTrackToPlaylistOnly(it) },
                                onRemoveFromPlaylist = { viewModel.removeTrackFromPlaylist(it) },
                                onClearPlaylist = { viewModel.clearMusicPlaylist() },
                                onTogglePlayPause = { viewModel.toggleMusicPlayPause() },
                                onCyclePlayMode = { viewModel.cycleMusicPlayMode() },
                                onSelectQuality = { viewModel.setMusicPreferredQuality(it) },
                                onOpenLyricsSheet = { viewModel.toggleFullLyricsSheet(true) },
                                onToggleLxScript = { viewModel.toggleLxScript(it) },
                                onAddLxScript = { name, url -> viewModel.addCustomLxScript(name, url) },
                                onSaveSpotifyClientId = { viewModel.updateSpotifyClientId(it) }
                            )
                        }
                    }
                }
            }
        }

        if (musicState.showFullLyricsSheet && musicState.currentTrack != null) {
            FullLyricsModalSheet(
                musicState = musicState,
                isLiked = isCurrentMusicLiked,
                onToggleLiked = {
                    musicState.currentTrack?.let { viewModel.toggleTrackLiked(it) }
                },
                onDismiss = { viewModel.toggleFullLyricsSheet(false) },
                onTogglePlay = { viewModel.toggleMusicPlayPause() },
                onPrev = { viewModel.playPrevMusicTrack() },
                onNext = { viewModel.playNextMusicTrack() },
                onSeek = { viewModel.seekMusicTo(it) },
                onCycleMode = { viewModel.cycleMusicPlayMode() },
                onSelectQuality = { viewModel.setMusicPreferredQuality(it) }
            )
        }

        if (showDiagnosticsSheet) {
            SourceDiagnosticsModalSheet(
                vodSources = vodSources,
                remoteSyncState = remoteSyncState,
                healthResults = healthResults,
                isRunningDiagnostics = isRunningDiagnostics,
                diagnosticLogs = diagnosticLogs,
                probeInspection = probeInspection,
                isProbingCustomSource = isProbingCustomSource,
                crossChannelQueryResult = crossChannelQueryResult,
                isCrossChannelSearching = isCrossChannelSearching,
                onDismiss = { viewModel.toggleDiagnosticsSheet(false) },
                onRunFullDiagnostics = { viewModel.runFullChannelDiagnostics() },
                onSyncRemoteSources = { viewModel.syncRemoteSourcesNow(it, silentToast = false) },
                onToggleVodSource = { viewModel.toggleVodSourceEnabled(it) },
                onClearDiagnosticLogs = { viewModel.clearDiagnosticLogs() },
                onProbeCustomApi = { api, kw -> viewModel.probeCustomSourceApi(api, kw) },
                onAddProbedVodSource = { id, name, api -> viewModel.addProbedVodSource(id, name, api) },
                onRunCrossChannelSearch = { kw -> viewModel.runCrossChannelSearch(kw) },
                onShowToast = { viewModel.showToast(it) }
            )
        }

        if (showDownloadCenterSheet) {
            VideoDownloadCenterModalSheet(
                downloads = videoDownloads,
                onDismiss = { viewModel.toggleDownloadCenterSheet(false) },
                onPlayOfflineTask = { viewModel.playOfflineDownload(it) },
                onPauseTask = { viewModel.pauseVideoDownload(it) },
                onResumeTask = { viewModel.resumeVideoDownload(it) },
                onDeleteTask = { viewModel.deleteVideoDownload(it) },
                onClearAllTasks = { viewModel.clearAllVideoDownloads() }
            )
        }

        if (showUpdateSheet) {
            AppUpdateCenterModalSheet(
                updateResult = appUpdateResult,
                isCheckingUpdate = isCheckingUpdate,
                isPublishingToGitHub = isPublishingToGitHub,
                remoteSyncState = remoteSyncState,
                onCheckUpdateNow = { customUrl -> viewModel.checkAppUpdateNow(customUrl, silent = false) },
                onSyncRemoteSourcesNow = { viewModel.syncRemoteSourcesNow(null, silentToast = false) },
                onPublishSourcesToGitHub = { token, repo ->
                    viewModel.publishCurrentSourcesToGitHub(token, repo)
                },
                onPublishVersionToGitHub = { token, repo, verName, verCode, apkUrl, log ->
                    viewModel.publishVersionJsonToGitHub(token, repo, verName, verCode, apkUrl, log)
                },
                onShowToast = { viewModel.showToast(it) },
                onDismiss = { viewModel.toggleUpdateSheet(false) }
            )
        }

        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 132.dp)
        ) {
            Surface(
                color = MhCardHover,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.border(0.5.dp, MhBorder, RoundedCornerShape(999.dp))
            ) {
                Text(
                    text = toastMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MhText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
