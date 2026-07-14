package com.corgimemo.app.ui.screens.date.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.viewmodel.DateCategoryStat
import com.corgimemo.app.viewmodel.DateGroupStat
import com.corgimemo.app.viewmodel.DateStatsViewModel
import com.corgimemo.app.viewmodel.DateGroup

/**
 * 日期数据统计页面
 *
 * 展示：
 * - Card 1: 分组占比（3 个环形进度图：倒计时 / 正计时 / 已归档）
 * - Card 2: 分类占比（横向条形图，各 DateCategory 的数量和占比）
 *
 * 数据范围：全部日期（含已归档）。
 * 入口：日期页 TopBar BarChart 图标。
 *
 * @param navController 导航控制器
 * @param viewModel 统计 ViewModel，通过 hiltViewModel() 注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateStatsScreen(
    navController: NavController,
    viewModel: DateStatsViewModel = hiltViewModel()
) {
    val isEmpty by viewModel.isEmpty.collectAsState()
    val stats by viewModel.stats.collectAsState()

    // 返回处理：通知 MainScreen 切回 DATE tab
    val handleBack: () -> Unit = {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set("targetTab", TabItem.DATE.name)
        navController.popBackStack()
    }
    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "数据统计",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isEmpty) {
                EmptyState(
                    onCreateClick = {
                        navController.navigate(Screen.SpecialDateQuickCreate.route)
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card 1: 分组占比（3 个环形进度图）
                    StatsCard(
                        title = "日期分组",
                        value = "${stats.totalCount} 个"
                    ) {
                        GroupRingChart(groupStats = stats.groupStats)
                    }

                    // Card 2: 分类占比（横向条形图）
                    StatsCard(
                        title = "日期分类",
                        value = null
                    ) {
                        CategoryBarChart(categoryStats = stats.categoryStats)
                    }
                }
            }
        }
    }
}

// ==================== 组件实现 ====================

/**
 * 统计卡片容器
 *
 * @param title 卡片标题
 * @param value 标题右侧数值（如 "24 个"），null 时不显示
 * @param chart 图表内容
 */
@Composable
private fun StatsCard(
    title: String,
    value: String?,
    chart: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (value != null) {
                        Text(
                            text = "：$value",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            chart()
        }
    }
}

/**
 * 分组环形进度图（3 个环形横向排列）
 *
 * @param groupStats 分组统计列表
 */
@Composable
private fun GroupRingChart(groupStats: List<DateGroupStat>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        groupStats.forEach { stat ->
            GroupRingItem(stat = stat)
        }
    }
}

/**
 * 单个分组环形进度图
 *
 * @param stat 分组统计项
 */
@Composable
private fun GroupRingItem(stat: DateGroupStat) {
    val color = when (stat.group) {
        DateGroup.COUNTDOWN -> Color(0xFF42A5F5)
        DateGroup.COUNTUP -> Color(0xFF66BB6A)
        DateGroup.EXPIRED -> Color(0xFF9E9E9E)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { stat.percentage },
                modifier = Modifier.size(80.dp),
                color = color,
                strokeWidth = 6.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(stat.percentage * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stat.count}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stat.displayName,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 分类横向条形图列表
 *
 * @param categoryStats 分类统计列表
 */
@Composable
private fun CategoryBarChart(categoryStats: List<DateCategoryStat>) {
    if (categoryStats.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无分类数据",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        categoryStats.forEach { stat ->
            CategoryBarItem(stat = stat)
        }
    }
}

/**
 * 单行分类条形项
 *
 * @param stat 分类统计项
 */
@Composable
private fun CategoryBarItem(stat: DateCategoryStat) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stat.emoji} ${stat.displayName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(stat.percentage * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 条形轨道
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterStart
        ) {
            // 条形填充
            Box(
                modifier = Modifier
                    .fillMaxWidth(stat.percentage.coerceIn(0f, 1f))
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(stat.color, stat.color.copy(alpha = 0.7f))
                        )
                    )
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (stat.percentage > 0.15f) {
                    Text(
                        text = "${stat.count}",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 空状态视图
 *
 * @param onCreateClick "去添加日期"按钮的点击回调
 */
@Composable
private fun EmptyState(onCreateClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🐶", fontSize = 64.sp)
            Text(
                text = "还没有日期记录，无法统计数据",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
            Button(onClick = onCreateClick) {
                Text(text = "去添加日期")
            }
        }
    }
}
