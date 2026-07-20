package com.corgimemo.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 圆形图片裁剪组件
 *
 * 交互：
 * - 单指拖动：平移图片（约束在裁剪框内）
 * - 双指捏合：缩放（0.5x ~ 5x）
 * - 双指拖动：平移（与单指一致）
 * - 圆形遮罩 + 半透明黑色背景突出裁剪区
 * - 裁剪框内 4x4 九宫格辅助线（高对比度，不超出圆形范围）
 *
 * 状态提升：scale/offsetX/offsetY/canvasSize 通过 MutableState 暴露给调用方，
 * 调用方可在"确认裁剪"时读取这些值，确保用户手势实际生效。
 *
 * @param sourceBitmap 待裁剪的源图片
 * @param cropSize    圆形裁剪框直径（dp，默认 280dp）
 * @param scaleState  用户缩放比例（共享状态，外部可读）
 * @param offsetXState 用户水平偏移（共享状态，外部可读）
 * @param offsetYState 用户垂直偏移（共享状态，外部可读）
 * @param canvasSizeState 画布尺寸（共享状态，外部可读，用于裁剪计算）
 * @param onCropComplete 裁剪确认回调（裁剪后的 Bitmap）
 * @param onCancel 取消回调
 */
@Composable
fun CircularImageCropper(
    sourceBitmap: Bitmap,
    cropSize: Dp = 280.dp,
    scaleState: androidx.compose.runtime.MutableState<Float>,
    offsetXState: androidx.compose.runtime.MutableState<Float>,
    offsetYState: androidx.compose.runtime.MutableState<Float>,
    canvasSizeState: androidx.compose.runtime.MutableState<IntSize>,
    onCropComplete: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    // ===== 状态（提升到调用方，确保裁剪时用用户实际手势值）=====
    var scale by scaleState
    var offsetX by offsetXState
    var offsetY by offsetYState
    var canvasSize by canvasSizeState

    val density = LocalDensity.current
    val cropSizePx = with(density) { cropSize.toPx() }
    val painter = remember(sourceBitmap) {
        BitmapPainter(sourceBitmap.asImageBitmap())
    }

    // ===== 初始化：图片按 Fit 缩放到裁剪框内 =====
    LaunchedEffect(canvasSize, sourceBitmap) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val sw = sourceBitmap.width.toFloat()
            val sh = sourceBitmap.height.toFloat()
            val fitScale = max(cropSizePx / sw, cropSizePx / sh)
            scale = fitScale
            offsetX = 0f
            offsetY = 0f
        }
    }

    // ===== 主画布 =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { canvasSize = it }
    ) {
        // 图片层
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // 计算 fitScale 作为 scale 的下限，确保用户最多缩小到初始尺寸
                    // （避免硬编码下限导致大图缩小不到初始 fit 状态）
                    val sw = sourceBitmap.width.toFloat()
                    val sh = sourceBitmap.height.toFloat()
                    val minScale = max(cropSizePx / sw, cropSizePx / sh)

                    // 自定义手势检测：严格区分单指/双指
                    // - 单指：只平移，不缩放（避免点击误触放大）
                    // - 双指：平移 + 缩放
                    awaitEachGesture {
                        val first = awaitFirstDown()
                        var prevCenter = first.position
                        var prevDist = 0f  // 0 表示单指状态，双指到来时才计算首帧基准
                        first.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.filter { it.pressed }
                            if (active.isEmpty()) break

                            when {
                                active.size >= 2 -> {
                                    val a = active[0].position
                                    val b = active[1].position
                                    val currentDist = (a - b).getDistance()
                                    val currentCenter = Offset(
                                        (a.x + b.x) / 2f,
                                        (a.y + b.y) / 2f
                                    )
                                    if (prevDist > 0f) {
                                        // 避免首帧 zoom 跳变：只在 prevDist 有效时才更新 scale
                                        val zoom = (currentDist / prevDist).coerceIn(0.5f, 2f)
                                        // 下限用 minScale（fitScale），确保能缩小回初始尺寸
                                        scale = (scale * zoom).coerceIn(minScale, 5f)
                                        offsetX += currentCenter.x - prevCenter.x
                                        offsetY += currentCenter.y - prevCenter.y
                                    }
                                    prevDist = currentDist
                                    prevCenter = currentCenter
                                }
                                active.size == 1 -> {
                                    // 单指：只平移，重置 prevDist（切回双指时重新计算基准）
                                    val current = active[0].position
                                    if (active[0].positionChanged()) {
                                        offsetX += current.x - prevCenter.x
                                        offsetY += current.y - prevCenter.y
                                    }
                                    prevCenter = current
                                    prevDist = 0f
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            // 绘制半透明黑色蒙版（除裁剪框外）
            drawRect(color = Color.Black.copy(alpha = 0.6f))

            // 计算裁剪框在画布中的位置（居中）
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = cropSizePx / 2f

            // 用 BlendMode.Clear 清除裁剪框区域的蒙版
            drawCircle(
                color = Color.Transparent,
                radius = r,
                center = Offset(cx, cy),
                blendMode = BlendMode.Clear
            )

            // 绘制图片
            val imgW = sourceBitmap.width * scale
            val imgH = sourceBitmap.height * scale
            drawImage(
                image = sourceBitmap.asImageBitmap(),
                dstOffset = IntOffset(
                    (cx - imgW / 2f + offsetX).toInt(),
                    (cy - imgH / 2f + offsetY).toInt()
                ),
                dstSize = IntSize(
                    imgW.toInt().coerceAtLeast(1),
                    imgH.toInt().coerceAtLeast(1)
                )
            )

            // 绘制白色圆形边框
            drawCircle(
                color = Color.White,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 4f)
            )

            // 绘制 4x4 九宫格辅助线（高对比度：黑色底+白色面，限制在圆形内）
            // 4等分 = 3条线，间距 = 2r/4 = r/2
            val gridStrokeW = 1.5f
            val gridShadowW = 3.5f
            val gridStep = r * 2f / 4f
            // 竖线
            for (i in 1 until 4) {
                val x = cx - r + gridStep * i
                val dx = x - cx
                // 圆内 y 范围：|dx| <= r 时才有交点
                if (abs(dx) < r) {
                    val halfChord = sqrt(r * r - dx * dx)
                    val yStart = cy - halfChord
                    val yEnd = cy + halfChord
                    // 黑色阴影底（提升任意背景对比度）
                    drawLine(
                        color = Color.Black.copy(alpha = 0.6f),
                        start = Offset(x, yStart),
                        end = Offset(x, yEnd),
                        strokeWidth = gridShadowW
                    )
                    // 白色主线
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(x, yStart),
                        end = Offset(x, yEnd),
                        strokeWidth = gridStrokeW
                    )
                }
            }
            // 横线
            for (i in 1 until 4) {
                val y = cy - r + gridStep * i
                val dy = y - cy
                if (abs(dy) < r) {
                    val halfChord = sqrt(r * r - dy * dy)
                    val xStart = cx - halfChord
                    val xEnd = cx + halfChord
                    drawLine(
                        color = Color.Black.copy(alpha = 0.6f),
                        start = Offset(xStart, y),
                        end = Offset(xEnd, y),
                        strokeWidth = gridShadowW
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(xStart, y),
                        end = Offset(xEnd, y),
                        strokeWidth = gridStrokeW
                    )
                }
            }
        }
    }

    // ===== 裁剪按钮（在裁剪框下方，由调用方控制；本组件仅暴露回调）=====
    // 此处故意不放按钮 — 让 ProfileDetailScreen 控制布局
    // 调用方应在裁剪框下方放置两个按钮：
    //   "取消" → onCancel()
    //   "使用" → 计算最终 Bitmap → onCropComplete(bitmap)
    // 裁剪时读取 scaleState/offsetXState/offsetYState/canvasSizeState 的值
    // （这些值随用户手势实时更新，确保裁剪结果与预览一致）
}

