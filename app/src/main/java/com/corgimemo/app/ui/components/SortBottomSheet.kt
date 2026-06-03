package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 排序选项数据类
 *
 * @param value 排序类型标识符（如 "updated_desc"）
 * @param label 显示文本
 */
data class SortOption(
    val value: String,
    val label: String
)

/**
 * 预定义的排序选项列表
 */
val SORT_OPTIONS = listOf(
    SortOption("updated_desc", "最新更新的在前"),
    SortOption("updated_asc", "最新更新的在后"),
    SortOption("created_desc", "最新创建的在前"),
    SortOption("created_asc", "最新创建的在后")
)

/**
 * 排序弹窗 BottomSheet 组件
 *
 * 提供便签/待办列表的排序方式选择功能，
 * 包含 4 种排序选项：按更新时间或创建时间升序/降序排列。
 *
 * @param sheetState 底部弹窗状态控制
 * @param currentSortOrder 当前选中的排序方式标识符
 * @param onDismiss 弹窗关闭回调
 * @param onSortOrderSelected 排序方式选择回调，返回选中的排序类型标识符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    sheetState: SheetState,
    currentSortOrder: String,
    onDismiss: () -> Unit,
    onSortOrderSelected: (String) -> Unit
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
            /** 自定义拖动指示器 */
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.width(36.dp)
                )
            }

            /** 标题栏：居中标题 + 右侧关闭按钮 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                /** 左侧占位，保持标题居中 */
                Spacer(modifier = Modifier.width(32.dp))

                /** 居中标题 "便签排序" */
                Text(
                    text = "便签排序",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                /** 暖橙色关闭按钮 */
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFFFF9A5C), // 暖橙色
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            /** 分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            /** 排序选项列表 */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SORT_OPTIONS.forEach { option ->
                    SortOptionButton(
                        label = option.label,
                        isSelected = currentSortOrder == option.value,
                        onClick = {
                            onSortOrderSelected(option.value)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 排序选项按钮组件
 *
 * 全宽圆角按钮，支持选中态和未选中态两种样式：
 * - 选中态：浅暖橙色背景 (#FFE0C0)，无边框
 * - 未选中态：白色背景，1px 灰色边框
 *
 * @param label 按钮显示文本
 * @param isSelected 是否为选中状态
 * @param onClick 点击回调
 */
@Composable
private fun SortOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        Color(0xFFFFE0C0) // 浅暖橙色背景
    } else {
        MaterialTheme.colorScheme.surface // 白色背景
    }

    val borderColor = if (isSelected) {
        Color.Transparent // 选中态无边框
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) // 未选中态灰色边框
    }

    val textColor = if (isSelected) {
        Color(0xFFE88A4D) // 深暖橙色文字
    } else {
        MaterialTheme.colorScheme.onSurface // 默认文字色
    }

    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(
                if (!isSelected) {
                    Modifier.border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
