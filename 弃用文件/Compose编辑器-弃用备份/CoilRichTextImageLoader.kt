package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.layout.ContentScale
import coil3.compose.rememberAsyncImagePainter
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.ImageData
import com.mohamedrejeb.richeditor.model.ImageLoader

/**
 * 基于 Coil 3 的富文本内联图片加载器
 *
 * ## 为什么需要它
 * compose-rich-editor 的 `DefaultImageLoader` 一律返回 null（不含任何解码能力），
 * 因此必须通过 `LocalImageLoader` 提供有效实现，正文内的内联图片才会真正显示出来。
 * 该 CompositionLocal 由上层 composition 继承，在编辑器外层提供即可对整个子树生效。
 *
 * ## 加载时机
 * - Coil 解码完成前 `painter.intrinsicSize` 为 Unspecified，此时返回 null，
 *   占位符保持预留尺寸，避免布局跳动（库的约定：返回 null 即"仍在加载或失败"）。
 * - 解码完成后返回 [ImageData]，库内部再依据容器宽度做等比钳制。
 *
 * ## 用法
 * ```kotlin
 * CompositionLocalProvider(LocalImageLoader provides CoilRichTextImageLoader) {
 *     RichTextEditor(state = richTextState)
 * }
 * ```
 */
@OptIn(ExperimentalRichTextApi::class)
object CoilRichTextImageLoader : ImageLoader {

    @Composable
    override fun load(model: Any): ImageData? {
        val painter = rememberAsyncImagePainter(model = model)
        val intrinsic = painter.intrinsicSize

        /** 尚未解码出内在尺寸时返回 null，保持占位符尺寸不变 */
        if (intrinsic.isUnspecified) return null

        return ImageData(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
