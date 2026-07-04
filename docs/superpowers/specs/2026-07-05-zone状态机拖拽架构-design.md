# Zone 状态机拖拽架构 设计文档

> 创建日期：2026-07-05
> 作者：CorgiMemo Team
> 状态：已审核（待实施）

---

## 0. 概述

### 0.1 目标

重构 CorgiMemo 的待办卡片拖拽架构，**根治跨区域拖拽时的位置跳跃问题**，实现置顶区/待完成区/已完成区三个区域的平滑拖拽体验。

### 0.2 核心思路

引入 `TodoZone` 4 值枚举与 `DragZoneStateMachine` 状态机，**拖拽中持续追踪被拖项的 currentZone，跨区时即时翻转视觉层 `isPinned`/`status`，释放时一次性持久化**。

### 0.3 范围

全栈重构：
- 数据模型（`TodoZone` 派生字段 + divider 仍含于 displayItems 但通过校验根治跨 divider）
- 组件层（`ZonedReorderableLazyColumn` + `DragZoneStateMachine`）
- ViewModel 层（`reorderOnDragResult` + 4 个独立 List）
- 数据库 Migration（sortOrder 按 zone 分段重算）

**约束**：保留 Calvin-LL/Reorderable 库不变。

---

## 1. 背景与问题

### 1.1 已修复的跳跃 bug 时间线

| # | Bug | 修复方式 | 是否根治 |
|---|-----|----------|----------|
| 1 | divider 边界 stale | `checkPinnedZoneCrossed` 遇 divider 即停止 | 治标 |
| 2 | 置顶按钮显示阈值 | 统一为 `pinnedCount >= 1` | 已根治 |
| 3 | isDivider 无法区分类型 | 引入 `DividerKind` 枚举 | 已根治 |
| 4 | 已完成区内拖拽跳跃 | `currentZone=COMPLETED` 回退到找邻居 | 治标 |

### 1.2 跳跃问题根因清单

| # | 根因 | 触发场景 |
|---|------|----------|
| 1 | **事后推断 zone**：`crossedPinnedZone` 在释放时基于当前位置推断，而非拖拽中持续追踪 | 跨区拖拽时 isPinned 翻转时机错误 |
| 2 | **库 onMove 不感知 divider**：库试图把项移到 divider 位置 | 跨区拖拽边界跳跃 |
| 3 | **索引转换复杂**：`pendingOffset`、`dividerIndex - 1` 等偏移公式易错 | 跨区拖拽位置偏移 |
| 4 | **sortOrder 重新分配**：跨区翻转 isPinned 后，sortOrder 重排导致位置变化 | 跨区拖拽后位置跳到区域顶部/底部 |
| 5 | **visibleCompletedTodos 排序**：`compareByDescending(isPinned).thenBy(sortOrder).thenByDescending(createdAt)` | 拖入已完成区后位置不可预期 |
| 6 | ~~子任务约束静默拒绝~~ | **已删除子任务约束**，父任务可独立完成 |
| 7 | **合并拖拽索引偏移**：多选拖拽跨区时的批量索引转换 | 合并拖拽位置错误 |
| 8 | **draggedCurrentIndex 依赖库内部状态** | 偶发位置错误 |

### 1.3 设计原则

1. **zone 持续追踪**：拖拽中持续追踪 currentZone，杜绝事后推断
2. **视觉翻转 + 释放时持久化**：拖拽中即时翻转视觉层状态，释放时一次性写数据库
3. **单一数据源**：`TodoZone` 是派生字段，不持久化到数据库
4. **zone 分段 sortOrder**：每个 zone 独立分配 sortOrder，跨区拖拽仅重排受影响 zone

---

## 2. 数据模型重构

### 2.1 TodoZone 4 值枚举

引入 `TodoZone` 枚举，作为 `(isPinned, status)` 笛卡尔积的语义化封装：

```kotlin
enum class TodoZone {
    PINNED_PENDING,    // isPinned=true,  status=0（置顶待完成）
    PENDING,           // isPinned=false, status=0（普通待完成）
    PINNED_COMPLETED,  // isPinned=true,  status=1（置顶已完成）
    COMPLETED          // isPinned=false, status=1（普通已完成）
}

/** 派生函数：由 isPinned + status 计算 zone */
fun TodoItem.zone(): TodoZone = when {
    isPinned && status == 0 -> TodoZone.PINNED_PENDING
    !isPinned && status == 0 -> TodoZone.PENDING
    isPinned && status == 1 -> TodoZone.PINNED_COMPLETED
    else -> TodoZone.COMPLETED
}
```

**关键决策**：zone 是派生字段，不持久化到数据库。数据源仍是 `isPinned` + `status`，避免双源不一致。无需 migration。

### 2.2 为什么 4 个值而非 3 个？

