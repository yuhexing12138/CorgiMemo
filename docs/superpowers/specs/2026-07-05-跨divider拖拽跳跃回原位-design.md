# 跨 divider 拖拽跳跃回原位修复 设计文档

> 创建日期：2026-07-05
> 作者：CorgiMemo Team
> 状态：已审核（待实施）

---

## 1. 背景与问题

### 1.1 用户报告的两个场景

1. **场景 1**：将置顶区的待办卡片从上往下拖拽到「待完成按钮（PendingDivider）」与「待完成区首个待办」之间释放时，该待办卡片会**跳跃回原位**。期望位置：在 PendingDivider 与待完成区首个待办之间。

2. **场景 2**：将待完成区的待办卡片从上往下拖拽到「已完成按钮（CompletedDivider）」与「已完成区首个待办」之间释放时，该待办卡片会**跳跃回原位**。期望位置：在 CompletedDivider 与已完成区首个待办之间。

### 1.2 影响范围

- **影响版本**：Zone 状态机拖拽架构重构 + onReorder 签名变更后（commit `53a0405` 起）
- **影响场景**：跨 divider 拖拽（A 从上一个 zone 拖到 divider 与下一个 zone 首个 Todo 之间）
- **影响程度**：跨 zone 拖拽功能完全不可用

---

## 2. 根因分析

### 2.1 根因 1：divider 未被 `ReorderableItem` 包裹

