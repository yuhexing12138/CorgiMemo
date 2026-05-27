package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 时间线分组标题组件
 * 用于在灵感列表中按日期分组显示标题
 * 格式示例："── 2026年5月27日 周三 ──"
 *
 * @param dateText 日期文本（格式："YYYY年M月D日 周X"）
 * @param modifier 修饰符
 */
@Composable
fun TimelineGroupHeader(
    dateText: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "── $dateText ──",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF666666),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp)
    )
}
