package com.corgimemo.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.model.UserTemplateEntity
import com.corgimemo.app.data.repository.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 模板 ViewModel
 * 管理用户自定义模板的 CRUD 操作和状态
 *
 * @param application 应用上下文
 */
class TemplateViewModel(application: Application) : AndroidViewModel(application) {

    /** 模板仓库实例 */
    private val templateRepository: TemplateRepository

    /** 用户模板列表（响应式数据流）*/
    private val _userTemplates = MutableStateFlow<List<UserTemplateEntity>>(emptyList())
    val userTemplates: StateFlow<List<UserTemplateEntity>> = _userTemplates.asStateFlow()

    /** 是否已达到最大模板数量限制 */
    private val _isMaxLimitReached = MutableStateFlow(false)
    val isMaxLimitReached: StateFlow<Boolean> = _isMaxLimitReached.asStateFlow()

    init {
        /** 初始化数据库和 Repository */
        val database = CorgiMemoDatabase.getDatabase(application)
        templateRepository = TemplateRepository(database.templateDao())

        /** 加载用户模板列表 */
        loadUserTemplates()
    }

    /**
     * 加载所有用户模板
     * 订阅 Flow 以实时更新列表
     */
    private fun loadUserTemplates() {
        viewModelScope.launch {
            templateRepository.getAllTemplates().collect { templates ->
                _userTemplates.value = templates
                /** 检查是否达到上限 */
                _isMaxLimitReached.value = templateRepository.isMaxLimitReached()
            }
        }
    }

    /**
     * 创建新模板
     *
     * @param name 模板名称
     * @param icon 模板图标（Emoji）
     * @param description 模板描述
     * @param todosJson 待办列表的 JSON 字符串
     */
    fun createTemplate(
        name: String,
        icon: String,
        description: String,
        todosJson: String
    ) {
        viewModelScope.launch {
            templateRepository.createTemplate(
                name = name,
                icon = icon,
                description = description,
                todosJson = todosJson
            )
        }
    }

    /**
     * 更新现有模板
     *
     * @param id 模板 ID
     * @param name 新的模板名称
     * @param icon 新的模板图标
     * @param description 新的模板描述
     * @param todosJson 新的待办列表 JSON
     */
    fun updateTemplate(
        id: Long,
        name: String,
        icon: String,
        description: String,
        todosJson: String
    ) {
        viewModelScope.launch {
            templateRepository.updateTemplate(
                id = id,
                name = name,
                icon = icon,
                description = description,
                todosJson = todosJson
            )
        }
    }

    /**
     * 删除模板
     *
     * @param template 要删除的模板实体
     */
    fun deleteTemplate(template: UserTemplateEntity) {
        viewModelScope.launch {
            templateRepository.deleteTemplate(template)
        }
    }

    /**
     * 根据 ID 删除模板
     *
     * @param id 要删除的模板 ID
     */
    fun deleteTemplateById(id: Long) {
        viewModelScope.launch {
            templateRepository.deleteTemplateById(id)
        }
    }

    /**
     * 从用户模板实体创建待办事项
     * 解析 todosJson 并批量创建待办
     *
     * @param userTemplate 用户模板实体
     * @param todoRepository 待办项 Repository（用于创建待办）
     */
    fun createTodosFromUserTemplate(
        userTemplate: UserTemplateEntity,
        todoRepository: com.corgimemo.app.data.repository.TodoRepository
    ) {
        viewModelScope.launch {
            try {
                /** 解析待办列表 JSON */
                val todoTitles = kotlinx.serialization.json.Json.decodeFromString<List<String>>(
                    userTemplate.todosJson
                )

                /** 批量创建待办 */
                todoTitles.forEach { title ->
                    val newTodo = TodoItem(
                        title = title,
                        content = "来自「${userTemplate.name}」模板",
                        categoryId = 0L,
                        priority = 1,
                        status = 0,
                        repeatType = 0,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    todoRepository.insertTodo(newTodo)
                }
            } catch (e: Exception) {
                /** JSON 解析失败，忽略错误 */
                e.printStackTrace()
            }
        }
    }
}
