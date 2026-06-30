package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.MoodHistoryDao
import com.corgimemo.app.data.model.MoodHistory
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 情绪历史记录仓库
 * 管理情绪历史的存储和查询
 */
@Singleton
class MoodHistoryRepository @Inject constructor(
    private val moodHistoryDao: MoodHistoryDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 记录今日情绪值
     * 同一天多次调用只保留最新记录
     *
     * @param moodValue 情绪值（0-100）
     * @param reason 变化原因（可选）
     */
    suspend fun recordTodayMood(
        moodValue: Int,
        reason: String? = null
    ) = withContext(ioDispatcher) {
        val today = LocalDate.now().format(dateFormatter)
        val history = MoodHistory(
            date = today,
            moodValue = moodValue,
            changeReason = reason
        )
        moodHistoryDao.insertOrReplace(history)
    }

    /**
     * 检查今日是否已记录情绪
     *
     * @return 是否已记录
     */
    suspend fun isTodayRecorded(): Boolean = withContext(ioDispatcher) {
        val today = LocalDate.now().format(dateFormatter)
        moodHistoryDao.getByDate(today) != null
    }

    /**
     * 获取近7天的情绪历史
     * 按日期升序排列（最早在前，最新在后）
     *
     * @return 情绪历史记录列表，最多7条
     */
    suspend fun getLast7Days(): List<MoodHistory> = withContext(ioDispatcher) {
        val recent = moodHistoryDao.getRecentDays(7)
        recent.sortedBy { it.date }
    }

    /**
     * 获取所有情绪历史
     *
     * @return 所有情绪历史记录
     */
    suspend fun getAll(): List<MoodHistory> = withContext(ioDispatcher) {
        moodHistoryDao.getAll()
    }

    /**
     * 清除所有情绪历史
     */
    suspend fun clearAll() = withContext(ioDispatcher) {
        moodHistoryDao.clearAll()
    }
}
