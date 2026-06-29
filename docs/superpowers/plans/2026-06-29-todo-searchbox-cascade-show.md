# 待办页搜索框显示过程级联效果改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将待办页搜索框的"显示过程"改造为与"隐藏过程"一致的"滚动驱动 + 即时跟随"级联效果，并保证搜索框最终态必须 snap 到完全显示（1f）或完全隐藏（0f）。

**Architecture:** 在 `HomeScreen.kt` 内新增滚动停止检测 LaunchedEffect（基于 debounce 时间戳），把搜索框"显示"和"隐藏"统一到同一套滚动驱动机制上。搜索词驱动和回顶驱动作为快速入口继续保留。整体改动约 38 行新增，无新文件、无新依赖。

**Tech Stack:** Kotlin 2.x + Jetpack Compose（`Animatable` / `LaunchedEffect` / `snapshotFlow` / `LazyListState`）

**测试策略:** 项目无现有 androidTest UI 测试基础设施（已确认 `app/src/androidTest/` 不存在），按 YAGNI 原则本次不引入 ComposeTestRule。验证方式：编译验证 + 手动测试清单（按设计文档 6.1 节执行）。

---

## 文件改动结构

| 文件 | 责任 |
|------|------|
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 修改：搜索框显隐动画 4 处改动 |

无新增文件、无新增依赖。

---

## Task 1: 新增 `lastScrollTimeMs` 状态

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:506-512`

- [ ] **Step 1: 在 `searchBarFullHeightPx` 声明之前新增 `lastScrollTimeMs` 状态**

打开 `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`，定位到第 506-510 行：

```kotlin
                    /** 滚动驱动搜索框显隐进度：1f=完全显示，0f=完全隐藏，使用Animatable支持平滑动画 */
                    val searchRevealProgress = remember { Animatable(1f) }

                    /** 搜索框完整高度（含padding），作为滚动→进度映射的基准 */
                    val searchBarFullHeightPx = with(LocalDensity.current) { 64.dp.toPx() }
```

在 `searchRevealProgress` 声明之后、`searchBarFullHeightPx` 声明之前，新增以下代码：

```kotlin
                    /** 标记最后一次滚动事件的时间戳（毫秒），用于滚动停止检测的 debounce 判定 */
                    val lastScrollTimeMs = remember { mutableLongStateOf(0L) }
```

- [ ] **Step 2: 确认 `mutableLongStateOf` 已在 import 中**

打开文件顶部 import 区，确认已存在：

```kotlin
import androidx.compose.runtime.mutableLongStateOf
```

如果不存在，在合适的 import 位置新增这一行。

- [ ] **Step 3: 编译验证**

按工作区规则"编译验证.md"，先询问用户是否进行编译。

预期：编译通过，无错误。如果报错 `Unresolved reference: mutableLongStateOf`，回到 Step 2 补 import。

---

## Task 2: 改造滚动驱动 LaunchedEffect（写入 `lastScrollTimeMs`）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:513-543`

- [ ] **Step 1: 在滚动驱动 LaunchedEffect 的 collect 块末尾写入时间戳**

定位到 513-543 行的 `LaunchedEffect(lazyListState)`，找到 `prevIdx = currentIndex` 之前的 `}` 之前（即 collect lambda 内部、`prevIdx = currentIndex` 之前），新增一行：

```kotlin
            // 记录滚动时间，供滚动停止检测使用
            lastScrollTimeMs.longValue = System.currentTimeMillis()
```

完整改动后的代码块（仅 collect lambda 内部）：

```kotlin
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
        }
        prevIdx = currentIndex
        prevOff = currentOffset
    }
```

- [ ] **Step 2: 编译验证**

按工作区规则"编译验证.md"，先询问用户是否进行编译。

预期：编译通过。如果报错 `Unresolved reference: longValue`，检查 `mutableLongStateOf` 的 import（Task 1 Step 2 已处理）。

---

## Task 3: 新增滚动停止检测 LaunchedEffect

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`（在 Task 2 改动的滚动驱动 LaunchedEffect 之后、`isAtTop` derivedStateOf 之前）

- [ ] **Step 1: 在滚动驱动 LaunchedEffect 之后新增滚动停止检测 LaunchedEffect**

定位到 Task 2 改动的滚动驱动 LaunchedEffect 结束位置（`val isAtTop by remember { ... }` 之前），新增以下完整代码块：

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

- [ ] **Step 2: 确认 `delay` 已在 import 中**

打开文件顶部 import 区，确认已存在：

```kotlin
import kotlinx.coroutines.delay
```

如果不存在，新增这一行。

- [ ] **Step 3: 编译验证**

按工作区规则"编译验证.md"，先询问用户是否进行编译。

预期：编译通过。如果报错 `Unresolved reference: delay`，回到 Step 2 补 import。

---

## Task 4: 重写搜索词驱动 LaunchedEffect

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:562-575`

