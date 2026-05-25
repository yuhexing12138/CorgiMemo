package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 操作日志数据访问对象接口
 * 提供操作日志的 CRUD 操作方法
 */
@Dao
interface OperationLogDao {

    /**
     * 插入一条操作日志
     *
     * @param log 操作日志实体
     * @return 新插入记录的 ID
     */
    @Insert
    suspend fun insert(log: OperationLogEntity): Long

    /**
     * 获取最近的操作日志（按时间倒序）
     *
     * @param limit 返回的最大数量
     * @return 操作日志列表的 Flow（响应式更新）
     */
    @Query("SELECT * FROM operation_logs ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<OperationLogEntity>

    /**
     * 获取所有操作日志（Flow 版本，用于 UI 监听）
     *
     * @param limit 返回的最大数量
     * @return 操作日志列表的 Flow
     */
    @Query("SELECT * FROM operation_logs ORDER BY created_at DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int): Flow<List<OperationLogEntity>>

    /**
     * 根据操作类型查询日志
     *
     * @param operationType 操作类型
     * @param limit 返回的最大数量
     * @return 匹配的操作日志列表
     */
    @Query("SELECT * FROM operation_logs WHERE operation_type = :operationType ORDER BY created_at DESC LIMIT :limit")
    suspend fun getLogsByType(operationType: String, limit: Int): List<OperationLogEntity>

    /**
     * 删除指定时间戳之前的旧日志
     * 用于定期清理，防止数据库无限增长
     *
     * @param timestamp 时间戳边界（删除此时间之前的记录）
     * @return 删除的记录数
     */
    @Query("DELETE FROM operation_logs WHERE created_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    /**
     * 删除单条日志
     *
     * @param id 日志 ID
     */
    @Query("DELETE FROM operation_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 获取日志总数
     *
     * @return 日志总数
     */
    @Query("SELECT COUNT(*) FROM operation_logs")
    suspend fun getCount(): Int

    /**
     * 清空所有日志
     */
    @Query("DELETE FROM operation_logs")
    suspend fun deleteAll()
}
