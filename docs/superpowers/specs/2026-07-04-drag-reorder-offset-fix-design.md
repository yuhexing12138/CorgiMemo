# 拖拽位置跳跃（多区域偏移）修复 设计文档

> **创建日期**: 2026-07-04
> **关联模块**: 待办页面拖拽排序（ReorderableLazyColumn + HomeViewModel）
> **问题状态**: 根因已定位，待实施
> **关联历史**: 替换 2026-06-30 调试方案（该方案针对"第三次跳跃"，本方案针对"多区域共存即跳跃"，根因不同）

---

## 1. 问题描述

### 1.1 现象

待办页面同时存在**待完成 + 已完成**（或 + 置顶）区域时，单次拖拽即产生位置跳跃。

**复现用例**：10 条待办（名称 1-10，1-9 待完成、10 已完成），Case B（置顶 < 4）结构。将卡片"1"拖到"2、3"之间释放。

- **预期顺序**：`2,1,3,4,5,6,7,8,9`（pending 区）
- **实际顺序**：`1,3,2,4,5,6,7,8,9`

### 1.2 触发条件

- 待完成区前方存在 1~2 个分隔按钮（`PendingDivider` / `PinnedDivider`），导致待办卡片在 `displayItems` 中**不从索引 0 开始**。
- 当仅存在待完成区（无已完成）时，`dividerIndex = -1`，代码误判所有项为"已完成区" → `removeAt` 越界 → 静默 `return`，排序被跳过（看似"正常"实则未生效）。

---

## 2. 根因

### 2.1 数据结构

