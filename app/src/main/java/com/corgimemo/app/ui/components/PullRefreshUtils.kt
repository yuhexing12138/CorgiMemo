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
