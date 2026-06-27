package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.TodoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<TodoItem>)

    @Update
    suspend fun update(todo: TodoItem)

    /**
     * 批量更新待办
     *
     * 用于多选模式下的批量操作（如 batchComplete）：
     * - 一次性事务，多条记录原子性更新
     * - Room Flow 单次发射，UI 一次性重组（视觉上"同步完成"）
     * - 性能优于循环单条 update（减少数据库往返）
     *
     * @param todos 待更新列表
     */
    @Update
    suspend fun updateAll(todos: List<TodoItem>)

    /**
     * 更新单个待办的 sortOrder（拖拽完成后调用）
     *
     * @param todoId 待办 ID
     * @param sortOrder 新的排序索引
     * @param updatedAt 更新时间戳
     */
    @Query("UPDATE todo_items SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :todoId")
    suspend fun updateSortOrder(todoId: Long, sortOrder: Int, updatedAt: Long)

    /**
     * 切换置顶状态（拖拽跨越分区时调用）
     *
     * 与现有 togglePin 区别：此处显式指定目标状态（true/false），
     * 用于拖拽跨越分区时的对称切换。
     *
     * @param todoId 待办 ID
     * @param isPinned 目标置顶状态
     * @param updatedAt 更新时间戳
     */
    @Query("UPDATE todo_items SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :todoId")
    suspend fun updatePinnedStatus(todoId: Long, isPinned: Boolean, updatedAt: Long)

    @Delete
    suspend fun delete(todo: TodoItem)

    @Query("DELETE FROM todo_items WHERE id = :todoId")
    suspend fun deleteById(todoId: Long)

    @Query("DELETE FROM todo_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM todo_items WHERE id = :todoId")
    suspend fun getTodoById(todoId: Long): TodoItem?

    /**
     * 切换待办置顶状态
     *
     * 使用 SQL CASE WHEN 单次往返完成切换，无需先查询再更新。
     * 旧值 0 → 新值 1（置顶），旧值 1 → 新值 0（取消置顶）。
     *
     * @param todoId 待办 ID
     */
    @Query("""
        UPDATE todo_items
        SET isPinned = CASE isPinned WHEN 1 THEN 0 ELSE 1 END,
            updatedAt = :updatedAt
        WHERE id = :todoId
    """)
    suspend fun togglePin(todoId: Long, updatedAt: Long)

    @Query("SELECT * FROM todo_items ORDER BY createdAt DESC")
    fun getAllTodos(): Flow<List<TodoItem>>

    /**
     * 按拖拽排序规则观察所有待办
     *
     * 排序规则：isPinned DESC, sortOrder ASC, createdAt DESC
     * - isPinned DESC：置顶项始终在前
     * - sortOrder ASC：同分区内按拖拽顺序
     * - createdAt DESC：兜底（sortOrder 并列时）
     *
     * @return 按 sortOrder 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC")
    fun observeAllSorted(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items ORDER BY createdAt DESC")
    suspend fun getAllTodosBlocking(): List<TodoItem>

    @Query("SELECT * FROM todo_items WHERE status = 0 AND reminderTime IS NOT NULL AND reminderTime > :currentTime ORDER BY reminderTime ASC")
    suspend fun getPendingRemindersBlocking(currentTime: Long): List<TodoItem>

    @Query("SELECT * FROM todo_items WHERE status = :status ORDER BY createdAt DESC")
    fun getTodosByStatus(status: Int): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun getTodosByCategory(categoryId: Long): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE priority = :priority ORDER BY createdAt DESC")
    fun getTodosByPriority(priority: Int): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE reminderTime IS NOT NULL AND reminderTime > :currentTime ORDER BY reminderTime ASC")
    fun getTodosWithReminders(currentTime: Long): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE categoryId = :categoryId AND status = :status ORDER BY createdAt DESC")
    fun getTodosByCategoryAndStatus(categoryId: Long, status: Int): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE status = :status ORDER BY priority DESC, startDate ASC")
    fun getTodosByStatusPriorityDueDate(status: Int): Flow<List<TodoItem>>

    @Query("SELECT COUNT(*) FROM todo_items WHERE status = 1 AND categoryId = :categoryId")
    suspend fun getCompletedCountByCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM todo_items WHERE status = 1")
    suspend fun getTotalCompletedCount(): Int

    @Query("DELETE FROM todo_items WHERE status = 1 AND completedAt < :threshold AND completedAt IS NOT NULL")
    suspend fun deleteOldCompletedTodos(threshold: Long): Int

    /**
     * 获取指定分类类型（工作=0/学习=1/生活=2）的已完成任务数
     */
    @Query("""
        SELECT COUNT(*) FROM todo_items t
        INNER JOIN categories c ON t.categoryId = c.id
        WHERE t.status = 1 AND c.type = :categoryType
    """)
    suspend fun getCompletedCountByCategoryType(categoryType: Int): Int

    /**
     * 获取今天完成的任务数
     */
    @Query("""
        SELECT COUNT(*) FROM todo_items 
        WHERE status = 1 AND completedAt >= :startOfDay AND completedAt < :endOfDay
    """)
    suspend fun getCompletedCountToday(startOfDay: Long, endOfDay: Long): Int

    /**
     * 搜索待办（按标题/内容模糊匹配）
     * @param query 搜索关键词
     * @return 匹配的待办列表
     */
    @Query("SELECT * FROM todo_items WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun searchTodos(query: String): List<TodoItem>

    // ==================== 排序查询方法 ====================

    /**
     * 获取所有待办（按更新时间降序 - 最新更新的在前）
     * @return 按 updatedAt DESC 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items ORDER BY updatedAt DESC")
    fun getAllTodosByUpdatedDesc(): Flow<List<TodoItem>>

    /**
     * 获取所有待办（按更新时间升序 - 最新更新的在后）
     * @return 按 updatedAt ASC 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items ORDER BY updatedAt ASC")
    fun getAllTodosByUpdatedAsc(): Flow<List<TodoItem>>

    /**
     * 获取所有待办（按创建时间升序 - 最新创建的在后）
     * @return 按 createdAt ASC 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items ORDER BY createdAt ASC")
    fun getAllTodosByCreatedAsc(): Flow<List<TodoItem>>

    /**
     * 获取指定分类下的待办（按更新时间降序）
     * @param categoryId 分类 ID
     * @return 该分类下按 updatedAt DESC 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items WHERE categoryId = :categoryId ORDER BY updatedAt DESC")
    fun getTodosByCategoryUpdatedDesc(categoryId: Long): Flow<List<TodoItem>>

    /**
     * 获取指定分类下的待办（按更新时间升序）
     * @param categoryId 分类 ID
     * @return 该分类下按 updatedAt ASC 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items WHERE categoryId = :categoryId ORDER BY updatedAt ASC")
    fun getTodosByCategoryUpdatedAsc(categoryId: Long): Flow<List<TodoItem>>

    /**
     * 获取指定分类下的待办（按创建时间升序）
     * @param 分类 ID
     * @return 该分类下按 createdAt ASC 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items WHERE categoryId = :categoryId ORDER BY createdAt ASC")
    fun getTodosByCategoryCreatedAsc(categoryId: Long): Flow<List<TodoItem>>

    // ==================== 截止时间（dueDate）相关查询方法 ====================

    /**
     * 按截止时间升序排序获取所有设置了截止时间的待办
     * 即将到期的任务排在前面
     * @return 按 dueDate ASC 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items WHERE dueDate IS NOT NULL ORDER BY dueDate ASC")
    fun getTodosByDueDateAsc(): Flow<List<TodoItem>>

    /**
     * 获取已过期未完成的待办事项
     * 用于提醒用户处理超期任务
     * @param currentTime 当前时间戳（毫秒）
     * @return 已过期的待办列表（按截止时间升序）
     */
    @Query("""
        SELECT * FROM todo_items 
        WHERE status = 0 
        AND dueDate IS NOT NULL 
        AND dueDate < :currentTime
        ORDER BY dueDate ASC
    """)
    suspend fun getOverdueTodos(currentTime: Long): List<TodoItem>

    /**
     * 获取指定时间范围内即将到期的待办事项
     * 用于展示近期需要完成的任务
     * @param startTime 时间范围起始点（毫秒）
     * @param endTime 时间范围结束点（毫秒）
     * @return 即将到期的待办列表 Flow（按截止时间升序）
     */
    @Query("""
        SELECT * FROM todo_items
        WHERE status = 0
        AND dueDate IS NOT NULL
        AND dueDate >= :startTime
        AND dueDate <= :endTime
        ORDER BY dueDate ASC
    """)
    fun getUpcomingTodos(startTime: Long, endTime: Long): Flow<List<TodoItem>>

    // ==================== 分页查询方法（Paging 3 集成）====================

    /**
     * 分页获取所有待办（按创建时间降序）
     *
     * 用于 Paging 3 分页加载，支持大数据量场景。
     *
     * @param limit 每页数据量（如 20 条）
     * @param offset 偏移量（页码 × 每页大小）
     * @return 当前页的待办列表
     */
    @Query("SELECT * FROM todo_items ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getTodosPaging(limit: Int, offset: Int): List<TodoItem>

    /**
     * 分页获取待办状态为"待处理"的项（status = 0）
     *
     * @param limit 每页数据量
     * @param offset 偏移量
     * @return 当前页的待处理待办列表
     */
    @Query("SELECT * FROM todo_items WHERE status = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPendingTodosPaging(limit: Int, offset: Int): List<TodoItem>

    /**
     * 分页获取待办状态为"已完成"的项（status = 1）
     *
     * @param limit 每页数据量
     * @param offset 偏移量
     * @return 当前页的已完成待办列表
     */
    @Query("SELECT * FROM todo_items WHERE status = 1 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getCompletedTodosPaging(limit: Int, offset: Int): List<TodoItem>
}
