# 拖拽位置跳跃（多区域偏移）修复 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复待办页面多区域共存时拖拽产生位置跳跃的 bug（displayItems 全局索引未减去待办区前导分隔按钮偏移）。

**Architecture:** `HomeScreen` 新增传入两个定位参数（pendingStartIndex、midPendingDividerIndex），`HomeViewModel` 的 `reorderOnDisplayList` 与 `mergeReorderOnDisplayList` 增加 pending 偏移校正 helper，并修正 `dividerIndex=-1`（无已完成区）时的区域误判。

**Tech Stack:** Kotlin、Compose、MVVM、JUnit4 + MockK（现有测试栈）

**关联设计文档**: [2026-07-04-drag-reorder-offset-fix-design.md](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-04-drag-reorder-offset-fix-design.md)

---

## 文件清单

| 文件 | 操作 | 职责 |
|---|---|---|
| `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` | 修改 | 修复 `reorderOnDisplayList`（L794-935）与 `mergeReorderOnDisplayList`（L953-1087）的偏移与判区 |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 修改 | 在 onReorder/onMergeReorder lambda（L821-833）中计算并传入两个定位参数 |
| `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt` | 修改 | 修正现有 Case A/B 跨区测试签名；新增 4 个偏移回归测试 |

---

## Task 1: 新增 Case B 同区拖拽偏移回归测试（先写失败测试）

**Files:**
- Modify: `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt`

- [ ] **Step 1: 在测试类末尾（`testTodo` 辅助方法之前）追加 4 个新测试**

在 `dividerIndex 负一表示无已完成区不应误判跨区` 测试之后、`// ==================== 测试辅助方法 ====================` 注释之前插入：

```kotlin
    /**
     * 场景：Case B（置顶 < 4）同区拖拽，9 待完成 + 1 已完成
     *
     * displayItems 结构：
     * [PendingDivider(0), 1(1), 2(2), 3(3), ..., 9(9), CompletedDivider(10), 10(11)]
     * pendingStartIndex = 1, midPendingDividerIndex = -1, dividerIndex = 10
     *
     * 拖"1"到 2、3 之间 → from=1, to=2
     * 预期 pending 顺序：[2,1,3,4,5,6,7,8,9]（不是 [1,3,2,...]）
     */
    @Test
    fun `Case B 同区拖拽应正确偏移`() = runTest {
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 1),
            testTodo(3, isPinned = false, sortOrder = 2),
            testTodo(4, isPinned = false, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6),
            testTodo(8, isPinned = false, sortOrder = 7),
            testTodo(9, isPinned = false, sortOrder = 8),
            testTodo(10, isPinned = false, sortOrder = 9).copy(status = 1)
        )
        viewModel.refreshTodosForTest(todos)

        viewModel.reorderOnDisplayList(
            fromIndex = 1,
            toIndex = 2,
            dividerIndex = 10,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = -1
        )

        // 验证：1 的 sortOrder 应为 1（位置 2），2 的 sortOrder 应为 0（位置 1）
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            byId[1L]?.sortOrder == 1 && byId[2L]?.sortOrder == 0
        }) }
    }

    /**
     * 场景：Case A（置顶 ≥ 4）置顶区内拖拽
     *
     * displayItems 结构：
     * [PinnedDivider(0), P1(1), P2(2), P3(3), P4(4), PendingDivider(5), N1(6), ..., N5(10), CompletedDivider(11), C1(12)]
     * pendingStartIndex = 1, midPendingDividerIndex = 5, dividerIndex = 11
     *
     * 拖 P1 到 P2、P3 之间 → from=1, to=2（均在置顶区，< mid=5）
     * 偏移 = pendingStartIndex(1) + 0 = 1
     * 预期 pendingList 顺序：[P2, P1, P3, P4, N1, N2, N3, N4, N5]
     */
    @Test
    fun `Case A 置顶区内拖拽应正确偏移`() = runTest {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = true, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6),
            testTodo(8, isPinned = false, sortOrder = 7),
            testTodo(9, isPinned = false, sortOrder = 8),
            testTodo(10, isPinned = false, sortOrder = 9).copy(status = 1)
        )
        viewModel.refreshTodosForTest(todos)

        viewModel.reorderOnDisplayList(
            fromIndex = 1,
            toIndex = 2,
            dividerIndex = 11,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = 5
        )

        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            byId[1L]?.sortOrder == 1 && byId[2L]?.sortOrder == 0
        }) }
    }

    /**
     * 场景：Case A（置顶 ≥ 4）非置顶区内拖拽
     *
     * displayItems 同上：pendingStartIndex = 1, mid = 5, dividerIndex = 11
     * 拖 N1(6) 到 N2(7)、N3(8) 之间 → from=6, to=7（均 > mid=5）
     * 偏移 = 1 + 1 = 2
     * 预期 pendingList 非置顶部分：[N2, N1, N3, N4, N5]
     */
    @Test
    fun `Case A 非置顶区内拖拽应正确偏移`() = runTest {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = true, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6),
            testTodo(8, isPinned = false, sortOrder = 7),
            testTodo(9, isPinned = false, sortOrder = 8),
            testTodo(10, isPinned = false, sortOrder = 9).copy(status = 1)
        )
        viewModel.refreshTodosForTest(todos)

        viewModel.reorderOnDisplayList(
            fromIndex = 6,
            toIndex = 7,
            dividerIndex = 11,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = 5
        )

        // N1(6) 原 sortOrder=4 → 新位置 5；N2(7) 原 5 → 新 4
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            byId[6L]?.sortOrder == 5 && byId[7L]?.sortOrder == 4
        }) }
    }

    /**
     * 场景：dividerIndex = -1（无已完成区）同区拖拽应正常持久化
     *
     * 旧 bug：dividerIndex=-1 时所有项被误判为已完成区 → removeAt 越界 → 静默 return
     * 修复后：dividerIndex<0 全部按 pending 处理
     */
    @Test
    fun `dividerIndex 负一同区拖拽应持久化`() = runTest {
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 1),
            testTodo(3, isPinned = false, sortOrder = 2)
        )
        viewModel.refreshTodosForTest(todos)

        viewModel.reorderOnDisplayList(
            fromIndex = 1,
            toIndex = 2,
            dividerIndex = -1,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = -1
        )

        // 应当调用 updateTodos（而非静默跳过）
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(any()) }
    }
```

