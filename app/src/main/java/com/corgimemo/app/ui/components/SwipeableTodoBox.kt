package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.corgimemo.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 内容区视觉指示器开关（默认 true = 显示水波纹）
 *
 * 用途：SwipeableTodoBox 内部用 CompositionLocalProvider 覆盖此值为 false，
 * 使 TodoListItem 在左滑时不显示 indication 水波纹（避免与外层左滑手势
 * 冲突造成的视觉干扰）。
 *
 * 注：此 Local 仅控制"是否使用默认 indication"，不影响操作层按钮的
 * clickable 反馈（操作层使用自己的 indication）。
 */
val LocalContentIndication = staticCompositionLocalOf { true }

/**
 * 弹性缓动函数（对应 Web 原型 cubic-bezier(0.34, 1.56, 0.64, 1)）
 * 使用 easeOutBack 数学模型实现回弹效果
 */
val ElasticOutEasing: Easing = Easing { fraction ->
    val c1 = 1.56f
    val c3 = c1 + 1f
    1f + c3 * Math.pow(fraction - 1.0, 3.0).toFloat() +
        c1 * Math.pow(fraction - 1.0, 2.0).toFloat()
}

/**
 * 左滑操作按钮的语义动作类型
 *
 * 由 SwipeButtonConfig 持有，SwipeableTodoBox 根据此类型路由到对应回调。
 * 这样调用方（如日期页）可注入自定义按钮列表而不必遵循原 label 字符串约定。
 *
 * 可见性：必须为 public，因为 SwipeableTodoBox 是 public 函数，
 * 其参数类型 List<SwipeButtonConfig> 间接依赖本枚举。
 */
enum class SwipeActionType { SHARE, PIN, ARCHIVE, UNARCHIVE, DELETE }

/**
 * 左滑操作按钮配置
 *
 * 可见性：必须为 public，因为 SwipeableTodoBox 是 public 函数，
 * 其参数类型 List<SwipeButtonConfig> 暴露了本类。
 *
 * @param label 按钮文字
 * @param backgroundColorRes 背景色资源 ID
 * @param icon Material 图标
 * @param zIndex z-index 值（从左到右递减：分享=3, 置顶=2, 删除=1）
 * @param shape 按钮的圆角形状
 * @param actionType 按钮的语义动作（用于路由到对应回调）
 */
data class SwipeButtonConfig(
    val label: String,
    val backgroundColorRes: Int,
    val icon: ImageVector,
    val zIndex: Float,
    val shape: RoundedCornerShape,
    val actionType: SwipeActionType
)

/**
 * 待办页默认按钮配置（分享→置顶→删除）
 * 抽出为顶层函数，让 SwipeableTodoBox 在 customButtons == null 时复用
 *
 * @param isPinned 当前是否已置顶（决定置顶按钮文字/图标）
 * @param cornerRadiusDp 圆角 dp
 */
internal fun defaultTodoButtons(
    isPinned: Boolean,
    cornerRadiusDp: androidx.compose.ui.unit.Dp = 16.dp
): List<SwipeButtonConfig> = listOf(
    SwipeButtonConfig(
        "分享", R.color.ui_swipe_share, Icons.Outlined.Share, 3f,
        RoundedCornerShape(topStart = cornerRadiusDp, bottomStart = cornerRadiusDp),
        SwipeActionType.SHARE
    ),
    SwipeButtonConfig(
        label = if (isPinned) "取消置顶" else "置顶",
        backgroundColorRes = R.color.ui_primary,
        icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
        zIndex = 2f,
        shape = RoundedCornerShape(0.dp),
        actionType = SwipeActionType.PIN
    ),
    SwipeButtonConfig(
        "删除", R.color.ui_swipe_delete, Icons.Outlined.Delete, 1f,
        RoundedCornerShape(topEnd = cornerRadiusDp, bottomEnd = cornerRadiusDp),
        SwipeActionType.DELETE
    )
)

