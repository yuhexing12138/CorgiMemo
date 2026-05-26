package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.AnimationResourceManager
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import kotlin.math.roundToInt

/**
 * 柯基下拉刷新指示器
 *
 * 自定义下拉刷新动画，柯基从右侧跑入并摇尾巴。
 *
 * 动画序列：
 * 1. 用户下拉时：柯基从右侧逐渐滑入（进度跟随下拉距离）
 * 2. 达到阈值后：柯基完全显示，切换为摇尾巴动画
 * 3. 刷新中：柯基持续摇尾巴
 * 4. 刷新完成：柯基向右滑出消失（800ms）
 *
 * @param isRefreshing 是否正在刷新
 * @param pullProgress 下拉进度 (0f = 未下拉, 1f = 达到刷新阈值, >1f = 过度下拉)
 * @param modifier 修饰符
 */
@Composable
fun CorgiPullToRefreshIndicator(
    isRefreshing: Boolean,
    pullProgress: Float,
    modifier: Modifier = Modifier
) {
    // 柯基跑入的偏移量：下拉进度 0→1 对应 偏移 100%→0%
    val corgiOffsetXFraction by animateFloatAsState(
        targetValue = if (isRefreshing) 0f else (1f - pullProgress.coerceIn(0f, 1f)),
        animationSpec = tween(
            durationMillis = if (isRefreshing) 800 else 400,
            easing = androidx.compose.animation.core.EaseOutBack
        ),
        label = "corgiOffsetX"
    )

    // 刷新完成后柯基滑出
    var isHiding by remember { mutableStateOf(false) }
    val hideOffsetX by animateFloatAsState(
        targetValue = if (isHiding) 1f else 0f,
        animationSpec = tween(
            durationMillis = 800,
            easing = androidx.compose.animation.core.EaseIn
        ),
        label = "corgiHideOffset"
    )

    // 监听刷新状态变化
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            // 刷新完成后延迟一下再隐藏柯基
            isHiding = true
        } else {
            isHiding = false
        }
    }

    // 选择动画类型：下拉中/刷新中 → WAG(摇尾巴)，否则 → RUN(跑步)
    val animationType = if (isRefreshing || pullProgress >= 1f) {
        AnimationType.WAG  // 摇尾巴
    } else if (pullProgress > 0.1f) {
        AnimationType.RUN  // 跑步跑入
    } else {
        AnimationType.WAG  // 默认摇尾巴
    }

    val totalOffsetFraction = corgiOffsetXFraction + hideOffsetX

    // 仅在有下拉进度或刷新中时显示
    if (pullProgress > 0.01f || isRefreshing || isHiding) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (totalOffsetFraction * 300).dp)
                    .graphicsLayer {
                        alpha = 1f - hideOffsetX
                    },
                contentAlignment = Alignment.Center
            ) {
                // 柯基帧动画
                val frames = remember(animationType) {
                    AnimationResourceManager.getAnimationFrames(animationType)
                }

                if (frames.isNotEmpty()) {
                    FrameAnimation(
                        frames = frames,
                        fps = 8,
                        isLooping = true,
                        isPlaying = pullProgress > 0.1f || isRefreshing,
                        modifier = Modifier.size(64.dp)
                    )
                }

                // 刷新提示文字
                if (isRefreshing) {
                    Text(
                        text = "柯基努力加载中~",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.offset(y = 40.dp)
                    )
                } else if (pullProgress >= 1f) {
                    Text(
                        text = "释放刷新",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.offset(y = 40.dp)
                    )
                }
            }
        }
    }
}
