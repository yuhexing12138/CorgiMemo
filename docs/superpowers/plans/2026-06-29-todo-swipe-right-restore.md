# 待办卡片左滑展开态右滑恢复 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 SwipeableTodoBox 已有"完全展开右滑快速关闭"基础上扩展为"任何展开度首次右滑即恢复"，并在展开态屏蔽 4 类点击入口，关闭动画后 200ms 恢复点击。

**Architecture:** 在 `SwipeableTodoBox` 内部用 `mutableStateOf<Job?>(null)` 跟踪"恢复动画"协程防止重入，新增 `isClickBlocked` 状态通过 `LaunchedEffect(isExpanded)` 与 200ms 延迟协调；`TodoListItem` 接收 `isClickBlocked` 参数，复用现有 `pressFeedback.enabled` 与 `isEnabled` 路径做点击屏蔽。

**Tech Stack:** Jetpack Compose（BOM 2026.04.01 + Compose 1.9.2）、Kotlin、Android、kotlinx-coroutines Job

**Reference Spec:** [docs/superpowers/specs/2026-06-29-todo-swipe-right-restore-design.md](../specs/2026-06-29-todo-swipe-right-restore-design.md)

**关键约束（项目规则）**：
- ⚠️ 编译验证前**必须**通过 AskUserQuestion 询问用户
- ⚠️ 每个任务完成后**必须**通过 AskUserQuestion 询问是否 git 提交
- ⚠️ 提交信息必须中文，格式 `type(scope): 描述`
- ⚠️ 解释每一步操作/代码变更背后的原因

---

## 文件结构

| 文件 | 变化 | 职责 |
|------|------|------|
| `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt` | 修改 | 新增 `restoreJob` / `isClickBlocked` 状态，改 onDrag / onDragEnd / onDragCancel 分支，改 content 签名 |
| `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt` | 修改 | 新增 `isClickBlocked` 参数；4 处点击入口接入屏蔽；新增 `delay` import |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 修改 | 适配 `SwipeableTodoBox.content` 新签名，向 `TodoListItem` 透传 `isClickBlocked` |

无新文件、无单元测试（JVM 单元测试无法覆盖 Compose 手势行为；项目现有 UI 改动均采用手动 UI 测试，见 [2026-06-24-todo-card-swipe.md](2026-06-24-todo-card-swipe.md)）。

---

## Task 1: SwipeableTodoBox — 新增 restoreJob 与 isClickBlocked 状态

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt:1-52`（import 段）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt:145-156`（状态声明）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt:182-190`（LaunchedEffect 段）

**原因**：`restoreJob` 跟踪"恢复动画"协程（防重入 + 等待完成）；`isClickBlocked` 控制 TodoListItem 的点击屏蔽。两者都是新增状态，不影响现有逻辑。

- [ ] **Step 1.1: 在 import 段新增 `Job` 和 `delay`**

打开 [SwipeableTodoBox.kt](app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt)，定位到 L51 附近的 import 段：

```kotlin
import kotlinx.coroutines.launch
```

在它**上方**新增两行（按字母顺序排列在 `launch` 之前）：

```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
```

> **原因**：`Job` 用于类型标注 `restoreJob: Job?`；`delay` 用于关闭动画后 200ms 等待点击恢复（spec §3.1.2）。

- [ ] **Step 1.2: 新增 restoreJob 状态**

定位到 L156 附近：

```kotlin
// 卡片位移状态（px，范围 -actionsWidthPx..0）
val cardOffsetX = remember { Animatable(0f) }
```

在它**正下方**新增一行：

```kotlin
// 卡片位移状态（px，范围 -actionsWidthPx..0）
val cardOffsetX = remember { Animatable(0f) }

// 恢复动画协程引用：用于在右滑首帧后跟踪正在跑的 animateTo(0f) 协程，
// 防止 drag / onDragEnd / onDragCancel 重复启动新协程
val restoreJob = remember { mutableStateOf<Job?>(null) }
```

> **原因**：`mutableStateOf` 包裹 `Job?` 允许在协程内 `restoreJob.value = null` 触发重组，与现有 `var isDragging by remember { mutableStateOf(false) }` 模式一致。

- [ ] **Step 1.3: 新增 isClickBlocked 状态**

定位到 L145：

```kotlin
var isDragging by remember { mutableStateOf(false) }
```

在它**正下方**新增一行：

```kotlin
var isDragging by remember { mutableStateOf(false) }