之前 bug 的根因正是"已完成区内没有 PinnedDivider，无法用 divider 判断置顶边界"。4 值 zone 把"置顶边界"从 divider 抽象出来，无论 UI 是否有 divider，zone 都能精准判定跨区。

### 2.3 DisplayItem 结构

```kotlin
sealed interface DisplayItem {
    data class Todo(val item: TodoItem) : DisplayItem
    data class PinnedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class PendingDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class CompletedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
}
```

displayItems 仍包含 divider（保持库的索引一致），但 `onMove` 通过 `isDraggable` 校验根治 divider 跨越。

### 2.4 UI 渲染顺序

```
PinnedDivider         (仅当 PINNED_PENDING 区非空时显示)
  PINNED_PENDING 区 Todo 们
PendingDivider       (始终显示)
  PENDING 区 Todo 们
CompletedDivider     (仅当已完成区非空时显示)
  PINNED_COMPLETED 区 Todo 们
  COMPLETED 区 Todo 们
```

UI 上仍是 3 个 divider（PINNED_COMPLETED 和 COMPLETED 共享 CompletedDivider），但拖拽时 zone 用 4 值精准判定。

---

## 3. Zone 状态机设计

### 3.1 核心问题：根除"事后推断"

当前架构的所有跳跃 bug 本质都是同一类问题：**拖拽过程中不追踪 zone，释放时才推断**。状态机的设计目标：**拖拽过程中持续追踪被拖项的 currentZone，跨区时即时翻转视觉层 isPinned/status**。

### 3.2 状态机定义

```kotlin
/**
 * 拖拽 Zone 状态机
 *
 * 持续追踪被拖项的 originalZone / currentZone，跨区时即时翻转视觉层状态。
 * 释放时输出最终 (item, originalZone, currentZone) 三元组供 ViewModel 持久化。
 */
class DragZoneStateMachine {
    var originalZone: TodoZone = TodoZone.PENDING
        private set
    var currentZone: TodoZone = TodoZone.PENDING
        private set
    var visualIsPinned: Boolean = false
        private set
    var visualStatus: Int = 0
        private set

    fun startDrag(item: TodoItem) {
        originalZone = item.zone()
        currentZone = originalZone
        visualIsPinned = item.isPinned
        visualStatus = item.status
    }

    /**
     * 拖拽中位置变化：根据当前位置计算 currentZone，跨区时翻转视觉状态
     * @return true 表示发生跨区
     */
    fun onPositionChanged(displayItems: List<TodoItem>, draggedIndex: Int): Boolean {
        val newZone = inferZone(displayItems, draggedIndex)
        if (newZone != currentZone) {
            applyZoneTransition(currentZone, newZone)
            currentZone = newZone
            return true
        }
        return false
    }

    fun endDrag(): DragResult {
        return DragResult(
            originalZone = originalZone,
            currentZone = currentZone,
            finalIsPinned = visualIsPinned,
            finalStatus = visualStatus,
            crossedZone = originalZone != currentZone
        )
    }

    fun reset() {
        originalZone = TodoZone.PENDING
        currentZone = TodoZone.PENDING
        visualIsPinned = false
        visualStatus = 0
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

data class DragResult(
    val originalZone: TodoZone,
    val currentZone: TodoZone,
    val finalIsPinned: Boolean,
    val finalStatus: Int,
    val crossedZone: Boolean
)
```

### 3.3 状态机时序

```
用户长按 → startDrag(item)
    ↓ originalZone / currentZone / visualIsPinned / visualStatus 初始化
拖拽中（库 onMove 触发）
    ↓ onPositionChanged(displayItems, draggedIndex)
    ↓   计算 newZone = inferZone(...)
    ↓   if (newZone != currentZone) → applyZoneTransition → 翻转视觉状态
被拖项的视觉 isPinned/status 实时变化
释放 → endDrag()
    ↓ 返回 DragResult(finalIsPinned, finalStatus, crossedZone)
    ↓ ViewModel 持久化（一次写入）
```

### 3.4 关键不变式

1. **单一数据源**：拖拽中 `visualIsPinned` / `visualStatus` 是视觉层状态，不写入数据库；释放时一次性持久化
2. **zone 推断不依赖 divider**：`inferZone` 通过邻居项的 zone 推断，divider 被过滤掉
3. **状态机无副作用**：所有方法都是纯逻辑，UI 渲染层订阅 `visualIsPinned` / `visualStatus` 渲染
4. **快速拖拽场景**：若 onMove 一次性把项从 zone A 跳到 zone C（跨过 zone B），`inferZone` 通过相邻邻居直接得到 C，`applyZoneTransition(A, C)` 基于字段差异计算（非增量），翻转结果仍然正确

---

## 4. 组件层架构

