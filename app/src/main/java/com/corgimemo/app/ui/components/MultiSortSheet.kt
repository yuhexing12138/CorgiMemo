package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.MultiSortConfig
import com.corgimemo.app.data.model.SortCondition
import com.corgimemo.app.data.model.SortDirection
import com.corgimemo.app.data.model.SortField

/**
 * 多级排序选择底部弹窗（N 级动态版）
 *
 * 支持任意数量的排序条件配置，使用 LazyColumn 动态渲染每一级。
 * 建议上限 5 级（超过后隐藏"添加"按钮），无硬性限制。
 *
 * **UI 布局**:
 * ```
 * ┌─────────────────────────────────────┐
 * │  ════════════════════════════════   │  ← 拖拽手柄 + 标题
 * │                                     │
 * │  ┌─ 第一级排序（主排序）──────────┐  │
 * │  │ [更新时间 ▼] [降序 ▼]          │  │
 * │  └────────────────────────────────┘  │
 * │                                     │
 * │  ┌─ 第二级排序 ──────────────────┐  │
 * │  │ [截止时间 ▼] [升序 ▼]   [✕]   │  │  ← 可删除
 * │  └────────────────────────────────┘  │
 * │                                     │
 * │  ┌─ 第三级排序 ──────────────────┐  │
 * │  │ [优先级 ▼]   [降序 ▼]   [✕]   │  │
 * │  └────────────────────────────────┘  │
 * │                                     │
 * │      [+ 添加第四级排序条件]         │  ← 动态添加（≤5 级时显示）
 * │                                     │
 * │  当前排序: 更新时间降序 → 截止时间升序│  ← 预览文字
 * │                                     │
 * │  [重置为默认]              [应用]   │  ← 操作按钮
 * └─────────────────────────────────────┘
 * ```
 *
 * @param sheetState BottomSheet 状态控制对象
 * @param currentConfig 当前的多级排序配置
 * @param onConfigChanged 配置变更回调（实时预览）
 * @param onApply 用户点击"应用"按钮时的回调
 * @param onDismiss 弹窗关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSortSheet(
    sheetState: androidx.compose.material3.SheetState = rememberModalBottomSheetState(),
    currentConfig: MultiSortConfig,
    onConfigChanged: (MultiSortConfig) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    /** 统一的 Snackbar 提示回调（由调用方传入） */
    onShowSnackbar: (String) -> Unit = {}
) {
    /** 内部状态：可编辑的配置副本 */
    var editableConfig by remember { mutableStateOf(currentConfig) }


    /** 建议最大级别数（超过后点击显示提示而非阻止） */
    val maxSuggestedLevels = 5

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /** 拖拽指示条 */
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "多级排序设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                /**
                 * 动态排序级别列表（LazyColumn 渲染）
                 *
                 * 遍历 editableConfig.sorts 列表，
                 * 为每个排序条件渲染一行配置 UI。
                 */
                LazyColumn(
                    modifier = Modifier.weight(weight = 1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    /** 动态渲染每一级排序条件 */
                    itemsIndexed(
                        items = editableConfig.sorts,
                        key = { _, condition -> "${condition.field.name}:${condition.direction.name}" }
                    ) { index, condition ->
                        SortLevelRow(
                            levelLabel = buildLevelLabel(index),
                            condition = condition,
                            onConditionChanged = { newCondition ->
                                editableConfig = updateSortAt(editableConfig, index, newCondition)
                                onConfigChanged(editableConfig)
                            },
                            showRemoveButton = index > 0, /** 第一级不可删除 */
                            onRemove = {
                                editableConfig = removeSortAt(editableConfig, index)
                                onConfigChanged(editableConfig)
                            }
                        )
                    }

                    /** 动态"添加下一级"按钮（始终显示，达到建议上限时提示） */
                    item {
                        val currentLevelCount = editableConfig.sorts.size
                        val isAtLimit = currentLevelCount >= maxSuggestedLevels

                        AddSortLevelButton(
                            text = if (isAtLimit) "+ 添加更多排序条件"
                            else "+ 添加第${indexToChinese(currentLevelCount + 1)}级排序条件",
                            onClick = {
                                if (isAtLimit) {
                                    /** 达到建议上限时显示 Snackbar 提示 */
                                    onShowSnackbar("建议不超过 ${maxSuggestedLevels} 级排序以获得最佳性能")
                                } else {
                                    /** 在列表末尾追加新的默认排序条件 */
                                    val nextDefaultField = when (currentLevelCount) {
                                        1 -> SortField.CREATED_AT
                                        2 -> SortField.DUE_DATE
                                        3 -> SortField.PRIORITY
                                        else -> SortField.TITLE
                                    }
                                    editableConfig = addSortLevel(editableConfig, SortCondition(nextDefaultField))
                                    onConfigChanged(editableConfig)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                /** 排序预览文本 */
                Text(
                    text = "当前排序: ${editableConfig.toDisplayDescription()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                /** 操作按钮行 */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    /** 重置按钮 */
                    androidx.compose.material3.TextButton(
                        onClick = {
                            editableConfig = MultiSortConfig.DEFAULT
                            onConfigChanged(editableConfig)
                        }
                    ) {
                        Text("重置为默认", color = MaterialTheme.colorScheme.error)
                    }

                    /** 应用按钮 */
                    androidx.compose.material3.Button(
                        onClick = onApply,
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9A5C) /** 暖橙色 */
                        )
                    ) {
                        Text("应用", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    )
}

/**
 * 构建排序级别的中文标签
 *
 * 根据索引生成人类可读的级别名称：
 * - 0 → "第一级（主排序）"
 * - 1 → "第二级"
 * - 2 → "第三级"
 * - ...
 *
 * @param index 排序级别索引（从 0 开始）
 * @return 级别标签字符串
 */
private fun buildLevelLabel(index: Int): String =
    if (index == 0) "第一级（主排序）"
    else "第${indexToChinese(index + 1)}级"

/**
 * 将索引转换为中文数字
 *
 * 用于生成排序级别的中文标签（如"第四级"、"第五级"）。
 *
 * @param index 从 1 开始的索引值
 * @return 对应的中文数字字符
 */
private fun indexToChinese(index: Int): String = when (index) {
    1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"
    6 -> "六"; 7 -> "七"; 8 -> "八"; 9 -> "九"; 10 -> "十"
    else -> index.toString()
}

/**
 * 替换指定位置的排序条件
 *
 * @param config 当前配置
 * @param index 要替换的位置索引
 * @param newCondition 新的排序条件
 * @return 更新后的新配置
 */
private fun updateSortAt(config: MultiSortConfig, index: Int, newCondition: SortCondition): MultiSortConfig {
    val newList = config.sorts.toMutableList()
    newList[index] = newCondition
    return config.copy(sorts = newList)
}

/**
 * 移除指定位置的排序条件
 *
 * @param config 当前配置
 * @param index 要移除的位置索引
 * @return 更新后的新配置
 */
private fun removeSortAt(config: MultiSortConfig, index: Int): MultiSortConfig {
    val newList = config.sorts.toMutableList()
    newList.removeAt(index)
    return config.copy(sorts = newList)
}

/**
 * 在末尾追加一个新的排序条件
 *
 * @param config 当前配置
 * @param condition 要追加的排序条件
 * @return 追加后的新配置
 */
private fun addSortLevel(config: MultiSortConfig, condition: SortCondition): MultiSortConfig {
    return config.copy(sorts = config.sorts + condition)
}

/**
 * 单级排序条件行组件
 *
 * 显示一个完整的排序条件配置行，
 * 包含字段选择器和方向切换器。
 *
 * @param levelLabel 级别标签（如"第一级（主排序）"、"第二级"）
 * @param condition 当前的排序条件
 * @param onConditionChanged 条件变更回调
 * @param showRemoveButton 是否显示删除按钮
 * @param onRemove 删除按钮点击回调（可选）
 */
@Composable
private fun SortLevelRow(
    levelLabel: String,
    condition: SortCondition,
    onConditionChanged: (SortCondition) -> Unit,
    showRemoveButton: Boolean = true,
    onRemove: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        /** 标签 + 删除按钮行 */
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = levelLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showRemoveButton && onRemove != null) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_delete),
                    contentDescription = "移除此级排序",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onRemove)
                )
            }
        }

        /** 字段 + 方向选择器行 */
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            /** 排序字段下拉框（模拟） */
            SortFieldDropdown(
                selectedField = condition.field,
                onFieldSelected = { newField ->
                    onConditionChanged(condition.copy(field = newField))
                },
                modifier = Modifier.weight(1f)
            )

            /** 排序方向切换按钮 */
            SortDirectionToggle(
                currentDirection = condition.direction,
                onDirectionChanged = { newDirection ->
                    onConditionChanged(condition.copy(direction = newDirection))
                }
            )
        }
    }
}

