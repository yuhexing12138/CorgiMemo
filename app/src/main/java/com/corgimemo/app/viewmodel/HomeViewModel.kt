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
import com.corgimemo.app.data.event.TodoEvent
import com.corgimemo.app.data.event.TodoEventBus
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.MoodHistory
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.model.UserType
import com.corgimemo.app.data.repository.AchievementChecker
import com.corgimemo.app.data.repository.AchievementRepository
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.DeletedTodoRepository
import com.corgimemo.app.data.repository.MoodHistoryRepository
import com.corgimemo.app.data.repository.RepeatTaskManager
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.data.local.db.OperationLogEntity
import com.corgimemo.app.data.repository.OperationLogRepository
import com.corgimemo.app.data.repository.TaskDailyStatsRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.util.FileCopyManager
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
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
    private val deletedTodoRepository: DeletedTodoRepository,
    private val achievementChecker: AchievementChecker,
    private val achievementRepository: AchievementRepository,
    private val corgiPreferences: CorgiPreferences,
    private val moodHistoryRepository: MoodHistoryRepository,
    private val operationLogRepository: OperationLogRepository,
    private val taskDailyStatsRepository: TaskDailyStatsRepository,
    private val fileCopyManager: FileCopyManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ========== 待办列表相关 ==========

    /** 数据是否已初始化完成（用于避免冷启动时从空列表闪烁到有数据） */
    private val _isDataInitialized = MutableStateFlow(false)
    val isDataInitialized: StateFlow<Boolean> = _isDataInitialized.asStateFlow()

    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    /** "已完成"区域是否展开（从持久化加载） */
    private val _showCompleted = MutableStateFlow(false)
    val showCompleted: StateFlow<Boolean> = _showCompleted.asStateFlow()

    /** 未完成待办列表（含置顶） */
    val pendingTodos: StateFlow<List<TodoItem>> = _todos.map { todos ->
        todos.filter { it.status == 0 }
            .sortedWith(
                compareByDescending<TodoItem> { it.isPinned }
                    .thenBy { it.sortOrder }
                    .thenByDescending { it.createdAt }
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 可见的已完成待办（30天内） */
    val visibleCompletedTodos: StateFlow<List<TodoItem>> = _todos.map { todos ->
        val thirtyDaysAgo = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
        todos.filter {
            it.status == 1 && it.completedAt != null && it.completedAt >= thirtyDaysAgo
        }.sortedWith(
            compareByDescending<TodoItem> { it.isPinned }
                .thenBy { it.sortOrder }
                .thenByDescending { it.createdAt }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 已完成待办总数（用于分隔按钮显示） */
    val completedCount: StateFlow<Int> = _todos.map { todos ->
        todos.count { it.status == 1 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ========== 搜索相关状态 ==========

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ========== 分类相关状态（必须在 filteredTodos 之前声明） ==========

    val categories: StateFlow<List<Category>> =
        categoryRepository.getAllCategories()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    /** 排序方式状态（默认为 "updated_desc" - 最新更新的在前） */
    private val _sortType = MutableStateFlow("updated_desc")
    val sortType: StateFlow<String> = _sortType.asStateFlow()

    /** 当前可见待办列表（未完成 + 展开时的已完成），应用搜索/分类/排序过滤 */
    val filteredTodos: StateFlow<List<TodoItem>> = run {
        val baseFlow = kotlinx.coroutines.flow.combine(
            pendingTodos, visibleCompletedTodos, _showCompleted
        ) { pending, completed, showCompleted ->
            if (showCompleted) pending + completed else pending
        }
        kotlinx.coroutines.flow.combine(
            baseFlow, _searchQuery, _selectedCategoryId, _sortType
        ) { baseList, query, categoryId, _ ->
            var result = baseList

            if (categoryId != null && categoryId > 0) {
                result = result.filter { it.categoryId == categoryId }
            } else if (categoryId != null && categoryId == 0L) {
                val validCategoryIds = categories.value.map { it.id }.toSet()
                result = result.filter { it.categoryId !in validCategoryIds }
            }

            if (query.isNotBlank()) {
                result = result.filter { todo ->
                    todo.title.contains(query, ignoreCase = true) ||
                    (todo.content?.contains(query, ignoreCase = true) ?: false) ||
                    (todo.contentFormat?.let { format ->
                        com.corgimemo.app.util.MarkdownParser.stripMarkdown(format)
                            .contains(query, ignoreCase = true)
                    } ?: false)
                }
            }

            result
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // 子任务进度映射：todoId -> 进度文本（如 "2/5"）
    private val _subTaskProgressMap = MutableStateFlow<Map<Long, String>>(emptyMap())
    val subTaskProgressMap: StateFlow<Map<Long, String>> = _subTaskProgressMap.asStateFlow()

    // 子任务列表映射：todoId -> 子任务列表
    private val _subTasksMap = MutableStateFlow<Map<Long, List<SubTask>>>(emptyMap())
    val subTasksMap: StateFlow<Map<Long, List<SubTask>>> = _subTasksMap.asStateFlow()

    // 展开状态集：已展开的待办 ID 集合
    private val _expandedTodos = MutableStateFlow<Set<Long>>(emptySet())
    val expandedTodos: StateFlow<Set<Long>> = _expandedTodos.asStateFlow()

    // ========== 左滑操作区展开状态（用于控制 ModalNavigationDrawer 手势） ==========

    /**
     * 是否有任一待办卡片的左滑操作区处于展开状态
     *
     * 用途：当任何待办卡片左滑展开时，让 MainScreen 临时禁用
     * ModalNavigationDrawer 的 gesturesEnabled，避免用户右滑关闭
     * 操作区时把侧边导航栏也带出来。
     *
     * - true：至少一个 SwipeableTodoBox 处于展开状态
     * - false：所有 SwipeableTodoBox 都已收起
     */
    private val _swipeActionExpanded = MutableStateFlow(false)
    val swipeActionExpanded: StateFlow<Boolean> = _swipeActionExpanded.asStateFlow()

    /**
     * 设置左滑操作区展开状态
     *
     * 由 HomeScreen 在 SwipeableTodoBox 的 onExpandChange 回调中调用，
     * 同步当前是否有卡片处于展开状态到 ViewModel。
     *
     * @param expanded true = 有卡片展开，false = 全部收起
     */
    fun setSwipeActionExpanded(expanded: Boolean) {
        if (_swipeActionExpanded.value != expanded) {
            _swipeActionExpanded.value = expanded
        }
    }

    // ========== 批量操作弹窗显示状态（提升到 ViewModel 以便 MainScreen 触发） ==========

    /**
     * 批量操作弹窗显示状态集合
     *
     * 背景：原 HomeScreen 中批量操作的弹窗（移动/删除确认/MoreOptions 等）状态
     * 全部以 `remember { mutableStateOf(false) }` 形式存在于 HomeScreen 内部。
     * 重构后，批量操作栏已提取到 MainScreen 的 bottomBar 槽位中（详见
     * HomeBatchActionBar），但弹窗渲染仍需在 HomeScreen 内（因弹窗依赖
     * filteredTodos / categories 等 HomeScreen 局部变量）。
     *
     * 因此把这 3 个 boolean 状态提升为 ViewModel 级 StateFlow：
     * - MainScreen 通过调用 setShowBatchXxx(true) 触发显示
     * - HomeScreen 通过 collectAsState() 订阅并渲染对应弹窗
     *
     * 此举保证"触发"和"渲染"两端在状态上的一致性，避免跨组件状态同步问题。
     */
    private val _showBatchDeleteDialog = MutableStateFlow(false)
    val showBatchDeleteDialog: StateFlow<Boolean> = _showBatchDeleteDialog.asStateFlow()

    private val _showBatchMoveDialog = MutableStateFlow(false)
    val showBatchMoveDialog: StateFlow<Boolean> = _showBatchMoveDialog.asStateFlow()

    private val _showMoreOptionsSheet = MutableStateFlow(false)
    val showMoreOptionsSheet: StateFlow<Boolean> = _showMoreOptionsSheet.asStateFlow()

    /** 关闭所有批量弹窗（批量操作完成后由调用方触发） */
    fun dismissAllBatchDialogs() {
        _showBatchDeleteDialog.value = false
        _showBatchMoveDialog.value = false
        _showMoreOptionsSheet.value = false
    }

    /** 设置批量删除确认弹窗显示状态 */
    fun setShowBatchDeleteDialog(show: Boolean) {
        _showBatchDeleteDialog.value = show
    }

    /** 设置批量移动分类选择弹窗显示状态 */
    fun setShowBatchMoveDialog(show: Boolean) {
        _showBatchMoveDialog.value = show
    }

    /** 设置 MoreOptions 菜单弹窗显示状态 */
    fun setShowMoreOptionsSheet(show: Boolean) {
        _showMoreOptionsSheet.value = show
    }

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

    // ========== 新成就系统事件流 ==========

    /**
     * 新成就解锁事件流
     * 用于 UI 层显示成就解锁弹窗
     */
    val achievementUnlockEvents: SharedFlow<com.corgimemo.app.data.model.Achievement> =
        achievementChecker.achievementUnlockEvents

    // ========== 情绪历史记录 ==========

    private val _moodHistory7Days = MutableStateFlow<List<MoodHistory>>(emptyList())
    val moodHistory7Days: StateFlow<List<MoodHistory>> = _moodHistory7Days.asStateFlow()

    // ========== 情绪变化提示 ==========

    private val _moodChangeMessage = MutableStateFlow<String?>(null)
    val moodChangeMessage: StateFlow<String?> = _moodChangeMessage.asStateFlow()

    private var lastMoodChangeHintTime = 0L

    // ========== 待办操作提示 ==========

    private val _todoActionMessage = MutableStateFlow<String?>(null)
    val todoActionMessage: StateFlow<String?> = _todoActionMessage.asStateFlow()

    // ========== 批量选择模式 ==========

    private val _isBatchMode = MutableStateFlow(false)
    val isBatchMode: StateFlow<Boolean> = _isBatchMode.asStateFlow()

    private val _selectedTodoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTodoIds: StateFlow<Set<Long>> = _selectedTodoIds.asStateFlow()

    // ========== 撤销删除相关 ==========

    /** 待删除待办的临时存储（用于撤销）*/
    private val _pendingDeletedTodo = MutableStateFlow<TodoItem?>(null)
    val pendingDeletedTodo: StateFlow<TodoItem?> = _pendingDeletedTodo.asStateFlow()

    /** 删除倒计时任务（可取消）*/
    private var deleteTimerJob: Job? = null

    /** 删除倒计时时长（5秒）*/
    private val UNDO_DELETE_DELAY_MS = 5000L

    // ========== 撤销完成相关 ==========

    /** 待完成/待恢复的待办临时存储（Pair<待办, 操作前是否已完成>）*/
    private val _pendingCompleteTodo = MutableStateFlow<Pair<TodoItem, Boolean>?>(null)
    val pendingCompleteTodo: StateFlow<Pair<TodoItem, Boolean>?> =
        _pendingCompleteTodo.asStateFlow()

    /**
     * 待显示 Snackbar 的批量完成数量
     *
     * 设计：
     * - null = 不显示
     * - 非 null（如 N）= Snackbar "已完成 N 项"
     *
     * 区别于 pendingCompleteTodo（单条），用于批量完成场景。
     * 单条走 pendingCompleteTodo（带撤销），批量无撤销直接通知。
     */
    private val _pendingBatchCompleteCount = MutableStateFlow<Int?>(null)
    val pendingBatchCompleteCount: StateFlow<Int?> =
        _pendingBatchCompleteCount.asStateFlow()

    /** 清除批量完成 Snackbar 状态（HomeScreen 显示完后调用） */
    fun clearPendingBatchComplete() {
        _pendingBatchCompleteCount.value = null
    }

    // ========== 批量复制失败状态（方案 B：删除进度条 UI，仅保留失败 Snackbar） ==========

    /**
     * 批量复制失败状态（非 null 时显示失败 Snackbar）
     *
     * 设计：失败时才反馈，避免进度条 UI 但保留用户感知
     */
    private val _pendingBatchDuplicateFailure = MutableStateFlow(false)
    val pendingBatchDuplicateFailure: StateFlow<Boolean> = _pendingBatchDuplicateFailure.asStateFlow()

    /**
     * 清除"批量复制失败" Snackbar 状态（HomeScreen 显示完后调用）
     */
    fun clearPendingBatchDuplicateFailure() {
        _pendingBatchDuplicateFailure.value = false
    }

    /** 完成操作倒计时任务（可取消）*/
    private var completeTimerJob: Job? = null

    /** 完成倒计时时长（3秒）*/
    private val COMPLETE_UNDO_DELAY_MS = 3000L

    // ========== 批量操作撤销相关 ==========

    /** 批量删除的待办列表临时存储（用于撤销）*/
    private val _pendingBatchDeletes = MutableStateFlow<List<TodoItem>?>(null)
    val pendingBatchDeletes: StateFlow<List<TodoItem>?> = _pendingBatchDeletes.asStateFlow()

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

    // ========== 侧滑导航栏相关状态 ==========

    private val _recentlyDeletedCount = MutableStateFlow(0)
    val recentlyDeletedCount: StateFlow<Int> = _recentlyDeletedCount.asStateFlow()

    /**
     * 每个分类的待办计数（包括全部和未分类）
     */
    val todoCountByCategory: StateFlow<Map<Long, Int>> =
        _todos.combine(categories) { todos, cats ->
            val counts = mutableMapOf<Long, Int>()
            val validCategoryIds = cats.map { it.id }.toSet()
            var uncategorizedCount = 0
            todos.forEach { todo ->
                if (todo.categoryId in validCategoryIds) {
                    counts[todo.categoryId] = (counts[todo.categoryId] ?: 0) + 1
                } else {
                    uncategorizedCount++
                }
            }
            counts[-1L] = todos.size
            counts[0L] = uncategorizedCount
            counts
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
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
        initSortOrder()
        checkStartupBehaviors()
        startIdleDetection()
        recordDailyMoodIfNeeded()
        loadMoodHistory()
        observeUserType()
        checkHoliday()
        checkSolarTerm()
        initAchievements()
        observeRecentlyDeleted()

        viewModelScope.launch {
            corgiPreferences.showCompleted.collect { _showCompleted.value = it }
        }

        // 订阅全局待办事件：编辑页保存/删除后自动刷新首页数据
        // （编辑器的 HomeViewModel 是 editor 自己的 NavBackStackEntry 实例，
        //  它调用的 refreshSubTaskProgress() 无法影响首页实例，因此需要事件总线）
        viewModelScope.launch {
            TodoEventBus.events.collect { event ->
                when (event) {
                    is TodoEvent.TodoSaved -> refreshAllData()
                    is TodoEvent.TodoDeleted -> refreshAllData()
                }
            }
        }
    }

    /**
     * 初始化成就系统
     * 插入所有成就并从旧 JSON 数据迁移
     */
    private fun initAchievements() {
        viewModelScope.launch {
            achievementRepository.initialize()
        }
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
     * 更新待办排序方式
     *
     * @param order 排序方式字符串（"updated_desc" | "updated_asc" | "created_desc" | "created_asc"）
     */
    fun updateSortOrder(order: String) {
        viewModelScope.launch {
            /** 保存排序偏好到 DataStore */
            corgiPreferences.saveSortOrder(order)
            /** 更新本地状态，触发 filteredTodos 重新计算 */
            _sortType.value = order
        }
    }

    /**
     * 拖拽完成后调用（统一入口，支持区域内排序和跨区域拖拽自动完成/取消完成）
     *
     * @param fromIndex 被拖项原始位置（displayItems 全局索引）
     * @param toIndex 被拖项最终位置（displayItems 全局索引）
     * @param crossedPinnedZone 是否跨越置顶区分界线
     */
    fun reorderOnDisplayList(fromIndex: Int, toIndex: Int, crossedPinnedZone: Boolean) {
        viewModelScope.launch {
            val pendingList = pendingTodos.value.toMutableList()
            val completedList = if (_showCompleted.value) {
                visibleCompletedTodos.value.toMutableList()
            } else {
                mutableListOf()
            }
            val dividerIndex = pendingList.size
            val totalSize = pendingList.size + 1 + completedList.size

            if (fromIndex == toIndex) return@launch
            if (fromIndex !in 0 until totalSize || toIndex !in 0..totalSize) return@launch

            val fromPending = fromIndex < dividerIndex
            val fromCompleted = fromIndex > dividerIndex
            val toPending = toIndex < dividerIndex
            val toCompleted = toIndex > dividerIndex

            // 1. 从对应区域移除被拖项
            val draggedItem = when {
                fromPending -> {
                    val idx = fromIndex
                    if (idx !in pendingList.indices) return@launch
                    pendingList.removeAt(idx)
                }
                fromCompleted -> {
                    val idx = fromIndex - dividerIndex - 1
                    if (idx !in completedList.indices) return@launch
                    completedList.removeAt(idx)
                }
                else -> return@launch
            }

            // 2. 处理跨区域：pending→completed 需检查子任务约束
            var finalItem = draggedItem
            var crossCompleted = false
            var crossUncompleted = false

            when {
                fromPending && (toCompleted || toIndex == dividerIndex) -> {
                    // 检查子任务是否全部完成
                    val progress = SubTaskManager.getProgress(context, draggedItem.id)
                    if (progress.total > 0 && progress.completed < progress.total) {
                        _todoActionMessage.value = "还有 ${progress.total - progress.completed} 个子任务未完成，请先完成所有子任务"
                        return@launch
                    }
                    val now = System.currentTimeMillis()
                    finalItem = draggedItem.copy(
                        status = 1,
                        completedAt = now,
                        updatedAt = now,
                        isPinned = if (crossedPinnedZone) !draggedItem.isPinned else draggedItem.isPinned
                    )
                    crossCompleted = true
                }
                fromCompleted && (toPending || toIndex == dividerIndex) -> {
                    finalItem = draggedItem.copy(
                        status = 0,
                        completedAt = null,
                        updatedAt = System.currentTimeMillis(),
                        isPinned = if (crossedPinnedZone) !draggedItem.isPinned else draggedItem.isPinned
                    )
                    crossUncompleted = true
                }
                else -> {
                    // 同区域：仅处理置顶切换
                    if (crossedPinnedZone) {
                        finalItem = draggedItem.copy(isPinned = !draggedItem.isPinned)
                    }
                }
            }

            // 3. 插入到目标区域
            when {
                toPending -> {
                    val insertIdx = toIndex.coerceAtMost(pendingList.size)
                    pendingList.add(insertIdx, finalItem)
                }
                toCompleted -> {
                    val insertIdx = (toIndex - pendingList.size - 1).coerceAtMost(completedList.size)
                    completedList.add(insertIdx, finalItem)
                }
                toIndex == dividerIndex -> {
                    if (fromPending) {
                        // pending→completed，插入completed顶部
                        completedList.add(0, finalItem)
                    } else {
                        // completed→pending，插入pending底部
                        pendingList.add(pendingList.size, finalItem)
                    }
                }
            }

            // 4. 持久化状态变更（完成/未完成/置顶）
            val stateChanged = finalItem.status != draggedItem.status
            val pinChanged = finalItem.isPinned != draggedItem.isPinned
            if (stateChanged || pinChanged) {
                todoRepository.updateTodo(finalItem)
            }
            if (crossCompleted) {
                handleTaskCompleted(draggedItem)
            }

            // 5. 重新分配全局 sortOrder：pending置顶 → pending普通 → completed置顶 → completed普通
            // ━━━ 关键修复：批量更新避免多次 Flow 推送 ━━━
            // 原实现：4 个 forEach 循环各调一次 updateSortOrder，导致 N 次 Flow 推送
            // 和 N 次 displayItems 重组。修复后：单次 updateTodos 批量更新。
            var globalOrder = 0
            val allItemsInOrder = pendingList.filter { it.isPinned } +
                pendingList.filter { !it.isPinned } +
                completedList.filter { it.isPinned } +
                completedList.filter { !it.isPinned }
            val now = System.currentTimeMillis()
            val updates = mutableListOf<TodoItem>()
            allItemsInOrder.forEach { item ->
                val needsUpdate = item.sortOrder != globalOrder || item.id == finalItem.id
                if (needsUpdate) {
                    updates.add(
                        item.copy(
                            sortOrder = globalOrder,
                            updatedAt = now
                        )
                    )
                }
                globalOrder++
            }
            if (updates.isNotEmpty()) {
                android.util.Log.d(
                    "CorgiMemo-Reorder",
                    "[reorder:updateTodos] count=${updates.size}"
                )
                todoRepository.updateTodos(updates)
            }
        }
    }

    /**
     * 合并拖拽批量重排：多选模式下将选中项作为一个整体移动到 toIndex 位置
     *
     * 对应设计文档第十三章 13.2 交互流程"释放"阶段：
     * - 从 pendingList / completedList 中移除所有选中项
     * - 按原 displayItems 相对顺序合并
     * - 插入到 toIndex 位置（根据区域判断 pending/completed）
     * - 处理跨区域状态变更（完成↔未完成）与置顶切换
     * - 重新分配全局 sortOrder：pending置顶 → pending普通 → completed置顶 → completed普通
     *
     * @param selectedIds 已选中项的 id 集合
     * @param toIndex 占位框在 displayItems 中的目标位置
     * @param crossedPinnedZone 是否跨越置顶区分界线
     */
    fun mergeReorderOnDisplayList(
        selectedIds: Set<Long>,
        toIndex: Int,
        crossedPinnedZone: Boolean
    ) {
        viewModelScope.launch {
            if (selectedIds.size <= 1) return@launch

            val pendingList = pendingTodos.value.toMutableList()
            val completedList = if (_showCompleted.value) {
                visibleCompletedTodos.value.toMutableList()
            } else {
                mutableListOf()
            }
            val dividerIndex = pendingList.size
            val totalSize = pendingList.size + 1 + completedList.size

            if (toIndex !in 0..totalSize) return@launch

            // 1. 收集所有选中项（按各自列表中的原始顺序，保留相对顺序）
            val selectedPending = pendingList.mapIndexedNotNull { idx, item ->
                if (item.id in selectedIds) idx to item else null
            }
            val selectedCompleted = completedList.mapIndexedNotNull { idx, item ->
                if (item.id in selectedIds) idx to item else null
            }

            if (selectedPending.isEmpty() && selectedCompleted.isEmpty()) return@launch

            // 2. 从原位置移除（从后往前移除避免索引错位）
            selectedPending.sortedByDescending { it.first }.forEach { (idx, _) ->
                pendingList.removeAt(idx)
            }
            selectedCompleted.sortedByDescending { it.first }.forEach { (idx, _) ->
                completedList.removeAt(idx)
            }

            // 3. 按原 displayItems 顺序合并选中项
            val mergedItems = (selectedPending.map { it.second } +
                selectedCompleted.map { it.second }).toMutableList()

            // 4. 计算目标区域（dividerIndex 在移除后已变化，用原 dividerIndex 判断）
            val toPending = toIndex < dividerIndex
            val toCompleted = toIndex > dividerIndex

            // 5. 处理跨区域状态变更（完成↔未完成、置顶切换）
            val finalItems = mergedItems.map { item ->
                var finalItem = item
                if (item.status == 0 && toCompleted) {
                    // pending → completed：标记完成
                    val now = System.currentTimeMillis()
                    finalItem = item.copy(
                        status = 1,
                        completedAt = now,
                        updatedAt = now,
                        isPinned = if (crossedPinnedZone) !item.isPinned else item.isPinned
                    )
                } else if (item.status == 1 && (toPending || toIndex == dividerIndex)) {
                    // completed → pending：标记未完成
                    finalItem = item.copy(
                        status = 0,
                        completedAt = null,
                        updatedAt = System.currentTimeMillis(),
                        isPinned = if (crossedPinnedZone) !item.isPinned else item.isPinned
                    )
                } else if (crossedPinnedZone) {
                    // 同区域：仅处理置顶切换
                    finalItem = item.copy(isPinned = !item.isPinned)
                }
                finalItem
            }

            // 6. 插入到目标区域
            when {
                toPending -> {
                    val insertIdx = toIndex.coerceAtMost(pendingList.size)
                    pendingList.addAll(insertIdx, finalItems)
                }
                toCompleted -> {
                    val insertIdx = (toIndex - pendingList.size - 1).coerceAtMost(completedList.size)
                    completedList.addAll(insertIdx, finalItems)
                }
                toIndex == dividerIndex -> {
                    pendingList.addAll(pendingList.size, finalItems)
                }
            }

            // 7. 持久化状态变更
            finalItems.forEachIndexed { idx, finalItem ->
                val originalItem = mergedItems[idx]
                val stateChanged = finalItem.status != originalItem.status
                val pinChanged = finalItem.isPinned != originalItem.isPinned
                if (stateChanged || pinChanged) {
                    todoRepository.updateTodo(finalItem)
                }
            }

            // 8. 重新分配全局 sortOrder：pending置顶 → pending普通 → completed置顶 → completed普通
            // ━━━ 关键修复：批量更新避免多次 Flow 推送 ━━━
            // 原实现：4 个 forEach 循环各调一次 updateSortOrder，导致 N 次 Flow 推送
            // 和 N 次 displayItems 重组。修复后：单次 updateTodos 批量更新。
            var globalOrder = 0
            val allItemsInOrder = pendingList.filter { it.isPinned } +
                pendingList.filter { !it.isPinned } +
                completedList.filter { it.isPinned } +
                completedList.filter { !it.isPinned }
            val now = System.currentTimeMillis()
            val updates = mutableListOf<TodoItem>()
            // 记录已变更项的 id，用于精准判断"需要更新"
            val changedIds = finalItems.map { it.id }.toSet()
            allItemsInOrder.forEach { item ->
                val needsUpdate = item.sortOrder != globalOrder || item.id in changedIds
                if (needsUpdate) {
                    updates.add(
                        item.copy(
                            sortOrder = globalOrder,
                            updatedAt = now
                        )
                    )
                }
                globalOrder++
            }
            if (updates.isNotEmpty()) {
                android.util.Log.d(
                    "CorgiMemo-Reorder",
                    "[reorder:updateTodos] count=${updates.size}"
                )
                todoRepository.updateTodos(updates)
            }

            // 9. 退出多选模式
            exitBatchMode()
        }
    }

    /**
     * 恢复默认排序：按当前 sortType 重算所有待办的 sortOrder
     *
     * 触发场景：用户在排序菜单点击"恢复默认排序"按钮
     */
    fun restoreDefaultOrder() {
        viewModelScope.launch {
            val currentList = _todos.value
            val naturalOrder = when (_sortType.value) {
                "updated_desc" -> currentList.sortedByDescending { it.updatedAt }
                "updated_asc"  -> currentList.sortedBy { it.updatedAt }
                "created_desc" -> currentList.sortedByDescending { it.createdAt }
                "created_asc"  -> currentList.sortedBy { it.createdAt }
                else -> currentList.sortedByDescending { it.updatedAt }
            }
            // ━━━ 关键修复：批量更新避免多次 Flow 推送 ━━━
            // 同 isPinned 分区内重新分配 sortOrder，使用单次 updateTodos 批量提交
            val now = System.currentTimeMillis()
            val updates = mutableListOf<TodoItem>()
            val orderByPinned = mutableMapOf<Boolean, Int>()
            naturalOrder.forEach { item ->
                val next = (orderByPinned[item.isPinned] ?: -1) + 1
                orderByPinned[item.isPinned] = next
                if (item.sortOrder != next) {
                    updates.add(
                        item.copy(
                            sortOrder = next,
                            updatedAt = now
                        )
                    )
                }
            }
            if (updates.isNotEmpty()) {
                android.util.Log.d(
                    "CorgiMemo-Reorder",
                    "[reorder:updateTodos] count=${updates.size}"
                )
                todoRepository.updateTodos(updates)
            }
        }
    }

    /**
     * 切换排序模式：保存偏好 + 重算 sortOrder
     *
     * 与原 updateSortOrder() 区别：此方法在切换后立即按新规则重算 sortOrder，
     * 确保后续列表显示完全由 sortOrder 决定（sortType 不参与显示排序）。
     *
     * @param newSortType 新排序模式（"updated_desc" | "updated_asc" | "created_desc" | "created_asc"）
     */
    fun onSortTypeChanged(newSortType: String) {
        viewModelScope.launch {
            corgiPreferences.saveSortOrder(newSortType)
            _sortType.value = newSortType
            restoreDefaultOrder()
        }
    }

    /**
     * 初始化排序方式（从 DataStore 读取保存的偏好）
     */
    private fun initSortOrder() {
        viewModelScope.launch {
            val savedSortOrder = corgiPreferences.getSortOrder()
            _sortType.value = savedSortOrder
        }
    }

    // ========== 侧滑导航栏相关方法 ==========

    /**
     * 观察最近删除待办数量
     */
    private fun observeRecentlyDeleted() {
        viewModelScope.launch {
            deletedTodoRepository.getDeletedCount().collect { count ->
                _recentlyDeletedCount.value = count
            }
        }
    }

    /**
     * 设置分类过滤
     *
     * @param categoryId 分类 ID（null=全部，0=未分类，>0=具体分类）
     */
    fun filterByCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    /**
     * 清除分类过滤，显示所有待办
     */
    fun clearCategoryFilter() {
        _selectedCategoryId.value = null
    }

    /**
     * 创建新分类
     *
     * @param name 分类名称
     */
    fun createCategory(name: String) {
        viewModelScope.launch {
            categoryRepository.insertCategory(
                com.corgimemo.app.data.model.Category(
                    name = name,
                    type = com.corgimemo.app.data.model.CategoryType.CUSTOM,
                    isDefault = false
                )
            )
        }
    }

    /**
     * 重命名分类
     *
     * @param id 分类 ID
     * @param newName 新名称
     */
    fun renameCategory(id: Long, newName: String) {
        viewModelScope.launch {
            val category = categoryRepository.getCategoryById(id) ?: return@launch
            categoryRepository.insertCategory(category.copy(name = newName))
        }
    }

    /**
     * 删除自定义分类
     *
     * @param id 分类 ID
     */
    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categoryRepository.deleteCustomCategory(id)
            if (_selectedCategoryId.value == id) {
                _selectedCategoryId.value = null
            }
        }
    }

    /**
     * 获取所有已删除待办
     */
    fun getRecentlyDeletedTodos(): kotlinx.coroutines.flow.Flow<List<com.corgimemo.app.data.model.DeletedTodo>> {
        return deletedTodoRepository.getAllDeletedTodos()
    }

    /**
     * 恢复已删除的待办
     *
     * @param deletedTodoId 已删除待办的 ID
     */
    fun restoreDeletedTodo(deletedTodoId: Long) {
        viewModelScope.launch {
            val deleted = deletedTodoRepository.restoreDeletedTodo(deletedTodoId) ?: return@launch
            val todo = com.corgimemo.app.data.model.DeletedTodo.toTodoItem(deleted)
            todoRepository.insertTodo(todo)
            deletedTodoRepository.permanentlyDelete(deletedTodoId)
        }
    }

    /**
     * 永久删除所有最近删除记录
     */
    fun permanentlyDeleteAllDeleted() {
        viewModelScope.launch {
            deletedTodoRepository.permanentlyDeleteAll()
        }
    }

    /**
     * 清理超过指定时间的最近删除记录
     *
     * @param threshold 时间阈值（毫秒）
     */
    fun cleanUpOldDeletedTodos(threshold: Long) {
        viewModelScope.launch {
            deletedTodoRepository.cleanUpOldDeletedTodos(threshold)
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
     * 加载待办列表及子任务进度
     */
    private fun loadTodos() {
        viewModelScope.launch {
            todoRepository.observeAllSorted().collect { allTodos ->
                _todos.value = allTodos

                // 标记数据已初始化完成（首次加载后不再重置，避免闪烁）
                if (!_isDataInitialized.value) {
                    _isDataInitialized.value = true
                }

                // 加载所有待办的子任务进度和子任务列表
                val progressMap = mutableMapOf<Long, String>()
                val subTasksMap = mutableMapOf<Long, List<SubTask>>()
                for (todo in allTodos) {
                    val progress = SubTaskManager.getProgressText(context, todo.id)
                    if (progress != null) {
                        progressMap[todo.id] = progress
                        // 同时加载子任务列表
                        val subTasks = SubTaskManager.getSubTasks(context, todo.id)
                        subTasksMap[todo.id] = subTasks
                    }
                }
                _subTaskProgressMap.value = progressMap
                _subTasksMap.value = subTasksMap

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
     * 切换"已完成"区域展开/折叠状态
     */
    fun toggleShowCompleted() {
        val newVal = !_showCompleted.value
        _showCompleted.value = newVal
        viewModelScope.launch {
            corgiPreferences.setShowCompleted(newVal)
        }
    }

    /**
     * 更新搜索关键词
     *
     * @param query 新的搜索关键词（支持标题和内容匹配）
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * 清空搜索关键词
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    // ========== 下拉刷新相关 ==========

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * 下拉刷新
     * 重新加载待办列表和柯基数据
     */
    fun onRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadTodos()
            refreshSubTaskProgress()
            refreshGreetingIfNeeded()
            kotlinx.coroutines.delay(800) // 确保柯基动画至少显示 800ms
            _isRefreshing.value = false
        }
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
     * 获取预计完成时间
     * 计算方式：startDate + estimatedDurationMinutes
     *
     * @param todo 待办项
     * @return 预计完成时间戳，如果没有 startDate 或 estimatedDurationMinutes 则返回 null
     */
    private fun getEstimatedEndTime(todo: TodoItem): Long? {
        return if (todo.startDate != null && todo.estimatedDurationMinutes != null) {
            todo.startDate + todo.estimatedDurationMinutes * 60000L
        } else {
            null
        }
    }

    /**
     * 获取今日总任务数
     */
    private fun getTodayTotalCount(): Int {
        val currentTime = System.currentTimeMillis()
        return _todos.value.count { todo ->
            MoodManager.isToday(todo.createdAt, currentTime) ||
                    (getEstimatedEndTime(todo) != null && MoodManager.isToday(getEstimatedEndTime(todo)!!, currentTime))
        }
    }

    /**
     * 获取超期任务数
     */
    private fun getOverdueTasksCount(): Int {
        val currentTime = System.currentTimeMillis()
        return _todos.value.count { todo ->
            todo.status == 0 && MoodManager.isOverdue(getEstimatedEndTime(todo), currentTime)
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
     * 清除待办操作提示消息
     */
    fun clearTodoActionMessage() {
        _todoActionMessage.value = null
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

                // 新成就系统：检测每日相关成就
                achievementChecker.checkOnDailyOpen()

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

            // 新成就系统：检测等级成就
            achievementChecker.checkOnLevelUp(newLevel)
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
     * 如果用户尝试勾选完成但子任务未全部完成，则阻止操作并显示提示
     */
    fun toggleTodoStatus(id: Long, isChecked: Boolean) {
        viewModelScope.launch {
            todoRepository.getTodoById(id)?.let { todo ->
                // 如果是勾选完成状态，先检查子任务是否全部完成
                if (isChecked) {
                    val progress = SubTaskManager.getProgress(context, id)
                    // 如果有子任务但未全部完成，则阻止完成操作并显示提示
                    if (progress.total > 0 && progress.completed < progress.total) {
                        val unfinishedCount = progress.total - progress.completed
                        _todoActionMessage.value = "还有 $unfinishedCount 个子任务未完成，请先完成所有子任务"
                        return@launch
                    }
                }

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
     * 刷新子任务进度
     * 当从编辑页面返回时调用，确保子任务进度更新
     */
    fun refreshSubTaskProgress() {
        viewModelScope.launch {
            val allTodos = todoRepository.getAllTodos().first()
            val progressMap = mutableMapOf<Long, String>()
            val subTasksMap = mutableMapOf<Long, List<SubTask>>()
            for (todo in allTodos) {
                val progress = SubTaskManager.getProgressText(context, todo.id)
                if (progress != null) {
                    progressMap[todo.id] = progress
                    val subTasks = SubTaskManager.getSubTasks(context, todo.id)
                    subTasksMap[todo.id] = subTasks
                }
            }
            _subTaskProgressMap.value = progressMap
            _subTasksMap.value = subTasksMap
        }
    }

    /**
     * 全量刷新首页数据
     *
     * 触发场景：
     * 1. 编辑页保存/删除后通过 [TodoEventBus] 触发
     * 2. 下拉刷新兜底
     *
     * 包含：
     * - `_todos`（按当前过滤器重算）
     * - `_subTaskProgressMap`（子任务进度文本）
     * - `_subTasksMap`（子任务列表，含 imagePaths/voicePaths）
     *
     * 与旧版 [refreshSubTaskProgress] 的区别：
     * - 同时刷新 _todos（避免父待办的 imagePaths/voiceNotePath 等字段不更新）
     * - 总是加载子任务列表（无 `if (progress != null)` 守卫，
     *   确保新建子任务后第一次进入首页就能看到附件计数）
     */
    fun refreshAllData() {
        viewModelScope.launch {
            val allTodos = todoRepository.getAllTodos().first()
            _todos.value = allTodos
            val progressMap = mutableMapOf<Long, String>()
            val subTasksMap = mutableMapOf<Long, List<SubTask>>()
            for (todo in allTodos) {
                // 修复守卫：解耦 progress 和 subTasks 加载
                SubTaskManager.getProgressText(context, todo.id)?.let { progress ->
                    progressMap[todo.id] = progress
                }
                // 总是加载子任务列表（无论是否有 progress text）
                val subTasks = SubTaskManager.getSubTasks(context, todo.id)
                subTasksMap[todo.id] = subTasks
            }
            _subTaskProgressMap.value = progressMap
            _subTasksMap.value = subTasksMap
        }
    }

    /**
     * 切换待办展开状态
     *
     * @param todoId 待办 ID
     */
    fun toggleExpand(todoId: Long) {
        val currentExpanded = _expandedTodos.value.toMutableSet()
        if (currentExpanded.contains(todoId)) {
            currentExpanded.remove(todoId)
        } else {
            currentExpanded.add(todoId)
        }
        _expandedTodos.value = currentExpanded
    }

    /**
     * 切换待办置顶状态
     *
     * 用于左滑操作"置顶"按钮。
     * 置顶后该待办在列表中始终排在最前面（按 isPinned DESC 排序）。
     *
     * @param todoId 待办 ID
     */
    fun togglePin(todoId: Long) {
        viewModelScope.launch {
            todoRepository.togglePin(todoId)
        }
    }

    /**
     * 切换子任务完成状态
     * 如果所有子任务完成，会自动完成父任务
     *
     * @param subTaskId 子任务 ID
     */
    fun toggleSubTaskCompletion(subTaskId: Long) {
        viewModelScope.launch {
            val result = SubTaskManager.toggleSubTaskCompletion(context, subTaskId)

            // 刷新子任务进度和列表
            val allTodos = todoRepository.getAllTodos().first()
            val progressMap = mutableMapOf<Long, String>()
            val subTasksMap = mutableMapOf<Long, List<SubTask>>()
            for (todo in allTodos) {
                val progress = SubTaskManager.getProgressText(context, todo.id)
                if (progress != null) {
                    progressMap[todo.id] = progress
                    val subTasks = SubTaskManager.getSubTasks(context, todo.id)
                    subTasksMap[todo.id] = subTasks
                }
            }
            _subTaskProgressMap.value = progressMap
            _subTasksMap.value = subTasksMap

            // 如果父任务被自动完成，触发完成逻辑
            if (result.parentTodoCompleted) {
                result.updatedSubTask?.let { subTask ->
                    val parentTodo = todoRepository.getTodoById(subTask.todoId)
                    if (parentTodo != null) {
                        handleTaskCompleted(parentTodo)
                    }
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

        // 处理重复任务：自动创建下一周期的任务
        RepeatTaskManager.handleRepeatTaskCompletion(context, todo)

        checkAchievements()

        // 新成就系统：检测任务完成相关成就
        achievementChecker.checkOnTaskComplete(todo)

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
     * 预计完成时间当天完成的任务优先级最高
     *
     * @param todo 任务项
     * @return 庆祝级别
     */
    private fun calculateCelebrationLevel(todo: TodoItem): CelebrationLevel {
        val currentTime = System.currentTimeMillis()
        val estimatedEndTime = getEstimatedEndTime(todo)

        // 检查是否预计完成时间当天完成（优先级最高）
        if (estimatedEndTime != null && MoodManager.isToday(estimatedEndTime, currentTime)) {
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
            refreshSubTaskProgress()
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
     * 柯基互动增加经验值（公开方法）
     * 用于柯基详情页的抚摸/喂食/玩耍互动
     *
     * @param amount 经验值数量（默认 5）
     */
    fun addInteractionExperience(amount: Int = 5) {
        viewModelScope.launch {
            addExperience(amount)
        }
    }

    /**
     * 获取本周完成数
     * 用于柯基详情页数据统计区
     *
     * @return 本周完成的任务数
     */
    suspend fun getWeeklyCompletedCount(): Int {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val calendar = java.util.Calendar.getInstance()
            // 设置到本周一
            calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            val startDate = sdf.format(calendar.time)
            // 结束日期为今天
            val endDate = sdf.format(java.util.Date())
            val stats = taskDailyStatsRepository.getStatsByDateRange(startDate, endDate)
            stats.sumOf { it.workCompleted + it.studyCompleted + it.lifeCompleted + it.entertainmentCompleted }
        } catch (_: Exception) {
            0
        }
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
            /** 1. 先获取完整的待办对象（用于撤销）*/
            val todo = todoRepository.getTodoById(id) ?: return@launch

            /** 2. 立即从数据库删除（让列表立即更新）*/
            todoRepository.deleteTodoById(id)

            /** 3. 临时存储待办 */
            _pendingDeletedTodo.value = todo

            /** 4. 取消之前的倒计时任务（如果有）*/
            deleteTimerJob?.cancel()

            /** 5. 启动新的倒计时 */
            deleteTimerJob = launch {
                delay(UNDO_DELETE_DELAY_MS)
                /** 倒计时结束，清除临时数据并记录确认日志 */
                val pendingTodo = _pendingDeletedTodo.value
                if (pendingTodo != null) {
                    recordOperationLog(
                        operationType = com.corgimemo.app.data.local.db.OperationType.DELETE,
                        targetId = id,
                        snapshotJson = todoToJson(pendingTodo)
                    )
                }
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

            /** 1. 取消倒计时任务 */
            deleteTimerJob?.cancel()
            deleteTimerJob = null

            /** 2. 重新插入待办 */
            todoRepository.insertTodo(todo)

            /** 3. 记录撤销操作日志 */
            recordOperationLog(
                operationType = com.corgimemo.app.data.local.db.OperationType.UNDO_DELETE,
                targetId = todo.id,
                snapshotJson = todoToJson(todo)
            )

            /** 4. 清除临时数据 */
            _pendingDeletedTodo.value = null
        }
    }

    /**
     * 切换待办完成状态（支持撤销）
     *
     * @param id 待办 ID
     */
    fun toggleComplete(id: Long) {
        viewModelScope.launch {
            /** 1. 获取当前待办 */
            val todo = todoRepository.getTodoById(id) ?: return@launch

            /** 2. 记录操作前的状态 */
            val wasCompleted = todo.status == 1
            val currentTime = System.currentTimeMillis()

            /** 3. 更新状态（切换）*/
            val newStatus = if (wasCompleted) 0 else 1
            val updatedTodo = if (newStatus == 1) {
                todo.copy(
                    status = 1,
                    completedAt = currentTime,
                    updatedAt = currentTime
                )
            } else {
                todo.copy(
                    status = 0,
                    completedAt = null,
                    updatedAt = currentTime
                )
            }

            todoRepository.updateTodo(updatedTodo)

            /** 4. 如果是完成操作，触发成就检测等副作用 */
            if (!wasCompleted) {
                handleTaskCompleted(updatedTodo)
            }

            /** 5. 存储到内存状态用于撤销 */
            _pendingCompleteTodo.value = Pair(todo.copy(), wasCompleted)

            /** 6. 取消之前的倒计时任务（如果有）*/
            completeTimerJob?.cancel()

            /** 7. 启动新的倒计时 */
            completeTimerJob = launch {
                delay(COMPLETE_UNDO_DELAY_MS)
                /** 倒计时结束，清除临时数据并记录确认日志 */
                val pendingTodo = _pendingCompleteTodo.value
                if (pendingTodo != null) {
                    recordOperationLog(
                        operationType = com.corgimemo.app.data.local.db.OperationType.COMPLETE,
                        targetId = id,
                        snapshotJson = todoToJson(pendingTodo.first)
                    )
                }
                _pendingCompleteTodo.value = null
            }
        }
    }

    /**
     * 撤销完成操作
     * 恢复待办到操作前的状态
     */
    fun undoComplete() {
        viewModelScope.launch {
            val pair = _pendingCompleteTodo.value ?: return@launch
            val (originalTodo, wasCompleted) = pair

            /** 1. 取消倒计时任务 */
            completeTimerJob?.cancel()
            completeTimerJob = null

            /** 2. 恢复原始状态 */
            todoRepository.updateTodo(originalTodo)

            /** 3. 记录撤销操作日志 */
            recordOperationLog(
                operationType = com.corgimemo.app.data.local.db.OperationType.UNDO_COMPLETE,
                targetId = originalTodo.id,
                snapshotJson = todoToJson(originalTodo)
            )

            /** 4. 清除临时数据 */
            _pendingCompleteTodo.value = null
        }
    }

    /**
     * 批量删除选中的待办（支持撤销）
     */
    fun batchDelete() {
        val selectedIds = _selectedTodoIds.value
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            /** 1. 获取所有选中的待办对象 */
            val todosToDelete = mutableListOf<TodoItem>()
            selectedIds.forEach { id ->
                val todo = todoRepository.getTodoById(id)
                if (todo != null) {
                    todosToDelete.add(todo)
                }
            }

            if (todosToDelete.isEmpty()) {
                exitBatchMode()
                return@launch
            }

            /** 2. 从数据库删除所有选中待办 */
            selectedIds.forEach { id ->
                todoRepository.deleteTodoById(id)
            }

            exitBatchMode()

            /** 3. 存储到内存状态用于撤销 */
            _pendingBatchDeletes.value = todosToDelete

            /** 4. 取消之前的倒计时任务（如果有）*/
            deleteTimerJob?.cancel()

            /** 5. 启动新的倒计时 */
            deleteTimerJob = launch {
                delay(UNDO_DELETE_DELAY_MS)
                /** 倒计时结束，清除临时数据并记录确认日志 */
                val batchTodos = _pendingBatchDeletes.value
                if (batchTodos != null) {
                    val idsJson = batchTodos.joinToString(",", "[", "]") { it.id.toString() }
                    recordOperationLog(
                        operationType = com.corgimemo.app.data.local.db.OperationType.BATCH_DELETE,
                        targetId = 0,
                        batchIdsJson = idsJson,
                        snapshotJson = batchTodos.joinToString(",", "[", "]") { todoToJson(it) }
                    )
                }
                _pendingBatchDeletes.value = null
            }
        }
    }

    /**
     * 撤销批量删除
     * 将所有被删除的待办重新插入数据库
     */
    fun undoBatchDelete() {
        viewModelScope.launch {
            val todos = _pendingBatchDeletes.value ?: return@launch

            /** 1. 取消倒计时任务 */
            deleteTimerJob?.cancel()
            deleteTimerJob = null

            /** 2. 重新插入所有待办 */
            todos.forEach { todo ->
                todoRepository.insertTodo(todo)
            }

            /** 3. 记录撤销操作日志 */
            val idsJson = todos.joinToString(",", "[", "]") { it.id.toString() }
            recordOperationLog(
                operationType = com.corgimemo.app.data.local.db.OperationType.UNDO_DELETE,
                targetId = 0,
                batchIdsJson = idsJson,
                snapshotJson = todos.joinToString(",", "[", "]") { todoToJson(it) }
            )

            /** 4. 清除临时数据 */
            _pendingBatchDeletes.value = null
        }
    }

    /**
     * 从模板批量创建待办事项
     * 将模板中的所有待办项逐一添加到数据库
     *
     * @param template 待办模板对象
     */
    fun createTodosFromTemplate(template: com.corgimemo.app.data.model.TodoTemplate) {
        viewModelScope.launch {
            template.todos.forEach { templateTodo ->
                val newTodo = com.corgimemo.app.data.model.TodoItem(
                    title = templateTodo.title,
                    content = "来自「${template.name}」模板",
                    categoryId = 0L,
                    priority = 1,
                    status = 0,
                    repeatType = 0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                /** 插入新待办到数据库 */
                todoRepository.insertTodo(newTodo)
            }

            /** 触发用户互动（用于柯基情绪和等级系统）*/
            onUserInteraction()
        }
    }

    /**
     * 快速添加待办事项
     * 用于悬浮柯基按钮左滑触发的快速添加功能
     *
     * @param title 待办标题
     * @param categoryId 分类 ID
     * @param priority 优先级（0=高 1=中 2=低）
     */
    fun quickAddTodo(title: String, categoryId: Long, priority: Int) {
        viewModelScope.launch {
            val newTodo = com.corgimemo.app.data.model.TodoItem(
                title = title,
                categoryId = categoryId,
                priority = priority,
                status = 0,
                repeatType = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            todoRepository.insertTodo(newTodo)
            onUserInteraction()
        }
    }

    /**
     * 完成首次引导流程
     * 触发庆祝动画、成就解锁和经验值奖励
     *
     * @param context 应用上下文（用于震动反馈）
     */
    fun completeFirstGuide(context: android.content.Context) {
        viewModelScope.launch {
            /** 1. 标记引导完成（已在 HomeScreen 中处理）*/

            /** 2. 显示庆祝动画（SUPER 级别）*/
            _celebrationState.value = com.corgimemo.app.viewmodel.CelebrationState(
                isShowing = true,
                level = com.corgimemo.app.viewmodel.CelebrationLevel.SUPER,
                message = "🎉 欢迎加入 CorgiMemo！"
            )

            /** 3. 延迟触发成就解锁（在庆祝动画之后）*/
            kotlinx.coroutines.delay(1500)

            /** 4. 解锁"新手探险家"成就*/
            achievementChecker.unlockAchievementManual(
                com.corgimemo.app.animation.AchievementId.FIRST_GUIDE
            )

            /** 5. 震动反馈（成就解锁长震动）*/
            HapticFeedbackManager.performHapticFeedback(
                context = context,
                type = InteractionType.ACHIEVEMENT_UNLOCK,
                enabled = hapticEnabled.value
            )

            /** 6. 设置庆祝姿态*/
            setPoseForCelebrating()

            /** 7. 3 秒后自动隐藏庆祝动画*/
            kotlinx.coroutines.delay(3000)
            _celebrationState.value = com.corgimemo.app.viewmodel.CelebrationState(
                isShowing = false,
                level = com.corgimemo.app.viewmodel.CelebrationLevel.LOW,
                message = ""
            )
        }
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
            MoodManager.isOverdue(getEstimatedEndTime(todo))
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

    // ==================== 批量选择模式方法 ====================

    /**
     * 进入批量模式
     * @param todoId 初始选中的待办 ID
     */
    fun enterBatchMode(todoId: Long) {
        _isBatchMode.value = true
        _selectedTodoIds.value = setOf(todoId)
    }

    /**
     * 退出批量模式，清空选择
     */
    fun exitBatchMode() {
        _isBatchMode.value = false
        _selectedTodoIds.value = emptySet()
    }

    /**
     * 切换待办选中状态
     */
    fun toggleSelection(todoId: Long) {
        val currentSelection = _selectedTodoIds.value
        if (currentSelection.contains(todoId)) {
            _selectedTodoIds.value = currentSelection - todoId
        } else {
            _selectedTodoIds.value = currentSelection + todoId
        }
    }

    /**
     * 全选当前可见待办
     */
    fun selectAll() {
        _selectedTodoIds.value = _todos.value.map { it.id }.toSet()
    }

    /**
     * 取消全选
     */
    fun clearSelection() {
        _selectedTodoIds.value = emptySet()
    }

    /**
     * 是否有选中项
     */
    fun hasSelection(): Boolean {
        return _selectedTodoIds.value.isNotEmpty()
    }

    /**
     * 获取选中的待办列表
     */
    private fun getSelectedTodos(): List<TodoItem> {
        val selectedIds = _selectedTodoIds.value
        return _todos.value.filter { selectedIds.contains(it.id) }
    }

    /**
     * 批量完成选中的待办（多选模式"完成"按钮）
     *
     * 用户反馈：原实现串行 updateTodo + 每条 handleTaskCompleted，
     * 导致：
     * 1. 视觉上"逐条完成"（数据库多次更新，UI 多次重组）
     * 2. 震动反馈多次触发（每条都震动）
     *
     * 修复策略：
     * 1. 收集所有待更新的 todos → 一次性 todoRepository.updateTodos() 批量更新
     * 2. 累计统计：经验、完成数、连续完成、心情重算等"全局"操作放在循环外只执行一次
     * 3. 重复任务处理：每条独立处理（RepeatTaskManager 行为不同）
     * 4. 震动反馈：循环外统一触发一次
     * 5. 庆祝动画：循环外触发一次（取最高优先级待办的庆祝级别）
     */
    fun batchComplete() {
        val selectedIds = _selectedTodoIds.value
        if (selectedIds.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        viewModelScope.launch {
            // 1. 收集待更新的 todos（仅未完成的）
            val todosToUpdate = mutableListOf<TodoItem>()
            val originalTodos = mutableListOf<TodoItem>()
            selectedIds.forEach { id ->
                val todo = todoRepository.getTodoById(id)
                if (todo != null && todo.status == 0) {
                    todosToUpdate.add(
                        todo.copy(
                            status = 1,
                            completedAt = currentTime,
                            updatedAt = currentTime
                        )
                    )
                    originalTodos.add(todo)
                }
            }

            if (todosToUpdate.isEmpty()) {
                exitBatchMode()
                return@launch
            }

            // 2. 一次性批量更新（数据库原子事务，UI 单次重组）
            todoRepository.updateTodos(todosToUpdate)

            // 3. 累计统计：每条都计入完成（不重复 addExperience/统计）
            val completedCount = todosToUpdate.size
            batchHandleCompletedTasks(todosToUpdate, completedCount)

            // 4. 重复任务处理：每条独立（不同待办可能有不同 repeatType）
            originalTodos.forEach { todo ->
                RepeatTaskManager.handleRepeatTaskCompletion(context, todo)
            }

            // 5. 触发单次震动反馈（修复前是每条震动一次）
            triggerHapticFeedback(InteractionType.TASK_COMPLETE)

            // 6. 触发单次庆祝动画（取最高优先级待办的庆祝级别）
            triggerBatchCelebration(originalTodos)

            // 7. 通知 UI 显示批量完成 Snackbar "已完成 N 项"
            _pendingBatchCompleteCount.value = completedCount

            // 8. 退出批量模式
            exitBatchMode()
        }
    }

    /**
     * 批量完成后的累计统计处理（一次性）
     *
     * 与单条 handleTaskCompleted 的区别：
     * - 经验值累加：每条都加，但只调用一次 addExperience（传入总经验）
     * - 完成数累加：每条都 +1，但只调用一次 incrementTotalCompleted
     * - 成就检测：每条都检测，但只调用一次 checkAchievements
     * - 心情重算：每条都参与，但只调用一次 recalculateMoodSuspend
     *
     * 这样避免"经验值 / 等级提升动画"被多次重复触发。
     */
    private suspend fun batchHandleCompletedTasks(
        completedTodos: List<TodoItem>,
        count: Int
    ) {
        val currentData = _corgiData.value ?: return

        // 1. 记录任务完成时间（仅记录一次连续完成时间）
        recordTaskCompletion()

        // 2. 经验值累加：每条基础经验 × 数量，一次性加
        val expGain = LevelManager.getExpOnTaskComplete() * count
        addExperience(expGain)

        // 3. 累计完成数（一次性 +count）
        repeat(count) {
            corgiRepository.incrementTotalCompleted()
        }
        _corgiData.value = currentData.copy(totalCompleted = currentData.totalCompleted + count)

        // 4. 成就检测：每条独立检测
        completedTodos.forEach { todo ->
            achievementChecker.checkOnTaskComplete(todo)
        }
        checkAchievements()

        // 5. 心情重算（一次性）
        recalculateMoodSuspend()
    }

    /**
     * 批量完成的庆祝动画（一次性，取最高级别）
     *
     * 修复前：每条 todo 都触发庆祝动画，多次触发造成 UI 卡顿
     * 修复后：取所有完成项中优先级最高的（SUPER > HIGH > MEDIUM > LOW），
     *        只触发一次庆祝动画
     */
    private fun triggerBatchCelebration(completedTodos: List<TodoItem>) {
        if (completedTodos.isEmpty()) return
        // 取最高优先级
        val highestPriorityTodo = completedTodos.maxByOrNull { it.priority } ?: return
        val level = calculateCelebrationLevel(highestPriorityTodo)
        val message = getEncouragementMessage(level)
        val duration = getCelebrationDuration(level)
        _celebrationState.value = CelebrationState(
            isShowing = true,
            level = level,
            message = message
        )
    }

    /**
     * 批量置顶选中的待办（统一置顶）
     *
     * 仅对未置顶的待办更新（避免无谓的 updatedAt 变化）。
     */
    fun batchPin() {
        val selectedIds = _selectedTodoIds.value
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            selectedIds.forEach { id ->
                todoRepository.getTodoById(id)?.let { todo ->
                    if (!todo.isPinned) {
                        todoRepository.updateTodo(
                            todo.copy(
                                isPinned = true,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * 批量设置优先级
     *
     * @param priority 0=无 1=低 2=中 3=高
     */
    fun batchUpdatePriority(priority: Int) {
        val selectedIds = _selectedTodoIds.value
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            selectedIds.forEach { id ->
                todoRepository.getTodoById(id)?.let { todo ->
                    if (todo.priority != priority) {
                        todoRepository.updateTodo(
                            todo.copy(
                                priority = priority,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * 批量设置提醒时间
     *
     * @param reminderTime 提醒时间戳（null 表示清除提醒）
     * @param repeatType 重复类型（0=不重复，1=每天，2=每周，3=每月，4=周一至周五，5=每年）
     *
     * 注意：ReminderPickerBottomSheet 的 `calendarEnabled` 参数仅是 UI 内部状态
     * （用于切换农历显示），不持久化到 TodoItem 字段。本方法仅更新 reminderTime
     * 与 repeatType。Alarm 调度由 TodoRepository.updateTodo 内部统一处理。
     */
    fun batchUpdateReminder(reminderTime: Long?, repeatType: Int) {
        val selectedIds = _selectedTodoIds.value
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            selectedIds.forEach { id ->
                todoRepository.getTodoById(id)?.let { todo ->
                    todoRepository.updateTodo(
                        todo.copy(
                            reminderTime = reminderTime,
                            repeatType = repeatType,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    /**
     * 批量复制选中的待办
     *
     * 每条新待办使用 Room 自增 id（id=0），createdAt/updatedAt 重置为当前时间，
     * 状态重置为未完成（status=0），completedAt 清空。
     *
     * **方案 B 简化（移除进度条 UI）**：
     * - 删除所有进度条 UI 反馈（_duplicateProgress / dismissDuplicateProgress / cancelDuplicate）
     * - 删除"已创建 N 个副本" Snackbar（_pendingBatchDuplicateCount）
     * - 文件复制改为顺序 for 循环（无需原子计数器）
     * - **保留**：失败 Snackbar（_pendingBatchDuplicateFailure，失败时弹 ⚠️ 提示）
     * - **保留**：闪退修复（顶层 try/catch + CancellationException 透传）
     * - **保留**：reminderTime / repeatType（不重置）
     * - **保留**：scheduleAlarm = true（副本也调度闹钟，与原 todo 一起触发；与之前方案 A 相反）
     * - **保留**：后台深复制（不阻塞 UI）
     *
     * @see FileCopyManager 附件复制工具
     */
    fun batchDuplicate() {
        val selectedIds = _selectedTodoIds.value
        if (selectedIds.isEmpty()) return
        val currentTime = System.currentTimeMillis()

        viewModelScope.launch {
            // ★★★ 关键修复：顶层 try/catch 防止 viewModelScope 因意外异常崩溃
            var hasFailure = false
            try {
                // newSubTaskMaps 用于跨协程回传：originalSubTask.id -> newSubTask.id
                val newSubTaskMaps = mutableMapOf<Long, Long>()  // oldSubTaskId -> newSubTaskId
                // (originalId, newId) 用于后续文件复制
                val duplicatePairs = mutableListOf<Pair<Long, Long>>()

                // ========== 阶段 1：主表 + SubTask 数据库复制（同步） ==========
                selectedIds.forEach { id ->
                    val todo = todoRepository.getTodoById(id) ?: return@forEach
                    val newId = todoRepository.insertTodo(
                        todo.copy(
                            id = 0,           // Room 自增
                            status = 0,        // 复制为未完成
                            completedAt = null,
                            createdAt = currentTime,
                            updatedAt = currentTime
                            // ★ reminderTime/repeatType 保留
                            // ★ scheduleAlarm 参数已删除（TodoRepository.insertTodo 默认行为：reminderTime != null 即调度）
                        )
                    )

                    // 复制子任务：原 addSubTasks 内部用 insertAll（Room 自增 id）
                    val subTasks = SubTaskManager.getSubTasks(context, id)
                    if (subTasks.isNotEmpty()) {
                        SubTaskManager.addSubTasks(context, newId, subTasks)
                        val newSubTasks = SubTaskManager.getSubTasks(context, newId)
                        for (original in subTasks) {
                            val matched = newSubTasks.firstOrNull { it.order == original.order }
                            if (matched != null) {
                                newSubTaskMaps[original.id] = matched.id
                            }
                        }
                    }

                    duplicatePairs.add(id to newId)
                }

                // 刷新子任务进度 Map，让新副本的"(0/1)"和"下箭头"立即可见
                if (newSubTaskMaps.isNotEmpty()) {
                    val updatedProgressMap = _subTaskProgressMap.value.toMutableMap()
                    val updatedSubTasksMap = _subTasksMap.value.toMutableMap()
                    duplicatePairs.forEach { (_, newId) ->
                        val progress = SubTaskManager.getProgressText(context, newId)
                        if (progress != null) {
                            updatedProgressMap[newId] = progress
                        }
                        val subTasks = SubTaskManager.getSubTasks(context, newId)
                        updatedSubTasksMap[newId] = subTasks
                    }
                    _subTaskProgressMap.value = updatedProgressMap
                    _subTasksMap.value = updatedSubTasksMap
                }

                // 退出批量模式（数据库操作已结束）
                exitBatchMode()

                // ========== 阶段 2：文件复制（后台异步，顺序 for 循环） ==========
                // 方案 B：无需精确进度，改为简单 for 循环
                // 每个 todo 的复制独立 try/catch，单 todo 失败不影响其他 todo
                for ((originalId, newId) in duplicatePairs) {
                    try {
                        // 静默收集（不更新 UI 进度）
                        fileCopyManager.copyAllAttachments(originalId, newId).collect { /* 静默 */ }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // 透传取消异常
                        throw e
                    } catch (e: Exception) {
                        // 单 todo 文件复制失败不影响其他 todo，仅记录
                        android.util.Log.e(
                            "HomeViewModel",
                            "批量复制附件失败: originalId=$originalId, newId=$newId",
                            e
                        )
                        hasFailure = true
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程取消时透传异常，不吞掉
                throw e
            } catch (e: Exception) {
                // ★★★ 顶层异常捕获：避免 viewModelScope 因意外异常崩溃闪退
                android.util.Log.e("HomeViewModel", "批量复制失败", e)
                hasFailure = true
            } finally {
                // 失败时弹 Snackbar（成功时不弹任何 UI 反馈）
                if (hasFailure) {
                    _pendingBatchDuplicateFailure.value = true
                }
            }
        }
    }

    /**
     * 批量移动选中的待办到指定分类
     * @param categoryId 目标分类 ID
     */
    fun batchMove(categoryId: Long) {
        val selectedIds = _selectedTodoIds.value
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            selectedIds.forEach { id ->
                val todo = todoRepository.getTodoById(id)
                if (todo != null) {
                    val updatedTodo = todo.copy(
                        categoryId = categoryId,
                        updatedAt = System.currentTimeMillis()
                    )
                    todoRepository.updateTodo(updatedTodo)
                }
            }
            exitBatchMode()
        }
    }

    /**
     * ViewModel 销毁时清理资源
     */
    override fun onCleared() {
        super.onCleared()
        stopIdleDetection()
        /** 取消所有倒计时任务，防止内存泄漏 */
        deleteTimerJob?.cancel()
        completeTimerJob?.cancel()
    }

    // ========== 辅助方法 ==========

    /**
     * 将 TodoItem 对象转换为 JSON 字符串
     * 用于操作日志的数据快照
     *
     * @param todo 待办对象
     * @return JSON 格式的字符串
     */
    private fun todoToJson(todo: TodoItem): String {
        return buildString {
            append("{")
            append("\"id\":${todo.id},")
            append("\"title\":\"${todo.title.replace("\"", "\\\"")}\",")
            append("\"content\":\"${(todo.content ?: "").replace("\"", "\\\"")}\",")
            append("\"categoryId\":${todo.categoryId},")
            append("\"priority\":${todo.priority},")
            append("\"status\":${todo.status},")
            append("\"repeatType\":${todo.repeatType},")
            append("\"createdAt\":${todo.createdAt},")
            append("\"updatedAt\":${todo.updatedAt}")
            if (todo.completedAt != null) {
                append(",\"completedAt\":${todo.completedAt}")
            }
            append("}")
        }
    }

    /**
     * 记录操作日志到数据库
     *
     * @param operationType 操作类型
     * @param targetId 目标待办 ID
     * @param batchIdsJson 批量操作的 ID 列表（可选）
     * @param snapshotJson 数据快照 JSON
     */
    private suspend fun recordOperationLog(
        operationType: String,
        targetId: Long,
        batchIdsJson: String? = null,
        snapshotJson: String
    ) {
        try {
            val log = OperationLogEntity(
                operationType = operationType,
                targetId = targetId,
                batchIdsJson = batchIdsJson,
                snapshotJson = snapshotJson
            )
            operationLogRepository.insertLog(log)
        } catch (e: Exception) {
            /** 日志记录失败不应影响主流程，仅打印日志 */
            android.util.Log.e("HomeViewModel", "Failed to record operation log", e)
        }
    }

    // ==================== 测试辅助方法（仅用于单元测试） ====================

    /**
     * 测试专用：直接设置 _todos 流的值
     *
     * 用于绕过 observeAllSorted() 的真实 DB 订阅，
     * 在单元测试中直接注入测试数据。
     */
    @androidx.annotation.VisibleForTesting
    fun refreshTodosForTest(todos: List<TodoItem>) {
        _todos.value = todos
    }

    /**
     * 测试专用：直接设置 _sortType
     */
    @androidx.annotation.VisibleForTesting
    fun setSortTypeForTest(sortType: String) {
        _sortType.value = sortType
    }
}
