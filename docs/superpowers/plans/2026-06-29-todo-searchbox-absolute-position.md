# 待办页搜索框级联显示 v2 实施计划 - 绝对位置驱动

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用绝对位置驱动（`scrollOffsetFromTop`）替换 v1 的 delta-based scroll-driven LaunchedEffect，彻底解决 overscroll 弹回引起的"搜索框无法隐藏 + 页面来回跳动"震荡 bug。

**Architecture:** 单文件改动（`HomeScreen.kt`）。删除 v1 的 `prevIdx/prevOff/isDown/delta` 局部状态和 51 行 scroll-driven LaunchedEffect；新增 `scrollOffsetFromTop` derivedStateOf（监听 `lazyListState.layoutInfo.visibleItemsInfo.first().offset`）和 absolute-position LaunchedEffect。保留 v1 的 `isScrolling` 决定性 snap、searchQuery 驱动、layout 测量和 translationY 视差。

**Tech Stack:** Kotlin, Jetpack Compose (foundation.lazy, runtime, animation.core)

**相关 Spec:** [2026-06-29-todo-searchbox-absolute-position-design.md](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-29-todo-searchbox-absolute-position-design.md)

---

## File Structure

**仅修改 1 个文件**:
- `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` - 替换 line 515-552 整段

---

## Task 1: 替换 scroll-driven LaunchedEffect 为 absolute-position 版本

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:515-552` (删除旧代码 + 新增新代码)

- [ ] **Step 1: 阅读当前代码确认范围**

读取 `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` 第 510-590 行，确认：
- `isScrolling` 订阅在 line 510（保留）
- 旧 scroll-driven LaunchedEffect 在 line 515-552（**待替换**）
- `LaunchedEffect(isScrolling)` 在 line 567-579（**保留**）
- `isAtTop` derivedStateOf 在 line 581-586（**保留**）

- [ ] **Step 2: 删除 line 515-552 整段**

在 `HomeScreen.kt` 中，定位到 line 515-552 之间的所有内容（从 `/** 滚动驱动：向上滑时... */` 注释开始，到 `}` 闭合 `LaunchedEffect(lazyListState)` 结束）。用 Edit 工具删除整段。

**整段删除的内容如下**（注意包含前导空行）：

```kotlin

                    /** 滚动驱动：向上滑时递减进度（snapTo保持与滚动同步），向下滑时不增加 */
                    LaunchedEffect(lazyListState) {
                        var prevIdx = 0
                        var prevOff = 0

                        snapshotFlow {
                            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
                        }.distinctUntilChanged().collect { (currentIndex, currentOffset) ->
                            if (searchQuery.isBlank()) {
                                val isDown = if (currentIndex != prevIdx) {
                                    currentIndex < prevIdx
                                } else {
                                    currentOffset < prevOff
                                }
                                val delta = if (currentIndex != prevIdx) {
                                    (abs(currentIndex - prevIdx) * searchBarFullHeightPx).coerceAtMost(searchBarFullHeightPx * 2)
                                } else {
                                    abs(currentOffset - prevOff).toFloat()
                                }
                                // 双向驱动：滚上减少 progress（隐藏），滚下增加 progress（显示），形成完整卷帘门对称
                                if (isDown && searchRevealProgress.value < 1f) {
                                    val newProgress = (searchRevealProgress.value + delta / searchBarFullHeightPx).coerceIn(0f, 1f)
                                    // 仅当进度实际变化时才更新，避免无效的 snapTo 调用
                                    if (newProgress != searchRevealProgress.value) {
                                        searchRevealProgress.snapTo(newProgress)
                                    }
                                } else if (!isDown && searchRevealProgress.value > 0f) {
                                    val newProgress = (searchRevealProgress.value - delta / searchBarFullHeightPx).coerceIn(0f, 1f)
                                    // 仅当进度实际变化时才更新，避免无效的 snapTo 调用
                                    if (newProgress != searchRevealProgress.value) {
                                        searchRevealProgress.snapTo(newProgress)
                                    }
                                }
                            }
                            prevIdx = currentIndex
                            prevOff = currentOffset
                        }
                    }
