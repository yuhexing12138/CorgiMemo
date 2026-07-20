package com.corgimemo.app.ui.screens.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设置列表项数据
 *
 * @param icon emoji 图标（如 "🔔"）
 * @param title 标题
 * @param onClick 点击回调
 */
data class SettingItem(
    val icon: String,
    val title: String,
    val onClick: () -> Unit
)

/**
 * 通用设置列表卡
 * 用于"我的"页的设置入口组、数据备份、关于与帮助三组列表
 *
 * 视觉规范（引用 UI设计规范 12.1）：
 * - 卡片圆角 20dp、内边距 16dp、elevation 2dp
 * - 列表项最小高度 48dp
 * - 项之间 1dp 分割线（outlineVariant，对应规范 #EEEEEE / 深色 #333333）
 * - 左侧图标 26dp 方形圆角 8dp，背景 #F5F0E8
 * - 标题 13sp Regular，右箭头 14sp hint 色
 *
 * @param items 列表项列表
 */
@Composable
fun SettingListCard(
    items: List<SettingItem>
) {
    // 显式声明 containerColor = MaterialTheme.colorScheme.surface
    // 亮色模式 surface = Color.White (6 种主题色统一)，深色模式 surface = 深灰
    // 用 surface 显式声明而非硬编码 Color.White，是为了遵循 UI 设计规范 12.1.2.2：
    //   - 亮色模式卡片背景 = #FFFFFF
    //   - 深色模式卡片背景 = #2A2A2A
    // 此 Card 被设置页 4 个分组共用（通知/身份与偏好/数据管理/关于与帮助）
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
                SettingRow(item = item)
                // 最后一项不显示分割线
                if (index < items.lastIndex) {
                    // 1dp 分割线：补充 background 以呈现 outlineVariant 颜色
                    // （计划原文 Spacer 未设背景会导致分割线不可见，此处按 docstring 意图补全）
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
 * 单个设置行
 * 左侧图标 + 标题 + 右箭头，最小高度 48dp 保证触摸区域
 *
 * @param item 设置项数据
 */
@Composable
private fun SettingRow(item: SettingItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = item.onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧图标背景框（26dp 方形，圆角 8dp，surfaceVariant 暖灰背景，深色模式自动适配）
        Row(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(0.dp),
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
        Text(
            text = item.title,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "›",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
