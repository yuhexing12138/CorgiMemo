package com.corgimemo.app.ui.components.appdrawer.model

/**
 * 侧滑栏分区类型（v2026-07-27 新增）
 *
 * 用于切换"分组管理"和"状态管理"两个互斥的分区。
 * 仅在 TODO Tab 下有意义，其他 Tab（INSPIRE / DATE / PROFILE）继续按原分区渲染。
 *
 * **设计背景**（参见 .trae/documents/侧滑栏添加状态管理切换功能实施计划.md）：
 * - 用户需求：在侧滑栏"分组管理"右侧增加"状态管理"标题，二者互斥切换
 * - 互斥选择：点击其中一个自动取消其他选中
 * - 视觉：Tab 风格（激活态加粗 + 主题色 + 橙色横线指示器）
 *
 * **可见性**：`public`（enum 顶层类型，跨包消费方需 import）
 */
enum class DrawerSection {
    /** 分组管理（默认）— 显示自定义分类列表 */
    GROUP,

    /** 状态管理 — 显示状态过滤项（置顶/待完成/已完成/已过期/重复提醒） */
    STATUS
}
