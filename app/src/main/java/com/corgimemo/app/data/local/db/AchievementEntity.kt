package com.corgimemo.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 成就实体
 * 用于存储成就的解锁状态
 *
 * @property id 成就 ID（与 Achievement.id 一致）
 * @property unlockedAt 解锁时间戳，null 表示未解锁
 */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val unlockedAt: Long? = null
)
