package com.corgimemo.app.ui.components.appdrawer.model

import com.corgimemo.app.data.model.Category

/**
 * 分类操作动作密封类（侧边栏专用）
 *
 * 用于在 CategoryGroupSection 与 MainScreen 之间传递分组操作意图。
 *
 * v2026-07-29 改造：移除 `ShowMenu` 类型（底部弹窗已删除）
 * - 原 `ShowMenu` 长按分类 → 弹出 CategoryOperationSheet 操作面板
 * - 现改为右滑展开操作按钮（SwipeableCategoryBox），不再需要 ShowMenu 动作
 *
 * 现保留 3 种操作（由 SwipeableCategoryBox 的右滑按钮触发）：
 * - `Pin` 置顶/取消置顶分组（右滑"置顶"按钮触发）
 * - `Rename` 重命名分组 → 弹出 RenameCategoryDialog（右滑"编辑"按钮触发）
 * - `Delete` 删除分组 → 弹出 DeleteCategoryConfirmDialog（右滑"删除"按钮触发）
 *
 * v2026-07-29 改造：取消"默认/自定义分组"区分后，所有分组（含 5 个种子分类）
 * 都可触发上述 3 种操作。
 *
 * 外部访问方式：通过 `com.corgimemo.app.ui.components.CategoryAction` typealias
 * 保持 MainScreen 等调用方的 import 路径不变。
 *
 * @see com.corgimemo.app.ui.components.AppDrawer
 */
sealed class CategoryAction {
    data class Pin(val category: Category) : CategoryAction()
    data class Rename(val category: Category) : CategoryAction()
    data class Delete(val category: Category) : CategoryAction()
}
