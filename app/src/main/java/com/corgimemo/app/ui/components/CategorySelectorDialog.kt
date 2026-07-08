package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType

/**
 * 分类选择弹窗（AlertDialog）
 *
 * 用于待办编辑页底部"分类"按钮：
 * - 弹窗分两个区域：「默认分类」+「我的分组（用户自定义）」
 * - 每个分类以 Tag 标签形式展示，点击即选中并关闭弹窗
 * - 「自定义」按钮点击后展开输入框，用于创建新分组
 * - 若用户没有自定义过分组，则只显示默认分类区域
 *
 * @param categories 可选分类列表（已包含默认 + 用户自定义）
 * @param currentCategoryId 当前 todo 的分类 ID（用于高亮显示）
 * @param onDismiss 弹窗关闭回调（取消、点击外部）
 * @param onCategorySelected 分类选中回调，参数为 (id, name)。
 *        - id > 0 表示选中默认/已存在分类
 *        - id == 0L 表示"自定义分类"（name 为用户输入）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySelectorDialog(
    categories: List<Category>,
    currentCategoryId: Long?,
    onDismiss: () -> Unit,
    onCategorySelected: (id: Long, name: String) -> Unit
) {
    /** 自定义输入框是否展开 */
    var showCustomInput by remember { mutableStateOf(false) }
    /** 自定义输入框当前内容 */
    var customInput by remember { mutableStateOf("") }

    /**
     * 将分类列表拆分为默认 / 自定义两组：
     * - defaultCategories：5 个默认分类（学习/工作/生活/运动/娱乐）
     * - customCategories：用户通过侧滑页"添加分组"或本弹窗"自定义"创建的所有自定义分类
     *
     * 拆分依据是 `isDefault` 字段（默认分类为 true），与 type 解耦，
     * 未来若新增其他默认分类类型（如"健康"）也无需修改此处逻辑。
     */
    val defaultCategories = categories.filter { it.isDefault }
    val customCategories = categories.filter { !it.isDefault && it.type == CategoryType.CUSTOM }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择分类",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                /** 区域 1：默认分类（始终显示） */
                SectionHeader(title = "默认分类")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    /** 5 个默认分类 Tag，使用各自 type 对应的 emoji */
                    defaultCategories.forEach { category ->
                        CategoryTag(
                            category = category,
                            isSelected = category.id == currentCategoryId,
                            onClick = {
                                onCategorySelected(category.id, category.name)
                                onDismiss()
                            }
                        )
                    }
                }

                /** 区域 2：用户自定义分组（仅在存在自定义分类时显示） */
                if (customCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(title = "我的分组")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        /** 自定义分类 Tag：使用统一的 📋 图标 */
                        customCategories.forEach { category ->
                            CategoryTag(
                                category = category,
                                isSelected = category.id == currentCategoryId,
                                onClick = {
                                    onCategorySelected(category.id, category.name)
                                    onDismiss()
                                },
                                /** 自定义分类统一使用 📋 图标（区别于默认分类的彩色 emoji） */
                                forceIcon = "📋"
                            )
                        }

                        /** 「+ 自定义」按钮：与自定义分类同一区域，点击展开输入框 */
                        CustomCategoryButton(
                            onClick = { showCustomInput = !showCustomInput }
                        )
                    }
                } else {
                    /**
                     * 当用户尚未创建任何自定义分组时，"+ 自定义"按钮单独显示在默认分类区域下方。
                     * 避免因空状态导致用户找不到创建入口。
                     */
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        CustomCategoryButton(
                            onClick = { showCustomInput = !showCustomInput }
                        )
                    }
                }

                /** 自定义输入框（点击"+ 自定义"按钮后展开） */
                if (showCustomInput) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            placeholder = { Text("输入分组名称", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val name = customInput.trim()
                                if (name.isNotBlank()) {
                                    onCategorySelected(0L, name)
                                    onDismiss()
                                }
                            },
                            enabled = customInput.trim().isNotBlank()
                        ) {
                            Text("确定", fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * 区域标题组件
 *
 * 用于在弹窗内区分「默认分类」与「我的分组」两个区域。
 * 视觉上使用小号灰色文字 + 上下留白，让用户明确感知到当前展示的是哪一组分类。
 *
 * @param title 区域标题文本（如"默认分类"、"我的分组"）
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * 分类标签组件
 *
 * 单个分类的可视化展示：
 * - 未选中：浅色背景 + 分类图标 + 名称
 * - 选中：边框 + 边框色背景加深
 *
 * @param category 分类实体
 * @param isSelected 是否处于选中状态
 * @param onClick 点击回调
 * @param forceIcon 可选参数：强制使用指定图标（用于自定义分类统一 📋）。
 *        传 null 时使用 type 映射的默认 emoji。
 */
@Composable
private fun CategoryTag(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    forceIcon: String? = null
) {
    val categoryColor = getCategoryColor(category.type)
    val bgColor = if (isSelected) categoryColor.copy(alpha = 0.25f)
                  else categoryColor.copy(alpha = 0.12f)
    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            color = categoryColor,
            shape = RoundedCornerShape(20.dp)
        )
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(borderModifier)
            .background(color = bgColor, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = forceIcon ?: getCategoryEmoji(category.type),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = category.name,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * 自定义分类按钮
 *
 * 点击后展开输入框；视觉上与分类标签风格统一但有"+ 号"标识。
 * 在弹窗中既可作为「用户尚未创建自定义分组时」的创建入口，
 * 也可作为「我的分组」区域末尾的添加新分组入口。
 */
@Composable
private fun CustomCategoryButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "+",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "自定义",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 分类类型 → Emoji 图标
 *
 * 默认分类（STUDY/WORK/LIFE/SPORT/ENTERTAINMENT）使用各自专属 emoji；
 * 其他类型（含自定义 CUSTOM）统一回退为 📋，保证自定义分类视觉一致。
 */
private fun getCategoryEmoji(type: Int): String = when (type) {
    CategoryType.STUDY -> "📚"
    CategoryType.WORK -> "💼"
    CategoryType.LIFE -> "🏠"
    CategoryType.SPORT -> "🏃"
    CategoryType.ENTERTAINMENT -> "🎮"
    else -> "📋"
}

/**
 * 分类类型 → 主题色（与 CategoryPickerSheet 保持一致）
 */
private fun getCategoryColor(type: Int): Color = when (type) {
    CategoryType.STUDY -> Color(0xFF7EC8A0)  // 薄荷绿
    CategoryType.WORK -> Color(0xFF90CAF9)   // 天空蓝
    CategoryType.LIFE -> Color(0xFFFFB74D)   // 暖橙色
    CategoryType.SPORT -> Color(0xFF7EB8DA)  // 运动蓝
    CategoryType.ENTERTAINMENT -> Color(0xFFE1BEE7)  // 紫粉
    else -> Color(0xFFB8A0D4)  // 自定义分组：紫色（与默认分类区分）
}
