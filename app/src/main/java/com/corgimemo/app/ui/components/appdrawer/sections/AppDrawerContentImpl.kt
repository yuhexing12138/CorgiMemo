package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.ui.components.UserAvatar
import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction
import com.corgimemo.app.ui.components.appdrawer.model.DateTypeAction
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.TagFilterMode

/**
 * 侧边栏主入口实现（侧边栏专用）
 *
 * 编排 4 个分区（TODO / INSPIRE / DATE / PROFILE）+ 顶部用户头 + 底部添加按钮。
 *
 * **架构角色**：本函数是真实实现，由 `com.corgimemo.app.ui.components.AppDrawer.kt` 薄壳
 * `AppDrawerContent` 转发调用。**外部调用方应使用 `AppDrawerContent`**，不要直接 import
 * 本函数（避免破坏薄壳兼容性）。
 *
 * @param currentTab 当前选中的 Tab（决定显示哪个分区）
 * @param corgiData 柯基数据（用户头渲染用）
 * @param categories 自定义分类列表（TODO Tab 用）
 * @param todoCountByCategory 待办分组计数（TODO Tab 用，key=-1=全部, key=0=未分类）
 * @param selectedCategoryId 当前选中的分类 ID（TODO Tab 用）
 * @param inspirationTags 灵感标签列表（INSPIRE Tab 用）
 * @param selectedTags 当前选中的标签集合（INSPIRE Tab 用）
 * @param tagFilterMode 标签筛选模式（INSPIRE Tab 用）
 * @param tagCounts 每个标签对应的灵感数量（INSPIRE Tab 用）
 * @param totalInspirationCount 灵感总数（INSPIRE Tab 用）
 * @param selectedDateCategory 当前选中的日期类型（DATE Tab 用）
 * @param dateCountByCategory 日期类型计数（DATE Tab 用）
 * @param customDateTypes 自定义日期类型列表（DATE Tab 用）
 * @param onCategoryClick 分类点击回调（TODO Tab）
 * @param onAddCategoryClick 添加分组回调（TODO Tab 底部按钮）
 * @param onCategoryAction 分类操作回调（TODO Tab，长按分类触发）
 * @param onTagClick 标签点击回调（INSPIRE Tab）
 * @param onTagFilterModeChange 标签筛选模式切换回调（INSPIRE Tab）
 * @param onClearTagSelection 清空选中标签回调（INSPIRE Tab，"全部灵感"项）
 * @param onAddTagClick 添加标签回调（INSPIRE Tab 底部按钮）
 * @param onDateCategoryClick 日期类型点击回调（DATE Tab）
 * @param onAddCustomTypeClick 添加自定义类型回调（DATE Tab 底部按钮）
 * @param onCustomTypeAction 自定义类型操作回调（DATE Tab，长按触发）
 * @param onSettingsClick 设置点击回调（PROFILE Tab）
 * @param onUserAreaClick 用户头区域点击回调（顶部，点击跳"我的"页）
 * @param modifier 外部 Modifier
 */
@Composable
fun AppDrawerContentImpl(
    currentTab: TabItem,
    corgiData: CorgiData?,
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    selectedCategoryId: Long?,
    inspirationTags: List<String> = emptyList(),
    selectedTags: Set<String> = emptySet(),
    tagFilterMode: TagFilterMode = TagFilterMode.OR,
    tagCounts: Map<String, Int> = emptyMap(),
    totalInspirationCount: Int = 0,
    selectedDateCategory: String? = null,
    dateCountByCategory: Map<String, Int> = emptyMap(),
    customDateTypes: List<CustomDateType> = emptyList(),
    onCategoryClick: (Long?) -> Unit = {},
    onAddCategoryClick: () -> Unit = {},
    onCategoryAction: (CategoryAction) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onTagFilterModeChange: (TagFilterMode) -> Unit = {},
    onClearTagSelection: () -> Unit = {},
    onAddTagClick: () -> Unit = {},
    onDateCategoryClick: (String?) -> Unit = {},
    onAddCustomTypeClick: () -> Unit = {},
    onCustomTypeAction: (DateTypeAction) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUserAreaClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(top = 48.dp)
    ) {
        // 1. 顶部用户头（所有 Tab 共享）
        DrawerUserHeader(
            corgiData = corgiData,
            onClick = onUserAreaClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 中部分区（按 Tab 分发）
        when (currentTab) {
            TabItem.TODO -> {
                CategoryGroupSection(
                    categories = categories,
                    todoCountByCategory = todoCountByCategory,
                    selectedCategoryId = selectedCategoryId,
                    onCategoryClick = onCategoryClick,
                    onCategoryAction = onCategoryAction,
                    modifier = Modifier.weight(1f)
                )
                AddCategoryButton(text = "添加分组", onClick = onAddCategoryClick)
            }
            TabItem.INSPIRE -> {
                InspirationFilterSection(
                    tags = inspirationTags,
                    selectedTags = selectedTags,
                    filterMode = tagFilterMode,
                    tagCounts = tagCounts,
                    totalInspirationCount = totalInspirationCount,
                    onTagClick = onTagClick,
                    onFilterModeChange = onTagFilterModeChange,
                    onClearTagSelection = onClearTagSelection,
                    modifier = Modifier.weight(1f)
                )
                AddCategoryButton(text = "添加标签", onClick = onAddTagClick)
            }
            TabItem.DATE -> {
                DateTypeFilterSection(
                    selectedDateCategory = selectedDateCategory,
                    dateCountByCategory = dateCountByCategory,
                    customDateTypes = customDateTypes,
                    onDateCategoryClick = onDateCategoryClick,
                    onCustomTypeAction = onCustomTypeAction,
                    modifier = Modifier.weight(1f)
                )
                AddCategoryButton(text = "添加类型", onClick = onAddCustomTypeClick)
            }
            TabItem.PROFILE -> {
                ProfileQuickNavSection(
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.weight(1f)
                )
            }
            TabItem.EDIT -> { /* 中央编辑按钮不是真实 Tab，无内容渲染 */ }
        }

        // 3. 底部留白
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 侧滑栏顶部用户头（私有，仅 AppDrawerContentImpl 使用）
 *
 * 视觉规范：
 * - 48dp 圆形头像 + 用户昵称 + 副标题"签名"
 * - 头像用 [UserAvatar] 组件（与"我的"页头卡保持视觉一致）
 * - 整行可点击，点击后切到"我的"页
 *
 * @param corgiData 柯基数据（昵称 / 签名 / 头像路径）
 * @param onClick 整行点击回调（MainScreen 传切到 PROFILE tab + 关 drawer）
 */
@Composable
private fun DrawerUserHeader(
    corgiData: CorgiData?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像 48dp（首字母占位或 Coil 加载真实头像）
        UserAvatar(
            nickname = corgiData?.name ?: "柯基",
            avatarPath = corgiData?.avatarPath,
            size = 48.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 昵称 + 签名
        Column {
            Text(
                text = corgiData?.name ?: "柯基",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (corgiData != null) {
                Text(
                    text = corgiData.signature,
                    fontSize = 13.sp,
                    color = Color(0xFF79747E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 底部"添加"按钮（私有，仅 AppDrawerContentImpl 使用）
 *
 * 3 个 Tab 都有底部添加按钮：TODO（添加分组）/ INSPIRE（添加标签）/ DATE（添加类型）
 * 文案通过 [text] 参数动态传入。
 */
@Composable
private fun AddCategoryButton(
    text: String = "添加分组",
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = UiColors.Primary,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
