# 同行图片拖拽重构设计文档

> 日期：2026-06-15
> 状态：已批准

## 1. 背景

当前图片拖拽系统支持两种模式：
- **INLINE_SORT**（同行交换）：Y偏移 < 30dp 时触发，目标图片显示虚线框
- **CROSS_LINE**（跨行交换/移动）：Y偏移 ≥ 30dp 时触发，支持跨行交换（虚线框）和跨行移动（橙色呼吸竖线）

用户需求：
1. 删除所有跨行图片交换和移动逻辑
2. 保留同行图片交换逻辑不变
3. 在同行之间新增图片移动逻辑，添加闪烁光标

## 2. 需求定义

### 2.1 同行交换（保持不变）

- 拖拽图片悬停在另一张图片上方 → 目标图片显示虚线框
- 释放后两张图片交换位置
- 判定逻辑：基于拖拽偏移的 `offsetUnits` 计算，与现有逻辑完全一致

### 2.2 同行移动（新增）

- 拖拽图片悬停在两张图片之间的间隙 → 显示橙色闪烁光标
- 光标位置在两两图片正中间
- 光标动画：类似文字光标闪烁（alpha 0.3~1.0 循环，周期约 1 秒）
- 释放后源图片从原位移除并插入到光标位置

### 2.3 交互规则

| 手指位置 | 视觉反馈 | 操作 |
|----------|----------|------|
| 悬停在图片上 | 虚线框 | 交换 |
| 悬停在图片间隙 | 闪烁光标 | 移动插入 |
| 悬停在源图片自身 | 无 | 无操作 |
| 悬停在最后一张图右侧 | 末尾光标 | 移动到末尾 |

## 3. 技术设计

### 3.1 删除项

#### CrossLineDragManager.kt
- `DragMode.CROSS_LINE` 枚举值
- `DropType` 整个枚举
- `DragState` 字段：`currentTargetLine`、`dropType`、`sourceImageHeightPx`
- `DragResult` 字段：`dropType`、`isCrossLineMove`、`targetLineIndex`
- 方法：`detectTargetRow()`、`detectDropPosition()`、`detectDropPositionWithIndex()`、`detectTargetImageIndex()`
- `applyDragResult()` 中的跨行分支
- `updateDrag()` 中所有跨行相关参数（`fingerY`、`imageCountMap`、`imageRowBoundsMap`、`imageRowScrollOffsetMap`、`imageRowBounds`、`rowBounds`）

#### DraggableImageAttachment.kt
- `showInsertLineBefore` / `showInsertLineAfter` 参数
- `InsertLineIndicator` 组件

#### CheckboxEditText.kt
- `onImageRowBoundsChanged` 参数
- `isDropTargetForCrossLine` 判定逻辑
- `showInsertLineBefore` / `showInsertLineAfter` 判定和透传
- `onImageRowBoundsChanged` 回调透传

#### TodoEditScreen.kt
- `imageRowBoundsMap` / `imageRowScrollOffsetMap` 缓存
- `onAttachmentDragUpdate` 中的跨行参数构建
- `onAttachmentDragEnd` 中的跨行结果处理

### 3.2 新增项

#### InlineDropType 枚举

```kotlin
enum class InlineDropType {
    NONE,           // 无目标
    SWAP,           // 交换：悬停在图片上
    INSERT_BEFORE,  // 移动：光标在图片左侧
    INSERT_AFTER    // 移动：光标在最后一张图右侧
}
```

#### DragState 新增字段

```kotlin
val inlineDropType: InlineDropType = InlineDropType.NONE
```

#### 同行 X 轴判定逻辑

基于手指在行内的相对 X 坐标（dp），判断是交换还是移动：

```
布局示意（3张图，宽100dp，间距8dp）：
  |← 图0 →| 8dp |← 图1 →| 8dp |← 图2 →|
  0       100   108     208   216     316

判定规则：
  relativeX ∈ [0, 100)   → SWAP(图0)
  relativeX ∈ [100, 108) → INSERT_BEFORE(图1)
  relativeX ∈ [108, 208) → SWAP(图1)
  relativeX ∈ [208, 216) → INSERT_BEFORE(图2)
  relativeX ∈ [216, ∞)   → INSERT_AFTER

  源图片自身位置不触发 SWAP
```

#### CursorIndicator 组件

替换原 `InsertLineIndicator`：

```
属性：
  - 宽度：2dp
  - 颜色：橙色
  - 高度：与图片同高（跟随 DraggableImageAttachment 的高度）
  - 动画：alpha 在 0.3~1.0 之间循环闪烁
    - 530ms 渐亮（0.3 → 1.0）
    - 530ms 渐暗（1.0 → 0.3）
  - 位置：在两张图片的正中间（8dp 间隙的中心，即距左图右边缘 4dp）
```

#### 数据操作

```
SWAP：与当前相同
  1. reorderedList.removeAt(fromIndex)
  2. reorderedList.add(toIndex, movedItem)

INSERT_BEFORE：
  1. reorderedList.removeAt(sourceIndex)
  2. insertIndex = if (targetIndex > sourceIndex) targetIndex - 1 else targetIndex
  3. reorderedList.add(insertIndex, movedItem)

INSERT_AFTER：
  1. reorderedList.removeAt(sourceIndex)
  2. reorderedList.add(reorderedList.size, movedItem)
```

### 3.3 保留项（同行交换逻辑不变）

| 保留项 | 说明 |
|--------|------|
| `DragMode.NONE` / `DragMode.INLINE_SORT` | 简化为两个值 |
| `DragState.isDragging` / `sourceLineIndex` / `sourceImageIndex` | 核心拖拽状态 |
| `DragState.currentTargetImage` | 同行目标图片索引 |
| `DragState.dragOffset` | 拖拽偏移量 |
| `isDropTargetInline` 判定 + 虚线框视觉 | 同行交换视觉反馈不变 |
| `applyDragResult()` 同行分支 | 同行数据操作不变 |

### 3.4 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| `CrossLineDragManager.kt` | 删除跨行逻辑，新增 `InlineDropType`，重写 `updateDrag()` 同行判定 |
| `DraggableImageAttachment.kt` | 删除 `showInsertLineBefore/After`，新增 `showCursorBefore/After`，替换 `InsertLineIndicator` 为 `CursorIndicator` |
| `CheckboxEditText.kt` | 删除跨行相关参数，新增 `showCursorBefore/After` 透传，删除 `onImageRowBoundsChanged` |
| `TodoEditScreen.kt` | 删除跨行回调逻辑，删除 `imageRowBoundsMap/ScrollOffsetMap`，简化 `onAttachmentDragEnd` |

## 4. 实现步骤

1. **CrossLineDragManager.kt**：删除跨行逻辑，新增 `InlineDropType`，重写 `updateDrag()`
2. **DraggableImageAttachment.kt**：替换插入线参数和组件
3. **CheckboxEditText.kt**：清理跨行参数，透传光标参数
4. **TodoEditScreen.kt**：清理跨行回调，简化数据流
5. **编译验证**
