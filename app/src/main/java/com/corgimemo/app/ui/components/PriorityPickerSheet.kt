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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 优先级选择底部弹窗
 *
 * 单选选择器型底部弹窗，提供 4 选 1 优先级列表。
 * 选中项左侧有 3px 暖橙竖条标识 + 浅暖橙背景 #FFF0E5 + 右侧 ✓ 图标。
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 * 严格遵循单选选择器型底部弹窗原型规范。
 *
 * @param sheetState 底部弹窗状态控制对象
 * @param initialPriority 初始选中的优先级（0=无, 1=低, 2=中, 3=高）
 * @param onDismiss 关闭弹窗回调
 * @param onConfirm 确认选择回调，参数为选中的优先级数值
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityPickerSheet(
    sheetState: SheetState,
    initialPriority: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (priority: Int) -> Unit
) {
    val priorities = listOf(
        0 to ("无优先级" to PriorityColors.None),
        1 to ("低优先级" to PriorityColors.Low),
        2 to ("中优先级" to PriorityColors.Medium),
        3 to ("高优先级" to PriorityColors.High)
    )

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
            /** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
            DragHandle()

            /** 标题栏：左对齐标题 + 右侧圆形关闭按钮 */
            TitleBar(title = "设置优先级", onDismiss = onDismiss)

            /** 标题下方分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(8.dp))

            /** 4 选 1 选项列表 */
            Column(modifier = Modifier.fillMaxWidth()) {
                priorities.forEach { (priority, pair) ->
                    val (name, priorityColor) = pair
                    val isSelected = priority == initialPriority

                    // 外层 Box：让竖条叠加在行上
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // 内容行（先绘制，在下层）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelected) Modifier.background(Color(0xFFFFF0E5))
                                    else Modifier
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onConfirm(priority) }
                                .padding(start = 24.dp, end = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 优先级颜色圆点（12dp）
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(priorityColor)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = name,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) priorityColor else Color(0xFF2D2D2D)
                                )
                            }

                            // 右侧选中 ✓
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选中",
                                    tint = priorityColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 选中态左侧 3px 暖橙竖条（后绘制，在上层，贴在 Box 左边缘）
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(36.dp)
                                    .align(Alignment.CenterStart)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(Color(0xFFFF9A5C))
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
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

/** 标题栏：左对齐标题 + 右侧圆形关闭按钮 */
@Composable
private fun TitleBar(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2D2D2D),
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
}
