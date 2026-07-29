package com.corgimemo.app.ui.components

// ==================== 薄壳层（侧边栏 AppDrawer 拆分后保留） ====================
//
// 本文件是 AppDrawer.kt 拆分后的"对外兼容层"。
// 真实实现已迁移至 `com.corgimemo.app.ui.components.appdrawer.*` 子包：
//   - model/      — 2 个 sealed class
//   - sections/   — 5 个分区 + 主入口 AppDrawerContentImpl
//   - dialogs/    — 3 个弹窗 + 1 个 OperationSheets（2 个 BottomSheet）
//
// 保留本薄壳的原因：
//   1) MainScreen.kt 已 `import com.corgimemo.app.ui.components.AppDrawerContent`，
//      迁移子包会导致 Composable 函数调用路径变化，影响面巨大；
//   2) 通过函数转发，MainScreen 调用 Composable 函数 0 改动；
//   3) sealed class 通过 typealias 暴露别名（**仅用于类型签名**），
//      但 sealed class 子类的作用域**不能**通过 typealias 访问
//      （Kotlin 编译器限制：typealias 不传递子类可见性）。
//      MainScreen 的 `is CategoryAction.ShowMenu` pattern match 需要**直接**
//      import 真实路径 `com.corgimemo.app.ui.components.appdrawer.model.CategoryAction`
//      才能解析子类型。
//   4) 后续可逐步把调用方迁到新路径，最后删除本文件。
//
// 外部调用方应继续使用本文件暴露的 API：
//   - AppDrawerContent(...)
//   - AddCategoryDialog(...)
//   - RenameCategoryDialog(...)
//   - DeleteCategoryConfirmDialog(...)
//   - CategoryOperationSheet(...)
//   - DateTypeOperationSheet(...)
//   - typealias CategoryAction / DateTypeAction（**仅作类型签名**，子类需直接 import 真实路径）
//
// 不要直接 import `com.corgimemo.app.ui.components.appdrawer.*`（内部实现细节）。
// =================================================================================

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.data.model.ProfileNavItem
import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction as CategoryActionImpl
import com.corgimemo.app.ui.components.appdrawer.model.DateTypeAction as DateTypeActionImpl
import com.corgimemo.app.ui.components.appdrawer.model.DrawerSection
import com.corgimemo.app.ui.components.appdrawer.sections.AppDrawerContentImpl
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.viewmodel.FilterItem
import com.corgimemo.app.viewmodel.SpecialDateViewModel
import com.corgimemo.app.viewmodel.StatusFilter
import com.corgimemo.app.viewmodel.TagFilterMode

// ==================== sealed class typealias ====================
//
// **重要**：Kotlin typealias 对 sealed class 子类的作用域解析**不生效**。
//
// typealias 仅在类型签名层面（如 `(CategoryAction) -> Unit` 参数类型）替换为原类型。
// 但当使用 `CategoryAction.ShowMenu` 这种**嵌套类型访问**语法时，
// 编译器需要在 sealed class 的可见作用域内查找 `ShowMenu` 子类，
// typealias 不传递子类的可见性，导致编译错误：
//   - 'when' expression must be exhaustive
//   - Unresolved reference 'ShowMenu'
//
// **解决方案**：调用方需要直接 import 真实 sealed class 路径：
//   import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction
//   import com.corgimemo.app.ui.components.appdrawer.model.DateTypeAction
// 这样 when 表达式 `is CategoryAction.ShowMenu` 才能正确解析。
//
// 详细说明见：.trae/rules/巨石组件拆分规范.md §3 薄壳层规则

/** 分类操作动作（薄壳 typealias，**仅作类型签名**），真实定义见 model/CategoryAction.kt */
typealias CategoryAction = CategoryActionImpl

/** 日期类型操作动作（薄壳 typealias，**仅作类型签名**），真实定义见 model/DateTypeAction.kt */
typealias DateTypeAction = DateTypeActionImpl

// ==================== 主入口转发 ====================

/**
 * 侧滑导航栏内容组件（薄壳转发）
 *
 * 真实实现见 [AppDrawerContentImpl]。本函数仅透传所有 35 个参数（v2026-07-27 从 33 增加到 35），
 * 目的是让 MainScreen 的 import 路径（`com.corgimemo.app.ui.components.AppDrawerContent`）不变。
 *
 * @see com.corgimemo.app.ui.components.appdrawer.sections.AppDrawerContentImpl
 */
