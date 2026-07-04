package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 分隔按钮类型枚举
 *
 * 用于区分 displayItems 中三种不同的区域分隔按钮，
 * 让算法能基于 divider 类型直接判定区域，而非间接"找邻居"。
 *
 * - [PINNED]: 置顶区与待完成区之间的分隔按钮（PinnedDivider）
 * - [PENDING]: 待完成区与已完成区之间的分隔按钮（PendingDivider）
 * - [COMPLETED]: 已完成区之后的分隔按钮（CompletedDivider）
 */
enum class DividerKind {
    PINNED,
    PENDING,
    COMPLETED
}

/**
 * 简化版可拖拽列表面向非 LazyColumn 场景（基于 Calvin-LL/Reorderable 库）
 *
 * 对于少量固定数量的列表项（如设置页面），
 * 使用库的 ReorderableColumn（基于 Column）实现拖拽排序。
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
    val context = LocalContext.current

    sh.calvin.reorderable.ReorderableColumn(
        list = items,
        onSettle = { fromIndex, toIndex ->
            if (fromIndex != toIndex) {
                HapticFeedbackManager.performHapticFeedback(
                    context = context,
                    type = InteractionType.CONFIRM,
                    enabled = true
                )
                onReorder(fromIndex, toIndex)
            }
        },
        modifier = modifier,
    ) { index, item, isDragging ->
        ReorderableItem {
            Box(modifier = Modifier.longPressDraggableHandle()) {
                content(index, item, isDragging)
            }
        }
    }
}

/**
 * 拖拽排序算法纯函数集合
 *
 * 重构后仅保留置顶区跨越检测，其余算法由 Calvin-LL/Reorderable 库内部处理。
 */
object ReorderAlgorithms {

    /**
     * 检测拖拽是否跨越了置顶区边界
     *
     * 混合算法：
     * 1. 优先基于 divider 类型判定：扫描被拖项前面最近的 divider，
     *    根据 divider 类型直接确定当前所在区域（PINNED/PENDING/COMPLETED）
     * 2. 若无 divider（如 pinnedCount=0 的纯待办场景），回退到"找邻居 + 比较 isPinned"
     *
     * 优势：
     * - 有 divider 时：基于 divider 类型直接判定，语义更清晰
     * - 无 divider 时：回退到找邻居，保持向后兼容
     *
     * @param displayItems 全局显示列表（含分隔按钮和待办项）
     * @param isPinned 查询项是否置顶
     * @param dividerKind 查询项的分隔按钮类型，返回 null 表示非 divider
     * @param draggedOriginalIsPinned 被拖项原始 isPinned
     * @param draggedCurrentIndex 被拖项当前在 displayItems 中的索引
     * @return true 表示跨越了置顶区边界（需翻转 isPinned）
     */
    fun <T> checkPinnedZoneCrossed(
        displayItems: List<T>,
        isPinned: (T) -> Boolean,
        dividerKind: (T) -> DividerKind?,
        draggedOriginalIsPinned: Boolean,
        draggedCurrentIndex: Int
    ): Boolean {
        if (draggedCurrentIndex < 0 || draggedCurrentIndex >= displayItems.size) return false

        // 1. 扫描被拖项前面最近的 divider，确定其当前所在区域
        var currentZone: DividerKind? = null
        for (i in draggedCurrentIndex - 1 downTo 0) {
            val kind = dividerKind(displayItems[i])
            if (kind != null) {
                currentZone = kind
                break
            }
        }

        // 2. 若找到 divider，基于 divider 类型直接判定
        if (currentZone != null) {
            val originalZone = if (draggedOriginalIsPinned) DividerKind.PINNED else DividerKind.PENDING
            return currentZone != originalZone
        }

        // 3. 无 divider → 回退到"找邻居 + 比较 isPinned"（保持向后兼容）
        var neighborIdx = -1
        val prevIdx = draggedCurrentIndex - 1
        if (prevIdx >= 0 && dividerKind(displayItems[prevIdx]) == null) {
            neighborIdx = prevIdx
        }
        if (neighborIdx < 0) {
            val nextIdx = draggedCurrentIndex + 1
            if (nextIdx < displayItems.size && dividerKind(displayItems[nextIdx]) == null) {
                neighborIdx = nextIdx
            }
        }
        if (neighborIdx < 0) return false

        return draggedOriginalIsPinned != isPinned(displayItems[neighborIdx])
    }

