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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.ui.components.SearchBar
import com.corgimemo.app.ui.components.UnifiedEmptyState
import com.corgimemo.app.ui.screens.inspiration.components.InspirationCard
import com.corgimemo.app.ui.screens.inspiration.components.TimelineGroupHeader
import com.corgimemo.app.viewmodel.InspirationViewModel

/**
 * 灵感记录列表页面（统一版）
 *
 * 展示所有灵感记录的时间线列表，支持搜索、分组展示和快速添加功能。
 * 顶部导航栏和侧滑导航栏由 MainScreen 统一管理。
 *
 * 功能说明：
 * - 搜索栏：支持关键词实时搜索灵感
 * - 时间线分组：按创建日期分组显示灵感卡片
 * - FAB按钮：跳转到灵感编辑页（统一铅笔图标）
 * - 空状态：使用 UnifiedEmptyState 统一空状态组件（含柯基帧动画）
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
    val groupedInspirations by viewModel.groupedInspirations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    /** 数据是否已完成首次加载（用于区分"正在加载"和"确实为空"） */
    val isDataInitialized by viewModel.isDataInitialized.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            /**
             * 内容区域显示逻辑：
             * 1. 数据未初始化 → 显示加载指示器（避免闪烁）
             * 2. 数据已初始化 + 列表为空 → 显示空状态
             * 3. 数据已初始化 + 列表有内容 → 显示列表
             */
            if (!isDataInitialized) {
                // 数据未初始化：显示页面专属骨架屏，避免从空列表闪烁到有数据
                InspirationSkeleton(groupCount = 1, itemsPerGroup = 2)
            } else if (groupedInspirations.isEmpty()) {
                UnifiedEmptyState(
                    icon = "💡",
                    title = "还没有灵感记录~",
                    subtitle = "点击下方按钮记录你的第一个灵感吧！",
                    ctaText = "💡 记录灵感",
                    onCtaClick = onFabClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedInspirations.forEach { (dateKey, inspirationsInGroup) ->
                        item(key = "header_$dateKey") {
                            TimelineGroupHeader(dateText = dateKey)
                        }

                        items(
                            items = inspirationsInGroup,
                            key = { inspiration -> "inspiration_${inspiration.id}" }
                        ) { inspiration ->
                            val tags = viewModel.decodeTags(inspiration.tags)
                            val imagePaths = viewModel.decodePaths(inspiration.imagePaths)
                            val formattedTime = viewModel.formatTime(inspiration.createdAt)

                            InspirationCard(
                                inspiration = inspiration,
                                tags = tags,
                                imagePaths = imagePaths,
                                formattedTime = formattedTime,
                                relationHint = null,
                                onClick = {
                                    navController.navigate("inspiration_edit/${inspiration.id}")
                                },
                                onLongClick = {}
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        FloatingActionButton(
            onClick = onFabClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                /** 小间距即可：父容器 Column 已通过 paddingValues 避开导航栏区域 */
                .padding(end = 20.dp, bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "记录灵感",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
