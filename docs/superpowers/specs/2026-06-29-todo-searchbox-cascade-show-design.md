# 待办页搜索框显示过程级联效果改造设计

- **日期**：2026-06-29
- **范围**：仅待办页（`HomeScreen.kt`）的搜索框显示/隐藏动画
- **目标**：将搜索框的"显示过程"改造为与"隐藏过程"一致的"滚动驱动 + 即时跟随"级联效果

---

## 1. 背景与目标

### 1.1 现状

[HomeScreen.kt](app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) 第 506-622 行实现了搜索框的显隐动画，关键组件：

- `searchRevealProgress: Animatable<Float>`：1f = 完全显示，0f = 完全隐藏
- 滚动驱动的 `snapTo` 同步隐藏逻辑（513-543 行）
- 搜索词变化驱动的 `animateTo(1f, 250ms)` 显示逻辑（562-575 行）
- 列表回顶驱动的 `animateTo(1f, 250ms)` 显示逻辑（552-560 行）
- 高度随进度的 `Modifier.layout` 自定义测量（585-593 行）
- 透明度随进度的 `graphicsLayer` alpha（595 行）
- 列表 `translationY` 视差（738-742 行）

### 1.2 问题

| 触发场景 | 当前实现 | 问题 |
|---------|---------|------|
| 向上滚动 | `snapTo` 同步跟随滚动距离 | ✅ 隐藏有"渐进跟随"感 |
| 列表回顶 | `animateTo(1f, 250ms)` | ❌ 显示是固定时长补间，没有"渐进"感 |
| 输入搜索词 | `animateTo(1f, 250ms)` | ❌ 同上 |

显示过程是固定时长补间动画，与隐藏过程的"滚动驱动 + 即时跟随"不一致，缺乏级联感。

### 1.3 目标

- ✅ 显示过程与隐藏过程一样由滚动驱动（`snapTo` 同步）
- ✅ 滚动停止后搜索框 snap 到完全显示（1f）或完全隐藏（0f），不能停在中间
- ✅ 保留 250ms / 200ms 动画时长
- ✅ 保留 0.12f 视差系数
- ✅ 保留 `searchBarFullHeightPx = 64.dp` 高度基准

---

## 2. 核心算法

### 2.1 触发源与对应动作

| 编号 | 触发源 | 条件 | 动作 | 动画规格 |
|------|--------|------|------|----------|
| A | 向上滚动 | `searchQuery.isBlank()` | `progress -= 滚动量 / searchBarFullHeightPx`，限制到 [0, 1] | `snapTo`（即时） |
| B | 向下滚动 | `searchQuery.isBlank()` 且 progress < 1f | `progress` 保持不变 | `snapTo`（保持） |
| C | 滚动停止检测 | 距离上次滚动 ≥ 150ms | 若 `0.05 < progress < 0.95`：`progress < 0.5` → 隐藏；`progress ≥ 0.5` → 显示；端点附近 → 不动 | `animateTo` 200ms / 250ms |
| D | 输入搜索词 | `searchQuery` 从 blank 变非 blank | `animateTo(1f, 250ms)` | tween 250ms |
| E | 清空搜索词 | `searchQuery` 从非 blank 变 blank | 由触发 C 接管（删除原"清空时立即 animateTo"分支） | 由 C 决定 |
| F | 列表回顶 | `isAtTop` 变 true 且 `searchQuery.isBlank()` | `animateTo(1f, 250ms)` | tween 250ms |

### 2.2 关键不变量

- `progress ∈ [0, 1]`
- 任何"非滚动驱动"的动作（D/F）结束后，`progress` 必须为精确的 0f 或 1f
- 滚动驱动（A/B）允许 progress 为中间值，但紧接着的 debounce 触发 C 保证最终态回到端点
- 永远不显示"半个搜索框"——progress 不会长时间停留在 (0.05, 0.95) 区间

---

## 3. 实现细节

### 3.1 新增状态

在 `HomeScreen` Composable 内添加：

```kotlin
// 标记最后一次滚动事件的时间戳（毫秒），用于 debounce 判定滚动停止
val lastScrollTimeMs = remember { mutableLongStateOf(0L) }
```

### 3.2 改造滚动驱动 LaunchedEffect

