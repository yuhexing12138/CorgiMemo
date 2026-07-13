package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 撕页效果顶部波浪 Shape
 *
 * 仅修改顶部边缘为波浪形,底部/左侧/右侧为直线。
 * 配合 Card 的 clip 使用,实现"撕日历"纸张效果。
 *
 * @param waveHeightPx 波浪高度(像素,默认 8f)
 * @param waveCount 波浪数量(默认 12)
 */
class WavyTopShape(
    private val waveHeightPx: Float = 8f,
    private val waveCount: Int = 12
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // 构造顶部波浪的闭合路径,底部/左/右为直线
        val path = Path().apply {
            // 起点:左下
            moveTo(0f, size.height)
            // 底部直线:左下到右下
            lineTo(size.width, size.height)
            // 右侧直线:右下到右上
            lineTo(size.width, 0f)
            // 顶部波浪:从右上到左上
            val waveWidth = size.width / waveCount
            for (i in 0 until waveCount) {
                val endX = size.width - (i + 1) * waveWidth
                val startX = size.width - i * waveWidth
                val midX = (startX + endX) / 2
                quadraticTo(
                    midX, -waveHeightPx,
                    endX, 0f
                )
            }
            // 左侧直线:左上到左下
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}
