package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.R

/**
 * 分享方式选择底部弹窗
 *
 * 触发：待办列表页多选待办后点击"分享"按钮，或待办编辑页点击右上角分享图标。
 * 提供两项操作：保存到相册（逐条截图保存）+ 更多分享（调用系统分享 Intent）。
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 * 严格遵循操作列表型底部弹窗原型规范。
 *
 * @param sheetState 底部弹窗状态（调用方需用 rememberModalBottomSheetState(skipPartiallyExpanded = true) 创建）
 * @param onDismiss 关闭弹窗回调
 * @param onSaveToAlbum 保存到相册回调
 * @param onMoreShare 更多分享回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareModeDialog(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSaveToAlbum: () -> Unit,
    onMoreShare: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            /** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0（与原型一致） */
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFE0E0E0))
                )
            }

            /** 标题栏：左对齐标题 + 右侧圆形关闭按钮（padding: 12px 24px 16px） */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择分享方式",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D2D2D),
                    modifier = Modifier.weight(1f)
                )

                /** 圆形暖橙色关闭按钮：32dp，#FFF0E5 背景，18dp #FF9A5C 图标 */
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFFFF0E5))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFFFF9A5C),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            /** 标题下方分割线：1px，rgba(0,0,0,0.08) */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(8.dp))

            /** 操作列表 */
            Column(modifier = Modifier.fillMaxWidth()) {
                ActionRow(
                    icon = Icons.Outlined.Image,
                    text = "保存到相册",
                    onClick = { onSaveToAlbum(); onDismiss() }
                )
                ActionRow(
                    icon = Icons.Outlined.Share,
                    text = "更多分享",
                    onClick = { onMoreShare(); onDismiss() }
                )
            }
        }
    }
}

/** 操作行：Icon 20px + 文字 16px Medium，gap 16dp，padding 14px 24px */
@Composable
private fun ActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color(0xFF2D2D2D),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2D2D2D),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 部分未保存分组时的确认弹窗
 *
 * 场景：用户点分享时，有 N 个分组未保存。
 * 让用户选择"仅分享已保存的 M 条"还是"先去保存"。
 *
 * @param totalGroups 总分组数（含未保存的）
 * @param unsavedCount 未保存分组数
 * @param savedCount 已保存分组数（含主 todo）
 * @param onDismiss 关闭弹窗
 * @param onShareSavedOnly 确认"仅分享已保存"，之后会再走 ShareModeDialog
 */
@Composable
fun PartialSaveConfirmDialog(
    totalGroups: Int,
    unsavedCount: Int,
    savedCount: Int,
    onDismiss: () -> Unit,
    onShareSavedOnly: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.partial_save_title)) },
        text = {
            Text(
                text = stringResource(
                    id = R.string.partial_save_subtitle,
                    totalGroups,
                    unsavedCount,
                    savedCount
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onShareSavedOnly) {
                Text(text = stringResource(id = R.string.partial_save_share_saved_only))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.partial_save_go_save))
            }
        }
    )
}
