# 待办卡片左滑交互重构设计

**日期**: 2026-06-25
**状态**: 设计中
**目标版本**: 当前迭代
**关联 Spec**: [2026-06-24-todo-card-swipe-design.md](file:///c:/Users/Lenovo/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-24-todo-card-swipe-design.md)（前序设计）
**目标**: 修复当前 `TodoListItem` 内嵌 swipe 实现的 3 个 bug，并按飞书风格重做为独立 `SwipeableTodoBox` 包装器组件。

---

## 1. 背景与目标

### 1.1 当前实现的问题

| ID | 现象 | 根因 |
|----|------|------|
| **Bug-1** | 左滑 3 个功能按钮**持续显示**在待办卡片上，遮挡卡片内容 | `SwipeActionButton` 中 `alpha` 仅应用在内部 `Column`（图标+文字）的 `Modifier.graphicsLayer`，**外层 `Box` 背景色不参与渐入**。`cardOffsetX=0` 时橙/蓝/红色块仍满色显示 |
| **Bug-2** | 左滑时卡片**未在预设位置停止**，而是从界面消失 | `triggerThresholdPx = 72dp`（单按钮宽）+ `actionsWidthPx = 216dp`——单按钮阈值过小，**任何**左滑都直接吸到 -216dp；加上 Bug-1 的色块遮挡，视觉上感觉"卡片消失" |
| **Bug-3** | 缺乏飞书式**堆叠**视觉效果，只有文字的简单淡入 | 原设计为 3 个 `Animatable<Float>` + `LaunchedEffect` 串行启动的飞书式堆叠；实现被简化为 `animateFloatAsState` + 阈值判断，且 alpha 只作用在内部 Column（参见 Bug-1）——三重缺失导致**无按钮级动画** |

### 1.2 目标

- 完全对标飞书"消息"列表左滑交互（参考飞书 App 左滑截图）
- 修复 Bug-1/Bug-2/Bug-3
- **抽出独立组件 `SwipeableTodoBox`**，将 swipe 逻辑与卡片本体解耦，便于复用和测试
- 保持与 `ReorderableLazyColumn` 拖拽排序手势的兼容

---

## 2. 范围

### 2.1 In-Scope

- 新建 `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt`（独立 Composable）
- 重构 `TodoListItem.kt`：删除 L274-745 的 Layout + Actions 渲染 + measurePolicy（约 470 行）
- 集成到 `HomeScreen.kt`：用 `SwipeableTodoBox` 包裹 `TodoListItem`
- 新建 `app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt`（单元测试）
- 迁移/调整 `TodoListItemTest.kt` 中现有 swipe 相关测试用例

### 2.2 Out-of-Scope

- 置顶功能后端实现（`TodoItem.pinned` 字段、列表排序、持久化）
- 主题色切换对按钮颜色的影响（保持规范中固定三色：橙/蓝/红）
- 已完成（status=1）卡片的左滑交互差异
- 多选模式下的批量操作
- 从子页面（分类页、搜索页）的卡片左滑
- 自定义长按拖动排序

---

## 3. 组件架构

### 3.1 整体结构

```
SwipeableTodoBox (Box)
├── ActionsLayer (底层) - 固定在屏幕右侧 216dp
│   ├── Btn1 (置顶)  ─→ 独立 Animatable 控制 alpha/scaleX/translateX
│   ├── Btn2 (分享)
│   └── Btn3 (删除)
└── ContentSlot (顶层) - 整体带 offset(x = cardOffsetX)
    └── TodoListItem (纯卡片本体，无 swipe 逻辑)
```

### 3.2 组件签名

```kotlin
/**
 * 可左滑展开操作区的容器组件（飞书风格）
 *
 * 用法：将待办卡片作为 content 传入，自动获得左滑操作能力。
 *
 * @param modifier 修饰符
 * @param isEnabled 是否启用左滑（批量模式或 disabled 状态下设为 false）
 * @param isExpanded 是否处于展开状态（由父组件控制，用于"互斥展开"语义）
 * @param onExpandChange 展开状态变化回调（true=展开, false=收起）
 * @param onPinClick 点击置顶按钮回调
 * @param onShareClick 点击分享按钮回调
 * @param onDeleteClick 点击删除按钮回调
 * @param content 卡片内容（通常是 TodoListItem）
 */
@Composable
fun SwipeableTodoBox(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    onPinClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    content: @Composable () -> Unit
)
```

### 3.3 文件变更

| 文件 | 状态 | 责任 |
|------|------|------|
| `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt` | **新建** | 手势检测、动画时序、按钮渲染 |
| `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt` | **修改** | 删除 L274-745 的 Layout + Actions 渲染 + measurePolicy + SwipeActionButton 私有函数（约 470 行）；删除 `onSwipeExpandChange`、`expandedTodoId` 参数；删除相关 import |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | **修改** | 用 `SwipeableTodoBox` 包裹 `TodoListItem`；保留现有 `swipeExpandedTodoId` 状态 |
| `app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt` | **新建** | 6 个单元测试用例 |
| `app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt` | **修改** | 删除现有 4 个 swipe 相关测试（迁移到 SwipeableTodoBoxTest） |

---

## 4. 几何与布局

### 4.1 几何参数

| 元素 | 尺寸 | 说明 |
|------|------|------|
| 卡片宽度 | fillMaxWidth | 与父容器同宽 |
| 卡片高度 | 内容自适应 | |
| 动作区总宽 | 216dp | 3 × 72dp |
| 单按钮宽 | 72dp | |
| 按钮高度 | = 卡片高度 | 通过自定义 `Layout` 锁定 |
| 卡片位移上限 | -216dp | 完全展开位置 |
| **触发阈值** | -72dp | 拖动 ≥ 72dp 触发完全展开 |
| **按钮 1 激活阈值** | -30dp | 拖动 > 30dp 时按钮 1 开始渐入 |
| **回弹阈值** | +30dp | 右滑 > 30dp 触发回弹关闭 |

### 4.2 圆角策略

| 区域 | 圆角 |
|------|------|
| 卡片左外 2 角 | 20dp（与动作区拼接处） |
| 卡片右外 2 角 | 20dp |
| 动作区左外 2 角 | 0（与卡片右半部分衔接，无视觉接缝） |
| 动作区右外 2 角 | 20dp（与卡片圆角一致） |
| 整体效果 | 4 个 20dp 圆角，与原卡片视觉一致 |

### 4.3 自定义 Layout 测量顺序

```kotlin
Layout(
    content = {
        // ⚠️ 严格顺序：先 Card 后 Actions，确保 measurables[0] = Card, measurables[1] = Actions
        Box(modifier = Modifier.offset(x = Dp(cardOffsetX.value))) {
            content()  // 卡片本体
        }
        Box { /* ActionsLayer 3 按钮 */ }
    },
    measurePolicy = { measurables, constraints ->
        val cardPlaceable = measurables[0].measure(constraints)
        val cardWidth = cardPlaceable.width
        val cardHeight = cardPlaceable.height
        val actionsPlaceable = if (measurables.size > 1 && isEnabled) {
            measurables[1].measure(
                Constraints.fixed(
                    width = with(density) { actionsWidthDp.roundToPx() },
                    height = cardHeight
                )
            )
        } else null
        layout(cardWidth, cardHeight) {
            cardPlaceable.placeRelative(0, 0)
            actionsPlaceable?.placeRelative(
                x = cardWidth - actionsPlaceable.width,
                y = 0
            )
        }
    }
)
```

---

## 5. 动画时序（飞书式堆叠）

### 5.1 状态机

```
                 [Closed: offset=0]
                       ↑  ↑ 
         右滑 / 拖动 < 30dp / 收回操作
                       │  │
                  拖动中 (snapTo 实时)
                       │  │
                       ↓  ↓ 左滑松开 ≥ 72dp
                 [Expanded: offset=-216]
                       ↑  ↑ 
                拖动 < 72dp 松开 / 父组件强制
```

### 5.2 飞书式按钮入场动画（核心创新）

**思路**：按钮不是"同步淡入"，而是**依次滑入 + 渐入**。

**阶段边界计算**（按钮 1 激活 30dp + 每按钮 48dp 间距）：
- 阶段 0 → 1 边界：`30dp`（按钮 1 开始）
- 阶段 1 → 2 边界：`30 + 48 = 78dp`（按钮 2 开始）
- 阶段 2 → 3 边界：`78 + 48 = 126dp`（按钮 3 开始）
- 阶段 3 → 4 边界：`126 + 48 = 174dp`（按钮 3 完全显示）
- 总宽 = 216dp

> 单按钮宽度 72dp 中预留 48dp 用于"按钮渐入动画区间"，剩余 24dp 为按钮已完全显示后的"惯性滑动区"。

| 阶段 | revealProgress 范围 | 按钮 1 | 按钮 2 | 按钮 3 |
|------|---------------------|--------|--------|--------|
| 0 | `[0, 30dp)` | α=0, scale=0.7, tx=+24 | α=0, scale=0.7, tx=+24 | α=0, scale=0.7, tx=+24 |
| 1 | `[30, 78dp)` | **渐入** α 0→1, scale 0.7→1, tx +24→0 (80ms) | α=0 | α=0 |
| 2 | `[78, 126dp)` | α=1, scale=1, tx=0 | **渐入** α 0→1, scale 0.7→1, tx +24→0 (80ms) | α=0 |
| 3 | `[126, 174dp)` | α=1, scale=1, tx=0 | α=1, scale=1, tx=0 | **渐入** α 0→1, scale 0.7→1, tx +24→0 (80ms) |
| 4 | `=216dp` | α=1, scale=1, tx=0 | α=1, scale=1, tx=0 | α=1, scale=1, tx=0 |
| 收回 | ↓ 反向 | 按 btn1→btn2→btn3 顺序归零 | | |

**缓动函数**：`tween(80ms, easing = FastOutSlowInEasing)`

### 5.3 revealProgress 计算

```kotlin
// 直接使用 dp 单位（不经过 px 转换）
val revealProgressDp = (-cardOffsetX.value / density.density).coerceIn(0f, 216f)
// revealProgressDp 范围 0f..216f，单位 dp
```

### 5.4 动画驱动策略

| 元素 | Animatable 数量 | 驱动方式 |
|------|----------------|---------|
| `cardOffsetX` | 1 | 拖动 `snapTo` 实时，松开 `animateTo(0 or -216, spring)` |
| `btn1Alpha/ScaleX/TranslateX` | 3 | `LaunchedEffect(revealProgress)` 监听进度变化，根据当前阶段 `animateTo` 目标值 |
| `btn2Alpha/ScaleX/TranslateX` | 3 | 同上 |
| `btn3Alpha/ScaleX/TranslateX` | 3 | 同上 |

**关键**：按钮动画使用 `Animatable.animateTo` 而非 `animateFloatAsState`，避免 `snapTo` 拖动时动画断层。

### 5.5 动画曲线细节

```kotlin
// 卡片位移（spring 弹性，更自然）
cardOffsetX.animateTo(
    targetValue = -actionsWidthPx,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
)

// 按钮渐入（tween + FastOutSlowInEasing，标准 Material 缓动）
btn1Alpha.animateTo(
    targetValue = 1f,
    animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing)
)
```

---

## 6. 手势与交互

### 6.1 手势检测

```kotlin
.pointerInput(isEnabled, isExpanded) {
    if (!isEnabled) return@pointerInput
    detectHorizontalDragGestures(
        onDragEnd = { /* 详见 6.2 阈值判断 */ },
        onDragCancel = { /* 同 onDragEnd */ }
    ) { _, dragAmount ->
        coroutineScope.launch {
            val newOffset = (cardOffsetX.value + dragAmount)
                .coerceIn(-actionsWidthPx, 0f)
            cardOffsetX.snapTo(newOffset)
        }
    }
}
```

### 6.2 阈值判断

```kotlin
onDragEnd = {
    val final = cardOffsetX.value
    val target = when {
        // 左滑 ≥ 72dp → 完全展开
        final <= -triggerThresholdPx -> -actionsWidthPx
        // 右滑 > 30dp → 收回
        final >= 30f -> 0f
        // 处于展开状态且轻微抖动 → 保持展开
        isExpanded -> -actionsWidthPx
        // 其他 → 收回
        else -> 0f
    }
    onExpandChange(target < 0f)
    coroutineScope.launch {
        cardOffsetX.animateTo(target, spring(...))
    }
}
```

### 6.3 4 种收回方式

| 方式 | 触发条件 | 实现 |
|------|---------|------|
| **右滑收回** | `dragAmount > 30f`（向右） | `onDragEnd` 中判定 `final >= 30f → target=0` |
| **点击卡片** | `combinedClickable.onClick` 触发 | 父组件 `onExpandChange(false)` |
| **点其他卡** | 其他卡片 `onExpandChange(true)` | HomeScreen 维护 `swipeExpandedTodoId`，自动收起其他卡 |
| **操作后自动** | 点击 Pin/Share/Delete 按钮 | 按钮 onClick 中 `onExpandChange(false)` |

### 6.4 手势冲突处理

- **与 LazyColumn 滚动**：通过 `detectHorizontalDragGestures` 的 lambda 形式，消费掉水平事件后 LazyColumn 不会响应
- **与 ReorderableLazyColumn vertical drag**：互不干扰（一个水平、一个垂直）
- **误触防护**：dragAmount > 12dp 才启动 swipe 跟踪（避免下拉刷新等场景误触发）

---

## 7. 集成影响

### 7.1 HomeScreen 集成示例

```kotlin
// HomeScreen.kt（保留现有 swipeExpandedTodoId 状态）
LazyColumn {
    items(todos) { todo ->
        SwipeableTodoBox(
            modifier = Modifier.testTag("swipeableTodoBox_${todo.id}"),
            isEnabled = !isBatchMode,
            isExpanded = swipeExpandedTodoId == todo.id,
            onExpandChange = { expanded ->
                swipeExpandedTodoId = if (expanded) todo.id else null
            },
            onPinClick = { /* Log */ },
            onShareClick = { shareTodoAsImage(context, todo, categories) },
            onDeleteClick = { showDeleteDialog = todo.id }
        ) {
            TodoListItem(todo = todo, /* 移除 swipe 相关参数 */)
        }
    }
}
```

### 7.2 TodoListItem 移除项

需要从 `TodoListItem` 中移除：
- 参数：`onSwipeExpandChange`、`expandedTodoId`
- 代码：L150-222 的 swipe 状态变量（`cardOffsetX`、`actionsWidthDp`、`triggerThresholdPx`、`actionsWidthPx`、`btn1Alpha/2/3Alpha`）和 `LaunchedEffect`
- 代码：L274-745 的 `Layout` 块（含 measurePolicy）
- 代码：L1239-1276 的 `SwipeActionButton` 私有函数
- Import：`animateDpAsState`、`Animatable`、`detectHorizontalDragGestures`、`pointerInput`、`Layout`、`Constraints`、`graphicsLayer`、`Layout`、`Offset`、`blur`、`RoundedCornerShape` 等（按需）

### 7.3 复用现有分享工具

无需新增分享工具函数。`SwipeableTodoBox` 的 `onShareClick` 回调由 HomeScreen 实现，直接调用 `shareTodoAsImage(context, todo, categories)`（现有图片分享实现）。

### 7.4 不影响的部分

- `HomeViewModel.deleteTodo` 保持原状
- `TodoListItem` 内部组件（PriorityBar、SubTaskListItem 等）保持原状
- `TodoListItem` 主体内容、Card 视觉、圆角、阴影等保持不变
- `ReorderableLazyColumn` 拖拽逻辑保持不变

---

## 8. 颜色与视觉规范

颜色完全沿用前序 spec 规范：

| 用途 | 色值 | 来源 |
|------|------|------|
| 置顶按钮背景 | `#FF9A5C` | UI 规范主题色（暖阳橙） |
| 分享按钮背景 | `#90CAF9` | UI 规范"低优先级"色（柔和蓝） |
| 删除按钮背景 | `#FF8A80` | UI 规范"高优先级"色（柔和红） |
| 按钮文字 | `#FFFFFF` | 白色（高对比） |
| 按钮图标 | `#FFFFFF` | 白色（高对比） |

> 颜色全部取自 `docs/superpowers/specs/UI设计规范.md`，不引入新色值。

---

## 9. 测试策略

### 9.1 单元测试文件

`app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt`（新建）：

| 测试用例 | 覆盖 |
|---------|------|
| `buttons_initiallyHidden` | offset=0 时 3 按钮 alpha=0、不可见 |
| `swipe_buttonsFadeInSequentially` | 拖动过程 3 按钮 alpha 顺序递增（btn1 → btn2 → btn3） |
| `swipe_fullyExpanded_allButtonsVisible` | 完全展开后 3 按钮完全可见 |
| `swipePartialReboundToClosed` | 拖动 < 72dp 松开后回弹到 0 |
| `clickPin_triggersCallback` | 点击置顶触发 onPinClick |
| `clickDelete_triggersCallback` | 点击删除触发 onDeleteClick |

### 9.2 旧测试调整

`TodoListItemTest.kt` 中现有的 4 个 swipe 相关测试（`swipe_buttonsInitiallyHidden`、`swipe_fullyExpanded_buttonsVisible`、`clickPinButton_triggersOnPinClick`、`clickDeleteButton_triggersOnDelete`）需：
- 删除（功能已迁移到 `SwipeableTodoBoxTest`）
- 或调整（去掉 swipe 相关断言，仅保留 TodoListItem 自身的纯 UI 测试）

---

## 10. 验收标准

| ID | 验收项 | 测试方法 |
|----|--------|---------|
| AC-1 | 未滑动时 3 按钮完全不可见（背景 + 内容都 alpha=0） | `assertIsNotDisplayed` |
| AC-2 | 拖动 30~78dp 区间，按钮 1 alpha 0→1，scaleX 0.7→1，translateX +24→0 | 截屏 + `assertWidth` |
| AC-3 | 拖动 78~126dp 区间，按钮 2 渐入（同上动画） | 截屏 |
| AC-4 | 拖动 ≥ 72dp 松手 → 完全展开（offset=-216dp，3 按钮完全可见） | 手动 + 单元测试 |
| AC-5 | 拖动 < 30dp 松手 → 回弹关闭 | 手动 + 单元测试 |
| AC-6 | 3 按钮按"置顶→分享→删除"依次入场 | 录屏验证 |
| AC-7 | 点击置顶按钮 → 输出 Log，其他无反应 | logcat |
| AC-8 | 点击分享按钮 → 弹系统分享面板 | 手动 |
| AC-9 | 点击删除按钮 → 弹二次确认弹窗 | 手动 |
| AC-10 | 同一时间只有一张卡片展开 | 手动 |
| AC-11 | 与 ReorderableLazyColumn 拖拽不冲突 | 手动 |
| AC-12 | 与 LazyColumn 滚动不冲突 | 手动 |
| AC-13 | 批量模式下左滑禁用 | 手动 |

---

## 11. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 拖动时按钮 alpha 抖动 | 视觉上按钮闪烁 | 用 `Animatable.animateTo` 而非 `snapTo` 驱动按钮 alpha（仅在 revealProgress 跨过阶段边界时启动动画） |
| 旧 swipe 测试用例不兼容 | 测试失败 | 迁移到 `SwipeableTodoBoxTest`，删除 `TodoListItemTest` 中的 swipe 部分 |
| 与 `ReorderableLazyColumn` 嵌套冲突 | reorder 不工作 | 通过 `Modifier.pointerInput` 分离手势标识；严格水平/垂直方向 |
| `Layout` measure policy 索引依赖 | Actions 渲染错位 | 严格保持 content lambda 中"先 Card 后 Actions"顺序；单元测试覆盖 |
| RevealProgress 计算误差 | 按钮入场时机不准 | 使用 density.toPx 统一换算；提供单元测试边界值（30dp, 78dp, 126dp, 174dp） |
| 动画与拖动事件竞争 | 动画卡顿 | 拖动中按钮 alpha 使用 `Animatable` 的 `animateTo` 而不是 `animateFloatAsState`，避免重组时序问题 |

---

## 12. 后续优化（不在本次范围）

- 置顶功能后端实现（`pinned: Boolean` 字段 + 排序逻辑）
- 多选模式下批量置顶 / 批量删除
- 自定义长按拖动排序
- 长按弹出更多操作（移动到分组、编辑、复制）
- 卡片左滑/右滑出现不同操作（当前仅左滑展开操作区）
- 振动反馈（HapticFeedback）—— 操作命中按钮时短暂震动
