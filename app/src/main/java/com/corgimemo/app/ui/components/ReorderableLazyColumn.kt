package com.corgimemo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.util.toPxFloat
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 简化版可拖拽列表面向非 LazyColumn 场景
 *
 * 对于少量固定数量的列表项（如设置页面），
 * 使用 Column + 手动管理状态的方式更简单。
 *
 * @param items 列表数据
 * @param onReorder 重排回调
 * @param modifier Modifier
 * @param content 列表项 Composable
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, item: T, isDragging: Boolean) -> Unit
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    /** 记录拖拽起始 Y 坐标，用于计算偏移量 */
    var dragStartY by remember { mutableFloatStateOf(0f) }
    /** 获取 Context 用于触觉反馈 */
    val context = LocalContext.current
    /** 获取屏幕密度用于 dp→px 转换（确保不同密度下视觉效果一致） */
    val density = LocalDensity.current
    /** 上一次跨项移动时的触觉反馈时间戳（节流 200ms） */
    var lastHapticTime by remember { mutableLongStateOf(0L) }
    /** 记录上一次 targetIndex 用于检测跨项移动 */
    var lastTargetIndex by remember { mutableIntStateOf(-1) }

    /**
     * 动态行高缓存：记录每个内容项的实际渲染高度（像素）
     *
     * 通过 onSizeChanged 在首次布局时捕获每项的真实尺寸，
     * 替代固定 120dp 预估，使拖拽索引计算更精准。
     * key = 列表索引, value = 高度（像素）
     *
     * 使用 Compose 原生 StateMap（Compose 1.9+ 支持 mutableIntStateMapOf）
     */
    val itemHeightsPx = remember { mutableStateMapOf<Int, Int>() }

    /**
     * 获取用于拖拽索引计算的预估行高（像素）
     *
     * 策略：
     * 1. 如果已测量到任意项的高度 → 使用所有已测量项的平均值
     * 2. 否则 → 回退到固定默认值 160px（约 50dp @ 320dpi）
     */
    val defaultHeightPx = if (itemHeightsPx.isNotEmpty()) {
        itemHeightsPx.values.sum().toFloat() / itemHeightsPx.size
    } else {
        160f
    }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            val isCurrentItemDragging = index == draggedIndex

            /**
             * 拖拽项视觉状态：
             * - 被拖项：放大 + 阴影 + 浮起
             * - 目标位置项（被跨越的项）：半透明
             * - 其他项：正常显示
             */
            val alpha = when {
                isCurrentItemDragging -> 1f /** 被拖项保持不透明 */
                index == targetIndex && draggedIndex != targetIndex -> 0.4f /** 目标位置半透明提示 */
                else -> 1f
            }

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .then(
                        if (isCurrentItemDragging) {
                            Modifier
                                .padding(8.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                .graphicsLayer(
                                    scaleX = 1.05f,
                                    scaleY = 1.05f,
                                    translationY = (-4).dp.toPxFloat(density)
                                )
                                /** Compose 1.9 原生投影：使用 DSL 块语法 */
                                .dropShadow(
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    color = Color.Black.copy(alpha = 0.25f)
                                    radius = 8f
                                }
                        } else {
                            Modifier
                                .padding(8.dp)
                                .graphicsLayer(alpha = alpha)
                        }
                    )
                    /** 捕获每项的实际布局高度，存入动态行高缓存 */
                    .onSizeChanged { size ->
                        if (size.height > 0) {
                            itemHeightsPx[index] = size.height
                        }
                    }
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                draggedIndex = index
                                targetIndex = index
                                dragStartY = offset.y
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                /**
                                 * 基于手指 Y 偏移量 + 动态行高计算目标索引
                                 *
                                 * 使用 itemHeightsPx 缓存的实际测量高度，
                                 * 比固定 120dp 预估更精准地反映真实内容尺寸。
                                 */
                                val dragOffset = change.position.y - dragStartY

                                /**
                                 * 计算目标索引：从被拖项出发，
                                 * 根据偏移量逐项累加实际高度判断越过了哪些项
                                 */
                                val effectiveItemHeight: Float = itemHeightsPx[index]
                                    ?.toFloat() ?: defaultHeightPx
                                val indexDelta = (dragOffset / effectiveItemHeight).toInt()
                                val newTargetIndex = (index + indexDelta)
                                    .coerceIn(0, items.size - 1)
                                targetIndex = newTargetIndex

                                /** 跨项移动时触发轻微触觉反馈（节流 200ms） */
                                if (newTargetIndex != lastTargetIndex && newTargetIndex != draggedIndex) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastHapticTime > 200L) {
                                        HapticFeedbackManager.performHapticFeedback(
                                            context = context,
                                            type = InteractionType.TEXT_MOVE,
                                            enabled = true
                                        )
                                        lastHapticTime = now
                                    }
                                    lastTargetIndex = newTargetIndex
                                }
                            },
                            onDragEnd = {
                                /** 排序完成时触发确认触觉反馈 */
                                if (draggedIndex >= 0 && targetIndex >= 0 &&
                                    draggedIndex != targetIndex) {
                                    HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = InteractionType.CONFIRM,
                                        enabled = true
                                    )
                                    onReorder(draggedIndex, targetIndex)
                                }
                                draggedIndex = -1
                                targetIndex = -1
                                lastTargetIndex = -1
                            },
                            onDragCancel = {
                                draggedIndex = -1
                                targetIndex = -1
                            }
                        )
                    }
            ) {
                content(index, item, isCurrentItemDragging)
            }
        }
    }
}

