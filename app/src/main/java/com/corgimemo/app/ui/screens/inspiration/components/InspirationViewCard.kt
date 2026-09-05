// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationViewCard.kt
package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.corgimemo.app.ui.theme.ContentFontManager
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.R
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.components.LinkedCardsRow
import com.corgimemo.app.ui.screens.inspiration.InspirationTextUtils
import com.corgimemo.app.ui.theme.UiColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 灵感展示页卡片内容
 *
 * 渲染单条灵感的完整内容：标题、日期时间、正文、图片、标签、关联卡片、字数徽章、Logo
 * 不包含 TopBar 和 HorizontalPager 容器（由父级 InspirationViewScreen 负责）
 *
 * 截图说明：分享截图由父级 InspirationViewScreen 通过
 * `InspirationScreenshot.captureAsBitmap` 对当前 page 的 GraphicsLayer 截图完成（位图放大 2x）。
 * 本组件支持传入可选的 GraphicsLayer，启用 Card 内容的录制。
 *
 * v2026-07-22 新增：支持在标签下方显示关联卡片 Chip 流（类似待办编辑页），
 * 由父级传入 [relations] / [relationTitles] 和三个回调。
 *
 * v2026-08-24 修复灵感图片不可见 bug：
 * - 灵感图片存储在 `content_blocks` 表（ownerType="inspiration"），
 *   [Inspiration.imagePaths] 字段已置空
 * - 移除原内部 `remember(inspiration.imagePaths) { ... JSONArray 解析 ... }`
 * - 改为由父级（[com.corgimemo.app.ui.screens.inspiration.InspirationViewScreen]）
 *   通过 [imagePaths] 参数传入真实图片路径列表（来自 ViewModel.imagePathsMap）
 * - 默认值为 `emptyList()`，保证旧调用方（如离屏截图 [InspirationScreenshot]）不传时仍可编译
 *
 * @param inspiration 灵感实体
 * @param onImageClick 图片点击回调，参数为图片索引
 * @param imagePaths 灵感图片路径列表（v2026-08-24 新增；由调用方从 content_blocks 表加载）
 * @param graphicsLayer 可选的 GraphicsLayer（启用时录制 Card 内容，用于截图分享）
 * @param relations 关联卡片列表（默认空，详情页传入以显示 Chip 流）
 * @param relationTitles 关联ID → 标题映射（由 ViewModel 异步加载）
 * @param onChipClick Chip 点击回调（弹出预览 Dialog）
 * @param onChipDelete Chip × 删除回调
 * @param onAddRelationClick ＋ 添加按钮点击回调
 * @param modifier Modifier（用于外部控制尺寸、padding 等）
 */
