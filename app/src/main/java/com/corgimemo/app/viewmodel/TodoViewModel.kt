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
 * 待办创建/编辑页面 ViewModel
 * 
 * 管理待办事项的创建和编辑逻辑
 * 
 * @param repository 待办事项数据仓库
 */
class TodoViewModel(
    private val repository: TodoRepository
) : ViewModel() {

    /**
     * UI 状态类
     * 
     * @property todo 当前编辑的待办事项（可选）
     * @property isLoading 是否正在加载
     * @property error 错误信息（可选）
     */
    data class UiState(
        val todo: TodoItem? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    // 内部可变状态
    private val _uiState = MutableStateFlow(UiState())
    
    // 对外暴露的不可变状态
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 加载待办事项（编辑模式）
     * 
     * @param id 待办事项ID
     */
    fun loadTodo(id: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val todo = repository.getTodoById(id)
                _uiState.value = UiState(
                    todo = todo,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * 保存待办事项（新建或更新）
     * 
     * @param title 待办标题
     * @param description 待办描述（可选）
     * @param id 待办ID（新建时为null）
     */
    fun saveTodo(title: String, description: String?, id: String?) {
        viewModelScope.launch {
            try {
                repository.saveTodo(title, description, id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}