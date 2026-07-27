package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * 分类数据访问接口
 */
@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    /**
     * 批量更新分类排序（v2026-07-27 新增）
     *
     * 用于 GROUP Tab 拖拽后批量持久化 sortOrder。
     * Room 自动按 @Update 主键匹配，无需显式 WHERE。
     *
     * @param categories 排序后的分类列表（每项 sortOrder 字段为目标位置）
     */
    @Update
    suspend fun updateSortOrders(categories: List<Category>)

    /**
     * 获取所有分类（Flow，按用户拖拽顺序）
     *
     * v2026-07-27 P8 Phase 1 调整：ORDER BY 改为 sortOrder ASC, id ASC
     * - sortOrder 优先：保证侧滑栏拖拽后的自定义顺序生效
     * - id ASC 兜底：sortOrder 相同（默认 0）时按创建顺序排列
     */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<Category>>

    /**
     * 获取所有分类（同步列表，按用户拖拽顺序）
     *
     * v2026-07-27 P8 Phase 1 调整：同上 sortOrder ASC, id ASC
     */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllCategoriesList(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE type = :type LIMIT 1")
    suspend fun getCategoryByType(type: Int): Category?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT id FROM categories WHERE type = :type LIMIT 1")
    suspend fun getCategoryIdByType(type: Int): Long?

    @Query("DELETE FROM categories WHERE id = :id AND isDefault = 0")
    suspend fun deleteCustomCategory(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
