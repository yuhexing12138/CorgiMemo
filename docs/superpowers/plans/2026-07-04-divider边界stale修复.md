# divider 边界 stale 修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `checkPinnedZoneCrossed` 算法将 divider 当作"可跳过的项"而非"区域边界"，导致被拖项紧贴 divider 放置时跨越边界找邻居、误判跨区的 bug。

**Architecture:** 在 `ReorderableLazyColumn.kt` 的 `checkPinnedZoneCrossed` 中，将向前/向后查找邻居的逻辑从"跳过 divider 继续找"改为"遇 divider 即停止"。同时新增 2 个算法层回归测试覆盖 divider 边界场景。

**Tech Stack:** Kotlin、JUnit 4

---

## File Structure

| 文件 | 责任 | 操作 |
|---|---|---|
| `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` | `checkPinnedZoneCrossed` 算法 | 修改 L128-L145 |
| `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt` | 算法纯函数测试 | 新增 2 个测试方法 |

---

### Task 1: 新增算法层回归测试（先写测试，TDD）

**Files:**
- Modify: `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt`

**说明：** 先写测试，让其失败，再改算法使其通过。测试覆盖两个边界场景：divider 后第一项、divider 前第一项。

- [ ] **Step 1: 在 ReorderAlgorithmsTest.kt 末尾追加 2 个测试方法**

在 `已置顶项与置顶区邻居同区不应跨区` 测试方法之后、类结束 `}` 之前，追加以下 2 个测试：

```kotlin
    /**
     * 场景：非置顶项紧贴 PendingDivider 之后放置（divider 后第一项）
     *
     * 这是 "N6 拖到 PendingDivider 和 N5 之间" bug 场景的算法层不变式：
     * - displayItems: [PinnedDivider, P4, PendingDivider, N6(被拖到此处), N5]
     * - draggedOriginalIsPinned=false（N6 非置顶）
     * - 前邻居是 PendingDivider（边界）→ 应停止向前搜索
     * - 后邻居是 N5（isPinned=false）
     * - 期望 crossed=false（不触发 isPinned 翻转）
     *
     * 回归保护：未来若算法被重构，必须保持 "divider 是区域边界，不能跨越" 契约。
     */
    @Test
    fun `divider 后第一项不应跨区`() {
        val items = listOf(
            TestItem(isPinned = false, isDivider = true),   // PinnedDivider
            TestItem(isPinned = true),                       // P4
            TestItem(isPinned = false, isDivider = true),   // PendingDivider
            TestItem(isPinned = false),                      // N6（被拖到此处）
            TestItem(isPinned = false)                        // N5
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = false, draggedCurrentIndex = 3)
        assertEquals(false, result)
    }

    /**
     * 场景：置顶项紧贴 PendingDivider 之前放置（divider 前第一项）
     *
     * 对称场景：置顶区末尾项不应因后邻居是 divider 而被误判跨区。
     * - displayItems: [PinnedDivider, P4(被拖到此处), PendingDivider, N5, N6]
     * - draggedOriginalIsPinned=true（P4 置顶）
     * - 前邻居是 P4 之前的位置（此处用 P1 替代，isPinned=true）
     * - 后邻居是 PendingDivider（边界）→ 应停止向后搜索
     * - 期望 crossed=false
     */
    @Test
    fun `divider 前第一项不应跨区`() {
        val items = listOf(
            TestItem(isPinned = false, isDivider = true),   // PinnedDivider
            TestItem(isPinned = true),                       // P1（前邻居）
            TestItem(isPinned = true),                       // P4（被拖到此处）
            TestItem(isPinned = false, isDivider = true),   // PendingDivider
            TestItem(isPinned = false)                        // N5
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 2)
        assertEquals(false, result)
    }
```

- [ ] **Step 2: 运行测试验证失败（算法未改，应失败）**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest.divider 后第一项不应跨区" --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest.divider 前第一项不应跨区" --console=plain
```

Expected: 2 个测试 FAILED
- `divider 后第一项不应跨区`：expected:<false> but was:<true>
- `divider 前第一项不应跨区`：expected:<false> but was:<true>

- [ ] **Step 3: 不提交，进入下一个 Task**

---

### Task 2: 应用算法修复

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:128-145`

- [ ] **Step 1: 修改 checkPinnedZoneCrossed 算法**

定位到 `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` 第 128-145 行的 `checkPinnedZoneCrossed` 函数体。

将以下旧代码：

```kotlin
        if (draggedCurrentIndex < 0 || draggedCurrentIndex >= displayItems.size) return false

        // 优先向前找第一个非 divider 邻居
        var neighborIdx = -1
        for (i in draggedCurrentIndex - 1 downTo 0) {
            if (!isDivider(displayItems[i])) { neighborIdx = i; break }
        }
        // 前面没有则向后找
        if (neighborIdx < 0) {
            for (i in draggedCurrentIndex + 1 until displayItems.size) {
                if (!isDivider(displayItems[i])) { neighborIdx = i; break }
            }
        }
        // 整个列表都是 divider（理论上不可能）→ 不跨区
        if (neighborIdx < 0) return false

        return draggedOriginalIsPinned != isPinned(displayItems[neighborIdx])
```

