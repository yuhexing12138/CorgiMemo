# 待办页搜索框级联显示优化设计

- **日期**：2026-06-29
- **范围**：仅待办页（`HomeScreen.kt`）的搜索框显示/隐藏动画
- **目标**：
  1. 搜索框的"显示过程"与"隐藏过程"一致——均采用"滚动驱动 + 即时跟随"级联效果（卷帘门效果）
  2. 修复"上滑后搜索框停在半路位置"——滚动真正停止时必须 snap 到端点

---

## 1. 背景与目标

### 1.1 现状

[HomeScreen.kt](app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) 第 505-680 行实现了搜索框的显隐动画，关键组件：

- `searchRevealProgress: Animatable<Float>`：1f = 完全显示，0f = 完全隐藏
- 滚动驱动的 `snapTo` 同步**隐藏**逻辑（只响应向上滑，517-549 行）
- 搜索词变化驱动的 `animateTo(1f, 250ms)` 显示逻辑（607-626 行）
- 列表回顶驱动的 `animateTo(1f, 250ms)` 显示逻辑（589-596 行）
- **150ms 时间戳 debounce + 50ms 轮询**的滚动停止检测（560-579 行）
- 高度随进度的 `Modifier.layout` 自定义测量（634-647 行）
- 透明度随进度的 `graphicsLayer` alpha（646 行）
- 列表 `translationY` 视差（791-793 行）

### 1.2 问题

| 编号 | 触发场景 | 当前实现 | 问题 |
|------|---------|---------|------|
| A | 缓慢向下滑 | progress 保持 0，依赖 `isAtTop` 触发的 250ms 补间 | ❌ 显示是固定时长补间，"突然显现"，无"卷帘门"感 |
| B | 上滑中途松手 | 时间戳 debounce 期望 150ms 无事件后 snap | ❌ LazyColumn 的 fling + settle 持续发射微事件，时间戳被持续重置，**进度卡在半路**（用户截图证实） |

### 1.3 目标

- ✅ **滚上**与**滚下**都驱动 progress 实时变化（`snapTo` 即时跟随），形成完整"卷帘门"对称效果
- ✅ 滚动真正停止时（fling + settle 全部结束）snap 到 0f 或 1f，不停在中间
- ✅ 跨设备/平台一致：使用 Compose 官方 `isScrollInProgress`，不依赖本地时序阈值
- ✅ 保留 250ms / 200ms 动画时长
- ✅ 保留 0.12f 视差系数
- ✅ 保留 `searchBarFullHeightPx = 64.dp` 高度基准

---

## 2. 核心算法

### 2.1 触发源与对应动作

| 编号 | 触发源 | 条件 | 动作 | 动画规格 |
|------|--------|------|------|----------|
| A | 向上滚动 | `searchQuery.isBlank()` | `progress -= delta/h` | `snapTo`（即时） |
| B | 向下滚动 | `searchQuery.isBlank()` 且 progress < 1f | `progress += delta/h` | `snapTo`（即时） |
| C | 滚动真正停止 | `isScrollInProgress` 由 true 变 false，且 `searchQuery.isBlank()` | progress 在 (0.05, 0.95) 时 snap 到端点 | `animateTo` 200ms / 250ms |
| D | 输入搜索词 | `searchQuery` 从 blank 变非 blank | `animateTo(1f, 250ms)` | tween 250ms |
| E | 清空搜索词 | `searchQuery` 从非 blank 变 blank | 基于 progress 主动 snap 到端点 | tween 200ms / 250ms |
| F | ~~列表回顶~~ | ~~isAtTop 变 true~~ | ~~animateTo(1f, 250ms)~~ | **删除**（滚下驱动已覆盖） |

### 2.2 关键不变量

- `progress ∈ [0, 1]`
- 任何"非滚动驱动"的动作（D/E）结束后，`progress` 必须为精确的 0f 或 1f
- 滚动驱动（A/B）允许 progress 为中间值，但触发 C 保证最终态回到端点
- 永远不显示"半个搜索框"——progress 不会长时间停留在 (0.05, 0.95) 区间

### 2.3 核心改进：用 `isScrollInProgress` 替代时间戳 debounce

**旧实现问题**：
```kotlin
// 旧：时间戳 + 50ms 轮询
val lastScrollTimeMs = remember { mutableLongStateOf(0L) }
// 每次 scroll 事件都重置 lastScrollTimeMs
// 轮询每 50ms 检查 now - last >= 150
```

LazyColumn 的 fling 惯性滚动期间会持续发射 `firstVisibleItemIndex/Offset` 变化事件（每帧 1 个 ≈ 16ms），settle 阶段也有微事件。每次事件都重置 `lastScrollTimeMs`，导致 150ms 窗口永远不通过，**progress 永远卡在半路**（与用户截图现象一致）。

