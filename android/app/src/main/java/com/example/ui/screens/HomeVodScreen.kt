package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.data.local.FavoriteVodEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.DoubanSubject
import com.example.data.model.MovieHubData
import com.example.data.model.VodItem
import com.example.data.model.VodSource
import com.example.data.network.MovieHubNetwork
import com.example.ui.RemoteSyncUiState
import com.example.ui.components.MinimalPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.VodPosterCard
import com.example.ui.theme.MhBg
import com.example.ui.theme.MhBgSoft
import com.example.ui.theme.MhBorder
import com.example.ui.theme.MhCard
import com.example.ui.theme.MhCardHover
import com.example.ui.theme.MhSpotifyGreen
import com.example.ui.theme.MhText
import com.example.ui.theme.MhTextDim

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeVodScreen(
    searchQuery: String,
    selectedSearchSrcId: String,
    vodSources: List<VodSource>,
    remoteSyncState: RemoteSyncUiState = RemoteSyncUiState(),
    doubanType: String,
    doubanTag: String,
    doubanItems: List<DoubanSubject>,
    homeChannelBlocks: Map<String, List<DoubanSubject>> = emptyMap(),
    isLoadingDouban: Boolean,
    doubanError: String?,
    watchHistory: List<WatchHistoryEntity>,
    favoriteVods: List<FavoriteVodEntity> = emptyList(),
    recentSearches: List<String>,
    latestItems: List<VodItem>,
    latestSourceName: String,
    isLoadingLatest: Boolean,
    latestError: String?,
    isSearchOverlayOpen: Boolean,
    isSearchingVod: Boolean,
    vodSearchError: String?,
    vodSearchResult: MovieHubNetwork.MultiSearchResult?,
    onSearchQueryChange: (String) -> Unit,
    onSelectSearchSrc: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onCloseSearchOverlay: () -> Unit,
    onToggleSourceEnabled: (String) -> Unit,
    onSyncRemoteSources: (String?) -> Unit = {},
    onSelectDoubanType: (String) -> Unit,
    onSelectDoubanTag: (String) -> Unit,
    onRetryDouban: () -> Unit,
    onOpenDoubanItem: (DoubanSubject) -> Unit,
    onOpenVodItem: (VodItem) -> Unit,
    onResumeHistory: (WatchHistoryEntity) -> Unit,
    onOpenFavoriteVod: (FavoriteVodEntity) -> Unit = {},
    onClearWatchHistory: () -> Unit,
    onClearRecentSearches: () -> Unit,
    onReloadLatest: (String) -> Unit,
    onJumpToCategoryChannel: (String) -> Unit = {},
    downloadCount: Int = 0,
    hasNewUpdate: Boolean = false,
    onOpenUpdateCenter: () -> Unit = {},
    onOpenDiagnosticsCenter: () -> Unit = {},
    onOpenDownloadCenter: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var showSourceMenu by remember { mutableStateOf(false) }
    var showSourceConfigDialog by remember { mutableStateOf(false) }

    if (isSearchOverlayOpen) {
        BackHandler {
            onCloseSearchOverlay()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MhBg)
    ) {
        // Minimalist Top Bar & Capsule Search
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            if (!isSearchOverlayOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "观影中心",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MhText
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Online Update Check & Hot-Update Center Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (hasNewUpdate) MhSpotifyGreen.copy(alpha = 0.16f) else MhBgSoft)
                            .border(
                                0.5.dp,
                                if (hasNewUpdate) MhSpotifyGreen else MhBorder,
                                RoundedCornerShape(999.dp)
                            )
                            .clickable { onOpenUpdateCenter() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("open_update_center_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (hasNewUpdate) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MhSpotifyGreen)
                            )
                        }
                        Text(
                            text = if (hasNewUpdate) "🔄 新版" else "🔄 更新",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = if (hasNewUpdate) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (hasNewUpdate) MhSpotifyGreen else MhTextDim
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    // Offline Video Download Center Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MhBgSoft)
                            .border(0.5.dp, MhBorder, RoundedCornerShape(999.dp))
                            .clickable { onOpenDownloadCenter() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("open_download_center_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (downloadCount > 0) "⬇ 缓存($downloadCount)" else "⬇ 缓存",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = if (downloadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (downloadCount > 0) MhSpotifyGreen else MhTextDim
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    // Channel Status Diagnostics & Remote Sync Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MhBgSoft)
                            .border(0.5.dp, MhBorder, RoundedCornerShape(999.dp))
                            .clickable { onOpenDiagnosticsCenter() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("manage_sources_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (remoteSyncState.isSyncing || remoteSyncState.isSpeedTesting) {
                                        Color(0xFFF5A623)
                                    } else {
                                        MhSpotifyGreen
                                    }
                                )
                        )
                        Text(
                            text = if (remoteSyncState.isSyncing || remoteSyncState.isSpeedTesting) {
                                remoteSyncState.syncStatusText
                            } else {
                                "🩺 检测 · ${vodSources.count { it.enabled }}源"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = MhTextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Unified Minimalist Capsule Search Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSearchOverlayOpen) {
                    IconButton(
                        onClick = onCloseSearchOverlay,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MhBgSoft)
                            .testTag("close_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MhText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MhBgSoft)
                        .border(0.5.dp, MhBorder, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MhTextDim,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "搜索电影、剧集、综艺、动漫…",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MhTextDim
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = MhText
                            ),
                            cursorBrush = SolidColor(MhText),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    onExecuteSearch(searchQuery)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vod_search_input")
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除",
                            tint = MhTextDim,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSearchQueryChange("") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Inline Source Selector inside Capsule
                    Box {
                        val currentSrcName = vodSources.find { it.id == selectedSearchSrcId }?.name ?: "全部源"
                        Text(
                            text = "$currentSrcName ▾",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = MhTextDim,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MhCardHover)
                                .clickable { showSourceMenu = true }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                .testTag("source_selector_button")
                        )
                        DropdownMenu(
                            expanded = showSourceMenu,
                            onDismissRequest = { showSourceMenu = false },
                            modifier = Modifier.background(MhCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部源", color = MhText, fontSize = 13.sp) },
                                onClick = {
                                    onSelectSearchSrc("")
                                    showSourceMenu = false
                                }
                            )
                            vodSources.filter { it.enabled }.forEach { src ->
                                DropdownMenuItem(
                                    text = { Text(src.name, color = MhText, fontSize = 13.sp) },
                                    onClick = {
                                        onSelectSearchSrc(src.id)
                                        showSourceMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MhText)
                            .clickable {
                                focusManager.clearFocus()
                                onExecuteSearch(searchQuery)
                            }
                            .padding(horizontal = 16.dp)
                            .testTag("vod_search_submit"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "搜索",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MhBg
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MhBorder)

        if (isSearchOverlayOpen) {
            SearchResultsContent(
                isSearching = isSearchingVod,
                error = vodSearchError,
                result = vodSearchResult,
                onOpenVodItem = onOpenVodItem,
                onClose = onCloseSearchOverlay
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Continue Watching (继续观看)
                if (watchHistory.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "继续观看",
                            actionText = "清空",
                            onActionClick = onClearWatchHistory
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(watchHistory, key = { it.normKey }) { hist ->
                                VodPosterCard(
                                    title = hist.name,
                                    posterUrl = hist.pic,
                                    subtitle = hist.epTitle,
                                    badgeText = if (hist.multiSourceCount > 1) "${hist.multiSourceCount} 源" else hist.sourceName,
                                    isMultiSourceBadge = hist.multiSourceCount > 1,
                                    onClick = { onResumeHistory(hist) },
                                    modifier = Modifier.width(118.dp)
                                )
                            }
                        }
                    }
                }

                // 1b. My Watchlist (我的片单)
                if (favoriteVods.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "我的片单",
                            subtitle = "${favoriteVods.size} 部已收藏"
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(favoriteVods, key = { it.normKey }) { fav ->
                                VodPosterCard(
                                    title = fav.name,
                                    posterUrl = fav.pic,
                                    subtitle = fav.year.ifEmpty { fav.sourceName },
                                    badgeText = fav.remarks,
                                    onClick = { onOpenFavoriteVod(fav) },
                                    modifier = Modifier.width(118.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Recent Searches (最近搜索)
                if (recentSearches.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "最近搜索",
                            actionText = "清除",
                            onActionClick = onClearRecentSearches
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentSearches.forEach { kw ->
                                MinimalPill(
                                    text = kw,
                                    selected = false,
                                    onClick = { onExecuteSearch(kw) }
                                )
                            }
                        }
                    }
                }

                // 3. Dedicated 4-Channel Hub: 电影 · 电视剧 · 动漫 · 纪录片
                item {
                    val activeSection = MovieHubData.VOD_MAJOR_CHANNELS.find { it.key == doubanType }
                        ?: MovieHubData.VOD_MAJOR_CHANNELS.first()

                    SectionHeader(
                        title = "${activeSection.title}专区",
                        subtitle = activeSection.subtitle,
                        actionText = "刷新",
                        onActionClick = onRetryDouban
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 5 Dedicated Primary Channel Cards (电影 / 电视剧 / 动漫 / 纪录片 / 短剧)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "movie" to "🎬 电影",
                                "tv" to "📺 剧集",
                                "anime" to "🎨 动漫",
                                "doc" to "🌍 纪录",
                                "short" to "📱 短剧"
                            ).forEach { (typeKey, label) ->
                                val selected = doubanType == typeKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) MhText else MhBgSoft)
                                        .border(
                                            0.5.dp,
                                            if (selected) MhText else MhBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSelectDoubanType(typeKey) }
                                        .padding(vertical = 9.dp)
                                        .testTag("douban_type_$typeKey"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = if (selected) MhBg else MhText,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Sub-tags for the active channel
                        val tags = activeSection.tags
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tags.forEach { tg ->
                                val active = doubanTag == tg
                                Text(
                                    text = tg,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (active) MhText else MhTextDim,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (active) MhCardHover else MhBgSoft)
                                        .clickable { onSelectDoubanTag(tg) }
                                        .padding(horizontal = 11.dp, vertical = 5.dp)
                                )
                            }
                        }

                        when {
                            isLoadingDouban -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MhTextDim,
                                        strokeWidth = 1.5.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            doubanError != null && doubanItems.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(doubanError, style = MaterialTheme.typography.bodyMedium, color = MhTextDim)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        TextButton(onClick = onRetryDouban) {
                                            Text("重试", color = MhText)
                                        }
                                    }
                                }
                            }
                            else -> {
                                val rows = doubanItems.chunked(3)
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    rows.forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowItems.forEach { subject ->
                                                VodPosterCard(
                                                    title = subject.title,
                                                    posterUrl = subject.cover,
                                                    subtitle = subject.year.ifEmpty { subject.subtitle },
                                                    rateBadge = if (subject.rate > 0) subject.rate.toString() else "",
                                                    onClick = { onOpenDoubanItem(subject) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            repeat(3 - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3b. Dedicated Horizontal Blocks for the Other 3 Major Channels (电影 / 电视剧 / 动漫 / 纪录片)
                MovieHubData.VOD_MAJOR_CHANNELS.filter { it.key != doubanType }.forEach { chSection ->
                    val blockList = homeChannelBlocks[chSection.key].orEmpty()
                    if (blockList.isNotEmpty()) {
                        item(key = "home_channel_block_${chSection.key}") {
                            SectionHeader(
                                title = "${chSection.title}精选",
                                subtitle = chSection.subtitle,
                                actionText = "片库 ▸",
                                onActionClick = { onJumpToCategoryChannel(chSection.key) }
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(blockList, key = { "${chSection.key}_${it.id}" }) { subject ->
                                    VodPosterCard(
                                        title = subject.title,
                                        posterUrl = subject.cover,
                                        subtitle = subject.year.ifEmpty { subject.subtitle },
                                        rateBadge = if (subject.rate > 0) subject.rate.toString() else "",
                                        onClick = { onOpenDoubanItem(subject) },
                                        modifier = Modifier.width(118.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Latest Updates (最新入库)
                item {
                    SectionHeader(
                        title = "最新入库",
                        subtitle = latestSourceName,
                        actionText = "换源",
                        onActionClick = {
                            val enabled = vodSources.filter { it.enabled }
                            val nextSrc = enabled.shuffled().firstOrNull()?.id ?: ""
                            onReloadLatest(nextSrc)
                        }
                    )

                    when {
                        isLoadingLatest -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MhTextDim,
                                    strokeWidth = 1.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        latestError != null && latestItems.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(onClick = { onReloadLatest("") }) {
                                    Text("重新加载片库", color = MhTextDim)
                                }
                            }
                        }
                        else -> {
                            val rows = latestItems.chunked(3)
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                rows.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowItems.forEach { item ->
                                            VodPosterCard(
                                                title = item.name,
                                                posterUrl = item.pic,
                                                subtitle = item.remarks.ifEmpty { item.typeName },
                                                badgeText = item.sourceName,
                                                onClick = { onOpenVodItem(item) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    if (showSourceConfigDialog) {
        var customUrlInput by remember(remoteSyncState.remoteUrl) {
            mutableStateOf(remoteSyncState.remoteUrl)
        }
        AlertDialog(
            onDismissRequest = { showSourceConfigDialog = false },
            containerColor = MhCard,
            titleContentColor = MhText,
            textContentColor = MhText,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "云端源站同步与测速 (${vodSources.count { it.enabled }}/${vodSources.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = remoteSyncState.syncStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MhSpotifyGreen
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Remote Source Station Config Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MhBgSoft)
                            .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "远程源配置地址 (默认写死你的 GitHub 仓库)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MhTextDim
                            )
                            Text(
                                text = "重置默认",
                                style = MaterialTheme.typography.labelSmall,
                                color = MhText,
                                modifier = Modifier.clickable {
                                    customUrlInput = MovieHubNetwork.DEFAULT_REMOTE_SOURCE_URL
                                    onSyncRemoteSources(MovieHubNetwork.DEFAULT_REMOTE_SOURCE_URL)
                                }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MhBg)
                                .border(0.5.dp, MhBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                        ) {
                            BasicTextField(
                                value = customUrlInput,
                                onValueChange = { customUrlInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.labelSmall.copy(
                                    color = MhText,
                                    fontSize = 11.sp
                                ),
                                cursorBrush = SolidColor(MhText),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "远程修改 sources.json 后启动即自动同步",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MhTextDim
                            )
                            MinimalPill(
                                text = if (remoteSyncState.isSyncing || remoteSyncState.isSpeedTesting) "同步测速中…" else "☁️ 立即同步并测速",
                                selected = true,
                                onClick = {
                                    if (!remoteSyncState.isSyncing && !remoteSyncState.isSpeedTesting) {
                                        onSyncRemoteSources(customUrlInput)
                                    }
                                }
                            )
                        }
                    }

                    // Synced VOD Sources List with Real Latency Badges
                    LazyColumn(
                        modifier = Modifier.height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(vodSources, key = { it.id }) { src ->
                            val latency = MovieHubNetwork.sourceLatencyMap[src.id] ?: 0L
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleSourceEnabled(src.id) }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = src.enabled,
                                    onCheckedChange = { onToggleSourceEnabled(src.id) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = src.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MhText
                                        )
                                        if (latency in 1..8500) {
                                            val badgeColor = when {
                                                latency < 900L -> MhSpotifyGreen
                                                latency < 2200L -> Color(0xFFF5A623)
                                                else -> MhTextDim
                                            }
                                            Text(
                                                text = "⚡ ${latency}ms",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = badgeColor
                                            )
                                        } else if (latency >= 8500L) {
                                            Text(
                                                text = "超时",
                                                fontSize = 10.sp,
                                                color = MhTextDim
                                            )
                                        }
                                    }
                                    Text(
                                        text = src.api,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MhTextDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceConfigDialog = false }) {
                    Text("完成", color = MhText)
                }
            }
        )
    }
}

@Composable
private fun SearchResultsContent(
    isSearching: Boolean,
    error: String?,
    result: MovieHubNetwork.MultiSearchResult?,
    onOpenVodItem: (VodItem) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MhText,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "正在并发检索多源片库…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MhTextDim
                    )
                }
            }
            return
        }

        if (result != null) {
            SectionHeader(
                title = result.keyword,
                subtitle = "${result.total} 部结果 · 命中 ${result.hitSources}/${result.sourceCount} 源",
                actionText = "关闭",
                onActionClick = onClose
            )
        }

        if (error != null && (result == null || result.items.isEmpty())) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MhTextDim)
                    Spacer(modifier = Modifier.height(12.dp))
                    MinimalPill(text = "返回首页", selected = true, onClick = onClose)
                }
            }
            return
        }

        val items = result?.items ?: emptyList()
        val rows = items.chunked(3)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(rows) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        VodPosterCard(
                            title = item.name,
                            posterUrl = item.pic,
                            subtitle = item.remarks.ifEmpty { item.typeName },
                            badgeText = if (item.multiSourceCount > 1) "${item.multiSourceCount} 源" else item.sourceName,
                            isMultiSourceBadge = item.multiSourceCount > 1,
                            onClick = { onOpenVodItem(item) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}
