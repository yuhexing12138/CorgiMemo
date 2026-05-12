package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodoEditViewModel @Inject constructor(
    private val todoRepository: TodoRepository
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

    fun loadTodo(todoId: Long) {
        viewModelScope.launch {
            todoRepository.getTodoById(todoId)?.let { todo ->
                existingTodo = todo
                _title.value = todo.title
                _content.value = todo.content ?: ""
                _categoryId.value = todo.categoryId
                _priority.value = todo.priority
                _dueDate.value = todo.dueDate
                // 加载地理围栏信息
                _geofenceLat.value = todo.geofenceLat
                _geofenceLng.value = todo.geofenceLng
                _geofenceRadius.value = todo.geofenceRadius
                _geofenceType.value = todo.geofenceType
                _geofenceEnabled.value = todo.geofenceEnabled
                _geofenceAddress.value = todo.geofenceAddress
            }
        }
    }

    fun saveTodo(): Boolean {
        if (_title.value.isBlank()) {
            return false
        }

        val currentTime = System.currentTimeMillis()
        val todo = if (existingTodo != null) {
            existingTodo!!.copy(
                title = _title.value,
                content = if (_content.value.isBlank()) null else _content.value,
                categoryId = _categoryId.value,
                priority = _priority.value,
                dueDate = _dueDate.value,
                updatedAt = currentTime,
                // 保存地理围栏信息
                geofenceLat = _geofenceLat.value,
                geofenceLng = _geofenceLng.value,
                geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                geofenceType = _geofenceType.value,
                geofenceEnabled = _geofenceEnabled.value,
                geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null
            )
        } else {
            TodoItem(
                title = _title.value,
                content = if (_content.value.isBlank()) null else _content.value,
                categoryId = _categoryId.value,
                priority = _priority.value,
                status = 0,
                dueDate = _dueDate.value,
                repeatType = 0,
                createdAt = currentTime,
                updatedAt = currentTime,
                // 保存地理围栏信息
                geofenceLat = _geofenceLat.value,
                geofenceLng = _geofenceLng.value,
                geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                geofenceType = _geofenceType.value,
                geofenceEnabled = _geofenceEnabled.value,
                geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null
            )
        }

        viewModelScope.launch {
            if (existingTodo != null) {
                todoRepository.updateTodo(todo)
            } else {
                todoRepository.insertTodo(todo)
            }
        }

        return true
    }
}