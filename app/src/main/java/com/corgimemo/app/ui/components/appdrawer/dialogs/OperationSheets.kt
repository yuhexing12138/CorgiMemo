package com.corgimemo.app.ui.components.appdrawer.dialogs

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CustomDateType

/**
 * 分类操作底部弹窗
 *
 * 触发：侧滑抽屉 → 点击分类项右侧 ⋮ → 弹出置顶/编辑/删除
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 * 严格遵循操作列表型底部弹窗原型规范。
 *
 * @param sheetState BottomSheet 状态（skipPartiallyExpanded = true 确保一次性展开）
 * @param category 被操作的分类
 * @param onPin 置顶回调
 * @param onRename 编辑回调
 * @param onDelete 删除回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOperationSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    category: Category,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null
    ) {
        // CategoryOperationSheet - 展开动画由 ModalBottomSheet 提供
        // 弹簧上滑 + 遮罩淡入，参考 SpringDefaults.DampingRatioMediumBouncy

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            DragHandle()

            // 标题栏：左对齐 + 右侧圆形关闭按钮（padding: 12px 24px 16px）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D2D2D),
                    modifier = Modifier.weight(1f)
                )

                CloseButton(onClick = onDismiss)
            }

            // 标题下方分割线
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 操作列表
            Column(modifier = Modifier.fillMaxWidth()) {
                // v2026-07-29 改造：Pin 操作已实现，文案根据 isPinned 状态切换
                ActionRow(
                    icon = Icons.Outlined.PushPin,
                    text = if (category.isPinned) "取消置顶分组" else "置顶分组"
                ) {
                    onPin()
                    onDismiss()
                }
                ActionRow(icon = Icons.Filled.Edit, text = "编辑分组") {
                    onRename()
                    onDismiss()
                }
                ActionRow(
                    icon = Icons.Outlined.Delete,
                    text = "删除分组",
                    isDestructive = true
                ) {
                    onDelete()
                    onDismiss()
                }
            }
        }
    }
}

/**
 * 自定义日期类型操作底部弹窗
 *
 * 与 CategoryOperationSheet 区别：仅含编辑/删除两项，不含置顶。
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 * 严格遵循操作列表型底部弹窗原型规范。
 *
 * @param sheetState BottomSheet 状态（skipPartiallyExpanded = true 确保一次性展开）
 * @param customType 被操作的自定义类型
 * @param onRename 编辑回调
 * @param onDelete 删除回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTypeOperationSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    customType: CustomDateType,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null
    ) {
        // DateTypeOperationSheet - 展开动画由 ModalBottomSheet 提供

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            DragHandle()

            // 标题栏：emoji + 类型名 + 右侧圆形关闭按钮（padding: 12px 24px 16px）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = customType.emoji, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = customType.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2D2D2D)
                    )
                }

                CloseButton(onClick = onDismiss)
            }

            // 标题下方分割线
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 操作列表
            Column(modifier = Modifier.fillMaxWidth()) {
                ActionRow(icon = Icons.Filled.Edit, text = "编辑类型") {
                    onRename()
                    onDismiss()
                }
                ActionRow(
                    icon = Icons.Outlined.Delete,
                    text = "删除类型",
                    isDestructive = true
                ) {
                    onDelete()
                    onDismiss()
                }
            }
        }
    }
}

// ==================== 共享子组件（遵循原型规范） ====================

/**
 * 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0（margin: 12px auto 0）
 */
@Composable
private fun DragHandle() {
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
}

/**
 * 圆形暖橙色关闭按钮：32dp，#FFF0E5 背景，18dp #FF9A5C 图标
 */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFFFF0E5))
            .clickable(onClick = onClick),
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

/**
 * 操作行：Icon 20px + 文字 16px Medium，gap 16dp，padding 14px 24px
 * 破坏性操作：#E53935 文字色 + hover 背景 #FFEBEE
 * 普通操作：hover 背景 #F8F6F3
 */
@Composable
private fun ActionRow(
    icon: ImageVector,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) Color(0xFFE53935) else Color(0xFF2D2D2D)

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
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
    }
}
