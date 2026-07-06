package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.AnimationResourceManager
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation

/**
 * 柯基下拉刷新指示器（v2 - 文字与动画分层版）
 *
 * 与 v1 的差异：
 * - Box(contentAlignment=Center) → Column(verticalArrangement=Center) 强制分层
 * - 提示文字位于柯基上方（符合"先读文字再看图"阅读顺序）
 * - 文字与柯基之间固定 4dp 间距，从根本上消除重叠
 * - 最小空白高度 72dp（容纳 16dp 文字 + 4dp 间距 + 48dp 最小柯基 + 4dp 余量）
 * - 8~16dp 区间内淡入淡出，避免硬切违和感
 * - 柯基动画所有阶段都使用 AnimationType.RUN（不变）
 * - 柯基大小随下拉进度从 48dp 渐变至 64dp（不变）
 *
 * 渲染层次：
 * 1. 空白区（高度 = max(pullOffset, 72dp)，铺满宽度）
 * 2. Column 居中布局：文字（顶部） → 4dp 间距 → 柯基（底部）
 * 3. 整体 graphicsLayer alpha 渐变（避免硬切）
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

    // 1. px → dp 转换（在调用纯函数前完成单位转换）
    val pullOffsetDp = with(density) { pullOffset.toDp().value }
    val maxPullHeightDp = with(density) { maxPullHeightPx.toDp().value }
    val refreshThresholdDp = with(density) { refreshThresholdPx.toDp().value }

    // 2. 调用纯函数计算布局
    val layout = remember(pullOffsetDp, state, maxPullHeightDp, refreshThresholdDp) {
        computePullRefreshIndicatorLayout(
            pullOffsetDp = pullOffsetDp,
            maxPullHeightDp = maxPullHeightDp,
            refreshThresholdDp = refreshThresholdDp,
            state = state
        )
    }

    // 3. IDLE 早返
    if (!layout.shouldRender) return

    // 4. dp → Dp 转换
    val displayHeight = layout.displayHeightDp.dp
    val corgiSize = layout.corgiSizeDp.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(displayHeight)
            .graphicsLayer { alpha = layout.contentAlpha },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 5. 提示文字（位于柯基上方）
        if (layout.tipText != null) {
            Text(
                text = layout.tipText!!,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
        }

        // 6. 柯基动画
        val frames = remember(AnimationType.RUN) {
            AnimationResourceManager.getAnimationFrames(AnimationType.RUN)
        }
        if (frames.isNotEmpty()) {
            FrameAnimation(
                frames = frames,
                fps = 8,
                isLooping = true,
                isPlaying = true,
                modifier = Modifier.size(corgiSize)
            )
        }
    }
}