    /**
     * 应用置顶区边界限制：占位框不能插入到置顶区起始位置之前
     *
     * 规则（对应原型 updatePlaceholderPosition 行 1481-1489）：
     * - 任何待办的占位框都不能插入到"置顶"标签上方
     * - 非置顶项仍可拖入置顶区（拖入后由外部 updateItemPinnedState 自动切换为置顶项）
     *
     * @param requestedIndex 请求的插入位置
     * @param pinnedStartIndex 置顶区起始位置（第一个 isPinned=true 的项的 index），-1 表示无置顶区
     * @return 限制后的安全插入位置
     */
    fun applyPinnedBoundary(
        requestedIndex: Int,
        pinnedStartIndex: Int
    ): Int {
        if (pinnedStartIndex < 0) return requestedIndex
        return if (requestedIndex < pinnedStartIndex) pinnedStartIndex else requestedIndex
    }
}

/**
 * 可拖拽排序 LazyColumn 容器组件（基于 Calvin-LL/Reorderable 库）
 *
 * **职责**：
 * - 长按进入拖拽模式（库内置 detectDragGesturesAfterLongPress）
 * - 拖拽中卡片半透明跟随手指，其他项 animateItem() 让位
 * - 库内置：边缘自动滚动（基于手柄中心距视口边缘）、释放回弹 spring 动画、首可见项保护
 * - 置顶区跨越自动检测
 * - 手指抬起提交排序，调用 onReorder
 * - 多选模式下长按已选中项触发合并拖拽（自定义 pointerInput 包装）
 *
 * **手势分层**：
 * - L1 库 longPressDraggableHandle：长按 + 拖拽（非多选模式，或多选模式下仅选中 1 项）
 * - L2 自定义 pointerInput：多选模式下 selectedIds.size > 1 时长按已选中项触发合并拖拽
 * - L3 SwipeableTodoBox：拖拽激活时通过 isDragActive 禁用左滑
 * - L4 TodoListItem：检测 isDragActive=true 时让位
 *
 * @param items 列表数据
 * @param isDragEnabled 是否允许拖拽（批量模式/左滑展开时设为 false）
 * @param isDraggable 判断项是否可拖拽（用于分隔按钮等不可拖拽项），默认全部可拖拽
 * @param isPinned 获取项是否置顶的函数（用于跨越检测与边界限制）
 * @param key 项的唯一标识
 * @param onReorder 排序提交回调 (fromIndex, toIndex, dividerIndex, crossedPinnedZone)
 * @param dividerIndex CompletedDivider 在 displayItems 中的真实索引（-1 表示没有已完成区）
 * @param isBatchMode 是否处于多选模式（来自 ViewModel）
 * @param selectedIds 已选中项的 key 集合（来自 ViewModel）
 * @param onMergeReorder 合并拖拽提交回调 (selectedIds, toIndex, dividerIndex, crossedPinnedZone)
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
    dividerKind: (T) -> DividerKind?,
    key: (T) -> Any,
    onReorder: (fromIndex: Int, toIndex: Int, dividerIndex: Int, crossedPinnedZone: Boolean) -> Unit,
    dividerIndex: Int = -1,
    isBatchMode: Boolean = false,
    selectedIds: Set<Any> = emptySet(),
    onMergeReorder: (selectedIds: Set<Any>, toIndex: Int, dividerIndex: Int, crossedPinnedZone: Boolean) -> Unit = { _, _, _, _ -> },
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, item: T, isDragging: Boolean, isDragActive: Boolean) -> Unit
) {
    val context = LocalContext.current

    // ━━━ 拖拽状态（从 15+ 精简到 5 个）━━━
    var displayItems by remember { mutableStateOf(items) }
    var isDragActive by remember { mutableStateOf(false) }
    var draggedOriginalIndex by remember { mutableIntStateOf(-1) }
    var draggedOriginalIsPinned by remember { mutableStateOf(false) }
    var crossedPinnedZone by remember { mutableStateOf(false) }

    /** 触觉反馈节流时间戳 */
    var lastHapticTime by remember { mutableLongStateOf(0L) }
    val hapticThrottleMs = 200L

    // ━━━ 合并拖拽状态（Task 6 新增）━━━
    /** 合并拖拽进行中标志 */
    var isMergeDragging by remember { mutableStateOf(false) }
    /** 合并拖拽中收集的选中项列表（按 displayItems 顺序，保留原相对顺序） */
    var mergeSelectedItems by remember { mutableStateOf(listOf<T>()) }
    /** 合并卡片跟随手指的累积 Y 偏移量（px） */
    var mergeFingerY by remember { mutableFloatStateOf(0f) }
    /** 占位框在 displayItems 中的插入位置 index */
    var mergePlaceholderIndex by remember { mutableIntStateOf(-1) }
    /** 合并卡片初始 Y 坐标（anchor 项在 LazyList 中的位置，用于计算合并卡片绝对位置） */
    var mergeCardInitialY by remember { mutableFloatStateOf(0f) }
    /** 累积拖拽量（用于阈值检测移动占位框） */
    var cumulativeDragY by remember { mutableFloatStateOf(0f) }

    // ━━━ 方案 C：释放动画状态（Task 6 优化）━━━
    /** 协程作用域，驱动释放动画 */
    val scope = rememberCoroutineScope()
    /** 释放动画进行中标志（区别于 isMergeDragging：拖拽中） */
    var isMergeAnimating by remember { mutableStateOf(false) }
    /** 合并卡片 alpha（0.98 正常 → 0 淡出，Animatable 驱动 cross-fade） */
    val cardAlpha = remember { Animatable(0.98f) }
    /** 合并卡片 Y 坐标（弹簧动画到占位框位置，Animatable 驱动） */
    val cardY = remember { Animatable(0f) }
    /** 原项淡入 alpha（0 → 1，Animatable 驱动） */
    val itemsAlpha = remember { Animatable(1f) }
    /** 后续项 translationY 补偿（-offset → 0，Animatable 驱动） */
    val itemsOffset = remember { Animatable(0f) }
    /** 释放动画期间保存的选中项 key 集合（用于区分原项与后续项） */
    var mergeSelectedKeys by remember { mutableStateOf(emptySet<Any>()) }
    /** 释放动画期间保存的合并拖拽项数量（用于计算后续项补偿范围） */
    var mergeSelectedCount by remember { mutableIntStateOf(0) }
    /** 释放动画初始化完成标志（消除 snapTo 前的一帧跳动：snapTo 是 suspend，需在协程中执行） */
    var mergeAnimInitDone by remember { mutableStateOf(false) }
    /** 释放动画的 transform 补偿量（同步存储，供渲染在 snapTo 执行前使用初始值） */
    var mergeAnimOffset by remember { mutableFloatStateOf(0f) }

    // ━━━ 多选模式下单选回退：selectedIds.size <= 1 时强制启用库的 handle ━━━
    // HomeScreen 传入 isDragEnabled = !isBatchMode，但多选模式下仅选中 1 项时
    // 应回退为普通拖拽，因此这里用 || 覆盖
    val effectiveDragEnabled = isDragEnabled || (isBatchMode && selectedIds.size <= 1)

    // ━━━ 同步外部 items 变更 ━━━
    LaunchedEffect(items) {
        if (isMergeAnimating) {
            // 释放动画中：onMergeReorder 触发 items 变更，允许 displayItems 更新
            // 但保持 isMergeDragging/isMergeAnimating，让释放动画继续播放
            displayItems = items
        } else if (!isDragActive && !isMergeDragging) {
            displayItems = items
        } else if (isMergeDragging) {
            // 合并拖拽中 items 被外部变更 → 取消合并拖拽
            displayItems = items
            isMergeDragging = false
            isDragActive = false
            mergeSelectedItems = emptyList()
            mergePlaceholderIndex = -1
            cumulativeDragY = 0f
        } else {
            // 普通拖拽中 items 被外部变更 → 取消拖拽
            displayItems = items
            isDragActive = false
            draggedOriginalIndex = -1
        }
    }

    // ━━━ 主题色（虚线占位框）━━━
    val primaryColor = MaterialTheme.colorScheme.primary

    // ━━━ 创建库的 ReorderableLazyListState ━━━
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            // ① 校验 from/to 是否指向不可拖拽项（CompletedDivider）
            val fromItem = displayItems.getOrNull(from.index)
            val toItem = displayItems.getOrNull(to.index)
            if (fromItem == null || toItem == null) return@rememberReorderableLazyListState
            if (!isDraggable(fromItem) || !isDraggable(toItem)) {
                return@rememberReorderableLazyListState
            }

            // ② 重排 displayItems
            val newDisplay = displayItems.toMutableList()
            val draggedItem = newDisplay.removeAt(from.index)
            newDisplay.add(to.index, draggedItem)
            displayItems = newDisplay

            // ③ 触觉反馈（节流）
            val now = System.currentTimeMillis()
            if (now - lastHapticTime > hapticThrottleMs) {
                HapticFeedbackManager.performHapticFeedback(
                    context = context,
                    type = InteractionType.TEXT_MOVE,
                    enabled = true
                )
                lastHapticTime = now
            }
        }
    )

    // ━━━ 合并拖拽：开始 ━━━
    /**
     * 开始合并拖拽：收集选中项 + 设置占位框初始位置 + 记录合并卡片初始位置
     * 对应原型 startMergeDrag（行 1667-1707）
     */
    fun startMergeDrag(anchor: T) {
        isMergeDragging = true
        isDragActive = true
        // 1. 收集已选中项（按 displayItems 顺序，保留原相对顺序）
        mergeSelectedItems = displayItems.filter { key(it) in selectedIds }
        // 2. 占位框初始位置 = anchor 项当前位置
        mergePlaceholderIndex = displayItems.indexOfFirst { key(it) == key(anchor) }
        // 3. 记录 anchor 在 LazyList 中的位置（用于合并卡片初始定位）
        val anchorItemInfo = listState.layoutInfo.visibleItemsInfo.find {
            it.key == key(anchor)
        }
        mergeCardInitialY = anchorItemInfo?.offset?.toFloat() ?: 0f
        // 4. 重置累积拖拽量
        mergeFingerY = 0f
        cumulativeDragY = 0f
        // 5. 触觉反馈
        HapticFeedbackManager.performHapticFeedback(
            context = context,
            type = InteractionType.TEXT_MOVE,
            enabled = true
        )
    }

    // ━━━ 合并拖拽：更新占位框位置（精确版，含置顶区边界限制）━━━
    /**
     * 拖拽中更新占位框位置（精确版）
     * 对应原型 updatePlaceholderPosition（行 1453-1506）
     *
     * 优化（Task 6）：使用 listState.layoutInfo.visibleItemsInfo 精确计算
     * 合并卡片中心 Y 与可见项中心 Y 的关系，替代固定 80px 阈值检测。
     *
     * 算法（对应设计文档 13.5）：
     * 1. 计算合并卡片中心 Y（相对于 LazyList 视口）
     * 2. 遍历可见项，找到第一个中心 Y > 合并卡片中心 Y 的项作为插入点
     * 3. 应用置顶区边界限制：任何待办的占位框都不能插入到"置顶"标签上方
     *
     * 坐标系说明：
     * - visibleItemsInfo.offset 是相对于 LazyList 视口顶部的偏移（考虑滚动）
     * - mergeCardInitialY 是开始拖拽时 anchor 项的 offset 快照
     * - mergeFingerY 是手指累积位移（不包含滚动）
     * - 若 anchor 项仍在可见区域，重新获取其 offset 以处理滚动
     */
    fun updateMergePlaceholderPosition() {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return

        // 1. 计算合并卡片中心 Y（相对于 LazyList 视口）
        // 若 anchor 项仍在可见区域，用其当前 offset 处理滚动；否则用初始快照
        val anchorKey = mergeSelectedItems.firstOrNull()?.let { key(it) }
        val anchorInfo = visibleItems.find { it.key == anchorKey }
        val effectiveAnchorY = anchorInfo?.offset?.toFloat() ?: mergeCardInitialY
        // 合并卡片高度近似为可见项高度（单项高度）
        val cardHeight = visibleItems.firstOrNull()?.size ?: 80
        val cardCenterY = effectiveAnchorY + mergeFingerY + cardHeight / 2f

        // 2. 遍历可见项，找到第一个中心 Y > cardCenterY 的项
        var insertBeforeLazyIndex = -1
        for (itemInfo in visibleItems) {
            // 跳过占位框本身
            if (itemInfo.key == "merge_placeholder") continue
            val itemCenterY = itemInfo.offset + itemInfo.size / 2
            if (cardCenterY < itemCenterY) {
                insertBeforeLazyIndex = itemInfo.index
                break
            }
        }

        // 3. 转换 LazyColumn index 为 displayItems index
        // 合并拖拽模式下 LazyColumn items 结构：
        //   [0..mergePlaceholderIndex-1] = displayItems[0..mergePlaceholderIndex-1]
        //   [mergePlaceholderIndex] = merge_placeholder（占位框）
        //   [mergePlaceholderIndex+1..] = displayItems[mergePlaceholderIndex..]
        val insertBeforeDisplayIndex = if (insertBeforeLazyIndex == -1) {
            // 没找到（手指在最后一个可见项之下）→ 放在最后
            displayItems.size
        } else if (mergePlaceholderIndex < 0 || insertBeforeLazyIndex <= mergePlaceholderIndex) {
            insertBeforeLazyIndex
        } else {
            insertBeforeLazyIndex - 1
        }

        // 4. 应用置顶区边界限制（设计文档 13.5）
        val pinnedStartIdx = displayItems.indexOfFirst { isPinned(it) }
        val limitedIndex = ReorderAlgorithms.applyPinnedBoundary(
            insertBeforeDisplayIndex, pinnedStartIdx
        )

        // 5. 更新占位框位置（带触觉反馈）
        if (limitedIndex != mergePlaceholderIndex && limitedIndex in 0..displayItems.size) {
            mergePlaceholderIndex = limitedIndex
            HapticFeedbackManager.performHapticFeedback(
                context = context,
                type = InteractionType.TEXT_MOVE,
                enabled = true
            )
        }
    }

    // ━━━ 合并拖拽：结束（方案 C：transform 补偿同步释放）━━━
    /**
     * 结束合并拖拽：cross-fade + transform 补偿同步释放
     * 对应原型 endDrag 合并分支（行 1545-1608），设计文档 13.3 Step 3
     *
     * 方案 C 核心思路（FLIP 变体消除原项展开导致的布局跳动）：
     * 1. 合并卡片弹簧动画到占位框位置 + 淡出（0.35s）
     * 2. 原项 alpha 锁定为 0 后淡入（0.2s，spring dampingRatio=0.7）
     * 3. 后续项 graphicsLayer translationY = -(N-1)×itemHeight 补偿后下移（0.3s）
     * 4. Animatable 替代原型双 rAF，同步启动 cross-fade
     *
     * @see <a href="设计文档 13.3 Step 3">方案 C 详细规范</a>
     */
    fun endMergeDrag() {
        val N = mergeSelectedItems.size
        if (N == 0 || mergePlaceholderIndex < 0 || mergePlaceholderIndex > displayItems.size) {
            // 无选中项或占位框位置无效 → 直接清理（内联 cancelMergeDrag 逻辑，避免前向引用）
            scope.launch {
                cardAlpha.snapTo(0.98f)
                cardY.snapTo(0f)
                itemsAlpha.snapTo(1f)
                itemsOffset.snapTo(0f)
            }
            isMergeDragging = false
            isMergeAnimating = false
            isDragActive = false
            mergeSelectedItems = emptyList()
            mergeSelectedKeys = emptySet()
            mergeSelectedCount = 0
            mergePlaceholderIndex = -1
            cumulativeDragY = 0f
            return
        }

        // 1. 计算置顶区跨越
        val anySelectedPinned = mergeSelectedItems.any { isPinned(it) }
        val crossed = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            isPinned = isPinned,
            dividerKind = dividerKind,
            draggedOriginalIsPinned = anySelectedPinned,
            draggedCurrentIndex = mergePlaceholderIndex.coerceAtMost(displayItems.size - 1)
        )

        // 2. 保存释放动画所需的选中项信息（displayItems 更新后用于区分原项与后续项）
        mergeSelectedKeys = mergeSelectedItems.map { key(it) }.toSet()
        mergeSelectedCount = N

        // 3. 获取单项高度（从可见项量取，用于计算 transform 补偿 offset）
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val itemHeightPx = visibleItems.firstOrNull { it.key != "merge_placeholder" }?.size?.toFloat() ?: 80f
        // 原项展开后多出的高度：(N-1) × 单项完整高度
        val offset = (N - 1) * itemHeightPx

        // 4. 记录占位框位置（合并卡片弹簧动画的目标位置）
        val placeholderInfo = visibleItems.find { it.key == "merge_placeholder" }
        val placeholderY = placeholderInfo?.offset?.toFloat() ?: (mergeCardInitialY + mergeFingerY)

        // 5. 调用外部回调执行批量重排（触发 items 变更 → LaunchedEffect 同步 displayItems）
        val selectedKeys = mergeSelectedItems.map { key(it) }.toSet()
        onMergeReorder(selectedKeys, mergePlaceholderIndex, dividerIndex, crossed)

        // 6. 确认触觉反馈
        HapticFeedbackManager.performHapticFeedback(
            context = context,
            type = InteractionType.CONFIRM,
            enabled = true
        )

        // 7. 进入释放动画阶段：isMergeDragging 保持 true（合并卡片仍显示），
        //    isMergeAnimating=true 切换渲染分支为"原项已展开 + transform 补偿"
        isMergeAnimating = true

        // 8. 方案 C：transform 补偿同步释放（Animatable 替代原型双 rAF）
        scope.launch {
            // 8.1 锁定初始状态（对应原型 inline style 锁定）
            cardY.snapTo(mergeCardInitialY + mergeFingerY)
            cardAlpha.snapTo(0.98f)
            itemsAlpha.snapTo(0f)
            itemsOffset.snapTo(-offset)

            // 8.2 同步启动 cross-fade + transform 补偿
            val cardYJob = launch {
                cardY.animateTo(placeholderY, spring(dampingRatio = 0.8f, stiffness = 300f))
            }
            val cardAlphaJob = launch {
                cardAlpha.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
            }
            val itemsAlphaJob = launch {
                itemsAlpha.animateTo(1f, spring(dampingRatio = 0.7f))
            }
            val itemsOffsetJob = launch {
                itemsOffset.animateTo(0f, spring())
            }

            // 8.3 等待所有动画完成
            cardYJob.join()
            cardAlphaJob.join()
            itemsAlphaJob.join()
            itemsOffsetJob.join()

            // 8.4 清理状态（保留多选模式，仍处于批量态）
            isMergeDragging = false
            isMergeAnimating = false
            isDragActive = false
            mergeSelectedItems = emptyList()
            mergeSelectedKeys = emptySet()
            mergeSelectedCount = 0
            mergePlaceholderIndex = -1
            cumulativeDragY = 0f

            // 8.5 重置 Animatable 到初始值，为下次合并拖拽准备
            cardAlpha.snapTo(0.98f)
            cardY.snapTo(0f)
            itemsAlpha.snapTo(1f)
            itemsOffset.snapTo(0f)
        }
    }

    // ━━━ 合并拖拽：取消 ━━━
    /**
     * 取消合并拖拽（手指取消、系统中断等）
     * 对应原型 onDragCancel
     */
    fun cancelMergeDrag() {
        // 异步重置 Animatable（snapTo 是 suspend 函数，需在协程中调用）
        scope.launch {
            cardAlpha.snapTo(0.98f)
            cardY.snapTo(0f)
            itemsAlpha.snapTo(1f)
            itemsOffset.snapTo(0f)
        }
        isMergeDragging = false
        isMergeAnimating = false
        isDragActive = false
        mergeSelectedItems = emptyList()
        mergeSelectedKeys = emptySet()
        mergeSelectedCount = 0
        mergePlaceholderIndex = -1
        cumulativeDragY = 0f
    }

    // ━━━ 渲染：Box 包裹 LazyColumn + 合并卡片浮层 ━━━
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isMergeAnimating) {
                // ━━━ 释放动画模式：原项已展开 + alpha 淡入 + 后续项 transform 补偿 ━━━
                // 对应原型 endDrag 合并分支的 DOM 操作后状态（行 1566-1606）
                // displayItems 已由 onMergeReorder 触发更新，原项已在正确位置
                val firstSelectedIndex = displayItems.indexOfFirst { key(it) in mergeSelectedKeys }
                itemsIndexed(
                    items = displayItems,
                    key = { _, item -> key(item) }
                ) { index, item ->
                    val isSelected = key(item) in mergeSelectedKeys
                    // 后续项 = 第一个选中项之后第 N 项开始（index >= firstSelectedIndex + N）
                    val isAfter = firstSelectedIndex >= 0 &&
                        index >= firstSelectedIndex + mergeSelectedCount
                    // 原项 alpha 由 itemsAlpha 驱动（0→1 淡入）
                    // 后续项 translationY 由 itemsOffset 驱动（-offset→0 补偿恢复）
                    val itemAlpha = if (isSelected) itemsAlpha.value else 1f
                    val itemTranslationY = if (isAfter) itemsOffset.value else 0f
                    Box(
                        modifier = Modifier.graphicsLayer {
                            alpha = itemAlpha
                            translationY = itemTranslationY
                        }
                    ) {
                        content(index, item, false, false)
                    }
                }
            } else if (isMergeDragging) {
                // ━━━ 合并拖拽模式：手动构建 items（不使用 ReorderableItem）━━━
                // 选中项折叠隐藏（height=0, alpha=0），占位框在 mergePlaceholderIndex 位置渲染
                displayItems.forEachIndexed { index, item ->
                    // 在占位框位置插入虚线边框占位框
                    if (index == mergePlaceholderIndex) {
                        item(key = "merge_placeholder", contentType = "placeholder") {
                            // 占位框：单项高度虚线边框（对应原型 .todo-item.placeholder）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .border(
                                        width = 2.dp,
                                        color = primaryColor.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            )
                        }
                    }
                    item(key = key(item), contentType = "todo") {
                        val isSelected = key(item) in selectedIds
                        // 多选模式下 selectedIds.size > 1 时，已选中项绑定自定义 pointerInput
                        val mergeDragModifier = if (
                            isBatchMode && isSelected && selectedIds.size > 1
                        ) {
                            Modifier.pointerInput(key(item)) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startMergeDrag(item) },
                                    onDrag = { _, dragAmount ->
                                        mergeFingerY += dragAmount.y
                                        updateMergePlaceholderPosition()
                                    },
                                    onDragEnd = { endMergeDrag() },
                                    onDragCancel = { cancelMergeDrag() }
                                )
                            }
                        } else {
                            Modifier
                        }
                        // 选中项折叠隐藏（等效原型 .merge-hidden：max-height:0, opacity:0）
                        val hiddenModifier = if (isSelected) {
                            Modifier
                                .height(0.dp)
                                .alpha(0f)
                        } else {
                            Modifier
                        }
                        Box(
                            modifier = Modifier
                                .then(mergeDragModifier)
                                .then(hiddenModifier)
                        ) {
                            content(index, item, false, true)
                        }
                    }
                }
            } else {
                // ━━━ 普通模式：使用库的 ReorderableItem ━━━
                itemsIndexed(
                    items = displayItems,
                    key = { _, item -> key(item) }
                ) { index, item ->
                    ReorderableItem(
                        state = reorderableState,
                        key = key(item),
                        enabled = effectiveDragEnabled && isDraggable(item)
                    ) { isDragging ->
                        // longPressDraggableHandle 启用条件：
                        // - effectiveDragEnabled（含多选模式单选回退）
                        // - isDraggable(item)
                        // - 非多选模式，或多选模式下仅选中 1 项（回退普通拖拽）
                        val handleEnabled = effectiveDragEnabled && isDraggable(item) &&
                            (!isBatchMode || selectedIds.size <= 1)
                        Box(
                            modifier = Modifier
                                .longPressDraggableHandle(
                                    enabled = handleEnabled,
                                    onDragStarted = {
                                        // 注意：draggedOriginalIndex 与 draggedOriginalIsPinned 必须用 displayItems 计算，
                                        // 与 onDragStopped 中 draggedCurrentIndex 的参照系保持一致。
                                        // 原因：onDragStarted lambda 可能捕获旧的 item（因 longPressDraggableHandle
                                        // 的 pointerInput 未因 items 变化而重启），导致 isPinned(item) 返回旧值，
                                        // 进而引发 "已置顶项二次拖拽时被误判为跨区" 的位置跳跃。
                                        isDragActive = true
                                        val draggedIdx = displayItems.indexOfFirst {
                                            key(it) == key(item)
                                        }
                                        draggedOriginalIndex = draggedIdx
                                        draggedOriginalIsPinned = if (draggedIdx >= 0) isPinned(displayItems[draggedIdx]) else false
                                        crossedPinnedZone = false
                                        HapticFeedbackManager.performHapticFeedback(
                                            context = context,
                                            type = InteractionType.TEXT_MOVE,
                                            enabled = true
                                        )
                                    },
                                    onDragStopped = {
                                        val draggedCurrentIndex = displayItems.indexOfFirst {
                                            key(it) == key(item)
                                        }
                                        if (draggedOriginalIndex >= 0 &&
                                            draggedOriginalIndex != draggedCurrentIndex &&
                                            draggedCurrentIndex >= 0
                                        ) {
                                            crossedPinnedZone = ReorderAlgorithms.checkPinnedZoneCrossed(
                                                displayItems = displayItems,
                                                isPinned = isPinned,
                                                dividerKind = dividerKind,
                                                draggedOriginalIsPinned = draggedOriginalIsPinned,
                                                draggedCurrentIndex = draggedCurrentIndex
                                            )
                                            onReorder(
                                                draggedOriginalIndex,
                                                draggedCurrentIndex,
                                                dividerIndex,
                                                crossedPinnedZone
                                            )
                                            HapticFeedbackManager.performHapticFeedback(
                                                context = context,
                                                type = InteractionType.CONFIRM,
                                                enabled = true
                                            )
                                        }
                                        isDragActive = false
                                        draggedOriginalIndex = -1
                                        crossedPinnedZone = false
                                    }
                                )
                        ) {
                            if (isDragging) {
                                // ━━━ 被拖项：虚线边框装饰 + 浮起卡片 ━━━
                                Box {
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
                                    Box(
                                        modifier = Modifier.graphicsLayer {
                                            alpha = 0.9f
                                            scaleX = 1.05f
                                            scaleY = 1.05f
                                            shadowElevation = 8.dp.toPx()
                                        }
                                    ) {
                                        content(index, item, true, true)
                                    }
                                }
                            } else {
                                // ━━━ 普通项：animateItem 让位动画（库默认 spring）━━━
                                content(index, item, false, isDragActive)
                            }
                        }
                    }
                }
            }
        }

        // ━━━ 合并卡片浮层（脱离 LazyColumn，渲染在 Box 上层）━━━
        // 对应原型 .merged-card：position:fixed 跟随手指
        // 释放动画中：cardY/cardAlpha 由 Animatable 驱动（弹簧动画到占位框 + 淡出）
        // 拖拽中：cardY = mergeCardInitialY + mergeFingerY（跟随手指），cardAlpha = 0.98
        if (isMergeDragging && mergeSelectedItems.isNotEmpty()) {
            val cardYValue = if (isMergeAnimating) cardY.value else (mergeCardInitialY + mergeFingerY)
            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset(0, cardYValue.roundToInt())
                    }
                    .graphicsLayer {
                        alpha = cardAlpha.value
                        shadowElevation = 8.dp.toPx()
                        scaleX = 1.05f
                        scaleY = 1.05f
                    },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "选中 ${mergeSelectedItems.size} 项",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryColor
                    )
                }
            }
        }
    }
}