- [ ] **Step 2: 运行新测试验证它们失败（编译错误：方法签名不匹配）**

Run:
```powershell
cd C:\Users\EDY\Desktop\CorgiMemo; .\gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.viewmodel.HomeViewModelReorderTest.Case B 同区拖拽应正确偏移" --tests "com.corgimemo.app.viewmodel.HomeViewModelReorderTest.dividerIndex 负一同区拖拽应持久化"
```
Expected: FAIL（编译错误，`reorderOnDisplayList` 不接受 `pendingStartIndex`/`midPendingDividerIndex` 参数）

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt
git commit -m "test: 新增拖拽偏移回归测试（当前失败）"
```

---

## Task 2: 修正现有 Case A/B 跨区测试签名

**Files:**
- Modify: `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt`

- [ ] **Step 1: 修正 `Case A 置顶大于等于4 跨边界拖拽应标记为完成` 测试的 reorderOnDisplayList 调用**

找到（约 L218-223）：
```kotlin
        viewModel.reorderOnDisplayList(
            fromIndex = 10,
            toIndex = 12,
            dividerIndex = 11,
            crossedPinnedZone = false
        )
```
替换为：
```kotlin
        viewModel.reorderOnDisplayList(
            fromIndex = 10,
            toIndex = 12,
            dividerIndex = 11,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = 5
        )
```

- [ ] **Step 2: 修正 `Case B 置顶小于4 跨边界拖拽应标记为完成` 测试的 reorderOnDisplayList 调用**

找到（约 L267-272）：
```kotlin
        viewModel.reorderOnDisplayList(
            fromIndex = 7,
            toIndex = 9,
            dividerIndex = 8,
            crossedPinnedZone = false
        )
```
替换为：
```kotlin
        viewModel.reorderOnDisplayList(
            fromIndex = 7,
            toIndex = 9,
            dividerIndex = 8,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = -1
        )
```

- [ ] **Step 3: 修正 `dividerIndex 负一表示无已完成区不应误判跨区` 测试的调用**

找到（约 L292-297）：
```kotlin
        viewModel.reorderOnDisplayList(
            fromIndex = 0,
            toIndex = 1,
            dividerIndex = -1,
            crossedPinnedZone = false
        )
```
替换为：
```kotlin
        viewModel.reorderOnDisplayList(
            fromIndex = 0,
            toIndex = 1,
            dividerIndex = -1,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = -1
        )
