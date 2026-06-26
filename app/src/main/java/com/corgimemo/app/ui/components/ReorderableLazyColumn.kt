package com.corgimemo.app.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.util.toPxFloat

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
