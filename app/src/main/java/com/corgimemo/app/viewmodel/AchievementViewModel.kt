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

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val corgiRepository: CorgiRepository
) : ViewModel() {

    private val _achievementsWithProgress =
        MutableStateFlow<List<Triple<Achievement, Boolean, Int?>>>(emptyList())
    val achievementsWithProgress: StateFlow<List<Triple<Achievement, Boolean, Int?>>> =
        _achievementsWithProgress.asStateFlow()

    private val _unlockedCount = MutableStateFlow(0)
    val unlockedCount: StateFlow<Int> = _unlockedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _selectedAchievement = MutableStateFlow<Achievement?>(null)
    val selectedAchievement: StateFlow<Achievement?> = _selectedAchievement.asStateFlow()

    private val _selectedIsUnlocked = MutableStateFlow(false)
    val selectedIsUnlocked: StateFlow<Boolean> = _selectedIsUnlocked.asStateFlow()

    private val _selectedProgress = MutableStateFlow<Int?>(null)
    val selectedProgress: StateFlow<Int?> = _selectedProgress.asStateFlow()

    private val _corgiData = MutableStateFlow<CorgiData?>(null)
    val corgiData: StateFlow<CorgiData?> = _corgiData.asStateFlow()

    private val _currentStage = MutableStateFlow<AchievementStage>(AchievementStage.BEGINNER)
    val currentStage: StateFlow<AchievementStage> = _currentStage.asStateFlow()

    private val _corgiPoseForStage = MutableStateFlow<CorgiPose>(PoseManager.getDefaultPose())
    val corgiPoseForStage: StateFlow<CorgiPose> = _corgiPoseForStage.asStateFlow()

    private val _stageUnlockedCount = MutableStateFlow(0)
    val stageUnlockedCount: StateFlow<Int> = _stageUnlockedCount.asStateFlow()

    private val _stageTotalCount = MutableStateFlow(0)
    val stageTotalCount: StateFlow<Int> = _stageTotalCount.asStateFlow()

    private val _nextUnlockableAchievement = MutableStateFlow<Achievement?>(null)
    val nextUnlockableAchievement: StateFlow<Achievement?> = _nextUnlockableAchievement.asStateFlow()

    private val _nextUnlockableProgress = MutableStateFlow<Int?>(null)
    val nextUnlockableProgress: StateFlow<Int?> = _nextUnlockableProgress.asStateFlow()

    init {
        loadAchievements()
        loadCorgiData()
    }

    fun loadAchievements() {
        viewModelScope.launch {
            achievementRepository.initialize()

            val achievementsWithStatus = achievementRepository.getAllAchievements()
            val unlockedCount = achievementRepository.getUnlockedCount()
            val totalCount = achievementRepository.getTotalCount()

            val result = mutableListOf<Triple<Achievement, Boolean, Int?>>()

            achievementsWithStatus.forEach { (achievement, isUnlocked) ->
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

            updateStageAndPose(unlockedCount)
            updateStageProgress(result, unlockedCount)
        }
    }

    private fun updateStageProgress(
        achievements: List<Triple<Achievement, Boolean, Int?>>,
        unlockedCount: Int
    ) {
        val stage = AchievementDefinition.getCurrentStage(unlockedCount)

        val stageAchievements = achievements.filter { it.first.stage == stage }
        val stageUnlocked = stageAchievements.count { it.second }
        val stageTotal = stageAchievements.size

        _stageUnlockedCount.value = stageUnlocked
        _stageTotalCount.value = stageTotal

        val nextAchievement = stageAchievements
            .filter { !it.second }
            .sortedBy { it.first.threshold }
            .firstOrNull()

        _nextUnlockableAchievement.value = nextAchievement?.first
        _nextUnlockableProgress.value = nextAchievement?.third
    }

    private fun loadCorgiData() {
        viewModelScope.launch {
            val data = corgiRepository.getCorgiData()
            _corgiData.value = data
        }
    }

    private fun updateStageAndPose(unlockedCount: Int) {
        val stage = AchievementDefinition.getCurrentStage(unlockedCount)
        _currentStage.value = stage

        val pose = when (stage) {
            AchievementStage.BEGINNER -> CorgiPose.LIE
            AchievementStage.GROWTH -> CorgiPose.SIT
            AchievementStage.LEAP -> CorgiPose.RUN
            AchievementStage.PEAK -> CorgiPose.STAND
        }
        _corgiPoseForStage.value = pose
    }

    fun selectAchievement(achievement: Achievement, isUnlocked: Boolean, progress: Int?) {
        _selectedAchievement.value = achievement
        _selectedIsUnlocked.value = isUnlocked
        _selectedProgress.value = progress
    }

    fun clearSelectedAchievement() {
        _selectedAchievement.value = null
        _selectedIsUnlocked.value = false
        _selectedProgress.value = null
    }

    fun refresh() {
        loadAchievements()
    }
}

private object AchievementDefinition {
    fun getCurrentStage(unlockedCount: Int): AchievementStage {
        return when {
            unlockedCount >= com.corgimemo.app.data.model.AchievementStage.PEAK.requiredUnlocked ->
                com.corgimemo.app.data.model.AchievementStage.PEAK
            unlockedCount >= com.corgimemo.app.data.model.AchievementStage.LEAP.requiredUnlocked ->
                com.corgimemo.app.data.model.AchievementStage.LEAP
            unlockedCount >= com.corgimemo.app.data.model.AchievementStage.GROWTH.requiredUnlocked ->
                com.corgimemo.app.data.model.AchievementStage.GROWTH
            else -> com.corgimemo.app.data.model.AchievementStage.BEGINNER
        }
    }
}