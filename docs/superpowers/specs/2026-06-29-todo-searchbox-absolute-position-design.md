# 待办页搜索框级联显示 v2 设计 - 绝对位置驱动

- **日期**：2026-06-29
- **范围**：仅待办页（`HomeScreen.kt`）的搜索框显示/隐藏动画
- **版本**：v2（v1 文档：[2026-06-29-todo-searchbox-cascade-show-design.md](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-29-todo-searchbox-cascade-show-design.md)，使用 delta-based 方案，存在震荡 bug）

---

## 1. 背景与目标

### 1.1 v1 方案的问题（已废弃）

v1 采用 **delta-based（增量驱动）**：根据相邻两次滚动事件的差值（`currentIndex - prevIdx`、`currentOffset - prevOff`）计算方向和增量，累加到 `progress`。

**致命 bug**：当 LazyColumn 出现 overscroll 弹回时，`currentOffset` 会从负值（overscroll）变回 0（弹回），导致 `isDown=true`，触发"增加 progress"分支，与"减少 progress"分支冲突 → **震荡**。

**用户报告**：
- 手指向上滑动时，搜索框无法隐藏
- 页面来回跳动

### 1.2 v2 方案目标

- ✅ **彻底解决震荡**：使用**绝对位置**驱动 progress，单调函数，无方向误判
- ✅ **保留 v1 的 UX 行为**：
  - 滚上：搜索框按比例即时收缩（卷帘门效果）
  - 滚下：搜索框按比例即时展开
  - 滚动停止后：决定性 snap 到 0f 或 1f（保持非 0 即 1 的 UX 感）
  - 输入搜索词：250ms 立即展开
- ✅ **代码更简洁**：删除 prevIdx/prevOff/isDown/delta 局部状态
- ✅ **跨设备一致**：基于 Compose 官方 `LazyListLayoutInfo`，不依赖时序阈值

---

## 2. 核心算法

### 2.1 触发源与对应动作

| 编号 | 触发源 | 条件 | 动作 | 动画规格 |
|------|--------|------|------|----------|
| A | **绝对滚动位置变化** | `scrollOffsetFromTop` 变化 | `progress = 1 - scrollOffsetFromTop / searchBarFullHeightPx`，clamp 到 [0, 1] | `snapTo`（即时） |
| B | 滚动真正停止 | `isScrollInProgress` 由 true 变 false，且 `searchQuery.isBlank()` | progress 在 (0.05, 0.95) 时 snap 到端点 | `animateTo` 200ms / 250ms |
| C | 输入搜索词 | `searchQuery` 从 blank 变非 blank | `animateTo(1f, 250ms)` | tween 250ms |
| D | 清空搜索词 | `searchQuery` 从非 blank 变 blank | 基于 progress 主动 snap 到端点 | tween 200ms / 250ms |

### 2.2 关键不变量

- `progress ∈ [0, 1]`
- `progress = 1 - scrollOffsetFromTop / searchBarFullHeightPx`（核心公式，单调函数）
- 滚动期间 progress 与滚动位置严格对应
- 滚动停止后（B），progress 必须为精确的 0f 或 1f（决定性 snap）
- 永远不显示"半个搜索框"（在静态状态下）

### 2.3 核心改进：绝对位置驱动的单调性证明

**v1（delta-based）的问题**：
```
isDown = (currentIndex < prevIdx) || (currentOffset < prevOff)
```
- 上滑时 `isDown=false` → progress 减少
- overscroll 弹回时 `currentOffset` 增大 → `isDown=true` → progress 增加
- 多次反复 → **震荡**

**v2（absolute-position）的正确性**：
```
scrollOffsetFromTop = -firstItem.offset  // 单调：滚动越多，值越大
progress = 1 - scrollOffsetFromTop / searchBarFullHeightPx  // 单调递减函数
```
- 上滑：`firstItem.offset` 减小 → `-firstItem.offset` 增大 → progress 减小 ✓
- overscroll 弹回：`firstItem.offset` **不变**（反映内容真实位置，不受视觉 overscroll 影响）→ progress 不变 ✓
- 下滑：`firstItem.offset` 增大 → `-firstItem.offset` 减小 → progress 增大 ✓
- **单调性**：position 单调 → progress 单调 → **零震荡**

---

## 3. 实现细节

### 3.1 删除：旧 scroll-driven LaunchedEffect（line 515-552）

删除整段 51 行，包括 `prevIdx`、`prevOff`、`isDown`、`delta` 等局部变量。

