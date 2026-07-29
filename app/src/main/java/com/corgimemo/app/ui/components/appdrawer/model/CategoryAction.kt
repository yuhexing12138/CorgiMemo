package com.corgimemo.app.ui.components.appdrawer.model

import com.corgimemo.app.data.model.Category

/**
 * 分类操作动作密封类（侧边栏专用）
 *
 * 用于在 CategoryGroupSection 与 MainScreen 之间传递分组操作意图。
 * - `ShowMenu` 长按分类 → 弹出 CategoryOperationSheet 操作面板
 * - `Pin` 置顶/取消置顶分组（v2026-07-29 起已实现，调用方为 MainScreen 的 onPin 回调）
 * - `Rename` 重命名分组 → 弹出 RenameCategoryDialog
 * - `Delete` 删除分组 → 弹出 DeleteCategoryConfirmDialog
 *
 * v2026-07-29 改造：取消"默认/自定义分组"区分后，所有分组（含 5 个种子分类）
 * 都可触发上述 4 种操作。CategoryGroupSection 中 `showMenu = true` 不再按 isDefault 过滤。
 *
 * 外部访问方式：通过 `com.corgimemo.app.ui.components.CategoryAction` typealias
 * 保持 MainScreen 等调用方的 import 路径不变。
 *
 * @see com.corgimemo.app.ui.components.AppDrawer
 */
sealed class CategoryAction {
    data class ShowMenu(val category: Category) : CategoryAction()
    data class Pin(val category: Category) : CategoryAction()
    data class Rename(val category: Category) : CategoryAction()
    data class Delete(val category: Category) : CategoryAction()
}
