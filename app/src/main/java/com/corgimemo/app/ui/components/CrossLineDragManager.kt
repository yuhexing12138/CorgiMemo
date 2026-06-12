package com.corgimemo.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.model.TodoLine
import kotlin.math.roundToInt

/**
 * 拖拽模式枚举
 */
enum class DragMode {
    NONE,
    INLINE_SORT,
    CROSS_LINE
}

/**
 * 拖拽状态数据类
 */
data class DragState(
    val isDragging: Boolean = false,
    val sourceLineIndex: Int = -1,
    val sourceImageIndex: Int = -1,
    val currentTargetLine: Int = -1,
    val currentTargetImage: Int? = null,
    val dragOffset: Offset = Offset.Zero,
    val dragMode: DragMode = DragMode.NONE
)

/**
 * 拖拽结果数据类
 *
 * 🔑 关键修复：包含 sourceLineIndex 和 sourceImageIndex
 *
 * 之前这些值存储在 CrossLineDragManager.state 中，
 * 但 endDrag() 调用 reset() 后 state 被重置为默认值（-1），
 * 导致 applyDragResult() 读取到无效索引引发 IndexOutOfBoundsException。
 *
 * 现在将源位置信息直接嵌入 DragResult，确保 applyDragResult()
 * 不依赖已被重置的 state。
 */
data class DragResult(
    val targetLineIndex: Int = -1,
    val targetImageIndex: Int? = null,
    val isCrossLineMove: Boolean = false,
    val isSuccess: Boolean = true,
    /** 🆕 源行索引（修复崩溃：不再依赖 reset 后的 state）*/
    val sourceLineIndex: Int = -1,
    /** 🆕 源图片位置索引（修复崩溃：不再依赖 reset 后的 state）*/
    val sourceImageIndex: Int = -1
)

/**
 * 跨行拖拽状态管理器
 *
 * 协调管理附件拖拽的全局状态。
 */
class CrossLineDragManager {

    /** 当前拖拽状态（Compose 响应式 State） */
    var state by mutableStateOf(DragState())
        private set

    /** Y轴偏移阈值（单位：dp） */
    val VERTICAL_THRESHOLD_DP = 30f

    /**
     * 行内排序时每张图片的估算宽度（单位：dp）
     *
     * 值应与 DraggableImageAttachment 中的实际渲染尺寸一致：
     * - 图片宽度: 100dp (attachmentWidth)
     * - 间距: 8dp (CheckboxEditText 中 Arrangement.spacedBy(8.dp))
     * - 总计: ~108dp
     *
     * 使用较小的阈值(72dp ≅ 2/3 张图宽)使排序更灵敏，
     * 用户不需要拖动整张图片的距离就能触发交换。
     */
    val INLINE_ITEM_WIDTH_DP = 72f

    /**
     * 开始拖拽操作
     */
    fun startDrag(lineIndex: Int, imageIndex: Int) {
        state = DragState(
            isDragging = true,
            sourceLineIndex = lineIndex,
            sourceImageIndex = imageIndex,
            currentTargetLine = lineIndex,
            currentTargetImage = imageIndex,
            dragOffset = Offset.Zero,
            dragMode = DragMode.INLINE_SORT
        )
    }

    /**
     * 更新拖拽过程中的状态
     */
    fun updateDrag(
        currentOffset: Offset,
        density: Float,
        rowBounds: List<androidx.compose.ui.geometry.Rect>? = null,
        fingerY: Float? = null
    ) {
        if (!state.isDragging) return

        val thresholdPx = VERTICAL_THRESHOLD_DP * density
        val newMode = detectDragMode(currentOffset.y, thresholdPx)

        var targetLine = state.currentTargetLine
        var targetImage = state.currentTargetImage

        if (newMode == DragMode.CROSS_LINE && rowBounds != null && fingerY != null) {
            /** 跨行模式：检测悬停的目标行 */
            targetLine = detectTargetRow(fingerY, rowBounds) ?: state.sourceLineIndex
            targetImage = null
        } else if (newMode == DragMode.INLINE_SORT) {
            /** 行内排序模式：基于 X 轴偏移量计算目标位置 */

            targetLine = state.sourceLineIndex

            /**
             * 🆕 改进的行内排序目标位置计算
             *
             * 算法改进：
             * 1. 使用较小的单位宽度（72dp vs 之前的108dp），使排序更灵敏
             * 2. 使用 roundToInt() 替代 toInt()，四舍五入更准确
             * 3. 结果限制在合理范围内 [0, sourceIndex + totalCount]
             *
             * 示例（3张图片，拖动第1张[索引1]向右100dp）：
             *   之前：offsetUnits = 100/108.toInt() = 0 → 目标不变 ❌
             *   现在：offsetUnits = 100/72.roundToInt() = 1 → 目标=2 ✅
             */
            val xOffsetDp = currentOffset.x / density
            val offsetUnits = (xOffsetDp / INLINE_ITEM_WIDTH_DP).roundToInt()

            /** 允许目标位置在 [0, sourceIndex + 剩余数量] 范围内 */
            val maxTarget = state.sourceImageIndex + 3  // 最多允许跨越3个位置
            targetImage = (state.sourceImageIndex + offsetUnits).coerceIn(0, maxTarget)
        }

        state = state.copy(
            dragOffset = currentOffset,
            dragMode = newMode,
            currentTargetLine = targetLine,
            currentTargetImage = targetImage
        )
    }

