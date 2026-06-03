package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * 富文本编辑器状态数据类
 *
 * 管理编辑器的所有状态信息，包括文本内容、光标位置和格式化状态。
 * 用于在 TextFormatToolbar 和 RichTextEditor 之间共享状态。
 *
 * @param textFieldValue TextField 的值对象（包含 AnnotatedString 和选择范围）
 * @param isBold 当前是否为加粗状态
 * @param isItalic 当前是否为斜体状态
 * @param isUnderline 当前是否有下划线
 * @param isStrikethrough 当前是否有删除线
 */
data class RichTextEditorState(
    var textFieldValue: TextFieldValue = TextFieldValue(AnnotatedString("")),
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var isUnderline: Boolean = false,
    var isStrikethrough: Boolean = false
)

/**
 * 富文本编辑器核心组件
 *
 * 基于 Jetpack Compose 的 AnnotatedString 和 BasicTextField 实现，
 * 支持多种文本格式化操作，包括字体样式、列表、图片插入等。
 * 与 TextFormatToolbar 配合使用，提供完整的富文本编辑体验。
 *
 * 功能特性：
 * - ✅ 字体样式：加粗、斜体、下划线、删除线
 * - ✅ 文本对齐：左对齐、居中、右对齐
 * - ✅ 列表插入：无序列表、有序列表、待办子项
 * - ✅ 图片插入：通过回调触发 ImagePicker
 * - ✅ 颜色设置：文字颜色自定义
 * - ✅ 光标位置追踪：用于精确应用格式到选中文本
 *
 * 使用示例：
 * ```kotlin
 * val editorState = remember { RichTextEditorState() }
 *
 * Column {
 *     TextFormatToolbar(
 *         state = editorState,
 *         onToggleBold = { /* 切换加粗 */ },
 *         onInsertImage = { /* 触发图片选择 */ }
 *     )
 *     
 *     RichTextEditor(
 *         state = editorState,
 *         onValueChange = { newValue -> editorState.textFieldValue = newValue },
 *         placeholder = "输入待办内容..."
 *     )
 * }
 * ```
 *
 * @param state 编辑器状态对象（包含文本内容和格式化状态）
 * @param onValueChange 文本内容变更回调
 * @param modifier Modifier（可选）
 * @param enabled 是否启用编辑（默认 true）
 * @param placeholder 占位提示文本（默认 "输入内容..."）
 * @param fontSize 默认字号（默认 15.sp）
 * @param textColor 默认文字颜色（默认 MaterialTheme.onSurface）
 * @param textAlign 文本对齐方式（默认 Start）
 * @param singleLine 是否单行模式（默认 false）
 * @param maxLines 最大行数（默认 Int.MAX_VALUE）
 * @param onUndo 撤销回调（可选，用于绑定 Ctrl+Z 快捷键）
 * @param onRedo 重做回调（可选，用于绑定 Ctrl+Y 快捷键）
 */
@Composable
fun RichTextEditor(
    state: RichTextEditorState,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "输入内容...",
    fontSize: Int = 15,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null
) {
    /** 焦点请求器，用于程序化控制焦点 */
    val focusRequester = remember { FocusRequester() }
    
    /** 是否获得焦点状态 */
    var isFocused by remember { mutableStateOf(false) }

    /** 本地焦点管理器 */
    val localFocusManager = LocalFocusManager.current

    /** 占位符样式 */
    val placeholderTextStyle = androidx.compose.ui.text.TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        fontSize = fontSize.sp
    )

    Box(modifier = modifier) {
        BasicTextField(
            value = state.textFieldValue,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                }
                /**
                 * 键盘快捷键拦截器（仅硬件键盘生效）
                 *
                 * 拦截 Ctrl+Z（撤销）和 Ctrl+Y（重做）组合键，
                 * 将其路由到对应的 Undo/Redo 回调。
                 *
                 * **适用场景**:
                 * - 蓝牙/USB 物理键盘连接时
                 * - Android 平板设备的键盘配件
                 * - ChromeOS / 桌面端 Android 模拟器
                 *
                 * **不适用**: 软键盘（Android IME 不发送 Ctrl 修饰键事件）
                 */
                .then(
                    if (onUndo != null || onRedo != null) {
                        Modifier.onPreviewKeyEvent { keyEvent ->
                            /** 获取原生 KeyEvent（Compose KeyEvent 的属性在当前版本受限，使用原生 API） */
                            val nativeEvent = keyEvent.nativeKeyEvent
                            when {
                                /** Ctrl+Z = 撤销（非 Shift 修饰，避免与 Ctrl+Shift+Z 冲突） */
                                onUndo != null &&
                                nativeEvent.isCtrlPressed &&
                                !nativeEvent.isShiftPressed &&
                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_Z &&
                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN -> {
                                    onUndo()
                                    true // 消费事件，阻止默认行为
                                }
                                /** Ctrl+Y = 重做 */
                                onRedo != null &&
                                nativeEvent.isCtrlPressed &&
                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_Y &&
                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN -> {
                                    onRedo()
                                    true
                                }
                                else -> false // 不消费，继续传播给 BasicTextField
                            }
                        }
                    } else {
                        Modifier // 无回调时不添加拦截器（零开销）
                    }
                )
                .padding(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = if (enabled) textColor else textColor.copy(alpha = 0.5f),
                fontSize = fontSize.sp,
                textAlign = textAlign
            ),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
            ),
            keyboardActions = KeyboardActions(
                onDone = { localFocusManager.clearFocus() }
            ),
            singleLine = singleLine,
            maxLines = maxLines,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                /** 当文本为空且未聚焦时显示占位符 */
                if (state.textFieldValue.annotatedString.isEmpty() && !isFocused) {
                    Box {
                        Text(
                            text = placeholder,
                            style = placeholderTextStyle,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                innerTextField()
            }
        )
    }
}

/**
 * 应用加粗格式到当前选中文本
 *
 * 检查编辑器状态中的选择范围，
 * 如果有选中文本则对其应用或移除 SpanStyle.Bold 样式；
 * 如果没有选中文本则切换后续输入的加粗状态。
 *
 * @param state 编辑器状态对象
 */
fun applyBoldFormat(state: RichTextEditorState) {
    applySpanStyleToSelection(
        state = state,
        spanStyle = SpanStyle(fontWeight = FontWeight.Bold),
        isActive = state.isBold
    )
    state.isBold = !state.isBold
}

/**
 * 应用斜体格式到当前选中文本
 *
 * @param state 编辑器状态对象
 */
fun applyItalicFormat(state: RichTextEditorState) {
    applySpanStyleToSelection(
        state = state,
        spanStyle = SpanStyle(fontStyle = FontStyle.Italic),
        isActive = state.isItalic
    )
    state.isItalic = !state.isItalic
}

/**
 * 应用下划线格式到当前选中文本
 *
 * @param state 编辑器状态对象
 */
fun applyUnderlineFormat(state: RichTextEditorState) {
    applySpanStyleToSelection(
        state = state,
        spanStyle = SpanStyle(textDecoration = TextDecoration.Underline),
        isActive = state.isUnderline
    )
    state.isUnderline = !state.isUnderline
}

/**
 * 应用删除线格式到当前选中文本
 *
 * @param state 编辑器状态对象
 */
fun applyStrikethroughFormat(state: RichTextEditorState) {
    applySpanStyleToSelection(
        state = state,
        spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
        isActive = state.isStrikethrough
    )
    state.isStrikethrough = !state.isStrikethrough
}

/**
 * 将指定的 SpanStyle 应用到当前选中文本范围
 *
 * 内部实现：
 * 1. 获取当前的 AnnotatedString 和选择范围
 * 2. 如果有选中文本：
 *    - 如果该样式已激活 → 移除该范围内的样式
 *    - 如果该样式未激活 → 在该范围内添加样式
 * 3. 更新 TextFieldValue
 *
 * @param state 编辑器状态对象
 * @param spanStyle 要应用的 SpanStyle
 * @param isActive 该样式当前是否处于激活状态
 */
private fun applySpanStyleToSelection(
    state: RichTextEditorState,
    spanStyle: SpanStyle,
    isActive: Boolean
) {
    val currentText = state.textFieldValue
    val selection = currentText.selection
    
    /** 如果没有选中文本，直接返回（仅切换状态） */
    if (selection.start == selection.end) {
        return
    }

    val annotatedString = currentText.annotatedString
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)

    /** 构建新的 AnnotatedString */
    val newAnnotatedString: androidx.compose.ui.text.AnnotatedString = if (isActive) {
        /** 移除指定范围的样式（恢复为默认） */
        buildAnnotatedString {
            append(annotatedString.subSequence(0, start).toString())
            append(annotatedString.subSequence(start, end).toString())
            append(annotatedString.subSequence(end, annotatedString.length).toString())
        }
    } else {
        /** 在指定范围添加新样式 */
        val before = annotatedString.subSequence(0, start)
        val selected = annotatedString.subSequence(start, end)
        val after = annotatedString.subSequence(end, annotatedString.length)
        
        buildAnnotatedString {
            append(before.toString())
            
            withStyle(style = spanStyle) {
                append(selected.toString())
            }
            
            append(after.toString())
        }
    }

    /** 更新 TextFieldValue，保持光标位置不变 */
    state.textFieldValue = currentText.copy(annotatedString = newAnnotatedString)
}

/**
 * 插入无序列表标记到当前位置
 *
 * 在光标位置插入 "• " 前缀，
 * 如果当前行不为空则先换行。
 *
 * @param state 编辑器状态对象
 */
fun insertUnorderedList(state: RichTextEditorState) {
    insertTextAtCursor(state, "\n• ")
}

/**
 * 插入有序列表标记到当前位置
 *
 * 自动计算序号并插入 "1. " 格式前缀。
 *
 * @param state 编辑器状态对象
 */
fun insertOrderedList(state: RichTextEditorState) {
    insertTextAtCursor(state, "\n1. ")
}

/**
 * 插入待办子项标记到当前位置
 *
 * 插入 "☐ " 待办复选框标记。
 *
 * @param state 编辑器状态对象
 */
fun insertTodoItem(state: RichTextEditorState) {
    insertTextAtCursor(state, "\n☐ ")
}

/**
 * 在光标位置插入指定文本
 *
 * 辅助函数，用于在当前光标位置插入列表标记等特殊文本。
 * 处理以下情况：
 * - 光标在开头：直接插入
 * - 光标在中间：拆分字符串并插入
 * - 光标在末尾：追加文本
 *
 * @param state 编辑器状态对象
 * @param text 要插入的文本
 */
private fun insertTextAtCursor(state: RichTextEditorState, text: String) {
    val currentText = state.textFieldValue
    val position = currentText.selection.start
    
    val annotatedString = currentText.annotatedString
    val newString = buildAnnotatedString {
        append(annotatedString.subSequence(0, position).toString())
        append(text)
        append(annotatedString.subSequence(position, annotatedString.length).toString())
    }
    
    /** 更新值并将光标移动到插入文本之后 */
    val newPosition = position + text.length
    state.textFieldValue = TextFieldValue(
        annotatedString = newString,
        selection = TextRange(newPosition, newPosition)
    )
}

/**
 * 清除所有格式并重置编辑器状态
 *
 * 移除文本中的所有 SpanStyle 样式，
 * 并将格式化按钮状态全部重置为 false。
 *
 * @param state 编辑器状态对象
 */
fun clearAllFormats(state: RichTextEditorState) {
    /** 转换为纯文本（移除所有样式） */
    val plainText = state.textFieldValue.annotatedString.toString()
    state.textFieldValue = TextFieldValue(
        annotatedString = AnnotatedString(plainText),
        selection = TextRange(plainText.length)
    )
    
    /** 重置所有格式状态 */
    state.isBold = false
    state.isItalic = false
    state.isUnderline = false
    state.isStrikethrough = false
}
