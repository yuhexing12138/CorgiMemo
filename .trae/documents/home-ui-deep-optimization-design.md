# 首页待办列表页 UI 深度优化 - 设计方案

> **日期**: 2025-01-20
> **版本**: v1.0
> **状态**: 待审核
> **技术方案**: Compose 原生动画 + 组件重构 (方案 A)

---

## 1. 整体架构设计

### 1.1 架构目标

将现有的 `HomeScreen.kt`（2460行）重构为**模块化组件架构**，提升可维护性和动画实现灵活性。

### 1.2 组件层次结构

```
HomeScreen (Coordinator)
├── EnhancedTopBar (标题栏)
│   ├── MenuButton (☰ 菜单)
│   ├── TitleWithUnderline (标题+下划线)
│   └── StatsButton (📊 统计)
├── SearchBar (搜索栏)
├── CorgiInteractionCard (柯基卡片) [已有]
├── FilterRow (过滤按钮) [已有]
├── EnhancedEmptyState (空状态) [增强]
├── AnimatedTodoList (待办列表 + 动画)
│   └── EnhancedTodoListItem (待办项) [增强]
└── AnimatedFAB (浮动按钮) [增强]
```

### 1.3 动画系统架构

```
AnimationController (统一管理)
├── PullToRefreshAnimation (下拉刷新)
├── CompleteTodoAnimation (完成待办)
├── DeleteTodoAnimation (删除待办)
├── ScrollItemAnimation (滚动列表项)
└── FabClickAnimation (FAB 点击)
```

---

## 2. 标题栏增强设计 (EnhancedTopBar)

### 2.1 布局结构

```
┌─────────────────────────────────────────────┐
│  ☰        📝 我的待办              📊      │  ← 高度 56dp
│           ════════════                     │  ← 暖橙色下划线
└─────────────────────────────────────────────┘
```

### 2.2 组件规格

| 元素 | 规格 | 说明 |
|------|------|------|
| **☰ 菜单按钮** | Icon 24dp, padding 12dp | 点击弹出 AlertDialog |
| **标题文字** | "📝 我的待办", 18sp Bold | 使用 UiTextStyles.CardTitle |
| **装饰下划线** | 高度 2dp, 颜色 #FF9A5C | Canvas 绘制笔触效果 |
| **📊 统计图标** | Icon 24dp, padding 12dp | 点击弹出排序弹窗 |

### 2.3 交互逻辑

- **☰ 菜单点击**: 弹出 AlertDialog，选项包括：
  - 🏠 首页
  - 📊 统计
  - ⚙️ 设置
  - ❓ 帮助与反馈

- **📊 统计点击**: 弹出排序选项：
  - ⬇️ 按时间排序（默认）
  - 🔴 按优先级排序
  - 📂 按分类排序
  - ✅ 按完成状态排序

### 2.4 下划线动画

使用 `Animatable` 实现下划线从左到右的绘制动画：

```kotlin
val underlineWidth by animateFloatAsState(
    targetValue = if (isVisible) 1f else 0f,
    animationSpec = tween(durationMillis = 600, easing = EaseOutQuart)
)
```

---

## 3. 搜索栏设计 (SearchBar)

### 3.1 布局结构

```
┌─────────────────────────────────────────────┐
│  🔍 ┌─────────────────────────────────┐    │
│     │  输入要搜索的内容...            │    │  ← 背景 #FFF3E8
│     └─────────────────────────────────┘    │    圆角 24dp, 高度 48dp
└─────────────────────────────────────────────┘
```

### 3.2 视觉规格

| 属性 | 值 | 说明 |
|------|-----|------|
| **背景色** | `#FFF3E8` | UiColors.SearchBackground (新增) |
| **圆角** | 24.dp | UiDimensions.cornerRadiusLarge |
| **高度** | 48.dp | 固定高度 |
| **图标颜色** | `#FF9A5C` | UiColors.Primary |
| **占位文字** | "输入要搜索的内容..." | 14sp, color: TextSecondary |
| **内边距** | horizontal 16.dp, vertical 12.dp | |

### 3.3 功能特性

1. **实时搜索**: 输入时即时过滤待办列表（debounce 300ms）
2. **清空按钮**: 输入内容后显示 ✕ 按钮
3. **空状态提示**: 无匹配结果时显示"未找到相关待办"
4. **键盘交互**:
   - Enter 键：执行搜索
   - 搜索图标点击：聚焦输入框

