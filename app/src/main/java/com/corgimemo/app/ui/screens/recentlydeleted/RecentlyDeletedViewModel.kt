// 最近删除页面的 ViewModel
package com.corgimemo.app.ui.screens.recentlydeleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.DeletedTodo
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.DeletedTodoRepository
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 最近删除页面 ViewModel
 *
 * - 订阅 deletedTodos + categories Flow，combine 后映射为按时间分组的 UiState
 * - init 时启动 30 天前自动清理
 * - 暴露写方法：恢复/永久删除/清空 + Dialog 状态切换
 * - 通过 [events] Channel 发送一次性 UI 事件（SnackBar）
 */
@HiltViewModel
class RecentlyDeletedViewModel @Inject constructor(
    private val deletedTodoRepository: DeletedTodoRepository,
    private val todoRepository: TodoRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentlyDeletedUiState())
    val uiState: StateFlow<RecentlyDeletedUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * 缓存最近一次永久删除的 DeletedTodo，用于 SnackBar 撤销（5 秒内）
     */
    private var lastDeletedForUndo: DeletedTodo? = null

    init {
        observeData()
        cleanupOldRecords()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                deletedTodoRepository.getAllDeletedTodos(),
                categoryRepository.getAllCategories()
            ) { deleted, categories ->
                mapToUiState(deleted, categories)
            }.collect { newState ->
                _uiState.update {
                    it.copy(
                        groups = newState.groups,
                        totalCount = newState.totalCount,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * 清理 30 天前的最近删除记录
     *
     * 静默执行：失败时吞掉异常，下次启动会重试。
     */
    private fun cleanupOldRecords() {
        viewModelScope.launch {
            runCatching {
                val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
                deletedTodoRepository.cleanUpOldDeletedTodos(threshold)
            }
        }
    }

    private fun mapToUiState(
        deleted: List<DeletedTodo>,
        categories: List<Category>
    ): RecentlyDeletedUiState {
        val now = System.currentTimeMillis()
        val categoryMap = categories.associateBy { it.id }

        // 1. 投影为 ListItem
        val items = deleted.map { d ->
            DeletedTodoListItem(
                id = d.id,
                title = d.title,
                originalCategoryId = if (d.categoryId == 0L) null else d.categoryId,
                categoryName = d.categoryId.takeIf { it != 0L }?.let { categoryMap[it]?.name },
                deletedAt = d.deletedAt,
                relativeTime = TimeClassifier.formatRelativeTime(d.deletedAt, now)
            )
        }

        // 2. 按时间分组，组内按 deletedAt 倒序
        val grouped = items
            .sortedByDescending { it.deletedAt }
            .groupBy { TimeClassifier.classifyByTime(it.deletedAt, now) }
            .map { (kind, list) ->
                DeletedTodoGroup(
                    kind = kind,
                    title = when (kind) {
                        DeletedTodoGroupKind.TODAY -> "今天"
                        DeletedTodoGroupKind.YESTERDAY -> "昨天"
                        DeletedTodoGroupKind.THIS_WEEK -> "本周"
                        DeletedTodoGroupKind.EARLIER -> "更早"
                    },
                    items = list
                )
            }

        return RecentlyDeletedUiState(
            groups = grouped,
            totalCount = items.size,
            isLoading = false
        )
    }

    /**
     * 恢复单条最近删除项
     *
     * 流程：
     * 1. 从 deleted_todo 读取
     * 2. 判断原 categoryId 是否还存在（被删则重置为 0）
     * 3. 计算 PENDING 区最大 sortOrder + 1
     * 4. 写入 todo_items，删除 deleted_todo 记录
     */
    fun restoreTodo(deletedTodoId: Long) {
        viewModelScope.launch {
            runCatching {
                val deleted = deletedTodoRepository.getByIdBlocking(deletedTodoId)
                if (deleted == null) {
                    _events.send(UiEvent.ShowSnackBar("该待办不存在"))
                    return@launch
                }

                val todo = DeletedTodo.toTodoItem(deleted)

                // 1. 决定 categoryId（原分组存在则保留，否则 0）
                val originalCategoryId = if (todo.categoryId == 0L) null else todo.categoryId
                val finalCategoryId = originalCategoryId?.let { id ->
                    if (categoryRepository.getCategoryById(id) != null) id else 0L
                } ?: 0L

                // 2. 决定 sortOrder（PENDING 区最大值 + 1）
                val maxSortOrder = todoRepository.getMaxSortOrderBlocking(isPinned = false, status = 0)
                val newSortOrder = (maxSortOrder ?: 9999) + 1

                val restoredTodo = todo.copy(
                    categoryId = finalCategoryId,
                    sortOrder = newSortOrder,
                    isPinned = false,
                    status = 0
                )

                // 3. 写入待办表 + 清理 deleted_todo
                todoRepository.insertTodo(restoredTodo)
                deletedTodoRepository.permanentlyDelete(deletedTodoId)
                _events.send(UiEvent.ShowSnackBar("已恢复"))
            }.onFailure {
                _events.send(UiEvent.ShowSnackBar("恢复失败，请重试"))
            }
        }
    }

    /**
     * 永久删除单条（带 SnackBar 撤销支持）
     */
    fun permanentlyDelete(deletedTodoId: Long) {
        viewModelScope.launch {
            runCatching {
                val deleted = deletedTodoRepository.getByIdBlocking(deletedTodoId)
                deletedTodoRepository.permanentlyDelete(deletedTodoId)
                if (deleted != null) {
                    lastDeletedForUndo = deleted
                    _events.send(UiEvent.ShowSnackBarWithUndo("已永久删除", deletedTodoId))
                }
            }.onFailure {
                _events.send(UiEvent.ShowSnackBar("删除失败，请重试"))
            }
        }
    }

    /**
     * 撤销最近一次永久删除（5 秒内有效）
     */
    fun undoLastDelete() {
        val cached = lastDeletedForUndo ?: return
        viewModelScope.launch {
            runCatching {
                deletedTodoRepository.insertDeletedTodo(
                    TodoItem(
                        id = cached.id,
                        title = cached.title,
                        content = cached.content,
                        categoryId = cached.categoryId,
                        priority = cached.priority,
                        status = cached.status,
                        startDate = cached.startDate,
                        estimatedDurationMinutes = cached.estimatedDurationMinutes,
                        reminderTime = cached.reminderTime,
                        repeatType = cached.repeatType,
                        createdAt = cached.createdAt,
                        updatedAt = cached.updatedAt,
                        completedAt = cached.completedAt
                    )
                )
                lastDeletedForUndo = null
                _events.send(UiEvent.ShowSnackBar("已恢复"))
            }
        }
    }

    fun showClearAllDialog() {
        _uiState.update { it.copy(showClearAllDialog = true) }
    }

    fun dismissClearAllDialog() {
        _uiState.update { it.copy(showClearAllDialog = false) }
    }

    fun confirmClearAll() {
        viewModelScope.launch {
            runCatching {
                deletedTodoRepository.permanentlyDeleteAll()
                _uiState.update { it.copy(showClearAllDialog = false) }
            }.onFailure {
                _uiState.update { it.copy(showClearAllDialog = false) }
                _events.send(UiEvent.ShowSnackBar("清空失败，请重试"))
            }
        }
    }

    /**
     * 一次性 UI 事件
     */
    sealed class UiEvent {
        /** 显示普通 SnackBar */
        data class ShowSnackBar(val message: String) : UiEvent()

        /** 显示带"撤销"按钮的 SnackBar */
        data class ShowSnackBarWithUndo(val message: String, val deletedTodoId: Long) : UiEvent()
    }
}
