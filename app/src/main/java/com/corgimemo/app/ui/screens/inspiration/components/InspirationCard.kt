package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.theme.UiColors

/**
 * 灵感卡片组件
 * 展示单条灵感记录的标题、内容预览、缩略图、标签和时间信息
 * 支持点击和长按回调，用于时间线列表展示
 *
 * @param inspiration 灵感实体数据
 * @param tags 解析后的标签列表
 * @param imagePaths 解析后的图片路径列表
 * @param formattedTime 格式化后的时间字符串（如 "14:30"）
 * @param onClick 点击回调（进入编辑页）
 * @param onLongClick 长按回调（显示操作菜单）
 * @param modifier 修饰符
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun InspirationCard(
    inspiration: Inspiration,
    tags: List<String>,
    imagePaths: List<String>,
    formattedTime: String,
    relationHint: String? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            /** 标题行：标题 + 时间 */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                /** 标题（最多1行省略） */
                Text(
                    text = inspiration.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                /** 时间（灰色小字） */
                Text(
                    text = formattedTime,
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            /** 内容预览（去除HTML标签，最多2行省略） */
            if (inspiration.content.isNotBlank()) {
                val plainContent = removeHtmlTags(inspiration.content)
                Text(
                    text = plainContent,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            /** 底部区域：图片预览 + 标签 */
            if (imagePaths.isNotEmpty() || tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    /** 缩略图预览（32×32dp，圆角8dp，最多显示2张） */
                    if (imagePaths.isNotEmpty()) {
                        ImageThumbnails(imagePaths = imagePaths)
                    } else {
                        Spacer(modifier = Modifier.height(1.dp))
                    }

                    /** 关联提示 */
                    if (relationHint != null) {
                        Text(
                            text = relationHint,
                            fontSize = 12.sp,
                            color = Color(0xFF999999),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    /** 标签列表（暖橙色背景，胶囊圆角20dp） */
                    if (tags.isNotEmpty()) {
                        TagChips(tags = tags, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 图片缩略图组件
 * 显示灵感关联的图片缩略图，最多显示2张，多余显示"+N"
 *
 * @param imagePaths 图片路径列表
 * @param modifier 修饰符
 */
@Composable
private fun ImageThumbnails(
    imagePaths: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        /** 显示前2张图片的占位框 */
        imagePaths.take(2).forEach { _ ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🖼️",
                    fontSize = 14.sp
                )
            }
        }

        /** 如果超过2张，显示多余数量 */
        if (imagePaths.size > 2) {
            val extraCount = imagePaths.size - 2
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extraCount",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}

/**
 * 标签芯片组件
 * 横向显示标签列表，使用暖橙色背景样式
 *
 * @param tags 标签文本列表
 * @param modifier 修饰符
 */
@Composable
private fun TagChips(
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.padding(start = 8.dp)
    ) {
        tags.take(3).forEach { tag ->
            Text(
                text = tag,
                fontSize = 12.sp,
                color = UiColors.Primary,
                modifier = Modifier
                    .background(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        /** 如果超过3个标签，显示多余数量 */
        if (tags.size > 3) {
            Text(
                text = "+${tags.size - 3}",
                fontSize = 12.sp,
                color = Color(0xFF999999),
                modifier = Modifier
                    .background(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 去除HTML标签工具函数
 * 将富文本内容中的HTML标签剥离，返回纯文本
 *
 * @param html 包含HTML标签的字符串
 * @return 纯文本字符串
 */
private fun removeHtmlTags(html: String): String {
    return html
        .replace("<[^>]*>".toRegex(), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()
}
