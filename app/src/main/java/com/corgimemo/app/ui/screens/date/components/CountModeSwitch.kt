package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 计时模式切换组件
 * 二选一的计时模式选择：
 * - ⏳ 倒计时 (mode 0)：计算距离目标日期还有多少天
 * - ⏱️ 正计时 (mode 1)：计算从目标日期开始已经过了多少天
 */
@Composable
fun CountModeSwitch(
    selectedMode: Int,
    onModeChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectableChip(
            selected = (selectedMode == 0),
            onClick = { onModeChanged(0) },
            label = "⏳ 倒计时"
        )

        SelectableChip(
            selected = (selectedMode == 1),
            onClick = { onModeChanged(1) },
            label = "⏱️ 正计时"
        )
    }
}
