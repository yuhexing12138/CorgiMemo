package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 情绪历史记录实体
 * 每日记录一次情绪值，用于绘制历史趋势图
 *
 * @property id 主键 ID
 * @property date 日期（格式：yyyy-MM-dd）
 * @property moodValue 情绪值（0-100）
 * @property changeReason 变化原因（可选）
 */
@Entity(tableName = "mood_history")
data class MoodHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val moodValue: Int,
    val changeReason: String? = null
)
