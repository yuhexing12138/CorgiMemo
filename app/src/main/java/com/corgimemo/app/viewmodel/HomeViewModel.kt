package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import java.util.Calendar
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.animation.Achievement
import com.corgimemo.app.animation.AchievementManager
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.CorgiPose
import com.corgimemo.app.animation.GreetingManager
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.Holiday
import com.corgimemo.app.animation.HolidayManager
import com.corgimemo.app.animation.SolarTerm
import com.corgimemo.app.animation.SolarTermManager
import com.corgimemo.app.animation.InteractionType
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
import com.corgimemo.app.model.UserType
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.MoodHistoryRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.animation.BehaviorType
import com.corgimemo.app.animation.CorgiBehaviorManager
import com.corgimemo.app.animation.DynamicGreetingManager
import com.corgimemo.app.animation.GreetingContext
import com.corgimemo.app.animation.TimeSlot
import com.corgimemo.app.data.weather.WeatherManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 庆祝动画级别
 * 根据任务优先级和截止日期决定庆祝动画的类型
 */
enum class CelebrationLevel(val priority: Int) {
    /** 低优先级/普通任务 */
    LOW(0),
    /** 中优先级任务 */
    MEDIUM(1),
    /** 高优先级任务 */
    HIGH(2),
    /** 截止日期当天完成的任务（超级庆祝） */
    SUPER(3);

