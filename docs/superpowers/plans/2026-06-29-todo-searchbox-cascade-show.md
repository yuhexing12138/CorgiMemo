# 待办页搜索框级联显示优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 `LazyListState.isScrollInProgress` 替换 150ms 时间戳 debounce，让搜索框显隐动画在"缓慢下滑"和"上滑中途松手"两个场景下都按预期工作——前者呈现卷帘门效果，后者真正 snap 到端点。

**Architecture:** 单文件改动（`HomeScreen.kt`）。滚动驱动 LaunchedEffect 扩展为双向（滚上+滚下都即时跟随 progress）；新增 `LaunchedEffect(isScrolling)` 监听官方滚动状态，在真正停止时 snap 到端点；删除 150ms 时间戳 debounce 相关的 `lastScrollTimeMs` 状态、50ms 轮询 LaunchedEffect 和 `isAtTop` 触发 LaunchedEffect。

**Tech Stack:** Kotlin, Jetpack Compose (foundation.lazy, runtime, animation.core), Hilt

**相关 Spec:** [2026-06-29-todo-searchbox-cascade-show-design.md](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-29-todo-searchbox-cascade-show-design.md)

---

## File Structure

**仅修改 1 个文件**:
- `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` - 重构搜索框显隐动画（5 处编辑 + 1 处构建验证）

---

## Task 1: 添加 isScrolling 状态订阅

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:508` (在 `searchRevealProgress` 之后新增)

- [ ] **Step 1: 编辑文件，在第 508 行后新增 isScrolling 订阅**

在 `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` 中，定位到第 508 行附近：

```kotlin
/** 滚动驱动搜索框显隐进度：1f=完全显示，0f=完全隐藏，使用Animatable支持平滑动画 */
val searchRevealProgress = remember { Animatable(1f) }
```

在 `searchRevealProgress` 之后、`lastScrollTimeMs` 之前，新增一行订阅：

```kotlin
/** 滚动驱动搜索框显隐进度：1f=完全显示，0f=完全隐藏，使用Animatable支持平滑动画 */
val searchRevealProgress = remember { Animatable(1f) }

/** 订阅 LazyListState 滚动状态（含 fling + settle），Compose 官方统一管理，跨设备一致 */
val isScrolling = lazyListState.isScrollInProgress

/** 标记最后一次滚动事件的时间戳（毫秒），用于滚动停止检测的 debounce 判定 */
val lastScrollTimeMs = remember { mutableLongStateOf(0L) }
```

注：`lazyListState.isScrollInProgress` 返回 `Boolean`，在 Composable 中读取会自动订阅，值变化时触发重组。

- [ ] **Step 2: 构建项目验证编译**

运行：
```bash
cd c:/Users/EDY/Desktop/CorgiMemo; .\gradlew.bat compileDebugKotlin
```

预期：编译通过。注意：项目当前存在不相关的编译错误（见 `报错汇总.md`，涉及 PressFeedback.kt、ReorderableLazyColumn.kt、TodoListItem.kt 的 `launch`/`delay` 引用问题），这些是预先存在的，不应影响本任务的编译验证（如果 HomeScreen.kt 本身编译通过即可）。如果其他文件的错误导致整个 build 失败，可单独运行：

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; .\gradlew.bat :app:compileDebugKotlin --quiet 2>&1 | Select-String "HomeScreen"
```

只看 HomeScreen.kt 相关错误。

- [ ] **Step 3: 提交**

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt; git commit -m "feat(todo): 添加 isScrolling 状态订阅，为替换时间戳 debounce 做准备"
```

---

## Task 2: 改造滚动驱动 LaunchedEffect 支持双向（滚上 + 滚下）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:517-549` (滚动驱动 LaunchedEffect 内部逻辑)

- [ ] **Step 1: 替换滚动驱动 LaunchedEffect 的 if 分支**

在 `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` 中，定位到第 517-549 行的 `LaunchedEffect(lazyListState)`。找到这段代码（大约在 535-545 行）：

