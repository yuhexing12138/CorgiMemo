package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.components.appdrawer.model.DrawerSection
import com.corgimemo.app.ui.theme.UiColors

/**
 * 侧滑栏 Tab 切换器（v2026-07-27 新增）
 *
 * 在 TODO Tab 顶部提供"分组管理"和"状态管理"两个互斥标题的切换入口。
 * 激活态显示**加粗 + 主题色 + 3dp 橙色横线**；未激活态显示**普通字重 + 黑色 + 透明横线**。
 *
 * **视觉规范**：参见 `docs/superpowers/specs/UI设计规范.md` 12.1.13 章节
 *
 * **架构角色**：
 * - 本函数是 sections 包的内部实现（`internal` 可见性）
 * - 由 [AppDrawerContentImpl] 在 TODO Tab 顶部调用
 * - 调用方应使用 `com.corgimemo.app.ui.components.AppDrawerContent`（薄壳层），不要直接 import 本函数
 *
 * **实现要点**：
 * - 用 `clip(RoundedCornerShape(8.dp))` 限定点击波纹区域为圆角矩形
 * - 横线使用 `Color.Transparent` 占位避免布局抖动
 * - 不需要动画：横线 alpha 二值切换足够平滑
 *
 * @param currentSection 当前激活的分区
 * @param onSectionChange 分区切换回调（参数为新选中的分区）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun DrawerSectionTab(
    currentSection: DrawerSection,
    onSectionChange: (DrawerSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // 1. "分组管理" 标签
        DrawerSectionLabel(
            text = "分组管理",
            isActive = currentSection == DrawerSection.GROUP,
            onClick = { onSectionChange(DrawerSection.GROUP) }
        )

        Spacer(modifier = Modifier.width(24.dp))

        // 2. "状态管理" 标签
        DrawerSectionLabel(
            text = "状态管理",
            isActive = currentSection == DrawerSection.STATUS,
            onClick = { onSectionChange(DrawerSection.STATUS) }
        )
    }
}

/**
 * 单个 Tab 标签（私有，仅 DrawerSectionTab 使用）
 *
 * 视觉三段式：
 * ```
 * [文字]  ← 16sp，Bold/Primary 或 Normal/Black
 *   6dp 间距
 * [横线]  ← 3dp 高度，激活态显示 Primary 色，未激活态透明
 * ```
 *
 * @param text 标签文字（"分组管理" / "状态管理"）
 * @param isActive 是否处于激活态
 * @param onClick 点击回调
 */
@Composable
private fun DrawerSectionLabel(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        // 标题文字
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) UiColors.Primary else Color(0xFF1C1B1F)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 激活态横线（未激活态用 Color.Transparent 占位，避免布局抖动）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    color = if (isActive) UiColors.Primary else Color.Transparent
                )
        )
    }
}