### 4.1 核心挑战：divider 虚拟化与库的协作

Calvin-LL/Reorderable 库通过 key 而非绝对索引匹配 item。采用"伪虚拟化"折中方案：displayItems 仍包含 divider，但 `onMove` 校验 `isDraggable` 杜绝 divider 参与拖拽。

### 4.2 ZonedReorderableLazyColumn 组件

```kotlin
@Composable
fun ZonedReorderableLazyColumn(
    pinnedPendingTodos: List<TodoItem>,
    pendingTodos: List<TodoItem>,
    pinnedCompletedTodos: List<TodoItem>,
    completedTodos: List<TodoItem>,
    showPinned: Boolean,
    showPending: Boolean,
    showCompleted: Boolean,
    onReorder: (dragResult: DragResult, fromIndex: Int, toIndex: Int) -> Unit,
    // ... 其他参数
) {
    var internalDisplayItems by remember { mutableStateOf(items) }
    val dragZoneState = remember { DragZoneStateMachine() }
    var draggedOriginalIndex by remember { mutableIntStateOf(-1) }
    var isDragActive by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromItem = internalDisplayItems.getOrNull(from.index)
            val toItem = internalDisplayItems.getOrNull(to.index)
            // 校验：from 和 to 都必须是 Todo 项（divider 不可拖拽、不可作为目标）
            if (fromItem !is DisplayItem.Todo || toItem !is DisplayItem.Todo) {
                return@rememberReorderableLazyListState
            }

            val newDisplay = internalDisplayItems.toMutableList()
            val draggedItem = newDisplay.removeAt(from.index)
            newDisplay.add(to.index, draggedItem)
            internalDisplayItems = newDisplay

            // 状态机追踪（仅 Todo 列表）
            val todosOnly = newDisplay.filterIsInstance<DisplayItem.Todo>().map { it.item }
            val draggedTodoIndex = todosOnly.indexOfFirst {
                it.id == (draggedItem as DisplayItem.Todo).item.id
            }
            dragZoneState.onPositionChanged(todosOnly, draggedTodoIndex)
        }
    )

    LazyColumn(state = listState) {
        itemsIndexed(internalDisplayItems, key = { _, it -> it.key }) { index, item ->
            when (item) {
                is DisplayItem.PinnedDivider -> PinnedDivider(...)
                is DisplayItem.PendingDivider -> PendingDivider(...)
                is DisplayItem.CompletedDivider -> CompletedDivider(...)
                is DisplayItem.Todo -> {
                    val isDragged = (index == draggedOriginalIndex)
                    val displayIsPinned = if (isDragged) dragZoneState.visualIsPinned else item.item.isPinned
                    val displayStatus = if (isDragged) dragZoneState.visualStatus else item.item.status

                    ReorderableItem(reorderableState, key = item.item.id) {
                        TodoRow(
                            item = item.item.copy(
                                isPinned = displayIsPinned,
                                status = displayStatus
                            ),
                            isDragging = isDragged,
                            // ...
                        )
                    }
                }
            }
        }
    }

    // onDragStarted / onDragStopped 回调
    // ...
}
```

### 4.3 关键技术细节

#### 4.3.1 isDraggable 校验

`onMove` 校验 `from` 和 `to` 都必须是 `DisplayItem.Todo`，divider 不可拖拽、不可作为目标。**根治根因 2**。

#### 4.3.2 状态机订阅 onMove

每次 `onMove` 触发后，提取纯 Todo 列表，调用 `dragZoneState.onPositionChanged`。状态机的 `inferZone` 通过 Todo 邻居推断 zone，**不依赖 divider**。

#### 4.3.3 渲染层绑定视觉状态

被拖项渲染时从状态机读取 `visualIsPinned` / `visualStatus`，非拖拽项读取原 `item.isPinned` / `item.status`。

### 4.4 释放时回调

```kotlin
onDragStopped = {
    val dragResult = dragZoneState.endDrag()
    val draggedCurrentIndex = internalDisplayItems.indexOfFirst {
        it is DisplayItem.Todo && it.item.id == draggedItemId
    }
    if (dragResult.crossedZone || draggedCurrentIndex != draggedOriginalIndex) {
        onReorder(dragResult, draggedOriginalIndex, draggedCurrentIndex)
    }
    dragZoneState.reset()
}
```

### 4.5 组件层职责边界

| 组件 | 职责 |
|------|------|
| `ZonedReorderableLazyColumn` | 渲染 divider + Todo，托管库的 ReorderableLazyListState |
| `DragZoneStateMachine` | 拖拽中追踪 zone，跨区时翻转视觉状态 |
| `onMove` 回调 | 物理重排 + isDraggable 校验 + 触发状态机 |
| `onDragStopped` 回调 | 调用状态机 `endDrag()`，输出 `DragResult` 给 ViewModel |

