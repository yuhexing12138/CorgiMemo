package com.corgimemo.app.util

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density

/**
 * 密度转换工具函数集合
 *
 * 统一处理 dp ↔ px 转换，避免在 graphicsLayer 等 API 中使用硬编码浮点值，
 * 确保不同屏幕密度下视觉效果一致。
 *
 * 使用示例：
 * ```kotlin
 * val density = LocalDensity.current
 * // Dp → Float px（用于 graphicsLayer.translationY 等场景）
 * translationY = 4.dp.toPxFloat(density)
 * // Float px → Dp（反向转换）
 * val dpValue = pixelValue.toDp(density)
 * ```
 */

/**
 * 将 Dp 值转换为 Float 像素值
 *
 * 用于 Compose Modifier 中需要 Float 类型像素值的场景（如 graphicsLayer）。
 * 通过 [Density] 实例进行转换，确保与当前屏幕密度一致。
 *
 * @param density 当前 Compose 的密度实例（通常来自 LocalDensity.current）
 * @return 对应的 Float 像素值
 */
fun Dp.toPxFloat(density: Density): Float = with(density) { toPx() }

/**
 * 将 Int 像素值转换为 Dp 值（反向转换）
 *
 * 当从原生 View 或触摸事件获取像素坐标后，
 * 需要转换为 Dp 以便在 Compose 布局中使用。
 *
 * @param density 当前 Compose 的密度实例
 * @return 对应的 Dp 值
 */
fun Int.toDp(density: Density): Dp = with(density) { this@toDp.dp }

/**
 * 将 Float 像素值转换为 Dp 值（反向转换）
 *
 * [Int.toDp] 的 Float 版本。
 */
fun Float.toDp(density: Density): Dp = with(density) { this@toDp.dp }
