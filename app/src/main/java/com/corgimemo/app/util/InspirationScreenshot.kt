// app/src/main/java/com/corgimemo/app/util/InspirationScreenshot.kt
package com.corgimemo.app.util

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.corgimemo.app.backup.exporter.ShareIntentHelper
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.screens.inspiration.components.InspirationViewCard
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 灵感卡片截图工具
 * 提供两种高清方案：
 * 1. captureAsBitmap: 截屏 GraphicsLayer + 位图双线性插值放大（简单可靠）
 * 2. captureHighResBitmap: 离屏渲染 + 矢量级高清（效果更优，实现复杂）
 */
object InspirationScreenshot {

    /**
     * 方案一：将 GraphicsLayer 捕获为 Android Bitmap（位图放大版）
     *
     * 高清策略：GraphicsLayer 录制的位图是按当前设备密度渲染的（通常 360~1080 宽），
     * 分享时按 scaleFactor 放大（默认 2x），使用双线性插值获得更大、清晰度更好的位图。
     *
     * @param layer 录制了卡片内容的 GraphicsLayer
     * @param scaleFactor 放大倍数（默认 2.0f）
     * @return Android Bitmap
     */
    suspend fun captureAsBitmap(
        layer: GraphicsLayer,
        scaleFactor: Float = 2.0f
    ): Bitmap {
        val originalBitmap = layer.toImageBitmap().asAndroidBitmap()

        // scaleFactor <= 1f 表示不放大，直接返回原图（节省内存）
        if (scaleFactor <= 1f) return originalBitmap

        // 等比例放大：宽高 = 原尺寸 * scaleFactor
        val newWidth = (originalBitmap.width * scaleFactor).toInt()
        val newHeight = (originalBitmap.height * scaleFactor).toInt()

        // 边界保护：避免 scaleFactor 异常值导致 newWidth/newHeight 为 0 或负数
        if (newWidth <= 0 || newHeight <= 0) return originalBitmap

        // createScaledBitmap 使用双线性插值（filter=true）进行高质量缩放
        val scaledBitmap = Bitmap.createScaledBitmap(
            originalBitmap,
            newWidth,
            newHeight,
            /* filter = */ true
        )

        // 如果 createScaledBitmap 返回了新的位图（与原图不同对象），则释放原图
        if (scaledBitmap !== originalBitmap) {
            originalBitmap.recycle()
        }

        return scaledBitmap
    }

