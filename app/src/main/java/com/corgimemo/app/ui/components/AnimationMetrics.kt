package com.corgimemo.app.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

/**
 * 带帧耗时监控的 animateFloatAsState 包装器
 *
 * 在动画执行过程中追踪每帧的实际渲染耗时，
 * 超过阈值（默认 16ms = 60fps）时输出 Logcat 警告。
 *
 * **使用场景**:
 * - 列表项入场动画（alpha / translationY）
 * - 任何需要性能监控的 Compose 动画
 *
 * **Logcat 过滤**:
 * ```bash
 * adb logcat -s AnimationPerf
 * ```
 *
 * @param targetValue 动画目标值
 * @param animationSpec 动画规格（默认 spring）
 * @param label 监控标签，用于 Logcat 日志识别不同动画
 * @param warnThresholdMs 帧耗时警告阈值（毫秒），默认 16ms（60fps）
 * @return 与 animateFloatAsState 相同的 State<Float>
 */
@Composable
fun TrackedAnimateFloatAsState(
    targetValue: Float,
    animationSpec: AnimationSpec<Float> = spring(),
    label: String = "animation",
    warnThresholdMs: Float = 16f
): State<Float> {
    /** 记录当前帧开始时间（纳秒）*/
    val frameStartTime = remember { mutableLongStateOf(0L) }

    /**
     * 当 targetValue 变化时记录起始时间点
     *
     * 这标志着新一轮动画的开始，
     * 后续的 LaunchedEffect(state.value) 会检测首帧耗时。
     */
    LaunchedEffect(targetValue) {
        frameStartTime.value = System.nanoTime()
    }

    /**
     * 执行实际的动画计算
     *
     * 使用与原 animateFloatAsState 完全相同的参数，
     * 保证行为一致性，仅额外添加监控逻辑。
     */
    val state = animateFloatAsState(
        targetValue = targetValue,
        animationSpec = animationSpec,
        label = label
    )

    /**
     * 帧耗时检测
     *
     * 当 state.value 发生变化时（即新的一帧渲染完成），
     * 计算从 frameStartTime 到当前的 elapsed 时间，
     * 超过阈值则输出 Logcat 警告。
     *
     * 注意：LaunchedEffect 在 Compose 的 side-effect 阶段执行，
     * 能准确反映实际渲染管道的帧耗时。
     */
    LaunchedEffect(state.value) {
        val elapsedMs = (System.nanoTime() - frameStartTime.value) / 1_000_000f

        if (elapsedMs > warnThresholdMs) {
            android.util.Log.w(
                "AnimationPerf",
                "[$label] Frame took ${elapsedMs.toInt()}ms " +
                        "(> ${warnThresholdMs.toInt()}ms threshold, target=$targetValue)"
            )
        }
    }

    return state
}
