package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.CorgiPose
import com.corgimemo.app.animation.OutfitIcon
import com.corgimemo.app.animation.PoseAnimation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 柯基桌宠状态枚举
 *
 * 状态流转：
 * - IDLE  → 5-10s 后 → WALKING → 到达 → IDLE
 * - IDLE  → 30s 无互动 → SLEEPING
 * - 任意  → 单击 → PETTING → 1s → IDLE
 * - 任意  → 双击 → EATING  → 1.5s → IDLE
 * - 任意  → 长按 500ms → MENU_OPEN
 * - 任意  → 长按 100ms + 移动 → DRAGGING → 抬手 → IDLE
 * - MENU_OPEN → 玩耍按钮 → PLAYING → 1.2s → IDLE
 * - MENU_OPEN → 睡觉按钮 → SLEEPING
 */
enum class CorgiPetState {
    /** 待机：原地呼吸，5-10s 后随机走动 */
    IDLE,
    /** 走动：朝目标点平滑移动 800-1500ms */
    WALKING,
    /** 睡觉：30s 无互动后进入 */
    SLEEPING,
    /** 抚摸中：单击触发，1000ms 后恢复 */
    PETTING,
    /** 喂食中：双击触发，1500ms 后恢复 */
    EATING,
    /** 玩耍中：菜单触发，1200ms 后恢复 */
    PLAYING,
    /** 拖动中：跟随手指 */
    DRAGGING,
    /** 菜单打开：长按 500ms 触发 */
    MENU_OPEN
}

/**
 * 柯基桌宠组件
 *
 * 让柯基以桌宠形式活动在整个页面上：
 * - 自主走动：IDLE 5-10s 后随机选目标点 WALKING，到达后回到 IDLE
 * - 长时间无互动：30s 无互动进入 SLEEPING
 * - 单击：PETTING（抚摸，摇尾巴 1s）
 * - 双击：EATING（喂食 1.5s）
 * - 长按 500ms：MENU_OPEN（弹出 4 按钮：抚摸/喂食/玩耍/睡觉）
 * - 拖动：长按 100ms + 移动 → DRAGGING，跟随手指
 *
 * 外层 Box 用 fillMaxSize 覆盖整页，仅柯基本体(96dp)消耗事件，
 * 其他区域事件穿透到下层 UI。
 *
 * @param pose 默认 pose（用于 IDLE / MENU_OPEN）
 * @param mood 当前情绪（暂不深度使用，保留接口）
 * @param outfitId 装扮 ID（null 或 "default" 时不渲染装扮）
 * @param soundEnabled 音效开关（暂未接入，保留接口）
 * @param hapticEnabled 触觉反馈开关（暂未接入，保留接口）
 * @param onPet 单击抚摸回调
 * @param onFeed 双击喂食回调
 * @param onPlay 菜单玩耍回调
 * @param onSleep 菜单睡觉回调
 * @param onShowSnackbar Snackbar 提示回调
 * @param modifier 修饰符（建议 fillMaxSize）
 */