替换 513-543 行的 `LaunchedEffect(lazyListState)`，增加 `lastScrollTimeMs` 写入：

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
            if (!isDown && searchRevealProgress.value > 0f) {
                val newProgress = (searchRevealProgress.value - delta / searchBarFullHeightPx)
                    .coerceIn(0f, 1f)
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
}
```

### 3.3 新增滚动停止检测 LaunchedEffect

在滚动驱动 LaunchedEffect 之后新增：

```kotlin
/**
 * 滚动停止检测：
 * - 距离上次滚动事件 ≥ 150ms 时，判定为"滚动已停止"
 * - 若 progress 在中间区域 (0.05, 0.95)，snap 到最近的端点
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

### 3.4 搜索词驱动 LaunchedEffect

替换 562-575 行的 `LaunchedEffect(searchQuery)`，**删除原"清空时基于 isAtTop 判断"的分支，改为基于当前 progress 主动 snap 到端点**（与 C 判定逻辑一致）：

```kotlin
/**
 * 搜索词变化时驱动搜索框：
 * - 输入搜索词（blank → 非 blank）→ 立即显示
 * - 清空搜索词（非 blank → blank）→ 基于当前 progress 主动 snap 到端点
 *
 * 原实现使用 isAtTop 判断"清空时是否隐藏"，但 isAtTop 与搜索框实际
 * 状态（progress）可能不一致（如滚动到中部时 progress=0.3 但 isAtTop=false）。
 * 改为基于 progress 判定，逻辑统一且与 C 一致。
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

### 3.5 回顶驱动 LaunchedEffect

552-560 行保持不变：

```kotlin
LaunchedEffect(isAtTop) {
    if (isAtTop && searchQuery.isBlank()) {
        searchRevealProgress.animateTo(
            1f,
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        )
    }
}
```

### 3.6 保留不变的部分

- 搜索框 `Box` + `Modifier.layout` 高度收缩（582-595 行）
- 列表 `translationY` 视差（738-742 行）
- `searchBarFullHeightPx` 高度基准（510 行）
- 搜索框渲染（597-621 行）
- `isAtTop` derivedStateOf（545-550 行）

---

## 4. 边界情况处理

| 场景 | 行为 |
|------|------|
| 滚动期间 progress 已到达 0f | 继续滚动但 progress 保持 0f（`coerceIn` 边界保护） |
| 滚动期间 progress 已到达 1f（向下滚） | 保持 1f（`isDown` 时不调用 `snapTo`） |
| 快速连续滚动 + 突然停止 | debounce 在最后停止点 150ms 后触发 snap |
| 滚动过程中输入搜索词 | 滚动驱动的 `if (searchQuery.isBlank())` 守卫让滚动不再影响 progress；输入触发 `animateTo(1f)` |
| 滚动停止检测与 animateTo 冲突 | 检测 LaunchedEffect 在 50ms 轮询间隙与 animateTo 自然衔接（animateTo 期间 `searchRevealProgress.value` 在变，debounce 检测后会基于新值判断） |
| LazyColumn 重组/页面销毁 | `LaunchedEffect(lazyListState)` 自动取消协程，无泄漏 |
| 首次进入页面，progress 初值 1f，`lastScrollTimeMs = 0` | 滚动停止检测的 `if (last > 0 && ...)` 守卫阻止误触 |
| 用户在搜索框输入时清空 | progress 已为 1f（由输入时 animateTo 设定），清空分支检测到 progress ≥ 0.95 → 不触发任何动画 |
| 搜索框因滚动处于中间 progress 时清空搜索词 | 搜索词 LaunchedEffect 主动基于 progress snap 到 0f 或 1f（无需等待 C 触发） |

---

## 5. 错误处理

- 滚动事件 `snapshotFlow` 抛出异常时，Compose 协程会被取消并自动重启 LaunchedEffect
- `animateTo` 期间被新的 `animateTo` 打断：Compose Animatable 内部处理，自动取消当前动画启动新动画
- 进度计算溢出：`coerceIn(0f, 1f)` 边界保护

---

## 6. 测试计划

### 6.1 自测清单

| 编号 | 场景 | 预期 |
|------|------|------|
| 1 | 向上滚动列表 | 搜索框按比例即时收缩 |
| 2 | 向上滚动中途停止 | 150ms 后搜索框自动完成到完全隐藏 |
| 3 | 滚动到一半时向下回滚一点 | 搜索框保持当前高度，停止 150ms 后 snap 到完全显示 |
| 4 | 列表回顶 | 搜索框 250ms 展开到完全显示 |
| 5 | 输入搜索词 | 搜索框 250ms 展开到完全显示 |
| 6 | 清空搜索词（不在顶部） | 滚动停止 150ms 后搜索框 200ms 收缩到完全隐藏 |
| 7 | 清空搜索词（在顶部） | 搜索框保持完全显示（由 isAtTop 驱动） |
| 8 | 列表 translationY 视差 | 始终随 progress 同步变化 |
| 9 | 搜索框可见状态下打开键盘 | 搜索框保持完全显示（不被键盘遮挡） |

### 6.2 回归测试

- 滚动性能：长列表快速滚动时帧率无明显下降
- 搜索功能：输入搜索词后过滤逻辑正常工作
- 批量模式：进入批量模式后搜索框依然正常工作
- 节日卡片：节气息屏卡片显示时搜索框依然正常工作

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
| 新增 lastScrollTimeMs 状态 | 在 506 行附近新增 | +1 |
| 滚动驱动 LaunchedEffect | 在 539 行附近增加 `lastScrollTimeMs` 写入 | +1 |
| 新增滚动停止检测 LaunchedEffect | 在滚动驱动之后新增 | +22 |
| 搜索词驱动 LaunchedEffect | 重写为基于 progress 的双向逻辑 | +14（删除 6 行 + 新增 20 行） |
| **合计** | | **+38 行** |

---

## 9. 关键决策记录

| 决策 | 原因 |
|------|------|
| 选方案 1（滚动驱动 + snapTo 同步 + 滚动停止 debounce snap） | 与隐藏过程"渐进跟随"感完全一致；停止后"完成"动作符合用户要求 |
| debounce 150ms | 既能覆盖慢滑场景（不会误判为停止），又能让用户感到"刚停手就有反馈" |
| 轮询 50ms | 反应及时，CPU 占用可接受 |
| 阈值 0.5 | 符合直觉：低于 50% 视为隐藏意图，高于 50% 视为显示意图 |
| 删除"清空搜索词时立即 animateTo"分支 | 避免与滚动停止检测双重动画冲突；逻辑唯一来源 |
| 端点容差 0.05/0.95 | 避免 progress 已在端点附近时做无意义的 snap 动画 |