```

- [ ] **Step 3: 在原位置新增 absolute-position 代码**

在原 LaunchedEffect 删除后的位置（即 `isScrolling` 订阅 line 510 之后），新增以下代码块：

```kotlin

                    /**
                     * 绝对位置驱动搜索框进度：
                     * - 进度 = 1 - scrollOffsetFromTop / searchBarFullHeightPx
                     * - scrollOffsetFromTop 来自 lazyListState.layoutInfo.visibleItemsInfo.first().offset
                     * - 这是单调函数，overscroll 弹回不会引起震荡（firstItem.offset 反映内容真实位置）
                     *
                     * 设计动机（v2）：替换 v1 的 delta-based 方案
                     * - v1 问题：基于相邻两次事件的差值（prevIdx/prevOff）判断方向，overscroll 弹回时
                     *   currentOffset 增大被误判为"滚下"，与"滚上"分支冲突 → 震荡
                     * - v2 方案：直接读 LazyListLayoutInfo 的 firstItem.offset（绝对像素位置），
                     *   单调函数，overscroll 时 offset 不变 → 零震荡
                     */
                    val scrollOffsetFromTop by remember {
                        derivedStateOf {
                            val firstItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
                            if (firstItem != null) {
                                // firstItem.offset: item 顶部相对 LazyColumn 顶部的偏移（负值=已滚出上方）
                                // 取反得到"已向下滚动的距离"（正值=已滚下）
                                (-firstItem.offset).coerceAtLeast(0)
                            } else {
                                0
                            }
                        }
                    }

                    LaunchedEffect(scrollOffsetFromTop) {
                        val target = (1f - scrollOffsetFromTop / searchBarFullHeightPx).coerceIn(0f, 1f)
                        if (searchRevealProgress.value != target) {
                            searchRevealProgress.snapTo(target)
                        }
                    }
```

- [ ] **Step 4: 验证 imports**

读取 `HomeScreen.kt` 第 1-150 行的 import 区域，确认以下 import 存在：

- `androidx.compose.runtime.derivedStateOf`（必需，新增的 derivedStateOf 块使用）
- `androidx.compose.runtime.getValue`（必需，`by` 委托读取 `scrollOffsetFromTop`）
- `androidx.compose.runtime.remember`（必需，`remember { derivedStateOf {...} }` 使用）
- `androidx.compose.runtime.LaunchedEffect`（必需，新增的 LaunchedEffect 使用）

如果以上 import 中有任何缺失，从下面补充（注意 import 应该按字母顺序插入到正确位置）：

- `import androidx.compose.runtime.derivedStateOf`
- `import androidx.compose.runtime.getValue` （如果缺失）
- `import androidx.compose.runtime.remember` （如果缺失）

注意：`kotlinx.coroutines.launch` 和 `kotlinx.coroutines.delay` 在 v1 Task 5 已删除，本任务不需要。

用 grep 工具检查：

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; Select-String -Path "app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt" -Pattern "import androidx\.compose\.runtime\.(derivedStateOf|getValue|remember|LaunchedEffect)"
```

- [ ] **Step 5: 静态验证**

确认以下内容：
- `searchRevealProgress` 已存在（line 507）
- `searchBarFullHeightPx` 已存在（line 513）
- `lazyListState` 已存在
- `coerceAtLeast(0)` 和 `coerceIn(0f, 1f)` 边界保护已添加
- `if (searchRevealProgress.value != target)` 守卫避免无效 snapTo

- [ ] **Step 6: 构建项目验证编译**

运行：
```bash
cd c:/Users/EDY/Desktop/CorgiMemo; .\gradlew.bat compileDebugKotlin
```

预期：HomeScreen.kt 编译通过。如遇到 KSP daemon AccessDenied 预存问题（详见 `报错汇总.md`），用 `--no-daemon` 重试或重启 daemon 进程。重点关注 HomeScreen.kt 是否新增错误（预存错误应仅在其他文件）。

- [ ] **Step 7: 提交**

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt; git commit -m "refactor(todo): 搜索框进度改为绝对位置驱动，修复 v1 震荡 bug

- 删除 v1 delta-based scroll-driven LaunchedEffect（prevIdx/prevOff/isDown/delta）
- 新增 scrollOffsetFromTop derivedStateOf 监听 layoutInfo
- 新增 absolute-position LaunchedEffect（progress = 1 - scrollOffset/h）
- 保留 isScrolling 决定性 snap 保持 UX 一致"
```

---

## Task 2: 完整功能验证

**Files:**
- Modify: 无（仅验证）

- [ ] **Step 1: 完整构建 APK**

运行：
```bash
cd c:/Users/EDY/Desktop/CorgiMemo; .\gradlew.bat assembleDebug
```

预期：BUILD SUCCESSFUL（项目其他文件的预存错误应已被之前 commit 修复）

- [ ] **Step 2: 静态确认所有改动到位**

读取 `HomeScreen.kt` 验证：
- `isScrolling` 订阅仍在 line 510 附近
- 旧 `LaunchedEffect(lazyListState)` 已删除（grep 验证）
- 新 `scrollOffsetFromTop by remember { derivedStateOf {...} }` 存在
- 新 `LaunchedEffect(scrollOffsetFromTop) { ... }` 存在
- `LaunchedEffect(isScrolling)` 仍在 line 567-579
- `isAtTop` derivedStateOf 仍在 line 581-586
- `LaunchedEffect(searchQuery)` 仍在 line 597-616
- 搜索框 `Box` + `Modifier.layout` 仍在 line 624-636
- 列表 `translationY` 仍在 line 791-793

用 grep 工具批量验证：

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; Select-String -Path "app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt" -Pattern "prevIdx|prevOff|isDown|firstVisibleItemScrollOffset" | Where-Object { $_.Line -notmatch "^\s*\*" }
```