---

## 5. ViewModel 层重构

### 5.1 核心简化：用 zone 替代索引偏移

当前 `reorderOnDisplayList` 的复杂度集中在三处索引转换：
1. `pendingOffset` 偏移公式
2. 已完成区 `dividerIndex - 1` 偏移
3. `crossedPinnedZone` 事后推断 → 翻转 isPinned

新架构下，`DragResult` 已携带 `finalIsPinned` / `finalStatus` / `crossedZone`，ViewModel 只需：
- 把 `displayItems` 的 Todo 重排应用到对应区域 List
- 应用 `DragResult.finalIsPinned` / `finalStatus` 到被拖项
- 持久化

### 5.2 reorderOnDragResult 方法

```kotlin
/**
 * 应用拖拽结果到数据层
 */
fun reorderOnDragResult(
    draggedItemId: Long,
    dragResult: DragResult,
    fromOriginalIndex: Int,
    toCurrentIndex: Int,
    displayItems: List<DisplayItem>
) {
    viewModelScope.launch {
        // ① 找到被拖项
        val draggedTodoItem = displayItems.getOrNull(toCurrentIndex) as? DisplayItem.Todo
            ?: return@launch
        val draggedTodo = draggedTodoItem.item

        // ② 计算目标 zone 内的相对位置（仅计数 Todo 项，跳过 divider）
        val targetZone = dragResult.currentZone
        val relativeIndexInZone = computeRelativeIndexInZone(
            displayItems, toCurrentIndex, targetZone
        )

        // ③ 从原区域移除
        val originalZone = dragResult.originalZone
        val originalList = getListForZone(originalZone).toMutableList()
        originalList.removeAll { it.id == draggedItemId }

        // ④ 应用 DragResult 的字段翻转
        val finalItem = draggedTodo.copy(
            isPinned = dragResult.finalIsPinned,
            status = dragResult.finalStatus
        )

        // ⑤ 插入到目标区域
        val targetList = getListForZone(targetZone).toMutableList()
        targetList.add(relativeIndexInZone.coerceIn(0, targetList.size), finalItem)

        // ⑥ 重新分配受影响区域的 sortOrder
        reassignSortOrder(targetList, targetZone)
        if (originalZone != targetZone) {
            reassignSortOrder(originalList, originalZone)
        }

        // ⑦ 持久化（批量）
        val allToUpdate = if (originalZone != targetZone) {
            targetList + originalList + listOf(finalItem)
        } else {
            targetList
        }
        repository.updateTodos(allToUpdate)

        // ⑧ 副作用（completedAt 时间戳）
        if (dragResult.crossedZone) {
            handleZoneChangeSideEffects(draggedTodo, dragResult)
        }
    }
}

private fun getListForZone(zone: TodoZone): MutableList<TodoItem> {
    return when (zone) {
        TodoZone.PINNED_PENDING -> _pinnedPendingTodos.value.toMutableList()
        TodoZone.PENDING -> _pendingTodos.value.toMutableList()
        TodoZone.PINNED_COMPLETED -> _pinnedCompletedTodos.value.toMutableList()
        TodoZone.COMPLETED -> _completedTodos.value.toMutableList()
    }
}

private fun computeRelativeIndexInZone(
    displayItems: List<DisplayItem>,
    globalIndex: Int,
    targetZone: TodoZone
): Int {
    var count = 0
    for (i in 0 until globalIndex) {
        val item = displayItems[i]
        if (item is DisplayItem.Todo && item.item.zone() == targetZone) {
            count++
        }
    }
    return count
}
```

### 5.3 StateFlow 拆分：4 个独立 List

```kotlin
val pinnedPendingTodos: StateFlow<List<TodoItem>>   // PINNED_PENDING
val pendingTodos: StateFlow<List<TodoItem>>         // PENDING
val pinnedCompletedTodos: StateFlow<List<TodoItem>> // PINNED_COMPLETED
val completedTodos: StateFlow<List<TodoItem>>       // COMPLETED
```

**优势**：
1. ViewModel 内部操作直接对应 List（无需过滤）
2. UI 渲染时按 zone 拼接，逻辑清晰
3. `getListForZone(zone)` O(1) 查找
4. `reassignSortOrder` 仅重排受影响 List

### 5.4 简化 sortOrder 分配

