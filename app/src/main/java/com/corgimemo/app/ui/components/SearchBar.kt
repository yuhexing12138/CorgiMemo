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
 * 搜索栏组件
 *
 * 圆角搜索输入框，支持实时搜索、清空、聚焦/失焦动画。
 * 背景色为暖橙色浅色 (#FFF3E8)，符合设计规范。
 *
 * @param query 当前搜索关键词
 * @param onQueryChange 搜索关键词变更回调（支持 debounce）
 * @param onClear 清空搜索回调
 * @param modifier 修饰符
 * @param placeholder 占位文字（默认："输入要搜索的内容..."）
 * @param enabled 是否启用（默认 true）
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier,
    placeholder: String = "输入要搜索的内容...",
    enabled: Boolean = true
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

        // 清空按钮
        if (localQuery.isNotEmpty()) {
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
    }
}
