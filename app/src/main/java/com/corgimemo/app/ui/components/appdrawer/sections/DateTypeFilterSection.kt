package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.ui.components.appdrawer.model.DateTypeAction
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.DateCategory

/**
 * 日期类型筛选分区（侧边栏）
 *
 * 布局：
 * 1. 标题"📅 类型筛选" + 橙色横线
 * 2. "全部日期" 项
 * 3. 8 个内置 [DateCategory]（BIRTHDAY / ANNIVERSARY / HOLIDAY / ...）
 * 4. 自定义类型列表（来自 [customDateTypes]，带菜单按钮可重命名/删除）
 *
 * **注意**：添加类型按钮由外层 AppDrawerContent 统一放置（与待办页/灵感页一致），
 * 避免内部 LazyColumn 无 weight 占据全部空间导致按钮不可见。
 *
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * @param selectedDateCategory 当前选中的类型（null=全部, "BIRTHDAY"=内置, "CUSTOM:42"=自定义）
 * @param dateCountByCategory 每个类型对应的日期计数
 * @param customDateTypes 自定义类型列表
 * @param onDateCategoryClick 类型点击回调
 * @param onCustomTypeAction 自定义类型操作回调（ShowMenu / Rename / Delete）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun DateTypeFilterSection(
    selectedDateCategory: String?,
    dateCountByCategory: Map<String, Int>,
    customDateTypes: List<CustomDateType>,
    onDateCategoryClick: (String?) -> Unit,
    onCustomTypeAction: (DateTypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 1. 标题
        Text(
            text = "📅 类型筛选",
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

        // 3. 类型列表
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            // "全部日期" 项
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部日期",
                    count = dateCountByCategory.values.sum(),
                    isSelected = selectedDateCategory == null,
                    showMenu = false,
                    onClick = { onDateCategoryClick(null) }
                )
            }

            // 8 个内置类型（DateCategory 枚举）
            items(DateCategory.entries.toList()) { dateCategory ->
                CategoryItem(
                    icon = dateCategory.emoji,
                    name = dateCategory.displayName,
                    count = dateCountByCategory[dateCategory.name] ?: 0,
                    isSelected = selectedDateCategory == dateCategory.name,
                    showMenu = false,
                    onClick = { onDateCategoryClick(dateCategory.name) }
                )
            }

            // 自定义类型（带菜单按钮可重命名/删除）
            items(customDateTypes) { customType ->
                CategoryItem(
                    icon = customType.emoji,
                    name = customType.name,
                    count = dateCountByCategory["CUSTOM:${customType.id}"] ?: 0,
                    isSelected = selectedDateCategory == "CUSTOM:${customType.id}",
                    showMenu = true,
                    onClick = { onDateCategoryClick("CUSTOM:${customType.id}") },
                    onMenuClick = {
                        onCustomTypeAction(DateTypeAction.ShowMenu(customType))
                    }
                )
            }
        }
    }
}