```kotlin
/**
 * 重新分配区域 List 内的 sortOrder
 *
 * 按 zone 分段，全局通过 zone 前缀保证唯一性：
 * - PINNED_PENDING: sortOrder 0..9999
 * - PENDING:        sortOrder 10000..19999
 * - PINNED_COMPLETED: sortOrder 20000..29999
 * - COMPLETED:      sortOrder 30000..39999
 */
private fun reassignSortOrder(list: MutableList<TodoItem>, zone: TodoZone) {
    val baseSortOrder = when (zone) {
        TodoZone.PINNED_PENDING -> 0
        TodoZone.PENDING -> 10000
        TodoZone.PINNED_COMPLETED -> 20000
        TodoZone.COMPLETED -> 30000
    }
    list.forEachIndexed { index, item ->
        if (item.sortOrder != baseSortOrder + index) {
            list[index] = item.copy(sortOrder = baseSortOrder + index)
        }
    }
}
```

**根治根因 4**：sortOrder 按 zone 分段，跨区拖拽后无需重新分配全局顺序，仅重排受影响 zone。

### 5.5 排序逻辑统一

```kotlin
// 各 zone List 直接按 sortOrder 排序
val pinnedPendingTodos = _todos
    .map { todos -> todos.filter { it.isPinned && it.status == 0 }
        .sortedBy { it.sortOrder } }
    .stateIn(...)

val pendingTodos = _todos
    .map { todos -> todos.filter { !it.isPinned && it.status == 0 }
        .sortedBy { it.sortOrder } }
    .stateIn(...)

val pinnedCompletedTodos = _todos
    .map { todos -> todos.filter { it.isPinned && it.status == 1 }
        .sortedBy { it.sortOrder } }
    .stateIn(...)

val completedTodos = _todos
    .map { todos -> todos.filter { !it.isPinned && it.status == 1 }
        .sortedBy { it.sortOrder } }
    .stateIn(...)
```

**根治根因 5**：sortOrder 在每个 zone 内连续，UI 直接按 sortOrder 顺序渲染，不再需要复杂的 `compareByDescending(isPinned)` 逻辑。

### 5.6 副作用统一管理

```kotlin
/** 处理 zone 变更副作用 */
private suspend fun handleZoneChangeSideEffects(
    draggedTodo: TodoItem,
    dragResult: DragResult
) {
    val fromCompleted = dragResult.originalZone == TodoZone.PINNED_COMPLETED ||
                        dragResult.originalZone == TodoZone.COMPLETED
    val toCompleted = dragResult.currentZone == TodoZone.PINNED_COMPLETED ||
                     dragResult.currentZone == TodoZone.COMPLETED

    when {
        !fromCompleted && toCompleted -> {
            repository.updateCompletedAt(draggedTodo.id, System.currentTimeMillis())
        }
        fromCompleted && !toCompleted -> {
            repository.updateCompletedAt(draggedTodo.id, null)
        }
        else -> { /* zone 内 isPinned 翻转：无副作用 */ }
    }
}
```

### 5.7 接口签名简化效果

| 参数 | 当前 `reorderOnDisplayList` | 新 `reorderOnDragResult` |
|------|----------------------------|---------------------------|
| 1 | `fromIndex` | `draggedItemId` |
| 2 | `toIndex` | `dragResult` |
| 3 | `dividerIndex` | `fromOriginalIndex` |
| 4 | `crossedPinnedZone` | `toCurrentIndex` |
| 5 | `pendingStartIndex` | `displayItems` |
| 6 | `midPendingDividerIndex` | - |

参数从 6 个变为 5 个，且语义更清晰（DragResult 封装跨区信息，无需外部偏移参数）。

---

## 6. 跨区拖拽流程

### 6.1 跨区拖拽时序

```
┌─ 拖拽开始 ────────────────────────────────────────────┐
│ startDrag(item)                                       │
│   originalZone = item.zone()  (如 PENDING)             │
│   currentZone = PENDING                               │
│   visualIsPinned = false                              │
│   visualStatus = 0                                    │
└──────────────────────────────────────────────────────┘
                    ↓
┌─ 拖拽中（库 onMove 触发）─────────────────────────────┐
│ onPositionChanged(displayItems, draggedIndex)         │
│   newZone = inferZone(...)  (如 COMPLETED)            │
│   if (newZone != currentZone) {                       │
│     applyZoneTransition(PENDING, COMPLETED)           │
│       visualStatus = 1   (实时翻转)                   │
│     currentZone = COMPLETED                            │
│   }                                                   │
│ → 被拖项视觉层立即变为"已完成"状态                   │
│ → 用户看到拖拽过程中卡片样式变化                       │
└──────────────────────────────────────────────────────┘
                    ↓
┌─ 释放 ───────────────────────────────────────────────┐
│ endDrag()                                             │
│   → DragResult(                                       │
│       originalZone = PENDING,                          │
│       currentZone = COMPLETED,                         │
│       finalIsPinned = false,                           │
│       finalStatus = 1,                                 │
│       crossedZone = true                               │
│     )                                                  │
│ → ViewModel.reorderOnDragResult(...)                   │
│   ① 应用 finalIsPinned / finalStatus                  │
│   ② 从 PENDING List 移除，插入到 COMPLETED List       │
│   ③ 重新分配两个 zone 的 sortOrder                    │
│   ④ 持久化                                            │
│   ⑤ 副作用（completedAt 时间戳）                     │
└──────────────────────────────────────────────────────┘
```