/**
 * 可左滑展开操作区的容器组件（飞书风格级联重叠堆叠动效）
 *
 * 将待办卡片作为 content 传入，自动获得左滑操作能力。
 * 按钮顺序：分享 → 置顶 → 删除（从左到右）
 * 动画参数：duration=300ms, staggerRatio=0, thresholdRatio=0.20, ElasticOutEasing
 *
 * @param modifier 修饰符
 * @param isEnabled 是否启用左滑（批量模式或 disabled 状态下设为 false）
 * @param isExpanded 是否处于展开状态（父组件控制互斥）
 * @param isPinned 当前待办是否已置顶（用于动态切换置顶按钮文字与图标）
 * @param onExpandChange 展开状态变化回调（true=展开, false=收起）
 * @param onShareClick 分享按钮回调
 * @param onPinClick 置顶按钮回调
 * @param onDeleteClick 删除按钮回调
 * @param durationMs 动画时长（默认 300ms）
 * @param staggerRatio 级联延迟比例（默认 0.00，同步移动）
 * @param thresholdRatio 吸附比例（默认 0.20）
 * @param easing 缓动函数（默认弹性效果，对应 Web 原型 cubic-bezier(0.34, 1.56, 0.64, 1)）
 * @param contentPadding 内容区内边距（v2026-07-20 新增）
 *        - 默认 PaddingValues(horizontal=0.dp, vertical=6.dp)
 *        - 给 Card 的 Modifier.shadow 预留空间，否则阴影会被外层 .clip(16.dp) 裁切
 *        - vertical 6dp 至少要 ≥ shadow.elevation 才能完整显示阴影
 *          （TodoListItem 默认 shadow=4dp / 长按 8dp，6dp vertical 留 4dp 给默认阴影、1dp 给长按阴影溢出）
 *        - horizontal 仍为 0dp，避免影响左滑手势判定区
 * @param content 卡片内容（通常是 TodoListItem）
 */