```kotlin
                            // 向上滑（远离顶部）→进度减少（隐藏），向下滑（未到顶）→不增加进度
                            if (!isDown && searchRevealProgress.value > 0f) {
                                val newProgress = (searchRevealProgress.value - delta / searchBarFullHeightPx).coerceIn(0f, 1f)
                                // 仅当进度实际变化时才更新，避免无效的 snapTo 调用
                                if (newProgress != searchRevealProgress.value) {
                                    searchRevealProgress.snapTo(newProgress)
                                }
                            }
                            // 记录滚动时间，供滚动停止检测使用
                            lastScrollTimeMs.longValue = System.currentTimeMillis()
```

替换为：

```kotlin
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
```

注：移除了 `lastScrollTimeMs.longValue = System.currentTimeMillis()` 这一行——后续任务中 `lastScrollTimeMs` 状态本身会被删除。

- [ ] **Step 2: 构建项目验证编译**

运行：
```bash
cd c:/Users/EDY/Desktop/CorgiMemo; .\gradlew.bat compileDebugKotlin
```

预期：HomeScreen.kt 编译通过。`lastScrollTimeMs` 暂时未使用（编译器会提示 unused variable warning），这是预期的，将在 Task 5 中删除。

- [ ] **Step 3: 提交**

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt; git commit -m "feat(todo): 滚动驱动 LaunchedEffect 支持滚下方向，进度双向跟随"
```

---

## Task 3: 新增 isScrollInProgress 滚动停止检测 LaunchedEffect

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:549` (在滚动驱动 LaunchedEffect 之后新增)

- [ ] **Step 1: 定位插入位置**

在 `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` 中，找到滚动驱动 LaunchedEffect 的结束位置（第 549 行附近，应该是 `}` 闭合括号）。在该闭合括号后、`isAtTop` derivedStateOf 之前，新增以下代码：

```kotlin

/**
 * 滚动真正停止时触发 snap：
 * - 直接订阅 lazyListState.isScrollInProgress，key 变化时 effect 自动重启
 * - 滚动开始（true）或滚动中（保持 true）→ body 内 if 判断跳过
 * - 滚动真正停止（false）→ 检查 progress，snap 到 0f 或 1f
 * - 使用 Compose 官方状态，准确捕获 fling + settle 结束，跨设备一致
 *
 * 设计动机：替换原 150ms 时间戳 debounce 方案
 * - 旧方案：fling 惯性滚动期间 firstVisibleItemIndex/Offset 持续发射微事件，
 *   每次都重置 lastScrollTimeMs，150ms 窗口永远不通过，progress 卡在半路
 * - 新方案：isScrollInProgress 由 Compose 框架统一维护 fling + settle 状态，
 *   真正停止时才变 false，effect 重启执行 snap
 */
LaunchedEffect(isScrolling) {
    if (!isScrolling && searchQuery.isBlank()) {
        val current = searchRevealProgress.value
        if (current > 0.05f && current < 0.95f) {
            val target = if (current < 0.5f) 0f else 1f
            val duration = if (target == 0f) 200 else 250
            searchRevealProgress.animateTo(
                targetValue = target,
                animationSpec = tween(duration, easing = FastOutSlowInEasing)
            )
        }
    }
}
```

- [ ] **Step 2: 构建项目验证编译**

运行：
```bash
cd c:/Users/EDY/Desktop/CorgiMemo; .\gradlew.bat compileDebugKotlin
```

预期：HomeScreen.kt 编译通过。新增的 LaunchedEffect 引用了 `isScrolling`（Task 1 已添加）、`searchRevealProgress`（已存在）、`searchQuery`（已存在）、`tween`/`FastOutSlowInEasing`（已 import）。`lastScrollTimeMs` 仍为 unused 状态。

- [ ] **Step 3: 提交**

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt; git commit -m "feat(todo): 新增 isScrollInProgress 滚动停止检测，替换时间戳 debounce"
```

---

## Task 4: 删除 isAtTop LaunchedEffect

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:589-596` (删除整个 LaunchedEffect(isAtTop) 块)

- [ ] **Step 1: 定位并删除 isAtTop LaunchedEffect**

在 `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` 中，定位到第 589-596 行（`isAtTop` 的 `LaunchedEffect`）。找到这段代码：

