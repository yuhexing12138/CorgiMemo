# 拖拽高度差跳动 Bug 修复设计

> **创建日期**：2026-06-28
> **关联文件**：`app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt`
> **关联测试**：`app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt`

## 1. 问题描述

### 现象
在待办卡片拖拽排序交互中，当**被拖拽卡片高度 < 目标交换卡片高度**时，交换过程中出现被拖卡片**上下反复跳动**的异常现象。

### 复现条件
- 被拖卡片 A 高度较小（如 80px，无描述/子任务的简洁卡片）
- 目标卡片 B 高度较大（如 200px，含描述+子任务+提醒的复杂卡片）
- 长按 A 并拖向 B，触发交换后立即出现震荡

### 影响
- 视觉体验严重下降（卡片快速上下抖动）
- 交换无法稳定完成，可能导致排序数据错误
- 震动反馈连续触发（节流 200ms 仍可感受到连续震动）

## 2. 根因分析

### 根因 1：基线计算使用了错误的尺寸（核心 Bug）

**位置**：`ReorderableLazyColumn.kt` 第 526-531 行

```kotlin
// 当前代码（有 Bug）
val otherInfo = listState.layoutInfo.visibleItemsInfo.find { it.key == swapTargetKey }
if (otherInfo != null) {
    draggedBaseCenterY = (otherInfo.offset + otherInfo.size / 2f)  // ← 用了 B 的尺寸
}
```

**问题推导**（A=被拖卡片 80px，B=目标卡片 200px）：

| 量 | 值 | 说明 |
|---|---|---|
| B 的旧 offset | 60 | 交换前 B 在列表中的顶部坐标 |
| B 的旧中心（当前代码计算值） | 60 + 200/2 = **160** | 用了 B 的尺寸 |
| A 在新位置的预期中心（正确值） | 60 + 80/2 = **100** | 应该用 A 的尺寸 |
| 基线偏差 | 160 - 100 = **60px** | A 的视觉位置偏离预期 60px |

交换后 A 的视觉中心 = `布局中心(100) + offset(fingerY - 160)` = `fingerY - 60`，但手指在 `fingerY`，**A 偏离手指 60px**。

这导致 A 与 B 的重叠比例骤降，`findSwapTarget` 返回 null，交换取消，A 回到原位 → 又满足交换条件 → **无限震荡**。

### 根因 2：`findSwapTarget` 的 `minSize` 使小卡片易触发交换

**位置**：`ReorderableLazyColumn.kt` 第 269 行

```kotlin
val minSize = minOf(draggedSize, other.size).toFloat()
if (minSize > 0 && overlapHeight / minSize > 0.5f) { ... }
```

当 `draggedSize=80, otherSize=200` 时，`minSize=80`，只需 **40px 重叠**就触发交换（80×0.5）。这使得小卡片在高度差场景下触发阈值过低，加剧震荡的触发频率。

### 根因 3：缺乏反向交换保护

交换后下一帧立即重新检测 `findSwapTarget`，没有任何"冷却期"或"方向锁定"。即使根因 1 修复后，在高度差极大的极端场景下（如 40px vs 300px），仍可能因为 `animateItem()` 动画进行中布局信息不稳定而出现偶发震荡。

## 3. 解决方案

采用**方案 C：全面修复**，三重保险彻底消除跳动。

### 3.1 修复点 1：基线计算改用被拖卡片尺寸

**变更**：
```kotlin
// 修正前（Bug）：用 otherInfo.size（目标项 B 的尺寸）
draggedBaseCenterY = (otherInfo.offset + otherInfo.size / 2f)

// 修正后：用 draggedSize（被拖项 A 的尺寸）
draggedBaseCenterY = (otherInfo.offset + draggedSize / 2f)
```

**原理**：交换后 A 占据 B 的旧 `offset` 位置，A 的布局中心 = `B.offset + A.size/2`。基线必须匹配 A 的实际布局中心，offset 偏移才能正确叠加。

### 3.2 修复点 2：`findSwapTarget` 阈值改用较大尺寸

**变更**：
```kotlin
// 修正前：minSize 易使小卡片触发交换
val minSize = minOf(draggedSize, other.size).toFloat()
if (minSize > 0 && overlapHeight / minSize > 0.5f) { ... }

// 修正后：maxSize 使交换阈值更稳定
val maxSize = maxOf(draggedSize, other.size).toFloat()
if (maxSize > 0 && overlapHeight / maxSize > 0.5f) { ... }
```

**阈值变化对比**：

| 场景 | 修正前阈值 | 修正后阈值 | 说明 |
|---|---|---|---|
| 80px vs 200px | 40px (80×0.5) | 100px (200×0.5) | 小卡片不再易触发 |
| 100px vs 100px | 50px | 50px | 等高场景无回归 |
| 300px vs 100px | 50px (100×0.5) | 150px (300×0.5) | 大卡片拖到小卡片也稳定 |

### 3.3 修复点 3：反向交换锁定机制

**新增状态**：
```kotlin
var lastSwapTargetKey by remember { mutableStateOf<Any?>(null) }
var lastSwapFingerY by remember { mutableFloatStateOf(0f) }
```

