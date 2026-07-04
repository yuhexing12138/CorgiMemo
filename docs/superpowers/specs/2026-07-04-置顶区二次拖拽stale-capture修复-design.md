# 置顶区二次拖拽 stale-capture 修复设计

## 1. 背景

### 1.1 用户报告的场景

> 已知有十条待办，名称为 1-10，从上到下按序排列。1，2，3，4 是置顶状态，其他都是待完成状态。当用户首次将 5 拖拽到置顶区释放是正常的，再次在置顶区拖动 5 释放后 5 会跳跃回到待完成区。

### 1.2 复现条件

- 初始 `pinnedCount = 4`（Case A：displayItems 含 PinnedDivider + PendingDivider）
- N5 初始 `isPinned = false`，位于待完成区
- 用户**首次**拖 N5 到置顶区 → N5 变 pinned ✓（正确）
- UI 完全刷新（N5 视觉上出现在置顶区）后，用户**再次**拖动 N5（在置顶区内或跨置顶区位置释放）→ N5 跳回待完成区 ✗（错误）

### 1.3 排除项

经用户确认：UI 已完全刷新后再进行第二次拖拽，排除 `LaunchedEffect(items)` 同步时序问题。

## 2. 根因分析

### 2.1 库层证据

[Reorderable/reorderable/.../draggable.kt#L49](file:///c:/Users/EDY/Desktop/CorgiMemo/Reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/draggable.kt#L49)：

```kotlin
pointerInput(key1, enabled) {
    ...
    detect(
        onDragStart = { ... onDragStarted(it) },
        onDragEnd = { ... onDragStopped() },
        ...
    )
}
```

- `key1 = reorderableLazyCollectionState`（state 实例，不变化）
- `pointerInput` 仅在 key 变化时重启 lambda
- displayItems 变化**不会**触发 pointerInput 重启 → lambda 内捕获的 `item` 是**首次注册时的旧引用**

### 2.2 调用方代码

[app/.../ReorderableLazyColumn.kt#L682-L700](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L682-L700)：

```kotlin
onDragStarted = {
    isDragActive = true
    draggedOriginalIndex = displayItems.indexOfFirst {  // ← 已用 displayItems 修复
        key(it) == key(item)
    }
    draggedOriginalIsPinned = isPinned(item)              // ← BUG: 仍用旧 item
    crossedPinnedZone = false
    ...
}
```

### 2.3 Bug 触发链

| 步骤 | 事件 | 状态 |
|---|---|---|
| 1 | 初始：N5 `isPinned=false` | displayItems 中 N5 是 pending |
| 2 | `itemsIndexed` 首次为 N5 注册 `pointerInput` | lambda 捕获 `item = N5(isPinned=false)` |
| 3 | 首次拖 N5 到置顶区，`onDragStarted` 触发 | `draggedOriginalIsPinned = false` ✓ |
| 4 | `onDragStopped`：邻居 N4 `isPinned=true` | `crossedPinnedZone = (false != true) = true` ✓ |
| 5 | ViewModel 翻转 N5 → `isPinned=true`，items 流更新 | LaunchedEffect 同步 `displayItems = items` ✓ |
| 6 | UI 刷新：N5 现在在置顶区，`isPinned=true` | 但 `pointerInput` 未重启（key=state 不变） |
| 7 | 用户第二次拖 N5，`onDragStarted` 触发 | lambda 仍持有**旧的** `item = N5(isPinned=false)` ❌ |
| 8 | `draggedOriginalIsPinned = isPinned(item) = false` | **错误！实际应为 true** |
| 9 | `onDragStopped`：邻居是置顶项 `isPinned=true` | `crossedPinnedZone = (false != true) = true` ❌ |
| 10 | ViewModel 翻转 N5 → `isPinned=false` | N5 跳回待完成区 ✗ |

### 2.4 代码注释已暗示同类问题

`onDragStarted` 上方注释：

> 注意：draggedOriginalIndex 必须用 displayItems 计算，与 onDragStopped 中 draggedCurrentIndex 的参照系保持一致。原因：onDragStarted lambda 可能捕获旧的 items（因 longPressDraggableHandle 的 pointerInput 未因 items 变化而重启），导致 draggedOriginalIndex 用旧 items 计算，与 draggedCurrentIndex（用最新 displayItems 计算）参照系不一致，进而引发位置跳跃。

**前一次修复只处理了 `draggedOriginalIndex`，遗漏了 `draggedOriginalIsPinned`。本次修复补齐该遗漏。**

## 3. 修复方案

### 3.1 选定方案：从 displayItems 查询 isPinned（方案 A）

#### 修改位置
[app/.../ReorderableLazyColumn.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt) `onDragStarted` lambda

#### 变更前
```kotlin
onDragStarted = {
    isDragActive = true
    draggedOriginalIndex = displayItems.indexOfFirst {
        key(it) == key(item)
    }
    draggedOriginalIsPinned = isPinned(item)
    crossedPinnedZone = false
    HapticFeedbackManager.performHapticFeedback(
        context = context,
        type = InteractionType.TEXT_MOVE,
        enabled = true
    )
},
```

#### 变更后
```kotlin
onDragStarted = {
    isDragActive = true
    // 注意：draggedOriginalIndex 与 draggedOriginalIsPinned 必须用 displayItems 计算，
    // 与 onDragStopped 中 draggedCurrentIndex 的参照系保持一致。
    // 原因：onDragStarted lambda 可能捕获旧的 item（因 longPressDraggableHandle
    // 的 pointerInput 未因 items 变化而重启），导致 isPinned(item) 返回旧值，
    // 进而引发"已置顶项二次拖拽时被误判为跨区"的位置跳跃。
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
```

### 3.2 边界情况覆盖

| 场景 | draggedIdx | 行为 |
|---|---|---|
| 正常拖拽 | >= 0 | 从 displayItems 读 isPinned ✓ |
| item 已从 displayItems 移除（异常） | -1 | fallback 为 false，且 `onDragStopped` 中 `draggedOriginalIndex >= 0` 校验会跳过 `onReorder` |

### 3.3 排除方案

| 方案 | 否决理由 |
|---|---|
| 方案 B：`rememberUpdatedState(item)` | 改动稍大，需在 `itemsIndexed` lambda 内每个 item 增加记忆化；与 `draggedOriginalIndex` 现有修复模式不一致 |
| 方案 C：在 `onDragStopped` 重新读 isPinned | 语义错误：`crossedPinnedZone` 需要的是**原始** isPinned，读取当前值会破坏跨区翻转逻辑 |

## 4. 测试策略

### 4.1 ViewModel 层测试

新增到 [HomeViewModelReorderTest.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt)：

| 测试名 | 场景 | 验证点 |
|---|---|---|
| `二次拖拽已置顶项不应翻转 isPinned` | 模拟 N5 已 pinned 后再次拖动到 pinned 区 | 第二次 `onReorder` 的 `crossedPinnedZone=false`，N5 保持 `isPinned=true` |

### 4.2 算法层回归测试

新增到 [ReorderAlgorithmsTest.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt)：

| 测试名 | 输入 | 期望 |
|---|---|---|
| `draggedOriginalIsPinned 与邻居同为 pinned 不应跨区` | draggedOriginalIsPinned=true，邻居 isPinned=true | crossed=false |

**说明**：`checkPinnedZoneCrossed` 算法本身正确，bug 在调用方传错 `draggedOriginalIsPinned`。算法层测试是回归保护，确保未来重构时不破坏该不变式。

## 5. 实施约束

1. **不动 ViewModel**：`reorderOnDisplayList` 的 else 分支翻转逻辑保留
2. **不动 LaunchedEffect(items) 同步逻辑**：displayItems 同步时机正确
3. **不动 merge drag 路径**：`startMergeDrag` 使用 `displayItems.filter { ... }`，已是当前快照
4. **遵循 workspace rule**：编辑后检查 import 语句完整性

## 6. 验收标准

- [ ] `onDragStarted` 中 `draggedOriginalIsPinned` 从 `displayItems` 查询
- [ ] 新增 ViewModel 层测试覆盖二次拖拽场景
- [ ] 新增算法层回归测试
- [ ] `./gradlew assembleDebug` 编译通过
- [ ] `./gradlew test` 全部测试通过
- [ ] 真机验证：N5 首次拖入置顶区后再次拖动不跳跃

## 7. 后续可优化方向

1. **审视其他 longPressDraggableHandle lambda 闭包变量**：检查是否还有其他从 `item` 直接读取的字段（如 `key(item)`），统一改为从 `displayItems` 查询
2. **抽取公共 helper**：考虑在 `ReorderableLazyColumn` 内提供 `currentItem(item)` 扩展函数，集中处理 stale capture 问题
3. **库层 PR**：向 `sh.calvin.reorderable` 库提 PR，使用 `rememberUpdatedState` 在库内部解决 lambda 复用问题
