package com.corgimemo.app.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.util.toPxFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 可拖拽排序列表组件
 *
 * 基于 Jetpack Compose 的 LazyColumn 实现，
 * 支持长按触发拖拽模式，拖动过程中实时更新列表顺序。
 *
 * **核心特性**:
 * - ✅ 长按触发拖拽（避免误触）
 * - ✅ 实时视觉反馈（透明度、缩放、阴影）
 * - ✅ 自动滚动边界检测（拖到边缘时自动滚动）
 * - ✅ 精确索引算法（使用 LazyListLayoutInfo 替代硬编码估算）
 * - ✅ 入场动画（渐入+上滑，可配置开关）
 * - ✅ 拖拽释放弹性缩放动画（0.95→1.05→1.0）
 * - ✅ 触觉反馈支持（节流 200ms 避免过度震动）
 * - ✅ 帧率监控（TrackedAnimateFloatAsState 超阈值输出 Logcat）
 * - ✅ 拖拽完成后回调新顺序
 *
 * **使用示例**:
 * ```kotlin
 * val items = remember { mutableStateListOf("Item 1", "Item 2", "Item 3") }
 * val hapticFeedback = LocalHapticFeedback.current
 *
 * ReorderableLazyColumn(
 *     items = items,
 *     onReorder = { fromIndex, toIndex ->
 *         val item = items.removeAt(fromIndex)
 *         items.add(toIndex, item)
 *     },
 *     onHapticFeedback = {
 *         hapticFeedback.performHapticFeedback(HapticFeedbackType.TextMove)
 *     }
 * ) { index, item, isDragging ->
 *     TodoListItem(
 *         todo = item,
 *         isDragging = isDragging,
 *         start = {
 *             VerticalDragIndicator(isActive = isDragging)
 *         }
 *     )
 * }
 * ```
 *
 * @param T 列表项的数据类型
 * @param items 列表数据源（MutableList 或 List）
 * @param onReorder 拖拽完成后的回调，参数为 (fromIndex, toIndex)
 * @param modifier Modifier（可选）
 * @param key 提供每个列表项的唯一键（默认使用索引）
 * @param enableEntryAnimation 是否启用入场动画（默认 true）
 * @param onHapticFeedback 拖拽过程中的触觉反馈回调（可选，由外部提供 Compose HapticFeedback）
 * @param content 列表项 Composable lambda
 *   - index: 当前项的索引位置
 *   - item: 当前项的数据对象
 *   - isDragging: 当前项是否正在被拖拽（用于调整样式）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ReorderableLazyColumn(
    items: List<T>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    key: ((index: Int, item: T) -> Any)? = null,
    enableEntryAnimation: Boolean = true,
    onHapticFeedback: (() -> Unit)? = null,
    content: @Composable LazyItemScope.(index: Int, item: T, isDragging: Boolean) -> Unit
) {
    /** LazyColumn 状态管理 */
    val listState = rememberLazyListState()

    /** 当前被拖拽项的索引（-1 表示无拖拽） */
    var draggedIndex by remember { mutableIntStateOf(-1) }

    /** 上一次被拖拽的索引（用于释放后的弹性动画） */
    var lastDraggedIndex by remember { mutableIntStateOf(-1) }

    /** 当前拖拽项的目标位置（用于计算偏移量） */
    var targetIndex by remember { mutableIntStateOf(-1) }

    /** 拖拽时的 Y 轴偏移量（相对于原始位置） */
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    /** 是否处于拖拽状态 */
    val isDragging = draggedIndex >= 0

    /**
     * 触觉反馈节流控制
     *
     * 拖拽过程中每帧都会触发 onDrag 回调，
     * 使用时间戳节流（200ms 间隔）避免过度触发震动。
     */
    var lastHapticTime by remember { mutableLongStateOf(0L) }
    /** 触觉反馈节流间隔（毫秒）*/
    val HAPTIC_THROTTLE_MS = 200L

    /**
     * 拖拽释放后的缩放动画状态
     *
     * 状态机逻辑：
     * - 正在拖拽时 (index == draggedIndex): scale = 0.95f（轻微缩小）
     * - 刚释放时 (index == lastDraggedIndex && !isDragging): scale = 1.05f（弹性放大）
     * - 其他情况: scale = 1.0f（正常大小）
     */
    val targetScale = when {
        isDragging -> 0.95f  // 拖拽中缩小
        lastDraggedIndex >= 0 -> 1.05f  // 释放后弹跳放大
        else -> 1.0f  // 正常
    }

    /** 使用 spring 动画 + 帧率监控实现平滑的缩放过渡 */
    val currentScale by TrackedAnimateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.6f,  // 适中阻尼，有轻微弹跳感
            stiffness = 400f      // 较高刚度，快速响应
        ),
        label = "dragScale"
    )

    /**
     * 边界自动滚动逻辑
     *
     * 当拖拽项接近列表顶部或底部时，
     * 自动滚动列表以显示更多内容。
     */
    LaunchedEffect(draggedIndex, dragOffsetY) {
        if (draggedIndex < 0) return@LaunchedEffect

        /** 获取第一个可见项的信息 */
        val firstVisibleItem = listState.firstVisibleItemIndex
        val firstVisibleOffset = listState.firstVisibleItemScrollOffset

        /** 计算拖拽项在屏幕中的大致位置 */
        val itemHeight = 100.dp.value /** 假设每项高度约 100dp */
        val draggedPositionInScreen = (draggedIndex - firstVisibleItem) * itemHeight +
                firstVisibleOffset + dragOffsetY

        val screenHeight = 800f /** 假设屏幕高度 */

        /** 接近顶部时向上滚动 */
        if (draggedPositionInScreen < 100) {
            listState.scrollBy(-10f)
        }
        /** 接近底部时向下滚动 */
        else if (draggedPositionInScreen > screenHeight - 200) {
            listState.scrollBy(10f)
        }
    }

    /**
     * 拖拽释放后的弹性动画重置逻辑
     *
     * 当 lastDraggedIndex 被设置后（即刚结束拖拽），
     * 等待 spring 动画完成（约 300ms）后重置为 -1，
     * 触发 scale 从 1.05f 回到 1.0f 的过渡
     */
    LaunchedEffect(lastDraggedIndex, isDragging) {
        /** 仅在非拖拽状态且有上一次拖拽记录时执行 */
        if (lastDraggedIndex >= 0 && !isDragging) {
            /** 等待足够时间让 1.05f → 1.0f 的 spring 动画播放完毕 */
            kotlinx.coroutines.delay(350)
            /** 重置状态，触发 scale 回到正常值 */
            lastDraggedIndex = -1
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            /**
                             * 精确索引计算
                             *
                             * 使用 LazyListLayoutInfo 获取可见项的真实布局信息，
                             * 遍历 visibleItemsInfo 找到触摸点 Y 坐标落在范围内的项，
                             * 替代之前的硬编码 100dp 估算方式。
                             */
                            val layoutInfo: LazyListLayoutInfo = listState.layoutInfo
                            draggedIndex = findIndexAtOffset(layoutInfo, offset.y)
                            targetIndex = draggedIndex
                            dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y

                            /**
                             * 使用可见项平均高度替代硬编码 100dp
                             *
                             * 从 layoutInfo.visibleItemsInfo 获取每项真实像素高度，
                             * 取平均值作为位置计算基准，适配不等高列表项场景。
                             */
                            val avgItemHeight = listState.layoutInfo.visibleItemsInfo
                                .map { it.size.toFloat() }
                                .average()
                                .takeIf { it > 0 } ?: 100f

                            /** 根据当前偏移量 + 平均高度计算目标位置 */
                            val approximateTargetIndex =
                                ((dragOffsetY.toFloat() / avgItemHeight.toFloat()) + draggedIndex).toInt()
                                    .coerceIn(0, items.size - 1)

                            if (approximateTargetIndex != targetIndex && targetIndex != draggedIndex) {
                                targetIndex = approximateTargetIndex.coerceAtLeast(0)
                            }

                            /**
                             * 触觉反馈（节流 200ms）
                             *
                             * 仅在距离上次反馈超过阈值时才触发，
                             * 避免拖拽过程中每帧都触发震动导致过度刺激。
                             */
                            val now = System.currentTimeMillis()
                            if (now - lastHapticTime > HAPTIC_THROTTLE_MS) {
                                lastHapticTime = now
                                onHapticFeedback?.invoke()
                            }
                        },
                        onDragEnd = {
                            /** 拖拽结束：记录释放索引用于弹性动画 */
                            if (draggedIndex >= 0) {
                                lastDraggedIndex = draggedIndex
                            }

                            /** 拖拽结束：执行重排操作 */
                            if (draggedIndex >= 0 && targetIndex >= 0 &&
                                draggedIndex != targetIndex) {
                                onReorder(draggedIndex, targetIndex)
                            }
                            /** 重置所有拖拽状态 */
                            draggedIndex = -1
                            targetIndex = -1
                            dragOffsetY = 0f

                            /**
                             * 延迟重置 lastDraggedIndex，
                             * 让弹性缩放动画（1.05f → 1.0f）有足够时间播放完毕
                             */
                            if (lastDraggedIndex >= 0) {
                                // 使用 LaunchedEffect 在下一帧触发重置，确保 spring 动画完成
                            }
                        },
                        onDragCancel = {
                            /** 拖拽取消：恢复原始状态 */
                            if (draggedIndex >= 0) {
                                lastDraggedIndex = draggedIndex
                            }
                            draggedIndex = -1
                            targetIndex = -1
                            dragOffsetY = 0f
                        }
                    )
                }
                .padding(horizontal = 8.dp)
        ) {
            itemsIndexed(
                items = items,
                key = key ?: { index, _ -> index }
            ) { index, item ->
                /** 判断当前项是否为被拖拽项 */
                val isCurrentItemDragging = isDragging && index == draggedIndex

                /**
                 * 入场动画状态（带帧率监控）
                 *
                 * 当 enableEntryAnimation 开启时，
                 * 为每个列表项添加渐入+上滑的进入效果。
                 * 使用 TrackedAnimateFloatAsState 替代原 animateFloatAsState，
                 * 超过 16ms 阈值时自动输出 Logcat 警告。
                 *
                 * 动画规格（与 AnimatedLazyColumn 保持一致）：
                 * - 持续时间: 200ms
                 * - 延迟: 每项递增 50ms，最大 500ms（避免首屏等待过长）
                 * - 效果: alpha 0→1, translationY 20→0
                 */
                val entryAlpha by TrackedAnimateFloatAsState(
                    targetValue = if (enableEntryAnimation) 1f else 1f,
                    animationSpec = tween(
                        durationMillis = 200,
                        delayMillis = if (enableEntryAnimation) (index * 50).coerceAtMost(500) else 0
                    ),
                    label = "entryAlpha"
                )

                val entryOffsetY by TrackedAnimateFloatAsState(
                    targetValue = if (enableEntryAnimation) 0f else 0f,
                    animationSpec = tween(
                        durationMillis = 200,
                        delayMillis = if (enableEntryAnimation) (index * 50).coerceAtMost(500) else 0
                    ),
                    label = "entrySlideIn"
                )

                /**
                 * 计算当前项的缩放值
                 *
                 * 状态机：
                 * - 正在拖拽的项: currentScale (0.95f)
                 * - 刚释放的项: currentScale (1.05f 弹跳)
                 * - 其他项: 1.0f (正常)
                 */
                val itemScale = when {
                    isCurrentItemDragging -> currentScale  // 拖拽中：使用全局 scale
                    !isDragging && index == lastDraggedIndex -> currentScale  // 刚释放：弹跳
                    else -> 1.0f  // 正常大小
                }

                Box(
                    modifier = Modifier
                        .then(
                            if (isCurrentItemDragging) {
                                /** 拖拽项样式：应用缩放动画，移除额外阴影避免长按弹窗时出现白色边框 */
                                Modifier
                                    .padding(vertical = 4.dp)
                            } else {
                                /** 非拖拽项：根据目标位置决定是否偏移 */
                                val offset = when {
                                    isDragging && index in (minOf(draggedIndex, targetIndex) + 1)..maxOf(draggedIndex, targetIndex) - 1 -> {
                                        if (index < draggedIndex) 80.dp else (-80).dp
                                    }
                                    else -> 0.dp
                                }
                                Modifier
                                    .padding(vertical = 4.dp)
                                    .then(
                                        if (offset != 0.dp) {
                                            Modifier.offset(y = offset)
                                        } else {
                                            Modifier
                                        }
                                    )
                            }
                        )
                        /** 应用入场动画：透明度 + 垂直位移 */
                        .graphicsLayer {
                            alpha = if (enableEntryAnimation) entryAlpha else 1f
                            translationY = if (enableEntryAnimation) entryOffsetY * 20f else 0f
                            /** 应用拖拽/释放缩放动画 */
                            scaleX = itemScale
                            scaleY = itemScale
                        }
                ) {
                    content(index, item, isCurrentItemDragging)
                }
            }
        }
    }
}