    companion object {
        /**
         * 根据 priority 值获取对应的庆祝级别
         * 注意：SUPER 级别不会通过此方法获取，需要特殊判断截止日期
         */
        fun fromPriority(priority: Int): CelebrationLevel {
            return when (priority) {
                3 -> SUPER
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
    private val moodHistoryRepository: MoodHistoryRepository,
    @ApplicationContext private val context: Context
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

    // 动态问候语缓存
    private var cachedGreeting: DynamicGreetingManager.CachedGreeting? = null

    // ========== 节日相关状态 ==========

    private val _currentHoliday = MutableStateFlow<Holiday?>(null)
    val currentHoliday: StateFlow<Holiday?> = _currentHoliday.asStateFlow()

    // ========== 节气相关状态 ==========

    private val _currentSolarTerm = MutableStateFlow<SolarTerm?>(null)
    val currentSolarTerm: StateFlow<SolarTerm?> = _currentSolarTerm.asStateFlow()

    private val _showSolarTermCard = MutableStateFlow(false)
    val showSolarTermCard: StateFlow<Boolean> = _showSolarTermCard.asStateFlow()

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

    // ========== 撤销删除相关 ==========

    // 待删除待办的临时存储（用于撤销）
    private val _pendingDeletedTodo = MutableStateFlow<TodoItem?>(null)
    val pendingDeletedTodo: StateFlow<TodoItem?> = _pendingDeletedTodo.asStateFlow()

    // 倒计时任务（可取消）
    private var deleteTimerJob: Job? = null

    // 倒计时时长（5秒）
    private val UNDO_DELETE_DELAY_MS = 5000L

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

    /**
     * 执行触觉反馈
     * 读取用户设置的震动开关状态，并触发对应的震动模式
     *
     * @param type 交互类型
     */
    private fun triggerHapticFeedback(type: InteractionType) {
        val hapticEnabledValue = hapticEnabled.value
        HapticFeedbackManager.performHapticFeedback(
            context = context,
            type = type,
            enabled = hapticEnabledValue
        )
    }

    // 用户身份状态
    private val _userType = MutableStateFlow<UserType>(UserType.WORKER)
    val userType: StateFlow<UserType> = _userType.asStateFlow()

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
        observeUserType()
        checkHoliday()
        checkSolarTerm()
    }

    /**
     * 检查当前节日
     * 更新节日状态和相关的问候语、装扮
     */
    private fun checkHoliday() {
        val currentTime = System.currentTimeMillis()
        val holiday = HolidayManager.getCurrentHoliday(currentTime)
        _currentHoliday.value = holiday
        updateGreeting()
    }

    /**
     * 刷新节日状态
     * 在测试模式切换节日时调用，触发 UI 实时更新
     */
    fun refreshHoliday() {
        checkHoliday()
    }

    /**
     * 检查当前节气
     * 更新节气状态和节气卡片显示
     */
    private fun checkSolarTerm() {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply { timeInMillis = currentTime }
            val todayStr = String.format(
                "%04d%02d%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            val solarTerm = SolarTermManager.getCurrentSolarTerm(currentTime)
            _currentSolarTerm.value = solarTerm

            if (solarTerm != null) {
                val isDismissed = corgiPreferences.isSolarTermCardDismissed(
                    solarTermId = solarTerm.id,
                    today = todayStr
                )
                _showSolarTermCard.value = !isDismissed
            } else {
                _showSolarTermCard.value = false
            }
        }
    }

    /**
     * 关闭节气科普卡片
     * 保存关闭状态，当天不再显示
     */
    fun dismissSolarTermCard() {
        val solarTerm = _currentSolarTerm.value ?: return
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = currentTime }
        val todayStr = String.format(
            "%04d%02d%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        viewModelScope.launch {
            corgiPreferences.saveSolarTermCardDismissed(
                solarTermId = solarTerm.id,
                today = todayStr
            )
            _showSolarTermCard.value = false
        }
    }

    /**
     * 刷新节气状态
     * 在测试模式切换节气时调用，触发 UI 实时更新
     */
    fun refreshSolarTerm() {
        checkSolarTerm()
    }

    /**
     * 监听用户身份变化
     * 当用户切换身份时，自动更新问候语
     */
    private fun observeUserType() {
        viewModelScope.launch {
            corgiPreferences.userType.collect { typeValue ->
                val userType = UserType.fromValue(typeValue)
                _userType.value = userType
                updateGreeting()
            }
        }
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
                    FilterStatus.COMPLETED -> {
                        val thirtyDaysAgo = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
                        allTodos.filter { 
                            it.status == 1 && 
                            it.completedAt != null && 
                            it.completedAt >= thirtyDaysAgo 
                        }
                    }
                }
                // 待办列表变化后检查待办堆积
                checkWorriedBehavior()
            }
        }
    }

    /**
     * 初始化柯基数据
     * 直接加载已保存的柯基数据（首次启动已由引导流程处理）
     */
    private fun initCorgiData() {
        viewModelScope.launch {
            loadCorgiData()
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
     * 刷新问候语（按需刷新）
     * 检查缓存是否过期或时间段变化，需要时重新生成
     */
    suspend fun refreshGreetingIfNeeded() {
        val currentTime = System.currentTimeMillis()

        if (DynamicGreetingManager.shouldRefreshGreeting(cachedGreeting, currentTime)) {
            val greeting = generateDynamicGreeting(currentTime)
            _greeting.value = greeting
            cachedGreeting = DynamicGreetingManager.CachedGreeting(
                greeting = greeting,
                timeSlot = getCurrentTimeSlot(),
                timestamp = currentTime
            )
        }
    }

    /**
     * 获取当前时间段
     */
    private fun getCurrentTimeSlot(): TimeSlot {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return DynamicGreetingManager.getTimeSlot(hour)
    }

    /**
     * 生成动态问候语
     * 失败时降级为原有的固定问候语
     */
    private suspend fun generateDynamicGreeting(currentTime: Long): String {
        return try {
            val calendar = Calendar.getInstance().apply { timeInMillis = currentTime }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

            val todos = todoRepository.getAllTodos().first()
            val pendingCount = todos.count { it.status == 0 }
            val urgentTodo = DynamicGreetingManager.getMostUrgentTodo(todos, currentTime)

            val weatherInfo = WeatherManager.getWeatherInfo()
            val userName = _corgiData.value?.name

            val context = GreetingContext(
                hour = hour,
                isWeekend = isWeekend,
                pendingTodoCount = pendingCount,
                urgentTodoTitle = urgentTodo?.title,
                userName = userName,
                weatherInfo = weatherInfo
            )

            DynamicGreetingManager.generateGreeting(context)
        } catch (e: Exception) {
            GreetingManager.getGreetingForUserType(
                mood = _currentMood.value,
                name = _corgiData.value?.name,
                userType = _userType.value
            )
        }
    }

    /**
     * 更新问候语
     * 根据用户身份生成个性化问候语
     */
    private fun updateGreeting() {
        viewModelScope.launch {
            refreshGreetingIfNeeded()
        }
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
                // 触觉反馈：成就解锁长震动
                triggerHapticFeedback(InteractionType.ACHIEVEMENT_UNLOCK)
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
                    handleTaskCompleted(todo)
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
            CelebrationLevel.LOW -> "太棒了！又完成一个！"
            CelebrationLevel.MEDIUM -> "完成得不错哦！"
            CelebrationLevel.HIGH -> "这么重要的任务都完成了！太厉害了！"
            CelebrationLevel.SUPER -> "抢在截止前完成了！柯基为你骄傲！"
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
            CelebrationLevel.SUPER -> 4000L
        }
    }

    /**
     * 处理任务完成后的逻辑
     * 包括：增加经验值、累计任务数、检测成就、重新计算情绪、柯基庆祝姿态、触觉反馈
     *
     * @param todo 完成的任务项
     */
    private suspend fun handleTaskCompleted(todo: TodoItem) {
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

        // 综合判断庆祝级别（优先级 + 截止日期）
        val level = calculateCelebrationLevel(todo)
        val message = getEncouragementMessage(level)
        val duration = getCelebrationDuration(level)

        // 触觉反馈：任务完成双短震动
        triggerHapticFeedback(InteractionType.TASK_COMPLETE)

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
     * 综合判断庆祝级别
     * 截止日期当天完成的任务优先级最高
     *
     * @param todo 任务项
     * @return 庆祝级别
     */
    private fun calculateCelebrationLevel(todo: TodoItem): CelebrationLevel {
        val currentTime = System.currentTimeMillis()

        // 检查是否截止日期当天完成（优先级最高）
        if (todo.dueDate != null && MoodManager.isToday(todo.dueDate, currentTime)) {
            return CelebrationLevel.SUPER
        }

        // 根据优先级返回
        return CelebrationLevel.fromPriority(todo.priority)
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
     * 删除待办（支持撤销）
     *
     * @param id 待办 ID
     */
    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            // 1. 先获取完整的待办对象（用于撤销）
            val todo = todoRepository.getTodoById(id) ?: return@launch

            // 2. 立即从数据库删除（让列表立即更新）
            todoRepository.deleteTodoById(id)

            // 3. 临时存储待办
            _pendingDeletedTodo.value = todo

            // 4. 取消之前的倒计时任务（如果有）
            deleteTimerJob?.cancel()

            // 5. 启动新的倒计时
            deleteTimerJob = launch {
                delay(UNDO_DELETE_DELAY_MS)
                // 倒计时结束，清除临时数据
                _pendingDeletedTodo.value = null
            }
        }
    }

    /**
     * 撤销删除
     * 将临时存储的待办重新插入数据库
     */
    fun undoDelete() {
        viewModelScope.launch {
            val todo = _pendingDeletedTodo.value ?: return@launch

            // 1. 取消倒计时任务
            deleteTimerJob?.cancel()
            deleteTimerJob = null

            // 2. 重新插入待办
            todoRepository.insertTodo(todo)

            // 3. 清除临时数据
            _pendingDeletedTodo.value = null
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
        // 如果当前是打哈欠状态，用户操作后立即恢复正常姿态
        if (_currentBehavior.value == BehaviorType.YAWNING) {
            _currentBehavior.value = BehaviorType.NONE
            _currentPose.value = PoseManager.getDefaultPose()
            // 重置打哈欠冷却时间，让用户下次空闲时可以立即再次打哈欠
            lastYawnTime = 0L
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
     * 一直保持打哈欠状态，直到用户操作才恢复
     */
    private fun triggerYawn() {
        viewModelScope.launch {
            lastYawnTime = System.currentTimeMillis()
            _currentBehavior.value = BehaviorType.YAWNING

            // TODO: 打哈欠帧动画暂未就绪，暂用 SLEEP 姿态作为占位
            // 后续替换为真实的打哈欠动画资源
            _currentPose.value = CorgiPose.SLEEP

            // 注意：打哈欠状态会一直持续，直到用户操作触发 onUserInteraction() 才恢复
            // 不再自动结束，保持"睡着了"的状态
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
        // 取消删除倒计时任务
        deleteTimerJob?.cancel()
    }
}
