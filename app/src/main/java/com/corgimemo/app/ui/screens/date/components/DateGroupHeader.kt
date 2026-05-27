package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.viewmodel.GroupType

/**
 * 日期分组标题组件
 * 用于在特殊日期列表中按GroupType分组显示标题
 * 显示格式：彩色圆点 + 分组描述文本
 *
 * 支持三种分组类型：
 * - UPCOMING: 即将到来 · 倒计时（橙色）
 * - CELEBRATING: 正在纪念 · 正计时（绿色）
 * - EXPIRED: 已过期（灰色）
 *
 * @param groupType 分组类型枚举值
 * @param modifier 修饰符
 */
@Composable
fun DateGroupHeader(
    groupType: GroupType,
    modifier: Modifier = Modifier
) {
    /** 根据分组类型获取圆点颜色和标题文本 */
    val (dotColor, headerText) = when (groupType) {
        GroupType.UPCOMING -> Color(0xFFFF9A5C) to "即将到来 · 倒计时"
        GroupType.CELEBRATING -> Color(0xFF4CAF50) to "正在纪念 · 正计时"
        GroupType.EXPIRED -> Color(0xFF999999) to "已过期"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
    ) {
        /** 彩色圆点指示器（4×14dp 圆角矩形） */
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(14.dp)
                .background(
                    color = dotColor,
                    shape = RoundedCornerShape(2.dp)
                )
        )

        /** 分组标题文本 */
        Text(
            text = headerText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF666666),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
