package com.corgimemo.app.kuikly

import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject

/**
 * 宿主侧自定义 Module（Kuikly 原生桥接落地）
 *
 * 与 shared 侧 [com.corgimemo.kuikly.CorgiBridgeModule] 通过 moduleName "KRCorgiBridgeModule" 对应。
 * Kuikly 页调用 toNative 后，框架在本类的 [call] 中分发到具体原生能力。
 *
 * 数据流：Kuikly 页 → toNative(setTodoStatus) → 本 call → [KuiklyBridge.onSetTodoStatus]
 * → 主工程 ViewModel 改 Room。
 */
class KRCorgiBridgeModule : KuiklyRenderBaseModule() {

    /**
     * 统一入口，按 methodName 分发
     *
     * @param method  方法名（与 Kuikly 侧 toNative 的 methodName 一致）
     * @param params  参数：Kuikly 侧传 Map 时此处为 Map；序列化为 JSON 字符串时此处为 String
     * @param callback 结果回调（Kuikly 侧 toNative 的 callback），可回传结果
     */
    override fun call(method: String, params: Any?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "setTodoStatus" -> handleSetTodoStatus(params, callback)
            else -> super.call(method, params, callback)
        }
    }

    // 兼容框架可能以 String 重载传入（JSON 字符串）的情况
    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return call(method, params as Any?, callback)
    }

    private fun handleSetTodoStatus(params: Any?, callback: KuiklyRenderCallback?): Any? {
        val todoId: Long
        val status: Int
        when (params) {
            is Map<*, *> -> {
                todoId = (params["todoId"] as? Number)?.toLong() ?: return null
                status = (params["status"] as? Number)?.toInt() ?: return null
            }
            is String -> {
                val json = runCatching { JSONObject(params) }.getOrNull() ?: return null
                todoId = json.optLong("todoId", 0L)
                status = json.optInt("status", 0)
            }
            else -> return null
        }
        // 回调主工程注入的处理器（默认由 MainScreen 接线到 homeViewModel.setTodoStatus）
        KuiklyBridge.onSetTodoStatus?.invoke(todoId, status)
        // 回传结果给 Kuikly 侧（如页面需要据此刷新文案）
        callback?.invoke(emptyMap<String, Any>())
        return null
    }
}
