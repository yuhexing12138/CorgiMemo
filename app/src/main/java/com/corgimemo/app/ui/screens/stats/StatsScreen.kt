package com.corgimemo.app.ui.screens.stats

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.screens.inspiration.stats.BarChart
import com.corgimemo.app.ui.screens.inspiration.stats.LineChart
import com.corgimemo.app.viewmodel.CategoryStat
import com.corgimemo.app.viewmodel.StatsViewModel

/**
 * 待办数据统计页面（精简版）
 *
 * 参考灵感页 InspirationStatsScreen 的单页卡片式布局：
 * - 4 张 StatsCard 图表卡片纵向排列
 * - 每张卡片 = 标题 + 右上角展开按钮 + 图表内容
 * - 展开按钮跳转至横屏全屏 30 天视图
 *
 * @param navController 导航控制器
 * @param viewModel 统计 ViewModel，通过 hiltViewModel() 注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel()
) {
    // 收集 ViewModel 状态
    val isEmpty by viewModel.isEmpty.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val todoLineChartData by viewModel.todoLineChartData.collectAsState()
    val todoBarChartData by viewModel.todoBarChartData.collectAsState()
    val totalCompleted by viewModel.totalCompleted.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val monthCompletionRate by viewModel.monthCompletionRate.collectAsState()
    val categoryStats30d by viewModel.categoryStats30d.collectAsState()
    val completionRate30d by viewModel.completionRate30d.collectAsState()

    // 第③④张卡片的展开/收起状态
    var categoryExpanded by remember { mutableStateOf(false) }
    var completionExpanded by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { navController.popBackStack() }) {
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
            // 空状态
            if (isEmpty && !isLoading) {
                EmptyState(
                    onCreateClick = { navController.navigate("todo_edit") }
                )
            } else {
                // 主内容：4 张图表卡片
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ① 累计完成数（折线图）
                    StatsCard(
                        title = "累计完成数",
                        value = "$totalCompleted 个",
                        onExpand = {
                            navController.navigate(Screen.ChartFullscreen.createRoute("todo_line"))
                        }
                    ) {
                        LineChart(points = todoLineChartData.points)
                    }

                    // ② 每日完成数（柱状图）
                    StatsCard(
                        title = "每日完成数",
                        value = null,
                        onExpand = {
                            navController.navigate(Screen.ChartFullscreen.createRoute("todo_bar"))
                        }
                    ) {
                        BarChart(points = todoBarChartData.points)
                    }

                    // ③ 分类完成率（横向条形图，就地展开/收起 7天↔30天）
                    StatsCard(
                        title = if (categoryExpanded) "分类完成率（30天）" else "分类完成率（7天）",
                        value = null,
                        onExpand = { categoryExpanded = !categoryExpanded },
                        expandIcon = if (categoryExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                        expandDescription = if (categoryExpanded) "收起" else "展开30天"
                    ) {
                        CategoryBarChart(
                            categoryStats = if (categoryExpanded) categoryStats30d else stats.categoryStats
                        )
                    }

                    // ④ 完成率（进度环，就地展开/收起 7天↔30天）
                    StatsCard(
                        title = if (completionExpanded) "完成率（30天）" else "完成率（7天）",
                        value = if (completionExpanded) {
                            "${(completionRate30d * 100).toInt()}%"
                        } else {
                            "${(monthCompletionRate * 100).toInt()}%"
                        },
                        onExpand = { completionExpanded = !completionExpanded },
                        expandIcon = if (completionExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                        expandDescription = if (completionExpanded) "收起" else "展开30天"
                    ) {
                        ProgressRing(
                            rate = if (completionExpanded) completionRate30d else monthCompletionRate
                        )
                    }
                }
            }
        }
    }
}

// ==================== 组件实现 ====================

/**
 * 统计卡片（含标题、展开按钮、图表）
 *
 * @param title 卡片标题（如"累计完成数"）
 * @param value 标题右侧显示的数值（如"123 个"），传 null 时不显示
 * @param onExpand 展开按钮点击回调
 * @param expandIcon 展开按钮图标，默认 OpenInNew
 * @param expandDescription 展开按钮描述，默认"横屏全屏展开"
 * @param chart 图表内容
 */
@Composable
private fun StatsCard(
    title: String,
    value: String?,
    onExpand: () -> Unit,
    expandIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.OpenInNew,
    expandDescription: String = "横屏全屏展开",
    chart: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行：左侧标题+数值，右侧展开按钮
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
                // 展开按钮
                IconButton(
                    onClick = onExpand,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = expandIcon,
                        contentDescription = expandDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // 图表内容
            chart()
        }
    }
}

/**
 * 环形进度组件
 *
 * @param rate 完成率 (0.0-1.0)
 */
@Composable
private fun ProgressRing(rate: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { rate },
                modifier = Modifier.size(100.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(rate * 100).toInt()}%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "完成率",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 分类横向条形图组件
 *
 * @param categoryStats 各分类统计数据列表
 */
@Composable
private fun CategoryBarChart(categoryStats: List<CategoryStat>) {
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
            BarItem(
                categoryName = getCategoryDisplayName(stat.categoryType, stat.categoryName),
                categoryType = stat.categoryType,
                completedCount = stat.completedCount,
                totalCount = stat.totalCount,
                completionRate = stat.completionRate
            )
        }
    }
}

/**
 * 单行条形项
 */
@Composable
private fun BarItem(
    categoryName: String,
    categoryType: Int,
    completedCount: Int,
    totalCount: Int,
    completionRate: Float
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${getCategoryEmoji(categoryType)} $categoryName",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(completionRate * 100).toInt()}%",
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
                    .fillMaxWidth(completionRate.coerceIn(0f, 1f))
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getCategoryGradient(categoryType))
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (completionRate > 0.15f) {
                    Text(
                        text = "$completedCount/$totalCount",
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
 * @param onCreateClick "去创建待办"按钮的点击回调
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
                text = "还没有待办记录，无法统计数据",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
            Button(onClick = onCreateClick) {
                Text(text = "去创建待办")
            }
        }
    }
}

// ==================== 工具函数 ====================

/**
 * 获取分类显示名称
 */
private fun getCategoryDisplayName(type: Int, name: String): String {
    return when (type) {
        0 -> "学习"
        1 -> "工作"
        2 -> "生活"
        3 -> "运动"
        else -> name
    }
}

/**
 * 获取分类 Emoji
 */
private fun getCategoryEmoji(type: Int): String {
    return when (type) {
        0 -> "📚"
        1 -> "💼"
        2 -> "🏠"
        3 -> "⚽"
        else -> "📁"
    }
}

/**
 * 获取分类颜色
 */
private fun getCategoryColor(type: Int): Color {
    return when (type) {
        0 -> Color(0xFF667EEA)
        1 -> Color(0xFFF093FB)
        2 -> Color(0xFF4FACFE)
        3 -> Color(0xFF43E97B)
        else -> Color(0xFF999999)
    }
}

/**
 * 获取分类渐变色
 */
private fun getCategoryGradient(type: Int): androidx.compose.ui.graphics.Brush {
    return when (type) {
        0 -> androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
        )
        1 -> androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(Color(0xFFF093FB), Color(0xFFF5576C))
        )
        2 -> androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
        )
        3 -> androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(Color(0xFF43E97B), Color(0xFF38F9D7))
        )
        else -> androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(Color(0xFF999999), Color(0xFFBBBBBB))
        )
    }
}
