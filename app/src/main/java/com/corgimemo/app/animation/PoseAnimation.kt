package com.corgimemo.app.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * 姿态动画组件
 * 支持姿态切换和平滑过渡
 *
 * @param pose 当前姿态
 * @param fps 帧率
 * @param isLooping 是否循环播放
 * @param modifier 修饰符
 * @param transitionDuration 过渡动画时长（毫秒）
 */
@Composable
fun PoseAnimation(
    pose: CorgiPose,
    fps: Int = 15,
    isLooping: Boolean = true,
    modifier: Modifier = Modifier,
    transitionDuration: Int = 300
) {
    // 获取姿态对应的动画类型
    val animationType = AnimationResourceManager.getAnimationForPose(pose)

    // 使用 AnimatedContent 实现姿态切换的平滑过渡
    AnimatedContent(
        targetState = animationType,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "PoseAnimation"
    ) { currentAnimationType ->
        FrameAnimation(
            animationType = currentAnimationType,
            fps = fps,
            isLooping = isLooping,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 带叠加动画的姿态组件
 * 支持基础姿态 + 叠加动作（如眨眼、摇尾巴）
 *
 * @param basePose 基础姿态
 * @param overlayAnimation 叠加动画（可选）
 * @param fps 帧率
 * @param modifier 修饰符
 */
@Composable
fun PoseAnimationWithOverlay(
    basePose: CorgiPose,
    overlayAnimation: AnimationType? = null,
    fps: Int = 15,
    modifier: Modifier = Modifier
) {
    var showOverlay by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        // 基础姿态动画
        PoseAnimation(
            pose = basePose,
            fps = fps,
            modifier = Modifier.matchParentSize()
        )

        // 叠加动画（如眨眼、摇尾巴）
        if (showOverlay && overlayAnimation != null) {
            FrameAnimation(
                animationType = overlayAnimation,
                fps = fps,
                isLooping = true,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/**
 * 简化版姿态显示组件
 * 自动根据时间选择默认姿态
 *
 * @param fps 帧率
 * @param modifier 修饰符
 */
@Composable
fun AutoPoseAnimation(
    fps: Int = 15,
    modifier: Modifier = Modifier
) {
    val defaultPose = PoseManager.getDefaultPose()

    PoseAnimation(
        pose = defaultPose,
        fps = fps,
        modifier = modifier
    )
}
