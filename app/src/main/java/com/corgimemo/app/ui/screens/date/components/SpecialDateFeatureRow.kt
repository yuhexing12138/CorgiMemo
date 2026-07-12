package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用"功能行"组件（日期新建页专用）
 *
 * 用于"日期/类型/置顶/关联"等行布局的统一抽象。
 * 接受任意右侧内容（文本 + 箭头 / Switch / 自定义 Composable），保持 4 行视觉一致。
 *
 * @param title 左侧标题文字
 * @param modifier 外部 Modifier
 * @param trailingText 右侧文本（与 [showArrow] 配合使用；如右侧是 Switch 等自定义组件可传 null）
 * @param showArrow 是否显示右侧 › 箭头（默认 true）
 * @param onClick 整行点击回调（可空；为空时不响应点击）
 * @param trailing 自定义右侧 Composable（优先级高于 [trailingText] + [showArrow]）
 */
@Composable
fun SpecialDateFeatureRow(
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    showArrow: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    // 整行采用 Surface 卡片容器：白底 + 圆角 16dp + elevation 2dp，与参考设计保持一致
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                // 仅在传入 onClick 时才挂载点击修饰符，避免无意义的水波纹
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        // 内部行布局：左右两端对齐，水平 16dp / 垂直 12dp 内边距
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧标题：占满剩余空间，固定字号 16sp + Medium 字重
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // 右侧内容：优先使用自定义 trailing（用于 Switch 等复杂控件），
            // 未提供时回退到"文本 + 可选箭头"的轻量形态
            if (trailing != null) {
                trailing()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trailingText != null) {
                        // 右侧文本使用 onSurfaceVariant 降低视觉权重，与标题形成层级
                        Text(
                            text = trailingText,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showArrow) {
                        // 右侧 › 箭头：使用 AutoMirrored 图标，RTL 环境下自动镜像
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