    /**
     * 方案二：矢量级高清截图（离屏渲染版）
     *
     * 真正的矢量级清晰度：与位图放大不同，本方案通过在离屏 ComposeView 中以更大画布重新渲染卡片，
     * 所有元素（文字、图标、图片）都会按更多像素真实渲染，文字边缘锐利无模糊。
     *
     * 原理：
     * 1. 在 DecorView 上添加一个不可见的 FrameLayout 容器（移到屏幕外）
     * 2. 容器尺寸 = 原始屏幕宽高 × scaleFactor（关键：让画布更大）
     * 3. 在容器内按原始 density 渲染 InspirationViewCard
     * 4. 由于容器变大了，Card 内所有元素（dp 转 px）都会得到更多像素
     * 5. 截取 GraphicsLayer 后得到真正的矢量级高清位图
     *
     * 优势（vs 位图放大）：
     * - 文字边缘锐利，没有位图放大的模糊感
     * - 图标、形状、圆角清晰
     * - 与原 Card 视觉表现完全一致，仅是"放大版"
     *
     * 风险：
     * - 需要 Activity 引用
     * - 容器尺寸按 scaleFactor 放大，内存占用增加（约 4x for 2x）
     * - 需要等待图片等异步资源加载完成
     * - 超时保护 5 秒（图片加载可能较慢）
     *
     * @param context 上下文（需要是 Activity）
     * @param inspiration 灵感数据
     * @param scaleFactor 放大倍数（默认 2.0f，建议 1.5~2.5，过大会 OOM）
     * @return 矢量级高清 Bitmap，失败返回 null（context 非 Activity、DecorView 获取失败、容器尺寸异常、渲染超时）
     */
    suspend fun captureHighResBitmap(
        context: Context,
        inspiration: Inspiration,
        scaleFactor: Float = 2.0f
    ): Bitmap? = withContext(Dispatchers.Main) {
        val activity = context as? Activity
        if (activity == null) {
            android.util.Log.e("InspirationScreenshot", "captureHighResBitmap: context 不是 Activity")
            return@withContext null
        }
        val decorView = activity.window.decorView as? ViewGroup
        if (decorView == null) {
            android.util.Log.e("InspirationScreenshot", "captureHighResBitmap: DecorView 不是 ViewGroup")
            return@withContext null
        }

        val displayMetrics = activity.resources.displayMetrics
        // 以屏幕宽度作为卡片基准宽度，最大高度不超过屏幕的 70%
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = (displayMetrics.heightPixels * 0.7f).toInt()

        // 容器尺寸按 scaleFactor 放大：这是矢量高清的关键
        val containerWidth = (screenWidth * scaleFactor).toInt()
        val containerHeight = (screenHeight * scaleFactor).toInt()

        // 边界保护：避免过大或过小的容器
        if (containerWidth <= 0 || containerHeight <= 0) {
            android.util.Log.e("InspirationScreenshot", "captureHighResBitmap: 容器尺寸异常 width=$containerWidth height=$containerHeight")
            return@withContext null
        }

        // ========== 关键改进 1: 预加载所有图片 ==========
        // 离屏渲染前先用 Coil 同步预加载所有图片到内存
        // 这样离屏渲染时 AsyncImage 可立即从内存显示，无需等待网络/磁盘加载
        val imagePaths = parseImagePaths(inspiration.imagePaths)
        if (imagePaths.isNotEmpty()) {
            android.util.Log.d("InspirationScreenshot", "captureHighResBitmap: 开始预加载 ${imagePaths.size} 张图片")
            val preloadResult = preloadImages(context, imagePaths)
            android.util.Log.d("InspirationScreenshot", "captureHighResBitmap: 图片预加载完成 成功=${preloadResult.first} 失败=${preloadResult.second}")
        }

        android.util.Log.d("InspirationScreenshot", "captureHighResBitmap: 容器=${containerWidth}x${containerHeight} 缩放=${scaleFactor}x")

        // 创建不可见容器
        // 关键：不能用 translationX 移到屏幕外，Android 会跳过绘制优化导致 GraphicsLayer 为空
        // 也不能用 GONE，会跳过 measure/layout
        // 正确做法：alpha=0 + INVISIBLE 让容器完全不可见，但仍正常 measure/layout/draw
        val container = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(containerWidth, containerHeight)
            alpha = 0f
            visibility = android.view.View.INVISIBLE
        }

        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(composeView)
        // 必须先 addView 到 DecorView，ComposeView 才能正常 attach 和渲染
        decorView.addView(container)

