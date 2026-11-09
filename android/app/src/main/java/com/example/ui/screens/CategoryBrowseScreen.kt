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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MovieHubData
import com.example.data.model.VodItem
import com.example.data.model.VodSource
import com.example.ui.CategoryUiState
import com.example.ui.components.MinimalPill
import com.example.ui.components.VodPosterCard
import com.example.ui.theme.MhBg
import com.example.ui.theme.MhBgSoft
import com.example.ui.theme.MhBorder
import com.example.ui.theme.MhCard
import com.example.ui.theme.MhCardHover
import com.example.ui.theme.MhText
import com.example.ui.theme.MhTextDim

@Composable
fun CategoryBrowseScreen(
    state: CategoryUiState,
    vodSources: List<VodSource>,
    onSelectSource: (String, Boolean) -> Unit,
    onSelectType: (String, String) -> Unit,
    onSelectYear: (String) -> Unit,
    onSelectSort: (String) -> Unit,
    onChangePage: (Int) -> Unit,
    onOpenVodItem: (VodItem) -> Unit
) {
    var showSourceDropdown by remember { mutableStateOf(false) }
    val activeSourceName = vodSources.find { it.id == state.selectedSourceId }?.name ?: "百度资源"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MhBg)
    ) {
        // Minimalist Filter Header
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
                    text = "分类片库",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MhText
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (state.total > 0) "${state.selectedTypeName} · ${state.total}" else state.selectedTypeName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MhTextDim
                )
                Spacer(modifier = Modifier.weight(1f))

                Box {
                    Text(
                        text = "$activeSourceName ▾",
                        style = MaterialTheme.typography.labelMedium,
                        color = MhText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MhBgSoft)
                            .border(0.5.dp, MhBorder, RoundedCornerShape(999.dp))
                            .clickable { showSourceDropdown = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("category_source_dropdown")
                    )
                    DropdownMenu(
                        expanded = showSourceDropdown,
                        onDismissRequest = { showSourceDropdown = false },
                        modifier = Modifier.background(MhCard)
                    ) {
                        vodSources.filter { it.enabled }.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name, color = MhText, fontSize = 13.sp) },
                                onClick = {
                                    showSourceDropdown = false
                                    onSelectSource(s.id, false)
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onSelectSource(state.selectedSourceId, true) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新分类树",
                        tint = MhTextDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Category Tree Pills
            val tree = state.tree
            if (state.isLoadingTree) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = MhTextDim, modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                    Text("正在发现分类树…", style = MaterialTheme.typography.labelMedium, color = MhTextDim)
                }
            } else if (tree != null) {
                val allNodes = buildList {
                    tree.groups.forEach { addAll(it.items) }
                    addAll(tree.top)
                }
                if (allNodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allNodes.forEach { node ->
                            MinimalPill(
                                text = node.name,
                                selected = state.selectedTypeId == node.id.toString(),
                                onClick = { onSelectType(node.id.toString(), node.name) }
                            )
                        }
                    }
                }
            }

            // Sort & Year Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MovieHubData.CATEGORY_ORDERS.forEach { (code, label) ->
                    val active = state.selectedSortBy == code
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (active) MhText else MhTextDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) MhCardHover else MhBg)
                            .clickable { onSelectSort(code) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(12.dp)
                        .background(MhBorder)
                )

                MovieHubData.CATEGORY_YEARS.forEach { yr ->
                    val active = state.selectedYear == yr
                    Text(
                        text = yr.ifEmpty { "全部年份" },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (active) MhText else MhTextDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) MhCardHover else MhBg)
                            .clickable { onSelectYear(yr) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MhBorder)

        when {
            state.isLoadingItems -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MhTextDim,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            state.itemsError != null && state.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.itemsError, style = MaterialTheme.typography.bodyMedium, color = MhTextDim)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { onSelectSource(state.selectedSourceId, false) }) {
                            Text("重试", color = MhText)
                        }
                    }
                }
            }
            else -> {
                val rows = state.items.chunked(3)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
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
                                    subtitle = item.remarks.ifEmpty { item.year },
                                    badgeText = item.typeName,
                                    onClick = { onOpenVodItem(item) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (state.pageCount > 1) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MinimalPill(
                                    text = "上一页",
                                    selected = false,
                                    onClick = { if (state.page > 1) onChangePage(state.page - 1) }
                                )
                                Text(
                                    text = "${state.page} / ${state.pageCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MhTextDim,
                                    modifier = Modifier.padding(horizontal = 18.dp)
                                )
                                MinimalPill(
                                    text = "下一页",
                                    selected = true,
                                    onClick = { if (state.page < state.pageCount) onChangePage(state.page + 1) }
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }
}
