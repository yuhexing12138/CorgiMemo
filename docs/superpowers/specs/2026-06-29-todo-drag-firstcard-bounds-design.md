# 待办拖拽：首页边界异常修复设计

| 项目 | 值 |
|------|----|
| 创建日期 | 2026-06-29 |
| 状态 | 待用户审核 |
| 关联模块 | `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` |

---

## 1. 问题陈述

### 1.1 Bug A：长按首页第一张卡片下拖 → 整个页面不受控下滑

**复现步骤**：
1. 进入待办页面
2. 长按首页第一张待办卡片 500ms
3. 继续按住并向下滑动
4. 观察：整个列表页面被拖动，呈现快速且不受控制的下滑；卡片本身未跟随手指

**期望行为**：长按卡片进入拖拽模式后，被拖卡片应跟随手指移动，列表本身不应滚动。

### 1.2 Bug B：非首页卡片上拖与首页卡片交换 → 跟随卡片突然上冲出可视区

**复现步骤**：
1. 进入待办页面（至少 2 张卡片）
2. 长按第 2 张（或第 N 张）卡片 500ms
3. 继续按住并向上拖动，与首页第一张卡片完成交换
4. 观察：原本跟随手指的第 2 张卡片突然向上飞出可视区域顶部

**期望行为**：交换完成后，被拖卡片应停留在新位置（首页），不应飞出屏幕。

---

## 2. 根因分析

### 2.1 Bug A 根因：长按触发到 isDragActive=true 之间的未消费窗口

**关键代码位置**：[ReorderableLazyColumn.kt:504-511](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L504-L511) 与 [ReorderableLazyColumn.kt:544-602](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L544-L602)

**时序图**：

```
T+0ms     用户手指 down
T+500ms   容器检测到长按 → longPressTriggered = true
T+500ms   ⚠️ 此刻 change 未被消费，dragScrollBlocker 未启用
T+505ms   用户手指开始向下移动
T+505ms   LazyColumn 的 verticalScroll 处理器把手指移动解释为"滚动"
T+505ms   ⚠️ 列表开始滚动（页面失控下滑）
T+520ms   dy 累计超过 8dp → isDragActive = true
T+520ms   此时才调用 change.consume() 和启用 dragScrollBlocker
T+520ms   拖拽模式正式生效，但页面已经滚动了 15ms
```

**根因总结**：
- `dragScrollBlocker.onPreScroll` 仅在 `isDragActive=true` 时拦截滚动（line 506-508）
- `change.consume()` 仅在 `isDragActive=true` 后的拖拽分支调用（line 606）
- 长按触发后但 `isDragActive=true` 前的窗口期内，事件未被消费、滚动未被拦截
- 列表顶部的卡片受此影响最明显：用户在顶部下拖，LazyColumn 触发 overscroll/弹性效果，视觉上呈现"整个页面乱滚"

### 2.2 Bug B 根因：visibleItemsInfo 与 displayItems 状态变更的时序错位