### 6.2 12 种跨区场景矩阵

所有跨区场景均允许（已删除子任务约束）：

| # | 跨区场景 | visualIsPinned 翻转 | visualStatus 翻转 | 副作用 |
|---|----------|---------------------|-------------------|--------|
| 1 | PINNED_PENDING → PENDING | true → false | 不变 | 无 |
| 2 | PENDING → PINNED_PENDING | false → true | 不变 | 无 |
| 3 | PINNED_PENDING → PINNED_COMPLETED | 不变 | 0 → 1 | completedAt |
| 4 | PINNED_COMPLETED → PINNED_PENDING | 不变 | 1 → 0 | 清除 completedAt |
| 5 | PINNED_PENDING → COMPLETED | true → false | 0 → 1 | completedAt |
| 6 | COMPLETED → PINNED_PENDING | false → true | 1 → 0 | 清除 completedAt |
| 7 | PENDING → PINNED_COMPLETED | false → true | 0 → 1 | completedAt |
| 8 | PINNED_COMPLETED → PENDING | true → false | 1 → 0 | 清除 completedAt |
| 9 | PENDING → COMPLETED | 不变 | 0 → 1 | completedAt |
| 10 | COMPLETED → PENDING | 不变 | 1 → 0 | 清除 completedAt |
| 11 | PINNED_COMPLETED → COMPLETED | true → false | 不变 | 无 |
| 12 | COMPLETED → PINNED_COMPLETED | false → true | 不变 | 无 |

**关键不变式**：
- 进入 `*_COMPLETED` zone 的场景（3, 5, 7, 9）设置 `completedAt`
- 离开 `*_COMPLETED` zone 的场景（4, 6, 8, 10）清除 `completedAt`
- zone 内的 isPinned 翻转（1, 2, 11, 12）无副作用

### 6.3 合并拖拽的 zone 处理

```kotlin
class MergeDragZoneStateMachine {
    private val itemStates = mutableMapOf<Long, DragZoneStateMachine>()

    fun startMergeDrag(items: List<TodoItem>) {
        items.forEach { item ->
            val sm = DragZoneStateMachine()
            sm.startDrag(item)
            itemStates[item.id] = sm
        }
    }

    /**
     * 合并拖拽中位置变化
     * - 所有选中项视为一个整体
     * - anchor 项作为代表判定 zone
     * - 所有项同步到同一 zone
     */
    fun onPositionChanged(
        displayItems: List<TodoItem>,
        anchorIndex: Int
    ): Boolean {
        val anchorSm = itemStates.values.first()
        val crossed = anchorSm.onPositionChanged(displayItems, anchorIndex)
        if (crossed) {
            // 所有项同步到 anchor 的 currentZone
            itemStates.values.forEach { sm ->
                sm.currentZone = anchorSm.currentZone
                sm.visualIsPinned = anchorSm.visualIsPinned
                sm.visualStatus = anchorSm.visualStatus
            }
        }
        return crossed
    }

    fun endMergeDrag(): List<DragResult> {
        return itemStates.values.map { it.endDrag() }
    }
}
```

**根治根因 7**：合并拖拽通过批量状态机统一处理 zone 转换，避免逐项索引偏移。

---

## 7. 数据库迁移与兼容性

### 7.1 关键决策：zone 不持久化

`TodoZone` 是派生字段（由 `isPinned` + `status` 计算），**无需数据库 migration**。数据库 schema 完全不变。

### 7.2 sortOrder 重新分配

新架构引入"按 zone 分段"的 sortOrder 策略，需要数据迁移：

| 维度 | 当前 sortOrder | 新 sortOrder 策略 |
|------|----------------|-------------------|
| 置顶待完成 | 全局连续（如 0-3） | 0-9999 |
| 待完成 | 全局连续（如 4-9） | 10000-19999 |
| 已完成 | `compareByDescending(isPinned).thenBy(sortOrder).thenByDescending(createdAt)` 推导 | 20000-29999（置顶）/ 30000-39999（非置顶） |
| 唯一性 | 全局唯一 | 全局唯一（zone 前缀保证） |

### 7.3 迁移方案

