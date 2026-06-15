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
 * 跨行模式下的放置类型（v7 新增）
 *
 * 用于区分"交换"和"移动/插入"两种操作，提供不同的视觉反馈：
 * - SWAP_TO_IMAGE：手指悬停在目标图片上方 → 该图显示虚线框
 * - INSERT_BEFORE：手指在两张图片之间 → 显示插入竖线
 * - INSERT_AFTER：手指在最后一张图片之后 → 显示末尾插入竖线
 */
enum class DropType {
    NONE,           // 未进入跨行模式或无有效目标
    SWAP_TO_IMAGE,  // 交换：悬停在某个图片上方
    INSERT_BEFORE,  // 移动/插入：在某张图片之前
    INSERT_AFTER    // 移动/插入：在最后一张图片之后
}

/**
 * 拖拽状态数据类
 */
data class DragState(
    val isDragging: Boolean = false,
    val sourceLineIndex: Int = -1,
    val sourceImageIndex: Int = -1,
    val currentTargetLine: Int = -1,
    /** 当前目标图片索引（SWAP=被悬停的图索引, INSERT=插入位置后的图索引） */
    val currentTargetImage: Int? = null,
    val dragOffset: Offset = Offset.Zero,
    val dragMode: DragMode = DragMode.NONE,
    /** 🆕 v7：跨行模式下的放置类型（交换/插入） */
    val dropType: DropType = DropType.NONE,
    /** 🆕 v7.5：源图片实际高度（像素），用于跨行拖拽时 Popup 边缘的 Y 轴精确计算
     *  0 表示未传入（回退使用 ATTACHMENT_HEIGHT_DP * density） */
    val sourceImageHeightPx: Float = 0f
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
    val sourceImageIndex: Int = -1,
    /** 🆕 v7.3：跨行放置类型（用于区分交换 vs 插入，决定 applyDragResult 的操作逻辑）*/
    val dropType: DropType = DropType.NONE
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
     * 图片附件实际宽度（单位：dp）
     *
     * 必须与 DraggableImageAttachment.attachmentWidth 保持一致（100dp）。
     */
    val ATTACHMENT_WIDTH_DP = 100f
    /** 🆕 v7.5：图片附件高度（用于跨行拖拽时 Popup 边缘的 Y 轴计算）*/
    val ATTACHMENT_HEIGHT_DP = 80f

    /**
     * 图片之间的间距（单位：dp）
     *
     * 必须与 CheckboxEditText 中 Arrangement.spacedBy() 的值一致（8dp）。
     */
    val IMAGE_SPACING_DP = 8f

    /**
     * 开始拖拽操作
     */
    /**
     * 开始拖拽操作
     *
     * @param lineIndex 源图片所在行的索引
     * @param imageIndex 源图片在该行中的位置索引
     * @param imageHeightPx 🆕 v7.5：源图片的实际高度（像素），用于跨行拖拽时 Popup 边缘的 Y 轴精确计算
     */
    fun startDrag(lineIndex: Int, imageIndex: Int, imageHeightPx: Float = 0f) {
        state = DragState(
            isDragging = true,
            sourceLineIndex = lineIndex,
            sourceImageIndex = imageIndex,
            currentTargetLine = lineIndex,
            currentTargetImage = imageIndex,
            dragOffset = Offset.Zero,
            dragMode = DragMode.INLINE_SORT,
            /** 🆕 v7.5：记录源图片实际高度，用于跨行 Y 轴边缘判定 */
            sourceImageHeightPx = imageHeightPx
        )
    }

    /**
     * 更新拖拽过程中的状态
     *
     * @param currentOffset 拖拽偏移量（像素）
     * @param density 屏幕密度
     * @param rowBounds 各行的边界矩形（用于跨行目标行检测，INLINE_SORT 和回退使用）
     * @param fingerY 手指当前 Y 坐标（用于跨行目标行检测）
     * @param fingerX 手指当前 X 坐标（屏幕绝对坐标，用于跨行模式下交换/插入判定）
     * @param targetLineImageCount 目标行中的图片数量（用于 X 轴位置计算）
     * @param targetRowLeftX 目标行的左边界 X 坐标（用于将 fingerX 从绝对坐标转为行内相对坐标）
     * @param imageRowScrollOffsetPx 🆕 v7.3：图片 Row 的水平滚动偏移量（像素），用于修正 localToScreen 的视觉坐标偏差
     * @param imageRowBounds 🆕 v7.3：各图片行的边界矩形（用于 CROSS_LINE 模式下更精确的 Y 轴边缘判定）
     */
    fun updateDrag(
        currentOffset: Offset,
        density: Float,
        rowBounds: List<androidx.compose.ui.geometry.Rect>? = null,
        fingerY: Float? = null,
        fingerX: Float? = null,
        targetLineImageCount: Int = 0,
        targetRowLeftX: Float = 0f,
        imageRowScrollOffsetPx: Float = 0f,
        imageRowBounds: List<androidx.compose.ui.geometry.Rect>? = null
    ) {
        if (!state.isDragging) return

        val thresholdPx = VERTICAL_THRESHOLD_DP * density
        val newMode = detectDragMode(currentOffset.y, thresholdPx)

        var targetLine = state.currentTargetLine
        var targetImage = state.currentTargetImage
        var dropType = DropType.NONE

        if (newMode == DragMode.CROSS_LINE && rowBounds != null && fingerY != null) {
            /**
             * 🆕 v7.5：跨行 Y 轴边缘判定——以 Popup 图片边缘为基准
             *
             * 类比同行 INLINE_SORT 的 X 轴逻辑（向右看右边缘、向左看左边缘），
             * 跨行拖拽时：
             * - 向上拖（dragOffset.y < 0）：用 Popup 上边缘判定目标行
             * - 向下拖（dragOffset.y > 0）：用 Popup 下边缘判定目标行
             *
             * 这样当 Popup 图片的边缘刚进入目标行的图片区域时，
             * 就能正确识别目标行，而不是等手指进入该区域。
             *
             * 计算公式：
             *   edgeY = fingerY + (dragOffset.y > 0 ? +半高 : -半高)
             *   其中 半高 = ATTACHMENT_HEIGHT_DP / 2 × density
             */
            /**
             * 🆕 v7.5 动态高度：优先使用 startDrag 时传入的实际图片高度，
             * 回退到固定常量 ATTACHMENT_HEIGHT_DP（兼容未传入高度的场景）
             */
            val effectiveHeightPx = if (state.sourceImageHeightPx > 0f) {
                state.sourceImageHeightPx
            } else {
                ATTACHMENT_HEIGHT_DP * density
            }
            val imageHalfHeightPx = effectiveHeightPx / 2f
            /** 根据拖拽方向选择 Popup 的上/下边缘 */
            val popupEdgeY = if (currentOffset.y > 0f) {
                /** 向下拖：用 Popup 下边缘 */
                fingerY + imageHalfHeightPx
            } else {
                /** 向上拖：用 Popup 上边缘 */
                fingerY - imageHalfHeightPx
            }

            /**
             * 优先使用 imageRowBounds（图片区域的边界）做目标行检测，
             * 而非 rowBounds（整行文本的边界，包含 checkbox + 文字，高度更大）。
             *
             * 回退：如果 imageRowBounds 不可用（如目标行无图片），则使用 rowBounds
             */
            val yDetectionBounds = if (!imageRowBounds.isNullOrEmpty()) imageRowBounds else rowBounds
            targetLine = detectTargetRow(popupEdgeY, yDetectionBounds) ?: state.sourceLineIndex

            /**
             * 🆕 v7：跨行模式下的 X 轴位置检测
             *
             * 根据手指在目标行中的 X 坐标，判断是"交换"还是"插入"：
             *
             * 目标行布局示意（假设 3 张图）：
             *   |← 图0(100dp) →| 8dp |← 图1(100dp) →| 8dp |← 图2(100dp) →|
             *   0              108    208             316    416
             *
             * 判定规则：
             * - fingerX 落在某张图片的 [left, right] 范围内 → SWAP_TO_IMAGE(该图索引)
             * - fingerX 落在两张图片之间的间隙内       → INSERT_BEFORE(下一张图索引)
             * - fingerX 超出最后一张图的右边界          → INSERT_AFTER
             */
            if (fingerX != null && targetLineImageCount > 0) {
                /**
                 * 🆕 v7.3 优化1：水平滚动偏移补偿
                 *
                 * horizontalScroll 导致 localToScreen 返回的是"视觉坐标"（已减去滚动量），
                 * 而 fingerX 是屏幕绝对坐标。两者之间存在 scrollOffsetPx 的偏差。
                 *
                 * 公式：relativeFingerXDp = (fingerX - left + scrollOffset) / density
                 *                              ^^^^^^^   ^^^^    ^^^^^^^^^^^^
                 *                              绝对坐标  左边界   滚动补偿
                 *
                 * 🆕 v7.4 优化2：滚动偏移容错
                 * - scrollOffset 可能为负值（理论上不应，但做防御性处理）
                 * - relativeFingerXDp 做 clamp 防止极端值导致越界判定异常
                 */
                val clampedScrollOffset = imageRowScrollOffsetPx.coerceAtLeast(0f)
                val relativeFingerXDp = (fingerX - targetRowLeftX + clampedScrollOffset) / density
                    .coerceIn(-1000f, 1000f)  // 防止极端值（正常范围：-500~+600dp）
                /**
                 * 🆕 v7.1 修复：detectDropPosition 现在同时返回放置类型和目标图片索引
                 */
                val result = detectDropPositionWithIndex(relativeFingerXDp, targetLineImageCount)
                dropType = result.first
                /** 所有模式都设置 currentTargetImage（INSERT_BEFORE=下一张图索引, INSERT_AFTER=null） */
                targetImage = result.second
            } else {
                targetImage = null
            }
        } else if (newMode == DragMode.INLINE_SORT) {
            /** 行内排序模式：基于 Popup 边缘位置计算目标 */

            targetLine = state.sourceLineIndex

            /**
             * 🆕 基于实际拖动距离的行内排序目标检测（v6 修复）
             *
             * 核心原理：
             * - Popup 图片中心对齐手指（DraggableImageAttachment 的居中修正）
             * - Popup 有 scale=1.08 放大效果，边缘超出手指位置
             *
             * 判定规则：
             * - 向右拖动：当 Popup 右边缘进入目标图片视觉范围时，该目标显示虚线框
             * - 向左拖动：当 Popup 左边缘进入目标图片视觉范围时，该目标显示虚线框
             *
             * 计算方式：
             * 1. 将像素偏移转为 dp（xOffsetDp = 手指实际移动距离）
             * 2. 按单元宽度（图片宽 + 间距 = 108dp）计算跨越的目标数量
             * 3. 使用 roundToInt() 四舍五入，约在拖过半个单元时触发切换
             *
             * 为什么不再使用 edgeCompensationDp：
             * edgeCompensationDp(54dp) 是 Popup 的**静态固有尺寸**（半宽 = 100×1.08/2），
             * 它在 t=0 长按瞬间就已存在，不属于拖拽过程中产生的位移。
             * 将其加到 xOffsetDp 上会导致"重复计算"，使触发点大幅提前。
             *
             * 示例（3张图，源=图2[索引1]，向右拖动）：
             *   单元宽度 = 100dp(图) + 8dp(间距) = 108dp
             *
             *   拖动 20dp：offsetUnits = round(20/108) = 0 → 目标=自身 ✅
             *   拖动 54dp：offsetUnits = round(54/108) = 1 → 目标=图3 ✅（右边缘刚进入）
             *   拖动162dp：offsetUnits = round(162/108)= 2 → 目标=图4 ✅
             */
            val xOffsetDp = currentOffset.x / density

            /** 一个完整单元的宽度 = 图片宽度 + 间距 */
            val unitWidthDp = ATTACHMENT_WIDTH_DP + IMAGE_SPACING_DP  // 108dp

            /**
             * 基于实际拖动距离计算跨越的目标位置数
             *
             * v6 移除了 edgeCompensationDp 补偿运算，
             * 直接使用原始偏移量除以单元宽度。
             * roundToInt() 确保约拖过半个单元（~54dp）时触发切换。
             */
            val offsetUnits = (xOffsetDp / unitWidthDp).roundToInt()

            /** 允许目标位置在 [0, sourceIndex + 剩余数量] 范围内 */
            val maxTarget = state.sourceImageIndex + 3
            targetImage = (state.sourceImageIndex + offsetUnits).coerceIn(0, maxTarget)
        }

        state = state.copy(
            dragOffset = currentOffset,
            dragMode = newMode,
            currentTargetLine = targetLine,
            currentTargetImage = targetImage,
            dropType = dropType
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

        /** 🆕 在 reset 之前保存源位置信息 + 放置类型 */
        val savedSourceLine = state.sourceLineIndex
        val savedSourceImage = state.sourceImageIndex
        val savedDropType = state.dropType  // 🆕 v7.3：保存放置类型

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
                    sourceImageIndex = savedSourceImage,
                    dropType = savedDropType  // 🆕 v7.3
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
                    sourceImageIndex = savedSourceImage,
                    dropType = savedDropType  // 🆕 v7.3
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
                    sourceImageIndex = savedSourceImage,
                    dropType = savedDropType  // 🆕 v7.3
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
     * 检测 Popup 边缘当前悬停的目标行
     *
     * 🆕 v7.5：参数从 fingerY 改为 edgeY，表示 Popup 图片边缘的 Y 坐标
     * （向上拖=上边缘，向下拖=下边缘），而非手指位置。
     */
    private fun detectTargetRow(
        edgeY: Float,
        rowBounds: List<androidx.compose.ui.geometry.Rect>
    ): Int? {
        if (rowBounds.isEmpty()) return null

        for ((index, rect) in rowBounds.withIndex()) {
            if (edgeY in rect.top..rect.bottom) {
                return index
            }
        }

        return when {
            edgeY < rowBounds.first().top -> 0
            edgeY > rowBounds.last().bottom -> rowBounds.lastIndex
            else -> null
        }
    }

    /**
     * 🆕 v7：检测跨行模式下手指的放置类型（交换 or 插入）
     *
     * 根据手指在目标行中的 X 坐标（dp），判断当前应显示哪种视觉反馈：
     *
     * @param fingerXDp 手指在目标行内的 X 坐标（已转为 dp，相对于行左边界）
     * @param imageCount 目标行中的图片总数
     * @return 放置类型枚举（SWAP_TO_IMAGE / INSERT_BEFORE / INSERT_AFTER / NONE）
     */
    private fun detectDropPosition(fingerXDp: Float, imageCount: Int): DropType {
        return detectDropPositionWithIndex(fingerXDp, imageCount).first
    }

    /**
     * 🆕 v7.1 修复：检测放置类型 + 目标图片索引（合并返回）
     *
     * 解决两个问题：
     * 1. INSERT 模式下需要知道目标图片索引（用于显示插入竖线）
     * 2. fingerX 需要减去目标行的左边界偏移（屏幕绝对坐标 → 行内相对坐标）
     *
     * 返回值：
     * - first:  DropType（放置类型）
     * - second: 目标图片索引（SWAP=被悬停的图, INSERT_BEFORE=插入点之后的图, INSERT_AFTER=null, NONE=null）
     */
    private fun detectDropPositionWithIndex(fingerXDp: Float, imageCount: Int): Pair<DropType, Int?> {
        if (imageCount <= 0) return Pair(DropType.NONE, null)

        val unitWidth = ATTACHMENT_WIDTH_DP + IMAGE_SPACING_DP  // 108dp

        for (i in 0 until imageCount) {
            /** 第 i 张图片的左边界和右边界（dp） */
            val imageLeft = i * unitWidth
            val imageRight = imageLeft + ATTACHMENT_WIDTH_DP

            if (fingerXDp in imageLeft..imageRight) {
                /** 手指在某张图片范围内 → 交换模式，目标为该图 */
                return Pair(DropType.SWAP_TO_IMAGE, i)
            }

            /** 检查是否在当前图与下一张图的间隙中 */
            if (i < imageCount - 1) {
                val gapStart = imageRight
                val gapEnd = (i + 1) * unitWidth
                if (fingerXDp in gapStart..gapEnd) {
                    /** 手指在间隙中 → 插入到下一张图之前，目标为下一张图索引 */
                    return Pair(DropType.INSERT_BEFORE, i + 1)
                }
            }
        }

        /** 超出最后一张图的右边界 → 追加到末尾，无具体目标图片 */
        val lastImageRight = (imageCount - 1) * unitWidth + ATTACHMENT_WIDTH_DP
        return if (fingerXDp > lastImageRight) Pair(DropType.INSERT_AFTER, null) else Pair(DropType.NONE, null)
    }

    /**
     * 🆕 v7：检测跨行交换模式下手指悬停的目标图片索引
     *
     * 当放置类型为 SWAP_TO_IMAGE 时调用，返回具体是哪张图片被悬停。
     *
     * @param fingerXDp 手指在目标行内的 X 坐标（dp）
     * @param imageCount 目标行中的图片总数
     * @return 目标图片索引（0-based）
     */
    private fun detectTargetImageIndex(fingerXDp: Float, imageCount: Int): Int? {
        if (imageCount <= 0) return null

        val unitWidth = ATTACHMENT_WIDTH_DP + IMAGE_SPACING_DP

        for (i in 0 until imageCount) {
            val imageLeft = i * unitWidth
            val imageRight = imageLeft + ATTACHMENT_WIDTH_DP
            if (fingerXDp in imageLeft..imageRight) {
                return i
            }
        }
        return null
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
            /**
             * 🆕 v7.3 优化3：跨行操作区分交换 vs 移动
             *
             * - SWAP_TO_IMAGE：源图与目标图互换位置（真正的交换）
             * - INSERT_BEFORE/AFTER：从源行删除 → 插入到目标行（移动）
             */
            if (result.dropType == DropType.SWAP_TO_IMAGE && result.targetImageIndex != null) {
                /** ===== 跨行交换逻辑 ===== */

                val tgtLineIdx = result.targetLineIndex
                val tgtImgIdx = result.targetImageIndex

                /**
                 * 🆕 v7.4 优化1：同位置交换保护
                 *
                 * 如果源行=目标行 且 源索引=目标索引，说明是同一张图的同一个位置，
                 * 交换操作无意义（结果等同于原状），直接跳过。
                 * 注：endDrag() 已对此情况返回 isSuccess=false，此处为双重保险。
                 */
                if (srcLineIdx != tgtLineIdx || srcImgIdx != tgtImgIdx) {
                    /** 验证索引有效性 */
                    if (srcLineIdx in updatedLines.indices &&
                        tgtLineIdx in updatedLines.indices &&
                        srcImgIdx in updatedLines[srcLineIdx].imagePaths.indices &&
                        tgtImgIdx in updatedLines[tgtLineIdx].imagePaths.indices
                    ) {
                        /** 读取两张图片的路径 */
                        val srcPath = updatedLines[srcLineIdx].imagePaths[srcImgIdx]
                        val tgtPath = updatedLines[tgtLineIdx].imagePaths[tgtImgIdx]

                        /** 交换：源行目标位置替换为目标图片，目标行对应位置替换为源图片 */
                        val srcMutable = updatedLines[srcLineIdx].imagePaths.toMutableList()
                        srcMutable[srcImgIdx] = tgtPath
                        updatedLines[srcLineIdx] = updatedLines[srcLineIdx].copy(imagePaths = srcMutable.toList())

                        val tgtMutable = updatedLines[tgtLineIdx].imagePaths.toMutableList()
                        tgtMutable[tgtImgIdx] = srcPath
                        updatedLines[tgtLineIdx] = updatedLines[tgtLineIdx].copy(imagePaths = tgtMutable.toList())
                    }
                }
            } else {
                /** ===== 跨行移动逻辑（INSERT 模式） ===== */

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
