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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveTvChannel
import com.example.data.network.LocalMediaProxyServer
import com.example.ui.components.MinimalPill
import com.example.ui.theme.MhBg
import com.example.ui.theme.MhBgSoft
import com.example.ui.theme.MhBorder
import com.example.ui.theme.MhCard
import com.example.ui.theme.MhCardHover
import com.example.ui.theme.MhMint
import com.example.ui.theme.MhText
import com.example.ui.theme.MhTextDim

@Composable
fun LiveTvScreen(
    channels: List<LiveTvChannel>,
    selectedGroup: String,
    activeChannel: LiveTvChannel?,
    onSelectGroup: (String) -> Unit,
    onPlayChannel: (LiveTvChannel) -> Unit,
    onClosePlayer: () -> Unit,
    onAddChannel: (String, String, String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val groups = remember(channels) {
        listOf("全部") + channels.map { it.group }.distinct()
    }
    val filteredChannels = remember(channels, selectedGroup) {
        if (selectedGroup == "全部") channels else channels.filter { it.group == selectedGroup }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            onClosePlayer()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MhBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "电视直播",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MhText
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MhBgSoft)
                        .border(0.5.dp, MhBorder, RoundedCornerShape(999.dp))
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("add_live_channel_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MhText, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("自定义源", style = MaterialTheme.typography.labelMedium, color = MhText)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groups.forEach { grp ->
                    MinimalPill(
                        text = grp,
                        selected = selectedGroup == grp,
                        onClick = { onSelectGroup(grp) }
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MhBorder)

        if (activeChannel != null) {
            val proxiedUrl = remember(activeChannel.url) {
                LocalMediaProxyServer.getVideoProxyUrl(activeChannel.url)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MhBgSoft)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MhMint)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeChannel.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MhText,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClosePlayer, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = MhTextDim, modifier = Modifier.size(16.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    NativeExoHlsVideoPlayer(
                        proxiedUrl = proxiedUrl,
                        rawUrl = activeChannel.url,
                        resumePositionSec = 0L,
                        playbackSpeed = 1.0f,
                        onNextEpisode = {},
                        onProgressUpdate = { _, _ -> },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = MhBorder)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
        ) {
            items(filteredChannels, key = { it.id }) { channel ->
                val isPlaying = activeChannel?.id == channel.id
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPlaying) MhCardHover else MhBg)
                            .clickable { onPlayChannel(channel) }
                            .padding(horizontal = 10.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channel.group,
                            style = MaterialTheme.typography.labelMedium,
                            color = MhTextDim,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MhBgSoft)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isPlaying) MhText else MhText.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = if (isPlaying) MhMint else MhTextDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MhBorder.copy(alpha = 0.5f))
                }
            }
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var group by remember { mutableStateOf("自定义") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = MhCard,
            titleContentColor = MhText,
            textContentColor = MhText,
            title = { Text("添加自定义直播源") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("频道名称") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = group,
                        onValueChange = { group = it },
                        label = { Text("分组") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("M3U8 地址") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddChannel(name, group, url)
                        showAddDialog = false
                    }
                ) {
                    Text("保存", color = MhText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消", color = MhTextDim)
                }
            }
        )
    }
}
