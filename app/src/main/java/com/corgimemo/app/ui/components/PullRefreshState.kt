package com.corgimemo.app.ui.components

/**
 * 下拉刷新状态机
 *
 * 4 值枚举，描述下拉刷新全生命周期：
 * - IDLE：空闲，无下拉
 * - PULLING：用户正在下拉
 * - RELEASING：释放后回弹动画中（未达阈值）
 * - REFRESHING：刷新中（达阈值释放后）
 *
 * 状态转换规则（详见 PullRefreshUtils.computeNextPullRefreshState）：
 * IDLE → PULLING → (释放) → REFRESHING（达阈值）或 RELEASING（未达阈值）
 * RELEASING → IDLE（回弹完成）
 * REFRESHING → IDLE（isRefreshing=false）
 */
enum class PullRefreshState {
    IDLE,
    PULLING,
    RELEASING,
    REFRESHING
}
