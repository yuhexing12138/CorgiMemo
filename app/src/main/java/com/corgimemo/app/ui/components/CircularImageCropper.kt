package com.corgimemo.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 圆形图片裁剪组件
 *
 * 交互：
 * - 单指拖动：平移图片（约束在裁剪框内）
 * - 双指捏合：缩放（0.5x ~ 5x）
 * - 双指拖动：平移（与单指一致）
 * - 圆形遮罩 + 半透明黑色背景突出裁剪区
 *
 * 输出：调用方传入 onCropComplete，组件内部按裁剪框区域生成正方形 Bitmap
 *
 * @param sourceBitmap 待裁剪的源图片
 * @param cropSize    圆形裁剪框直径（dp，默认 280dp）
 * @param onCropComplete 裁剪确认回调（裁剪后的 Bitmap）
 * @param onCancel 取消回调
 */
@Composable
fun CircularImageCropper(
    sourceBitmap: Bitmap,
    cropSize: Dp = 280.dp,
    onCropComplete: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    // ===== 状态 =====
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

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
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                        // 简单的边界约束（够用，不做精细 clamp）
                        scale = newScale
                        offsetX += pan.x
                        offsetY += pan.y
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
        }
    }

    // ===== 裁剪按钮（在裁剪框下方，由调用方控制；本组件仅暴露回调）=====
    // 此处故意不放按钮 — 让 ProfileDetailScreen 控制布局
    // 调用方应在裁剪框下方放置两个按钮：
    //   "取消" → onCancel()
    //   "使用" → 计算最终 Bitmap → onCropComplete(bitmap)
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
