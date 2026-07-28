package com.corgimemo.app.viewmodel

/**
 * 待办侧滑栏统一过滤项（v2026-07-28 P8 跨维度改造新增）
 *
 * 背景：原 [_selectedCategoryId] 和 [_statusFilter] 是 2 个独立单选状态，
 * 分组和状态之间无法混选。v2 设计把两者统一为 [FilterItem] 集合，
 * 配合 1 个 [_filterMode] 实现跨维度多选 + OR/AND/NOT 组合。
 *
 * **为什么用 sealed class？**
 * - 类型安全：编译期保证只能添加 Category 和 Status 两类
 * - pattern match：`when (item) { is FilterItem.Category -> ... }` 无需 else 分支
 * - 易于扩展：未来可加 FilterItem.Date 等其他维度
 *
 * **AND 模式边界**：
 * 分组项（[FilterItem.Category]）在 AND 模式下被忽略
 * （todo 只能属于 1 个分组，多个分组 AND 退化为空集）。
 * 状态项（[FilterItem.Status]）在所有模式下都生效。
 *
 * @see com.corgimemo.app.viewmodel.HomeViewModel.applyFilterItems
 */
sealed class FilterItem {
    /**
     * 分组过滤项
     *
     * @param id 0L=未分类（与 todo.categoryId==0L 对齐），>0L=具体自定义分组 ID
     */
    data class Category(val id: Long) : FilterItem()

    /**
     * 状态过滤项
     *
     * 注意：[StatusFilter.ALL] 不应作为多选元素（选中 ALL = 全部 = 不过滤）。
     * UI 层在生成 [FilterItem.Status] 列表时跳过 ALL。
     */
    data class Status(val filter: StatusFilter) : FilterItem()
}
