package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页 ViewModel
 * 
 * 管理首页的状态和业务逻辑
 * 
 * @param repository 待办事项数据仓库
 */
class HomeViewModel(
    private val repository: TodoRepository
) : ViewModel() {

    /**
     * UI 状态类
     * 
     * @property isLoading 是否正在加载
     * @property todos 待办事项列表
     * @property error 错误信息（可选）
     */
    data class UiState(
        val isLoading: Boolean = true,
        val todos: List<TodoItem> = emptyList(),
        val error: String? = null
    )

    // 内部可变状态
    private val _uiState = MutableStateFlow(UiState())
    
    // 对外暴露的不可变状态
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 初始化时加载待办事项列表
     */
    init {
        loadTodos()
    }

    /**
     * 加载待办事项列表
     */
    fun loadTodos() {
        viewModelScope.launch {
            try {
                // 更新状态为加载中
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // 观察数据库中的待办事项变化
                repository.getAllTodos().collect { todos ->
                    _uiState.value = UiState(
                        isLoading = false,
                        todos = todos,
                        error = null
                    )
                }
            } catch (e: Exception) {
                // 处理异常
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * 删除待办事项
     * 
     * @param todo 待删除的待办事项
     */
    fun deleteTodo(todo: TodoItem) {
        viewModelScope.launch {
            try {
                repository.deleteTodo(todo)
            } catch (e: Exception) {
                // 记录错误日志
                e.printStackTrace()
            }
        }
    }

    /**
     * 切换待办事项完成状态
     * 
     * @param id 待办事项ID
     */
    fun toggleTodoStatus(id: String) {
        viewModelScope.launch {
            try {
                repository.toggleTodoStatus(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}