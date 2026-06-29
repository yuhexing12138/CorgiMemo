# 待办卡片左滑展开态右滑恢复逻辑

- **日期**：2026-06-29
- **范围**：[SwipeableTodoBox.kt](app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt) + [TodoListItem.kt](app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt)
- **目标**：
  1. 卡片处于"左滑展开"任意状态下，**首次检测到右滑手势**即触发恢复动画
  2. 展开状态下，禁用 4 类点击入口：详情点击 / 子待办展开 / 长按进批量 / 复选框
  3. 关闭动画结束后 **200ms** 才恢复点击能力，避免尾帧误触

---

## 1. 背景与目标

### 1.1 现状

[SwipeableTodoBox.kt](app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt) 当前在 L329-345 已有"完全展开 + dragAmount > 0 → animateTo(0f)"的特殊分支，**仅在完全展开（offset <= -actionsWidthPx）时**才走快速关闭路径。其它情况下右滑仍走 `snapTo` 跟手逻辑（[L347-353](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt#L347-L353)）。

### 1.2 问题

| 编号 | 场景 | 当前行为 | 期望行为 |
|------|------|---------|---------|
| A | 完全展开 → 右滑 | ✅ 走快速关闭分支 | ✅ 保持不变 |
| B | 半展开（onDrag 中 offset 介于 -216dp 和 0 之间） → 右滑 | ❌ 走 snapTo 跟手，抬手才吸附 | ✅ 立即触发关闭动画 |
| C | 展开状态下点卡片 | ✅ 触发 onClick → 跳详情 | ❌ 应当被禁用 |
| D | 展开状态下点子待办展开按钮 | ✅ 触发 onToggleExpand | ❌ 应当被禁用 |
| E | 展开状态下长按卡片 | ✅ 触发 onLongClick → 进批量 | ❌ 应当被禁用 |
| F | 展开状态下点复选框 | ✅ 触发 onToggleComplete | ❌ 应当被禁用 |
| G | 关闭动画尾帧误触 | ❌ 动画结束立即可点 | ✅ 额外延后 200ms 恢复 |

### 1.3 目标

- ✅ "任何展开度 + 首次 dragAmount > 0"立即触发 300ms 弹性归位
- ✅ 展开状态下，4 个点击入口（详情 / 子待办展开 / 长按 / 复选框）全部失灵
- ✅ 关闭动画结束后 **200ms** 才恢复点击
- ✅ 不影响现有左滑展开、互斥展开、fling 关闭、强制收起等所有其它路径
- ✅ 复用现有 `pressFeedback.enabled` 参数（[PressFeedback.kt:77-79](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/PressFeedback.kt#L77-L79)）做禁用

---

## 2. 核心算法

### 2.1 触发源与对应动作

| 编号 | 触发源 | 条件 | 动作 | 动画规格 |
|------|--------|------|------|----------|
| A | 展开态首次右滑 | `cardOffsetX.value < 0f && dragAmount > 0f && restoreJob == null` | `animateTo(0f)` | tween 300ms / ElasticOutEasing |
| B | onDragEnd fling | `velocity.x > 800 px/s` 且 `restoreJob == null` | `animateTo(0f)` 存入 restoreJob | tween 300ms / ElasticOutEasing |
| C | onDragEnd 阈值吸附 | `reveal >= threshold && restoreJob == null` | `animateTo(-actionsWidthPx)` | tween 300ms / ElasticOutEasing |
| D | 父组件强制收起 | `isExpanded` 由 true 变 false（外部 setState） | 走原 `LaunchedEffect(isExpanded)` | tween 300ms / ElasticOutEasing |
| E | 关闭动画完成 | 任意路径触发 | `restoreJob = null` + `onExpandChange(false)` + 200ms 延迟后 `isClickBlocked = false` | — |

### 2.2 关键不变量

- `cardOffsetX.value ∈ [-actionsWidthPx, 0f]`，新逻辑保证"任何 offset < 0f 时右滑都触发恢复"
- `restoreJob.value` 非空时，所有 drag 事件被忽略（动画进行中）
- `isClickBlocked = true` 时，4 个点击入口全部 no-op
- `isClickBlocked` 仅在 `isExpanded = true` 期间为 true，`isExpanded` 变 false 后 200ms 释放
- 现有 4 条关闭路径（**新逻辑**、fling、阈值吸附、强制收起）互不冲突，由 `restoreJob` 协调防重入

### 2.3 状态机

```
┌─────────────────┐   左滑过阈值 + 松手   ┌─────────────────────┐
│ 收起 (offset=0)  │ ────────────────────→ │ 展开 (offset=-216)  │
│ isExpanded=false │                       │ isExpanded=true     │
│ isClickBlocked=F │                       │ isClickBlocked=T    │
└─────────────────┘                       └─────────────────────┘
        ↑                                          │
        │           首次 dragAmount>0              │
        │ ◄────────────────────────────────────────┘
        │  (新逻辑：触发 animateTo(0f))
        │
        │  动画完成 + 200ms 延迟
        │  isClickBlocked = false
        ↓
┌─────────────────┐
│  收起 (同上)     │
└─────────────────┘
```

### 2.4 核心改进：用 `restoreJob` 跟踪恢复动画

**旧实现问题**：
- `cardOffsetX.value <= -actionsWidthPx` 条件仅覆盖"完全展开"
- 无防重入机制，连续 drag 事件可能启动多个 animateTo 协程冲突
- onDragEnd / onDragCancel 不知道"关闭动画是否在进行中"，可能误设 isDragging = false

**新实现**：
```kotlin
val restoreJob = remember { mutableStateOf<Job?>(null) }

// 在 onDrag 中
if (cardOffsetX.value < 0f && dragAmount > 0f && restoreJob.value == null) {
    restoreJob.value = coroutineScope.launch {
        cardOffsetX.animateTo(0f, tween(300, easing = easing))
        onExpandChange(false)
        isDragging = false
        restoreJob.value = null
    }
} else if (restoreJob.value == null) {
    // 仅在"未在恢复中"时走 snapTo 跟手
    coroutineScope.launch {
        val newOffset = (cardOffsetX.value + dragAmount)
            .coerceIn(-actionsWidthPx, 0f)
        cardOffsetX.snapTo(newOffset)
    }
}
```

---

## 3. 组件改动

### 3.1 [SwipeableTodoBox.kt](app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt)

#### 3.1.1 新增状态（L156 附近，紧邻 `cardOffsetX` 声明）

```kotlin
val cardOffsetX = remember { Animatable(0f) }
val restoreJob = remember { mutableStateOf<Job?>(null) }   // ← 新增
var isClickBlocked by remember { mutableStateOf(false) }    // ← 新增
```

#### 3.1.2 新增 LaunchedEffect 同步 isClickBlocked（L183 之后）

```kotlin
LaunchedEffect(isExpanded) {
    if (isExpanded) {
        isClickBlocked = true
    } else if (isClickBlocked) {
        delay(200L)
        isClickBlocked = false
    }
}
```

#### 3.1.3 修改 onDrag 中的快速关闭分支（L329-353）

```kotlin
// 改前
if (cardOffsetX.value <= -actionsWidthPx && dragAmount > 0f) {
    coroutineScope.launch {
        cardOffsetX.animateTo(0f, tween(300, easing = easing))
        onExpandChange(false)
        isDragging = false
    }
} else {
    coroutineScope.launch {
        val newOffset = (cardOffsetX.value + dragAmount)
            .coerceIn(-actionsWidthPx, 0f)
        cardOffsetX.snapTo(newOffset)
    }
}

// 改后
if (cardOffsetX.value < 0f && dragAmount > 0f && restoreJob.value == null) {
    restoreJob.value = coroutineScope.launch {
        cardOffsetX.animateTo(0f, tween(300, easing = easing))
        onExpandChange(false)
        isDragging = false
        restoreJob.value = null
    }
} else if (restoreJob.value == null) {
    coroutineScope.launch {
        val newOffset = (cardOffsetX.value + dragAmount)
            .coerceIn(-actionsWidthPx, 0f)
        cardOffsetX.snapTo(newOffset)
    }
}
```

#### 3.1.4 修改 onDragEnd（L241-283）

将 `coroutineScope.launch { ... }` 改为将 Job 存入 `restoreJob`，并在最后清空：

```kotlin
onDragEnd = {
    if (restoreJob.value != null) {
        // 恢复动画进行中，等它完成
        coroutineScope.launch { restoreJob.value?.join() }
    } else {
        val velocity = velocityTracker.calculateVelocity()
        if (velocity.x > flingVelocityThresholdPx) {
            // 快速右滑：改为存入 restoreJob
            restoreJob.value = coroutineScope.launch {
                cardOffsetX.animateTo(0f, tween(300, easing = easing))
                onExpandChange(false)
                isDragging = false
                restoreJob.value = null
            }
        } else {
            val currentReveal = -cardOffsetX.value
            val target = if (currentReveal >= thresholdPx) -actionsWidthPx else 0f
            restoreJob.value = coroutineScope.launch {
                cardOffsetX.animateTo(target, tween(300, easing = easing))
                onExpandChange(target < 0f)
                isDragging = false
                restoreJob.value = null
            }
        }
    }
}
```

#### 3.1.5 修改 onDragCancel（L285-322）

模式同 onDragEnd。

#### 3.1.6 修改 content lambda 签名

```kotlin
// 改前
content: @Composable () -> Unit

// 改后
content: @Composable (isClickBlocked: Boolean) -> Unit

// 调用处
CompositionLocalProvider(LocalContentIndication provides !isDragging) {
    content(isClickBlocked)
}
```

#### 3.1.7 需要的 import

```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
```

### 3.2 [TodoListItem.kt](app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt)

#### 3.2.1 新增参数（L97 附近）

```kotlin
@Composable
fun TodoListItem(
    todo: TodoItem,
    ...
    isClickBlocked: Boolean = false   // ← 新增
) { ... }
```

#### 3.2.2 pressFeedback 启用控制（L184）

新增 `enabled` 参数控制整个按压反馈是否启用：

```kotlin
.pressFeedback(
    interactionSource = interactionSource,
    scale = cardScale,
    isBatchMode = isBatchMode,
    enabled = !isClickBlocked,   // ← 新增：展开时整个按压反馈失灵
    onTap = { ... },
    onLongClick = { ... },
    scaleDown = 0.92f,
    scaleDownDurationMs = 60,
    scaleUpDurationMs = 80,
    isDragActive = { isDragActive }
)
```

> `pressFeedback` 已有 `enabled: Boolean = true` 参数（[PressFeedback.kt:77-79](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/PressFeedback.kt#L77-L79)），`enabled = false` 时直接返回原 modifier，零开销禁用 onTap / onLongClick / scale 反馈。

#### 3.2.3 CircularCheckbox onCheckedChange 屏蔽（L240-250）

```kotlin
CircularCheckbox(
    checked = if (isBatchMode) isSelected else todo.status == 1,
    onCheckedChange = { isChecked ->
        if (isClickBlocked) return@CircularCheckbox   // ← 新增
        if (isBatchMode) onSelectClick()
        else onToggleComplete(todo.id, isChecked)
    },
    dimmed = todo.status == 1,
    modifier = Modifier.padding(end = 12.dp)
)
```

#### 3.2.4 展开/收起按钮 Surface onClick 屏蔽（L489）

```kotlin
Surface(
    onClick = { if (!isClickBlocked) onToggleExpand() },   // ← 改这里
    shape = androidx.compose.foundation.shape.CircleShape,
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 2.dp,
    modifier = Modifier.size(32.dp)
) { ... }
```

#### 3.2.5 SubTaskCheckbox 禁用态透传（L524-528）

```kotlin
SubTaskInTodoListItem(
    subTask = subTask,
    isParentCompleted = todo.status == 1,
    isEnabled = !isBatchMode && !isClickBlocked,   // ← 改这里
    onToggleComplete = { onToggleSubTask(subTask.id) },
    onDisabledLongPress = { ... }
)
```

> 复用现有 `isEnabled` 路径——`SubTaskCheckbox` 已有 disabled 视觉降权（[TodoListItem.kt:854-855](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L854-L855)），无需新增逻辑。

### 3.3 [HomeScreen.kt](app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt)

#### 3.3.1 透传 isClickBlocked 给 TodoListItem

```kotlin
SwipeableTodoBox(
    ...
) { isClickBlocked ->   // ← 改 content lambda 解构
    TodoListItem(
        todo = todo,
        ...
        isClickBlocked = isClickBlocked   // ← 透传
    )
}
```

---

## 4. 边界情况处理

| 场景 | 行为 |
|------|------|
| 半展开态（onDrag 进行中）首次右滑 | `cardOffsetX.value < 0f` 条件成立 → 触发恢复 |
| 恢复动画进行中用户继续左滑 | `restoreJob != null` → 忽略所有 drag 事件，动画继续 |
| 恢复动画进行中用户松手 | `onDragEnd` 检测 `restoreJob != null` → `join()` 等动画完成 |
| 快速右滑 fling | 现在 fling 走原路径（存入 restoreJob） |
| 父组件强制收起（互斥展开） | 走原 `LaunchedEffect(isExpanded)` 路径，**不走新逻辑**——因为是父组件 setState 触发的，不是用户手势 |
| 批量模式下右滑 | `isEnabled = !isBatchMode` 守护，新逻辑不触发 |
| 关闭动画尾帧点击 | `isClickBlocked = true` 持续到动画完成 + 200ms，期间 4 个入口全部 no-op |
| 拖动排序中右滑 | `isEnabled = !dragActive` 守护，新逻辑不触发 |

---

## 5. 测试要点

| 测试项 | 期望结果 |
|--------|---------|
| 完全展开 → 右滑 | 卡片 300ms 弹性归位 |
| 半展开（拖到一半就右滑）| 卡片从当前位置 300ms 弹性归位 |
| 完全展开 → 点击详情 | 无反应（isClickBlocked） |
| 完全展开 → 长按 | 无反应 |
| 完全展开 → 点复选框 | 无反应 |
| 完全展开 → 点子待办展开按钮 | 无反应 |
| 完全展开 → 点子待办勾选框 | 无反应 |
| 右滑关闭后立即点详情 | 200ms 内无效，之后正常 |
| 关闭过程中用户反向左滑 | 关闭动画继续完成，忽略反向滑动 |
| 多个卡片互斥展开（A 展开时左滑 B）| B 触发展开的同时 A 自动收起（行为不变） |
| 批量模式下左滑/右滑 | 全部不响应（行为不变） |
| 长按拖拽排序时左滑 | 不响应（行为不变） |
| 动画进行中切到后台再回前台 | 动画继续，restoreJob 不丢失（协程随 ViewModelScope 重启而保留） |

---

## 6. 兼容性

- 现有 `onExpandChange` 调用方（HomeScreen.kt:826-829）**不变**
- 现有 4 条关闭路径（**新逻辑**、fling、阈值吸附、强制收起）**全部保留**，由 `restoreJob` 协调防重入
- 现有 `isDragging` / `LocalContentIndication` / `velocityTracker` **全部保留**
- 不影响 [HomeScreen.kt](app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) 其它逻辑

---

## 7. 后续可优化方向

- **触觉反馈**：右滑触发恢复时给一个轻微 haptic 反馈（与长按 / 切换完成一致）
- **视觉降权**（如未来需要）：展开时整张卡片 `graphicsLayer.alpha = 0.7f` 暗示"不可点击"——本次未做以保持视觉简洁
- **手势方向自定义**：暴露参数让调用方配置"哪些方向算恢复"（如允许左滑恢复时禁用右滑恢复）
