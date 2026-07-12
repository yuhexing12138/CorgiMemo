package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.ui.screens.home.DisplayItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 基于 Zone 状态机的可拖拽排序 LazyColumn
 *
 * 与 [ReorderableLazyColumn] 的差异：
 * - 使用 [DragZoneStateMachine] 追踪被拖项的 originalZone / currentZone
 * - onMove 中触发 [DragZoneStateMachine.onPositionChanged]，跨区时即时翻转视觉层状态
 * - 渲染层绑定 [DragZoneStateMachine.visualIsPinned] / [DragZoneStateMachine.visualStatus]
 *   （被拖项的视觉状态实时反映跨区结果，未持久化）
 * - onDragStopped 调用 [DragZoneStateMachine.endDrag] 输出 [ZoneDragResult]，
 *   通过 [onReorder] 一次性提交给 ViewModel 持久化
 *
 * 简化点（相比 [ReorderableLazyColumn]）：
 * - 不含合并拖拽（多选拖拽）逻辑
 * - 不含释放动画（cross-fade / transform 补偿）
 * - 不含被拖项装饰（虚线边框 / 浮起卡片），由调用方通过 [content] 的 isDragging 自行处理
 *
 * @param items 显示列表（含 divider 与 Todo）
 * @param isDragEnabled 是否允许拖拽（批量模式 / 左滑展开时设为 false）
 * @param key 项的唯一标识
 * @param onReorder 排序提交回调 (dragResult, draggedItem, targetZoneRelativeIndex)
 *                  - dragResult: 状态机输出的最终 zone 状态
 *                  - draggedItem: 被拖项（基于组件内部 displayItems 取得，类型安全）
 *                  - targetZoneRelativeIndex: 目标 zone 内的相对索引（仅统计同 zone Todo）
 * @param listState LazyListState 实例
 * @param modifier Modifier
 * @param headerContent 列表前置项内容（搜索框、下拉刷新 spacer 等），不参与拖拽排序
 * @param headerItemCount 前置项数量（用于 onMove 索引偏移：全局索引 → displayItems 索引）
 * @param footerContent 列表尾部项内容（如避开 FAB 的底部 Spacer），不参与拖拽排序
 * @param itemSpacing 列表项之间的间距
 * @param content 列表项 Composable，参数为 (index, item, isDragging, isDragActive)
 */
