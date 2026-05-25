package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.local.db.TemplateDao
import com.corgimemo.app.data.model.UserTemplateEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户模板仓库类
 * 封装对用户模板的数据访问逻辑，提供统一的接口给 ViewModel 使用
 *
 * @param templateDao 模板数据访问对象
 */
class TemplateRepository(private val templateDao: TemplateDao) {

    /**
     * 获取所有用户模板（按更新时间倒序）
     *
     * @return 模板列表的 Flow，数据变化时自动更新
     */
    fun getAllTemplates(): Flow<List<UserTemplateEntity>> {
        return templateDao.getAllTemplates()
    }

    /**
     * 根据 ID 获取单个模板
     *
     * @param id 模板 ID
     * @return 模板实体，未找到则返回 null
     */
    suspend fun getTemplateById(id: Long): UserTemplateEntity? {
        return templateDao.getTemplateById(id)
    }

    /**
     * 创建新模板
     *
     * @param name 模板名称
     * @param icon 模板图标（Emoji）
     * @param description 模板描述
     * @param todosJson 待办列表的 JSON 字符串
     * @return 新创建的模板 ID
     */
    suspend fun createTemplate(
        name: String,
        icon: String,
        description: String,
        todosJson: String
    ): Long {
        val template = UserTemplateEntity(
            name = name,
            icon = icon,
            description = description,
            todosJson = todosJson
        )
        return templateDao.insertTemplate(template)
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
    suspend fun updateTemplate(
        id: Long,
        name: String,
        icon: String,
        description: String,
        todosJson: String
    ) {
        val existingTemplate = templateDao.getTemplateById(id) ?: return
        val updatedTemplate = existingTemplate.copy(
            name = name,
            icon = icon,
            description = description,
            todosJson = todosJson,
            updatedAt = System.currentTimeMillis()
        )
        templateDao.updateTemplate(updatedTemplate)
    }

    /**
     * 删除模板
     *
     * @param template 要删除的模板实体
     */
    suspend fun deleteTemplate(template: UserTemplateEntity) {
        templateDao.deleteTemplate(template)
    }

    /**
     * 根据 ID 删除模板
     *
     * @param id 要删除的模板 ID
     */
    suspend fun deleteTemplateById(id: Long) {
        templateDao.deleteTemplateById(id)
    }

    /**
     * 获取用户创建的模板数量
     *
     * @return 模板数量
     */
    suspend fun getTemplateCount(): Int {
        return templateDao.getTemplateCount()
    }

    /**
     * 检查是否已达到最大模板数量限制
     *
     * @return true 如果已达到上限（20个）
     */
    suspend fun isMaxLimitReached(): Boolean {
        return templateDao.isMaxLimitReached()
    }
}
