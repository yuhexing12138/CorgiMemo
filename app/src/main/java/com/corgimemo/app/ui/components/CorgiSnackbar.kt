package com.corgimemo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.R
import com.corgimemo.app.ui.theme.UiColors

/**
 * 柯基品牌风格 Snackbar
 *
 * 三段式布局：
 * - 左侧：圆形 APP 图标（橙色背景 + ic_launcher 柯基歪头图）
 * - 中间：浅色圆角矩形 + 提示文字
 * - 右侧：方块（撤销按钮 或 FAB 装饰）
 *
 * 设计参照：docs/superpowers/specs/2026-07-14-完成待办鼓励语改Snackbar-design.md v2 段
 *
 * @param message 提示文案
 * @param actionLabel 撤销/撤回按钮文案，null 时右侧显示 FAB 装饰
 * @param onAction actionLabel 点击回调
 * @param modifier 外部 Modifier
 */
@Composable
fun CorgiSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：圆形 APP 图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(UiColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }

            // 中间：浅色背景 + 文字
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(UiColors.PrimaryLight)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 右侧：方块 + 撤销按钮或 FAB 装饰
            val rightModifier = Modifier
                .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(UiColors.Primary)
                .let { baseModifier ->
                    if (actionLabel != null) {
                        baseModifier.clickable(enabled = onAction != null) {
                            onAction?.invoke()
                        }
                    } else {
                        baseModifier
                    }
                }
                .padding(horizontal = 10.dp)

            Box(
                modifier = rightModifier,
                contentAlignment = Alignment.Center
            ) {
                if (actionLabel != null) {
                    Text(
                        text = actionLabel,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
