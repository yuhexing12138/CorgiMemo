package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.viewmodel.GroupType

/**
 * 日期分组标题组件（网格布局版本）
 * 用于在特殊日期网格列表中按GroupType分组显示标题
 * 格式："── 即将到来（倒计时） ──"
 *
 * 支持三种分组类型：
 * - UPCOMING: ── 即将到来（倒计时） ──
 * - CELEBRATING: ── 正在纪念（正计时） ──
 * - EXPIRED: ── 已过期 ──
 *
 * @param groupType 分组类型枚举值
 * @param modifier 修饰符
 */
@Composable
fun DateGroupHeader(
    groupType: GroupType,
    modifier: Modifier = Modifier
) {
    /** 根据分组类型获取标题文本 */
    val headerText = when (groupType) {
        GroupType.UPCOMING -> "即将到来（倒计时）"
        GroupType.CELEBRATING -> "正在纪念（正计时）"
        GroupType.EXPIRED -> "已过期"
    }

    Text(
        text = "── $headerText ──",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = androidx.compose.ui.graphics.Color(0xFF666666),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp)
    )
}
