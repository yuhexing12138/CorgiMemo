package com.corgimemo.app.kuikly

/**
 * Kuikly 桥接单例（宿主原生侧 ↔ Kuikly 原生 Module 的中间人）
 *
 * 由主工程在合适的 UI 作用域注入回调（如 [com.corgimemo.app.ui.screens.main.MainScreen]
 * 中接线到 HomeViewModel.setTodoStatus）。Kuikly 页经 CorgiBridgeModule → KRCorgiBridgeModule
 * 调用 [onSetTodoStatus] 时，即把操作回写到主工程数据层。
 *
 * 设计为可空回调 + 单例，避免 Kuikly 框架持有着 ViewModel 引用导致的内存泄漏；
 * 宿主在组合作用域内设置、离开时按需置空即可。
 */
object KuiklyBridge {

    /**
     * 标记待办完成 / 取消完成
     * @param todoId 待办 ID
     * @param status 目标状态：1 = 已完成，0 = 未完成
     */
    var onSetTodoStatus: ((Long, Int) -> Unit)? = null
}
