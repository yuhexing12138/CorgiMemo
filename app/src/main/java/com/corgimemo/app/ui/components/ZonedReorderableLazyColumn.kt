package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
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
 * @param onReorder 排序提交回调 (dragResult, fromIndex, toIndex)
 *                  - dragResult: 状态机输出的最终 zone 状态
 *                  - fromIndex: 被拖项在 displayItems 中的原始索引
 *                  - toIndex:   被拖项在 displayItems 中的最终索引
 * @param listState LazyListState 实例
 * @param modifier Modifier
 * @param content 列表项 Composable，参数为 (index, item, isDragging, isDragActive)
 */
@Composable
fun ZonedReorderableLazyColumn(
    items: List<DisplayItem>,
    isDragEnabled: Boolean,
    key: (DisplayItem) -> Any,
    onReorder: (dragResult: ZoneDragResult, fromIndex: Int, toIndex: Int) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
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
            val fromItem = displayItems.getOrNull(from.index)
            val toItem = displayItems.getOrNull(to.index)

            // ① 校验：from 和 to 都必须是 Todo 项（divider 不可拖拽、不可作为目标）
            if (fromItem !is DisplayItem.Todo || toItem !is DisplayItem.Todo) {
                return@rememberReorderableLazyListState
            }

            // ② 重排 displayItems
            val newDisplay = displayItems.toMutableList()
            newDisplay.removeAt(from.index)
            newDisplay.add(to.index, fromItem)
            displayItems = newDisplay

            // ③ 状态机追踪（仅 Todo 列表，过滤 divider）
            val todosOnly = newDisplay.filterIsInstance<DisplayItem.Todo>().map { it.item }
            val draggedTodoIndex = todosOnly.indexOfFirst { it.id == fromItem.item.id }
            val crossed = dragZoneState.onPositionChanged(todosOnly, draggedTodoIndex)

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
    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(
            items = displayItems,
            key = { _, it -> key(it) }
        ) { index, item ->
            when (item) {
                is DisplayItem.PinnedDivider,
                is DisplayItem.PendingDivider,
                is DisplayItem.CompletedDivider -> {
                    // Divider 不可拖拽，直接渲染
                    content(index, item, false, isDragActive)
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
                                        onReorder(
                                            dragResult,
                                            draggedOriginalIndex,
                                            draggedCurrentIndex
                                        )
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
