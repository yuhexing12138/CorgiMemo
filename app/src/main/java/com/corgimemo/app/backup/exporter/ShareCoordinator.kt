package com.corgimemo.app.backup.exporter

import android.content.Context
import android.content.Intent
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.ui.screens.home.shareTodoAsImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 分享协调器（统一入口）
 *
 * 负责协调"待办编辑页"和"多选模式"两处分享入口的逻辑：
 * - 判断单/多个待办
 * - 单个：直接调 shareTodoAsImage（单张分享）
 * - 多个：通过 onShowDialog 回调显示选择弹窗（合并/一条条）
 * - 未保存分组：通过 onShowSnackBar 提示用户先保存
 *
 * 本身不持有 UI 状态，UI 层传入回调即可。
 *
 * v2026-07-25 单一数据源重构：所有方法新增 todoImagePaths 和 subTaskImagePaths 参数
 * - 由调用方从 content_blocks 表预查并传入
 * - 替代旧的从 `todo.imagePaths` / `sub.imagePaths` 字段解析的方案（阶段2 重构后字段已置空）
 */
object ShareCoordinator {

    /**
     * 从待办编辑页调用
     *
     * @param context 上下文（用于启动 Intent）
     * @param mainTodo 主 todo（编辑页主对象）
     * @param savedSubTodos 已保存的子 todo 列表（按 sortOrder/groupId 拆分）
     * @param categories 分类列表
     * @param hasUnsavedGroups 是否有未保存的分组（true 时不进入分享流程）
     * @param todoImagePaths 各待办的图片路径映射（key = todoId, value = 图片路径列表）
     *        v2026-07-25 单一数据源重构：从 content_blocks 表派生，替代旧的 todo.imagePaths 字段
     * @param subTaskImagePaths 各子任务的图片路径映射（key = subTaskId, value = 图片路径列表）
     *        v2026-07-25 单一数据源重构：从 content_blocks 表派生，替代旧的 sub.imagePaths 字段
     * @param onShowSnackBar 显示 SnackBar（用于未保存提示）
     * @param onShowDialog 显示分享方式选择弹窗（参数为待分享数量）
     */
    suspend fun shareTodosFromEdit(
        context: Context,
        mainTodo: TodoItem,
        savedSubTodos: List<TodoItem>,
        categories: List<Category>,
        hasUnsavedGroups: Boolean,
        todoImagePaths: Map<Long, List<String>>,
        subTaskImagePaths: Map<Long, List<String>>,
        onShowSnackBar: (String) -> Unit,
        onShowDialog: ((count: Int) -> Unit)?
    ) {
        // 1. 未保存分组：直接提示用户先保存
        if (hasUnsavedGroups) {
            onShowSnackBar("请先保存所有分组")
            return
        }

        // 2. 合成完整 todo 列表（mainTodo 永远存在）
        val allTodos = listOf(mainTodo) + savedSubTodos
        if (allTodos.isEmpty()) return  // 防御性编程：mainTodo 必传，理论上不会触发

        // 3. 单个：直接调 shareTodoAsImage（fire-and-forget，由其内部协程完成分享）
        if (allTodos.size == 1) {
            // v2026-07-25 单一数据源：从映射查询图片路径，替代旧的 todo.imagePaths 字段
            val imgPaths = todoImagePaths[allTodos[0].id] ?: emptyList()
            shareTodoAsImage(context, allTodos[0], categories, imgPaths, subTaskImagePaths)
            return
        }

        // 4. 多个：弹 Dialog 让用户选择合并/一条条
        onShowDialog?.invoke(allTodos.size)
    }

    /**
     * 从多选模式调用
     *
     * @param context 上下文
     * @param todos 选中的 todo 列表
     * @param categories 分类列表
     * @param todoImagePaths 各待办的图片路径映射（key = todoId, value = 图片路径列表）
     *        v2026-07-25 单一数据源重构：从 content_blocks 表派生
     * @param subTaskImagePaths 各子任务的图片路径映射（key = subTaskId, value = 图片路径列表）
     *        v2026-07-25 单一数据源重构：从 content_blocks 表派生
     * @param onShowDialog 显示分享方式选择弹窗
     */
    suspend fun shareTodos(
        context: Context,
        todos: List<TodoItem>,
        categories: List<Category>,
        todoImagePaths: Map<Long, List<String>>,
        subTaskImagePaths: Map<Long, List<String>>,
        onShowDialog: ((count: Int) -> Unit)?
    ) {
        if (todos.isEmpty()) return

        if (todos.size == 1) {
            // v2026-07-25 单一数据源：从映射查询图片路径
            val imgPaths = todoImagePaths[todos[0].id] ?: emptyList()
            shareTodoAsImage(context, todos[0], categories, imgPaths, subTaskImagePaths)
            return
        }

        onShowDialog?.invoke(todos.size)
    }

