package com.corgimemo.app.ui.screens.inspiration.stats

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.viewmodel.ChartFullscreenViewModel

/**
 * 灵感图表横屏全屏页面
 *
 * - 进入时强制横屏（[ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE]）
 * - 离开时恢复原方向（[ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED]）
 * - 右上角 X 按钮关闭，调用 [NavController.popBackStack]
 * - 标题显示对应图表名称与当前累计字数（折线图场景）
 *
 * @param navController 导航控制器
 * @param viewModel ViewModel，通过 hiltViewModel() 注入
 */
@Composable
fun ChartFullscreenScreen(
    navController: NavController,
    viewModel: ChartFullscreenViewModel = hiltViewModel()
) {
    val chartType = viewModel.chartType
    val isLoading by viewModel.isLoading.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val currentCumulativeChars by viewModel.currentCumulativeChars.collectAsState()

    // 强制横屏，进入时设置，离开时恢复
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 顶部避开系统状态栏，避免标题与 X 按钮被状态栏遮挡
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // 顶部标题行：左侧标题 + 数值，右侧 X 按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleText = when (chartType) {
                    "line" -> "累计总字数：$currentCumulativeChars 字"
                    "bar" -> "输入字数"
                    "todo_line" -> {
                        val todoTotal by viewModel.todoTotalCompleted.collectAsState()
                        "累计完成数：$todoTotal 个"
                    }
                    "todo_bar" -> "每日完成数"
                    else -> "图表"
                }
                Text(
                    text = titleText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "收起",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 图表内容
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isLoading) {
                    when (chartType) {
                        "line" -> {
                            LineChart(points = chartData.points)
                        }
                        "bar" -> {
                            BarChart(points = chartData.points)
                        }
                        "todo_line" -> {
                            val todoData by viewModel.todoChartData.collectAsState()
                            LineChart(points = todoData.points)
                        }
                        "todo_bar" -> {
                            val todoData by viewModel.todoChartData.collectAsState()
                            BarChart(points = todoData.points)
                        }
                    }
                }
            }
        }
    }
}

