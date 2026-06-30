package com.corgimemo.app.ui.screens.stats

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.viewmodel.CategoryStat
import com.corgimemo.app.viewmodel.CompletionStats
import com.corgimemo.app.viewmodel.StatsViewModel
import com.corgimemo.app.viewmodel.TimePeriodStat
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.ui.theme.UiDimensions
import com.corgimemo.app.ui.theme.UiTextStyles

/**
 * 完成统计页面（重构版）
 * 使用 TabRow 切换 4 个统计维度：概览、分类、时段、分布
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "📊 数据统计",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // TabRow：使用已废弃的 ScrollableTabRow（PrimaryScrollableTabRow 签名不兼容，需要 scrollState 参数）
            @Suppress("DEPRECATION")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                edgePadding = 8.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "概览",
                            fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "分类",
                            fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "时段",
                            fontWeight = if (selectedTab == 2) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Text(
                            text = "分布",
                            fontWeight = if (selectedTab == 3) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 内容区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize()
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (selectedTab) {
                        0 -> OverviewTabContent(stats = stats)
                        1 -> CategoryTabContent(stats = stats)
                        2 -> PeriodTabContent(stats = stats)
                        3 -> DistributionTabContent(stats = stats)
                    }
                }
            }
        }
    }
}

/**
 * 概览 Tab 内容
 * 显示：统计卡片 + 趋势预测 + 7天趋势图
 */
@Composable
private fun OverviewTabContent(stats: CompletionStats) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SummaryCards(stats = stats)

        PredictionCard(
            completionRate = stats.predictionRate,
            predictedCompletion = stats.predictedCompletion,
            predictedTotal = stats.predictedTotal,
            needsEncouragement = stats.needsEncouragement
        )

        TrendChart(weeklyTrend = stats.weeklyTrend)

        ConsecutiveDaysCard(consecutiveDays = stats.consecutiveDays)

        TotalCompletedCard(totalCompleted = stats.totalCompleted)
    }
}

/**
 * 分类 Tab 内容
 * 显示：分类完成率条形图
 */
@Composable
private fun CategoryTabContent(stats: CompletionStats) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CategoryBarChart(
            categoryStats = stats.categoryStats,
            onCategoryClick = { /* TODO: 显示详情 BottomSheet */ }
        )
    }
}

/**
 * 时段 Tab 内容
 * 显示：最佳时段横幅 + 时段网格 + 效率建议
 */
@Composable
private fun PeriodTabContent(stats: CompletionStats) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BestTimeBanner(bestPeriod = stats.bestTimePeriod)

        TimePeriodGrid(
            timePeriodStats = stats.timePeriodStats,
            bestPeriod = stats.bestTimePeriod
        )

        EfficiencySuggestions(timePeriodStats = stats.timePeriodStats)
    }
}

/**
 * 分布 Tab 内容
 * 显示：饼图 + 图例
 */
@Composable
private fun DistributionTabContent(stats: CompletionStats) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PieChartSection(categoryStats = stats.categoryStats)
    }
}

// ==================== 组件实现 ====================

/**
 * 统计概览卡片
 * 显示今日、本周、本月完成数
 */