    /**
     * 用户在弹窗中选择了"合并分享"
     *
     * 流程：每个 todo 生成 Bitmap → mergeBitmaps 合并成 1 张 → 保存到缓存 → 系统分享
     *
     * @param context 上下文
     * @param todos 待分享的 todo 列表
     * @param categories 分类列表
     * @param todoImagePaths 各待办的图片路径映射（key = todoId, value = 图片路径列表）
     *        v2026-07-25 单一数据源重构：从 content_blocks 表派生，替代旧的 todo.imagePaths 字段
     * @param subTaskImagePaths 各子任务的图片路径映射（key = subTaskId, value = 图片路径列表）
     *        v2026-07-25 单一数据源重构：从 content_blocks 表派生，替代旧的 sub.imagePaths 字段
     * @param onShowSnackBar 用于错误提示
     */
    suspend fun shareMerged(
        context: Context,
        todos: List<TodoItem>,
        categories: List<Category>,
        todoImagePaths: Map<Long, List<String>>,
        subTaskImagePaths: Map<Long, List<String>>,
        onShowSnackBar: (String) -> Unit
    ) {
        try {
            withContext(Dispatchers.IO) {
                // 1. 为每个 todo 生成单张分享卡片（含子待办和图片）
                val bitmaps = todos.map { todo ->
                    val category = categories.find { it.id == todo.categoryId }
                    // 异步查询子待办
                    val subTasks = com.corgimemo.app.data.repository.SubTaskManager.getSubTasks(context, todo.id)
                    // v2026-07-25 单一数据源：从映射查询图片路径，替代旧的 todo.imagePaths 字段解析
                    val imagePaths = todoImagePaths[todo.id] ?: emptyList()
                    ImageExporter.createTodoShareCard(
                        context = context,
                        todo = todo,
                        category = category,
                        subTodos = subTasks,
                        imagePaths = imagePaths,
                        subTaskImagePaths = subTaskImagePaths,
                        relationCount = 0
                    )
                }

                // 2. 合并为 1 张（mergeBitmaps 内部已处理 >10 张的 TooManyBitmapsException）
                val merged = ImageExporter.mergeBitmaps(bitmaps)

                // 3. 保存到缓存
                val file = ImageExporter.saveBitmapToCache(context, merged)

                // 4. 构建分享 Intent 并启动
                val shareIntent = ShareIntentHelper.createShareImageIntent(
                    context = context,
                    imageFile = file,
                    text = "我在 CorgiMemo 创建了 ${todos.size} 条待办"
                )
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(shareIntent)
            }
        } catch (e: ImageExporter.TooManyBitmapsException) {
            onShowSnackBar("图片过多，请选择一条条分享")
        } catch (e: Exception) {
            // 统一通过回调提示（由调用方 Snackbar 展示）
            onShowSnackBar("图片生成失败：${e.message}")
        }
    }

    /**
     * 用户在弹窗中选择了"一条条分享"
     *
     * 循环调 shareTodoAsImage，间隔 800ms 避免系统分享面板叠加
     *
     * @param context 上下文
     * @param todos 待分享的 todo 列表
     * @param categories 分类列表
     * @param todoImagePaths 各待办的图片路径映射（key = todoId, value = 图片路径列表）
     *        v2026-07-25 单一数据源重构：从 content_blocks 表派生
     * @param subTaskImagePaths 各子任务的图片路径映射（key = subTaskId, value = 图片路径列表）
     *        v2026-07-25 单一数据源重构：从 content_blocks 表派生
     * @param onShowSnackBar 用于错误提示
     */
    suspend fun shareOneByOne(
        context: Context,
        todos: List<TodoItem>,
        categories: List<Category>,
        todoImagePaths: Map<Long, List<String>>,
        subTaskImagePaths: Map<Long, List<String>>,
        onShowSnackBar: (String) -> Unit
    ) {
        for (todo in todos) {
            try {
                // v2026-07-25 单一数据源：从映射查询图片路径
                val imgPaths = todoImagePaths[todo.id] ?: emptyList()
                shareTodoAsImage(context, todo, categories, imgPaths, subTaskImagePaths)
                delay(800)  // 避免系统分享面板叠加
            } catch (e: Exception) {
                // 统一通过回调提示（由调用方 Snackbar 展示）
                onShowSnackBar("分享失败：${e.message}")
            }
        }
    }
}