**新实现**：
```kotlin
// 新：直接订阅 LazyListState.isScrollInProgress
// isScrollInProgress 是 State<Boolean>，需用 .value 或 by 委托拿到实际 Boolean
val isScrolling by lazyListState.isScrollInProgress  // Boolean（property delegate）
LaunchedEffect(isScrolling) {  // Boolean 作为 key，value 变化时 effect 自动重启
    if (!isScrolling && searchQuery.isBlank()) {
        // 滚动真正停止时（Compose 已统一处理 fling + settle），snap 到端点
        ...
    }
}
```

- `isScrollInProgress` 由 Compose 框架维护，准确反映"是否在滚动（含 fling + settle）"
- 使用 `by` 委托订阅 `State<Boolean>`，key 变化时 effect 自动重启
- 不需要轮询循环，无 CPU 浪费
- 不依赖本地时序阈值，跨设备/平台一致
- **依赖 import**：`androidx.compose.runtime.getValue`（用于 `by` 委托）

---

## 3. 实现细节

### 3.1 改造滚动驱动 LaunchedEffect（同时支持滚上/滚下）

替换 513-543 行的 `LaunchedEffect(lazyListState)`：

```kotlin
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
                (abs(currentIndex - prevIdx) * searchBarFullHeightPx)
                    .coerceAtMost(searchBarFullHeightPx * 2)
            } else {
                abs(currentOffset - prevOff).toFloat()
            }
            // 双向驱动：滚上减少 progress，滚下增加 progress
            if (isDown && searchRevealProgress.value < 1f) {
                val newProgress = (searchRevealProgress.value + delta / searchBarFullHeightPx)
                    .coerceIn(0f, 1f)
                if (newProgress != searchRevealProgress.value) {
                    searchRevealProgress.snapTo(newProgress)
                }
            } else if (!isDown && searchRevealProgress.value > 0f) {
                val newProgress = (searchRevealProgress.value - delta / searchBarFullHeightPx)
                    .coerceIn(0f, 1f)
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

### 3.2 替换滚动停止检测：用 isScrollInProgress

**删除**：50ms 轮询 `LaunchedEffect(lazyListState, searchQuery)`（521-538 行整段删除）
**删除**：`val lastScrollTimeMs = remember { mutableLongStateOf(0L) }`（506 行删除）

**新增**：在滚动驱动 LaunchedEffect 之后：

```kotlin
/**
 * 滚动真正停止时触发 snap：
 * - 直接订阅 lazyListState.isScrollInProgress，key 变化时 effect 自动重启
 * - 滚动开始（true）或滚动中（保持 true）→ body 内 if 判断跳过
 * - 滚动真正停止（false）→ 检查 progress，snap 到 0f 或 1f
 * - 使用 Compose 官方状态，准确捕获 fling + settle 结束，跨设备一致
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

### 3.3 删除列表回顶驱动 LaunchedEffect

**删除**：552-560 行的 `LaunchedEffect(isAtTop)`，因为滚下已驱动 progress 到 1f。

**保留**：`isAtTop` 的 `derivedStateOf`（545-550 行），因为代码中可能其他地方也使用。

### 3.4 搜索词驱动 LaunchedEffect（保持不变）

```kotlin
LaunchedEffect(searchQuery) {
    if (searchQuery.isNotBlank()) {
        searchRevealProgress.animateTo(
            1f,
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        )
    } else {
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

### 3.5 保留不变的部分

- 搜索框 `Box` + `Modifier.layout` 高度收缩（634-647 行）
- 列表 `translationY` 视差（791-793 行）
- `searchBarFullHeightPx` 高度基准（514 行）
- 搜索框渲染（648-672 行）
- `isAtTop` derivedStateOf（581-586 行，可能仍被引用）

---

## 4. 边界情况处理

| 场景 | 行为 |
|------|------|
| 上滑中途松手，fling 继续 | 滚动驱动 LaunchedEffect 持续响应 snapshotFlow 发射，progress 持续减少 |
| Fling + settle 真正结束 | `isScrollInProgress` 变 false，LaunchedEffect 重启，body 执行 snap |
| 缓慢向下滑 | snapshotFlow 每次发射，滚下分支增加 progress，实时跟随 |
| 滚下中途松手，fling 继续 | progress 持续增加（fling 方向通常是远离起手点） |
| 滚下到顶部后松手 | progress 应已接近 1f；isScrollInProgress 变 false 时若 progress ≥ 0.95 则不触发动画 |
| progress 已在端点附近 | `if (current > 0.05f && current < 0.95f)` 守卫避免无意义 snap |
| 滚动期间输入搜索词 | 滚动驱动的 `if (searchQuery.isBlank())` 守卫让滚动不再影响 progress；输入触发 `animateTo(1f)` |
| 输入搜索词时滚动 | 滚动驱动的守卫让滚动不影响 progress；isScrolling effect 在 searchQuery 非 blank 时不执行 snap |
| LazyColumn 重组/页面销毁 | `LaunchedEffect(isScrolling)` 自动取消协程，无泄漏 |
| 首次进入页面，progress 初值 1f | 不触发任何动画（isScrolling 初始为 false，但 progress 已是 1f，守卫跳过） |
| 跨设备 fling 时长差异 | 由 Compose 框架统一管理 `isScrollInProgress`，不依赖本地时序阈值 |

---

## 5. 错误处理

- `snapshotFlow` 抛出异常时，Compose 协程会被取消并自动重启 LaunchedEffect
- `animateTo` 期间被新的 `animateTo` / `snapTo` 打断：Compose Animatable 内部处理，自动取消当前动画启动新动画
- 进度计算溢出：`coerceIn(0f, 1f)` 边界保护

---

## 6. 测试计划

### 6.1 自测清单

| 编号 | 场景 | 预期 |
|------|------|------|
| 1 | 向上滚动列表 | 搜索框按比例即时收缩 |
| 2 | **向上滚动中途松手** | fling 结束 + isScrollInProgress 变 false 后，搜索框 snap 到 0f（**修复用户截图问题**）|
| 3 | 滚动到一半时向下回滚一点 | 搜索框保持当前高度，isScrollInProgress 变 false 后 snap 到完全显示 |
| 4 | 缓慢向下滑 | 搜索框按比例即时展开（卷帘门效果，**修复"突然显现"问题**）|
| 5 | 列表回顶 | 搜索框已通过滚下驱动到 1f，无需额外动画 |
| 6 | 输入搜索词 | 搜索框 250ms 展开到完全显示 |
| 7 | 清空搜索词（不在顶部） | 搜索词变化 effect 主动 snap 到端点 |
| 8 | 清空搜索词（在顶部） | 搜索框保持完全显示 |
| 9 | 列表 translationY 视差 | 始终随 progress 同步变化 |
| 10 | 搜索框可见状态下打开键盘 | 搜索框保持完全显示（不被键盘遮挡）|

### 6.2 回归测试

- 滚动性能：长列表快速滚动时帧率无明显下降
- 搜索功能：输入搜索词后过滤逻辑正常工作
- 批量模式：进入批量模式后搜索框依然正常工作
- 节日卡片：节气息屏卡片显示时搜索框依然正常工作
- 下拉刷新：触发刷新时不影响搜索框动画
- 多设备测试：低端机 / 高端机 / 平板 fling 行为差异

---

## 7. 不在范围内

- 不修改 ViewModel（[HomeViewModel.kt](app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt)）
- 不修改 SearchBar 组件（[SearchBar.kt](app/src/main/java/com/corgimemo/app/ui/components/SearchBar.kt)）
- 不修改其他页面的搜索框（灵感页、日期页等）
- 不修改搜索框高度基准值 `searchBarFullHeightPx`
- 不修改 200ms / 250ms 动画时长
- 不修改 0.12f 视差系数

---

## 8. 文件改动汇总

**仅 1 个文件**：[HomeScreen.kt](app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt)

| 位置 | 改动 | 行数变化 |
|------|------|----------|
| 滚动驱动 LaunchedEffect | 在 517-549 行基础上新增滚下分支 | +9 |
| 删除 50ms 轮询 LaunchedEffect | 删除 560-579 行（含 lastScrollTimeMs 引用）| -22 |
| 删除 `lastScrollTimeMs` 状态 | 删除 511 行 | -1 |
| 新增 isScrollInProgress effect | 在滚动驱动之后新增 17 行 | +17 |
| 删除 `LaunchedEffect(isAtTop)` | 删除 589-596 行 | -9 |
| **合计** | | **-6 行** |

---

## 9. 关键决策记录

| 决策 | 原因 |
|------|------|
| 滚上/滚下都驱动 progress（变体 A） | 与隐藏过程"渐进跟随"感完全一致；停止后"完成"动作符合用户要求 |
| 用 `isScrollInProgress` 替代时间戳 debounce | 解决 fling+settle 期间时间戳被持续重置的 bug；跨设备一致 |
| 删除 isAtTop 触发 | 滚下已驱动 progress 到 1f，避免双动画冲突 |
| 删除 lastScrollTimeMs 状态 | 不再需要时间戳追踪 |
| 删除 50ms 轮询 LaunchedEffect | 由 isScrolling effect 替代，CPU 占用更低 |
| 阈值 0.5 | 符合直觉：低于 50% 视为隐藏意图，高于 50% 视为显示意图 |
| 端点容差 0.05/0.95 | 避免 progress 已在端点附近时做无意义的 snap 动画 |
| 保留输入搜索词的 animateTo(1f, 250ms) | 显式用户意图必须立即响应，不能等滚动驱动 |
