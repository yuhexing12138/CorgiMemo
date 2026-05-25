package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.UserTemplateEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户模板数据访问对象（DAO）
 * 提供对 user_templates 表的 CRUD 操作
 */
@Dao
interface TemplateDao {
    /**
     * 获取所有用户模板（按更新时间倒序）
     *
     * @return 模板列表的 Flow，数据变化时自动更新
     */
    @Query("SELECT * FROM user_templates ORDER BY updatedAt DESC")
    fun getAllTemplates(): Flow<List<UserTemplateEntity>>

    /**
     * 根据 ID 获取单个模板
     *
     * @param id 模板 ID
     * @return 模板实体，未找到则返回 null
     */
    @Query("SELECT * FROM user_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): UserTemplateEntity?

    /**
     * 获取用户创建的模板数量
     *
     * @return 模板数量
     */
    @Query("SELECT COUNT(*) FROM user_templates")
    suspend fun getTemplateCount(): Int

    /**
     * 检查是否已达到最大模板数量限制
     *
     * @return true 如果已达到上限（20个）
     */
    @Query("SELECT (SELECT COUNT(*) FROM user_templates) >= 20")
    suspend fun isMaxLimitReached(): Boolean

    /**
     * 插入新模板
     *
     * @param template 要插入的模板实体
     * @return 新插入记录的 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: UserTemplateEntity): Long

    /**
     * 更新现有模板
     *
     * @param template 要更新的模板实体
     */
    @Update
    suspend fun updateTemplate(template: UserTemplateEntity)

    /**
     * 删除模板
     *
     * @param template 要删除的模板实体
     */
    @Delete
    suspend fun deleteTemplate(template: UserTemplateEntity)

    /**
     * 根据 ID 删除模板
     *
     * @param id 要删除的模板 ID
     */
    @Query("DELETE FROM user_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)
}
