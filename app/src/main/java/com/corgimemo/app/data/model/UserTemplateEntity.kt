package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户自定义模板实体类
 * 用于存储用户创建的待办模板
 *
 * @param id 模板唯一标识（自增主键）
 * @param name 模板名称
 * @param icon 模板图标（Emoji）
 * @param description 模板描述
 * @param todosJson 待办列表的 JSON 字符串
 * @param createdAt 创建时间戳（毫秒）
 * @param updatedAt 更新时间戳（毫秒）
 */
@Entity(tableName = "user_templates")
data class UserTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String,
    val description: String,
    val todosJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