```kotlin
val MIGRATION_X_TO_X1 = object : Migration(X, X1) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. PINNED_PENDING: 0, 1, 2, ... (按 createdAt 排序)
        database.execSQL("""
            UPDATE todos
            SET sortOrder = (
                SELECT COUNT(*)
                FROM todos t2
                WHERE t2.isPinned = 1
                  AND t2.status = 0
                  AND t2.createdAt <= todos.createdAt
            ) - 1
            WHERE isPinned = 1 AND status = 0
        """.trimIndent())

        // 2. PENDING: 10000, 10001, ...
        database.execSQL("""
            UPDATE todos
            SET sortOrder = 10000 + (
                SELECT COUNT(*)
                FROM todos t2
                WHERE t2.isPinned = 0
                  AND t2.status = 0
                  AND t2.createdAt <= todos.createdAt
            ) - 1
            WHERE isPinned = 0 AND status = 0
        """.trimIndent())

        // 3. PINNED_COMPLETED: 20000, 20001, ...
        // 4. COMPLETED: 30000, 30001, ...
        // ... 类似
    }
}
```

### 7.4 Entity 字段同步检查

按项目规则 `.trae/rules/entity与 migration同步检查.md`：

| 字段 | Entity 定义 | Migration DEFAULT | 一致性 |
|------|-------------|-------------------|--------|
| `sortOrder` | `Int`（已有） | 不变（迁移时重算） | ✅ |
| `isPinned` | `Boolean`（已有） | 不变 | ✅ |
| `status` | `Int`（已有） | 不变 | ✅ |

**无新字段**，仅 sortOrder 值变更，无需 `defaultValue` 同步。

### 7.5 兼容性边界场景

#### 7.5.1 升级时已有未持久化的拖拽

拖拽状态全部在内存中（`DragZoneStateMachine`），应用重启后状态丢失。数据库中的数据仍是上次持久化的状态，一致性保证。

#### 7.5.2 多设备同步冲突

sortOrder 仅用于本地显示顺序，同步时按 `updatedAt` 取最新值。

#### 7.5.3 旧版本数据的 sortOrder 范围

Migration 一次性重算所有 sortOrder，确保落在对应 zone 段内。

### 7.6 回滚策略

如果新架构上线后发现严重问题：

1. **代码回滚**：恢复 `ReorderableLazyColumn` + `reorderOnDisplayList` 旧代码
2. **数据兼容**：旧代码对新 sortOrder 值（如 10001）也能正常工作，因为旧代码用 `compareByDescending(isPinned).thenBy(sortOrder)` 排序，sortOrder 单调即可
3. **无需 migration 回滚**：新 sortOrder 值在旧代码下仍能正常显示

**关键不变式**：sortOrder 在新旧架构下都是 `Int`，单调递增，旧代码的排序逻辑兼容新值。

---

## 8. 测试策略

### 8.1 测试金字塔

| 层级 | 测试类型 | 重点 |
|------|----------|------|
| L1 | 算法/状态机单元测试 | `DragZoneStateMachine` 纯逻辑、`TodoZone` 派生函数 |
| L2 | ViewModel 单元测试 | `reorderOnDragResult` 跨区场景、sortOrder 分配、副作用 |
| L3 | Room Migration 测试 | sortOrder 重算正确性 |
| L4 | 组件集成测试 | `ZonedReorderableLazyColumn` 渲染与拖拽协作 |
| L5 | 手动真机验证 | 用户报告的跳跃场景回归 |

### 8.2 L1：状态机单元测试

`DragZoneStateMachineTest.kt`，覆盖 12 种跨区场景 + 边界场景：

- `startDrag 初始化 originalZone 和 currentZone`
- `同区域内拖拽不翻转状态`
- `跨区拖拽翻转 isPinned - PINNED_PENDING 到 PENDING`
- `跨区拖拽翻转 status - PENDING 到 COMPLETED`
- `跨区拖拽翻转 isPinned 和 status - PINNED_PENDING 到 COMPLETED`
- `endDrag 返回 DragResult 携带最终状态`
- `inferZone 通过前面邻居推断 zone`
- `inferZone 仅自己时保持原 zone`
- `reset 清空所有状态`
- ... 12 种跨区场景全覆盖

### 8.3 L2：ViewModel 单元测试

`HomeViewModelReorderTest.kt`，覆盖 `reorderOnDragResult`：

- `同区域拖拽保持 zone 不变`
- `跨区拖拽 PENDING 到 COMPLETED 翻转 status`
- `跨区拖拽设置 completedAt 时间戳`
- `跨区拖拽离开 COMPLETED 清除 completedAt`
- `跨区拖拽 PINNED_PENDING 到 COMPLETED 翻转 isPinned 和 status`
- `sortOrder 按 zone 分段分配`
- `sortOrder 重排仅影响受影响 zone`

### 8.4 L3：Migration 测试

- `migration_X_to_X1_reassigns_sortOrder_by_zone`
- `migration_preserves_relative_order_within_zone`
- `migration_handles_empty_zones`

### 8.5 L4：组件集成测试