```

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt
git commit -m "test: 同步现有跨区测试签名（新增偏移参数）"
```

---

## Task 3: 修复 reorderOnDisplayList

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` (L785-935)

- [ ] **Step 1: 更新方法签名与 KDoc**

找到（L785-794）：
```kotlin
    /**
     * 拖拽完成后调用（统一入口，支持区域内排序和跨区域拖拽自动完成/取消完成）
     *
     * @param fromIndex 被拖项原始位置（displayItems 全局索引）
     * @param toIndex 被拖项最终位置（displayItems 全局索引）
     * @param dividerIndex CompletedDivider 在 displayItems 中的真实索引（-1 表示没有已完成区）
     *                    由 HomeScreen 通过 `displayItems.indexOfFirst { it is DisplayItem.CompletedDivider }` 计算后传入
     * @param crossedPinnedZone 是否跨越置顶区分界线
     */
    fun reorderOnDisplayList(fromIndex: Int, toIndex: Int, dividerIndex: Int, crossedPinnedZone: Boolean) {
```
替换为：
```kotlin
    /**
     * 拖拽完成后调用（统一入口，支持区域内排序和跨区域拖拽自动完成/取消完成）
     *
     * @param fromIndex 被拖项原始位置（displayItems 全局索引）
     * @param toIndex 被拖项最终位置（displayItems 全局索引）
     * @param dividerIndex CompletedDivider 在 displayItems 中的真实索引（-1 表示没有已完成区）
     *                    由 HomeScreen 通过 `displayItems.indexOfFirst { it is DisplayItem.CompletedDivider }` 计算后传入
     * @param crossedPinnedZone 是否跨越置顶区分界线
     * @param pendingStartIndex 第一个待办卡片在 displayItems 中的全局索引（前导分隔按钮数）
     * @param midPendingDividerIndex Case A 中分隔置顶/非置顶的 PendingDivider 索引；Case B 传 -1
     */
    fun reorderOnDisplayList(
        fromIndex: Int,
        toIndex: Int,
        dividerIndex: Int,
        crossedPinnedZone: Boolean,
        pendingStartIndex: Int,
        midPendingDividerIndex: Int
    ) {
```

- [ ] **Step 2: 在 `viewModelScope.launch {` 之后添加 pendingOffset helper**

找到（L795-796）：
```kotlin
    ) {
        viewModelScope.launch {
            val pendingList = pendingTodos.value.toMutableList()
```
替换为：
```kotlin
    ) {
        viewModelScope.launch {
            /** 将 displayItems 全局索引转换为 pendingList 索引 */
            fun pendingOffset(displayIdx: Int): Int =
                pendingStartIndex + (if (midPendingDividerIndex >= 0 && displayIdx > midPendingDividerIndex) 1 else 0)

            val pendingList = pendingTodos.value.toMutableList()
```

- [ ] **Step 3: 修正区域判断（dividerIndex=-1 全部按 pending）**

找到（L818-821）：
```kotlin
            val fromPending = fromIndex < dividerIndex
            val fromCompleted = fromIndex > dividerIndex
            val toPending = toIndex < dividerIndex
            val toCompleted = toIndex > dividerIndex
```
替换为：
```kotlin
            val fromPending = dividerIndex < 0 || fromIndex < dividerIndex
            val fromCompleted = dividerIndex >= 0 && fromIndex > dividerIndex
            val toPending = dividerIndex < 0 || toIndex < dividerIndex
            val toCompleted = dividerIndex >= 0 && toIndex > dividerIndex
```

- [ ] **Step 4: 修正 pending 移除索引**

找到（L825-829）：
```kotlin
                fromPending -> {
                    val idx = fromIndex
                    if (idx !in pendingList.indices) return@launch
                    pendingList.removeAt(idx)
                }
```
替换为：
```kotlin
                fromPending -> {
                    val idx = fromIndex - pendingOffset(fromIndex)
                    if (idx !in pendingList.indices) return@launch
                    pendingList.removeAt(idx)
                }
```

- [ ] **Step 5: 修正 pending 插入索引**

找到（L878-882）：
```kotlin
                toPending -> {
                    val insertIdx = toIndex.coerceAtMost(pendingList.size)
                    pendingList.add(insertIdx, finalItem)
                }
```
替换为：
```kotlin
                toPending -> {
                    val insertIdx = (toIndex - pendingOffset(toIndex)).coerceIn(0, pendingList.size)
                    pendingList.add(insertIdx, finalItem)
                }
```

- [ ] **Step 6: 运行单拖相关测试验证通过**

Run:
```powershell
cd C:\Users\EDY\Desktop\CorgiMemo; .\gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.viewmodel.HomeViewModelReorderTest"
```
Expected: PASS（所有 7 个测试通过，包括 Task 1 新增的 4 个）

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt
git commit -m "fix: 修复 reorderOnDisplayList 待办区偏移与无已完成区误判"
```

---

## Task 4: 修复 mergeReorderOnDisplayList

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` (L938-1087)

- [ ] **Step 1: 更新方法签名与 KDoc**

找到（L948-958）：
```kotlin
     * @param selectedIds 已选中项的 id 集合
     * @param toIndex 占位框在 displayItems 中的目标位置
     * @param dividerIndex CompletedDivider 在 displayItems 中的真实索引（-1 表示没有已完成区）
     * @param crossedPinnedZone 是否跨越置顶区分界线
     */
    fun mergeReorderOnDisplayList(
        selectedIds: Set<Long>,
        toIndex: Int,
        dividerIndex: Int,
        crossedPinnedZone: Boolean
    ) {
        viewModelScope.launch {
```
替换为：
```kotlin
     * @param selectedIds 已选中项的 id 集合
     * @param toIndex 占位框在 displayItems 中的目标位置
     * @param dividerIndex CompletedDivider 在 displayItems 中的真实索引（-1 表示没有已完成区）
     * @param crossedPinnedZone 是否跨越置顶区分界线
     * @param pendingStartIndex 第一个待办卡片在 displayItems 中的全局索引（前导分隔按钮数）
     * @param midPendingDividerIndex Case A 中分隔置顶/非置顶的 PendingDivider 索引；Case B 传 -1
     */
    fun mergeReorderOnDisplayList(
        selectedIds: Set<Long>,
        toIndex: Int,
        dividerIndex: Int,
        crossedPinnedZone: Boolean,
        pendingStartIndex: Int,
        midPendingDividerIndex: Int
    ) {
        viewModelScope.launch {
            /** 将 displayItems 全局索引转换为 pendingList 索引 */
            fun pendingOffset(displayIdx: Int): Int =
                pendingStartIndex + (if (midPendingDividerIndex >= 0 && displayIdx > midPendingDividerIndex) 1 else 0)
```

- [ ] **Step 2: 修正区域判断（dividerIndex=-1 全部按 pending）**

找到（L999-1001）：
```kotlin
            // 4. 计算目标区域（dividerIndex 在移除后已变化，用原 dividerIndex 判断）
            val toPending = toIndex < dividerIndex
            val toCompleted = toIndex > dividerIndex
```
替换为：
```kotlin
            // 4. 计算目标区域（dividerIndex 在移除后已变化，用原 dividerIndex 判断）
            val toPending = dividerIndex < 0 || toIndex < dividerIndex
            val toCompleted = dividerIndex >= 0 && toIndex > dividerIndex
```

- [ ] **Step 3: 修正 pending 插入索引**

找到（L1031-1035）：
```kotlin
            when {
                toPending -> {
                    val insertIdx = toIndex.coerceAtMost(pendingList.size)
                    pendingList.addAll(insertIdx, finalItems)
                }
```
替换为：
```kotlin
            when {
                toPending -> {
                    val insertIdx = (toIndex - pendingOffset(toIndex)).coerceIn(0, pendingList.size)
                    pendingList.addAll(insertIdx, finalItems)
                }
```

- [ ] **Step 4: 运行全量 ViewModel 测试确认无回归**

Run:
```powershell
cd C:\Users\EDY\Desktop\CorgiMemo; .\gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.viewmodel.HomeViewModelReorderTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt
git commit -m "fix: 修复 mergeReorderOnDisplayList 同样的偏移与误判问题"
```

---

## Task 5: HomeScreen 计算并传入定位参数

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` (L806-835)

- [ ] **Step 1: 在 ReorderableLazyColumn 调用前添加定位参数计算**

找到（L806-820）：
```kotlin
                            ReorderableLazyColumn(
                                items = displayItems,
                                listState = lazyListState,
                                isDragEnabled = !isBatchMode && swipeExpandedTodoId == null
                                    && searchQuery.isBlank() && selectedCategoryId == null,
                                isDraggable = { it is DisplayItem.Todo },
                                isPinned = { (it as? DisplayItem.Todo)?.item?.isPinned ?: false },
                                key = { item ->
                                    when (item) {
                                        is DisplayItem.Todo -> item.item.id
                                        is DisplayItem.PinnedDivider -> "pinned_divider_${item.count}"
                                        is DisplayItem.PendingDivider -> "pending_divider_${item.count}"
                                        is DisplayItem.CompletedDivider -> "completed_divider"
                                    }
                                },
                                onReorder = { fromIndex, toIndex, dividerIndex, crossed ->
                                    viewModel.reorderOnDisplayList(fromIndex, toIndex, dividerIndex, crossed)
                                },
                                isBatchMode = isBatchMode,
                                selectedIds = selectedTodoIds.map { it as Any }.toSet(),
                                onMergeReorder = { selectedIds, toIndex, dividerIndex, crossed ->
                                    viewModel.mergeReorderOnDisplayList(
                                        selectedIds.mapNotNull { it as? Long }.toSet(),
                                        toIndex,
                                        dividerIndex,
                                        crossed
                                    )
                                },
```
替换为：
```kotlin
                            val pendingStartIndex = displayItems.indexOfFirst { it is DisplayItem.Todo }
                            val midPendingDividerIndex =
                                if (pinnedCount >= 4)
                                    displayItems.indexOfFirst { it is DisplayItem.PendingDivider }
                                else -1

                            ReorderableLazyColumn(
                                items = displayItems,
                                listState = lazyListState,
                                isDragEnabled = !isBatchMode && swipeExpandedTodoId == null
                                    && searchQuery.isBlank() && selectedCategoryId == null,
                                isDraggable = { it is DisplayItem.Todo },
                                isPinned = { (it as? DisplayItem.Todo)?.item?.isPinned ?: false },
                                key = { item ->
                                    when (item) {
                                        is DisplayItem.Todo -> item.item.id
                                        is DisplayItem.PinnedDivider -> "pinned_divider_${item.count}"
                                        is DisplayItem.PendingDivider -> "pending_divider_${item.count}"
                                        is DisplayItem.CompletedDivider -> "completed_divider"
                                    }
                                },
                                onReorder = { fromIndex, toIndex, dividerIndex, crossed ->
                                    viewModel.reorderOnDisplayList(
                                        fromIndex, toIndex, dividerIndex, crossed,
                                        pendingStartIndex, midPendingDividerIndex
                                    )
                                },
                                isBatchMode = isBatchMode,
                                selectedIds = selectedTodoIds.map { it as Any }.toSet(),
                                onMergeReorder = { selectedIds, toIndex, dividerIndex, crossed ->
                                    viewModel.mergeReorderOnDisplayList(
                                        selectedIds.mapNotNull { it as? Long }.toSet(),
                                        toIndex,
                                        dividerIndex,
                                        crossed,
                                        pendingStartIndex,
                                        midPendingDividerIndex
                                    )
                                },
```

- [ ] **Step 2: 编译验证**

Run:
```powershell
cd C:\Users\EDY\Desktop\CorgiMemo; .\gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "fix: HomeScreen 计算并传入待办区定位参数"
```

---

## Task 6: 全量构建与测试验证

**Files:**
- 无修改

- [ ] **Step 1: 运行全量单元测试**

Run:
```powershell
cd C:\Users\EDY\Desktop\CorgiMemo; .\gradlew :app:testDebugUnitTest
```
Expected: 所有测试通过（含 ReorderAlgorithmsTest）

- [ ] **Step 2: 运行 assembleDebug 确认整体编译**

Run:
```powershell
cd C:\Users\EDY\Desktop\CorgiMemo; .\gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: （可选）手动验证复现场景**

在设备上安装 debug 包，复现用户报告场景：
1. 9 待完成 + 1 已完成，拖"1"到 2、3 之间 → 期望 `2,1,3,...`
2. Case A 置顶 ≥ 4，置顶区内拖拽 → 期望正确偏移
3. 仅待完成（无已完成）拖拽 → 期望正常持久化（非静默跳过）

---

## 验收标准

- [x] `Case B 同区拖拽应正确偏移` 测试通过
- [x] `Case A 置顶区内拖拽应正确偏移` 测试通过
- [x] `Case A 非置顶区内拖拽应正确偏移` 测试通过
- [x] `dividerIndex 负一同区拖拽应持久化` 测试通过
- [x] 现有 `Case A/B 跨边界` 测试通过（签名同步）
- [x] `assembleDebug` 构建成功
