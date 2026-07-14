package com.corgimemo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.R
import com.corgimemo.app.ui.theme.UiColors

/**
 * 柯基品牌风格 Snackbar Host
 *
 * 统一全项目 9 处 snackbarHost 槽位，单段式布局：
 * - 左侧：柯基图（直接 Image，无 Box 背景包裹）
 * - 中间：提示文字（maxLines=1 + Ellipsis）
 * - 右侧：Material 3 原生 TextButton（仅当有 actionLabel 时显示）
 *
 * 容器宽度自适应：跟随内容宽度，最大 560dp（Material 3 推荐）。
 * - 无按钮时：图标 + 文字整体居中
 * - 有按钮时：文字靠左（weight=1f）占满剩余空间，按钮靠右
 *
 * 设计参照：
 * - docs/superpowers/specs/2026-07-14-Snackbar格式重设计-design.md
 * - docs/superpowers/specs/2026-07-14-Snackbar体验优化-design.md
 *
 * @param hostState Snackbar 状态
 * @param modifier 外部 Modifier（用于偏移，如 InspirationImageGallery 离底 80dp）
 */
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    // SnackbarHost 默认 fillMaxWidth()，外层 Box 配合 widthIn 实现宽度自适应
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        SnackbarHost(
            hostState = hostState,
            // 关键：移除默认的 fillMaxWidth，让 SnackbarHost 内部按内容宽度渲染
            modifier = Modifier.widthIn(max = SnackbarMaxWidth)
        ) { data ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                val hasAction = data.visuals.actionLabel != null
                Row(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        // 带按钮时垂直内边距稍大，让按钮文字与图标垂直居中更协调
                        vertical = if (hasAction) 6.dp else 8.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // 左侧：柯基图（直接引用 PNG，无 Box/圆圈背景包裹）
                    // 资源使用 R.drawable.corgi_tilt_2frames_01（绕开 ic_launcher.xml 的 <bitmap> 包装问题）
                    Image(
                        painter = painterResource(id = R.drawable.corgi_tilt_2frames_01),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    // 中间：提示文字（一行内省略号截断）
                    // 带按钮时 weight=1f 占满剩余空间（靠左对齐），无按钮时不带 weight 自然靠左
                    val textModifier = if (hasAction) {
                        Modifier.weight(1f, fill = false)
                    } else {
                        Modifier
                    }
                    Text(
                        text = data.visuals.message,
                        modifier = textModifier,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 右侧：Material 3 原生按钮（仅当有 actionLabel 时显示）
                    data.visuals.actionLabel?.let { label ->
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { data.performAction() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = UiColors.Primary
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 0.dp
                            )
                        ) {
                            Text(
                                text = label,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Snackbar 最大宽度（Material 3 推荐 560dp-640dp 范围） */
private val SnackbarMaxWidth = 560.dp
