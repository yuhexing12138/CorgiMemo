package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 柯基数据实体
 * 存储柯基的所有成长和装扮信息
 *
 * @property id 主键 ID
 * @property name 柯基名字
 * @property level 当前等级
 * @property experience 总经验值
 * @property currentOutfit 当前装扮 ID（null 表示默认）
 * @property unlockedOutfits 已解锁装扮 ID 列表（JSON 字符串）
 * @property unlockedAchievements 已解锁成就 ID 列表（JSON 字符串）
 * @property moodValue 情绪值（0-100）
 * @property lastActiveDate 最后活跃日期（格式：yyyy-MM-dd）
 * @property totalCompleted 累计完成任务数
 * @property consecutiveDays 连续活跃天数
 * @property maxConsecutiveDays 历史最长连续天数
 * @property consecutiveEarlyDays 连续早起天数
 * @property lastEarlyDate 最后一次早起日期（格式：yyyy-MM-dd）
 */
@Entity(tableName = "corgi_data")
data class CorgiData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val level: Int = 1,
    val experience: Int = 0,
    val currentOutfit: String? = null,
    val unlockedOutfits: String = "[]",
    val unlockedAchievements: String = "[]",
    val moodValue: Int = 50,
    val lastActiveDate: String,
    val totalCompleted: Int = 0,
    val consecutiveDays: Int = 0,
    val maxConsecutiveDays: Int = 0,
    val consecutiveEarlyDays: Int = 0,
    val lastEarlyDate: String = ""
)
