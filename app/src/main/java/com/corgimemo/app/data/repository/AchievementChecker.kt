package com.corgimemo.app.data.repository

import com.corgimemo.app.animation.AchievementManager
import com.corgimemo.app.animation.OutfitManager
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementCondition
import com.corgimemo.app.data.model.AchievementDefinition
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.NewAchievementId
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 成就检测器
 * 负责检测各种成就条件并在满足时解锁成就
 */
@Singleton
class AchievementChecker @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val todoRepository: TodoRepository,
    private val corgiRepository: CorgiRepository,
    private val categoryRepository: CategoryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 成就解锁事件流
     * 用于通知 UI 层显示成就解锁弹窗
     */
    private val _achievementUnlockEvents = MutableSharedFlow<Achievement>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val achievementUnlockEvents: SharedFlow<Achievement> = _achievementUnlockEvents.asSharedFlow()

    /**
     * 任务完成时调用
     * 检测：首次完成、累计完成、单天完成、分类完成、早起
     *
     * @param completedTodo 刚完成的待办项
     */
    suspend fun checkOnTaskComplete(completedTodo: TodoItem) = withContext(ioDispatcher) {
        val corgiData = corgiRepository.getCorgiData() ?: return@withContext
        val unlockedIds = achievementRepository.getUnlockedIds().toSet()

        // 1. 检测首次完成（totalCompleted 现在应该 >= 1）
        checkFirstComplete(unlockedIds, corgiData.totalCompleted + 1)

        // 2. 检测累计完成成就
        checkCumulativeTotal(unlockedIds, corgiData.totalCompleted + 1)

        // 3. 检测分类成就
        checkCategoryComplete(completedTodo, unlockedIds)

        // 4. 检测单天完成
        checkDailyTotal(unlockedIds)

        // 5. 检测早起达人（如果是今天第一个完成的任务）
        checkEarlyBird(completedTodo.completedAt ?: System.currentTimeMillis(), corgiData)
    }

    /**
     * 每日首次打开时调用
     * 检测：连续天数成就
     */
    suspend fun checkOnDailyOpen() = withContext(ioDispatcher) {
        val corgiData = corgiRepository.getCorgiData() ?: return@withContext
        val unlockedIds = achievementRepository.getUnlockedIds().toSet()

        // 检测连续天数成就
        checkConsecutiveDays(unlockedIds, corgiData.consecutiveDays)
    }

    /**
     * 柯基升级时调用
     * 检测：柯基等级成就
     *
     * @param newLevel 新等级
     */
    suspend fun checkOnLevelUp(newLevel: Int) = withContext(ioDispatcher) {
        val unlockedIds = achievementRepository.getUnlockedIds().toSet()
        checkCorgiLevel(unlockedIds, newLevel)
    }

    // ==================== 私有检测方法 ====================

    /**
     * 检测首次完成成就
     * 完成第 1 个任务时解锁
     */
    private suspend fun checkFirstComplete(unlockedIds: Set<String>, totalCompleted: Int) {
        val achievementId = NewAchievementId.FIRST_COMPLETE
        if (!unlockedIds.contains(achievementId) && totalCompleted >= 1) {
            unlockAchievement(achievementId)
        }
    }

    /**
     * 检测累计完成成就
     * total_100（100个）、total_500（500个）
     */
    private suspend fun checkCumulativeTotal(unlockedIds: Set<String>, totalCompleted: Int) {
        // total_100
        val total100Id = NewAchievementId.TOTAL_100
        if (!unlockedIds.contains(total100Id) && totalCompleted >= 100) {
            unlockAchievement(total100Id)
        }

        // total_500
        val total500Id = NewAchievementId.TOTAL_500
        if (!unlockedIds.contains(total500Id) && totalCompleted >= 500) {
            unlockAchievement(total500Id)
        }
    }

    /**
     * 检测分类完成成就
     * 学习：study_50（50个）、study_100（100个）
     * 工作：work_100（100个）
     * 生活：fitness_30（30个）
     */
    private suspend fun checkCategoryComplete(completedTodo: TodoItem, unlockedIds: Set<String>) {
        val category = completedTodo.categoryId?.let {
            runCatching { categoryRepository.getCategoryById(it) }.getOrNull()
        }

        val categoryType = category?.type ?: CategoryType.LIFE

        when (categoryType) {
            CategoryType.STUDY -> {
                // 学习分类
                val studyCount = todoRepository.getCompletedCountByCategoryType(CategoryType.STUDY)

                // study_50
                if (!unlockedIds.contains(NewAchievementId.STUDY_50) && studyCount >= 50) {
                    unlockAchievement(NewAchievementId.STUDY_50)
                }

                // study_100
                if (!unlockedIds.contains(NewAchievementId.STUDY_100) && studyCount >= 100) {
                    unlockAchievement(NewAchievementId.STUDY_100)
                }
            }

            CategoryType.WORK -> {
                // 工作分类
                val workCount = todoRepository.getCompletedCountByCategoryType(CategoryType.WORK)

                // work_100
                if (!unlockedIds.contains(NewAchievementId.WORK_100) && workCount >= 100) {
                    unlockAchievement(NewAchievementId.WORK_100)
                }
            }

            CategoryType.LIFE -> {
                // 生活分类（包含健身、日常等）
                val lifeCount = todoRepository.getCompletedCountByCategoryType(CategoryType.LIFE)

                // fitness_30
                if (!unlockedIds.contains(NewAchievementId.FITNESS_30) && lifeCount >= 30) {
                    unlockAchievement(NewAchievementId.FITNESS_30)
                }
            }
        }
    }

    /**
     * 检测单天完成成就
     * daily_10_tasks（单天10个）
     */
    private suspend fun checkDailyTotal(unlockedIds: Set<String>) {
        val todayCount = todoRepository.getCompletedCountToday()

        if (!unlockedIds.contains(NewAchievementId.DAILY_10_TASKS) && todayCount >= 10) {
            unlockAchievement(NewAchievementId.DAILY_10_TASKS)
        }
    }

    /**
     * 检测连续天数成就
     * consecutive_7_days（7天）、consecutive_30_days（30天）
     */
    private suspend fun checkConsecutiveDays(unlockedIds: Set<String>, consecutiveDays: Int) {
        // consecutive_7_days
        if (!unlockedIds.contains(NewAchievementId.CONSECUTIVE_7_DAYS) && consecutiveDays >= 7) {
            unlockAchievement(NewAchievementId.CONSECUTIVE_7_DAYS)
        }

        // consecutive_30_days
        if (!unlockedIds.contains(NewAchievementId.CONSECUTIVE_30_DAYS) && consecutiveDays >= 30) {
            unlockAchievement(NewAchievementId.CONSECUTIVE_30_DAYS)
        }
    }

    /**
     * 检测早起达人成就
     * early_7_days（连续7天早上8点前完成任务）
     *
     * 逻辑：
     * - 如果完成时间 < 当天 8:00，则检查连续早起天数
     * - 如果 lastEarlyDate == 昨天，则 consecutiveEarlyDays++
     * - 否则 consecutiveEarlyDays = 1
     */
    private suspend fun checkEarlyBird(completedAt: Long, corgiData: com.corgimemo.app.data.model.CorgiData) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = completedAt

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val completedDate = dateFormat.format(completedAt)

        // 检查是否在早上 8 点前完成
        if (hour >= 8) {
            // 不是早起，重置连续早起天数
            if (corgiData.consecutiveEarlyDays > 0) {
                updateEarlyDays(0, "")
            }
            return
        }

        // 是早起完成的任务
        val yesterday = getYesterdayString()
        val newEarlyDays: Int
        val newLastEarlyDate: String

        if (corgiData.lastEarlyDate == yesterday) {
            // 昨天也早起了，连续
            newEarlyDays = corgiData.consecutiveEarlyDays + 1
            newLastEarlyDate = completedDate
        } else {
            // 今天是新的连续开始
            newEarlyDays = 1
            newLastEarlyDate = completedDate
        }

        // 更新数据
        updateEarlyDays(newEarlyDays, newLastEarlyDate)

        // 检测成就
        val unlockedIds = achievementRepository.getUnlockedIds().toSet()
        if (!unlockedIds.contains(NewAchievementId.EARLY_7_DAYS) && newEarlyDays >= 7) {
            unlockAchievement(NewAchievementId.EARLY_7_DAYS)
        }
    }

    /**
     * 检测柯基等级成就
     * corgi_level_5（5级）、corgi_level_10（10级）
     */
    private suspend fun checkCorgiLevel(unlockedIds: Set<String>, newLevel: Int) {
        // corgi_level_5
        if (!unlockedIds.contains(NewAchievementId.CORGI_LEVEL_5) && newLevel >= 5) {
            unlockAchievement(NewAchievementId.CORGI_LEVEL_5)
        }

        // corgi_level_10
        if (!unlockedIds.contains(NewAchievementId.CORGI_LEVEL_10) && newLevel >= 10) {
            unlockAchievement(NewAchievementId.CORGI_LEVEL_10)
        }
    }

    /**
     * 检测复合成就（解锁所有成就解锁披风）
     * all_achievements（解锁其他13个基础成就）
     */
    private suspend fun checkAllAchievements() {
        val unlockedIds = achievementRepository.getUnlockedIds().toSet()

        if (unlockedIds.contains(NewAchievementId.ALL_ACHIEVEMENTS)) {
            return  // 已解锁
        }

        // 统计基础成就解锁数量
        val basicUnlockedCount = AchievementDefinition.basicAchievementIds.count { unlockedIds.contains(it) }

        if (basicUnlockedCount >= AchievementDefinition.BASIC_COUNT) {
            unlockAchievement(NewAchievementId.ALL_ACHIEVEMENTS)
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解锁成就并发送事件
     *
     * @param achievementId 成就 ID
     * @return 是否已解锁
     */
    private suspend fun unlockAchievement(achievementId: String): Boolean {
        val achievement = achievementRepository.unlock(achievementId) ?: return false

        // 发送事件
        _achievementUnlockEvents.emit(achievement)

        // 解锁对应的装扮
        unlockOutfitForAchievement(achievementId)

        // 检测复合成就
        checkAllAchievements()

        return true
    }

    /**
     * 解锁成就对应的装扮
     */
    private suspend fun unlockOutfitForAchievement(achievementId: String) {
        val outfitId = AchievementDefinition.getOutfitForAchievement(achievementId)
            ?: return  // 没有关联装扮

        val corgiData = corgiRepository.getCorgiData() ?: return

        if (!OutfitManager.isOutfitUnlocked(outfitId, corgiData.unlockedOutfits)) {
            val updatedOutfits = OutfitManager.addOutfits(
                corgiData.unlockedOutfits,
                listOf(outfitId)
            )
            corgiRepository.updateUnlockedOutfits(updatedOutfits)
        }
    }

    /**
     * 更新连续早起天数
     */
    private suspend fun updateEarlyDays(consecutiveEarlyDays: Int, lastEarlyDate: String) {
        val corgiData = corgiRepository.getCorgiData() ?: return

        val updated = corgiData.copy(
            consecutiveEarlyDays = consecutiveEarlyDays,
            lastEarlyDate = lastEarlyDate
        )
        corgiRepository.updateCorgi(updated)
    }

    /**
     * 获取昨天的日期字符串
     */
    private fun getYesterdayString(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat.format(calendar.time)
    }
}