### 3.4 状态管理

```kotlin
data class SearchState(
    val query: String = "",
    val isFocused: Boolean = false,
    val isSearching: Boolean = false
)
```

在 `HomeViewModel` 中添加：
- `searchQuery: StateFlow<String>`
- `filteredTodos: StateFlow<List<TodoItem>>` (组合 searchQuery + filterStatus)
- `fun updateSearchQuery(query: String)`
- `fun clearSearch()`

---

## 4. 空状态增强设计 (EnhancedEmptyState)

### 4.1 布局结构

```
┌─────────────────────────────────────────────┐
│                                             │
│           🐕 (柯基歪头动画)                  │  ← FrameAnimation(corgi_tilt)
│              200×200 dp                      │
│                                             │
│      还没有待办~                             │  ← 20sp Bold
│  点击下方按钮添加第一个待办吧！               │  ← 14sp Secondary
│                                             │
│  ┌─────────────────────────────────┐       │
│  │        ➕ 添加待办               │       │  ← CTA 按钮
│  └─────────────────────────────────┘       │     暖橙色背景, 圆角 12dp
│                                             │
└─────────────────────────────────────────────┘
```

### 4.2 不同场景的空状态配置

| 页面/场景 | 柯基姿态 | 标题 | 描述文案 | CTA 文字 |
|----------|---------|------|---------|---------|
| **首页待办为空** | TILT (歪头) | 还没有待办~ | 点击下方按钮添加第一个待办吧！ | ➕ 添加待办 |
| **已完成为空** | SIT (坐着) | 还没有已完成的待办~ | 完成任务就能在这里看到啦！ | 去添加 |
| **搜索无结果** | CONFUSED (困惑) | 未找到相关待办~ | 换个关键词试试？ | 清空搜索 |
| **分类为空** | LIE (趴着) | 「xxx」还没有待办~ | 在分类下添加待办试试？ | 添加待办 |

### 4.3 CTA 按钮规格