```kotlin

                    /** 到达列表顶部时，搜索框平滑展开显示（级联动画） */
                    LaunchedEffect(isAtTop) {
                        if (isAtTop && searchQuery.isBlank()) {
                            searchRevealProgress.animateTo(
                                1f,
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            )
                        }
                    }
```

整段删除（包括前面的注释和空行）。

注：`isAtTop` 的 `derivedStateOf`（第 581-586 行）保留——它可能仍被其他代码引用，且不影响逻辑。

- [ ] **Step 2: 构建项目验证编译**

运行：
```bash
cd c:/Users/EDY/Desktop/CorgiMemo; .\gradlew.bat compileDebugKotlin
```

预期：HomeScreen.kt 编译通过。`isAtTop` 变量若未在其他地方使用，编译器会提示 unused warning，可忽略。

- [ ] **Step 3: 提交**

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt; git commit -m "refactor(todo): 删除 isAtTop LaunchedEffect（滚下驱动已覆盖）"
```

---

## Task 5: 删除 lastScrollTimeMs 状态和 50ms 轮询 LaunchedEffect

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` 删除两处
  - 第 511 行：`val lastScrollTimeMs = remember { mutableLongStateOf(0L) }`
  - 第 552-579 行：50ms 轮询 LaunchedEffect 整段

- [ ] **Step 1: 删除 lastScrollTimeMs 状态声明**

在 `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` 中，定位到第 511 行附近。找到这段代码：

```kotlin

/** 标记最后一次滚动事件的时间戳（毫秒），用于滚动停止检测的 debounce 判定 */
val lastScrollTimeMs = remember { mutableLongStateOf(0L) }
```

整段删除（包括前面的注释行和空行）。

- [ ] **Step 2: 删除 50ms 轮询 LaunchedEffect**

在 `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` 中，定位到第 552-579 行（轮询 LaunchedEffect）。找到这段代码：

```kotlin

                    /**
                     * 滚动停止检测：
                     * - 距离上次滚动事件 ≥ 150ms 时，判定为"滚动已停止"
                     * - 若 progress 在中间区域 (0.05, 0.95)，snap 到最近的端点
                     *   - progress < 0.5 → 完全隐藏（0f），200ms 动画
                     *   - progress ≥ 0.5 → 完全显示（1f），250ms 动画
                     * - 端点附近（≤0.05 或 ≥0.95）→ 不动（避免无意义动画）
                     * - 轮询间隔 50ms，反应及时
                     */
                    LaunchedEffect(lazyListState, searchQuery) {
                        while (true) {
                            delay(50)
                            val now = System.currentTimeMillis()
                            val last = lastScrollTimeMs.longValue
                            if (last > 0 && now - last >= 150 && searchQuery.isBlank()) {
                                val current = searchRevealProgress.value
                                if (current > 0.05f && current < 0.95f) {
                                    val target = if (current < 0.5f) 0f else 1f
                                    val duration = if (target == 0f) 200 else 250
                                    searchRevealProgress.animateTo(
                                        targetValue = target,
                                        animationSpec = tween(duration, easing = FastOutSlowInEasing)
                                    )
                                }
                                // 标记本轮已处理，避免重复触发
                                lastScrollTimeMs.longValue = 0L
                            }
                        }
                    }
```

整段删除（包括前面的注释行和空行）。

- [ ] **Step 3: 清理未使用的 import**

检查文件顶部 import 区域。如果以下 import 不再被任何代码使用，删除它们：

- `import kotlinx.coroutines.delay` （检查全文是否还有 `delay` 调用）
- `import androidx.compose.runtime.mutableLongStateOf` （Task 5 Step 1 已删除唯一使用）

用 grep 工具检查：

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; Select-String -Path "app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt" -Pattern "\bdelay\b|\bmutableLongStateOf\b"
```

如果两者的引用次数都是 0，则可以删除对应 import 行。如果还有其他引用，保留。

- [ ] **Step 4: 构建项目验证编译**

运行：
```bash
cd c:/Users/EDY/Desktop/CorgiMemo; .\gradlew.bat compileDebugKotlin
```

预期：HomeScreen.kt 编译通过，无 unused variable/import warning。

- [ ] **Step 5: 提交**

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt; git commit -m "refactor(todo): 删除 lastScrollTimeMs 状态和 50ms 轮询 LaunchedEffect"
```

