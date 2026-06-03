# V2.5 体验增强优化设计文档

> 日期: 2026-06-03
> 版本: V2.5 (基于 V2.4 的进一步体验增强)
> 状态: 已批准，实施中

## 概述

在 V2.4（高亮动画、多关键词高亮、Undo栈压缩、长按Undo菜单）的基础上，
进行 5 项对称性/体验深度优化：

| # | 功能 | 优先级 | 复杂度 |
|---|------|--------|--------|
| F1 | Redo 对称长按历史菜单 | P0 | 低 |
| F2 | LazyColumn 虚拟化菜单（>15条） | P0 | 低 |
| F3 | FAB 长按缩放脉冲动画 | P0 | 低 |
| F4 | 逐区间交错淡入高亮 | P1 | 中 |
| F5 | 编辑历史时间线（双入口） | P2 | 高 |

## F1: Redo 对称长按历史菜单

### 设计目标
与 Undo 长按菜单完全镜像对称，提供一致的操作体验。

### ViewModel 变更 (TodoEditViewModel.kt)

**新增方法：**
- `getRedoHistoryDescriptions(): List<Pair<Int, String>>` — 返回 Redo 栈倒序描述列表
- `redoToHistoryStep(steps: Int): AnnotatedString?` — 批量重做到指定历史点

**与 Undo 方法的差异：**
- Redo 栈的「倒序」意味着最早的 redo 记录在前（索引0 = 最先被 redo 的）
- `redoToHistoryStep` 从 Redo 栈头部开始取 steps 条记录
- 中间状态推入 **Undo** 栈（与 undoToHistoryStep 反向）

### UI 变更 (TodoEditScreen.kt)

- Redo FAB 添加 `combinedClickable(onClick, onLongClick)`
- 新增 `var showRedoHistoryMenu` 状态变量
- 新增 `DropdownMenu` 显示 Redo 历史（结构与 Undo 菜单一致）

## F2: LazyColumn 虚拟化菜单

### 设计目标
当历史条目超过 15 条时自动切换为虚拟化渲染，避免 DropdownMenu 性能问题。

### 实现策略

```
if (history.size > LAZY_THRESHOLD) {
    DropdownMenu(scrollState = rememberScrollState()) {
        LazyColumn { items(history) { ... } }
    }
} else {
    DropdownMenu { history.forEachIndexed { ... } }
}
```

- `LAZY_THRESHOLD = 15`
- 同时应用于 Undo 和 Redo 两个下拉菜单
- LazyColumn 需包裹在固定高度容器内或使用 DropdownMenu 的 scrollState

## F3: FAB 长按缩放脉冲动画

### 设计目标
长按 Undo/Redo FAB 时提供视觉反馈（1.0x → 1.15x 弹性缩放），暗示「有更多操作可用」。

### 实现方式

```kotlin
val fabScale by animateFloatAsState(
    targetValue = if (isLongPressing) 1.15f else 1f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "fabScale"
)
Modifier.graphicsLayer { scaleX = fabScale; scaleY = fabScale }
```

### 触发机制
- 使用 `detectTapGestures(onPress={...})` 获取按下/释放状态
- onPress 开始 → scale 放大；释放/取消 → scale 归位
- 与 combinedClickable 协作：onClick 处理单击，onPress 处理动画状态

## F4: 逐区间交错淡入高亮

### 设计目标
搜索结果出现时，每个高亮区块从左到右依次淡入，形成波浪扫描效果。

### 架构变更

**HighlightUtil.kt 新增：**

```kotlin
/** 高亮区间数据 */
data class HighlightRange(
    val text: String,
    val isHighlight: Boolean,
    val startIndex: Int
)

/** 返回拆分后的区间列表 */
fun buildHighlightRanges(text: String, keywords: List<String>, color: Color): List<HighlightRange>
```

**渲染管线变更：**
- 原：`buildHighlightedText()` → 单个 AnnotatedString → Text()
- 新：`buildHighlightRanges()` → List<HighlightRange> → Row{逐个 Text(独立 alpha) }

**TodoListItem.kt 动画参数：**
- 每个 range 的 `delayMillis = (startIndex * 2).coerceAtMost(300)` ms
- `animateFloatAsState(targetValue=1f/0f, tween(300, delayMillis))`
- `Modifier.graphicsLayer { alpha = rangeAlpha }`

## F5: 编辑历史时间线

### 设计目标
展示当前 Todo 的完整编辑时间线，支持点击任意历史节点恢复。

### 新建文件

| 文件 | 职责 |
|------|------|
| `EditHistoryScreen.kt` | 垂直时间线 UI |
| `EditHistoryViewModel.kt` | 数据聚合（内存栈 + DataStore 日志） |

### 数据模型

```kotlin
data class EditTimelineEntry(
    val id: Int,
    val timestamp: Long?,
    val contentPreview: String,
    val fullContent: String,
    val type: TimelineType,   // UNDO_SNAPSHOT / REDO_SNAPSHOT / LOG_ENTRY
    val isCurrent: Boolean
)
```

### 双入口

1. **编辑器入口**：TodoEditScreen 工具栏时钟图标 → navigate("edit_history/$todoId")
2. **设置页入口**：OperationHistoryScreen 增加 Tab 切换

### UI 布局
垂直时间轴：圆点 + 时间标签 + 内容预览卡片，当前状态高亮显示。

## 文件变更清单

| 文件 | 变更类型 | 涉及功能 |
|------|----------|----------|
| `TodoEditViewModel.kt` | 修改 | F1 (新增 Redo 方法), F5 (数据源) |
| `TodoEditScreen.kt` | 修改 | F1 (Redo菜单), F2 (LazyColumn), F3 (FAB动画), F5 (入口按钮) |
| `HighlightUtil.kt` | 修改 | F4 (HighlightRange + buildHighlightRanges) |
| `TodoListItem.kt` | 修改 | F4 (逐区间渲染+独立动画) |
| `EditHistoryScreen.kt` | **新建** | F5 (时间线页面) |
| `EditHistoryViewModel.kt` | **新建** | F5 (数据聚合) |
