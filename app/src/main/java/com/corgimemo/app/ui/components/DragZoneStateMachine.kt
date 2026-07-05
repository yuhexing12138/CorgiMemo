package com.corgimemo.app.ui.components

import com.corgimemo.app.data.model.TodoItem

/**
 * Zone 拖拽结果
 *
 * 释放时由 [DragZoneStateMachine.endDrag] 输出，供 ViewModel 一次性持久化。
 *
 * 注：命名为 ZoneDragResult 以避免与 [CrossLineDragManager] 中的 DragResult 冲突。
 */
data class ZoneDragResult(
    val originalZone: TodoZone,
    val currentZone: TodoZone,
    val finalIsPinned: Boolean,
    val finalStatus: Int,
    val crossedZone: Boolean
)

/**
 * 拖拽 Zone 状态机
 *
 * 持续追踪被拖项的 originalZone / currentZone，跨区时即时翻转视觉层状态。
 * 释放时输出 [ZoneDragResult] 供 ViewModel 持久化。
 *
 * 关键不变式：
 * 1. 单一数据源：visualIsPinned / visualStatus 不写入数据库，释放时一次性持久化
 * 2. zone 推断不依赖 divider：inferZone 通过邻居项的 zone 推断
 * 3. 状态机无副作用：纯逻辑，UI 渲染层订阅 visualIsPinned / visualStatus 渲染
 * 4. 快速拖拽场景：onMove 跨多 zone 时 applyZoneTransition 基于字段差异计算（非增量），结果仍正确
 */
class DragZoneStateMachine {

    /** 被拖项原始 zone（拖拽开始时确定，全程不变） */
    var originalZone: TodoZone = TodoZone.PENDING
        private set

    /** 被拖项当前所在 zone（拖拽中实时更新） */
    var currentZone: TodoZone = TodoZone.PENDING
        private set

    /** 被拖项的视觉层 isPinned（实时翻转，未持久化） */
    var visualIsPinned: Boolean = false
        private set

    /** 被拖项的视觉层 status（实时翻转，未持久化） */
    var visualStatus: Int = 0
        private set

    /** 启动拖拽：记录原始 zone，初始化视觉状态 */
    fun startDrag(item: TodoItem) {
        originalZone = item.zone()
        currentZone = originalZone
        visualIsPinned = item.isPinned
        visualStatus = item.status
    }

    /**
     * 拖拽中位置变化：根据当前位置计算 currentZone，跨区时翻转视觉状态
     *
     * @param displayItems 仅含 Todo 的列表（divider 已被调用方过滤）
     * @param draggedIndex 被拖项当前在 displayItems 中的索引
     * @return true 表示发生跨区
     */
    fun onPositionChanged(displayItems: List<TodoItem>, draggedIndex: Int): Boolean {
        if (draggedIndex < 0 || draggedIndex >= displayItems.size) return false
        val newZone = inferZone(displayItems, draggedIndex)
        if (newZone != currentZone) {
            applyZoneTransition(currentZone, newZone)
            currentZone = newZone
            return true
        }
        return false
    }

    /** 释放：返回最终状态，供 ViewModel 持久化 */
    fun endDrag(): ZoneDragResult {
        return ZoneDragResult(
            originalZone = originalZone,
            currentZone = currentZone,
            finalIsPinned = visualIsPinned,
            finalStatus = visualStatus,
            crossedZone = originalZone != currentZone
        )
    }

    /**
     * 直接设置 currentZone 并应用视觉层翻转
     *
     * 用于外部基于更准确的信息源（如含 divider 的 displayItems）推断 zone 后，
     * 显式同步到状态机。
     *
     * 与 [onPositionChanged] 的区别：
     * - [onPositionChanged] 内部调用 inferZone 推断（基于 todosOnly）
     * - 本方法由调用方推断后传入，状态机只负责应用翻转
     *
     * @param newZone 新的 currentZone
     * @return true 表示发生跨区（currentZone 变化）
     */
    fun setZone(newZone: TodoZone): Boolean {
        if (newZone == currentZone) return false
        applyZoneTransition(currentZone, newZone)
        currentZone = newZone
        return true
    }