/**
 * 拖拽排序算法纯函数集合
 *
 * 抽离为顶层 object 便于单元测试（不依赖 Compose Runtime）。
 * 运行时由 ReorderableLazyColumn 调用，测试由 ReorderAlgorithmsTest 验证。
 */
object ReorderAlgorithms {

    /**
     * 检测被拖项与哪个可见项重叠超过 50%
     *
     * 算法：
     * - 被拖项中心 = fingerY（手指位置即卡片视觉中心）
     * - 计算与每个可见项的重叠高度
     * - 重叠比例 = 重叠高度 / max(被拖项高, 其他项高)
     * - 比例 > 0.5 时返回该其他项的 key
     *
     * 说明：使用 max 而非 min 作为分母，避免小卡片在高度差场景下
     * 触发阈值过低导致交换震荡。
     *
     * @param draggedKey 被拖项 key（不与自身比较）
     * @param fingerY 手指 Y 坐标（视口坐标）
     * @param draggedSize 被拖项高度（px）
     * @param visibleItems 可见项信息列表（key, offset, size）
     * @return 目标项的 key，无交换返回 null
     */
    fun findSwapTarget(
        draggedKey: Any,
        fingerY: Float,
        draggedSize: Int,
        visibleItems: List<VisibleItemInfo>
    ): Any? {
        val draggedCenter = fingerY
        val draggedTop = draggedCenter - draggedSize / 2f
        val draggedBottom = draggedCenter + draggedSize / 2f

        for (other in visibleItems) {
            if (other.key == draggedKey) continue

            val otherTop = other.offset.toFloat()
            val otherBottom = (other.offset + other.size).toFloat()

            val overlapTop = maxOf(draggedTop, otherTop)
            val overlapBottom = minOf(draggedBottom, otherBottom)
            val overlapHeight = maxOf(0f, overlapBottom - overlapTop)

            val maxSize = maxOf(draggedSize, other.size).toFloat()
            if (maxSize > 0 && overlapHeight / maxSize > 0.5f) {
                return other.key
            }
        }
        return null
    }

