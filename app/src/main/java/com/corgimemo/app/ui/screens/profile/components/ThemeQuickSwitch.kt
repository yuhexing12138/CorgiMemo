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
 * @param key 持久化 key（与 ThemeManager / CorgiPreferences / Color.kt getColorScheme 一致）
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
 * 与 ThemeManager.themeColor、CorgiPreferences（DataStore key: theme_color）、
 * Color.kt#getColorScheme 的取值完全一致。
 *
 * 名称映射（与 UI 设计规范 12.1.3 一致）：
 * | key    | 名称   | 主色     |
 * |--------|--------|----------|
 * | orange | 暖阳橙 | #FF9A5C |
 * | pink   | 樱花粉 | #FFB5C2 |
 * | green  | 薄荷绿 | #7EC8A0 |
 * | blue   | 天空蓝 | #7EB8DA |
 * | purple | 薰衣紫 | #B8A0D4 |
 * | brown  | 奶茶棕 | #C4A882 |
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
 * 主题配色卡（"我的"页）
 *
 * 行为变更（v1.0 → v1.1）：
 * - 旧版：展示 6 个色点供快速切换，点击"管理 ›"跳整页设置
 * - 新版：**只读展示当前主题**（大色点 + 名称 + 描述），整卡可点击跳转 `Screen.Appearance`
 *
 * 切换主题已统一收敛到 `AppearanceScreen`，本页不再承担切换职责，保持"我的"页的简洁与只读语义。
 *
 * 视觉规范：
 * - 卡片圆角 20dp、elevation 2dp、内边距 16dp
 * - 左侧 56dp 圆形大色点（3dp 主色环 + 48dp 色点），右侧 主题名 + 描述，右上"›"箭头
 * - 整卡 `clickable`，统一触觉/视觉反馈
 *
 * @param currentColorKey 当前主题色 key
 * @param onCardClick 点击整卡回调（跳转外观页）
 */
@Composable
fun ThemeQuickSwitch(
    currentColorKey: String,
    onCardClick: () -> Unit
) {
    // 当前主题色预设（找不到时兜底为 orange）
    val currentPreset = ThemePresets.firstOrNull { it.key == currentColorKey }
        ?: ThemePresets.first()

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 左侧：大色点（56dp 主色环 + 48dp 色点）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp)
            ) {
                // 主色环（始终显示，强化"这是当前主题"的语义）
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
                // 色点
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(currentPreset.color)
                        .border(
                            width = 1.dp,
                            color = Color.Black.copy(alpha = 0.06f),
                            shape = CircleShape
                        )
                )
            }

            // 中间：标题 + 描述
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "🎨 主题配色",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "当前：${currentPreset.name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "点击切换 6 种主题色或深色模式",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // 右侧：箭头
            Text(
                text = "›",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
