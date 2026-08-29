package com.corgimemo.kuikly

import com.tencent.kuikly.core.module.Module

/**
 * 统一 Kuikly↔原生桥接 Module（阶段二·桥体系化）
 *
 * 作为 Kuikly 页面调用原生能力的唯一入口，按事件名经 [toNative] 派发到宿主侧
 * [com.corgimemo.app.kuikly.KRCorgiBridgeModule]（双方 moduleName 均为 "CorgiBridge"）。
 *
 * 写操作（无返回，fire-and-forget）：
 * - [setStatus]       设定完成状态（1=已完成 0=未完成）
 * - [toggleComplete]  切换完成
 * - [togglePin]       切换置顶
 * - [delete]          删除待办
 * - [update]          整条更新（标题/内容等，Map 形式）
 *
 * 读操作（带回调，宿主经 JSON 字符串回传，shared 侧自动解析为 Map）：
 * - [loadTodos]       拉取待办列表
 * - [loadTodoDetail]  拉取单条详情
 *
 * 设计要点：读操作回传走 String(JSON) 往返，规避 core 内部回调类型限制——
 * 宿主用 org.json 构造 JSONObject 并 toString()，shared 侧 [com.tencent.kuikly.core.module.Module.toNative]
 * 的 CallbackFn 会把 String 自动解析回 core JSONObject，页面侧再 optJSONArray/optJSONObject/toMap 取出。
 */
class CorgiBridgeModule : Module() {

    override fun moduleName(): String = "CorgiBridge"

    // ==================== 写操作 ====================

    /**
     * 设定待办完成状态
     * @param todoId 待办 ID
     * @param status 目标状态：1 = 已完成，0 = 未完成
     */
    fun setStatus(todoId: Long, status: Int) {
        toNative(
            keepCallbackAlive = false,
            methodName = "setStatus",
            param = mapOf("todoId" to todoId, "status" to status),
            callback = null,
            syncCall = false
        )
    }

    /** 切换待办完成状态（取反） */
    fun toggleComplete(todoId: Long) {
        toNative(
            keepCallbackAlive = false,
            methodName = "toggleComplete",
            param = mapOf("todoId" to todoId),
            callback = null,
            syncCall = false
        )
    }

    /** 切换置顶 */
    fun togglePin(todoId: Long) {
        toNative(
            keepCallbackAlive = false,
            methodName = "togglePin",
            param = mapOf("todoId" to todoId),
            callback = null,
            syncCall = false
        )
    }

    /** 删除待办 */
    fun delete(todoId: Long) {
        toNative(
            keepCallbackAlive = false,
            methodName = "delete",
            param = mapOf("todoId" to todoId),
            callback = null,
            syncCall = false
        )
    }

    /**
     * 整条更新（标题/内容/分类等），字段以 Map 传递
     * @param todo 待办字段 Map，至少含 "todoId"；其余字段缺省表示不改
     */
    fun update(todo: Map<String, Any?>) {
        toNative(
            keepCallbackAlive = false,
            methodName = "update",
            param = todo,
            callback = null,
            syncCall = false
        )
    }

    // ==================== 读操作 ====================

    /**
     * 拉取待办列表
     * @param region 区域/分组过滤（-1 表示全部）；当前宿主忽略，返回当前过滤列表
     * @param callback 结果回调，回传 List<Map<String, Any?>>
     */
    fun loadTodos(region: Int = -1, callback: (List<Map<String, Any?>>) -> Unit) {
        toNative(
            keepCallbackAlive = false,
            methodName = "loadTodos",
            param = mapOf("region" to region),
            callback = { json ->
                val list = mutableListOf<Map<String, Any?>>()
                json?.optJSONArray("list")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.toMap()?.let { list.add(it) }
                    }
                }
                callback(list)
            },
            syncCall = false
        )
    }

    /**
     * 拉取单条待办详情
     * @param todoId 待办 ID
     * @param callback 结果回调，回传 Map<String, Any?>（查不到返回空 Map）
     */
    fun loadTodoDetail(todoId: Long, callback: (Map<String, Any?>) -> Unit) {
        toNative(
            keepCallbackAlive = false,
            methodName = "loadTodoDetail",
            param = mapOf("todoId" to todoId),
            callback = { json ->
                val item = json?.optJSONObject("item")?.toMap() ?: emptyMap<String, Any?>()
                callback(item)
            },
            syncCall = false
        )
    }
}