@Composable
private fun SummaryCards(stats: CompletionStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(
            label = "今日",
            value = stats.todayCount,
            icon = "📅",
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "本周",
            value = stats.weekCount,
            icon = "📆",
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "本月",
            value = stats.monthCount,
            icon = "🗓️",
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 单个统计卡片
 */
@Composable
private fun SummaryCard(
    label: String,
    value: Int,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp, 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 趋势预测卡片
 */
@Composable
private fun PredictionCard(
    completionRate: Float,
    predictedCompletion: Int,
    predictedTotal: Int,
    needsEncouragement: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "本月完成预测",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 环形进度圆（简化版，完整实现在单独组件）
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { completionRate },
                        modifier = Modifier.size(100.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(completionRate * 100).toInt()}%",
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

                // 预测信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "预计本月完成情况",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$predictedCompletion / $predictedTotal 个任务 ✅",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (needsEncouragement) {
                        Spacer(modifier = Modifier.height(UiDimensions.spacingSmall))
                        Text(
                            text = "💪 加油！还差一点点就达标了",
                            fontSize = UiTextStyles.Caption.fontSize,
                            color = UiColors.Warning,
                            modifier = Modifier
                                .clip(RoundedCornerShape(UiDimensions.cornerRadiusSmall))
                                .background(UiColors.PrimaryLight)
                                .padding(UiDimensions.spacingSmall, UiDimensions.spacingCardPadding)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 分类横向条形图组件
 */
@Composable
private fun CategoryBarChart(
    categoryStats: List<CategoryStat>,
    onCategoryClick: (CategoryStat) -> Unit
) {
    if (categoryStats.isEmpty()) {
        EmptyStateCard(message = "暂无分类数据", icon = "📂")
        return
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📂 分类完成率",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            categoryStats.forEachIndexed { index, stat ->
                BarItem(
                    categoryName = getCategoryDisplayName(stat.categoryType, stat.categoryName),
                    categoryType = stat.categoryType,
                    completedCount = stat.completedCount,
                    totalCount = stat.totalCount,
                    completionRate = stat.completionRate,
                    onClick = { onCategoryClick(stat) }
                )
                if (index < categoryStats.lastIndex) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
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
    completionRate: Float,
    onClick: () -> Unit
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onClick { onClick() },
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
 * 最佳效率时段横幅
 */
@Composable
private fun BestTimeBanner(bestPeriod: TimePeriodStat?) {
    if (bestPeriod == null) return

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                    )
                )
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = "⏰", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "你的最佳效率时段：${bestPeriod.periodLabel}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * 时段网格卡片
 */
@Composable
private fun TimePeriodGrid(
    timePeriodStats: List<TimePeriodStat>,
    bestPeriod: TimePeriodStat?
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📈 各时段完成数量",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 3x2 网格
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                timePeriodStats.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { period ->
                            TimePeriodCard(
                                period = period,
                                isBest = period.periodId == bestPeriod?.periodId,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 补空位
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个时段卡片
 */
@Composable
private fun TimePeriodCard(
    period: TimePeriodStat,
    isBest: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isBest) UiColors.PrimaryLight else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (isBest) {
            androidx.compose.foundation.BorderStroke(2.dp, UiColors.Warning)
        } else null
    ) {
        Box(
            modifier = Modifier.padding(12.dp, 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isBest) {
                    Text(text = "👑", fontSize = 18.sp)
                }
                Text(
                    text = period.periodLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${period.completedCount}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "个任务",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * 效率建议卡片
 */
@Composable
private fun EfficiencySuggestions(timePeriodStats: List<TimePeriodStat>) {
    if (timePeriodStats.isEmpty()) return

    val suggestions = mutableListOf<String>()
    val bestPeriod = timePeriodStats.maxByOrNull { it.completedCount }
    val average = timePeriodStats.map { it.completedCount }.average()

    bestPeriod?.let {
        when (it.periodId) {
            0 -> suggestions.add("🌅 早晨 ${it.periodLabel} 是你的黄金时段，建议安排重要任务")
            1 -> suggestions.add("☀️ 上午 ${it.periodLabel} 是你的黄金时段，建议安排重要任务")
            else -> suggestions.add("⭐ ${it.periodLabel} 是你的最佳效率时段，建议安排重要任务")
        }
    }

    val lunchPeriod = timePeriodStats.find { it.periodId == 2 }
    lunchPeriod?.let {
        if (it.completedCount < average * 0.5) {
            suggestions.add("🍱 午休时段完成率较低，适合处理简单事项")
        }
    }

    val nightPeriod = timePeriodStats.find { it.periodId == 5 }
    if (nightPeriod != null) {
        if (nightPeriod.completedCount > average * 1.5) {
            suggestions.add("🌙 晚上经常加班？注意劳逸结合 💪")
        } else if (nightPeriod.completedCount < average * 0.5) {
            suggestions.add("😴 晚上 22 点后效率明显下降，注意休息 💤")
        }
    }

    if (suggestions.isEmpty()) {
        suggestions.add("✨ 保持当前的工作节奏，继续加油！")
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "💡 效率建议",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            suggestions.forEachIndexed { index, suggestion ->
                Text(
                    text = "• $suggestion",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
                if (index < suggestions.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * 饼图区域组件（简化版）
 */
@Composable
private fun PieChartSection(categoryStats: List<CategoryStat>) {
    if (categoryStats.isEmpty()) {
        EmptyStateCard(message = "暂无分布数据", icon = "🥧")
        return
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🥧 任务类型分布",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 简化的饼图占位（Canvas 版本在独立组件中实现）
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(90.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📊\n饼图",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // 图例
                Column(modifier = Modifier.weight(1f)) {
                    categoryStats.forEach { stat ->
                        PieLegendItem(
                            categoryType = stat.categoryType,
                            categoryName = getCategoryDisplayName(stat.categoryType, stat.categoryName),
                            percentage = stat.completionRate,
                            count = stat.completedCount
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/**
 * 单个图例项
 */
@Composable
private fun PieLegendItem(
    categoryType: Int,
    categoryName: String,
    percentage: Float,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(getCategoryColor(categoryType))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "${getCategoryEmoji(categoryType)} $categoryName",
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(percentage * 100).toInt()}% ($count)",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ==================== 复用现有组件 ====================

/**
 * 连续完成天数卡片
 */
@Composable
private fun ConsecutiveDaysCard(consecutiveDays: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔥", fontSize = 32.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$consecutiveDays 天",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (consecutiveDays > 0) Color(0xFFFF6B00) else MaterialTheme.colorScheme.onSurface
                    // TODO: 特殊用途颜色 - 连续天数火焰橙，可后续迁移到 UiColors.StreakOrange
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (consecutiveDays > 0) "连续完成天数" else "今天还没有完成任务哦",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (consecutiveDays >= 7) {
                Text(
                    text = "太棒了！保持这个势头！",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * 最近7天趋势图
 */
@Composable
private fun TrendChart(weeklyTrend: List<Pair<String, Int>>) {
    if (weeklyTrend.isEmpty()) return

    val maxValue = weeklyTrend.maxOfOrNull { it.second } ?: 1
    val normalizedMax = maxValue.coerceAtLeast(1)

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "最近 7 天趋势",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyTrend.forEach { (label, count) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = count.toString(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(
                                    (count.toFloat() / normalizedMax * 100).coerceAtLeast(4f).dp
                                )
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (count > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 累计完成卡片
 */
@Composable
private fun TotalCompletedCard(totalCompleted: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🏆", fontSize = 28.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "累计完成",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$totalCompleted 个任务",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = getEncouragement(totalCompleted),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 空状态卡片
 */
@Composable
private fun EmptyStateCard(message: String, icon: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 40.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 工具函数 ====================

/**
 * 根据累计完成数获取鼓励语
 */
private fun getEncouragement(total: Int): String {
    return when {
        total >= 500 -> "🌟"
        total >= 200 -> "💪"
        total >= 100 -> "👏"
        total >= 50 -> "😊"
        total >= 10 -> "👍"
        else -> "开始"
    }
}

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

/**
 * 点击修饰符扩展
 */
private fun Modifier.onClick(onClick: () -> Unit): Modifier =
    this.then(
        clickable(onClick = onClick)
    )
