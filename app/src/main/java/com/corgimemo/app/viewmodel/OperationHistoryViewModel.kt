package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.db.OperationLogEntity
import com.corgimemo.app.data.local.db.OperationType
import com.corgimemo.app.data.repository.OperationLogRepository
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 操作历史页面 ViewModel
 * 管理操作日志的显示和撤销功能
 *
 * @param operationLogRepository 操作日志仓库
 * @param todoRepository 待办仓库（用于恢复数据）
 */
@HiltViewModel
class OperationHistoryViewModel @Inject constructor(
    private val operationLogRepository: OperationLogRepository,
    private val todoRepository: TodoRepository
) : ViewModel() {

    /** 最近操作日志列表 */
    private val _recentLogs = MutableStateFlow<List<OperationLogEntity>>(emptyList())
    val recentLogs: StateFlow<List<OperationLogEntity>> = _recentLogs.asStateFlow()

    /** 是否正在加载 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 加载最近的操作日志 */
    fun loadRecentLogs(limit: Int = 100) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val logs = operationLogRepository.getRecentLogs(limit)
                _recentLogs.value = logs
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 撤销指定操作
     * 根据操作类型执行不同的恢复逻辑
     *
     * @param logId 操作日志 ID
     * @return 是否成功撤销
     */
    suspend fun undoOperation(logId: Long): Boolean {
        return try {
            /** 1. 获取操作日志 */
            val allLogs = _recentLogs.value
            val log = allLogs.find { it.id == logId } ?: return false

            when (log.operationType) {
                OperationType.DELETE -> {
                    /** 恢复被删除的单个待办 */
                    val todo = jsonToTodo(log.snapshotJson)
                    if (todo != null) {
                        todoRepository.insertTodo(todo)
                        true
                    } else {
                        false
                    }
                }

                OperationType.BATCH_DELETE -> {
                    /** 恢复批量删除的待办 */
                    val todos = jsonToTodoList(log.snapshotJson)
                    if (todos.isNotEmpty()) {
                        todos.forEach { todo ->
                            todoRepository.insertTodo(todo)
                        }
                        true
                    } else {
                        false
                    }
                }

                OperationType.COMPLETE -> {
                    /** 恢复为未完成状态 */
                    val todo = jsonToTodo(log.snapshotJson)
                    if (todo != null) {
                        todoRepository.updateTodo(todo)
                        true
                    } else {
                        false
                    }
                }

                else -> {
                    /** 其他类型的操作暂不支持从历史记录撤销 */
                    false
                }
            }.also { success ->
                if (success) {
                    /** 删除已撤销的日志记录 */
                    operationLogRepository.deleteById(logId)
                    /** 刷新列表 */
                    loadRecentLogs()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OperationHistoryVM", "Failed to undo operation", e)
            false
        }
    }

    /**
     * 清空所有操作历史
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            operationLogRepository.deleteAll()
            _recentLogs.value = emptyList()
        }
    }

    // ========== JSON 解析辅助方法 ==========

    /**
     * 将 JSON 字符串解析为 TodoItem 对象
     * 简单的手动解析，避免引入额外依赖
     *
     * @param json JSON 字符串
     * @return 解析后的 TodoItem 对象，失败返回 null
     */
    private fun jsonToTodo(json: String): com.corgimemo.app.data.model.TodoItem? {
        return try {
            /** 提取各字段值 */
            val id = extractLong(json, "id") ?: 0L
            val title = extractString(json, "title") ?: ""
            val content = extractString(json, "content")
            val categoryId = extractLong(json, "categoryId") ?: 0L
            val priority = extractInt(json, "priority") ?: 1
            val status = extractInt(json, "status") ?: 0
            val repeatType = extractInt(json, "repeatType") ?: 0
            val createdAt = extractLong(json, "createdAt") ?: System.currentTimeMillis()
            val updatedAt = extractLong(json, "updatedAt") ?: System.currentTimeMillis()
            val completedAt = extractLong(json, "completedAt")

            com.corgimemo.app.data.model.TodoItem(
                id = id,
                title = title,
                content = content,
                categoryId = categoryId,
                priority = priority,
                status = status,
                repeatType = repeatType,
                createdAt = createdAt,
                updatedAt = updatedAt,
                completedAt = completedAt
            )
        } catch (e: Exception) {
            android.util.Log.e("OperationHistoryVM", "Failed to parse todo JSON", e)
            null
        }
    }

    /**
     * 将 JSON 数组字符串解析为 TodoItem 列表
     *
     * @param jsonArray JSON 数组字符串
     * @return 解析后的 TodoItem 列表
     */
    private fun jsonToTodoList(jsonArray: String): List<com.corgimemo.app.data.model.TodoItem> {
        return try {
            /** 简单分割 JSON 数组中的对象 */
            val objects = jsonArray
                .removeSurrounding("[", "]")
                .split("},")
                .filter { it.isNotEmpty() }
                .map { if (!it.endsWith("}")) "$it}" else it }

            objects.mapNotNull { jsonToTodo(it) }
        } catch (e: Exception) {
            android.util.Log.e("OperationHistoryVM", "Failed to parse todo list JSON", e)
            emptyList()
        }
    }

    /**
     * 从 JSON 字符串中提取 Long 类型字段值
     */
    private fun extractLong(json: String, key: String): Long? {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * 从 JSON 字符串中提取 Int 类型字段值
     */
    private fun extractInt(json: String, key: String): Int? {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * 从 JSON 字符串中提取 String 类型字段值
     */
    private fun extractString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }
}
