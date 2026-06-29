# 待办拖拽松手后视觉跳跃 Bug 修复设计

> **创建日期**：2026-06-29
> **关联文件**：
> - `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt`
> - `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt`
> **关联 Bug**：`2026-06-28-拖拽高度差跳动bug修复-design.md`（6月28日已修复拖拽中震荡，本次修复松手后跳跃）
> **状态**：待审阅

## 1. 问题描述

### 1.1 现象

在待办页面长按拖拽卡片并完成交换后，**被交换的两张卡片**（A 和 B）会出现「上下跳跃交换顺序」的视觉跳变，松手后约 250-500ms 才稳定到正确位置。

### 1.2 复现条件

- **任意高度差场景**（等高、不等高都触发）
- 一次或多次交换后松手
- 100% 复现率

### 1.3 影响

| 影响维度 | 说明 |
|---------|------|
| 视觉体验 | 用户感知"操作不干净"，与 Material Design 拖拽预期不符 |
| 已有修复稳定性 | 与 6月28日高度差震荡修复叠加，可能影响用户对修复的信心 |
| 性能 | 双重动画触发（被拖项 + 被交换项）造成额外重组 |

### 1.4 与 6月28日修复的差异

| 维度 | 6月28日修复（高度差震荡） | 本次问题（松手后跳跃） |
|------|------------------------|----------------------|
| 阶段 | 拖拽过程中（手指按下时） | 松手瞬间（手指抬起时） |
| 根因 | `draggedBaseCenterY` 计算错误 + 无反向锁定 | finally 块状态重置时序冲突 |
| 现象 | 上下反复震荡 | 上下跳跃后稳定 |

## 2. 根因分析

### 2.1 三重时序冲突

**问题根源**：`finally` 块（`ReorderableLazyColumn.kt:596-631`）在同一帧内执行三个相互冲突的操作：

1. **`onReorder` 提交** → 触发 ViewModel 异步更新 `items`
2. **`isDragActive = false`** → `derivedStateOf` 重算 `dragOffsetY = 0`
3. **`draggedKey = null`** → 被拖项 A 从「拖拽分支」切换到「普通分支」

### 2.2 关键时序

假设拖拽前 `[A, B, C, D, E]`，用户将 A 拖到 C 位置（index 2）：

| 帧 | 操作 | 视觉表现 |
|----|------|---------|
| frame 0 (松手) | `awaitPointerEvent` 返回 up → `try/finally` 进入 | A 在手指位置 |
| frame 0 (重置) | `isDragActive=false`、`draggedKey=null` | A 瞬时跳到 `draggedBaseCenterY` |
| frame 0 (重组) | A 切到「普通分支」，B 的 `animateItem` 重新启动 | **A 跳变 + B 跳变同时发生** |
| frame 1+ | ViewModel 异步结果回流，`items` 更新 | displayItems 再次变化，可能触发二次动画 |

### 2.3 涉及的具体代码行

| 行号 | 代码 | 问题 |
|------|------|------|
| 596-631 | `finally` 块 | 状态重置无过渡 |
| 401-406 | `dragOffsetY` derivedStateOf | 依赖 `isDragActive`，无中间态 |
| 638-687 | itemsIndexed Box 结构 | 「拖拽分支」/「普通分支」硬切换 |

### 2.4 根因细化

#### 根因 1：`dragOffsetY` 归零无过渡

`derivedStateOf` 中 `isDragActive=false` 后，`dragOffsetY` 立即从 `fingerY - draggedBaseCenterY` 跳到 `0`。A 的内层 Box（`offset { IntOffset(0, dragOffsetY.roundToInt()) }`）的 offset 瞬间归零，造成视觉瞬移。

#### 根因 2：拖拽分支硬切换

`isDragging = key(item) == draggedKey`，`draggedKey = null` 后 A 立即从「拖拽分支」切换到「普通分支」：
- `zIndex` 从 `1f` 变为 `0f`
- `alpha` 从 `0.7f` 变为 `1f`
- `scale` 从 `1.05f` 变为 `1f`
- 内层 Box（浮起卡片）消失
- 外层 Box 保留但 children 结构变化

虽然 `animateItem()` 是外层 Box 的 modifier，理论上不重启，但由于内层 Box 的 offset 突变，A 的视觉位置不连续。

