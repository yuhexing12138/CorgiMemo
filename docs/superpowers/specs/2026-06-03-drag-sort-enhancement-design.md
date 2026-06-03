# CorgiMemo 拖拽排序增强设计文档

> 日期：2026-06-03  
> 状态：✅ 已批准  
> 范围：5 项优化（全部实施）

---

## 1. 概述

对 HomeScreen 的 ReorderableLazyColumn 拖拽排序功能进行 5 项增强，涵盖 **UI 可发现性**、**算法精度**、**撤销能力**、**感官反馈** 和 **主题适配**。

### 用户决策记录

| 决策项 | 选择 |
|--------|------|
| 实施范围 | 全部 5 项 |
| DragHandle 样式 | VerticalDragIndicator（竖向6点） |
| 撤销栈深度 | 10 次 |
| 触觉反馈方式 | 混合模式（Compose 原生 + HapticFeedbackManager） |
| 帧率监控 | 自定义指标 + Logcat |

---

## 2. 特性 1：DragHandle 集成到 TodoListItem

### 目标
为 TodoListItem 添加可视化的拖拽手柄图标，提升用户对拖拽排序功能的可发现性。

### 设计方案
**新增 start slot 参数 + isDragging 状态参数**

### 修改文件
- `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`

### 变更详情

#### 新增参数
```kotlin
@Composable
fun TodoListItem(
    // ... 现有参数保持不变 ...
    /** 前置内容槽位（用于放置 DragHandle）*/
    start: @Composable () -> Unit = {},
    /** 是否处于拖拽状态（传递给内部组件调整样式）*/
    isDragging: Boolean = false,
)
```

#### 布局变更
```
原有布局: [Checkbox] → [标题区域] → [操作按钮]
变更后:   [Checkbox] → [start slot] → [标题区域] → [操作按钮]
                          ↑
                   VerticalDragIndicator(isActive=isDragging)
```

#### HomeScreen 调用变更
```kotlin
ReorderableLazyColumn(
    items = filteredTodos,
    onReorder = { from, to -> viewModel.reorderTodos(from, to) },
    key = { _, todo -> todo.id }
) { index, todo, isDragging ->
    TodoListItem(
        todo = todo,
        isDragging = isDragging,           // ← 新增
        start = {                         // ← 新增
            VerticalDragIndicator(isActive = isDragging)
        },
        // ... 其余参数不变 ...
    )
}
```

### 已有资源复用
`DragHandle.kt` 中已有 `VerticalDragIndicator` 组件，支持：
- `isActive: Boolean` — 控制圆点颜色（默认灰 / 激活时暖橙色 #FF9A5C）
- 使用 `MaterialTheme.colorScheme.onSurfaceVariant` — 自动适配暗色模式

---

## 3. 特性 2：精确索引计算算法

### 目标
替换 `ReorderableLazyColumn.onDragStart` 中的 TODO 占位符和硬编码的 `100.dp.value`，使用真实的列表项布局信息进行像素级精确的位置映射。

### 设计方案
**使用 LazyListLayoutInfo.visibleItemsInfo 遍历匹配**

### 修改文件
- `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt`

### 核心算法

#### 新增辅助函数
```kotlin
/**
 * 根据触摸 Y 坐标精确查找对应的列表项索引
 *
 * 遍历 LazyListLayoutInfo 的 visibleItemsInfo，
 * 找到 Y 坐标落在某项 offset~offset+size 范围内的项。
 *
 * @param layoutInfo LazyColumn 的布局信息
 * @param touchY 触摸点相对于 LazyColumn 容器的 Y 坐标
 * @return 匹配到的项索引，边界情况返回首项或末项索引
 */
private fun findIndexAtOffset(
    layoutInfo: LazyListLayoutInfo,
    touchY: Float
): Int {
    if (layoutInfo.visibleItemsInfo.isEmpty()) return -1
    
    for (item in layoutInfo.visibleItemsInfo) {
        if (touchY >= item.offset && touchY < item.offset + item.size) {
            return item.index
        }
    }
    
    // 边界处理：返回最近的可见项
    return when {
        touchY < layoutInfo.visibleItemsInfo.first().offset -> 
            layoutInfo.visibleItemsInfo.first().index
        else -> layoutInfo.visibleItemsInfo.last().index
    }
}
```

