// 回收站页面 ViewModel（三数据源：待办 + 灵感 + 日期）
package com.corgimemo.app.ui.screens.recyclebin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.DeletedInspiration
import com.corgimemo.app.data.model.DeletedSpecialDate
import com.corgimemo.app.data.model.DeletedTodo
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.DeletedInspirationRepository
import com.corgimemo.app.data.repository.DeletedSpecialDateRepository
import com.corgimemo.app.data.repository.DeletedTodoRepository
import com.corgimemo.app.data.repository.InspirationRepository
import com.corgimemo.app.data.repository.SpecialDateRepository
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
import com.corgimemo.app.util.TagUtils
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 回收站页面 ViewModel
 *
 * - 订阅 deletedTodos + deletedInspirations + deletedDates + categories 四个 Flow，
 *   combine 后分别映射为待办/灵感/日期的时间分组 UiState
 * - init 时启动 30 天前自动清理（三个表）
 * - 暴露写方法：恢复/永久删除/清空 + Tab 切换 + Dialog 状态切换
 * - 通过 [events] Channel 发送一次性 UI 事件（SnackBar / SnackBarWithUndo）
 * - 通过 [SavedStateHandle] 读取 source 参数决定初始 Tab
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val deletedTodoRepository: DeletedTodoRepository,
    private val deletedInspirationRepository: DeletedInspirationRepository,
    private val todoRepository: TodoRepository,
    private val inspirationRepository: InspirationRepository,
    private val categoryRepository: CategoryRepository,
    private val deletedSpecialDateRepository: DeletedSpecialDateRepository,
    private val specialDateRepository: SpecialDateRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecycleBinUiState())
    val uiState: StateFlow<RecycleBinUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * 缓存最近一次永久删除的对象，用于 SnackBar 撤销（5 秒内）
     * type: "todo" / "inspiration" / "date"，id: 记录 ID
     */
    private var lastDeletedForUndo: Triple<String, Long, Any>? = null

    init {
        // 根据 source 参数决定初始 Tab
        val source = savedStateHandle.get<String>("source")
        val initialTab = when (source) {
            "inspiration" -> RecycleBinTab.INSPIRATION
            "date" -> RecycleBinTab.DATE
            else -> RecycleBinTab.TODO
        }
        _uiState.update { it.copy(selectedTab = initialTab) }

        observeData()
        cleanupOldRecords()
    }

    /**
     * 订阅四个 Flow，combine 后映射为时间分组的 UiState
     */
    private fun observeData() {
        viewModelScope.launch {
            combine(
                deletedTodoRepository.getAllDeletedTodos(),
                deletedInspirationRepository.getAllDeletedInspirations(),
                deletedSpecialDateRepository.getAllDeletedDates(),
                categoryRepository.getAllCategories()
            ) { deletedTodos, deletedInspirations, deletedDates, categories ->
                mapToUiState(deletedTodos, deletedInspirations, deletedDates, categories)
            }.collect { newState ->
                _uiState.update {
                    it.copy(
                        todoGroups = newState.todoGroups,
                        inspirationGroups = newState.inspirationGroups,
                        dateGroups = newState.dateGroups,
                        todoTotalCount = newState.todoTotalCount,
                        inspirationTotalCount = newState.inspirationTotalCount,
                        dateTotalCount = newState.dateTotalCount,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * 清理 30 天前的回收站记录（三个表同时清理）
     *
     * 静默执行：失败时吞掉异常，下次启动会重试。
     */
    private fun cleanupOldRecords() {
        viewModelScope.launch {
            runCatching {
                val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
                deletedTodoRepository.cleanUpOldDeletedTodos(threshold)
                deletedInspirationRepository.cleanUpOldDeletedInspirations(threshold)
                deletedSpecialDateRepository.cleanUpOldDeletedDates(threshold)
            }
        }
    }

    /**
     * 将原始数据映射为 UiState（待办 + 灵感 + 日期各自按时间分组）
     */
    private fun mapToUiState(
        deletedTodos: List<DeletedTodo>,
        deletedInspirations: List<DeletedInspiration>,
        deletedDates: List<DeletedSpecialDate>,
        categories: List<Category>
    ): RecycleBinUiState {
        val now = System.currentTimeMillis()
        val categoryMap = categories.associateBy { it.id }

        // ---- 待办分组 ----
        val todoItems = deletedTodos.map { d ->
            DeletedTodoListItem(
                id = d.id,
                title = d.title,
                originalCategoryId = if (d.categoryId == 0L) null else d.categoryId,
                categoryName = d.categoryId.takeIf { it != 0L }?.let { categoryMap[it]?.name },
                deletedAt = d.deletedAt,
                relativeTime = TimeClassifier.formatRelativeTime(d.deletedAt, now),
                // v2026-07-20 新增：从 DeletedTodo 透传 priority，用于回收站卡片视觉标识
                priority = d.priority
            )
        }

        val todoGroups = todoItems
            .sortedByDescending { it.deletedAt }
            .groupBy { TimeClassifier.classifyByTime(it.deletedAt, now) }
            .map { (kind, list) ->
                DeletedTodoGroup(
                    kind = kind,
                    title = groupTitle(kind),
                    items = list
                )
            }

        // ---- 灵感分组 ----
        val inspirationItems = deletedInspirations.map { d ->
            DeletedInspirationListItem(
                id = d.id,
                title = d.title,
                tags = TagUtils.decodeTags(d.tags),
                deletedAt = d.deletedAt,
                relativeTime = TimeClassifier.formatRelativeTime(d.deletedAt, now)
            )
        }

        val inspirationGroups = inspirationItems
            .sortedByDescending { it.deletedAt }
            .groupBy { TimeClassifier.classifyByTime(it.deletedAt, now) }
            .map { (kind, list) ->
                DeletedInspirationGroup(
                    kind = kind,
                    title = groupTitle(kind),
                    items = list
                )
            }

        // ---- 日期分组 ----
        val dateItems = deletedDates.map { d ->
            DeletedDateListItem(
                id = d.id,
                title = d.title,
                category = d.category,
                deletedAt = d.deletedAt,
                relativeTime = TimeClassifier.formatRelativeTime(d.deletedAt, now)
            )
        }

        val dateGroups = dateItems
            .sortedByDescending { it.deletedAt }
            .groupBy { TimeClassifier.classifyByTime(it.deletedAt, now) }
            .map { (kind, list) ->
                DeletedDateGroup(
                    kind = kind,
                    title = groupTitle(kind),
                    items = list
                )
            }

        return RecycleBinUiState(
            todoGroups = todoGroups,
            inspirationGroups = inspirationGroups,
            dateGroups = dateGroups,
            todoTotalCount = todoItems.size,
            inspirationTotalCount = inspirationItems.size,
            dateTotalCount = dateItems.size,
            isLoading = false
        )
    }

    /**
     * 分组标题映射
     */
    private fun groupTitle(kind: DeletedGroupKind): String = when (kind) {
        DeletedGroupKind.TODAY -> "今天"
        DeletedGroupKind.YESTERDAY -> "昨天"
        DeletedGroupKind.THIS_WEEK -> "本周"
        DeletedGroupKind.EARLIER -> "更早"
    }

    // ========== 恢复操作 ==========

    /**
     * 恢复单条待办
     *
     * 流程：
     * 1. 从 deleted_todos 读取
     * 2. 判断原 categoryId 是否还存在（被删则重置为 0）
     * 3. 计算 PENDING 区最大 sortOrder + 1
     * 4. 写入 todo_items，删除 deleted_todos 记录
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

                // 3. 写入待办表 + 清理 deleted_todos
                todoRepository.insertTodo(restoredTodo)
                deletedTodoRepository.permanentlyDelete(deletedTodoId)
                _events.send(UiEvent.ShowSnackBar("已恢复"))
            }.onFailure {
                _events.send(UiEvent.ShowSnackBar("恢复失败，请重试"))
            }
        }
    }

    /**
     * 恢复单条灵感
     *
     * 流程：
     * 1. 从 deleted_inspirations 读取
     * 2. 判断原 categoryId 是否还存在（被删则重置为 0）
     * 3. 计算非置顶区最大 position + 1
     * 4. 写入 inspirations，删除 deleted_inspirations 记录
     */
    fun restoreInspiration(deletedInspirationId: Long) {
        viewModelScope.launch {
            runCatching {
                val deleted = deletedInspirationRepository.getByIdBlocking(deletedInspirationId)
                if (deleted == null) {
                    _events.send(UiEvent.ShowSnackBar("该灵感不存在"))
                    return@launch
                }

                val inspiration = DeletedInspiration.toInspiration(deleted)

                // 1. 决定 categoryId（原分组存在则保留，否则 0）
                val originalCategoryId = if (inspiration.categoryId == 0L) null else inspiration.categoryId
                val finalCategoryId = originalCategoryId?.let { id ->
                    if (categoryRepository.getCategoryById(id) != null) id else 0L
                } ?: 0L

                // 2. 决定 position（非置顶区最大值 + 1）
                val maxPosition = inspirationRepository.getMaxPosition(isPinned = false)
                val newPosition = (maxPosition ?: 9999) + 1

                val restoredInspiration = inspiration.copy(
                    categoryId = finalCategoryId,
                    position = newPosition,
                    isPinned = false,
                    status = 0
                )

                // 3. 写入灵感表 + 清理 deleted_inspirations
                inspirationRepository.insert(restoredInspiration)
                deletedInspirationRepository.permanentlyDelete(deletedInspirationId)
                _events.send(UiEvent.ShowSnackBar("已恢复"))
            }.onFailure {
                _events.send(UiEvent.ShowSnackBar("恢复失败，请重试"))
            }
        }
    }

    /**
     * 恢复单条日期
     *
     * 流程：
     * 1. 从 deleted_special_dates 读取
     * 2. 通过 DeletedSpecialDate.toSpecialDate() 转换（已重置 isPinned/isArchived）
     * 3. 写入 special_dates，删除 deleted_special_dates 记录
     */
    fun restoreDate(deletedDateId: Long) {
        viewModelScope.launch {
            runCatching {
                val deleted = deletedSpecialDateRepository.getByIdBlocking(deletedDateId)
                if (deleted == null) {
                    _events.send(UiEvent.ShowSnackBar("该日期不存在"))
                    return@launch
                }

                val restored = DeletedSpecialDate.toSpecialDate(deleted)

                // 写入日期主表 + 清理 deleted_special_dates
                specialDateRepository.insert(restored)
                deletedSpecialDateRepository.permanentlyDelete(deletedDateId)
                _events.send(UiEvent.ShowSnackBar("已恢复"))
            }.onFailure {
                _events.send(UiEvent.ShowSnackBar("恢复失败，请重试"))
            }
        }
    }

    // ========== 永久删除操作（带 SnackBar 撤销） ==========

    /**
     * 永久删除单条待办（带 SnackBar 撤销支持）
     */
    fun permanentlyDeleteTodo(deletedTodoId: Long) {
        viewModelScope.launch {
            runCatching {
                val deleted = deletedTodoRepository.getByIdBlocking(deletedTodoId)
                deletedTodoRepository.permanentlyDelete(deletedTodoId)
                if (deleted != null) {
                    lastDeletedForUndo = Triple("todo", deletedTodoId, deleted)
                    _events.send(UiEvent.ShowSnackBarWithUndo("已永久删除", "todo", deletedTodoId))
                }
            }.onFailure {
                _events.send(UiEvent.ShowSnackBar("删除失败，请重试"))
            }
        }
    }

    /**
     * 永久删除单条灵感（带 SnackBar 撤销支持）
     */
    fun permanentlyDeleteInspiration(deletedInspirationId: Long) {
        viewModelScope.launch {
            runCatching {
                val deleted = deletedInspirationRepository.getByIdBlocking(deletedInspirationId)
                deletedInspirationRepository.permanentlyDelete(deletedInspirationId)
                if (deleted != null) {
                    lastDeletedForUndo = Triple("inspiration", deletedInspirationId, deleted)
                    _events.send(UiEvent.ShowSnackBarWithUndo("已永久删除", "inspiration", deletedInspirationId))
                }
            }.onFailure {
                _events.send(UiEvent.ShowSnackBar("删除失败，请重试"))
            }
        }
    }

    /**
     * 永久删除单条日期（带 SnackBar 撤销支持）
     */
    fun permanentlyDeleteDate(deletedDateId: Long) {
        viewModelScope.launch {
            runCatching {
                val deleted = deletedSpecialDateRepository.getByIdBlocking(deletedDateId)
                deletedSpecialDateRepository.permanentlyDelete(deletedDateId)
                if (deleted != null) {
                    lastDeletedForUndo = Triple("date", deletedDateId, deleted)
                    _events.send(UiEvent.ShowSnackBarWithUndo("已永久删除", "date", deletedDateId))
                }
            }.onFailure {
                _events.send(UiEvent.ShowSnackBar("删除失败，请重试"))
            }
        }
    }

    /**
     * 撤销最近一次永久删除（5 秒内有效）
     *
     * 根据 type 判断撤销的是待办、灵感还是日期
     */
    fun undoLastDelete() {
        val cached = lastDeletedForUndo ?: return
        lastDeletedForUndo = null

        viewModelScope.launch {
            runCatching {
                when (cached.first) {
                    "todo" -> {
                        val deletedTodo = cached.third as DeletedTodo
                        // 将待办重新插入 deleted_todos 表
                        deletedTodoRepository.insertDeletedTodo(
                            DeletedTodo.toTodoItem(deletedTodo)
                        )
                    }
                    "inspiration" -> {
                        val deletedInspiration = cached.third as DeletedInspiration
                        // 将灵感重新插入 deleted_inspirations 表
                        deletedInspirationRepository.insertDeletedInspiration(
                            DeletedInspiration.toInspiration(deletedInspiration)
                        )
                    }
                    "date" -> {
                        val deletedDate = cached.third as DeletedSpecialDate
                        // 将日期重新插入 deleted_special_dates 表
                        deletedSpecialDateRepository.insertDeletedDate(
                            DeletedSpecialDate.toSpecialDate(deletedDate)
                        )
                    }
                }
                _events.send(UiEvent.ShowSnackBar("已恢复"))
            }.onFailure {
                _events.send(UiEvent.ShowSnackBar("撤销失败，请重试"))
            }
        }
    }

    // ========== 清空全部 ==========

    /**
     * 确认清空回收站（同时清空三个表）
     */
    fun confirmClearAll() {
        viewModelScope.launch {
            runCatching {
                deletedTodoRepository.permanentlyDeleteAll()
                deletedInspirationRepository.permanentlyDeleteAll()
                deletedSpecialDateRepository.permanentlyDeleteAll()
                _uiState.update { it.copy(showClearAllDialog = false) }
            }.onFailure {
                _uiState.update { it.copy(showClearAllDialog = false) }
                _events.send(UiEvent.ShowSnackBar("清空失败，请重试"))
            }
        }
    }

    // ========== Tab 切换 ==========

    /**
     * 切换 Tab
     */
    fun selectTab(tab: RecycleBinTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // ========== Dialog 状态 ==========

    /**
     * 显示清空全部确认弹窗
     */
    fun showClearAllDialog() {
        _uiState.update { it.copy(showClearAllDialog = true) }
    }

    /**
     * 关闭清空全部确认弹窗
     */
    fun dismissClearAllDialog() {
        _uiState.update { it.copy(showClearAllDialog = false) }
    }

    /**
     * 一次性 UI 事件
     */
    sealed class UiEvent {
        /** 显示普通 SnackBar */
        data class ShowSnackBar(val message: String) : UiEvent()

        /** 显示带"撤销"按钮的 SnackBar */
        data class ShowSnackBarWithUndo(val message: String, val type: String, val id: Long) : UiEvent()
    }
}