预期：无 `prevIdx`/`prevOff`/`isDown` 残留（`firstVisibleItemScrollOffset` 应仅在 spec 注释中或 import 中，不应在运行代码中）

- [ ] **Step 3: 手动测试场景（基于 v2 Spec §6.1）**

将 debug APK 安装到测试设备，逐项验证：

| 编号 | 场景 | 预期结果 | 验证 |
|------|------|---------|------|
| 1 | 向上滚动列表 | 搜索框按比例即时收缩，**无震荡** | ☐ |
| 2 | **向上滚动中途松手** | fling + overscroll 弹回后，搜索框 snap 到 0f（**修复 v1 bug**）| ☐ |
| 3 | 滚动到一半时向下回滚一点 | 搜索框跟随滚动位置变化，停止后决定性 snap | ☐ |
| 4 | 缓慢向下滑 | 搜索框按比例即时展开（卷帘门）| ☐ |
| 5 | 列表回顶 | 搜索框已通过 absolute-position 跟随到 1f | ☐ |
| 6 | **强 fling 触顶 overscroll** | **页面不跳动，搜索框稳定隐藏**（**修复 v1 bug**）| ☐ |
| 7 | 输入搜索词 | 搜索框 250ms 展开到完全显示 | ☐ |
| 8 | 清空搜索词（不在顶部） | 搜索词 effect 主动 snap 到端点 | ☐ |
| 9 | 清空搜索词（在顶部） | 搜索框保持完全显示 | ☐ |
| 10 | 列表 translationY 视差 | 始终随 progress 同步变化 | ☐ |
| 11 | 滚动到列表底部 | progress 持续 0f，搜索框完全隐藏 | ☐ |
| 12 | 跨设备测试 | 低端机、高端机、平板上 fling + overscroll 行为一致 | ☐ |

- [ ] **Step 4: 如果验证通过，创建最终标记**

如果上一步所有场景验证通过，创建一个空提交作为 v2 功能完成的标记：

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; git commit --allow-empty -m "feat(todo): 搜索框级联显示 v2 完成 - 绝对位置驱动修复震荡 bug"
```

如果验证发现问题，记录问题描述并修复后提交 fix commit。

---

## Self-Review Checklist

**Spec 覆盖度**：
- ✅ Spec §2.1 触发源 A（绝对位置）→ Task 1 Step 3
- ✅ Spec §2.1 触发源 B（isScrolling snap）→ 保留（v1 Task 3 已添加，无需操作）
- ✅ Spec §2.1 触发源 C（输入搜索词）→ 保留（v1 已存在）
- ✅ Spec §2.1 触发源 D（清空搜索词）→ 保留（v1 已存在）
- ✅ Spec §3.1（删除 v1）→ Task 1 Step 2
- ✅ Spec §3.2（新增 absolute-position）→ Task 1 Step 3
- ✅ Spec §3.3（保留 isScrolling）→ 保留
- ✅ Spec §3.4（保留 searchQuery）→ 保留
- ✅ Spec §3.5（保留其他）→ 保留
- ✅ Spec §6.1 测试场景 → Task 2

**占位符扫描**：无 TBD/TODO/类似引用

**类型一致性**：
- `scrollOffsetFromTop` 在 Task 1 Step 3 定义为 `val scrollOffsetFromTop by remember { derivedStateOf {...} }`（Float via property delegate）
- Task 1 Step 3 在 `LaunchedEffect(scrollOffsetFromTop)` 中使用，与定义一致
- `LaunchedEffect(scrollOffsetFromTop)` 接受 Float 作为 key，与定义一致

**依赖项一致性**：
- `derivedStateOf` import → 已 import（v1 已有 isAtTop 使用）
- `getValue` import → 已 import（v1 Task 1 已添加）
- `remember` import → 已 import
- `LaunchedEffect` import → 已 import

**潜在风险**：
- 预存编译错误（PressFeedback.kt 等）不在本计划范围内，已被之前 commit 修复
- 手动测试依赖真机/模拟器，工程师需具备相应环境