@Composable
fun SwipeableTodoBox(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isExpanded: Boolean = false,
    isPinned: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    onShareClick: () -> Unit = {},
    onPinClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    /**
     * 自定义按钮配置列表（新增）。
     * - null（默认）：使用内置"分享/置顶/删除"配置（待办页兼容）
     * - 非 null：使用调用方传入的列表（日期页用"置顶/归档/删除"）
     */
    customButtons: List<SwipeButtonConfig>? = null,
    durationMs: Int = 300,
    staggerRatio: Float = 0.00f,
    thresholdRatio: Float = 0.20f,
    easing: Easing = ElasticOutEasing,
    /**
     * 内容区内边距（v2026-07-20 新增）
     * 给 Card shadow 预留显示空间，避免被外层 .clip(RoundedCornerShape) 裁切
     * 默认上下 6dp，左右 0dp（不影响左滑手势）
     */
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
    content: @Composable (isClickBlocked: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    /**
     * 是否处于手势进行中（含拖动 + 归位动画）
     *
     * 设计意图：仅在以下两个阶段禁用内容卡片的水波纹：
     * 1. 拖动中：手指在屏幕上左右拖动
     * 2. 归位动画中：onDragEnd/onDragCancel 后 animateTo 动画进行中
     *
     * 其他场景（点击进入编辑、多选模式下点击选择）保持显示水波纹。
     *
     * 状态机：
     * - onDragStart → true
     * - animateTo 协程结束（吸附/归位/快速右滑关闭）→ false
     */
    var isDragging by remember { mutableStateOf(false) }

    // 展开期间屏蔽卡片内 4 类点击入口（详情 / 子待办展开 / 长按 / 复选框），
    // 关闭动画结束后 200ms 才解除屏蔽（避免尾帧误触）
    var isClickBlocked by remember { mutableStateOf(false) }

    // 右滑意图跟踪：onDrag 中任何 dragAmount > 0 都标记为右滑意图，
    // 用于 onDragEnd 判断是否需要"抬手总关闭"（即使慢速右滑也关闭）
    // 实现"右滑跟手 + 抬手总关闭"语义
    var hadRightDrag by remember { mutableStateOf(false) }

    // 几何参数
    val buttonWidthDp = 72.dp
    val actionsWidthDp = buttonWidthDp * 3 // 3 个按钮 = 216dp
    val buttonWidthPx = with(density) { buttonWidthDp.toPx() }
    val actionsWidthPx = with(density) { actionsWidthDp.toPx() }
    val thresholdPx = actionsWidthPx * thresholdRatio
    val cornerRadiusDp = 16.dp

    // 卡片位移状态（px，范围 -actionsWidthPx..0）
    val cardOffsetX = remember { Animatable(0f) }

    // 恢复动画协程引用：用于在右滑首帧后跟踪正在跑的 animateTo(0f) 协程，
    // 防止 drag / onDragEnd / onDragCancel 重复启动新协程
    val restoreJob = remember { mutableStateOf<Job?>(null) }

    // 速度跟踪器：用于检测"快速右滑"（fling right）手势以关闭已展开的卡片
    val velocityTracker = remember { VelocityTracker() }
    // 快速右滑速度阈值：x 方向 > 800 px/s 视为 fling
    val flingVelocityThresholdPx = with(density) { 800.dp.toPx() }

    // 按钮配置
    // - customButtons 优先：日期页等需要自定义按钮顺序/类型时传入
    // - 否则用待办页默认"分享→置顶→删除"
    val buttons = remember(isPinned, customButtons) {
        customButtons ?: defaultTodoButtons(isPinned, cornerRadiusDp)
    }

    // 父组件强制收起时同步动画
    LaunchedEffect(isExpanded, isEnabled) {
        if (!isExpanded && cardOffsetX.value < 0f && isEnabled) {
            cardOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMs, easing = easing)
            )
        }
    }

    // 同步 isClickBlocked 与 isExpanded：isExpanded 变 true 立即屏蔽；
    // isExpanded 变 false 后延后 200ms 解除（让关闭动画跑完 + 留出尾帧安全余量）
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            isClickBlocked = true
        } else if (isClickBlocked) {
            delay(200L)
            isClickBlocked = false
        }
    }

    // revealProgress 连续函数（与 Web 原型 1:1 对齐）
    val revealPx = (-cardOffsetX.value).coerceIn(0f, actionsWidthPx)
    val revealProgress = if (actionsWidthPx > 0f) revealPx / actionsWidthPx else 0f

    // 按钮点击后收起的公共逻辑
    val onButtonClicked: () -> Unit = {
        // 关键：onExpandChange(false) 延后到 animateTo 之后，
        // 避免动画期间 swipeActionExpanded 提前变 false 导致 Drawer 手势恢复
        coroutineScope.launch {
            cardOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMs, easing = easing)
            )
            onExpandChange(false)
        }
    }

    // 双层叠加 Layout：内容层(z=10) + 操作层(z=1)
    Layout(
        modifier = modifier
            // v2026-07-20：先 padding 再 clip
            // - padding(contentPadding) 给 Card 的 Modifier.shadow 预留空间
            // - 否则 Card 周围画出的 shadow 会被外层 .clip(RoundedCornerShape) 裁切
            // - padding 在 clip 之前：clip 的形状只裁切 padding 内的内容，padding 区域的 shadow 不会被切
            .padding(contentPadding)
            .clip(RoundedCornerShape(cornerRadiusDp))
            // 第一层防护：在外层拦截 down 事件，阻止父级 ModalNavigationDrawer
            // 看到 down 后启动 Drawer 打开手势
            // （解决"卡片展开后右滑关闭时带出侧边栏"问题）
            // 仅在卡片展开期间激活，避免影响其他区域
            .pointerInput(isEnabled, isExpanded) {
                if (!isEnabled || !isExpanded) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 智能分流：down 落在按钮区域则放行，让事件透传给按钮 clickable
                    val buttonAreaStartX = size.width - actionsWidthPx
                    val isButtonArea = down.position.x >= buttonAreaStartX
                    if (!isButtonArea) {
                        // 仅消费非按钮区域，阻止 ModalNavigationDrawer 抢手势
                        down.consume()
                    }
                }
            },
        content = {
            // === 内容层（measurables[0]）===
            Box(
                modifier = Modifier
                    .offset { IntOffset(cardOffsetX.value.roundToInt(), 0) }
                    .pointerInput(isEnabled, isExpanded) {
                        // 关键：key 加入 isExpanded，使得完全展开 ↔ 收起时手势检测器重启，
                        // 避免旧协程与新手势的 snapTo 冲突，解决"完全展开后右滑卡住"问题
                        if (!isEnabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = {
                                // 开始新一轮拖动：
                                // 1. 重置速度跟踪器 + 右滑意图
                                // 2. 标记为手势进行中 → 禁用内容卡片的 indication 水波纹
                                // 3. 取消正在跑的恢复动画 → 避免新 snapTo 与旧 animateTo 争抢 cardOffsetX
                                //    （保留回弹效果让"跟手"更自然）
                                velocityTracker.resetTracking()
                                hadRightDrag = false
                                restoreJob.value?.cancel()
                                restoreJob.value = null
                                isDragging = true
                            },
                            onDragEnd = {
                                // 关键：恢复动画进行中，仅等待其完成，不要启动新动画
                                if (restoreJob.value != null) {
                                    coroutineScope.launch { restoreJob.value?.join() }
                                } else {
                                    // 计算抬手时的 x 方向速度（px/s）
                                    val velocity = velocityTracker.calculateVelocity()
                                    // 关键：右滑意图（hadRightDrag）或快速右滑（fling）时，立即关闭卡片
                                    // - hadRightDrag：onDrag 中任何 dragAmount > 0 都标记，实现"跟手 + 抬手总关闭"
                                    // - velocity.x > flingVelocityThresholdPx：高速右滑 fling 不依赖跟手
                                    if (hadRightDrag || velocity.x > flingVelocityThresholdPx) {
                                        // 存入 restoreJob 防止与正在跑的动画冲突
                                        restoreJob.value = coroutineScope.launch {
                                            cardOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(
                                                    durationMillis = durationMs,
                                                    easing = easing
                                                )
                                            )
                                            onExpandChange(false)
                                            // 归位动画结束：恢复 indication
                                            isDragging = false
                                            restoreJob.value = null
                                        }
                                    } else {
                                        // 普通抬手：按阈值吸附
                                        val currentReveal = -cardOffsetX.value
                                        val target = if (currentReveal >= thresholdPx) {
                                            -actionsWidthPx
                                        } else {
                                            0f
                                        }
                                        // 关键：onExpandChange 延后到 animateTo 之后调用，
                                        // 避免动画期间 swipeActionExpanded 被错误置为 false，
                                        // 导致 MainScreen 的 gesturesEnabled 提前恢复 true，
                                        // 让右滑事件被父级 ModalNavigationDrawer 识别为打开 Drawer
                                        // 存入 restoreJob 防止与正在跑的动画冲突
                                        restoreJob.value = coroutineScope.launch {
                                            cardOffsetX.animateTo(
                                                targetValue = target,
                                                animationSpec = tween(
                                                    durationMillis = durationMs,
                                                    easing = easing
                                                )
                                            )
                                            onExpandChange(target < 0f)
                                            // 吸附/归位动画结束：恢复 indication
                                            isDragging = false
                                            restoreJob.value = null
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                // 关键：恢复动画进行中，仅等待其完成
                                if (restoreJob.value != null) {
                                    coroutineScope.launch { restoreJob.value?.join() }
                                } else {
                                    // 取消手势时同样按"右滑意图或快速右滑"判断（极少见，但保持一致）
                                    val velocity = velocityTracker.calculateVelocity()
                                    // 关键：右滑意图（hadRightDrag）或快速右滑（fling）时，立即关闭卡片
                                    // - hadRightDrag：onDrag 中任何 dragAmount > 0 都标记，实现"跟手 + 抬手总关闭"
                                    // - velocity.x > flingVelocityThresholdPx：高速右滑 fling 不依赖跟手
                                    if (hadRightDrag || velocity.x > flingVelocityThresholdPx) {
                                        restoreJob.value = coroutineScope.launch {
                                            cardOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(
                                                    durationMillis = durationMs,
                                                    easing = easing
                                                )
                                            )
                                            onExpandChange(false)
                                            // 归位动画结束：恢复 indication
                                            isDragging = false
                                            restoreJob.value = null
                                        }
                                    } else {
                                        val currentReveal = -cardOffsetX.value
                                        val target = if (currentReveal >= thresholdPx) {
                                            -actionsWidthPx
                                        } else {
                                            0f
                                        }
                                        // 关键：onExpandChange 延后到 animateTo 之后
                                        restoreJob.value = coroutineScope.launch {
                                            cardOffsetX.animateTo(
                                                targetValue = target,
                                                animationSpec = tween(
                                                    durationMillis = durationMs,
                                                    easing = easing
                                                )
                                            )
                                            onExpandChange(target < 0f)
                                            // 吸附/归位动画结束：恢复 indication
                                            isDragging = false
                                            restoreJob.value = null
                                        }
                                    }
                                }
                            }
                        ) { change, dragAmount ->
                            // 记录每个 pointer 事件的位置和时间，用于计算抬手时的速度
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            // 计算本帧 snapTo 后的目标位置
                            val newOffset = (cardOffsetX.value + dragAmount)
                                .coerceIn(-actionsWidthPx, 0f)

                            // 右滑意图跟踪：dragAmount > 0 时标记，
                            // 用于 onDragEnd 判断是否需要"抬手总关闭"
                            // 实现"右滑跟手 + 抬手总关闭"语义
                            if (dragAmount > 0f) {
                                hadRightDrag = true
                            } else if (dragAmount < 0f && hadRightDrag) {
                                // 右滑后"反悔"语义：用户左滑到完全展开位置时清除右滑意图，
                                // 抬手后按阈值吸附保持展开（不再触发总关闭）
                                // 需求："右滑后中途左滑到最大位置松手应保持展开"
                                if (newOffset <= -actionsWidthPx) {
                                    hadRightDrag = false
                                }
                            }

                            // 关键：右滑"跟手"（snapTo），不再立即触发关闭动画
                            // onDragEnd 会根据 hadRightDrag / velocity 决定是否关闭
                            // 这样用户能看到卡片跟随手指位置移动，抬手时由 onDragEnd 启动关闭动画
                            coroutineScope.launch {
                                cardOffsetX.snapTo(newOffset)
                            }
                        }
                    }
                    .zIndex(10f)
            ) {
                /**
                 * 内容卡片的 indication 水波纹开关（基于手势进行中状态）
                 *
                 * - isDragging = true（拖动中 或 归位/吸附动画中）→ LocalContentIndication = false
                 * - isDragging = false（静止状态）→ LocalContentIndication = true（默认）
                 *
                 * 为什么需要在 content() 外层加 CompositionLocalProvider：
                 * 左滑时外层 detectHorizontalDragGestures 与内部 TodoListItem 的
                 * detectTapGestures.onPress 会同时响应 down 事件，onPress 会发射
                 * Press.Interaction → indication 渲染水波纹。仅在拖动/归位阶段禁用，
                 * 其他阶段（点击进入编辑、多选模式点击选择）保持显示水波纹。
                 *
                 * 注：操作层（分享/置顶/删除按钮）的 clickable 在 Box 外层，
                 * 仍使用默认 indication，水波纹保留。
                 */
                CompositionLocalProvider(LocalContentIndication provides !isDragging) {
                    content(isClickBlocked)
                }
            }

            // === 操作层（measurables[1..3]）===
            // 不使用 Row 包裹，直接放置三个按钮，由自定义 Layout 分别强制固定宽高，
            // 避免 Row 布局中子元素 fillMaxHeight() 不生效导致的高度不匹配问题
            if (isEnabled) {
                buttons.forEachIndexed { index, btnConfig ->
                    // 级联算法：计算本地进度
                    val localStart = index * staggerRatio
                    val denom = 1f - localStart
                    val localProgress = if (denom > 0f) {
                        ((revealProgress - localStart) / denom).coerceIn(0f, 1f)
                    } else {
                        if (revealProgress >= localStart) 1f else 0f
                    }
                    // 偏移量：初始堆叠在 Delete 槽位 → 终态回到原始位置
                    val offset = (buttons.size - 1 - index) * buttonWidthPx
                    val translateX = offset * (1f - localProgress)
                    // opacity 二元化：无淡入淡出
                    val alpha = if (revealPx > 0f) 1f else 0f

                    // 点击回调：根据 SwipeActionType 路由到对应 onXxxClick
                    val clickAction: () -> Unit = when (btnConfig.actionType) {
                        SwipeActionType.SHARE -> {
                            { onShareClick(); onButtonClicked() }
                        }
                        SwipeActionType.PIN -> {
                            { onPinClick(); onButtonClicked() }
                        }
                        SwipeActionType.ARCHIVE -> {
                            { onArchiveClick(); onButtonClicked() }
                        }
                        // 2026-07-14 新增：UNARCHIVE 复用 onArchiveClick 回调
                        // 原因：上层（SpecialDateScreen）已根据 isArchived 决定
                        //       onArchiveClick 是调用 onArchive 还是 onUnarchive
                        //       SwipeableTodoBox 不需要关心具体业务逻辑
                        SwipeActionType.UNARCHIVE -> {
                            { onArchiveClick(); onButtonClicked() }
                        }
                        SwipeActionType.DELETE -> {
                            { onDeleteClick(); onButtonClicked() }
                        }
                    }

                    SwipeActionButton(
                        config = btnConfig,
                        translateX = translateX,
                        alpha = alpha,
                        onClick = clickAction,
                        modifier = Modifier.zIndex(btnConfig.zIndex)
                    )
                }
            }
        },
        measurePolicy = { measurables, constraints ->
            val contentPlaceable = measurables[0].measure(constraints)
            val cardWidth = contentPlaceable.width
            val cardHeight = contentPlaceable.height
            val singleButtonWidthPx = with(density) { buttonWidthDp.roundToPx() }

            // 分别测量三个按钮，每个按钮强制固定宽度=72dp、高度=卡片实际高度
            // 这样可以 100% 保证按钮高度与卡片高度一致
            val actionPlaceables = if (isEnabled) {
                List(buttons.size) { i ->
                    measurables[1 + i].measure(
                        Constraints.fixed(
                            width = singleButtonWidthPx,
                            height = cardHeight
                        )
                    )
                }
            } else emptyList()

            layout(cardWidth, cardHeight) {
                contentPlaceable.placeRelative(0, 0)
                // 从右往左依次放置三个按钮（删除在最右，置顶在中间，分享在最左）
                actionPlaceables.forEachIndexed { index, placeable ->
                    val x = cardWidth - (buttons.size - index) * singleButtonWidthPx
                    placeable.placeRelative(x = x, y = 0)
                }
            }
        }
    )
}

/**
 * 单个左滑操作按钮（图标在上，文字在下，纵向居中）
 *
 * @param config 按钮配置（包含背景色、图标、文字、圆角形状等）
 * @param translateX 横向偏移量（级联堆叠动画）
 * @param alpha 透明度（二元化）
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun SwipeActionButton(
    config: SwipeButtonConfig,
    translateX: Float,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = colorResource(id = config.backgroundColorRes)

    Box(
        modifier = modifier
            .graphicsLayer {
                this.translationX = translateX
                this.alpha = alpha
            }
            .background(backgroundColor, shape = config.shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = config.label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = config.label,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}
