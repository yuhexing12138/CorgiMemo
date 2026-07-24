package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

/**
 * "我的"页快捷导航分区（侧边栏）
 *
 * 布局：
 * 1. 标题"🔗 快捷导航" + 橙色横线
 * 2. 3 个固定项：统计 / 成就 / 设置
 *
 * **TODO 待完善**：统计 / 成就 当前是 TODO 占位（点击无动作），设置项已对接 [onSettingsClick]。
 *
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * @param onSettingsClick "设置"项点击回调（MainScreen 负责导航到 Screen.Settings）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun ProfileQuickNavSection(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 1. 标题
        Text(
            text = "🔗 快捷导航",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // 2. 橙色分割线
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .fillMaxWidth()
                .background(UiColors.Primary)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. 3 个固定项
        Column(modifier = Modifier.fillMaxWidth()) {
            // 统计
            CategoryItem(
                icon = "📊",
                name = "统计",
                count = 0,
                isSelected = false,
                showMenu = false,
                onClick = { /* TODO: 导航到统计页 */ }
            )
            // 成就
            CategoryItem(
                icon = "🏆",
                name = "成就",
                count = 0,
                isSelected = false,
                showMenu = false,
                onClick = { /* TODO: 导航到成就页 */ }
            )
            // 设置（已对接回调）
            CategoryItem(
                icon = "⚙️",
                name = "设置",
                count = 0,
                isSelected = false,
                showMenu = false,
                onClick = onSettingsClick
            )
        }
    }
}
