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
 * 提供待办列表的排序方式选择功能，
 * 包含 4 种排序选项：按更新时间或创建时间升序/降序排列。
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 *
 * @param sheetState 底部弹窗状态控制（调用方需用 rememberModalBottomSheetState(skipPartiallyExpanded = true) 创建）
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
    onSortOrderSelected: (String) -> Unit,
    /** 恢复默认排序回调（按当前 sortType 重算 sortOrder） */
    onRestoreDefaultOrder: () -> Unit = {}
) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
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

            /** 左对齐标题（18px SemiBold）+ 右侧圆形关闭按钮 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "待办排序",
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
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )
            Spacer(modifier = Modifier.height(16.dp))

            /** 恢复默认排序按钮（独立于 SortOption 列表） */
            TextButton(
                onClick = {
                    onRestoreDefaultOrder()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFFF9A5C).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Text(
                    text = "恢复默认排序",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE88A4D),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
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
