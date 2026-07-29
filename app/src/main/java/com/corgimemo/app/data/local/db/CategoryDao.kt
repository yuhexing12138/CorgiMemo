package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * 分类数据访问接口
 *
 * v2026-07-29 改造：不再区分默认/自定义分组，所有分组都可被删除。
 * - 原 `deleteCustomCategory(id)` 改为 `deleteCategory(id)`，去掉 `AND isDefault = 0` 过滤
 * - 新增 `setPinned(id, isPinned)` 用于置顶分组
 * - getAllCategories ORDER BY 调整为 `isPinned DESC, sortOrder ASC, id ASC`
 *   让置顶分组排在前，未置顶分组按拖拽顺序，sortOrder 相同时按 id ASC 兜底
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
     * 获取所有分类（Flow，按"置顶优先 + 用户拖拽顺序"）
     *
     * v2026-07-29 调整：ORDER BY 改为 `isPinned DESC, sortOrder ASC, id ASC`
     * - isPinned DESC：置顶分组排在前
     * - sortOrder ASC：未置顶分组按侧滑栏拖拽顺序
     * - id ASC 兜底：sortOrder 相同（默认 0）时按创建顺序排列
     */
    @Query("SELECT * FROM categories ORDER BY isPinned DESC, sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<Category>>

    /**
     * 获取所有分类（同步列表，按"置顶优先 + 用户拖拽顺序"）
     *
     * v2026-07-29 调整：同上 isPinned DESC, sortOrder ASC, id ASC
     */
    @Query("SELECT * FROM categories ORDER BY isPinned DESC, sortOrder ASC, id ASC")
    suspend fun getAllCategoriesList(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE type = :type LIMIT 1")
    suspend fun getCategoryByType(type: Int): Category?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT id FROM categories WHERE type = :type LIMIT 1")
    suspend fun getCategoryIdByType(type: Int): Long?

    /**
     * 删除分类（v2026-07-29 改造）
     *
     * 原 `deleteCustomCategory` 加了 `AND isDefault = 0` 过滤，仅允许删除非默认分组。
     * 现已取消默认/自定义区分，所有分组都可被删除，故改名为 `deleteCategory`。
     *
     * @param id 要删除的分类 ID
     */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    /**
     * 设置分类置顶状态（v2026-07-29 新增）
     *
     * 配合 [Category.isPinned] 字段，用于置顶/取消置顶分组。
     * 由 [com.corgimemo.app.data.repository.CategoryRepository.setCategoryPinned] 调用。
     *
     * @param id 分类 ID
     * @param isPinned true=置顶（排在前），false=取消置顶
     */
    @Query("UPDATE categories SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