// 展开期间屏蔽卡片内 4 类点击入口（详情 / 子待办展开 / 长按 / 复选框），
// 关闭动画结束后 200ms 才解除屏蔽（避免尾帧误触）
var isClickBlocked by remember { mutableStateOf(false) }
```

> **原因**：与现有 `isDragging` 同段，便于阅读时关联两个状态。

- [ ] **Step 1.4: 新增 LaunchedEffect 同步 isClickBlocked**

定位到 L183-190 的 `LaunchedEffect(isExpanded, isEnabled)` 块：

```kotlin
// 父组件强制收起时同步动画
LaunchedEffect(isExpanded, isEnabled) {
    if (!isExpanded && cardOffsetX.value < 0f && isEnabled) {
        cardOffsetX.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = durationMs, easing = easing)
        )
    }
}
```

在它**正下方**新增一个 `LaunchedEffect`：

```kotlin
// 同步 isClickBlocked 与 isExpanded：isExpanded 变 true 立即屏蔽；
// isExpanded 变 false 后延后 200ms 解除（让关闭动画跑完 + 留出尾帧安全余量）
LaunchedEffect(isExpanded) {
    if (isExpanded) {
        isClickBlocked = true
    } else if (isClickBlocked) {
        delay(200L)
        isClickBlocked = false
    }
}
```

> **原因**：`delay(200L)` 后再 `isClickBlocked = false` 避免用户在动画尾帧误点。`else if (isClickBlocked)` 守护首次 Composable 组合时（`isExpanded=false, isClickBlocked=false`）不进入 delay 分支。

- [ ] **Step 1.5: 询问用户是否 git 提交**

> **项目规则要求**：每个任务完成后必须询问。

向用户发出询问："是否对 Task 1 的修改进行 git 提交？"

- 若用户选择提交：用 `git add app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt` + `git commit -m "refactor(todo): SwipeableTodoBox 新增 restoreJob 与 isClickBlocked 状态"`
- 若用户选择跳过：继续 Task 2

---

## Task 2: SwipeableTodoBox — 改造 onDrag 右滑分支

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt:323-354`（onDrag 块内的 if-else）

**原因**：spec §3.1.3 核心改动 —— 把"完全展开 + dragAmount > 0"扩展为"任何展开 + dragAmount > 0"，并加 `restoreJob` 防重入。

- [ ] **Step 2.1: 修改 onDrag 内的快速关闭分支**

定位到 L329-353 的 if-else：

```kotlin
if (cardOffsetX.value <= -actionsWidthPx && dragAmount > 0f) {
    // 关键：onExpandChange(false) 延后到 animateTo 完成后调用，
    // 保证整个关闭动画期间 swipeActionExpanded 仍为 true，
    // MainScreen 的 gesturesEnabled 保持 false，
    // 父级 ModalNavigationDrawer 不会响应右滑事件
    coroutineScope.launch {
        cardOffsetX.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = easing
            )
        )
        onExpandChange(false)
        // 关闭动画结束：恢复 indication
        isDragging = false
    }
} else {
    // 未完全展开：正常 snapTo 跟手
    coroutineScope.launch {
        val newOffset = (cardOffsetX.value + dragAmount)
            .coerceIn(-actionsWidthPx, 0f)
        cardOffsetX.snapTo(newOffset)
    }
}
```

**替换为**：

```kotlin
// 关键：任何展开度（offset < 0f）+ 首次 dragAmount > 0 立即触发恢复
// 1) 触发条件从 "完全展开" 扩展为 "任何展开度"
// 2) 用 restoreJob 防重入：协程在跑时后续 drag 事件全部忽略
if (cardOffsetX.value < 0f && dragAmount > 0f && restoreJob.value == null) {
    // 关键：onExpandChange(false) 延后到 animateTo 完成后调用，
    // 保证整个关闭动画期间 swipeActionExpanded 仍为 true，
    // MainScreen 的 gesturesEnabled 保持 false，
    // 父级 ModalNavigationDrawer 不会响应右滑事件
    restoreJob.value = coroutineScope.launch {
        cardOffsetX.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = easing
            )
        )
        onExpandChange(false)
        // 关闭动画结束：恢复 indication
        isDragging = false
        // 协程完成：清空 restoreJob 允许下次拖动
        restoreJob.value = null
    }
} else if (restoreJob.value == null) {
    // 未在恢复中：正常 snapTo 跟手（左滑继续展开 / 已经收起时右滑无效）
    coroutineScope.launch {
        val newOffset = (cardOffsetX.value + dragAmount)
            .coerceIn(-actionsWidthPx, 0f)
        cardOffsetX.snapTo(newOffset)
    }
}
// 注：restoreJob.value != null 的 else 分支不处理，
// 表示恢复动画进行中忽略所有 drag 事件
```

