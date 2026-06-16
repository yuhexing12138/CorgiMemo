package com.corgimemo.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.corgimemo.app.ui.model.TodoLine
import kotlin.math.roundToInt

/**
 * 拖拽模式枚举
 */
enum class DragMode {
    NONE,
    /** 同行内排序（水平拖动为主） */
    INLINE_SORT
}

/**
 * 同行拖拽放置类型
 *
 * 区分"交换"和"移动/插入"两种操作，提供不同的视觉反馈：
 * - SWAP：手指悬停在目标图片上方 → 该图显示虚线框
 * - INSERT_BEFORE：手指在两张图片之间 → 显示闪烁光标
 * - INSERT_AFTER：手指在最后一张图片之后 → 显示末尾闪烁光标
 */
enum class InlineDropType {
    NONE,           // 无目标
    SWAP,           // 交换：悬停在某个图片上方
    INSERT_BEFORE,  // 移动/插入：在某张图片之前（光标在该图左侧）
    INSERT_AFTER    // 移动/插入：在最后一张图片之后（光标在末尾）
}

/**
 * 拖拽状态数据类
 */
data class DragState(
    val isDragging: Boolean = false,
    val sourceLineIndex: Int = -1,
    val sourceImageIndex: Int = -1,
    /** 当前目标图片索引（SWAP=被悬停的图索引, INSERT_BEFORE=插入位置后的图索引, INSERT_AFTER=null） */
    val currentTargetImage: Int? = null,
    val dragOffset: Offset = Offset.Zero,
    val dragMode: DragMode = DragMode.NONE,
    /** 同行模式下的放置类型（交换/移动） */
    val inlineDropType: InlineDropType = InlineDropType.NONE
)

/**
 * 拖拽结果数据类
 *
 * 包含 sourceLineIndex 和 sourceImageIndex，
 * 确保 applyDragResult() 不依赖已被重置的 state。
 */
data class DragResult(
    val targetLineIndex: Int = -1,
    val targetImageIndex: Int? = null,
    val isSuccess: Boolean = true,
    /** 源行索引（修复崩溃：不再依赖 reset 后的 state）*/
    val sourceLineIndex: Int = -1,
    /** 源图片位置索引（修复崩溃：不再依赖 reset 后的 state）*/
    val sourceImageIndex: Int = -1,
    /** 同行放置类型（用于区分交换 vs 插入，决定 applyDragResult 的操作逻辑）*/
    val inlineDropType: InlineDropType = InlineDropType.NONE
)

/**
 * 同行拖拽状态管理器
 *
 * 协调管理附件拖拽的全局状态，仅支持同行内操作。
 */
class CrossLineDragManager {

    /** 当前拖拽状态（Compose 响应式 State） */
    var state by mutableStateOf(DragState())
        private set

    /**
     * 图片附件实际宽度（单位：dp）
     *
     * 必须与 DraggableImageAttachment.attachmentWidth 保持一致（100dp）。
     */
    val ATTACHMENT_WIDTH_DP = 100f

    /**
     * 图片之间的间距（单位：dp）
     *
     * 必须与 CheckboxEditText 中 Arrangement.spacedBy() 的值一致（8dp）。
     */
    val IMAGE_SPACING_DP = 8f

    /**
     * 开始拖拽操作
     *
     * @param lineIndex 源图片所在行的索引
     * @param imageIndex 源图片在该行中的位置索引
     */
    fun startDrag(lineIndex: Int, imageIndex: Int, imageHeightPx: Float = 0f) {
        state = DragState(
            isDragging = true,
            sourceLineIndex = lineIndex,
            sourceImageIndex = imageIndex,
            currentTargetImage = imageIndex,
            dragOffset = Offset.Zero,
            dragMode = DragMode.INLINE_SORT
        )
    }