**关键代码位置**：[ReorderableLazyColumn.kt:632-668](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt#L632-L668)

**错误计算**（line 648-652）：
```kotlin
val otherInfo = listState.layoutInfo.visibleItemsInfo
    .find { it.key == swapTargetKey }
if (otherInfo != null) {
    draggedBaseCenterY = (otherInfo.offset + draggedSize / 2f)
}
```

**数值推演**（假设每张卡片 200px 高）：

```
交换前 displayItems = [A, B, C, D]
A 在 index 0, offset = 0
B 在 index 1, offset = 200

用户拖 B 上移与 A 交换
   ↓
displayItems = [B, A, C, D]  ← B 在 index 0, A 在 index 1
   ↓
visibleItemsInfo 在下一帧刷新：
  B.offset = 0 (新位置)
  A.offset = 200 (新位置)
   ↓
line 648 读取 otherInfo (A)：
  - 若读到旧值: A.offset = 0
  - 若读到新值: A.offset = 200
   ↓
line 651 错误计算:
  draggedBaseCenterY = 200 + 100 = 300  ← 实际应为 B 在 index 0 的中心 = 100
   ↓
fingerY ≈ 100 (用户手指在 A 原来位置)
   ↓
dragOffsetY = 100 - 300 = -200  ← 整个卡片高度的向上偏移
   ↓
被拖项 B 渲染位置 = listOffset(0) + offset(-200) = -200
   ↓
B 飞出屏幕顶部
```

**根因总结**：
- `draggedBaseCenterY` 的语义是"被拖项应有中心 Y"，但实际用 `otherInfo.offset + draggedSize/2` 表达
- `otherInfo` 是被交换的目标项（首页 A），其 offset 在 `displayItems` 变更后立即变化
- 直接读 `otherInfo.offset` 会读到与新 `displayItems` 不一致的位置
- 对首页 A 特别致命：A 在交换后从 index 0 → index 1，offset 从 0 变到 cardHeight，基线被错算到 `1.5h`

---

## 3. 修复方案

### 3.1 状态模型重构（核心改动）

**当前状态变量**：
- `draggedBaseCenterY`：含义模糊，既要表达"displayItems 中应有位置"，又要在 auto-scroll 时调整

**新状态模型**：拆分为两个独立状态

```kotlin
// 纯逻辑基线：被拖项在 displayItems 中的应有中心 Y（不含 auto-scroll 调整）
var draggedListCenterY by remember { mutableFloatStateOf(0f) }

// 滚动补偿：auto-scroll 期间列表整体平移了多少
var scrollCompensationY by remember { mutableFloatStateOf(0f) }
```

**派生 dragOffsetY**：
```kotlin
val dragOffsetY by remember {
    derivedStateOf {
        when {
            isDragActive -> fingerY - draggedListCenterY - scrollCompensationY
            isReleasing -> releaseDragOffset.floatValue
            else -> 0f
        }
    }
}
```

**auto-scroll 调整**（替换原 `draggedBaseCenterY += scrollDelta`）：
```kotlin
LaunchedEffect(isDragActive, fingerY) {
    while (isDragActive) {
        // ... 现有 scrollDelta 计算 ...
        if (scrollDelta != 0f) {
            listState.scrollBy(scrollDelta)
            scrollCompensationY += scrollDelta  // 仅补偿滚动，不动基线
        }
        delay(16)
    }
}
```

### 3.2 Bug A 修复：长按即拦截

**新增状态**：
```kotlin
var isLongPressActive by remember { mutableStateOf(false) }
```

**升级 dragScrollBlocker**（line 504-511）：
```kotlin
val dragScrollBlocker = remember {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            return if (isLongPressActive) available else Offset.Zero
        }
    }
}
```

**长按触发分支增加消费**（line 551-557）：
```kotlin
if (!longPressTriggered && now - downTime >= longPressTimeoutMs) {
    val dragDistance = (change.position - downPosition).getDistance()
    if (dragDistance <= dragThresholdPx) {
        longPressTriggered = true
        isLongPressActive = true
        change.consume()  // 关键：长按触发即吞事件
    }
}
```

**finally 块同步重置**（line 672-733）：
- 在 `isDragActive = false` 的同一行附近增加 `isLongPressActive = false`

### 3.3 Bug B 修复：基线重算纯函数

**新增纯函数**（在 `ReorderAlgorithms` object 中）：

```kotlin
/**
 * 计算交换后被拖项在 displayItems 中的应有中心 Y
 *
 * 不依赖 listState.layoutInfo.visibleItemsInfo（与 displayItems 状态变更
 * 之间存在一帧延迟，交换后立即读取会读到陈旧位置）。
 *
 * 策略：目标索引 × 平均行高 = 被拖项应有顶部 Y；中心 = 顶部 + 自身高度/2
 */
fun computeDraggedListCenterY(
    targetIndex: Int,
    draggedSize: Int,
    averageItemHeightPx: Float
): Float {
    if (averageItemHeightPx <= 0f) return draggedSize / 2f
    val topY = targetIndex * averageItemHeightPx
    return topY + draggedSize / 2f
}
```

**交换分支替换**（line 636-668）：
```kotlin
if (targetIndex >= 0 && targetIndex != draggedCurrentIndex) {
    val newDisplay = displayItems.toMutableList()
    val draggedItem = newDisplay.removeAt(draggedCurrentIndex)
    newDisplay.add(targetIndex, draggedItem)
    displayItems = newDisplay
    draggedCurrentIndex = targetIndex

    // 关键修复：用目标索引反推基线，不读 visibleItemsInfo
    draggedListCenterY = ReorderAlgorithms.computeDraggedListCenterY(
        targetIndex = targetIndex,
        draggedSize = draggedSize,
        averageItemHeightPx = averageItemHeightPx
    )

    // 删除原 otherInfo 读取与 draggedBaseCenterY 赋值
    // 保留 lastSwapTargetKey / lastSwapFingerY 反向锁定逻辑
    lastSwapTargetKey = swapTargetKey
    lastSwapFingerY = fingerY

    // ... 触觉反馈保留 ...
}
```

**averageItemHeightPx 来源**：
- 在组件内新增 `val itemHeightsPx = remember { mutableStateMapOf<Int, Int>() }`
- 通过 `Modifier.onSizeChanged` 捕获每个 displayItems 索引对应的实际高度
- 取所有项的平均值作为 `averageItemHeightPx`
- 若 `itemHeightsPx` 为空，回退到 `defaultItemHeightPx = 160f`

**进入拖拽时初始化**（line 588-602）：
```kotlin
isDragActive = true
draggedKey = key(draggedItem)
draggedOriginalIndex = draggedIndex.index
draggedCurrentIndex = draggedIndex.index
draggedOriginalIsPinned = isPinned(draggedItem)
fingerY = change.position.y
draggedListCenterY = ReorderAlgorithms.computeDraggedListCenterY(
    targetIndex = draggedIndex.index,
    draggedSize = draggedIndex.size,
    averageItemHeightPx = averageItemHeightPx
)
scrollCompensationY = 0f
```

---

## 4. 边界情况

| # | 情况 | 处理 |
|---|------|------|
| 1 | 首次进入拖拽时 `averageItemHeightPx` 为 0 | 纯函数内回退到 `draggedSize/2f` |
| 2 | auto-scroll 期间发生交换 | `scrollCompensationY` 累加，`draggedListCenterY` 不变 |
| 3 | 释放时状态清理 | finally 块中重置 `draggedListCenterY = 0f` / `scrollCompensationY = 0f` |
| 4 | 拖拽期间 items 外部变更 | LaunchedEffect(items) 同步重置所有新状态 |
| 5 | 子项高度差异大 | 平均行高有像素级误差，可接受；极端情况后续可改中位数 |

---

## 5. 改动文件清单

| 文件 | 改动 | 关键改动 |
|------|------|----------|
| [ReorderableLazyColumn.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt) | 修改 | 状态模型重构 + Bug A 拦截 + Bug B 基线重算 |
| [ReorderAlgorithmsTest.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt) | 新增测试 | 6 个新测试用例 |

**不修改**：
- `PressFeedback.kt`（子项让位逻辑保持依赖 `isDragActive`）
- `ReorderableColumn.kt`（非 LazyColumn 路径不受影响）
- ViewModel 层

---

## 6. 测试方案

### 6.1 单元测试（`ReorderAlgorithmsTest.kt`）

| # | 用例 | 验证点 |
|---|------|--------|
| 1 | `computeDraggedListCenterY(0, 150, 160f)` = `75f` | 基础公式 |
| 2 | `computeDraggedListCenterY(2, 150, 160f)` = `395f` | 索引 2 |
| 3 | `computeDraggedListCenterY(0, 150, 0f)` = `75f`（回退） | 边界 1 |
| 4 | 模拟"B 从 index 1 移到 index 0"：基线应为 `75f`（顶部），不是 `235f` | Bug B 上拖回归 |
| 5 | 模拟"A 从 index 0 移到 index 1"：A 的新位置不影响 B 的基线 | Bug B 双向回归 |
| 6 | `isLongPressActive` 状态机：长按触发后为 `true`，松手后为 `false` | Bug A 状态机 |

### 6.2 手动验证清单

| # | 场景 | 期望 |
|---|------|------|
| 1 | 首页顶部下拖 | 卡片跟随手指，列表不滚动 |
| 2 | 首页顶部上拖到屏幕外 | 卡片跟随手指，列表不滚动 |
| 3 | 第 2 张卡片上拖与首页交换 | 卡片停留在新位置（顶部），不飞出 |
| 4 | 第 2 张卡片下拖与首页交换 | 卡片停留在新位置（顶部），不飞出 |
| 5 | 中间卡片上拖到首页 | 卡片停留在新位置（顶部），不飞出 |
| 6 | 中间卡片连续快速交换 3 次 | 每次交换后卡片精确停留 |
| 7 | 接近顶部边缘时 auto-scroll 触发 | 卡片平滑跟随，基线无漂移 |
| 8 | auto-scroll 期间发生交换 | 卡片精确停留 |
| 9 | 跨置顶区交换（普通项与置顶项） | 卡片精确停留，isPinned 自动切换 |
| 10 | 长按首页后立即松手（不移动） | 触发 onLongClick，无拖拽副作用 |
| 11 | 长按后快速移动超过 8dp | 进入拖拽模式，无列表滚动闪烁 |

---

## 7. 风险与回退

**风险**：
- **低**：`isLongPressActive` 是新增状态，不影响现有 `isDragActive` 语义
- **中**：`computeDraggedListCenterY` 依赖平均行高，对内容高度差异大的列表有像素级误差

**回退方案**：
- 若新基线计算出现新问题，可临时回退到 `otherInfo.offset` 读取 + 在 line 648 后用 `Snapshot.observe` 等待 layoutInfo 刷新

---

## 8. 实施步骤

1. 在 `ReorderAlgorithms` object 中新增 `computeDraggedListCenterY` 纯函数
2. 在 `ReorderAlgorithmsTest` 中新增 6 个测试用例，TDD 验证
3. 在 `ReorderableLazyColumn` 中：
   - 新增 `isLongPressActive` / `draggedListCenterY` / `scrollCompensationY` 状态
   - 新增 `itemHeightsPx` StateMap + onSizeChanged 捕获
   - 升级 `dragScrollBlocker` 使用 `isLongPressActive`
   - 长按触发分支增加 `change.consume()`
   - 替换交换分支的基线计算
   - 替换 auto-scroll 的基线调整
   - 替换进入拖拽的初始化
   - finally 块同步重置新状态
4. 编译验证 + 手动验证 11 个场景
5. 提交代码
