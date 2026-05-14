package com.corgimemo.app.animation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 帧动画播放状态
 */
enum class AnimationState {
    /** 播放中 */
    PLAYING,
    /** 已暂停 */
    PAUSED,
    /** 已停止 */
    STOPPED,
    /** 已完成（单次播放时） */
    FINISHED
}

/**
 * 帧动画播放组件
 * 支持循环播放和单次播放两种模式
 *
 * @param frames 动画帧资源 ID 列表
 * @param fps 帧率（每秒帧数），默认 15 FPS
 * @param isLooping 是否循环播放，默认 true
 * @param isPlaying 是否播放动画，默认 true
 * @param onFinished 动画完成回调（仅单次播放时触发）
 * @param modifier 修饰符
 * @param contentScale 内容缩放模式
 */
@Composable
fun FrameAnimation(
    frames: List<Int>,
    fps: Int = 15,
    isLooping: Boolean = true,
    isPlaying: Boolean = true,
    onFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    // 当前帧索引
    var currentFrameIndex by remember { mutableIntStateOf(0) }

    // 计算每帧的延迟时间（毫秒）
    val frameDelayMs = 1000L / fps

    // 帧切换逻辑
    if (isPlaying && frames.isNotEmpty()) {
        LaunchedEffect(isPlaying, frames, isLooping) {
            // 如果是单次播放且已经完成，不重新开始
            if (!isLooping && currentFrameIndex >= frames.size) {
                return@LaunchedEffect
            }

            // 重置帧索引
            currentFrameIndex = 0

            while (true) {
                // 等待一帧时间
                delay(frameDelayMs)

                // 播放下一帧
                currentFrameIndex = (currentFrameIndex + 1) % frames.size

                // 如果是单次播放且播放到最后一帧
                if (!isLooping && currentFrameIndex == frames.size - 1) {
                    // 等待最后一帧显示完成
                    delay(frameDelayMs)
                    // 触发完成回调
                    onFinished?.invoke()
                    break
                }
            }
        }
    }

    // 显示当前帧
    if (frames.isNotEmpty() && currentFrameIndex < frames.size) {
        Image(
            painter = painterResource(id = frames[currentFrameIndex]),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

/**
 * 简化版帧动画组件（使用 AnimationType）
 *
 * @param animationType 动画类型
 * @param fps 帧率
 * @param isLooping 是否循环播放
 * @param isPlaying 是否播放
 * @param onFinished 完成回调
 * @param modifier 修饰符
 * @param contentScale 内容缩放模式
 */
@Composable
fun FrameAnimation(
    animationType: AnimationType,
    fps: Int = 15,
    isLooping: Boolean = true,
    isPlaying: Boolean = true,
    onFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val frames = AnimationResourceManager.getAnimationFrames(animationType)

    FrameAnimation(
        frames = frames,
        fps = fps,
        isLooping = isLooping,
        isPlaying = isPlaying,
        onFinished = onFinished,
        modifier = modifier,
        contentScale = contentScale
    )
}
