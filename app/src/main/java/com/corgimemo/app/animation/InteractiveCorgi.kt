package com.corgimemo.app.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.LinkedList

/**
 * 触摸互动类型
 */
enum class InteractionType {
    /** 单击 */
    SINGLE_CLICK,
    /** 双击 */
    DOUBLE_CLICK,
    /** 长按 */
    LONG_CLICK,
    /** 快速连点 */
    RAPID_TAPS
}

/**
 * 快速连点检测器
 * 检测 3 秒内 5 次点击
 */
class RapidTapDetector(
    private val windowMs: Long = 3000,
    private val threshold: Int = 5
) {
    private val tapTimestamps = LinkedList<Long>()

    /**
     * 记录点击时间，检测是否达到快速连点阈值
     * @return 是否触发快速连点
     */
    fun onTap(): Boolean {
        val now = System.currentTimeMillis()
        tapTimestamps.add(now)

        // 移除超过时间窗口的点击记录
        while (tapTimestamps.isNotEmpty() && now - tapTimestamps.first > windowMs) {
            tapTimestamps.removeFirst()
        }

        return tapTimestamps.size >= threshold
    }

    /**
     * 重置检测器
     */
    fun reset() {
        tapTimestamps.clear()
    }
}

/**
 * 获取等级对应的尺寸比例
 *
 * @param level 当前等级
 * @return 尺寸比例 (0.8 - 1.0)
 */
fun getSizeScaleForLevel(level: Int): Float {
    val stage = LevelManager.getLevelStage(level)
    return when (stage) {
        LevelStage.BABY -> 0.80f
        LevelStage.YOUTH -> 0.90f
        LevelStage.ADULT -> 1.0f
        LevelStage.MASTER -> 1.0f
    }
}

/**
 * 获取等级阶段对应的光晕效果
 *
 * @param level 当前等级
 * @return 是否显示特殊光晕
 */
fun hasGlowEffectForLevel(level: Int): Boolean {
    return level >= LevelStage.MASTER.minLevel
}

/**
 * 互动柯基组件
 * 整合姿态、动画、情绪、触摸互动、等级形态、装扮显示
 *
 * @param pose 当前姿态
 * @param mood 当前情绪
 * @param corgiName 柯基名字
 * @param level 当前等级
 * @param outfitId 当前装扮 ID
 * @param modifier 修饰符
 * @param onInteraction 互动回调
 */
@Composable
fun InteractiveCorgi(
    pose: CorgiPose = PoseManager.getDefaultPose(),
    mood: CorgiMood = CorgiMood.NORMAL,
    corgiName: String? = null,
    level: Int = 1,
    outfitId: String? = null,
    modifier: Modifier = Modifier,
    onInteraction: ((InteractionType) -> Unit)? = null
) {
    var isWinking by remember { mutableStateOf(false) }
    var isWagging by remember { mutableStateOf(false) }
    var isRolling by remember { mutableStateOf(false) }
    var showHearts by remember { mutableStateOf(false) }
    var isShy by remember { mutableStateOf(false) }

    val rapidTapDetector = remember { RapidTapDetector() }
    val shyOffsetX = remember { Animatable(0f) }

    val sizeScale = getSizeScaleForLevel(level)
    val baseSize = 120.dp
    val corgiSize = (baseSize.value * sizeScale).dp
    val hasGlow = hasGlowEffectForLevel(level)

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFF9A5C),
            Color(0xFFFFB366)
        )
    )

    val greeting = GreetingManager.getGreeting(mood, corgiName)

    LaunchedEffect(isShy) {
        if (isShy) {
            shyOffsetX.animateTo(
                targetValue = 200f,
                animationSpec = tween(durationMillis = 500)
            )
            delay(2000)
            shyOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500)
            )
            isShy = false
        }
    }

    LaunchedEffect(isWagging) {
        if (isWagging) {
            delay(1500)
            isWagging = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val rapid = rapidTapDetector.onTap()
                        if (rapid) {
                            isShy = true
                            onInteraction?.invoke(InteractionType.RAPID_TAPS)
                            rapidTapDetector.reset()
                        } else {
                            isWinking = true
                            isWagging = true
                            onInteraction?.invoke(InteractionType.SINGLE_CLICK)
                        }
                    },
                    onDoubleTap = {
                        isRolling = true
                        onInteraction?.invoke(InteractionType.DOUBLE_CLICK)
                    },
                    onLongPress = {
                        showHearts = true
                        onInteraction?.invoke(InteractionType.LONG_CLICK)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(gradient)
        )

        if (hasGlow) {
            Box(
                modifier = Modifier
                    .size(corgiSize + 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD700).copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = shyOffsetX.value.toInt(),
                        y = 0
                    )
                }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (outfitId != null && outfitId != com.corgimemo.app.animation.OutfitId.DEFAULT) {
                OutfitIcon(
                    outfitId = outfitId,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(bottom = 8.dp)
                )
            }

            PoseAnimation(
                pose = if (isRolling) pose else if (isShy) CorgiPose.SIT else pose,
                modifier = Modifier.size(corgiSize)
            )

            AnimatedVisibility(
                visible = isWinking,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FrameAnimation(
                    animationType = AnimationType.WINK,
                    isLooping = false,
                    fps = 10,
                    modifier = Modifier.size(corgiSize),
                    onFinished = { isWinking = false }
                )
            }

            AnimatedVisibility(
                visible = isWagging,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FrameAnimation(
                    animationType = AnimationType.WAG,
                    isLooping = true,
                    fps = 12,
                    modifier = Modifier.size(corgiSize)
                )
            }

            AnimatedVisibility(
                visible = isRolling,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FrameAnimation(
                    animationType = AnimationType.ROLL,
                    isLooping = false,
                    fps = 8,
                    modifier = Modifier.size(corgiSize),
                    onFinished = { isRolling = false }
                )
            }

            AnimatedVisibility(
                visible = isShy,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FrameAnimation(
                    animationType = AnimationType.SHY,
                    modifier = Modifier.size(corgiSize)
                )
            }

            HeartParticleEffect(
                isActive = showHearts,
                modifier = Modifier.size(corgiSize + 30.dp)
            )
        }

        Text(
            text = corgiName ?: "未命名",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )

        Text(
            text = greeting,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * 装扮图标组件
 * 根据装扮 ID 显示对应的装扮图标
 *
 * @param outfitId 装扮 ID
 * @param modifier 修饰符
 */
@Composable
fun OutfitIcon(
    outfitId: String,
    modifier: Modifier = Modifier
) {
    val icon = when (outfitId) {
        com.corgimemo.app.animation.OutfitId.SCHOLAR_HAT -> "🎓"
        com.corgimemo.app.animation.OutfitId.TIE -> "👔"
        com.corgimemo.app.animation.OutfitId.CROWN -> "👑"
        com.corgimemo.app.animation.OutfitId.ANGEL_WINGS -> "🪽"
        com.corgimemo.app.animation.OutfitId.CAPE -> "🧥"
        else -> null
    }

    icon?.let {
        Text(
            text = it,
            fontSize = 28.sp,
            modifier = modifier
        )
    }
}
