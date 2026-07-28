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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.corgimemo.app.data.model.ProfileNavItem
import com.corgimemo.app.ui.components.UserAvatar
import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction
import com.corgimemo.app.ui.components.appdrawer.model.DateTypeAction
import com.corgimemo.app.ui.components.appdrawer.model.DrawerSection
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.FilterItem
import com.corgimemo.app.viewmodel.SpecialDateViewModel
import com.corgimemo.app.viewmodel.StatusFilter
import com.corgimemo.app.viewmodel.TagFilterMode

/**
 * 侧边栏主入口实现（侧边栏专用）
 *
 * 编排 4 个分区（TODO / INSPIRE / DATE / PROFILE）+ 顶部用户头 + 底部添加按钮。
 * TODO Tab 下支持"分组管理"和"状态管理"两个互斥分区切换（v2026-07-27 新增）。
 *
 * **架构角色**：本函数是真实实现，由 `com.corgimemo.app.ui.components.AppDrawer.kt` 薄壳
 * `AppDrawerContent` 转发调用。**外部调用方应使用 `AppDrawerContent`**，不要直接 import
 * 本函数（避免破坏薄壳兼容性）。
 *
 * @param currentTab 当前选中的 Tab（决定显示哪个分区）
 * @param corgiData 柯基数据（用户头渲染用）
 * @param categories 自定义分类列表（TODO Tab 用）
 * @param todoCountByCategory 待办分组计数（TODO Tab 用，key=-1=全部, key=0=未分类）
 * @param selectedFilterItems 跨维度统一选中的过滤项集合（v2026-07-28 v2 跨维度，TODO Tab 用）
 * @param filterMode 跨维度统一过滤模式（v2026-07-28 v2 跨维度，TODO Tab 用，跨分组+状态共享）
 * @param inspirationTags 灵感标签列表（INSPIRE Tab 用）
 * @param selectedTags 当前选中的标签集合（INSPIRE Tab 用）
 * @param tagFilterMode 标签筛选模式（INSPIRE Tab 用）
 * @param tagCounts 每个标签对应的灵感数量（INSPIRE Tab 用）
 * @param totalInspirationCount 灵感总数（INSPIRE Tab 用）
 * @param selectedDateCategory 当前选中的日期类型（DATE Tab 用）
 * @param dateCountByCategory 日期类型计数（DATE Tab 用）
 * @param customDateTypes 自定义日期类型列表（DATE Tab 用）
 * @param onCategoryToggle 分组项点击回调（v2026-07-28 v2 跨维度，TODO Tab）
 * @param onAddCategoryClick 添加分组回调（TODO Tab 底部按钮）
 * @param onCategoryAction 分类操作回调（TODO Tab，长按分类触发）
 * @param onReorderCategory 分类拖拽排序回调（v2026-07-27 P8 Phase 1 新增，TODO/GROUP Tab）
 * @param onStatusFilterToggle 状态项点击回调（v2026-07-28 v2 跨维度，TODO Tab）
 * @param onFilterModeChange 跨维度 mode 切换回调（v2026-07-28 v2 跨维度，TODO Tab）
 * @param onClearAllFilters 清空所有过滤回调（v2026-07-28 v2 跨维度，TODO Tab）
 * @param onTagClick 标签点击回调（INSPIRE Tab）
 * @param onTagFilterModeChange 标签筛选模式切换回调（INSPIRE Tab）
 * @param onClearTagSelection 清空选中标签回调（INSPIRE Tab，"全部灵感"项）
 * @param onAddTagClick 添加标签回调（INSPIRE Tab 底部按钮）
 * @param onReorderInspirationTag 灵感标签拖拽排序回调（v2026-07-27 P8 Phase 4 新增，INSPIRE Tab）
 * @param onDateCategoryClick 日期类型点击回调（DATE Tab）
 * @param onAddCustomTypeClick 添加自定义类型回调（DATE Tab 底部按钮）
 * @param onCustomTypeAction 自定义类型操作回调（DATE Tab，长按触发）
 * @param onReorderDateType 日期类型拖拽排序回调（v2026-07-27 P8 Phase 3 新增，DATE Tab）
 * @param onSettingsClick 设置点击回调（PROFILE Tab）
 * @param navItems 个人快速导航项（v2026-07-27 P8 Phase 5 新增，PROFILE Tab 用）
 * @param onReorderNav 快速导航项拖拽排序回调（v2026-07-27 P8 Phase 5 新增，PROFILE Tab）
 * @param onUserAreaClick 用户头区域点击回调（顶部，点击跳"我的"页）
 * @param currentDrawerSection 当前侧滑栏分区（v2026-07-27 新增，TODO Tab 用，默认 [DrawerSection.GROUP]）
 * @param onDrawerSectionChange 分区切换回调（v2026-07-27 新增）
 * @param statusOrder 状态过滤项顺序（v2026-07-27 P8 Phase 2 新增，TODO Tab 用）
 * @param onReorderStatus 状态过滤项拖拽排序回调（v2026-07-27 P8 Phase 2 新增）
 * @param totalTodoCount 全部待办数（v2026-07-27 新增，状态管理用）
 * @param pinnedCount 置顶待办数（v2026-07-27 新增）
 * @param pendingCount 待完成待办数（v2026-07-27 新增）
 * @param completedCount 已完成待办数（v2026-07-27 新增）
 * @param overdueCount 已过期待办数（v2026-07-27 新增）
 * @param repeatReminderCount 重复提醒待办数（v2026-07-27 新增）
 * @param modifier 外部 Modifier
 */
