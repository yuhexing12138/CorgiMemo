package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 成就数据访问对象
 * 提供成就的增删改查操作
 */
@Dao
interface AchievementDao {

    /**
     * 查询所有成就实体
     *
     * @return 所有成就实体列表
     */
    @Query("SELECT * FROM achievements")
    suspend fun getAll(): List<AchievementEntity>

    /**
     * 查询所有已解锁的成就
     * 按解锁时间倒序排列
     *
     * @return 已解锁的成就实体列表
     */
    @Query("SELECT * FROM achievements WHERE unlockedAt IS NOT NULL ORDER BY unlockedAt DESC")
    suspend fun getUnlocked(): List<AchievementEntity>

    /**
     * 根据 ID 查询单个成就
     *
     * @param id 成就 ID
     * @return 成就实体，如果不存在返回 null
     */
    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun getById(id: String): AchievementEntity?

    /**
     * 插入单个成就（忽略已存在的）
     *
     * @param achievement 成就实体
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(achievement: AchievementEntity)

    /**
     * 批量插入成就（忽略已存在的）
     * 用于初始化所有成就
     *
     * @param achievements 成就实体列表
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(achievements: List<AchievementEntity>)

    /**
     * 解锁成就（更新解锁时间戳）
     *
     * @param id 成就 ID
     * @param timestamp 解锁时间戳
     */
    @Query("UPDATE achievements SET unlockedAt = :timestamp WHERE id = :id")
    suspend fun unlock(id: String, timestamp: Long)

    /**
     * 检查成就是否已解锁
     *
     * @param id 成就 ID
     * @return 是否已解锁
     */
    @Query("SELECT COUNT(*) > 0 FROM achievements WHERE id = :id AND unlockedAt IS NOT NULL")
    suspend fun isUnlocked(id: String): Boolean

    /**
     * 获取已解锁成就的数量
     *
     * @return 已解锁数量
     */
    @Query("SELECT COUNT(*) FROM achievements WHERE unlockedAt IS NOT NULL")
    suspend fun getUnlockedCount(): Int

    /**
     * 获取所有已解锁成就的 ID 列表
     *
     * @return 已解锁成就 ID 列表
     */
    @Query("SELECT id FROM achievements WHERE unlockedAt IS NOT NULL")
    suspend fun getUnlockedIds(): List<String>
}