#### 根因 3：被交换项 animateItem 被重置

B 在拖拽中已经完成让位动画（拖到 C 旧位置时 B 让位到 A 旧位置 + A 高度）。松手时虽然 displayItems 顺序没变，但 `displayItems` 引用变化（重置为 `items` 时），可能让 B 的 `animateItem()` 重新启动让位动画。

#### 根因 4：onReorder 异步结果回流

ViewModel 中 `viewModelScope.launch { ... todoDao.updateSortOrders(updates) ... }` 是异步的，DB 更新后 `items` 重新排序，通过 `LaunchedEffect(items)` 重置 `displayItems`。如果 sortOrder 计算与 displayItems 不完全一致（边界情况），会触发额外动画。

## 3. 解决方案

采用**方案 A：拖拽结束动画化**。

### 3.1 核心思路

松手后**不立即**重置 `isDragActive` 和 `draggedKey`，而是引入一个 250ms 的「释放动画期」（Releasing 状态），期间：
- `draggedKey` 保持有效 → A 继续在「拖拽分支」中 → `animateItem()` 不重启
- `dragOffsetY` 由 `Animatable<Float>` 驱动，从 `fingerY - draggedBaseCenterY` 平滑过渡到 `0`
- `displayItems` 不再变化（`LaunchedEffect(items)` 检测到 `isReleasing` 时跳过更新）
- 250ms 动画结束后再重置 `draggedKey` → A 切到「普通分支」，此时 displayItems 与 items 顺序一致，无额外动画

### 3.2 状态机扩展

```
State: Dragging
  └─ changedToUp → State: Releasing
                     ├─ isDragActive = false（不再消费 pointerEvent）
                     ├─ isReleasing = true
                     ├─ draggedKey 保持有效（A 仍在「拖拽分支」）
                     ├─ 启动 releaseDragOffset: Animatable<Float>(current → 0, 250ms tween)
                     ├─ 调用 onReorder（异步）
                     └─ pointerInput 协程退出（自然结束 awaitEachGesture）

State: Releasing (200-300ms)
  ├─ releaseDragOffset 持续驱动 dragOffsetY（从 fingerY-baseCenterY → 0）
  ├─ A 视觉位置平滑从「手指位置」过渡到「基线位置」
  ├─ B/C/D/E 等不受影响（displayItems 不再变化）
  └─ animateItem 不重启（displayItems 引用稳定）

State: Idle (releaseDragOffset 完成后)
  ├─ isReleasing = false
  ├─ draggedKey = null → A 切到「普通分支」
  └─ 此时 displayItems 与 items 顺序一致，无动画
```

### 3.3 关键代码变更

#### 3.3.1 新增状态变量

```kotlin
// 释放动画期标志：A 保持在「拖拽分支」中，但不再响应 pointerEvent
var isReleasing by remember { mutableStateOf(false) }

// 释放动画驱动：驱动 dragOffsetY 从松手时位置平滑过渡到 0
val releaseDragOffset = remember { Animatable(0f) }
```

#### 3.3.2 dragOffsetY 改造

```kotlin
// 修改前（derivedStateOf 仅判断 isDragActive）
val dragOffsetY by remember {
    derivedStateOf {
        if (isDragActive && draggedKey != null) fingerY - draggedBaseCenterY else 0f
    }
}

// 修改后（增加 isReleasing 状态，保持 A 在「拖拽分支」中时也有非零值）
val dragOffsetY by remember {
    derivedStateOf {
        when {
            isDragActive -> fingerY - draggedBaseCenterY
            isReleasing -> releaseDragOffset.value
            else -> 0f
        }
    }
}
```

#### 3.3.3 finally 块改造

