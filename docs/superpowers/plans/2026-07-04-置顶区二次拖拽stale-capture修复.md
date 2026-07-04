# 置顶区二次拖拽 stale-capture 修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `onDragStarted` lambda 中 `draggedOriginalIsPinned = isPinned(item)` 捕获旧 `item` 引用导致的"已置顶项二次拖拽后跳回待完成区"bug。

**Architecture:** 在 `ReorderableLazyColumn.kt` 的 `onDragStarted` lambda 中，将 `draggedOriginalIsPinned` 的读取方式从直接访问捕获的 `item` 改为通过 `displayItems.indexOfFirst { key(it) == key(item) }` 查询当前 `displayItems` 中的最新引用，与 `draggedOriginalIndex` 已有的修复模式保持一致。同时新增算法层与 ViewModel 层的回归测试，固化 bug 场景不变式。

**Tech Stack:** Kotlin、Jetpack Compose、JUnit 4、MockK、kotlinx-coroutines-test

---

## File Structure

| 文件 | 责任 | 操作 |
|---|---|---|
| `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` | 拖拽组件，包含 `onDragStarted` lambda | 修改 L682-L700 |
| `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt` | `checkPinnedZoneCrossed` 算法纯函数测试 | 新增 1 个测试方法 |
| `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt` | `reorderOnDisplayList` ViewModel 测试 | 新增 1 个测试方法 |

---

### Task 1: 新增算法层回归测试

**Files:**
- Modify: `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt`

**说明：** `checkPinnedZoneCrossed` 算法本身正确，bug 在调用方传错 `draggedOriginalIsPinned`。此测试作为回归保护，确保未来重构算法时不破坏"已置顶项与置顶区邻居同区不应跨区"不变式。

- [ ] **Step 1: 在 ReorderAlgorithmsTest.kt 末尾追加测试方法**

在 `ReorderAlgorithmsTest.kt` 类的最后一个测试方法 `draggedCurrentIndex 负数应返回 false` 之后，追加以下测试：

```kotlin
    /**
     * 场景：已置顶项（isPinned=true）在置顶区内拖拽，邻居同为 isPinned=true
     *
     * 这是 "首次拖入置顶区后再次拖拽" bug 场景的算法层不变式：
     * - draggedOriginalIsPinned=true（修复后由 displayItems 查询得到）
     * - 邻居 isPinned=true
     * - 期望 crossed=false（不触发 isPinned 翻转）
     *
     * 回归保护：未来若算法被重构，必须保持此不变式。
     */
    @Test
    fun `已置顶项与置顶区邻居同区不应跨区`() {
        val items = listOf(
            TestItem(isPinned = true),   // P1
            TestItem(isPinned = true)    // P2
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 1)
        assertEquals(false, result)
    }
```

- [ ] **Step 2: 运行测试验证通过（算法已正确，应直接通过）**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest.已置顶项与置顶区邻居同区不应跨区"
```

Expected: PASSED

- [ ] **Step 3: 不提交，进入下一个 Task**

（按 workspace rule，所有任务完成后统一询问是否提交）

---

### Task 2: 新增 ViewModel 层回归测试

**Files:**
- Modify: `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt`

**说明：** 此测试模拟 bug 场景的 ViewModel 下游行为：N5 已 pinned（首次拖入后的状态），在置顶区内拖动且 `crossedPinnedZone=false`（修复后的正确值）时，ViewModel 不应调用 `updateTodo` 翻转 `isPinned`。

- [ ] **Step 1: 在 HomeViewModelReorderTest.kt 末尾、`testTodo` 辅助方法之前追加测试方法**

定位到 `// ==================== 测试辅助方法 ====================` 注释行之前，追加以下测试：

```kotlin
    /**
     * 场景：N5 首次拖入置顶区后再次在置顶区内拖动
     *
     * 这是用户报告的 "二次拖拽跳跃" bug 场景的 ViewModel 层验证：
     * - 初始：N1-N4 已 pinned，N5 已 pinned（首次拖入后的状态）
     * - 二次拖动：N5 在置顶区内移动，crossedPinnedZone=false（修复后正确值）
     * - 预期：不调用 updateTodo 翻转 isPinned（N5 保持 isPinned=true）
     *         仅调用 updateTodos 重排 sortOrder
     *
     * 回归保护：确保 ViewModel 在 crossedPinnedZone=false 时不会误翻转 isPinned。
     */
    @Test
    fun `二次拖拽已置顶项不应翻转 isPinned`() = runTest(testDispatcher) {
        // Given: N1-N4 已 pinned + N5 已 pinned（首次拖入后的状态）+ N6-N7 pending
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = true, sortOrder = 3),
            testTodo(5, isPinned = true, sortOrder = 4),   // N5 已 pinned
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6)
        )
        setupTodos(todos)

        // When: 二次拖动 N5(置顶区内移动)，crossedPinnedZone=false（修复后正确值）
        // displayItems: [PinnedDivider(0), P1(1), P2(2), P3(3), P4(4), N5(5), PendingDivider(6), N6(7), N7(8)]
        // 拖 N5(5) 到 P4(4) 上方 → fromIndex=5, toIndex=4
        viewModel.reorderOnDisplayList(
            fromIndex = 5,
            toIndex = 4,
            dividerIndex = -1,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = 6
        )

        // Then: 不调用 updateTodo 翻转 N5 的 isPinned
        coVerify(exactly = 0) { mockTodoRepository.updateTodo(match { it.id == 5L && !it.isPinned }) }
        // Then: 调用 updateTodos 重排 sortOrder，且 N5 仍为 isPinned=true
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val n5 = updates.find { it.id == 5L }
            n5?.isPinned == true
        }) }
    }
```