替换为：

```kotlin
        if (draggedCurrentIndex < 0 || draggedCurrentIndex >= displayItems.size) return false

        // 向前找邻居：divider 是区域边界，遇到立即停止（不能跨越边界找邻居）
        var neighborIdx = -1
        val prevIdx = draggedCurrentIndex - 1
        if (prevIdx >= 0 && !isDivider(displayItems[prevIdx])) {
            neighborIdx = prevIdx
        }
        // 前面被 divider 阻断或到列表头 → 向后找
        if (neighborIdx < 0) {
            val nextIdx = draggedCurrentIndex + 1
            if (nextIdx < displayItems.size && !isDivider(displayItems[nextIdx])) {
                neighborIdx = nextIdx
            }
        }
        // 两边都被 divider 阻断或到列表边界 → 无法判定，不跨区
        if (neighborIdx < 0) return false

        return draggedOriginalIsPinned != isPinned(displayItems[neighborIdx])
```

- [ ] **Step 2: 检查 import 语句完整性**

按 workspace rule 检查 `ReorderableLazyColumn.kt` 的 import 语句。本次修改未引入新类型或新函数，仅修改函数体内部逻辑，无需新增 import。

- [ ] **Step 3: 运行新增测试验证通过**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest.divider 后第一项不应跨区" --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest.divider 前第一项不应跨区" --console=plain
```

Expected: 2 个测试 PASSED

- [ ] **Step 4: 编译验证**

Run:
```bash
./gradlew :app:assembleDebug --console=plain
```

Expected: BUILD SUCCESSFUL

---

### Task 3: 全量测试 + 真机验证

- [ ] **Step 1: 运行全部算法测试，确认无回归**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest" --console=plain
```

Expected: 全部 15 个测试通过（13 原有 + 2 新增）

- [ ] **Step 2: 运行全量单元测试**

Run:
```bash
./gradlew :app:testDebugUnitTest --console=plain
```

Expected: BUILD SUCCESSFUL（ReminderTimeFormatterTest 已在上一个 commit 修复）

- [ ] **Step 3: 真机手动验证 bug 场景**

在真机上执行以下步骤：

1. 创建 10 条待办，命名为 1-10
2. 将 1、2、3、4 设为置顶状态
3. 长按 6，拖动到 "待完成" 按钮（PendingDivider）和 5 之间释放
4. 验证：6 落在 "待完成" 按钮后、5 前，仍为非置顶状态 ✓
5. 重复步骤 3 多次，验证 6 始终落在待完成区，不跳跃到置顶区 ✓

- [ ] **Step 4: 不提交，等待用户确认**

---

### Task 4: 询问用户是否进行 git 提交

按 workspace rule（`c:\Users\EDY\Desktop\CorgiMemo\.trae\rules\git提交.md`），任务完成后必须询问用户是否进行 git 提交。

- [ ] **Step 1: 询问用户是否进行 git 提交**

向用户呈现本次变更摘要：

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` | 修改 | checkPinnedZoneCrossed 将 divider 视为区域边界，遇之即停止搜索 |
| `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt` | 新增测试 | 2 个 divider 边界场景回归测试 |
| `docs/superpowers/specs/2026-07-04-divider边界stale修复-design.md` | 新增 | 设计文档 |
| `docs/superpowers/plans/2026-07-04-divider边界stale修复.md` | 新增 | 实施计划 |

若用户选择提交：
- 智能生成中文提交信息
- 执行 `git add <specific files>` + `git commit -m "..."`

若用户选择不提交：跳过提交步骤。

---

## Self-Review

### Spec 覆盖检查

| Spec 要求 | 对应 Task |
|---|---|
| 将 divider 视为区域边界，遇到即停止搜索 | Task 2 Step 1 |
| 新增 2 个算法层测试覆盖 divider 边界场景 | Task 1 Step 1 |
| `./gradlew assembleDebug` 编译通过 | Task 2 Step 4 |
| `./gradlew test` 全部测试通过 | Task 3 Step 1-2 |
| 真机验证 | Task 3 Step 3 |
| import 语句检查 | Task 2 Step 2 |
| git 提交询问 | Task 4 |

### Placeholder 扫描

无 TBD/TODO/placeholder。所有代码块完整。✓

### 类型一致性

- `prevIdx`/`nextIdx`：Int（与 `draggedCurrentIndex` 一致）
- `neighborIdx`：Int（与原变量类型一致）
- `isDivider(displayItems[prevIdx])`：Boolean（与 `isDivider` 函数签名一致） ✓

### 后续可优化方向（来自 spec 第 7 节）

1. 算法语义文档化：在 `checkPinnedZoneCrossed` 顶部补充 KDoc
2. 属性测试：引入 Kotlin Property Testing 框架
3. 抽取 divider 类型：泛型化 divider 类型支持更精细区域判定
