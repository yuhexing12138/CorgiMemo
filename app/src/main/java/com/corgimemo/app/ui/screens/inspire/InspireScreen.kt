package com.corgimemo.app.ui.screens.inspire

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 灵感记录数据模型
 */
data class InspireItem(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val createdAt: Date = Date(),
    val category: InspireCategory = InspireCategory.IDEA
)

/**
 * 灵感分类枚举
 */
enum class InspireCategory(val displayName: String, val emoji: String) {
    IDEA("想法", "💡"),
    QUOTE("语录", "✍️"),
    DREAM("梦想", "🌟"),
    GOAL("目标", "🎯")
}

/**
 * 灵感记录页面
 * 提供快速记录和浏览灵感的功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspireScreenPlaceholder() {
    // 状态管理：灵感列表（使用 remember + mutableStateListOf）
    val inspireList = remember { mutableStateListOf<InspireItem>() }

    // 状态管理：是否显示添加对话框
    var showAddDialog by remember { mutableStateOf(false) }

    // 新灵感内容输入
    var newContent by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (inspireList.isEmpty()) {
            // 空状态显示
            EmptyInspireState(onAddClick = { showAddDialog = true })
        } else {
            // 灵感列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = inspireList,
                    key = { item -> item.id }
                ) { inspire ->
                    InspireCard(
                        item = inspire,
                        onDelete = {
                            inspireList.removeAll { item -> item.id == inspire.id }
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
                contentDescription = "添加灵感",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // 添加灵感对话框
        if (showAddDialog) {
            AddInspireDialog(
                content = newContent,
                onContentChange = { newContent = it },
                onConfirm = {
                    if (newContent.isNotBlank()) {
                        inspireList.add(
                            0,
                            InspireItem(content = newContent.trim())
                        )
                        newContent = ""
                        showAddDialog = false
                    }
                },
                onDismiss = {
                    showAddDialog = false
                    newContent = ""
                }
            )
        }
    }
}

/**
 * 空状态组件
 * 当没有灵感记录时显示
 */
@Composable
private fun EmptyInspireState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onAddClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "记录你的灵感",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "点击右下角 + 按钮添加第一条灵感",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 灵感卡片组件
 * 显示单条灵感内容
 */
@Composable
private fun InspireCard(
    item: InspireItem,
    onDelete: () -> Unit
) {
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
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分类标签
                Text(
                    text = "${item.category.emoji} ${item.category.displayName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                // 时间戳
                Text(
                    text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(item.createdAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 灵感内容
            Text(
                text = item.content,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 添加灵感对话框
 */
@Composable
private fun AddInspireDialog(
    content: String,
    onContentChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "✨ 记录新灵感")
        },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                label = { Text("灵感内容") },
                placeholder = { Text("写下你的想法...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = onConfirm,
                enabled = content.isNotBlank()
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
