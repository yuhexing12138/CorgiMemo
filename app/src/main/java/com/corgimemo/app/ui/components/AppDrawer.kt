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
import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction as CategoryActionImpl
import com.corgimemo.app.ui.components.appdrawer.model.DateTypeAction as DateTypeActionImpl
import com.corgimemo.app.ui.components.appdrawer.sections.AppDrawerContentImpl
import com.corgimemo.app.ui.components.navigation.TabItem
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
