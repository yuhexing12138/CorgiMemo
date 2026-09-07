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
import androidx.compose.ui.unit.dp

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
