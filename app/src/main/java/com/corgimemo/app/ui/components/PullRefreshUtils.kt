package com.corgimemo.app.ui.components

import kotlin.math.sqrt

/**
 * 下拉刷新核心纯函数
 *
 * 与 Compose runtime 解耦，便于单元测试。
 * 所有函数均为纯函数：相同输入 → 相同输出，无副作用。
 */

/**
 * 阻尼曲线：随下拉量增大，阻尼递增（越拉越难拉）
 *
 * 阻尼公式：dampedOffset = rawOffset - rawOffset² / (4 * maxPullHeight)
 * 等价于：dampedOffset = maxPullHeight * (2·ratio - ratio²)，其中 ratio = rawOffset / (2·maxPullHeight)
 *
 * 特性（全程单调递增，增速递减）：
 * - rawOffset = 0：阻尼系数 1.0（与手指 1:1 跟随，无阻尼）
 * - rawOffset = maxPullHeight：阻尼系数 0.75（75% 跟随）
 * - rawOffset = 2·maxPullHeight：阻尼系数 0.5（50% 跟随），dampedOffset 达到 maxPullHeight
 *
 * 数学性质：
 * - f(0) = 0, f'(0) = 1（起点跟手）
 * - f'(x) = 1 - x/(2·M)，导数从 1 线性递减到 0（阻尼递增）
 * - f(2M) = M，之后封顶为 maxPullHeight
 * - f''(x) = -1/(2M) < 0，凹函数（拉得越远越难拉）
 *
 * 边界处理：
 * - rawOffset <= 0：返回 0（无下拉）
 * - rawOffset >= 2*maxPullHeight：返回 maxPullHeight（封顶）
 * - maxPullHeight <= 0：返回 0（防护）
 *
 * @param rawOffset 原始下拉量（累计手指移动距离，单位 px）
 * @param maxPullHeight 最大下拉高度（px）
 * @return 阻尼后的实际偏移量（px）
 */
fun computeDampedOffset(rawOffset: Float, maxPullHeight: Float): Float {
    if (maxPullHeight <= 0f) return 0f
    if (rawOffset <= 0f) return 0f
    if (rawOffset >= 2f * maxPullHeight) return maxPullHeight
    return rawOffset - rawOffset * rawOffset / (4f * maxPullHeight)
}

/**
 * 阻尼反向换算：从已阻尼的偏移量反推原始下拉量
 *
 * 用于 onPreScroll 收回阶段：线性减少 dampedOffset 后，反推对应的 rawOffset，
 * 保证再次下拉时阻尼曲线连续。
 *
 * 公式推导：damped = raw - raw²/(4M)
 *   → raw² - 4M·raw + 4M·damped = 0
 *   → raw = 2M(1 - √(1 - damped/M))（取 [0, 2M] 区间内的根）
 *
 * @param dampedOffset 已阻尼的偏移量（px）
 * @param maxPullHeight 最大下拉高度（px）
 * @return 对应的原始下拉量（px）
 */
fun computeRawOffsetForDamped(dampedOffset: Float, maxPullHeight: Float): Float {
    if (maxPullHeight <= 0f) return 0f
    if (dampedOffset <= 0f) return 0f
    if (dampedOffset >= maxPullHeight) return 2f * maxPullHeight
    return 2f * maxPullHeight * (1f - sqrt(1f - dampedOffset / maxPullHeight))
}

/**
 * 状态机转换：根据当前状态与触发条件计算下一个状态
 *
 * 转换规则：
 * - IDLE + pullOffset > 0 → PULLING
 * - IDLE + pullOffset == 0 → IDLE（保持）
 * - PULLING + isReleased + pullOffset >= threshold → REFRESHING
 * - PULLING + isReleased + pullOffset < threshold → RELEASING
 * - PULLING + !isReleased → PULLING（保持）
 * - RELEASING + pullOffset <= 0 → IDLE（回弹完成）
 * - RELEASING + pullOffset > 0 → RELEASING（保持，动画中）
 * - REFRESHING + !isRefreshing → IDLE（刷新完成）
 * - REFRESHING + isRefreshing → REFRESHING（锁定，忽略下拉与释放）
 *
 * @param current 当前状态
 * @param pullOffset 当前下拉偏移（已阻尼，单位与 threshold 一致）
 * @param refreshThreshold 刷新阈值
 * @param isReleased 用户是否释放手指（onPreFling 触发时为 true）
 * @param isRefreshing ViewModel 的 isRefreshing 状态
 * @return 下一个状态
 */
fun computeNextPullRefreshState(
    current: PullRefreshState,
    pullOffset: Float,
    refreshThreshold: Float,
    isReleased: Boolean,
    isRefreshing: Boolean
): PullRefreshState {
    return when (current) {
        PullRefreshState.IDLE -> {
            if (pullOffset > 0f) PullRefreshState.PULLING else PullRefreshState.IDLE
        }
        PullRefreshState.PULLING -> {
            when {
                isReleased && pullOffset >= refreshThreshold -> PullRefreshState.REFRESHING
                isReleased && pullOffset < refreshThreshold -> PullRefreshState.RELEASING
                else -> PullRefreshState.PULLING
            }
        }
        PullRefreshState.RELEASING -> {
            if (pullOffset <= 0f) PullRefreshState.IDLE else PullRefreshState.RELEASING
        }
        PullRefreshState.REFRESHING -> {
            if (!isRefreshing) PullRefreshState.IDLE else PullRefreshState.REFRESHING
        }
    }
}
