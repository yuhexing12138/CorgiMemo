package com.corgimemo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors
import kotlinx.coroutines.delay

/**
 * 搜索栏组件（增强版）
 *
 * 圆角搜索输入框，支持实时搜索、清空、聚焦/失焦动画、自定义尾部图标。
 * 背景色为暖橙色浅色 (#FFF3E8)，符合设计规范。
 *
 * @param query 当前搜索关键词
 * @param onQueryChange 搜索关键词变更回调（支持 debounce）
 * @param onClear 清空搜索回调
 * @param modifier 修饰符
 * @param placeholder 占位文字（默认："输入要搜索的内容..."）
 * @param enabled 是否启用（默认 true）
 * @param trailingIcon 尾部图标内容 Composable lambda（可选）
 *                   - 与清空按钮互斥显示（当 showTrailingIconAlways = false 时）
 *                   - 支持任意 Composable 内容（IconButton、Icon、Text 等）
 * @param showTrailingIconAlways 是否始终显示 trailingIcon（默认 false）
 *                             - true: 忽略清空按钮，始终显示 trailingIcon
 *                             - false: 当有输入内容时显示清空按钮，否则显示 trailingIcon
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier,
    placeholder: String = "输入要搜索的内容...",
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
    showTrailingIconAlways: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var localQuery by remember(query) { mutableStateOf(query) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else UiColors.SearchBackground,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "searchBarBackground"
    )

    LaunchedEffect(localQuery) {
        if (localQuery != query) {
            delay(300) // debounce 300ms
            if (localQuery == query) return@LaunchedEffect
            onQueryChange(localQuery)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 搜索图标
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "搜索",
            tint = UiColors.Primary,
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    focusRequester.requestFocus()
                }
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 输入框
        BasicTextField(
            value = localQuery,
            onValueChange = { newValue ->
                localQuery = newValue
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                },
            enabled = enabled,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            singleLine = true,
            cursorBrush = SolidColor(UiColors.Primary),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    onQueryChange(localQuery)
                }
            ),
            decorationBox = { innerTextField ->
                if (localQuery.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                innerTextField()
            }
        )

        /** 尾部区域：清空按钮 / trailingIcon 智能切换 */
        when {
            // 场景 A：始终显示模式（忽略输入状态）
            showTrailingIconAlways && trailingIcon != null -> {
                trailingIcon()
            }

            // 场景 B：有输入内容 → 显示清空按钮
            localQuery.isNotEmpty() -> {
                IconButton(
                    onClick = {
                        localQuery = ""
                        onClear()
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 场景 C：无输入 + 有自定义图标 → 显示 trailingIcon
            trailingIcon != null -> {
                trailingIcon()
            }

            // 场景 D：无输入 + 无自定义图标 → 不显示任何内容
            else -> { /* 不渲染 */ }
        }
    }
}
