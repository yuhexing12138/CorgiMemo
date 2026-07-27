package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.CustomDateType
import kotlinx.coroutines.flow.Flow

/**
 * 自定义日期类型数据访问接口
 */
@Dao
interface CustomDateTypeDao {

    /** 新增自定义类型，返回自增 ID */
    @Insert
    suspend fun insert(customType: CustomDateType): Long

    /** 更新自定义类型（重命名/修改 emoji） */
    @Update
    suspend fun update(customType: CustomDateType)

    /** 获取所有自定义类型（响应式），按 sortOrder ASC 排列 */
    @Query("SELECT * FROM custom_date_types ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllCustomDateTypes(): Flow<List<CustomDateType>>

    /** 根据 ID 获取自定义类型 */
    @Query("SELECT * FROM custom_date_types WHERE id = :id")
    suspend fun getById(id: Long): CustomDateType?

    /** 删除自定义类型 */
    @Delete
    suspend fun delete(customType: CustomDateType)

    /**
     * 批量更新自定义类型（v2026-07-27 新增，P8 Phase 3 实施）
     *
     * 用于 DATE Tab 拖拽后批量持久化 sortOrder。
     * Room 自动按 @Update 主键匹配，无需显式 WHERE。
     *
     * @param customTypes 排序后的自定义类型列表（每项 sortOrder 字段为目标位置）
     */
    @Update
    suspend fun updateAll(customTypes: List<CustomDateType>)
}
