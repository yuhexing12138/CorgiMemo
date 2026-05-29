package com.corgimemo.app.ui.screens.date

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
import com.corgimemo.app.ui.screens.date.components.DateGroupHeader
import com.corgimemo.app.ui.screens.date.components.SpecialDateCard
import com.corgimemo.app.viewmodel.GroupType
import com.corgimemo.app.viewmodel.SpecialDateViewModel

/**
 * 特殊日期列表页面（统一版）
 *
 * 展示所有特殊日期的分组列表，支持搜索和类型筛选。
 * 顶部导航栏和侧滑导航栏由 MainScreen 统一管理。
 *
 * 功能说明：
 * - 搜索栏：使用共享 SearchBar 组件（暖橙色背景）
 * - 分组列表：按类型分组（即将到来/正在纪念/已过期）
 * - FAB按钮：跳转到日期编辑页（统一铅笔图标）
 * - 空状态：使用 UnifiedEmptyState 统一空状态组件（含柯基动画）
 *
 * @param navController 导航控制器，用于页面跳转
 * @param onFabClick FAB按钮点击回调（由 MainScreen 传入）
 * @param corgiData 柯基数据（由 MainScreen 传入，用于空状态柯基动画）
 * @param currentPose 柯基当前姿态
 * @param currentMood 柯基当前情绪
 * @param viewModel 日期视图模型（通过 Hilt 自动注入）
 */
@Composable
fun SpecialDateScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    corgiData: CorgiData? = null,
    currentPose: CorgiPose = com.corgimemo.app.animation.PoseManager.getDefaultPose(),
    currentMood: CorgiMood = CorgiMood.NORMAL,
    viewModel: SpecialDateViewModel = hiltViewModel()
) {
    val groupedDates by viewModel.groupedDates.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onClear = { viewModel.updateSearchQuery("") },
                placeholder = "搜索日期...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (groupedDates.isEmpty()) {
                UnifiedEmptyState(
                    icon = "📅",
                    title = "还没有特殊日期~",
                    subtitle = "记录重要的日子，不错过每个纪念！",
                    ctaText = "📅 添加日期",
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
                    GroupType.values().forEach { groupType ->
                        val datesInGroup = groupedDates[groupType] ?: emptyList()
                        if (datesInGroup.isNotEmpty()) {
                            item { DateGroupHeader(groupType = groupType) }
                            items(datesInGroup, key = { "date_${it.id}" }) { date ->
                                SpecialDateCard(
                                    date = date,
                                    onClick = { navController.navigate("date_edit/${date.id}") },
                                    onLongClick = {}
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }
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
                contentDescription = "添加日期",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