**交换逻辑中追加**：
```kotlin
if (targetIndex >= 0 && targetIndex != draggedCurrentIndex) {
    // ━━━ 执行交换 ━━━
    // ...（原有交换代码）...
    
    // 记录本次交换信息，用于反向锁定
    lastSwapTargetKey = swapTargetKey
    lastSwapFingerY = fingerY
}

// 反向锁定清除：手指离开上次交换位置超过 draggedSize/2 时清除锁定
if (lastSwapTargetKey != null && abs(fingerY - lastSwapFingerY) > draggedSize / 2f) {
    lastSwapTargetKey = null
}
```

**findSwapTarget 调用前过滤**：
```kotlin
// 排除刚交换过的目标项，防止立即反向交换
val effectiveVisibleItems = visibleInfos.filter { it.key != lastSwapTargetKey }
val swapTargetKey = ReorderAlgorithms.findSwapTarget(
    draggedKey = draggedKey!!,
    fingerY = fingerY,
    draggedSize = draggedSize,
    visibleItems = effectiveVisibleItems
)
```

**拖拽结束时清除锁定**（finally 块内）：
```kotlin
lastSwapTargetKey = null
lastSwapFingerY = 0f
```

**锁定清除条件**：手指移动超过 `draggedSize/2` 后清除锁定，允许继续与该项交换（支持快速连续拖拽多个位置）。

## 4. 数据流

```
手指移动 → fingerY 更新
         → dragOffsetY = fingerY - draggedBaseCenterY（derivedStateOf）
         → findSwapTarget(effectiveVisibleItems)  ← 过滤 lastSwapTargetKey
            ├─ 无目标 → 不交换
            └─ 有目标 → 执行交换
                         → displayItems 更新（A 移到 B 旧位置）
                         → draggedCurrentIndex 更新
                         → draggedBaseCenterY = B.offset + draggedSize/2  ← 修复点 1
                         → lastSwapTargetKey = swapTargetKey              ← 修复点 3
                         → lastSwapFingerY = fingerY
         → 反向锁定检查：|fingerY - lastSwapFingerY| > draggedSize/2 → 清除锁定
```

## 5. 测试策略

### 5.1 单元测试更新（ReorderAlgorithmsTest.kt）

**修改现有测试**：4 个 `findSwapTarget` 测试用例的期望值需更新（阈值从 minSize 改为 maxSize）

| 测试用例 | 修正前期望 | 修正后期望 |
|---|---|---|
| 等高 70% 重叠 | 返回目标（70% > 50%） | 返回目标（70% > 50%，无变化） |
| 等高 10% 重叠 | null（10% < 50%） | null（10% < 50%，无变化） |
| 变高项重叠 100% | 返回目标（100% > 50%） | **需重新计算**：100% / max(80,200) = 40% < 50% → **null** |
| 仅自身可见 | null | null（无变化） |

**新增测试**：高度差场景验证
- 80px vs 200px，重叠 60px → 60/200=30% < 50% → null
- 80px vs 200px，重叠 110px → 110/200=55% > 50% → 返回目标

### 5.2 手动验证场景

1. 小卡片（80px）拖向大卡片（200px）：交换平稳，无跳动
2. 大卡片（200px）拖向小卡片（80px）：交换平稳，无跳动
3. 等高卡片交换：行为无回归
4. 快速连续拖拽过多个不同高度卡片：每个位置交换稳定
5. 拖拽中手指停留：不触发连续交换
6. 拖拽结束后再次拖拽：锁定状态已清除，行为正常

## 6. 边界情况处理

| 边界情况 | 处理方式 |
|---|---|
| `draggedSize = 0`（布局未完成） | `maxSize > 0` 检查已覆盖，返回 null 不触发交换 |
| `otherInfo = null`（目标项不可见） | 保留原有 `if (otherInfo != null)` 守卫 |
| 手指快速划过多个项 | `lastSwapTargetKey` 在手指移动超过 `draggedSize/2` 后清除，允许继续交换 |
| 拖拽结束时的锁定状态 | finally 块清除 `lastSwapTargetKey`，不影响下次拖拽 |
| 自动滚动时的基线调整 | 保留原有 `draggedBaseCenterY += scrollDelta` 逻辑，与修复点 1 兼容 |

## 7. 不变的部分

- 三层手势分离架构不变（容器拖拽 L1 / 左滑 L2 / 卡片点击 L3）
- `animateItem()` 让位动画不变
- 自动滚动逻辑不变
- 置顶区跨越检测不变
- `onReorder` 回调签名不变
- `VisibleItemInfo` 数据类不变
- `checkPinnedZoneCrossed` 算法不变

## 8. 影响范围

| 文件 | 变更类型 | 影响 |
|---|---|---|
| `ReorderableLazyColumn.kt` | 修改 | 3 个修复点 + 2 个新状态变量 |
| `ReorderAlgorithmsTest.kt` | 修改 | 更新现有测试期望值 |
| `ReorderAlgorithmsTest.kt` | 新增 | 添加高度差场景测试用例 |

## 9. 参考文档

- 原始设计：`docs/superpowers/specs/2026-06-28-待办卡片长按拖拽排序-design.md`
- 实现计划：`docs/superpowers/plans/2026-06-28-待办卡片长按拖拽排序.md`
- 项目记忆：ReorderableLazyColumn 相关教训
