package com.corgimemo.app.kuikly

import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONArray
import org.json.JSONObject

/**
 * 宿主侧统一桥接 Module（Kuikly 原生桥接落地，阶段二·桥体系化）
 *
 * 与 shared 侧 [com.corgimemo.kuikly.CorgiBridgeModule] 通过 moduleName "CorgiBridge" 对应。
 * Kuikly 页调用 [com.corgimemo.kuikly.CorgiBridgeModule] 的任一方法后，框架在本类的
 * [call] 中按 methodName 分发到 [KuiklyBridge] 注册的对应处理器。
 *
 * 事件清单（methodName → 处理器）：
 * - setStatus       → KuiklyBridge.onSetStatus(todoId, status)
 * - toggleComplete  → KuiklyBridge.onToggleComplete(todoId)
 * - togglePin       → KuiklyBridge.onTogglePin(todoId)
 * - delete          → KuiklyBridge.onDelete(todoId)
 * - update          → KuiklyBridge.onUpdate(map)
 * - loadTodos       → KuiklyBridge.todosProvider()（回调回传 JSON 列表）
 * - loadTodoDetail  → KuiklyBridge.todoProvider(todoId)（回调回传 JSON 单条）
 *
 * 读操作回传约定：宿主用 org.json 构造 JSONObject 并 toString() 交给 callback，
 * shared 侧 CallbackFn 会把 String 自动解析回 core JSONObject 供页面读取。
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
            "setStatus" -> handleSetStatus(params, callback)
            "toggleComplete" -> handleIdOnly(params) { id -> KuiklyBridge.onToggleComplete?.invoke(id) }
            "togglePin" -> handleIdOnly(params) { id -> KuiklyBridge.onTogglePin?.invoke(id) }
            "delete" -> handleIdOnly(params) { id -> KuiklyBridge.onDelete?.invoke(id) }
            "update" -> handleUpdate(params)
            "loadTodos" -> handleLoadTodos(callback)
            "loadTodoDetail" -> handleLoadTodoDetail(params, callback)
            else -> super.call(method, params, callback)
        }
    }

    // 兼容框架可能以 String 重载传入（JSON 字符串）的情况
    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return call(method, params as Any?, callback)
    }

    // ---------------- 解析工具 ----------------

    /** 从 Map 或 JSON String 中取出 todoId（缺失/非法返回 null） */
    private fun parseTodoId(params: Any?): Long? = when (params) {
        is Map<*, *> -> (params["todoId"] as? Number)?.toLong()
        is String -> {
            val id = runCatching { JSONObject(params) }.getOrNull()?.optLong("todoId", 0L) ?: 0L
            id.takeIf { it != 0L }
        }
        else -> null
    }

    /**
     * 解析 setStatus 的 todoId + status，调用 action 后经 callback 回传空结果。
     * 兼容 Map 与 JSON String 两种入参形态。
     */
    private fun handleSetStatus(params: Any?, callback: KuiklyRenderCallback?): Any? {
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
        KuiklyBridge.onSetStatus?.invoke(todoId, status)
        callback?.invoke(emptyMap<String, Any>())
        return null
    }

    /** 仅含 todoId 的事件（toggleComplete/togglePin/delete）统一解析 */
    private fun handleIdOnly(params: Any?, action: (Long) -> Unit): Any? {
        val id = parseTodoId(params) ?: return null
        action(id)
        return null
    }

    /** 整条更新：把入参规整为 String 键的 Map 交给 KuiklyBridge.onUpdate */
    private fun handleUpdate(params: Any?): Any? {
        val map: Map<String, Any?> = when (params) {
            is Map<*, *> -> params.mapKeys { it.key.toString() }
            is String -> runCatching { jsonToMap(JSONObject(params)) }.getOrNull() ?: emptyMap()
            else -> emptyMap()
        }
        KuiklyBridge.onUpdate?.invoke(map)
        return null
    }

    /**
     * 将 Android [org.json.JSONObject] 转成 [Map<String, Any?>]。
     * 注意：宿主侧用的是 Android 自带 org.json，没有 Kuikly core JSONObject 的 toMap()，
     * 故手动遍历 keys 构造 Map（嵌套 JSONObject/JSONArray 以原类型保留）。
     */
    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            result[key] = json.opt(key)
        }
        return result
    }

    /** loadTodos：从 provider 取列表，序列化为 {"list":[...]} JSON 字符串回传 */
    private fun handleLoadTodos(callback: KuiklyRenderCallback?): Any? {
        val list = KuiklyBridge.todosProvider?.invoke() ?: emptyList()
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject(it)) }
        val root = JSONObject().apply { put("list", arr) }
        callback?.invoke(root.toString())
        return null
    }

    /** loadTodoDetail：从 provider 取单条，序列化为 {"item":{...}} JSON 字符串回传 */
    private fun handleLoadTodoDetail(params: Any?, callback: KuiklyRenderCallback?): Any? {
        val id = parseTodoId(params) ?: return null
        val item = KuiklyBridge.todoProvider?.invoke(id) ?: return null
        val root = JSONObject().apply { put("item", JSONObject(item)) }
        callback?.invoke(root.toString())
        return null
    }
}