**删除前**（v1）：
```kotlin
LaunchedEffect(lazyListState) {
    var prevIdx = 0
    var prevOff = 0
    snapshotFlow { ... }.distinctUntilChanged().collect { (currentIndex, currentOffset) ->
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
            if (isDown && searchRevealProgress.value < 1f) {
                // 滚下分支
            } else if (!isDown && searchRevealProgress.value > 0f) {
                // 滚上分支
            }
        }
        prevIdx = currentIndex
        prevOff = currentOffset
    }
}
```

### 3.2 新增：绝对位置驱动 LaunchedEffect

**插入位置**：原 scroll-driven LaunchedEffect 位置（line 515-552 整段删除后）

```kotlin
/**
 * 绝对位置驱动搜索框进度：
 * - 进度 = 1 - scrollOffsetFromTop / searchBarFullHeightPx
 * - scrollOffsetFromTop 来自 lazyListState.layoutInfo.visibleItemsInfo.first().offset
 * - 这是单调函数，overscroll 弹回不会引起震荡（firstItem.offset 反映内容真实位置）
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

**依赖**：
- `searchRevealProgress`（已存在）
- `searchBarFullHeightPx`（line 513 已存在）
- `lazyListState`（已存在）
- `kotlinx.coroutines.launch`（无需，LaunchedEffect 自带）
- `androidx.compose.runtime.derivedStateOf`（已 import）
- `androidx.compose.runtime.getValue`（已 import，line 80）
- `androidx.compose.runtime.snapTo`（Animatable 扩展，已可用）

### 3.3 保留：isScrollInProgress 决定性 snap

```kotlin
/**
 * 滚动真正停止时触发决定性 snap：
 * - isScrollInProgress 由 true 变 false 时，effect 重启
 * - progress 在 (0.05, 0.95) 时 snap 到 0f 或 1f，保持非 0 即 1 的 UX 感
 * - 与绝对位置驱动配合：滚动期间由 absolute-position 实时跟随，停止后决定性 snap
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

**保留位置**：line 567-579（v1 实施时已添加，无需修改）

### 3.4 保留：搜索词驱动 LaunchedEffect

```kotlin
LaunchedEffect(searchQuery) {
    if (searchQuery.isNotBlank()) {
        searchRevealProgress.animateTo(1f, animationSpec = tween(250, easing = FastOutSlowInEasing))
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

**保留位置**：line 597-616（无需修改）

### 3.5 保留不变部分

- 搜索框 `Box` + `Modifier.layout` 高度收缩（line 624-636）
- 列表 `translationY` 视差（line 791-793）
- `searchBarFullHeightPx` 高度基准（line 513）
- 搜索框渲染（line 638-672）
- `isAtTop` derivedStateOf（line 581-586，可能仍被引用）
- `isScrolling` 订阅（line 510）

---

## 4. 边界情况处理

| 场景 | 行为 |
|------|------|
| 上滑（含 overscroll 弹回） | firstItem.offset 单调减小（弹回时不变），progress 单调减小，**零震荡** |
| 缓慢下滑 | firstItem.offset 单调增大，progress 立即增大（卷帘门）|
| 滚下到顶部后松手 | firstItem.offset = 0 → progress = 1f（已满）|
| 滚到一半松手 | isScrolling 变 false，effect 决定性 snap 到 0f（< 0.5）或 1f（> 0.5）|
| 滚动期间输入搜索词 | isScrolling effect 的 `searchQuery.isBlank()` 守卫阻止 snap；absolute-position effect 继续更新；搜索词 effect 触发 `animateTo(1f)` |
| 输入搜索词时滚动 | 搜索词 effect 阻止 absolute-position 生效（searchQuery 非 blank 时 absolute-position 不会覆盖，因为 progress 已是 1f）|
| LazyColumn 空（visibleItemsInfo 为空） | firstItem = null → scrollOffsetFromTop = 0 → progress = 1f（搜索框显示）|
| 节日卡片遮挡 | firstVisibleItem 仍可读，progress 正常更新 |
| 首次进入页面 | 初始 progress = 1f，scrollOffsetFromTop = 0，target = 1f，无动画 |
| 跨设备一致性 | 使用 Compose 官方 `LazyListLayoutInfo`，不依赖平台/设备特性 |

---

## 5. 错误处理

- `derivedStateOf` 抛异常时，Compose 自动捕获并跳过该次计算
- `snapTo` 期间被新的 `snapTo`/`animateTo` 打断：Compose Animatable 内部处理，自动取消当前动画启动新动画
- `firstItem.offset` 为 null（空列表）：fallback 到 0，progress = 1f
- `coerceAtLeast(0)` / `coerceIn(0f, 1f)` 边界保护

---

## 6. 测试计划

### 6.1 自测清单

| 编号 | 场景 | 预期 |
|------|------|------|
| 1 | 向上滚动列表 | 搜索框按比例即时收缩，**无震荡** |
| 2 | **向上滚动中途松手** | fling + overscroll 弹回后，搜索框 snap 到 0f（**修复 v1 bug**）|
| 3 | 滚动到一半时向下回滚一点 | 搜索框跟随滚动位置变化，停止后决定性 snap |
| 4 | 缓慢向下滑 | 搜索框按比例即时展开（卷帘门）|
| 5 | 列表回顶 | 搜索框已通过 absolute-position 跟随到 1f |
| 6 | **强 fling 触顶 overscroll** | **页面不跳动，搜索框稳定隐藏**（**修复 v1 bug**）|
| 7 | 输入搜索词 | 搜索框 250ms 展开到完全显示 |
| 8 | 清空搜索词（不在顶部） | 搜索词 effect 主动 snap 到端点 |
| 9 | 清空搜索词（在顶部） | 搜索框保持完全显示 |
| 10 | 列表 translationY 视差 | 始终随 progress 同步变化 |
| 11 | 滚动到列表底部 | progress 持续 0f，搜索框完全隐藏 |
| 12 | 跨设备测试 | 低端机、高端机、平板上 fling + overscroll 行为一致 |

### 6.2 回归测试

- 滚动性能：长列表快速滚动时帧率无明显下降（`derivedStateOf` 在 layout 变化时重算，单次 O(1)）
- 搜索功能：输入搜索词后过滤逻辑正常工作
- 批量模式：进入批量模式后搜索框依然正常工作
- 节日卡片：节气息屏卡片显示时搜索框依然正常工作
- 下拉刷新：触发刷新时不影响搜索框动画

---

## 7. 不在范围内

- 不修改 ViewModel
- 不修改 SearchBar 组件
- 不修改其他页面的搜索框
- 不修改搜索框高度基准值
- 不修改 200ms / 250ms 动画时长
- 不修改 0.12f 视差系数

---

## 8. 文件改动汇总

**仅 1 个文件**：[HomeScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt)

| 位置 | 改动 | 行数变化 |
|------|------|----------|
| 删除 v1 scroll-driven LaunchedEffect | 删除 line 515-552 整段 | -38 |
| 新增 `scrollOffsetFromTop` derivedStateOf | 在原 LaunchedEffect 位置新增 | +10 |
| 新增 absolute-position LaunchedEffect | 紧接 derivedStateOf 之后新增 | +8 |
| **合计** | | **-20 行** |

注：
- `isScrolling` 订阅（line 510）：保留（v1 Task 1 已添加）
- `LaunchedEffect(isScrolling)`（line 567-579）：保留（v1 Task 3 已添加）
- `LaunchedEffect(searchQuery)`（line 597-616）：保留（v1 已存在）
- `isAtTop` derivedStateOf（line 581-586）：保留（v1 Task 4 删除 LaunchedEffect 但保留 state）

---

## 9. 关键决策记录

| 决策 | 原因 |
|------|------|
| **改为 absolute-position 驱动** | 解决 v1 delta-based 方案的 overscroll 震荡 bug；单调函数，零震荡 |
| **删除 prevIdx/prevOff/isDown/delta 局部状态** | 不再需要：absolute-position 直接从 layoutInfo 读绝对位置 |
| **删除 snapshotFlow/distinctUntilChanged** | 不再需要监听滚动事件流；直接用 `derivedStateOf` 监听 `layoutInfo` |
| **删除 50ms 轮询 LaunchedEffect** | 已被 absolute-position 替代（v1 Task 5 已删除，保持不变）|
| **删除 lastScrollTimeMs 状态** | 不再需要时间戳追踪（v1 Task 5 已删除，保持不变）|
| **保留 isScrollInProgress 决定性 snap** | UX 要求：滚动停止后 progress 必须是 0f 或 1f（v1 引入，保留）|
| **保留 searchQuery 驱动** | 显式用户意图必须立即响应 |
| **`-firstItem.offset` 而非 `firstItem.index` 计算 scrollOffset** | 处理不同 item 高度、不同滚动状态；绝对像素距离 |
| **`coerceAtLeast(0)` 保护** | 防止 overscroll 导致负值（虽然理论上 Compose 不会让 offset 变负）|
| **`coerceIn(0f, 1f)` 保护** | progress 必须非负且不超过 1 |
| **v1 → v2 不创建分支** | 整体替换而非并存：v1 的 delta-based 代码 100% 删除 |
| **v1 已 commit 的 5 个 task 保留在 git 历史** | 提供"v1 尝试 → 反馈 bug → v2 修复"的完整演进记录 |
