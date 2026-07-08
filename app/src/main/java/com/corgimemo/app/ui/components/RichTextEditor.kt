package com.corgimemo.app.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
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
 * 富文本编辑器状态类
 */
class RichTextEditorState(
    var textFieldValue: androidx.compose.runtime.MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(AnnotatedString(""))),
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var isUnderline: Boolean = false,
    var isStrikethrough: Boolean = false
)

/**
 * 富文本编辑器核心组件
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
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val localFocusManager = LocalFocusManager.current

    val placeholderTextStyle = androidx.compose.ui.text.TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        fontSize = fontSize.sp
    )

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        BasicTextField(
            value = state.textFieldValue.value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                }
                .then(
                    if (onUndo != null || onRedo != null) {
                        Modifier.onPreviewKeyEvent { keyEvent ->
                            val nativeEvent = keyEvent.nativeKeyEvent
                            when {
                                onUndo != null &&
                                nativeEvent.isCtrlPressed &&
                                !nativeEvent.isShiftPressed &&
                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_Z &&
                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN -> {
                                    onUndo()
                                    true
                                }
                                onRedo != null &&
                                nativeEvent.isCtrlPressed &&
                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_Y &&
                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN -> {
                                    onRedo()
                                    true
                                }
                                else -> false
                            }
                        }
                    } else {
                        Modifier
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
                if (state.textFieldValue.value.annotatedString.isEmpty()) {
                    androidx.compose.foundation.layout.Box {
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

/** ===== 格式化操作函数 ===== */

fun applyBoldFormat(state: RichTextEditorState) {
    applySpanStyleToSelection(state, SpanStyle(fontWeight = FontWeight.Bold), state.isBold)
    state.isBold = !state.isBold
}

fun applyItalicFormat(state: RichTextEditorState) {
    applySpanStyleToSelection(state, SpanStyle(fontStyle = FontStyle.Italic), state.isItalic)
    state.isItalic = !state.isItalic
}

fun applyUnderlineFormat(state: RichTextEditorState) {
    applySpanStyleToSelection(state, SpanStyle(textDecoration = TextDecoration.Underline), state.isUnderline)
    state.isUnderline = !state.isUnderline
}

fun applyStrikethroughFormat(state: RichTextEditorState) {
    applySpanStyleToSelection(state, SpanStyle(textDecoration = TextDecoration.LineThrough), state.isStrikethrough)
    state.isStrikethrough = !state.isStrikethrough
}

private fun applySpanStyleToSelection(
    state: RichTextEditorState,
    spanStyle: SpanStyle,
    isActive: Boolean
) {
    val currentText = state.textFieldValue.value
    val selection = currentText.selection

    if (selection.start == selection.end) return

    val annotatedString = currentText.annotatedString
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)

    val newAnnotatedString: AnnotatedString = if (isActive) {
        buildAnnotatedString {
            append(annotatedString.subSequence(0, start).toString())
            append(annotatedString.subSequence(start, end).toString())
            append(annotatedString.subSequence(end, annotatedString.length).toString())
        }
    } else {
        val before = annotatedString.subSequence(0, start)
        val selected = annotatedString.subSequence(start, end)
        val after = annotatedString.subSequence(end, annotatedString.length)

        buildAnnotatedString {
            append(before.toString())
            withStyle(style = spanStyle) { append(selected.toString()) }
            append(after.toString())
        }
    }

    state.textFieldValue.value = currentText.copy(annotatedString = newAnnotatedString)
}

/** ===== 列表插入函数 ===== */

fun insertUnorderedList(state: RichTextEditorState) {
    insertTextAtCursor(state, "\n• ")
}

fun insertOrderedList(state: RichTextEditorState) {
    insertTextAtCursor(state, "\n1. ")
}

fun insertTodoItem(state: RichTextEditorState) {
    insertTextAtCursor(state, "\n☐ ")
}

private fun insertTextAtCursor(state: RichTextEditorState, text: String) {
    val currentText = state.textFieldValue.value
    val position = currentText.selection.start
    val annotatedString = currentText.annotatedString
    val newString = buildAnnotatedString {
        append(annotatedString.subSequence(0, position).toString())
        append(text)
        append(annotatedString.subSequence(position, annotatedString.length).toString())
    }

    val newPosition = position + text.length
    state.textFieldValue.value = TextFieldValue(
        annotatedString = newString,
        selection = TextRange(newPosition, newPosition)
    )
}

/** ===== 清除格式 ===== */

fun clearAllFormats(state: RichTextEditorState) {
    val plainText = state.textFieldValue.value.annotatedString.toString()
    state.textFieldValue.value = TextFieldValue(
        annotatedString = AnnotatedString(plainText),
        selection = TextRange(plainText.length)
    )
    state.isBold = false
    state.isItalic = false
    state.isUnderline = false
    state.isStrikethrough = false
}
