package com.corgimemo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.ui.util.formatReminderDisplay
import com.corgimemo.app.R
import kotlinx.coroutines.delay
import android.widget.Toast
import androidx.compose.ui.res.stringResource
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
 * @param relationHint 关联提示文字
 * @param searchQuery 搜索关键词（非空时对标题和内容进行高亮显示）
 */
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@Composable
fun TodoListItem(
    todo: TodoItem,
    subTaskProgress: String? = null,
    subTasks: List<SubTask> = emptyList(),
    isExpanded: Boolean = false,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
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
    relationHint: String? = null,
    /** 搜索关键词（非空时对标题和内容进行高亮显示） */
    searchQuery: String = "",
    /** 是否启用触觉震动反馈 */
    hapticEnabled: Boolean = true
) {

    /** 逐区间动画参数：每字符延迟 2ms，最大延迟上限 300ms */
    val STAGGER_DELAY_PER_CHAR = 2
    val STAGGER_MAX_DELAY = 300

    /**
     * V2.5 逐区间交错淡入动画控制标志
     *
     * 当 searchQuery 非空时启用，所有高亮区间以「从左到右波浪扫描」方式依次淡入。
     */
    val isHighlightActive = searchQuery.isNotBlank()

    /**
     * 卡片背景色：始终使用 surface，不随选中态变色
     *
     * 背景：早期版本选中时使用 primaryContainer 半透明作为视觉反馈，但实际效果是
     * 整张卡片被橙色覆盖，视觉干扰过大，且与"已完成"待办的视觉降权（dimmed）混淆。
     * 重构后：选中反馈完全交给左侧 CircularCheckbox（橙色填充√），
     * 卡片本身保持 neutral 状态，与未选中态一致。
     */
    val cardBackground = MaterialTheme.colorScheme.surface

    /**
     * 读取内容区水波纹开关
     *
     * - 默认 true：显示水波纹
     * - false：被 SwipeableTodoBox 在其 content() 外层通过
     *   CompositionLocalProvider(LocalContentIndication provides false) 覆盖
     *
     * 设计意图：左滑时外层 detectHorizontalDragGestures 与内部 detectTapGestures
     * 同时看到 down 事件，内部 onPress 会 emit Press → indication 渲染水波纹，
     * 造成"左滑时卡片显示水波纹"的视觉干扰。SwipeableTodoBox 通过 LocalContentIndication
     * 关闭水波纹，左滑过程视觉干净。
     */
    val contentIndicationEnabled = LocalContentIndication.current

    /** 复选框左侧间距：批量模式下空出 8dp 让位给 Checkbox */
    val checkboxStartPadding by animateDpAsState(
        targetValue = if (isBatchMode) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "checkboxStartPadding"
    )

    /** 获取 Android Context，用于震动反馈 */
    val context = LocalContext.current

    /**
     * 多选模式退出提示文案（国际化）
     *
     * 从 strings.xml 读取，支持多语言切换。
     * - 中文：请先退出多选模式
     * - 英文：Please exit batch mode first
     */
    val exitBatchModeHint = stringResource(R.string.todo_batch_exit_hint)

    /** 用于管理水波纹按压交互状态 */
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 内容区水波纹开关：SwipeableTodoBox 在左滑时会将其设为 false，
            // 避免左滑过程中内部 onPress 触发的 Press 事件渲染 indication 水波纹，
            // 造成"左滑时卡片显示水波纹"的视觉干扰
            .run {
                if (contentIndicationEnabled) {
                    this.indication(interactionSource, androidx.compose.material3.ripple())
                } else {
                    this
                }
            }
            .pointerInput(isBatchMode, onClick, onLongClick, onSelectClick) {
                detectTapGestures(
                    onTap = {
                        if (isBatchMode) {
                            onSelectClick()
                        } else {
                            onClick()
                        }
                    },
                    onLongPress = {
                        // 长按触发时执行震动反馈（脉冲式长震动）
                        HapticFeedbackManager.performHapticFeedback(
                            context = context,
                            type = InteractionType.LONG_CLICK,
                            enabled = hapticEnabled
                        )
                        // 普通模式：进入批量模式并选中该条
                        // 批量模式：切换该条选中状态（toggleSelection 语义）
                        onLongClick()
                    },
                    onPress = { pressOffset ->
                        // 按下时发射 Press 事件，触发水波纹效果
                        val pressInteraction = PressInteraction.Press(pressOffset)
                        interactionSource.emit(pressInteraction)

                        /**
                         * 标记是否已发射终结事件（Release 或 Cancel）。
                         *
                         * 关键修复：原代码只在 else 分支发射 Cancel，但当协程被外部
                         * 中断时（如 onLongPress 触发 → enterBatchMode 改变 _isBatchMode
                         * → pointerInput key 变化 → gesture detector 重启），tryAwaitRelease()
                         * 会抛 CancellationException，导致 else 分支不执行，Cancel 事件
                         * 从未发射 → interactionSource 残留 Press 状态 → 水波纹持续渲染
                         * （用户感知为"卡片变暗"）。
                         *
                         * 修复策略：用 try/finally 确保 Cancel 总是被发射。
                         * 注：Compose 的 MutableInteractionSource 对重复 Cancel 事件是幂等的。
                         */
                        var terminalEmitted = false

                        try {
                            // 等待手指释放：tryAwaitRelease() 返回 true 表示正常抬起，
                            // false 表示被取消（滑动/其他手势抢占）
                            val released = tryAwaitRelease()

                            if (released) {
                                // 手指正常抬起：结束水波纹动画
                                interactionSource.emit(PressInteraction.Release(pressInteraction))
                                terminalEmitted = true
                            }
                            // 释放被取消的情况由 finally 块统一发射 Cancel
                        } finally {
                            /**
                             * 异常场景兜底：保证 Press 状态被正确清理
                             *
                             * 覆盖以下所有可能路径：
                             * 1. 协程正常执行到 else 分支（已 emit Cancel，但 finally 中再 emit 一次是幂等的）
                             * 2. 协程因 tryAwaitRelease 返回 false 而进入 else 分支（已 emit Cancel）
                             * 3. 协程因重组/CancellationException 被中断（必须 emit Cancel）
                             * 4. 任何其他异常（必须 emit Cancel）
                             *
                             * 为什么是"幂等"的：
                             * - MutableInteractionSource 内部基于 Channel，重复 emit 同一 Press
                             *   对应的 Cancel 不会引发问题（指示器仅在收到 Press 后未收到 Cancel
                             *   时持续渲染，重复 Cancel 不会"重启"渲染）
                             */
                            if (!terminalEmitted) {
                                interactionSource.emit(PressInteraction.Cancel(pressInteraction))
                            }
                        }
                    }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        // 卡片内部：左侧 4dp 优先级竖条 + 右侧内容
        // 使用自定义 Layout 强制 PriorityBar 高度 = Column 实际内容总高度，
        // 避免 Row.height(IntrinsicSize.Max) 的 max 取值（max 子项 intrinsic）远小于
        // Column 实际内容总高度（sum）导致 Card 高度被低估、内容被裁剪的问题
        Layout(
            content = {
                /** 左侧 4dp 优先级竖条（无优先级时透明，不占视觉空间） */
                PriorityBar(priority = todo.priority, isCompleted = todo.status == 1)

                /** 内容区域，占满除竖条外的宽度 */
                Column(modifier = Modifier.fillMaxWidth()) {
                // 顶部 Row：复选框 + start 槽位 + 内容 + 展开按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 复选框区域
                    if (isBatchMode) {
                        CircularCheckbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectClick() },
                            // 已完成待办在批量模式下也保持橙色系降权（dimmed）
                            dimmed = todo.status == 1,
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
                            // 已完成态视觉降权：勾选框变淡（保持橙色系仅降深度）
                            dimmed = todo.status == 1,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }

                    // 标题 + 分类 + 时间等内容 Column
                    Column(modifier = Modifier.weight(1f)) {
                        // 标题行
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                            Color(todo.backgroundColor) else null
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
                        }

                        // 完成时间
                        if (todo.status == 1 && todo.completedAt != null) {
                            Text(
                                text = formatCompletedTime(todo.completedAt),
                                fontSize = 12.sp,
                                // 已完成态视觉降权：使用 CompletedColors.Text 而非 primary 橙色
                                color = CompletedColors.Text,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // 提醒时间 + 附件数量（聚合：父 + 所有子任务）
                        val aggregateCounts = aggregateAttachmentCounts(todo, subTasks)
                        if (todo.reminderTime != null || aggregateCounts.first > 0 || aggregateCounts.second > 0 || categoryName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                // 分类（内联展示，带阴影效果）
                                if (categoryName != null) {
                                    CategoryTagWithShadow(
                                        categoryName = categoryName!!,
                                        isCompleted = todo.status == 1
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                if (todo.reminderTime != null) {
                                    val reminder = formatReminderDisplay(todo.reminderTime)
                                    // 已完成态视觉降权：强制使用 CompletedColors.Text 灰色，
                                    // 覆盖"已过期"场景下的红色（Color(0xFFDC2626)）
                                    val reminderColor = if (todo.status == 1) {
                                        CompletedColors.Text
                                    } else if (reminder.isOverdue) {
                                        Color(0xFFDC2626)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = if (reminder.isOverdue) "已过期提醒" else "提醒",
                                        tint = reminderColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = reminder.text,
                                        fontSize = 12.sp,
                                        color = reminderColor,
                                        fontWeight = if (reminder.isOverdue && todo.status != 1)
                                            androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))   // 提醒与附件间 1 个空格的间距
                                }

                                // 附件计数（图片 + 语音）
                                if (aggregateCounts.first > 0 || aggregateCounts.second > 0) {
                                    val attachmentText = buildString {
                                        if (aggregateCounts.second > 0) append("🎤×${aggregateCounts.second}")
                                        if (aggregateCounts.first > 0 && aggregateCounts.second > 0) append(" ")  // 两种附件间 1 个空格
                                        if (aggregateCounts.first > 0) append("🖼×${aggregateCounts.first}")
                                    }
                                    Text(
                                        text = attachmentText,
                                        fontSize = 12.sp,
                                        // 已完成态视觉降权：使用 CompletedColors.Text 而非 onSurfaceVariant
                                        color = if (todo.status == 1) CompletedColors.Text
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 分类行已删除（迁移到提醒行左侧，详见下方 CategoryTagWithShadow）

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

                        // 进度条
                        if (todo.status != 1 && todo.content.isNullOrBlank() && subTaskProgress != null) {
                            SubTaskProgressBar(
                                progress = parseProgress(subTaskProgress),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    // 子任务进度（移至展开按钮左侧）+ 展开/收起按钮（带阴影）
                    //
                    // 多选模式保留显示的原因：
                    // 1. 进度文本 "(0/1)" 是信息性 UI，不影响多选操作
                    // 2. 展开/收起按钮是独立 Surface(onClick = onToggleExpand)，
                    //    点击事件会被 Surface 消费，不会冒泡到外层 Card 的
                    //    onTap（多选点击），所以两个操作互不干扰
                    // 3. 保持 UI 一性：进入多选模式后所有 UI 元素不"凭空消失"
                    if (subTaskProgress != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 子任务进度文本：紧贴展开按钮左侧
                            Text(
                                text = "($subTaskProgress)",
                                fontSize = 13.sp,
                                // 已完成态视觉降权：使用 CompletedColors.Text 而非 primary 橙色
                                color = if (todo.status == 1) CompletedColors.Text
                                        else MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // 展开/收起按钮：Surface 圆形阴影 2dp
                            Surface(
                                onClick = onToggleExpand,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
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
                            }
                        }
                    }
                }

                // 展开时显示子任务列表
                if (isExpanded && subTasks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 60.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        subTasks.forEach { subTask ->
                            SubTaskInTodoListItem(
                                subTask = subTask,
                                isParentCompleted = todo.status == 1,
                                // 关键：多选模式下子任务勾选框不可点击
                                // - 仅可查看，不可切换完成状态
                                // - 视觉上 alpha 降低，提供 disabled 反馈
                                isEnabled = !isBatchMode,
                                onToggleComplete = { onToggleSubTask(subTask.id) },
                                // 多选模式下长按子任务勾选框，弹 Toast 提示用户先退出多选模式
                                // 文案来自 strings.xml，支持中英文等多语言
                                onDisabledLongPress = {
                                    Toast.makeText(
                                        context,
                                        exitBatchModeHint,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                            if (subTask != subTasks.last()) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
            },
            // 关键 measurePolicy：先测量 Column 拿到实际渲染高度，
            // 再用 Constraints.fixed(width=4dp, height=columnHeight) 测量 PriorityBar，
            // 保证 PriorityBar 高度严格 = Column 实际内容总高度（与 Card 一致），
            // 与 SwipeableTodoBox 的 actions 层形成精确对齐
            measurePolicy = { measurables, constraints ->
                // PriorityBar 宽度固定 4dp
                val barWidthPx = 4.dp.toPx().toInt()
                // 先 measure Column，宽度 = 父级宽度 - 4dp
                val columnPlaceable = measurables[1].measure(
                    constraints.copy(
                        minWidth = 0,
                        maxWidth = (constraints.maxWidth - barWidthPx).coerceAtLeast(0)
                    )
                )
                // 再 measure PriorityBar，固定 width=4dp, height=column 实际高度
                val barPlaceable = measurables[0].measure(
                    Constraints.fixed(width = barWidthPx, height = columnPlaceable.height)
                )
                // 整体尺寸 = 4dp + Column 实际宽度
                layout(
                    width = barWidthPx + columnPlaceable.width,
                    height = columnPlaceable.height
                ) {
                    // 左侧 PriorityBar 占满整个高度（贴左）
                    barPlaceable.placeRelative(x = 0, y = 0)
                    // 右侧 Column 内容
                    columnPlaceable.placeRelative(x = barWidthPx, y = 0)
                }
            }
        )
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
    isParentCompleted: Boolean = false,
    isEnabled: Boolean = true,
    onToggleComplete: () -> Unit = {},
    onDisabledLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圆形复选框
        SubTaskCheckbox(
            isCompleted = subTask.isCompleted,
            isParentCompleted = isParentCompleted,
            // 关键：批量模式下不可点击切换子任务状态
            isEnabled = isEnabled,
            onClick = onToggleComplete,
            // 透传禁用态长按回调（多选模式弹"请先退出多选模式" Toast）
            onDisabledLongPress = onDisabledLongPress
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

        // 子任务自身附件计数（独立于父卡聚合）
        val subImageCount = parseImagePathsCount(subTask.imagePaths)
        val subVoiceCount = parseVoicePathsCount(subTask.voicePaths)
        if (subImageCount > 0 || subVoiceCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            val text = buildString {
                if (subVoiceCount > 0) append("🎤×$subVoiceCount")
                if (subImageCount > 0 && subVoiceCount > 0) append(" ")
                if (subImageCount > 0) append("🖼×$subImageCount")
            }
            Text(
                text = text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 子任务复选框组件（与SubTaskListItem一致）
 *
 * @param isCompleted 是否已完成
 * @param isParentCompleted 父待办是否已完成（影响颜色）
 * @param isEnabled 是否可点击（默认 true）。批量模式下设为 false：
 *                   - 视觉上降低 alpha 表示不可点击
 *                   - 短按不切换完成状态
 *                   - 长按触发 [onDisabledLongPress]（用于显示"请先退出多选模式"等提示）
 * @param onClick 短按回调（仅在 [isEnabled] = true 时触发）
 * @param onDisabledLongPress 禁用态长按回调（仅在 [isEnabled] = false 时触发）
 */
@Composable
private fun SubTaskCheckbox(
    isCompleted: Boolean,
    isParentCompleted: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit = {},
    onDisabledLongPress: () -> Unit = {}
) {
    /**
     * 勾选框背景色：
     * - 子任务未完成 → 浅灰描边
     * - 子任务完成（无论父待办状态） → CheckboxBgDim 浅橙色
     *
     * 用户统一要求：父未完成+子完成、父完成+子完成 两种情况颜色一致：
     * **浅橙色底 + 白色√**
     *
     * 这样与父待办 CircularCheckbox 在 dimmed=true 时的"浅橙底 + 白色√"完全统一，
     * 实现"已完成态"的跨组件视觉一致性。
     */
    val bgColor = when {
        !isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> CompletedColors.CheckboxBgDim
    }

    /**
     * 不可用态视觉降权
     *
     * 批量模式下，子任务勾选框不可点击（避免与多选操作语义冲突）。
     * 通过 Box 的 graphicsLayer 降低透明度，提供 disabled 视觉反馈。
     */
    val disabledAlpha = 0.4f

    /**
     * 关键：用 pointerInput + detectTapGestures 替代 clickable
     *
     * 原因：clickable 只支持"短按"，不支持"短按+长按"组合。combinedClickable
     * 在 enabled=false 时会整体禁用，无法实现"长按仍然响应"的需求。
     *
     * 自定义手势分流：
     * - 短按（onTap）：
     *     - 启用态（isEnabled = true）→ 执行 onClick（切换完成状态）
     *     - 禁用态（isEnabled = false）→ 执行 onDisabledLongPress（弹 Toast）
     * - 长按（onLongPress）：
     *     - 启用态（isEnabled = true）→ 无操作
     *     - 禁用态（isEnabled = false）→ 执行 onDisabledLongPress（弹 Toast）
     *
     * 为什么禁用态下短按也要弹 Toast？
     * 用户反馈：多选模式下点子待办勾选框无任何反馈，不知道发生了什么。
     * 修复后：禁用态下任何点击操作都触发 onDisabledLongPress，统一反馈。
     *
     * key 包括 isEnabled、onClick、onDisabledLongPress：当任一变化时重启手势检测器，
     * 避免长按协程引用旧的 lambda 闭包。
     */
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .pointerInput(isEnabled, onClick, onDisabledLongPress) {
                detectTapGestures(
                    onTap = {
                        // 短按：
                        // - 启用态 → 切换完成状态
                        // - 禁用态 → 弹 Toast 提示（与长按统一反馈）
                        if (isEnabled) onClick() else onDisabledLongPress()
                    },
                    onLongPress = {
                        // 长按：仅在禁用态触发
                        if (!isEnabled) onDisabledLongPress()
                    }
                )
            }
            .graphicsLayer {
                this.alpha = if (isEnabled) 1f else disabledAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            /**
             * 已完成态 √ 颜色：白色
             *
             * 用户统一要求：父未完成+子完成、父完成+子完成 两种情况下，
             * √ 颜色统一为白色，与背景 CheckboxBgDim 浅橙形成清晰对比。
             *
             * 这样与父待办 CircularCheckbox 在 dimmed=true 时的
             * "浅橙底 + 白色√" 完全一致，实现跨组件视觉统一。
             */
            Text(
                text = "✓",
                color = Color.White,
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
 * 优先级竖条 - 显示在待办卡片左侧 4dp 宽的彩色线条
 *
 * 颜色根据 todo.priority 动态变化：
 * - 0 (无优先级) → 透明
 * - 1 (低) → PriorityColors.Low（柔蓝）
 * - 2 (中) → PriorityColors.Medium（柔橙）
 * - 3 (高) → PriorityColors.High（柔红）
 *
 * 通过 animateColorAsState 实现 200ms 颜色平滑过渡。
 * 高度通过 fillMaxHeight() 自适应父容器（Card），无需硬编码。
 *
 * @param priority 优先级数值
 * @param modifier Modifier
 */
@Composable
private fun PriorityBar(
    priority: Int,
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    /** 目标颜色：未完成用原色，已完成用浅色版（保留色相但降饱和） */
    val targetColor = if (isCompleted) {
        PriorityColors.dimColorOf(priority)
    } else {
        PriorityColors.colorOf(priority)
    }

    /** 颜色过渡动画：与卡片其他动画保持 200ms 节奏一致 */
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 200),
        label = "PriorityBarColor"
    )

    Box(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(animatedColor)
    )
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

/**
 * 解析 JSON 数组格式的图片路径字符串，返回图片数量
 *
 * @param imagePathsJson org.json.JSONArray 序列化的字符串
 * @return 图片数量（解析失败或为空返回 0）
 */
private fun parseImagePathsCount(imagePathsJson: String): Int {
    if (imagePathsJson.isBlank()) return 0
    return try {
        org.json.JSONArray(imagePathsJson).length()
    } catch (e: Exception) {
        0
    }
}

/**
 * 解析 JSON 数组格式的语音路径字符串，返回语音数量
 *
 * @param voicePathsJson org.json.JSONArray 序列化的字符串
 * @return 语音数量（解析失败或为空返回 0）
 */
private fun parseVoicePathsCount(voicePathsJson: String): Int {
    if (voicePathsJson.isBlank()) return 0
    return try {
        org.json.JSONArray(voicePathsJson).length()
    } catch (e: Exception) {
        0
    }
}

/**
 * 聚合待办卡片附件数量（父自身 + 所有子任务）
 *
 * @param todo 父待办
 * @param subTasks 子任务列表
 * @return Pair(图片总数, 语音总数)
 */
private fun aggregateAttachmentCounts(
    todo: TodoItem,
    subTasks: List<SubTask>
): Pair<Int, Int> {
    val imageCount = parseImagePathsCount(todo.imagePaths) +
            subTasks.sumOf { parseImagePathsCount(it.imagePaths) }
    val voiceCount = (if (todo.voiceNotePath != null) 1 else 0) +
            subTasks.sumOf { parseVoicePathsCount(it.voicePaths) }
    return imageCount to voiceCount
}

/**
 * 带阴影效果的分类标签
 *
 * 用于 TodoListItem 卡片提醒行左侧：
 * - 阴影：水平偏移 2px，垂直偏移 2px，模糊半径 4px，颜色 rgba(0,0,0,0.1)
 * - 字号 12sp
 * - 已完成态使用 CompletedColors.Text 降权
 *
 * 实现：双层 Box
 * - 外层 Box：matchParentSize + offset(2.dp, 2.dp) + 半透明黑背景 + blur(4.dp) 模拟阴影
 * - 内层 Row：实际内容（背景色 + 图标 + 名称）
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CategoryTagWithShadow(
    categoryName: String,
    isCompleted: Boolean
) {
    val textColor = if (isCompleted) CompletedColors.Text
                    else MaterialTheme.colorScheme.primary
    val bgColor = if (isCompleted) CompletedColors.Text.copy(alpha = 0.12f)
                  else MaterialTheme.colorScheme.primaryContainer

    Box(contentAlignment = Alignment.Center) {
        // 外层：阴影（偏移 2dp 半透明黑 + 4dp 模糊）
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 2.dp)
                .background(
                    color = Color(0x1A000000),  // rgba(0, 0, 0, 0.1)
                    shape = RoundedCornerShape(4.dp)
                )
                .blur(radius = 4.dp)
        )
        // 内层：实际内容（仅文字，不再包含 emoji 图标）
        //
        // 用户要求："待办卡片上的类型组件不要emoji表情，只要文字"
        // 原实现：Row { emoji Text + Spacer + name Text }
        // 新实现：直接 Text(categoryName)，更紧凑
        Text(
            text = categoryName,
            fontSize = 12.sp,
            color = textColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .background(color = bgColor, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