```kotlin
} finally {
    if (isDragActive) {
        // 1. 记录松手时偏移，启动释放动画
        val releaseStartOffset = fingerY - draggedBaseCenterY
        isDragActive = false
        // draggedKey 保持有效 → A 继续在「拖拽分支」，animateItem 不重启

        Log.d("ReorderableLazyColumn",
            "[RELEASE_START] offset=$releaseStartOffset idx=$draggedCurrentIndex")

        // 2. 提交排序（异步）
        if (draggedOriginalIndex != draggedCurrentIndex && draggedOriginalIndex >= 0) {
            val displayPinned = displayItems.map { isPinned(it) }
            val crossedPinnedZone = ReorderAlgorithms.checkPinnedZoneCrossed(
                displayItems = displayPinned,
                draggedOriginalIsPinned = draggedOriginalIsPinned,
                draggedCurrentIndex = draggedCurrentIndex
            )
            onReorder(draggedOriginalIndex, draggedCurrentIndex, crossedPinnedZone)

            // 确认震动
            HapticFeedbackManager.performHapticFeedback(
                context, InteractionType.CONFIRM, enabled = true
            )
        }

        // 3. 启动释放动画（250ms tween，与 animateItem 默认一致）
        scope.launch {
            try {
                isReleasing = true
                releaseDragOffset.snapTo(releaseStartOffset)
                Log.d("ReorderableLazyColumn",
                    "[RELEASE_OFFSET_SNAP] from=$releaseStartOffset to=${releaseDragOffset.value}")
                releaseDragOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 250,
                        easing = FastOutSlowInEasing
                    )
                )
            } finally {
                // 4. 动画结束后重置所有状态
                isReleasing = false
                draggedKey = null
                draggedOriginalIndex = -1
                draggedCurrentIndex = -1
                fingerY = 0f
                draggedBaseCenterY = 0f
                lastSwapTargetKey = null
                lastSwapFingerY = 0f
                Log.d("ReorderableLazyColumn", "[RELEASE_END] isDragActive=$isDragActive")
            }
        }
    } else if (isReleasing) {
        // pointerInput 因异常/外部原因被取消，但释放动画仍在进行
        // 让动画自然完成（Animatable 协程独立于 pointerInput 协程）
        Log.d("ReorderableLazyColumn", "[RELEASE_START] pointerInput cancelled during releasing")
    }
}
```

#### 3.3.4 LaunchedEffect(items) 保护

```kotlin
LaunchedEffect(items) {
    when {
        isReleasing -> {
            // 释放动画期间：跳过更新，避免破坏动画
            Log.d("ReorderableLazyColumn",
                "[DISPLAY_ITEMS_REFRESH] skipped: isReleasing=true")
        }
        !isDragActive -> {
            displayItems = items
        }
        else -> {
            // 拖拽中 items 被外部变更 → 取消拖拽
            displayItems = items
            isDragActive = false
            // ... 重置状态
        }
    }
}
```

#### 3.3.5 拖拽启动时清理释放动画

```kotlin
// 在 awaitEachGesture 入口处
if (isReleasing) {
    // 之前有未完成的释放动画 → 取消并重置
    isReleasing = false
    draggedKey = null
    draggedBaseCenterY = 0f
    // Animatable 协程在 finally 块中检测到 isReleasing=false 后会自然结束
}
```

### 3.4 Logcat 打点

| 标识 | 位置 | 关键字段 | 用途 |
|------|------|---------|------|
| `RELEASE_START` | finally 块入口 | `offset`, `idx` | 记录松手瞬间状态 |
| `RELEASE_OFFSET_SNAP` | `snapTo` 前后 | `fromValue`, `toValue` | 验证初始对齐 |
| `RELEASE_ANIM_TICK` | Animatable 回调（每帧，可选） | `value` | 验证动画曲线（可选打点） |
| `DISPLAY_ITEMS_REFRESH` | `LaunchedEffect(items)` | `isReleasing`, `items.size` | 验证释放期间跳过 |
| `RELEASE_END` | 动画 finally 块 | `isDragActive` | 验证重置时序 |

**打点原则**：
- 使用 `Log.d(TAG, "[标识] 字段=值")` 格式
- TAG 用 `"ReorderableLazyColumn"`
- 生产构建用 `if (BuildConfig.DEBUG)` 包裹（本次不强制实施，留作后续优化）

## 4. 数据流

### 4.1 完整数据流图