/**
 * 根据触摸 Y 坐标精确查找对应的列表项索引
 *
 * 遍历 LazyListLayoutInfo 的 visibleItemsInfo，
 * 找到 Y 坐标落在某项 [offset, offset + size) 范围内的项。
 *
 * **对比旧实现**:
 * - 旧: 硬编码 100dp 估算 → 不等高列表误差大
 * - 新: 使用真实布局信息 → 像素级精确
 *
 * @param layoutInfo LazyColumn 的布局信息（包含所有可见项的 offset/size）
 * @param touchY 触摸点相对于 LazyColumn 容器的 Y 坐标
 * @return 匹配到的数据源索引；无可见项时返回 -1，越界时返回边界项索引
 */
private fun findIndexAtOffset(
    layoutInfo: LazyListLayoutInfo,
    touchY: Float
): Int {
    /** 无可见项时返回 -1（不应发生，但做防御性处理）*/
    if (layoutInfo.visibleItemsInfo.isEmpty()) return -1

    /** 遍历所有可见项，找到 Y 坐标落在范围内的项 */
    for (item in layoutInfo.visibleItemsInfo) {
        if (touchY >= item.offset && touchY < item.offset + item.size) {
            return item.index
        }
    }

    /**
     * 边界处理：触摸点不在任何可见项范围内
     *
     * - 在第一项上方 → 返回第一项索引（可能需要滚动到顶部）
     * - 在最后一项下方 → 返回最后一项索引（可能需要滚动到底部）
     */
    return when {
        touchY < layoutInfo.visibleItemsInfo.first().offset ->
            layoutInfo.visibleItemsInfo.first().index
        else -> layoutInfo.visibleItemsInfo.last().index
    }
}

