package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.FontCatalog

/**
 * 编辑页字体选择面板（内联面板，非底部弹窗）。
 *
 * 由 [InspirationEditBottomBar] 插入在「格式工具栏」与「相机行」之间：
 * 展开时把相机行向下推开（键盘让位 + 面板占位），收起时还原。
 *
 * **布局（对照已审核原型「工具栏/灵感编辑页字体选择面板.html」）**：
 * - 面板头（34dp）：左「字体」+ 当前选中名，右「完成」文字按钮（收起面板）
 * - 网格：每行固定 4 块（`GridCells.Fixed(4)`），gap 8dp，左右内边距 16dp
 * - 中文组标题「中文字体」→ 每块显示「刻记」（26sp）
 * - 拉丁组标题「英文/数字字体」→ 每块显示「Corgi」（19sp）
 * - **预览块不显示字体名**（用户决策 3）：隐藏后块高 44dp、预览字号放大，一屏看更多、
 *   更利于逐块对比字形；数据源按 [FontCatalog.entries] / [FontCatalog.latinEntries] 逐款渲染，
 *   拉丁组不含系统字体占位项（用户决策 5：默认跟随中文，见 [currentLatinId]）
 *
 * **高度（用户决策 2）**：面板高度 = 软键盘高度（由调用方经 [panelHeight] 传入，
 * 取 `WindowInsets.ime` 记录的最近一次键盘高度，键盘未弹出过时兜底 291dp），
 * 内容超出时网格纵向滚动。
 *
 * **选择语义**：
 * - 中文组：点选即选中（中文字体必选，无取消态）
 * - 拉丁组：**再点当前已选项 = 取消**，本面板直接回调空串，
 *   对应 `inspirations.latinFontId = ""`（英文/数字跟随中文字体，
   * 与 [FontCatalog.DEFAULT_LATIN_ID] 语义一致）
 *
 * @param panelHeight 面板总高度（= 键盘高度；内容区超出时纵向滚动）
 * @param currentCjkId 当前中文字体 id（回显选中态）
 * @param currentLatinId 当前英文/数字字体 id；空串 = 跟随中文（无选中高亮）
 * @param onCjkSelect 中文组点击回调（参数为字体 id）
 * @param onLatinSelect 拉丁组点击回调；再点已选项时回调**空串**表示取消（跟随中文）
 * @param onDone 点击「完成」收起面板
 */
@Composable
fun FontPickerPanel(
    panelHeight: Dp,
    currentCjkId: String,
    currentLatinId: String,
    onCjkSelect: (String) -> Unit,
    onLatinSelect: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
        ) {
            /** ---- 面板头：标题 + 当前选中名 | 完成 ---- */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    /** 40dp = TextButton 最小触摸高度，避免 34dp 容器裁切「完成」按钮 */
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "字体",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentSelectionLabel(currentCjkId, currentLatinId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDone) {
                    Text(
                        text = "完成",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            /** ---- 网格：每行 4 块，内容超出纵向滚动 ---- */
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    /** weight 占据面板头以下的全部剩余高度（有界约束，Lazy 系列必需） */
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 组标题：中文字体（占满整行）
                item(key = "title_cjk", span = { GridItemSpan(maxLineSpan) }) {
                    GroupTitle(text = "中文字体")
                }
                items(
                    items = FontCatalog.entries,
                    key = { "cjk_${it.id}" }
                ) { entry ->
                    FontPreviewCell(
                        previewText = "刻记",
                        fontFamily = entry.family,
                        fontSize = 26,
                        selected = entry.id == currentCjkId,
                        onClick = { onCjkSelect(entry.id) }
                    )
                }

                // 组标题：英文/数字字体（占满整行，含「再点已选可取消」提示）
                item(key = "title_latin", span = { GridItemSpan(maxLineSpan) }) {
                    GroupTitle(text = "英文/数字字体", hint = "再点已选可取消")
                }
                items(
                    items = FontCatalog.latinEntries,
                    key = { "latin_${it.id}" }
                ) { entry ->
                    FontPreviewCell(
                        previewText = "Corgi",
                        fontFamily = entry.family,
                        fontSize = 19,
                        selected = entry.id == currentLatinId,
                        onClick = {
                            /** 再点当前已选项 = 取消 → 回调空串（跟随中文），见类头「选择语义」 */
                            onLatinSelect(if (entry.id == currentLatinId) "" else entry.id)
                        }
                    )
                }

                // 底部留白（滚动到底时与相机行保持间距）
                item(key = "bottom_pad", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/** 当前选中的字体组合描述（面板头回显用）。 */
private fun currentSelectionLabel(cjkId: String, latinId: String): String {
    val cjkName = FontCatalog.get(cjkId).displayName
    return if (latinId.isBlank()) {
        cjkName
    } else {
        val latinName = FontCatalog.getLatin(latinId)?.displayName
        if (latinName != null) "$cjkName · $latinName" else cjkName
    }
}

/** 网格组标题（占满整行的小节标题；[hint] 可选，右对齐灰色提示）。 */
@Composable
private fun GroupTitle(text: String, hint: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hint != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * 单个字体预览块（无字体名，用户决策 3）：
 * 中文「刻记」/ 拉丁「Corgi」用该字体真实字形渲染，块高 44dp、圆角 8dp。
 * 选中态 = 暖橙边框 + 浅暖橙底（`colorScheme.primary` / `primary` 12% 透明度）。
 */
@Composable
private fun FontPreviewCell(
    previewText: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontSize: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            /** 去水波纹：indication = null 必须同时显式传 interactionSource 才生效 */
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = previewText,
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
