package com.corgimemo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.ui.components.CircularCheckbox
import com.corgimemo.app.ui.components.PriorityDot
import com.corgimemo.app.ui.components.TodoActionSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 待办列表项组件
 *
 * @param todo 待办数据
 * @param subTaskProgress 子任务进度（如 "2/5"，无子任务时为 null）
 * @param subTasks 子任务列表
 * @param isExpanded 是否展开显示子任务
 * @param isBatchMode 是否处于批量选择模式
 * @param isSelected 是否已选中（批量模式下）
 * @param isDragging 是否正在被拖拽（用于调整 DragHandle 等子组件样式）
 * @param categoryName 分类名称
 * @param categoryIcon 分类图标（emoji）
 * @param onToggleComplete 切换完成状态回调
 * @param onDelete 删除回调
 * @param onClick 点击回调（普通模式）
 * @param onLongClick 长按回调（进入批量模式）
 * @param onSelectClick 选择回调（批量模式下点击）
 * @param onShareAsImage 分享为图片回调
 * @param onToggleExpand 切换展开状态回调
 * @param onToggleSubTask 切换子任务完成状态回调
 * @param start 前置内容槽位（用于放置 DragHandle 等拖拽相关 UI）
 * @param relationHint 关联提示文字
 * @param searchQuery 搜索关键词（非空时对标题和内容进行高亮显示）
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TodoListItem(
    todo: TodoItem,
    subTaskProgress: String? = null,
    subTasks: List<SubTask> = emptyList(),
    isExpanded: Boolean = false,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    isDragging: Boolean = false,
    categoryName: String? = null,
    categoryIcon: String? = null,
    onToggleComplete: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectClick: () -> Unit = {},
    onShareAsImage: () -> Unit = {},
    onToggleExpand: () -> Unit = {},
    onToggleSubTask: (Long) -> Unit = {},
    start: @Composable () -> Unit = {},
    relationHint: String? = null,
    /** 搜索关键词（非空时对标题和内容进行高亮显示） */
    searchQuery: String = ""
) {
    val deleteWidth = 80.dp
    var offsetX by remember { mutableStateOf(0f) }

    /**
     * V2.5 逐区间交错淡入动画控制标志
     *
     * 当 searchQuery 从空变为非空（搜索结果首次出现）时设为 true，
     * 触发所有高亮区间的交错淡入动画序列。
     * 每个区间的延迟由其在原文中的位置决定（startIndex * 2ms），
     * 形成「从左到右波浪扫描」的视觉效果。
     *
     * 相比 V2.4 的整体 alpha 模式，逐区间独立动画提供更精细的视觉反馈：
     * - 用户可直观看到每个匹配位置的出现顺序
     * - 多关键词搜索时，不同关键词的高亮依次亮起，层次分明
     */
    val isHighlightActive = searchQuery.isNotBlank()

    /** 逐区间动画参数：每字符延迟 2ms，最大延迟上限 300ms */
    val STAGGER_DELAY_PER_CHAR = 2
    val STAGGER_MAX_DELAY = 300

    val cardBackground by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardBackground"
    )

    val checkboxStartPadding by animateDpAsState(
        targetValue = if (isBatchMode) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "checkboxStartPadding"
    )

    var showLongPressMenu by remember { mutableStateOf(false) }

    val actionSheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        if (!isBatchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .width(deleteWidth)
                        .height(68.dp)
                        .background(Color(0xFFEF4444))
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .offset(x = Dp(offsetX))
                .pointerInput(isBatchMode) {
                    if (!isBatchMode) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX < -deleteWidth.value / 2) {
                                    onDelete(todo.id)
                                }
                                offsetX = 0f
                            }
                        ) { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-deleteWidth.value, 0f)
                        }
                    }
                }
                .combinedClickable(
                    onClick = {
                        if (isBatchMode) {
                            onSelectClick()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = {
                        if (isBatchMode) {
                            onLongClick()
                        } else {
                            showLongPressMenu = true
                        }
                    },
                    role = Role.Tab
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBatchMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectClick() },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    } else {
                        if (checkboxStartPadding > 0.dp) {
                            Spacer(modifier = Modifier.width(checkboxStartPadding))
                        }
                        CircularCheckbox(
                            checked = todo.status == 1,
                            onCheckedChange = { isChecked ->
                                onToggleComplete(todo.id, isChecked)
                            },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }

                    /**
                     * 前置内容槽位（start slot）
                     *
                     * 用于放置 DragHandle 等拖拽相关的 UI 组件。
                     * 默认为空（不渲染任何内容），
                     * 当与 ReorderableLazyColumn 配合使用时，
                     * 可在此处插入 VerticalDragIndicator。
                     */
                    start()

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            /**
                             * 标题文本（支持逐区间交错淡入高亮）
                             *
                             * V2.5 改造：使用 buildHighlightRanges() 拆分为独立区间列表，
                             * 每个高亮区间拥有独立的 animateFloatAsState + 延迟，
                             * 实现从左到右的波浪式淡入效果。
                             */
                            if (isHighlightActive) {
                                val (titleRanges, titleHighlightColor) =
                                    com.corgimemo.app.util.HighlightUtil.buildHighlightRanges(
                                        text = todo.title,
                                        searchQuery = searchQuery,
                                        containerBgColor = if (todo.backgroundColor != 0)
                                            androidx.compose.ui.graphics.Color(todo.backgroundColor) else null
                                    )
                                /** 逐区间渲染：每个 HighlightRange 独立动画 */
                                androidx.compose.foundation.layout.Row {
                                    titleRanges.forEach { range ->
                                        val rangeAlpha by androidx.compose.animation.core.animateFloatAsState(
                                            targetValue = 1f,
                                            animationSpec = androidx.compose.animation.core.tween(
                                                durationMillis = 300,
                                                delayMillis = (range.startIndex * STAGGER_DELAY_PER_CHAR)
                                                    .coerceAtMost(STAGGER_MAX_DELAY)
                                            ),
                                            label = "titleRangeAlpha_${range.startIndex}"
                                        )
                                        Text(
                                            text = range.text,
                                            fontSize = 16.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                            textDecoration = if (todo.status == 1) TextDecoration.LineThrough else TextDecoration.None,
                                            color = if (todo.status == 1) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.graphicsLayer { alpha = rangeAlpha },
                                            style = if (range.isHighlight) androidx.compose.ui.text.TextStyle(
                                                background = titleHighlightColor
                                            ) else androidx.compose.ui.text.TextStyle.Default
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = todo.title,
                                    fontSize = 16.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    textDecoration = if (todo.status == 1) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (todo.status == 1) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            subTaskProgress?.let { progress ->
                                Text(
                                    text = " ($progress)",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            }
                        }
                        if (todo.status == 1 && todo.completedAt != null) {
                            Text(
                                text = formatCompletedTime(todo.completedAt),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else if (!todo.content.isNullOrBlank() || todo.contentFormat.isNotBlank()) {
                            /**
                             * 条件渲染：优先使用富文本格式预览
                             *
                             * 当 contentFormat 不为空时，使用 MarkdownInlineText 渲染格式化内容
                             * （保留粗体/斜体/删除线样式），否则回退到纯文本显示。
                             *
                             * 注意：contentFormat 可能包含行级附件快照数据（格式为 "{Markdown}|||LINE_ATTACHMENTS|||[{JSON}]"），
                             * 需要先提取纯净的 Markdown 部分用于显示。
                             */
                            val displayContentFormat = com.corgimemo.app.ui.model.LineSnapshotUtils.extractDisplayContent(todo.contentFormat)
                            if (displayContentFormat.isNotBlank()) {
                                /**
                                 * 富文本内容（支持逐区间交错淡入高亮 + Markdown样式保留）
                                 *
                                 * V2.6 改造：搜索激活时使用 buildStyledHighlightRanges() 逐区间渲染，
                                 * 保留粗体/斜体/删除线等 Markdown 行内样式，每个区间独立淡入动画。
                                 * 非搜索时使用 MarkdownInlineText。
                                 */
                                if (isHighlightActive) {
                                    /** V2.6: 使用带样式的搜索高亮（保留Markdown格式） */
                                    val (styledMdRanges, styledMdColor) =
                                        com.corgimemo.app.util.HighlightUtil.buildStyledHighlightRanges(
                                            markdown = displayContentFormat,
                                            searchQuery = searchQuery,
                                            containerBgColor = if (todo.backgroundColor != 0)
                                                androidx.compose.ui.graphics.Color(todo.backgroundColor) else null
                                        )
                                    androidx.compose.foundation.layout.Row {
                                        styledMdRanges.forEach { range ->
                                            val rangeAlpha by androidx.compose.animation.core.animateFloatAsState(
                                                targetValue = 1f,
                                                animationSpec = androidx.compose.animation.core.tween(
                                                    durationMillis = 300,
                                                    delayMillis = (range.startIndex * STAGGER_DELAY_PER_CHAR)
                                                        .coerceAtMost(STAGGER_MAX_DELAY)
                                                ),
                                                label = "styledMdAlpha_${range.startIndex}"
                                            )
                                            /** 构建基础 TextStyle：颜色 + 字号 + 高亮背景 */
                                            val baseStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                background = if (range.isHighlight) styledMdColor else androidx.compose.ui.graphics.Color.Unspecified
                                            )
                                            /** 合并 Markdown 样式（粗体/斜体等） */
                                            val finalStyle = if (range.spanStyle != null) {
                                                baseStyle.merge(
                                                    androidx.compose.ui.text.TextStyle(
                                                        fontWeight = range.spanStyle.fontWeight.takeIf { it != androidx.compose.ui.text.font.FontWeight.Normal },
                                                        fontStyle = range.spanStyle.fontStyle.takeIf { it != androidx.compose.ui.text.font.FontStyle.Normal },
                                                        textDecoration = range.spanStyle.textDecoration.takeIf { it != TextDecoration.None }
                                                    )
                                                )
                                            } else {
                                                baseStyle
                                            }

                                            Text(
                                                text = range.text,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.graphicsLayer { alpha = rangeAlpha },
                                                style = finalStyle
                                            )
                                        }
                                    }
                                } else {
                                    MarkdownInlineText(
                                        markdown = displayContentFormat,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            } else {
                                /**
                                 * 纯文本内容（支持逐区间交错淡入高亮）
                                 *
                                 * V2.5 改造：使用 buildHighlightRanges() 逐区间独立动画渲染
                                 */
                                if (isHighlightActive) {
                                    val (contentRanges, contentHighlightColor) =
                                        com.corgimemo.app.util.HighlightUtil.buildHighlightRanges(
                                            text = todo.content ?: "",
                                            searchQuery = searchQuery,
                                            containerBgColor = if (todo.backgroundColor != 0)
                                                androidx.compose.ui.graphics.Color(todo.backgroundColor) else null
                                        )
                                    androidx.compose.foundation.layout.Row {
                                        contentRanges.forEach { range ->
                                            val rangeAlpha by androidx.compose.animation.core.animateFloatAsState(
                                                targetValue = 1f,
                                                animationSpec = androidx.compose.animation.core.tween(
                                                    durationMillis = 300,
                                                    delayMillis = (range.startIndex * STAGGER_DELAY_PER_CHAR)
                                                        .coerceAtMost(STAGGER_MAX_DELAY)
                                                ),
                                                label = "contentRangeAlpha_${range.startIndex}"
                                            )
                                            Text(
                                                text = range.text,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.graphicsLayer { alpha = rangeAlpha },
                                                style = if (range.isHighlight) androidx.compose.ui.text.TextStyle(
                                                    background = contentHighlightColor
                                                ) else androidx.compose.ui.text.TextStyle.Default
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = todo.content ?: "",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (categoryName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = categoryIcon ?: "📋",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = categoryName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )

                                // 语音备注图标
                                if (todo.voiceNotePath != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "🎤",
                                        fontSize = 12.sp
                                    )
                                    todo.voiceDuration?.let { duration ->
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${duration}s",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else if (todo.voiceNotePath != null) {
                            // 无分类但有语音备注时单独显示
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "🎤",
                                    fontSize = 12.sp
                                )
                                todo.voiceDuration?.let { duration ->
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "语音备注 ${duration}s",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 关联提示
                        if (relationHint != null) {
                            Text(
                                text = relationHint,
                                fontSize = 12.sp,
                                color = Color(0xFF999999),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // 开始时间显示
                        if (todo.startDate != null) {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                val timeDisplayText = todo.estimatedDurationMinutes?.let { duration ->
                                    val endTime = todo.startDate + duration * 60 * 1000
                                    formatTimeRange(todo.startDate, endTime)
                                } ?: formatDateTime(todo.startDate)

                                Text(
                                    text = "\uD83D\uDD51 $timeDisplayText",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (todo.status != 1 && System.currentTimeMillis() < todo.startDate) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    CountdownDisplay(startDate = todo.startDate)
                                }
                            }
                        }

                        /** 截止时间（dueDate）显示 */
                        if (todo.dueDate != null) {
                            val isOverdue = todo.status != 1 && todo.dueDate!! < System.currentTimeMillis()
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    text = "\u23F0 ${formatDateTime(todo.dueDate!!)}${if (isOverdue) " (已过期)" else ""}",
                                    fontSize = 12.sp,
                                    color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isOverdue) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            }
                        }

                        if (todo.status != 1 && todo.content.isNullOrBlank() && subTaskProgress != null) {
                            SubTaskProgressBar(
                                progress = parseProgress(subTaskProgress),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    // 展开/收起按钮（仅在有子任务且非批量模式时显示）
                    if (subTaskProgress != null && !isBatchMode) {
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = if (isExpanded) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        PriorityDot(priority = todo.priority.toTodoPriority())
                    }
                }

                // 展开时显示子任务列表
                if (isExpanded && subTasks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        subTasks.forEach { subTask ->
                            SubTaskInTodoListItem(
                                subTask = subTask,
                                onToggleComplete = { onToggleSubTask(subTask.id) }
                            )
                            if (subTask != subTasks.last()) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showLongPressMenu) {
            TodoActionSheet(
                sheetState = actionSheetState,
                onDismiss = {
                    showLongPressMenu = false
                },
                onEdit = {
                    onClick()
                },
                onShare = {
                    onShareAsImage()
                },
                onBatchSelect = {
                    onLongClick()
                },
                onDelete = {
                    onDelete(todo.id)
                }
            )
        }
    }
}

/**
 * 格式化完成时间为友好的显示文本
 *
 * @param completedAt 完成时间戳（毫秒）
 * @return 格式化后的时间文本，如 "3 分钟前完成"、"2 小时前完成"、"5 天前完成"
 */
private fun formatCompletedTime(completedAt: Long): String {
    val diffMillis = System.currentTimeMillis() - completedAt
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

    return when {
        diffMinutes < 1 -> "刚刚完成"
        diffMinutes < 60 -> "$diffMinutes 分钟前完成"
        diffHours < 24 -> "$diffHours 小时前完成"
        else -> "$diffDays 天前完成"
    }
}

/**
 * 子任务进度条组件
 *
 * @param progress 进度值 (0.0-1.0)
 * @param modifier Modifier
 */
@Composable
private fun SubTaskProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val warmOrange = Color(0xFFF97316)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(trackColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            val trackWidth = size.width
            val progressWidth = trackWidth * progress.coerceIn(0f, 1f)
            val height = size.height

            // 绘制进度条（暖橙色）
            if (progressWidth > 0f) {
                drawRoundRect(
                    color = warmOrange,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(progressWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

/**
 * 解析进度文本为浮点值
 *
 * @param progressText 进度文本，如 "2/5"
 * @return 进度值 (0.0-1.0)，解析失败返回 0f
 */
private fun parseProgress(progressText: String): Float {
    return try {
        val parts = progressText.split("/")
        if (parts.size == 2) {
            val completed = parts[0].toInt()
            val total = parts[1].toInt()
            if (total > 0) completed.toFloat() / total.toFloat() else 0f
        } else {
            0f
        }
    } catch (e: Exception) {
        0f
    }
}

/**
 * 待办列表中的子任务项组件
 * 用于展开待办后显示的子任务列表
 *
 * @param subTask 子任务
 * @param onToggleComplete 切换完成状态回调
 * @param modifier Modifier
 */
@Composable
private fun SubTaskInTodoListItem(
    subTask: SubTask,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圆形复选框
        SubTaskCheckbox(
            isCompleted = subTask.isCompleted,
            onClick = onToggleComplete
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 子任务标题
        Text(
            text = subTask.title,
            fontSize = 14.sp,
            color = if (subTask.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (subTask.isCompleted) {
                TextDecoration.LineThrough
            } else {
                TextDecoration.None
            },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 子任务复选框组件（与SubTaskListItem一致）
 *
 * @param isCompleted 是否已完成
 * @param onClick 点击回调
 */
@Composable
private fun SubTaskCheckbox(
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 格式化日期时间为显示文本
 *
 * @param timestamp 时间戳（毫秒）
 * @return 格式化后的日期时间文本，如 "05-15 14:30"
 */
private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 格式化时间范围为显示文本
 *
 * @param startTime 开始时间戳（毫秒）
 * @param endTime 结束时间戳（毫秒）
 * @return 格式化后的时间范围文本
 *         同一天：05-22 15:00 至 17:30
 *         隔天：05-22 15:00 至 05-23 17:30
 */
private fun formatTimeRange(startTime: Long, endTime: Long): String {
    val sdfDate = SimpleDateFormat("MM-dd", Locale.getDefault())
    val sdfDateTime = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

    val startDate = Date(startTime)
    val endDate = Date(endTime)

    val cal1 = java.util.Calendar.getInstance().apply { time = startDate }
    val cal2 = java.util.Calendar.getInstance().apply { time = endDate }

    val isSameDay = cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                    cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)

    return if (isSameDay) {
        "${sdfDate.format(startDate)} ${sdfTime.format(startDate)} 至 ${sdfTime.format(endDate)}"
    } else {
        "${sdfDateTime.format(startDate)} 至 ${sdfDateTime.format(endDate)}"
    }
}

/**
 * 计算并格式化距离开始时间的剩余时间
 *
 * @param startDate 开始时间戳（毫秒）
 * @param currentTime 当前时间戳（毫秒）
 * @return 格式化后的剩余时间文本，大于1小时显示到分钟，小于等于1小时显示到秒
 */
private fun formatCountdown(startDate: Long, currentTime: Long): String {
    val diffMillis = startDate - currentTime

    if (diffMillis <= 0) return "已开始"

    val diffSeconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

    return if (diffHours > 0) {
        when {
            diffDays > 0 -> "还剩 ${diffDays}天 ${diffHours % 24}时${diffMinutes % 60}分"
            else -> "还剩 ${diffHours}时${diffMinutes % 60}分"
        }
    } else {
        "还剩 ${diffMinutes}分${diffSeconds % 60}秒"
    }
}

/**
 * 倒计时显示组件
 * 实时显示距离开始时间的剩余时间
 *
 * @param startDate 开始时间戳（毫秒）
 * @param onExpired 倒计时结束时的回调
 */
@Composable
private fun CountdownDisplay(
    startDate: Long,
    onExpired: () -> Unit = {}
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(startDate) {
        while (true) {
            val now = System.currentTimeMillis()
            currentTime = now

            if (now >= startDate) {
                onExpired()
                break
            }

            val remainingMillis = startDate - now
            val delayMillis = if (remainingMillis > 3600000L) {
                60000L
            } else {
                1000L
            }

            delay(delayMillis)
        }
    }

    val countdownText = formatCountdown(startDate, currentTime)

    Text(
        text = "⏳ $countdownText",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.primary
    )
}
