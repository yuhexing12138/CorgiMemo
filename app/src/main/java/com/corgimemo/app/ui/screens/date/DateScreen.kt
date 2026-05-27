package com.corgimemo.app.ui.screens.date

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 特殊日期数据模型
 */
data class SpecialDate(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val date: Date,
    val category: DateCategory = DateCategory.OTHER,
    val description: String = ""
)

/**
 * 日期分类枚举
 */
enum class DateCategory(val displayName: String, val emoji: String, val color: Color) {
    BIRTHDAY("生日", "🎂", Color(0xFFFF6B9D)),
    ANNIVERSARY("纪念日", "💕", Color(0xFFFF9A5C)),
    HOLIDAY("节日", "🎉", Color(0xFF4ECDC4)),
    OTHER("其他", "📅", Color(0xFF95E1D3))
}

/**
 * 特殊日期页面
 * 管理和显示重要日期，支持倒计时功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateScreenPlaceholder() {
    // 状态管理：特殊日期列表（使用 remember + mutableStateListOf）
    val dateList = remember { mutableStateListOf<SpecialDate>() }

    // 状态管理：是否显示添加对话框
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (dateList.isEmpty()) {
            // 空状态显示
            EmptyDateState(onAddClick = { showAddDialog = true })
        } else {
            // 日期列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = dateList.sortedBy { it.date },
                    key = { date -> date.id }
                ) { specialDate ->
                    DateCard(
                        item = specialDate,
                        onDelete = {
                            dateList.removeAll { date -> date.id == specialDate.id }
                        }
                    )
                }
            }
        }

        // 悬浮添加按钮
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加日期",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // 添加日期对话框
        if (showAddDialog) {
            AddDateDialog(
                onConfirm = { newDate ->
                    dateList.add(0, newDate)
                    showAddDialog = false
                },
                onDismiss = { showAddDialog = false }
            )
        }
    }
}

/**
 * 空状态组件
 */
@Composable
private fun EmptyDateState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onAddClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "记录重要日期",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "点击右下角 + 按钮添加第一个特殊日期",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 日期卡片组件
 * 显示倒计时和日期信息
 */
@Composable
private fun DateCard(
    item: SpecialDate,
    onDelete: () -> Unit
) {
    // 计算倒计时
    val daysRemaining = remember(item.date) {
        calculateDaysRemaining(item.date)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* 可扩展：点击查看详情 */ }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 倒计时圆圈
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(item.category.color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = abs(daysRemaining).toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = item.category.color
                    )
                    Text(
                        text = if (daysRemaining > 0) "天后" else if (daysRemaining < 0) "天前" else "今天",
                        fontSize = 10.sp,
                        color = item.category.color
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 日期详情
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 标题
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 分类标签
                    Text(
                        text = "${item.category.emoji} ${item.category.displayName}",
                        fontSize = 11.sp,
                        color = item.category.color,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 日期显示
                Text(
                    text = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(item.date),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 描述（如果有）
                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.description,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * 计算距离目标日期的天数
 */
private fun calculateDaysRemaining(targetDate: Date): Long {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val target = targetDate.time

    return TimeUnit.MILLISECONDS.toDays(target - today)
}

/**
 * 添加日期对话框
 */
@Composable
private fun AddDateDialog(
    onConfirm: (SpecialDate) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DateCategory.BIRTHDAY) }
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }
    var description by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "📅 添加特殊日期")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 标题输入
                androidx.compose.material3.OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    placeholder = { Text("例如：小明生日") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // 分类选择
                Text(text = "分类：", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateCategory.entries.forEach { category ->
                        val isSelected = selectedCategory == category
                        androidx.compose.material3.FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = {
                                Text("${category.emoji} ${category.displayName}", fontSize = 12.sp)
                            },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                // 描述输入（可选）
                androidx.compose.material3.OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    placeholder = { Text("备注信息...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        val calendar = Calendar.getInstance()
                        calendar.set(selectedYear, selectedMonth - 1, selectedDay)

                        onConfirm(
                            SpecialDate(
                                title = title.trim(),
                                date = calendar.time,
                                category = selectedCategory,
                                description = description.trim()
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