    /**
     * 检测拖拽是否跨越置顶区分界线
     *
     * 规则：
     * - 置顶区 = 列表顶部连续的 isPinned=true 项
     * - 跨越 = 被拖项原始 isPinned 与当前位置邻居的 isPinned 不同
     *
     * @param displayItems 当前显示列表（被拖项已移除后）的 isPinned 序列
     * @param draggedOriginalIsPinned 被拖项原始 isPinned
     * @param draggedCurrentIndex 被拖项当前列表位置（插入位置）
     * @return true=已跨越分界线
     */
    fun checkPinnedZoneCrossed(
        displayItems: List<Boolean>,
        draggedOriginalIsPinned: Boolean,
        draggedCurrentIndex: Int
    ): Boolean {
        if (draggedCurrentIndex < 0 || draggedCurrentIndex >= displayItems.size) return false
        val neighborIsPinned = when {
            draggedCurrentIndex > 0 -> displayItems[draggedCurrentIndex - 1]
            draggedCurrentIndex < displayItems.size - 1 -> displayItems[draggedCurrentIndex + 1]
            else -> draggedOriginalIsPinned
        }
        return draggedOriginalIsPinned != neighborIsPinned
    }

    /**
     * 计算释放动画的起始 offset（从 ReorderableLazyColumn finally 块提取为纯函数）
     *
     * 用途：用户松手时，被拖卡片 A 的内层 Box offset 仍保持在 `fingerY - baseCenterY`，
     * 需要驱动 Animatable 从该值平滑过渡到 0，避免松手瞬间 offset 归零造成的视觉瞬移。
     *
     * @param fingerY 手指 Y 坐标（视口坐标）
     * @param baseCenterY 拖拽基线中心 Y（最后交换时的目标项 offset + draggedSize/2）
     * @return 释放动画起始 offset（手指相对基线的偏移）
     */
    fun computeReleaseStartOffset(fingerY: Float, baseCenterY: Float): Float =
        fingerY - baseCenterY

    /**
     * 判断释放动画期间是否应跳过 displayItems 更新
     *
     * 用途：松手后 250ms 释放动画期间，ViewModel 异步 onReorder 结果回流可能触发
     * `LaunchedEffect(items)` 重置 displayItems。如果在动画期间重置，会破坏正在播放的
     * 释放动画（A 的内层 Box offset 与新的 displayItems 位置不一致），造成新的跳变。
     *
     * @param isReleasing 是否处于释放动画期
     * @return true = 跳过 displayItems 更新；false = 正常更新
     */
    fun shouldSkipDisplayUpdate(isReleasing: Boolean): Boolean = isReleasing
}

/**
 * 可见项信息（测试友好型数据类，避免依赖 LazyListLayoutInfo）
 */
data class VisibleItemInfo(
    val key: Any,
    val offset: Int,
    val size: Int
)

/**
 * 可拖拽排序 LazyColumn 容器组件
 *
 * **职责**：
 * - 长按 500ms + 纵向移动 > 8dp → 进入拖拽模式
 * - 拖拽中卡片半透明跟随手指，其他项 animateItem() 让位
 * - 50% 重叠触发交换，置顶区跨越自动切换 isPinned
 * - 边缘自动滚动（变速）
 * - 手指抬起提交排序，调用 onReorder
 *
 * **手势分层**：
 * - L1 容器（本组件）：pointerInput(Unit) { awaitEachGesture } 处理长按+拖拽
 * - L2 SwipeableTodoBox：拖拽激活时通过 isEnabled=false 禁用左滑
 * - L3 TodoListItem：检测 isDragActive=true 时 emit Cancel + break 让位
 *
 * **无 1 帧跳变**：手动追踪 draggedBaseCenterY 基线，
 * dragOffsetY = fingerY - draggedBaseCenterY（纯同步计算，不依赖 layoutInfo）
 *
 * @param items 列表数据
 * @param isDragEnabled 是否允许拖拽（批量模式/左滑展开时设为 false）
 * @param isDraggable 判断项是否可拖拽（用于分隔按钮等不可拖拽项），默认全部可拖拽
 * @param isPinned 获取项是否置顶的函数（用于跨越检测）
 * @param key 项的唯一标识
 * @param onReorder 排序提交回调 (fromIndex, toIndex, crossedPinnedZone)
 * @param listState LazyListState 实例，外部传入可读取滚动状态；默认内部创建
 * @param modifier Modifier
 * @param content 列表项 Composable，参数为 (index, item, isDragging, isDragActive)
 */
