package com.corgimemo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.ui.model.TodoLine
import com.corgimemo.app.ui.util.formatReminderDisplay
import com.corgimemo.app.util.VoicePlayer
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 复选框文本编辑器组件（多容器分组版）
 *
 * 按 [TodoLine.groupId] 将行分为多个独立圆角容器，
 * 每个容器代表一个待办组（主任务 + 子任务）。
 * 每个容器底部有操作栏：[提醒按钮] [优先级选择] [完成按钮]
 *
 * 支持行级附件：每一行都可以有自己的图片和语音附件，
 * 附件显示在文本输入框下方，子任务的附件会跟随缩进。
 *
 * 交互规则：
 * - 回车：在当前容器内新建子任务行（带缩进）
 * - 输入 "/"：消费 "/" 字符，在下方创建新待办容器
 * - Backspace（空行）：删除当前行；若为容器首行则整组删除
 * - 首个容器的首行不可删除
 * - @/# 触发关联/位置弹窗
 *
 * @param lines 当前行列表数据
 * @param onLinesChange 行数据变更回调
 * @param onLineCheckToggle 某一行复选框被点击时的回调
 * @param onSpecialCharDetected 特殊字符（@/#）检测回调
 * @param onNewGroupRequested 用户输入 "/" 时请求创建新分组的回调
 * @param onNewSubTaskRequested 用户按下回车新建子待办后的回调（v2026-07-25 新增）
 *   参数是新子待办行的全局索引（lineIndex + 1），用于 UI 层触发 externalPendingFocus 焦点转移
 *   背景：原 handleKeyEvent 内 onLinesChange + onFocusChange 已同步 ViewModel 状态，
 *   但 CheckboxEditRow 内 LaunchedEffect(isFocused) 触发 requestFocus() 存在时序竞态
 *   （新行 BasicTextField 可能未完全渲染好）。本回调让 UI 层用 externalPendingFocus 机制兜底。
 * @param onReminderClick 当前容器提醒按钮点击回调，参数为 groupId
 * @param onFocusedLineChange 当前聚焦行索引变化回调（用于确定附件插入目标）
 * @param priority 当前优先级值（0=低, 1=中, 2=高）
 * @param onPriorityChange 优先级变更回调，参数为 (groupId, 新优先级)
 * @param onSaveClick 当前容器完成/保存按钮点击回调，参数为 groupId
 * @param onImageClick 某一行图片被点击的回调（查看大图）
 * @param onDeleteImage 删除某一行某张图片的回调，参数为 (行索引, 图片路径)
 * @param onDeleteVoice 删除某一行某条语音的回调，参数为 (行索引, 语音路径)
 * @param canUndo 是否可撤销（控制撤销按钮启用/禁用状态）
 * @param canRedo 是否可恢复（控制恢复按钮启用/禁用状态）
 * @param onUndoClick 撤销按钮点击回调
 * @param onRedoClick 恢复按钮点击回调
 * @param modifier 容器修饰符
 * @param enabled 是否启用编辑
 * @param placeholder 占位提示文字
 *
 * 🆕 v2026-07-27 P5 改造：图片拖拽迁移到 Reorderable 库
 * - 删除 5 个旧拖拽参数（dragState / onAttachmentDragStart/Update/End / onRowBoundsChanged）
 * - 新增单回调 [onImageReorder]：图片行内排序完成后由 Reorderable 库 onMove 触发
 * - UI 层接到回调后调用 viewModel.applyImageReorder(lineIndex, newOrder)
 * - 行边界捕获（onGloballyPositioned）、滚动偏移补偿、Popup 浮层等复杂状态机全部由库接管
 *
 * 🆕 v2026-07-27 P6 改造：语音拖拽迁移到 Reorderable 库
 * - 删除 4 个旧拖拽参数（dragState / onAttachmentDragStart/Update/End）
 * - 新增单回调 [onVoiceReorder]：语音行内排序完成后由 Reorderable 库 onMove 触发
 * - UI 层接到回调后调用 viewModel.applyVoiceReorder(lineIndex, newOrder)
 * - 配套删除 CrossLineDragManager.kt 整文件（384 行）— 自研跨行拖拽状态机已废弃
 * - 图片 + 语音统一使用 LazyRow + rememberReorderableLazyListState + ReorderableItem 模式
 */