        try {
            // 8 秒超时：图片已预加载，组合和绘制只需等几帧
            val timeoutMs = 8000L
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

            // 注意：项目使用 kotlinx-coroutines 1.9.0
            // 1. suspendCancellableCoroutine 只有 block 一个参数（onCancellation 命名参数是 1.10+ 才有）
            // 2. CancellableContinuation.resume(value) 已被废弃/移除，1.9+ 强制要求 onCancellation 参数
            //    必须用 continuation.resume(value, onCancellation = null) 或 continuation.resume(value) { ... } 形式
            val bitmap = suspendCancellableCoroutine<Bitmap?> { continuation ->
                val timeoutRunnable = Runnable {
                    android.util.Log.w("InspirationScreenshot", "captureHighResBitmap: 渲染超时 ${timeoutMs}ms")
                    if (continuation.isActive) continuation.resume(null, onCancellation = null)
                }
                mainHandler.postDelayed(timeoutRunnable, timeoutMs)
                // 协程被取消时清理超时定时器
                continuation.invokeOnCancellation {
                    mainHandler.removeCallbacks(timeoutRunnable)
                }

                try {
                    composeView.setContent {
                        val graphicsLayer = rememberGraphicsLayer()

                        LaunchedEffect(Unit) {
                            try {
                                // 关键改进 2: 等待 15 帧让 Compose 完成首次组合
                                repeat(15) { awaitFrame() }
                                // 短暂 delay 让预加载的图片有时间渲染到 GraphicsLayer
                                delay(500)
                                // 再次等待 10 帧确保图片显示完成
                                repeat(10) { awaitFrame() }

                                // ========== 关键改进 5: 尺寸检查 ==========
                                // GraphicsLayer 必须在 size > 0 时才能 toImageBitmap
                                // 离屏环境下可能 size = 0，需要继续等待
                                var bmp: Bitmap? = null
                                var attempts = 0
                                val maxAttempts = 20
                                while (bmp == null && attempts < maxAttempts) {
                                    val size = graphicsLayer.size
                                    android.util.Log.d("InspirationScreenshot", "尝试 $attempts: GraphicsLayer size=$size")
                                    if (size.width > 0 && size.height > 0) {
                                        try {
                                            bmp = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                        } catch (e: IllegalArgumentException) {
                                            // 仍有竞态：GraphicsLayer 还未完全渲染
                                            android.util.Log.w("InspirationScreenshot", "toImageBitmap 失败重试: ${e.message}")
                                            bmp = null
                                        }
                                    }
                                    if (bmp == null) {
                                        delay(100)
                                        attempts++
                                    }
                                }

                                mainHandler.removeCallbacks(timeoutRunnable)
                                if (bmp != null) {
                                    android.util.Log.d("InspirationScreenshot", "captureHighResBitmap: 截图成功 ${bmp.width}x${bmp.height} (尝试 $attempts 次)")
                                    if (continuation.isActive) continuation.resume(bmp, onCancellation = null)
                                } else {
                                    android.util.Log.e("InspirationScreenshot", "captureHighResBitmap: GraphicsLayer 始终 size=0，已达最大重试次数")
                                    if (continuation.isActive) continuation.resume(null, onCancellation = null)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("InspirationScreenshot", "captureHighResBitmap: LaunchedEffect 异常", e)
                                mainHandler.removeCallbacks(timeoutRunnable)
                                if (continuation.isActive) continuation.resume(null, onCancellation = null)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // 录制内容到 GraphicsLayer 用于截图
                                .drawWithContent {
                                    graphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(graphicsLayer)
                                }
                        ) {
                            InspirationViewCard(
                                inspiration = inspiration,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("InspirationScreenshot", "captureHighResBitmap: setContent 异常", e)
                    mainHandler.removeCallbacks(timeoutRunnable)
                    if (continuation.isActive) continuation.resume(null, onCancellation = null)
                }
            }
            bitmap
        } finally {
            // 必须清理：移除离屏容器，避免内存泄漏
            decorView.removeView(container)
        }
    }

    /**
     * 解析灵感图片路径 JSON 数组
     * 与 InspirationViewCard 内部的解析逻辑保持一致
     */
    private fun parseImagePaths(imagePathsJson: String): List<String> {
        if (imagePathsJson.isBlank()) return emptyList()
        return try {
            org.json.JSONArray(imagePathsJson).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (e: Exception) {
            android.util.Log.w("InspirationScreenshot", "parseImagePaths 失败", e)
            emptyList()
        }
    }

    /**
     * 关键改进 4: 预加载所有图片到内存
     * 使用 Coil.imageLoader.execute() 同步预加载所有图片
     * 离屏渲染时 AsyncImage 即可从内存立即显示，无需等待网络/磁盘加载
     *
     * @return Pair<成功数量, 失败数量>
     */
    private suspend fun preloadImages(
        context: Context,
        imagePaths: List<String>
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val imageLoader = context.imageLoader
        var success = 0
        var failed = 0
        // 并行预加载所有图片
        val deferreds = imagePaths.map { path ->
            async {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(path)
                        .build()
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        success++
                    } else {
                        android.util.Log.w("InspirationScreenshot", "预加载图片结果非成功: $path")
                        failed++
                    }
                } catch (e: Exception) {
                    android.util.Log.w("InspirationScreenshot", "预加载图片失败: $path", e)
                    failed++
                }
            }
        }
        // 等待所有预加载完成
        deferreds.awaitAll()
        Pair(success, failed)
    }

    /**
     * 保存 Bitmap 到系统相册（让图片出现在"照片"/"图库"等应用中）
     *
     * 关键：使用 MediaStore API（API 29+）保存到公共 Pictures 目录，
     * 系统相册自动扫描显示，无需任何存储权限。
     *
     * 旧实现保存到 getExternalFilesDir()（应用私有目录），
     * 不会出现在系统相册中 —— 这是用户反馈"找不到图片"的原因。
     *
     * API 兼容：
     * - API 29+ (Android 10+)：MediaStore.Images.Media.EXTERNAL_CONTENT_URI（Scoped Storage），无需权限
     * - API 26-28 (Android 8-9)：回退到应用私有目录（用户私有图片）
     *
     * @param context 上下文
     * @param bitmap 要保存的位图
     * @return 保存的 Uri（API 29+）或 File（API 26-28），失败返回 null
     */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap): Uri? =
        withContext(Dispatchers.IO) {
            val fileName = "Inspiration_${System.currentTimeMillis()}.png"
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // ========== API 29+ 方案：MediaStore（Scoped Storage）==========
                    // 无需 WRITE_EXTERNAL_STORAGE 权限
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        // 关键：RELATIVE_PATH 让图片保存到公共 Pictures 目录
                        // 系统相册自动扫描并显示
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext null
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    // 标记完成，让其他应用可见
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    android.util.Log.d("InspirationScreenshot", "保存到系统相册成功（MediaStore）: $uri")
                    uri
                } else {
                    // ========== API 26-28 方案：回退到应用私有目录 ==========
                    // 旧版本没有 Scoped Storage，公共目录需要 WRITE_EXTERNAL_STORAGE 权限
                    // 由于 manifest 未声明该权限，回退到应用私有目录
                    val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                        ?: return@withContext null
                    if (!picturesDir.exists()) picturesDir.mkdirs()
                    val file = File(picturesDir, fileName)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    android.util.Log.d("InspirationScreenshot", "保存到应用私有目录（API<29）: ${file.absolutePath}")
                    Uri.fromFile(file)
                }
            } catch (e: Exception) {
                android.util.Log.e("InspirationScreenshot", "保存到系统相册失败", e)
                null
            }
        }

    /**
     * 创建分享图片的 Intent（复用项目内现有 ShareIntentHelper）
     *
     * @param context 上下文
     * @param imageFile 分享的图片文件
     * @return 启动系统分享面板的 Intent
     */
    fun createShareIntent(context: Context, imageFile: File): Intent {
        return ShareIntentHelper.createShareImageIntent(
            context = context,
            imageFile = imageFile,
            text = "灵感分享"
        )
    }

    /**
     * 创建分享图片 Uri 的 Intent（用于分享 MediaStore Uri）
     *
     * 与 File 重载的区别：直接使用 MediaStore Uri，无需 FileProvider
     * MediaStore Uri 已经是 content:// 形式，可直接分享
     *
     * @param context 上下文
     * @param imageUri 分享的图片 Uri（content://）
     * @return 启动系统分享面板的 Intent
     */
    fun createShareIntent(context: Context, imageUri: Uri): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "分享灵感卡片").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
