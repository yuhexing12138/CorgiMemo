package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.animation.Achievement
import com.corgimemo.app.animation.AchievementManager
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.CorgiPose
import com.corgimemo.app.animation.GreetingManager
import com.corgimemo.app.animation.LevelManager
import com.corgimemo.app.animation.LevelStage
import com.corgimemo.app.animation.MoodManager
import com.corgimemo.app.animation.OutfitManager
import com.corgimemo.app.animation.PoseManager
import com.corgimemo.app.animation.PoseScene
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.MoodHistory
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.MoodHistoryRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.animation.BehaviorType
import com.corgimemo.app.animation.CorgiBehaviorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 庆祝动画级别
 * 根据任务优先级决定庆祝动画的类型
 */
enum class CelebrationLevel(val priority: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2);

    companion object {
        /**
         * 根据 priority 值获取对应的庆祝级别
         */
        fun fromPriority(priority: Int): CelebrationLevel {
            return when (priority) {
                2 -> HIGH
                1 -> MEDIUM
                else -> LOW
            }
        }
    }
}

/**
 * 庆祝状态数据类
 * 包含显示状态、级别和鼓励语
 */
data class CelebrationState(
    val isShowing: Boolean = false,
    val level: CelebrationLevel = CelebrationLevel.LOW,
    val message: String = "太棒了！"
)