@Composable
fun CheckboxEditText(
    lines: List<TodoLine>,
    onLinesChange: (List<TodoLine>) -> Unit,
    onLineCheckToggle: (index: Int, isChecked: Boolean) -> Unit,
    onSpecialCharDetected: ((String, String?) -> Unit)? = null,
    onNewGroupRequested: ((index: Int, currentText: String) -> Unit)? = null,
    /**
     * 🆕 v2026-07-25 回车新建子待办后的回调
     *
     * 参数：新子待办行的全局索引（lineIndex + 1）
     *
     * 触发时机：handleKeyEvent 的 KEYCODE_ENTER 分支处理完 onLinesChange + onFocusChange 后调用
     * 用途：UI 层据此触发 externalPendingFocus 焦点转移，复用删除分组场景的成熟机制，
     *      避免新行 BasicTextField 未渲染完成时 requestFocus() 失败的时序竞态
     */
    onNewSubTaskRequested: ((newSubTaskIndex: Int) -> Unit)? = null,
    onReminderClick: ((Int) -> Unit)? = null,
    /** 各分组的提醒时间映射（key=groupId, value=提醒时间戳或 null） */
    groupReminders: Map<Int, Long?> = emptyMap(),
    /** × 按钮点击回调，参数是 groupId */
    onReminderDelete: ((Int) -> Unit)? = null,
    /** 各分组的分类 ID 映射（key=groupId, value=categoryId, 0L=未分类） */
    groupCategoryIds: Map<Int, Long> = emptyMap(),
    /** 各分组的分类名称映射（key=groupId, value=categoryName, null=未设置） */
    groupCategoryNames: Map<Int, String?> = emptyMap(),
    /** "分类"按钮点击回调（参数=groupId） */
    onCategoryClick: ((Int) -> Unit)? = null,
    /** "分类"×按钮点击回调：清除已设置的分类（参数=groupId） */
    onCategoryClear: ((Int) -> Unit)? = null,
    onFocusedLineChange: ((Int) -> Unit)? = null,
    priority: Int = 1,
    onPriorityChange: ((Int, Int) -> Unit)? = null,
    onSaveClick: ((Int) -> Unit)? = null,
    onImageClick: ((Int, String) -> Unit)? = null,
    onDeleteImage: ((Int, String) -> Unit)? = null,
    onDeleteVoice: ((Int, String) -> Unit)? = null,
    /**
     * 🆕 v2026-07-27 P5 改造：图片行内排序回调
     *
     * 参数：
     * - lineIndex：发生排序的待办行全局索引
     * - newOrder：排序后的新图片路径列表
     *
     * 替代关系（v2026-07-27 P6 完成）：
     * - 删除：onRowBoundsChanged（行边界捕获，Reorderable 库内置 LazyListItemInfo 替代）
     * - 删除：dragState / onAttachmentDragStart/Update/End（语音拖拽已迁至 Reorderable 库）
     *
     * 与 onVoiceReorder 一起形成完整的"附件拖拽迁移"闭环：
     * 图片 + 语音都迁至 Reorderable 库后，CrossLineDragManager 整文件已删除。
     */
    onImageReorder: ((lineIndex: Int, newOrder: List<String>) -> Unit)? = null,
    /**
     * 🆕 v2026-07-27 P6 改造：语音行内排序回调
     *
     * 参数：
     * - lineIndex：发生排序的待办行全局索引
     * - newOrder：排序后的新语音附件列表
     *
     * v2026-07-27 P6 状态：与 onImageReorder 一起形成完整的"附件拖拽迁移"闭环。
     * 图片 + 语音都迁至 Reorderable 库后，CrossLineDragManager 整文件已删除。
     */
    onVoiceReorder: ((lineIndex: Int, newOrder: List<com.corgimemo.app.ui.model.VoiceAttachment>) -> Unit)? = null,
    /** 各分组的保存状态（key=groupId, value=保存状态） */
    groupSaveStates: Map<Int, com.corgimemo.app.viewmodel.GroupSaveState> = emptyMap(),
    /** 各分组的优先级（key=groupId, value=优先级 0=无,1=低,2=中,3=高） */
    groupPriorities: Map<Int, Int> = emptyMap(),
    /** 优先级按钮点击回调（参数=groupId） */
    onPriorityButtonClick: ((Int) -> Unit)? = null,
    // 🆕 关联功能相关参数（v2026-07-21 新增）
    /** 各分组的关联列表（key=groupId, value=该分组的关联列表） */
    groupRelations: Map<Int, List<com.corgimemo.app.data.model.CardRelation>> = emptyMap(),
    /** 关联ID → 标题的映射（由 ViewModel 异步加载） */
    relationTitles: Map<Long, String> = emptyMap(),
    /** 点击 ＋ 添加关联按钮的回调（参数=groupId） */
    onAddRelationClick: ((Int) -> Unit)? = null,
    /** 点击 Chip 弹预览的回调（参数=关联实体） */
    onPreviewRelation: ((com.corgimemo.app.data.model.CardRelation) -> Unit)? = null,
    /** 点击 × 删除关联的回调（参数=relationId, groupId） */
    onDeleteRelation: ((Long, Int) -> Unit)? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndoClick: (() -> Unit)? = null,
    onRedoClick: (() -> Unit)? = null,
    /**
     * 外部触发的焦点转移目标行索引（v2026-07-25 新增）
     *
     * - 默认 -1：无操作
     * - 非负整数：触发 focusRequesters[it]?.requestFocus()
     *
     * 与内部 [pendingFocusIndex] 区别：
     * - [pendingFocusIndex] 由"/"新建行触发，是组件内部 state
     * - [externalPendingFocus] 由外部（如删除分组后焦点转移）触发，
     *   修改后通过 LaunchedEffect 同步到内部 pendingFocusIndex，复用同一套焦点转移机制
     *
     * 使用方：[com.corgimemo.app.ui.screens.todo.TodoEditScreen] 在调用
     * [com.corgimemo.app.viewmodel.TodoEditViewModel.deleteGroupByLineIndex] 后，
     * 将返回的新聚焦行索引写入本参数，触发焦点转移到上一分组首行。
     *
     * 注意：连续两次返回相同的索引值时，需配合 [externalPendingFocusTrigger] 递增触发器
     * 才能保证 LaunchedEffect 一定执行。
     */
    externalPendingFocus: Int = -1,
    /**
     * 外部焦点转移触发器（v2026-07-25 新增）
     *
     * 配合 [externalPendingFocus] 使用：每次调用 deleteGroupByLineIndex 后递增本值，
     * 强制 LaunchedEffect 触发，避免连续两次返回相同行索引时不执行焦点转移。
     */
    externalPendingFocusTrigger: Int = 0,
    /**
     * 🆕 v2026-07-25 各容器的预计时长文本（key=groupId, value=格式化字符串如"1小时30分"）
     *
     * - null 或不存在：该容器底部不显示预计时长行
     * - 非空字符串：在容器底部左下角渲染 "⏱️ 预计时长: $value"
     *
     * 由 TodoEditScreen 根据 reminderTime/dueDate 计算后按 groupId 传入。
     */
    groupEstimatedDurations: Map<Int, String?> = emptyMap(),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "回车可连续添加子待办，输入 / 可新建待办"
) {
    val focusManager = LocalFocusManager.current

    /** 记录当前聚焦的行索引 */
    var focusedLineIndex by remember { mutableIntStateOf(0) }

    /** "/" 新建待办后，需要转移焦点的目标行索引（-1 表示无） */
    var pendingFocusIndex by remember { mutableIntStateOf(-1) }

    /** 收集每行的 FocusRequester，用于 "/" 新建后转移焦点 */
    val focusRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }

    /**
     * 🆕 v2026-07-25 各行"光标置末尾"触发器（key=lineIndex, value=trigger 计数）
     *
     * 用途：当 handleKeyEvent 在 KEYCODE_DEL 分支删除空行后，
     * 需要把上一行光标强制设置到文本末尾（满足"删除到上一行时光标从上一行行尾开始往前删"的需求）。
     *
     * 机制：对上一行 lineIndex 递增本 map 对应 value，
     * 由 CheckboxEditRow 内 LaunchedEffect(cursorAtEndTrigger) 监听并设置光标位置。
     *
     * 用 SnapshotStateMap 而非普通 Map 是为了让 CheckboxEditRow 能感知变化并触发 LaunchedEffect。
     */
    val cursorAtEndTriggers = remember { mutableStateMapOf<Int, Int>() }

    /**
     * 🆕 语音播放器实例管理
     *
     * 以 "行索引_语音索引" 为 key，为每个语音附件创建独立的 VoicePlayer 实例。
     * 使用 remember 保持实例稳定（不会因重组而重建）。
     *
     * 数据结构：key = "${lineIndex}_${voiceIndex}", value = VoicePlayer 实例
     */
    val context = LocalContext.current
    val voicePlayerMap = remember { mutableStateMapOf<String, com.corgimemo.app.util.VoicePlayer>() }

    /**
     * 🆕 多语音互斥播放控制
     *
     * 播放指定语音时，自动暂停所有其他正在播放的语音。
     * 确保同一时间只有一条语音在播放，避免音频重叠干扰。
     *
     * @param targetKey 目标语音的 key（"${lineIndex}_${voiceIndex}"），该语音不会被暂停
     */
    fun pauseAllOtherVoices(targetKey: String) {
        voicePlayerMap.forEach { (key, player) ->
            if (key != targetKey && player.isPlaying.value) {
                player.pause()
            }
        }
    }

    /**
     * 🆕 行数据变化时清理过期的 VoicePlayer 实例
     *
     * 当行被删除或语音附件被移除时，对应的 VoicePlayer 需要释放资源：
     * - stop()：停止播放
     * - release()：释放 MediaPlayer 资源
     * - 从 map 中移除引用
     */
    LaunchedEffect(lines) {
        /** 构建当前所有有效语音的 key 集合 */
        val validKeys = mutableSetOf<String>()
        lines.forEachIndexed { lineIdx, line ->
            line.voiceAttachments.forEachIndexed { voiceIdx, _ ->
                validKeys.add("${lineIdx}_${voiceIdx}")
            }
        }
        /** 找出需要清理的过期 key */
        val keysToRemove = voicePlayerMap.keys - validKeys
        keysToRemove.forEach { key ->
            voicePlayerMap[key]?.stop()
            voicePlayerMap[key]?.release()
            voicePlayerMap.remove(key)
        }
    }

    // 每次 lines 变化时重建焦点映射，避免索引漂移导致的过期条目
    focusRequesters.clear()

    // "/" 新建待办后，自动将焦点转移到新行
    LaunchedEffect(pendingFocusIndex) {
        if (pendingFocusIndex >= 0) {
            // 等待新行渲染完成后再请求焦点（延迟足够长以覆盖多次快速操作）
            kotlinx.coroutines.delay(100)
            focusRequesters[pendingFocusIndex]?.requestFocus()
            pendingFocusIndex = -1
        }
    }

    // 🆕 v2026-07-25 外部触发的焦点转移（删除分组后转移到目标聚焦行）
    // - 子分组删除：转移到上一分组首行（原行为）
    // - 主分组删除（多容器场景）：转移到下一分组首行（即提升后的新主分组首行，索引 0）
    // 监听 externalPendingFocusTrigger 递增触发器，确保连续两次返回相同行索引时仍能触发
    // （只监听 externalPendingFocus 会有 key 相同不触发的边界问题）
    LaunchedEffect(externalPendingFocusTrigger) {
        if (externalPendingFocusTrigger > 0 && externalPendingFocus >= 0) {
            // 等待删除后 todoLines 重新渲染完成（focusRequesters 注册回调先于本 LaunchedEffect 执行）
            kotlinx.coroutines.delay(100)
            focusRequesters[externalPendingFocus]?.requestFocus()
            // 同步局部 focusedLineIndex，避免后续 onFocusedLineChange 回调时与 ViewModel 不一致
            focusedLineIndex = externalPendingFocus
        }
    }

    // 按 groupId 分组
    val groups = remember(lines) {
        lines.groupBy { it.groupId }.toSortedMap()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (groups.isEmpty()) {
            // 无任何内容时显示占位容器
            // 用 Column 包裹"容器+预计时长"作为一组，组内紧贴，组间由外层 spacedBy(8.dp) 控制
            Column {
                TodoGroupContainer(
                    groupId = 0,
                    showBottomBar = true,
                    onReminderClick = { onReminderClick?.invoke(0) },
                    reminderTime = groupReminders[0],
                    onReminderDelete = { onReminderDelete?.invoke(0) },
                    categoryId = groupCategoryIds[0] ?: 0L,
                    categoryName = groupCategoryNames[0],
                    onCategoryClick = { onCategoryClick?.invoke(0) },
                    onCategoryClear = { onCategoryClear?.invoke(0) },
                    priority = priority,
                    onPriorityClick = { onPriorityButtonClick?.invoke(0) },
                    onSaveClick = { onSaveClick?.invoke(0) },
                    // 🆕 关联功能参数（v2026-07-21 新增）
                    groupRelations = groupRelations[0] ?: emptyList(),
                    relationTitles = relationTitles,
                    onAddRelationClick = onAddRelationClick?.let { cb -> { cb(0) } },
                    onPreviewRelation = onPreviewRelation,
                    onDeleteRelation = onDeleteRelation,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndoClick = onUndoClick,
                    onRedoClick = onRedoClick
                ) {
                    CheckboxEditRow(
                        lineIndex = 0,
                        line = TodoLine(stableId = TodoLine.generateStableId(), groupId = 0),
                    isEnabled = enabled,
                    isFocused = true,
                    placeholder = placeholder,
                    isGroupFirst = true,
                    onTextChange = { newText ->
                                // "/" 检测：输入 "/" 时在当前行下方创建新待办容器
                                if (newText.endsWith("/")) {
                                    val textWithoutSlash = newText.removeSuffix("/")
                                    // 🆕 v2026-07-25 架构根治：新建 TodoLine 时分配 stableId
                                    val newLine = TodoLine(stableId = TodoLine.generateStableId(), text = textWithoutSlash, groupId = 0)
                                    onLinesChange(listOf(newLine))
                                    onNewGroupRequested?.invoke(0, textWithoutSlash)
                                    pendingFocusIndex = 1 // 新行插入在 index 1
                                } else {
                                    val newLine = TodoLine(stableId = TodoLine.generateStableId(), text = newText, groupId = 0)
                                    onLinesChange(listOf(newLine))
                                }
                                detectSpecialChars(newText, onSpecialCharDetected)
                            },
                    onKeyEvent = { false },
                    onFocusChange = { isFocused ->
                        if (isFocused) {
                            focusedLineIndex = 0
                            onFocusedLineChange?.invoke(0)
                        }
                    },
                    onCheckedChange = {},
                    onRegisterFocusRequester = { idx, fr -> focusRequesters[idx] = fr },
                    onImageClick = { },
                    onDeleteImage = { },
                    onDeleteVoice = { },
                    /** 🆕 空状态占位行也需要传入语音播放器参数（虽然不会有语音附件）*/
                    voicePlayerMap = voicePlayerMap,
                    context = context,
                    pauseAllOtherVoices = ::pauseAllOtherVoices
                )
                }

                // 🆕 v2026-07-25 容器外左下角预计时长显示
                // 位置：紧贴容器底部外侧，左对齐（start=4dp 与容器内边距对齐）
                // 由 TodoEditScreen 根据 reminderTime/dueDate 计算后按 groupId 传入
                groupEstimatedDurations[0]?.let { duration ->
                    Text(
                        text = "⏱️ 预计时长: $duration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        } else {
            var globalIndex = 0
            for ((groupId, groupLines) in groups) {
                val groupFirstIndex = globalIndex

                // 计算当前容器是否已保存
                val isGroupSaved = groupSaveStates[groupId]?.isSaved == true
                /** 获取当前分组的优先级（0=无,1=低,2=中,3=高） */
                val groupPriority = groupPriorities[groupId] ?: 0

                // 用 Column 包裹"容器+预计时长"作为一组，组内紧贴，组间由外层 spacedBy(8.dp) 控制
                Column {
                TodoGroupContainer(
                    groupId = groupId,
                    showBottomBar = true,
                    isSaved = isGroupSaved,
                    priority = groupPriority,  // 传递分组独立优先级
                    onReminderClick = { onReminderClick?.invoke(groupId) },
                    reminderTime = groupReminders[groupId],
                    onReminderDelete = { onReminderDelete?.invoke(groupId) },
                categoryId = groupCategoryIds[groupId] ?: 0L,
                categoryName = groupCategoryNames[groupId],
                onCategoryClick = { onCategoryClick?.invoke(groupId) },
                onCategoryClear = { onCategoryClear?.invoke(groupId) },
                onPriorityClick = { onPriorityButtonClick?.invoke(groupId) },
                    onSaveClick = { onSaveClick?.invoke(groupId) },
                    // 🆕 关联功能参数（v2026-07-21 新增）
                    groupRelations = groupRelations[groupId] ?: emptyList(),
                    relationTitles = relationTitles,
                    onAddRelationClick = onAddRelationClick?.let { cb -> { cb(groupId) } },
                    onPreviewRelation = onPreviewRelation,
                    onDeleteRelation = onDeleteRelation,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndoClick = onUndoClick,
                    onRedoClick = onRedoClick
                ) {
                    groupLines.forEachIndexed { localIndex, line ->
                        val currentIndex = globalIndex++

                        CheckboxEditRow(
                            lineIndex = currentIndex,
                            line = line,
                            isEnabled = enabled,
                            isFocused = focusedLineIndex == currentIndex,
                            placeholder = if (localIndex == 0 && !line.isSubTask && line.text.isBlank()) placeholder else "",
                            isGroupFirst = localIndex == 0 && !line.isSubTask,
                            onTextChange = { newText ->
                                // "/" 检测：输入 "/" 时在当前行下方创建新待办容器
                                if (newText.endsWith("/")) {
                                    val textWithoutSlash = newText.removeSuffix("/")
                                    updateLineAt(line, lines, textWithoutSlash, onLinesChange)
                                    onNewGroupRequested?.invoke(currentIndex, textWithoutSlash)
                                    pendingFocusIndex = currentIndex + 1 // 焦点转移到新行
                                } else {
                                    updateLineAt(line, lines, newText, onLinesChange)
                                }
                                detectSpecialChars(newText, onSpecialCharDetected)
                            },
                            onCheckedChange = { checked ->
                                onLineCheckToggle(currentIndex, checked)
                                val updatedLines = lines.toMutableList()
                                if (!line.isSubTask) {
                                    // 主待办：级联更新同组所有子待办
                                    for (i in updatedLines.indices) {
                                        if (updatedLines[i].groupId == line.groupId) {
                                            updatedLines[i] = updatedLines[i].copy(isChecked = checked)
                                        }
                                    }
                                } else {
                                    // 子待办：仅更新自身
                                    // 修复：添加 isSubTask 条件，避免当子任务 order=0 时误匹配到主任务行（groupId=0, order=0）
                                    // 导致主任务行被子任务内容覆盖、标题消失
                                    val targetIndex = updatedLines.indexOfFirst { it.groupId == line.groupId && it.order == line.order && it.isSubTask }
                                    if (targetIndex >= 0 && targetIndex < updatedLines.size) {
                                        updatedLines[targetIndex] = line.copy(isChecked = checked)
                                    }
                                }
                                onLinesChange(updatedLines)
                            },
                            onKeyEvent = { keyEvent ->
                                handleKeyEvent(
                                    keyEvent = keyEvent,
                                    index = currentIndex,
                                    lines = lines,
                                    line = line,
                                    onLinesChange = onLinesChange,
                                    // 🆕 v2026-07-25 修复 Bug：handleKeyEvent 的 onFocusChange 也需要通知 ViewModel
                                    // 之前只更新局部 focusedLineIndex，导致 ViewModel 的 focusedLineIndex 与内部不同步
                                    // 表现：用户用键盘删除行后，ViewModel 不知道焦点已转移，删除按钮 onClick 用旧值
                                    // 修复：在更新内部状态的同时也调用 onFocusedLineChange 回调
                                    onFocusChange = { newFocusIdx ->
                                        focusedLineIndex = newFocusIdx
                                        onFocusedLineChange?.invoke(newFocusIdx)
                                    },
                                    focusManager = focusManager,
                                    onNewGroupRequested = onNewGroupRequested,
                                    onNewSubTaskRequested = onNewSubTaskRequested,
                                    /**
                                     * 🆕 v2026-07-25 光标置末尾回调
                                     *
                                     * handleKeyEvent 在 KEYCODE_DEL 分支删除空行后调用，
                                     * 参数是上一行的新索引（index - 1）。
                                     * 本回调对 cursorAtEndTriggers map 中对应行的计数 +1，
                                     * 触发 CheckboxEditRow 内 LaunchedEffect(cursorAtEndTrigger) 设置光标到末尾。
                                     */
                                    onCursorAtEndRequested = { targetIdx ->
                                        cursorAtEndTriggers[targetIdx] =
                                            (cursorAtEndTriggers[targetIdx] ?: 0) + 1
                                    }
                                )
                            },
                            onFocusChange = { isFocused ->
                                if (isFocused) {
                                    // 🆕 v2026-07-25 架构根治：onFocusChange 用 stableId 反查 freshIndex
                                    //
                                    // 根因（已通过日志确认）：
                                    // Compose 的 onFocusChanged modifier 回调可能在重组后还被旧 lambda 触发，
                                    // 旧 lambda 捕获的 currentIndex 是上次重组时 globalIndex 计算的结果，
                                    // 与当前 lines 列表不匹配（如 lines.size=3 但 currentIndex=5）。
                                    //
                                    //根治方案：
                                    // 1. TodoLine 新增 stableId 字段（跨重组稳定，copy 时保留）
                                    // 2. onFocusChange 触发时，通过 line.stableId 在当前 lines 中反查最新 index
                                    // 3. 即使旧 lambda 被触发，line.stableId 仍能找到当前 lines 中正确的位置
                                    //
                                    // 优势（相比之前的 line 引用反查）：
                                    // - line 引用（===）在 copy() 后会失效，需要 fallback 到 groupId
                                    // - stableId 在 copy() 后保留，反查更可靠
                                    // - stableId 全局唯一，不会出现多个 line 命中同一反查的情况
                                    val freshIndex = lines.indexOfFirst { it.stableId == line.stableId }
                                    val safeIndex = if (freshIndex >= 0) {
                                        freshIndex
                                    } else {
                                        // 兜底：stableId 未找到（理论不会发生，除非 line 是完全过期的引用）
                                        // 用 groupId 反查首行作为最后防线
                                        val byGroupId = lines.indexOfFirst { it.groupId == line.groupId }
                                        if (byGroupId >= 0) byGroupId else currentIndex
                                    }
                                    if (safeIndex != currentIndex) {
                                        android.util.Log.w(
                                            "TodoEditDelete",
                                            "onFocusChange 闭包 currentIndex=$currentIndex 已过期，" +
                                            "stableId=${line.stableId} 反查 safeIndex=$safeIndex " +
                                            "(freshIndex=$freshIndex, groupId=${line.groupId}, size=${lines.size})"
                                        )
                                    }
                                    focusedLineIndex = safeIndex
                                    onFocusedLineChange?.invoke(safeIndex)
                                }
                            },
                            onRegisterFocusRequester = { idx, fr -> focusRequesters[idx] = fr },
                            onImageClick = { imagePath -> onImageClick?.invoke(currentIndex, imagePath) },
                            onDeleteImage = { imagePath -> onDeleteImage?.invoke(currentIndex, imagePath) },
                            onDeleteVoice = { voicePath -> onDeleteVoice?.invoke(currentIndex, voicePath) },
                            // 🆕 v2026-07-27 P5 改造：图片行内排序回调
                            // Reorderable 库 onMove 触发时调用此回调，参数：(行索引, 排序后新图片路径列表)
                            // UI 层（TodoEditScreen）接到后调用 viewModel.applyImageReorder() 写回数据
                            onImageReorder = { newOrder -> onImageReorder?.invoke(currentIndex, newOrder) },
                            /**
                             * 🆕 v2026-07-27 P6 改造：语音行内排序回调
                             * Reorderable 库 onMove 触发时调用此回调，参数：排序后的新语音附件列表
                             * UI 层（TodoEditScreen）接到后调用 viewModel.applyVoiceReorder() 写回数据
                             */
                            onVoiceReorder = { newOrder -> onVoiceReorder?.invoke(currentIndex, newOrder) },
                            /** 🆕 传入语音播放器相关参数 */
                            voicePlayerMap = voicePlayerMap,
                            context = context,
                            pauseAllOtherVoices = ::pauseAllOtherVoices,
                            /**
                             * 🆕 v2026-07-25 光标置末尾触发器
                             *
                             * 从 cursorAtEndTriggers map 读取当前行的触发器计数，
                             * 当 handleKeyEvent 删除空行后对上一行触发计数 +1，
                             * 本行 LaunchedEffect 监听到变化后把光标设置到文本末尾。
                             */
                            cursorAtEndTrigger = cursorAtEndTriggers[currentIndex] ?: 0
                        )
                    }
                }

                // 🆕 v2026-07-25 容器外左下角预计时长显示
                // 位置：紧贴容器底部外侧，左对齐（start=4dp 与容器内边距对齐）
                // 由 TodoEditScreen 根据 reminderTime/dueDate 计算后按 groupId 传入
                groupEstimatedDurations[groupId]?.let { duration ->
                    Text(
                        text = "⏱️ 预计时长: $duration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
                } // Column（容器+预计时长一组）结尾
            }
        }
    }
}

/**
 * 单个待办组的圆角容器（含底部操作栏）
 *
 * 底部操作栏采用Chips标签布局（v2026-07-21 重新设计）：
 * - Chips行：[🔔提醒] [●优先级] [📁分组] 三个圆角胶囊标签水平排列
 * - 按钮行：[↩撤销] [↪恢复] ... [完成] 撤销/恢复在左，完成在右
 *
 * @param priority 优先级 (0=无, 1=低, 2=中, 3=高)
 * @param onPriorityClick 优先级按钮点击回调（触发弹窗）
 * @param onUndoClick 撤销按钮点击回调
 * @param onRedoClick 恢复按钮点击回调
 * @param canUndo 是否可撤销（控制撤销按钮启用/禁用状态）
 * @param canRedo 是否可恢复（控制恢复按钮启用/禁用状态）
 */
@Composable
private fun TodoGroupContainer(
    groupId: Int,
    showBottomBar: Boolean,
    isSaved: Boolean = false,
    onReminderClick: (() -> Unit)? = null,
    reminderTime: Long? = null,
    onReminderDelete: (() -> Unit)? = null,
    categoryId: Long? = null,
    categoryName: String? = null,
    onCategoryClick: (() -> Unit)? = null,
    onCategoryClear: (() -> Unit)? = null,
    priority: Int = 0,
    onPriorityClick: (() -> Unit)? = null,
    onSaveClick: (() -> Unit)? = null,
    onUndoClick: (() -> Unit)? = null,
    onRedoClick: (() -> Unit)? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    // 🆕 关联功能相关参数（v2026-07-21 新增）
    /** 当前分组的关联列表 */
    groupRelations: List<com.corgimemo.app.data.model.CardRelation> = emptyList(),
    /** 关联ID → 标题的映射 */
    relationTitles: Map<Long, String> = emptyMap(),
    /** 点击 ＋ 添加关联按钮的回调 */
    onAddRelationClick: (() -> Unit)? = null,
    /** 点击 Chip 弹预览的回调 */
    onPreviewRelation: ((com.corgimemo.app.data.model.CardRelation) -> Unit)? = null,
    /** 点击 × 删除关联的回调（参数=relationId, groupId） */
    onDeleteRelation: ((Long, Int) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    /**
     * 根据优先级计算边框颜色
     * - 3=高 → 柔红
     * - 2=中 → 柔橙
     * - 1=低 → 柔蓝
     * - 0=无 → 浅绿 #C8E6C9（v2026-07-20 新增，与首页/回收站统一"无优先级也显示边框"的视觉）
     */
    val borderColor = when (priority) {
        3 -> PriorityColors.colorOf(3)  // 高优先级
        2 -> PriorityColors.colorOf(2)  // 中优先级
        1 -> PriorityColors.colorOf(1)  // 低优先级
        else -> PriorityColors.colorOf(0)  // 无优先级：浅绿色边框（v2026-07-20）
    }

    /**
     * 优先级单字显示（v2026-07-21 Chips新设计：只显示无/低/中/高单字）
     */
    val prioritySingleChar = when (priority) {
        3 -> "高"
        2 -> "中"
        1 -> "低"
        else -> "无"
    }

    /**
     * 分组名称截断显示：超长时只显示前两个字+省略号
     */
    val groupDisplayText = if (categoryName != null) {
        if (categoryName.length > 2) categoryName.take(2) + "…" else categoryName
    } else {
        "分组"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                // 优先级边框颜色（borderColor 来自 when 表达式必返回非空 Color，移除冗余空判断 2026-07-20）
                Modifier.border(
                    width = 1.5.dp,
                    color = borderColor.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()

        // 底部操作栏：Chips标签布局（v2026-07-21 重新设计）
        // 结构：分割线 / Chips行([🔔提醒][●优先级][📁分组]) / 分割线 / 按钮行([↩撤销][↪恢复]...[完成])
        if (showBottomBar) {
            // 实时刷新当前时间（30s轮询检测过期）
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(reminderTime) {
                now = System.currentTimeMillis()
                while (true) {
                    val nextTick = ((System.currentTimeMillis() / 30_000L) + 1L) * 30_000L
                    kotlinx.coroutines.delay(nextTick - System.currentTimeMillis())
                    now = System.currentTimeMillis()
                }
            }

            // 提醒相关状态
            val isOverdue = reminderTime != null && reminderTime < now
            val reminderText = reminderTime?.let { formatReminderDisplay(it, now).text } ?: "提醒"
            val reminderChipColor = if (isOverdue) Color(0xFFDC2626) else Color(0xFFFF9A5C)
            val reminderChipBg = if (isOverdue) Color(0xFFFFF5F5) else Color(0xFFFFF5EE)
            val reminderChipBorder = if (isOverdue) Color(0xFFDC2626) else Color(0xFFFF9A5C)

            // 优先级chip背景色
            val priorityChipBg = when (priority) {
                3 -> Color(0xFFFFF5F5)
                2 -> Color(0xFFFFF8EB)
                1 -> Color(0xFFF0F7FC)
                else -> Color(0xFFF5F5F5)
            }

            // 分组chip颜色
            val groupChipColor = if (categoryName != null) Color(0xFFFF9A5C) else Color(0xFF999999)
            val groupChipBorder = if (categoryName != null) Color(0xFFFF9A5C) else Color(0xFFEEEEEE)
            val groupChipBg = if (categoryName != null) Color(0xFFFFF5EE) else Color.Transparent

            // 未设置chip默认颜色
            val defaultChipBorder = Color(0xFFEEEEEE)
            val defaultChipText = Color(0xFF999999)

            // 分割线颜色
            val dividerColor = Color(0xFFEEEEEE)

            // 撤销/恢复按钮颜色（与编辑区底部工具栏图标颜色一致：onSurfaceVariant #666666）
            val historyEnabledColor = Color(0xFF666666)
            val historyDisabledColor = Color(0xFF999999)

            // 完成按钮颜色（柔和绿#7EC8A0代替刺眼绿#4CAF50）
            val saveTextColor = if (isSaved) Color(0xFF7EC8A0) else Color(0xFFFF9A5C)
            val saveText = if (isSaved) "已保存" else "完成"

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 顶部分割线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )

                // Chips行：固定高度36dp，内容垂直居中
                // 宽度策略（v2026-07-21 调整）：
                // - 优先级chip：固定宽度44dp（只显示圆点+单字）
                // - 分组chip：自适应内容宽度（分组名已两字截断，内容固定较短）
                // - 提醒chip：weight(1f, fill=false) 占据剩余空间，默认按内容宽度，长文本时在剩余空间内ellipsis
                // 顺序：[提醒] [分组] [优先级]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chip1: 设置提醒（weight(1f,fill=false) 占据剩余空间，文本超长ellipsis）
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (reminderTime != null) {
                                    Modifier
                                        .background(reminderChipBg)
                                        .border(1.dp, reminderChipBorder, RoundedCornerShape(14.dp))
                                } else {
                                    Modifier.border(1.dp, defaultChipBorder, RoundedCornerShape(14.dp))
                                }
                            )
                            .clickable(enabled = onReminderClick != null) { onReminderClick?.invoke() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = if (reminderTime != null) "已设置提醒" else "设置提醒",
                                tint = if (reminderTime != null) reminderChipColor else defaultChipText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = reminderText,
                                fontSize = 12.sp,
                                color = if (reminderTime != null) reminderChipColor else defaultChipText,
                                fontWeight = if (reminderTime != null) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // weight(1f,fill=false) 让Text在有×按钮时仍能正确ellipsis，默认状态按内容宽度
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (reminderTime != null && onReminderDelete != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "×",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666),
                                    modifier = Modifier.clickable { onReminderDelete() }
                                )
                            }
                        }
                    }

                    // Chip2: 分组（自适应内容宽度，因为分组名已两字截断）
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(groupChipBg)
                            .border(1.dp, groupChipBorder, RoundedCornerShape(14.dp))
                            .clickable(enabled = onCategoryClick != null) { onCategoryClick?.invoke() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            // 文件夹图标📁
                            Text(
                                text = "📁",
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = groupDisplayText,
                                fontSize = 12.sp,
                                color = if (categoryName != null) groupChipColor else defaultChipText,
                                fontWeight = if (categoryName != null) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (categoryName != null && onCategoryClear != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "×",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666),
                                    modifier = Modifier.clickable { onCategoryClear() }
                                )
                            }
                        }
                    }

                    // Chip3: 优先级（固定宽度44dp，只显示圆点+单字）
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(priorityChipBg)
                            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .clickable(enabled = onPriorityClick != null) { onPriorityClick?.invoke() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(borderColor)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = prioritySingleChar,
                                fontSize = 12.sp,
                                color = borderColor,
                                fontWeight = if (priority > 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 底部分割线（Chips 行下方）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )

                // 🆕 已关联区域：横向 Chip 流（v2026-07-21 新增）
                // 仅当 onAddRelationClick 不为空时渲染（即编辑模式，预览模式不显示）
                if (onAddRelationClick != null) {
                    LinkedCardsRow(
                        relations = groupRelations,
                        groupId = groupId,
                        relationTitles = relationTitles,
                        onAddClick = { onAddRelationClick?.invoke() },
                        onChipClick = { relation -> onPreviewRelation?.invoke(relation) },
                        onChipDelete = { relationId, gid -> onDeleteRelation?.invoke(relationId, gid) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // 已关联区域下方分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(dividerColor)
                    )
                }

                // 按钮行：固定高度36dp，与Chips行等高，撤销/恢复在左，完成在右
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 撤销按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = canUndo && onUndoClick != null) { onUndoClick?.invoke() }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "↩",
                            fontSize = 14.sp,
                            color = if (canUndo) historyEnabledColor else historyDisabledColor
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "撤销",
                            fontSize = 14.sp,
                            color = if (canUndo) historyEnabledColor else historyDisabledColor,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 恢复按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = canRedo && onRedoClick != null) { onRedoClick?.invoke() }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "↪",
                            fontSize = 14.sp,
                            color = if (canRedo) historyEnabledColor else historyDisabledColor
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "恢复",
                            fontSize = 14.sp,
                            color = if (canRedo) historyEnabledColor else historyDisabledColor,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 完成按钮（纯文字，无箭头无背景无阴影）
                    Text(
                        text = saveText,
                        color = saveTextColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable(enabled = onSaveClick != null && !isSaved) { onSaveClick?.invoke() }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * 单行复选框编辑行
 *
 * 渲染一行：[缩进] [复选框] [文本输入框]
 * 如果该行有附件，在文本输入框下方显示附件预览：
 * - 图片：横向滚动的图片列表
 * - 语音：语音播放器列表
 * 子任务行的附件会跟随缩进
 */
@Composable
private fun CheckboxEditRow(
    lineIndex: Int,
    line: TodoLine,
    isEnabled: Boolean,
    isFocused: Boolean,
    placeholder: String,
    isGroupFirst: Boolean,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onKeyEvent: (android.view.KeyEvent) -> Boolean,
    onFocusChange: (Boolean) -> Unit,
    onRegisterFocusRequester: (Int, FocusRequester) -> Unit = { _, _ -> },
    onImageClick: (String) -> Unit = {},
    onDeleteImage: (String) -> Unit = {},
    onDeleteVoice: (String) -> Unit = {},
    /**
     * 🆕 v2026-07-27 P5 改造：图片行内排序回调（替代原图片拖拽相关参数）
     *
     * 参数：newOrder - 排序后的新图片路径列表（Reorderable 库 onMove 触发）
     * 由外层 CheckboxEditText 收到回调后转给 UI 层（TodoEditScreen）调用 viewModel.applyImageReorder()
     */
    onImageReorder: (List<String>) -> Unit = {},
    /**
     * 🆕 v2026-07-27 P6 改造：语音行内排序回调
     *
     * 参数：newOrder - 排序后的新语音附件列表（Reorderable 库 onMove 触发）
     * 由外层 CheckboxEditText 收到回调后转给 UI 层（TodoEditScreen）调用 viewModel.applyVoiceReorder()
     */
    onVoiceReorder: (List<com.corgimemo.app.ui.model.VoiceAttachment>) -> Unit = {},
    /** 行修饰符（保留：外层可能传 padding/clickable 等） */
    modifier: Modifier = Modifier,
    /** 🆕 语音播放器实例管理 Map（由外层 CheckboxEditText 传入）*/
    voicePlayerMap: kotlin.collections.MutableMap<String, com.corgimemo.app.util.VoicePlayer>,
    /** 🆕 Android Context（用于创建 VoicePlayer 实例）*/
    context: android.content.Context,
    /** 🆕 多语音互斥播放控制函数*/
    pauseAllOtherVoices: (String) -> Unit,
    /**
     * 🆕 v2026-07-25 光标置末尾触发器
     *
     * 用途：当外部需要强制把该行的光标位置设置到文本末尾时，递增本值即可触发。
     * 典型场景：用户在子待办行按 Backspace 删除空行后，焦点转移到上一行，
     * 此时上一行的光标必须落在文本末尾，用户继续按 Backspace 才能从行尾往前删除。
     *
     * 实现：LaunchedEffect(cursorAtEndTrigger) 监听本值变化，
     * 触发时把本地 TextFieldValue.selection 设置为 TextRange(text.length)。
     */
    cursorAtEndTrigger: Int = 0
) {
    /** 复选框颜色动画 */
    val checkboxColor by animateColorAsState(
        targetValue = if (line.isChecked) Color(0xFF7EC8A0) else Color(0xFFCCCCCC),
        label = "checkboxColor"
    )

    /** 文本颜色：已完成时使用次要色+删除线效果 */
    val textColor = if (line.isChecked) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val focusRequester = remember { FocusRequester() }

    // 注册 FocusRequester 到外层 map，用于 "/" 新建后转移焦点
    onRegisterFocusRequester(lineIndex, focusRequester)

    /**
     * 🆕 v2026-07-25 本地 TextFieldValue 状态
     *
     * 改造动机：原 BasicTextField 用 String 重载（value = line.text），
     * Compose 内部维护 TextFieldValue 状态。当通过 requestFocus() 转移焦点到
     * 一行从未被聚焦过的 BasicTextField 时，光标位置默认是 0（行首），
     * 导致用户按 Backspace 删除空行后转到上一行时，光标在上一行行首，
     * 继续按 Backspace 无法删除任何字符。
     *
     * 改造为 TextFieldValue 重载后，可显式控制光标位置：
     * - 正常输入：onValueChange 同步更新本地状态 + 调用 onTextChange
     * - 外部 line.text 变化（撤销/恢复/DB 加载）：LaunchedEffect 同步本地状态，保持光标位置合理
     * - cursorAtEndTrigger 触发：强制把光标设置到文本末尾
     *
     * 用 stableId 作为 remember key：行删除时 stableId 消失，避免状态错位到其他行
     */
    var textFieldValue by remember(line.stableId) {
        mutableStateOf(TextFieldValue(text = line.text, selection = TextRange(line.text.length)))
    }

    /**
     * 同步外部 line.text 变化到本地 TextFieldValue
     *
     * 触发场景：
     * - 撤销/恢复操作后 line.text 从快照恢复
     * - 从 DB 加载初始数据
     * - 外部代码直接修改 todoLines（如 detectSpecialChars 移除 "/"）
     *
     * 同步策略：
     * - 文本相同 → 不更新（避免无意义重组）
     * - 文本不同 → 更新 text，保持原 selection（coerce 到新文本长度范围内）
     *   这样用户在编辑时光标不会被意外重置
     */
    LaunchedEffect(line.text) {
        if (textFieldValue.text != line.text) {
            val newStart = textFieldValue.selection.start.coerceIn(0, line.text.length)
            val newEnd = textFieldValue.selection.end.coerceIn(0, line.text.length)
            textFieldValue = TextFieldValue(
                text = line.text,
                selection = TextRange(newStart, newEnd)
            )
        }
    }

    /**
     * 🆕 v2026-07-25 光标置末尾触发器
     *
     * 外部递增 cursorAtEndTrigger 时，强制把本地 TextFieldValue.selection
     * 设置为 TextRange(text.length)，让光标落在文本末尾。
     *
     * 典型场景：用户在子待办行按 Backspace 删除空行后，焦点转移到上一行，
     * 此时需要把上一行光标设置到末尾，用户继续按 Backspace 才能从行尾往前删除。
     */
    LaunchedEffect(cursorAtEndTrigger) {
        if (cursorAtEndTrigger > 0) {
            textFieldValue = TextFieldValue(
                text = line.text,
                selection = TextRange(line.text.length)
            )
        }
    }

    /**
     * 行容器：应用外部传入的 modifier（保留接口供未来扩展）
     *
     * v2026-07-27 P5 改造：删除原 onGloballyPositioned 行边界捕获逻辑
     * 现图片拖拽已迁至 Reorderable 库，不再需要 rowBoundsMap。
     * modifier 参数保留但暂未由外层赋值（默认 Modifier）。
     */
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 子任务行缩进
        if (line.isSubTask) {
            Spacer(modifier = Modifier.width(28.dp))
        }

        // 复选框：圆角方形
        Box(
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = isEnabled) { onCheckedChange(!line.isChecked) }
                .clip(RoundedCornerShape(5.dp))
                .then(
                    if (line.isChecked) {
                        Modifier.background(Color(0xFF7EC8A0))
                    } else {
                        Modifier.background(Color(0xFFF0F0F0))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (line.isChecked) {
                Text(
                    text = "\u2713",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 文本输入区域
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                // 🆕 v2026-07-25 改用 TextFieldValue 重载
                // 同步更新本地 textFieldValue 状态 + 通知外部 line.text 变化
                textFieldValue = newValue
                onTextChange(newValue.text)
            },
            enabled = isEnabled,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = textColor,
                letterSpacing = 0.3.sp
            ).let { style ->
                if (line.isChecked) {
                    style.copy(textDecoration = TextDecoration.LineThrough)
                } else {
                    style
                }
            },
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    onFocusChange(focusState.isFocused)
                }
                .onPreviewKeyEvent { keyEvent ->
                    onKeyEvent(keyEvent.nativeKeyEvent)
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (line.text.isBlank() && placeholder.isNotBlank()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    onKeyEvent(android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_ENTER
                    ))
                }
            )
        )
    }

    /** 行级附件区域：显示在该行文本输入框下方 */
    /**
     * 支持拖拽功能的附件渲染区域
     *
     * 每张图片都使用 DraggableImageAttachment 组件，
     * 支持长按触发拖拽、行内排序和跨行移动。
     * 子任务行的附件会自动跟随缩进。
     */
    if (line.imagePaths.isNotEmpty() || line.voiceAttachments.isNotEmpty()) {
        // 子任务行的附件需要跟随缩进
        val attachmentIndent = if (line.isSubTask) 28.dp else 0.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = attachmentIndent, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            /**
             * 🆕 v2026-07-27 P5 改造：图片行用 LazyRow + Reorderable 库接管
             *
             * 原实现（v2026-07-27 之前）：Row + horizontalScroll + 手动边缘滚动 + DraggableImageAttachment（带 Popup 浮层）
             * 新实现：LazyRow + rememberReorderableLazyListState + ReorderableItem + 简化版 ImageAttachmentItem
             *
             * 收益：
             * - LazyRow 仅渲染可见项（图片上限 20 时必要）
             * - 边缘自动滚动由库内置 scroller 处理（无需 30+ 行手写）
             * - 多指手势由库内部处理（无需 70+ 行 pointerInput 手写）
             * - 拖拽状态机（SWAP/INSERT_BEFORE/INSERT_AFTER）由库接管
             * - 删除 CursorIndicator、Popup 浮层、行边界捕获等复杂逻辑
             */
            if (line.imagePaths.isNotEmpty()) {
                /**
                 * LazyRow + Reorderable 库核心 API（来自 sh.calvin.reorderable 3.1.0）
                 *
                 * rememberReorderableLazyListState 返回 state，绑定到 LazyRow 后：
                 * - onMove(from, to)：拖拽 from 到 to 时回调
                 * - 库内部自动处理：自动滚动、触觉、拖拽浮起视觉
                 *
                 * items + ReorderableItem 模式：
                 * - key 必须稳定（用 imagePath 作为 key）
                 * - ReorderableItem 块内 Modifier.draggableHandle 启用整 item 拖拽
                 */
                val lazyListState = rememberLazyListState()
                val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    /**
                     * 兜底：from == to 时早退，避免无意义重组
                     *
                     * Reorderable 库内部用 derivedStateOf 检测差异停止推进，
                     * 但 onMove 仍可能被触发；早退保证安全
                     */
                    if (from.index == to.index) return@rememberReorderableLazyListState

                    /**
                     * 构造排序后的新图片路径列表
                     *
                     * 1. 拷贝当前 list
                     * 2. removeAt(from.index) 取出源 item
                     * 3. add(to.index, item) 插入到目标位置
                     *
                     * 注意：库传回的是 LazyListItemInfo（key + index + offset + size），
                     * 我们只用 key 和 index
                     */
                    val currentList = line.imagePaths
                    val newList = currentList.toMutableList().apply {
                        val fromKey = from.key
                        val toIndex = to.index
                        if (fromKey is String) {
                            val fromIdx = indexOf(fromKey)
                            if (fromIdx >= 0) {
                                val item = removeAt(fromIdx)
                                add(toIndex.coerceIn(0, size), item)
                            }
                        }
                    }

                    /**
                     * 触发回调，UI 层接到后调用 viewModel.applyImageReorder() 写回数据
                     */
                    onImageReorder(newList)

                    /**
                     * 🆕 v2026-07-27 触觉反馈：拖拽完成时（onMove 触发）轻微震动
                     *
                     * 项目统一用 HapticFeedbackManager + InteractionType.CONFIRM
                     * 与 DraggableImageAttachment 原 onDragEnd 一致
                     */
                    HapticFeedbackManager.performHapticFeedback(
                        context = context,
                        type = InteractionType.CONFIRM,
                        enabled = true
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = lazyListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(
                        items = line.imagePaths,
                        key = { imagePath -> imagePath }
                    ) { imagePath ->
                        // 🆕 v2026-07-27 P5 改造：用 ReorderableItem 包裹每张图片
                        // ReorderableItem 提供 isDragging 状态，库自动处理 zIndex / 浮起视觉
                        ReorderableItem(
                            state = reorderableLazyListState,
                            key = imagePath
                        ) { isDragging ->
                            // 🆕 v2026-07-27 zIndex 必须在 LazyItemScope 内（项目踩坑：Compose 1.9.2）
                            // 当前 scope 是 LazyItemScope（items 块内），符合要求
                            val elevation by animateFloatAsState(
                                targetValue = if (isDragging) 8f else 0f,
                                label = "imageDragElevation"
                            )
                            Box(
                                modifier = Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        shadowElevation = elevation
                                        scaleX = if (isDragging) 1.08f else 1f
                                        scaleY = if (isDragging) 1.08f else 1f
                                        alpha = if (isDragging) 0.9f else 1f
                                    }
                            ) {
                                // 🆕 v2026-07-27 P5 改造：简化版图片附件组件
                                // 原 DraggableImageAttachment 删除 Popup 浮层、pointerInput 长按检测、graphicsLayer 拖拽
                                // 新组件只保留：缩略图渲染、点击查看大图、×删除按钮、isDragging 隐藏删除按钮
                                ImageAttachmentItem(
                                    imagePath = imagePath,
                                    isDragging = isDragging,
                                    onClick = { onImageClick(it) },
                                    onDelete = { onDeleteImage(it) },
                                    // 🆕 v2026-07-27 P5 改造：图片拖拽使用 LongPress 模式
                                    // 关键修正：默认 DragGestureDetector.Press 是"按下立即触发拖拽"，
                                    // 会与 LazyRow 水平滚动手势冲突 → 用户滑动查看图片时会误触拖拽。
                                    // 改用 LongPress 后：短按 + 拖 = 滚动查看图片；长按 + 拖 = 触发拖拽重排，与系统行为一致。
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            HapticFeedbackManager.performHapticFeedback(
                                                context = context,
                                                type = InteractionType.TEXT_MOVE,
                                                enabled = true
                                            )
                                        },
                                        onDragStopped = {},
                                        dragGestureDetector = DragGestureDetector.LongPress
                                    )
                                )
                            }
                        }
                    }
                }
            }

            /**
             * 🆕 v2026-07-27 P6 改造：语音行用 LazyRow + Reorderable 库接管
             *
             * 原实现（v2026-07-27 之前）：forEachIndexed + DraggableVoiceAttachment（带 3 个 onDrag* 回调 + 拖拽中简化 UI）
             * 新实现：LazyColumn + rememberReorderableLazyListState + ReorderableItem + 简化版 VoiceAttachmentItem
             *
             * 注：语音行用 LazyColumn（垂直排列）而非 LazyRow（水平排列），
             * 因为每个语音条是 fillMaxWidth 横向铺满，垂直堆叠显示。
             *
             * 收益：
             * - LazyColumn 仅渲染可见语音（语音条通常较大，懒加载更必要）
             * - 拖拽视觉由库接管（缩放 1.08 + 阴影 + zIndex）
             * - 删除 3 个 onDrag* 回调桥接 + 拖拽中简化 UI 模式
             * - 拖拽开始/暂停语义改用 onDragStarted 回调触发（由父组件 draggableHandle 提供）
             */
            if (line.voiceAttachments.isNotEmpty()) {
                /**
                 * 语音行 LazyColumn 状态管理
                 *
                 * 与图片 LazyRow 类似，rememberLazyListState + rememberReorderableLazyListState 模式
                 * onMove 触发时构造新顺序并通过 onVoiceReorder 回调上抛
                 */
                val voiceLazyListState = rememberLazyListState()
                val voiceReorderableState = rememberReorderableLazyListState(voiceLazyListState) { from, to ->
                    if (from.index == to.index) return@rememberReorderableLazyListState

                    val newList = line.voiceAttachments.toMutableList().apply {
                        val fromKey = from.key
                        val toIndex = to.index
                        if (fromKey is String) {
                            val fromIdx = indexOfFirst { it.path == fromKey }
                            if (fromIdx >= 0) {
                                val item = removeAt(fromIdx)
                                add(toIndex.coerceIn(0, size), item)
                            }
                        }
                    }
                    onVoiceReorder(newList)

                    HapticFeedbackManager.performHapticFeedback(
                        context = context,
                        type = InteractionType.CONFIRM,
                        enabled = true
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = voiceLazyListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(
                        items = line.voiceAttachments,
                        key = { voice -> voice.path }
                    ) { voice ->
                        ReorderableItem(
                            state = voiceReorderableState,
                            key = voice.path
                        ) { isDragging ->
                            val elevation by animateFloatAsState(
                                targetValue = if (isDragging) 8f else 0f,
                                label = "voiceDragElevation"
                            )
                            Box(
                                modifier = Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        shadowElevation = elevation
                                        scaleX = if (isDragging) 1.08f else 1f
                                        scaleY = if (isDragging) 1.08f else 1f
                                        alpha = if (isDragging) 0.9f else 1f
                                    }
                            ) {
                                /**
                                 * 获取或创建 VoicePlayer 实例
                                 *
                                 * key = "${lineIndex}_${voiceIndex}"，
                                 * 跨 ReorderableItem 重建时保持实例稳定。
                                 */
                                val voiceKey = "${lineIndex}_${line.voiceAttachments.indexOf(voice)}"
                                val voicePlayer = voicePlayerMap.getOrPut(voiceKey) {
                                    com.corgimemo.app.util.VoicePlayer(context)
                                }

                                VoiceAttachmentItem(
                                    voiceAttachment = voice,
                                    isDragging = isDragging,
                                    voicePlayer = voicePlayer,
                                    onClick = {
                                        /** 点击时先暂停其他正在播放的语音（互斥播放）*/
                                        pauseAllOtherVoices(voiceKey)
                                    },
                                    onDelete = {
                                        /** 删除前释放播放器资源 */
                                        voicePlayer.stop()
                                        voicePlayer.release()
                                        voicePlayerMap.remove(voiceKey)
                                        onDeleteVoice(voice.path)
                                    },
                                    /**
                                     * draggableHandle 绑定到 Box，库自动处理长按触发 + 拖拽
                                     * onDragStarted 时暂停正在播放的语音（替代原 onPauseRequest）
                                     */
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            if (voicePlayer.isPlaying.value) {
                                                voicePlayer.pause()
                                            }
                                            HapticFeedbackManager.performHapticFeedback(
                                                context = context,
                                                type = InteractionType.TEXT_MOVE,
                                                enabled = true
                                            )
                                        },
                                        onDragStopped = {}
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 自动聚焦到目标行
    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }
}

/**
 * 处理键盘事件：回车新建子任务、Backspace 删除行、"/" 创建新组等
 */
private fun handleKeyEvent(
    keyEvent: android.view.KeyEvent,
    index: Int,
    lines: List<TodoLine>,
    line: TodoLine,
    onLinesChange: (List<TodoLine>) -> Unit,
    onFocusChange: (Int) -> Unit,
    focusManager: Any,
    onNewGroupRequested: ((index: Int, currentText: String) -> Unit)?,
    /**
     * 🆕 v2026-07-25 回车新建子待办后的回调
     *
     * 参数：新子待办行的全局索引（lineIndex + 1）
     * 用途：让 UI 层据此触发 externalPendingFocus 焦点转移，
     *      兜底 LaunchedEffect(isFocused) 在新行未完全渲染时 requestFocus() 失败的时序竞态
     */
    onNewSubTaskRequested: ((newSubTaskIndex: Int) -> Unit)?,
    /**
     * 🆕 v2026-07-25 光标置末尾回调
     *
     * 参数：需要把光标设置到文本末尾的目标行索引
     * 触发场景：handleKeyEvent 在 KEYCODE_DEL 分支删除空行后调用，
     * 让 UI 层据此递增目标行的 cursorAtEndTrigger，
     * 由 CheckboxEditRow 内 LaunchedEffect 把光标设置到文本末尾。
     *
     * 用户需求：当用户在子待办行按 Backspace 删除空行后，焦点转移到上一行，
     * 光标必须落在上一行的文本末尾，用户继续按 Backspace 才能从行尾往前删除。
     */
    onCursorAtEndRequested: ((targetLineIndex: Int) -> Unit)?
): Boolean {
    if (keyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    return when (keyEvent.keyCode) {
        android.view.KeyEvent.KEYCODE_ENTER -> {
            // 回车：在当前行下方插入子任务行（同 groupId，带缩进）
            // 🆕 v2026-07-25 架构根治：回车新建子任务行时分配 stableId
            val newLine = TodoLine(
                stableId = TodoLine.generateStableId(),
                isSubTask = true,
                groupId = line.groupId,
                order = index + 1
            )
            val updatedLines = lines.toMutableList()
            val insertIndex = (index + 1).coerceAtMost(updatedLines.size)
            updatedLines.add(insertIndex, newLine)
            reindexOrders(updatedLines)
            onLinesChange(updatedLines)
            onFocusChange(index + 1)
            // 🆕 v2026-07-25 通知 UI 层触发 externalPendingFocus 焦点转移
            // 复用删除分组场景的成熟机制，避免 LaunchedEffect(isFocused) 时序竞态
            onNewSubTaskRequested?.invoke(insertIndex)
            true
        }
        android.view.KeyEvent.KEYCODE_DEL -> {
            // 删除键逻辑：
            // 1. 首行的首行（全局第一个）不可删除
            // 2. 空行可删除
            // 3. 若删除的是某组首行且该组有子行，整组删除
            if (line.text.isNotBlank()) return false

            // 全局第一行不可删
            if (index == 0) return false

            val currentGroup = line.groupId
            val isFirstInGroup = lines.take(index).none { it.groupId == currentGroup }

            val updatedLines = lines.toMutableList()

            if (isFirstInGroup) {
                // 删除整组：移除所有同 groupId 的行
                updatedLines.removeAll { it.groupId == currentGroup }
            } else {
                // 仅删除当前行（通过 groupId+order 匹配，避免索引越界）
                val targetIndex = updatedLines.indexOfFirst { it.groupId == line.groupId && it.order == line.order }
                if (targetIndex >= 0) {
                    updatedLines.removeAt(targetIndex)
                }
            }

            reindexOrders(updatedLines)
            onLinesChange(updatedLines)
            val targetFocusIndex = (index - 1).coerceAtLeast(0)
            onFocusChange(targetFocusIndex)
            // 🆕 v2026-07-25 删除空行后，通知 UI 层把上一行的光标强制设置到文本末尾
            //
            // 用户需求：在子待办行按 Backspace 删除空行后，焦点转移到上一行，
            // 光标必须落在上一行的文本末尾，用户继续按 Backspace 才能从行尾往前删除。
            //
            // 机制：通过 onCursorAtEndRequested 回调递增目标行的 cursorAtEndTrigger，
            // 由 CheckboxEditRow 内 LaunchedEffect(cursorAtEndTrigger) 监听并设置光标位置。
            //
            // 注意：仅当 targetFocusIndex 在 updatedLines 范围内才触发，
            // 避免整组删除后列表为空时调用越界（虽然 onFocusChange 已 coerce，但这里再加一道防线）
            if (targetFocusIndex in updatedLines.indices) {
                onCursorAtEndRequested?.invoke(targetFocusIndex)
            }
            true
        }
        else -> false
    }
}

/**
 * 重新计算列表中每行的 order 值
 */
private fun reindexOrders(lines: MutableList<TodoLine>) {
    for (i in lines.indices) {
        lines[i] = lines[i].copy(order = i)
    }
}

/**
 * 在指定位置更新一行文本内容（通过 groupId+order 身份匹配，避免索引漂移）
 */
private fun updateLineAt(
    line: TodoLine,
    lines: List<TodoLine>,
    newText: String,
    onLinesChange: (List<TodoLine>) -> Unit
) {
    val updatedLines = lines.toMutableList()
    // 用 groupId+order 精确匹配目标行，而非依赖可能过时的索引
    val targetIndex = updatedLines.indexOfFirst { it.groupId == line.groupId && it.order == line.order }
    if (targetIndex >= 0 && targetIndex < updatedLines.size) {
        updatedLines[targetIndex] = updatedLines[targetIndex].copy(text = newText)
        onLinesChange(updatedLines)
    }
}

/**
 * 检测文本中的特殊字符（@ 触发关联选择，# 触发位置提醒）
 * 逻辑与 InspirationEditScreen 保持一致
 *
 * 回调参数：query 为 null 表示关闭弹窗，非 null（含空串）表示打开/更新弹窗
 */
private fun detectSpecialChars(
    text: String,
    callback: ((String, String?) -> Unit)?
) {
    if (callback == null) return

    // @ 触发关联选择弹窗
    val atIndex = text.lastIndexOf('@')
    if (atIndex >= 0) {
        val afterAt = text.substring(atIndex + 1)
        if (!afterAt.contains(' ') && !afterAt.contains('\n')) {
            callback("@", afterAt)
        } else {
            callback("@", null)
        }
    } else {
        callback("@", null)
    }

    // # 触发位置提醒弹窗（全角 # 也支持）
    val hashIndex = maxOf(
        text.lastIndexOf('#'),
        text.lastIndexOf('\uFF03')
    )
    if (hashIndex >= 0) {
        val afterHash = text.substring(hashIndex + 1)
        if (!afterHash.contains(' ') && !afterHash.contains('\n')) {
            callback("#", afterHash)
        } else {
            callback("#", null)
        }
    } else {
        callback("#", null)
    }
}
