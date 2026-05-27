package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 提醒设置组件
 * 用于设置重复日期的提前提醒天数，仅在 repeatType != 0 时显示
 */
@Composable
fun ReminderSetting(
    reminderDays: Int,
    onDaysChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomInput by remember { mutableStateOf(false) }
    var customDays by remember { mutableStateOf(reminderDays.toString()) }

    val presetOptions = listOf(1, 3, 7)

    Column(modifier = modifier) {
        Text(
            text = "提前提醒",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetOptions.forEach { days ->
                SelectableChip(
                    selected = (!showCustomInput && reminderDays == days),
                    onClick = {
                        showCustomInput = false
                        onDaysChanged(days)
                    },
                    label = "${days}天"
                )
            }

            SelectableChip(
                selected = showCustomInput || (reminderDays !in presetOptions && reminderDays > 0),
                onClick = { showCustomInput = true },
                label = if (showCustomInput || (reminderDays !in presetOptions && reminderDays > 0)) "${reminderDays}天" else "自定义"
            )
        }

        if (showCustomInput) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customDays,
                    onValueChange = { input ->
                        customDays = input.filter { it.isDigit() }
                        customDays.toIntOrNull()?.let { days ->
                            if (days in 1..30) {
                                onDaysChanged(days)
                            }
                        }
                    },
                    label = { Text("自定义天数") },
                    placeholder = { Text("输入1-30") },
                    singleLine = true,
                    modifier = Modifier.width(120.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "天",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
