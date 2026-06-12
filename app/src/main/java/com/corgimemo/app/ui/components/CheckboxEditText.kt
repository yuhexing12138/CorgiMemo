package com.corgimemo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.corgimemo.app.ui.model.TodoLine

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
    onFocusedLineChange: ((Int) -> Unit)? = null,
    priority: Int = 1,
    onPriorityChange: ((Int, Int) -> Unit)? = null,
    onSaveClick: ((Int) -> Unit)? = null,
    onImageClick: ((Int, String) -> Unit)? = null,
    onDeleteImage: ((Int, String) -> Unit)? = null,
    onDeleteVoice: ((Int, String) -> Unit)? = null,
    /** 🆕 拖拽状态：来自 CrossLineDragManager，驱动子组件的视觉反馈 */
    dragState: com.corgimemo.app.ui.components.DragState = com.corgimemo.app.ui.components.DragState(),
    /** 🆕 附件拖拽开始回调（源行索引, 源图片/语音位置索引）*/
    onAttachmentDragStart: ((Int, Int) -> Unit)? = null,
    /** 🆕 附件拖拽过程中更新回调（当前偏移量, 手指Y坐标）
     *  用于同步 CrossLineDragManager 状态和计算目标行 */
    onAttachmentDragUpdate: ((androidx.compose.ui.geometry.Offset, Float) -> Unit)? = null,
    /** 🆕 附件拖拽结束回调（源行, 源位置, 目标行, 目标位置[null=追加末尾]）*/
    onAttachmentDragEnd: ((Int, Int, Int, Int?) -> Unit)? = null,
    /** 🆕 行边界更新回调（用于精确的目标行检测，参数：行索引, Rect边界矩形）*/
    onRowBoundsChanged: ((Int, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
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
                priority = priority,
                onPriorityChange = { onPriorityChange?.invoke(0, it) },
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
                    onDeleteVoice = { }
                )
            }
        } else {
            var globalIndex = 0
            for ((groupId, groupLines) in groups) {
                val groupFirstIndex = globalIndex

                TodoGroupContainer(
                    groupId = groupId,
                    showBottomBar = true,
                    onReminderClick = { onReminderClick?.invoke(groupId) },
                    priority = priority,
                    onPriorityChange = { onPriorityChange?.invoke(groupId, it) },
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
                            /** 🆕 附件拖拽回调 */
                            onAttachmentDragStart = { srcLineIdx, srcImgIdx ->
                                onAttachmentDragStart?.invoke(srcLineIdx, srcImgIdx)
                            },
                            /** 🆕 附件拖拽过程中更新回调 */
                            onAttachmentDragUpdate = { dragOffset, fingerY ->
                                onAttachmentDragUpdate?.invoke(dragOffset, fingerY)
                            },
                            onAttachmentDragEnd = { srcLineIdx, srcImgIdx, targetLineIdx, targetImgIdx ->
                                onAttachmentDragEnd?.invoke(srcLineIdx, srcImgIdx, targetLineIdx, targetImgIdx)
                            },
                            /** 🆕 应用行边界捕获修饰符 */
                            modifier = rowModifier
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个待办组的圆角容器（含底部操作栏）
 */
@Composable
private fun TodoGroupContainer(
    groupId: Int,
    showBottomBar: Boolean,
    onReminderClick: (() -> Unit)? = null,
    priority: Int = 1,
    onPriorityChange: ((Int) -> Unit)? = null,
    onSaveClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()

        // 底部操作栏：[提醒按钮] [优先级] ... [完成]
        if (showBottomBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：提醒按钮
                Box(
                    modifier = Modifier
                        .clickable(enabled = onReminderClick != null) { onReminderClick?.invoke() }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "设置提醒",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "设置提醒",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 中间：优先级选择
                listOf(Pair(0, "低"), Pair(1, "中"), Pair(2, "高")).forEach { (value, label) ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .clickable(enabled = onPriorityChange != null) { onPriorityChange?.invoke(value) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        color = if (priority == value) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (priority == value) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        fontSize = 12.sp
                    )
                    if (value < 2) Spacer(modifier = Modifier.width(2.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                // 右侧：完成按钮
                Text(
                    text = "完成",
                    modifier = Modifier
                        .clickable(enabled = onSaveClick != null) { onSaveClick?.invoke() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color(0xFFFF9A5C),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 14.sp
                )
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
    /** 🆕 附件拖拽开始回调 */
    onAttachmentDragStart: ((Int, Int) -> Unit)? = null,
    /** 🆕 附件拖拽过程中更新回调（同步 CrossLineDragManager 状态）*/
    onAttachmentDragUpdate: ((androidx.compose.ui.geometry.Offset, Float) -> Unit)? = null,
    /** 🆕 附件拖拽结束回调 */
    onAttachmentDragEnd: ((Int, Int, Int, Int?) -> Unit)? = null,
    /** 🆕 行修饰符（用于 onGloballyPositioned 行边界捕获）*/
    modifier: Modifier = Modifier
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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
                         * 判断当前行是否为跨行拖拽的目标位置
                         *
                         * 条件：
                         * - dragMode == CROSS_LINE（处于跨行移动模式）
                         * - currentTargetLine == lineIndex（当前悬停目标为此行）
                         * - 且不是源行本身（避免自己高亮自己）
                         */
                        val isDropTargetForCrossLine = dragState.dragMode == com.corgimemo.app.ui.components.DragMode.CROSS_LINE &&
                                dragState.currentTargetLine == lineIndex &&
                                dragState.sourceLineIndex != lineIndex

                        /**
                         * 使用可拖拽的图片附件组件
                         *
                         * 替代原有的静态 Box + InlineImagePreview 组合，
                         * 新增长按拖拽、浮层效果、目标高亮等交互能力。
                         */
                        DraggableImageAttachment(
                            imagePath = imagePath,
                            lineIndex = lineIndex,
                            imageIndex = imageIndex,
                            isDragging = isThisImageDragging,
                            isDropTarget = isDropTargetForCrossLine,
                            onDragStart = { sourceLineIdx, sourceImgIdx ->
                                /** 通知外部开始拖拽：更新 CrossLineDragManager 状态 */
                                onAttachmentDragStart?.invoke(sourceLineIdx, sourceImgIdx)
                            },
                            /** 🆕 关键：传递拖拽更新回调，同步 CrossLineDragManager 状态 */
                            onDragUpdate = { dragOffset, fingerY ->
                                onAttachmentDragUpdate?.invoke(dragOffset, fingerY)
                            },
                            onDragEnd = { targetLineIdx, targetImgIdx ->
                                /** 通知外部结束拖拽：执行实际的数据移动操作 */
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
                val isVoiceDropTarget = dragState.dragMode == com.corgimemo.app.ui.components.DragMode.CROSS_LINE &&
                        dragState.currentTargetLine == lineIndex &&
                        dragState.sourceLineIndex != lineIndex

                /**
                 * 使用可拖拽的语音附件组件
                 *
                 * 替代原有的静态 VoicePlayerComponent，
                 * 新增长按拖拽、浮层效果、自动暂停播放等交互能力。
                 */
                DraggableVoiceAttachment(
                    voiceAttachment = voice,
                    lineIndex = lineIndex,
                    voiceIndex = voiceIndex,
                    isDragging = isThisVoiceDragging,
                    isDropTarget = isVoiceDropTarget,
                    isPlaying = false, // TODO: 从外部获取实际播放状态
                    onDragStart = { sourceLineIdx, sourceVoiceIdx ->
                        /** 通知外部开始拖拽：更新 CrossLineDragManager 状态 */
                        onAttachmentDragStart?.invoke(sourceLineIdx, sourceVoiceIdx)
                    },
                    /** 🆕 关键：传递拖拽更新回调，同步 CrossLineDragManager 状态 */
                    onDragUpdate = { dragOffset, fingerY ->
                        onAttachmentDragUpdate?.invoke(dragOffset, fingerY)
                    },
                    onDragEnd = { targetLineIdx, targetVoiceIdx ->
                        /** 通知外部结束拖拽：执行实际的数据移动操作 */
                        onAttachmentDragEnd?.invoke(lineIndex, voiceIndex, targetLineIdx, targetVoiceIdx)
                    },
                    onPauseRequest = {
                        // TODO: 暂停该语音的播放器实例
                    },
                    onResumeRequest = {
                        // TODO: 恢复该语音的播放器实例
                    },
                    onClick = {
                        // TODO: 切换播放/暂停状态
                    },
                    onDelete = { onDeleteVoice(voice.path) }
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
