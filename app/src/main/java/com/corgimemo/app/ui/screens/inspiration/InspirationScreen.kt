package com.corgimemo.app.ui.screens.inspiration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.components.SearchBar
import com.corgimemo.app.ui.components.UnifiedEmptyState
import com.corgimemo.app.ui.screens.inspiration.components.InspirationLongPressSheet
import com.corgimemo.app.ui.screens.inspiration.components.TimelineInspirationItem
import com.corgimemo.app.viewmodel.InspirationViewModel
import java.util.Calendar

/**
 * 灵感记录列表页面（时间线版）
 *
 * 展示所有灵感记录的时间线列表，支持搜索、分组展示和快速添加功能。
 * 顶部导航栏和侧滑导航栏由 MainScreen 统一管理。
 *
 * 功能说明：
 * - 搜索栏：支持关键词实时搜索灵感
 * - 时间线布局：左侧日期列 + 右侧内容区
 * - 置顶区域：置顶灵感显示在最顶部
 * - 长按操作：置顶/标签/改日期/删除
 * - 日历弹窗：从顶部日期点击展开，查看每天的灵感
 * - FAB按钮：跳转到灵感编辑页
 *
 * @param navController 导航控制器，用于页面跳转
 * @param onFabClick FAB按钮点击回调（由 MainScreen 传入）
 * @param viewModel 灵感视图模型（通过 Hilt 自动注入）
 */
@Composable
fun InspirationScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    viewModel: InspirationViewModel = hiltViewModel()
) {
    val pinnedInspirations by viewModel.pinnedInspirations.collectAsState()
    val normalGroupedInspirations by viewModel.normalGroupedInspirations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDataInitialized by viewModel.isDataInitialized.collectAsState()

    // 弹窗状态
    var showLongPressSheet by remember { mutableStateOf(false) }
    var longPressedInspiration by remember { mutableStateOf<Inspiration?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索框（保留现有）
            SearchBar(
                query = searchQuery,
                onQueryChange = { newQuery ->
                    viewModel.search(newQuery)
                },
                onClear = {
                    viewModel.clearSearch()
                },
                placeholder = "搜索灵感...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = dimensionResource(com.corgimemo.app.R.dimen.ui_search_bar_bottom_margin))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 内容区域
            if (!isDataInitialized) {
                InspirationSkeleton(groupCount = 1, itemsPerGroup = 2)
            } else if (pinnedInspirations.isEmpty() && normalGroupedInspirations.isEmpty()) {
                UnifiedEmptyState(
                    icon = "💡",
                    title = "还没有灵感记录~",
                    subtitle = "点击右下角按钮记录你的第一个灵感吧！",
                    ctaText = "💡 记录灵感",
                    onCtaClick = onFabClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // ===== 置顶区域 =====
                    if (pinnedInspirations.isNotEmpty()) {
                        items(
                            items = pinnedInspirations,
                            key = { "pinned_${it.id}" }
                        ) { inspiration ->
                            val tags = viewModel.decodeTags(inspiration.tags)
                            val imagePaths = viewModel.decodePaths(inspiration.imagePaths)
                            val formattedTime = viewModel.formatTime(inspiration.createdAt)
                            val showDate = pinnedInspirations.indexOf(inspiration) == 0

                            TimelineInspirationItem(
                                inspiration = inspiration,
                                tags = tags,
                                imagePaths = imagePaths,
                                formattedTime = formattedTime,
                                showDate = showDate,
                                isPinnedItem = true,
                                onClick = {
                                    navController.navigate("inspiration_edit/${inspiration.id}")
                                },
                                onLongClick = {
                                    longPressedInspiration = inspiration
                                    showLongPressSheet = true
                                }
                            )
                        }
                    }

                    // ===== 普通灵感（按年月分组）=====
                    normalGroupedInspirations.forEach { (yearMonth, inspirationsInGroup) ->
                        // 按日期分组（同一年月内按日期分组显示）
                        val dayGroups = inspirationsInGroup.groupBy {
                            val cal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
                            cal.get(Calendar.DAY_OF_MONTH)
                        }.toSortedMap(reverseOrder())

                        dayGroups.forEach { (day, dayInspirations) ->
                            items(
                                items = dayInspirations,
                                key = { "inspiration_${it.id}" }
                            ) { inspiration ->
                                val tags = viewModel.decodeTags(inspiration.tags)
                                val imagePaths = viewModel.decodePaths(inspiration.imagePaths)
                                val formattedTime = viewModel.formatTime(inspiration.createdAt)
                                val isFirstOfDay = dayInspirations.indexOf(inspiration) == 0

                                TimelineInspirationItem(
                                    inspiration = inspiration,
                                    tags = tags,
                                    imagePaths = imagePaths,
                                    formattedTime = formattedTime,
                                    showDate = isFirstOfDay,
                                    isPinnedItem = false,
                                    onClick = {
                                        navController.navigate("inspiration_edit/${inspiration.id}")
                                    },
                                    onLongClick = {
                                        longPressedInspiration = inspiration
                                        showLongPressSheet = true
                                    }
                                )
                            }
                        }
                    }

                    // 底部留白
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // 加载指示器
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // FAB 按钮
        FloatingActionButton(
            onClick = onFabClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "记录灵感",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // 长按操作面板
    if (showLongPressSheet && longPressedInspiration != null) {
        InspirationLongPressSheet(
            isPinned = longPressedInspiration!!.isPinned,
            onPinClick = {
                viewModel.togglePin(longPressedInspiration!!.id)
                showLongPressSheet = false
                longPressedInspiration = null
            },
            onTagClick = {
                // 标签管理功能后续实现
                showLongPressSheet = false
                longPressedInspiration = null
            },
            onDateClick = {
                showDateTimePicker = true
                showLongPressSheet = false
            },
            onDeleteClick = {
                showDeleteConfirm = true
                showLongPressSheet = false
            },
            onDismiss = {
                showLongPressSheet = false
                longPressedInspiration = null
            }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirm && longPressedInspiration != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条灵感吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteInspiration(longPressedInspiration!!.id)
                    showDeleteConfirm = false
                    longPressedInspiration = null
                }) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
