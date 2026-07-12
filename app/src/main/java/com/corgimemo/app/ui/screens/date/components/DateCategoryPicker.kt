package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.viewmodel.DateCategory

/**
 * 日期分类选择器组件
 * 使用 FilterChip 实现单选互斥的分类选择，支持 7 种日期类型：
 * - 🎂 生日 (BIRTHDAY)
 * - 💕 纪念日 (ANNIVERSARY)
 * - 🎉 节日 (HOLIDAY)
 * - 🌱 生活 (LIFE)
 * - 📚 学习 (STUDY)
 * - 💼 工作 (WORK)
 * - 🎮 娱乐 (ENTERTAINMENT)
 * - 📅 其他 (OTHER)
 *
 * 渲染逻辑通过 `DateCategory.entries.forEach` 自动遍历枚举，
 * 因此枚举扩展后无需修改本组件即可支持新分类。
 *
 * @param selected 当前选中的分类
 * @param onSelected 分类选中回调函数
 * @param modifier 修饰符
 */
@Composable
fun DateCategoryPicker(
    selected: DateCategory,
    onSelected: (DateCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        /** 遍历所有日期分类枚举值 */
        DateCategory.entries.forEach { category ->
            FilterChip(
                selected = (category == selected),
                onClick = { onSelected(category) },
                label = {
                    Text(
                        text = "${category.emoji} ${category.displayName}",
                        fontSize = 12.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = com.corgimemo.app.ui.theme.UiColors.Primary.copy(alpha = 0.15f),
                    selectedLabelColor = com.corgimemo.app.ui.theme.UiColors.Primary
                )
            )
        }
    }
}
