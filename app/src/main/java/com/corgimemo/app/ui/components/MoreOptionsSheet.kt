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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 更多选项底部弹窗（批量模式）
 *
 * 多选模式下底部工具栏 ⋮ 按钮触发的 6 项操作菜单：
 * 1. 完成 — 触发批量完成并退出多选
 * 2. 置顶 — 批量置顶
 * 3. 优先级 — 弹 PriorityPickerSheet
 * 4. 提醒时间 — 弹 ReminderPickerBottomSheet
 * 5. 创建副本 — 批量复制
 * 6. 转换为灵感 — Toast "功能开发中"（暂未实现）
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 * 严格遵循操作列表型底部弹窗原型规范。
 *
 * @param sheetState 底部弹窗状态（调用方需用 rememberModalBottomSheetState(skipPartiallyExpanded = true) 创建）
 * @param onDismiss 关闭弹窗回调
 * @param onComplete 完成回调
 * @param onPin 置顶回调
 * @param onPriority 优先级回调
 * @param onReminder 提醒时间回调
 * @param onDuplicate 创建副本回调
 * @param onConvertToInspiration 转换为灵感回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onPin: () -> Unit,
    onPriority: () -> Unit,
    onReminder: () -> Unit,
    onDuplicate: () -> Unit,
    onConvertToInspiration: () -> Unit
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

            // 6 项操作列表（无标题栏、无分割线）
            Column(modifier = Modifier.fillMaxWidth()) {
                ActionRow(
                    icon = Icons.Default.Check,
                    text = "完成",
                    onClick = { onComplete(); onDismiss() }
                )
                ActionRow(
                    icon = Icons.Default.PushPin,
                    text = "置顶",
                    onClick = { onPin(); onDismiss() }
                )
                ActionRow(
                    icon = Icons.Default.Flag,
                    text = "优先级",
                    onClick = { onPriority(); onDismiss() }
                )
                ActionRow(
                    icon = Icons.Default.Alarm,
                    text = "提醒时间",
                    onClick = { onReminder(); onDismiss() }
                )
                ActionRow(
                    icon = Icons.Default.ContentCopy,
                    text = "创建副本",
                    onClick = { onDuplicate(); onDismiss() }
                )
                ActionRow(
                    icon = Icons.Outlined.Lightbulb,
                    text = "转换为灵感",
                    onClick = { onConvertToInspiration(); onDismiss() }
                )
            }
        }
    }
}

/**
 * 操作行：Icon 20px + 文字 16px Medium，gap 16dp，padding 14px 24px
 * 与 OperationSheets.kt 共享同一原型规范。
 */
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
