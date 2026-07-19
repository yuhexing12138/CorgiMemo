package com.corgimemo.app.ui.screens.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 主题色预设定义
 * 对应 UI设计规范 12.1.3 的 6 种主题配色方案
 *
 * @param key 持久化 key（与 ThemeManager / CorgiPreferences 一致）
 * @param name 显示名
 * @param color 色块展示色
 */
data class ThemePreset(
    val key: String,
    val name: String,
    val color: Color
)

/**
 * 6 种主题色预设
 * key 顺序：orange / pink / green / blue / purple / brown
 * 与 ThemeManager.themeColor 及 CorgiPreferences（DataStore key: theme_color）的取值完全一致
 */
val ThemePresets = listOf(
    ThemePreset("orange", "暖阳橙", Color(0xFFFF9A5C)),
    ThemePreset("pink", "樱花粉", Color(0xFFFFB5C2)),
    ThemePreset("green", "薄荷绿", Color(0xFF7EC8A0)),
    ThemePreset("blue", "天空蓝", Color(0xFF7EB8DA)),
    ThemePreset("purple", "薰衣紫", Color(0xFFB8A0D4)),
    ThemePreset("brown", "奶茶棕", Color(0xFFC4A882))
)

/**
 * 主题配色快选卡
 * 6 色圆点横排，当前选中带主色环，点击即切
 *
 * 视觉规范：
 * - 卡片圆角 20dp、内边距 16dp
 * - 色点 32dp 圆形，间距 10dp（用 Arrangement.spacedBy + weight 均分）
 * - 选中态：色点外 4dp 主色环（2dp border + 3dp padding）
 *
 * @param currentColorKey 当前主题色 key
 * @param onColorSelected 点击色点回调
 * @param onManageClick 点击"管理 ›"回调
 */
@Composable
fun ThemeQuickSwitch(
    currentColorKey: String,
    onColorSelected: (String) -> Unit,
    onManageClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎨 主题配色",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "管理 ›",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onManageClick)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemePresets.forEach { preset ->
                    ThemeDot(
                        preset = preset,
                        isSelected = preset.key == currentColorKey,
                        onClick = { onColorSelected(preset.key) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 单个主题色圆点
 * 选中态：2dp 主色 border + 3dp 内边距，形成主色环效果
 *
 * @param preset 主题色预设
 * @param isSelected 是否当前选中
 * @param onClick 点击回调
 * @param modifier 外部修饰符（用于 weight 均分）
 */
@Composable
private fun ThemeDot(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(preset.color)
            .clickable(onClick = onClick)
    )
}
