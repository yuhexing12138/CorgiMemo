package com.corgimemo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import com.corgimemo.app.ui.model.TodoLine
import com.corgimemo.app.ui.util.formatReminderDisplay
import com.corgimemo.app.util.VoicePlayer

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
 * @param onReminderClick 当前容器提醒按钮点击回调，参数为 groupId
 * @param onFocusedLineChange 当前聚焦行索引变化回调（用于确定附件插入目标）
 * @param priority 当前优先级值（0=低, 1=中, 2=高）
 * @param onPriorityChange 优先级变更回调，参数为 (groupId, 新优先级)
 * @param onSaveClick 当前容器完成/保存按钮点击回调，参数为 groupId
 * @param onImageClick 某一行图片被点击的回调（查看大图）
 * @param onDeleteImage 删除某一行某张图片的回调，参数为 (行索引, 图片路径)
 * @param onDeleteVoice 删除某一行某条语音的回调，参数为 (行索引, 语音路径)
 * @param modifier 容器修饰符
 * @param enabled 是否启用编辑
 * @param placeholder 占位提示文字
 */
@Composable
fun CheckboxEditText(
    lines: List<TodoLine>,
    onLinesChange: (List<TodoLine>) -> Unit,
    onLineCheckToggle: (index: Int, isChecked: Boolean) -> Unit,
    onSpecialCharDetected: ((String, String?) -> Unit)? = null,
    onNewGroupRequested: ((index: Int, currentText: String) -> Unit)? = null,
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
    /** 🆕 拖拽状态：来自 CrossLineDragManager，驱动子组件的视觉反馈 */
    dragState: com.corgimemo.app.ui.components.DragState = com.corgimemo.app.ui.components.DragState(),
    /** 🆕 附件拖拽开始回调（源行索引, 源图片/语音位置索引, 图片高度px[语音传0]）*/
    onAttachmentDragStart: ((Int, Int, Float) -> Unit)? = null,
    /** 🆕 附件拖拽过程中更新回调（当前偏移量, 手指X坐标, 手指Y坐标, 滚动偏移px）
     *  用于同步 CrossLineDragManager 状态和计算目标位置 */
    onAttachmentDragUpdate: ((androidx.compose.ui.geometry.Offset, Float, Float, Float) -> Unit)? = null,
    /** 🆕 附件拖拽结束回调（源行, 源位置, 目标行, 目标位置[null=追加末尾]）*/
    onAttachmentDragEnd: ((Int, Int, Int, Int?) -> Unit)? = null,
    /** 🆕 行边界更新回调（用于精确的目标行检测，参数：行索引, Rect边界矩形）*/
    onRowBoundsChanged: ((Int, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    /** 各分组的保存状态（key=groupId, value=保存状态） */
    groupSaveStates: Map<Int, com.corgimemo.app.viewmodel.GroupSaveState> = emptyMap(),
    /** 各分组的优先级（key=groupId, value=优先级 0=无,1=低,2=中,3=高） */
    groupPriorities: Map<Int, Int> = emptyMap(),
    /** 优先级按钮点击回调（参数=groupId） */
    onPriorityButtonClick: ((Int) -> Unit)? = null,
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
                onSaveClick = { onSaveClick?.invoke(0) }
            ) {
                CheckboxEditRow(
                    lineIndex = 0,
                    line = TodoLine(groupId = 0),
                    isEnabled = enabled,
                    isFocused = true,
                    placeholder = placeholder,
                    isGroupFirst = true,
                    onTextChange = { newText ->
                                // "/" 检测：输入 "/" 时在当前行下方创建新待办容器
                                if (newText.endsWith("/")) {
                                    val textWithoutSlash = newText.removeSuffix("/")
                                    val newLine = TodoLine(text = textWithoutSlash, groupId = 0)
                                    onLinesChange(listOf(newLine))
                                    onNewGroupRequested?.invoke(0, textWithoutSlash)
                                    pendingFocusIndex = 1 // 新行插入在 index 1
                                } else {
                                    val newLine = TodoLine(text = newText, groupId = 0)
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
        } else {
            var globalIndex = 0
            for ((groupId, groupLines) in groups) {
                val groupFirstIndex = globalIndex

                // 计算当前容器是否已保存
                val isGroupSaved = groupSaveStates[groupId]?.isSaved == true
                /** 获取当前分组的优先级（0=无,1=低,2=中,3=高） */
                val groupPriority = groupPriorities[groupId] ?: 0

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
                    onSaveClick = { onSaveClick?.invoke(groupId) }
                ) {
                    groupLines.forEachIndexed { localIndex, line ->
                        val currentIndex = globalIndex++
                        /**
                         * 🆕 行边界捕获：使用 onGloballyPositioned 获取每行的屏幕坐标
                         *
                         * 将每行的 Rect 边界信息通过 onRowBoundsChanged 回调传递给外部，
                         * 外部存储到 rowBoundsMap 中，供 CrossLineDragManager.detectTargetRow() 使用。
                         *
                         * 这实现了精确的目标行检测算法（替代硬编码的估算方式）。
                         */
                        val rowModifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                /**
                                 * 获取该行的位置矩形（用于跨行拖拽的目标行检测）
                                 *
                                 * 使用 localToScreen(Offset.Zero) 获取左上角屏幕坐标，
                                 * 配合 size 构建完整的 Rect。
                                 *
                                 * Compose 1.9.2 兼容性说明：
                                 * - boundsInParent() / boundsInRoot() / boundsInWindow() 均不可用
                                 * - positionInParent() 也不可用
                                 * - localToScreen() 是 LayoutCoordinates 的核心方法，全版本可用
                                 */
                                val screenPos = coordinates.localToScreen(androidx.compose.ui.geometry.Offset.Zero)
                                val sz = coordinates.size
                                val rect = androidx.compose.ui.geometry.Rect(
                                    left = screenPos.x,
                                    top = screenPos.y,
                                    right = screenPos.x + sz.width,
                                    bottom = screenPos.y + sz.height
                                )
                                /** 通知外部更新行边界缓存 */
                                onRowBoundsChanged?.invoke(currentIndex, rect)
                            }

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
                                    val targetIndex = updatedLines.indexOfFirst { it.groupId == line.groupId && it.order == line.order }
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
                                    onFocusChange = { newFocusIdx -> focusedLineIndex = newFocusIdx },
                                    focusManager = focusManager,
                                    onNewGroupRequested = onNewGroupRequested
                                )
                            },
                            onFocusChange = { isFocused ->
                                if (isFocused) {
                                    focusedLineIndex = currentIndex
                                    onFocusedLineChange?.invoke(currentIndex)
                                }
                            },
                            onRegisterFocusRequester = { idx, fr -> focusRequesters[idx] = fr },
                            onImageClick = { imagePath -> onImageClick?.invoke(currentIndex, imagePath) },
                            onDeleteImage = { imagePath -> onDeleteImage?.invoke(currentIndex, imagePath) },
                            onDeleteVoice = { voicePath -> onDeleteVoice?.invoke(currentIndex, voicePath) },
                            /** 🆕 传递拖拽状态给子组件（用于判断 isDragging / isDropTarget）*/
                            dragState = dragState,
                            /** 🆕 附件拖拽回调（v7.5：增加高度参数）*/
                            onAttachmentDragStart = { srcLineIdx, srcImgIdx, heightPx ->
                                onAttachmentDragStart?.invoke(srcLineIdx, srcImgIdx, heightPx)
                            },
                            /** 🆕 附件拖拽过程中更新回调 */
                            onAttachmentDragUpdate = { dragOffset, fingerX, fingerY, scrollOffsetPx ->
                                onAttachmentDragUpdate?.invoke(dragOffset, fingerX, fingerY, scrollOffsetPx)
                            },
                            onAttachmentDragEnd = { srcLineIdx, srcImgIdx, targetLineIdx, targetImgIdx ->
                                onAttachmentDragEnd?.invoke(srcLineIdx, srcImgIdx, targetLineIdx, targetImgIdx)
                            },
                            /** 🆕 应用行边界捕获修饰符 */
                            modifier = rowModifier,
                            /** 🆕 传入语音播放器相关参数 */
                            voicePlayerMap = voicePlayerMap,
                            context = context,
                            pauseAllOtherVoices = ::pauseAllOtherVoices
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个待办组的圆角容器（含底部操作栏）
 *
 * @param priority 优先级 (0=无, 1=低, 2=中, 3=高)
 * @param onPriorityClick 优先级按钮点击回调（触发弹窗）
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
    content: @Composable () -> Unit
) {
    /**
     * 根据优先级计算边框颜色
     * - 3=高 → 红色
     * - 2=中 → 黄色
     * - 1=低 → 绿色
     * - 0=无 → 无边框
     */
    val borderColor = when (priority) {
        3 -> PriorityColors.colorOf(3)  // 高优先级
        2 -> PriorityColors.colorOf(2)  // 中优先级
        1 -> PriorityColors.colorOf(1)  // 低优先级
        else -> null                     // 无优先级：无边框
    }

    /** 优先级按钮显示文字 */
    val priorityLabel = when (priority) {
        3 -> "高优先级"
        2 -> "中优先级"
        1 -> "低优先级"
        else -> "优先级"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                // 优先级边框颜色
                if (borderColor != null) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = borderColor.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()

        // 底部操作栏：两行布局
        // Row1：[优先级按钮] [8dp间距] [分类按钮]
        // Row2：[提醒按钮] ... [完成按钮]
        if (showBottomBar) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ===== Row1: 优先级 + 分类 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 优先级按钮（保持原有视觉与交互）
                    Box(
                        modifier = Modifier
                            .clickable(enabled = onPriorityClick != null) { onPriorityClick?.invoke() }
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 优先级颜色圆点
                            if (borderColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(borderColor)
                                )
                            }
                            Text(
                                text = priorityLabel,
                                fontSize = 13.sp,
                                color = borderColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (priority > 0) androidx.compose.ui.text.font.FontWeight.SemiBold
                                             else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 分类按钮
                    if (onCategoryClick != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF5F5F5))
                                .widthIn(max = 120.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 分类点击区域：图标 + 名称
                            Row(
                                modifier = Modifier
                                    .clickable(enabled = onCategoryClick != null) { onCategoryClick?.invoke() }
                                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = if (categoryName != null && onCategoryClear != null) 4.dp else 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📋",
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = categoryName ?: "分类",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (categoryName != null) androidx.compose.ui.text.font.FontWeight.SemiBold
                                                 else androidx.compose.ui.text.font.FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            // × 按钮：仅当已设置分类时显示，复刻"设置提醒"×按钮交互
                            if (categoryName != null && onCategoryClear != null) {
                                // 1px 垂直分割线
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(14.dp)
                                        .background(Color(0xFFCCCCCC))
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .clickable(enabled = onCategoryClear != null) { onCategoryClear?.invoke() }
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = "×",
                                        fontSize = 16.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== Row2: 提醒 + 完成 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 实时刷新当前时间：进入页面时取一次，对齐到下一个 30s 整数倍开始轮询，
                    // 最迟 30s 内必然跨分钟，"已过期/未过期"自动切换。
                    // reminderTime 变化时（用户改时间 / × 删除）LaunchedEffect 重启，立刻按新值重算。
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(reminderTime) {
                        now = System.currentTimeMillis()
                        while (true) {
                            // 对齐到下一个 30s 整数倍边界，最大漂移 30s
                            val nextTick = ((System.currentTimeMillis() / 30_000L) + 1L) * 30_000L
                            kotlinx.coroutines.delay(nextTick - System.currentTimeMillis())
                            now = System.currentTimeMillis()
                        }
                    }

                    // 已过期判定：reminderTime 严格小于 now（等于当前时刻视为未过期）
                    val isOverdue = reminderTime != null && reminderTime < now
                    val displayText = reminderTime?.let { formatReminderDisplay(it, now).text } ?: "设置提醒"
                    val iconTint: Color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                    val textColor: Color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .clickable(enabled = onReminderClick != null) { onReminderClick?.invoke() }
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(
                                start = 10.dp,
                                top = 6.dp,
                                end = if (reminderTime != null) 6.dp else 10.dp,
                                bottom = 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = if (reminderTime != null) "已设置提醒" else "设置提醒",
                            tint = iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = displayText,
                            fontSize = 13.sp,
                            color = textColor,
                            fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (reminderTime != null) {
                            Spacer(Modifier.width(6.dp))
                            // 1px 垂直分割线
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(14.dp)
                                    .background(Color(0xFFCCCCCC))
                            )
                            Spacer(Modifier.width(6.dp))
                            // × 按钮：独立点击区域，不冒泡到 onReminderClick
                            Box(
                                modifier = Modifier
                                    .clickable(enabled = onReminderDelete != null) { onReminderDelete?.invoke() }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = "×",
                                    fontSize = 16.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 右侧：完成 / 已保存 按钮
                    Text(
                        text = if (isSaved) "已保存 ✓" else "完成",
                        modifier = Modifier
                            .clickable(enabled = onSaveClick != null && !isSaved) { onSaveClick?.invoke() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        color = if (isSaved) androidx.compose.ui.graphics.Color(0xFF4CAF50) else Color(0xFFFF9A5C),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize = 14.sp
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
    /** 🆕 拖拽状态（来自 CrossLineDragManager）*/
    dragState: com.corgimemo.app.ui.components.DragState = com.corgimemo.app.ui.components.DragState(),
    /** 🆕 附件拖拽开始回调（v7.5：增加图片高度参数）*/
    onAttachmentDragStart: ((Int, Int, Float) -> Unit)? = null,
    /** 🆕 附件拖拽过程中更新回调（同步 CrossLineDragManager 状态）*/
    onAttachmentDragUpdate: ((androidx.compose.ui.geometry.Offset, Float, Float, Float) -> Unit)? = null,
    /** 🆕 附件拖拽结束回调 */
    onAttachmentDragEnd: ((Int, Int, Int, Int?) -> Unit)? = null,
    /** 🆕 行修饰符（用于 onGloballyPositioned 行边界捕获）*/
    modifier: Modifier = Modifier,
    /** 🆕 语音播放器实例管理 Map（由外层 CheckboxEditText 传入）*/
    voicePlayerMap: kotlin.collections.MutableMap<String, com.corgimemo.app.util.VoicePlayer>,
    /** 🆕 Android Context（用于创建 VoicePlayer 实例）*/
    context: android.content.Context,
    /** 🆕 多语音互斥播放控制函数*/
    pauseAllOtherVoices: (String) -> Unit
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
     * 🆕 行容器：应用外部传入的修饰符（用于 onGloballyPositioned 行边界捕获）
     *
     * 外部通过此修饰符获取每行的屏幕坐标，
     * 存储到 rowBoundsMap 中供 CrossLineDragManager 使用。
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
            value = line.text,
            onValueChange = { newValue ->
                onTextChange(newValue)
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
            /** 图片附件列表（支持拖拽排序） */
            if (line.imagePaths.isNotEmpty()) {
                /**
                 * 🆕 v7.4：图片行滚动状态（在 Composable 作用域内创建）
                 *
                 * rememberScrollState 必须在 @Composable 作用域中调用。
                 * 其 value 将在 onGloballyPositioned 中读取并传给回调。
                 */
                val imageScrollState = rememberScrollState()

                /**
                 * 🆕 边缘自动滚动速度和方向
                 * 正值=向右滚动，负值=向左滚动，0=不滚动
                 */
                var edgeScrollSpeed by remember { mutableStateOf(0f) }

                /**
                 * 🆕 边缘自动滚动驱动
                 *
                 * 当 edgeScrollSpeed != 0 时，使用 LaunchedEffect + animate 循环
                 * 每帧调用 imageScrollState.dispatchRawDelta() 实现平滑自动滚动。
                 */
                if (edgeScrollSpeed != 0f) {
                    LaunchedEffect(edgeScrollSpeed) {
                        while (edgeScrollSpeed != 0f) {
                            /** 每帧滚动 = 速度 * 帧间隔（约16ms） */
                            val delta = edgeScrollSpeed * 16f / 1000f
                            imageScrollState.dispatchRawDelta(delta)
                            /** 等待下一帧 */
                            kotlinx.coroutines.delay(16)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(imageScrollState)
                        /**
                         * 🆕 多指手势处理：第二指滚动 + 边缘自动滚动
                         *
                         * 仅在当前行是拖拽源行时激活。
                         * 第一指：拖拽图片（由 DraggableImageAttachment 的 detectDragGesturesAfterLongPress 处理）
                         * 第二指：水平滑动 → 驱动 imageScrollState 滚动
                         * 单指边缘：靠近左/右边缘 → 自动滚动
                         */
                        .pointerInput(dragState.isDragging, dragState.sourceLineIndex, lineIndex) {
                            if (!dragState.isDragging || dragState.sourceLineIndex != lineIndex) {
                                return@pointerInput
                            }

                            val density = density

                            awaitPointerEventScope {
                                var secondPointerId: Long = -1L
                                var secondPointerLastX = 0f

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes

                                    when (changes.size) {
                                        /** 多指（≥2根手指）：第二指驱动水平滚动 */
                                        0 -> { /* 无指针事件 */ }
                                        in 1..1 -> {
                                            /** 单指：检测边缘自动滚动 */
                                            secondPointerId = -1L
                                            val change = changes.first()
                                            val fingerX = change.position.x
                                            val rowWidth = size.width.toFloat()
                                            val edgeThreshold = 40f * density

                                            edgeScrollSpeed = when {
                                                fingerX < edgeThreshold -> {
                                                    /** 左边缘：向左滚动，越近越快 */
                                                    val ratio = 1f - (fingerX / edgeThreshold)
                                                    -150f * density * ratio
                                                }
                                                fingerX > rowWidth - edgeThreshold -> {
                                                    /** 右边缘：向右滚动，越近越快 */
                                                    val ratio = 1f - ((rowWidth - fingerX) / edgeThreshold)
                                                    150f * density * ratio
                                                }
                                                else -> 0f
                                            }
                                        }
                                        else -> {
                                            /** 多指：找到第二根手指并追踪其水平滑动 */
                                            edgeScrollSpeed = 0f

                                            for (change in changes) {
                                                val pointerId = change.id.value
                                                if (pointerId != secondPointerId) {
                                                    /** 新手指按下（或切换手指），记录初始位置 */
                                                    secondPointerId = pointerId
                                                    secondPointerLastX = change.position.x
                                                }
                                            }

                                            /** 找到第二指并计算滑动增量 */
                                            val secondFinger = changes.find {
                                                it.id.value == secondPointerId && it.pressed
                                            }
                                            if (secondFinger != null) {
                                                val deltaX = secondFinger.position.x - secondPointerLastX
                                                if (kotlin.math.abs(deltaX) > 0.5f) {
                                                    /** 反向滚动：手指向右滑 → 内容向左移动 */
                                                    imageScrollState.dispatchRawDelta(-deltaX)
                                                    secondPointerLastX = secondFinger.position.x
                                                }
                                                secondFinger.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    line.imagePaths.forEachIndexed { imageIndex, imagePath ->
                        /**
                         * 判断当前图片是否正在被拖拽
                         *
                         * 通过 dragState 判断：
                         * - isDragging == true（全局拖拽状态激活）
                         * - sourceLineIndex == lineIndex（源行匹配）
                         * - sourceImageIndex == imageIndex（源图片位置匹配）
                         */
                        val isThisImageDragging = dragState.isDragging &&
                                dragState.sourceLineIndex == lineIndex &&
                                dragState.sourceImageIndex == imageIndex

                        /**
                         * 判断当前图片是否为拖拽目标（同行交换模式）
                         *
                         * 行内排序模式（INLINE_SORT）：
                         * - 当前图片索引与目标图片索引匹配
                         * - 且不是源图片本身（避免自己高亮自己）
                         * - 且放置类型为 SWAP（交换模式才显示虚线框）
                         */
                        val isDropTargetInline = dragState.dragMode == com.corgimemo.app.ui.components.DragMode.INLINE_SORT &&
                                dragState.sourceLineIndex == lineIndex &&
                                dragState.currentTargetImage == imageIndex &&
                                dragState.sourceImageIndex != imageIndex &&
                                dragState.inlineDropType == com.corgimemo.app.ui.components.InlineDropType.SWAP

                        /**
                         * 🆕 同行移动光标显示判定
                         *
                         * INSERT_BEFORE：在当前图片之前显示光标
                         *   → inlineDropType == INSERT_BEFORE 且 currentTargetImage == 当前索引
                         *
                         * INSERT_AFTER：在最后一张图片之后显示光标
                         *   → inlineDropType == INSERT_AFTER 且当前是最后一张图
                         */
                        val showCursorBefore = dragState.dragMode == com.corgimemo.app.ui.components.DragMode.INLINE_SORT &&
                                dragState.sourceLineIndex == lineIndex &&
                                dragState.inlineDropType == com.corgimemo.app.ui.components.InlineDropType.INSERT_BEFORE &&
                                dragState.currentTargetImage == imageIndex

                        val showCursorAfter = dragState.dragMode == com.corgimemo.app.ui.components.DragMode.INLINE_SORT &&
                                dragState.sourceLineIndex == lineIndex &&
                                dragState.inlineDropType == com.corgimemo.app.ui.components.InlineDropType.INSERT_AFTER &&
                                imageIndex == line.imagePaths.lastIndex

                        /**
                         * 使用可拖拽的图片附件组件
                         */
                        DraggableImageAttachment(
                            imagePath = imagePath,
                            lineIndex = lineIndex,
                            imageIndex = imageIndex,
                            isDragging = isThisImageDragging,
                            isDropTarget = isDropTargetInline,
                            /** 🆕 同行移动光标参数 */
                            showCursorBefore = showCursorBefore,
                            showCursorAfter = showCursorAfter,
                            onDragStart = { sourceLineIdx, sourceImgIdx, imageHeightPx ->
                                onAttachmentDragStart?.invoke(sourceLineIdx, sourceImgIdx, imageHeightPx)
                            },
                            onDragUpdate = { dragOffset, fingerX, fingerY ->
                                /** 🆕 透传滚动偏移量，用于 CrossLineDragManager 的 X 轴补偿 */
                                onAttachmentDragUpdate?.invoke(dragOffset, fingerX, fingerY, imageScrollState.value.toFloat())
                            },
                            onDragEnd = { targetLineIdx, targetImgIdx ->
                                onAttachmentDragEnd?.invoke(lineIndex, imageIndex, targetLineIdx, targetImgIdx)
                            },
                            onClick = { imgPath -> onImageClick(imgPath) },
                            onDelete = { imgPath -> onDeleteImage(imgPath) }
                        )
                    }
                }
            }

            /** 语音附件列表（支持拖拽排序）*/
            line.voiceAttachments.forEachIndexed { voiceIndex, voice ->
                /**
                 * 判断当前语音是否正在被拖拽
                 *
                 * 与图片拖拽判断逻辑一致：
                 * - isDragging == true（全局拖拽状态激活）
                 * - sourceLineIndex == lineIndex（源行匹配）
                 * - sourceImageIndex == voiceIndex（源语音位置，复用 imageIndex 字段）
                 */
                val isThisVoiceDragging = dragState.isDragging &&
                        dragState.sourceLineIndex == lineIndex &&
                        dragState.sourceImageIndex == voiceIndex

                /**
                 * 判断当前行是否为跨行语音拖拽的目标位置
                 */
                val isVoiceDropTarget = false  // 语音附件暂不支持拖拽目标判定

                /**
                 * 使用可拖拽的语音附件组件
                 *
                 * 🆕 传入 VoicePlayer 实例，启用完整播放器 UI：
                 * - 波形图替代进度条
                 * - 点击整行播放/暂停
                 * - 递增时间显示
                 */

                /**
                 * 🆕 获取或创建当前语音附件的 VoicePlayer 实例
                 *
                 * 使用 "行索引_语音索引" 作为唯一 key，
                 * 确保每个语音附件有独立的播放器实例。
                 */
                val voiceKey = "${lineIndex}_${voiceIndex}"
                val voicePlayer = voicePlayerMap.getOrPut(voiceKey) {
                    com.corgimemo.app.util.VoicePlayer(context)
                }

                DraggableVoiceAttachment(
                    voiceAttachment = voice,
                    lineIndex = lineIndex,
                    voiceIndex = voiceIndex,
                    isDragging = isThisVoiceDragging,
                    isDropTarget = isVoiceDropTarget,
                    /** 🆕 传入 VoicePlayer 实例，显示完整播放器 UI */
                    voicePlayer = voicePlayer,
                    onDragStart = { sourceLineIdx, sourceVoiceIdx ->
                        /** 语音附件无高度概念，传 0f */
                        onAttachmentDragStart?.invoke(sourceLineIdx, sourceVoiceIdx, 0f)
                    },
                    onDragUpdate = { dragOffset, fingerX, fingerY, _ ->
                        /** 语音附件无水平滚动，忽略 scrollOffsetPx 参数 */
                        onAttachmentDragUpdate?.invoke(dragOffset, fingerX, fingerY, 0f)
                    },
                    onDragEnd = { targetLineIdx, targetVoiceIdx ->
                        onAttachmentDragEnd?.invoke(lineIndex, voiceIndex, targetLineIdx, targetVoiceIdx)
                    },
                    onPauseRequest = {
                        /** 🆕 暂停播放（拖拽开始时自动调用）*/
                        if (voicePlayer.isPlaying.value) {
                            voicePlayer.pause()
                        }
                    },
                    onResumeRequest = {
                        /** 🆕 恢复播放（拖拽结束时自动调用）*/
                        // 拖拽结束后不自动恢复，由用户手动点击播放
                    },
                    onClick = {
                        /** 🆕 点击时先暂停其他正在播放的语音（互斥播放）*/
                        pauseAllOtherVoices(voiceKey)
                        /** 播放/暂停切换由 VoicePlayerComponent 内部处理 */
                    },
                    onDelete = {
                        /** 🆕 删除前释放播放器资源 */
                        voicePlayer.stop()
                        voicePlayer.release()
                        voicePlayerMap.remove(voiceKey)
                        onDeleteVoice(voice.path)
                    }
                )
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
    onNewGroupRequested: ((index: Int, currentText: String) -> Unit)?
): Boolean {
    if (keyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    return when (keyEvent.keyCode) {
        android.view.KeyEvent.KEYCODE_ENTER -> {
            // 回车：在当前行下方插入子任务行（同 groupId，带缩进）
            val newLine = TodoLine(
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
            onFocusChange((index - 1).coerceAtLeast(0))
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
