# SwipeableTodoBox Compose 集成设计文档

> **日期**：2026-06-25
> **版本**：v1（基于 Web 原型算法移植到 Compose）
> **技术栈**：Jetpack Compose + Compose BOM 2026.04.01 + Compose 1.9.2
> **用途**：将 Web 原型 `docs/analysis/todo-swipe-prototype.html` 的级联重叠堆叠左滑动效移植为 Compose 独立包装器组件，集成到待办卡片
> **参考文档**：
> - [Web 原型设计](2026-06-25-swipe-actions-web-component-design.md)
> - [Compose 重构设计](2026-06-25-todo-card-swipe-redesign-design.md)
> - [Web 原型文件](../../analysis/todo-swipe-prototype.html)

---

## 一、设计目标

1. **新建独立包装器组件** `SwipeableTodoBox.kt`，与 `TodoListItem` 解耦
2. **复用 Web 原型算法**：revealProgress 连续函数驱动按钮 transform/opacity
3. **严格遵循参数配置**：
   - 动画时长 `duration = 300ms`
   - 级联延迟比例 `staggerRatio = 0.00`（同步移动）
   - 吸附比例 `thresholdRatio = 0.20`
   - 缓动函数 `ElasticOutEasing`（弹性效果，对应 Web 原型 cubic-bezier(0.34, 1.56, 0.64, 1)）
4. **按钮顺序固定**：从左到右为 分享 → 置顶 → 删除
5. **配色方案**：Web 原型配色（黄 `#FFD54F` / 橙 `#FF9A5C` / 红 `#FF5252`）
6. **opacity 二元化**：收起态=0，滑动/展开态=1（无淡入淡出）
7. **初始堆叠**：3 按钮完全重叠在 Delete 槽位（最右）
8. **无间隙最终态**：完全展开后按钮紧密相邻

---

## 二、文件结构

