package com.corgimemo.app.kuikly

import android.content.Context
import com.corgimemo.app.data.model.TodoItem
import org.json.JSONObject

/**
 * Kuikly 页面入口统一封装（宿主侧）
 *
 * 集中维护「页面名 + pageData 组装 + 启动承载页」的映射，避免在多个入口
 * （顶栏三点菜单、待办卡片按钮等）重复拼接 JSON 导致字段遗漏或不一致。
 *
 * 页面侧读取方式（shared）：`pageData.params.optXxx("key")`，
 * 宿主把 JSONObject 平铺为顶层 key 后传入（见 [KuiklyRenderActivity.createPageData]）。
 */
object KuiklyEntry {

    /** 待办详情页的页面名，与 shared 侧 `@Page("todoDetail")` 一致 */
    const val PAGE_TODO_DETAIL = "todoDetail"

    /**
     * 打开指定待办的 Kuikly 详情页
     *
     * @param context 上下文
     * @param todo 目标待办，其字段会被平铺为页面入参
     */
    fun openTodoDetail(context: Context, todo: TodoItem) {
        KuiklyRenderActivity.start(context, PAGE_TODO_DETAIL, buildTodoDetailData(todo))
    }

    /**
     * 组装待办详情页的 pageData
     *
     * **关键点**：本方法返回**扁平**结构（即字段直接挂在顶层），
     * **不要**在这里套 `param` 嵌套层。
     *
     * 原因：KUIKLY 内部的 `KuiklyRenderView.generateWithParams(Map)` 会把用户传入的
     * 整个 pageData Map 作为 value 放进 `result["param"]` 这个 key 下
     * （参见 `core-render-android-2.26.0` 字节码 line 332-336）。也就是说，
     * Kuikly 自己**已经**在 pageData 里加了 "param" 这一层。
     *
     * 页面侧 `pageData.params.optString("title", "")` 的访问路径其实是：
     *   `rawPageData.optJSONObject("param")` → 用户 Map 转成的 JSONObject → `.optString("title")`
     *
     * 所以用户应该传扁平字段。如果再嵌套一层，字段会落到
     * `rawPageData.param.param.title` 这种位置，KUIKLY 找不到。
     *
     * 字段与 shared 侧 `TodoDetailPage` 的读取保持一致；`content` / `dueDate` 为可空字段，
     * 空值统一兜底为 "" / 0，避免页面侧 optString / optLong 取到异常值。
     */
    private fun buildTodoDetailData(todo: TodoItem): JSONObject = JSONObject().apply {
        put("todoId", todo.id)
        put("title", todo.title)
        put("content", todo.content ?: "")
        put("dueDate", todo.dueDate ?: 0L)
        put("status", todo.status)
        put("isPinned", todo.isPinned)
    }
}
