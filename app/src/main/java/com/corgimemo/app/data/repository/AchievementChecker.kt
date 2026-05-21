package com.corgimemo.app.data.repository

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
    private val taskDailyStatsRepository: TaskDailyStatsRepository,
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

        // 获取任务分类类型
        val category = completedTodo.categoryId?.let {
            runCatching { categoryRepository.getCategoryById(it) }.getOrNull()
        }
        val categoryType = category?.type ?: CategoryType.LIFE

        // 记录每日统计
        taskDailyStatsRepository.recordTaskCompletion(categoryType)

        // 1. 检测首次完成（totalCompleted 现在应该 >= 1）
        checkFirstComplete(unlockedIds, corgiData.totalCompleted + 1)

        // 2. 检测累计完成成就
        checkCumulativeTotal(unlockedIds, corgiData.totalCompleted + 1)

        // 3. 检测分类成就（包含新的成就）
        checkCategoryComplete(completedTodo, categoryType, unlockedIds)

        // 4. 检测单天完成
        checkDailyTotal(unlockedIds)

        // 5. 检测早起达人（如果是今天第一个完成的任务）
        checkEarlyBird(completedTodo.completedAt ?: System.currentTimeMillis(), corgiData)

        // 6. 检测加班终结者（22:00前完成）
        checkEarlyEnd(completedTodo.completedAt ?: System.currentTimeMillis(), unlockedIds)
    }

    /**
     * 每日首次打开时调用
     * 检测：连续天数成就、连续工作天数、连续学习天数
     */
    suspend fun checkOnDailyOpen() = withContext(ioDispatcher) {
        val corgiData = corgiRepository.getCorgiData() ?: return@withContext
        val unlockedIds = achievementRepository.getUnlockedIds().toSet()

        // 检测连续天数成就
        checkConsecutiveDays(unlockedIds, corgiData.consecutiveDays)

        // 检测连续工作天数成就
        checkConsecutiveWorkDays(unlockedIds)

        // 检测连续学习天数成就
        checkConsecutiveStudyDays(unlockedIds)
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
     * 学习：study_30、study_50、study_100、study_200
     * 工作：work_first、work_20、work_50、work_100、work_monthly_30
     * 生活：fitness_30
     * 娱乐：entertainment_20
     */
    private suspend fun checkCategoryComplete(
        completedTodo: TodoItem,
        categoryType: Int,
        unlockedIds: Set<String>
    ) {
        when (categoryType) {
            CategoryType.STUDY -> {
                // 学习分类
                val studyCount = todoRepository.getCompletedCountByCategoryType(CategoryType.STUDY)

                // study_30 笔记达人
                if (!unlockedIds.contains(NewAchievementId.STUDY_30) && studyCount >= 30) {
                    unlockAchievement(NewAchievementId.STUDY_30)
                }

                // study_50 学习之星
                if (!unlockedIds.contains(NewAchievementId.STUDY_50) && studyCount >= 50) {
                    unlockAchievement(NewAchievementId.STUDY_50)
                }

                // study_100 学霸
                if (!unlockedIds.contains(NewAchievementId.STUDY_100) && studyCount >= 100) {
                    unlockAchievement(NewAchievementId.STUDY_100)
                }

                // study_200 毕业在望
                if (!unlockedIds.contains(NewAchievementId.STUDY_200) && studyCount >= 200) {
                    unlockAchievement(NewAchievementId.STUDY_200)
                }

                // study_semester_100 奖学金候选人（本学期）
                val semesterStudyCount = taskDailyStatsRepository.getSemesterStudyCompleted()
                if (!unlockedIds.contains(NewAchievementId.STUDY_SEMESTER_100) && semesterStudyCount >= 100) {
                    unlockAchievement(NewAchievementId.STUDY_SEMESTER_100)
                }
            }

            CategoryType.WORK -> {
                // 工作分类
                val workCount = todoRepository.getCompletedCountByCategoryType(CategoryType.WORK)

                // work_first 职场新人
                if (!unlockedIds.contains(NewAchievementId.WORK_FIRST) && workCount >= 1) {
                    unlockAchievement(NewAchievementId.WORK_FIRST)
                }

                // work_project 项目达人
                if (!unlockedIds.contains(NewAchievementId.WORK_PROJECT) && workCount >= 20) {
                    unlockAchievement(NewAchievementId.WORK_PROJECT)
                }

                // work_20 工作能手
                if (!unlockedIds.contains(NewAchievementId.WORK_20) && workCount >= 50) {
                    unlockAchievement(NewAchievementId.WORK_20)
                }

                // work_100 职场精英
                if (!unlockedIds.contains(NewAchievementId.WORK_100) && workCount >= 100) {
                    unlockAchievement(NewAchievementId.WORK_100)
                }

                // work_monthly_30 KPI达成
                val monthlyWorkCount = taskDailyStatsRepository.getMonthlyWorkCompleted()
                if (!unlockedIds.contains(NewAchievementId.WORK_MONTHLY_30) && monthlyWorkCount >= 30) {
                    unlockAchievement(NewAchievementId.WORK_MONTHLY_30)
                }
            }

            CategoryType.LIFE -> {
                // 生活分类（包含健身、日常等）
                val lifeCount = todoRepository.getCompletedCountByCategoryType(CategoryType.LIFE)

                // fitness_30 运动达人
                if (!unlockedIds.contains(NewAchievementId.FITNESS_30) && lifeCount >= 30) {
                    unlockAchievement(NewAchievementId.FITNESS_30)
                }
            }

            else -> {
                // 自定义/娱乐分类
                val entertainmentCount = todoRepository.getCompletedCountByCategoryType(categoryType)

                // entertainment_20 娱乐达人
                if (!unlockedIds.contains(NewAchievementId.ENTERTAINMENT_20) && entertainmentCount >= 20) {
                    unlockAchievement(NewAchievementId.ENTERTAINMENT_20)
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
     * 检测连续工作天数成就
     * work_promotion（30天）
     */
    private suspend fun checkConsecutiveWorkDays(unlockedIds: Set<String>) {
        val consecutiveWorkDays = taskDailyStatsRepository.getConsecutiveWorkDays()

        if (!unlockedIds.contains(NewAchievementId.WORK_PROMOTION) && consecutiveWorkDays >= 30) {
            unlockAchievement(NewAchievementId.WORK_PROMOTION)
        }
    }

    /**
     * 检测连续学习天数成就
     * study_exam_week（7天）
     */
    private suspend fun checkConsecutiveStudyDays(unlockedIds: Set<String>) {
        val consecutiveStudyDays = taskDailyStatsRepository.getConsecutiveStudyDays()

        if (!unlockedIds.contains(NewAchievementId.STUDY_EXAM_WEEK) && consecutiveStudyDays >= 7) {
            unlockAchievement(NewAchievementId.STUDY_EXAM_WEEK)
        }
    }

    /**
     * 检测早起达人成就
     * early_7_days（连续7天早上8点前完成任务）
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
     * 检测加班终结者成就
     * work_early_end（连续3天在22:00前完成任务）
     */
    private suspend fun checkEarlyEnd(completedAt: Long, unlockedIds: Set<String>) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = completedAt

        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // 检查是否在晚上 10 点前完成
        if (hour >= 22) {
            // 不是早结束，不需要检测
            return
        }

        // 简化实现：检测今日工作任务完成数
        val todayStats = taskDailyStatsRepository.getTodayStats()
        if (todayStats != null && todayStats.workCompleted > 0) {
            // 这里可以根据连续天数逻辑扩展
            // 简化：如果今天工作任务数 > 0 且在22点前完成，可作为一个简化条件
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
     * all_achievements（解锁其他25个基础成就）
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
