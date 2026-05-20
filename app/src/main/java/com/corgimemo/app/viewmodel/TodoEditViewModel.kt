package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.RepeatTaskManager
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 待办编辑 ViewModel
 */
@HiltViewModel
class TodoEditViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _categoryId = MutableStateFlow(0L)
    val categoryId: StateFlow<Long> = _categoryId.asStateFlow()

    private val _priority = MutableStateFlow(1)
    val priority: StateFlow<Int> = _priority.asStateFlow()

    private val _dueDate = MutableStateFlow<Long?>(null)
    val dueDate: StateFlow<Long?> = _dueDate.asStateFlow()

    private val _repeatType = MutableStateFlow(0)
    val repeatType: StateFlow<Int> = _repeatType.asStateFlow()

    // 地理围栏相关字段
    private val _geofenceLat = MutableStateFlow<Double?>(null)
    val geofenceLat: StateFlow<Double?> = _geofenceLat.asStateFlow()

    private val _geofenceLng = MutableStateFlow<Double?>(null)
    val geofenceLng: StateFlow<Double?> = _geofenceLng.asStateFlow()

    private val _geofenceRadius = MutableStateFlow<Float?>(100f)
    val geofenceRadius: StateFlow<Float?> = _geofenceRadius.asStateFlow()

    private val _geofenceType = MutableStateFlow(0)
    val geofenceType: StateFlow<Int> = _geofenceType.asStateFlow()

    private val _geofenceEnabled = MutableStateFlow(false)
    val geofenceEnabled: StateFlow<Boolean> = _geofenceEnabled.asStateFlow()

    private val _geofenceAddress = MutableStateFlow<String?>(null)
    val geofenceAddress: StateFlow<String?> = _geofenceAddress.asStateFlow()

    // 子任务相关
    private val _subTasks = MutableStateFlow<List<SubTask>>(emptyList())
    val subTasks: StateFlow<List<SubTask>> = _subTasks.asStateFlow()

    private var existingTodo: TodoItem? = null

    fun setTitle(title: String) {
        _title.value = title
    }

    fun setContent(content: String) {
        _content.value = content
    }

    fun setCategoryId(categoryId: Long) {
        _categoryId.value = categoryId
    }

    fun setPriority(priority: Int) {
        _priority.value = priority
    }

    fun setDueDate(dueDate: Long?) {
        _dueDate.value = dueDate
    }

    fun setRepeatType(repeatType: Int) {
        _repeatType.value = repeatType
    }

    // 地理围栏相关方法
    fun setGeofenceLat(lat: Double?) {
        _geofenceLat.value = lat
    }

    fun setGeofenceLng(lng: Double?) {
        _geofenceLng.value = lng
    }

    fun setGeofenceRadius(radius: Float) {
        _geofenceRadius.value = radius
    }

    fun setGeofenceType(type: Int) {
        _geofenceType.value = type
    }

    fun setGeofenceEnabled(enabled: Boolean) {
        _geofenceEnabled.value = enabled
    }

    fun setGeofenceAddress(address: String?) {
        _geofenceAddress.value = address
    }

    // 子任务相关方法

    /**
     * 添加子任务
     *
     * @param title 子任务标题
     */
    fun addSubTask(title: String) {
        if (title.isBlank()) return
        val currentList = _subTasks.value
        val newSubTask = SubTask(
            id = 0,
            todoId = existingTodo?.id ?: 0,
            title = title,
            isCompleted = false,
            order = currentList.size + 1
        )
        _subTasks.value = currentList + newSubTask
    }

    /**
     * 删除子任务
     *
     * @param subTask 要删除的子任务
     */
    fun removeSubTask(subTask: SubTask) {
        val currentList = _subTasks.value
        _subTasks.value = currentList.filter { it.id != subTask.id || it.order != subTask.order }
    }

    /**
     * 切换子任务完成状态（仅在编辑已有待办时持久化到数据库）
     * 如果所有子任务完成，会自动完成父任务
     *
     * @param subTask 子任务
     */
    fun toggleSubTaskCompletion(subTask: SubTask) {
        val currentList = _subTasks.value
        val updatedList = currentList.map {
            if (it.id == subTask.id || (it.id == 0L && it.order == subTask.order)) {
                it.copy(isCompleted = !it.isCompleted)
            } else {
                it
            }
        }
        _subTasks.value = updatedList

        if (existingTodo != null && subTask.id > 0) {
            viewModelScope.launch {
                SubTaskManager.toggleSubTaskCompletion(context, subTask.id)
            }
        }
    }

    /**
     * 加载待办及子任务
     *
     * @param todoId 待办 ID
     */
    fun loadTodo(todoId: Long) {
        viewModelScope.launch {
            todoRepository.getTodoById(todoId)?.let { todo ->
                existingTodo = todo
                _title.value = todo.title
                _content.value = todo.content ?: ""
                _categoryId.value = todo.categoryId
                _priority.value = todo.priority
                _dueDate.value = todo.dueDate
                _repeatType.value = todo.repeatType
                _geofenceLat.value = todo.geofenceLat
                _geofenceLng.value = todo.geofenceLng
                _geofenceRadius.value = todo.geofenceRadius
                _geofenceType.value = todo.geofenceType
                _geofenceEnabled.value = todo.geofenceEnabled
                _geofenceAddress.value = todo.geofenceAddress

                val subTasks = SubTaskManager.getSubTasks(context, todoId)
                _subTasks.value = subTasks
            }
        }
    }

    /**
     * 保存待办
     *
     * @return 是否成功
     */
    fun saveTodo(): Boolean {
        if (_title.value.isBlank()) {
            return false
        }

        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val hasSubTasks = _subTasks.value.isNotEmpty()

            val todoId: Long = if (existingTodo != null) {
                val todo = existingTodo!!.copy(
                    title = _title.value,
                    content = if (_content.value.isBlank()) null else _content.value,
                    categoryId = _categoryId.value,
                    priority = _priority.value,
                    dueDate = _dueDate.value,
                    repeatType = _repeatType.value,
                    updatedAt = currentTime,
                    geofenceLat = _geofenceLat.value,
                    geofenceLng = _geofenceLng.value,
                    geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                    geofenceType = _geofenceType.value,
                    geofenceEnabled = _geofenceEnabled.value,
                    geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
                    hasSubTasks = hasSubTasks
                )
                todoRepository.updateTodo(todo)
                existingTodo!!.id
            } else {
                val todo = TodoItem(
                    title = _title.value,
                    content = if (_content.value.isBlank()) null else _content.value,
                    categoryId = _categoryId.value,
                    priority = _priority.value,
                    status = 0,
                    dueDate = _dueDate.value,
                    repeatType = _repeatType.value,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    geofenceLat = _geofenceLat.value,
                    geofenceLng = _geofenceLng.value,
                    geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                    geofenceType = _geofenceType.value,
                    geofenceEnabled = _geofenceEnabled.value,
                    geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
                    hasSubTasks = hasSubTasks
                )
                todoRepository.insertTodo(todo)
            }

            saveSubTasks(todoId)
        }

        return true
    }

    /**
     * 保存子任务（编辑模式下先删除旧子任务再添加新的）
     * 并同步更新待办的 hasSubTasks 字段
     *
     * @param todoId 待办 ID
     */
    private suspend fun saveSubTasks(todoId: Long) {
        val currentSubTasks = _subTasks.value

        if (existingTodo != null) {
            SubTaskManager.deleteAllSubTasks(context, todoId)
        }

        if (currentSubTasks.isNotEmpty()) {
            val titles = currentSubTasks.map { it.title }
            SubTaskManager.addSubTasks(context, todoId, titles)
        }
    }

    /**
     * 删除子任务并同步到数据库（编辑已有待办时）
     *
     * @param subTask 要删除的子任务
     */
    fun deleteSubTask(subTask: SubTask) {
        removeSubTask(subTask)

        if (existingTodo != null && subTask.id > 0) {
            viewModelScope.launch {
                SubTaskManager.deleteSubTask(context, subTask.id)
            }
        }
    }
}
