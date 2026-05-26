package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 增强标题栏组件
 *
 * 包含左侧 ☰ 菜单按钮、中间标题+暖橙色下划线、右侧 📊 统计按钮和 🐕 柯基图标按钮。
 * 支持菜单弹窗和排序选项弹窗。
 *
 * @param title 标题文字（默认："📝 我的待办"）
 * @param onMenuClick 菜单按钮点击回调（可选，默认显示内置菜单）
 * @param onStatsClick 统计按钮点击回调（可选，默认显示内置排序选项）
 * @param onCorgiClick 柯基图标点击回调（可选，点击进入柯基详情页）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedTopBar(
    title: String = "📝 我的待办",
    onMenuClick: (() -> Unit)? = null,
    onStatsClick: (() -> Unit)? = null,
    onCorgiClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenuDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var underlineAnimationStarted by remember { mutableStateOf(false) }

    val underlineProgress by animateFloatAsState(
        targetValue = if (underlineAnimationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.EaseOutQuart),
        label = "underlineProgress"
    )

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        delay(100)
        underlineAnimationStarted = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：☰ 菜单按钮
            IconButton(onClick = {
                onMenuClick?.invoke() ?: run { showMenuDialog = true }
            }) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "菜单",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 中间：标题文字
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧：📊 统计按钮 + 🐕 柯基图标按钮
            IconButton(onClick = {
                onStatsClick?.invoke() ?: run { showSortDialog = true }
            }) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "统计与排序",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 🐕 柯基图标按钮
            IconButton(onClick = {
                onCorgiClick?.invoke()
            }) {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = "柯基详情",
                    tint = UiColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 暖橙色装饰下划线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
                .height(3.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                if (underlineProgress > 0f) {
                    val endX = size.width * underlineProgress
                    // 暖橙色笔触渐变效果：从透明到主色再到透明
                    val brush = Brush.horizontalGradient(
                        colors = listOf(
                            UiColors.Primary.copy(alpha = 0.3f),
                            UiColors.Primary,
                            UiColors.Primary.copy(alpha = 0.8f),
                            UiColors.Primary.copy(alpha = 0.2f)
                        ),
                        startX = 0f,
                        endX = endX
                    )
                    drawLine(
                        brush = brush,
                        start = Offset(0f, size.height / 2),
                        end = Offset(endX, size.height / 2),
                        strokeWidth = size.height,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }

    // 菜单弹窗 (AlertDialog 简化版)
    if (showMenuDialog) {
        AlertDialog(
            onDismissRequest = { showMenuDialog = false },
            title = { Text("导航菜单") },
            text = {
                Column {
                    MenuOptionItem(text = "🏠 首页", onClick = { showMenuDialog = false })
                    MenuOptionItem(text = "📊 统计", onClick = { showMenuDialog = false })
                    MenuOptionItem(text = "⚙️ 设置", onClick = { showMenuDialog = false })
                    MenuOptionItem(text = "❓ 帮助与反馈", onClick = { showMenuDialog = false })
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMenuDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 排序选项弹窗
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("排序方式") },
            text = {
                Column {
                    SortOptionItem(text = "⬇️ 按时间排序（默认）", onClick = { showSortDialog = false })
                    SortOptionItem(text = "🔴 按优先级排序", onClick = { showSortDialog = false })
                    SortOptionItem(text = "📂 按分类排序", onClick = { showSortDialog = false })
                    SortOptionItem(text = "✅ 按完成状态排序", onClick = { showSortDialog = false })
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 菜单选项项组件
 */
@Composable
private fun MenuOptionItem(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 排序选项项组件
 */
@Composable
private fun SortOptionItem(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