---

## Task 6: 完整功能验证

**Files:**
- Modify: 无（仅验证）

- [ ] **Step 1: 完整构建 APK**

运行：
```bash
cd c:/Users/EDY/Desktop/CorgiMemo; .\gradlew.bat assembleDebug
```

预期：如果项目其他文件的预存错误（`报错汇总.md` 中列出的 PressFeedback.kt、ReorderableLazyColumn.kt、TodoListItem.kt 的 `launch`/`delay` 引用问题）已修复，则构建成功。如果这些预存错误仍未修复，预期 build 失败——这与本任务无关，单独修复即可。

- [ ] **Step 2: 手动测试场景（基于 Spec §6.1）**

将 debug APK 安装到测试设备，逐项验证：

| 编号 | 场景 | 预期结果 | 验证 |
|------|------|---------|------|
| 1 | 向上滚动列表 | 搜索框按比例即时收缩，卷帘门效果 | ☐ |
| 2 | **向上滚动中途松手** | fling 结束后搜索框 snap 到 0f（修复用户截图问题）| ☐ |
| 3 | 滚动到一半时向下回滚一点 | 搜索框保持当前高度，停止后 snap 到完全显示 | ☐ |
| 4 | 缓慢向下滑 | 搜索框按比例即时展开（修复"突然显现"问题）| ☐ |
| 5 | 列表回顶 | 搜索框已通过滚下驱动到 1f，无需额外动画 | ☐ |
| 6 | 输入搜索词 | 搜索框 250ms 展开到完全显示 | ☐ |
| 7 | 清空搜索词（不在顶部） | 搜索词变化 effect 主动 snap 到端点 | ☐ |
| 8 | 清空搜索词（在顶部） | 搜索框保持完全显示 | ☐ |
| 9 | 列表 translationY 视差 | 始终随 progress 同步变化 | ☐ |
| 10 | 跨设备测试 | 低端机、高端机、平板上 fling 行为一致 | ☐ |

- [ ] **Step 3: 如果验证通过，提交最终标记**

如果上一步所有场景验证通过，无需修改代码，可选地创建一个空提交作为功能完成的标记：

```bash
cd c:/Users/EDY/Desktop/CorgiMemo; git commit --allow-empty -m "feat(todo): 搜索框级联显示优化完成 - 修复半路停下 + 突然显现"
```

如果验证发现问题，记录问题描述并修复后提交 fix commit。

---

## Self-Review Checklist

**Spec 覆盖度**：
- ✅ Spec §2.1 触发源 A（滚上）→ Task 2
- ✅ Spec §2.1 触发源 B（滚下）→ Task 2
- ✅ Spec §2.1 触发源 C（滚动真正停止）→ Task 3
- ✅ Spec §2.1 触发源 D（输入搜索词）→ 保持不变，无需任务
- ✅ Spec §2.1 触发源 E（清空搜索词）→ 保持不变，无需任务
- ✅ Spec §2.1 触发源 F（isAtTop 删除）→ Task 4
- ✅ Spec §3.5 保留不变部分 → 保持不变
- ✅ Spec §6.1 测试场景 → Task 6

**占位符扫描**：无 TBD/TODO/类似引用

**类型一致性**：
- `isScrolling` 在 Task 1 定义为 `val isScrolling = lazyListState.isScrollInProgress`（Boolean）
- Task 3 在 `LaunchedEffect(isScrolling)` 中使用，与定义一致
- Task 3 中 `if (!isScrolling && ...)` 使用 Boolean 运算，与定义一致

**依赖项一致性**：
- `tween`/`FastOutSlowInEasing` 在 Task 3 使用，已在文件顶部 import（无需新增）
- `searchRevealProgress`/`searchQuery` 在 Task 3 使用，已存在

**潜在风险**：
- 预存编译错误（PressFeedback.kt 等）不在本计划范围内，需另行修复
- 手动测试依赖真机/模拟器，工程师需具备相应环境
