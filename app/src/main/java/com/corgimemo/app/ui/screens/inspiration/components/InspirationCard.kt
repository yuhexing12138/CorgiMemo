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
import com.corgimemo.app.ui.theme.ContentFontManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Inspiration
/** 灵感正文 → 纯文本的唯一入口（摘要/搜索/字数统计共用同一口径，v2026-09-23） */
import com.corgimemo.app.ui.screens.inspiration.InspirationTextUtils
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
    /** 本条灵感的内容字体族（每条灵感单独记字体；空 fontId=系统默认，空 latinFontId=跟随中文） */
    val inspirationFontFamily = remember(
        inspiration.fontId, inspiration.latinFontId
    ) {
        ContentFontManager.contentFontFamily(inspiration.fontId, inspiration.latinFontId)
    }
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
                /** 标题（最多1行省略）——按本条灵感记录的字体渲染（每条灵感单独记字体） */
                Text(
                    text = inspiration.title,
                    fontFamily = inspirationFontFamily,
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

            /**
             * 内容预览（最多 2 行省略）
             *
             * v2026-09-23 修正：此前用本文件私有的 `removeHtmlTags()`，它只剥 HTML 标签、
             * **不认 markdown**，导致正文里的分割线 `---` / `***`、标题 `#`、粗体 `**`、
             * `@@@CORGI_…@@@` 内部 token 原样漏进列表摘要。现统一走
             * [InspirationTextUtils.markdownToPlainText]（= `MarkdownParser.toPlainText`），
             * 与首页时间线 [TimelineInspirationItem] 保持同一口径。
             *
             * v2026-09-23 再修「摘要行距翻倍」：BlockNote 每行是一个独立段落块，导出
             * markdown 时块间以 `\n\n` 连接，而 Compose 的 `Text` 会把每个 `\n` 画成
             * 一整行空行 ⇒ `maxLines = 2` 实际只显示 1 行内容。故再叠一层
             * [InspirationTextUtils.collapseBlankLines]，口径与另外两处摘要一致：
             * **摘要里不出现任何空行**（含用户手动敲的空行）。仅作用于渲染，不写回数据。
             */
            if (inspiration.content.isNotBlank()) {
                val plainContent = InspirationTextUtils.collapseBlankLines(
                    InspirationTextUtils.markdownToPlainText(inspiration.content)
                )
                Text(
                    text = plainContent,
                    fontFamily = inspirationFontFamily,
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
                    /**
                     * 图片预览区域：
                     * 原 ImageThumbnails 函数（占位符实现）已删除，
                     * 由于 InspirationCard 组件当前未被任何页面引用，
                     * 此处不再渲染图片，避免再次引入旧的占位符实现。
                     * 如未来需恢复，可复用 TimelineInspirationItem 中的 InspirationTimelineImage 组件。
                     */
                    if (imagePaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(1.dp))
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