> **原因**：
> - 条件 `cardOffsetX.value < 0f` 把"完全展开"扩展为"任何展开度"
> - `restoreJob.value == null` 防止 onDrag 重复触发多次 animateTo
> - `else if` 加守卫确保恢复动画期间不再走 snapTo（避免 snapTo 与正在跑的 animateTo 冲突）
> - 协程内最后一行 `restoreJob.value = null` 是"清理"，让下一次手势可以重新触发

- [ ] **Step 2.2: 询问用户是否 git 提交**

向用户发出询问："是否对 Task 2 的修改进行 git 提交？"

- 提交信息建议：`feat(todo): 任何展开度右滑首帧触发恢复动画`
- 若用户跳过：继续 Task 3

---

## Task 3: SwipeableTodoBox — 改造 onDragEnd 与 onDragCancel

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt:241-322`（onDragEnd + onDragCancel 块）

**原因**：spec §3.1.4 / §3.1.5 —— fling / 阈值吸附 / drag cancel 三种关闭路径都需要把协程存入 `restoreJob` 防止重入。

- [ ] **Step 3.1: 修改 onDragEnd 块**

定位到 L241-283 的 `onDragEnd = { ... }` 完整 lambda，**替换为**：

```kotlin
onDragEnd = {
    // 关键：恢复动画进行中，仅等待其完成，不要启动新动画
    if (restoreJob.value != null) {
        coroutineScope.launch { restoreJob.value?.join() }
    } else {
        // 计算抬手时的 x 方向速度（px/s）
        val velocity = velocityTracker.calculateVelocity()
        // 关键：fling right（快速右滑）时，立即关闭卡片
        // 速度为正表示向右滑动，超过阈值即视为快速右滑
        if (velocity.x > flingVelocityThresholdPx) {
            // 存入 restoreJob 防止与正在跑的动画冲突
            restoreJob.value = coroutineScope.launch {
                cardOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = durationMs,
                        easing = easing
                    )
                )
                onExpandChange(false)
                // 归位动画结束：恢复 indication
                isDragging = false
                restoreJob.value = null
            }
        } else {
            // 普通抬手：按阈值吸附
            val currentReveal = -cardOffsetX.value
            val target = if (currentReveal >= thresholdPx) {
                -actionsWidthPx
            } else {
                0f
            }
            // 关键：onExpandChange 延后到 animateTo 之后调用，
            // 避免动画期间 swipeActionExpanded 被错误置为 false，
            // 导致 MainScreen 的 gesturesEnabled 提前恢复 true，
            // 让右滑事件被父级 ModalNavigationDrawer 识别为打开 Drawer
            // 存入 restoreJob 防止与正在跑的动画冲突
            restoreJob.value = coroutineScope.launch {
                cardOffsetX.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = durationMs,
                        easing = easing
                    )
                )
                onExpandChange(target < 0f)
                // 吸附/归位动画结束：恢复 indication
                isDragging = false
                restoreJob.value = null
            }
        }
    }
},
```

> **原因**：
> - 顶部 `if (restoreJob.value != null) { join() }` 守护"onDrag 在 Task 2 已经启动了恢复动画"的情况
> - 两个分支的 `coroutineScope.launch` 都改为 `restoreJob.value = coroutineScope.launch { ... }` + 协程末尾 `restoreJob.value = null`

- [ ] **Step 3.2: 修改 onDragCancel 块**

定位到 L285-322 的 `onDragCancel = { ... }` 完整 lambda，**替换为**：

```kotlin
onDragCancel = {
    // 关键：恢复动画进行中，仅等待其完成
    if (restoreJob.value != null) {
        coroutineScope.launch { restoreJob.value?.join() }
    } else {
        // 取消手势时同样按速度判断（极少见，但保持一致）
        val velocity = velocityTracker.calculateVelocity()
        if (velocity.x > flingVelocityThresholdPx) {
            restoreJob.value = coroutineScope.launch {
                cardOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = durationMs,
                        easing = easing
                    )
                )
                onExpandChange(false)
                // 归位动画结束：恢复 indication
                isDragging = false
                restoreJob.value = null
            }
        } else {
            val currentReveal = -cardOffsetX.value
            val target = if (currentReveal >= thresholdPx) {
                -actionsWidthPx
            } else {
                0f
            }
            // 关键：onExpandChange 延后到 animateTo 之后
            restoreJob.value = coroutineScope.launch {
                cardOffsetX.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = durationMs,
                        easing = easing
                    )
                )
                onExpandChange(target < 0f)
                // 吸附/归位动画结束：恢复 indication
                isDragging = false
                restoreJob.value = null
            }
        }
    }
}
```

> **原因**：与 onDragEnd 完全对称，处理手势被父级意外中断的情况。

- [ ] **Step 3.3: 询问用户是否 git 提交**

向用户发出询问："是否对 Task 3 的修改进行 git 提交？"

- 提交信息建议：`refactor(todo): onDragEnd / onDragCancel 接入 restoreJob 防重入`
- 若用户跳过：继续 Task 4

---

## Task 4: SwipeableTodoBox — 改造 content lambda 签名

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt:127`（content 形参）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt:375`（content 调用）

**原因**：spec §3.1.6 —— 把 `isClickBlocked` 透传给 TodoListItem，需要 content lambda 接收一个参数。

- [ ] **Step 4.1: 修改 content 形参签名**

定位到 L127：

```kotlin
content: @Composable () -> Unit
```

**替换为**：

```kotlin
content: @Composable (isClickBlocked: Boolean) -> Unit
```

- [ ] **Step 4.2: 修改 content 调用**

定位到 L374-376：

```kotlin
CompositionLocalProvider(LocalContentIndication provides !isDragging) {
    content()
}
```

**替换为**：

```kotlin
CompositionLocalProvider(LocalContentIndication provides !isDragging) {
    content(isClickBlocked)
}
```

> **原因**：把 `isClickBlocked` 作为参数传给 content，TodoListItem 接收后决定是否屏蔽点击。

- [ ] **Step 4.3: 询问用户是否 git 提交**

向用户发出询问："是否对 Task 4 的修改进行 git 提交？"

- 提交信息建议：`refactor(todo): SwipeableTodoBox.content 透传 isClickBlocked`
- 若用户跳过：继续 Task 5

> **重要提示**：到此 SwipeableTodoBox 修改完毕，但**尚未调用编译验证**。在进入 Task 5 前询问用户是否需要编译一次以确保 SwipeableTodoBox 单独编译通过。

---

## Task 5: TodoListItem — 新增 isClickBlocked 参数并接入 4 处点击入口

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:67`（import 段）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:97-122`（参数段）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:184-212`（pressFeedback）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:238-250`（CircularCheckbox）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:487-507`（展开/收起 Surface）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:518-536`（SubTaskInTodoListItem）

**原因**：spec §3.2 —— TodoListItem 接收 `isClickBlocked` 后需要屏蔽 4 个点击入口。

- [ ] **Step 5.1: 在 import 段确认 kotlinx.coroutines.delay 已存在**

打开 [TodoListItem.kt](app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt)，搜索 `import kotlinx.coroutines.delay`，确认该 import 在 L67 处已存在。若不存在则新增（实际 L67 已存在，本步骤通常为 NO-OP）。

> **说明**：本任务不引入新 import，但需确认现有 import 完整，避免后续步骤编译失败。

- [ ] **Step 5.2: 在 TodoListItem 函数签名中新增 isClickBlocked 参数**

定位到 L96-122 的 `fun TodoListItem(...)` 形参段，在 `hapticEnabled: Boolean = true,` **之后**新增一行：

```kotlin
hapticEnabled: Boolean = true,
/** 左滑操作面板是否展开（true 时屏蔽详情点击 / 子待办展开 / 长按 / 复选框） */
isClickBlocked: Boolean = false,
```

- [ ] **Step 5.3: pressFeedback 启用控制**

定位到 L184-212 的 `pressFeedback(...)` 调用。在 `isBatchMode = isBatchMode,` **之后**新增一行：

```kotlin
.pressFeedback(
    interactionSource = interactionSource,
    scale = cardScale,
    isBatchMode = isBatchMode,
    enabled = !isClickBlocked,   // ← 新增：左滑操作面板展开时屏蔽整个按压反馈
    onTap = {
        // 短按：根据批量模式分发
        if (isBatchMode) {
            onSelectClick()
        } else {
            onClick()
        }
    },
    onLongClick = {
        // 长按：仅非批量模式时触发震动反馈
        if (!isBatchMode) {
            HapticFeedbackManager.performHapticFeedback(
                context = context,
                type = InteractionType.LONG_CLICK,
                enabled = hapticEnabled
            )
        }
        onLongClick()
    },
    scaleDown = 0.92f,
    scaleDownDurationMs = 60,
    scaleUpDurationMs = 80,
    // 拖拽协调：ReorderableLazyColumn 启动拖拽时让位
    isDragActive = { isDragActive }
)
```

> **原因**：`pressFeedback` 已有 `enabled: Boolean = true` 参数（[PressFeedback.kt:77-79](../specs/2026-06-29-todo-swipe-right-restore-design.md)），`enabled = false` 时直接返回原 modifier，零开销禁用 onTap / onLongClick / scale 反馈。无需改 `onTap` / `onLongClick` 内部逻辑。

- [ ] **Step 5.4: CircularCheckbox onCheckedChange 屏蔽**

定位到 L238-250 的 `CircularCheckbox(...)` 调用：

```kotlin
CircularCheckbox(
    checked = if (isBatchMode) isSelected else todo.status == 1,
    onCheckedChange = { isChecked ->
        if (isBatchMode) {
            onSelectClick()
        } else {
            onToggleComplete(todo.id, isChecked)
        }
    },
    // 已完成态视觉降权：勾选框变淡（保持橙色系仅降深度）
    dimmed = todo.status == 1,
    modifier = Modifier.padding(end = 12.dp)
)
```

**替换为**：

```kotlin
CircularCheckbox(
    checked = if (isBatchMode) isSelected else todo.status == 1,
    onCheckedChange = { isChecked ->
        // 左滑操作面板展开时屏蔽复选框点击
        if (isClickBlocked) return@CircularCheckbox
        if (isBatchMode) {
            onSelectClick()
        } else {
            onToggleComplete(todo.id, isChecked)
        }
    },
    // 已完成态视觉降权：勾选框变淡（保持橙色系仅降深度）
    dimmed = todo.status == 1,
    modifier = Modifier.padding(end = 12.dp)
)
```

> **原因**：`return@CircularCheckbox` 早返回，避免在 onCheckedChange 内做复杂的 if-else 嵌套。

- [ ] **Step 5.5: 展开/收起按钮 Surface onClick 屏蔽**

定位到 L488-494 的 `Surface(...)` 调用：

```kotlin
Surface(
    onClick = onToggleExpand,
    shape = androidx.compose.foundation.shape.CircleShape,
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 2.dp,
    modifier = Modifier.size(32.dp)
)
```

**替换为**：

```kotlin
Surface(
    onClick = { if (!isClickBlocked) onToggleExpand() },
    shape = androidx.compose.foundation.shape.CircleShape,
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 2.dp,
    modifier = Modifier.size(32.dp)
)
```

> **原因**：用 if 守护 onToggleExpand 的调用，左滑操作面板展开时按钮点击空操作。

- [ ] **Step 5.6: SubTaskInTodoListItem 透传 isClickBlocked 到 isEnabled**

定位到 L518-536 的 `SubTaskInTodoListItem(...)` 调用：

```kotlin
subTasks.forEach { subTask ->
    SubTaskInTodoListItem(
        subTask = subTask,
        isParentCompleted = todo.status == 1,
        // 关键：多选模式下子任务勾选框不可点击
        // - 仅可查看，不可切换完成状态
        // - 视觉上 alpha 降低，提供 disabled 反馈
        isEnabled = !isBatchMode,
        onToggleComplete = { onToggleSubTask(subTask.id) },
        // 多选模式下长按子任务勾选框，弹 Toast 提示用户先退出多选模式
        // 文案来自 strings.xml，支持中英文等多语言
        onDisabledLongPress = {
            Toast.makeText(
                context,
                exitBatchModeHint,
                Toast.LENGTH_SHORT
            ).show()
        }
    )
    if (subTask != subTasks.last()) {
        Spacer(modifier = Modifier.height(4.dp))
    }
}
```

**替换 `isEnabled` 行**为：

```kotlin
        // 关键：多选模式下子任务勾选框不可点击
        // - 仅可查看，不可切换完成状态
        // - 视觉上 alpha 降低，提供 disabled 反馈
        // 左滑操作面板展开时也屏蔽（与父卡片保持一致）
        isEnabled = !isBatchMode && !isClickBlocked,
