package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    /** 选中内置类型 */
    data class BuiltIn(val category: DateCategory) : DateTypePickerResult()
    /** 选中已有自定义类型 */
    data class CustomExisting(val customType: CustomDateType) : DateTypePickerResult()
    /** 新建自定义类型（返回名称，调用方负责创建） */
    data class CustomNew(val name: String) : DateTypePickerResult()
}

/**
 * 类型选择底部弹窗（日期新建/编辑页专用）
 *
 * 提供三种类型选择方式：
 * 1. 7 个固定内置类型：纪念日 / 生日 / 节日 / 生活 / 学习 / 工作 / 娱乐
 * 2. 已有自定义类型列表（从侧滑栏同步，存储为 "CUSTOM:<id>" 格式）
 * 3. "自定义"输入功能：输入名称后新建类型
 *
 * 数据同步说明：
 * - 自定义类型列表与侧滑栏、数据统计页共享同一数据源（CustomDateType 表）
 * - 选中已有自定义类型时存储为 "CUSTOM:<id>" 格式，重命名/删除时自动同步
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
    // 弹窗状态：控制滑入/滑出动画与展开高度
    val sheetState = rememberModalBottomSheetState()

    // 7 个固定类型列表：纪念日/生日/节日/生活/学习/工作/娱乐
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

    // 是否展开"自定义"输入区
    var showCustomInput by remember { mutableStateOf(false) }

    // 自定义类型名称输入框内容
    var customName by remember { mutableStateOf("") }

    // 弹窗主体：圆角顶部 24dp，使用 surface 背景色与项目主题保持一致
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // 顶部标题栏：标题 + 关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择类型",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 7 个固定类型选项：点击后回调 BuiltIn 结果并关闭弹窗
            fixedCategories.forEach { category ->
                DateTypeOptionRow(
                    emoji = category.emoji,
                    name = category.displayName,
                    onClick = {
                        onSelected(DateTypePickerResult.BuiltIn(category))
                        onDismissRequest()
                    }
                )
            }

            // 已有自定义类型列表（从侧滑栏同步）
            if (customDateTypes.isNotEmpty()) {
                // 分隔线
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                customDateTypes.forEach { customType ->
                    DateTypeOptionRow(
                        emoji = customType.emoji,
                        name = customType.name,
                        onClick = {
                            onSelected(DateTypePickerResult.CustomExisting(customType))
                            onDismissRequest()
                        }
                    )
                }
            }

            // 分隔线（自定义输入区之前）
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // "自定义"选项：未展开时显示为普通行；点击后展开为输入区
            if (!showCustomInput) {
                DateTypeOptionRow(
                    emoji = "✏️",
                    name = "自定义",
                    onClick = { showCustomInput = true }
                )
            } else {
                // 自定义输入区：OutlinedTextField + 添加按钮
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    // 限制输入 1-10 字（实时过滤超过 10 字的输入）
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { if (it.length <= 10) customName = it },
                        label = { Text("输入类型名称（1-10 字）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 添加按钮：仅在输入非空时启用；回调 CustomNew 结果，由调用方创建类型
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 单个类型选项行（emoji + 名称）
 *
 * 内部 Composable，弹窗内复用。固定高度 56dp，emoji 固定 32dp 宽度占位，名称紧跟其后。
 *
 * @param emoji 左侧 emoji 图标
 * @param name 类型显示名称
 * @param onClick 整行点击回调
 */
@Composable
private fun DateTypeOptionRow(
    emoji: String,
    name: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧 emoji：固定 32dp 宽度以保证名称对齐
        Text(
            text = emoji,
            fontSize = 20.sp,
            modifier = Modifier.width(32.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 类型名称
        Text(
            text = name,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