```
手指抬起 (change.changedToUp)
  ↓
awaitPointerEvent 返回 up → break
  ↓
try/finally 进入 finally 块
  ↓
RELEASE_START: 记录 releaseStartOffset
  ↓
isDragActive = false  （不再消费事件）
  ↓
onReorder 同步调用（内部通过 ViewModel.viewModelScope.launch 异步执行 DB 更新）
  ↓
scope.launch { releaseDragOffset.animateTo(0f, tween(250)) }
  ↓
isReleasing = true
  ↓
releaseDragOffset.snapTo(releaseStartOffset)  // 立即对齐到松手时偏移
  ↓
RELEASE_OFFSET_SNAP
  ↓
animatable.animateTo(0f)  // 250ms tween FastOutSlowInEasing
  ↓ 期间
  dragOffsetY = releaseDragOffset.value  // 持续驱动 A 的内层 Box offset
  A 视觉位置：layoutInfo.offset + draggedSize/2 + releaseDragOffset.value
  B/C/D/E 不受影响（animateItem 不重启）
  ↓
animateTo 完成
  ↓
isReleasing = false, draggedKey = null
  ↓
A 切到「普通分支」→ animateItem 不重启（displayItems 引用未变）
  ↓
RELEASE_END
```

### 4.2 状态变量生命周期

| 变量 | Idle | Dragging | Releasing | Idle (动画后) |
|------|------|----------|-----------|---------------|
| `isDragActive` | false | true | **false** | false |
| `isReleasing` | false | false | **true** | false |
| `draggedKey` | null | A 的 key | **A 的 key** | null |
| `fingerY` | 0f | 实时更新 | 松手时值 | 0f |
| `draggedBaseCenterY` | 0f | 同步更新 | 松手时值 | 0f |
| `releaseDragOffset.value` | 0f | 0f | 1f → 0f 动画 | 0f |
| `dragOffsetY`（derived） | 0f | fingerY-base | **releaseOffset** | 0f |

## 5. 边界情况与错误处理

| # | 场景 | 处理方式 |
|---|------|---------|
| 1 | 释放动画期间 pointerInput 协程被取消（异常/外部中断） | `try/finally` 确保状态重置；`isReleasing` 由 Animatable 协程独立管理 |
| 2 | 释放动画期间 `items` 被外部更新（如同步） | `LaunchedEffect(items)` 检测到 `isReleasing=true`，**跳过** displayItems 重置 |
| 3 | 用户在释放动画期间尝试新拖拽 | 容器 `pointerInput` 入口处检测 `isReleasing=true` → 取消前次动画协程，重置状态 |
| 4 | 释放动画期间 `displayItems` 引用变化（边缘场景） | `derivedStateOf` 检测到 `isReleasing=true`，继续使用 `releaseDragOffset.value`，不依赖 `fingerY` |
| 5 | 释放动画期间 Activity 销毁/配置变更 | `Animatable` 状态由 `remember` 保存；`isReleasing` 状态重建后需手动处理（可接受） |
| 6 | 快速连续拖拽（第一次还没释放完就第二次） | 拖拽启动时清理释放动画；`Animatable` 协程 `finally` 块检测到 `isReleasing=false` 后自然结束 |
| 7 | 释放动画与 ViewModel 异步结果同时到达 | `LaunchedEffect(items)` 跳过 → displayItems 顺序与 items 顺序一致（onReorder 就是把 displayItems 持久化），animateItem 不启动 |
| 8 | `releaseStartOffset = 0`（松手时已在基线） | `animateTo(0f)` 不产生可见动画，立即进入下一状态 |
| 9 | 释放动画期间 `items.size` 变化（删除/新增） | `LaunchedEffect(items)` 跳过；等动画结束后下次 `LaunchedEffect` 重置 displayItems |

### 5.1 关键防御代码

#### 5.1.1 pointerInput 入口保护

```kotlin
// ReorderableLazyColumn.kt: pointerInput(Unit) { awaitEachGesture { ... } }
awaitEachGesture {
    // 释放动画期间尝试新拖拽 → 清理前次动画
    if (isReleasing) {
        isReleasing = false
        draggedKey = null
        draggedBaseCenterY = 0f
        // Animatable 协程的 finally 块会检测 isReleasing=false → 跳出循环
    }

    val down = awaitFirstDown(requireUnconsumed = false)
    if (down.isConsumed) return@awaitEachGesture
    if (!isDragEnabled) return@awaitEachGesture
    if (items.size < 2) return@awaitEachGesture
    // ... 原有逻辑
}
```

