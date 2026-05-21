package com.corgimemo.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日任务统计实体
 * 用于按日期统计各类任务完成数量
 *
 * @property date 日期（格式：yyyy-MM-dd）
 * @property studyCompleted 学习类任务完成数
 * @property workCompleted 工作类任务完成数
 * @property lifeCompleted 生活类任务完成数
 * @property entertainmentCompleted 娱乐类任务完成数
 * @property lastUpdated 最后更新时间戳
 */
@Entity(tableName = "task_daily_stats")
data class TaskDailyStats(
    @PrimaryKey
    val date: String,
    val studyCompleted: Int = 0,
    val workCompleted: Int = 0,
    val lifeCompleted: Int = 0,
    val entertainmentCompleted: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
