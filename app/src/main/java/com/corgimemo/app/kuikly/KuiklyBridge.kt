package com.corgimemo.app.kuikly

/**
 * Kuikly 桥接单例（宿主原生侧 ↔ Kuikly 原生 Module 的中间人，阶段二·桥体系化）
 *
 * 由主工程在合适的 UI 作用域注入回调（如 [com.corgimemo.app.ui.screens.main.MainScreen]
 * 中接线到 HomeViewModel 的各方法）。Kuikly 页经 [com.corgimemo.kuikly.CorgiBridgeModule]
 * → [KRCorgiBridgeModule] 调用对应事件时，即把操作回写到主工程数据层，或经 provider 拉取数据。
 *
 * 设计为可空回调 + 单例，避免 Kuikly 框架持有着 ViewModel 引用导致的内存泄漏；
 * 宿主在组合作用域内设置、离开时按需置空即可。
 */
object KuiklyBridge {

    // ==================== 写操作处理器 ====================

    /** 设定待办完成状态：todoId → 目标状态（1=已完成 0=未完成） */
    var onSetStatus: ((Long, Int) -> Unit)? = null

    /** 切换待办完成状态（取反） */
    var onToggleComplete: ((Long) -> Unit)? = null

    /** 切换置顶 */
    var onTogglePin: ((Long) -> Unit)? = null

    /** 删除待办 */
    var onDelete: ((Long) -> Unit)? = null

    /** 整条更新（标题/内容等），参数为规整后的 String 键 Map（至少含 todoId） */
    var onUpdate: ((Map<String, Any?>) -> Unit)? = null

    /**
     * 关闭当前 Kuikly 承载页（回到主工程）。
     *
     * 由 [com.corgimemo.app.kuikly.KuiklyRenderActivity] 在 onCreate 注册为 `finish()`，
     * 用于删除等终态操作后页面已无意义的场景。注意本回调由 Activity 自己注册与清理，
     * 不走 MainScreen 的统一接线。
     */
    var onClosePage: (() -> Unit)? = null

    // ==================== 读操作数据提供方 ====================

    /** 拉取待办列表（当前过滤结果），返回桥接用的 Map 列表 */
    var todosProvider: (() -> List<Map<String, Any?>>)? = null

    /** 按 ID 拉取单条待办详情，返回桥接用的 Map（查不到返回空 Map） */
    var todoProvider: ((Long) -> Map<String, Any?>) ? = null
}
