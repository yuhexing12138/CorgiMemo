package com.corgimemo.app.kuikly

import android.content.Context
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.SubTaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
     * 挂起函数：需读取该待办的子任务，用于在详情页**区分父待办标题与子待办标题**
     * （各自取实际值）。
     *
     * @param context 上下文
     * @param todo 目标待办（父待办），其字段会被平铺为页面入参
     */
    suspend fun openTodoDetail(context: Context, todo: TodoItem) {
        KuiklyRenderActivity.start(context, PAGE_TODO_DETAIL, buildTodoDetailData(context, todo))
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
     * 页面侧 `pageData.params.optString("parentTitle", "")` 的访问路径其实是：
     *   `rawPageData.optJSONObject("param")` → 用户 Map 转成的 JSONObject → `.optString("parentTitle")`
     *
     * 所以用户应该传扁平字段。如果再嵌套一层，字段会落到
     * `rawPageData.param.param.title` 这种位置，KUIKLY 找不到。
     *
     * **父/子标题区分**（主工程把单一 `title` 字段拆为两个显式字段）：
     * - `parentTitle`：父待办标题（被点击待办自身的 `title` 实际值），供详情页标题输入框编辑/回写。
     * - `childTitles`：子待办标题列表，序列化为 JSON 字符串
     *   `[{"title":"测试2","done":false},{"title":"测试3","done":false}]`，
     *   值为各子待办的实际 `title` 与完成状态。用**字符串**平铺传递，避免嵌套数组在
     *   `argsToMap`（非递归）转换中丢失；页面侧用 Kuikly 的 `JSONArray` 解析。
     *
     * 字段与 shared 侧 `TodoDetailPage` 的读取保持一致；`content` / `dueDate` 为可空字段，
     * 空值统一兜底为 "" / 0，避免页面侧 optString / optLong 取到异常值。
     */
    private suspend fun buildTodoDetailData(context: Context, todo: TodoItem): JSONObject {
        // 子任务读取放到 IO 调度，避免阻塞调用方（通常为主线程）协程
        val subTasks = withContext(Dispatchers.IO) {
            SubTaskManager.getSubTasks(context, todo.id)
        }
        val childArray = JSONArray()
        subTasks.forEach { st ->
            childArray.put(
                JSONObject().apply {
                    put("title", st.title)          // 子待办标题（实际值）
                    put("done", st.isCompleted)      // 子待办完成状态
                }
            )
        }
        return JSONObject().apply {
            put("todoId", todo.id)
            put("parentTitle", todo.parentTitle)            // 父待办标题（实际值）
            put("content", todo.content ?: "")
            put("dueDate", todo.dueDate ?: 0L)
            put("status", todo.status)
            put("isPinned", todo.isPinned)
            put("childTitles", childArray.toString()) // 子待办标题列表（各实际值，JSON 字符串）
        }
    }
}