`HomeScreen` 构建的 `displayItems` 是**扁平结构**，分隔按钮与待办卡片混排（[HomeScreen.kt:744-790](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt#L744-L790)）：

- **Case B（置顶 < 4）**：`[PendingDivider(0), 1(1), 2(2), ..., CompletedDivider(k), C1(k+1), ...]`
- **Case A（置顶 ≥ 4）**：`[PinnedDivider(0), P1(1), ..., PendingDivider(k), N1(k+1), ..., CompletedDivider(m), ...]`

待办卡片在 `displayItems` 中的起始索引 = 前导分隔按钮数（1 或 2），**不为 0**。

### 2.2 Bug 推演（复现用例）

Case B，9 待完成 + 1 已完成：
```
displayItems = [PendingDivider(0), 1(1), 2(2), 3(3), ..., 9(9), CompletedDivider(10), 10(11)]
pendingList  = [1, 2, 3, ..., 9]  (索引 0-8)
dividerIndex = 10
```

拖"1"到 2、3 之间 → 库回调 `onReorder(fromIndex=1, toIndex=2, dividerIndex=10)`。

[HomeViewModel.kt:825-829](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L825-L829) pending 分支**直接**用全局 `fromIndex`/`toIndex` 操作 `pendingList`：

```
移除: pendingList.removeAt(fromIndex=1)  → 移除的是"2"（应为"1"）
插入: pendingList.add(toIndex=2, "2")    → [1, 3, 2, 4, ...]
```

结果 `1,3,2` —— 与用户观察完全一致。

### 2.3 第二个 Bug：dividerIndex = -1 误判

[HomeViewModel.kt:818-821](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L818-L821)：

```kotlin
val fromPending = fromIndex < dividerIndex      // dividerIndex=-1 → 永远 false
val fromCompleted = fromIndex > dividerIndex    // → 永远 true
```

无已完成区时所有项被误判为"已完成区"，`removeAt(fromIndex - dividerIndex - 1)` 越界 → 静默 `return`，排序跳过。

### 2.4 影响范围

`mergeReorderOnDisplayList`（合并拖拽）存在**完全相同**的两个 bug：
- [HomeViewModel.kt:1000-1001](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L1000-L1001)：`dividerIndex=-1` 误判
- [HomeViewModel.kt:1033](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L1033)：pending 插入用全局 `toIndex`

---

## 3. 修复方案：偏移校正

### 3.1 核心思路

`HomeScreen` 额外传入两个定位参数，ViewModel 在 pending 分支将"全局 displayItems 索引"转换为"pendingList 索引"。

### 3.2 新增参数

| 参数 | 含义 | 计算方式 |
|---|---|---|
| `pendingStartIndex` | 第一个待办卡片在 displayItems 中的全局索引 | `displayItems.indexOfFirst { it is DisplayItem.Todo }` |
| `midPendingDividerIndex` | Case A 中分隔置顶/非置顶的 PendingDivider 索引；Case B 传 -1 | Case A: `displayItems.indexOfFirst { it is DisplayItem.PendingDivider }`；Case B: `-1` |

### 3.3 偏移计算 helper

```kotlin
fun pendingOffset(displayIdx: Int): Int =
    pendingStartIndex + (if (midPendingDividerIndex >= 0 && displayIdx > midPendingDividerIndex) 1 else 0)
```

**正确性依据**：分隔按钮 `isDraggable = false`，`onMove` 会阻止拖拽跨越分隔按钮（[ReorderableLazyColumn.kt:285-287](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L285-L287)）。因此单次拖拽的 from/to 必在同一子区（置顶区或非置顶区），单一偏移量成立。

### 3.4 reorderOnDisplayList 修改

**签名**：增加 `pendingStartIndex: Int, midPendingDividerIndex: Int`。

**判区修正**（dividerIndex = -1 全部按 pending）：
```kotlin
val fromPending = dividerIndex < 0 || fromIndex < dividerIndex
val fromCompleted = dividerIndex >= 0 && fromIndex > dividerIndex
val toPending = dividerIndex < 0 || toIndex < dividerIndex
val toCompleted = dividerIndex >= 0 && toIndex > dividerIndex
```

**pending 移除**（[L825-829](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L825-L829)）：
```kotlin
fromPending -> {
    val idx = fromIndex - pendingOffset(fromIndex)
    if (idx !in pendingList.indices) return@launch
    pendingList.removeAt(idx)
}
```

**pending 插入**（[L879-881](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L879-L881)）：
```kotlin
toPending -> {
    val insertIdx = (toIndex - pendingOffset(toIndex)).coerceIn(0, pendingList.size)
    pendingList.add(insertIdx, finalItem)
}
```

**completed 分支不变**（已完成区偏移 `toIndex - dividerIndex - 1` 原本正确）。

### 3.5 mergeReorderOnDisplayList 修改

**签名**：同样增加 `pendingStartIndex, midPendingDividerIndex`。

**判区修正**（[L1000-1001](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L1000-L1001)）：同 3.4。

**pending 插入**（[L1033](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L1033)）：
```kotlin
toPending -> {
    val insertIdx = (toIndex - pendingOffset(toIndex)).coerceIn(0, pendingList.size)
    pendingList.addAll(insertIdx, finalItems)
}
```

**移除逻辑不变**（[L988-993](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L988-L993) 使用 pendingList 索引，正确）。

### 3.6 HomeScreen 修改

[HomeScreen.kt:821-832](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt#L821-L832) 的两个 lambda 增加参数计算与传递：

```kotlin
val pendingStartIndex = displayItems.indexOfFirst { it is DisplayItem.Todo }
val midPendingDividerIndex =
    if (pinnedCount >= 4) displayItems.indexOfFirst { it is DisplayItem.PendingDivider }
    else -1

onReorder = { fromIndex, toIndex, dividerIndex, crossed ->
    viewModel.reorderOnDisplayList(
        fromIndex, toIndex, dividerIndex, crossed,
        pendingStartIndex, midPendingDividerIndex
    )
}
onMergeReorder = { selectedIds, toIndex, dividerIndex, crossed ->
    viewModel.mergeReorderOnDisplayList(
        selectedIds.mapNotNull { it as? Long }.toSet(),
        toIndex, dividerIndex, crossed,
        pendingStartIndex, midPendingDividerIndex
    )
}
```

### 3.7 验证（复现用例）

`displayItems=[PendingDivider(0),1(1),2(2),…,9(9),CompletedDivider(10),10(11)]`
`pendingStartIndex=1, midPendingDividerIndex=-1, dividerIndex=10`

拖"1"到 2、3 之间 → `from=1, to=2`：
- `pendingOffset(1) = 1 + 0 = 1` → 移除 `idx = 1-1 = 0` → 移除"1" → `[2,3,…,9]`
- `pendingOffset(2) = 1 + 0 = 1` → 插入 `insertIdx = 2-1 = 1` → `add(1,"1")` → `[2,1,3,…,9]` ✓

---

## 4. 测试计划

### 4.1 现有测试修正

`HomeViewModelReorderTest.kt` 中 `Case A/B 跨边界` 测试当前用全局 displayIndex 调用但未传新参数 → 补齐参数后应通过。

### 4.2 新增测试

| 测试名 | 场景 | 预期 |
|---|---|---|
| `Case B 同区拖拽应正确偏移` | 9 待完成+1 已完成，拖 1 到 2/3 间 | pending 顺序 = `2,1,3,…,9` |
| `Case A 同区拖拽置顶区内偏移` | 4 置顶+5 非置顶，置顶区内拖拽 | 正确偏移 1 |
| `Case A 同区拖拽非置顶区内偏移` | 同上，非置顶区内拖拽 | 正确偏移 2 |
| `dividerIndex 负一同区拖拽持久化` | 仅 2 待完成无已完成 | 正常排序，不静默跳过 |

---

## 5. 风险与权衡

| 风险 | 缓解 |
|---|---|
| 折叠区域（showPinned=false）时 pendingStartIndex 变化 | `indexOfFirst` 动态计算，自然适配；折叠项不在 displayItems 但在 pendingList，重新分配 sortOrder 覆盖全部项，无碰撞 |
| 跨分隔按钮拖拽导致偏移错乱 | `onMove` 已阻止跨越不可拖拽项，from/to 必在同一子区 |
| 现有测试签名变更 | 仅补齐参数，不改断言逻辑 |

---

**文档版本**: v1.0
**创建日期**: 2026-07-04
