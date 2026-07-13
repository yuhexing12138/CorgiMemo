package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 撕页效果顶部+底部波浪 Shape
 *
 * 同时修改顶部和底部边缘为波浪形,左侧/右侧为直线。
 * 配合 Box 的 clip 使用,实现"撕日历"纸张上下双面波浪效果。
 *
 * @param topWaveHeightPx 顶部波浪高度(像素,默认 8f)
 * @param bottomWaveHeightPx 底部波浪高度(像素,默认 8f)
 * @param waveCount 波浪数量(默认 12)
 */
class WavyTopBottomShape(
    private val topWaveHeightPx: Float = 8f,
    private val bottomWaveHeightPx: Float = 8f,
    private val waveCount: Int = 12
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(0f, topWaveHeightPx)
            val topWaveWidth = size.width / waveCount
            for (i in 0 until waveCount) {
                val startX = i * topWaveWidth
                val endX = (i + 1) * topWaveWidth
                val midX = (startX + endX) / 2
                quadraticTo(
                    midX, 0f,
                    endX, topWaveHeightPx
                )
            }
            lineTo(size.width, size.height - bottomWaveHeightPx)
            val bottomWaveWidth = size.width / waveCount
            for (i in waveCount - 1 downTo 0) {
                val startX = (i + 1) * bottomWaveWidth
                val endX = i * bottomWaveWidth
                val midX = (startX + endX) / 2
                quadraticTo(
                    midX, size.height,
                    endX, size.height - bottomWaveHeightPx
                )
            }
            lineTo(0f, topWaveHeightPx)
            close()
        }
        return Outline.Generic(path)
    }
}
