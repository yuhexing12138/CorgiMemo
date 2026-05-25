package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.OperationLogDao
import com.corgimemo.app.data.local.db.OperationLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 操作日志仓库类
 * 封装操作日志的数据访问逻辑，提供高层抽象接口
 *
 * @param operationLogDao 操作日志数据访问对象
 */
@Singleton
class OperationLogRepository @Inject constructor(
    private val operationLogDao: OperationLogDao
) {
    /**
     * 插入一条操作日志
     *
     * @param log 操作日志实体
     * @return 新插入记录的 ID
     */
    suspend fun insertLog(log: OperationLogEntity): Long = withContext(Dispatchers.IO) {
        operationLogDao.insert(log)
    }

    /**
     * 获取最近的操作日志（一次性查询）
     *
     * @param limit 返回的最大数量，默认 100 条
     * @return 操作日志列表
     */
    suspend fun getRecentLogs(limit: Int = 100): List<OperationLogEntity> =
        withContext(Dispatchers.IO) {
            operationLogDao.getRecentLogs(limit)
        }

    /**
     * 获取最近的操作日志（Flow 响应式版本）
     * 用于 UI 层实时监听数据变化
     *
     * @param limit 返回的最大数量，默认 100 条
     * @return 操作日志列表的 Flow
     */
    fun getRecentLogsFlow(limit: Int = 100): Flow<List<OperationLogEntity>> {
        return operationLogDao.getRecentLogsFlow(limit)
    }

    /**
     * 根据操作类型查询日志
     *
     * @param operationType 操作类型
     * @param limit 返回的最大数量，默认 50 条
     * @return 匹配的操作日志列表
     */
    suspend fun getLogsByType(
        operationType: String,
        limit: Int = 50
    ): List<OperationLogEntity> = withContext(Dispatchers.IO) {
        operationLogDao.getLogsByType(operationType, limit)
    }

    /**
     * 删除指定时间戳之前的旧日志
     * 用于定期清理，防止数据库无限增长
     *
     * @param timestamp 时间戳边界（删除此时间之前的记录）
     * @return 删除的记录数
     */
    suspend fun deleteOlderThan(timestamp: Long): Int = withContext(Dispatchers.IO) {
        operationLogDao.deleteOlderThan(timestamp)
    }

    /**
     * 删除单条日志
     *
     * @param id 日志 ID
     */
    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        operationLogDao.deleteById(id)
    }

    /**
     * 获取日志总数
     *
     * @return 日志总数
     */
    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        operationLogDao.getCount()
    }

    /**
     * 清空所有日志
     * 慎用！此操作不可逆
     */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        operationLogDao.deleteAll()
    }
}
