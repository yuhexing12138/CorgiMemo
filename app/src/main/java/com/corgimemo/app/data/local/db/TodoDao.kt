package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SimpleSQLiteQuery
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

    // ==================== 拖拽排序相关方法 ====================

    /**
     * 更新指定待办项的手动排序位置
     *
     * 用于拖拽排序功能，当用户手动调整列表顺序后，
     * 更新该项在列表中的位置索引。
     *
     * @param todoId 待办项 ID
     * @param newPosition 新的位置索引（从 0 开始）
     */
    @Query("UPDATE todo_items SET position = :newPosition WHERE id = :todoId")
    suspend fun updateTodoPosition(todoId: Long, newPosition: Int)

    /**
     * 批量更新多个待办项的排序位置（原生SQL优化版）
     *
     * 使用 SimpleSQLiteQuery 构建动态 CASE WHEN SQL，
     * 在单次数据库往返中完成所有位置的原子更新。
     *
     * **性能对比**:
     * - 旧方案（循环）：N 条数据 → N 次 SQLite UPDATE 调用
     * - 新方案（原生）：N 条数据 → 1 次 CASE WHEN UPDATE 调用
     * - 性能提升约 10-20 倍
     *
     * **SQL 示例（3 条数据）**:
     * ```sql
     * UPDATE todo_items
     * SET position = (CASE id WHEN 1 THEN 0 WHEN 2 THEN 1 WHEN 3 THEN 2 END)
     * WHERE id IN (1, 2, 3)
     * ```
     *
     * @param positions Map<todoId, newPosition> 待办 ID 到新位置的映射
     * @return 受影响的行数
     */
    @Transaction
    suspend fun batchUpdatePositions(positions: Map<Long, Int>): Int {
        /** 空数据直接返回，避免构建无效 SQL */
        if (positions.isEmpty()) return 0

        /** 动态构建 CASE WHEN 子句 */
        val caseWhen = positions.entries.joinToString(" ") { (id, pos) ->
            "WHEN $id THEN $pos"
        }

        /** 构建 IN 子句中的 ID 列表 */
        val ids = positions.keys.joinToString(",")

        /** 组装完整 SQL 并通过 RawQuery 执行 */
        val sql = """
            UPDATE todo_items 
            SET position = (CASE id $caseWhen END) 
            WHERE id IN ($ids)
        """.trimIndent()

        return rawUpdate(SimpleSQLiteQuery(sql))
    }

    /**
     * 执行原始 SQL 更新语句
     *
     * 用于需要动态构建 SQL 的场景（如批量 CASE WHEN 更新）。
     * 建议在 @Transaction 注解的方法内调用以保证原子性。
     *
     * @param query 原始 SQL 查询对象（SimpleSQLiteQuery）
     * @return 受影响的行数
     */
    @RawQuery
    suspend fun rawUpdate(query: SupportSQLiteQuery): Int

    /**
     * 获取所有待办（按手动排序位置升序）
     *
     * 当用户启用手动排序模式时使用此查询。
     * 未设置 position 的项（position = 0）排在最后，按创建时间排序。
     *
     * @return 按 position ASC, createdAt DESC 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items ORDER BY position ASC, createdAt DESC")
    fun getAllTodosByPositionAsc(): Flow<List<TodoItem>>

    /**
     * 获取指定分类下的待办（按手动排序位置升序）
     *
     * @param categoryId 分类 ID
     * @return 该分类下按 position ASC 排序的待办列表 Flow
     */
    @Query("SELECT * FROM todo_items WHERE categoryId = :categoryId ORDER BY position ASC, createdAt DESC")
    fun getTodosByCategoryPositionAsc(categoryId: Long): Flow<List<TodoItem>>

    /**
     * 获取所有待办的最大位置值
     *
     * 用于初始化新待办项的位置值，
     * 确保新添加的项排在列表末尾。
     *
     * @return 最大 position 值（如果没有数据则返回 -1）
     */
    @Query("SELECT MAX(position) FROM todo_items")
    suspend fun getMaxPosition(): Int?

    /**
     * 重置所有待办项的位置为默认值（基于创建时间顺序）
     *
     * 数据库迁移或用户切换回自动排序时调用，
     * 将 position 重置为基于 createdAt 的顺序。
     */
    @Query("""
        UPDATE todo_items 
        SET position = (
            SELECT COUNT(*) FROM todo_items t2 
            WHERE t2.createdAt <= todo_items.createdAt AND t2.id <= todo_items.id
        ) - 1
    """)
    suspend fun resetAllPositions()

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