    /**
     * 更新拖拽过程中的状态
     *
     * @param currentOffset 拖拽偏移量（像素）
     * @param density 屏幕密度
     * @param imageCount 源行中的图片数量
     * @param scrollOffsetPx 🆕 图片行水平滚动偏移量（像素），用于补偿 X 轴计算
     */
    fun updateDrag(
        currentOffset: Offset,
        density: Float,
        imageCount: Int = 0,
        scrollOffsetPx: Float = 0f
    ) {
        if (!state.isDragging) return

        var targetImage: Int? = null
        var inlineDropType = InlineDropType.NONE

        /** 行内排序模式：基于拖动距离计算目标 */
        val sourceLine = state.sourceLineIndex

        /**
         * 基于实际拖动距离的行内排序目标检测
         *
         * 🆕 滚动偏移补偿：
         * 当图片行通过第二指滑动或边缘自动滚动时，scrollOffsetPx 记录了滚动量。
         * 滚动导致图片视觉位置偏移，需要在 X 轴计算中补偿：
         *   xOffsetDp = (currentOffset.x + scrollOffsetPx) / density
         *   ^^^^^^^^^   ^^^^^^^^^^^^^^^^^^^^    ^^^^^^^^^^^^^^^^
         *   手指拖动量   拖动偏移                滚动补偿
         *
         * 计算方式：
         * 1. 将像素偏移转为 dp（xOffsetDp = 手指实际移动距离 + 滚动补偿）
         * 2. 按单元宽度（图片宽 + 间距 = 108dp）计算跨越的目标数量
         * 3. 使用 roundToInt() 四舍五入，约在拖过半个单元时触发切换
         */
        val xOffsetDp = (currentOffset.x + scrollOffsetPx) / density

        /** 一个完整单元的宽度 = 图片宽度 + 间距 */
        val unitWidthDp = ATTACHMENT_WIDTH_DP + IMAGE_SPACING_DP  // 108dp

        /**
         * 基于实际拖动距离计算跨越的目标位置数
         *
         * roundToInt() 确保约拖过半个单元（~54dp）时触发切换。
         */
        val offsetUnits = (xOffsetDp / unitWidthDp).roundToInt()

        /** 允许目标位置在 [0, sourceIndex + 剩余数量] 范围内 */
        val maxTarget = state.sourceImageIndex + 3
        val rawTarget = (state.sourceImageIndex + offsetUnits).coerceIn(0, maxTarget)

        /**
         * 🆕 区分交换 vs 移动：基于 offsetUnits 的小数部分
         *
         * 当 offsetUnits 为整数时（四舍五入后），手指正对某张图片 → SWAP
         * 当手指处于两张图片之间的间隙时，offsetUnits 的小数部分接近 0.5
         *
         * 但当前算法使用 roundToInt()，无法区分间隙。
         * 改用精确的 X 坐标判定：计算手指在行内的精确位置，
         * 判断是落在图片区域还是间隙区域。
         */
        val exactOffsetUnits = xOffsetDp / unitWidthDp
        val fractionalPart = exactOffsetUnits - kotlin.math.floor(exactOffsetUnits)

        /**
         * 判定逻辑：
         * - 每个单元 108dp 中，前 100dp 是图片区域，后 8dp 是间隙
         * - fractionalPart ∈ [0, 100/108) ≈ [0, 0.926) → 图片区域 → SWAP
         * - fractionalPart ∈ [100/108, 1) ≈ [0.926, 1) → 间隙区域 → INSERT
         *
         * 同时需要考虑方向：
         * - 向右拖（offsetUnits > 0）：间隙在目标图之前
         * - 向左拖（offsetUnits < 0）：间隙在目标图之后
         * - offsetUnits == 0：未拖动或拖动很小
         */
        val imageFraction = ATTACHMENT_WIDTH_DP / unitWidthDp  // 100/108 ≈ 0.926

        if (offsetUnits == 0) {
            /** 拖动距离不足以跨越到其他图片 */
            targetImage = state.sourceImageIndex
            inlineDropType = InlineDropType.NONE
        } else if (fractionalPart < imageFraction) {
            /** 手指在图片区域内 → 交换模式 */
            targetImage = rawTarget
            /** 源图片自身不触发 SWAP */
            inlineDropType = if (rawTarget == state.sourceImageIndex) {
                InlineDropType.NONE
            } else {
                InlineDropType.SWAP
            }
        } else {
            /** 手指在间隙区域 → 移动/插入模式 */
            if (offsetUnits > 0) {
                /** 向右拖：光标在 rawTarget 图片之前（即 rawTarget-1 和 rawTarget 之间） */
                if (rawTarget >= imageCount) {
                    /** 超出最后一张图 → INSERT_AFTER */
                    inlineDropType = InlineDropType.INSERT_AFTER
                    targetImage = null
                } else {
                    inlineDropType = InlineDropType.INSERT_BEFORE
                    targetImage = rawTarget
                }
            } else {
                /** 向左拖：光标在 rawTarget 图片之后（即 rawTarget 和 rawTarget+1 之间） */
                if (rawTarget >= imageCount - 1) {
                    /** 在最后一张图右侧 → INSERT_AFTER */
                    inlineDropType = InlineDropType.INSERT_AFTER
                    targetImage = null
                } else {
                    inlineDropType = InlineDropType.INSERT_BEFORE
                    targetImage = rawTarget + 1
                }
            }
        }

        state = state.copy(
            dragOffset = currentOffset,
            dragMode = DragMode.INLINE_SORT,
            currentTargetImage = targetImage,
            inlineDropType = inlineDropType
        )
    }

