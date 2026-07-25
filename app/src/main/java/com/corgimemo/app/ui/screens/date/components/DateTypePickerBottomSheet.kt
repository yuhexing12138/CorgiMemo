package com.corgimemo.app.ui.screens.date.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.viewmodel.DateCategory

/**
 * 类型选择结果密封类
 *
 * 统一三种选择场景的回调类型，调用方根据类型分别处理：
 * - [BuiltIn]：选中内置 DateCategory 枚举类型
 * - [CustomExisting]：选中已有自定义类型（存储为 "CUSTOM:<id>" 格式）
 * - [CustomNew]：新建自定义类型（调用方需先创建类型再保存日期）
 */
sealed class DateTypePickerResult {
    data class BuiltIn(val category: DateCategory) : DateTypePickerResult()
    data class CustomExisting(val customType: CustomDateType) : DateTypePickerResult()
    data class CustomNew(val name: String) : DateTypePickerResult()
}

/**
 * 类型选择底部弹窗（日期新建/编辑页专用）
 *
 * 单选选择器型底部弹窗，提供三种类型选择方式。
 * 选中即回调并关闭弹窗，无预选状态。
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 * 严格遵循单选选择器型底部弹窗原型规范。
 *
 * @param customDateTypes 已有自定义类型列表（从 ViewModel 获取）
 * @param onDismissRequest 关闭弹窗回调
 * @param onSelected 选中类型回调，返回 [DateTypePickerResult] 三种子类之一
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTypePickerBottomSheet(
    customDateTypes: List<CustomDateType> = emptyList(),
    onDismissRequest: () -> Unit,
    onSelected: (DateTypePickerResult) -> Unit
) {
    val fixedCategories = remember {
        listOf(
            DateCategory.ANNIVERSARY,
            DateCategory.BIRTHDAY,
            DateCategory.HOLIDAY,
            DateCategory.LIFE,
            DateCategory.STUDY,
            DateCategory.WORK,
            DateCategory.ENTERTAINMENT
        )
    }

    var showCustomInput by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            TitleBar(title = "选择类型", onDismiss = onDismissRequest)

            /** 标题下方分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 7 个固定类型选项
            fixedCategories.forEach { category ->
                TypeOptionRow(
                    emoji = category.emoji,
                    name = category.displayName,
                    onClick = {
                        onSelected(DateTypePickerResult.BuiltIn(category))
                        onDismissRequest()
                    }
                )
            }

            // 已有自定义类型列表
            if (customDateTypes.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = Color(0x14000000)
                )

                customDateTypes.forEach { customType ->
                    TypeOptionRow(
                        emoji = customType.emoji,
                        name = customType.name,
                        onClick = {
                            onSelected(DateTypePickerResult.CustomExisting(customType))
                            onDismissRequest()
                        }
                    )
                }
            }

            // 分隔线
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = Color(0x14000000)
            )

            // 自定义输入区
            if (!showCustomInput) {
                TypeOptionRow(
                    emoji = "✏️",
                    name = "自定义",
                    onClick = { showCustomInput = true }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { if (it.length <= 10) customName = it },
                        label = { Text("输入类型名称（1-10 字）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val trimmed = customName.trim()
                            if (trimmed.isNotEmpty() && trimmed.length <= 10) {
                                onSelected(DateTypePickerResult.CustomNew(trimmed))
                                onDismissRequest()
                            }
                        },
                        enabled = customName.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("添加")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
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

/** 类型选项行：emoji + 名称，标准单选选择器行样式 */
@Composable
private fun TypeOptionRow(
    emoji: String,
    name: String,
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
        /** emoji 占位：20sp，固定 32dp 宽度保证名称对齐 */
        Text(
            text = emoji,
            fontSize = 20.sp,
            modifier = Modifier.width(32.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2D2D2D),
            modifier = Modifier.weight(1f)
        )
    }
}
