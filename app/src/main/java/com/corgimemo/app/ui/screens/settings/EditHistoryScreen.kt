package com.corgimemo.app.ui.screens.settings

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corgimemo.app.viewmodel.EditHistoryViewModel
import com.corgimemo.app.viewmodel.TimelineType
import kotlinx.coroutines.launch

/**
 * 编辑历史时间线页面
 *
 * 以垂直时间轴形式展示当前 Todo 的完整编辑历史记录，
 * 支持查看每次文本变更的内容预览。
 *
 * **双入口**:
 * 1. 编辑器入口：TodoEditScreen 工具栏的时钟图标按钮
 * 2. 设置页入口：OperationHistoryScreen 的 Tab 切换
 *
 * @param viewModel 编辑历史 ViewModel（提供时间线数据）
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHistoryScreen(
    viewModel: EditHistoryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onBack: () -> Unit,
    /** V2.6: 当前编辑的 Todo ID（用于按 TodoId 隔离加载历史） */
    todoId: Long = -1L,
    /** V2.6: 点击时间线条目时的回调（将目标文本传回编辑器） */
    onRestoreText: ((String) -> Unit)? = null
) {
    /** 收集 UI 状态 */
    val timelineEntries by viewModel.timelineEntries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    /** 是否显示清空确认对话框 */
    var showClearDialog by remember { mutableStateOf(false) }

    /** 协程作用域 */
    val coroutineScope = rememberCoroutineScope()

    /** 页面加载时自动获取数据（V2.6: 传入todoId） */
    LaunchedEffect(todoId) {
        if (todoId >= 0) {
            viewModel.loadTimeline(todoId)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "编辑历史",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (timelineEntries.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "清空历史"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                /** 加载中状态 */
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                /** 空状态 */
                timelineEntries.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📝",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无编辑历史",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "编辑内容时的撤销/重做记录会显示在这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                /** 有数据：显示垂直时间线 */
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 16.dp
                        )
                    ) {
                        itemsIndexed(
                            items = timelineEntries.reversed(), /** 最新的在前（倒序显示） */
                            key = { _, entry -> "${entry.type.name}_${entry.id}" }
                        ) { index, entry ->
                            TimelineItem(
                                entry = entry,
                                isLast = index == timelineEntries.lastIndex,
                                /** V2.7: 点击恢复回调（优先传递 annotatedJson 以保留格式） */
                                onClick = if (onRestoreText != null) {
                                    {
                                        /** 优先使用完整序列化 JSON（含 SpanStyle），回退到纯文本 */
                                        val restoreData = entry.annotatedJson.ifBlank { entry.fullText }
                                        onRestoreText(restoreData)
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    /** 清空确认对话框 */
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空编辑历史") },
            text = { Text("确定要清空所有编辑历史记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (todoId >= 0) viewModel.clearHistory(todoId)
                        showClearDialog = false
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 单个时间线条目组件
 *
 * 垂直时间轴上的一个节点，包含：
 * - 左侧圆点指示器（当前状态高亮）
 * - 连接线（非最后一项时显示）
 * - 右侧内容卡片（类型标签 + 内容预览）
 *
 * @param entry 时间线条目数据
 * @param 是否为最后一个条目（控制连接线是否显示）
 */
@Composable
private fun TimelineItem(
    entry: com.corgimemo.app.viewmodel.EditTimelineEntry,
    isLast: Boolean,
    /** V2.6: 点击恢复回调（非空时启用点击交互） */
    onClick: (() -> Unit)? = null
) {
    /** 提前捕获颜色值，避免在 DrawScope（非 @Composable 上下文）中读取 MaterialTheme */
    val currentDotColor = if (entry.isCurrent)
        androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.8f)
    else
        if (entry.type == TimelineType.UNDO_SNAPSHOT)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)

    val innerDotColor = androidx.compose.ui.graphics.Color.White
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val cardBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(with(LocalDensity.current) { 8.dp.toPx() })

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (onClick != null)
                    Modifier.clickable(onClick = onClick)
                else
                    Modifier
            )
    ) {
        /** ===== 左侧时间轴线 ===== */
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            /** 时间轴圆点 */
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .then(
                        if (entry.isCurrent)
                            Modifier.drawBehind {
                                drawCircle(color = currentDotColor)
                            }
                        else
                            Modifier.drawBehind {
                                drawCircle(color = currentDotColor)
                            }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (entry.isCurrent) {
                    /** 当前状态指示器：内圈白点 */
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .then(Modifier.drawBehind {
                                drawCircle(color = innerDotColor)
                            })
                    )
                }
            }

            /** 连接线（非最后一项时绘制） */
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .then(Modifier.drawBehind {
                            drawRect(color = lineColor)
                        })
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        /** ===== 右侧内容卡片 ===== */
        Card(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (entry.isCurrent)
                        Modifier.drawBehind {
                            drawRoundRect(
                                color = cardBgColor,
                                cornerRadius = cornerRadius
                            )
                        }
                    else
                        Modifier
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (entry.isCurrent)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (entry.isCurrent) 2.dp else 0.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                /** 类型标签 + 当前状态标记 */
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /** 类型标签 */
                    Text(
                        text = when (entry.type) {
                            TimelineType.UNDO_SNAPSHOT -> "撤销"
                            TimelineType.REDO_SNAPSHOT -> "重做"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (entry.type) {
                            TimelineType.UNDO_SNAPSHOT -> MaterialTheme.colorScheme.primary
                            TimelineType.REDO_SNAPSHOT -> MaterialTheme.colorScheme.secondary
                        },
                        fontWeight = FontWeight.Medium
                    )

                    /** 当前状态标记 */
                    if (entry.isCurrent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "· 当前",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    /** 序号 */
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "#${entry.id + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                /** 内容预览 */
                Text(
                    text = entry.contentPreview.ifEmpty { "(空内容)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