    /**
     * 结束拖拽操作
     *
     * 将 source 信息保存到 DragResult 中，确保 applyDragResult() 安全读取。
     */
    fun endDrag(): DragResult {
        if (!state.isDragging) {
            return DragResult(isSuccess = false)
        }

        /** 在 reset 之前保存源位置信息 */
        val savedSourceLine = state.sourceLineIndex
        val savedSourceImage = state.sourceImageIndex
        val savedInlineDropType = state.inlineDropType

        val result = when {
            /** 无效操作：未发生实际位移或仍在源位置 */
            savedInlineDropType == InlineDropType.NONE -> {
                DragResult(
                    targetLineIndex = savedSourceLine,
                    targetImageIndex = savedSourceImage,
                    isSuccess = false,
                    sourceLineIndex = savedSourceLine,
                    sourceImageIndex = savedSourceImage,
                    inlineDropType = savedInlineDropType
                )
            }
            /** 有效操作：交换或移动 */
            else -> {
                DragResult(
                    targetLineIndex = savedSourceLine,
                    targetImageIndex = state.currentTargetImage,
                    isSuccess = true,
                    sourceLineIndex = savedSourceLine,
                    sourceImageIndex = savedSourceImage,
                    inlineDropType = savedInlineDropType
                )
            }
        }

        /** 重置所有拖拽状态 */
        reset()

        return result
    }

    /** 取消拖拽 */
    fun cancelDrag() {
        reset()
    }

    /** 重置状态为默认值 */
    private fun reset() {
        state = DragState()
    }

    /**
     * 执行拖拽结果对应的列表更新操作
     *
     * 从 result 读取源信息（而非已重置的 state）
     */
    fun applyDragResult(
        lines: List<TodoLine>,
        result: DragResult,
        imagePath: String
    ): List<TodoLine> {
        if (!result.isSuccess || result.targetLineIndex !in lines.indices) {
            return lines
        }

        /** 从 result 读取源位置（而非已重置的 state）*/
        val srcLineIdx = result.sourceLineIndex
        val srcImgIdx = result.sourceImageIndex

        val updatedLines = lines.toMutableList()
        val lineIndex = result.targetLineIndex

        if (lineIndex !in updatedLines.indices) return lines

        val currentLine = updatedLines[lineIndex]

        when (result.inlineDropType) {
            InlineDropType.SWAP -> {
                /** ===== 同行交换逻辑（保持不变）===== */
                val fromIndex = srcImgIdx
                val toIndex = result.targetImageIndex ?: fromIndex

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
            InlineDropType.INSERT_BEFORE -> {
                /** ===== 同行移动逻辑（在目标图之前插入）===== */
                val fromIndex = srcImgIdx
                val targetIndex = result.targetImageIndex ?: fromIndex

                if (fromIndex in currentLine.imagePaths.indices &&
                    targetIndex in 0..currentLine.imagePaths.size) {
                    val reorderedList = currentLine.imagePaths.toMutableList()
                    val movedItem = reorderedList.removeAt(fromIndex)

                    /** 移除后索引偏移修正：如果目标在源之后，需减1 */
                    val insertIndex = if (targetIndex > fromIndex) {
                        targetIndex - 1
                    } else {
                        targetIndex
                    }.coerceIn(0, reorderedList.size)

                    reorderedList.add(insertIndex, movedItem)

                    updatedLines[lineIndex] = currentLine.copy(
                        imagePaths = reorderedList
                    )
                }
            }
            InlineDropType.INSERT_AFTER -> {
                /** ===== 同行移动逻辑（在末尾插入）===== */
                val fromIndex = srcImgIdx

                if (fromIndex in currentLine.imagePaths.indices) {
                    val reorderedList = currentLine.imagePaths.toMutableList()
                    val movedItem = reorderedList.removeAt(fromIndex)

                    /** 移除后直接追加到末尾 */
                    reorderedList.add(movedItem)

                    updatedLines[lineIndex] = currentLine.copy(
                        imagePaths = reorderedList
                    )
                }
            }
            InlineDropType.NONE -> {
                /** 无操作 */
            }
        }

        return updatedLines
    }
}