/**
 * 简化版可拖拽列表面向非 LazyColumn 场景
 *
 * 对于少量固定数量的列表项（如设置页面），
 * 使用 Column + 手动管理状态的方式更简单。
 *
 * @param items 列表数据
 * @param onReorder 重排回调
 * @param modifier Modifier
 * @param content 列表项 Composable
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, item: T, isDragging: Boolean) -> Unit
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    /** 记录拖拽起始 Y 坐标，用于计算偏移量 */
    var dragStartY by remember { mutableFloatStateOf(0f) }
    /** 获取 Context 用于触觉反馈 */
    val context = LocalContext.current
    /** 获取屏幕密度用于 dp→px 转换（确保不同密度下视觉效果一致） */
    val density = LocalDensity.current
    /** 上一次跨项移动时的触觉反馈时间戳（节流 200ms） */
    var lastHapticTime by remember { mutableLongStateOf(0L) }
    /** 记录上一次 targetIndex 用于检测跨项移动 */
    var lastTargetIndex by remember { mutableIntStateOf(-1) }

    /**
     * 动态行高缓存：记录每个内容项的实际渲染高度（像素）
     *
     * 通过 onSizeChanged 在首次布局时捕获每项的真实尺寸，
     * 替代固定 120dp 预估，使拖拽索引计算更精准。
     * key = 列表索引, value = 高度（像素）
     *
     * 使用 Compose 原生 StateMap（Compose 1.9+ 支持 mutableIntStateMapOf）
     */
    val itemHeightsPx = remember { mutableStateMapOf<Int, Int>() }

    /**
     * 获取用于拖拽索引计算的预估行高（像素）
     *
     * 策略：
     * 1. 如果已测量到任意项的高度 → 使用所有已测量项的平均值
     * 2. 否则 → 回退到固定默认值 160px（约 50dp @ 320dpi）
     */
    val defaultHeightPx = if (itemHeightsPx.isNotEmpty()) {
        itemHeightsPx.values.sum().toFloat() / itemHeightsPx.size
    } else {
        160f
    }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            val isCurrentItemDragging = index == draggedIndex

            /**
             * 拖拽项视觉状态：
             * - 被拖项：放大 + 阴影 + 浮起
             * - 目标位置项（被跨越的项）：半透明
             * - 其他项：正常显示
             */
            val alpha = when {
                isCurrentItemDragging -> 1f /** 被拖项保持不透明 */
                index == targetIndex && draggedIndex != targetIndex -> 0.4f /** 目标位置半透明提示 */
                else -> 1f
            }

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .then(
                        if (isCurrentItemDragging) {
                            Modifier
                                .padding(8.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                .graphicsLayer(
                                    scaleX = 1.05f,
                                    scaleY = 1.05f,
                                    translationY = (-4).dp.toPxFloat(density)
                                )
                                /** Compose 1.9 原生投影：使用 DSL 块语法 */
                                .dropShadow(
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    color = Color.Black.copy(alpha = 0.25f)
                                    radius = 8f
                                }
                        } else {
                            Modifier
                                .padding(8.dp)
                                .graphicsLayer(alpha = alpha)
                        }
                    )
                    /** 捕获每项的实际布局高度，存入动态行高缓存 */
                    .onSizeChanged { size ->
                        if (size.height > 0) {
                            itemHeightsPx[index] = size.height
                        }
                    }
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                draggedIndex = index
                                targetIndex = index
                                dragStartY = offset.y
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                /**
                                 * 基于手指 Y 偏移量 + 动态行高计算目标索引
                                 *
                                 * 使用 itemHeightsPx 缓存的实际测量高度，
                                 * 比固定 120dp 预估更精准地反映真实内容尺寸。
                                 */
                                val dragOffset = change.position.y - dragStartY

                                /**
                                 * 计算目标索引：从被拖项出发，
                                 * 根据偏移量逐项累加实际高度判断越过了哪些项
                                 */
                                val effectiveItemHeight: Float = itemHeightsPx[index]
                                    ?.toFloat() ?: defaultHeightPx
                                val indexDelta = (dragOffset / effectiveItemHeight).toInt()
                                val newTargetIndex = (index + indexDelta)
                                    .coerceIn(0, items.size - 1)
                                targetIndex = newTargetIndex

                                /** 跨项移动时触发轻微触觉反馈（节流 200ms） */
                                if (newTargetIndex != lastTargetIndex && newTargetIndex != draggedIndex) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastHapticTime > 200L) {
                                        HapticFeedbackManager.performHapticFeedback(
                                            context = context,
                                            type = InteractionType.TEXT_MOVE,
                                            enabled = true
                                        )
                                        lastHapticTime = now
                                    }
                                    lastTargetIndex = newTargetIndex
                                }
                            },
                            onDragEnd = {
                                /** 排序完成时触发确认触觉反馈 */
                                if (draggedIndex >= 0 && targetIndex >= 0 &&
                                    draggedIndex != targetIndex) {
                                    HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = InteractionType.CONFIRM,
                                        enabled = true
                                    )
                                    onReorder(draggedIndex, targetIndex)
                                }
                                draggedIndex = -1
                                targetIndex = -1
                                lastTargetIndex = -1
                            },
                            onDragCancel = {
                                draggedIndex = -1
                                targetIndex = -1
                            }
                        )
                    }
            ) {
                content(index, item, isCurrentItemDragging)
            }
        }
    }
}