- `onMove 拦截 divider 跨越`
- `拖拽中视觉状态实时翻转`
- `释放时调用 onReorder 传递 DragResult`

### 8.6 L5：手动真机验证清单

| # | 场景 | 期望 |
|---|------|------|
| 1 | 已完成区内拖拽 7 到 8 和 9 之间 | 顺序变为 8, 7, 9（不再跳跃） |
| 2 | 待完成区拖 6 到"待完成"按钮和 5 之间 | 落在 PENDING 区，不显示置顶 |
| 3 | 置顶待完成项拖到普通待完成区 | isPinned 实时翻转 |
| 4 | 普通待完成项拖到置顶待完成区 | isPinned 实时翻转 |
| 5 | 待完成项拖到已完成区 | status 实时翻转 + completedAt 设置 |
| 6 | 已完成项拖回待完成区 | status 实时翻转 + completedAt 清除 |
| 7 | 已完成置顶项拖到普通已完成区 | isPinned 实时翻转，status 不变 |
| 8 | 已完成非置顶项拖到置顶已完成区 | isPinned 实时翻转，status 不变 |
| 9 | 多选拖拽跨区 | 所有选中项同步翻转 |
| 10 | 拖拽中取消（手指移出列表） | 状态机 reset，无副作用 |

### 8.7 回归测试

| 测试套件 | 现有 | 新增 | 说明 |
|----------|------|------|------|
| `ReorderAlgorithmsTest` | 20 | - | 删除（算法被状态机替代） |
| `DragZoneStateMachineTest` | - | 12+ | 新增（12 种跨区场景） |
| `HomeViewModelReorderTest` | 现有 | 7+ | 适配新接口 + 跨区场景 |
| `MigrationTest` | - | 3+ | 新增 sortOrder 重算 |

---

## 9. 实施范围

### 9.1 包含

- 新增 `TodoZone` 枚举 + 派生函数
- 新增 `DragZoneStateMachine` + `MergeDragZoneStateMachine`
- 新增 `ZonedReorderableLazyColumn` 组件
- 重构 `HomeViewModel`：4 个独立 List + `reorderOnDragResult`
- 新增 Room Migration：sortOrder 重算
- 删除 `ReorderAlgorithms`（被状态机替代）
- 删除 `ReorderableLazyColumn`（被 `ZonedReorderableLazyColumn` 替代）

### 9.2 不包含

- 不修改 Calvin-LL/Reorderable 库
- 不修改数据库 schema（无新字段）
- 不修改 `TodoItem` Entity（zone 是派生字段）
- 不引入子任务约束（已删除）

---

## 10. 根因根治度对照

| 根因 | 新架构如何根治 |
|------|----------------|
| 1. 事后推断 zone | `DragZoneStateMachine.onPositionChanged` 拖拽中持续追踪 |
| 2. 库 onMove 不感知 divider | `onMove` 校验 `isDraggable`，divider 不可拖拽、不可作为目标 |
| 3. 索引转换复杂 | `computeRelativeIndexInZone` 仅计数 Todo，`DragResult` 携带最终状态 |
| 4. sortOrder 重新分配 | 按 zone 分段（0/10000/20000/30000），仅重排受影响 zone |
| 5. visibleCompletedTodos 排序 | 4 个独立 List + `sortedBy(sortOrder)`，移除复杂排序 |
| 6. ~~子任务约束静默拒绝~~ | **已删除子任务约束** |
| 7. 合并拖拽索引偏移 | `MergeDragZoneStateMachine` 批量状态机统一处理 |
| 8. draggedCurrentIndex 依赖库内部状态 | 状态机持续追踪，释放时通过 `internalDisplayItems.indexOfFirst` 查找 |

---

## 11. 验收标准

1. ✅ 12 种跨区场景全部通过单元测试
2. ✅ ViewModel 层 `reorderOnDragResult` 测试全部通过
3. ✅ Migration 测试全部通过（sortOrder 重算正确）
4. ✅ 组件集成测试全部通过
5. ✅ 手动真机验证 10 个场景全部通过
6. ✅ 无回归：现有非拖拽功能不受影响

---

## 12. 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| Migration SQL 错误 | 中 | 升级失败 | 单元测试覆盖 Migration |
| sortOrder 重算后顺序变化 | 高 | 用户感知顺序变化 | 按 createdAt 排序作为兜底，保持相对顺序 |
| 状态机边界 bug | 中 | 拖拽跳跃 | 12 种跨区场景全覆盖测试 |
| 状态机与库协作不同步 | 中 | 视觉状态错误 | 集成测试覆盖 |
| 跨设备同步冲突 | 低 | 个别项顺序异常 | 同步层按 updatedAt 解决 |
| 升级时崩溃导致数据丢失 | 极低 | 数据丢失 | Room 事务保证，Migration 失败自动回滚 |
