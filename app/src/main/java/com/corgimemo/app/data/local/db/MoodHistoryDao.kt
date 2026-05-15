package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corgimemo.app.data.model.MoodHistory

/**
 * 情绪历史数据访问对象
 * 管理情绪历史记录的增删改查
 */
@Dao
interface MoodHistoryDao {

    /**
     * 插入或更新情绪历史记录
     * 同一天只保留一条记录，使用 REPLACE 策略
     *
     * @param moodHistory 情绪历史记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(moodHistory: MoodHistory)

    /**
     * 获取指定日期的情绪历史记录
     *
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 情绪历史记录，不存在则返回 null
     */
    @Query("SELECT * FROM mood_history WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): MoodHistory?

    /**
     * 获取最近 N 天的情绪历史记录
     * 按日期升序排列
     *
     * @param limit 获取条数
     * @return 情绪历史记录列表
     */
    @Query("SELECT * FROM mood_history ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentDays(limit: Int): List<MoodHistory>

    /**
     * 获取所有情绪历史记录
     * 按日期升序排列
     *
     * @return 所有情绪历史记录
     */
    @Query("SELECT * FROM mood_history ORDER BY date ASC")
    suspend fun getAll(): List<MoodHistory>

    /**
     * 删除所有情绪历史记录
     */
    @Query("DELETE FROM mood_history")
    suspend fun clearAll()
}
