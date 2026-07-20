package com.corgimemo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.res.stringResource
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
    isSimpleMode: Boolean = false,
    /** 统一的 Snackbar 提示回调（由调用方传入，用于替代 Toast） */
    onShowSnackbar: (String) -> Unit = {}
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
     * 当存在子任务时，展开按钮始终可见（简化模式下也可见），
     * 用户点击展开按钮可展开/收起子任务列表（不受简化模式限制）。
     * 仅当子任务列表为空时强制隐藏。
     */
    val effectiveExpanded = isExpanded && subTasks.isNotEmpty()

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

    /**
     * 长按过程状态（v2026-07-20 新增）
     *
     * 由 [pressFeedback] 内部维护：>= 500ms 持续按下时为 true，
     * 抬起/移动/拖拽让位/异常退出时为 false。
     * 这里传给 [pressFeedback] 让其内部维护状态，外部读取同步。
     */
    val isLongPressed = remember { mutableStateOf(false) }

    /**
     * 优先级三联视觉（v2026-07-20 新增）
     *
     * 统一获取竖条/边框/阴影三处视觉元素的颜色源。
     * - 用 remember 缓存避免重组时重复计算（同时降低 lambda 捕获陷阱风险）
     * - 已完成态（status=1）时三联颜色同步降权为 dim 版
     */
    val priorityVisual = remember(todo.priority, todo.status) {
        PriorityColors.priorityVisualOf(
            priority = todo.priority,
            isCompleted = todo.status == 1
        )
    }

    /**
     * 优先级阴影（v2026-07-20 v7 彻底根除"首次渲染跳动"）
     *
     * 历史变更链：
     * - v1-v4: animateDpAsState 初始值 0.dp → 0→4dp 弹跳
     * - v5: 加深 shadowAmbientColor / shadowSpotAlpha（v5 视觉优化）
     * - v6: 改用 Animatable 初始 4.dp，根除 0→4 弹跳
     * - **v7（本次）**：完全去掉 Animatable + 改用静态 4.dp
     *   - 根因新发现：Modifier.shadow 通过 RenderEffect/BlurEffect 渲染，
     *     首次 frame 时 RenderNode 初始化需要额外一帧，导致首帧阴影 = 0、第二帧阴影 = 4dp，
     *     视觉上"阴影从无到有"再次形成 0→4 弹跳（即使 Animatable 初始 = 4dp 也无济于事）
     *   - 修复：直接传 `elevation = 4.dp`（无 State/Dp 读取），让 Compose 编译期确定 elevation，
     *     跳过可能的 RenderNode 初始化延迟
     *   - 副作用：失去"长按时 4→8dp 阴影抬升"效果（用 border 加深 0.6f→0.8f 补偿）
     */
    val shadowAmbientColor = Color.Black.copy(alpha = 0.20f)
    val shadowSpotAlpha = if (isLongPressed.value) 1.0f else 0.85f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // v2026-07-20 v3 关键修复：Modifier 顺序调整，shadow 必须在最外层
            // - 旧顺序：.pressFeedback().border().shadow()
            //   shadow 在 pressFeedback 的 graphicsLayer 内绘制，被 graphicsLayer
            //   边界裁切 → 阴影外溢部分全部丢失（用户完全看不到阴影）
            // - 新顺序：.shadow().pressFeedback().border()
            //   shadow 在 graphicsLayer 外绘制，不被裁切；pressFeedback 的
            //   graphicsLayer 包裹 border + content，按压时一起缩放
            // - 视觉效果：按压时 shadow 不缩放（"露出来"） + border 缩放 +
            //   content 缩放 → "卡片陷下去、shadow 浮起来"的双重视觉
            //
            // v2026-07-20 v7 关键修复：阴影 elevation 改为静态 4.dp
            // - 不用 shadowElevation.value (State 读取)，因为 State 读取在首次 frame
            //   时可能未完成，导致首帧阴影 = 0（RenderNode 初始化延迟）
            // - 直接传 4.dp（编译期常量），根除"首帧无阴影"问题
            // - 代价：失去长按时 4→8dp 阴影抬升效果（用 border 加深补偿）
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = shadowAmbientColor,
                spotColor = priorityVisual.shadow.copy(alpha = shadowSpotAlpha)
            )
            .pressFeedback(
                interactionSource = interactionSource,
                scale = cardScale,
                isBatchMode = isBatchMode,
                enabled = !isClickBlocked,   // 左滑操作面板展开时屏蔽整个按压反馈
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
                isDragActive = { isDragActive },
                // 长按状态：传给 pressFeedback 内部维护，外部读取用于阴影抬升
                isLongPressed = isLongPressed
            )
            // 优先级边框：1.5dp + 优先级色 alpha 0.6f
            // v2026-07-20 改动：放在 pressFeedback 之后（在 graphicsLayer 内），
            // 让边框跟着 content 一起缩放，避免"内容缩小但边框不缩"的违和感
            .border(
                width = 1.5.dp,
                color = priorityVisual.border.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            ),
        // v2026-07-20 改动：让出默认阴影给外层 Modifier.shadow，避免双层阴影叠加
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                // 聚合附件数量（提前计算，供元数据布局判断使用）
                val aggregateCounts = aggregateAttachmentCounts(todo, subTasks)
                /** 是否存在分类标签（详情模式且categoryName不为空） */
                val hasCategory = !isSimpleMode && categoryName != null
                /** 是否存在提醒时间 */
                val hasReminder = todo.reminderTime != null
                /** 是否存在附件（详情模式下图片或语音数量>0） */
                val hasAttachment = !isSimpleMode && (aggregateCounts.first > 0 || aggregateCounts.second > 0)
                /**
                 * 元数据是否"拥挤"：分类+提醒+附件三者同时存在
                 * 拥挤时采用两行布局：第一行分类+附件，第二行提醒单独一行突出显示
                 * 非拥挤时（缺少任一元素）：所有可见元数据同一行显示
                 */
                val isMetaCrowded = hasCategory && hasReminder && hasAttachment

                // 主区域行：复选框 + (标题+元数据列) + 展开区域
                // 复选框通过 Row(verticalAlignment=CenterVertically) 相对于"标题+元数据"整体垂直居中
                // 关联提示/进度条/子任务在该行下方单独渲染，不影响复选框垂直居中位置
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp),
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

                    // 标题 + 完成时间 + 元数据 Column
                    // 这是复选框垂直居中对齐的参照区域（包含标题行+元数据，不含关联提示/进度条/子任务）
                    Column(modifier = Modifier.weight(1f)) {
                        // ========== 标题行：标题文本 + 进度文本 + 展开/收起按钮 ==========
                        // 标题文本占据剩余空间（weight=1f），单行省略；进度文本和展开按钮在右侧
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            /**
                             * 标题文本（支持逐区间交错淡入高亮）
                             *
                             * V2.5 改造：使用 buildHighlightRanges() 拆分为独立区间列表，
                             * 每个高亮区间拥有独立的 animateFloatAsState + 延迟，
                             * 实现从左到右的波浪式淡入效果。
                             *
                             * 父标题始终单行显示，超长时末尾截断为"..."省略号，
                             * 确保不会与右侧进度文本和展开按钮重叠。
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
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.weight(1f)
                                ) {
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
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
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
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // 子任务进度文本：详情模式显示，简化模式隐藏
                            if (subTasks.isNotEmpty() && !isSimpleMode && subTaskProgress != null) {
                                Text(
                                    text = "($subTaskProgress)",
                                    fontSize = 13.sp,
                                    // 已完成态视觉降权：使用 CompletedColors.Text 而非 primary 橙色
                                    color = if (todo.status == 1) CompletedColors.Text
                                            else MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // 展开/收起按钮：Surface 圆形阴影 2dp，两种模式下都保留显示
                            if (subTasks.isNotEmpty()) {
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

                        // 完成时间（已完成待办显示"X分钟前完成"）
                        if (todo.status == 1 && todo.completedAt != null) {
                            Text(
                                text = formatCompletedTime(todo.completedAt),
                                fontSize = 12.sp,
                                // 已完成态视觉降权：使用 CompletedColors.Text 而非 primary 橙色
                                color = CompletedColors.Text,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // ========== 元数据智能布局 ==========
                        // 判断是否有任何元数据需要显示
                        val hasAnyMeta = hasReminder || hasAttachment || hasCategory
                        if (hasAnyMeta) {
                            // 附件图标颜色（已完成态视觉降权）
                            val attachmentColor = if (todo.status == 1) CompletedColors.Text
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            // 提醒时间相关数据
                            val reminderData = if (hasReminder) {
                                // reminderTime 在 hasReminder 分支内已智能转换为非空 Long，移除冗余 !! 2026-07-20
                                val reminder = formatReminderDisplay(todo.reminderTime)
                                val reminderColor = if (todo.status == 1) {
                                    CompletedColors.Text
                                } else if (reminder.isOverdue) {
                                    Color(0xFFDC2626)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Triple(reminder, reminderColor, reminder.isOverdue)
                            } else null

                            if (isMetaCrowded) {
                                // ---- 拥挤模式（tag+reminder+attach三者都有）：两行布局 ----
                                // 第一行：分类标签 + 附件计数
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    // 分类标签
                                    CategoryTagWithShadow(
                                        // isMetaCrowded 分支内 categoryName 已智能转换为非空 String，移除冗余 !! 2026-07-20
                                        categoryName = categoryName,
                                        isCompleted = todo.status == 1
                                    )
                                    // 附件计数（语音+图片）
                                    if (hasAttachment) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AttachmentCountsRow(
                                            imageCount = aggregateCounts.first,
                                            voiceCount = aggregateCounts.second,
                                            color = attachmentColor
                                        )
                                    }
                                }
                                // 第二行：提醒时间单独一行（突出显示，过期时红色醒目）
                                if (reminderData != null) {
                                    val (reminder, reminderColor, isOverdue) = reminderData
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        ReminderInfoRow(
                                            text = reminder.text,
                                            color = reminderColor,
                                            isOverdue = isOverdue && todo.status != 1
                                        )
                                    }
                                }
                            } else {
                                // ---- 非拥挤模式：所有可见元数据同一行 ----
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    // 分类标签
                                    if (hasCategory) {
                                        // hasCategory 分支内 categoryName 已智能转换为非空 String，移除冗余 !! 2026-07-20
                                        CategoryTagWithShadow(
                                            categoryName = categoryName,
                                            isCompleted = todo.status == 1
                                        )
                                    }
                                    // 提醒时间
                                    if (reminderData != null) {
                                        if (hasCategory) Spacer(modifier = Modifier.width(8.dp))
                                        val (reminder, reminderColor, isOverdue) = reminderData
                                        ReminderInfoRow(
                                            text = reminder.text,
                                            color = reminderColor,
                                            isOverdue = isOverdue && todo.status != 1
                                        )
                                    }
                                    // 附件计数
                                    if (hasAttachment) {
                                        if (hasReminder) Spacer(modifier = Modifier.width(8.dp))
                                        AttachmentCountsRow(
                                            imageCount = aggregateCounts.first,
                                            voiceCount = aggregateCounts.second,
                                            color = attachmentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 关联提示（在主区域行下方，缩进对齐标题文本）
                if (relationHint != null) {
                    Text(
                        text = relationHint,
                        fontSize = 12.sp,
                        color = Color(0xFF999999),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 52.dp, end = 16.dp, top = 4.dp)
                    )
                }

                // 进度条（在主区域行下方，缩进对齐标题文本）
                if (todo.status != 1 && todo.content.isNullOrBlank() && subTaskProgress != null) {
                    SubTaskProgressBar(
                        progress = parseProgress(subTaskProgress),
                        modifier = Modifier.padding(start = 52.dp, end = 16.dp, top = 6.dp)
                    )
                }

                // 展开时显示子任务列表
                AnimatedVisibility(
                    visible = effectiveExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 52.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
                    ) {
                        subTasks.forEach { subTask ->
                            SubTaskInTodoListItem(
                                subTask = subTask,
                                isParentCompleted = todo.status == 1,
                                isEnabled = !isBatchMode && !isClickBlocked,
                                onToggleComplete = { onToggleSubTask(subTask.id) },
                                onDisabledLongPress = {
                                    onShowSnackbar(exitBatchModeHint)
                                }
                            )
                            if (subTask != subTasks.last()) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // 底部兜底间距：当主区域行下方没有任何内容时，提供 16dp 底部内边距
                val hasBelowContent = relationHint != null ||
                    (todo.status != 1 && todo.content.isNullOrBlank() && subTaskProgress != null) ||
                    effectiveExpanded
                if (!hasBelowContent) {
                    Spacer(modifier = Modifier.height(16.dp).fillMaxWidth())
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
                    // - 禁用态 → 弹 Snackbar 提示（与长按统一反馈）
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

/**
 * 提醒时间显示组件（闹钟图标 + 时间文本）
 *
 * 用于元数据行中渲染提醒时间，支持过期红色高亮。
 * 从原内联代码提取，避免拥挤/非拥挤两种布局下的代码重复。
 *
 * @param text 格式化后的提醒时间文字（如 "7月15日 20:00"、"明天09:00"）
 * @param color 文字和图标颜色（过期为红色，已完成为灰色，普通为 onSurfaceVariant）
 * @param isOverdue 是否已过期（true 时使用 SemiBold 字重增强视觉权重）
 */
@Composable
private fun ReminderInfoRow(
    text: String,
    color: Color,
    isOverdue: Boolean
) {
    Icon(
        imageVector = Icons.Default.Alarm,
        contentDescription = if (isOverdue) "已过期提醒" else "提醒",
        tint = color,
        modifier = Modifier.size(14.dp)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = text,
        fontSize = 12.sp,
        color = color,
        fontWeight = if (isOverdue)
            androidx.compose.ui.text.font.FontWeight.SemiBold
        else
            androidx.compose.ui.text.font.FontWeight.Normal
    )
}

/**
 * 附件计数行（语音 + 图片附件数量显示）
 *
 * 从原内联代码提取，避免拥挤/非拥挤两种布局下的代码重复。
 * 当两种附件都有时，语音在前图片在后，中间留 6dp 间距。
 *
 * @param imageCount 图片附件数量
 * @param voiceCount 语音附件数量
 * @param color 图标和文字颜色（已完成态使用灰色降权）
 */
@Composable
private fun AttachmentCountsRow(
    imageCount: Int,
    voiceCount: Int,
    color: Color
) {
    // 语音附件
    if (voiceCount > 0) {
        Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = "语音附件",
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "×$voiceCount",
            fontSize = 12.sp,
            color = color
        )
        // 两种附件间间距
        if (imageCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
    // 图片附件
    if (imageCount > 0) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = "图片附件",
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "×$imageCount",
            fontSize = 12.sp,
            color = color
        )
    }
}
