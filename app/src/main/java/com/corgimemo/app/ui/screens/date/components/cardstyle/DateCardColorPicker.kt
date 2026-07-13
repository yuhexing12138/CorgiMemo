package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.corgimemo.app.data.model.DateCardColor

/**
 * 日期卡片颜色选择器(2×7 网格)
 *
 * 布局(参考图):
 * - 第 1 行:Default(斜线无颜色) + Blue + SkyBlue + Teal + Green + Lime + Orange
 * - 第 2 行:Red + Pink + Purple + Navy + Brown + Black + Rainbow(渐变占位)
 *
 * 交互:
 * - 点击单色/Default → 触发 onSelect(DateCardColor)
 * - 点击 Rainbow → 触发 onRainbowClick()(不更新 selected,弹 Snackbar)
 *
 * 视觉:
 * - 圆直径 ≈ 36dp(由 weight(1f) + aspectRatio(1f) + 7 列 + 间距 8dp 算出)
 * - 选中态:白色 2dp 描边圆环 + 内 2dp padding(避免描边覆盖)
 * - Default 圆:白底 + 红色 2dp 对角线(用 Canvas)
 * - Rainbow 圆:sweepGradient 6 色闭环
 *
 * @param selected 当前选中的颜色
 * @param onSelect 选中回调(单色与 Default 走此回调)
 * @param onRainbowClick 点击 Rainbow 占位回调(由调用方弹 Snackbar)
 */
@Composable
fun DateCardColorPicker(
    selected: DateCardColor,
    onSelect: (DateCardColor) -> Unit,
    onRainbowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 第 1 行 7 个:Default + 6 个浅色
    val row1: List<DateCardColor> = listOf(
        DateCardColor.Default,
        DateCardColor.Blue, DateCardColor.SkyBlue, DateCardColor.Teal,
        DateCardColor.Green, DateCardColor.Lime, DateCardColor.Orange
    )
    // 第 2 行 7 个:6 个深色 + Rainbow
    val row2: List<DateCardColor> = listOf(
        DateCardColor.Red, DateCardColor.Pink, DateCardColor.Purple,
        DateCardColor.Navy, DateCardColor.Brown, DateCardColor.Black,
        DateCardColor.Rainbow
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 第 1 行
        ColorRow(items = row1, selected = selected, onSelect = onSelect, onRainbowClick = onRainbowClick)
        // 第 2 行
        ColorRow(items = row2, selected = selected, onSelect = onSelect, onRainbowClick = onRainbowClick)
    }
}

@Composable
private fun ColorRow(
    items: List<DateCardColor>,
    selected: DateCardColor,
    onSelect: (DateCardColor) -> Unit,
    onRainbowClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { color ->
            ColorCircle(
                color = color,
                isSelected = color == selected,
                onClick = {
                    if (color == DateCardColor.Rainbow) onRainbowClick() else onSelect(color)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ColorCircle(
    color: DateCardColor,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (isSelected) Modifier
                    .border(2.dp, Color.White, CircleShape)
                else Modifier
            )
            .padding(if (isSelected) 2.dp else 0.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 内部颜色盘(选中的外圈已留 2dp 间隙)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    when (color) {
                        DateCardColor.Default -> Color.White
                        DateCardColor.Rainbow -> rainbowBrush()
                        else -> topBarColor(color)
                    }
                )
        ) {
            // Default 圆:在白底上画一条对角红线
            if (color == DateCardColor.Default) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 2.dp.toPx()
                    drawLine(
                        color = Color(0xFFFF5252),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }
    }
}

/**
 * 模拟彩虹的 sweepGradient brush
 * 6 段色:红/橙/黄/绿/蓝/紫,首尾闭环
 */
private fun rainbowBrush(): Brush = Brush.sweepGradient(
    colors = listOf(
        Color(0xFFFF5252),  // 红
        Color(0xFFFF9A5C),  // 橙
        Color(0xFFFFEB3B),  // 黄
        Color(0xFF4CAF50),  // 绿
        Color(0xFF3F5BFF),  // 蓝
        Color(0xFF7E57C2),  // 紫
        Color(0xFFFF5252)   // 闭环回红
    )
)
