# 长按交互 Bug 修复设计

> 创建日期：2026-06-27
> 状态：已确认，待实施

## 一、问题概述

上一轮"待办卡片长按交互优化"（awaitEachGesture 替换 detectTapGestures）引入了 3 个 bug：

| # | 问题 | 触发场景 |
|---|------|---------|
| 1 | 每次选择卡片都触发震动 | 批量模式下长按（>500ms）选择其他卡片 |
| 2 | 水波纹无法恢复 | 批量模式下选择卡片后，Card 水波纹卡住不消失 |
| 3 | 点击展开按钮触发震动+水波纹 | 点击子待办展开按钮时，外层 Card 也触发手势 |

## 二、根因分析

### 2.1 问题1：每次选择卡片都触发震动

**根因**：`longPressJob` 中无条件触发 `HapticFeedbackManager.performHapticFeedback`，无论是否已在批量模式。

```kotlin
// 当前代码（有问题）
val longPressJob = scope.launch {
    delay(500)
    longPressTriggered = true
    HapticFeedbackManager.performHapticFeedback(...) // 无条件触发
}
```

用户需求：仅首次进入批量模式时震动一次，之后选择其他卡片不震动。

### 2.2 问题2：水波纹无法恢复

**根因**：`scope.launch { interactionSource.emit(...) }` 异步包装 emit 调用，导致 `emit(Release)` 可能在 `onSelectClick()` 触发重组之后才执行。

时序问题：
```
up → scope.launch { emit(Release) }  ← 异步，可能未执行
  → onSelectClick()                   ← 同步，立即触发重组
  → pointerInput key 变化             ← lambda 新实例
  → awaitEachGesture 协程被取消       ← 重组导致
  → finally: terminalEmitted=true     ← 不执行 emit(Cancel)

结果：Press 已发射，Release 未执行，Cancel 也未执行 → 水波纹卡住
```

### 2.3 问题3：点击展开按钮触发震动+水波纹

**根因**：`awaitFirstDown(requireUnconsumed = false)` 接收所有 down 事件，包括点击内层展开按钮 `Surface(onClick)` 的事件。

- 外层 `awaitEachGesture` 被触发 → 启动 `longPressJob`（震动）+ `emit(Press)`（水波纹）
- `onToggleExpand` 触发重组 → `pointerInput` key 变化 → 手势检测器重启
- `emit(Release)` 未执行 → 水波纹卡住

## 三、修复方案

### 3.1 修复1：longPressJob 检查 isBatchMode

在 `longPressJob` 中增加 `isBatchMode` 检查，仅非批量模式时触发震动。

```kotlin
val longPressJob = scope.launch {
    delay(500)
    longPressTriggered = true
    // 仅非批量模式时触发震动（首次进入批量模式）
    if (!isBatchMode) {
        HapticFeedbackManager.performHapticFeedback(
            context = context,
            type = InteractionType.LONG_CLICK,
            enabled = hapticEnabled
        )
    }
}
```

### 3.2 修复2：Channel + LaunchedEffect 替代 scope.launch

用 `Channel<PressInteraction>(Channel.UNLIMITED)` 作为事件队列。`trySend` 是同步非挂起函数，可在 `PointerInputScope` 受限作用域中调用；`LaunchedEffect` 中按顺序收集并 `emit`，独立于手势协程生命周期。

**Composable 层新增**：
```kotlin
val interactionChannel = remember { Channel<PressInteraction>(Channel.UNLIMITED) }
LaunchedEffect(Unit) {
    for (interaction in interactionChannel) {
        interactionSource.emit(interaction)
    }
}
```

**awaitEachGesture 内部替换**：
```kotlin
// 替换 scope.launch { interactionSource.emit(...) }
interactionChannel.trySend(PressInteraction.Press(downPosition))
interactionChannel.trySend(PressInteraction.Release(pressInteraction))
interactionChannel.trySend(PressInteraction.Cancel(pressInteraction))
```

**优势**：
- `trySend` 同步入队，确保 Press/Release/Cancel 顺序正确
- `LaunchedEffect` 独立于 `pointerInput` 协程，不受 key 变化导致的手势检测器重启影响
- `Channel.UNLIMITED` 确保 `trySend` 永不失败

### 3.3 修复3：检查 down 事件是否被内层消费

在 `awaitFirstDown` 后检查事件是否已被内层组件（如展开按钮 Surface）消费，如果被消费则直接 return，不触发外层手势。

```kotlin
val down = awaitFirstDown(requireUnconsumed = false)
// 如果 down 事件已被内层组件消费（如展开按钮 Surface），
// 不触发外层手势（避免震动+水波纹）
if (down.isConsumed) {
    return@awaitEachGesture
}
```

## 四、改动范围

| 文件 | 改动内容 |
|------|---------|
| `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt` | 3 处修复 |

**不改动**：HomeScreen.kt、HomeViewModel.kt、SwipeableTodoBox.kt

## 五、边界情况

| 边界情况 | 处理方式 |
|---------|---------|
| Channel 在 composable 销毁时 | `remember` 自动清理，`LaunchedEffect` 协程取消 |
| `trySend` 返回失败 | `Channel.UNLIMITED` 保证永不失败 |
| 快速连续点击多张卡片 | 每张卡片有独立的 `interactionChannel`，互不干扰 |
| 展开按钮快速点击 | `down.isConsumed` 检查阻止外层手势触发 |
| 批量模式下长按选择 | `isBatchMode` 检查阻止额外震动 |