```

> **原因**：复用现有 `isEnabled` 路径——`SubTaskCheckbox` 已有 disabled 视觉降权（[TodoListItem.kt:854-855](../specs/2026-06-29-todo-swipe-right-restore-design.md)），无需新增逻辑。

- [ ] **Step 5.7: 询问用户是否 git 提交**

向用户发出询问："是否对 Task 5 的修改进行 git 提交？"

- 提交信息建议：`feat(todo): TodoListItem 接入 isClickBlocked 屏蔽 4 类点击入口`
- 若用户跳过：继续 Task 6

---

## Task 6: HomeScreen — 适配新 content 签名并透传 isClickBlocked

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:822-839`（SwipeableTodoBox 调用段）

**原因**：spec §3.3 —— `SwipeableTodoBox.content` 签名变了（从 `() -> Unit` 改为 `(Boolean) -> Unit`），需要解构新参数并透传给 `TodoListItem`。

- [ ] **Step 6.1: 修改 SwipeableTodoBox 调用段**

定位到 L822-839 的 `SwipeableTodoBox(...) { TodoListItem(...) }` 整段：

```kotlin
SwipeableTodoBox(
    modifier = Modifier.padding(1.dp),
    isEnabled = !isBatchMode && !dragActive,
    isExpanded = swipeExpandedTodoId == todo.id,
    onExpandChange = { expanded ->
        swipeExpandedTodoId = if (expanded) todo.id else null
        viewModel.setSwipeActionExpanded(expanded)
    },
    onShareClick = {
        shareTodoAsImage(context, todo, categories)
    },
    onPinClick = {
        viewModel.togglePin(todo.id)
    },
    onDeleteClick = {
        pendingDeleteId = todo.id
    }
) {
    TodoListItem(
        todo = todo,
        ...
    )
}
```

