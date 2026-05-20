package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementCondition
import com.corgimemo.app.data.repository.AchievementRepository
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 成就页面视图模型
 * 管理成就墙页面的数据和状态
 */
@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val todoRepository: TodoRepository,
    private val corgiRepository: CorgiRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    // 所有成就列表（带解锁状态和当前进度）
    private val _achievementsWithProgress =
        MutableStateFlow<List<Triple<Achievement, Boolean, Int?>>>(emptyList())
    val achievementsWithProgress: StateFlow<List<Triple<Achievement, Boolean, Int?>>> =
        _achievementsWithProgress.asStateFlow()

    // 已解锁成就数量
    private val _unlockedCount = MutableStateFlow(0)
    val unlockedCount: StateFlow<Int> = _unlockedCount.asStateFlow()

    // 总成就数量
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    // 选中的成就（用于显示详情）
    private val _selectedAchievement = MutableStateFlow<Achievement?>(null)
    val selectedAchievement: StateFlow<Achievement?> = _selectedAchievement.asStateFlow()

    // 选中成就的解锁状态
    private val _selectedIsUnlocked = MutableStateFlow(false)
    val selectedIsUnlocked: StateFlow<Boolean> = _selectedIsUnlocked.asStateFlow()

    // 选中成就的当前进度
    private val _selectedProgress = MutableStateFlow<Int?>(null)
    val selectedProgress: StateFlow<Int?> = _selectedProgress.asStateFlow()

    // 初始化
    init {
        loadAchievements()
    }

    /**
     * 加载所有成就数据
     * 包括解锁状态和当前进度
     */
    fun loadAchievements() {
        viewModelScope.launch {
            val achievements = achievementRepository.getAllAchievements()
            val unlockedIds = achievementRepository.getUnlockedAchievementIds()

            val result = mutableListOf<Triple<Achievement, Boolean, Int?>>()
            var unlocked = 0

            achievements.forEach { achievement ->
                val isUnlocked = unlockedIds.contains(achievement.id)
                if (isUnlocked) {
                    unlocked++
                }

                // 计算当前进度（只对未解锁成就）
                val progress = if (!isUnlocked) {
                    getCurrentProgress(achievement)
                } else {
                    null
                }

                // 为已解锁成就添加解锁时间
                val achievementWithTime = if (isUnlocked) {
                    val unlockedAt = achievementRepository.getUnlockedAt(achievement.id)
                    achievement.copy(unlockedAt = unlockedAt)
                } else {
                    achievement
                }

                result.add(Triple(achievementWithTime, isUnlocked, progress))
            }

            _achievementsWithProgress.value = result
            _unlockedCount.value = unlocked
            _totalCount.value = achievements.size
        }
    }

    /**
     * 获取成就的当前进度
     * 根据成就条件类型查询相应数据
     */
    private suspend fun getCurrentProgress(achievement: Achievement): Int {
        return when (achievement.condition) {
            AchievementCondition.COMPLETED_TASKS -> {
                todoRepository.getCompletedCount()
            }
            AchievementCondition.DAILY_OPEN -> {
                corgiRepository.getConsecutiveOpenDays()
            }
            AchievementCondition.LOGIN_DAYS -> {
                corgiRepository.getTotalOpenDays()
            }
            AchievementCondition.LEVEL -> {
                corgiRepository.getCorgiData()?.level ?: 1
            }
            AchievementCondition.STUDY_TASKS -> {
                getCompletedCountByCategoryType("study")
            }
            AchievementCondition.WORK_TASKS -> {
                getCompletedCountByCategoryType("work")
            }
            AchievementCondition.LIFE_TASKS -> {
                getCompletedCountByCategoryType("life")
            }
            AchievementCondition.HEALTH_TASKS -> {
                getCompletedCountByCategoryType("health")
            }
            AchievementCondition.CATEGORIES -> {
                categoryRepository.getActiveCategoriesCount()
            }
            AchievementCondition.TODO_NOTES -> {
                todoRepository.getTotalCount()
            }
            AchievementCondition.ALL_ACHIEVEMENTS -> {
                achievementRepository.getUnlockedAchievementIds().size
            }
        }
    }

    /**
     * 根据分类类型获取已完成任务数量
     */
    private suspend fun getCompletedCountByCategoryType(type: String): Int {
        val category = categoryRepository.getCategoryByType(type)
        return if (category != null) {
            todoRepository.getCompletedCountByCategoryId(category.id)
        } else {
            0
        }
    }

    /**
     * 选中成就（显示详情）
     */
    fun selectAchievement(achievement: Achievement, isUnlocked: Boolean, progress: Int?) {
        _selectedAchievement.value = achievement
        _selectedIsUnlocked.value = isUnlocked
        _selectedProgress.value = progress
    }

    /**
     * 取消选中成就（关闭详情）
     */
    fun clearSelectedAchievement() {
        _selectedAchievement.value = null
        _selectedIsUnlocked.value = false
        _selectedProgress.value = null
    }

    /**
     * 刷新成就数据
     */
    fun refresh() {
        loadAchievements()
    }
}