- [ ] **Step 1: 替换搜索词驱动 LaunchedEffect 的完整代码块**

定位到第 562-575 行的 `LaunchedEffect(searchQuery)`，将整个代码块替换为：

```kotlin
                    /**
                     * 搜索词变化时驱动搜索框：
                     * - 输入搜索词（blank → 非 blank）→ 立即显示（250ms 展开）
                     * - 清空搜索词（非 blank → blank）→ 基于当前 progress 主动 snap 到端点
                     *
                     * 原实现使用 isAtTop 判断"清空时是否隐藏"，但 isAtTop 与搜索框实际
                     * 状态（progress）可能不一致（如滚动到中部时 progress=0.3 但 isAtTop=false）。
                     * 改为基于 progress 判定，逻辑统一且与滚动停止检测一致。
                     */
                    LaunchedEffect(searchQuery) {
                        if (searchQuery.isNotBlank()) {
                            // 输入搜索词：立即显示
                            searchRevealProgress.animateTo(
                                1f,
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            )
                        } else {
                            // 清空搜索词：基于当前 progress 主动 snap 到端点
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

- [ ] **Step 2: 编译验证**

按工作区规则"编译验证.md"，先询问用户是否进行编译。

预期：编译通过。

---

## Task 5: 手动测试清单验证

**Files:** 无代码改动

- [ ] **Step 1: 启动 App 进入待办页**

构建并运行 App，导航到待办页（HomeScreen）。

- [ ] **Step 2: 执行设计文档 6.1 节自测清单**

按以下清单逐项验证：

| 编号 | 操作 | 预期 |
|------|------|------|
| 1 | 向上滚动列表 | 搜索框按比例即时收缩 |
| 2 | 向上滚动中途停止 | 150ms 后搜索框自动完成到完全隐藏 |
| 3 | 滚动到一半时向下回滚一点 | 搜索框保持当前高度，停止 150ms 后 snap 到完全显示 |
| 4 | 滚动到顶部 | 搜索框 250ms 展开到完全显示 |
| 5 | 点击搜索框输入搜索词 | 搜索框 250ms 展开到完全显示 |
| 6 | 清空搜索词（不在顶部） | 搜索框 200ms 收缩到完全隐藏 |
| 7 | 清空搜索词（在顶部） | 搜索框保持完全显示 |
| 8 | 任意滚动 | 列表 translationY 视差随搜索框同步变化 |
| 9 | 搜索框可见状态下打开键盘 | 搜索框保持完全显示（不被键盘遮挡） |

- [ ] **Step 3: 执行设计文档 6.2 节回归测试**

- 滚动性能：长列表快速滚动时帧率无明显下降
- 搜索功能：输入搜索词后过滤逻辑正常工作
- 批量模式：进入批量模式后搜索框依然正常工作
- 节日卡片：节气息屏卡片显示时搜索框依然正常工作

- [ ] **Step 4: 记录测试结果**

将每项测试结果（通过/失败 + 备注）记录下来。如有失败，回到对应 Task 修复。

---

## Task 6: 询问用户是否进行 git 提交

**Files:** 无代码改动

- [ ] **Step 1: 按工作区规则"git提交.md"询问用户是否提交**

使用 AskUserQuestion 询问用户。如用户选择提交，使用以下中文提交信息：

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat: 待办页搜索框显示过程改造为级联效果

- 新增滚动停止检测 LaunchedEffect，基于 150ms debounce 把搜索框
  snap 到完全显示或完全隐藏
- 改造搜索词驱动 LaunchedEffect，清空时基于 progress 主动 snap
  到端点（替代原 isAtTop 判断，与滚动停止检测逻辑一致）
- 滚动驱动 LaunchedEffect 增加 lastScrollTimeMs 写入

保证搜索框最终态必须 snap 到 0f 或 1f，永不显示半个搜索框。
动画规格：250ms 显示 / 200ms 隐藏 / 0.12f 视差系数 保持不变。"
```

---

## 验收标准

- [ ] Task 1-4 全部完成
- [ ] 编译通过，无 Lint 警告
- [ ] Task 5 全部测试用例通过
- [ ] Task 6 git 提交完成（如用户同意）

---

## 风险与回退

| 风险 | 影响 | 回退方案 |
|------|------|----------|
| 50ms 轮询造成 CPU 占用上升 | 轻微 | 调大轮询间隔到 100ms（已记录在设计文档决策表中） |
| debounce 150ms 与某些极端慢滑冲突 | 搜索框可能不 snap | 调大 debounce 到 200ms |
| 滚动停止检测与 animateTo 冲突 | 视觉跳动 | 当前设计中已用 50ms 轮询间隙自然衔接，实测中如有问题再调 |

如出现严重问题，直接 `git revert` 恢复即可。
