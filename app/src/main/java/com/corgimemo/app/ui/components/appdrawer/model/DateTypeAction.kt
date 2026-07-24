package com.corgimemo.app.ui.components.appdrawer.model

import com.corgimemo.app.data.model.CustomDateType

/**
 * 自定义日期类型操作动作密封类（侧边栏专用）
 *
 * 用于在 DateTypeFilterSection 与 MainScreen 之间传递日期类型操作意图。
 * - `ShowMenu` 长按自定义类型 → 弹出 DateTypeOperationSheet 操作面板
 * - `Rename` 重命名自定义类型 → 弹出 RenameCategoryDialog 复用
 * - `Delete` 删除自定义类型
 *
 * 外部访问方式：通过 `com.corgimemo.app.ui.components.DateTypeAction` typealias
 * 保持 MainScreen 等调用方的 import 路径不变。
 *
 * 注意：自定义类型**不支持**置顶（与 Category 不同）。
 *
 * @see com.corgimemo.app.ui.components.AppDrawer
 */
sealed class DateTypeAction {
    data class ShowMenu(val customType: CustomDateType) : DateTypeAction()
    data class Rename(val customType: CustomDateType) : DateTypeAction()
    data class Delete(val customType: CustomDateType) : DateTypeAction()
}
