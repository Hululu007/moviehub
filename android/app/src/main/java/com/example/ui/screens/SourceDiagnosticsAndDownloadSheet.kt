package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.local.SourceDiagnosticLogEntity
import com.example.data.local.VideoDownloadEntity
import com.example.data.model.VodEpisode
import com.example.data.model.VodSource
import com.example.data.network.MovieHubNetwork
import com.example.ui.RemoteSyncUiState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StatusAmber = Color(0xFFF5A623)

/**
 * Complete Channel Status Detection, Diagnostic History Logging, and Source Query/Discovery Center.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDiagnosticsModalSheet(
    vodSources: List<VodSource>,
    remoteSyncState: RemoteSyncUiState,
    healthResults: List<MovieHubNetwork.SourceHealthCheckResult>,
    isRunningDiagnostics: Boolean,
    diagnosticLogs: List<SourceDiagnosticLogEntity>,
    probeInspection: MovieHubNetwork.SourceProbeInspection?,
    isProbingCustomSource: Boolean,
    crossChannelQueryResult: MovieHubNetwork.MultiSearchResult?,
    isCrossChannelSearching: Boolean,
    onDismiss: () -> Unit,
    onRunFullDiagnostics: () -> Unit,
    onSyncRemoteSources: (String?) -> Unit,
    onToggleVodSource: (String) -> Unit,
    onClearDiagnosticLogs: () -> Unit,
    onProbeCustomApi: (String, String) -> Unit,
    onAddProbedVodSource: (String, String, String) -> Unit,
    onRunCrossChannelSearch: (String) -> Unit,
    onShowToast: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0=渠道检测, 1=情况记录, 2=查源方法

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MhBg,
        contentColor = MhText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 18.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "渠道状态检测 · 记录 · 查源中心",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MhText
                    )
                    Text(
                        text = remoteSyncState.syncStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MhSpotifyGreen
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MhTextDim
                    )
                }
            }

            // 3 Sub-Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MinimalPill(
                    text = "1. 渠道状态检测",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                MinimalPill(
                    text = "2. 情况记录 (${diagnosticLogs.size})",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                MinimalPill(
                    text = "3. 查询源方法",
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MhBorder)
            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                0 -> ChannelStatusCheckTab(
                    vodSources = vodSources,
                    remoteSyncState = remoteSyncState,
                    healthResults = healthResults,
                    isRunningDiagnostics = isRunningDiagnostics,
                    onRunFullDiagnostics = onRunFullDiagnostics,
                    onSyncRemoteSources = onSyncRemoteSources,
                    onToggleVodSource = onToggleVodSource,
                    onCopyReport = {
                        val report = buildDiagnosticReportText(vodSources, healthResults)
                        clipboard.setText(AnnotatedString(report))
                        onShowToast("已复制完整渠道状态检测报告")
                    }
                )
                1 -> DiagnosticHistoryLogsTab(
                    logs = diagnosticLogs,
                    onClearLogs = onClearDiagnosticLogs,
                    onCopyLogs = {
                        val text = buildHistoryLogText(diagnosticLogs)
                        clipboard.setText(AnnotatedString(text))
                        onShowToast("已复制 ${diagnosticLogs.size} 条历史检测情况记录")
                    }
                )
                2 -> SourceQueryInspectorTab(
                    vodSources = vodSources,
                    probeInspection = probeInspection,
                    isProbingCustomSource = isProbingCustomSource,
                    crossChannelQueryResult = crossChannelQueryResult,
                    isCrossChannelSearching = isCrossChannelSearching,
                    onProbeCustomApi = onProbeCustomApi,
                    onAddProbedVodSource = onAddProbedVodSource,
                    onRunCrossChannelSearch = onRunCrossChannelSearch,
                    onCopyText = { txt, toast ->
                        clipboard.setText(AnnotatedString(txt))
                        onShowToast(toast)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChannelStatusCheckTab(
    vodSources: List<VodSource>,
    remoteSyncState: RemoteSyncUiState,
    healthResults: List<MovieHubNetwork.SourceHealthCheckResult>,
    isRunningDiagnostics: Boolean,
    onRunFullDiagnostics: () -> Unit,
    onSyncRemoteSources: (String?) -> Unit,
    onToggleVodSource: (String) -> Unit,
    onCopyReport: () -> Unit
) {
    var customUrlInput by remember(remoteSyncState.remoteUrl) {
        mutableStateOf(remoteSyncState.remoteUrl)
    }

    val healthyCount = healthResults.count { it.status == "HEALTHY" }
    val degradedCount = healthResults.count { it.status == "DEGRADED" }
    val offlineCount = healthResults.count { it.status == "OFFLINE" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // Remote Sync Station Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MhBgSoft)
                    .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "远程云端源站地址（写死默认自动同步）",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MhText
                    )
                    Text(
                        text = "恢复默认仓库",
                        style = MaterialTheme.typography.labelSmall,
                        color = MhSpotifyGreen,
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
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall.copy(
                            color = MhText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(MhText),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinimalPill(
                        text = if (remoteSyncState.isSyncing) "同步中…" else "☁️ 在线同步云端源",
                        selected = true,
                        onClick = { onSyncRemoteSources(customUrlInput) }
                    )
                    MinimalPill(
                        text = if (isRunningDiagnostics) "深度检测中…" else "🩺 一键全渠道深度检测",
                        selected = !isRunningDiagnostics,
                        onClick = {
                            if (!isRunningDiagnostics) onRunFullDiagnostics()
                        }
                    )
                    if (healthResults.isNotEmpty()) {
                        MinimalPill(
                            text = "复制报告",
                            selected = false,
                            onClick = onCopyReport
                        )
                    }
                }
            }
        }

        // Summary Bar
        if (healthResults.isNotEmpty() || isRunningDiagnostics) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MhBgSoft)
                        .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRunningDiagnostics) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.8.dp,
                                color = MhSpotifyGreen
                            )
                            Text(
                                text = "正在并发探测影视点播源、M3U8 切片流、电视直播与音乐源…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MhTextDim
                            )
                        }
                    } else {
                        Text(
                            text = "检测汇总：共 ${healthResults.size} 渠道",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MhText
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("正常 $healthyCount", fontSize = 11.5.sp, color = MhSpotifyGreen, fontWeight = FontWeight.SemiBold)
                            Text("缓慢 $degradedCount", fontSize = 11.5.sp, color = StatusAmber, fontWeight = FontWeight.SemiBold)
                            Text("失效 $offlineCount", fontSize = 11.5.sp, color = MhDanger, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // VOD Sources List with Deep Status
        item {
            Text(
                text = "影视点播源 (${vodSources.count { it.enabled }}/${vodSources.size} 启用)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MhText
            )
        }

        items(vodSources, key = { "vod_${it.id}" }) { src ->
            val report = healthResults.find { it.sourceId == src.id && it.channelType == "VOD" }
            val fastPing = MovieHubNetwork.sourceLatencyMap[src.id] ?: 0L
            val statusLabel = when (report?.status) {
                "HEALTHY" -> "正常极速"
                "DEGRADED" -> "响应较慢"
                "OFFLINE" -> "异常/超时"
                else -> if (fastPing in 1..2000) "在线 ${fastPing}ms" else if (fastPing >= 8500) "连接超时" else "待检测"
            }
            val statusColor = when (report?.status) {
                "HEALTHY" -> MhSpotifyGreen
                "DEGRADED" -> StatusAmber
                "OFFLINE" -> MhDanger
                else -> if (fastPing in 1..1200) MhSpotifyGreen else if (fastPing in 1201..3500) StatusAmber else MhTextDim
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MhBgSoft)
                    .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                    .clickable { onToggleVodSource(src.id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = src.enabled,
                    onCheckedChange = { onToggleVodSource(src.id) }
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${src.name} (${src.id})",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MhText
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }
                    Text(
                        text = src.api,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MhTextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (report != null) {
                        Text(
                            text = report.detailMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MhText.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Live & Music Channel Reports (if diagnostics have been run)
        val otherReports = healthResults.filter { it.channelType != "VOD" }
        if (otherReports.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "电视直播与音乐渠道抽样状态 (${otherReports.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MhText
                )
            }
            items(otherReports, key = { "${it.channelType}_${it.sourceId}" }) { rep ->
                val color = when (rep.status) {
                    "HEALTHY" -> MhSpotifyGreen
                    "DEGRADED" -> StatusAmber
                    else -> MhDanger
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MhBgSoft)
                        .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "[${rep.channelType}] ${rep.sourceName}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MhText
                            )
                            Text(
                                text = if (rep.status == "HEALTHY") "正常" else if (rep.status == "DEGRADED") "较慢" else "异常",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = color
                            )
                        }
                        Text(
                            text = rep.detailMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MhTextDim
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticHistoryLogsTab(
    logs: List<SourceDiagnosticLogEntity>,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "共记录 ${logs.size} 条渠道检测与同步日志",
                style = MaterialTheme.typography.labelMedium,
                color = MhTextDim
            )
            if (logs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MinimalPill(
                        text = "复制记录",
                        selected = false,
                        onClick = onCopyLogs
                    )
                    MinimalPill(
                        text = "清空记录",
                        selected = false,
                        onClick = onClearLogs
                    )
                }
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无历史记录，点击「1. 渠道状态检测 -> 一键全渠道深度检测」即可生成并持久化保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MhTextDim
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 36.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    val statusColor = when (log.status) {
                        "HEALTHY" -> MhSpotifyGreen
                        "DEGRADED" -> StatusAmber
                        else -> MhDanger
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MhBgSoft)
                            .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = "[${log.channelType}] ${log.sourceName}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MhText
                                )
                                Text(
                                    text = log.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            }
                            Text(
                                text = fmt.format(Date(log.checkedAt)),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MhTextDim
                            )
                        }
                        Text(
                            text = log.detailMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MhText.copy(alpha = 0.85f)
                        )
                        Text(
                            text = log.apiUrl,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MhTextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceQueryInspectorTab(
    vodSources: List<VodSource>,
    probeInspection: MovieHubNetwork.SourceProbeInspection?,
    isProbingCustomSource: Boolean,
    crossChannelQueryResult: MovieHubNetwork.MultiSearchResult?,
    isCrossChannelSearching: Boolean,
    onProbeCustomApi: (String, String) -> Unit,
    onAddProbedVodSource: (String, String, String) -> Unit,
    onRunCrossChannelSearch: (String) -> Unit,
    onCopyText: (String, String) -> Unit
) {
    var probeUrlInput by remember {
        mutableStateOf(vodSources.firstOrNull()?.api ?: "https://bfzyapi.com/api.php/provide/vod/")
    }
    var probeKeywordInput by remember { mutableStateOf("") }
    var crossSearchKeyword by remember { mutableStateOf("庆余年") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Method 1: Single CMS Source API Inspector
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MhBgSoft)
                    .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "方法一：苹果 CMS 采集站接口探测与验证",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MhText
                )
                Text(
                    text = "输入任意影视采集站 API 地址（或点击下方已有源快捷填入），自动检测其 JSON 格式、片库总量、线路标识与真实 M3U8 视频流可用性，并生成可放入远程 sources.json 的标准配置。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MhTextDim
                )

                // Quick Preset Chips from current sources
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vodSources.forEach { s ->
                        MinimalPill(
                            text = s.name,
                            selected = probeUrlInput == s.api,
                            onClick = { probeUrlInput = s.api }
                        )
                    }
                }

                // API URL Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MhBg)
                        .border(0.5.dp, MhBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (probeUrlInput.isEmpty()) {
                        Text(
                            text = "https://xxx.com/api.php/provide/vod/",
                            fontSize = 11.sp,
                            color = MhTextDim
                        )
                    }
                    BasicTextField(
                        value = probeUrlInput,
                        onValueChange = { probeUrlInput = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall.copy(
                            color = MhText,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(MhText),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Optional Keyword Filter + Probe Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MhBg)
                            .border(0.5.dp, MhBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        if (probeKeywordInput.isEmpty()) {
                            Text(
                                text = "可选：输入片名测试该源搜索（留空则测最新列表）",
                                fontSize = 11.sp,
                                color = MhTextDim
                            )
                        }
                        BasicTextField(
                            value = probeKeywordInput,
                            onValueChange = { probeKeywordInput = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall.copy(
                                color = MhText,
                                fontSize = 11.5.sp
                            ),
                            cursorBrush = SolidColor(MhText),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    MinimalPill(
                        text = if (isProbingCustomSource) "探测中…" else "🔍 探测该源",
                        selected = true,
                        onClick = {
                            if (!isProbingCustomSource) {
                                onProbeCustomApi(probeUrlInput, probeKeywordInput)
                            }
                        }
                    )
                }

                // Probe Result Display
                if (probeInspection != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = MhBorder)
                    val ok = probeInspection.isReachable && probeInspection.sampleItems.isNotEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (ok) {
                                "✅ 探测成功 · 片库 ${probeInspection.totalVodCount} 部 · 接口 ${probeInspection.apiLatencyMs}ms" +
                                    if (probeInspection.streamLatencyMs > 0) " · 视频流 ${probeInspection.streamLatencyMs}ms" else ""
                            } else {
                                "❌ ${probeInspection.errorMsg ?: "源不可用"}"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (ok) MhSpotifyGreen else MhDanger
                        )
                    }

                    if (probeInspection.suggestedJsonSnippet.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MinimalPill(
                                text = "📋 复制 sources.json 配置片段",
                                selected = false,
                                onClick = {
                                    onCopyText(
                                        probeInspection.suggestedJsonSnippet,
                                        "已复制该源的 JSON 配置片段，可直接粘贴至远程 sources.json"
                                    )
                                }
                            )
                            MinimalPill(
                                text = "➕ 加入当前可用源",
                                selected = true,
                                onClick = {
                                    val hostId = probeInspection.apiUrl
                                        .substringAfter("://")
                                        .substringBefore("/")
                                        .replace("www.", "")
                                        .substringBefore(".")
                                        .ifEmpty { "custom" }
                                    onAddProbedVodSource(
                                        hostId,
                                        "自定义_$hostId",
                                        probeInspection.apiUrl
                                    )
                                }
                            )
                        }
                    }

                    probeInspection.sampleItems.take(5).forEach { sample ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MhBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "《${sample.title}》 ${sample.remarks}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MhText
                                )
                                Text(
                                    text = sample.typeName,
                                    fontSize = 10.sp,
                                    color = MhTextDim
                                )
                            }
                            Text(
                                text = "线路：${sample.lineFlags}",
                                fontSize = 10.5.sp,
                                color = MhSpotifyGreen
                            )
                            if (sample.firstEpisodeUrl.isNotEmpty()) {
                                Text(
                                    text = "首集流：${sample.firstEpisodeUrl}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MhTextDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Method 2: Cross-Channel Penetration Query by Movie Title
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MhBgSoft)
                    .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "方法二：按片名全渠道穿透查源对比",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MhText
                )
                Text(
                    text = "输入任意影片名称，并发穿透查询所有已启用片源，直观对比哪些渠道收录了该片、各有多少集、以及各渠道响应速度。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MhTextDim
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MhBg)
                            .border(0.5.dp, MhBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = crossSearchKeyword,
                            onValueChange = { crossSearchKeyword = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall.copy(
                                color = MhText,
                                fontSize = 12.sp
                            ),
                            cursorBrush = SolidColor(MhText),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    MinimalPill(
                        text = if (isCrossChannelSearching) "穿透查询中…" else "⚡ 全渠道查源",
                        selected = true,
                        onClick = {
                            if (!isCrossChannelSearching && crossSearchKeyword.isNotBlank()) {
                                onRunCrossChannelSearch(crossSearchKeyword)
                            }
                        }
                    )
                }

                if (crossChannelQueryResult != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = MhBorder)
                    Text(
                        text = "关键词「${crossChannelQueryResult.keyword}」：命中 ${crossChannelQueryResult.hitSources}/${crossChannelQueryResult.sourceCount} 个源，共聚合 ${crossChannelQueryResult.total} 条目",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MhSpotifyGreen
                    )
                    crossChannelQueryResult.items.take(6).forEach { vod ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MhBg)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "《${vod.name}》 (${vod.sources.size} 个源命中)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MhText
                            )
                            vod.sources.forEach { m ->
                                val epTotal = m.lines.firstOrNull()?.episodes?.size ?: 0
                                val msText = if (m.latencyMs in 1..8500) "${m.latencyMs}ms" else "待测"
                                Text(
                                    text = "• [${m.sourceName}] ${m.remarks.ifEmpty { "共${epTotal}集" }} · 线路: ${m.lines.joinToString("/") { it.flag }} · 响应: $msText",
                                    fontSize = 11.sp,
                                    color = MhTextDim
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Offline Video Download Center Modal Sheet ("手机端视频下载中心").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloadCenterModalSheet(
    downloads: List<VideoDownloadEntity>,
    onDismiss: () -> Unit,
    onPlayOfflineTask: (VideoDownloadEntity) -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (VideoDownloadEntity) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearAllTasks: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MhBg,
        contentColor = MhText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "离线视频下载中心 (${downloads.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MhText
                    )
                    Text(
                        text = "支持 M3U8 多线程分片离线打包与 MP4 断点续传，无网也可秒播",
                        style = MaterialTheme.typography.labelSmall,
                        color = MhTextDim
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (downloads.isNotEmpty()) {
                        TextButton(onClick = onClearAllTasks) {
                            Text("清空全部", color = MhDanger, fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = MhTextDim)
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MhBorder)
            Spacer(modifier = Modifier.height(10.dp))

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MhTextDim,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "暂无离线缓存任务",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MhText
                        )
                        Text(
                            text = "在任意影片播放页点击「⬇ 下载缓存」即可将剧集缓存至手机本地",
                            style = MaterialTheme.typography.labelSmall,
                            color = MhTextDim
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 36.dp)
                ) {
                    items(downloads, key = { it.taskId }) { task ->
                        val isCompleted = task.status == "COMPLETED"
                        val isDownloading = task.status == "DOWNLOADING" || task.status == "QUEUED"
                        val isFailed = task.status == "FAILED"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MhBgSoft)
                                .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isCompleted) onPlayOfflineTask(task)
                                    else if (isDownloading) onPauseTask(task.taskId)
                                    else onResumeTask(task)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MhCard)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(6.dp))
                            ) {
                                if (task.vodPic.isNotEmpty()) {
                                    AsyncImage(
                                        model = task.vodPic,
                                        contentDescription = task.vodName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${task.vodName} · ${task.epTitle}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MhText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val sizeText = formatBytesSize(task.downloadedBytes)
                                val speedText = if (isDownloading && task.speedBytesPerSec > 0) {
                                    " · ${formatBytesSize(task.speedBytesPerSec)}/s"
                                } else ""

                                val statusDesc = when (task.status) {
                                    "COMPLETED" -> "✅ 已完成离线缓存 · $sizeText · 点击立即播放"
                                    "DOWNLOADING" -> "⬇ 正在缓存 (${task.downloadedSegments}/${task.totalSegments.coerceAtLeast(1)} 分片) · ${task.progressPercent}% · $sizeText$speedText"
                                    "PAUSED" -> "⏸ 已暂停 (${task.progressPercent}%) · 已下 $sizeText"
                                    "FAILED" -> "❌ 下载失败：${task.errorMessage.ifEmpty { "点击重试" }}"
                                    else -> "等待下载中…"
                                }

                                Text(
                                    text = statusDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        isCompleted -> MhSpotifyGreen
                                        isFailed -> MhDanger
                                        isDownloading -> MhText
                                        else -> MhTextDim
                                    },
                                    maxLines = 2
                                )

                                if (!isCompleted) {
                                    LinearProgressIndicator(
                                        progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = if (isFailed) MhDanger else MhSpotifyGreen,
                                        trackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        when {
                                            isCompleted -> onPlayOfflineTask(task)
                                            isDownloading -> onPauseTask(task.taskId)
                                            else -> onResumeTask(task)
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isCompleted -> Icons.Default.PlayArrow
                                            isDownloading -> Icons.Default.Pause
                                            else -> Icons.Default.Refresh
                                        },
                                        contentDescription = "操作",
                                        tint = if (isCompleted) MhSpotifyGreen else MhText
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteTask(task.taskId) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "删除",
                                        tint = MhTextDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog allowing the user to pick one or multiple episodes of the current movie to cache offline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeBatchDownloadDialog(
    vodName: String,
    sourceName: String,
    episodes: List<VodEpisode>,
    currentEpisodeIndex: Int,
    existingDownloads: List<VideoDownloadEntity>,
    sourceId: String,
    vodId: String,
    onDismiss: () -> Unit,
    onConfirmDownloadEpisodes: (List<Int>) -> Unit,
    onOpenDownloadCenter: () -> Unit
) {
    val selectedIndices = remember(episodes.size, currentEpisodeIndex) {
        mutableStateListOf<Int>().apply {
            if (currentEpisodeIndex in episodes.indices) add(currentEpisodeIndex)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MhCard,
        titleContentColor = MhText,
        textContentColor = MhText,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "选择剧集离线缓存",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "$vodName · 片源: $sourceName (共 ${episodes.size} 集)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MhTextDim
                    )
                }
                TextButton(onClick = onOpenDownloadCenter) {
                    Text("下载管理 ▸", color = MhSpotifyGreen, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinimalPill(
                        text = "全选 (${episodes.size})",
                        selected = selectedIndices.size == episodes.size,
                        onClick = {
                            selectedIndices.clear()
                            selectedIndices.addAll(episodes.indices)
                        }
                    )
                    MinimalPill(
                        text = "仅选当前集",
                        selected = selectedIndices.size == 1 && selectedIndices.contains(currentEpisodeIndex),
                        onClick = {
                            selectedIndices.clear()
                            if (currentEpisodeIndex in episodes.indices) {
                                selectedIndices.add(currentEpisodeIndex)
                            }
                        }
                    )
                    MinimalPill(
                        text = "清空选择",
                        selected = false,
                        onClick = { selectedIndices.clear() }
                    )
                }

                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chunked = episodes.mapIndexed { idx, ep -> idx to ep }.chunked(3)
                    items(chunked) { rowList ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowList.forEach { (epIdx, ep) ->
                                val isSelected = selectedIndices.contains(epIdx)
                                val alreadyTask = existingDownloads.find {
                                    it.sourceId == sourceId && it.vodId == vodId && it.epIndex == epIdx
                                }
                                val isDone = alreadyTask?.status == "COMPLETED"
                                val isBusy = alreadyTask?.status == "DOWNLOADING"

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isDone -> MhSpotifyGreen.copy(alpha = 0.18f)
                                                isSelected -> MhText
                                                else -> MhBgSoft
                                            }
                                        )
                                        .border(
                                            0.5.dp,
                                            when {
                                                isDone -> MhSpotifyGreen
                                                isSelected -> MhText
                                                else -> MhBorder
                                            },
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (isSelected) selectedIndices.remove(epIdx)
                                            else selectedIndices.add(epIdx)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when {
                                            isDone -> "✓ ${ep.title}"
                                            isBusy -> "⬇ ${ep.title}"
                                            else -> ep.title
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected || isDone) FontWeight.SemiBold else FontWeight.Normal,
                                        color = when {
                                            isDone -> MhSpotifyGreen
                                            isSelected -> MhBg
                                            else -> MhText
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            repeat(3 - rowList.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MhTextDim)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedIndices.isNotEmpty()) {
                        onConfirmDownloadEpisodes(selectedIndices.sorted())
                    }
                    onDismiss()
                }
            ) {
                Text(
                    text = "立即下载 (${selectedIndices.size})",
                    color = MhSpotifyGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

fun formatBytesSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

private fun buildDiagnosticReportText(
    vodSources: List<VodSource>,
    results: List<MovieHubNetwork.SourceHealthCheckResult>
): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return buildString {
        appendLine("# 观影中心 (MovieHub) 渠道状态检测报告")
        appendLine("生成时间：${fmt.format(Date())}")
        appendLine("启用影视源：${vodSources.count { it.enabled }}/${vodSources.size}")
        appendLine("----------------------------------------")
        results.forEach { r ->
            appendLine("[${r.channelType}] ${r.sourceName} (${r.sourceId}) -> ${r.status}")
            appendLine("  接口：${r.apiUrl}")
            appendLine("  详情：${r.detailMessage}")
        }
    }
}

private fun buildHistoryLogText(logs: List<SourceDiagnosticLogEntity>): String {
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return buildString {
        appendLine("# 观影中心 (MovieHub) 渠道检测历史情况记录 (${logs.size} 条)")
        logs.forEach { l ->
            appendLine("${fmt.format(Date(l.checkedAt))} [${l.channelType}] ${l.sourceName} (${l.status}) - ${l.detailMessage}")
        }
    }
}

/**
 * Full-screen Modal Bottom Sheet for Online App Update Check & Cloud Hot-Update ("检查更新与在线升级中心").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateCenterModalSheet(
    updateResult: MovieHubNetwork.AppUpdateCheckResult,
    isCheckingUpdate: Boolean,
    isPublishingToGitHub: Boolean = false,
    remoteSyncState: RemoteSyncUiState,
    onCheckUpdateNow: (String) -> Unit,
    onSyncRemoteSourcesNow: () -> Unit,
    onPublishSourcesToGitHub: (String, String) -> Unit = { _, _ -> },
    onPublishVersionToGitHub: (String, String, String, Int, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onShowToast: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customManifestUrl by remember {
        mutableStateOf(
            updateResult.checkedEndpoint.ifEmpty { MovieHubNetwork.DEFAULT_UPDATE_MANIFEST_URL }
        )
    }
    var githubToken by remember { mutableStateOf("") }
    var githubRepo by remember { mutableStateOf("berse1kguts-pixel/moviehub") }
    var targetVerName by remember { mutableStateOf("2.6.0") }
    var targetVerCode by remember { mutableStateOf("26") }
    var targetApkUrl by remember {
        mutableStateOf("https://github.com/berse1kguts-pixel/moviehub/releases/latest/download/moviehub-release.apk")
    }
    var targetChangelog by remember {
        mutableStateOf("1. 新增高清片源与短剧频道\n2. 优化播放器清晰度切换与手势体验")
    }

    fun copyAndToast(text: String, msg: String) {
        clipboard.setText(AnnotatedString(text))
        onShowToast(msg)
    }

    fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            copyAndToast(url, "已复制下载链接，请在浏览器打开")
        }
    }

    val sampleVersionJson = remember {
        """
        {
          "versionName": "2.6.0",
          "versionCode": 26,
          "forceUpdate": false,
          "title": "观影中心 v2.6.0 正式版",
          "publishedAt": "2026-11-10",
          "apkUrl": "https://github.com/berse1kguts-pixel/moviehub/releases/download/v2.6.0/moviehub-release.apk",
          "mirrorUrl": "https://ghp.ci/https://github.com/berse1kguts-pixel/moviehub/releases/download/v2.6.0/moviehub-release.apk",
          "releasePageUrl": "https://github.com/berse1kguts-pixel/moviehub/releases",
          "changelog": "1. 新增高清片源与短剧频道\n2. 优化播放器清晰度切换与手势体验\n3. 修复已知问题"
        }
        """.trimIndent()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MhBg,
        contentColor = MhText,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .background(MhBg)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "检查更新与在线升级中心",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MhText
                    )
                    Text(
                        text = "支持云端源免安装热更新 · GitHub Release / version.json 在线升级",
                        style = MaterialTheme.typography.labelSmall,
                        color = MhTextDim
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MhBgSoft)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MhText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MhBorder)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // 1. Current Version & Check Result Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MhBgSoft)
                            .border(
                                0.8.dp,
                                if (updateResult.hasNewAppVersion) MhSpotifyGreen else MhBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "当前版本 v${updateResult.currentVersionName}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MhText
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (updateResult.hasNewAppVersion) MhSpotifyGreen.copy(alpha = 0.2f)
                                                else MhCardHover
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (updateResult.hasNewAppVersion) {
                                                "🎉 发现新版本 v${updateResult.latestVersionName}"
                                            } else {
                                                "✓ 已是最新"
                                            },
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (updateResult.hasNewAppVersion) MhSpotifyGreen else MhTextDim
                                        )
                                    }
                                }
                                Text(
                                    text = updateResult.statusMessage,
                                    fontSize = 11.5.sp,
                                    color = if (updateResult.hasNewAppVersion) MhSpotifyGreen else MhTextDim
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MhText)
                                    .clickable(enabled = !isCheckingUpdate) {
                                        onCheckUpdateNow(customManifestUrl)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("trigger_check_update_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCheckingUpdate) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = MhBg,
                                            strokeWidth = 1.8.dp,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "检测中…",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MhBg
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "🔄 立即检查更新",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MhBg
                                    )
                                }
                            }
                        }

                        if (updateResult.releaseNotes.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MhCard)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${updateResult.releaseTitle} · 版本说明",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MhText
                                )
                                Text(
                                    text = updateResult.releaseNotes,
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp,
                                    color = MhTextDim
                                )
                            }
                        }

                        // Online Update Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MhSpotifyGreen)
                                    .clickable {
                                        openExternalUrl(updateResult.mirrorDownloadUrl)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (updateResult.hasNewAppVersion) "🚀 极速下载新版 APK" else "📦 打开 Release 下载页",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MhCardHover)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSyncRemoteSourcesNow()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚡ 免安装热更新片源",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MhText
                                )
                            }
                        }
                    }
                }

                // 2. Custom Update Manifest URL Configuration
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MhBgSoft)
                            .border(0.5.dp, MhBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "在线更新检测源地址 (version.json / GitHub Release)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MhText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MhCard)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                BasicTextField(
                                    value = customManifestUrl,
                                    onValueChange = { customManifestUrl = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MhText
                                    ),
                                    cursorBrush = SolidColor(MhSpotifyGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            MinimalPill(
                                text = "重置默认",
                                selected = false,
                                onClick = {
                                    customManifestUrl = MovieHubNetwork.DEFAULT_UPDATE_MANIFEST_URL
                                    onCheckUpdateNow(MovieHubNetwork.DEFAULT_UPDATE_MANIFEST_URL)
                                }
                            )
                        }
                    }
                }

                // 2.5 In-App Direct GitHub Publisher (Push sources.json & version.json from phone)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MhBgSoft)
                            .border(0.8.dp, MhSpotifyGreen.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "☁️ 手机端一键上传到 GitHub（免电脑远程发布器）",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MhSpotifyGreen
                        )
                        Text(
                            text = "填入你的 GitHub Personal Access Token (ghp_...)，即可直接在手机端把当前所有片源/直播源或新版本号一键直推到 GitHub 仓库：",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MhTextDim
                        )

                        // Repo + Token inputs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(0.45f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MhCard)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                BasicTextField(
                                    value = githubRepo,
                                    onValueChange = { githubRepo = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MhText
                                    ),
                                    cursorBrush = SolidColor(MhSpotifyGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(0.55f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MhCard)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                if (githubToken.isEmpty()) {
                                    Text(
                                        text = "粘贴 GitHub Token (ghp_...)",
                                        fontSize = 11.sp,
                                        color = MhTextDim
                                    )
                                }
                                BasicTextField(
                                    value = githubToken,
                                    onValueChange = { githubToken = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MhText
                                    ),
                                    cursorBrush = SolidColor(MhSpotifyGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Version Name + Version Code + APK URL inputs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(0.35f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MhCard)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                BasicTextField(
                                    value = targetVerName,
                                    onValueChange = { targetVerName = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = MhText
                                    ),
                                    cursorBrush = SolidColor(MhSpotifyGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(0.25f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MhCard)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                BasicTextField(
                                    value = targetVerCode,
                                    onValueChange = { targetVerCode = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = MhText
                                    ),
                                    cursorBrush = SolidColor(MhSpotifyGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(0.4f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MhCard)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                BasicTextField(
                                    value = targetChangelog,
                                    onValueChange = { targetChangelog = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = MhText
                                    ),
                                    cursorBrush = SolidColor(MhSpotifyGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MhSpotifyGreen)
                                    .clickable(enabled = !isPublishingToGitHub) {
                                        onPublishSourcesToGitHub(githubToken, githubRepo)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPublishingToGitHub) "上传中…" else "📤 一键上传当前片源到 GitHub",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MhCardHover)
                                    .border(0.5.dp, MhBorder, RoundedCornerShape(10.dp))
                                    .clickable(enabled = !isPublishingToGitHub) {
                                        onPublishVersionToGitHub(
                                            githubToken,
                                            githubRepo,
                                            targetVerName,
                                            targetVerCode.toIntOrNull() ?: 26,
                                            targetApkUrl,
                                            targetChangelog
                                        )
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPublishingToGitHub) "发布中…" else "🚀 一键发布 version.json",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MhText
                                )
                            }
                        }
                    }
                }

                // 3. Detailed Guide: How Online Update Works & version.json Template
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MhBgSoft)
                            .border(0.5.dp, MhBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 软件如何实现在线更新？（双层更新机制）",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MhText
                            )
                            MinimalPill(
                                text = "📋 复制 version.json 模板",
                                selected = true,
                                onClick = {
                                    copyAndToast(sampleVersionJson, "已复制 version.json 模板，可提交到 GitHub 仓库根目录")
                                }
                            )
                        }

                        Text(
                            text = "① 日常更新片源/直播源/音源（免重装云端热更新）：\n" +
                                "• 你只需在 GitHub 仓库 (berse1kguts-pixel/moviehub) 修改 moviehub/sources.json 并提交。\n" +
                                "• 手机端每次打开软件或点击「免安装热更新片源」会自动同步最新源并测速排序，完全不需要重新打包或重装 APK！\n\n" +
                                "② 升级软件界面/播放器功能（客户端 APK 在线更新）：\n" +
                                "• 当修改了 Kotlin 代码并导出新 APK 后，你只需在 GitHub 仓库点击「Releases -> Draft a new release」，填上新版本号（如 v2.6.0）并上传 APK 文件；或者在仓库根目录放一个 version.json。\n" +
                                "• 用户在软件内点击「立即检查更新」，软件会自动对比版本号并弹出新版本说明，点击「极速下载新版 APK」即可通过镜像加速下载并覆盖安装（保留全部收藏、历史记录与离线缓存视频）。",
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            color = MhTextDim
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MhCard)
                                .border(0.5.dp, MhBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = sampleVersionJson,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp,
                                color = MhSpotifyGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

