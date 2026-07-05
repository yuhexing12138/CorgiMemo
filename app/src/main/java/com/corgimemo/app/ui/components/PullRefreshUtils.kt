package com.corgimemo.app.ui.components

/**
 * 下拉刷新核心纯函数
 *
 * 与 Compose runtime 解耦，便于单元测试。
 * 所有函数均为纯函数：相同输入 → 相同输出，无副作用。
 */

/**
 * 阻尼曲线：随下拉量增大，阻尼系数递减
 *
 * 阻尼公式：dampingFactor = 1 - (rawOffset / maxPullHeight)²
 *
 * 特性：
 * - rawOffset = 0：阻尼系数 1.0（无阻尼，跟随手指）
 * - rawOffset = maxPullHeight / 2：阻尼系数 0.75（轻微阻尼）
 * - rawOffset = maxPullHeight：阻尼系数 0.0（完全锁死，无法继续下拉）
 *
 * 边界处理：
 * - rawOffset <= 0：返回 0（无下拉）
 * - rawOffset >= maxPullHeight：返回 maxPullHeight（封顶）
 * - maxPullHeight <= 0：返回 0（防护）
 *
 * @param rawOffset 原始下拉量（单位与 maxPullHeight 一致，px 或 dp）
 * @param maxPullHeight 最大下拉高度
 * @return 阻尼后的实际偏移
 */
fun computeDampedOffset(rawOffset: Float, maxPullHeight: Float): Float {
    if (maxPullHeight <= 0f) return 0f
    if (rawOffset <= 0f) return 0f
    if (rawOffset >= maxPullHeight) return maxPullHeight
    val ratio = rawOffset / maxPullHeight
    val dampingFactor = 1f - ratio * ratio
    return rawOffset * dampingFactor
}