| 属性 | 值 |
|------|-----|
| **背景色** | `UiColors.Primary` (#FF9A5C) |
| **文字色** | White |
| **圆角** | 12.dp |
| **内边距** | horizontal 32.dp, vertical 14.dp |
| **文字大小** | 15.sp Medium |
| **点击缩放动画** | scale 0.95 → 1.0 (100ms) |

### 4.4 柯基动画增强

复用现有 `FrameAnimation` 组件，但增加以下效果：
- **呼吸动画**: 缓慢的上下浮动 (±4dp, 2000ms 循环)
- **眨眼动画**: 随机眨眼（已内置在帧动画中）
- **点击反馈**: 点击柯基时触发 `InteractionType.PAT` 事件

---

## 5. FAB 优化设计 (AnimatedFAB)

### 5.1 视觉规格

| 属性 | 当前值 | 目标值 |
|------|--------|--------|
| **图标** | Icons.Default.Add (+) | Icons.Default.Edit (✏️) |
| **背景色** | MaterialTheme.colorScheme.primary | `UiColors.Primary` (#FF9A5C) |
| **尺寸** | 56.dp (默认) | 56.dp (保持) |
| **位置** | 右下角 (默认) | 右下角, bottom = 88.dp |
| **阴影** | DefaultElevation | elevation = 6.dp |

### 5.2 点击动画序列

```
用户点击 FAB
    ↓
① 缩放动画: scale 1.0 → 0.9 → 1.1 → 1.0 (总时长 300ms)
    ↓
② 旋转动画: rotation 0° → 180° (同时进行)
    ↓
③ 导航到创建页面 (动画结束后)
```

**实现代码示例**:

```kotlin
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.9f else 1.0f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
)

val rotation by animateFloatAsState(
    targetValue = if (isAnimating) 360f else 0f,
    animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic)
)

FloatingActionButton(
    onClick = {
        isPressed = true
        isAnimating = true
        // 导航逻辑...
    },
    modifier = Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = rotation
        }
) {
    Icon(Icons.Default.Edit, contentDescription = "编辑")
}
```

---

## 6. 待办列表项增强设计 (EnhancedTodoListItem)

### 6.1 复选框改造

#### 当前问题
- 使用 Material3 默认方形 Checkbox
- 样式与设计规范不符

#### 目标设计

**未完成状态**:
```
○ (空心圆, 2dp 边框, 颜色 Outline)
```

**已完成状态**:
```
● (实心圆, 背景色 Primary, 白色 ✓ 图标)
```

**规格**:

| 属性 | 值 |
|------|-----|
| **尺寸** | 24.dp × 24.dp |
| **形状** | Circle (圆形) |
| **边框宽度** | 2.dp (未完成时) |
| **未完成边框色** | `UiColors.Outline` (#BDBDBD) |
| **完成背景色** | `UiColors.Primary` (#FF9A5C) |
| **完成图标色** | White |
| **点击动画** | scale 弹性动画 (spring) |

**实现方式**: 自定义 `CircularCheckbox` Composable

```kotlin
@Composable
fun CircularCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioBouncy)
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (checked) UiColors.Primary else Color.Transparent)
            .border(
                width = if (checked) 0.dp else 2.dp,
                color = if (checked) Color.Transparent else UiColors.Outline,
                shape = CircleShape
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已完成",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
```

### 6.2 优先级圆点增强

#### 当前实现
- 已有 `PriorityBadge` 组件（显示文字标签）

#### 目标设计
改为彩色圆点 + 可选文字标签：

| 优先级 | 圆点颜色 | 显示样式 |
|--------|---------|---------|
| **高** | `#EF4444` (红) | 🔴 或 "高" |
| **中** | `#F59E0B` (黄) | 🟡 或 "中" |
| **低** | `#10B981` (绿) | 🟢 或 "低" |

**圆点规格**:

| 属性 | 值 |
|------|-----|
| **尺寸** | 8.dp × 8.dp |
| **形状** | Circle |
| **间距** | 左侧 8.dp |

### 6.3 第二行信息展示

#### 当前实现
- 已显示分类标签、语音备注、截止日期

#### 目标布局（优化版）

```
第一行: ○ 提交周报          🔴
第二行: 📅 今天 18:00  📁 工作
```

**信息优先级**:
1. 截止日期（必显）: 📅 MM-dd HH:mm
2. 分类标签（必显）: 📁 分类名
3. 语音备注（可选）: 🎤 时长

### 6.4 长按 BottomSheet 增强

#### 当前实现
- AlertDialog 弹出菜单（分享为图片、批量选择）

#### 目标设计
改为 ModalBottomSheet：

```
┌─────────────────────────────────────┐
│  ═══════════════════════════════    │  ← 拖动指示器
│                                     │
│  📌 置顶待办                        │
│  ✏️ 编辑待办                        │
│  🖼️ 分享为图片                      │
│  📋 批量选择                        │
│  ──────────────────────────────    │
│  🗑️ 删除待办                       │  ← 红色警告
│                                     │
└─────────────────────────────────────┘
```

**操作项配置**:

| 操作 | 图标 | 文字 | 颜色 |
|------|------|------|------|
| 置顶 | Icons.Default.PushPin | 置顶待办 | OnSurface |
| 编辑 | Icons.Default.Edit | 编辑待办 | OnSurface |
| 分享 | Icons.Default.Share | 分享为图片 | OnSurface |
| 批量选择 | Icons.Default.SelectAll | 批量选择 | OnSurface |
| 删除 | Icons.Default.Delete | 删除待办 | Error (红色) |

---

## 7. 微交互动画系统设计

### 7.1 动画概览表

| # | 动画名称 | 触发场景 | 时长 | 复杂度 | 优先级 |
|---|---------|---------|------|--------|--------|
| 1 | PullToRefreshAnimation | 下拉刷新 | 800ms | ⭐⭐⭐ | P0 |
| 2 | CompleteTodoAnimation | 完成待办 | 400ms | ⭐⭐⭐ | P0 |
| 3 | DeleteTodoAnimation | 删除待办 | 300ms | ⭐⭐ | P0 |
| 4 | ScrollItemAnimation | 滚动列表 | 200ms/项 | ⭐⭐ | P1 |
| 5 | FabClickAnimation | 点击 FAB | 300ms | ⭐⭐ | P1 |

### 7.2 动画 1: 下拉刷新 (PullToRefreshAnimation)

**视觉效果**:
```
初始状态: 柯基在屏幕右侧不可见
    ↓ (用户下拉)
中间态: 柯基从右侧滑入屏幕 (translateX 100% → 0%)
         同时摇尾巴动画循环播放
    ↓ (释放)
结束态: 柯基回到右侧消失, 刷新指示器显示
```

**技术实现**:

```kotlin
@Composable
fun PullToRefreshAnimation(
    isRefreshing: Boolean,
    pullProgress: Float, // 0f - 1f
    modifier: Modifier = Modifier
) {
    val offsetX by animateFloatAsState(
        targetValue = if (isRefreshing) 0f else 100f * (1 - pullProgress),
        animationSpec = tween(durationMillis = 800, easing = EaseOutBack),
        label = "corgiOffsetX"
    )

    val tailWagAngle by animateFloatAsState(
        targetValue = if (isRefreshing || pullProgress > 0.5f) 30f else 0f,
        animationSpec = repeatable(
            iterations = Int.MAX_VALUE,
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tailWag"
    )

    Box(modifier = modifier.offset(x = offsetX.dp)) {
        InteractiveCorgi(
            pose = CorgiPose.RUN, // 跑动姿态
            // ... 其他参数
        )
        // 尾巴摇动动画层 (叠加旋转)
    }
}
```

**关键参数**:
- 平移距离: 屏幕 100% 宽度
- 动画曲线: `EaseOutBack` (轻微过冲效果)
- 尾巴摆动角度: ±30°
- 尾巴频率: 200ms/次 (往返)

### 7.3 动画 2: 完成待办 (CompleteTodoAnimation)

**视觉效果**:
```
初始状态: 正常显示的待办卡片
    ↓ (用户点击复选框)
① 卡片向右滑出: translateX 0 → 120% (200ms, EaseIn)
② 绿色 ✓ 弹出: scale 0 → 1.2 → 1.0 (150ms, spring)
③ 柯基跳跃: translateY 0 → -20 → 0 (200ms, spring)
④ 卡片从列表移除 (动画结束后)
```

**技术实现**:

```kotlin
@Composable
fun CompleteTodoAnimation(
    isCompleted: Boolean,
    onComplete: () -> Unit,
    content: @Composable () -> Unit
) {
    val cardOffsetX by animateFloatAsState(
        targetValue = if (isCompleted) 300f else 0f,
        animationSpec = tween(200, easing = EaseIn),
        label = "cardSlideOut"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkPop"
    )

    Box(modifier = Modifier.offset(x = cardOffsetX.dp)) {
        content()

        // 绿色 ✓ 弹出层
        if (isCompleted && checkScale > 0) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已完成",
                tint = Color(0xFF4CAF50), // 绿色
                modifier = Modifier
                    .size(48.dp)
                    .scale(checkScale)
                    .align(Alignment.Center)
            )
        }
    }

    // 监听动画完成
    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            delay(400) // 等待滑出动画完成
            onComplete()
        }
    }
}
```

**关键参数**:
- 卡片滑出距离: 300dp
- ✓ 弹出最大缩放: 1.2x
- ✓ 颜色: Success Green (#4CAF50)
- 总时长: 400ms (包含延迟)

### 7.4 动画 3: 删除待办 (DeleteTodoAnimation)

**视觉效果**:
```
初始状态: 正常显示的待办卡片
    ↓ (用户滑动删除或确认删除)
① 卡片向左滑出: translateX 0 → -120% (250ms, EaseIn)
② 红 × 弹出: scale 0 → 1.1 → 1.0 (120ms, spring)
③ 卡片淡出: alpha 1 → 0 (同步进行)
④ 从 DOM 移除 (动画结束后)
```

**技术实现**:

```kotlin
@Composable
fun DeleteTodoAnimation(
    isDeleting: Boolean,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val cardOffsetX by animateFloatAsState(
        targetValue = if (isDeleting) -300f else 0f,
        animationSpec = tween(250, easing = EaseIn),
        label = "cardSlideLeft"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(250),
        label = "cardFade"
    )

    val crossScale by animateFloatAsState(
        targetValue = if (isDeleting) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "crossPop"
    )

    Box(
        modifier = Modifier
            .offset(x = cardOffsetX.dp)
            .graphicsLayer { alpha = cardAlpha }
    ) {
        content()

        // 红 × 弹出层
        if (isDeleting && crossScale > 0) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFEF4444), // 红色
                modifier = Modifier
                    .size(40.dp)
                    .scale(crossScale)
                    .align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            delay(300)
            onDelete()
        }
    }
}
```

**关键参数**:
- 滑出方向: 向左 (-X轴)
- × 颜色: Error Red (#EF4444)
- 总时长: 300ms

### 7.5 动画 4: 滚动列表项 (ScrollItemAnimation)

**视觉效果**:
```
新项目进入可视区域:
    ↓
① 初始状态: alpha=0, offsetY=20dp
② 渐入+上移: alpha 0→1, offsetY 20→0 (200ms, EaseOut)
③ 每个项目依次延迟: index * 50ms
```

**技术实现**:

```kotlin
@Composable
fun AnimatedLazyColumn(
    items: List<T>,
    key: ((T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(item: T, index: Int) -> Unit
) {
    LazyColumn {
        items(
            count = items.size,
            key = key?.let { { index -> it(items[index]) } }
        ) { index ->
            val visibleState = remember { MutableTransitionState(false) }
            visibleState.targetState = true

            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(
                    animationSpec = tween(200, delayMillis = index * 50)
                ) + slideInVertically(
                    initialOffsetY = { 20 },
                    animationSpec = tween(200, delayMillis = index * 50)
                )
            ) {
                itemContent(items[index], index)
            }
        }
    }
}
```

**关键参数**:
- 单项时长: 200ms
- 项间延迟: 50ms (错开进入效果)
- 上移距离: 20dp
- 最大可见项数: 建议 ≤10 (性能考虑)

### 7.6 动画 5: FAB 点击 (FabClickAnimation)

**视觉效果**:
```
用户按下 FAB:
    ↓
① 按压缩放: scale 1.0 → 0.9 (100ms)
② 释放回弹: scale 0.9 → 1.1 (100ms, spring)
③ 旋转: rotation 0° → 360° (200ms, EaseInOut)
④ 导航跳转 (动画结束后)
```

**技术实现**:

```kotlin
@Composable
fun AnimatedFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var isAnimating by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.9f
            isAnimating -> 1.05f
            else -> 1.0f
        },
        animationSpec = if (isPressed)
            spring(dampingRatio = Spring.DampingRatioHighBouncy)
        else
            spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fabScale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isAnimating) 360f else 0f,
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "fabRotation"
    )

    FloatingActionButton(
        onClick = {
            isPressed = true
            isAnimating = true
            // 导航逻辑
            onClick()
            // 重置状态
            isPressed = false
            isAnimating = false
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            },
        containerColor = UiColors.Primary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "编辑",
            tint = Color.White
        )
    }
}
```

**关键参数**:
- 按压缩放: 0.9x
- 回弹缩放: 1.05x
- 旋转角度: 360°
- 总时长: 300ms
- 弹簧阻尼: Bouncy (活泼感)

---

## 8. 新增资源清单

### 8.1 颜色资源 (colors_ui.xml 新增)

```xml
<!-- 搜索栏背景 -->
<color name="ui_search_background">#FFF3E8</color>

<!-- 优先级颜色 -->
<color name="ui_priority_high">#EF4444</color>
<color name="ui_priority_medium">#F59E0B</color>
<color name="ui_priority_low">#10B981</color>

<!-- 动画辅助色 -->
<color name="ui_success_green">#4CAF50</color>
<color name="ui_error_red">#EF4444</color>
```

### 8.2 尺寸资源 (dimens.xml 新增)

```xml
<!-- 搜索栏 -->
<dimen name="ui_search_bar_height">48dp</dimen>
<dimen name="ui_search_bar_corner_radius">24dp</dimen>

<!-- FAB -->
<dimen name="ui_fab_size">56dp</dimen>
<dimen name="ui_fab_bottom_margin">88dp</dimen>

<!-- 优先级圆点 -->
<dimen name="ui_priority_dot_size">8dp</dimen>

<!-- 复选框 -->
<dimen name="ui_checkbox_size">24dp</dimen>
<dimen name="ui_checkbox_border_width">2dp</dimen>
```

### 8.3 图标资源 (无需新增)

使用 Material Icons：
- `Icons.Default.Menu` (☰)
- `Icons.Default.Search` (🔍)
- `Icons.Default.BarChart` (📊)
- `Icons.Default.Edit` (✏️)
- `Icons.Default.Close` (×)
- `Icons.Default.Check` (✓)
- `Icons.Default.PushPin` (📌)
- `Icons.Default.Share` (分享)
- `Icons.Default.Delete` (🗑️)

---

## 9. 文件变更清单

### 9.1 新增文件

| 文件路径 | 说明 |
|---------|------|
| `app/.../ui/components/EnhancedTopBar.kt` | 增强标题栏组件 |
| `app/.../ui/components/SearchBar.kt` | 搜索栏组件 |
| `app/.../ui/components/CircularCheckbox.kt` | 圆形复选框组件 |
| `app/.../ui/components/PriorityDot.kt` | 优先级圆点组件 |
| `app/.../ui/components/AnimatedFAB.kt` | 动画 FAB 组件 |
| `app/.../ui/components/AnimatedTodoListItem.kt` | 动画待办列表项 |
| `app/.../ui/components/TodoActionSheet.kt` | 待办操作 BottomSheet |
| `app/.../ui/animations/PullToRefreshAnimation.kt` | 下拉刷新动画 |
| `app/.../ui/animations/CompleteTodoAnimation.kt` | 完成待办动画 |
| `app/.../ui/animations/DeleteTodoAnimation.kt` | 删除待办动画 |
| `app/.../ui/animations/ScrollItemAnimation.kt` | 滚动列表项动画 |

### 9.2 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `HomeScreen.kt` | 集成新组件，替换原有 TopAppBar/FAB/列表 |
| `HomeViewModel.kt` | 添加搜索状态管理、动画触发逻辑 |
| `TodoListItem.kt` | 替换为 AnimatedTodoListItem 或重构 |
| `EmptyState.kt` | 增强空状态（CTA 按钮、多场景配置） |
| `UiColors.kt` | 新增搜索背景色、优先级颜色 |
| `UiDimensions.kt` | 新增搜索栏、FAB、复选框尺寸 |
| `res/values/colors_ui.xml` | 新增 XML 颜色定义 |
| `res/values/dimens.xml` | 新增 XML 尺寸定义 |

---

## 10. 实施步骤（建议顺序）

### Phase 1: 基础 UI 组件 (预计工作量: 3-4小时)

1. **Step 1.1**: 创建 `CircularCheckbox.kt` 组件
2. **Step 1.2**: 创建 `PriorityDot.kt` 组件
3. **Step 1.3**: 创建 `SearchBar.kt` 组件
4. **Step 1.4**: 创建 `EnhancedTopBar.kt` 组件
5. **Step 1.5**: 创建 `AnimatedFAB.kt` 组件
6. **Step 1.6**: 更新资源文件 (colors_ui.xml, dimens.xml)

**验证标准**:
- [ ] 圆形复选框正确显示未完成/已完成状态
- [ ] 搜索栏可输入、清空、实时过滤
- [ ] 标题栏显示菜单、下划线、统计图标
- [ ] FAB 显示铅笔图标并支持点击

---

### Phase 2: 列表项增强 (预计工作量: 2-3小时)

1. **Step 2.1**: 重构 `TodoListItem.kt` (集成 CircularCheckbox + PriorityDot)
2. **Step 2.2**: 创建 `TodoActionSheet.kt` (BottomSheet 替代 AlertDialog)
3. **Step 2.3**: 优化第二行信息布局 (日期 + 分类标签)
4. **Step 2.4**: 更新 `EmptyState.kt` (CTA 按钮 + 多场景配置)

**验证标准**:
- [ ] 待办项使用圆形复选框
- [ ] 优先级显示为彩色圆点
- [ ] 长按弹出 BottomSheet (包含置顶/编辑/删除)
- [ ] 空状态显示柯基动画 + CTA 按钮

---

### Phase 3: 微交互动画 (预计工作量: 3-4小时)

1. **Step 3.1**: 实现 `FabClickAnimation` (FAB 缩放+旋转)
2. **Step 3.2**: 实现 `ScrollItemAnimation` (列表项渐入)
3. **Step 3.3**: 实现 `CompleteTodoAnimation` (完成滑出+✓弹出)
4. **Step 3.4**: 实现 `DeleteTodoAnimation` (删除滑出+×弹出)
5. **Step 3.5**: 实现 `PullToRefreshAnimation` (柯基跑入+摇尾)

**验证标准**:
- [ ] FAB 点击时有缩放+旋转动画 (300ms)
- [ ] 列表项滚动时依次淡入 (200ms/项)
- [ ] 完成待办时卡片右滑+绿色✓弹出 (400ms)
- [ ] 删除待办时卡片左滑+红色×弹出 (300ms)
- [ ] 下拉刷新时柯基跑入+摇尾巴 (800ms)

---

### Phase 4: 集成测试 (预计工作量: 1-2小时)

1. **Step 4.1**: 在 `HomeScreen.kt` 中集成所有新组件
2. **Step 4.2**: 更新 `HomeViewModel.kt` (搜索、动画状态)
3. **Step 4.3**: 端到端功能测试
4. **Step 4.4**: 性能测试 (动画流畅度、内存占用)
5. **Step 4.5**: 深色模式适配检查

**验证标准**:
- [ ] 所有功能正常工作无崩溃
- [ ] 动画流畅 (60fps, 无掉帧)
- [ ] 深色模式下颜色对比度符合 WCAG AA 标准
- [ ] 内存无泄漏 (LeakCanary 检查通过)

---

## 11. 技术风险与应对

### 11.1 风险清单

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|---------|
| **Compose Animation 学习曲线** | 开发效率降低 | 中 | 提供详细代码示例，参考官方文档 |
| **复杂动画性能问题** | 掉帧、卡顿 | 低 | 使用 `graphicsLayer` 加速，避免重组 |
| **状态管理复杂度** | Bug 增多 | 中 | 明确 ViewModel 职责边界，单一数据源 |
| **深色模式适配遗漏** | 用户投诉 | 低 | 同步更新 values-night 资源 |
| **低端机型兼容性** | 动画卡顿 | 低 | 根据 API level 降低动画精度 |

### 11.2 性能优化策略

1. **使用 `graphicsLayer`**: 所有动画属性 (scale, rotation, alpha) 通过 graphicsLayer 修改，避免触发重组
2. **remember 关键状态**: 动画状态使用 `remember` 保存，避免重复计算
3. **合理使用 `derivedStateOf`**: 派生状态自动缓存
4. **LazyColumn 优化**: 限制同时动画的项目数 (≤10)
5. **debounce 搜索输入**: 避免频繁过滤列表

---

## 12. 成功标准

### 12.1 功能完整性

- [ ] 标题栏包含 ☰ 菜单、标题+下划线、📊 统计
- [ ] 搜索栏支持实时过滤和清空
- [ ] 空状态显示柯基动画 + CTA 按钮
- [ ] FAB 为铅笔图标 + 点击动画
- [ ] 待办项使用圆形复选框 + 优先级圆点
- [ ] 长按弹出 BottomSheet (5个操作项)
- [ ] 5 种微交互动画全部实现

### 12.2 视觉还原度

- [ ] 与设计稿视觉偏差 < 5%
- [ ] 颜色值完全符合设计规范
- [ ] 尺寸误差 < 1dp
- [ ] 动画时长误差 < 50ms

### 12.3 性能指标

- [ ] 动画帧率 ≥ 55fps (中高端机型)
- [ ] 动画帧率 ≥ 45fps (低端机型)
- [ ] 内存增长 < 10MB (相比优化前)
- [ ] 冷启动时间增加 < 200ms

### 12.4 代码质量

- [ ] 组件职责单一，可独立测试
- [ ] 无硬编码颜色/尺寸 (全部使用 UiColors/UiDimensions)
- [ ] 代码注释完整 (公共 API 必须注释)
- [ ] 通过 lint 检查无 warning

---

## 附录 A: 参考资料

- [Compose Animation Official Docs](https://developer.android.com/jetpack/compose/animation)
- [Compose Graphics Layer](https://developer.android.com/jetpack/compose/graphics/draw/graphics-layer)
- [Material Design 3 - FAB](https://m3.google.io/components/floating-action-button)
- [Material Design 3 - Checkboxes](https://m3.google.io/components/checkbox)
- [WCAG 2.0 Contrast Guidelines](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html)

---

## 附录 B: 术语表

| 术语 | 全称 | 说明 |
|------|------|------|
| FAB | FloatingActionButton | 浮动操作按钮 |
| BottomSheet | 底部弹出面板 | 从底部滑出的模态面板 |
| Compose | Jetpack Compose | Android 现代声明式 UI 工具包 |
| StateFlow | State Flow | Kotlin Flow 的状态持有者 |
| LaunchedEffect | Launched Effect | Compose 副作用 API |
| Animatable | Animatable | Compose 动画值包装器 |
| graphicsLayer | Graphics Layer | Compose 图形图层 (硬件加速) |
