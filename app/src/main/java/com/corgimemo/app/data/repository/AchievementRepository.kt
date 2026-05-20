package com.corgimemo.app.data.repository

import com.corgimemo.app.animation.AchievementManager
import com.corgimemo.app.data.local.db.AchievementDao
import com.corgimemo.app.data.local.db.AchievementEntity
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementCondition
import com.corgimemo.app.data.model.AchievementDefinition
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 成就仓库
 * 负责成就的存储、查询、初始化和数据迁移
 */
@Singleton
class AchievementRepository @Inject constructor(
    private val achievementDao: AchievementDao,
    private val corgiRepository: CorgiRepository,
    private val todoRepository: TodoRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private var isInitialized = false

    /**
     * 初始化成就系统
     * 1. 插入所有成就（忽略已存在的）
     * 2. 从旧 JSON 数据迁移
     */
    suspend fun initialize() = withContext(ioDispatcher) {
        if (isInitialized) return@withContext

        // 1. 插入所有成就
        val entities = AchievementDefinition.allAchievements.map {
            AchievementEntity(id = it.id, unlockedAt = null)
        }
        achievementDao.insertAll(entities)

        // 2. 从旧 JSON 数据迁移
        migrateFromOldData()

        isInitialized = true
    }

    /**
     * 从旧的 unlockedAchievements JSON 迁移数据到新表
     */
    private suspend fun migrateFromOldData() {
        val corgiData = corgiRepository.getCorgiData() ?: return
        val oldIds = AchievementManager.parseAchievementIds(corgiData.unlockedAchievements)

        if (oldIds.isEmpty()) return

        oldIds.forEach { oldId ->
            val newId = AchievementDefinition.mapOldToNewId(oldId)
            if (newId != null && !achievementDao.isUnlocked(newId)) {
                achievementDao.unlock(newId, System.currentTimeMillis())
            }
        }
    }

    /**
     * 获取所有成就及其解锁状态
     *
     * @return 成就和解锁状态的列表
     */
    suspend fun getAllAchievements(): List<Pair<Achievement, Boolean>> = withContext(ioDispatcher) {
        val entities = achievementDao.getAll()
        val unlockedIds = entities.filter { it.unlockedAt != null }.map { it.id }.toSet()

        AchievementDefinition.allAchievements.map { achievement ->
            val entity = entities.find { it.id == achievement.id }
            val isUnlocked = unlockedIds.contains(achievement.id)
            val achievementWithTime = entity?.unlockedAt?.let { timestamp ->
                achievement.copy(unlockedAt = timestamp)
            } ?: achievement
            achievementWithTime to isUnlocked
        }
    }

    /**
     * 获取已解锁的成就列表
     *
     * @return 已解锁的成就列表
     */
    suspend fun getUnlockedAchievements(): List<Achievement> = withContext(ioDispatcher) {
        val unlockedEntities = achievementDao.getUnlocked()
        val unlockedIds = unlockedEntities.associate { it.id to it.unlockedAt }

        AchievementDefinition.allAchievements
            .filter { unlockedIds.containsKey(it.id) }
            .map { it.copy(unlockedAt = unlockedIds[it.id]) }
    }

    /**
     * 获取已解锁成就的数量
     *
     * @return 已解锁数量
     */
    suspend fun getUnlockedCount(): Int = withContext(ioDispatcher) {
        achievementDao.getUnlockedCount()
    }

    /**
     * 检查成就是否已解锁
     *
     * @param achievementId 成就 ID
     * @return 是否已解锁
     */
    suspend fun isUnlocked(achievementId: String): Boolean = withContext(ioDispatcher) {
        achievementDao.isUnlocked(achievementId)
    }

    /**
     * 解锁成就
     *
     * @param achievementId 成就 ID
     * @return 解锁的成就对象，如果已解锁或不存在返回 null
     */
    suspend fun unlock(achievementId: String): Achievement? = withContext(ioDispatcher) {
        // 检查是否已解锁
        if (achievementDao.isUnlocked(achievementId)) {
            return@withContext null
        }

        // 更新数据库
        achievementDao.unlock(achievementId, System.currentTimeMillis())

        // 返回成就对象
        AchievementDefinition.getAchievementById(achievementId)?.copy(
            unlockedAt = System.currentTimeMillis()
        )
    }

    /**
     * 获取成就的当前进度
     *
     * @param achievementId 成就 ID
     * @return 当前进度值
     */
    suspend fun getProgress(achievementId: String): Int = withContext(ioDispatcher) {
        val achievement = AchievementDefinition.getAchievementById(achievementId)
            ?: return@withContext 0

        val corgiData = corgiRepository.getCorgiData() ?: return@withContext 0

        when (achievement.condition) {
            AchievementCondition.CUMULATIVE_TOTAL -> corgiData.totalCompleted
            AchievementCondition.CUMULATIVE_STUDY -> todoRepository.getCompletedCountByCategoryType(1)
            AchievementCondition.CUMULATIVE_WORK -> todoRepository.getCompletedCountByCategoryType(0)
            AchievementCondition.CUMULATIVE_LIFE -> todoRepository.getCompletedCountByCategoryType(2)
            AchievementCondition.CONSECUTIVE_DAYS -> corgiData.consecutiveDays
            AchievementCondition.CORGI_LEVEL -> corgiData.level
            AchievementCondition.EARLY_BIRD -> corgiData.consecutiveEarlyDays
            AchievementCondition.DAILY_TOTAL -> todoRepository.getCompletedCountToday()
            AchievementCondition.ALL_ACHIEVEMENTS -> achievementDao.getUnlockedIds()
                .count { it != AchievementDefinition.NewAchievementId.ALL_ACHIEVEMENTS }
            AchievementCondition.FIRST_COMPLETE -> corgiData.totalCompleted
        }
    }

    /**
     * 获取所有已解锁成就的 ID 列表
     *
     * @return 已解锁成就 ID 列表
     */
    suspend fun getUnlockedIds(): List<String> = withContext(ioDispatcher) {
        achievementDao.getUnlockedIds()
    }

    /**
     * 获取总成就数量
     *
     * @return 总数量
     */
    fun getTotalCount(): Int = AchievementDefinition.TOTAL_COUNT

    /**
     * 获取基础成就数量（不包含复合成就）
     *
     * @return 基础成就数量
     */
    fun getBasicCount(): Int = AchievementDefinition.BASIC_COUNT
}
