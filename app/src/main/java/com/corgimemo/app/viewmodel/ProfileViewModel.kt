package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.animation.Achievement
import com.corgimemo.app.animation.AchievementManager
import com.corgimemo.app.animation.LevelManager
import com.corgimemo.app.animation.LevelStage
import com.corgimemo.app.animation.Outfit
import com.corgimemo.app.animation.OutfitManager
import com.corgimemo.app.animation.OutfitRecommendation
import com.corgimemo.app.animation.SeasonalOutfitRecommender
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.MoodHistory
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.MoodHistoryRepository
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人中心视图模型
 * 管理成就展示、装扮选择、柯基名字修改等功能
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val corgiRepository: CorgiRepository,
    private val corgiPreferences: CorgiPreferences,
    private val todoRepository: TodoRepository,
    private val categoryRepository: CategoryRepository,
    private val moodHistoryRepository: MoodHistoryRepository
) : ViewModel() {

    private val _corgiData = MutableStateFlow<CorgiData?>(null)
    val corgiData: StateFlow<CorgiData?> = _corgiData.asStateFlow()

    private val _moodHistory7Days = MutableStateFlow<List<MoodHistory>>(emptyList())
    val moodHistory7Days: StateFlow<List<MoodHistory>> = _moodHistory7Days.asStateFlow()

    private val _achievements = MutableStateFlow<List<Pair<Achievement, Boolean>>>(emptyList())
    val achievements: StateFlow<List<Pair<Achievement, Boolean>>> = _achievements.asStateFlow()

    private val _outfits = MutableStateFlow<List<Pair<Outfit, Boolean>>>(emptyList())
    val outfits: StateFlow<List<Pair<Outfit, Boolean>>> = _outfits.asStateFlow()

    private val _levelStage = MutableStateFlow<LevelStage>(LevelStage.BABY)
    val levelStage: StateFlow<LevelStage> = _levelStage.asStateFlow()

    private val _levelProgress = MutableStateFlow<Float>(0f)
    val levelProgress: StateFlow<Float> = _levelProgress.asStateFlow()

    private val _progressText = MutableStateFlow("0/50")
    val progressText: StateFlow<String> = _progressText.asStateFlow()

    // ========== 触觉/音效反馈设置 ==========
    // 直接使用 corgiPreferences 的 Flow，响应式更新用户设置

    val hapticEnabled: StateFlow<Boolean> = corgiPreferences.hapticEnabled
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val soundEnabled: StateFlow<Boolean> = corgiPreferences.soundEnabled
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // ========== 预览模式状态 ==========

    private val _isPreviewMode = MutableStateFlow(false)
    val isPreviewMode: StateFlow<Boolean> = _isPreviewMode.asStateFlow()

    private val _previewOutfit = MutableStateFlow<String?>(null)
    val previewOutfit: StateFlow<String?> = _previewOutfit.asStateFlow()

    private var originalOutfit: String? = null

    // ========== 装扮推荐 ==========

    private val _recommendedOutfit = MutableStateFlow<OutfitRecommendation?>(null)
    val recommendedOutfit: StateFlow<OutfitRecommendation?> = _recommendedOutfit.asStateFlow()

    // ========== 敏感词汇列表 ==========

    private val sensitiveWords = listOf(
        "傻逼", "操", "尼玛", "草泥马", "fuck", "shit", "nigger", "bitch",
        "去死", "脑残", "智障", "白痴", "笨蛋", "废物", "垃圾"
    )

    init {
        loadCorgiData()
    }

    /**
     * 加载柯基数据
     */
    private fun loadCorgiData() {
        viewModelScope.launch {
            val data = corgiRepository.getCorgiData()
            _corgiData.value = data

            data?.let {
                val (level, progress) = LevelManager.getCurrentLevelAndProgress(it.experience)
                _levelStage.value = LevelManager.getLevelStage(level)
                _levelProgress.value = progress
                _progressText.value = LevelManager.getProgressText(it.experience)

                _achievements.value = AchievementManager.getAchievementsWithStatus(it.unlockedAchievements)
                _outfits.value = OutfitManager.getOutfitsWithStatus(it.unlockedOutfits)

                _recommendedOutfit.value = SeasonalOutfitRecommender.getCurrentRecommendation(
                    unlockedOutfitsJson = it.unlockedOutfits
                )
            }

            _moodHistory7Days.value = moodHistoryRepository.getLast7Days()
        }
    }

    /**
     * 选择装扮
     *
     * @param outfitId 装扮 ID（null 表示取消装扮）
     */
    fun selectOutfit(outfitId: String?) {
        viewModelScope.launch {
            corgiRepository.updateOutfit(outfitId)
            _corgiData.value = _corgiData.value?.copy(currentOutfit = outfitId)
        }
    }

    /**
     * 取消装扮（恢复默认）
     */
    fun unselectOutfit() {
        selectOutfit(null)
    }

    // ==================== 预览模式 ====================

    /**
     * 进入预览模式
     * 保存当前装扮，用于退出预览时恢复
     */
    fun enterPreviewMode() {
        originalOutfit = _corgiData.value?.currentOutfit
        _previewOutfit.value = originalOutfit
        _isPreviewMode.value = true
    }

    /**
     * 预览装扮（不保存到数据库）
     *
     * @param outfitId 装扮 ID
     */
    fun previewOutfit(outfitId: String?) {
        _previewOutfit.value = outfitId
    }

    /**
     * 应用预览的装扮（保存到数据库）
     */
    fun applyPreview() {
        _previewOutfit.value?.let { newOutfit ->
            selectOutfit(newOutfit)
        }
        _isPreviewMode.value = false
    }

    /**
     * 退出预览模式，恢复原装扮
     */
    fun cancelPreview() {
        _previewOutfit.value = originalOutfit
        _isPreviewMode.value = false
    }

    /**
     * 检查装扮是否已解锁
     *
     * @param outfitId 装扮 ID
     * @return 是否已解锁
     */
    fun isOutfitUnlocked(outfitId: String): Boolean {
        val data = _corgiData.value ?: return outfitId == OutfitManager.defaultOutfit.id
        return OutfitManager.isOutfitUnlocked(outfitId, data.unlockedOutfits)
    }

    /**
     * 检查成就是否已解锁
     *
     * @param achievementId 成就 ID
     * @return 是否已解锁
     */
    fun isAchievementUnlocked(achievementId: String): Boolean {
        val data = _corgiData.value ?: return false
        return AchievementManager.isAchievementUnlocked(achievementId, data.unlockedAchievements)
    }

    /**
     * 获取当前装扮
     */
    fun getCurrentOutfit(): Outfit {
        return OutfitManager.getCurrentOutfit(_corgiData.value?.currentOutfit)
    }

    /**
     * 获取成就进度
     *
     * @param achievement 成就
     * @return 当前进度值
     */
    suspend fun getAchievementProgress(achievement: Achievement): Int {
        val data = _corgiData.value ?: return 0

        val studyCategoryId = categoryRepository.getStudyCategoryId()
        val workCategoryId = categoryRepository.getWorkCategoryId()

        val learningCompleted = studyCategoryId?.let {
            todoRepository.getCompletedCountByCategory(it)
        } ?: 0

        val workCompleted = workCategoryId?.let {
            todoRepository.getCompletedCountByCategory(it)
        } ?: 0

        return AchievementManager.getAchievementProgress(
            achievementId = achievement.id,
            learningCompleted = learningCompleted,
            workCompleted = workCompleted,
            consecutiveDays = data.consecutiveDays,
            totalCompleted = data.totalCompleted,
            unlockedAchievementsJson = data.unlockedAchievements
        )
    }

    /**
     * 获取成就进度百分比
     */
    fun getAchievementProgressPercentage(achievement: Achievement, progress: Int): Float {
        return AchievementManager.getProgressPercentage(achievement, progress)
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        loadCorgiData()
    }

    // ==================== 名字修改相关 ====================

    /**
     * 验证名字是否有效
     *
     * @param name 待验证的名字
     * @return 验证结果（是否有效 + 错误信息）
     */
    fun validateName(name: String): Pair<Boolean, String> {
        // 长度验证
        if (name.isEmpty()) {
            return Pair(false, "名字不能为空")
        }
        if (name.length > 8) {
            return Pair(false, "名字不能超过8个字符")
        }

        // 敏感词过滤
        val lowerCaseName = name.lowercase()
        for (word in sensitiveWords) {
            if (lowerCaseName.contains(word.lowercase())) {
                return Pair(false, "名字中包含敏感词汇，请重新输入")
            }
        }

        return Pair(true, "")
    }

    /**
     * 更新柯基名字
     * 同时更新 DataStore 和 Room 数据库
     *
     * @param newName 新的柯基名字
     */
    fun updateCorgiName(newName: String) {
        viewModelScope.launch {
            // 验证名字
            val (isValid, _) = validateName(newName)
            if (!isValid) return@launch

            // 更新 Room 数据库
            corgiRepository.updateCorgiName(newName)

            // 更新 DataStore
            corgiPreferences.saveCorgiName(newName)

            // 更新本地状态（UI 立即刷新）
            _corgiData.value = _corgiData.value?.copy(name = newName)
        }
    }
}