    /** 重置（用于拖拽取消或释放后） */
    fun reset() {
        originalZone = TodoZone.PENDING
        currentZone = TodoZone.PENDING
        visualIsPinned = false
        visualStatus = 0
    }

    /**
     * 内部同步方法：合并拖拽时由 MergeDragZoneStateMachine 调用，批量同步状态
     */
    internal fun syncFrom(other: DragZoneStateMachine) {
        currentZone = other.currentZone
        visualIsPinned = other.visualIsPinned
        visualStatus = other.visualStatus
    }

    /**
     * 根据 displayItems 中位置推断 zone（通过邻居项，不依赖 divider）
     *
     * 注：displayItems 是仅含 Todo 的列表（divider 已被调用方过滤）
     */
    private fun inferZone(displayItems: List<TodoItem>, draggedIndex: Int): TodoZone {
        // 1. 优先看前面最近的 Todo 项
        val prevIdx = draggedIndex - 1
        if (prevIdx >= 0) {
            return displayItems[prevIdx].zone()
        }
        // 2. 前面无项，看后面
        val nextIdx = draggedIndex + 1
        if (nextIdx < displayItems.size) {
            return displayItems[nextIdx].zone()
        }
        // 3. 仅自己，保持原 zone
        return originalZone
    }

    /** 应用 zone 转换：翻转视觉层 isPinned/status */
    private fun applyZoneTransition(from: TodoZone, to: TodoZone) {
        val fromPinned = from == TodoZone.PINNED_PENDING || from == TodoZone.PINNED_COMPLETED
        val toPinned = to == TodoZone.PINNED_PENDING || to == TodoZone.PINNED_COMPLETED
        val fromCompleted = from == TodoZone.PINNED_COMPLETED || from == TodoZone.COMPLETED
        val toCompleted = to == TodoZone.PINNED_COMPLETED || to == TodoZone.COMPLETED

        if (fromPinned != toPinned) visualIsPinned = toPinned
        if (fromCompleted != toCompleted) visualStatus = if (toCompleted) 1 else 0
    }
}

/**
 * 合并拖拽 Zone 状态机（多选拖拽）
 *
 * 所有选中项视为一个整体，anchor 项作为代表判定 zone，所有项同步翻转
 */
class MergeDragZoneStateMachine {

    private val itemStates = mutableMapOf<Long, DragZoneStateMachine>()

    /** 启动合并拖拽：为每个选中项初始化独立状态机 */
    fun startMergeDrag(items: List<TodoItem>) {
        itemStates.clear()
        items.forEach { item ->
            val sm = DragZoneStateMachine()
            sm.startDrag(item)
            itemStates[item.id] = sm
        }
    }

    /**
     * 合并拖拽中位置变化
     * - anchor 项作为代表判定 zone
     * - 所有项同步到 anchor 的 currentZone / visualIsPinned / visualStatus
     *
     * @return true 表示发生跨区
     */
    fun onPositionChanged(
        displayItems: List<TodoItem>,
        anchorIndex: Int
    ): Boolean {
        if (itemStates.isEmpty()) return false
        val anchorSm = itemStates.values.first()
        val crossed = anchorSm.onPositionChanged(displayItems, anchorIndex)
        if (crossed) {
            itemStates.values.forEach { sm ->
                sm.syncFrom(anchorSm)
            }
        }
        return crossed
    }

    /** 释放：返回所有项的 ZoneDragResult */
    fun endMergeDrag(): List<ZoneDragResult> {
        return itemStates.values.map { it.endDrag() }
    }

    /** 重置 */
    fun reset() {
        itemStates.clear()
    }
}