**替换为**（注意 content lambda 的形参从 `{}` 改为 `{ isClickBlocked ->`）：

```kotlin
SwipeableTodoBox(
    modifier = Modifier.padding(1.dp),
    isEnabled = !isBatchMode && !dragActive,
    isExpanded = swipeExpandedTodoId == todo.id,
    onExpandChange = { expanded ->
        swipeExpandedTodoId = if (expanded) todo.id else null
        viewModel.setSwipeActionExpanded(expanded)
    },
    onShareClick = {
        shareTodoAsImage(context, todo, categories)
    },
    onPinClick = {
        viewModel.togglePin(todo.id)
    },
    onDeleteClick = {
        pendingDeleteId = todo.id
    }
) { isClickBlocked ->
    TodoListItem(
        todo = todo,
        ...
        isClickBlocked = isClickBlocked,
        ...
    )
}
```

> **修改点**：
> 1. content lambda 形参从 `{}` 改为 `{ isClickBlocked ->`
> 2. TodoListItem 调用新增 `isClickBlocked = isClickBlocked,` 一行

- [ ] **Step 6.2: 询问用户是否 git 提交**

向用户发出询问："是否对 Task 6 的修改进行 git 提交？"

- 提交信息建议：`feat(todo): HomeScreen 适配新 content 签名并透传 isClickBlocked`
- 若用户跳过：继续 Task 7