#### 5.1.2 Animatable 协程取消安全

```kotlin
// 通过 isReleasing 标志让 derivedStateOf 能感知到释放期状态
// 协程被外部 cancel() 时，animateTo() 立即抛 CancellationException
// finally 块仍会执行，状态仍能正确重置
scope.launch {
    try {
        isReleasing = true
        releaseDragOffset.snapTo(releaseStartOffset)
        releaseDragOffset.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
    } finally {
        isReleasing = false  // 无论是否被取消，都重置标志
        draggedKey = null
        // ... 其他状态
    }
}
```

**关键说明**：
- `isReleasing` 标志**不参与**协程取消控制，仅用于 `derivedStateOf` 和 `LaunchedEffect(items)` 的状态判断
- 协程取消由 Compose 协程作用域的取消传播机制自动处理（pointerInput 协程被取消时不会影响 scope.launch 启动的独立协程）
- 真正取消 Animatable 协程的途径是 Composable 离开 composition（此时所有 remember 协程会被取消）

### 5.2 错误处理

- **Animatable 协程被外部 cancel()**：通过 `isReleasing` 标志 + finally 块确保状态重置
- **Animatable 协程抛出异常**：异常会被 `try/finally` 捕获，状态仍能重置
- **ViewModel 异步 onReorder 失败**：与本次修复无关，沿用现有错误处理（DB 写入失败回退到内存顺序）

## 6. 测试策略

### 6.1 纯函数单元测试

#### 6.1.1 新增可测试的纯函数

在 `ReorderAlgorithms` object 中新增两个纯函数（从 finally 块提取）：

```kotlin
object ReorderAlgorithms {
    /**
     * 计算释放动画的起始 offset（从 ReorderableLazyColumn finally 块提取）
     * @param fingerY 手指 Y 坐标
     * @param baseCenterY 拖拽基线中心 Y
     * @return 释放动画起始 offset
     */
    fun computeReleaseStartOffset(fingerY: Float, baseCenterY: Float): Float =
        fingerY - baseCenterY

    /**
     * 判断释放动画期间是否应跳过 displayItems 更新
     * @param isReleasing 是否处于释放动画期
     * @return true = 跳过更新
     */
    fun shouldSkipDisplayUpdate(isReleasing: Boolean): Boolean = isReleasing
}
```

#### 6.1.2 新增测试用例（ReorderAlgorithmsTest.kt）

| 测试 ID | 测试名称 | 输入 | 期望 |
|---------|---------|------|------|
| TC-R-01 | `computeReleaseStartOffset 正偏移` | `fingerY=200f, baseCenterY=140f` | `60.0f` |
| TC-R-02 | `computeReleaseStartOffset 零偏移` | `fingerY=140f, baseCenterY=140f` | `0.0f` |
| TC-R-03 | `computeReleaseStartOffset 负偏移（手指在基线下方）` | `fingerY=110f, baseCenterY=140f` | `-30.0f` |
| TC-R-04 | `shouldSkipDisplayUpdate 释放期间` | `isReleasing=true` | `true` |
| TC-R-05 | `shouldSkipDisplayUpdate 非释放期间` | `isReleasing=false` | `false` |

### 6.2 手动验证清单

| # | 场景 | 预期 |
|---|------|------|
| 1 | 等高卡片单次交换后松手 | A 从手指位置平滑滑到新位置，无跳跃 |
| 2 | 不等高卡片交换后松手 | 同上，无高度差震荡（与 6月28日修复叠加） |
| 3 | 多次连续交换后松手 | A 平滑落位，B/C/D 不产生额外动画 |
| 4 | 释放动画期间尝试新拖拽 | 当前动画被打断，立即进入新拖拽 |
| 5 | 释放动画期间外部同步 | displayItems 不被覆盖，动画不被打断 |
| 6 | Logcat 验证 | 5 个打点按预期顺序输出，RELEASE_START 到 RELEASE_END 间隔约 250ms |
| 7 | 释放动画期间 `items.size` 变化 | 动画结束后下次 `LaunchedEffect` 正确重置 displayItems |
| 8 | Activity 旋转/配置变更 | 释放动画结束后状态正确重置 |

