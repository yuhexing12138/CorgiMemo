package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle

/**
 * DateCardStyle 渲染分发器
 *
 * 集中处理 sealed class -> 具体样式组件的映射,避免业务页面直接依赖具体实现。
 * 编译器保证 when 穷尽(新加 DateCardStyle 子类时编译报错提示补全)。
 */
@Composable
fun DateCardStyleRenderer(
    style: DateCardStyle,
    title: String,
    targetDateMillis: Long,
    modifier: Modifier = Modifier,
    isThumbnail: Boolean = false,
    cardColor: DateCardColor = DateCardColor.DEFAULT,  // ← 新增
    onShareClick: (() -> Unit)? = null,
    onCalendarClick: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null
) {
    when (style) {
        is DateCardStyle.OrangeTearOff -> OrangeTearOffCard(
            title = title,
            targetDateMillis = targetDateMillis,
            modifier = modifier,
            isThumbnail = isThumbnail,
            cardColor = cardColor,  // ← 透传
            onShareClick = onShareClick,
            onCalendarClick = onCalendarClick,
            onCardClick = onCardClick
        )
        is DateCardStyle.CalendarTearOff -> CalendarTearOffCard(
            title = title,
            targetDateMillis = targetDateMillis,
            modifier = modifier,
            isThumbnail = isThumbnail,
            cardColor = cardColor,  // ← 透传
            onCardClick = onCardClick
        )
    }
}
