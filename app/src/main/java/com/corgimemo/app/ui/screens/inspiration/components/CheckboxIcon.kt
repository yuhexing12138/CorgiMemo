package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 列表 marker 前缀判定（v2026-09-07）：`- ` / `* ` / `+ ` / `1. ` / `1) `，允许前导缩进空格。
 * 用于判断复选框段的内容 markdown 是否同时是列表项（决定按列表层级还是纯文本层级算缩进）。
 */
private val CheckboxListMarkerRegex = Regex("^\\s*([-*+]|\\d+[.)])\\s")

/**
 * 复选框标识的**跟随缩进宽度**（v2026-09-07）：点击「增加 / 减少缩进」时，
 * 文本段的缩进由库以**段落样式 TextIndent** 实现（整段左移），而复选框标识是
 * Row 内的独立组件、不参与文本排版 → 用本函数换算等宽偏移加到复选框上，
 * 使标识与文本**同步位移、两者间距保持不变**。
 *
 * **每级宽度 = [perLevelSp]（默认 [LIST_LEVEL_INDENT_SP] = 30sp）**，依据库的真实公式：
 * - 纯文本段（`DefaultParagraph`）：`TextIndent(firstLine = restLine = indent × (level-1))`，
 *   步长 `indent = config.orderedListIndent`（App 配 [LIST_LEVEL_INDENT_SP]）；
 * - 列表段（`OrderedList` / `UnorderedList`）：同为 `base = indent × (level-1)`。
 *
 * ⚠️ 坑（v2026-09-07 首版踩过）：**不能用段首全角空格（U+2003）个数 × 字号估算**——
 * 那两个 EM 空格只是 **markdown 持久化载体**（编码端输出 / 解码端剥除还原 level），
 * 并非渲染出来的字符，与字号无关；按字号估算会与文本实际缩进量不等，缩进层级
 * 越深偏差越大（表现为复选框与文本间距逐渐变化）。
 *
 * @param bodyMarkdown 复选框段**剥掉复选框前缀后**的内容 markdown
 *   （编辑页 = 块 markdown；详情页 = 段落去掉 `- [ ] ` / `- [x] ` 后的正文）
 * @param perLevelSp 每级缩进宽度（sp）：编辑页传块的 `state.config.orderedListIndent`
 *   以严格对齐库实际值；详情页默认 [LIST_LEVEL_INDENT_SP]（与详情页 config 一致）
 * @return 复选框应右移的宽度（一级无缩进返回 0dp）
 */
@Composable
internal fun checkboxIndentDp(bodyMarkdown: String, perLevelSp: Int = LIST_LEVEL_INDENT_SP): Dp {
    val isList = CheckboxListMarkerRegex.containsMatchIn(bodyMarkdown)
    val level = if (isList) listLevelOfMd(bodyMarkdown) else plainIndentLevelOfMd(bodyMarkdown)
    if (level <= 1) return 0.dp
    return with(LocalDensity.current) { ((level - 1) * perLevelSp).sp.toDp() }
}

/**
 * 复选框标识（v2026-09-07）：编辑页块级复选框（BlockTextItem）与详情页正文
 * 复选框段（InspirationViewCard）共用的视觉组件。
 *
 * 视觉（与确认截图一致，填充色经用户确认为主题暖橙）：
 * - 未勾选：18dp 圆角(5dp)方框，1.5dp 描边（onSurfaceVariant 灰调，与图二一致）；
 * - 已勾选：主题 primary（#FF9A5C）填充圆角方框 + 白色对勾，文字由调用方做
 *   视觉降级（本组件只负责框体）；
 * - 交互：[onClick] 非空时整框可点击（去水波纹——`indication = null` 必须显式传
 *   `interactionSource`，否则不生效）；null 时不可点击（详情页截图离屏渲染等
 *   只读场景）。
 *
 * @param checked 是否处于勾选态
 * @param onClick 点击回调；null = 不可点击（只读）
 * @param modifier 外部布局参数（调用方用 padding 微调与第一行文字的对齐）
 */
@Composable
internal fun CheckboxBoxIcon(
    checked: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    /** 框体形状：圆角 5dp（视觉与参考截图一致） */
    val shape = RoundedCornerShape(5.dp)

    Box(
        modifier = modifier
            .size(18.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            /** 勾选态：主题 primary 填充 + 白色对勾（Icons.Default.Check） */
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.primary, shape = shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(14.dp),
                )
            }
        } else {
            /** 未勾选态：1.5dp 描边圆角方框（onSurfaceVariant 灰调） */
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = shape,
                    ),
            )
        }
    }
}