| 文件 | 状态 | 责任 |
|------|------|------|
| `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt` | **新建** | 左滑包装器：手势检测、动画时序、按钮渲染 |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | **修改** | 用 SwipeableTodoBox 包裹 TodoListItem；新增 swipeExpandedTodoId 状态 |
| `app/src/main/res/values/colors_ui.xml` | **修改** | 新增 `ui_swipe_share`(#FFD54F)、`ui_swipe_delete`(#FF5252) |
| `app/src/main/res/values-night/colors_ui.xml` | **修改** | 新增夜间模式对应色值 |
| `app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt` | **新建** | 6 个单元测试 |

---

## 三、组件 API

### 3.1 函数签名

```kotlin
/**
 * 可左滑展开操作区的容器组件（飞书风格级联重叠堆叠动效）
 *
 * 用法：将待办卡片作为 content 传入，自动获得左滑操作能力。
 *
 * @param modifier 修饰符
 * @param isEnabled 是否启用左滑（批量模式或 disabled 状态下设为 false）
 * @param isExpanded 是否处于展开状态（由父组件控制，用于"互斥展开"语义）
 * @param onExpandChange 展开状态变化回调（true=展开, false=收起）
 * @param onShareClick 点击分享按钮回调
 * @param onPinClick 点击置顶按钮回调
 * @param onDeleteClick 点击删除按钮回调
 * @param durationMs 动画时长（默认 300ms）
 * @param staggerRatio 级联延迟比例（默认 0.00，所有按钮同步移动）
 * @param thresholdRatio 吸附比例（默认 0.20）
 * @param easing 缓动函数（默认 ElasticOutEasing 弹性效果，对应 Web 原型 cubic-bezier(0.34, 1.56, 0.64, 1)）
 * @param content 卡片内容（通常是 TodoListItem）
 */
@Composable
fun SwipeableTodoBox(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    onShareClick: () -> Unit = {},
    onPinClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    durationMs: Int = 300,
    staggerRatio: Float = 0.00f,
    thresholdRatio: Float = 0.20f,
    easing: Easing = ElasticOutEasing,
    content: @Composable () -> Unit
)

/**
 * 弹性缓动函数（对应 Web 原型 cubic-bezier(0.34, 1.56, 0.64, 1)）
 * 使用 easeOutBack 数学模型实现回弹效果
 */
val ElasticOutEasing = Easing { fraction ->
    val c1 = 1.56f
    val c3 = c1 + 1f
    1f + c3 * Math.pow(fraction - 1f, 3.0).toFloat() + c1 * Math.pow(fraction - 1f, 2.0).toFloat()
}
```

### 3.2 实例方法（通过状态提升实现）

| 方法 | 实现方式 |
|------|---------|
| `open()` | 父组件调 `onExpandChange(true)`，内部 `animateTo(-actionsWidthPx)` |
| `close()` | 父组件调 `onExpandChange(false)`，内部 `animateTo(0f)` |
| `toggle()` | 父组件切换 `isExpanded`，内部自动动画 |

---

## 四、双层叠加布局

### 4.1 DOM 结构（自定义 Layout）

```
SwipeableTodoBox (Layout)
├── ContentLayer (measurables[0], z-index=10) - 整体 offset(x = cardOffsetX)
│   └── content()  // TodoListItem
└── ActionsLayer (measurables[1], z-index=1) - 固定右侧 216dp
    ├── BtnShare  (z-index=3)  分享 #FFD54F
    ├── BtnPin    (z-index=2)  置顶 #FF9A5C
    └── BtnDelete (z-index=1)  删除 #FF5252
```

### 4.2 Layout measurePolicy

```kotlin
Layout(
    content = {
        // 先内容后操作层，确保 measurables[0]=content, measurables[1]=actions
        Box(modifier = Modifier.offset { IntOffset(cardOffsetX.value.roundToInt(), 0) }) {
            content()
        }
        Box { /* ActionsRow with 3 buttons */ }
    },
    measurePolicy = { measurables, constraints ->
        val contentPlaceable = measurables[0].measure(constraints)
        val cardWidth = contentPlaceable.width
        val cardHeight = contentPlaceable.height
        val actionsPlaceable = if (measurables.size > 1 && isEnabled) {
            measurables[1].measure(
                Constraints.fixed(
                    width = with(density) { actionsWidthDp.roundToPx() },
                    height = cardHeight
                )
            )
        } else null
        layout(cardWidth, cardHeight) {
            contentPlaceable.placeRelative(0, 0, zIndex = 10f)
            actionsPlaceable?.placeRelative(
                x = cardWidth - actionsPlaceable.width,
                y = 0,
                zIndex = 1f
            )
        }
    }
)
```

### 4.3 关键布局规则

- **容器**：`Modifier.clip(RoundedCornerShape(16.dp))`，裁剪超出的操作层
- **内容层**：通过 `Modifier.offset { IntOffset(cardOffsetX, 0) }` 左滑，z-index=10
- **操作层**：固定在容器右侧，z-index=1
- **按钮**：72dp 等宽，高度 = `Constraints.fixed(height = cardHeight)` 与内容层同高
- **z-index 倒置**：分享=3, 置顶=2, 删除=1（从左到右递减）
- **容器高度**：由内容层决定，操作层跟随

---

## 五、动画算法（Web 原型算法 Compose 移植）

### 5.1 核心状态

```kotlin
val cardOffsetX = remember { Animatable(0f) }  // px, 范围 -actionsWidthPx..0
val coroutineScope = rememberCoroutineScope()
```

### 5.2 revealProgress 连续函数（与 Web 原型 1:1 对齐）

```kotlin
// 每帧由 cardOffsetX.value 驱动
val revealPx = (-cardOffsetX.value).coerceIn(0f, actionsWidthPx)
val revealProgress = revealPx / actionsWidthPx  // 0→1

buttons.forEachIndexed { i, btn ->
    // 级联延迟计算
    val localStart = i * staggerRatio                    // staggerRatio=0 时全为 0
    val denom = 1f - localStart
    val localProgress = if (denom > 0f) {
        ((revealProgress - localStart) / denom).coerceIn(0f, 1f)
    } else {
        if (revealProgress >= localStart) 1f else 0f
    }
    // 偏移量：初始堆叠在 Delete 槽位 → 终态回到各自原始位置
    val offset = (buttons.size - 1 - i) * buttonWidthPx   // 分享=144, 置顶=72, 删除=0
    val translateX = offset * (1f - localProgress)         // 堆叠偏移 → 0
    // opacity 二元化：无淡入淡出
    val alpha = if (revealPx > 0f) 1f else 0f
}
```

### 5.3 动画时间线

| 阶段 | 触发 | cardOffsetX | 按钮层 | 时长 |
|------|------|-------------|--------|------|
| 跟手 | onDrag | `snapTo(newOffset)` 实时 | 重组重算 translateX/alpha | 实时 |
| 吸附 | onDragEnd ≥ 阈值 | `animateTo(-actionsWidthPx, tween(300, easing=ElasticOutEasing))` | 重组跟随 | 300ms |
| 回弹 | onDragEnd < 阈值 | `animateTo(0f, tween(300, easing=ElasticOutEasing))` | 重组跟随 | 300ms |
| 收起 | close() | `animateTo(0f, tween(300, easing=ElasticOutEasing))` | 重组跟随 | 300ms |

### 5.4 阈值计算（thresholdRatio=0.20）

```kotlin
val actionsWidthPx = with(density) { 216.dp.toPx() }     // 3 × 72dp
val thresholdPx = actionsWidthPx * thresholdRatio        // 216 * 0.20 = 43.2dp
// 松手时：revealPx ≥ 43.2dp → 吸附展开；< 43.2dp → 回弹
```

### 5.5 验证状态表

**收起状态 (revealProgress=0, staggerRatio=0)**：

| 按钮 i | offset | localProgress | translateX | 视觉位置 | alpha |
|--------|--------|---------------|------------|---------|-------|
| 0 分享 | 144 | 0 | 144 | 144 | 0 |
| 1 置顶 | 72 | 0 | 72 | 144 | 0 |
| 2 删除 | 0 | 0 | 0 | 144 | 0 |

全部堆叠在 Delete 槽位 (144px)。

**完全展开 (revealProgress=1.0)**：

| 按钮 i | localProgress | translateX | 视觉位置 | alpha |
|--------|---------------|------------|---------|-------|
| 0 分享 | 1.0 | 0 | 0 | 1 |
| 1 置顶 | 1.0 | 0 | 72 | 1 |
| 2 删除 | 1.0 | 0 | 144 | 1 |

按钮紧密相邻（间距 0px），无间隙。

---

## 六、手势与交互

### 6.1 手势检测

```kotlin
Modifier.pointerInput(isEnabled, isExpanded) {
    if (!isEnabled) return@pointerInput
    detectHorizontalDragGestures(
        onDragEnd = {
            val revealPx = -cardOffsetX.value
            val target = if (revealPx >= thresholdPx) -actionsWidthPx else 0f
            onExpandChange(target < 0f)
            coroutineScope.launch {
                cardOffsetX.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = durationMs, easing = easing)
                )
            }
        },
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

### 6.2 手势冲突处理

| 冲突场景 | 处理方式 |
|---------|---------|
| 与 LazyColumn 垂直滚动 | `detectHorizontalDragGestures` 仅消费水平事件，垂直事件自动传递给父级 |
| 与 ReorderableLazyColumn 拖拽排序 | 水平/垂直方向天然分离，互不干扰 |
| 误触防护 | `detectHorizontalDragGestures` 内置初始拖动判定 |

### 6.3 收起规则（4 种方式）

| 方式 | 触发 | 实现 |
|------|------|------|
| 右滑收起 | onDragEnd 判定 `revealPx < thresholdPx` | `animateTo(0f)` |
| 点击内容层 | `combinedClickable.onClick` | `onExpandChange(false)` + `animateTo(0f)` |
| 点击其他卡片 | HomeScreen 维护 `swipeExpandedTodoId`，新卡片展开时旧卡片收起 | 父组件调 `onExpandChange(false)` |
| 点击操作按钮 | 按钮 onClick | 执行回调 + `onExpandChange(false)` + `animateTo(0f)` |

### 6.4 互斥展开

HomeScreen 新增状态：
```kotlin
var swipeExpandedTodoId by remember { mutableStateOf<Long?>(null) }
```
每张卡片 `isExpanded = swipeExpandedTodoId == todo.id`，展开新卡片时自动收起旧卡片。

---

## 七、HomeScreen 集成

### 7.1 集成方式

```kotlin
// HomeScreen.kt - ReorderableLazyColumn 内
var swipeExpandedTodoId by remember { mutableStateOf<Long?>(null) }

items(todos) { todo ->
    SwipeableTodoBox(
        modifier = Modifier.testTag("swipeableTodoBox_${todo.id}"),
        isEnabled = !isBatchMode,
        isExpanded = swipeExpandedTodoId == todo.id,
        onExpandChange = { expanded ->
            swipeExpandedTodoId = if (expanded) todo.id else null
        },
        onShareClick = { shareTodoAsImage(context, todo, categories) },
        onPinClick = { viewModel.togglePin(todo.id) },
        onDeleteClick = { pendingDeleteId = todo.id }
    ) {
        TodoListItem(todo = todo, /* 现有参数不变 */)
    }
}
```

### 7.2 TodoListItem 保持不变

`TodoListItem.kt` 不做任何修改，保持当前 951 行的纯净状态。所有 swipe 逻辑由 `SwipeableTodoBox` 承担。

### 7.3 复用现有分享工具

`shareTodoAsImage(context, todo, categories)` 函数已存在于 HomeScreen.kt（第 2424 行），直接作为 `onShareClick` 回调使用。

---

## 八、颜色与视觉规范

### 8.1 颜色定义（colors_ui.xml 新增）

```xml
<!-- values/colors_ui.xml -->
<color name="ui_swipe_share">#FFD54F</color>   <!-- 分享：黄色 -->
<color name="ui_swipe_delete">#FF5252</color>  <!-- 删除：正红色 -->
<!-- ui_primary (#FF9A5C) 已存在，用于置顶 -->
```

```xml
<!-- values-night/colors_ui.xml（夜间模式适配色） -->
<color name="ui_swipe_share">#FFC107</color>   <!-- 分享：深黄 -->
<color name="ui_swipe_delete">#D32F2F</color>  <!-- 删除：深红 -->
```

### 8.2 按钮配置

| 位置 | 按钮 | 背景色 | 图标 | 文字 | 文字颜色 | z-index | 级联顺序 |
|------|------|--------|------|------|---------|---------|----------|
| 最左 | 分享 | `#FFD54F` | Share | 分享 | `#FFFFFF` | 3 | 第 1（最先） |
| 中间 | 置顶 | `#FF9A5C` | PushPin | 置顶 | `#FFFFFF` | 2 | 第 2 |
| 最右 | 删除 | `#FF5252` | Delete | 删除 | `#FFFFFF` | 1 | 第 3 |

### 8.3 按钮内部布局

- 72dp × 100%高（与卡片同高）
- 图标 24dp + 文字 12sp，纵向居中（`Arrangement.Center`）
- 图标与文字间距 4dp
- 完全展开后按钮紧密相邻（间距 0），整个操作区连续可点击

---

## 九、测试策略

### 9.1 单元测试文件

`app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt`（新建）

| 测试用例 | 验证点 |
|---------|--------|
| `buttons_initiallyHidden` | offset=0 时 3 按钮 alpha=0，不可见 |
| `swipe_buttonsVisible_WhenExpanded` | 完全展开后 3 按钮 alpha=1，紧密相邻 |
| `swipePartialReboundToClosed` | 拖动 < 20% 松开后回弹到 0 |
| `swipeFullyExpanded_atThreshold` | 拖动 ≥ 20% 松开后吸附到 -216dp |
| `clickShare_triggersCallback` | 点击分享按钮触发 onShareClick |
| `clickDelete_triggersCallback` | 点击删除按钮触发 onDeleteClick |

---

## 十、验收标准

| 编号 | 验收项 | 验证方式 |
|------|--------|---------|
| AC-1 | 未滑动时 3 按钮完全不可见（alpha=0，堆叠在 Delete 槽位） | 单元测试 + 视觉检查 |
| AC-2 | 横向左滑触发交互，竖向滑动不触发（允许页面垂直滚动） | 手动测试 |
| AC-3 | 滑动 < 43.2dp 松手 → 回弹复位 | 单元测试 |
| AC-4 | 滑动 ≥ 43.2dp 松手 → 吸附展开（thresholdRatio=0.20） | 单元测试 |
| AC-5 | 内容层左滑最大位移 = 216dp，禁止超出边界 | 单元测试 |
| AC-6 | 3 按钮按 分享→置顶→删除 顺序排列 | 视觉检查 |
| AC-7 | 初始 3 按钮完全重叠在 Delete 槽位 | 视觉检查 |
| AC-8 | 完全展开后按钮紧密相邻（间距 0），无间隙 | 视觉检查 |
| AC-9 | opacity 二元化：收起态=0，滑动/展开态=1（无淡入淡出） | 视觉检查 |
| AC-10 | 动画 300ms 弹性缓动（ElasticOutEasing） | 视觉体感 |
| AC-11 | 点击内容层 → 操作层收起 | 手动测试 |
| AC-12 | 点击任意按钮 → 执行回调后收起 | 单元测试 |
| AC-13 | 同一时间只有一张卡片展开（互斥） | 手动测试 |
| AC-14 | 与 ReorderableLazyColumn 拖拽不冲突 | 手动测试 |
| AC-15 | 批量模式下左滑禁用 | 手动测试 |
| AC-16 | 组件可嵌入任意容器，内容层支持自定义替换 | 5 个示例验证 |

---

## 十一、性能优化

### 11.1 GPU 加速

- 仅使用 `offset` 和 `alpha`（graphicsLayer）变更，避免触发 layout/paint
- 按钮使用 `Modifier.graphicsLayer { alpha = ... }` 而非 `Modifier.alpha`

### 11.2 动画优化

- `Animatable.snapTo` 跟手无延迟
- `Animatable.animateTo` 吸附/回弹，结束自动停止
- 避免在 `animateTo` 期间启动新的 `snapTo`（通过 `isExpanded` 状态管理）

### 11.3 内存管理

- `remember { Animatable(0f) }` 确保状态在重组间保持
- `coroutineScope` 自动绑定到 Composable 生命周期
- `pointerInput` 的 key 变化时自动取消旧手势监听

---

## 十二、兼容性

| 平台 | 最低版本 | 备注 |
|------|---------|------|
| Android API | 24+ (Android 7.0) | Compose 1.9.2 基线 |
| Compose BOM | 2026.04.01 | 项目当前版本 |
| Compose | 1.9.2 | 项目当前版本 |
| 测试框架 | Compose UI Test | `createComposeRule` |

---

## 十三、后续优化（不在本次范围）

- 置顶功能后端实现（`TodoItem.pinned` 字段 + 排序逻辑 + 持久化）
- 多选模式下批量置顶 / 批量删除
- 振动反馈（HapticFeedback）—— 操作命中按钮时短暂震动
- 按钮长按触发二次确认
- 卡片左滑/右滑出现不同操作（当前仅左滑展开操作区）
- 移动端 `passive listener` 优化
