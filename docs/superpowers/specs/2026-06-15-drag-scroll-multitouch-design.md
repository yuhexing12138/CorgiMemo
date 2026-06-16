# 拖拽时多指滚动设计文档

> 日期：2026-06-15
> 状态：已批准

## 1. 背景

当前图片拖拽使用 `detectDragGesturesAfterLongPress` 捕获手势，`change.consume()` 阻止了父容器滚动。当图片行中有多张图片超出屏幕时，用户无法在拖拽过程中滚动查看屏幕外的图片来进行交换或移动。

## 2. 需求定义

### 2.1 第二指滚动

| 操作 | 说明 |
|------|------|
| 触发 | 第一指处于拖拽中 + 第二指在图片行上水平滑动 |
| 行为 | 图片行水平滚动，露出屏幕外的图片 |
| 拖拽图片 | Popup 位置不变（图片固定在手指处），下方 Row 滚动 |
| 停止 | 第二指抬起 |

### 2.2 边缘自动滚动

| 操作 | 说明 |
|------|------|
| 触发 | 拖拽中的第一指靠近图片 Row 左/右边缘（< 40dp） |
| 速度 | 距离边缘越近越快，边缘处最大约 150dp/s |
| 停止 | 手指离开边缘区域 或 拖拽结束 |

### 2.3 滚动偏移补偿

当图片行发生滚动后，`updateDrag()` 中的 X 轴位置计算需要补偿滚动偏移量，确保目标图片索引计算正确。

## 3. 技术设计

### 3.1 多指 pointerInput 实现

在图片 Row 上添加额外的 `pointerInput`，使用 `awaitPointerEventScope` 检测多指：

```kotlin
// 图片 Row 的多指滚动处理
.pointerInput(isDragging) {
    if (!isDragging) return@pointerInput
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            // 找到第二指（非拖拽指针）
            val secondPointer = event.changes.firstOrNull { it.pressed }
            if (secondPointer != null) {
                val scrollDelta = secondPointer.position.x - secondPointer.previousPosition.x
                imageScrollState.scrollBy(-scrollDelta)
                secondPointer.consume()
            }
        }
    }
}
```

### 3.2 边缘自动滚动实现

在 `DraggableImageAttachment` 的 `onDrag` 中检测手指 X 坐标是否进入边缘区域：

```kotlin
// 边缘检测常量
val EDGE_THRESHOLD_DP = 40f
val MAX_SCROLL_SPEED_DP = 150f  // 每秒最大滚动距离

// 在 onDrag 中：
val fingerXInRow = change.position.x  // 手指在图片行内的 X 坐标
val rowWidth = componentSize.width.toFloat()
val isNearLeftEdge = fingerXInRow < EDGE_THRESHOLD_DP * density
val isNearRightEdge = fingerXInRow > rowWidth - EDGE_THRESHOLD_DP * density

// 计算滚动速度（离边缘越近越快）
val scrollSpeed = when {
    isNearLeftEdge -> {
        val distanceFromEdge = fingerXInRow / (EDGE_THRESHOLD_DP * density)
        -MAX_SCROLL_SPEED_DP * (1f - distanceFromEdge)  // 负值=向左滚动
    }
    isNearRightEdge -> {
        val distanceFromEdge = (rowWidth - fingerXInRow) / (EDGE_THRESHOLD_DP * density)
        MAX_SCROLL_SPEED_DP * (1f - distanceFromEdge)   // 正值=向右滚动
    }
    else -> 0f
}
```

通过 `LaunchedEffect` 驱动定时滚动，每帧调用 `scrollBy()`。

### 3.3 滚动偏移追踪

重新引入 `scrollOffsetPx` 状态并将其通过 `onAttachmentDragUpdate` 透传给 `CrossLineDragManager.updateDrag()`：

```kotlin
// CrossLineDragManager.updateDrag() 新增参数
fun updateDrag(
    currentOffset: Offset,
    density: Float,
    imageCount: Int = 0,
    scrollOffsetPx: Float = 0f  // 🆕 滚动偏移（像素）
) {
    // 在 X 轴计算中补偿滚动偏移
    val xOffsetDp = (currentOffset.x + scrollOffsetPx) / density
    // ... 其余计算逻辑不变
}
```

### 3.4 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| `CheckboxEditText.kt` | 图片 Row 上新增多指 `pointerInput`；跟踪 `scrollOffsetPx` 状态；在 `onAttachmentDragUpdate` 回调中透传滚动偏移 |
| `CrossLineDragManager.kt` | `updateDrag()` 新增 `scrollOffsetPx` 参数，在 `xOffsetDp` 计算中补偿滚动量 |
| `DraggableImageAttachment.kt` | 新增 `onEdgeScroll` 回调参数，在 `onDrag` 中检测边缘并触发回调 |
| `TodoEditScreen.kt` | `onAttachmentDragUpdate` 中透传 `scrollOffsetPx` |

## 4. 实现步骤

1. **CrossLineDragManager.kt**：`updateDrag()` 新增 `scrollOffsetPx` 参数
2. **DraggableImageAttachment.kt**：新增 `onEdgeScroll` 回调，在 `onDrag` 中检测边缘
3. **CheckboxEditText.kt**：多指 `pointerInput` + 滚动偏移追踪 + 透传
4. **TodoEditScreen.kt**：透传 `scrollOffsetPx`
5. **编译验证**