@Composable
fun AppDrawerContent(
    currentTab: TabItem,
    corgiData: CorgiData?,
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    // ★ v2026-07-28 v2 跨维度：删除 selectedCategoryId，新增 selectedFilterItems + filterMode
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
    // v2026-07-27 P8 Phase 1 新增：分类拖拽排序回调
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
    // v2026-07-27 P8 Phase 5 新增：个人快速导航项 + 拖拽回调（透传至 AppDrawerContentImpl）
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
    AppDrawerContentImpl(
        currentTab = currentTab,
        corgiData = corgiData,
        categories = categories,
        todoCountByCategory = todoCountByCategory,
        // ★ v2026-07-28 v2 跨维度：透传新参数
        selectedFilterItems = selectedFilterItems,
        filterMode = filterMode,
        inspirationTags = inspirationTags,
        selectedTags = selectedTags,
        tagFilterMode = tagFilterMode,
        tagCounts = tagCounts,
        totalInspirationCount = totalInspirationCount,
        selectedDateCategory = selectedDateCategory,
        dateCountByCategory = dateCountByCategory,
        // 🆕 v2026-07-28 P8.6：透传统一日期类型列表
        dateTypeOrder = dateTypeOrder,
        // ★ v2026-07-28 v2 跨维度：透传 onCategoryToggle
        onCategoryToggle = onCategoryToggle,
        onAddCategoryClick = onAddCategoryClick,
        onCategoryAction = onCategoryAction,
        // v2026-07-27 P8 Phase 1：透传分类拖拽回调
        onReorderCategory = onReorderCategory,
        // ★ v2026-07-28 v2 跨维度：透传 onStatusFilterToggle / onFilterModeChange / onClearAllFilters
        onStatusFilterToggle = onStatusFilterToggle,
        onFilterModeChange = onFilterModeChange,
        onClearAllFilters = onClearAllFilters,
        onTagClick = onTagClick,
        onTagFilterModeChange = onTagFilterModeChange,
        onClearTagSelection = onClearTagSelection,
        onAddTagClick = onAddTagClick,
        // v2026-07-27 P8 Phase 4：透传灵感标签拖拽回调
        onReorderInspirationTag = onReorderInspirationTag,
        onDateCategoryClick = onDateCategoryClick,
        onAddCustomTypeClick = onAddCustomTypeClick,
        onCustomTypeAction = onCustomTypeAction,
        // 🆕 v2026-07-28 P8.6：透传统一 List<DateTypeEntry> 拖拽回调
        onReorderDateType = onReorderDateType,
        onSettingsClick = onSettingsClick,
        onUserAreaClick = onUserAreaClick,
        // v2026-07-27 P8 Phase 5：透传个人快速导航项 + 拖拽回调
        navItems = navItems,
        onReorderNav = onReorderNav,
        // v2026-07-27 新增：状态管理 Tab 透传
        currentDrawerSection = currentDrawerSection,
        onDrawerSectionChange = onDrawerSectionChange,
        // v2026-07-27 P8 Phase 2：透传状态过滤项拖拽顺序 + 回调
        statusOrder = statusOrder,
        onReorderStatus = onReorderStatus,
        totalTodoCount = totalTodoCount,
        pinnedCount = pinnedCount,
        pendingCount = pendingCount,
        completedCount = completedCount,
        overdueCount = overdueCount,
        repeatReminderCount = repeatReminderCount,
        modifier = modifier
    )
}

// ==================== 弹窗薄壳（保留原始签名） ====================
//
// 注意：薄壳内部转发一律使用 FQN（不 import 同名函数），避免与本文件的同名顶层函数冲突。

/**
 * 添加/编辑分组弹窗（薄壳转发）
 *
 * @see com.corgimemo.app.ui.components.appdrawer.dialogs.AddCategoryDialog
 */
@Composable
fun AddCategoryDialog(
    onConfirmName: (String) -> Unit = {},
    onConfirm: (String, String) -> Unit = { name, _ -> onConfirmName(name) },
    onDismiss: () -> Unit,
    title: String = "新建分组",
    label: String = "分组名称",
    showEmojiPicker: Boolean = false
) {
    com.corgimemo.app.ui.components.appdrawer.dialogs.AddCategoryDialog(
        onConfirmName = onConfirmName,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        title = title,
        label = label,
        showEmojiPicker = showEmojiPicker
    )
}

/**
 * 重命名分组弹窗（薄壳转发）
 *
 * @see com.corgimemo.app.ui.components.appdrawer.dialogs.RenameCategoryDialog
 */
@Composable
fun RenameCategoryDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    com.corgimemo.app.ui.components.appdrawer.dialogs.RenameCategoryDialog(
        currentName = currentName,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * 删除分类/类型确认弹窗（薄壳转发）
 *
 * v2026-07-29 改造：同步底层新增 `todoCount` 参数；
 * `message` 改为 `String?`（可空，默认 null → 由底层按 todoCount 自动生成待办分组文案）。
 *
 * @see com.corgimemo.app.ui.components.appdrawer.dialogs.DeleteCategoryConfirmDialog
 */
@Composable
fun DeleteCategoryConfirmDialog(
    categoryName: String,
    todoCount: Int = 0,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "删除分组",
    message: String? = null
) {
    com.corgimemo.app.ui.components.appdrawer.dialogs.DeleteCategoryConfirmDialog(
        categoryName = categoryName,
        todoCount = todoCount,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        title = title,
        message = message
    )
}

/**
 * 分类长按操作菜单（薄壳转发）
 *
 * @param sheetState BottomSheet 状态（默认 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`，
 *                   因本函数是 `@Composable`，Composable 函数可作为参数默认值）
 * @see com.corgimemo.app.ui.components.appdrawer.dialogs.CategoryOperationSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOperationSheet(
    sheetState: SheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    category: Category,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    com.corgimemo.app.ui.components.appdrawer.dialogs.CategoryOperationSheet(
        sheetState = sheetState,
        category = category,
        onPin = onPin,
        onRename = onRename,
        onDelete = onDelete,
        onDismiss = onDismiss
    )
}

/**
 * 自定义日期类型长按操作菜单（薄壳转发）
 *
 * @param sheetState BottomSheet 状态（默认 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`）
 * @see com.corgimemo.app.ui.components.appdrawer.dialogs.DateTypeOperationSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTypeOperationSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    customType: CustomDateType,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    com.corgimemo.app.ui.components.appdrawer.dialogs.DateTypeOperationSheet(
        sheetState = sheetState,
        customType = customType,
        onRename = onRename,
        onDelete = onDelete,
        onDismiss = onDismiss
    )
}
