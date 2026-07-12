package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 灵感长按操作底部面板
 * 包含四个操作选项：置顶、标签、改日期、删除
 *
 * @param isPinned 当前灵感是否已置顶
 * @param onPinClick 置顶/取消置顶点击回调
 * @param onTagClick 标签点击回调
 * @param onDateClick 改日期点击回调
 * @param onDeleteClick 删除点击回调
 * @param onDebugClick 调试输出 imagePaths 点击回调（仅 DEBUG 模式显示）
 * @param onDismiss 关闭回调
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InspirationLongPressSheet(
    isPinned: Boolean,
    onPinClick: () -> Unit,
    onTagClick: () -> Unit,
    onDateClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDebugClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            // 顶部指示条
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = Color(0xFFDDDDDD),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // 选项1：置顶/取消置顶
            LongPressOptionItem(
                icon = "📌",
                text = if (isPinned) "取消置顶" else "置顶",
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onPinClick()
                    }
                }
            )

            // 分割线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFEEEEEE))
                    .padding(horizontal = 16.dp)
            )

            // 选项2：标签
            LongPressOptionItem(
                icon = "🏷️",
                text = "标签",
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onTagClick()
                    }
                }
            )

            // 分割线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFEEEEEE))
                    .padding(horizontal = 16.dp)
            )

            // 选项3：改日期
            LongPressOptionItem(
                icon = "📅",
                text = "改日期",
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDateClick()
                    }
                }
            )

            // 分割线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFEEEEEE))
                    .padding(horizontal = 16.dp)
            )

            // 选项4：删除（红色）
            LongPressOptionItem(
                icon = "🗑️",
                text = "删除",
                textColor = Color(0xFFE53935),
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDeleteClick()
                    }
                }
            )

            // 临时调试选项：输出当前灵感的 imagePaths 到 logcat
            // 仅在 onDebugClick 不为空时显示，便于排查图片加载问题
            if (onDebugClick != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFEEEEEE))
                        .padding(horizontal = 16.dp)
                )
                LongPressOptionItem(
                    icon = "🐛",
                    text = "调试输出 imagePaths",
                    textColor = Color(0xFF666666),
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDebugClick()
                        }
                    }
                )
            }
        }
    }
}

/**
 * 长按面板选项条目
 *
 * @param icon 图标emoji
 * @param text 选项文字
 * @param textColor 文字颜色（默认主文字色）
 * @param onClick 点击回调
 */
@Composable
private fun LongPressOptionItem(
    icon: String,
    text: String,
    textColor: Color = Color(0xFF2D2D2D),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            modifier = Modifier.width(32.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
