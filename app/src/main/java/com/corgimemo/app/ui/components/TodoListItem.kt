package com.corgimemo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.runtime.mutableFloatStateOf
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
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

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
 * @param isClickBlocked 左滑操作面板是否展开（true 时屏蔽详情点击 / 子待办展开 / 长按 / 复选框）
 * @param isSimpleMode 简化模式（true 时隐藏分类标签、子任务进度文本、子任务列表、附件数量，仅保留标题/提醒/优先级/置顶/勾选框）
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
    hapticEnabled: Boolean = true,
    /** 是否被拖拽中（视觉反馈用） */
    isDragging: Boolean = false,
    /** 容器拖拽是否激活（手势协调用，激活时子项不再消费长按后的移动） */
    isDragActive: Boolean = false,
    /** 左滑操作面板是否展开（true 时屏蔽详情点击 / 子待办展开 / 长按 / 复选框） */
    isClickBlocked: Boolean = false,
    /** 简化模式：隐藏分类标签 / 子任务进度文本 / 子任务列表 / 附件数量，仅保留标题/提醒/优先级/置顶/勾选框 */
    isSimpleMode: Boolean = false
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
     * 子任务展开的有效状态
     *
     * 简化模式下强制收起（即使 isExpanded=true 也不展开子任务列表），
     * 用于 AnimatedVisibility 的 visible 判断及展开按钮图标方向。
     */
    val effectiveExpanded = isExpanded && !isSimpleMode

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
     * 注意：左滑时禁用内部水波纹由 SwipeableTodoBox 通过 LocalContentIndication 控制。
     * 重构后该值仅作为保留字段备用（未在 Card modifier 中使用，因为 .indication 块已迁移至
     * Modifier.pressFeedback 内部统一处理）。
     */
    @Suppress("unused")
    val contentIndicationEnabled = LocalContentIndication.current

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

    /**
     * 水波纹按压交互状态（已迁移至 Modifier.pressFeedback 内部）
     *
     * 原代码在本函数内维护 MutableInteractionSource + Channel + pointerInput，
     * 重构后全部由 Modifier.pressFeedback 接管：
     * - interactionSource 传给 Modifier.pressFeedback，由其内部发射 Press/Release/Cancel，
     *   indication 监听到后显示水波纹
     * - cardScale 是 MutableFloatState（同步赋值 0.92f / 1f，无动画过渡）。
     *   详细原因见 PressFeedback.kt KDoc。
     */
    val interactionSource = remember { MutableInteractionSource() }
    val cardScale = remember { mutableFloatStateOf(1f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 统一的按压反馈：滑动接触缩放 + 点击水波纹 + 长按检测 + 拖拽让位
            .pressFeedback(
                interactionSource = interactionSource,
                scale = cardScale,
                isBatchMode = isBatchMode,
                enabled = !isClickBlocked,   // ← 新增：左滑操作面板展开时屏蔽整个按压反馈
                onTap = {
                    // 短按：根据批量模式分发
                    if (isBatchMode) {
                        onSelectClick()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    // 长按：仅非批量模式时触发震动反馈
                    if (!isBatchMode) {
                        HapticFeedbackManager.performHapticFeedback(
                            context = context,
                            type = InteractionType.LONG_CLICK,
                            enabled = hapticEnabled
                        )
                    }
                    onLongClick()
                },
                scaleDown = 0.94f,
                scaleDownDurationMs = 60,
                scaleUpDurationMs = 200,
                // 拖拽协调：ReorderableLazyColumn 启动拖拽时让位
                isDragActive = { isDragActive }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        // 卡片内部：左侧 4dp 优先级竖条 + 右侧内容
        // 使用自定义 Layout 强制 PriorityBar 高度 = Column 实际内容总高度，
        // 避免 Row.height(IntrinsicSize.Max) 的 max 取值（max 子项 intrinsic）远小于
        // Column 实际内容总高度（sum）导致 Card 高度被低估、内容被裁剪的问题
        //
        // 外层 Box 用于承载右上角置顶图标（align(Alignment.TopEnd)），
        // Layout 内 measurePolicy 返回的尺寸会撑满 Box，Icon 浮于其上。
        Box {
            Layout(
                content = {
                    /** 左侧 4dp 优先级竖条（无优先级时透明，不占视觉空间） */
                    PriorityBar(priority = todo.priority, isCompleted = todo.status == 1)

                    /** 内容区域，占满除竖条外的宽度；paddingEnd=24dp 为右上角置顶图标预留空间避免重叠 */
                    Column(modifier = Modifier.fillMaxWidth().padding(end = 24.dp)) {
                // 顶部 Row：复选框 + start 槽位 + 内容 + 展开按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 复选框区域
                    // 统一渲染单个 CircularCheckbox，避免 if/else 分支切换导致
                    // 节点重建（scale 动画重启→闪烁）和 Spacer 突然出现（跳跃）
                    CircularCheckbox(
                        checked = if (isBatchMode) isSelected else todo.status == 1,
                        onCheckedChange = { isChecked ->
                        // 左滑操作面板展开时屏蔽复选框点击
                        if (isClickBlocked) return@CircularCheckbox
                        if (isBatchMode) {
                            onSelectClick()
                        } else {
                            onToggleComplete(todo.id, isChecked)
                        }
                    },
                        // 已完成态视觉降权：勾选框变淡（保持橙色系仅降深度）
                        dimmed = todo.status == 1,
                        modifier = Modifier.padding(end = 12.dp)
                    )

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
                        // 简化模式下仅保留提醒时间，分类标签与附件数量均隐藏
                        if (todo.reminderTime != null || (!isSimpleMode && (aggregateCounts.first > 0 || aggregateCounts.second > 0 || categoryName != null))) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                // 分类（内联展示，带阴影效果）— 简化模式下隐藏
                                if (!isSimpleMode && categoryName != null) {
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

                                // 附件计数（图片 + 语音）— 简化模式下隐藏
                                if (!isSimpleMode && (aggregateCounts.first > 0 || aggregateCounts.second > 0)) {
                                    // 附件图标颜色（已完成态视觉降权）
                                    val attachmentColor = if (todo.status == 1) CompletedColors.Text
                                            else MaterialTheme.colorScheme.onSurfaceVariant

                                    // 语音附件
                                    if (aggregateCounts.second > 0) {
                                        Icon(
                                            imageVector = Icons.Outlined.Mic,
                                            contentDescription = "语音附件",
                                            tint = attachmentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "×${aggregateCounts.second}",
                                            fontSize = 12.sp,
                                            color = attachmentColor
                                        )
                                        // 两种附件间 1 个空格的间距
                                        if (aggregateCounts.first > 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                    }

                                    // 图片附件
                                    if (aggregateCounts.first > 0) {
                                        Icon(
                                            imageVector = Icons.Outlined.Image,
                                            contentDescription = "图片附件",
                                            tint = attachmentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "×${aggregateCounts.first}",
                                            fontSize = 12.sp,
                                            color = attachmentColor
                                        )
                                    }
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
                    // 简化模式下隐藏子任务进度文本与展开按钮
                    if (!isSimpleMode && subTaskProgress != null) {
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
                                onClick = { if (!isClickBlocked) onToggleExpand() },
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (effectiveExpanded) {
                                            Icons.Default.ExpandLess
                                        } else {
                                            Icons.Default.ExpandMore
                                        },
                                        contentDescription = if (effectiveExpanded) "收起" else "展开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 展开时显示子任务列表
                // 使用 AnimatedVisibility 包裹，提供平滑的展开/收起过渡动画
                //
                // 设计要点：
                // - 默认 enter = expandVertically() + fadeIn()，从顶部展开 + 淡入
                // - 默认 exit = shrinkVertically() + fadeOut()，从顶部收起 + 淡出
                // - 触发场景：用户点击"展开子待办"按钮（手动展开）+ 全部子任务完成时自动收起
                //   （自动收起由 HomeViewModel.toggleSubTaskCompletion 在 parentTodoCompleted = true 时
                //   调用 toggleExpand(todoId) 触发，isExpanded 从 true 变 false，AnimatedVisibility 自动播放 exit 动画）
                AnimatedVisibility(
                    visible = effectiveExpanded && subTasks.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
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
                                // 左滑操作面板展开时也屏蔽（与父卡片保持一致）
                                isEnabled = !isBatchMode && !isClickBlocked,
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

            // 右上角置顶图标：todo.isPinned 为 true 时显示
            // 已完成态使用灰色（onSurfaceVariant），未完成态使用品牌色（primary）
            // 图标大小 16dp，对齐到 Box 的 TopEnd（即卡片右上角）
            if (todo.isPinned) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = "置顶",
                    tint = if (todo.status == 1) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(16.dp)
                )
            }
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

        // 子任务自身附件计数（独立于父卡聚合）- 使用 Material Icons 图标
        val subImageCount = parseImagePathsCount(subTask.imagePaths)
        val subVoiceCount = parseVoicePathsCount(subTask.voicePaths)
        if (subImageCount > 0 || subVoiceCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            val attachmentColor = MaterialTheme.colorScheme.onSurfaceVariant

            // 语音附件
            if (subVoiceCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = "语音附件",
                    tint = attachmentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "×$subVoiceCount",
                    fontSize = 12.sp,
                    color = attachmentColor
                )
                if (subImageCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            // 图片附件
            if (subImageCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "图片附件",
                    tint = attachmentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "×$subImageCount",
                    fontSize = 12.sp,
                    color = attachmentColor
                )
            }
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
     * 按压反馈所需状态（迁移至 Modifier.pressFeedback 内部统一处理）
     *
     * - interactionSource：发射 Press/Release/Cancel 事件，indication 监听到后显示水波纹
     * - cardScale：MutableFloatState，手指接触时同步赋值为 0.92f，抬起时同步赋值为 1f
     *   （无动画过渡，详见 PressFeedback.kt KDoc 中的技术原因说明）
     */
    val interactionSource = remember { MutableInteractionSource() }
    val cardScale = remember { mutableFloatStateOf(1f) }

    /**
     * 关键：用 Modifier.pressFeedback 替代 pointerInput + detectTapGestures
     *
     * Modifier.pressFeedback 内部统一处理：
     * - 滑动接触反馈：手指在 18dp 复选框上滑动时缩小到 0.92f 后恢复，无水波纹
     * - 短按/长按水波纹：静止点击时显示水波纹
     * - 长按检测：500ms 后抬起触发 onLongClick
     *
     * 改造前用 pointerInput + detectTapGestures 是因为需要"启用态长按无操作、禁用态长按弹 Toast"
     * 的差异化语义。改造后由 Modifier.pressFeedback 的 onLongClick 统一回调，
     * 在回调内部根据 isEnabled 分流：
     * - 启用态（isEnabled = true）→ 长按 onLongClick() 内部走 onClick 分支 → 切换状态
     *   （行为略有变更：原逻辑启用态长按无操作，现统一走切换完成。需与产品确认是否可接受。）
     * - 禁用态（isEnabled = false）→ 短按和长按都触发 onDisabledLongPress
     */
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .pressFeedback(
                interactionSource = interactionSource,
                scale = cardScale,
                // 关键：批量模式下彻底禁用交互（不接收 down、不发射 Press、scale 不变）
                enabled = isEnabled,
                onTap = {
                    // 短按：
                    // - 启用态 → 切换完成状态
                    // - 禁用态 → 弹 Toast 提示（与长按统一反馈）
                    if (isEnabled) onClick() else onDisabledLongPress()
                },
                onLongClick = {
                    // 长按：仅在禁用态触发（启用态无操作，保留原行为）
                    if (!isEnabled) onDisabledLongPress()
                },
                // 复选框本身较小（18dp），使用与父卡片一致的缩放参数保持视觉统一
                scaleDown = 0.94f,
                scaleDownDurationMs = 60,
                scaleUpDurationMs = 200,
                // 子任务无拖拽排序，无需拖拽让位协调，使用默认 isDragActive = false
                isDragActive = { false }
            )
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