#### onDragStart 改造
```kotlin
onDragStart = { offset ->
    val layoutInfo = listState.layoutInfo
    draggedIndex = findIndexAtOffset(layoutInfo, offset.y)
    targetIndex = draggedIndex
    dragOffsetY = 0f
}
```

#### onDrag 中目标位置改造
```kotlin
// 替换硬编码 100.dp.value 为动态平均高度
val avgItemHeight = listState.layoutInfo.visibleItemsInfo
    .map { it.size.toFloat() }
    .average()
    .takeIf { it > 0 } ?: 100f

val approximateTargetIndex =
    ((dragOffsetY / avgItemHeight) + draggedIndex)
        .toInt().coerceIn(0, items.size - 1)
```

---

## 4. 特性 3：多级撤销历史栈

### 目标
将当前的单次撤销（`MutableStateFlow<ReorderUndoData?>`）扩展为支持最多 10 次连续操作的撤销历史栈。

### 设计方案
**ArrayDeque 栈结构 + 上限 10**

### 修改文件
- `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt`
- `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

### 数据结构变更

#### 删除旧代码
```kotlin
// 删除：
private val _pendingReorderUndo = MutableStateFlow<ReorderUndoData?>(null)
val pendingReorderUndo: StateFlow<ReorderUndoData?> = _pendingReorderUndo.asStateFlow()
```

#### 新增栈结构
```kotlin
/** 多级撤销历史栈 */
private val _reorderUndoStack = MutableStateFlow(ArrayDeque<ReorderUndoData>())
val reorderUndoStack: StateFlow<ArrayDeque<ReorderUndoData>> = 
    _reorderUndoStack.asStateFlow()

/** 撤销栈最大深度 */
private const val MAX_UNDO_STACK_SIZE = 10
```

#### reorderTodos() 变更要点
1. 构建新的 `ReorderUndoData` 对象
2. 创建新 ArrayDeque，将新数据 `addFirst()` 到栈顶
3. 超过 `MAX_UNDO_STACK_SIZE` 时 `removeLast()` 移除最旧记录
4. 更新 `_reorderUndoStack.value`
5. 启动 5 秒定时器（仅栈非空时）

#### undoReorder() 变更要点
1. 从栈顶 `removeFirst()` 获取最新的撤销数据
2. 使用 `oldPositions` 调用 `batchUpdatePositions()` 恢复
3. 更新 `_reorderUndoStack.value`
4. `loadTodos()` 刷新 UI

#### HomeScreen Snackbar 变更
- 监听 `reorderUndoStack.size` 变化（而非单个 nullable 对象）
- Snackbar 消息显示 `"已调整排序顺序 (N 层可撤销)"`
- 点击"撤销"调用 `viewModel.undoReorder()`

---

## 5. 特性 4：触觉/音效反馈（混合模式）

### 目标
在拖拽开始、拖拽过程、释放完成三个时机提供触觉/音效反馈，增强交互真实感。

### 设计方案
**混合模式：拖拽中用 Compose LocalHapticFeedback，成功/取消用 HapticFeedbackManager**

### 反馈时机表

| 事件 | 反馈类型 | API | 节流 |
|------|----------|-----|------|
| onDragStart | 微震提示开始 | Compose `LocalHapticFeedback(HapticFeedbackType.HandleTouch)` | 无 |
| onDrag 经过其他项 | 轻微节律震 | Compose `LocalHapticFeedback(HapticFeedbackType.TextMove)` | **200ms** |
| onDragEnd（成功重排） | 确认震+音效 | `HapticFeedbackManager(context, CONFIRM, hapticEnabled)` | 无 |
| onDragCancel | 取消震 | `HapticFeedbackManager(context, CANCEL, hapticEnabled)` | 无 |

### 修改文件
- `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` — 新增参数 + 节流逻辑
- `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` — 回调连接
- `app/src/main/java/com/corgimemo/app/animation/InteractiveCorgi.kt` — 可能需扩展 InteractionType

### ReorderableLazyColumn 新增参数
```kotlin
fun <T> ReorderableLazyColumn(
    // ... 现有参数 ...
    /** 拖拽过程中的触觉反馈回调（由外部提供 LocalHapticFeedback）*/
    onHapticFeedback: (() -> Unit)? = null,
    content: ...
)
```

### 节流实现（ReorderableLazyColumn 内部）
```kotlin
var lastHapticTime by remember { mutableLongStateOf(0L) }
val HAPTIC_THROTTLE_MS = 200L

