package com.corgimemo.kuikly

import com.tencent.kuikly.core.module.Module

/**
 * Kuikly 侧桥接 Module
 *
 * 暴露给 Kuikly 页面调用，通过 [toNative] 触发宿主原生能力。
 * 与宿主侧 [com.corgimemo.app.kuikly.KRCorgiBridgeModule]（继承 KuiklyRenderBaseModule）
 * 通过 [moduleName] 一一对应。
 *
 * 数据流：Kuikly 页（如 TodoDetailPage）→ CorgiBridgeModule.setTodoStatus → toNative →
 * 宿主 KRCorgiBridgeModule.call("setTodoStatus") → KuiklyBridge.onSetTodoStatus → 主工程 ViewModel 改 Room。
 */
class CorgiBridgeModule : Module() {

    override fun moduleName(): String = "KRCorgiBridgeModule"

    /**
     * 标记待办完成 / 取消完成
     *
     * @param todoId 待办 ID
     * @param status 目标状态：1 = 已完成，0 = 未完成
     */
    fun setTodoStatus(todoId: Long, status: Int) {
        toNative(
            keepCallbackAlive = false,
            methodName = "setTodoStatus",
            param = mapOf("todoId" to todoId, "status" to status),
            callback = null,
            syncCall = false
        )
    }
}
