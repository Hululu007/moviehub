package com.example.ui

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.FavoriteVodEntity
import com.example.data.local.LocalDataRepository
import com.example.data.local.SourceDiagnosticLogEntity
import com.example.data.local.VideoDownloadEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.CategoryTree
import com.example.data.model.DoubanSubject
import com.example.data.model.LiveTvChannel
import com.example.data.model.LrcLine
import com.example.data.model.LxScriptSource
import com.example.data.model.MovieHubData
import com.example.data.model.MusicPlatform
import com.example.data.model.MusicTrack
import com.example.data.model.PlayMode
import com.example.data.model.VodEpisode
import com.example.data.model.VodItem
import com.example.data.model.VodPlayLine
import com.example.data.model.VodSource
import com.example.data.model.VodSourceMatch
import com.example.data.network.LocalMediaProxyServer
import com.example.data.network.MovieHubNetwork
import com.example.data.network.VideoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MainTab {
    HOME, CATEGORY, LIVE_TV, MUSIC
}

enum class MusicSubTab {
    SEARCH, PLAYLIST, SOURCES_SPOTIFY
}

data class VodPlayerState(
    val item: VodItem? = null,
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
    val selectedSourceIndex: Int = 0,
    val selectedLineIndex: Int = 0,
    val selectedEpisodeIndex: Int = 0,
    val episodesAscending: Boolean = true,
    val isPlaying: Boolean = false,
    val useWebViewHls: Boolean = false,
    val resumePositionSec: Long = 0L,
    val isMatchingMoreSources: Boolean = false,
    val isSpeedTestingSources: Boolean = false
) {
    val activeSourceMatch: VodSourceMatch?
        get() = item?.sources?.getOrNull(selectedSourceIndex)

    val activeLines
        get() = activeSourceMatch?.lines ?: item?.lines ?: emptyList()

    val activeLine
        get() = activeLines.getOrNull(selectedLineIndex)

    val activeEpisode
        get() = activeLine?.episodes?.getOrNull(selectedEpisodeIndex)
}

data class CategoryUiState(
    val selectedSourceId: String = "bfzy",
    val isLoadingTree: Boolean = false,
    val treeError: String? = null,
    val tree: CategoryTree? = null,
    val selectedTypeId: String = "",
    val selectedTypeName: String = "全部",
    val selectedYear: String = "",
    val selectedSortBy: String = "",
    val page: Int = 1,
    val pageCount: Int = 1,
    val total: Int = 0,
    val isLoadingItems: Boolean = false,
    val itemsError: String? = null,
    val items: List<VodItem> = emptyList()
)

data class MusicUiState(
    val subTab: MusicSubTab = MusicSubTab.SEARCH,
    val platforms: List<MusicPlatform> = MovieHubData.MUSIC_PLATFORMS,
    val selectedPlatform: String = "wy",
    val searchKeyword: String = "",
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val searchResults: List<MusicTrack> = emptyList(),
    val currentTrack: MusicTrack? = null,
    val isResolvingUrl: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTimeSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val resolvedVia: String = "",
    val preferredQuality: String = "320k",
    val playMode: PlayMode = PlayMode.LIST_LOOP,
    val lyricLines: List<LrcLine> = emptyList(),
    val isLoadingLyric: Boolean = false,
    val showFullLyricsSheet: Boolean = false,
    val lxScripts: List<LxScriptSource> = MovieHubData.DEFAULT_LX_SCRIPTS,
    val spotifyClientId: String = ""
)

data class RemoteSyncUiState(
    val remoteUrl: String = MovieHubNetwork.DEFAULT_REMOTE_SOURCE_URL,
    val isSyncing: Boolean = false,
    val isSpeedTesting: Boolean = false,
    val syncVersion: String = "内置默认",
    val syncStatusText: String = "正在同步远程源站…",
    val syncedFromHost: String = "github.com/berse1kguts-pixel/moviehub"
)

class MovieHubViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalDataRepository(
        AppDatabase.getInstance(application).movieHubDao()
    )

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = repository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteVods: StateFlow<List<FavoriteVodEntity>> = repository.favoriteVods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentVodSearches: StateFlow<List<String>> = repository.recentVodSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistTracks: StateFlow<List<MusicTrack>> = repository.playlistTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val diagnosticLogs: StateFlow<List<SourceDiagnosticLogEntity>> = repository.diagnosticLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoDownloads: StateFlow<List<VideoDownloadEntity>> = repository.videoDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showDiagnosticsSheet = MutableStateFlow(false)
    val showDiagnosticsSheet: StateFlow<Boolean> = _showDiagnosticsSheet.asStateFlow()

    private val _showDownloadCenterSheet = MutableStateFlow(false)
    val showDownloadCenterSheet: StateFlow<Boolean> = _showDownloadCenterSheet.asStateFlow()

    private val _showUpdateSheet = MutableStateFlow(false)
    val showUpdateSheet: StateFlow<Boolean> = _showUpdateSheet.asStateFlow()

    private val _appUpdateResult = MutableStateFlow(MovieHubNetwork.AppUpdateCheckResult())
    val appUpdateResult: StateFlow<MovieHubNetwork.AppUpdateCheckResult> = _appUpdateResult.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _isPublishingToGitHub = MutableStateFlow(false)
    val isPublishingToGitHub: StateFlow<Boolean> = _isPublishingToGitHub.asStateFlow()

    private val _healthResults = MutableStateFlow<List<MovieHubNetwork.SourceHealthCheckResult>>(emptyList())
    val healthResults: StateFlow<List<MovieHubNetwork.SourceHealthCheckResult>> = _healthResults.asStateFlow()

    private val _isRunningDiagnostics = MutableStateFlow(false)
    val isRunningDiagnostics: StateFlow<Boolean> = _isRunningDiagnostics.asStateFlow()

    private val _probeInspection = MutableStateFlow<MovieHubNetwork.SourceProbeInspection?>(null)
    val probeInspection: StateFlow<MovieHubNetwork.SourceProbeInspection?> = _probeInspection.asStateFlow()

    private val _isProbingCustomSource = MutableStateFlow(false)
    val isProbingCustomSource: StateFlow<Boolean> = _isProbingCustomSource.asStateFlow()

    private val _crossChannelQueryResult = MutableStateFlow<MovieHubNetwork.MultiSearchResult?>(null)
    val crossChannelQueryResult: StateFlow<MovieHubNetwork.MultiSearchResult?> = _crossChannelQueryResult.asStateFlow()

    private val _isCrossChannelSearching = MutableStateFlow(false)
    val isCrossChannelSearching: StateFlow<Boolean> = _isCrossChannelSearching.asStateFlow()

    private val _currentTab = MutableStateFlow(MainTab.HOME)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _vodSources = MutableStateFlow(MovieHubData.DEFAULT_VOD_SOURCES)
    val vodSources: StateFlow<List<VodSource>> = _vodSources.asStateFlow()

    private val _remoteSyncState = MutableStateFlow(RemoteSyncUiState())
    val remoteSyncState: StateFlow<RemoteSyncUiState> = _remoteSyncState.asStateFlow()

    private val _selectedSearchSrcId = MutableStateFlow("") // "" = 全部源
    val selectedSearchSrcId: StateFlow<String> = _selectedSearchSrcId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Home: Douban & 4 Major Channel Blocks (电影 / 电视剧 / 动漫 / 纪录片)
    private val _doubanType = MutableStateFlow("movie")
    val doubanType: StateFlow<String> = _doubanType.asStateFlow()

    private val _doubanTag = MutableStateFlow("热门")
    val doubanTag: StateFlow<String> = _doubanTag.asStateFlow()

    private val _doubanItems = MutableStateFlow<List<DoubanSubject>>(emptyList())
    val doubanItems: StateFlow<List<DoubanSubject>> = _doubanItems.asStateFlow()

    private val _homeChannelBlocks = MutableStateFlow<Map<String, List<DoubanSubject>>>(emptyMap())
    val homeChannelBlocks: StateFlow<Map<String, List<DoubanSubject>>> = _homeChannelBlocks.asStateFlow()

    private val _isLoadingDouban = MutableStateFlow(false)
    val isLoadingDouban: StateFlow<Boolean> = _isLoadingDouban.asStateFlow()

    private val _doubanError = MutableStateFlow<String?>(null)
    val doubanError: StateFlow<String?> = _doubanError.asStateFlow()

    // Home: Latest VOD state
    private val _latestVodItems = MutableStateFlow<List<VodItem>>(emptyList())
    val latestVodItems: StateFlow<List<VodItem>> = _latestVodItems.asStateFlow()

    private val _latestSourceName = MutableStateFlow("")
    val latestSourceName: StateFlow<String> = _latestSourceName.asStateFlow()

    private val _isLoadingLatest = MutableStateFlow(false)
    val isLoadingLatest: StateFlow<Boolean> = _isLoadingLatest.asStateFlow()

    private val _latestError = MutableStateFlow<String?>(null)
    val latestError: StateFlow<String?> = _latestError.asStateFlow()

    // Multi-source Search Overlay state
    private val _isSearchOverlayOpen = MutableStateFlow(false)
    val isSearchOverlayOpen: StateFlow<Boolean> = _isSearchOverlayOpen.asStateFlow()

    private val _isSearchingVod = MutableStateFlow(false)
    val isSearchingVod: StateFlow<Boolean> = _isSearchingVod.asStateFlow()

    private val _vodSearchError = MutableStateFlow<String?>(null)
    val vodSearchError: StateFlow<String?> = _vodSearchError.asStateFlow()

    private val _vodSearchResult = MutableStateFlow<MovieHubNetwork.MultiSearchResult?>(null)
    val vodSearchResult: StateFlow<MovieHubNetwork.MultiSearchResult?> = _vodSearchResult.asStateFlow()

    // Active VOD Detail & Player state
    private val _vodPlayerState = MutableStateFlow(VodPlayerState())
    val vodPlayerState: StateFlow<VodPlayerState> = _vodPlayerState.asStateFlow()

    // Category Browse state
    private val _categoryState = MutableStateFlow(CategoryUiState())
    val categoryState: StateFlow<CategoryUiState> = _categoryState.asStateFlow()

    // Live TV state
    private val _liveChannels = MutableStateFlow(MovieHubData.LIVE_TV_CHANNELS)
    val liveChannels: StateFlow<List<LiveTvChannel>> = _liveChannels.asStateFlow()

    private val _selectedLiveGroup = MutableStateFlow("全部")
    val selectedLiveGroup: StateFlow<String> = _selectedLiveGroup.asStateFlow()

    private val _activeLiveChannel = MutableStateFlow<LiveTvChannel?>(null)
    val activeLiveChannel: StateFlow<LiveTvChannel?> = _activeLiveChannel.asStateFlow()

    // Music state
    private val _musicState = MutableStateFlow(MusicUiState())
    val musicState: StateFlow<MusicUiState> = _musicState.asStateFlow()

    // Toast message
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var musicProgressJob: Job? = null
    private var vodSearchJob: Job? = null
    private var doubanJob: Job? = null

    init {
        LocalMediaProxyServer.ensureStarted()
        loadDoubanHot()
        preloadAllFourChannelBlocks()
        bootstrapAndSyncRemoteSources()
        checkAppUpdateNow(silent = true)
    }

    private fun preloadAllFourChannelBlocks() {
        viewModelScope.launch {
            MovieHubData.VOD_MAJOR_CHANNELS.forEach { ch ->
                launch(Dispatchers.IO) {
                    try {
                        val list = MovieHubNetwork.fetchDoubanHot(
                            type = ch.key,
                            tag = ch.tags.first(),
                            limit = 18,
                            fallbackSources = _vodSources.value
                        )
                        if (list.isNotEmpty()) {
                            _homeChannelBlocks.update { prev -> prev + (ch.key to list) }
                            if (_doubanType.value == ch.key && _doubanItems.value.isEmpty()) {
                                _doubanItems.value = list
                                _isLoadingDouban.value = false
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun bootstrapAndSyncRemoteSources() {
        viewModelScope.launch {
            // 1. Load cached remote config and custom URL from Room first for instant cold start
            val savedUrl = repository.getRemoteSourceUrl()?.takeIf { it.isNotBlank() }
                ?: MovieHubNetwork.DEFAULT_REMOTE_SOURCE_URL
            _remoteSyncState.update { it.copy(remoteUrl = savedUrl, isSyncing = true, syncStatusText = "正在同步远程源站…") }

            val cachedJson = repository.getRemoteSourceBundleJson()
            if (!cachedJson.isNullOrBlank()) {
                MovieHubNetwork.parseRemoteSourceBundleJson(cachedJson, "本地缓存")?.let { bundle ->
                    applyRemoteBundle(bundle, isCached = true)
                }
            }

            // Start initial latest VOD load while online sync proceeds
            loadLatestVod()

            // 2. Sync online from hardcoded remote source website (berse1kguts-pixel/moviehub)
            syncRemoteSourcesNow(customUrl = savedUrl, silentToast = true)
        }
    }

    private fun applyRemoteBundle(bundle: MovieHubNetwork.RemoteSourceBundle, isCached: Boolean) {
        if (bundle.vodSources.isNotEmpty()) {
            // Preserve user's enabled/disabled toggle if IDs match
            val currentMap = _vodSources.value.associateBy { it.id }
            val merged = bundle.vodSources.map { remoteSrc ->
                val prev = currentMap[remoteSrc.id]
                if (prev != null) remoteSrc.copy(enabled = prev.enabled) else remoteSrc
            }
            _vodSources.value = merged
        }
        if (bundle.liveChannels.isNotEmpty()) {
            val existingUrls = bundle.liveChannels.map { it.url }.toHashSet()
            val extraBuiltIn = MovieHubData.LIVE_TV_CHANNELS.filter { it.url !in existingUrls }
            _liveChannels.value = bundle.liveChannels + extraBuiltIn
        }
        if (bundle.lxScripts.isNotEmpty()) {
            _musicState.update { st ->
                val existingIds = bundle.lxScripts.map { it.id }.toHashSet()
                val keptCustom = st.lxScripts.filter { it.id !in existingIds }
                st.copy(lxScripts = bundle.lxScripts + keptCustom)
            }
        }
        val tag = if (isCached) "已载入缓存源 (${bundle.version})" else "云端已同步 (${bundle.version})"
        _remoteSyncState.update {
            it.copy(
                syncVersion = bundle.version,
                syncStatusText = tag
            )
        }
    }

    fun syncRemoteSourcesNow(customUrl: String? = null, silentToast: Boolean = false) {
        viewModelScope.launch {
            val targetUrl = customUrl?.trim()?.ifEmpty { MovieHubNetwork.DEFAULT_REMOTE_SOURCE_URL }
                ?: _remoteSyncState.value.remoteUrl.ifEmpty { MovieHubNetwork.DEFAULT_REMOTE_SOURCE_URL }

            if (customUrl != null) {
                repository.saveRemoteSourceUrl(targetUrl)
            }

            _remoteSyncState.update {
                it.copy(
                    remoteUrl = targetUrl,
                    isSyncing = true,
                    syncStatusText = "正在从远程源站同步…"
                )
            }

            try {
                val bundle = MovieHubNetwork.syncRemoteSources(targetUrl)
                repository.saveRemoteSourceBundleJson(bundle.rawJson)
                applyRemoteBundle(bundle, isCached = false)

                // Immediately speed-test all synced VOD sources concurrently and sort fastest -> slowest
                _remoteSyncState.update {
                    it.copy(
                        isSyncing = false,
                        isSpeedTesting = true,
                        syncStatusText = "云端同步完成 · 正在并发测速节点…"
                    )
                }
                val sortedFastest = MovieHubNetwork.speedTestVodSources(_vodSources.value)
                _vodSources.value = sortedFastest
                val fastestSrc = sortedFastest.firstOrNull { it.enabled }
                val fastestMs = fastestSrc?.let { MovieHubNetwork.sourceLatencyMap[it.id] } ?: 0L
                val statusMsg = if (fastestSrc != null && fastestMs in 1..8000) {
                    "云端已同步 · 最快节点: ${fastestSrc.name} (${fastestMs}ms)"
                } else {
                    "云端已同步 · ${ sortedFastest.count { it.enabled } } 源就绪"
                }
                _remoteSyncState.update {
                    it.copy(
                        isSyncing = false,
                        isSpeedTesting = false,
                        syncStatusText = statusMsg
                    )
                }
                // Refresh latest VOD from the fastest source
                loadLatestVod(preferredSrcId = fastestSrc?.id.orEmpty())
                repository.insertDiagnosticLog(
                    SourceDiagnosticLogEntity(
                        channelType = "SYNC",
                        sourceId = "remote_sync",
                        sourceName = "远程源站同步 (${bundle.version})",
                        apiUrl = targetUrl,
                        status = "HEALTHY",
                        httpCode = 200,
                        apiLatencyMs = fastestMs,
                        streamLatencyMs = fastestMs,
                        vodTotalCount = sortedFastest.size,
                        detailMessage = "$statusMsg · 共 ${sortedFastest.size} 个影视源 / ${_liveChannels.value.size} 个直播源"
                    )
                )
                if (!silentToast) {
                    showToast(statusMsg)
                }
            } catch (e: Exception) {
                // Even if remote GitHub is unreachable, still speed-test current sources so fastest is prioritized
                _remoteSyncState.update {
                    it.copy(
                        isSyncing = false,
                        isSpeedTesting = true,
                        syncStatusText = "使用本地源 · 正在测速节点…"
                    )
                }
                val sortedFastest = MovieHubNetwork.speedTestVodSources(_vodSources.value)
                _vodSources.value = sortedFastest
                val fastestSrc = sortedFastest.firstOrNull { it.enabled }
                val fastestMs = fastestSrc?.let { MovieHubNetwork.sourceLatencyMap[it.id] } ?: 0L
                _remoteSyncState.update {
                    it.copy(
                        isSyncing = false,
                        isSpeedTesting = false,
                        syncStatusText = if (fastestSrc != null && fastestMs in 1..8000) {
                            "已就绪 · 最快: ${fastestSrc.name} (${fastestMs}ms)"
                        } else {
                            "本地源已就绪 (${sortedFastest.count { s -> s.enabled }}源)"
                        }
                    )
                }
                if (!silentToast) {
                    showToast("已测速并优选最快片源：${fastestSrc?.name ?: "默认"}")
                }
            }
        }
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
        viewModelScope.launch {
            delay(2800)
            if (_toastMessage.value == msg) {
                _toastMessage.value = null
            }
        }
    }

    fun selectTab(tab: MainTab) {
        if (tab != MainTab.LIVE_TV && _activeLiveChannel.value != null) {
            _activeLiveChannel.value = null
        }
        _currentTab.value = tab
        if (tab == MainTab.CATEGORY && _categoryState.value.tree == null && !_categoryState.value.isLoadingTree) {
            loadCategoryTree(_categoryState.value.selectedSourceId)
        }
        if (tab == MainTab.MUSIC && _musicState.value.searchResults.isEmpty() && !_musicState.value.isSearching) {
            _musicState.update { it.copy(searchKeyword = "周杰伦") }
            searchMusic("周杰伦")
        }
    }

    fun jumpToCategoryChannel(channelKey: String) {
        _currentTab.value = MainTab.CATEGORY
        val tree = _categoryState.value.tree
        if (tree == null && !_categoryState.value.isLoadingTree) {
            loadCategoryTree(_categoryState.value.selectedSourceId)
            return
        }
        if (tree != null) {
            val allNodes = buildList {
                tree.groups.forEach { addAll(it.items) }
                addAll(tree.top)
            }
            val matched = allNodes.firstOrNull { MovieHubData.classifyVodChannel(it.name) == channelKey }
            if (matched != null) {
                selectCategoryType(matched.id.toString(), matched.name)
            }
        }
    }

    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun updateSelectedSearchSrc(srcId: String) {
        _selectedSearchSrcId.value = srcId
    }

    fun toggleVodSourceEnabled(srcId: String) {
        _vodSources.update { list ->
            list.map { if (it.id == srcId) it.copy(enabled = !it.enabled) else it }
        }
    }

    /* ─────────────────── 1. 首页：电影 · 电视剧 · 动漫 · 纪录片 四大专区与最新入库 ─────────────────── */

    fun selectDoubanType(type: String) {
        if (_doubanType.value == type) return
        _doubanType.value = type
        val defaultTag = when (type) {
            "tv" -> MovieHubData.DOUBAN_TV_TAGS.first()
            "anime" -> MovieHubData.DOUBAN_ANIME_TAGS.first()
            "doc" -> MovieHubData.DOUBAN_DOC_TAGS.first()
            "short" -> MovieHubData.DOUBAN_SHORT_TAGS.first()
            else -> MovieHubData.DOUBAN_MOVIE_TAGS.first()
        }
        _doubanTag.value = defaultTag
        val preloaded = _homeChannelBlocks.value[type].orEmpty()
        if (preloaded.isNotEmpty()) {
            _doubanItems.value = preloaded
            _doubanError.value = null
            _isLoadingDouban.value = false
        } else {
            _doubanItems.value = emptyList()
        }
        loadDoubanHot(forceClearBeforeLoad = false)
    }

    fun selectDoubanTag(tag: String) {
        if (_doubanTag.value == tag) return
        _doubanTag.value = tag
        loadDoubanHot(forceClearBeforeLoad = true)
    }

    fun loadDoubanHot(forceClearBeforeLoad: Boolean = false) {
        val type = _doubanType.value
        val tag = _doubanTag.value
        doubanJob?.cancel()
        doubanJob = viewModelScope.launch {
            if (forceClearBeforeLoad || _doubanItems.value.isEmpty()) {
                _isLoadingDouban.value = true
            }
            _doubanError.value = null
            try {
                val items = MovieHubNetwork.fetchDoubanHot(
                    type = type,
                    tag = tag,
                    limit = 24,
                    fallbackSources = _vodSources.value
                )
                if (items.isEmpty()) {
                    if (_doubanItems.value.isEmpty()) {
                        _doubanError.value = "暂未获取到榜单内容，可切换标签或点击刷新重试"
                    }
                } else {
                    _doubanItems.value = items
                    val defaultTag = when (type) {
                        "tv" -> MovieHubData.DOUBAN_TV_TAGS.first()
                        "anime" -> MovieHubData.DOUBAN_ANIME_TAGS.first()
                        "doc" -> MovieHubData.DOUBAN_DOC_TAGS.first()
                        "short" -> MovieHubData.DOUBAN_SHORT_TAGS.first()
                        else -> MovieHubData.DOUBAN_MOVIE_TAGS.first()
                    }
                    if (tag == defaultTag || _homeChannelBlocks.value[type].isNullOrEmpty()) {
                        _homeChannelBlocks.update { prev -> prev + (type to items) }
                    }
                }
            } catch (e: Exception) {
                if (_doubanItems.value.isEmpty()) {
                    _doubanError.value = e.message ?: "片单连接失败"
                }
            } finally {
                _isLoadingDouban.value = false
            }
        }
    }

    fun loadLatestVod(preferredSrcId: String = "") {
        viewModelScope.launch {
            _isLoadingLatest.value = true
            _latestError.value = null
            try {
                val res = MovieHubNetwork.fetchLatestVod(
                    sources = _vodSources.value,
                    preferredSrcId = preferredSrcId,
                    page = 1
                )
                _latestVodItems.value = res.items
                _latestSourceName.value = res.srcName
            } catch (e: Exception) {
                _latestError.value = e.message ?: "片库加载失败"
            } finally {
                _isLoadingLatest.value = false
            }
        }
    }

    /* ─────────────────── 2. 影视多源搜索与豆瓣直达 ─────────────────── */

    fun executeVodSearch(keyword: String = _searchQuery.value) {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            showToast("请输入搜索关键词")
            return
        }
        _searchQuery.value = kw
        _isSearchOverlayOpen.value = true
        vodSearchJob?.cancel()
        vodSearchJob = viewModelScope.launch {
            repository.addRecentSearch(kw, "vod")
            _isSearchingVod.value = true
            _vodSearchError.value = null
            try {
                val result = MovieHubNetwork.searchVod(
                    keyword = kw,
                    targetSrcId = _selectedSearchSrcId.value,
                    allSources = _vodSources.value
                )
                _vodSearchResult.value = result
                if (result.items.isEmpty()) {
                    _vodSearchError.value = "未在 ${result.sourceCount} 个片源中找到「$kw」"
                }
            } catch (e: Exception) {
                _vodSearchError.value = e.message ?: "搜索失败"
            } finally {
                _isSearchingVod.value = false
            }
        }
    }

    fun closeSearchOverlay() {
        _isSearchOverlayOpen.value = false
    }

    fun clearRecentVodSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches("vod")
        }
    }

    /**
     * Instant-Start Douban Open:
     * Races all enabled sources in parallel (`searchVodFastFirst`), immediately starts playing
     * as soon as the fastest CDN responds (< 1s), and continues aggregating remaining backup sources in the background.
     */
    fun openDoubanItem(subject: DoubanSubject) {
        val title = subject.title.trim()
        if (title.isEmpty()) return
        pauseMusicForVideo()
        _vodPlayerState.value = VodPlayerState(
            item = VodItem(
                name = title,
                pic = subject.cover,
                year = subject.year,
                remarks = if (subject.rate > 0) "豆瓣 ${subject.rate}" else subject.subtitle
            ),
            isLoadingDetail = true
        )
        viewModelScope.launch {
            repository.addRecentSearch(title, "vod")
            try {
                // Fast-path: start playing as soon as the very first source responds!
                val fastMatch = MovieHubNetwork.searchVodFastFirst(
                    keyword = title,
                    allSources = _vodSources.value
                )
                if (fastMatch != null) {
                    _vodPlayerState.value = VodPlayerState(
                        item = fastMatch.copy(
                            pic = fastMatch.pic.ifEmpty { subject.cover }
                        ),
                        isLoadingDetail = false,
                        selectedSourceIndex = 0,
                        selectedLineIndex = 0,
                        selectedEpisodeIndex = 0,
                        isPlaying = true
                    )
                    recordCurrentVodWatchHistory(0L, 0L)
                    // Gather remaining backup sources silently in background
                    aggregateAllSourcesForCurrentVod(silent = true)
                    return@launch
                }

                // Fallback full search if fast race missed
                val searchRes = MovieHubNetwork.searchVod(
                    keyword = title,
                    targetSrcId = "",
                    allSources = _vodSources.value
                )
                if (searchRes.items.isEmpty()) {
                    _vodPlayerState.update {
                        it.copy(
                            isLoadingDetail = false,
                            detailError = "各片源暂未收录《$title》"
                        )
                    }
                    return@launch
                }
                val normTarget = MovieHubData.normalizeName(title)
                val bestMatch = searchRes.items.find { MovieHubData.normalizeName(it.name) == normTarget }
                    ?: searchRes.items.first()
                _vodPlayerState.value = VodPlayerState(
                    item = bestMatch.copy(
                        pic = bestMatch.pic.ifEmpty { subject.cover }
                    ),
                    isLoadingDetail = false,
                    selectedSourceIndex = 0,
                    selectedLineIndex = 0,
                    selectedEpisodeIndex = 0,
                    isPlaying = true
                )
                recordCurrentVodWatchHistory(0L, 0L)
            } catch (e: Exception) {
                _vodPlayerState.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailError = "片源匹配失败：${e.message}"
                    )
                }
            }
        }
    }

    /* ─────────────────── 3. 影视详情、选集与播放器 ─────────────────── */

    fun openVodItem(item: VodItem, resumeEpIndex: Int = 0, resumePosSec: Long = 0L) {
        pauseMusicForVideo()
        val hasLines = item.lines.isNotEmpty() || item.sources.any { it.lines.isNotEmpty() }
        if (hasLines) {
            val normalizedItem = if (item.sources.isNotEmpty()) {
                item
            } else {
                item.copy(
                    sources = listOf(
                        VodSourceMatch(
                            sourceId = item.source,
                            sourceName = item.sourceName.ifEmpty { "默认源" },
                            vodId = item.vodId,
                            lines = item.lines,
                            pic = item.pic,
                            remarks = item.remarks,
                            content = item.content,
                            year = item.year,
                            area = item.area,
                            actor = item.actor,
                            director = item.director,
                            typeName = item.typeName,
                            latencyMs = MovieHubNetwork.sourceLatencyMap[item.source] ?: 0L
                        )
                    )
                )
            }
            val safeEpIdx = resumeEpIndex.coerceIn(
                0,
                (normalizedItem.sources.firstOrNull()?.lines?.firstOrNull()?.episodes?.size ?: 1) - 1
            )
            _vodPlayerState.value = VodPlayerState(
                item = normalizedItem,
                isLoadingDetail = false,
                selectedSourceIndex = 0,
                selectedLineIndex = 0,
                selectedEpisodeIndex = safeEpIdx,
                isPlaying = true,
                resumePositionSec = resumePosSec
            )
            recordCurrentVodWatchHistory(resumePosSec, 0L)
            if (normalizedItem.sources.size <= 1) {
                aggregateAllSourcesForCurrentVod(silent = true)
            }
        } else {
            // Need to fetch ac=detail&ids=vodId
            _vodPlayerState.value = VodPlayerState(
                item = item,
                isLoadingDetail = true
            )
            viewModelScope.launch {
                try {
                    val src = _vodSources.value.find { it.id == item.source }
                        ?: _vodSources.value.first()
                    val fullItem = MovieHubNetwork.fetchVodDetail(src, item.vodId)
                    val safeEpIdx = resumeEpIndex.coerceIn(
                        0,
                        (fullItem.lines.firstOrNull()?.episodes?.size ?: 1) - 1
                    )
                    _vodPlayerState.value = VodPlayerState(
                        item = fullItem,
                        isLoadingDetail = false,
                        selectedSourceIndex = 0,
                        selectedLineIndex = 0,
                        selectedEpisodeIndex = safeEpIdx,
                        isPlaying = true,
                        resumePositionSec = resumePosSec
                    )
                    recordCurrentVodWatchHistory(resumePosSec, 0L)
                    if (fullItem.sources.size <= 1) {
                        aggregateAllSourcesForCurrentVod(silent = true)
                    }
                } catch (e: Exception) {
                    _vodPlayerState.update {
                        it.copy(
                            isLoadingDetail = false,
                            detailError = e.message ?: "获取影片详情失败"
                        )
                    }
                }
            }
        }
    }

    fun aggregateAllSourcesForCurrentVod(silent: Boolean = false) {
        val currentItem = _vodPlayerState.value.item ?: return
        val title = currentItem.name.trim()
        if (title.isEmpty() || _vodPlayerState.value.isMatchingMoreSources) return

        _vodPlayerState.update { it.copy(isMatchingMoreSources = true) }
        viewModelScope.launch {
            try {
                val searchRes = MovieHubNetwork.searchVod(
                    keyword = title,
                    targetSrcId = "",
                    allSources = _vodSources.value
                )
                val normTarget = MovieHubData.normalizeName(title)
                val matchedItem = searchRes.items.find { MovieHubData.normalizeName(it.name) == normTarget }
                    ?: searchRes.items.firstOrNull()

                if (matchedItem != null && matchedItem.sources.isNotEmpty()) {
                    _vodPlayerState.update { st ->
                        val activeItem = st.item ?: return@update st.copy(isMatchingMoreSources = false)
                        if (MovieHubData.normalizeName(activeItem.name) != normTarget) {
                            return@update st.copy(isMatchingMoreSources = false)
                        }
                        val currentSelectedId = st.activeSourceMatch?.sourceId
                        val existingSources = activeItem.sources
                        val mergedSources = buildList {
                            addAll(existingSources)
                            matchedItem.sources.forEach { candidate ->
                                if (none { it.sourceId == candidate.sourceId }) {
                                    add(candidate)
                                }
                            }
                        }.sortedBy { m -> if (m.latencyMs > 0L) m.latencyMs else 9999L }

                        val newSelectedIdx = if (currentSelectedId != null) {
                            mergedSources.indexOfFirst { it.sourceId == currentSelectedId }.coerceAtLeast(0)
                        } else 0

                        st.copy(
                            item = activeItem.copy(
                                sources = mergedSources,
                                content = activeItem.content.ifEmpty { matchedItem.content },
                                actor = activeItem.actor.ifEmpty { matchedItem.actor },
                                director = activeItem.director.ifEmpty { matchedItem.director },
                                year = activeItem.year.ifEmpty { matchedItem.year },
                                area = activeItem.area.ifEmpty { matchedItem.area }
                            ),
                            selectedSourceIndex = newSelectedIdx,
                            isMatchingMoreSources = false
                        )
                    }
                    if (!silent) {
                        showToast("已聚合 ${_vodPlayerState.value.item?.sources?.size ?: 1} 个可用片源（已按速度排序）")
                    }
                } else {
                    _vodPlayerState.update { it.copy(isMatchingMoreSources = false) }
                    if (!silent) showToast("未发现更多同名片源")
                }
            } catch (_: Exception) {
                _vodPlayerState.update { it.copy(isMatchingMoreSources = false) }
                if (!silent) showToast("全网搜源失败")
            }
        }
    }

    /**
     * Concurrently probes the actual M3U8 video stream URLs of all matched sources for the current movie,
     * sorts them by lowest first-byte stream latency, and automatically switches to the fastest source!
     */
    fun autoSwitchToFastestSource() {
        val st = _vodPlayerState.value
        val item = st.item ?: return
        val sources = item.sources
        if (sources.isEmpty() || st.isSpeedTestingSources) return

        _vodPlayerState.update { it.copy(isSpeedTestingSources = true) }
        viewModelScope.launch {
            try {
                val epIdx = st.selectedEpisodeIndex
                val testedSources = coroutineScope {
                    sources.map { srcMatch ->
                        async(Dispatchers.IO) {
                            val epList = srcMatch.lines.firstOrNull()?.episodes.orEmpty()
                            val sampleEp = epList.getOrNull(epIdx) ?: epList.firstOrNull()
                            val streamLatency = if (sampleEp != null) {
                                MovieHubNetwork.probeStreamLatencyMs(sampleEp.url)
                            } else {
                                Long.MAX_VALUE
                            }
                            val effectiveMs = if (streamLatency < Long.MAX_VALUE) streamLatency else 9999L
                            MovieHubNetwork.sourceLatencyMap[srcMatch.sourceId] = effectiveMs
                            srcMatch.copy(latencyMs = effectiveMs)
                        }
                    }.awaitAll()
                }.sortedBy { it.latencyMs }

                val fastest = testedSources.firstOrNull()
                val newEpIdx = epIdx.coerceIn(
                    0,
                    ((fastest?.lines?.firstOrNull()?.episodes?.size ?: 1) - 1).coerceAtLeast(0)
                )
                _vodPlayerState.update { cur ->
                    val curItem = cur.item ?: return@update cur.copy(isSpeedTestingSources = false)
                    cur.copy(
                        item = curItem.copy(sources = testedSources),
                        selectedSourceIndex = 0,
                        selectedLineIndex = 0,
                        selectedEpisodeIndex = newEpIdx,
                        isSpeedTestingSources = false
                    )
                }
                if (fastest != null && fastest.latencyMs < 9000L) {
                    showToast("⚡ 已切换至最快片源：${fastest.sourceName} (${fastest.latencyMs}ms)")
                } else {
                    showToast("测速完成，已优选最佳可用线路")
                }
            } catch (_: Exception) {
                _vodPlayerState.update { it.copy(isSpeedTestingSources = false) }
            }
        }
    }

    fun resumeFromWatchHistory(hist: WatchHistoryEntity) {
        val item = VodItem(
            name = hist.name,
            pic = hist.pic,
            remarks = hist.remarks,
            source = hist.sourceId,
            sourceName = hist.sourceName,
            vodId = hist.vodId
        )
        openVodItem(item, resumeEpIndex = hist.epIndex, resumePosSec = hist.positionSec)
    }

    fun deleteWatchHistoryItem(normKey: String) {
        viewModelScope.launch {
            repository.removeWatchHistory(normKey)
        }
    }

    fun clearAllWatchHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
            showToast("已清空观看历史")
        }
    }

    fun toggleFavoriteCurrentVod() {
        val st = _vodPlayerState.value
        val item = st.item ?: return
        val match = st.activeSourceMatch
        val normKey = MovieHubData.normalizeName(item.name).ifEmpty { item.name }
        val exists = favoriteVods.value.any { it.normKey == normKey }
        viewModelScope.launch {
            if (exists) {
                repository.removeFavoriteVod(normKey)
                showToast("已从我的片单移除")
            } else {
                repository.saveFavoriteVod(
                    FavoriteVodEntity(
                        normKey = normKey,
                        name = item.name,
                        pic = item.pic.ifEmpty { match?.pic.orEmpty() },
                        remarks = item.remarks.ifEmpty { match?.remarks.orEmpty() },
                        year = item.year.ifEmpty { match?.year.orEmpty() },
                        sourceId = match?.sourceId ?: item.source,
                        sourceName = match?.sourceName ?: item.sourceName,
                        vodId = match?.vodId ?: item.vodId
                    )
                )
                showToast("已加入我的片单")
            }
        }
    }

    fun openFavoriteVod(fav: FavoriteVodEntity) {
        val item = VodItem(
            name = fav.name,
            pic = fav.pic,
            remarks = fav.remarks,
            year = fav.year,
            source = fav.sourceId,
            sourceName = fav.sourceName,
            vodId = fav.vodId
        )
        openVodItem(item)
    }

    fun removeFavoriteVod(normKey: String) {
        viewModelScope.launch {
            repository.removeFavoriteVod(normKey)
        }
    }

    fun selectVodSource(index: Int) {
        _vodPlayerState.update { state ->
            if (index !in (state.item?.sources?.indices ?: IntRange.EMPTY)) return@update state
            val newSource = state.item?.sources?.get(index)
            val prevEpIdx = state.selectedEpisodeIndex
            val maxEps = newSource?.lines?.firstOrNull()?.episodes?.size ?: 1
            state.copy(
                selectedSourceIndex = index,
                selectedLineIndex = 0,
                selectedEpisodeIndex = prevEpIdx.coerceIn(0, (maxEps - 1).coerceAtLeast(0)),
                isPlaying = true,
                resumePositionSec = 0L
            )
        }
        recordCurrentVodWatchHistory(0L, 0L)
    }

    fun selectVodLine(lineIndex: Int) {
        _vodPlayerState.update { state ->
            val lines = state.activeLines
            if (lineIndex !in lines.indices) return@update state
            val maxEps = lines[lineIndex].episodes.size
            state.copy(
                selectedLineIndex = lineIndex,
                selectedEpisodeIndex = state.selectedEpisodeIndex.coerceIn(0, (maxEps - 1).coerceAtLeast(0)),
                isPlaying = true,
                resumePositionSec = 0L
            )
        }
        recordCurrentVodWatchHistory(0L, 0L)
    }

    fun selectVodEpisode(epIndex: Int) {
        _vodPlayerState.update { state ->
            val eps = state.activeLine?.episodes ?: return@update state
            if (epIndex !in eps.indices) return@update state
            state.copy(
                selectedEpisodeIndex = epIndex,
                isPlaying = true,
                resumePositionSec = 0L
            )
        }
        recordCurrentVodWatchHistory(0L, 0L)
    }

    fun playNextVodEpisode() {
        val st = _vodPlayerState.value
        val eps = st.activeLine?.episodes ?: return
        if (st.selectedEpisodeIndex + 1 < eps.size) {
            selectVodEpisode(st.selectedEpisodeIndex + 1)
            showToast("自动连播：${eps[st.selectedEpisodeIndex + 1].title}")
        } else {
            showToast("已是最后一集")
        }
    }

    fun toggleEpisodesOrder() {
        _vodPlayerState.update { it.copy(episodesAscending = !it.episodesAscending) }
    }

    fun toggleVodEngineWebView() {
        _vodPlayerState.update {
            val next = !it.useWebViewHls
            showToast(if (next) "已切换至 WebView HLS.js 播放内核" else "已切换至 ExoPlayer 原生播放内核")
            it.copy(useWebViewHls = next)
        }
    }

    fun closeVodPlayer() {
        _vodPlayerState.value = VodPlayerState()
    }

    fun updateVodWatchProgress(positionSec: Long, durationSec: Long) {
        recordCurrentVodWatchHistory(positionSec, durationSec)
    }

    private fun recordCurrentVodWatchHistory(positionSec: Long, durationSec: Long) {
        val st = _vodPlayerState.value
        val item = st.item ?: return
        val match = st.activeSourceMatch
        val ep = st.activeEpisode ?: return
        val normKey = MovieHubData.normalizeName(item.name).ifEmpty { item.name }
        viewModelScope.launch {
            repository.saveWatchHistory(
                WatchHistoryEntity(
                    normKey = normKey,
                    name = item.name,
                    pic = item.pic.ifEmpty { match?.pic.orEmpty() },
                    remarks = item.remarks.ifEmpty { match?.remarks.orEmpty() },
                    sourceId = match?.sourceId ?: item.source,
                    sourceName = match?.sourceName ?: item.sourceName,
                    vodId = match?.vodId ?: item.vodId,
                    lineIndex = st.selectedLineIndex,
                    epIndex = st.selectedEpisodeIndex,
                    epTitle = ep.title,
                    epUrl = ep.url,
                    positionSec = positionSec,
                    durationSec = durationSec,
                    multiSourceCount = item.multiSourceCount
                )
            )
        }
    }

    /* ─────────────────── 4. 分类浏览 (Ported from moviehub/public/cat.js) ─────────────────── */

    fun loadCategoryTree(sourceId: String, forceRefresh: Boolean = false) {
        val src = _vodSources.value.find { it.id == sourceId }
            ?: _vodSources.value.firstOrNull { it.enabled }
            ?: MovieHubData.DEFAULT_VOD_SOURCES.first()

        _categoryState.update {
            it.copy(
                selectedSourceId = src.id,
                isLoadingTree = true,
                treeError = null
            )
        }

        viewModelScope.launch {
            try {
                var tree = if (!forceRefresh) repository.getCachedCategoryTree(src.id) else null
                if (tree == null || tree.version < 2 || (tree.groups.isEmpty() && tree.top.isEmpty())) {
                    tree = MovieHubNetwork.discoverCategoryTree(src)
                    if (tree.types.isNotEmpty()) {
                        repository.saveCategoryTreeCache(src.id, tree)
                    }
                }
                val allCleanNodes = buildList {
                    tree.groups.forEach { addAll(it.items) }
                    addAll(tree.top)
                }.filterNot { MovieHubData.isBlockedCategoryOrTitle(it.name) }
                val firstNode = allCleanNodes.firstOrNull { MovieHubData.classifyVodChannel(it.name) == "movie" }
                    ?: allCleanNodes.firstOrNull()
                _categoryState.update {
                    it.copy(
                        isLoadingTree = false,
                        tree = tree,
                        selectedTypeId = firstNode?.id?.toString() ?: "",
                        selectedTypeName = firstNode?.name ?: "电影",
                        page = 1
                    )
                }
                loadCategoryBrowseItems()
            } catch (e: Exception) {
                _categoryState.update {
                    it.copy(
                        isLoadingTree = false,
                        treeError = e.message ?: "分类树发现失败"
                    )
                }
            }
        }
    }

    fun selectCategoryType(typeId: String, typeName: String) {
        _categoryState.update {
            it.copy(
                selectedTypeId = typeId,
                selectedTypeName = typeName,
                page = 1
            )
        }
        loadCategoryBrowseItems()
    }

    fun selectCategoryYear(year: String) {
        _categoryState.update { it.copy(selectedYear = year, page = 1) }
        loadCategoryBrowseItems()
    }

    fun selectCategorySort(sortBy: String) {
        _categoryState.update { it.copy(selectedSortBy = sortBy, page = 1) }
        loadCategoryBrowseItems()
    }

    fun changeCategoryPage(newPage: Int) {
        val st = _categoryState.value
        val target = newPage.coerceIn(1, st.pageCount.coerceAtLeast(1))
        if (target == st.page) return
        _categoryState.update { it.copy(page = target) }
        loadCategoryBrowseItems()
    }

    fun loadCategoryBrowseItems() {
        val st = _categoryState.value
        val src = _vodSources.value.find { it.id == st.selectedSourceId }
            ?: MovieHubData.DEFAULT_VOD_SOURCES.first()
        viewModelScope.launch {
            _categoryState.update { it.copy(isLoadingItems = true, itemsError = null) }
            try {
                val res = MovieHubNetwork.browseCategory(
                    source = src,
                    typeId = st.selectedTypeId,
                    page = st.page,
                    year = st.selectedYear,
                    sortBy = st.selectedSortBy
                )
                _categoryState.update {
                    it.copy(
                        isLoadingItems = false,
                        items = res.items,
                        page = res.page,
                        pageCount = res.pageCount,
                        total = res.total,
                        selectedTypeName = res.typeName.ifEmpty { it.selectedTypeName }
                    )
                }
            } catch (e: Exception) {
                _categoryState.update {
                    it.copy(
                        isLoadingItems = false,
                        itemsError = e.message ?: "分类数据加载失败"
                    )
                }
            }
        }
    }

    /* ─────────────────── 5. 电视直播 (cn-tv-tested.m3u) ─────────────────── */

    fun selectLiveGroup(group: String) {
        _selectedLiveGroup.value = group
    }

    fun playLiveChannel(channel: LiveTvChannel) {
        pauseMusicForVideo()
        _activeLiveChannel.value = channel
    }

    fun closeLivePlayer() {
        _activeLiveChannel.value = null
    }

    fun addCustomLiveChannel(name: String, group: String, url: String) {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (cleanName.isEmpty() || !cleanUrl.startsWith("http", ignoreCase = true)) {
            showToast("请输入有效的频道名称和 http/https 直播源地址")
            return
        }
        val newCh = LiveTvChannel(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            group = group.trim().ifEmpty { "自定义" },
            url = cleanUrl
        )
        _liveChannels.update { listOf(newCh) + it }
        showToast("已添加频道：$cleanName")
    }

    /* ─────────────────── 6. 音乐中心、跨源解析与同步歌词 ─────────────────── */

    fun selectMusicSubTab(subTab: MusicSubTab) {
        _musicState.update { it.copy(subTab = subTab) }
    }

    fun selectMusicPlatform(platformKey: String) {
        _musicState.update { it.copy(selectedPlatform = platformKey) }
        val kw = _musicState.value.searchKeyword.trim()
        if (kw.isNotEmpty()) {
            searchMusic(kw, platformKey)
        }
    }

    fun updateMusicSearchKeyword(kw: String) {
        _musicState.update { it.copy(searchKeyword = kw) }
    }

    fun searchMusic(
        keyword: String = _musicState.value.searchKeyword,
        platform: String = _musicState.value.selectedPlatform
    ) {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            showToast("请输入歌曲或歌手名")
            return
        }
        _musicState.update {
            it.copy(
                searchKeyword = kw,
                selectedPlatform = platform,
                isSearching = true,
                searchError = null,
                subTab = MusicSubTab.SEARCH
            )
        }
        viewModelScope.launch {
            try {
                val tracks = MovieHubNetwork.searchMusic(kw, platform, 1)
                _musicState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = tracks,
                        searchError = if (tracks.isEmpty()) "未搜到相关歌曲" else null
                    )
                }
            } catch (e: Exception) {
                _musicState.update {
                    it.copy(
                        isSearching = false,
                        searchError = "搜索失败：${e.message}"
                    )
                }
            }
        }
    }

    fun playMusicTrack(track: MusicTrack, addToPlaylist: Boolean = true) {
        viewModelScope.launch {
            if (addToPlaylist) {
                repository.addTrackToPlaylist(track)
            }
            _musicState.update {
                it.copy(
                    currentTrack = track,
                    isResolvingUrl = true,
                    isPlaying = false,
                    currentTimeSec = 0.0,
                    durationSec = track.duration.toDouble(),
                    resolvedVia = "解析中…",
                    lyricLines = emptyList(),
                    isLoadingLyric = true
                )
            }

            // Fetch lyrics concurrently
            launch {
                val rawLrc = MovieHubNetwork.fetchLyric(track)
                val parsed = MovieHubData.parseLrc(rawLrc)
                _musicState.update {
                    it.copy(
                        lyricLines = parsed,
                        isLoadingLyric = false
                    )
                }
            }

            // Resolve audio URL and start MediaPlayer
            try {
                val resolved = MovieHubNetwork.resolveMusicUrl(
                    track = track,
                    preferredQuality = _musicState.value.preferredQuality
                )
                _musicState.update {
                    it.copy(
                        isResolvingUrl = false,
                        resolvedVia = "${resolved.via} · ${resolved.quality.uppercase()}"
                    )
                }
                startMediaPlayer(resolved.url)
            } catch (e: Exception) {
                _musicState.update {
                    it.copy(
                        isResolvingUrl = false,
                        isPlaying = false,
                        resolvedVia = "解析失败"
                    )
                }
                showToast("播放失败（自动尝试下一首）：${e.message}")
                delay(800)
                playNextMusicTrack(autoFromError = true)
            }
        }
    }

    private fun startMediaPlayer(streamUrl: String, triedProxy: Boolean = false) {
        releaseMediaPlayer()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(streamUrl)
                setOnPreparedListener { player ->
                    player.start()
                    val durSec = if (player.duration > 0) player.duration / 1000.0 else _musicState.value.durationSec
                    _musicState.update {
                        it.copy(
                            isPlaying = true,
                            durationSec = durSec
                        )
                    }
                    startMusicProgressLoop()
                }
                setOnCompletionListener {
                    playNextMusicTrack(autoEnded = true)
                }
                setOnErrorListener { _, _, _ ->
                    if (!triedProxy && !streamUrl.contains("127.0.0.1")) {
                        val proxyUrl = LocalMediaProxyServer.getAudioProxyUrl(streamUrl)
                        startMediaPlayer(proxyUrl, triedProxy = true)
                    } else {
                        _musicState.update { it.copy(isPlaying = false, resolvedVia = "加载中断") }
                    }
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            if (!triedProxy && !streamUrl.contains("127.0.0.1")) {
                val proxyUrl = LocalMediaProxyServer.getAudioProxyUrl(streamUrl)
                startMediaPlayer(proxyUrl, triedProxy = true)
            } else {
                _musicState.update { it.copy(isPlaying = false, resolvedVia = "播放器异常") }
            }
        }
    }

    fun stopAndCloseMusicPlayer() {
        releaseMediaPlayer()
        _musicState.update {
            it.copy(
                currentTrack = null,
                isPlaying = false,
                isResolvingUrl = false,
                showFullLyricsSheet = false,
                currentTimeSec = 0.0
            )
        }
    }

    private fun startMusicProgressLoop() {
        musicProgressJob?.cancel()
        musicProgressJob = viewModelScope.launch {
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && _musicState.value.isPlaying) {
                    try {
                        if (mp.isPlaying) {
                            val curSec = mp.currentPosition / 1000.0
                            val durSec = if (mp.duration > 0) mp.duration / 1000.0 else _musicState.value.durationSec
                            _musicState.update {
                                it.copy(
                                    currentTimeSec = curSec,
                                    durationSec = durSec
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                delay(250)
            }
        }
    }

    fun toggleMusicPlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            val cur = _musicState.value.currentTrack ?: playlistTracks.value.firstOrNull()
            if (cur != null) playMusicTrack(cur, addToPlaylist = false)
            return
        }
        try {
            if (mp.isPlaying) {
                mp.pause()
                _musicState.update { it.copy(isPlaying = false) }
            } else {
                mp.start()
                _musicState.update { it.copy(isPlaying = true) }
            }
        } catch (_: Exception) {
        }
    }

    fun seekMusicTo(fraction: Float) {
        val mp = mediaPlayer ?: return
        val dur = _musicState.value.durationSec
        if (dur <= 0) return
        val targetMs = (fraction.coerceIn(0f, 1f) * dur * 1000).toInt()
        try {
            mp.seekTo(targetMs)
            _musicState.update { it.copy(currentTimeSec = targetMs / 1000.0) }
        } catch (_: Exception) {
        }
    }

    fun cycleMusicPlayMode() {
        _musicState.update {
            val next = it.playMode.next()
            showToast("播放模式：${next.label}")
            it.copy(playMode = next)
        }
    }

    fun setMusicPreferredQuality(quality: String) {
        _musicState.update { it.copy(preferredQuality = quality) }
        showToast("首选音质已设为：${quality.uppercase()}")
    }

    fun toggleFullLyricsSheet(show: Boolean = !_musicState.value.showFullLyricsSheet) {
        _musicState.update { it.copy(showFullLyricsSheet = show) }
    }

    fun playNextMusicTrack(autoEnded: Boolean = false, autoFromError: Boolean = false) {
        val queue = playlistTracks.value.ifEmpty { _musicState.value.searchResults }
        if (queue.isEmpty()) return
        val cur = _musicState.value.currentTrack
        if (autoEnded && _musicState.value.playMode == PlayMode.SINGLE_LOOP && cur != null && !autoFromError) {
            seekMusicTo(0f)
            mediaPlayer?.start()
            _musicState.update { it.copy(isPlaying = true) }
            return
        }
        val curIdx = queue.indexOfFirst { it.uniqueKey == cur?.uniqueKey }
        val nextIdx = when (_musicState.value.playMode) {
            PlayMode.SHUFFLE -> (queue.indices).random()
            else -> if (curIdx < 0) 0 else (curIdx + 1) % queue.size
        }
        playMusicTrack(queue[nextIdx], addToPlaylist = false)
    }

    fun playPrevMusicTrack() {
        val queue = playlistTracks.value.ifEmpty { _musicState.value.searchResults }
        if (queue.isEmpty()) return
        val cur = _musicState.value.currentTrack
        val curIdx = queue.indexOfFirst { it.uniqueKey == cur?.uniqueKey }
        val prevIdx = if (curIdx <= 0) queue.lastIndex else curIdx - 1
        playMusicTrack(queue[prevIdx], addToPlaylist = false)
    }

    fun addTrackToPlaylistOnly(track: MusicTrack) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(track)
            showToast("已加入喜欢的音乐：${track.name}")
        }
    }

    fun toggleTrackLiked(track: MusicTrack) {
        val exists = playlistTracks.value.any { it.uniqueKey == track.uniqueKey }
        viewModelScope.launch {
            if (exists) {
                repository.removeTrackFromPlaylist(track.uniqueKey)
                showToast("已从喜欢的音乐移除")
            } else {
                repository.addTrackToPlaylist(track)
                showToast("已加入喜欢的音乐")
            }
        }
    }

    fun removeTrackFromPlaylist(uniqueKey: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(uniqueKey)
        }
    }

    fun clearMusicPlaylist() {
        viewModelScope.launch {
            repository.clearPlaylist()
            showToast("已清空播放列表")
        }
    }

    fun toggleLxScript(id: String) {
        _musicState.update { st ->
            st.copy(
                lxScripts = st.lxScripts.map {
                    if (it.id == id) it.copy(enabled = !it.enabled) else it
                }
            )
        }
    }

    fun addCustomLxScript(name: String, url: String) {
        if (name.isBlank() || !url.startsWith("http", ignoreCase = true)) {
            showToast("请输入有效的音源名称与脚本 URL")
            return
        }
        val newScript = LxScriptSource(
            id = "custom_${System.currentTimeMillis()}",
            name = name.trim(),
            url = url.trim(),
            enabled = true
        )
        _musicState.update { it.copy(lxScripts = it.lxScripts + newScript) }
        showToast("已添加自定义音源：${name.trim()}")
    }

    fun updateSpotifyClientId(clientId: String) {
        _musicState.update { it.copy(spotifyClientId = clientId.trim()) }
        showToast("已保存 Spotify Client ID 配置")
    }

    /* ─────────────────── 7. 渠道状态检测、情况记录与查询源方法 ─────────────────── */

    fun toggleDiagnosticsSheet(show: Boolean = !_showDiagnosticsSheet.value) {
        _showDiagnosticsSheet.value = show
        if (show && _healthResults.value.isEmpty() && !_isRunningDiagnostics.value) {
            runFullChannelDiagnostics()
        }
    }

    fun toggleDownloadCenterSheet(show: Boolean = !_showDownloadCenterSheet.value) {
        _showDownloadCenterSheet.value = show
    }

    fun toggleUpdateSheet(show: Boolean = !_showUpdateSheet.value) {
        _showUpdateSheet.value = show
        if (show && _appUpdateResult.value.checkLatencyMs == 0L && !_isCheckingUpdate.value) {
            checkAppUpdateNow(silent = true)
        }
    }

    fun checkAppUpdateNow(customUrl: String = "", silent: Boolean = false) {
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true
        viewModelScope.launch {
            try {
                val res = MovieHubNetwork.checkAppUpdate(customUrl)
                _appUpdateResult.value = res
                repository.insertDiagnosticLog(
                    SourceDiagnosticLogEntity(
                        channelType = "UPDATE",
                        sourceId = "app_update",
                        sourceName = "版本检查 (当前 v${res.currentVersionName} -> 远程 v${res.latestVersionName})",
                        apiUrl = customUrl.ifBlank { MovieHubNetwork.DEFAULT_UPDATE_MANIFEST_URL },
                        status = "HEALTHY",
                        httpCode = 200,
                        apiLatencyMs = res.checkLatencyMs,
                        streamLatencyMs = res.checkLatencyMs,
                        vodTotalCount = res.latestVersionCode,
                        detailMessage = if (res.hasNewAppVersion) {
                            "发现新版本 v${res.latestVersionName} (${res.checkedEndpoint})"
                        } else {
                            "当前已是最新版本 v${res.currentVersionName} (${res.checkedEndpoint})"
                        }
                    )
                )
                if (!silent) {
                    if (res.hasNewAppVersion) {
                        showToast("🎉 发现新版本 v${res.latestVersionName}，点击「一键加速下载新版本 APK」即可覆盖升级！")
                    } else {
                        showToast("✅ 当前客户端 v${res.currentVersionName} 已是最新版本（片源支持云端免安装热更新）")
                    }
                }
            } catch (e: Exception) {
                if (!silent) {
                    showToast("检查更新失败：${e.message}")
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun publishCurrentSourcesToGitHub(githubToken: String, repoFullName: String = "berse1kguts-pixel/moviehub") {
        if (_isPublishingToGitHub.value) return
        _isPublishingToGitHub.value = true
        viewModelScope.launch {
            try {
                val jsonContent = MovieHubNetwork.buildRemoteSourcesJson(
                    vodSources = _vodSources.value,
                    liveChannels = _liveChannels.value,
                    lxScripts = _musicState.value.lxScripts
                )
                val res = MovieHubNetwork.publishFileToGitHub(
                    githubToken = githubToken,
                    repoFullName = repoFullName,
                    filePath = "moviehub/sources.json",
                    fileContent = jsonContent,
                    commitMessage = "chore(sources): 手机端一键同步最新影视源与直播源 (${_vodSources.value.size} VOD / ${_liveChannels.value.size} Live)"
                )
                showToast(res.message)
                if (res.success) {
                    syncRemoteSourcesNow(silentToast = true)
                }
            } finally {
                _isPublishingToGitHub.value = false
            }
        }
    }

    fun publishVersionJsonToGitHub(
        githubToken: String,
        repoFullName: String = "berse1kguts-pixel/moviehub",
        versionName: String,
        versionCode: Int,
        apkUrl: String,
        changelog: String
    ) {
        if (_isPublishingToGitHub.value) return
        _isPublishingToGitHub.value = true
        viewModelScope.launch {
            try {
                val jsonContent = MovieHubNetwork.buildVersionManifestJson(
                    versionName = versionName,
                    versionCode = versionCode,
                    apkUrl = apkUrl,
                    changelog = changelog
                )
                val res = MovieHubNetwork.publishFileToGitHub(
                    githubToken = githubToken,
                    repoFullName = repoFullName,
                    filePath = "version.json",
                    fileContent = jsonContent,
                    commitMessage = "release: 手机端一键发布新版本 v$versionName (build $versionCode)"
                )
                showToast(res.message)
                if (res.success) {
                    checkAppUpdateNow(silent = true)
                }
            } finally {
                _isPublishingToGitHub.value = false
            }
        }
    }

    fun runFullChannelDiagnostics() {
        if (_isRunningDiagnostics.value) return
        _isRunningDiagnostics.value = true
        viewModelScope.launch {
            try {
                val results = MovieHubNetwork.runFullChannelDiagnostics(
                    vodSources = _vodSources.value,
                    liveChannels = _liveChannels.value
                )
                _healthResults.value = results
                val now = System.currentTimeMillis()
                val logEntities = results.map { r ->
                    SourceDiagnosticLogEntity(
                        channelType = r.channelType,
                        sourceId = r.sourceId,
                        sourceName = r.sourceName,
                        apiUrl = r.apiUrl,
                        status = r.status,
                        httpCode = r.httpCode,
                        apiLatencyMs = r.apiLatencyMs,
                        streamLatencyMs = r.streamLatencyMs,
                        vodTotalCount = r.vodTotalCount,
                        detailMessage = r.detailMessage,
                        checkedAt = now
                    )
                }
                repository.insertDiagnosticLogs(logEntities)
                val healthy = results.count { it.status == "HEALTHY" }
                val slow = results.count { it.status == "DEGRADED" }
                val broken = results.count { it.status == "OFFLINE" }
                showToast("全渠道检测完成：正常 $healthy · 较慢 $slow · 异常 $broken（已写入情况记录）")
            } catch (e: Exception) {
                showToast("渠道检测异常：${e.message}")
            } finally {
                _isRunningDiagnostics.value = false
            }
        }
    }

    fun clearDiagnosticLogs() {
        viewModelScope.launch {
            repository.clearDiagnosticLogs()
            showToast("已清空渠道情况记录")
        }
    }

    fun probeCustomSourceApi(apiUrl: String, keyword: String) {
        if (_isProbingCustomSource.value) return
        _isProbingCustomSource.value = true
        viewModelScope.launch {
            try {
                val inspection = MovieHubNetwork.inspectCustomSourceApi(apiUrl, keyword)
                _probeInspection.value = inspection
                repository.insertDiagnosticLog(
                    SourceDiagnosticLogEntity(
                        channelType = "VOD",
                        sourceId = "probe_api",
                        sourceName = "接口探测 (${inspection.apiUrl.take(28)})",
                        apiUrl = inspection.apiUrl,
                        status = if (inspection.isReachable) "HEALTHY" else "OFFLINE",
                        httpCode = if (inspection.isReachable) 200 else 0,
                        apiLatencyMs = inspection.apiLatencyMs,
                        streamLatencyMs = inspection.streamLatencyMs,
                        vodTotalCount = inspection.totalVodCount,
                        detailMessage = inspection.errorMsg ?: "探测成功：返回 ${inspection.sampleItems.size} 条抽样 · 切片流 ${inspection.streamLatencyMs}ms"
                    )
                )
            } catch (e: Exception) {
                showToast("探测失败：${e.message}")
            } finally {
                _isProbingCustomSource.value = false
            }
        }
    }

    fun addProbedVodSource(id: String, name: String, apiUrl: String) {
        val cleanUrl = apiUrl.trim()
        if (cleanUrl.isEmpty()) return
        val newSrc = VodSource(
            id = id.ifEmpty { "src_${System.currentTimeMillis() % 10000}" },
            name = name.ifEmpty { "自定义资源站" },
            api = cleanUrl,
            enabled = true
        )
        _vodSources.update { current ->
            val filtered = current.filterNot { it.api.equals(cleanUrl, ignoreCase = true) || it.id == newSrc.id }
            listOf(newSrc) + filtered
        }
        showToast("已将「${newSrc.name}」加入当前可用片源列表")
    }

    fun runCrossChannelSearch(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty() || _isCrossChannelSearching.value) return
        _isCrossChannelSearching.value = true
        viewModelScope.launch {
            try {
                val res = MovieHubNetwork.searchVod(
                    keyword = kw,
                    targetSrcId = "",
                    allSources = _vodSources.value
                )
                _crossChannelQueryResult.value = res
                repository.insertDiagnosticLog(
                    SourceDiagnosticLogEntity(
                        channelType = "VOD",
                        sourceId = "cross_search",
                        sourceName = "按片名反查渠道「$kw」",
                        apiUrl = "全渠道并发检索 (${res.sourceCount}源)",
                        status = if (res.hitSources > 0) "HEALTHY" else "OFFLINE",
                        httpCode = 200,
                        apiLatencyMs = 0L,
                        streamLatencyMs = 0L,
                        vodTotalCount = res.total,
                        detailMessage = "命中 ${res.hitSources}/${res.sourceCount} 个源，共找到 ${res.total} 条片源结果"
                    )
                )
            } catch (e: Exception) {
                showToast("跨渠道查询失败：${e.message}")
            } finally {
                _isCrossChannelSearching.value = false
            }
        }
    }

    /* ─────────────────── 8. 手机端离线视频下载缓存管理 (.m3u8 / .mp4) ─────────────────── */

    fun downloadCurrentVodEpisodes(episodeIndices: List<Int>) {
        val st = _vodPlayerState.value
        val item = st.item ?: return
        val match = st.activeSourceMatch
        val episodes = st.activeLine?.episodes ?: return
        if (episodeIndices.isEmpty()) return

        val srcId = match?.sourceId ?: item.source.ifEmpty { "local" }
        val srcName = match?.sourceName ?: item.sourceName.ifEmpty { "默认源" }
        val vodId = match?.vodId ?: item.vodId.ifEmpty { MovieHubData.normalizeName(item.name) }
        val pic = item.pic.ifEmpty { match?.pic.orEmpty() }
        val normTitle = MovieHubData.normalizeName(item.name).ifEmpty { "vod" }

        var startedCount = 0
        episodeIndices.forEach { epIdx ->
            val ep = episodes.getOrNull(epIdx) ?: return@forEach
            if (ep.url.isBlank()) return@forEach
            val taskId = "${srcId}_${vodId}_${st.selectedLineIndex}_$epIdx".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .ifEmpty { "${normTitle}_$epIdx" }
            val entity = VideoDownloadEntity(
                taskId = taskId,
                vodName = item.name,
                vodPic = pic,
                sourceId = srcId,
                sourceName = srcName,
                vodId = vodId,
                epIndex = epIdx,
                epTitle = ep.title,
                remoteUrl = ep.url,
                status = "DOWNLOADING"
            )
            VideoDownloadManager.startOrResumeDownload(
                context = getApplication(),
                repository = repository,
                entity = entity
            )
            startedCount++
        }
        if (startedCount > 0) {
            showToast("⬇ 已加入离线缓存队列（共 $startedCount 集），点击右上角「缓存」可查看进度")
        }
    }

    fun pauseVideoDownload(taskId: String) {
        VideoDownloadManager.pauseDownload(repository, taskId)
        showToast("已暂停下载任务")
    }

    fun resumeVideoDownload(task: VideoDownloadEntity) {
        VideoDownloadManager.startOrResumeDownload(
            context = getApplication(),
            repository = repository,
            entity = task
        )
        showToast("已继续下载：${task.vodName} - ${task.epTitle}")
    }

    fun deleteVideoDownload(taskId: String) {
        VideoDownloadManager.deleteDownload(
            context = getApplication(),
            repository = repository,
            taskId = taskId
        )
        showToast("已删除本地缓存文件")
    }

    fun clearAllVideoDownloads() {
        VideoDownloadManager.clearAllDownloads(
            context = getApplication(),
            repository = repository
        )
        showToast("已清空全部离线视频缓存")
    }

    fun playOfflineDownload(task: VideoDownloadEntity) {
        pauseMusicForVideo()
        _showDownloadCenterSheet.value = false
        val playUrl = task.localFilePath.ifEmpty { task.remoteUrl }
        val ep = VodEpisode(
            title = "${task.epTitle} (本地离线)",
            url = playUrl
        )
        val line = VodPlayLine(
            flag = "本地缓存 · ${task.sourceName}",
            episodes = listOf(ep)
        )
        val offlineMatch = VodSourceMatch(
            sourceId = task.sourceId,
            sourceName = "本地缓存 (${task.sourceName})",
            vodId = task.vodId,
            lines = listOf(line),
            pic = task.vodPic,
            remarks = "已缓存离线秒播"
        )
        val vodItem = VodItem(
            vodId = task.vodId,
            name = task.vodName,
            pic = task.vodPic,
            remarks = "本地离线缓存",
            source = task.sourceId,
            sourceName = "本地缓存",
            lines = listOf(line),
            sources = listOf(offlineMatch)
        )
        _vodPlayerState.value = VodPlayerState(
            item = vodItem,
            isLoadingDetail = false,
            selectedSourceIndex = 0,
            selectedLineIndex = 0,
            selectedEpisodeIndex = 0,
            isPlaying = true
        )
    }

    private fun pauseMusicForVideo() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _musicState.update { it.copy(isPlaying = false) }
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseMediaPlayer() {
        musicProgressJob?.cancel()
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        releaseMediaPlayer()
    }
}