@Composable
fun InspirationViewCard(
    inspiration: Inspiration,
    onImageClick: (Int) -> Unit = {},
    /**
     * 灵感图片路径列表（v2026-08-24 新增）
     *
     * 由父级从 [com.corgimemo.app.viewmodel.InspirationViewModel.imagePathsMap]
     * 读取（数据源为 `content_blocks` 表，ownerType="inspiration"）。
     * 旧实现中本组件从 [Inspiration.imagePaths] 字段解析，但该字段在保存时被置空。
     */
    imagePaths: List<String> = emptyList(),
    graphicsLayer: GraphicsLayer? = null,
    relations: List<CardRelation> = emptyList(),
    relationTitles: Map<Long, String> = emptyMap(),
    onChipClick: (CardRelation) -> Unit = {},
    onChipDelete: (relationId: Long, groupId: Int) -> Unit = { _, _ -> },
    onAddRelationClick: () -> Unit = {},
    /**
     * v2026-08-30 新增：内部堆叠图拖动状态回调（true=正在拖动卡片）。
     * 父级（HorizontalPager）可用此回调临时禁用同级水平手势，
     * 避免双层手势竞争导致「卡一下再回底」。
     * 默认空实现，不影响其他调用方。
     */
    onImageStackDragStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 缓存：标签列表
    val tagsList = remember(inspiration.tags) { InspirationTextUtils.parseTags(inspiration.tags) }
    /** 本条灵感的内容字体族（每条灵感单独记字体；空 fontId=系统默认，空 latinFontId=跟随中文） */
    val inspirationFontFamily = remember(
        inspiration.fontId, inspiration.latinFontId
    ) {
        ContentFontManager.contentFontFamily(inspiration.fontId, inspiration.latinFontId)
    }
    // v2026-08-24 修复灵感图片不可见 bug：
    // - 移除原 `remember(inspiration.imagePaths) { org.json.JSONArray(...) }` 解析逻辑
    // - 改为直接使用父级传入的 [imagePaths] 参数
    // - 因 imagePaths 已是 List<String>，无需再解析
    // 缓存：字数（v2026-07-31 改造：只统计正文字符数，不含标题/标签/关联）
    val charCount = remember(inspiration.id, inspiration.content) {
        InspirationTextUtils.countInspirationContentChars(inspiration.content)
    }
    // 缓存：格式化日期
    val formattedDate = remember(inspiration.createdAt) {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(inspiration.createdAt))
    }

    Box(
        modifier = modifier
            // 截图录制：drawWithContent 放最外层 Box，录制内容 = 卡面背景 + 内容层
            .then(
                if (graphicsLayer != null) {
                    Modifier.drawWithContent {
                        // 关键：录制层 clip=false —— GraphicsLayer 默认 clip=true，
                        // 会把 InspirationDetailImageStack 拖拽溢出的顶卡裁掉。
                        // 关闭后录制层与实显都不裁剪。
                        graphicsLayer.clip = false
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
                } else Modifier
            )
            // v2026-08-30 修复：最外层 Box 自身 RenderNode 默认 clip=true，
            // 会裁掉 InspirationDetailImageStack 拖出的顶卡。关闭后允许内容一路溢出到
            // Pager viewport / 屏幕边界（配合内部各层 clip=false 形成完整不裁剪链）。
            .graphicsLayer { this.clip = false }
    ) {
        // v2026-08-30 重构（v12）：卡片主体 Box——高度随内容（wrap），恢复卡片下边缘。
        // v8 曾把卡面 fillMaxSize 撑满 page 全高（当时为防默认 graphicsLayer 裁剪的过度设计），
        // 导致卡片高度固定 = 屏高、下边缘被推到屏幕外（用户反馈「下边缘看不见」）。
        // 现在：本 Box fillMaxWidth + wrap 高度，高度由内容 Column 决定；
        // 卡面用 matchParentSize 跟随本 Box 尺寸（matchParentSize 不参与父测量）。
        // 拖拽裁剪链与本 Box 无关（卡片在 Column 内，经根 Box→Pager 兜底），无需撑满。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { this.clip = false }
        ) {
            // ① 白色卡面背景：matchParentSize 跟随卡片主体尺寸（= 内容 Column 高度），
            // background/shadow 绘制在 padding 后的局部坐标（水平屏 28~362dp）→ 卡面留白
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 28.dp)
                    // 投影：4dp 阴影（与原 Card elevation 对齐），clip = false 让阴影 RenderNode 不裁剪
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false
                    )
                    // 白色卡面 + 12dp 圆角（与原 Card shape 对齐；background 只裁背景自身，不裁子内容）
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .graphicsLayer { this.clip = false }
            )
                // 内容 Column：标题、日期、正文、图片、标签、Logo
                // 顶部 padding 36dp 留白给右上角的字数徽章
                // 加 verticalScroll 允许内容超出时上下滚动（图片多时）
                Column(
                    modifier = Modifier
                        // v2026-08-30 修复（v11/v12）：padding 按轴拆分 + 高度随内容。
                        // fillMaxWidth（不 fillMaxSize）：Column 高度 = 内容高度（内容不超屏时
                        // 卡片高度随内容、下边缘可见；超屏时 = 屏高进入滚动）。
                        // VerticalScrollableClipShape 源码（foundation 1.11）：
                        //   横轴（交叉轴）= bounds ± 30dp 放宽；纵轴（滚动主轴）= 严格 bounds。
                        //   垂直 padding(top=36, bottom=18) 在 verticalScroll 外层 → 滚动区垂直
                        //     固定留白，内容滚动上/下边缘与 v9 一致；
                        //   水平 padding(46) 在 verticalScroll 内层 → verticalScroll 水平 bounds =
                        //     全宽 0~390dp，clip 水平 = −30~420dp → 拖拽图片到屏边缘不被裁。
                        .fillMaxWidth()
                        .padding(top = 36.dp, bottom = 18.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 46.dp, end = 46.dp)
                        // v2026-08-30 修复：Column 自身 RenderNode 默认 clip=true，会
                        // 把 InspirationDetailImageStack 拖出 Column 边界的顶卡裁掉。关闭后
                        // 允许 Stage 卡片溢出绘制，形成完整不裁剪链。
                        .graphicsLayer { this.clip = false }
                ) {
                    // 标题（18sp Medium）——按本条灵感记录的字体渲染
                    Text(
                        text = inspiration.title,
                        fontFamily = inspirationFontFamily,
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
                    // 正文（15sp，行高 22sp，#666666）——按本条灵感记录的字体渲染
                    // v2026-09-05 修复「字号/字体颜色设置后丢失」：改用只读 [RichText] 渲染
                    // 富文本 [Inspiration.contentFormat]（含逐字 fontSize/color 内联 span），
                    // 使编辑页设置的排版在详情页同样生效。基础样式与改造前纯 Text 一致，
                    // 未设置排版的字符回落基础样式；旧记录 contentFormat 为空时回退纯文本 content。
                    InspirationBodyRichText(
                        contentFormat = inspiration.contentFormat,
                        fallbackContent = inspiration.content,
                        fontFamily = inspirationFontFamily,
                    )
                    // 图片区（如果有）
                    // v2026-08-29 改造：默认堆叠展示，点「展开 N」按钮向下展开为图片列并恢复
                    // 图片原始宽高比；点图片本身则进入图片附件页（不触发展开/收起）。
                    // 图片区宽 = 内容宽，由父级 Column 的 start/end = 18dp 内边距决定，
                    // 组件内部**不再**额外叠加左右内边距，避免出现双重留白。
                    if (imagePaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        InspirationDetailImageStack(
                            imagePaths = imagePaths,
                            onImageClick = onImageClick,
                            onDragStateChange = onImageStackDragStateChange
                        )
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
                    // v2026-07-22 新增：关联卡片 Chip 流（类似待办编辑页）
                    // 始终显示，让用户可以查看关联和点击 + 添加新关联
                    Spacer(modifier = Modifier.height(12.dp))
                    LinkedCardsRow(
                        relations = relations,
                        groupId = 0,
                        relationTitles = relationTitles,
                        onAddClick = onAddRelationClick,
                        onChipClick = onChipClick,
                        onChipDelete = onChipDelete
                    )
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
                //
                // v2026-07-31 字数统计规则：只统计正文字符数（去除空白），与
                // 灵感编辑页"标题和正文之间"字数行保持一致——**不包含标题、标签、关联卡片**。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        // v2026-08-30 修复（v12）：align 相对卡片主体 Box（宽 = 全宽），
                        // 往左退回卡面右缘（卡面 28dp 边距），贴卡面右上角
                        .padding(end = 28.dp)
                        .background(
                            Color(0xFFE0E0E0).copy(alpha = 0.6f),
                            RoundedCornerShape(
                                topStart = 0.dp,       // 左上 0：与 Card 顶/左边内边距接触
                                // v2026-08-30 修复（v14）：右上改 12dp 圆角。
                                // 原 0dp 直角设计依赖父级 Material3 Card 裁掉突出圆角的部分；
                                // v2 起父级全部 clip=false 无人裁 → 直角部分悬在卡面圆角外，
                                // 半透明背景透出卡面阴影（「角标位置多出一点阴影」）。
                                // 自带 12dp 圆角与卡面圆角贴合后不再悬空。
                                topEnd = 12.dp,
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

/**
 * 灵感详情页正文渲染：用只读 [RichText] 展示富文本 [contentFormat]（含逐字字号 / 字体颜色
 * 内联 span），使编辑页设置的排版在详情页同样持久生效。
 *
 * **修复背景**：库旧版 markdown 编码器在 `toMarkdown()` 时丢弃 `SpanStyle.fontSize` 与
 * `SpanStyle.color`，仅保留字重；故此前「编辑页设的字号/颜色」无法落库、再进入即丢失。
 * 现版本编码器已将这两项以 `<span style="font-size:Npx;color:#RRGGBB">` 内联往返保留，
 * 详情页只需改用 [RichText] 渲染 [contentFormat] 即可还原。
 *
 * **v2026-09-05 二次修复「段间多出空行」**：库把 markdown 段落分隔 `\n\n` 解码为一个
 * **可见空段**（`[a, b]` ⇄ `a\n\nb` 双向自洽的库内语义），而编辑页 `BodyBlocksEditor`
 * 加载时按 `\n\n` 拆成单段落块、空段丢弃——两边渲染不一致。故详情页**镜像编辑页的分段**：
 * 复用 [parseMarkdownSegments] 切段、Text 段按 `\n\n` 拆段逐段渲染（段间零间距=相邻行），
 * 保证编辑页看到的换行间距与详情页一致。图片段跳过（由下方 InspirationDetailImageStack
 * 统一渲染，避免 markdown 内联图片与图片堆叠重复显示）。
 *
 * @param contentFormat 富文本 Markdown（由编辑页 `RichTextState.toMarkdown()` 导出）。
 * @param fallbackContent 旧记录 `contentFormat` 为空时的纯文本回退（按改造前的纯 Text
 *   渲染，不喂给 markdown 解析，避免旧文本里的 `*` 等字符被误当语法）。
 * @param fontFamily 本条灵感记录的字体族（与标题、编辑页一致）。
 */
@Composable
private fun InspirationBodyRichText(
    contentFormat: String,
    fallbackContent: String,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    if (contentFormat.isEmpty()) {
        // 旧记录无富文本（contentFormat 未迁移）：保持改造前的纯 Text 渲染，
        // 不走 markdown 解析（旧纯文本可能含 `*` 等字符，解析会误判为语法）。
        Text(
            text = fallbackContent,
            fontFamily = fontFamily,
            fontSize = 15.sp,
            color = Color(0xFF666666),
            lineHeight = 22.sp,
            letterSpacing = 0.5.sp
        )
        return
    }

    // 与编辑页 initialize 同源的分段：图片段独立（此处跳过），Text 段按 \n\n 拆段。
    // 每段一个 RichText、Column 零间距堆叠 → 视觉上与编辑页的相邻段落块一致。
    val paragraphs = remember(contentFormat) {
        parseMarkdownSegments(contentFormat)
            .filterIsInstance<MdSegment.TextSeg>()
            .flatMap { it.md.split("\n\n") }
            .map { it.trim('\n') }
            .filter { it.isNotEmpty() }
    }
    Column(modifier = modifier) {
        paragraphs.forEach { para ->
            InspirationBodyParagraph(
                markdown = para,
                fontFamily = fontFamily,
            )
        }
    }
}

/**
 * 详情页正文单段渲染：把单段 markdown 解析进独立 [RichTextState] 后用只读 [RichText] 展示。
 *
 * 基础样式（15sp / #666666 / 行高 22sp / 字距 0.5sp）与改造前纯 Text 一致；
 * 段内逐字 span 的 fontSize/color 覆盖基础值，未设置处回落基础样式。
 * 空白块占位段（NBSP）会渲染为一行空白，与编辑页空白块语义一致。
 *
 * @param markdown 单段 markdown（不含 `\n\n` 段落分隔）。
 * @param fontFamily 本条灵感记录的字体族。
 */
@Composable
private fun InspirationBodyParagraph(
    markdown: String,
    fontFamily: FontFamily,
) {
    val richTextState = rememberRichTextState()
    LaunchedEffect(markdown) {
        // setMarkdown 非 suspend，直接调用；状态变更后 RichText 自动重组渲染。
        richTextState.setMarkdown(markdown)
    }
    RichText(
        state = richTextState,
        // 基础样式与改造前纯 Text 完全一致：未设置排版的字符回落下列值，
        // 已设 fontSize/color 的字符以 span 内联值为准（覆盖基础样式）。
        fontFamily = fontFamily,
        fontSize = 15.sp,
        color = Color(0xFF666666),
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp,
    )
}
