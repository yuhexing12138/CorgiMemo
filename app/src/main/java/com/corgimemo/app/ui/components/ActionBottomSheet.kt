package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 操作项数据类
 *
 * 定义底部弹窗中的单个操作项，包含图标、文本和点击行为。
 * 支持标记为破坏性操作（如删除），会以红色显示。
 *
 * @param icon 操作图标（使用 Material Icons）
 * @param text 操作显示文本
 * @param isDestructive 是否为破坏性操作（默认为 false）
 *              破坏性操作会以红色（error 色）显示
 * @param onClick 点击回调函数
 */
data class ActionItem(
    val icon: ImageVector,
    val text: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 通用操作底部弹窗组件
 *
 * 可复用的 ModalBottomSheet 组件，用于展示操作列表。
 * 支持自定义标题、操作项列表、分割线位置等配置。
 * 适用于待办操作、分组操作等多种场景。
 *
 * @param sheetState 底部弹窗状态控制对象
 * @param title 弹窗标题（可选，为 null 则不显示标题栏）
 * @param actions 操作项列表
 * @param dividerIndex 分割线插入位置索引（可选，从 0 开始），
 *                     在指定操作项之后插入分割线
 * @param onDismiss 弹窗关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionBottomSheet(
    sheetState: SheetState,
    title: String? = null,
    actions: List<ActionItem>,
    dividerIndex: Int? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null // 使用自定义拖动指示器
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            /** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0（与原型一致） */
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
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

            /** 可选标题栏：左对齐标题（18px SemiBold）+ 右侧暖橙色关闭按钮 */
            title?.let { titleText ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = titleText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

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

                /** 标题下方分割线 */
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            }

            /** 操作项列表 */
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                actions.forEachIndexed { index, action ->
                    /** 在指定位置插入分割线 */
                    if (dividerIndex != null && index == dividerIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    ActionItemRow(
                        icon = action.icon,
                        text = action.text,
                        isDestructive = action.isDestructive,
                        onClick = {
                            action.onClick()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 操作行组件
 *
 * 单个操作项的 UI 实现，包含图标和文本。
 * 支持普通状态和破坏性状态两种样式。
 *
 * @param icon 操作图标
 * @param text 操作文本
 * @param isDestructive 是否为破坏性操作
 * @param onClick 点击回调
 */
@Composable
private fun ActionItemRow(
    icon: ImageVector,
    text: String,
    isDestructive: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error // 破坏性操作使用红色
    } else {
        MaterialTheme.colorScheme.onSurface // 普通操作使用默认色
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
