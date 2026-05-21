package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.TaskDailyStats
import com.corgimemo.app.data.local.db.TaskDailyStatsDao
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日任务统计仓库
 * 负责管理每日各类任务完成数量的统计
 */
@Singleton
class TaskDailyStatsRepository @Inject constructor(
    private val taskDailyStatsDao: TaskDailyStatsDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 记录任务完成
     * 在任务完成时调用，更新当日统计
     *
     * @param categoryType 任务分类类型
     */
    suspend fun recordTaskCompletion(categoryType: Int) = withContext(ioDispatcher) {
        val today = dateFormat.format(System.currentTimeMillis())
        val currentTime = System.currentTimeMillis()

        // 尝试更新现有记录
        val rowsUpdated = when (categoryType) {
            CategoryType.STUDY -> taskDailyStatsDao.incrementStudyCompleted(today, currentTime)
            CategoryType.WORK -> taskDailyStatsDao.incrementWorkCompleted(today, currentTime)
            CategoryType.LIFE -> taskDailyStatsDao.incrementLifeCompleted(today, currentTime)
            else -> 0 // 娱乐类或其他类型
        }

        // 如果没有更新（记录不存在），则创建新记录
        if (rowsUpdated == 0) {
            val stats = when (categoryType) {
                CategoryType.STUDY -> TaskDailyStats(
                    date = today,
                    studyCompleted = 1,
                    lastUpdated = currentTime
                )
                CategoryType.WORK -> TaskDailyStats(
                    date = today,
                    workCompleted = 1,
                    lastUpdated = currentTime
                )
                CategoryType.LIFE -> TaskDailyStats(
                    date = today,
                    lifeCompleted = 1,
                    lastUpdated = currentTime
                )
                else -> TaskDailyStats(
                    date = today,
                    entertainmentCompleted = 1,
                    lastUpdated = currentTime
                )
            }
            taskDailyStatsDao.insertOrUpdate(stats)
        }
    }

    /**
     * 获取今日统计
     */
    suspend fun getTodayStats(): TaskDailyStats? = withContext(ioDispatcher) {
        val today = dateFormat.format(System.currentTimeMillis())
        taskDailyStatsDao.getByDate(today)
    }

    /**
     * 获取指定日期统计
     */
    suspend fun getStatsByDate(date: String): TaskDailyStats? = withContext(ioDispatcher) {
        taskDailyStatsDao.getByDate(date)
    }

    /**
     * 获取本周工作类任务完成数
     */
    suspend fun getWeeklyWorkCompleted(): Int = withContext(ioDispatcher) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startDate = dateFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDate = dateFormat.format(calendar.time)

        taskDailyStatsDao.getWorkCompletedInRange(startDate, endDate) ?: 0
    }

    /**
     * 获取本月工作类任务完成数
     */
    suspend fun getMonthlyWorkCompleted(): Int = withContext(ioDispatcher) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startDate = dateFormat.format(calendar.time)

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        val endDate = dateFormat.format(calendar.time)

        taskDailyStatsDao.getWorkCompletedInRange(startDate, endDate) ?: 0
    }

    /**
     * 获取本月学习类任务完成数
     */
    suspend fun getMonthlyStudyCompleted(): Int = withContext(ioDispatcher) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startDate = dateFormat.format(calendar.time)

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        val endDate = dateFormat.format(calendar.time)

        taskDailyStatsDao.getStudyCompletedInRange(startDate, endDate) ?: 0
    }

    /**
     * 获取当前学期学习类任务完成数（简化：取最近6个月）
     */
    suspend fun getSemesterStudyCompleted(): Int = withContext(ioDispatcher) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -6)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startDate = dateFormat.format(calendar.time)

        val endDate = dateFormat.format(System.currentTimeMillis())

        taskDailyStatsDao.getStudyCompletedInRange(startDate, endDate) ?: 0
    }

    /**
     * 获取连续工作天数
     * 计算从今天往前数，连续每天都完成至少一个工作任务的天数
     */
    suspend fun getConsecutiveWorkDays(): Int = withContext(ioDispatcher) {
        val calendar = Calendar.getInstance()
        var consecutiveDays = 0

        // 最多检查365天
        for (i in 0 until 365) {
            val date = dateFormat.format(calendar.time)
            val stats = taskDailyStatsDao.getByDate(date)

            if (stats != null && stats.workCompleted > 0) {
                consecutiveDays++
            } else if (i > 0) {
                // 如果不是今天且没有完成，中断连续天数
                break
            }

            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        consecutiveDays
    }

    /**
     * 获取连续学习天数
     */
    suspend fun getConsecutiveStudyDays(): Int = withContext(ioDispatcher) {
        val calendar = Calendar.getInstance()
        var consecutiveDays = 0

        for (i in 0 until 365) {
            val date = dateFormat.format(calendar.time)
            val stats = taskDailyStatsDao.getByDate(date)

            if (stats != null && stats.studyCompleted > 0) {
                consecutiveDays++
            } else if (i > 0) {
                break
            }

            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        consecutiveDays
    }
}