/**
 * 从源 Bitmap + 缩放/偏移参数裁剪出最终圆形头像 Bitmap
 *
 * 算法：
 * 1. 用最终 scale/offset 重绘图片到目标 Bitmap
 * 2. 取中心 cropSize 区域
 * 3. 输出 512x512（足够 Profile 头卡 + 抽屉 48dp 高清显示）
 *
 * @param source  源图
 * @param scale   用户缩放比例
 * @param offsetX 用户水平偏移（px）
 * @param offsetY 用户垂直偏移（px）
 * @param canvasSize 裁剪器画布尺寸
 * @param cropSizePx 裁剪框直径（px）
 * @param outputSize  输出 Bitmap 尺寸（默认 512）
 */
fun cropCircularBitmap(
    source: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    canvasSize: IntSize,
    cropSizePx: Float,
    outputSize: Int = 512
): Bitmap {
    val sw = source.width
    val sh = source.height

    // 计算图片在画布中的位置
    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f
    val imgW = sw * scale
    val imgH = sh * scale
    val imgLeft = cx - imgW / 2f + offsetX
    val imgTop = cy - imgH / 2f + offsetY

    // 计算裁剪框对应的源图区域
    val cropLeft = cx - cropSizePx / 2f
    val cropTop = cy - cropSizePx / 2f

    // 源图上对应的区域
    val srcLeft = ((cropLeft - imgLeft) / scale).toInt().coerceIn(0, sw - 1)
    val srcTop = ((cropTop - imgTop) / scale).toInt().coerceIn(0, sh - 1)
    val srcRight = ((cropLeft + cropSizePx - imgLeft) / scale).toInt()
        .coerceIn(srcLeft + 1, sw)
    val srcBottom = ((cropTop + cropSizePx - imgTop) / scale).toInt()
        .coerceIn(srcTop + 1, sh)

    // 裁剪 + 缩放到输出尺寸
    val cropped = Bitmap.createBitmap(source, srcLeft, srcTop, srcRight - srcLeft, srcBottom - srcTop)
    return Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)
}
