// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationViewCard.kt
package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.corgimemo.app.R
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.screens.inspiration.InspirationTextUtils
import com.corgimemo.app.ui.theme.UiColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 灵感展示页卡片内容
 *
 * 渲染单条灵感的完整内容：标题、日期时间、正文、图片、标签、字数徽章、Logo
 * 不包含 TopBar 和 HorizontalPager 容器（由父级 InspirationViewScreen 负责）
 *
 * 截图说明：分享截图由父级 InspirationViewScreen 通过
 * `InspirationScreenshot.captureAsBitmap` 对当前 page 的 GraphicsLayer 截图完成（位图放大 2x）。
 * 本组件支持传入可选的 GraphicsLayer，启用 Card 内容的录制。
 *
 * @param inspiration 灵感实体
 * @param onImageClick 图片点击回调，参数为图片索引
 * @param graphicsLayer 可选的 GraphicsLayer（启用时录制 Card 内容，用于截图分享）
 * @param modifier Modifier（用于外部控制尺寸、padding 等）
 */
@Composable
fun InspirationViewCard(
    inspiration: Inspiration,
    onImageClick: (Int) -> Unit = {},
    graphicsLayer: GraphicsLayer? = null,
    modifier: Modifier = Modifier
) {
    // 缓存：标签列表
    val tagsList = remember(inspiration.tags) { InspirationTextUtils.parseTags(inspiration.tags) }
    // 缓存：图片路径列表
    val imagePaths = remember(inspiration.imagePaths) {
        if (inspiration.imagePaths.isBlank()) emptyList()
        else try {
            org.json.JSONArray(inspiration.imagePaths).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (e: Exception) { emptyList() }
    }
    // 缓存：字数（依赖标题/正文/标签变化）
    val charCount = remember(inspiration.id, inspiration.title, inspiration.content, inspiration.tags) {
        InspirationTextUtils.countInspirationChars(inspiration)
    }
    // 缓存：格式化日期
    val formattedDate = remember(inspiration.createdAt) {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(inspiration.createdAt))
    }

    Box(
        modifier = modifier
    ) {
        // 卡片主体
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)  // 18dp → 10dp：更紧凑
                // 关键：把 drawWithContent 放到 Card 上，使 GraphicsLayer 录制的尺寸
                // 就是 Card 实际尺寸（白色卡片+圆角+阴影），不会录制到外层 Box 的空白区域
                .then(
                    if (graphicsLayer != null) {
                        Modifier.drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        }
                    } else Modifier
                ),
            shape = RoundedCornerShape(12.dp),  // 20dp → 12dp：更小更精致
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            // Card 内层 Box：用于字数徽章自由贴边定位
            Box(modifier = Modifier.fillMaxWidth()) {
                // 内容 Column：标题、日期、正文、图片、标签、Logo
                // 顶部 padding 36dp 留白给右上角的字数徽章
                // 加 verticalScroll 允许内容超出时上下滚动（图片多时）
                Column(
                    modifier = Modifier
                        .padding(start = 18.dp, end = 18.dp, top = 36.dp, bottom = 18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 标题（18sp Medium）
                    Text(
                        text = inspiration.title,
                        fontSize = 18.sp,  // 16sp → 18sp
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // 日期时间（12sp 灰色）
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,  // 11sp → 12sp
                        color = Color(0xFF999999),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                    // 正文（15sp，行高 22sp，#666666）
                    Text(
                        text = inspiration.content,
                        fontSize = 15.sp,  // 14sp → 15sp
                        color = Color(0xFF666666),
                        lineHeight = 22.sp,  // 21sp → 22sp
                        letterSpacing = 0.5.sp
                    )
                    // 图片列表（如果有）
                    // 竖向排列：每张图片占一行，宽度与灵感正文一致（fillMaxWidth），
                    // 高度完全按原图比例计算（不限制最大高度）
                    if (imagePaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            imagePaths.forEachIndexed { index, path ->
                                ImageWithRatio(
                                    path = path,
                                    onClick = { onImageClick(index) }
                                )
                            }
                        }
                    }
                    // 标签（最多显示 5 个）
                    if (tagsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tagsList.take(5).forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color(0xFFFFF3E0),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        fontSize = 11.sp,
                                        color = UiColors.Primary,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                    // Logo 居中区
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = Color(0xFF999999),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.inspiration_view_logo_text),  // 从字符串资源读取
                            fontSize = 13.sp,
                            color = Color(0xFF999999),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                // 字数徽章：右上角贴边（距离卡片右边缘、距离顶均为 0）
                // 圆角设计：左上/右上/右下 = 0（无圆角），左下 = 12dp（与 Card 圆角一致）
                // Card 12dp 圆角自然裁剪徽章右上角，徽章与 Card 边完美融合
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            Color(0xFFE0E0E0).copy(alpha = 0.6f),
                            RoundedCornerShape(
                                topStart = 0.dp,       // 左上 0：与 Card 顶/左边内边距接触
                                topEnd = 0.dp,         // 右上 0：贴 Card 顶/右边
                                bottomEnd = 0.dp,      // 右下 0：贴 Card 右边
                                bottomStart = 12.dp    // 左下 12dp：与 Card 圆角一致
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${charCount}字",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666)
                    )
                }
            }
        }
    }
}

/**
 * 按原图比例显示的竖向图片项
 *
 * - 宽度严格撑满（fillMaxWidth），与灵感正文宽度一致
 * - 高度 = 宽度 / 原图宽高比（不设最大高度限制，按原比例完整显示）
 * - 使用 ContentScale.Crop 让图片填满 Box（不留白），保证宽度一致
 * - 使用 SubcomposeAsyncImage 在 lambda 内读取 painter.intrinsicSize
 *   获取真实宽高比
 *
 * @param path 图片路径
 * @param onClick 点击图片回调
 */
@Composable
private fun ImageWithRatio(
    path: String,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    SubcomposeAsyncImage(
        model = coil3.request.ImageRequest.Builder(context)
            .data(path)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        val painter = this.painter
        val intrinsicSize = painter.intrinsicSize
        if (intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val containerWidth = maxWidth
                val aspectRatio = intrinsicSize.width / intrinsicSize.height
                // 高度 = 容器宽度 / 宽高比，完全按原图比例显示，不限制最大高度
                val finalHeight = containerWidth / aspectRatio
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(finalHeight)
                )
            }
        } else {
            // painter 尚未就绪（loading 状态），显示占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
            )
        }
    }
}
