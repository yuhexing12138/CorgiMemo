package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.Inspiration
import kotlinx.coroutines.flow.Flow

/**
 * 灵感数据访问对象接口
 * 提供对 inspirations 表的增删改查操作
 */
@Dao
interface InspirationDao {
    
    /**
     * 插入新灵感
     * @param inspiration 灵感实体
     * @return 新插入记录的ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inspiration: Inspiration): Long
    
    /**
     * 批量插入灵感
     * @param inspirations 灵感列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(inspirations: List<Inspiration>)
    
    /**
     * 更新灵感
     * @param inspiration 灵感实体
     */
    @Update
    suspend fun update(inspiration: Inspiration)
    
    /**
     * 删除灵感
     * @param inspiration 灵感实体
     */
    @Delete
    suspend fun delete(inspiration: Inspiration)
    
    /**
     * 按ID删除灵感
     * @param id 灵感ID
     */
    @Query("DELETE FROM inspirations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * 删除所有灵感
     */
    @Query("DELETE FROM inspirations")
    suspend fun deleteAll()
    
    /**
     * 获取所有灵感（按置顶+创建时间降序）
     * @return 灵感列表的响应式流
     */
    @Query("SELECT * FROM inspirations ORDER BY isPinned DESC, createdAt DESC")
    fun getAllInspirations(): Flow<List<Inspiration>>
    
    /**
     * 获取所有灵感（阻塞方式，用于一次性读取）
     * @return 灵感列表
     */
    @Query("SELECT * FROM inspirations ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAllInspirationsBlocking(): List<Inspiration>
    
    /**
     * 按ID获取灵感
     * @param id 灵感ID
     * @return 灵感实体，不存在则返回null
     */
    @Query("SELECT * FROM inspirations WHERE id = :id")
    suspend fun getInspirationById(id: Long): Inspiration?
    
    /**
     * 搜索灵感（支持标题/内容/标签模糊匹配）
     * @param query 搜索关键词
     * @return 匹配的灵感列表流
     */
    @Query("""SELECT * FROM inspirations
              WHERE title LIKE '%' || :query || '%'
                 OR content LIKE '%' || :query || '%'
                 OR tags LIKE '%' || :query || '%'
              ORDER BY isPinned DESC, createdAt DESC""")
    fun searchInspirations(query: String): Flow<List<Inspiration>>

    /**
     * 搜索灵感（阻塞方式，v2026-07-22 新增）
     *
     * 与 [searchInspirations] 等价但返回 `List<Inspiration>`，供 [com.corgimemo.app.data.repository.CardRelationRepository.searchCards]
     * 等需要一次性返回结果的场景使用。
     *
     * 性能说明：
     * - 相比"全表加载 + 内存过滤"方案，SQL 层 LIKE 过滤由数据库引擎执行，避免大表 OOM
     * - LIKE '%x%' 走全表扫描，但 inspirations 表通常 < 1000 条，性能可接受
     * - 若数据量上升，可后续切换到 FTS5 全文搜索
     *
     * @param query 搜索关键词
     * @return 匹配的灵感列表（按 isPinned DESC, createdAt DESC 排序）
     */
    @Query("""SELECT * FROM inspirations
              WHERE title LIKE '%' || :query || '%'
                 OR content LIKE '%' || :query || '%'
                 OR tags LIKE '%' || :query || '%'
              ORDER BY isPinned DESC, createdAt DESC""")
    suspend fun searchInspirationsBlocking(query: String): List<Inspiration>
    
    /**
     * 获取灵感总数
     * @return 记录数
     */
    @Query("SELECT COUNT(*) FROM inspirations")
    suspend fun getCount(): Int
    
    /**
     * 切换置顶状态
     * @param id 灵感ID
     * @param isPinned 是否置顶
     */
    @Query("UPDATE inspirations SET isPinned = :isPinned WHERE id = :id")
    suspend fun togglePin(id: Long, isPinned: Boolean)
    
    /**
     * 切换归档状态
     * @param id 灵感ID
     * @param isArchived 是否归档
     */
    @Query("UPDATE inspirations SET isArchived = :isArchived WHERE id = :id")
    suspend fun toggleArchive(id: Long, isArchived: Boolean)

    // ==================== 分页查询方法（Paging 3 集成）====================

    /**
     * 分页获取所有灵感（按置顶+创建时间降序）
     *
     * 用于 Paging 3 分页加载，支持大数据量场景。
     *
     * @param limit 每页数据量（如 20 条）
     * @param offset 偏移量（页码 × 每页大小）
     * @return 当前页的灵感列表
     */
    @Query("SELECT * FROM inspirations ORDER BY isPinned DESC, createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getInspirationsPaging(limit: Int, offset: Int): List<Inspiration>

    /**
     * 获取非置顶灵感的最大 position 值
     *
     * 用于恢复灵感时计算新的排序位置。
     *
     * @param isPinned 是否置顶
     * @return 最大 position 值，无记录时返回 null
     */
    @Query("SELECT MAX(position) FROM inspirations WHERE isPinned = :isPinned")
    suspend fun getMaxPosition(isPinned: Boolean): Int?
}
