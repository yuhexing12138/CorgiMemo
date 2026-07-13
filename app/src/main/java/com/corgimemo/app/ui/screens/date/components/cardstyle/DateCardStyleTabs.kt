package com.corgimemo.app.ui.screens.date.components.cardstyle

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 卡片样式 tab 类型 */
enum class DateCardStyleTab(val displayName: String) {
    STYLE("样式"),
    COLOR("颜色")
}

/**
 * 样式/颜色 tab 切换条
 *
 * @param selected 当前选中的 tab
 * @param onTabChange tab 切换回调;COLOR tab 当前阶段触发占位 Snackbar(由调用方处理)
 */
@Composable
fun DateCardStyleTabs(
    selected: DateCardStyleTab,
    onTabChange: (DateCardStyleTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        DateCardStyleTab.values().forEach { tab ->
            val isSelected = tab == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTabChange(tab) }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = tab.displayName,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF666666)
                )
                Spacer(Modifier.height(4.dp))
                // 选中态显示主色下划线
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                )
            }
        }
    }
}
