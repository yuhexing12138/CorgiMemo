package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.AnimationResourceManager
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation

/**
 * 柯基下拉刷新指示器（新版）
 *
 * 与旧版的差异：
 * - 删除"从右侧跑入"逻辑
 * - 改为空白区 + 水平居中奔跑柯基 + 提示文字
 * - 柯基动画所有阶段都使用 AnimationType.RUN（不切换为 WAG）
 * - 柯基大小随下拉进度从 48dp 渐变至 64dp
 *
 * 渲染层次：
 * 1. 空白区（高度 = pullOffset，铺满宽度）
 * 2. 居中奔跑柯基（FrameAnimation）
 * 3. 提示文字（下拉刷新 / 释放刷新 / 柯基努力加载中~）
 *
 * @param pullOffset 当前下拉偏移（px，已阻尼）
 * @param state 当前下拉刷新状态
 * @param maxPullHeightPx 最大下拉高度（px，用于计算柯基大小渐变）
 * @param refreshThresholdPx 刷新阈值（px，用于判断"释放刷新"提示）
 * @param modifier 修饰符
 */
@Composable
fun CorgiPullRefreshIndicator(
    pullOffset: Float,
    state: PullRefreshState,
    maxPullHeightPx: Float,
    refreshThresholdPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pullOffsetDp = with(density) { pullOffset.toDp() }

    // 柯基显示条件：有下拉量 或 刷新中
    val showCorgi = pullOffset > 0.01f || state == PullRefreshState.REFRESHING

    // 下拉进度（0~1），用于柯基大小渐变
    val pullProgress = if (maxPullHeightPx > 0f) {
        (pullOffset / maxPullHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 柯基大小：48dp → 64dp 渐变
    val corgiSizeDp = 48.dp + (64.dp - 48.dp) * pullProgress

    // 提示文字
    val tipText: String? = when {
        state == PullRefreshState.REFRESHING -> "柯基努力加载中~"
        state == PullRefreshState.PULLING && pullOffset >= refreshThresholdPx -> "释放刷新"
        state == PullRefreshState.PULLING -> "下拉刷新"
        else -> null
    }

    if (!showCorgi) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(pullOffsetDp),
        contentAlignment = Alignment.Center
    ) {
        // 居中奔跑柯基
        val frames = remember(AnimationType.RUN) {
            AnimationResourceManager.getAnimationFrames(AnimationType.RUN)
        }

        if (frames.isNotEmpty()) {
            FrameAnimation(
                frames = frames,
                fps = 8,
                isLooping = true,
                isPlaying = true,
                modifier = Modifier.size(corgiSizeDp)
            )
        }

        // 提示文字（位于柯基下方）
        if (tipText != null) {
            Text(
                text = tipText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