/**
 * 排序字段下拉选择器（简化版）
 *
 * 实际项目中应使用 DropdownMenu 或第三方库实现真正的下拉功能。
 * 此处简化为点击循环切换所有可用字段。
 *
 * @param selectedField 当前选中的字段
 * @param onFieldSelected 字段选择回调
 * @param modifier Modifier
 */
@Composable
private fun SortFieldDropdown(
    selectedField: SortField,
    onFieldSelected: (SortField) -> Unit,
    modifier: Modifier = Modifier
) {
    val allFields = SortField.entries

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable {
                /** 循环切换到下一个字段 */
                val currentIndex = allFields.indexOf(selectedField)
                val nextIndex = (currentIndex + 1) % allFields.size
                onFieldSelected(allFields[nextIndex])
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = selectedField.displayName,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Icon(
                painter = painterResource(id = android.R.drawable.arrow_down_float),
                contentDescription = "切换排序字段",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 排序方向切换按钮
 *
 * 在 ASCENDING 和 DESCENDING 之间切换。
 *
 * @param currentDirection 当前方向
 * @param onDirectionChanged 方向变更回调
 */
@Composable
private fun SortDirectionToggle(
    currentDirection: SortDirection,
    onDirectionChanged: (SortDirection) -> Unit
) {
    val isAscending = currentDirection == SortDirection.ASCENDING
    val backgroundColor = if (isAscending) {
        Color(0xFFFFE0C0) /** 升序：浅暖橙 */
    } else {
        MaterialTheme.colorScheme.primaryContainer /** 降序：主题色 */
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable {
                onDirectionChanged(
                    if (isAscending) SortDirection.DESCENDING else SortDirection.ASCENDING
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isAscending) "↑ 升序" else "↓ 降序",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isAscending) Color(0xFFE88A4D) else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 添加排序级别按钮
 *
 * 用于引导用户添加下一级排序条件。
 *
 * @param text 按钮显示文本
 * @param onClick 点击回调
 */
@Composable
private fun AddSortLevelButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFFFF9A5C), /** 暖橙色 */
            fontWeight = FontWeight.Medium
        )
    }
}
