package com.corgimemo.app.animation

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 触觉反馈管理器
 * 负责处理不同类型的震动反馈
 */
object HapticFeedbackManager {
    /**
     * 执行触觉反馈
     *
     * @param context Android 上下文
     * @param type 交互类型
     * @param enabled 是否启用触觉反馈
     */
    fun performHapticFeedback(
        context: Context,
        type: InteractionType,
        enabled: Boolean = true
    ) {
        if (!enabled) return

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            when (type) {
                InteractionType.SINGLE_CLICK -> {
                    // 短震动 50ms
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                50,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(50)
                    }
                }
                InteractionType.DOUBLE_CLICK -> {
                    // 中等震动 100ms
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                100,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
                    }
                }
                InteractionType.LONG_CLICK -> {
                    // 脉冲震动：等待50ms + 震动50ms + 等待50ms + 震动50ms + 等待50ms + 震动50ms
                    val pattern = longArrayOf(50, 50, 50, 50, 50, 50)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(pattern, -1)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                }
                InteractionType.TASK_COMPLETE -> {
                    // 双短震动：等待100ms + 震动50ms + 等待100ms + 震动50ms
                    val pattern = longArrayOf(100, 50, 100, 50)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(pattern, -1)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                }
                InteractionType.ACHIEVEMENT_UNLOCK -> {
                    // 长震动 200ms
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                200,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(200)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * 音效管理器
 * 负责加载和播放音效
 */
class SoundFeedbackManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var wagSoundId: Int = 0
    private var soundLoaded: Boolean = false

    init {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                soundPool = SoundPool.Builder()
                    .setMaxStreams(5)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
            } else {
                @Suppress("DEPRECATION")
                soundPool = SoundPool(5, android.media.AudioManager.STREAM_MUSIC, 0)
            }

            soundPool?.setOnLoadCompleteListener { _, _, status ->
                soundLoaded = status == 0
            }

            // 尝试加载音效资源（如果资源不存在则忽略）
            try {
                val resId = context.resources.getIdentifier(
                    "wag_sound",
                    "raw",
                    context.packageName
                )
                if (resId != 0) {
                    wagSoundId = soundPool?.load(context, resId, 1) ?: 0
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 播放汪音效
     */
    fun playWagSound(enabled: Boolean = true) {
        if (!enabled || !soundLoaded || wagSoundId == 0) return

        try {
            soundPool?.play(wagSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            soundPool?.release()
            soundPool = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

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
    /** 任务完成 */
    TASK_COMPLETE,
    /** 成就解锁 */
    ACHIEVEMENT_UNLOCK
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
 * 可用于随机动画的动画类型列表
 * 排除 LIE（趴卧），因为它是默认姿态
 */
private val INTERACTION_ANIMATIONS = listOf(
    AnimationType.WINK,   // 眨眼
    AnimationType.WAG,    // 摇尾巴
    AnimationType.TILT,   // 歪头
    AnimationType.SLEEP,  // 睡觉
    AnimationType.SAD,    // 悲伤
    AnimationType.PROUD,  // 骄傲
    AnimationType.SHY,    // 害羞
    AnimationType.WORRY,  // 担心
    AnimationType.RUN,    // 跑步
    AnimationType.ROLL,   // 打滚
    AnimationType.SIT,    // 坐立
    AnimationType.STAND   // 站立
)

/**
 * 随机选择一个动画类型
 */
private fun getRandomAnimation(): AnimationType {
    val index = Random.nextInt(INTERACTION_ANIMATIONS.size)
    return INTERACTION_ANIMATIONS[index]
}

/**
 * 随机选择两个不同的动画类型
 */
private fun getTwoRandomAnimations(): Pair<AnimationType, AnimationType> {
    val index1 = Random.nextInt(INTERACTION_ANIMATIONS.size)
    var index2 = Random.nextInt(INTERACTION_ANIMATIONS.size)
    // 确保两个动画不同
    while (index2 == index1) {
        index2 = Random.nextInt(INTERACTION_ANIMATIONS.size)
    }
    return Pair(INTERACTION_ANIMATIONS[index1], INTERACTION_ANIMATIONS[index2])
}

/**
 * 互动柯基组件
 * 整合姿态、动画、情绪、触摸互动、等级形态、装扮显示
 * 动画互斥：上一个动画要结束，不能影响下一个动画
 *
 * 触摸交互：
 * - 单击：随机播放一种动画 + 短震动 + 汪音效
 * - 双击：随机播放两种动画组合 + 中等震动 + 汪音效
 * - 长按：打滚动画循环 + 位置随机变动 + 脉冲震动 + 汪音效
 *
 * @param pose 当前姿态
 * @param mood 当前情绪
 * @param corgiName 柯基名字
 * @param level 当前等级
 * @param outfitId 当前装扮 ID
 * @param modifier 修饰符
 * @param onInteraction 互动回调
 * @param onLongPress 长按专用回调（用于快速换装等）
 * @param soundEnabled 音效开关
 * @param hapticEnabled 触觉反馈开关
 */
@Composable
fun InteractiveCorgi(
    pose: CorgiPose = PoseManager.getDefaultPose(),
    mood: CorgiMood = CorgiMood.NORMAL,
    corgiName: String? = null,
    level: Int = 1,
    outfitId: String? = null,
    modifier: Modifier = Modifier,
    onInteraction: ((InteractionType) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    soundEnabled: Boolean = true,
    hapticEnabled: Boolean = true,
    showText: Boolean = true
) {
    val context = LocalContext.current

    // 创建音效管理器
    val soundManager = remember(context) {
        SoundFeedbackManager(context)
    }

    // 释放音效资源
    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
        }
    }

    // 执行触觉反馈的函数
    fun triggerHaptic(type: InteractionType) {
        HapticFeedbackManager.performHapticFeedback(context, type, hapticEnabled)
    }

    // 播放音效的函数
    fun triggerSound() {
        soundManager.playWagSound(soundEnabled)
    }

    // 单个动画播放状态
    var isPlayingSingle by remember { mutableStateOf(false) }
    var singleAnimationType by remember { mutableStateOf<AnimationType?>(null) }

    // 双击动画播放状态
    var isPlayingDouble by remember { mutableStateOf(false) }
    var doubleAnimation1 by remember { mutableStateOf<AnimationType?>(null) }
    var doubleAnimation2 by remember { mutableStateOf<AnimationType?>(null) }
    var currentDoubleIndex by remember { mutableStateOf(0) }

    // 长按状态
    var isLongPressing by remember { mutableStateOf(false) }

    // 位置偏移（用于长按时的随机位置变动）
    val randomOffsetX = remember { Animatable(0f) }
    val randomOffsetY = remember { Animatable(0f) }

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

    // 单个动画播放逻辑
    LaunchedEffect(isPlayingSingle, singleAnimationType) {
        if (isPlayingSingle && singleAnimationType != null) {
            // 播放2秒后结束
            delay(2000)
            isPlayingSingle = false
            singleAnimationType = null
        }
    }

    // 双击动画播放逻辑
    LaunchedEffect(isPlayingDouble, currentDoubleIndex) {
        if (isPlayingDouble) {
            // 每个动画播放1.5秒
            delay(1500)
            if (currentDoubleIndex == 0) {
                // 第一个动画播放完，切换到第二个
                currentDoubleIndex = 1
            } else {
                // 第二个动画播放完，结束
                isPlayingDouble = false
                doubleAnimation1 = null
                doubleAnimation2 = null
                currentDoubleIndex = 0
            }
        }
    }

    // 长按位置随机变动逻辑
    LaunchedEffect(isLongPressing) {
        if (isLongPressing) {
            // 长按时，每隔1秒随机变动一次位置
            while (isLongPressing) {
                // 随机生成新的偏移位置
                // X轴：±150dp，Y轴：±50dp
                val targetX = (Random.nextFloat() - 0.5f) * 300f  // ±150dp
                val targetY = (Random.nextFloat() - 0.5f) * 100f  // ±50dp

                // 平滑移动到新位置（300ms动画）
                randomOffsetX.animateTo(
                    targetValue = targetX,
                    animationSpec = tween(durationMillis = 300)
                )
                randomOffsetY.animateTo(
                    targetValue = targetY,
                    animationSpec = tween(durationMillis = 300)
                )

                // 等待1秒后再变动
                delay(1000)
            }
        } else {
            // 长按结束，平滑返回原始位置
            randomOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500)
            )
            randomOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500)
            )
        }
    }

    // 停止所有动画的函数
    fun stopAllAnimations() {
        isPlayingSingle = false
        singleAnimationType = null
        isPlayingDouble = false
        doubleAnimation1 = null
        doubleAnimation2 = null
        currentDoubleIndex = 0
        isLongPressing = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // 停止其他动画
                        stopAllAnimations()

                        // 触觉反馈：短震动 50ms
                        triggerHaptic(InteractionType.SINGLE_CLICK)

                        // 音效反馈：播放汪音效
                        triggerSound()

                        // 单击：随机播放一种动画
                        val randomAnim = getRandomAnimation()
                        singleAnimationType = randomAnim
                        isPlayingSingle = true
                        onInteraction?.invoke(InteractionType.SINGLE_CLICK)
                    },
                    onDoubleTap = {
                        // 停止其他动画
                        stopAllAnimations()

                        // 触觉反馈：中等震动 100ms
                        triggerHaptic(InteractionType.DOUBLE_CLICK)

                        // 音效反馈：播放汪音效
                        triggerSound()

                        // 双击：随机播放两种动画组合
                        val (anim1, anim2) = getTwoRandomAnimations()
                        doubleAnimation1 = anim1
                        doubleAnimation2 = anim2
                        currentDoubleIndex = 0
                        isPlayingDouble = true
                        onInteraction?.invoke(InteractionType.DOUBLE_CLICK)
                    },
                    onLongPress = {
                        // 停止其他动画
                        stopAllAnimations()

                        // 触觉反馈：脉冲震动
                        triggerHaptic(InteractionType.LONG_CLICK)

                        // 音效反馈：播放汪音效
                        triggerSound()

                        // 长按：打滚动画循环 + 位置随机变动
                        isLongPressing = true
                        onInteraction?.invoke(InteractionType.LONG_CLICK)

                        // 调用长按专用回调（用于快速换装等）
                        onLongPress?.invoke()
                    },
                    onPress = {
                        // 等待释放事件来检测长按结束
                        val released = tryAwaitRelease()
                        if (released && isLongPressing) {
                            // 长按结束
                            isLongPressing = false
                        }
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
                        x = randomOffsetX.value.toInt(),
                        y = randomOffsetY.value.toInt()
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

            // 判断是否有交互动画正在播放
            val hasInteractionAnimation = isPlayingSingle || isPlayingDouble || isLongPressing

            // 基础姿态动画（只有在没有交互动画播放时才显示）
            if (!hasInteractionAnimation) {
                PoseAnimation(
                    pose = pose,
                    modifier = Modifier.size(corgiSize)
                )
            }

            // 单击动画（随机选择的一种动画）
            AnimatedVisibility(
                visible = isPlayingSingle && singleAnimationType != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                singleAnimationType?.let { animType ->
                    FrameAnimation(
                        animationType = animType,
                        isLooping = true,
                        fps = 8,
                        modifier = Modifier.size(corgiSize)
                    )
                }
            }

            // 双击动画（两种动画组合）
            AnimatedVisibility(
                visible = isPlayingDouble,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val currentAnim = if (currentDoubleIndex == 0) doubleAnimation1 else doubleAnimation2
                currentAnim?.let { animType ->
                    FrameAnimation(
                        animationType = animType,
                        isLooping = true,
                        fps = 8,
                        modifier = Modifier.size(corgiSize)
                    )
                }
            }

            // 长按动画（打滚循环）
            AnimatedVisibility(
                visible = isLongPressing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FrameAnimation(
                    animationType = AnimationType.ROLL,
                    isLooping = true,
                    fps = 8,
                    modifier = Modifier.size(corgiSize)
                )
            }
        }

        if (showText) {
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