@Composable
fun ZonedReorderableLazyColumn(
    items: List<DisplayItem>,
    isDragEnabled: Boolean,
    key: (DisplayItem) -> Any,
    onReorder: (
        dragResult: ZoneDragResult,
        draggedItem: DisplayItem.Todo,
        targetZoneRelativeIndex: Int
    ) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    headerContent: LazyListScope.() -> Unit = {},
    /**
     * 列表底部追加项内容
     *
     * 用途：在 LazyColumn 末尾添加额外 item（如 Spacer），用于：
     * - 避开底部悬浮 FAB（参考灵感页 InspirationScreen 的 80.dp Spacer）
     * - 避开底部固定操作栏
     *
     * 注意：footerContent 不参与拖拽排序，与 headerContent 行为一致。
     */
    footerContent: LazyListScope.() -> Unit = {},
    headerItemCount: Int = 0,
    itemSpacing: Dp = 0.dp,
    content: @Composable (index: Int, item: DisplayItem, isDragging: Boolean, isDragActive: Boolean) -> Unit
) {
    val context = LocalContext.current

    // ━━━ 拖拽状态 ━━━
    var displayItems by remember { mutableStateOf(items) }
    var isDragActive by remember { mutableStateOf(false) }
    var draggedOriginalIndex by remember { mutableIntStateOf(-1) }
    val dragZoneState = remember { DragZoneStateMachine() }

    // ━━━ 同步外部 items 变更 ━━━
    // 拖拽中 items 被外部变更 → 取消拖拽（防御性处理）
    LaunchedEffect(items) {
        if (!isDragActive) {
            displayItems = items
        } else {
            displayItems = items
            isDragActive = false
            draggedOriginalIndex = -1
            dragZoneState.reset()
        }
    }

    // ━━━ 创建库的 ReorderableLazyListState ━━━
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            // 全局索引（含 header）→ displayItems 索引（不含 header）
            // from.index / to.index 是 LazyColumn 全局索引（含 headerContent 项），
            // 而 displayItems 仅含 divider + Todo，需在所有访问 displayItems 的索引处减去 headerItemCount。
            val fromIdx = from.index - headerItemCount
            val toIdx = to.index - headerItemCount
            val fromItem = displayItems.getOrNull(fromIdx)
            // ① 仅校验 from 是 Todo（divider 不可拖拽，由 Modifier.draggable(enabled=false) 保证）
            //    to 可以是 Todo 或 divider（divider 已加入 reorderableKeys，可作为目标）
            if (fromItem !is DisplayItem.Todo) {
                return@rememberReorderableLazyListState
            }

            // ② 重排 displayItems（A 跨过 divider / Todo，divider 不动）
            val newDisplay = displayItems.toMutableList()
            newDisplay.removeAt(fromIdx)
            newDisplay.add(toIdx, fromItem)
            displayItems = newDisplay

            // ③ 基于 newDisplay 推断 currentZone（含 divider，准确识别 zone 边界）
            // 注：indexOfFirst 基于 item.id 匹配，与 header 偏移无关，无需额外偏移。
            val newDraggedIdx = newDisplay.indexOfFirst {
                (it as? DisplayItem.Todo)?.item?.id == fromItem.item.id
            }
            val newZone = inferZoneFromDisplayItems(newDisplay, newDraggedIdx)
            val crossed = newZone != dragZoneState.currentZone
            if (crossed) {
                dragZoneState.setZone(newZone)
            }

            // ④ 跨区触觉反馈
            if (crossed) {
                HapticFeedbackManager.performHapticFeedback(
                    context = context,
                    type = InteractionType.CONFIRM,
                    enabled = true
                )
            }
        }
    )

    // ━━━ 渲染 ━━━
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        // 前置项（搜索框、下拉刷新 spacer 等），不参与拖拽排序
        headerContent()
        itemsIndexed(
            items = displayItems,
            key = { _, it -> key(it) }
        ) { index, item ->
            when (item) {
                is DisplayItem.PinnedDivider,
                is DisplayItem.PendingDivider,
                is DisplayItem.CompletedDivider -> {
                    // Divider 包裹 ReorderableItem(enabled=true) 加入 reorderableKeys，
                    // 使其可作为 onMove 的 to 目标（其他项可跨过 divider）。
                    // 但 Modifier.longPressDraggableHandle(enabled=false) 保证 divider 本身不可被拖拽。
                    // 注：库的 longPressDraggableHandle 是 ReorderableCollectionItemScope 的扩展，
                    // 不是 Compose 标准库的 Modifier.draggable（后者需要 state + orientation 参数）。
                    val dividerKey = when (item) {
                        is DisplayItem.PinnedDivider -> "pinned_divider"
                        is DisplayItem.PendingDivider -> "pending_divider"
                        is DisplayItem.CompletedDivider -> "completed_divider"
                        else -> error("不可达：外层 when 已过滤非 divider 类型")
                    }
                    ReorderableItem(
                        state = reorderableState,
                        key = dividerKey,
                        enabled = true   // 加入 reorderableKeys，可作为 to
                    ) {
                        Box(
                            modifier = Modifier.longPressDraggableHandle(enabled = false)  // 不可拖拽
                        ) {
                            content(index, item, false, isDragActive)
                        }
                    }
                }
                is DisplayItem.Todo -> {
                    ReorderableItem(
                        state = reorderableState,
                        key = item.item.id,
                        enabled = isDragEnabled
                    ) { isDragging ->
                        Box(
                            modifier = Modifier.longPressDraggableHandle(
                                enabled = isDragEnabled,
                                onDragStarted = {
                                    // 注意：必须用 displayItems 计算索引与原始数据，
                                    // 与 onDragStopped 中 draggedCurrentIndex 的参照系保持一致。
                                    // 原因：onDragStarted lambda 可能捕获旧的 item（因 longPressDraggableHandle
                                    // 的 pointerInput 未因 items 变化而重启），导致返回旧值。
                                    isDragActive = true
                                    val draggedIdx = displayItems.indexOfFirst {
                                        key(it) == key(item)
                                    }
                                    draggedOriginalIndex = draggedIdx
                                    val draggedTodoItem = (displayItems.getOrNull(draggedIdx)
                                        as? DisplayItem.Todo)?.item
                                    if (draggedTodoItem != null) {
                                        dragZoneState.startDrag(draggedTodoItem)
                                    }
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
                                        // 状态机输出最终 zone 状态
                                        val dragResult = dragZoneState.endDrag()

                                        // 基于内部 displayItems（已被 onMove 更新）取被拖项
                                        val draggedTodoItem = displayItems[draggedCurrentIndex]
                                            as? DisplayItem.Todo
                                            ?: return@longPressDraggableHandle

                                        // 基于 dragResult.currentZone 计算目标 zone 内相对索引
                                        val relativeIndex = computeRelativeIndexInZone(
                                            displayItems = displayItems,
                                            draggedCurrentIndex = draggedCurrentIndex,
                                            targetZone = dragResult.currentZone
                                        )

                                        onReorder(dragResult, draggedTodoItem, relativeIndex)

                                        HapticFeedbackManager.performHapticFeedback(
                                            context = context,
                                            type = InteractionType.CONFIRM,
                                            enabled = true
                                        )
                                    }
                                    isDragActive = false
                                    draggedOriginalIndex = -1
                                    dragZoneState.reset()
                                }
                            )
                        ) {
                            // 被拖项：绑定状态机的视觉层 isPinned/status（实时翻转，未持久化）
                            val displayItem = if (isDragging) {
                                item.copy(
                                    item = item.item.copy(
                                        isPinned = dragZoneState.visualIsPinned,
                                        status = dragZoneState.visualStatus
                                    )
                                )
                            } else {
                                item
                            }
                            content(index, displayItem, isDragging, isDragActive)
                        }
                    }
                }
            }
        }
        // 尾部追加项（如避开 FAB 的底部 Spacer），不参与拖拽排序
        footerContent()
    }
}

