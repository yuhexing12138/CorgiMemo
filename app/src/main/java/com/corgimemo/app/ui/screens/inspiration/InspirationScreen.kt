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
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.CorgiPose
import com.corgimemo.app.data.model.CorgiData
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
 * - 空状态：使用 UnifiedEmptyState 统一空状态组件（含柯基动画）
 *
 * @param navController 导航控制器，用于页面跳转
 * @param onFabClick FAB按钮点击回调（由 MainScreen 传入）
 * @param corgiData 柯基数据（由 MainScreen 传入，用于空状态柯基动画）
 * @param currentPose 柯基当前姿态
 * @param currentMood 柯基当前情绪
 * @param viewModel 灵感视图模型（通过 Hilt 自动注入）
 */
@Composable
fun InspirationScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    corgiData: CorgiData? = null,
    currentPose: CorgiPose = com.corgimemo.app.animation.PoseManager.getDefaultPose(),
    currentMood: CorgiMood = CorgiMood.NORMAL,
    viewModel: InspirationViewModel = hiltViewModel()
) {
    val groupedInspirations by viewModel.groupedInspirations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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

            if (groupedInspirations.isEmpty()) {
                UnifiedEmptyState(
                    icon = "💡",
                    title = "还没有灵感记录~",
                    subtitle = "点击下方按钮记录你的第一个灵感吧！",
                    ctaText = "💡 记录灵感",
                    onCtaClick = onFabClick,
                    corgiData = corgiData,
                    currentPose = currentPose,
                    currentMood = currentMood,
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
