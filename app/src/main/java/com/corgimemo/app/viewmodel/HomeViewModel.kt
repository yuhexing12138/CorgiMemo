package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页视图模型
 * 管理待办列表和柯基陪伴系统的状态
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val corgiRepository: CorgiRepository,
    private val corgiPreferences: CorgiPreferences
) : ViewModel() {

    // ========== 待办列表相关 ==========

    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    private val _filterStatus = MutableStateFlow(FilterStatus.ALL)
    val filterStatus: StateFlow<FilterStatus> = _filterStatus.asStateFlow()

    // ========== 柯基数据相关 ==========

    private val _corgiData = MutableStateFlow<CorgiData?>(null)
    val corgiData: StateFlow<CorgiData?> = _corgiData.asStateFlow()

    private val _showNamerDialog = MutableStateFlow(false)
    val showNamerDialog: StateFlow<Boolean> = _showNamerDialog.asStateFlow()

    init {
        loadTodos()
        initCorgiData()
    }

    /**
     * 加载待办列表
     */
    private fun loadTodos() {
        viewModelScope.launch {
            todoRepository.getAllTodos().collect { allTodos ->
                _todos.value = when (_filterStatus.value) {
                    FilterStatus.ALL -> allTodos
                    FilterStatus.PENDING -> allTodos.filter { it.status == 0 }
                    FilterStatus.COMPLETED -> allTodos.filter { it.status == 1 }
                }
            }
        }
    }

    /**
     * 初始化柯基数据
     * 检查是否首次启动，若是则显示命名对话框
     * 否则加载已保存的柯基数据
     */
    private fun initCorgiData() {
        viewModelScope.launch {
            // 只获取一次首次启动状态，避免持续监听导致重复加载
            val isFirst = corgiPreferences.isFirstLaunch.first()
            if (isFirst) {
                _showNamerDialog.value = true
            } else {
                // 加载已有的柯基数据
                loadCorgiData()
            }
        }
    }

    /**
     * 从数据库加载柯基数据
     */
    private fun loadCorgiData() {
        viewModelScope.launch {
            val data = corgiRepository.getCorgiData()
            _corgiData.value = data
        }
    }

    /**
     * 保存柯基名字
     * 首次命名时创建新的柯基数据并保存到数据库和DataStore
     */
    fun saveCorgiName(name: String) {
        viewModelScope.launch {
            // 创建新的柯基数据
            val newCorgi = CorgiData(
                name = name,
                level = 1,
                experience = 0,
                moodValue = 50,
                lastActiveDate = System.currentTimeMillis().toString()
            )

            // 保存到数据库
            corgiRepository.insertCorgi(newCorgi)

            // 保存到DataStore
            corgiPreferences.saveCorgiName(name)
            corgiPreferences.setFirstLaunchDone()

            // 更新UI状态
            _corgiData.value = newCorgi
            _showNamerDialog.value = false
        }
    }

    /**
     * 关闭命名对话框（点击"稍后"）
     */
    fun dismissNamerDialog() {
        _showNamerDialog.value = false
    }

    /**
     * 设置过滤器状态
     */
    fun setFilterStatus(status: FilterStatus) {
        _filterStatus.value = status
        loadTodos()
    }

    /**
     * 切换待办完成状态
     */
    fun toggleTodoStatus(id: Long, isChecked: Boolean) {
        viewModelScope.launch {
            todoRepository.getTodoById(id)?.let { todo ->
                val updatedTodo = todo.copy(
                    status = if (isChecked) 1 else 0,
                    completedAt = if (isChecked) System.currentTimeMillis() else null,
                    updatedAt = System.currentTimeMillis()
                )
                todoRepository.updateTodo(updatedTodo)

                // 如果完成待办，给柯基增加经验
                if (isChecked) {
                    corgiRepository.addExperience(10)
                    loadCorgiData()
                }
            }
        }
    }

    /**
     * 删除待办
     */
    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            todoRepository.deleteTodoById(id)
        }
    }

    /**
     * 待办过滤器枚举
     */
    enum class FilterStatus {
        ALL, PENDING, COMPLETED
    }
}