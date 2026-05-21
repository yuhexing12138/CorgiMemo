package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.animation.CorgiPose
import com.corgimemo.app.animation.PoseManager
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementStage
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.repository.AchievementRepository
import com.corgimemo.app.data.repository.CorgiRepository
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
    private val corgiRepository: CorgiRepository
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

    // 柯基数据
    private val _corgiData = MutableStateFlow<CorgiData?>(null)
    val corgiData: StateFlow<CorgiData?> = _corgiData.asStateFlow()

    // 当前成就阶段
    private val _currentStage = MutableStateFlow<AchievementStage>(AchievementStage.BEGINNER)
    val currentStage: StateFlow<AchievementStage> = _currentStage.asStateFlow()

    // 根据成就阶段获取柯基姿态
    private val _corgiPoseForStage = MutableStateFlow<CorgiPose>(PoseManager.getDefaultPose())
    val corgiPoseForStage: StateFlow<CorgiPose> = _corgiPoseForStage.asStateFlow()

    // 初始化
    init {
        loadAchievements()
        loadCorgiData()
    }

    /**
     * 加载所有成就数据
     * 包括解锁状态和当前进度
     */
    fun loadAchievements() {
        viewModelScope.launch {
            // 先初始化成就系统（确保数据库有数据）
            achievementRepository.initialize()
            
            // 获取所有成就及其解锁状态
            val achievementsWithStatus = achievementRepository.getAllAchievements()
            val unlockedCount = achievementRepository.getUnlockedCount()
            val totalCount = achievementRepository.getTotalCount()

            val result = mutableListOf<Triple<Achievement, Boolean, Int?>>()

            achievementsWithStatus.forEach { (achievement, isUnlocked) ->
                // 计算当前进度（只对未解锁成就）
                val progress = if (!isUnlocked) {
                    achievementRepository.getProgress(achievement.id)
                } else {
                    null
                }

                result.add(Triple(achievement, isUnlocked, progress))
            }

            _achievementsWithProgress.value = result
            _unlockedCount.value = unlockedCount
            _totalCount.value = totalCount

            // 根据已解锁数量计算当前阶段并更新柯基姿态
            updateStageAndPose(unlockedCount)
        }
    }

    /**
     * 加载柯基数据
     */
    private fun loadCorgiData() {
        viewModelScope.launch {
            val data = corgiRepository.getCorgiData()
            _corgiData.value = data
        }
    }

    /**
     * 根据已解锁成就数量更新阶段和柯基姿态
     */
    private fun updateStageAndPose(unlockedCount: Int) {
        // 计算当前阶段
        val stage = when {
            unlockedCount >= AchievementStage.PEAK.requiredUnlocked -> AchievementStage.PEAK
            unlockedCount >= AchievementStage.LEAP.requiredUnlocked -> AchievementStage.LEAP
            unlockedCount >= AchievementStage.GROWTH.requiredUnlocked -> AchievementStage.GROWTH
            else -> AchievementStage.BEGINNER
        }
        _currentStage.value = stage

        // 根据阶段设置柯基姿态
        val pose = when (stage) {
            AchievementStage.BEGINNER -> CorgiPose.LIE   // 初见阶段：趴卧（休闲）
            AchievementStage.GROWTH -> CorgiPose.SIT     // 成长阶段：坐立（专注）
            AchievementStage.LEAP -> CorgiPose.RUN       // 飞跃阶段：奔跑（兴奋）
            AchievementStage.PEAK -> CorgiPose.STAND    // 巅峰阶段：站立（骄傲）
        }
        _corgiPoseForStage.value = pose
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