---

## Task 7: 完整编译验证

**原因**：所有代码改动完成后，必须验证整个项目能正常编译。本项目规则要求编译验证前必须询问用户。

- [ ] **Step 7.1: 询问用户是否进行完整编译**

> **项目规则要求**：编译验证前必须询问。

向用户发出询问：

"目前已完成所有代码改动：
- `SwipeableTodoBox.kt` 新增 `restoreJob` / `isClickBlocked` 状态 + 3 个 drag 分支改造 + content 签名改造
- `TodoListItem.kt` 新增 `isClickBlocked` 参数 + 4 处点击入口屏蔽
- `HomeScreen.kt` 适配新签名

是否进行 `./gradlew :app:assembleDebug` 完整编译验证？"

- 若用户选择编译：执行 `./gradlew :app:assembleDebug`，观察输出
  - 若成功（`BUILD SUCCESSFUL`）：继续 Step 7.2
  - 若失败：分析错误并修复后重新编译
- 若用户跳过：跳到 Task 8

- [ ] **Step 7.2: 处理编译错误（如有）**

若编译失败，按错误信息回到对应任务修复。常见错误：
- `Unresolved reference: Job` → Task 1.1 未执行
- `Unresolved reference: delay` → Task 1.1 未执行
- `Type mismatch` on `content` lambda → Task 4.1 或 Task 6.1 未执行
- `'invoke' is not found` on `TodoListItem` → Task 5.2 未传 `isClickBlocked`