- [ ] **Step 2: 运行测试验证通过（ViewModel 已正确，应直接通过）**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.viewmodel.HomeViewModelReorderTest.二次拖拽已置顶项不应翻转 isPinned"
```

Expected: PASSED

- [ ] **Step 3: 不提交，进入下一个 Task**

---

### Task 3: 应用 onDragStarted 修复

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:682-700`

- [ ] **Step 1: 修改 onDragStarted lambda**

定位到 `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` 第 682-700 行的 `onDragStarted` lambda。

将以下旧代码：

```kotlin
                                    onDragStarted = {
                                        // 注意：draggedOriginalIndex 必须用 displayItems 计算，
                                        // 与 onDragStopped 中 draggedCurrentIndex 的参照系保持一致。
                                        // 原因：onDragStarted lambda 可能捕获旧的 items（因 longPressDraggableHandle
                                        // 的 pointerInput 未因 items 变化而重启），导致 draggedOriginalIndex
                                        // 用旧 items 计算，与 draggedCurrentIndex（用最新 displayItems 计算）
                                        // 参照系不一致，进而引发位置跳跃。
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

替换为：

```kotlin
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
```

- [ ] **Step 2: 检查 import 语句完整性**

按 workspace rule 检查 `ReorderableLazyColumn.kt` 的 import 语句。本次修改未引入新类型或新函数，仅修改 lambda 内部逻辑，无需新增 import。

- [ ] **Step 3: 编译验证**

Run:
```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

---

### Task 4: 全量测试 + 手动验证

- [ ] **Step 1: 运行全部单元测试**

Run:
```bash
./gradlew :app:testDebugUnitTest
```

Expected: 所有测试通过（包括原有的 12 个 ReorderAlgorithmsTest + 9 个 HomeViewModelReorderTest，以及 Task 1/2 新增的 2 个测试）

- [ ] **Step 2: 真机手动验证 bug 场景**

在真机上执行以下步骤：

1. 创建 10 条待办，命名为 1-10
2. 将 1、2、3、4 设为置顶状态
3. 长按 5 拖入置顶区（例如拖到 1 上方），释放 → 验证 5 变为置顶状态 ✓
4. 等待 UI 完全刷新（5 视觉上出现在置顶区）
5. 长按 5 在置顶区内拖动到不同位置（例如 4 上方），释放 → 验证 5 仍为置顶状态 ✓
6. 重复步骤 5 多次，验证 5 始终保持置顶状态 ✓

- [ ] **Step 3: 不提交，等待用户确认**

---

### Task 5: 询问用户是否进行 git 提交

按 workspace rule（`c:\Users\EDY\Desktop\CorgiMemo\.trae\rules\git提交.md`），任务完成后必须询问用户是否进行 git 提交。

- [ ] **Step 1: 询问用户是否进行 git 提交**

向用户呈现本次变更摘要：

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` | 修改 | onDragStarted 中 draggedOriginalIsPinned 改为从 displayItems 查询 |
| `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt` | 新增测试 | 算法层回归测试 |
| `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt` | 新增测试 | ViewModel 层回归测试 |

若用户选择提交：
- 智能生成中文提交信息
- 执行 `git add <specific files>` + `git commit -m "..."`

若用户选择不提交：跳过提交步骤。

---

## Self-Review

### Spec 覆盖检查

| Spec 要求 | 对应 Task |
|---|---|
| 修改 onDragStarted 中 draggedOriginalIsPinned 读取方式 | Task 3 Step 1 |
| 边界情况：draggedIdx < 0 时 fallback false | Task 3 Step 1（`if (draggedIdx >= 0) ... else false`） |
| 新增算法层回归测试 | Task 1 |
| 新增 ViewModel 层回归测试 | Task 2 |
| 编译通过 | Task 3 Step 3 |
| 全部测试通过 | Task 4 Step 1 |
| 真机验证 | Task 4 Step 2 |
| import 语句检查 | Task 3 Step 2 |
| git 提交询问 | Task 5 |

### Placeholder 扫描

无 TBD/TODO/placeholder。所有代码块完整。✓

### 类型一致性

- `draggedIdx`：Int（与 `draggedOriginalIndex` 一致）
- `isPinned(displayItems[draggedIdx])`：Boolean（与 `draggedOriginalIsPinned` 一致）
- `key(it) == key(item)`：与现有代码一致 ✓

### 后续可优化方向（来自 spec 第 7 节）

1. 审视其他 longPressDraggableHandle lambda 闭包变量
2. 抽取公共 helper `currentItem(item)`
3. 向 `sh.calvin.reorderable` 库提 PR 使用 `rememberUpdatedState`
