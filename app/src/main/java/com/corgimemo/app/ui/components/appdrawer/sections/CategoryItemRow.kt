package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

// ==================== 共享图标常量（internal） ====================
// 原 AppDrawer.kt L64-72 — 拆出后改为 internal，让同包 section 可见
// 4 个分区都用 CategoryItem，这些常量随 CategoryItem 一起放在本文件

/** "全部待办" 分类图标 */
internal const val DRAWER_ICON_ALL = "📋"

/** "未分类" 分类图标 */
internal const val DRAWER_ICON_UNCATEGORIZED = "📦"

/** 自定义分组的默认 emoji 映射表（按分类 sortOrder 索引） */
internal val categoryIcons: Map<Int, String> = mapOf(
    0 to "📚",
    1 to "💼",
    2 to "🏠",
    3 to "🏃"
)

/**
 * 分类/标签/日期类型 通用 Item 行（internal）
 *
 * 4 个 section 共用：CategoryGroupSection / InspirationFilterSection /
 * DateTypeFilterSection / ProfileQuickNavSection。
 *
 * UI 组成：[图标 emoji] [名称（选中时高亮）] [(数量)] [右箭头 / 菜单图标]
 *
 * **可见性说明**：原 `private` 改为 `internal` 是拆分的**必要调整** —
 * 4 个 section 跨文件调用，private 会导致编译失败。internal 在单模块项目中等价于 public，
 * 但不暴露给外部依赖（外部调用方应通过 AppDrawerContent 进入）。
 *
 * @param icon 前置 emoji
 * @param name 显示名称
 * @param count 关联数量（>0 时显示括号数字）
 * @param isSelected 是否选中（选中时加粗 + Primary 色）
 * @param showMenu 是否显示三点菜单（true 时显示 IconButton，false 时显示右箭头）
 * @param textColor 未选中时文字颜色
 * @param onClick 行点击回调
 * @param onMenuClick 菜单按钮点击回调（仅 showMenu=true 时生效）
 */
@Composable
internal fun CategoryItem(
    icon: String,
    name: String,
    count: Int,
    isSelected: Boolean,
    showMenu: Boolean,
    textColor: Color = Color(0xFF1C1B1F),
    onClick: () -> Unit,
    onMenuClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 前置 emoji 图标
        Text(text = icon, fontSize = 20.sp)

        Spacer(modifier = Modifier.width(12.dp))

        // 2. 名称（选中时加粗 + Primary 色）
        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) UiColors.Primary else textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 3. 关联数量（>0 才显示）
        if (count > 0) {
            Text(
                text = "($count)",
                fontSize = 13.sp,
                color = Color(0xFF79747E)
            )
        }

        // 4. 右侧：菜单按钮 or 右箭头
        if (showMenu) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    tint = Color(0xFF79747E),
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFFBDBDBD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
