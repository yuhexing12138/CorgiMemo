package com.corgimemo.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.ImageData
import com.mohamedrejeb.richeditor.model.ImageLoader

/**
 * RichTextEditor 自定义图片加载器
 *
 * v2026-08-01 Phase 4：为 compose-rich-editor 库提供从内部存储路径加载图片的能力。
 *
 * 背景：
 * - compose-rich-editor 库的 DefaultImageLoader 对所有 model 返回 null
 * - 库提供了 richeditor-compose-coil3 模块（Coil3ImageLoader），但项目未引入该子模块
 * - 项目已有 Coil3 依赖，直接在 app 内实现自定义 ImageLoader 更简洁
 *
 * 工作原理：
 * 1. RichSpanStyle.Image 的 model 字段存储图片路径（String）
 * 2. 库通过 LocalImageLoader 调用 load(model) 获取 ImageData
 * 3. 本加载器用 Coil3 的 rememberAsyncImagePainter 异步加载图片
 * 4. 加载成功后返回 ImageData(painter)，库自动渲染到 inline content 位置
 *
 * @see com.mohamedrejeb.richeditor.model.ImageLoader
 * @see com.mohamedrejeb.richeditor.model.RichSpanStyle.Image
 */
@OptIn(ExperimentalRichTextApi::class)
object RichTextImageLoader : ImageLoader {

    /**
     * 加载图片并返回 ImageData
     *
     * @param model 图片标识（本项目使用内部存储文件路径 String）
     * @return ImageData 包含加载完成的 Painter；加载中/失败返回 null（库显示占位符）
     */
    @Composable
    override fun load(model: Any): ImageData? {
        val context = LocalContext.current

        /** 构建 Coil3 ImageRequest，启用淡入动画 */
        val request = remember(model) {
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build()
        }

        /** 使用 Coil3 异步加载 Painter */
        val painter = rememberAsyncImagePainter(model = request)

        /** 追踪加载状态，仅在成功时返回 ImageData */
        var imageData by remember {
            mutableStateOf<ImageData?>(null)
        }

        LaunchedEffect(painter.state) {
            painter.state.collect { state ->
                imageData = when (state) {
                    is AsyncImagePainter.State.Success -> {
                        ImageData(
                            painter = state.painter,
                            contentScale = ContentScale.Fit,
                        )
                    }
                    else -> null
                }
            }
        }

        return imageData
    }
}
