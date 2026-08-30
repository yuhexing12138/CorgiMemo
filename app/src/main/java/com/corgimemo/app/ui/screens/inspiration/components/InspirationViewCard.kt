// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationViewCard.kt
package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    ) {
        // 卡片主体
        // v2026-08-29 改造：原 Material3 Card 内部 Surface 会用 Modifier.clip 裁掉子内容，
        // 导致 InspirationDetailImageStack 顶卡拖出卡片范围时被裁（出现「卡一下回到底部」
        // 的现象）。改用 Box + shadow + background 复刻视觉，Box 本身不裁剪子内容。
        // 关键点 1：drawWithContent 录制层 GraphicsLayer 默认 clip=true，会把拖拽溢出的
        //  顶卡裁掉，必须在录制前显式 graphicsLayer.clip = false。
        // 关键点 2：drawWithContent 放在 shadow/background 之前（最外层 Draw 修饰），
        //  录制内容 = 阴影 + 白色卡面 + 圆角 + 全部子内容，与截图分享所需一致。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)  // 18dp → 10dp：更紧凑
                // 截图录制：drawWithContent 放在 shadow/background 之前（最外层 Draw 修饰），
                // 使 GraphicsLayer 录制的内容 = 阴影 + 白色卡面 + 圆角 + 全部子内容，
                // 不会录制到外层 Box 的空白区域。
                .then(
                    if (graphicsLayer != null) {
                        Modifier.drawWithContent {
                            // 关键：录制层 clip=false —— GraphicsLayer 默认 clip=true，
                            // 会把 InspirationDetailImageStack 拖拽溢出的顶卡裁掉（重新引入
                            // 「卡片在详情卡片边界被裁」的问题）。关闭后录制层与实显都不裁剪。
                            graphicsLayer.clip = false
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        }
                    } else Modifier
                )
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
        ) {
            // 内层 Box：用于字数徽章自由贴边定位
            Box(modifier = Modifier.fillMaxWidth()) {
                // 内容 Column：标题、日期、正文、图片、标签、Logo
                // 顶部 padding 36dp 留白给右上角的字数徽章
                // 加 verticalScroll 允许内容超出时上下滚动（图片多时）
                Column(
                    modifier = Modifier
                        .padding(start = 18.dp, end = 18.dp, top = 36.dp, bottom = 18.dp)
                        .verticalScroll(rememberScrollState())
                        // v2026-08-30 修复：Column 自身 RenderNode 默认 clip=true，会
                        // 把 InspirationDetailImageStack 拖出 Column 边界的顶卡裁掉（出现
                        // 「卡在 Column 左缘」的现象）。关闭后允许 Stage 卡片溢出 Column
                        // padding 范围绘制，与外层 Box（已 clip=false）形成完整不裁剪链。
                        .graphicsLayer { this.clip = false }
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
