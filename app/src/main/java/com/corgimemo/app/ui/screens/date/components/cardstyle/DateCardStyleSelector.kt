package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.ui.theme.UiColors

/**
 * 底部样式选择器(横向滚动)
 *
 * @param styles 可选样式列表
 * @param selected 当前选中的样式
 * @param onSelect 点击样式回调
 * @param targetDateMillis 缩略图渲染用的目标日期(与主预览一致)
 * @param title 缩略图渲染用的标题(与主预览一致)
 * @param cardColor 透传给缩略图渲染的卡片颜色(默认 DEFAULT,使用样式原色;由 DateCardColor 透传)
 */
@Composable
fun DateCardStyleSelector(
    styles: List<DateCardStyle>,
    selected: DateCardStyle,
    onSelect: (DateCardStyle) -> Unit,
    targetDateMillis: Long,
    title: String,
    modifier: Modifier = Modifier,
    cardColor: DateCardColor = DateCardColor.DEFAULT  // ← 新增
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(styles, key = { it.serialName }) { style ->
            val isSelected = style == selected
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) UiColors.Primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(style) }
            ) {
                // 缩略图渲染：使用完整版卡片 + 居中裁剪（等比例缩小，不拉伸变形）
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    DateCardStyleRenderer(
                        style = style,
                        title = title,
                        targetDateMillis = targetDateMillis,
                        modifier = Modifier
                            .fillMaxHeight()
                            .then(
                                when (style) {
                                    DateCardStyle.OrangeTearOff -> Modifier.aspectRatio(4f / 5f)
                                    DateCardStyle.CalendarTearOff -> Modifier.aspectRatio(2f / 3f)
                                }
                            ),
                        isThumbnail = false,
                        cardColor = cardColor
                    )
                }
                // 选中态右上角勾选标记
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(UiColors.Primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "已选中",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
