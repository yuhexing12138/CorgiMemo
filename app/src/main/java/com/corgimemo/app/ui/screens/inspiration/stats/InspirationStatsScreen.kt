package com.corgimemo.app.ui.screens.inspiration.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.stats.ChartRange
import com.corgimemo.app.viewmodel.InspirationStatsViewModel

/**
 * 灵感字数统计页面
 *
 * 展示最近 7/30 天的累计总字数（折线图）与每日输入字数（柱状图）。
 * 每张卡片右上角有展开按钮，可在 7 天与 30 天视图间切换。
 *
 * @param navController 导航控制器，用于返回上一页与跳转灵感编辑页（空状态 CTA）
 * @param viewModel 字数统计 ViewModel，通过 hiltViewModel() 注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationStatsScreen(
    navController: NavController,
    viewModel: InspirationStatsViewModel = hiltViewModel()
) {
    // 收集 ViewModel 状态（StateFlow → Compose State）
    val isEmpty by viewModel.isEmpty.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lineChartData by viewModel.lineChartData.collectAsState()
    val barChartData by viewModel.barChartData.collectAsState()
    val currentCumulativeChars by viewModel.currentCumulativeChars.collectAsState()
    val lineRange by viewModel.lineRange.collectAsState()
    val barRange by viewModel.barRange.collectAsState()

    Scaffold(
        topBar = {
            // 顶部应用栏：标题"字数统计" + 返回按钮
            TopAppBar(
                title = {
                    Text(
                        text = "字数统计",
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
            // 空状态：完全无灵感数据时显示
            if (isEmpty && !isLoading) {
                EmptyState(
                    onCreateClick = {
                        // 跳转到新建灵感页
                        navController.navigate("inspiration_edit")
                    }
                )
            } else {
                // 主内容：累计总字数卡片 + 输入字数卡片
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 累计总字数卡片（折线图）
                    StatsCard(
                        title = "累计总字数",
                        value = "$currentCumulativeChars 字",
                        range = lineRange,
                        onToggleRange = { viewModel.toggleLineRange() }
                    ) {
                        LineChart(points = lineChartData.points)
                    }

                    // 输入字数卡片（柱状图）
                    StatsCard(
                        title = "输入字数",
                        value = null,
                        range = barRange,
                        onToggleRange = { viewModel.toggleBarRange() }
                    ) {
                        BarChart(points = barChartData.points)
                    }
                }
            }
        }
    }
}

/**
 * 统计卡片（含标题、展开按钮、图表）
 *
 * @param title 卡片标题（如"累计总字数"）
 * @param value 标题右侧显示的数值（如"123 字"），传 null 时不显示
 * @param range 当前时间范围（用于切换图标）
 * @param onToggleRange 切换时间范围回调
 * @param chart 图表内容（LineChart 或 BarChart）
 */
@Composable
private fun StatsCard(
    title: String,
    value: String?,
    range: ChartRange,
    onToggleRange: () -> Unit,
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
                        // 数值后缀，如"：123 字"
                        Text(
                            text = "：$value",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // 展开/收起按钮：7 天显示 OpenInNew（展开），30 天显示 NorthEast（返回 7 天）
                IconButton(
                    onClick = onToggleRange,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (range == ChartRange.SEVEN_DAYS) {
                            Icons.Default.OpenInNew
                        } else {
                            Icons.Default.NorthEast
                        },
                        contentDescription = if (range == ChartRange.SEVEN_DAYS) {
                            "展开为 30 天"
                        } else {
                            "返回 7 天"
                        },
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
 * 空状态视图
 *
 * 当数据库中没有任何灵感记录时显示，提示用户去记录灵感。
 *
 * @param onCreateClick "去记录灵感"按钮的点击回调
 */
@Composable
private fun EmptyState(onCreateClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 柯基表情（保持品牌一致性）
            Text(text = "🐶", fontSize = 64.sp)
            Text(
                text = "还没有灵感记录，无法统计字数",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
            // CTA：跳转至灵感编辑页
            Button(onClick = onCreateClick) {
                Text(text = "去记录灵感")
            }
        }
    }
}