@Composable
fun AppDrawerContentImpl(
    currentTab: TabItem,
    corgiData: CorgiData?,
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    // ★ v2026-07-28 v2 跨维度：删除 selectedCategoryId、statusFilter，改用 selectedFilterItems + filterMode
    selectedFilterItems: Set<FilterItem> = emptySet(),
    filterMode: TagFilterMode = TagFilterMode.OR,
    inspirationTags: List<String> = emptyList(),
    selectedTags: Set<String> = emptySet(),
    tagFilterMode: TagFilterMode = TagFilterMode.OR,
    tagCounts: Map<String, Int> = emptyMap(),
    totalInspirationCount: Int = 0,
    selectedDateCategory: String? = null,
    dateCountByCategory: Map<String, Int> = emptyMap(),
    // 🆕 v2026-07-28 P8.6：统一日期类型列表（内置 8 个 + 自定义混排）
    dateTypeOrder: List<SpecialDateViewModel.DateTypeEntry> = emptyList(),
    // ★ v2026-07-28 v2 跨维度：删除 onCategoryClick，新增 onCategoryToggle
    onCategoryToggle: (FilterItem) -> Unit = {},
    onAddCategoryClick: () -> Unit = {},
    onCategoryAction: (CategoryAction) -> Unit = {},
    // v2026-07-27 P8 Phase 1 新增：分类拖拽回调（CategoryGroupSection 内置 Reorderable）
    onReorderCategory: (List<Category>) -> Unit = {},
    // ★ v2026-07-28 v2 跨维度：删除 onStatusFilterClick，新增 onStatusFilterToggle
    onStatusFilterToggle: (FilterItem) -> Unit = {},
    onFilterModeChange: (TagFilterMode) -> Unit = {},
    onClearAllFilters: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onTagFilterModeChange: (TagFilterMode) -> Unit = {},
    onClearTagSelection: () -> Unit = {},
    onAddTagClick: () -> Unit = {},
    // v2026-07-27 P8 Phase 4 新增：灵感标签拖拽回调
    onReorderInspirationTag: (List<String>) -> Unit = {},
    onDateCategoryClick: (String?) -> Unit = {},
    onAddCustomTypeClick: () -> Unit = {},
    onCustomTypeAction: (DateTypeAction) -> Unit = {},
    // 🆕 v2026-07-28 P8.6：日期类型拖拽回调（统一 List<DateTypeEntry>，支持跨组混排）
    onReorderDateType: (List<SpecialDateViewModel.DateTypeEntry>) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUserAreaClick: () -> Unit = {},
    // v2026-07-27 P8 Phase 5 新增：个人快速导航项 + 拖拽回调
    navItems: List<ProfileNavItem> = emptyList(),
    onReorderNav: (List<ProfileNavItem>) -> Unit = {},
    // ===== v2026-07-27 新增：状态管理 Tab 切换 =====
    currentDrawerSection: DrawerSection = DrawerSection.GROUP,
    onDrawerSectionChange: (DrawerSection) -> Unit = {},
    // v2026-07-27 P8 Phase 2 新增：状态过滤项拖拽顺序 + 回调
    statusOrder: List<StatusFilter> = StatusFilter.values().toList(),
    onReorderStatus: (List<StatusFilter>) -> Unit = {},
    totalTodoCount: Int = 0,
    pinnedCount: Int = 0,
    pendingCount: Int = 0,
    completedCount: Int = 0,
    overdueCount: Int = 0,
    repeatReminderCount: Int = 0,
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
                // 2.1 Tab 切换器（v2026-07-27 调整：位置 = 原"分组管理" 内部标题位置）
                DrawerSectionTab(
                    currentSection = currentDrawerSection,
                    onSectionChange = onDrawerSectionChange
                )

                // 2.2 整条 3dp 橙线（v2026-07-27 新增：保留原"分组管理" 内部标题下方的橙线）
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .height(3.dp)
                        .fillMaxWidth()
                        .background(UiColors.Primary)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 🆕 v2026-07-28 v2 跨维度：全局 FilterChip Row（OR/AND/NOT 跨 2 个分区共享）
                //   位置：DrawerSectionTab 下方，section 上方
                //   业务规则：点击 AND 时自动清空分组项（避免 AND 多分组退化为空集）
                FilterModeChipRow(
                    mode = filterMode,
                    onModeChange = onFilterModeChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 2.3 互斥渲染"分组管理"或"状态管理"
                when (currentDrawerSection) {
                    DrawerSection.GROUP -> {
                        // ★ v2026-07-28 v2 跨维度：拆分 selectedFilterItems 为 Category 子集
                        val selectedCategoryItems = selectedFilterItems
                            .filterIsInstance<FilterItem.Category>()
                            .toSet()
                        CategoryGroupSection(
                            categories = categories,
                            todoCountByCategory = todoCountByCategory,
                            selectedCategoryItems = selectedCategoryItems,
                            onCategoryToggle = onCategoryToggle,
                            onClearAllFilters = onClearAllFilters,
                            onCategoryAction = onCategoryAction,
                            onReorder = onReorderCategory,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DrawerSection.STATUS -> {
                        // ★ v2026-07-28 v2 跨维度：拆分 selectedFilterItems 为 Status 子集
                        val selectedStatusItems = selectedFilterItems
                            .filterIsInstance<FilterItem.Status>()
                            .toSet()
                        StatusFilterSection(
                            statusOrder = statusOrder,
                            selectedStatusItems = selectedStatusItems,
                            totalCount = totalTodoCount,
                            pinnedCount = pinnedCount,
                            pendingCount = pendingCount,
                            completedCount = completedCount,
                            overdueCount = overdueCount,
                            repeatReminderCount = repeatReminderCount,
                            onStatusToggle = onStatusFilterToggle,
                            onClearAllFilters = onClearAllFilters,
                            onReorder = onReorderStatus,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2.4 共用底部"添加分组"按钮（两种模式都用）
                AddCategoryButton(text = "添加分组", onClick = onAddCategoryClick)
            }
            TabItem.INSPIRE -> {
                InspirationFilterSection(
                    orderedTags = inspirationTags,
                    selectedTags = selectedTags,
                    filterMode = tagFilterMode,
                    tagCounts = tagCounts,
                    totalInspirationCount = totalInspirationCount,
                    onTagClick = onTagClick,
                    onFilterModeChange = onTagFilterModeChange,
                    onClearTagSelection = onClearTagSelection,
                    // v2026-07-27 P8 Phase 4：透传拖拽回调，委托外层 ViewModel 持久化
                    onReorder = onReorderInspirationTag,
                    modifier = Modifier.weight(1f)
                )
                AddCategoryButton(text = "添加标签", onClick = onAddTagClick)
            }
            TabItem.DATE -> {
                DateTypeFilterSection(
                    selectedDateCategory = selectedDateCategory,
                    dateCountByCategory = dateCountByCategory,
                    // 🆕 v2026-07-28 P8.6：传入统一日期类型列表
                    dateTypeOrder = dateTypeOrder,
                    onDateCategoryClick = onDateCategoryClick,
                    onCustomTypeAction = onCustomTypeAction,
                    // v2026-07-28 P8.6：透传统一 List<DateTypeEntry> 拖拽回调
                    onReorder = onReorderDateType,
                    modifier = Modifier.weight(1f)
                )
                AddCategoryButton(text = "添加类型", onClick = onAddCustomTypeClick)
            }
            TabItem.PROFILE -> {
                ProfileQuickNavSection(
                    // v2026-07-27 P8 Phase 5：透传数据驱动的 nav item 列表 + 拖拽回调
                    navItems = navItems,
                    onReorder = onReorderNav,
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

/**
 * 🆕 v2026-07-28 v2 跨维度：全局 FilterChip Row（OR/AND/NOT）
 *
 * 位置：TODO Tab 下 DrawerSectionTab 下方，跨分组+状态 2 个分区共享 1 个 mode。
 *
 * 视觉：参考 [com.corgimemo.app.ui.components.appdrawer.sections.InspirationFilterSection] 的
 * OR/AND/NOT chip 样式（FilterChip + UiColors.PrimaryLight 选中色 + 12sp 文字）。
 *
 * 业务规则：点击 AND chip 时，由外层 HomeViewModel 自动清空分组项
 * （applyFilterItems 在 AND 模式下分组项被忽略），避免"看起来已选但无效"。
 * 这里只负责切换 mode，清空逻辑在 HomeViewModel 中实现。
 *
 * @param mode 当前选中的过滤模式
 * @param onModeChange 切换 mode 回调
 * @param modifier 外部 Modifier
 */
@Composable
private fun FilterModeChipRow(
    mode: TagFilterMode,
    onModeChange: (TagFilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = mode == TagFilterMode.OR,
            onClick = { onModeChange(TagFilterMode.OR) },
            label = { Text("OR", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = UiColors.PrimaryLight,
                selectedLabelColor = UiColors.Primary
            )
        )
        FilterChip(
            selected = mode == TagFilterMode.AND,
            onClick = { onModeChange(TagFilterMode.AND) },
            label = { Text("AND", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = UiColors.PrimaryLight,
                selectedLabelColor = UiColors.Primary
            )
        )
        FilterChip(
            selected = mode == TagFilterMode.NOT,
            onClick = { onModeChange(TagFilterMode.NOT) },
            label = { Text("NOT", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = UiColors.PrimaryLight,
                selectedLabelColor = UiColors.Primary
            )
        )
    }
}
