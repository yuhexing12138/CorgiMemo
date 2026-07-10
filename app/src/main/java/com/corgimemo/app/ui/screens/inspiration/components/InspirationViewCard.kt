// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationViewCard.kt
package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
 * @param inspiration 灵感实体
 * @param onImageClick 图片点击回调，参数为图片索引
 * @param graphicsLayer 可选的 GraphicsLayer（用于 Task 11 截图）
 * @param modifier Modifier（用于外部控制尺寸、padding、graphicsLayer 录制等）
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
        // 可选：将卡片内容录制到 GraphicsLayer（用于截图分享）
        modifier = modifier.then(
            if (graphicsLayer != null) {
                Modifier.drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                }
            } else Modifier
        )
    ) {
        // 卡片主体
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 标题（16sp Medium）
                Text(
                    text = inspiration.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 日期时间（11sp 灰色）
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = Color(0xFF999999),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(9.dp))
                // 正文（14sp，行高 21sp，#666666）
                Text(
                    text = inspiration.content,
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    lineHeight = 21.sp,
                    letterSpacing = 0.5.sp
                )
                // 图片列表（如果有）
                if (imagePaths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(imagePaths.size) { index ->
                            val path = imagePaths[index]
                            AsyncImage(
                                model = path,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onImageClick(index) }
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
                        text = "- 简记事 -",
                        fontSize = 13.sp,
                        color = Color(0xFF999999),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // 字数徽章（卡片右上角，胶囊形状）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 28.dp)
                .background(
                    Color(0xFFE0E0E0).copy(alpha = 0.6f),
                    RoundedCornerShape(12.dp)
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