修复后回到 Step 7.1 重新编译。

---

## Task 8: 手动 UI 测试

**原因**：本任务核心是 Compose 手势行为，JVM 单元测试无法覆盖；项目现有 UI 改动均采用手动测试。

- [ ] **Step 8.1: 在真机/模拟器上安装编译产物**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 8.2: 按 spec §5 测试要点逐项验证**

| 编号 | 测试项 | 期望结果 | 通过? |
|------|--------|---------|-------|
| 1 | 完全展开 → 右滑 | 卡片 300ms 弹性归位 | ☐ |
| 2 | 半展开（拖到一半就右滑）| 卡片从当前位置 300ms 弹性归位 | ☐ |
| 3 | 完全展开 → 点击详情 | 无反应 | ☐ |
| 4 | 完全展开 → 长按 | 无反应 | ☐ |
| 5 | 完全展开 → 点复选框 | 无反应 | ☐ |
| 6 | 完全展开 → 点子待办展开按钮 | 无反应 | ☐ |
| 7 | 完全展开 → 点子待办勾选框 | 无反应 | ☐ |
| 8 | 右滑关闭后立即点详情 | 200ms 内无效，之后正常 | ☐ |
| 9 | 关闭过程中用户反向左滑 | 关闭动画继续完成，忽略反向滑动 | ☐ |
| 10 | 多个卡片互斥展开 | 行为不变（A 展开时左滑 B → A 自动收起，B 展开）| ☐ |
| 11 | 批量模式下左滑/右滑 | 全部不响应 | ☐ |
| 12 | 长按拖拽排序时左滑 | 不响应 | ☐ |

- [ ] **Step 8.3: 记录测试结果并修复发现的问题**

- 若所有项目通过：跳到 Step 8.4
- 若有失败：分析 root cause，回到对应任务修复后重新测试

- [ ] **Step 8.4: 询问用户是否 git 提交最终修复（如有）**

向用户发出询问："手动 UI 测试完成，是否对测试中发现的问题修复进行 git 提交？"

- 若用户选择提交：用 `git add` 添加修复文件 + 提交
- 若用户跳过：进入交付总结

---

## 交付总结

实施完成的标志：
- [ ] Task 1-6 全部完成
- [ ] Task 7 编译验证通过
- [ ] Task 8 全部 12 项手动 UI 测试通过
- [ ] 用户已确认所有任务 git 提交完成（或明确选择跳过）

提交历史示例（按顺序）：
1. `refactor(todo): SwipeableTodoBox 新增 restoreJob 与 isClickBlocked 状态`
2. `feat(todo): 任何展开度右滑首帧触发恢复动画`
3. `refactor(todo): onDragEnd / onDragCancel 接入 restoreJob 防重入`
4. `refactor(todo): SwipeableTodoBox.content 透传 isClickBlocked`
5. `feat(todo): TodoListItem 接入 isClickBlocked 屏蔽 4 类点击入口`
6. `feat(todo): HomeScreen 适配新 content 签名并透传 isClickBlocked`
7. （测试修复）`fix(todo): ...`（按实际情况）

---

## 后续可优化方向

- **触觉反馈**：右滑触发恢复时给一个轻微 haptic 反馈（与长按 / 切换完成一致）
- **视觉降权**（如未来需要）：展开时整张卡片 `graphicsLayer.alpha = 0.7f` 暗示"不可点击"
- **手势方向自定义**：暴露参数让调用方配置"哪些方向算恢复"
- **JVM 单元测试覆盖**：将"触发条件"和"isClickBlocked 状态转换"抽离为纯函数后用 JUnit 测试（参考 [2026-06-29-待办卡片滑动接触缩放.md](2026-06-29-待办卡片滑动接触缩放.md) 的 PressFeedbackLogicTest 模式）
