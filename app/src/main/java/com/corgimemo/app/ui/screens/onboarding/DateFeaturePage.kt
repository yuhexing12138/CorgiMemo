package com.corgimemo.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.viewmodel.DateCategory
import com.corgimemo.app.viewmodel.OnboardingViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 一天的毫秒数 */
private const val DAY_IN_MILLIS = 24L * 60L * 60L * 1000L

/**
 * 日期功能介绍页
 *
 * 上半部分：功能介绍（5 个 emoji 卖点）
 * 下半部分：表单（标题 + 日期选择 + 4 个类别 Chip）或成功态（PROUD 动画 + 迷你倒计时卡片）
 * 创建成功后显示柯基 PROUD 动画
 * 可跳过
 *
 * @param viewModel 引导 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFeaturePage(
    viewModel: OnboardingViewModel
) {
    val createdDateCount by viewModel.createdDateCount.collectAsState()

    var dateTitle by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableLongStateOf(todayStartMillis()) }
    var selectedCategory by remember { mutableStateOf(DateCategory.BIRTHDAY) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }
    var createdDate by remember { mutableStateOf<SpecialDate?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上半：功能介绍
            Text(
                text = "📅",
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "日期功能",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "记录重要的日子",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 下半：表单或成功态
            if (showSuccessAnimation && createdDate != null) {
                // 成功态
                FrameAnimation(
                    animationType = AnimationType.PROUD,
                    fps = 12,
                    isLooping = true,
                    modifier = Modifier.size(120.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "🎉 日期创建成功！",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                DateCardMiniPreview(
                    date = createdDate!!,
                    daysUntil = daysUntil(createdDate!!.targetDate)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "点击下一步继续",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 表单态
                OutlinedTextField(
                    value = dateTitle,
                    onValueChange = { dateTitle = it },
                    label = { Text(text = "创建你的第一个日期") },
                    placeholder = { Text(text = "例如：我的生日") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 日期选择
                Text(
                    text = "日期：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDate(selectedDateMillis),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(text = "📅", fontSize = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 类别选择
                Text(
                    text = "类别：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 类别选择：2 行 × 2 个居中显示
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 第一行：生日 + 纪念日
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            DateCategory.BIRTHDAY to "🎂 生日",
                            DateCategory.ANNIVERSARY to "💝 纪念日"
                        ).forEach { (cat, label) ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(label) }
                            )
                        }
                    }
                    // 第二行：节日 + 其他
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            DateCategory.HOLIDAY to "🎉 节日",
                            DateCategory.OTHER to "📌 其他"
                        ).forEach { (cat, label) ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 创建按钮
                Button(
                    onClick = {
                        if (dateTitle.isNotBlank()) {
                            viewModel.createFirstDate(
                                title = dateTitle,
                                targetDate = selectedDateMillis,
                                category = selectedCategory.name
                            )
                            // 构造展示用的 SpecialDate 对象
                            createdDate = SpecialDate(
                                title = dateTitle,
                                targetDate = selectedDateMillis,
                                category = selectedCategory.name,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            showSuccessAnimation = true
                        }
                    },
                    enabled = dateTitle.isNotBlank(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "创建日期",
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "你也可以跳过，稍后在 APP 中创建",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // 已创建日期计数
            if (createdDateCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已创建 $createdDateCount 个日期",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // 日期选择器弹窗
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                        showDatePicker = false
                    }
                ) {
                    Text(text = "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 日期卡片迷你预览
 * 在成功态展示创建好的日期，包含倒计时天数、目标日期、标题和类别
 *
 * @param date 已创建的日期对象
 * @param daysUntil 距今天数（正数=未来，负数=过去）
 */
@Composable
private fun DateCardMiniPreview(
    date: SpecialDate,
    daysUntil: Int
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(0.8f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 倒计时文案
            Text(
                text = if (daysUntil >= 0) "还有 $daysUntil 天" else "已过 ${-daysUntil} 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 倒计时天数（大字号）
            Text(
                text = daysUntil.toString(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 目标日期
            Text(
                text = formatDate(date.targetDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            // 标题
            Text(
                text = date.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 类别标签
            val (emoji, label) = when (date.category) {
                "BIRTHDAY" -> "🎂" to "生日"
                "ANNIVERSARY" -> "💝" to "纪念日"
                "HOLIDAY" -> "🎉" to "节日"
                else -> "📌" to "其他"
            }
            Text(
                text = "$emoji $label",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 获取今天 0 点的时间戳（毫秒）
 */
private fun todayStartMillis(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/**
 * 格式化日期为 "2026/08/15 周六" 格式
 */
private fun formatDate(millis: Long): String {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd EEE", Locale.CHINA)
    return dateFormat.format(Date(millis))
}

/**
 * 计算从今天到目标日期的天数差
 */
private fun daysUntil(targetMillis: Long): Int {
    val today = todayStartMillis()
    return ((targetMillis - today) / DAY_IN_MILLIS).toInt()
}