/**
 * 首页视图模型
 * 管理待办列表和柯基陪伴系统的状态
 * 包括：等级/经验值系统、成就检测、装扮解锁
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val corgiRepository: CorgiRepository,
    private val categoryRepository: CategoryRepository,
    private val corgiPreferences: CorgiPreferences,
    private val moodHistoryRepository: MoodHistoryRepository
) : ViewModel() {

    // ========== 待办列表相关 ==========

    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    private val _filterStatus = MutableStateFlow(FilterStatus.ALL)
    val filterStatus: StateFlow<FilterStatus> = _filterStatus.asStateFlow()

    // ========== 柯基数据相关 ==========

    private val _corgiData = MutableStateFlow<CorgiData?>(null)
    val corgiData: StateFlow<CorgiData?> = _corgiData.asStateFlow()

    private val _showNamerDialog = MutableStateFlow(false)
    val showNamerDialog: StateFlow<Boolean> = _showNamerDialog.asStateFlow()

    // ========== 动画和情绪状态 ==========

    private val _currentPose = MutableStateFlow(PoseManager.getDefaultPose())
    val currentPose: StateFlow<CorgiPose> = _currentPose.asStateFlow()

    private val _currentMood = MutableStateFlow(CorgiMood.NORMAL)
    val currentMood: StateFlow<CorgiMood> = _currentMood.asStateFlow()

    private val _currentOutfit = MutableStateFlow<String?>(null)
    val currentOutfit: StateFlow<String?> = _currentOutfit.asStateFlow()

    private val _levelStage = MutableStateFlow(LevelStage.BABY)
    val levelStage: StateFlow<LevelStage> = _levelStage.asStateFlow()

    private val _celebrationState = MutableStateFlow(CelebrationState())
    val celebrationState: StateFlow<CelebrationState> = _celebrationState.asStateFlow()

    private val _greeting = MutableStateFlow("")
    val greeting: StateFlow<String> = _greeting.asStateFlow()

    // ========== 成长系统状态 ==========

    private val _showLevelUp = MutableStateFlow<Int?>(null)
    val showLevelUp: StateFlow<Int?> = _showLevelUp.asStateFlow()

    private val _showAchievementUnlock = MutableStateFlow<Achievement?>(null)
    val showAchievementUnlock: StateFlow<Achievement?> = _showAchievementUnlock.asStateFlow()

    private val _showConsecutiveBonus = MutableStateFlow(false)
    val showConsecutiveBonus: StateFlow<Boolean> = _showConsecutiveBonus.asStateFlow()

    // ========== 自主行为状态 ==========

    private val _currentBehavior = MutableStateFlow(BehaviorType.NONE)
    val currentBehavior: StateFlow<BehaviorType> = _currentBehavior.asStateFlow()

    private val _showMissedYouDialog = MutableStateFlow(false)
    val showMissedYouDialog: StateFlow<Boolean> = _showMissedYouDialog.asStateFlow()

    private val _missedYouDays = MutableStateFlow(0)
    val missedYouDays: StateFlow<Int> = _missedYouDays.asStateFlow()

    // ========== 快速换装 BottomSheet ==========

    private val _showOutfitSheet = MutableStateFlow(false)
    val showOutfitSheet: StateFlow<Boolean> = _showOutfitSheet.asStateFlow()

    // ========== 情绪历史记录 ==========

    private val _moodHistory7Days = MutableStateFlow<List<MoodHistory>>(emptyList())
    val moodHistory7Days: StateFlow<List<MoodHistory>> = _moodHistory7Days.asStateFlow()

    // ========== 情绪变化提示 ==========

    private val _moodChangeMessage = MutableStateFlow<String?>(null)
    val moodChangeMessage: StateFlow<String?> = _moodChangeMessage.asStateFlow()

    private var lastMoodChangeHintTime = 0L

    // 空闲检测相关
    private var lastUserInteractionTime = System.currentTimeMillis()
    private var lastYawnTime = 0L
    private var idleCheckJob: Job? = null

    // 连续完成任务相关
    private val recentCompletedTimes = mutableListOf<Long>()

    init {
        loadTodos()
        initCorgiData()
        initPoseAndMood()
        initDefaultCategories()
        checkStartupBehaviors()
        startIdleDetection()
        recordDailyMoodIfNeeded()
        loadMoodHistory()
    }

    /**
     * 初始化默认分类
     */
    private fun initDefaultCategories() {
        viewModelScope.launch {
            categoryRepository.initDefaultCategories()
        }
    }

    /**
     * 初始化姿态和情绪
     * 默认姿态根据情绪选择
     */
    private fun initPoseAndMood() {
        _currentMood.value = CorgiMood.NORMAL
        _currentPose.value = PoseManager.getPoseForMood(CorgiMood.NORMAL)
    }

    /**
     * 加载待办列表
     */
    private fun loadTodos() {
        viewModelScope.launch {
            todoRepository.getAllTodos().collect { allTodos ->
                _todos.value = when (_filterStatus.value) {
                    FilterStatus.ALL -> allTodos
                    FilterStatus.PENDING -> allTodos.filter { it.status == 0 }
                    FilterStatus.COMPLETED -> allTodos.filter { it.status == 1 }
                }
                // 待办列表变化后检查待办堆积
                checkWorriedBehavior()
            }
        }
    }

    /**
     * 初始化柯基数据
     * 检查是否首次启动，若是则显示命名对话框
     * 否则加载已保存的柯基数据
     */
    private fun initCorgiData() {
        viewModelScope.launch {
            val isFirst = corgiPreferences.isFirstLaunch.first()
            if (isFirst) {
                _showNamerDialog.value = true
            } else {
                loadCorgiData()
            }
        }
    }

    /**
     * 从数据库加载柯基数据
     * 同步更新装扮、等级阶段和情绪
     * 注意：用户要求无论什么时候进入APP，第一眼柯基都要是趴卧姿态
     * 所以这里只加载数据但不设置情绪对应的姿态
     * 也不调用 recalculateMood()，避免重新计算情绪后改变姿态
     */
    private fun loadCorgiData() {
        viewModelScope.launch {
            val data = corgiRepository.getCorgiData()
            _corgiData.value = data

            data?.let { corgi ->
                _currentOutfit.value = corgi.currentOutfit
                _levelStage.value = LevelManager.getLevelStage(corgi.level)

                val mood = MoodManager.getMoodFromValue(corgi.moodValue)
                _currentMood.value = mood
            }

            updateGreeting()
        }
    }

    /**
     * 更新问候语
     */
    private fun updateGreeting() {
        _greeting.value = GreetingManager.getGreeting(_currentMood.value, _corgiData.value?.name)
    }

    /**
     * 保存柯基名字
     * 首次命名时创建新的柯基数据并保存到数据库和DataStore
     */
    fun saveCorgiName(name: String) {
        viewModelScope.launch {
            val newCorgi = CorgiData(
                name = name,
                level = 1,
                experience = 0,
                moodValue = 50,
                lastActiveDate = System.currentTimeMillis().toString()
            )

            corgiRepository.insertCorgi(newCorgi)
            corgiPreferences.saveCorgiName(name)
            corgiPreferences.setFirstLaunchDone()

            _corgiData.value = newCorgi
            _showNamerDialog.value = false
        }
    }

    /**
     * 关闭命名对话框（点击"稍后"）
     */
    fun dismissNamerDialog() {
        _showNamerDialog.value = false
    }

    /**
     * 设置过滤器状态
     */
    fun setFilterStatus(status: FilterStatus) {
        _filterStatus.value = status
        loadTodos()
    }

    /**
     * 获取今日已完成的任务数
     */
    private fun getTodayCompletedCount(): Int {
        val currentTime = System.currentTimeMillis()
        return _todos.value.count { todo ->
            todo.status == 1 &&
                    todo.completedAt != null &&
                    MoodManager.isToday(todo.completedAt, currentTime)
        }
    }

    /**
     * 获取今日总任务数
     */
    private fun getTodayTotalCount(): Int {
        val currentTime = System.currentTimeMillis()
        return _todos.value.count { todo ->
            MoodManager.isToday(todo.createdAt, currentTime) ||
                    (todo.dueDate != null && MoodManager.isToday(todo.dueDate, currentTime))
        }
    }

    /**
     * 获取超期任务数
     */
    private fun getOverdueTasksCount(): Int {
        val currentTime = System.currentTimeMillis()
        return _todos.value.count { todo ->
            todo.status == 0 && MoodManager.isOverdue(todo.dueDate, currentTime)
        }
    }

    /**
     * 重新计算并更新情绪值
     * 情绪变化时自动更新对应姿态
     */
    private fun recalculateMood() {
        viewModelScope.launch {
            recalculateMoodSuspend()
        }
    }

    /**
     * 挂起版本的重新计算情绪
     */
    private suspend fun recalculateMoodSuspend() {
        val corgiData = _corgiData.value ?: return
        val oldMoodValue = corgiData.moodValue

        val completedToday = getTodayCompletedCount()
        val totalToday = getTodayTotalCount()
        val completionRate = MoodManager.calculateCompletionRate(completedToday, totalToday)
        val consecutiveDays = corgiData.consecutiveDays
        val overdueCount = getOverdueTasksCount()

        val newMoodValue = MoodManager.calculateMoodValue(
            todayCompletionRate = completionRate,
            consecutiveDays = consecutiveDays,
            overdueTasksCount = overdueCount
        )

        val newMood = MoodManager.getMoodFromValue(newMoodValue)
        corgiRepository.updateMood(newMoodValue)
        _corgiData.value = _corgiData.value?.copy(moodValue = newMoodValue)
        _currentMood.value = newMood
        _currentPose.value = PoseManager.getPoseForMood(newMood)
        updateGreeting()

        if (MoodManager.isSignificantChange(oldMoodValue, newMoodValue)) {
            val now = System.currentTimeMillis()
            val cooldownMs = 5 * 60 * 1000L
            if (now - lastMoodChangeHintTime > cooldownMs) {
                val message = MoodManager.getChangeMessage(
                    oldMood = oldMoodValue,
                    newMood = newMoodValue,
                    completionRate = completionRate,
                    overdueCount = overdueCount,
                    consecutiveDays = consecutiveDays
                )
                _moodChangeMessage.value = message
                lastMoodChangeHintTime = now
            }
        }
    }

    /**
     * 清除情绪变化提示消息
     */
    fun clearMoodChangeMessage() {
        _moodChangeMessage.value = null
    }

    /**
     * 检查是否跨天，如果是则重置连续活跃天数并重新计算情绪
     */
    private fun checkDateChange() {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val corgiData = _corgiData.value ?: return@launch

            val lastActiveDate = corgiData.lastActiveDate.toLongOrNull() ?: currentTime
            val daysDiff = MoodManager.calculateDaysBetween(lastActiveDate, currentTime)

            if (daysDiff > 0) {
                val newConsecutiveDays = if (daysDiff == 1) {
                    corgiRepository.incrementConsecutiveDays()
                    corgiData.consecutiveDays + 1
                } else {
                    corgiRepository.resetConsecutiveDays()
                    1
                }

                corgiRepository.updateLastActiveDate(currentTime.toString())

                val newMaxConsecutiveDays = maxOf(newConsecutiveDays, corgiData.maxConsecutiveDays)
                if (newMaxConsecutiveDays > corgiData.maxConsecutiveDays) {
                    corgiRepository.updateMaxConsecutiveDays(newMaxConsecutiveDays)
                }

                _corgiData.value = corgiData.copy(
                    lastActiveDate = currentTime.toString(),
                    consecutiveDays = newConsecutiveDays,
                    maxConsecutiveDays = newMaxConsecutiveDays
                )

                if (LevelManager.shouldGiveConsecutive7DaysReward(newConsecutiveDays)) {
                    addConsecutive7DaysBonus()
                }

                checkAchievements()
                recalculateMood()
            }
        }
    }

    /**
     * 添加连续 7 天奖励
     */
    private suspend fun addConsecutive7DaysBonus() {
        val currentData = _corgiData.value ?: return
        val bonusExp = LevelManager.getExpOnConsecutive7Days()
        val newTotalExp = currentData.experience + bonusExp

        val newLevel = LevelManager.getCurrentLevel(newTotalExp)
        if (newLevel > currentData.level) {
            _showLevelUp.value = newLevel
        }

        _showConsecutiveBonus.value = true
        delay(3000)
        _showConsecutiveBonus.value = false

        corgiRepository.addExperience(bonusExp)
        _corgiData.value = currentData.copy(
            experience = newTotalExp,
            level = newLevel
        )
    }

    /**
     * 增加经验值
     *
     * @param amount 经验值数量
     * @return 是否升级
     */
    private suspend fun addExperience(amount: Int): Boolean {
        val currentData = _corgiData.value ?: return false
        val currentLevel = currentData.level
        val currentExp = currentData.experience
        val newTotalExp = currentExp + amount

        val newLevel = LevelManager.getCurrentLevel(newTotalExp)
        val levelUp = newLevel > currentLevel

        if (levelUp) {
            _showLevelUp.value = newLevel
        }

        corgiRepository.addExperience(amount)
        _corgiData.value = currentData.copy(
            experience = newTotalExp,
            level = newLevel
        )

        return levelUp
    }

    /**
     * 检测并处理新解锁的成就
     */
    private suspend fun checkAchievements() {
        val currentData = _corgiData.value ?: return

        val studyCategoryId = categoryRepository.getStudyCategoryId()
        val workCategoryId = categoryRepository.getWorkCategoryId()

        val learningCompleted = studyCategoryId?.let {
            todoRepository.getCompletedCountByCategory(it)
        } ?: 0

        val workCompleted = workCategoryId?.let {
            todoRepository.getCompletedCountByCategory(it)
        } ?: 0

        val newAchievements = AchievementManager.checkNewAchievements(
            learningCompleted = learningCompleted,
            workCompleted = workCompleted,
            consecutiveDays = currentData.consecutiveDays,
            totalCompleted = currentData.totalCompleted,
            unlockedAchievementsJson = currentData.unlockedAchievements
        )

        if (newAchievements.isNotEmpty()) {
            val newAchievementIds = newAchievements.map { it.id }
            val updatedAchievementsJson = AchievementManager.addAchievements(
                currentData.unlockedAchievements,
                newAchievementIds
            )

            val newOutfitIds = OutfitManager.getOutfitsToUnlock(
                newAchievementIds,
                currentData.unlockedOutfits
            )

            val updatedOutfitsJson = if (newOutfitIds.isNotEmpty()) {
                OutfitManager.addOutfits(currentData.unlockedOutfits, newOutfitIds)
            } else {
                currentData.unlockedOutfits
            }

            corgiRepository.updateUnlockedAchievements(updatedAchievementsJson)
            if (newOutfitIds.isNotEmpty()) {
                corgiRepository.updateUnlockedOutfits(updatedOutfitsJson)
            }

            _corgiData.value = currentData.copy(
                unlockedAchievements = updatedAchievementsJson,
                unlockedOutfits = updatedOutfitsJson
            )

            for (achievement in newAchievements) {
                _showAchievementUnlock.value = achievement
                delay(3000)
            }
            _showAchievementUnlock.value = null
        }
    }

    /**
     * 切换待办完成状态
     */
    fun toggleTodoStatus(id: Long, isChecked: Boolean) {
        viewModelScope.launch {
            todoRepository.getTodoById(id)?.let { todo ->
                val updatedTodo = todo.copy(
                    status = if (isChecked) 1 else 0,
                    completedAt = if (isChecked) System.currentTimeMillis() else null,
                    updatedAt = System.currentTimeMillis()
                )
                todoRepository.updateTodo(updatedTodo)

                if (isChecked) {
                    handleTaskCompleted(todo.priority)
                }
            }
        }
    }

    /**
     * 获取鼓励语
     * 根据庆祝级别返回对应的鼓励语
     */
    private fun getEncouragementMessage(level: CelebrationLevel): String {
        return when (level) {
            CelebrationLevel.LOW -> "太棒了！"
            CelebrationLevel.MEDIUM -> "完成得不错哦！"
            CelebrationLevel.HIGH -> "这么重要的任务都完成了！柯基为你骄傲！"
        }
    }

    /**
     * 获取庆祝持续时间
     * 根据庆祝级别返回对应的持续时间（毫秒）
     */
    private fun getCelebrationDuration(level: CelebrationLevel): Long {
        return when (level) {
            CelebrationLevel.LOW -> 1000L
            CelebrationLevel.MEDIUM -> 2000L
            CelebrationLevel.HIGH -> 3000L
        }
    }

    /**
     * 处理任务完成后的逻辑
     * 包括：增加经验值、累计任务数、检测成就、重新计算情绪、柯基庆祝姿态
     *
     * @param priority 任务优先级（0=低, 1=中, 2=高）
     */
    private suspend fun handleTaskCompleted(priority: Int) {
        val currentData = _corgiData.value ?: return

        // 记录任务完成时间（用于连续完成开心检测）
        recordTaskCompletion()

        val expGain = LevelManager.getExpOnTaskComplete()
        addExperience(expGain)

        corgiRepository.incrementTotalCompleted()
        val newTotalCompleted = currentData.totalCompleted + 1
        _corgiData.value = _corgiData.value?.copy(totalCompleted = newTotalCompleted)

        checkAchievements()
        recalculateMoodSuspend()

        val level = CelebrationLevel.fromPriority(priority)
        val message = getEncouragementMessage(level)
        val duration = getCelebrationDuration(level)

        _celebrationState.value = CelebrationState(
            isShowing = true,
            level = level,
            message = message
        )
        _currentPose.value = CorgiPose.STAND

        delay(duration)
        _celebrationState.value = CelebrationState(isShowing = false)

        // 如果没有进入连续开心模式，恢复默认姿态
        if (_currentBehavior.value != BehaviorType.HAPPY_STREAK) {
            _currentPose.value = PoseManager.getDefaultPose()
        }
    }

    /**
     * 关闭升级提示
     */
    fun dismissLevelUp() {
        _showLevelUp.value = null
    }

    /**
     * 关闭成就解锁提示
     */
    fun dismissAchievementUnlock() {
        _showAchievementUnlock.value = null
    }

    /**
     * 关闭连续天数奖励提示
     */
    fun dismissConsecutiveBonus() {
        _showConsecutiveBonus.value = false
    }

    /**
     * 更新当前姿态
     */
    fun updatePose(pose: CorgiPose) {
        _currentPose.value = pose
    }

    /**
     * 任务创建后调用
     * 刷新任务列表，保持柯基状态
     */
    fun onTaskCreated() {
        viewModelScope.launch {
            loadTodos()
        }
    }

    /**
     * 获取柯基名字
     */
    fun getCorgiName(): String? {
        return _corgiData.value?.name
    }

    /**
     * 设置为创建待办时的姿态
     */
    fun setPoseForCreating() {
        _currentPose.value = PoseManager.getPoseForScene(PoseScene.CREATING)
    }

    /**
     * 设置为加载中的姿态
     */
    fun setPoseForLoading() {
        _currentPose.value = PoseManager.getPoseForScene(PoseScene.LOADING)
    }

    /**
     * 设置为庆祝姿态
     * 保存任务成功时调用
     */
    fun setPoseForCelebrating() {
        _currentPose.value = PoseManager.getPoseForScene(PoseScene.CELEBRATING)
    }

    /**
     * 立即恢复为默认姿态
     */
    fun resetPoseToDefault() {
        _currentPose.value = PoseManager.getDefaultPose()
    }

    /**
     * 延迟恢复为默认姿态
     */
    fun restorePoseWithDelay(delayMs: Long = 500) {
        viewModelScope.launch {
            delay(delayMs)
            _currentPose.value = PoseManager.getDefaultPose()
        }
    }

    /**
     * 删除待办
     */
    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            todoRepository.deleteTodoById(id)
        }
    }

    /**
     * 待办过滤器枚举
     */
    enum class FilterStatus {
        ALL, PENDING, COMPLETED
    }

    // ==================== 自主行为相关方法 ====================

    /**
     * 用户操作时调用
     * 重置空闲计时器，标记用户活跃
     */
    fun onUserInteraction() {
        lastUserInteractionTime = System.currentTimeMillis()
        viewModelScope.launch {
            corgiPreferences.saveLastActiveTimestamp(lastUserInteractionTime)
        }
        // 如果当前是打哈欠状态，用户操作后恢复正常
        if (_currentBehavior.value == BehaviorType.YAWNING) {
            _currentBehavior.value = BehaviorType.NONE
        }
    }

    /**
     * 检查启动时行为
     * 包括：被忽略想念、深夜入睡
     */
    private fun checkStartupBehaviors() {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()

            // 1. 检查是否被忽略想念（3天未打开）
            val lastActive = corgiPreferences.getLastActiveTimestamp()
            val daysDiff = MoodManager.calculateDaysBetween(lastActive, currentTime)

            if (CorgiBehaviorManager.wasMissed(daysDiff)) {
                _missedYouDays.value = daysDiff
                _showMissedYouDialog.value = true
                _currentBehavior.value = BehaviorType.MISSED_YOU
                _currentMood.value = CorgiMood.SAD
                _currentPose.value = PoseManager.getPoseForMood(CorgiMood.SAD)
            }

            // 2. 检查是否需要深夜入睡
            checkNightSleepBehavior(currentTime)

            // 3. 检查待办堆积担心
            checkWorriedBehavior()

            // 更新最后活跃时间
            corgiPreferences.saveLastActiveTimestamp(currentTime)
        }
    }

    /**
     * 检查深夜入睡行为
     * 23:00 后首次打开 APP 触发
     *
     * @param currentTime 当前时间戳
     */
    private suspend fun checkNightSleepBehavior(currentTime: Long) {
        val currentHour = CorgiBehaviorManager.getCurrentHour(currentTime)
        val currentDate = CorgiBehaviorManager.getCurrentDateString(currentTime)
        val checkedDate = corgiPreferences.getNightSleepCheckedDate()

        // 23点后且今日未检查过
        if (CorgiBehaviorManager.isNightTime(currentHour) && checkedDate != currentDate) {
            // 标记今日已检查
            corgiPreferences.saveNightSleepCheckedDate(currentDate)

            // 如果不是被忽略想念状态，显示深夜入睡
            if (_currentBehavior.value != BehaviorType.MISSED_YOU) {
                _currentBehavior.value = BehaviorType.SLEEPING_NIGHT
                _currentMood.value = CorgiMood.SLEEPY
                _currentPose.value = CorgiPose.SLEEP
                _greeting.value = CorgiBehaviorManager.getSleepMessage(_corgiData.value?.name)
            }
        }
    }

    /**
     * 检查待办堆积担心行为
     * 待办数 > 10 且 > 50% 超期
     */
    private fun checkWorriedBehavior() {
        val pendingTodos = _todos.value.filter { it.status == 0 }
        val overdueCount = pendingTodos.count { todo ->
            MoodManager.isOverdue(todo.dueDate)
        }

        if (CorgiBehaviorManager.shouldWorry(pendingTodos.size, overdueCount)) {
            // 只在没有更高优先级行为时设置
            if (CorgiBehaviorManager.shouldOverrideBehavior(
                    _currentBehavior.value,
                    BehaviorType.WORRIED
                )
            ) {
                _currentBehavior.value = BehaviorType.WORRIED
                _currentMood.value = CorgiMood.WORRIED
                _currentPose.value = PoseManager.getPoseForMood(CorgiMood.WORRIED)
                _greeting.value = CorgiBehaviorManager.getWorriedMessage(
                    _corgiData.value?.name,
                    pendingTodos.size,
                    overdueCount
                )
            }
        }
    }

    /**
     * 启动空闲检测
     * 每 1 秒检查一次用户是否操作
     */
    private fun startIdleDetection() {
        idleCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)

                val currentTime = System.currentTimeMillis()

                // 检查是否应该打哈欠
                if (CorgiBehaviorManager.shouldYawn(
                        lastUserInteractionTime,
                        lastYawnTime,
                        currentTime
                    )
                ) {
                    // 检查当前行为优先级是否允许打哈欠
                    if (CorgiBehaviorManager.shouldOverrideBehavior(
                            _currentBehavior.value,
                            BehaviorType.YAWNING
                        ) || _currentBehavior.value == BehaviorType.NONE
                    ) {
                        triggerYawn()
                    }
                }
            }
        }
    }

    /**
     * 停止空闲检测
     * ViewModel 销毁时调用
     */
    private fun stopIdleDetection() {
        idleCheckJob?.cancel()
        idleCheckJob = null
    }

    /**
     * 触发打哈欠行为
     */
    private fun triggerYawn() {
        viewModelScope.launch {
            lastYawnTime = System.currentTimeMillis()
            _currentBehavior.value = BehaviorType.YAWNING

            // TODO: 打哈欠帧动画暂未就绪，暂用 SLEEP 姿态作为占位
            // 后续替换为真实的打哈欠动画资源
            _currentPose.value = CorgiPose.SLEEP

            // 打哈欠持续约 2 秒
            delay(CorgiBehaviorManager.YAWN_DURATION_MS)

            // 恢复正常
            if (_currentBehavior.value == BehaviorType.YAWNING) {
                _currentBehavior.value = BehaviorType.NONE
                _currentPose.value = PoseManager.getDefaultPose()
                _currentMood.value = CorgiMood.NORMAL
            }
        }
    }

    /**
     * 记录任务完成时间
     * 用于连续完成开心检测
     */
    private fun recordTaskCompletion() {
        val currentTime = System.currentTimeMillis()
        recentCompletedTimes.add(currentTime)

        // 只保留最近的完成时间
        val windowStart = currentTime - CorgiBehaviorManager.HAPPY_STREAK_WINDOW_MS
        recentCompletedTimes.removeAll { it < windowStart }

        // 检查是否达成连续完成开心
        if (CorgiBehaviorManager.hasHappyStreak(recentCompletedTimes, currentTime)) {
            // 检查当前行为优先级
            if (CorgiBehaviorManager.shouldOverrideBehavior(
                    _currentBehavior.value,
                    BehaviorType.HAPPY_STREAK
                )
            ) {
                triggerHappyStreak()
            }
        }
    }

    /**
     * 触发连续完成开心行为
     */
    private fun triggerHappyStreak() {
        viewModelScope.launch {
            _currentBehavior.value = BehaviorType.HAPPY_STREAK
            _currentMood.value = CorgiMood.EXCITED
            _currentPose.value = CorgiPose.RUN
            _greeting.value = CorgiBehaviorManager.getHappyStreakMessage(_corgiData.value?.name)

            // 持续 30 秒后恢复
            delay(CorgiBehaviorManager.HAPPY_STREAK_DURATION_MS)

            if (_currentBehavior.value == BehaviorType.HAPPY_STREAK) {
                _currentBehavior.value = BehaviorType.NONE
                _currentMood.value = CorgiMood.NORMAL
                _currentPose.value = PoseManager.getDefaultPose()
                updateGreeting()
            }
        }
    }

    /**
     * 关闭被忽略想念弹窗
     */
    fun dismissMissedYouDialog() {
        _showMissedYouDialog.value = false
        _currentBehavior.value = BehaviorType.NONE
        _currentMood.value = CorgiMood.NORMAL
        _currentPose.value = PoseManager.getDefaultPose()
        updateGreeting()
    }

    // ==================== 快速换装 BottomSheet ====================

    /**
     * 显示/隐藏快速换装 BottomSheet
     */
    fun toggleOutfitSheet() {
        _showOutfitSheet.value = !_showOutfitSheet.value
    }

    /**
     * 隐藏快速换装 BottomSheet
     */
    fun hideOutfitSheet() {
        _showOutfitSheet.value = false
    }

    /**
     * 快速切换装扮
     *
     * @param outfitId 装扮 ID（null 或 DEFAULT 表示默认）
     */
    fun quickSwitchOutfit(outfitId: String?) {
        viewModelScope.launch {
            val effectiveId = if (outfitId == OutfitManager.defaultOutfit.id) null else outfitId
            corgiRepository.updateOutfit(effectiveId)
            _corgiData.value = _corgiData.value?.copy(currentOutfit = effectiveId)
            _currentOutfit.value = effectiveId
            _showOutfitSheet.value = false
        }
    }

    // ==================== 情绪历史记录 ====================

    /**
     * 记录今日情绪值（如果今天还没记录）
     * 每日首次进入 App 时调用
     */
    private fun recordDailyMoodIfNeeded() {
        viewModelScope.launch {
            val corgi = _corgiData.value ?: return@launch
            val alreadyRecorded = moodHistoryRepository.isTodayRecorded()
            if (!alreadyRecorded) {
                moodHistoryRepository.recordTodayMood(
                    moodValue = corgi.moodValue,
                    reason = "每日记录"
                )
            }
        }
    }

    /**
     * 加载近7天的情绪历史
     */
    private fun loadMoodHistory() {
        viewModelScope.launch {
            val history = moodHistoryRepository.getLast7Days()
            _moodHistory7Days.value = history
        }
    }

    /**
     * 重新计算并更新今日情绪历史记录
     * 在情绪值变化时调用
     *
     * @param newMoodValue 新的情绪值
     * @param reason 变化原因
     */
    fun updateTodayMoodHistory(newMoodValue: Int, reason: String? = null) {
        viewModelScope.launch {
            moodHistoryRepository.recordTodayMood(newMoodValue, reason)
            loadMoodHistory()
        }
    }

    /**
     * ViewModel 销毁时清理资源
     */
    override fun onCleared() {
        super.onCleared()
        stopIdleDetection()
    }
}