// 在 onDrag 回调内：
val now = System.currentTimeMillis()
if (now - lastHapticTime > HAPTIC_THROTTLE_MS) {
    lastHapticTime = now
    onHapticFeedback?.invoke()
}
```

---

## 6. 特性 5：帧率监控 + 暗色模式验证

### 6a. 自定义动画帧率监控

#### 目标
为 ReorderableLazyColumn 中的入场动画和缩放动画添加帧耗时追踪，超阈值输出 Logcat 警告。

#### 新建文件
- `app/src/main/java/com/corgimemo/app/ui/components/AnimationMetrics.kt`

#### 核心 API：TrackedAnimateFloatAsState
```kotlin
/**
 * 带帧耗时监控的 animateFloatAsState 包装器
 *
 * 在动画执行过程中追踪每帧渲染耗时，
 * 超 16ms（60fps 阈值）时输出 Logcat 警告。
 *
 * @param label 监控标签（用于 Logcat 过滤: adb logcat -s AnimationPerf）
 */
@Composable
fun TrackedAnimateFloatAsState(
    targetValue: Float,
    animationSpec: AnimationSpec<Float> = spring(),
    label: String = "animation",
    warnThresholdMs: Float = 16f
): State<Float>
```

#### 替换目标（ReorderableLazyColumn 内）
- `entryAlpha` 动画 → 标签 `"entryAlpha"`
- `entryOffsetY` 动画 → 标签 `"entrySlideIn"`
- `currentScale`（拖拽缩放）→ 标签 `"dragScale"`

### 6b. 暗色模式验证清单

| 检查项 | 当前状态 | 所需修复 |
|--------|----------|----------|
| 拖拽阴影 elevation | ✅ MaterialTheme 自动处理 | 无 |
| DragHandle 圆点颜色 | ✅ onSurfaceVariant 自适应 | 无 |
| 弹性缩放 scale | ✅ 数值不受主题影响 | 无 |
| 入场动画 alpha 可见度 | ⚠️ 深色背景上 fade-in 可能偏弱 | 可选：translationY 从 20→30dp |
| Snackbar 对比度 | ✅ Material3 自动处理 | 无 |

---

## 7. 文件修改总览

| 文件 | 操作 | 涉及特性 |
|------|------|----------|
| `TodoListItem.kt` | 修改 | #1 DragHandle 集成 |
| `ReorderableLazyColumn.kt` | 修改 | #2 精确索引、#4 触觉反馈、#5a 帧率监控 |
| `HomeViewModel.kt` | 修改 | #3 多级撤销、#4 触觉回调 |
| `HomeScreen.kt` | 修改 | #1 DragHandle 调用、#3 Snackbar 逻辑 |
| `InteractiveCorgi.kt` | 可能微调 | #4 InteractionType 扩展 |
| `AnimationMetrics.kt` | **新建** | #5a 帧率监控工具 |

---

## 8. 实施顺序建议

```
1. AnimationMetrics.kt（新建）         ← 独立工具，无依赖
2. TodoListItem.kt（start + isDragging）← UI 基础设施
3. ReorderableLazyColumn.kt（3 合 1）   ← 精确算法 + 触觉 + 帧率监控
4. HomeViewModel.kt（撤销栈重构）       ← 数据层
5. HomeScreen.kt（集成所有变更）         ← 最终组装
6. 编译验证                              ← 确认无错误
```
