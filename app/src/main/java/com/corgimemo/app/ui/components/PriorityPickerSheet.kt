package com.corgimemo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 优先级选择底部弹窗组件
 *
 * 使用 [ModalBottomSheet] 展示 4 选 1 单选列表。
 * 选中项右侧显示 ✓ 图标，文字加粗并以主题色高亮。
 * 点击任意项立即触发 [onConfirm] 回调，由调用方负责关闭弹窗。
 *
 * 优先级数值语义（与 TodoItem.priority 字段一致）：
 * - 0 = 无
 * - 1 = 低
 * - 2 = 中
 * - 3 = 高
 *
 * @param sheetState 底部弹窗状态控制对象
 * @param initialPriority 初始选中的优先级（默认 0=无）
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
    /**
     * 优先级选项列表（id, 名称）
     * 注意：保持 0/1/2/3 数值语义，与 TodoItem.priority 字段一致。
     *
     * v2026-07-21 统一：文案从「无/低/中/高」改为「无优先级/低优先级/中优先级/高优先级」，
     * 与 TodoEditScreen.kt 内的 [showPriorityDialog] 弹窗选项文案保持一致，
     * 避免用户在两个弹窗中看到不同长度的标签产生认知负担。
     */
    val priorities = listOf(
        0 to "无优先级",
        1 to "低优先级",
        2 to "中优先级",
        3 to "高优先级"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            /** 标题 */
            Text(
                text = "设置优先级",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )

            /** 4 选 1 列表 */
            priorities.forEach { (priority, name) ->
                val isSelected = priority == initialPriority
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onConfirm(priority)
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /** 左侧：选项文本 */
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )

                    /** 右侧：选中态显示 ✓ */
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
