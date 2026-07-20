package com.corgimemo.app.ui.screens.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 开关项数据
 * 与 [SettingItem] 配对，用于需要在分组卡片内展示开关的场景
 *
 * @param icon emoji 图标（如 "🔊"）
 * @param title 标题
 * @param description 可选描述（次行灰色文字）
 * @param checked 当前开关状态
 * @param onCheckedChange 开关变化回调
 */
data class SwitchItem(
    val icon: String,
    val title: String,
    val description: String? = null,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

/**
 * 开关组卡片
 * 与 [SettingListCard] 视觉一致（圆角 20dp / 项高 48dp / 分割线 / 左侧图标框），
 * 差异仅在于右侧 trailing 为 [Switch] 而非箭头。
 *
 * 用于设置页"声音与反馈"等需要直接操作开关的分组，
 * 让设置页与"我的"页的分组卡片形成统一的视觉语言。
 *
 * 视觉规范（引用 UI设计规范 12.1）：
 * - 卡片圆角 20dp、水平内边距 16dp、垂直内边距 4dp、elevation 2dp
 * - 列表项最小高度 48dp
 * - 项之间 1dp 分割线（outlineVariant）
 * - 左侧图标 26dp 方形圆角 8dp，背景 surfaceVariant
 * - 标题 13sp Regular，描述 11sp onSurfaceVariant
 *
 * @param items 开关项列表
 */
@Composable
fun SettingSwitchGroupCard(
    items: List<SwitchItem>
) {
    // 显式声明 containerColor = MaterialTheme.colorScheme.surface
    // 亮色模式 surface = Color.White (6 种主题色统一)，深色模式 surface = 深灰
    // 用 surface 显式声明而非硬编码 Color.White，是为了遵循 UI 设计规范 12.1.2.2：
    //   - 亮色模式卡片背景 = #FFFFFF
    //   - 深色模式卡片背景 = #2A2A2A
    // 设置页"声音与反馈"分组使用，与 SettingListCard 保持视觉一致
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            items.forEachIndexed { index, item ->
                SwitchRow(item = item)
                // 最后一项不显示分割线
                if (index < items.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

/**
 * 单个开关行
 * 左侧图标 + 标题（+可选描述） + 右侧 Switch，最小高度 48dp 保证触摸区域
 *
 * @param item 开关项数据
 */
@Composable
private fun SwitchRow(item: SwitchItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧图标背景框（26dp 方形，圆角 8dp，surfaceVariant 背景，深色模式自动适配）
        Row(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.icon,
                fontSize = 13.sp,
                color = Color.Unspecified
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 可选描述：存在时显示为次行灰色文字
            item.description?.let { desc ->
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = item.checked,
            onCheckedChange = item.onCheckedChange
        )
    }
}
