package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * 每日任务统计数据访问接口
 */
@Dao
interface TaskDailyStatsDao {

    /**
     * 插入或更新每日统计
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: TaskDailyStats)

    /**
     * 根据日期获取统计
     */
    @Query("SELECT * FROM task_daily_stats WHERE date = :date")
    suspend fun getByDate(date: String): TaskDailyStats?

    /**
     * 获取日期范围内的统计
     */
    @Query("SELECT * FROM task_daily_stats WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<TaskDailyStats>

    /**
     * 获取指定日期范围内工作类任务完成总数
     */
    @Query("SELECT SUM(workCompleted) FROM task_daily_stats WHERE date >= :startDate AND date <= :endDate")
    suspend fun getWorkCompletedInRange(startDate: String, endDate: String): Int?

    /**
     * 获取指定日期范围内学习类任务完成总数
     */
    @Query("SELECT SUM(studyCompleted) FROM task_daily_stats WHERE date >= :startDate AND date <= :endDate")
    suspend fun getStudyCompletedInRange(startDate: String, endDate: String): Int?

    /**
     * 增加指定日期的工作任务完成数
     */
    @Query("UPDATE task_daily_stats SET workCompleted = workCompleted + 1, lastUpdated = :currentTime WHERE date = :date")
    suspend fun incrementWorkCompleted(date: String, currentTime: Long): Int

    /**
     * 增加指定日期的学习任务完成数
     */
    @Query("UPDATE task_daily_stats SET studyCompleted = studyCompleted + 1, lastUpdated = :currentTime WHERE date = :date")
    suspend fun incrementStudyCompleted(date: String, currentTime: Long): Int

    /**
     * 增加指定日期的生活任务完成数
     */
    @Query("UPDATE task_daily_stats SET lifeCompleted = lifeCompleted + 1, lastUpdated = :currentTime WHERE date = :date")
    suspend fun incrementLifeCompleted(date: String, currentTime: Long): Int

    /**
     * 增加指定日期的娱乐任务完成数
     */
    @Query("UPDATE task_daily_stats SET entertainmentCompleted = entertainmentCompleted + 1, lastUpdated = :currentTime WHERE date = :date")
    suspend fun incrementEntertainmentCompleted(date: String, currentTime: Long): Int
}