[ZonedReorderableLazyColumn.kt:125-131](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ZonedReorderableLazyColumn.kt#L125-131) 中 divider 直接渲染，未通过 `ReorderableItem` 包裹：

```kotlin
is DisplayItem.PinnedDivider,
is DisplayItem.PendingDivider,
is DisplayItem.CompletedDivider -> {
    content(index, item, false, isDragActive)  // ← 直接渲染
}
```

Calvin-LL 库通过 `reorderableKeys` 集合过滤 onMove 目标（[ReorderableLazyCollection.kt:599-605](file:///c:/Users/EDY/Desktop/CorgiMemo/Reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/ReorderableLazyCollection.kt#L599-605)）。divider 未被 `ReorderableItem` 包裹 → key 不会加入 `reorderableKeys` → **A 拖到 divider 位置时 onMove 不触发** → A 留在原位 → 释放后弹回原位 = "跳跃回原位"。

### 2.2 根因 2：`inferZone` 不感知 divider

即使修复根因 1，[DragZoneStateMachine.kt:109-122](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/DragZoneStateMachine.kt#L109-122) 的 `inferZone` 接收的是 `List<TodoItem>`（divider 已被过滤）：

```kotlin
private fun inferZone(displayItems: List<TodoItem>, draggedIndex: Int): TodoZone {
    val prevIdx = draggedIndex - 1
    if (prevIdx >= 0) {
        return displayItems[prevIdx].zone()  // ← 只看邻居 Todo，不感知 divider
    }
    ...
}
```

**场景 2 验证**（A 已跨过 CompletedDivider）：
- todosOnly = [B, A, X, Y]，A 在 idx=1
- inferZone 看 todosOnly[0] = B，B.zone() = PENDING → 错误返回 PENDING
- 实际应为 COMPLETED（A 已跨过 CompletedDivider）

### 2.3 触发链路

```
用户拖 A 到 divider 与下一个 zone 首个 Todo 之间
  ↓
A 视觉位置在 divider 之后，但 A 中心点未超过下一个 Todo 中心点
  ↓
Calvin-LL 库的 shouldItemMove 不命中（divider 不在 reorderableKeys，Todo 中心点未被覆盖）
  ↓
onMove 不触发 → displayItems 不变 → A 还在原位
  ↓
释放 → onDragStopped → draggedOriginalIndex == draggedCurrentIndex → 不调用 onReorder
  ↓
Calvin-LL 拖拽动画释放 → A 弹回原位 = "跳跃回原位"
```

### 2.4 关键代码位置

- [ZonedReorderableLazyColumn.kt:125-131](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ZonedReorderableLazyColumn.kt#L125-131)：divider 渲染（未包裹 ReorderableItem）
- [ZonedReorderableLazyColumn.kt:88-116](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ZonedReorderableLazyColumn.kt#L88-116)：onMove 实现（含 to 校验与状态机调用）
- [DragZoneStateMachine.kt:109-122](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/DragZoneStateMachine.kt#L109-122)：inferZone（基于 todosOnly，不感知 divider）
- [ReorderableLazyCollection.kt:599-605](file:///c:/Users/EDY/Desktop/CorgiMemo/Reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/ReorderableLazyCollection.kt#L599-605)：Calvin-LL 库的 findTargetItem 过滤逻辑

---

## 3. 设计方案

### 3.1 方案选型

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **方案 M（采用）** | ① divider 包裹 `ReorderableItem(enabled=true)` + `Modifier.draggable(enabled=false)`<br>② onMove 允许 to 是 divider<br>③ 新增 `inferZoneFromDisplayItems` 基于含 divider 的 displayItems 推断 zone<br>④ DragZoneStateMachine 新增 `setZone` 方法 | 根治两个根因；改动聚焦；保留现有 onPositionChanged 接口与 19 个测试 | 需新增 1 个 public 方法 + 1 个函数 |
| 方案 N | 修改 `DragZoneStateMachine.onPositionChanged` 签名接收含 divider 的 displayItems | 接口更干净 | 需修改 19 个测试 + MergeDragZoneStateMachine |
| 方案 O | 在 onDragStopped 时基于拖拽偏移量判定 zone | 不改 onMove | 需访问 Calvin-LL 库内部状态，复杂且脆弱 |

### 3.2 选型理由

1. **根因 1 修复**：让 divider 可作为 onMove 的 to 目标，A 能跨过 divider
2. **根因 2 修复**：`inferZoneFromDisplayItems` 基于 divider 类型推断 zone，准确识别 CompletedDivider → COMPLETED
3. **最小改动**：保留 `onPositionChanged` 接口与 19 个测试，新增 `setZone` + `inferZoneFromDisplayItems`
4. **Calvin-LL 库兼容**：A 的索引从 from 变到 to（与库的预期一致），视觉正常

### 3.3 架构变更

#### 修改前

```
┌────────────────────────────────────┐
│ ZonedReorderableLazyColumn          │
│                                    │
│ divider 渲染：直接 content()         │  ← divider 不在 reorderableKeys
│ onMove:                             │  ← to=divider 被拒绝
│   if (toItem !is Todo) return       │  ← A 无法跨过 divider
│                                    │
│ dragZoneState.onPositionChanged      │  ← inferZone 不感知 divider
│   (todosOnly, draggedTodoIndex)      │  ← 基于邻居 Todo 推断 zone（错误）
└────────────────────────────────────┘
```

#### 修改后

```
┌────────────────────────────────────┐
│ ZonedReorderableLazyColumn          │
│                                    │
│ divider 渲染：ReorderableItem       │  ← divider 加入 reorderableKeys
│   (enabled=true) + draggable(false) │  ← divider 可作为 to，但不可拖拽
│ onMove:                             │
│   if (fromItem !is Todo) return     │  ← 只校验 from
│   removeAt(from) + add(to, from)    │  ← A 跨过 divider（divider 不动）
│                                    │
│ inferZoneFromDisplayItems           │  ← 新函数：基于含 divider 的 displayItems
│   (newDisplay, insertIdx)           │  ← 扫描前面最近 divider 推断 zone
│ dragZoneState.setZone(newZone)      │  ← 新方法：直接设置 currentZone
└────────────────────────────────────┘
```

### 3.4 关键变化

1. **divider 包裹 ReorderableItem**：divider 加入 `reorderableKeys`，可作为 onMove 的 to 目标
2. **onMove 校验简化**：只校验 from 是 Todo，to 可以是 Todo 或 divider
3. **zone 推断逻辑上移**：从 DragZoneStateMachine.inferZone（基于 todosOnly）上移到 ZonedReorderableLazyColumn.inferZoneFromDisplayItems（基于含 divider 的 displayItems）
4. **DragZoneStateMachine 新增 setZone**：允许直接设置 currentZone，保留原有 onPositionChanged 接口

### 3.5 不变的部分

- `ZoneDragResult` 接口不变
- `HomeViewModel.reorderOnDragResult` 签名不变
- `onReorder` 签名不变（仍是 `(dragResult, draggedItem, targetZoneRelativeIndex)`）
- `computeRelativeIndexInZone` 不变
- `onPositionChanged` 接口保留（MergeDragZoneStateMachine 仍使用）

---

## 4. 组件与接口

### 4.1 divider 包裹 ReorderableItem

**修改前**：

```kotlin
is DisplayItem.PinnedDivider,
is DisplayItem.PendingDivider,
is DisplayItem.CompletedDivider -> {
    content(index, item, false, isDragActive)
}
```

**修改后**：

```kotlin
is DisplayItem.PinnedDivider,
is DisplayItem.PendingDivider,
is DisplayItem.CompletedDivider -> {
    ReorderableItem(
        state = reorderableState,
        key = when (item) {
            is DisplayItem.PinnedDivider -> "pinned_divider"
            is DisplayItem.PendingDivider -> "pending_divider"
            is DisplayItem.CompletedDivider -> "completed_divider"
        },
        enabled = true   // ← 加入 reorderableKeys，可作为 to
    ) {
        Box(
            modifier = Modifier.draggable(enabled = false)  // ← 不可拖拽
        ) {
            content(index, item, false, isDragActive)
        }
    }
}
```

### 4.2 onMove 校验简化

**修改前**：

```kotlin
onMove = { from, to ->
    val fromItem = displayItems.getOrNull(from.index)
    val toItem = displayItems.getOrNull(to.index)

    // ① 校验：from 和 to 都必须是 Todo 项
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
    if (crossed) { ... }
}
```

**修改后**：

```kotlin
onMove = { from, to ->
    val fromItem = displayItems.getOrNull(from.index)
    // ① 仅校验 from 是 Todo（divider 不可拖拽，由 Modifier.draggable(enabled=false) 保证）
    //    to 可以是 Todo 或 divider（divider 已加入 reorderableKeys，可作为目标）
    if (fromItem !is DisplayItem.Todo) {
        return@rememberReorderableLazyListState
    }

    // ② 重排 displayItems（A 跨过 divider / Todo，divider 不动）
    val newDisplay = displayItems.toMutableList()
    newDisplay.removeAt(from.index)
    newDisplay.add(to.index, fromItem)
    displayItems = newDisplay

    // ③ 基于 newDisplay 推断 currentZone（含 divider，准确识别 zone 边界）
    val newDraggedIdx = newDisplay.indexOfFirst {
        (it as? DisplayItem.Todo)?.item?.id == fromItem.item.id
    }
    val newZone = inferZoneFromDisplayItems(newDisplay, newDraggedIdx)
    val crossed = newZone != dragZoneState.currentZone
    if (crossed) {
        dragZoneState.setZone(newZone)
    }

    // ④ 跨区触觉反馈
    if (crossed) { ... }
}
```

### 4.3 inferZoneFromDisplayItems 设计

新增 top-level internal 函数：

```kotlin
/**
 * 基于 displayItems（含 divider）推断被拖项的当前 zone
 *
 * 算法：从被拖项位置向前扫描，遇到最近的 divider 即确定 zone：
 * - PinnedDivider → PINNED_PENDING
 * - PendingDivider → PENDING
 * - CompletedDivider → COMPLETED
 *
 * 若扫描到列表开头仍无 divider，根据被拖项自身 zone 推断（防御性回退）。
 *
 * 注：与 DragZoneStateMachine.inferZone 的区别：
 * - DragZoneStateMachine.inferZone 基于 todosOnly（divider 已过滤），看邻居 Todo 的 zone
 * - 本函数基于含 divider 的 displayItems，直接识别 divider 类型，准确度更高
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
```

### 4.4 DragZoneStateMachine 新增 setZone

```kotlin
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
```

### 4.5 关键点

| 配置项 | 值 | 作用 |
|--------|------|------|
| `ReorderableItem.enabled` | `true` | divider 加入 `reorderableKeys`，可作为 onMove 的 to 目标 |
| `Modifier.draggable(enabled)` | `false` | divider 不可作为 onMove 的 from（不可被拖拽） |
| `key` | `"pinned_divider"` / `"pending_divider"` / `"completed_divider"` | 稳定的字符串 key，与 Todo 的 `item.id`（Long）不冲突 |

---

## 5. 场景验证

### 5.1 场景 1 验证：置顶区 → 待完成区（跨 PendingDivider）

**初始状态**：
```
displayItems = [
    PinnedDivider, A(isPinned=true, status=0),   // PINNED_PENDING
    PendingDivider, B(isPinned=false, status=0),  // PENDING
    CompletedDivider, X(isPinned=false, status=1) // COMPLETED
]
```

**用户操作**：长按 A，向下拖到 PendingDivider 与 B 之间释放。

**onMove 触发链路**：

| 步骤 | 状态 |
|------|------|
| 1. onDragStarted | dragZoneState.startDrag(A)；originalZone=PINNED_PENDING, currentZone=PINNED_PENDING |
| 2. A 拖到 PendingDivider 位置（A rect 覆盖 PendingDivider center） | Calvin-LL 触发 onMove(from=A_idx=2, to=PendingDivider_idx=3) |
| 3. onMove 修改后：from=A（Todo）通过；removeAt(2) + add(3, A) | newDisplay = [PinnedDivider, PendingDivider, A, B, CompletedDivider, X] |
| 4. inferZoneFromDisplayItems(newDisplay, draggedIdx=2) | 扫描 newDisplay[1]=PendingDivider → 返回 PENDING |
| 5. dragZoneState.setZone(PENDING) | currentZone: PINNED_PENDING → PENDING；applyZoneTransition 翻转 visualIsPinned=true→false |
| 6. 触发触觉反馈 | CONFIRM |
| 7. 用户释放 | onDragStopped：draggedCurrentIndex=2，draggedOriginalIndex=1，两者不等 → 进入持久化 |
| 8. computeRelativeIndexInZone(newDisplay, 2, PENDING) | 遍历 newDisplay[0..1]：PinnedDivider（跳过）、PendingDivider（跳过）→ relativeIndex=0 |
| 9. onReorder(dragResult, A, 0) | ViewModel 把 A 插入 PENDING 区 idx=0 |

**ViewModel 持久化结果**：
- PINNED_PENDING 区：A 被移除（list 变空）
- PENDING 区：[A(sortOrder=10000), B(sortOrder=10001)]
- 视觉位置：A 在 PendingDivider 后第一项 = B 之前 ✅ 正确

### 5.2 场景 2 验证：待完成区 → 已完成区（跨 CompletedDivider）

**初始状态**：
```
displayItems = [
    PendingDivider, B(isPinned=false, status=0),  // PENDING
    CompletedDivider, X(isPinned=false, status=1) // COMPLETED
]
```

**用户操作**：长按 B，向下拖到 CompletedDivider 与 X 之间释放。

**onMove 触发链路**：

| 步骤 | 状态 |
|------|------|
| 1. onDragStarted | originalZone=PENDING, currentZone=PENDING |
| 2. B 拖到 CompletedDivider 位置 | onMove(from=B_idx=1, to=CompletedDivider_idx=2) |
| 3. removeAt(1) + add(2, B) | newDisplay = [PendingDivider, CompletedDivider, B, X] |
| 4. inferZoneFromDisplayItems(newDisplay, draggedIdx=2) | 扫描 newDisplay[1]=CompletedDivider → 返回 COMPLETED |
| 5. dragZoneState.setZone(COMPLETED) | currentZone: PENDING → COMPLETED；visualStatus=0→1 |
| 6. 释放 | onDragStopped 进入持久化 |
| 7. computeRelativeIndexInZone(newDisplay, 2, COMPLETED) | 遍历 newDisplay[0..1]：PendingDivider（跳过）、CompletedDivider（跳过）→ relativeIndex=0 |
| 8. onReorder(dragResult, B, 0) | ViewModel 把 B 插入 COMPLETED 区 idx=0 |

**持久化结果**：COMPLETED 区 = [B(sortOrder=30000), X(sortOrder=30001)]，B 在 X 之前 ✅ 正确

### 5.3 边界场景：同 zone 内拖拽（不跨 divider）

**初始状态**：`[PinnedDivider, A, B, C, PendingDivider, D, E]`

**用户操作**：A 拖到 B 和 C 之间。

**修改后行为**：
1. onMove(from=1, to=2) → newDisplay = [PinnedDivider, B, A, C, PendingDivider, D, E]
2. inferZoneFromDisplayItems(newDisplay, 2) → 扫描 newDisplay[1]=B（Todo，继续）→ newDisplay[0]=PinnedDivider → 返回 PINNED_PENDING
3. setZone(PINNED_PENDING) → 与原 currentZone 相同，返回 false，不触发触觉反馈
4. relativeIndex = 1（前面有 B 一个同 zone 项）

**结果**：A 在 B 和 C 之间 ✅ 无回归

### 5.4 边界场景：快速拖拽跨多 zone

**初始状态**：`[PinnedDivider, A, PendingDivider, B, C, CompletedDivider, X]`

**用户操作**：快速把 A 从顶部拖到底部（跨过 PendingDivider + B + C + CompletedDivider 到 X 之后）。

**Calvin-LL 库的 onMove 多次触发**（每次只移动一项）：

| onMove 次数 | from | to | newDisplay | newDraggedIdx | inferZone | setZone |
|-----------|------|------|-----------|---------------|-----------|---------|
| 1 | A(1) | PendingDivider(2) | [PinnedDivider, PendingDivider, A, B, C, CompletedDivider, X] | 2 | PendingDivider → PENDING | PINNED_PENDING → PENDING |
| 2 | A(2) | B(3) | [PinnedDivider, PendingDivider, B, A, C, CompletedDivider, X] | 3 | PendingDivider → PENDING | PENDING（不变） |
| 3 | A(3) | C(4) | [PinnedDivider, PendingDivider, B, C, A, CompletedDivider, X] | 4 | PendingDivider → PENDING | PENDING（不变） |
| 4 | A(4) | CompletedDivider(5) | [PinnedDivider, PendingDivider, B, C, CompletedDivider, A, X] | 5 | CompletedDivider → COMPLETED | PENDING → COMPLETED |
| 5 | A(5) | X(6) | [PinnedDivider, PendingDivider, B, C, CompletedDivider, X, A] | 6 | CompletedDivider → COMPLETED | COMPLETED（不变） |

**最终状态**：currentZone=COMPLETED, visualStatus=1, relativeIndex=1（前面有 X 一个 COMPLETED 项）

**结论**：快速拖拽跨多 zone 时，每次 onMove 都基于最新 newDisplay 推断 zone，状态机正确翻转。✅ 正确

---

## 6. 测试策略

### 6.1 测试层级划分

| 层级 | 测试范围 | 是否需要新增/修改 |
|------|----------|-------------------|
| 单元测试（已有） | `DragZoneStateMachine` 现有 19 个测试 | ❌ 不变（onPositionChanged 接口保留） |
| 单元测试（已有） | `computeRelativeIndexInZone` 4 个测试 | ❌ 不变 |
| 单元测试（已有） | `HomeViewModel.reorderOnDragResult` 3 个测试 | ❌ 不变 |
| 单元测试（新增） | `inferZoneFromDisplayItems` 纯函数 | ✅ 新增 5 个测试 |
| 单元测试（新增） | `DragZoneStateMachine.setZone` 新方法 | ✅ 新增 3 个测试 |
| 手动验证 | 端到端拖拽场景 | ✅ 新增 4 个场景 |

### 6.2 新增单元测试：inferZoneFromDisplayItems

| # | 测试名 | 场景 | 期望 |
|---|--------|------|------|
| 1 | `inferZone 扫描到 PinnedDivider 返回 PINNED_PENDING` | [PinnedDivider, A, B]，draggedIdx=2 | PINNED_PENDING |
| 2 | `inferZone 扫描到 PendingDivider 返回 PENDING` | [PinnedDivider, A, PendingDivider, B]，draggedIdx=3 | PENDING |
| 3 | `inferZone 扫描到 CompletedDivider 返回 COMPLETED` | [PendingDivider, A, CompletedDivider, X]，draggedIdx=3 | COMPLETED |
| 4 | `inferZone 跨过多项找到最近的 divider` | [PinnedDivider, A, B, C]，draggedIdx=3 | PINNED_PENDING（前面最近 divider 是 PinnedDivider） |
| 5 | `inferZone 无 divider 回退到被拖项自身 zone` | [A, B, C]（无 divider），draggedIdx=1 | A.zone() = PENDING |

### 6.3 新增单元测试：DragZoneStateMachine.setZone

| # | 测试名 | 场景 | 期望 |
|---|--------|------|------|
| 1 | `setZone 同 zone 不触发翻转` | startDrag(A/PENDING)，setZone(PENDING) | 返回 false；visualIsPinned/visualStatus 不变 |
| 2 | `setZone 跨 zone 触发视觉翻转` | startDrag(A/PENDING)，setZone(COMPLETED) | 返回 true；visualStatus=1 |
| 3 | `setZone 跨 zone 后再 setZone 同 zone 不触发` | startDrag(A/PENDING)，setZone(COMPLETED)，setZone(COMPLETED) | 第一次返回 true，第二次返回 false |

### 6.4 手动验证场景

| # | 场景 | 预期结果 |
|---|------|----------|
| 1 | 置顶区 A 拖到 PendingDivider 与 B 之间 | A 落在 PendingDivider 后第一项，**不跳跃回原位** |
| 2 | 待完成区 B 拖到 CompletedDivider 与 X 之间 | B 落在 CompletedDivider 后第一项，**不跳跃回原位** |
| 3 | 已完成区 X 拖到 PendingDivider 与 B 之间 | X 跨回 PENDING 区，位置正确 |
| 4 | 快速拖拽跨多 zone（PINNED_PENDING → COMPLETED） | 状态机正确翻转，最终位置正确 |

### 6.5 回归测试

- 运行所有现有单元测试（49/49 应全部通过）
- 验证 Debug APK 编译成功

### 6.6 关键不变式

1. **`onPositionChanged` 接口不变**：现有 19 个 DragZoneStateMachineTest 全部通过
2. **`computeRelativeIndexInZone` 不变**：现有 4 个测试全部通过
3. **`HomeViewModel.reorderOnDragResult` 不变**：现有 3 个测试全部通过
4. **divider 包裹 ReorderableItem 不影响 Todo 渲染**：编译成功 + 手动验证视觉无异常

---

## 7. 风险与缓解

### 7.1 风险 1：divider 包裹 ReorderableItem 后视觉异常

**场景**：库可能对 divider 应用 `animateItem()` 或拖拽装饰。

**排查**：`animateItem()` 仅在 onMove 触发时生效。divider 作为 to 目标被 onMove 调用后，divider 自身的 index 会被库顺延，库会对其应用 animateItem。

**影响**：divider 视觉位置可能轻微移动（与其他项一起被顺延），这是正常行为。

**缓解**：手动验证场景 1-4 中观察 divider 视觉表现，确认无异常。

### 7.2 风险 2：divider key 与 Todo key 冲突

**场景**：divider 使用字符串 key，Todo 使用 `item.id`（Long）。LazyColumn 要求 key 唯一。

**缓解**：divider key 是固定的 3 个字符串，每个 zone 只有一个对应 divider，key 唯一性保证。

### 7.3 风险 3：A 跨过 divider 后，divider 顺延导致后续索引错乱

**场景**：
- 初始：`[PinnedDivider(0), A(1), PendingDivider(2), B(3)]`
- onMove(from=1, to=2) → `[PinnedDivider(0), PendingDivider(1), A(2), B(3)]`
- **PendingDivider 从 idx=2 移到 idx=1**

**库的预期**：库内部按 key 跟踪 A，A 的 index 从 1 变到 2，库走"清空预测偏移，用真实 offset"分支，视觉正常。

**结论**：无风险。divider 顺延是正常数据更新，库基于 key 与 rect 跟踪，正确处理。

### 7.4 风险 4：onMove 校验简化后，divider 误作为 from

**场景**：用户长按 divider 拖拽。

**排查**：divider 包裹了 `Modifier.draggable(enabled = false)`，**longPressDraggableHandle 不会触发 onDragStarted** → Calvin-LL 库不会将 divider 设为 draggingItem → onMove 的 from 永远不会是 divider。

**结论**：无风险。divider 不可拖拽由 `Modifier.draggable(enabled=false)` 保证。

### 7.5 风险 5：setZone 与 onPositionChanged 行为不一致

**场景**：`MergeDragZoneStateMachine` 仍使用 `onPositionChanged`（基于 todosOnly 推断 zone），而 `ZonedReorderableLazyColumn` 使用 `setZone`（基于含 divider 的 displayItems 推断）。两个路径的 zone 推断结果可能不一致。

**影响**：合并拖拽场景下，inferZone 仍可能错误（根因 2 未修复）。但本次任务范围只覆盖单选拖拽，合并拖拽是后续优化项。

**缓解**：本次不修复合并拖拽的 zone 推断问题；后续可统一改为 `inferZoneFromDisplayItems`。

### 7.6 风险 6：inferZoneFromDisplayItems 无 divider 回退逻辑

**场景**：测试环境或异常情况下 displayItems 无 divider。

**缓解**：第 2 步回退到"看被拖项自身 zone"，防御性返回 PENDING（默认值）。不会崩溃。

### 7.7 风险评估总结

| 风险 | 概率 | 影响 | 缓解策略 |
|------|------|------|----------|
| 1. divider 视觉异常 | 低 | 视觉小问题 | 手动验证观察 |
| 2. key 冲突 | 极低 | N/A | Long vs String 类型不同 |
| 3. divider 顺延导致索引错乱 | 已排查 | N/A | 库基于 key 与 rect 跟踪 |
| 4. divider 误作为 from | 极低 | N/A | draggable(enabled=false) 保证 |
| 5. setZone 与 onPositionChanged 不一致 | 已存在 | 合并拖拽 zone 推断错误 | 本次不修复，后续统一 |
| 6. 无 divider 回退 | 极低 | 返回默认 zone | 防御性回退到自身 zone |

---

## 8. 实施范围与验收标准

### 8.1 实施范围

#### 包含

| 文件 | 改动类型 | 内容 |
|------|----------|------|
| [ZonedReorderableLazyColumn.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ZonedReorderableLazyColumn.kt) | 修改 | ① divider 包裹 `ReorderableItem(enabled=true)` + `Modifier.draggable(enabled=false)`；② onMove 校验简化（仅校验 from 是 Todo）；③ onMove 中调用 `inferZoneFromDisplayItems` + `dragZoneState.setZone`；④ 新增 `inferZoneFromDisplayItems` top-level internal 函数 |
| [DragZoneStateMachine.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/DragZoneStateMachine.kt) | 修改 | 新增 `setZone(newZone: TodoZone): Boolean` public 方法 |
| `ZonedReorderableLazyColumnTest.kt` | 修改 | 新增 5 个 `inferZoneFromDisplayItems` 测试 |
| `DragZoneStateMachineTest.kt` | 修改 | 新增 3 个 `setZone` 测试 |

#### 不包含

- 不修改 `HomeViewModel.reorderOnDragResult` 签名与实现
- 不修改 `ZoneDragResult` / `onReorder` 签名
- 不修改 `computeRelativeIndexInZone`
- 不修改 `onPositionChanged` 接口（保留 19 个测试 + MergeDragZoneStateMachine）
- 不修改 Calvin-LL/Reorderable 库
- 不修复合并拖拽 zone 推断（后续优化项）

### 8.2 验收标准

#### 单元测试

| # | 验收项 | 通过标准 |
|---|--------|----------|
| 1 | 新增 5 个 `inferZoneFromDisplayItems` 测试 | 全部通过 |
| 2 | 新增 3 个 `setZone` 测试 | 全部通过 |
| 3 | 现有 49 个单元测试 | 全部通过（无回归） |
| 4 | Debug APK 编译 | BUILD SUCCESSFUL |

合计：57/57 单元测试通过

#### 手动验证场景

| # | 场景 | 通过标准 |
|---|------|----------|
| 1 | 置顶区 A 拖到 PendingDivider 与 B 之间 | A 落在 PendingDivider 后第一项，**不跳跃回原位** |
| 2 | 待完成区 B 拖到 CompletedDivider 与 X 之间 | B 落在 CompletedDivider 后第一项，**不跳跃回原位** |
| 3 | 已完成区 X 拖到 PendingDivider 与 B 之间 | X 跨回 PENDING 区，位置正确 |
| 4 | 快速拖拽跨多 zone（PINNED_PENDING → COMPLETED） | 状态机正确翻转，最终位置正确 |

#### 核心验收

**最重要的验收标准**：用户将待办卡片拖拽到 divider 与下一个 zone 首个 Todo 之间释放时，**视觉位置与持久化位置一致**，不出现"跳跃回原位"现象。

### 8.3 实施顺序

1. **Task 1**：新增 `inferZoneFromDisplayItems` 纯函数 + 5 个单元测试（TDD）
2. **Task 2**：新增 `DragZoneStateMachine.setZone` 方法 + 3 个单元测试（TDD）
3. **Task 3**：修改 `ZonedReorderableLazyColumn.kt`：divider 包裹 ReorderableItem + onMove 校验简化 + 调用 inferZoneFromDisplayItems + setZone
4. **Task 4**：回归测试 + 手动验证

---

## 9. 后续可优化项

1. **统一合并拖拽的 zone 推断**：将 `MergeDragZoneStateMachine` 也改为基于含 divider 的 displayItems 推断 zone（当前使用 todosOnly + inferZone，根因 2 未修复）
2. **删除冗余的 `DragZoneStateMachine.inferZone`**：若 `onPositionChanged` 不再被任何路径使用，可考虑删除（当前仍被 MergeDragZoneStateMachine 使用，需先统一）
3. **`zone()` 扩展函数定位**：HomeScreen 已不再直接调用 `item.item.zone()`，建议确认其他调用点是否仍需保留