### 6.3 验证指标

- **动画时长**：Logcat `RELEASE_START` 到 `RELEASE_END` 间隔应为 250ms ± 16ms（1帧误差）
- **位置准确性**：A 释放结束后视觉位置 = `displayItems[新 index].offset + draggedSize/2`
- **无二次动画**：释放结束后 `animateItem()` 不应启动（B/C/D/E 位置不变化）

## 7. 影响范围

| 文件 | 变更类型 | 行数变化 |
|------|---------|---------|
| `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` | 修改 | +35 行（finally 块重写、新增 2 个状态、5 个 Logcat 打点、LaunchedEffect 保护、pointerInput 入口保护） |
| `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt` | 新增 | +60 行（2 个纯函数 + 5 个测试用例） |
| 无其他文件变更 | | |

**不改动**：
- `TodoListItem.kt`、`SwipeableTodoBox.kt`、`HomeScreen.kt`、`HomeViewModel.kt`
- 数据库 schema、Migration
- 6月28日修复（高度差震荡）的 3 个修复点

## 8. 不变的部分

- 三层手势分离架构（容器拖拽 L1 / 左滑 L2 / 卡片点击 L3）
- `ReorderAlgorithms.findSwapTarget` 算法（6月28日 maxSize 修复保留）
- 反向交换锁定机制（`lastSwapTargetKey`、`lastSwapFingerY`）
- 50% 重叠检测阈值
- 边缘自动滚动（变速）
- 置顶区跨越检测
- 拖拽期间的视觉反馈（虚线占位框 + 浮起卡片 + 阴影）
- `animateItem()` 让位动画
- 自动滚动期间 `draggedBaseCenterY += scrollDelta` 的同步逻辑
- `onReorder` 回调签名
- 数据库 sortOrder 持久化逻辑
- `pointerInput(Unit)` 键策略

## 9. 实施步骤

1. **修改 `ReorderableLazyColumn.kt`**：
   - 引入 `Animatable` 和 `tween`、`FastOutSlowInEasing` 的 import
   - 新增 `isReleasing`、`releaseDragOffset` 状态
   - 改造 `dragOffsetY` derivedStateOf
   - 重写 finally 块（启动 release 协程）
   - 改造 `LaunchedEffect(items)`（增加 `isReleasing` 分支）
   - 改造 pointerInput 入口（清理前次释放动画）
   - 添加 5 个 Logcat 打点
2. **修改 `ReorderAlgorithms.kt`**（在 `ReorderableLazyColumn.kt` 中）：
   - 新增 `computeReleaseStartOffset` 纯函数
   - 新增 `shouldSkipDisplayUpdate` 纯函数
3. **新增 `ReorderAlgorithmsTest.kt` 测试用例**：
   - TC-R-01 至 TC-R-05 共 5 个测试
4. **手动验证**：按 6.2 清单逐项测试
5. **Logcat 验证**：运行 `adb logcat -s ReorderableLazyColumn:D`，确认 5 个打点顺序正确

## 10. 后续可优化点

- **可单元测试的纯函数更多**：可考虑把 `displayItems` 交换逻辑也提取为纯函数（如 `swapInList(list, fromIdx, toIdx)`），便于单测
- **释放动画可配置**：当前 250ms 硬编码，可通过参数传入以便在不同场景下调整
- **Logcat 打点分级**：本次所有打点都是 `Log.d`，未来可考虑分级（Release 用 `Log.i`，Tick 用 `Log.v`）
- **Compose UI 测试**：可在 `androidTest` 中增加 UI 测试，验证视觉位置正确（本次不做）
- **触觉反馈优化**：释放动画期间是否需要震动？目前是松手瞬间震动一次

## 11. 参考文档

- **6月28日 Bug 修复**：`docs/superpowers/specs/2026-06-28-拖拽高度差跳动bug修复-design.md`
- **拖拽核心设计**：`docs/superpowers/specs/2026-06-28-待办卡片长按拖拽排序-design.md`
- **长按交互 Bug 修复**：`docs/superpowers/specs/2026-06-27-长按交互bug修复-design.md`
- **项目记忆**：`ReorderableLazyColumn` pointerInput key 用 `Unit`、避免早期 return、`Animatable` 用法