@Composable
fun <T> ReorderableLazyColumn(
    items: List<T>,
    isDragEnabled: Boolean,
    isDraggable: (T) -> Boolean = { true },
    isPinned: (T) -> Boolean,
    key: (T) -> Any,
    onReorder: (fromIndex: Int, toIndex: Int, crossedPinnedZone: Boolean) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, item: T, isDragging: Boolean, isDragActive: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    // ━━━ 拖拽状态 ━━━
    var isDragActive by remember { mutableStateOf(false) }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    var draggedOriginalIndex by remember { mutableIntStateOf(-1) }
    var draggedCurrentIndex by remember { mutableIntStateOf(-1) }
    var draggedOriginalIsPinned by remember { mutableStateOf(false) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var draggedBaseCenterY by remember { mutableFloatStateOf(0f) }
    var lastHapticTime by remember { mutableLongStateOf(0L) }

    // ━━━ 释放动画状态（松手后视觉跳跃修复）━━━
    /**
     * 释放动画期标志
     *
     * 松手后 250ms 期间为 true。期间：
     * - draggedKey 保持有效，A 继续在「拖拽分支」中（animateItem 不重启）
     * - dragOffsetY 由 releaseDragOffset 驱动平滑过渡到 0
     * - LaunchedEffect(items) 跳过 displayItems 更新（避免破坏动画）
     */
    var isReleasing by remember { mutableStateOf(false) }

    /**
     * 释放动画 Animatable
     *
     * 驱动 A 的内层 Box offset 从松手时偏移（fingerY - baseCenterY）平滑过渡到 0。
     * 使用 Animatable 而非直接修改 fingerY，因为：
     * - Animatable 是 Composable 状态，Compose 自动驱动重组
     * - 250ms tween FastOutSlowInEasing 与 animateItem 默认一致
     */
    val releaseDragOffset = remember { Animatable(0f) }

    // ━━━ 反向交换锁定状态（修复点 3）━━━
    // 交换后记录目标 key 和手指位置，防止下一帧立即反向交换导致震荡
    // 清除条件：手指移动超过 draggedSize/2 后清除锁定
    var lastSwapTargetKey by remember { mutableStateOf<Any?>(null) }
    var lastSwapFingerY by remember { mutableFloatStateOf(0f) }

    // ━━━ 显示列表（拖拽中可重排，与 items 解耦）━━━
    var displayItems by remember { mutableStateOf(items) }
    LaunchedEffect(items) {
        when {
            // 释放动画期间：跳过更新，避免破坏正在播放的释放动画
            // （A 的内层 Box offset 与新 displayItems 位置不一致会导致跳变）
            ReorderAlgorithms.shouldSkipDisplayUpdate(isReleasing) -> {
                Log.d("ReorderableLazyColumn",
                    "[DISPLAY_ITEMS_REFRESH] skipped: isReleasing=true, items.size=${items.size}")
            }
            // 非拖拽中：正常更新
            !isDragActive -> {
                displayItems = items
            }
            // 拖拽中 items 被外部变更（如同步）→ 取消拖拽
            else -> {
                displayItems = items
                isDragActive = false
                draggedKey = null
                draggedOriginalIndex = -1
                draggedCurrentIndex = -1
                fingerY = 0f
                draggedBaseCenterY = 0f
            }
        }
    }

    // ━━━ 阈值参数 ━━━
    val dragThresholdPx = with(density) { 8.dp.toPx() }
    val longPressTimeoutMs = 500L
    val hapticThrottleMs = 200L
    val edgeThresholdPx = with(density) { 80.dp.toPx() }
    val maxScrollSpeedPx = with(density) { 20.dp.toPx() }

    // ━━━ dragOffsetY 同步计算（derivedStateOf 避免在 Composable 中声明带 getter 的局部属性）━━━
    val dragOffsetY by remember {
        derivedStateOf {
            when {
                isDragActive -> fingerY - draggedBaseCenterY
                isReleasing -> releaseDragOffset.value
                else -> 0f
            }
        }
    }

    // ━━━ 自动滚动 ━━━
    LaunchedEffect(isDragActive, fingerY) {
        while (isDragActive) {
            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            if (viewportHeight > 0) {
                val scrollDelta = when {
                    fingerY < edgeThresholdPx -> {
                        val ratio = (edgeThresholdPx - fingerY) / edgeThresholdPx
                        ratio * maxScrollSpeedPx
                    }
                    fingerY > viewportHeight - edgeThresholdPx -> {
                        val ratio = (fingerY - (viewportHeight - edgeThresholdPx)) / edgeThresholdPx
                        -ratio * maxScrollSpeedPx
                    }
                    else -> 0f
                }
                if (scrollDelta != 0f) {
                    listState.scrollBy(scrollDelta)
                    draggedBaseCenterY += scrollDelta // 同步调整基线
                }
            }
            delay(16)
        }
    }

    // ━━━ 主题色（虚线占位框）━━━
    val primaryColor = MaterialTheme.colorScheme.primary

    // ━━━ 协程作用域（用于启动释放动画独立协程）━━━
    val scope = rememberCoroutineScope()

    // ━━━ 拖拽时禁用列表滚动（通过nestedScroll拦截）━━━
    val dragScrollBlocker = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                // 拖拽激活时消费所有滚动请求，阻止列表跟随手指滚动
                return if (isDragActive) available else Offset.Zero
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .nestedScroll(dragScrollBlocker)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                if (down.isConsumed) return@awaitEachGesture

                // 早期 return 会导致所有手势无响应（项目记忆教训）
                // 将 isDragEnabled 与 items.size 判断移至手势内部
                if (!isDragEnabled) return@awaitEachGesture
                if (items.size < 2) return@awaitEachGesture

                val downPosition = down.position
                var longPressTriggered = false
                val downTime = System.currentTimeMillis()

                // 注：不使用 scope.launch { delay(500) } 启动长按定时器
                // 原因：awaitEachGesture 是受限挂起作用域，子项已有自己的 500ms 长按定时器
                // 容器改为在主循环中通过时间戳检测长按是否触发

                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        val now = System.currentTimeMillis()

                        // 检测长按触发（未移动且超过 500ms）
                        if (!longPressTriggered && now - downTime >= longPressTimeoutMs) {
                            val dragDistance = (change.position - downPosition).getDistance()
                            if (dragDistance <= dragThresholdPx) {
                                longPressTriggered = true
                                // 长按触发但不震动（子项负责 LONG_CLICK 震动）
                            }
                        }

                        // 检测手指抬起
                        if (change.changedToUp()) {
                            break
                        }

                        // 长按未触发：移动 > touchSlop → 让位左滑
                        if (!longPressTriggered) {
                            val touchSlop = viewConfiguration.touchSlop
                            val dragDistance = (change.position - downPosition).getDistance()
                            if (dragDistance > touchSlop) {
                                break // 让位 SwipeableTodoBox
                            }
                        } else {
                            // 长按已触发：检测纵向移动 > dragThreshold → 进入拖拽
                            if (!isDragActive) {
                                val dy = change.position.y - downPosition.y
                                if (abs(dy) > dragThresholdPx) {
                                    // ━━━ 进入拖拽模式 ━━━
                                    val draggedIndex = listState.layoutInfo.visibleItemsInfo
                                        .find { it.offset <= downPosition.y && (it.offset + it.size) >= downPosition.y }
                                        ?: break
                                    val draggedItem = displayItems.getOrNull(draggedIndex.index)
                                        ?: break

                                    if (!isDraggable(draggedItem)) {
                                        change.consume()
                                        break
                                    }

                                    isDragActive = true
                                    draggedKey = key(draggedItem)
                                    draggedOriginalIndex = draggedIndex.index
                                    draggedCurrentIndex = draggedIndex.index
                                    draggedOriginalIsPinned = isPinned(draggedItem)
                                    fingerY = change.position.y
                                    draggedBaseCenterY = (draggedIndex.offset + draggedIndex.size / 2f)

                                    // 震动：标记进入拖拽
                                    HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = InteractionType.TEXT_MOVE,
                                        enabled = true
                                    )
                                }
                            } else {
                                // ━━━ 拖拽中 ━━━
                                fingerY = change.position.y
                                change.consume()

                                // 检测交换目标
                                val draggedSize = listState.layoutInfo.visibleItemsInfo
                                    .find { it.key == draggedKey }?.size ?: 0
                                val visibleInfos = listState.layoutInfo.visibleItemsInfo.map {
                                    VisibleItemInfo(it.key, it.offset, it.size)
                                }

                                // 反向交换锁定检查：手指离开上次交换位置超过 draggedSize/2 时清除锁定
                                if (lastSwapTargetKey != null && draggedSize > 0 &&
                                    abs(fingerY - lastSwapFingerY) > draggedSize / 2f) {
                                    lastSwapTargetKey = null
                                }

                                // 排除刚交换过的目标项，防止立即反向交换
                                val effectiveVisibleItems = visibleInfos.filter {
                                    it.key != lastSwapTargetKey
                                }
                                val swapTargetKey = ReorderAlgorithms.findSwapTarget(
                                    draggedKey = draggedKey!!,
                                    fingerY = fingerY,
                                    draggedSize = draggedSize,
                                    visibleItems = effectiveVisibleItems
                                )

                                if (swapTargetKey != null && swapTargetKey != draggedKey) {
                                    val targetIndex = displayItems.indexOfFirst {
                                        key(it) == swapTargetKey
                                    }
                                    if (targetIndex >= 0 && targetIndex != draggedCurrentIndex) {
                                        // ━━━ 执行交换 ━━━
                                        val newDisplay = displayItems.toMutableList()
                                        val draggedItem = newDisplay.removeAt(draggedCurrentIndex)
                                        newDisplay.add(targetIndex, draggedItem)
                                        displayItems = newDisplay
                                        draggedCurrentIndex = targetIndex

                                        // 同步更新基线 = 被拖项在新位置的预期中心 Y
                                        // 关键：必须用 draggedSize（被拖项高度）而非 otherInfo.size（目标项高度）
                                        // 因为交换后被拖项占据目标项的旧 offset 位置，
                                        // 其布局中心 = target.offset + draggedSize / 2
                                        val otherInfo = listState.layoutInfo.visibleItemsInfo
                                            .find { it.key == swapTargetKey }
                                        if (otherInfo != null) {
                                            draggedBaseCenterY = (otherInfo.offset + draggedSize / 2f)
                                        }

                                        // 记录本次交换信息，用于反向锁定（修复点 3）
                                        lastSwapTargetKey = swapTargetKey
                                        lastSwapFingerY = fingerY

                                        // 节流震动
                                        if (now - lastHapticTime > hapticThrottleMs) {
                                            HapticFeedbackManager.performHapticFeedback(
                                                context = context,
                                                type = InteractionType.TEXT_MOVE,
                                                enabled = true
                                            )
                                            lastHapticTime = now
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    // ━━━ 拖拽结束或异常 ━━━
                    if (isDragActive) {
                        // 1. 计算释放动画起始 offset（松手时手指相对基线的偏移）
                        val releaseStartOffset = ReorderAlgorithms.computeReleaseStartOffset(
                            fingerY = fingerY,
                            baseCenterY = draggedBaseCenterY
                        )
                        // isDragActive 置 false（不再消费 pointerEvent）
                        // draggedKey 保持有效 → A 继续在「拖拽分支」中，animateItem 不重启
                        isDragActive = false

                        Log.d("ReorderableLazyColumn",
                            "[RELEASE_START] offset=$releaseStartOffset idx=$draggedCurrentIndex")

                        // 2. 提交排序（同步调用；ViewModel 内部 viewModelScope.launch 异步执行 DB 更新）
                        if (draggedOriginalIndex != draggedCurrentIndex && draggedOriginalIndex >= 0) {
                            val displayPinned = displayItems.map { isPinned(it) }
                            val crossedPinnedZone = ReorderAlgorithms.checkPinnedZoneCrossed(
                                displayItems = displayPinned,
                                draggedOriginalIsPinned = draggedOriginalIsPinned,
                                draggedCurrentIndex = draggedCurrentIndex
                            )
                            onReorder(
                                draggedOriginalIndex,
                                draggedCurrentIndex,
                                crossedPinnedZone
                            )

                            // 确认震动
                            HapticFeedbackManager.performHapticFeedback(
                                context = context,
                                type = InteractionType.CONFIRM,
                                enabled = true
                            )
                        }

                        // 3. 启动释放动画（250ms tween，与 animateItem 默认一致）
                        // 使用 rememberCoroutineScope 启动独立协程，不受 pointerInput 协程取消影响
                        scope.launch {
                            try {
                                isReleasing = true
                                releaseDragOffset.snapTo(releaseStartOffset)
                                Log.d("ReorderableLazyColumn",
                                    "[RELEASE_OFFSET_SNAP] from=$releaseStartOffset to=${releaseDragOffset.value}")
                                releaseDragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 250,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            } finally {
                                // 4. 动画结束后重置所有状态
                                isReleasing = false
                                draggedKey = null
                                draggedOriginalIndex = -1
                                draggedCurrentIndex = -1
                                fingerY = 0f
                                draggedBaseCenterY = 0f
                                lastSwapTargetKey = null
                                lastSwapFingerY = 0f
                                Log.d("ReorderableLazyColumn",
                                    "[RELEASE_END] isDragActive=$isDragActive")
                            }
                        }
                    } else if (isReleasing) {
                        // pointerInput 因异常/外部原因被取消，但释放动画仍在进行
                        // Animatable 协程独立于 pointerInput 协程，会自然完成
                        Log.d("ReorderableLazyColumn",
                            "[RELEASE_START] pointerInput cancelled during releasing")
                    }
                }
            }
        }
    ) {
        itemsIndexed(displayItems, key = { _, item -> key(item) }) { index, item ->
            val isDragging = key(item) == draggedKey

            if (isDragging) {
                // ━━━ 被拖项：虚线占位框 + 浮起卡片 ━━━
                Box(
                    modifier = Modifier
                        .animateItem()
                        .zIndex(1f)
                ) {
                    // 1. 虚线占位框（底层，定义 Box 尺寸）
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.4f),
                            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                            size = Size(
                                this.size.width - 2.dp.toPx(),
                                this.size.height - 2.dp.toPx()
                            ),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(12f, 8f), 0f
                                )
                            ),
                            cornerRadius = CornerRadius(16.dp.toPx())
                        )
                    }

                    // 2. 实际拖拽卡片（上层，offset 跟随手指）
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                            .graphicsLayer {
                                alpha = 0.7f
                                scaleX = 1.05f
                                scaleY = 1.05f
                                shadowElevation = 8.dp.toPx()
                            }
                    ) {
                        content(index, item, true, true)
                    }
                }
            } else {
                // ━━━ 普通项：animateItem 让位动画 ━━━
                Box(
                    modifier = Modifier
                        .animateItem()
                        .zIndex(0f)
                ) {
                    content(index, item, false, isDragActive)
                }
            }
        }
    }
}
