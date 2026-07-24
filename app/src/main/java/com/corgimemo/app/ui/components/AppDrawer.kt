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
//   1) MainScreen.kt 已 `import com.corgimemo.app.ui.components.AppDrawerContent`
//      和 `import com.corgimemo.app.ui.components.{CategoryAction,DateTypeAction}`，
//      迁移子包会导致所有 import 路径变化，影响面巨大；
//   2) 通过 typealias + 函数转发，MainScreen 0 改动；
//   3) 后续可逐步把调用方迁到新路径，最后删除本文件。
//
// 外部调用方应继续使用本文件暴露的 API：
//   - AppDrawerContent(...)
//   - AddCategoryDialog(...)
//   - RenameCategoryDialog(...)
//   - DeleteCategoryConfirmDialog(...)
//   - CategoryOperationSheet(...)
//   - DateTypeOperationSheet(...)
//   - typealias CategoryAction / DateTypeAction
//
// 不要直接 import `com.corgimemo.app.ui.components.appdrawer.*`（内部实现细节）。
// =================================================================================

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction as CategoryActionImpl
import com.corgimemo.app.ui.components.appdrawer.model.DateTypeAction as DateTypeActionImpl
import com.corgimemo.app.ui.components.appdrawer.sections.AppDrawerContentImpl
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.viewmodel.TagFilterMode

// ==================== sealed class typealias ====================
//
// Kotlin typealias 适用于任何类型（含 sealed class），编译期展开为原类型。
// MainScreen 的 `is CategoryAction.ShowMenu` pattern match 编译后等价于
// `is com.corgimemo.app.ui.components.appdrawer.model.CategoryAction.ShowMenu`，完全兼容。

/** 分类操作动作（薄壳 typealias），真实定义见 model/CategoryAction.kt */
typealias CategoryAction = CategoryActionImpl

/** 日期类型操作动作（薄壳 typealias），真实定义见 model/DateTypeAction.kt */
typealias DateTypeAction = DateTypeActionImpl

// ==================== 主入口转发 ====================

/**
 * 侧滑导航栏内容组件（薄壳转发）
 *
 * 真实实现见 [AppDrawerContentImpl]。本函数仅透传所有 24 个参数，
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
    AppDrawerContentImpl(
        currentTab = currentTab,
        corgiData = corgiData,
        categories = categories,
        todoCountByCategory = todoCountByCategory,
        selectedCategoryId = selectedCategoryId,
        inspirationTags = inspirationTags,
        selectedTags = selectedTags,
        tagFilterMode = tagFilterMode,
        tagCounts = tagCounts,
        totalInspirationCount = totalInspirationCount,
        selectedDateCategory = selectedDateCategory,
        dateCountByCategory = dateCountByCategory,
        customDateTypes = customDateTypes,
        onCategoryClick = onCategoryClick,
        onAddCategoryClick = onAddCategoryClick,
        onCategoryAction = onCategoryAction,
        onTagClick = onTagClick,
        onTagFilterModeChange = onTagFilterModeChange,
        onClearTagSelection = onClearTagSelection,
        onAddTagClick = onAddTagClick,
        onDateCategoryClick = onDateCategoryClick,
        onAddCustomTypeClick = onAddCustomTypeClick,
        onCustomTypeAction = onCustomTypeAction,
        onSettingsClick = onSettingsClick,
        onUserAreaClick = onUserAreaClick,
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
 * @see com.corgimemo.app.ui.components.appdrawer.dialogs.DeleteCategoryConfirmDialog
 */
@Composable
fun DeleteCategoryConfirmDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "删除分组",
    message: String = "确定要删除分组「$categoryName」吗？\n该分组下的待办将变为未分类状态。"
) {
    com.corgimemo.app.ui.components.appdrawer.dialogs.DeleteCategoryConfirmDialog(
        categoryName = categoryName,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        title = title,
        message = message
    )
}

/**
 * 分类长按操作菜单（薄壳转发）
 *
 * @param sheetState BottomSheet 状态（必须由调用方在 Composable 作用域创建，**不可给默认值**，
 *                   因为 `rememberModalBottomSheetState()` 是 Composable 函数）
 * @see com.corgimemo.app.ui.components.appdrawer.dialogs.CategoryOperationSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOperationSheet(
    sheetState: SheetState,
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
 * @param sheetState BottomSheet 状态（必须由调用方在 Composable 作用域创建，**不可给默认值**）
 * @see com.corgimemo.app.ui.components.appdrawer.dialogs.DateTypeOperationSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTypeOperationSheet(
    sheetState: SheetState,
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
