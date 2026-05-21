package com.corgimemo.app.data.repository

import com.corgimemo.app.animation.AchievementManager
import com.corgimemo.app.data.local.db.AchievementDao
import com.corgimemo.app.data.local.db.AchievementEntity
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementCondition
import com.corgimemo.app.data.model.AchievementDefinition
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.NewAchievementId
import com.corgimemo.app.model.UserType
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
    private val taskDailyStatsRepository: TaskDailyStatsRepository,
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
     * 根据用户类型获取可见的成就及其解锁状态
     *
     * @param userType 用户类型（上班族/学生）
     * @return 成就和解锁状态的列表
     */
    suspend fun getVisibleAchievements(userType: UserType): List<Pair<Achievement, Boolean>> = withContext(ioDispatcher) {
        val entities = achievementDao.getAll()
        val unlockedIds = entities.filter { it.unlockedAt != null }.map { it.id }.toSet()
        val visibleAchievements = AchievementDefinition.getVisibleAchievements(userType)

        visibleAchievements.map { achievement ->
            val entity = entities.find { it.id == achievement.id }
            val isUnlocked = unlockedIds.contains(achievement.id)
            val achievementWithTime = entity?.unlockedAt?.let { timestamp ->
                achievement.copy(unlockedAt = timestamp)
            } ?: achievement
            achievementWithTime to isUnlocked
        }
    }

    /**
     * 获取所有成就及其解锁状态（包含所有身份的成就）
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
     * 根据用户类型获取可见的已解锁成就数量
     *
     * @param userType 用户类型
     * @return 已解锁数量
     */
    suspend fun getVisibleUnlockedCount(userType: UserType): Int = withContext(ioDispatcher) {
        val unlockedIds = achievementDao.getUnlockedIds().toSet()
        val visibleIds = AchievementDefinition.getVisibleAchievementIds(userType)
        visibleIds.count { unlockedIds.contains(it) }
    }

    /**
     * 获取已解锁成就的数量（所有成就）
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
            AchievementCondition.CUMULATIVE_STUDY -> todoRepository.getCompletedCountByCategoryType(CategoryType.STUDY)
            AchievementCondition.CUMULATIVE_WORK -> todoRepository.getCompletedCountByCategoryType(CategoryType.WORK)
            AchievementCondition.CUMULATIVE_LIFE -> todoRepository.getCompletedCountByCategoryType(CategoryType.LIFE)
            AchievementCondition.CUMULATIVE_ENTERTAINMENT -> taskDailyStatsRepository.getTodayStats()?.entertainmentCompleted ?: 0
            AchievementCondition.CONSECUTIVE_DAYS -> corgiData.consecutiveDays
            AchievementCondition.CONSECUTIVE_WORK_DAYS -> taskDailyStatsRepository.getConsecutiveWorkDays()
            AchievementCondition.CONSECUTIVE_STUDY_DAYS -> taskDailyStatsRepository.getConsecutiveStudyDays()
            AchievementCondition.CORGI_LEVEL -> corgiData.level
            AchievementCondition.EARLY_BIRD -> corgiData.consecutiveEarlyDays
            AchievementCondition.DAILY_TOTAL -> todoRepository.getCompletedCountToday()
            AchievementCondition.MONTHLY_WORK -> taskDailyStatsRepository.getMonthlyWorkCompleted()
            AchievementCondition.SEMESTER_STUDY -> taskDailyStatsRepository.getSemesterStudyCompleted()
            AchievementCondition.BEFORE_TIME -> taskDailyStatsRepository.getTodayStats()?.workCompleted ?: 0
            AchievementCondition.ALL_ACHIEVEMENTS -> achievementDao.getUnlockedIds()
                .count { it != NewAchievementId.ALL_ACHIEVEMENTS }
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
     * 根据用户类型获取总成就数量
     *
     * @param userType 用户类型
     * @return 总数量
     */
    fun getVisibleTotalCount(userType: UserType): Int {
        return AchievementDefinition.getVisibleCount(userType)
    }

    /**
     * 获取总成就数量（所有身份）
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
