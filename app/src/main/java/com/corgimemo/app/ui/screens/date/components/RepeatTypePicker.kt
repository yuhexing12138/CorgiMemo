package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 重复类型选择器组件
 * 三选一的重复类型选择：
 * - 不重复 (type 0)：日期仅出现一次
 * - 按年重复 (type 1)：每年同月同日重复（如生日）
 * - 按月重复 (type 2)：每月同日重复（如月度纪念日）
 */
@Composable
fun RepeatTypePicker(
    selectedType: Int,
    onTypeChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val repeatOptions = listOf(
        Pair(0, "不重复"),
        Pair(1, "按年重复"),
        Pair(2, "按月重复")
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeatOptions.forEach { (type, label) ->
            SelectableChip(
                selected = (selectedType == type),
                onClick = { onTypeChanged(type) },
                label = label
            )
        }
    }
}
