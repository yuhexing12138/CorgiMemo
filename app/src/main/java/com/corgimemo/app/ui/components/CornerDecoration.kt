package com.corgimemo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 暖橙色装饰角标组件
 *
 * 在内容区域的四角绘制 L 型对角条纹装饰，
 * 为编辑页增添温暖、精致的设计感。
 * 默认使用暖橙色（#F97316），与整体设计风格保持一致。
 *
 * 使用示例：
 * ```kotlin
 * Box(modifier = Modifier.fillMaxSize()) {
 *     // 内容区域
 *     Column(modifier = Modifier.padding(48.dp)) { ... }
 *
 *     // 四角装饰
 *     CornerDecoration()
 * }
 * ```
 *
 * @param modifier Modifier（可选）
 * @param color 装饰线条颜色，默认为暖橙色 #F97316
 * @param size 装饰区域大小，默认 24.dp
 * @param strokeWidth 线条粗细，默认 3.dp
 */
@Composable
fun CornerDecoration(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF97316), // 暖橙色
    size: Dp = 24.dp,
    strokeWidth: Dp = 3.dp
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        /** 左上角装饰 */
        Canvas(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(size)
        ) {
            /** 绘制水平线（顶部） */
            drawLine(
                color = color,
                start = Offset(0f, strokeWidth.toPx() / 2),
                end = Offset(size.toPx(), strokeWidth.toPx() / 2),
                strokeWidth = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            /** 绘制垂直线（左侧） */
            drawLine(
                color = color,
                start = Offset(strokeWidth.toPx() / 2, 0f),
                end = Offset(strokeWidth.toPx() / 2, size.toPx()),
                strokeWidth = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        /** 右上角装饰 */
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(size)
        ) {
            /** 绘制水平线（顶部） */
            drawLine(
                color = color,
                start = Offset(0f, strokeWidth.toPx() / 2),
                end = Offset(size.toPx(), strokeWidth.toPx() / 2),
                strokeWidth = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            /** 绘制垂直线（右侧） */
            drawLine(
                color = color,
                start = Offset(size.toPx() - strokeWidth.toPx() / 2, 0f),
                end = Offset(size.toPx() - strokeWidth.toPx() / 2, size.toPx()),
                strokeWidth = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        /** 左下角装饰 */
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .size(size)
        ) {
            /** 绘制水平线（底部） */
            drawLine(
                color = color,
                start = Offset(0f, size.toPx() - strokeWidth.toPx() / 2),
                end = Offset(size.toPx(), size.toPx() - strokeWidth.toPx() / 2),
                strokeWidth = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            /** 绘制垂直线（左侧） */
            drawLine(
                color = color,
                start = Offset(strokeWidth.toPx() / 2, 0f),
                end = Offset(strokeWidth.toPx() / 2, size.toPx()),
                strokeWidth = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        /** 右下角装饰 */
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(size)
        ) {
            /** 绘制水平线（底部） */
            drawLine(
                color = color,
                start = Offset(0f, size.toPx() - strokeWidth.toPx() / 2),
                end = Offset(size.toPx(), size.toPx() - strokeWidth.toPx() / 2),
                strokeWidth = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            /** 绘制垂直线（右侧） */
            drawLine(
                color = color,
                start = Offset(size.toPx() - strokeWidth.toPx() / 2, 0f),
                end = Offset(size.toPx() - strokeWidth.toPx() / 2, size.toPx()),
                strokeWidth = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

/**
 * 带装饰角标的内容容器组件
 *
 * 将 CornerDecoration 与内容区域组合在一起，
 * 简化使用方式，无需手动在 Box 中对齐四个角。
 * 支持自定义背景色，用于实现编辑页背景色选择功能。
 *
 * @param content 内容 Composable lambda
 * @param modifier 外层 Modifier（可选）
 * @param color 装饰线条颜色（默认暖橙色）
 * @param size 装饰区域大小（默认 24.dp）
 * @param strokeWidth 线条粗细（默认 3.dp）
 * @param backgroundColor 背景填充颜色（默认透明，即不设置背景）
 */
@Composable
fun DecoratedContentBox(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF97316), // 暖橙色
    size: Dp = 24.dp,
    strokeWidth: Dp = 3.dp,
    backgroundColor: Color = Color.Transparent, /** 背景色参数（新增） */
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        /** 内容区域（带内边距以避免与装饰重叠） */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(size + 8.dp) // 确保内容不遮挡装饰角标
                .then(
                    if (backgroundColor != Color.Transparent) {
                        /** 如果设置了背景色，则应用到内容区域 */
                        Modifier.background(backgroundColor)
                    } else {
                        Modifier /** 无背景色时不添加 background 修饰符 */
                    }
                ),
            contentAlignment = Alignment.TopStart
        ) {
            content()
        }

        /** 四角装饰 */
        CornerDecoration(
            color = color,
            size = size,
            strokeWidth = strokeWidth
        )
    }
}