@Composable
fun CorgiDesktopPet(
    pose: CorgiPose,
    mood: CorgiMood,
    outfitId: String?,
    soundEnabled: Boolean = true,
    hapticEnabled: Boolean = true,
    onPet: () -> Unit = {},
    onFeed: () -> Unit = {},
    onPlay: () -> Unit = {},
    onSleep: () -> Unit = {},
    onShowSnackbar: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val corgiSizeDp = 96.dp
        val corgiSizePx = with(density) { corgiSizeDp.toPx() }

        // ===== 状态 =====
        var petState by remember { mutableStateOf(CorgiPetState.IDLE) }
        var facingRight by remember { mutableStateOf(true) }
        var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var lastTapTime by remember { mutableLongStateOf(0L) }
        var initialized by remember { mutableStateOf(false) }

        // ===== 位置（Animatable 支持平滑动画 + snapTo 即时跳转）=====
        val positionX = remember { Animatable(0f) }
        val positionY = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()

        // ===== 初始化位置：屏幕底部中央 =====
        LaunchedEffect(screenWidth, screenHeight) {
            if (!initialized && screenWidth > 0 && screenHeight > 0) {
                positionX.snapTo(screenWidth / 2f - corgiSizePx / 2f)
                positionY.snapTo(screenHeight - corgiSizePx - 32f) // 底部留 32dp "地面"间距
                initialized = true
            }
        }

        // ===== 自动行为循环（IDLE 5-10s → WALKING → IDLE；30s 无互动 → SLEEPING）=====
        LaunchedEffect(initialized) {
            if (!initialized) return@LaunchedEffect
            while (true) {
                delay(500) // 检查频率
                val now = System.currentTimeMillis()
                val idleTime = now - lastInteractionTime

                // 这些状态不自动转换，等待手动结束
                if (petState in setOf(
                        CorgiPetState.DRAGGING,
                        CorgiPetState.MENU_OPEN,
                        CorgiPetState.PETTING,
                        CorgiPetState.EATING,
                        CorgiPetState.PLAYING
                    )
                ) continue

                when {
                    // 30s 无互动 → SLEEPING
                    petState == CorgiPetState.IDLE && idleTime > 30000 -> {
                        petState = CorgiPetState.SLEEPING
                    }
                    // IDLE 5-10s 后随机走动
                    petState == CorgiPetState.IDLE && idleTime > 5000 -> {
                        // 再等 0-5s 随机化（总等待 5-10s）
                        delay(Random.nextLong(0, 5000))
                        if (petState != CorgiPetState.IDLE) continue

                        petState = CorgiPetState.WALKING
                        val targetX = Random.nextFloat() * (screenWidth - corgiSizePx)
                        // y 限制在屏幕中部到底部之间（避免柯基走到 TopAppBar 区域）
                        val targetY = Random.nextFloat() * (screenHeight * 0.7f) + screenHeight * 0.2f - corgiSizePx / 2f
                        facingRight = targetX >= positionX.value
                        val duration = 800 + Random.nextInt(700)

                        coroutineScope.launch {
                            positionX.animateTo(targetX, tween(duration))
                        }
                        coroutineScope.launch {
                            positionY.animateTo(targetY.coerceIn(0f, screenHeight - corgiSizePx), tween(duration))
                        }
                        delay(duration.toLong())

                        if (petState == CorgiPetState.WALKING) {
                            petState = CorgiPetState.IDLE
                            // 走完算"互动"，重置 30s 计时
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    }
                }
            }
        }

        // ===== 互动状态自动恢复（PETTING/EATING/PLAYING 各自时长后回到 IDLE）=====
        LaunchedEffect(petState) {
            when (petState) {
                CorgiPetState.PETTING -> {
                    delay(1000)
                    if (petState == CorgiPetState.PETTING) {
                        petState = CorgiPetState.IDLE
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
                CorgiPetState.EATING -> {
                    delay(1500)
                    if (petState == CorgiPetState.EATING) {
                        petState = CorgiPetState.IDLE
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
                CorgiPetState.PLAYING -> {
                    delay(1200)
                    if (petState == CorgiPetState.PLAYING) {
                        petState = CorgiPetState.IDLE
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
                else -> {}
            }
        }

        // ===== 菜单打开时：透明遮罩捕获菜单外点击 =====
        // 声明在柯基 Box 之前 → 渲染在柯基下方 → 不阻挡菜单按钮
        if (petState == CorgiPetState.MENU_OPEN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val first = awaitFirstDown()
                            first.consume()
                            // 点击菜单外 → 关闭菜单，回到 IDLE
                            petState = CorgiPetState.IDLE
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    }
            )
        }

        // ===== 桌宠本体（96dp + offset + 手势检测）=====
        if (initialized) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(positionX.value.toInt(), positionY.value.toInt()) }
                    .size(corgiSizeDp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val first = awaitFirstDown()
                            first.consume()
                            val downTime = System.currentTimeMillis()
                            val downPos = first.position

                            var isDragging = false
                            var isLongPressTriggered = false
                            var dragStartX = 0f
                            var dragStartY = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val active = event.changes.filter { it.pressed }

                                if (active.isEmpty()) {
                                    // 抬手：所有手指离开
                                    val upTime = System.currentTimeMillis()
                                    val pressDuration = upTime - downTime

                                    if (!isDragging && !isLongPressTriggered) {
                                        if (pressDuration < 200) {
                                            // 短按 → 单击/双击判定
                                            if (upTime - lastTapTime < 300) {
                                                // 双击：喂食
                                                petState = CorgiPetState.EATING
                                                onFeed()
                                                lastTapTime = 0L
                                                onShowSnackbar("🍖 柯基吃得好香~")
                                            } else {
                                                // 单击：抚摸
                                                petState = CorgiPetState.PETTING
                                                onPet()
                                                lastTapTime = upTime
                                                onShowSnackbar("🤚 柯基好开心~")
                                            }
                                        }
                                    } else if (isDragging) {
                                        // 拖动结束 → IDLE
                                        petState = CorgiPetState.IDLE
                                        onShowSnackbar("📍 柯基被放到新位置啦~")
                                    }

                                    lastInteractionTime = upTime
                                    break
                                }

                                val change = active.first()
                                val now = System.currentTimeMillis()
                                val moved = (change.position - downPos).getDistance()

                                // 长按 500ms + 不动 → MENU_OPEN
                                if (!isLongPressTriggered && !isDragging &&
                                    now - downTime > 500 && moved < 8f
                                ) {
                                    isLongPressTriggered = true
                                    petState = CorgiPetState.MENU_OPEN
                                }

                                // 长按 100ms + 移动 → DRAGGING
                                if (!isDragging && now - downTime > 100 && moved > 8f) {
                                    isDragging = true
                                    dragStartX = positionX.value
                                    dragStartY = positionY.value
                                    petState = CorgiPetState.DRAGGING
                                }

                                if (isDragging) {
                                    val dx = change.position.x - downPos.x
                                    val dy = change.position.y - downPos.y
                                    val newX = (dragStartX + dx).coerceIn(0f, screenWidth - corgiSizePx)
                                    val newY = (dragStartY + dy).coerceIn(0f, screenHeight - corgiSizePx)
                                    // snapTo 是 suspend，awaitEachGesture 是 RestrictedSuspendingFunction
                                    // 不能直接调用外部 suspend 函数，需通过 coroutineScope.launch 启动新协程
                                    coroutineScope.launch { positionX.snapTo(newX) }
                                    coroutineScope.launch { positionY.snapTo(newY) }
                                }

                                change.consume()
                            }
                        }
                    }
            ) {
                // 柯基本体（带朝向翻转；WALKING/RUN 朝向运动方向）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = if (facingRight) 1f else -1f },
                    contentAlignment = Alignment.Center
                ) {
                    // 当前姿态映射
                    val currentPose = when (petState) {
                        CorgiPetState.SLEEPING -> CorgiPose.SLEEP
                        CorgiPetState.PETTING -> CorgiPose.SIT
                        CorgiPetState.EATING -> CorgiPose.SIT
                        CorgiPetState.PLAYING -> CorgiPose.RUN
                        CorgiPetState.WALKING -> CorgiPose.RUN
                        CorgiPetState.DRAGGING -> CorgiPose.STAND
                        else -> pose // IDLE / MENU_OPEN 用默认 pose
                    }

                    PoseAnimation(
                        pose = currentPose,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 装扮（不翻转，叠在柯基顶部）
                if (outfitId != null && outfitId != "default") {
                    OutfitIcon(
                        outfitId = outfitId,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 0.dp)
                    )
                }

                // 状态气泡（不翻转，相对柯基上方显示）
                when (petState) {
                    CorgiPetState.SLEEPING -> {
                        StatusBubble(
                            text = "Zzz",
                            color = Color(0xFF666666),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 20.dp, y = (-10).dp)
                        )
                    }
                    CorgiPetState.PETTING -> {
                        StatusBubble(
                            text = "❤️",
                            color = Color.Transparent,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 20.dp, y = (-10).dp)
                        )
                    }
                    CorgiPetState.EATING -> {
                        StatusBubble(
                            text = "🍖",
                            color = Color.Transparent,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 20.dp, y = (-10).dp)
                        )
                    }
                    CorgiPetState.PLAYING -> {
                        StatusBubble(
                            text = "🎾",
                            color = Color.Transparent,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 20.dp, y = (-10).dp)
                        )
                    }
                    else -> {}
                }

                // 长按菜单（MENU_OPEN 时浮在柯基上方）
                if (petState == CorgiPetState.MENU_OPEN) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-64).dp)
                            .background(Color.White, RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(20.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MenuButton("🤚", "抚摸") {
                            petState = CorgiPetState.PETTING
                            onPet()
                            onShowSnackbar("🤚 柯基好开心~")
                        }
                        MenuButton("🍖", "喂食") {
                            petState = CorgiPetState.EATING
                            onFeed()
                            onShowSnackbar("🍖 柯基吃得好香~")
                        }
                        MenuButton("🎾", "玩耍") {
                            petState = CorgiPetState.PLAYING
                            onPlay()
                            onShowSnackbar("🎾 柯基玩得好欢~")
                        }
                        MenuButton("💤", "睡觉") {
                            petState = CorgiPetState.SLEEPING
                            onSleep()
                            // 强制立即进入睡眠：把 lastInteractionTime 设为很久以前
                            lastInteractionTime = 0L
                        }
                    }
                }
            }
        }
    }
}

/**
 * 状态气泡（小标签或纯 emoji）
 *
 * @param text 显示文字
 * @param color 背景色（Color.Transparent 表示无背景）
 * @param modifier 修饰符（含 align + offset）
 */
@Composable
private fun StatusBubble(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (color == Color.Transparent) {
        Text(
            text = text,
            fontSize = 16.sp,
            modifier = modifier
        )
    } else {
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .background(color, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 菜单按钮（圆形 emoji + 标签）
 *
 * @param emoji emoji 图标
 * @param label 按钮文字
 * @param onClick 点击回调
 */
@Composable
private fun MenuButton(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = Color(0xFFFF9A5C).copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = emoji, fontSize = 16.sp)
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF666666)
        )
    }
}