    /**
     * 结束拖拽操作
     *
     * 🔑 关键修复：将 source 信息保存到 DragResult 中
     *
     * 之前的问题：
     *   endDrag() → 计算 result → reset() 重置 state → 返回 result
     *   然后 applyDragResult() 读取 state.sourceLineIndex → 已是 -1！→ 💥 崩溃
     *
     * 现在的流程：
     *   endDrag() → 将 state.sourceLineIndex/sourceImageIndex 存入 result → reset() → 返回 result
     *   applyDragResult() 从 result 读取源信息 ✅ 安全
     */
    fun endDrag(): DragResult {
        if (!state.isDragging) {
            return DragResult(isSuccess = false)
        }

        /** 🆕 在 reset 之前保存源位置信息 */
        val savedSourceLine = state.sourceLineIndex
        val savedSourceImage = state.sourceImageIndex

        val result = when {
            /** 无效操作：未发生实际位移 */
            savedSourceLine == state.currentTargetLine &&
                    savedSourceImage == state.currentTargetImage -> {
                DragResult(
                    targetLineIndex = savedSourceLine,
                    targetImageIndex = savedSourceImage,
                    isCrossLineMove = false,
                    isSuccess = false,
                    sourceLineIndex = savedSourceLine,
                    sourceImageIndex = savedSourceImage
                )
            }
            /** 跨行移动：目标行与源行不同 */
            savedSourceLine != state.currentTargetLine -> {
                DragResult(
                    targetLineIndex = state.currentTargetLine,
                    targetImageIndex = state.currentTargetImage,
                    isCrossLineMove = true,
                    isSuccess = true,
                    sourceLineIndex = savedSourceLine,
                    sourceImageIndex = savedSourceImage
                )
            }
            /** 同行内排序：目标位置与源位置不同 */
            else -> {
                DragResult(
                    targetLineIndex = state.currentTargetLine,
                    targetImageIndex = state.currentTargetImage,
                    isCrossLineMove = false,
                    isSuccess = true,
                    sourceLineIndex = savedSourceLine,
                    sourceImageIndex = savedSourceImage
                )
            }
        }

        /** 重置所有拖拽状态（在返回 result 之后） */
        reset()

        return result
    }

    fun cancelDrag() {
        reset()
    }

    private fun reset() {
        state = DragState()
    }

    private fun detectDragMode(verticalOffsetPx: Float, thresholdPx: Float): DragMode {
        return if (kotlin.math.abs(verticalOffsetPx) >= thresholdPx) {
            DragMode.CROSS_LINE
        } else {
            DragMode.INLINE_SORT
        }
    }

    /**
     * 检测手指当前悬停的目标行
     */
    private fun detectTargetRow(
        fingerY: Float,
        rowBounds: List<androidx.compose.ui.geometry.Rect>
    ): Int? {
        if (rowBounds.isEmpty()) return null

        for ((index, rect) in rowBounds.withIndex()) {
            if (fingerY in rect.top..rect.bottom) {
                return index
            }
        }

        return when {
            fingerY < rowBounds.first().top -> 0
            fingerY > rowBounds.last().bottom -> rowBounds.lastIndex
            else -> null
        }
    }

    /**
     * 执行拖拽结果对应的列表更新操作
     *
     * 🔑 关键修复：从 result 读取源信息（而非已重置的 state）
     *
     * 之前：updatedLines[state.sourceLineIndex] → state 已被 reset 为 -1 → 💥
     * 现在：updatedLines[result.sourceLineIndex] → 从 DragResult 读取 ✅
     */
    fun applyDragResult(
        lines: List<TodoLine>,
        result: DragResult,
        imagePath: String
    ): List<TodoLine> {
        if (!result.isSuccess || result.targetLineIndex !in lines.indices) {
            return lines
        }

        /** 🆕 从 result 读取源位置（而非已重置的 state）*/
        val srcLineIdx = result.sourceLineIndex
        val srcImgIdx = result.sourceImageIndex

        val updatedLines = lines.toMutableList()

        if (result.isCrossLineMove) {
            /** ===== 跨行移动逻辑 ===== */

            /** 1. 从源行删除图片 */
            if (srcLineIdx in updatedLines.indices) {
                val sourceLine = updatedLines[srcLineIdx]
                updatedLines[srcLineIdx] = sourceLine.copy(
                    imagePaths = sourceLine.imagePaths.filter { it != imagePath }
                )
            }

            /** 2. 插入到目标行 */
            if (result.targetLineIndex in updatedLines.indices) {
                val targetLine = updatedLines[result.targetLineIndex]
                val newImagePaths = if (result.targetImageIndex != null) {
                    val mutableList = targetLine.imagePaths.toMutableList()
                    val insertIdx = result.targetImageIndex.coerceIn(0, mutableList.size)
                    mutableList.add(insertIdx, imagePath)
                    mutableList.toList()
                } else {
                    targetLine.imagePaths + imagePath
                }
                updatedLines[result.targetLineIndex] = targetLine.copy(
                    imagePaths = newImagePaths
                )
            }

        } else {
            /** ===== 同行内排序逻辑 ===== */

            val lineIndex = result.targetLineIndex
            if (lineIndex !in updatedLines.indices) return lines

            val currentLine = updatedLines[lineIndex]
            val fromIndex = srcImgIdx
            val toIndex = result.targetImageIndex ?: fromIndex

            /** 重新排列 imagePaths 列表 */
            val imageSize = currentLine.imagePaths.size
            if (fromIndex in currentLine.imagePaths.indices &&
                toIndex in 0..imageSize) {
                val reorderedList = currentLine.imagePaths.toMutableList()
                val movedItem = reorderedList.removeAt(fromIndex)

                /** removeAt 后列表长度减1，需要调整插入位置 */
                val adjustedToIndex = toIndex.coerceIn(0, reorderedList.size)
                reorderedList.add(adjustedToIndex, movedItem)

                updatedLines[lineIndex] = currentLine.copy(
                    imagePaths = reorderedList
                )
            }
        }

        return updatedLines
    }
}
