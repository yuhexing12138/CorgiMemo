package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 增强标题栏组件（统一版）
 *
 * 包含左侧 ☰ 菜单按钮、中间标题+暖橙色下划线、右侧可配置功能按钮+🐕 柯基图标按钮。
 * 三个核心页面（待办/灵感/日期）共用此组件，通过参数配置差异化内容。
 *
 * @param title 标题文字
 * @param onMenuClick 菜单按钮点击回调（打开侧滑导航栏）
 * @param onCorgiClick 柯基图标点击回调（进入柯基详情页）
 * @param actionButtons 右侧自定义功能按钮列表（柯基按钮之前显示）
 * @param modifier 修饰符
 */
@Composable
fun EnhancedTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onCorgiClick: () -> Unit,
    actionButtons: List<@Composable () -> Unit> = emptyList(),
    modifier: Modifier = Modifier
) {
    var underlineAnimationStarted by remember { mutableStateOf(false) }

    val underlineProgress by animateFloatAsState(
        targetValue = if (underlineAnimationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.EaseOutQuart),
        label = "underlineProgress"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        underlineAnimationStarted = true
    }

    Column(modifier = modifier.fillMaxWidth().safeAreaForTopBar()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "菜单",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(8.dp))

            actionButtons.forEach { button ->
                button()
            }

            IconButton(onClick = onCorgiClick) {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = "柯基详情",
                    tint = UiColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
                .height(3.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                if (underlineProgress > 0f) {
                    val endX = size.width * underlineProgress
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
}
