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
}