/**
 * 计算被拖项在目标 zone 内的相对索引
 *
 * 仅统计 displayItems[0..draggedCurrentIndex-1] 中与 targetZone 相同的 Todo 项数量，
 * 跳过 divider 与其他 zone 的 Todo。
 *
 * 内部可见（internal），便于单元测试。
 *
 * @param displayItems 组件内部 displayItems（已被 onMove 更新）
 * @param draggedCurrentIndex 被拖项在 displayItems 中的当前位置
 * @param targetZone 目标 zone（来自 [ZoneDragResult.currentZone]）
 * @return 目标 zone 内的相对索引（0..size）
 */
internal fun computeRelativeIndexInZone(
    displayItems: List<DisplayItem>,
    draggedCurrentIndex: Int,
    targetZone: TodoZone
): Int {
    var relativeIndex = 0
    for (i in 0 until draggedCurrentIndex) {
        val item = displayItems[i]
        if (item is DisplayItem.Todo && item.item.zone() == targetZone) {
            relativeIndex++
        }
    }
    return relativeIndex
}

/**
 * 基于 displayItems（含 divider）推断被拖项的当前 zone
 *
 * 算法：从被拖项位置向前扫描，遇到最近的 divider 即确定 zone：
 * - PinnedDivider → PINNED_PENDING（前面是置顶待完成区）
 * - PendingDivider → PENDING（前面是待完成区）
 * - CompletedDivider → COMPLETED（前面是已完成区）
 *
 * 若扫描到列表开头仍无 divider，根据被拖项自身 zone 推断（防御性回退）。
 *
 * 注：与 DragZoneStateMachine.inferZone 的区别：
 * - DragZoneStateMachine.inferZone 基于 todosOnly（divider 已过滤），看邻居 Todo 的 zone
 * - 本函数基于含 divider 的 displayItems，直接识别 divider 类型，准确度更高
 *
 * @param displayItems 含 divider 的完整显示列表
 * @param draggedIndex 被拖项在 displayItems 中的当前位置
 * @return 被拖项当前所在 zone
 */
internal fun inferZoneFromDisplayItems(
    displayItems: List<DisplayItem>,
    draggedIndex: Int
): TodoZone {
    // 1. 从被拖项前一项向前扫描，找最近的 divider
    for (i in draggedIndex - 1 downTo 0) {
        when (displayItems[i]) {
            is DisplayItem.PinnedDivider -> return TodoZone.PINNED_PENDING
            is DisplayItem.PendingDivider -> return TodoZone.PENDING
            is DisplayItem.CompletedDivider -> return TodoZone.COMPLETED
            else -> { /* Todo，继续向前扫描 */ }
        }
    }
    // 2. 扫描到列表开头仍无 divider，看被拖项自身 zone（单 zone 列表场景）
    val draggedItem = displayItems.getOrNull(draggedIndex)
    return if (draggedItem is DisplayItem.Todo) draggedItem.item.zone()
           else TodoZone.PENDING
}
