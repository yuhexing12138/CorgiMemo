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
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction
import com.corgimemo.app.ui.theme.UiColors

/**
 * 待办分组管理分区（侧边栏）
 *
 * 布局：
 * 1. 标题"分组管理" + 橙色横线
 * 2. "全部待办"项（selectedCategoryId == null）
 * 3. "未分类"项（selectedCategoryId == 0L）
 * 4. 自定义分类列表（来自 [categories]，按 sortOrder 排序）
 *
 * 长按自定义分类 → 触发 [CategoryAction.ShowMenu]（MainScreen 显示 BottomSheet）
 *
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * @param categories 自定义分类列表（已按 sortOrder 排序）
 * @param todoCountByCategory 各分类 ID → 待办数量映射（key=-1 表示全部，key=0 表示未分类）
 * @param selectedCategoryId 当前选中的分类 ID（null=全部, 0L=未分类, 其他=自定义分类 ID）
 * @param onCategoryClick 点击分类行回调（参数为分类 ID）
 * @param onCategoryAction 分组操作回调（ShowMenu / Pin / Rename / Delete）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun CategoryGroupSection(
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    selectedCategoryId: Long?,
    onCategoryClick: (Long?) -> Unit,
    onCategoryAction: (CategoryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 标题
        Text(
            text = "分组管理",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // 橙色横线（视觉分隔）
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .fillMaxWidth()
                .background(UiColors.Primary)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 分类列表
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. "全部待办" 项（特殊 ID: -1L）
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部待办",
                    count = todoCountByCategory[-1L] ?: 0,
                    isSelected = selectedCategoryId == null,
                    showMenu = false,
                    onClick = { onCategoryClick(null) }
                )
            }

            // 2. "未分类" 项（特殊 ID: 0L）
            item {
                CategoryItem(
                    icon = DRAWER_ICON_UNCATEGORIZED,
                    name = "未分类",
                    count = todoCountByCategory[0L] ?: 0,
                    isSelected = selectedCategoryId == 0L,
                    showMenu = false,
                    onClick = { onCategoryClick(0L) }
                )
            }

            // 3. 自定义分类列表（默认分类无菜单按钮）
            items(categories) { category ->
                val icon = categoryIcons[category.type] ?: "📂"
                CategoryItem(
                    icon = icon,
                    name = category.name,
                    count = todoCountByCategory[category.id] ?: 0,
                    isSelected = selectedCategoryId == category.id,
                    showMenu = !category.isDefault,
                    onClick = { onCategoryClick(category.id) },
                    onMenuClick = {
                        onCategoryAction(
                            CategoryAction.ShowMenu(category)
                        )
                    }
                )
            }
        }
    }
}
