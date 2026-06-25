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
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ========== 待办列表相关 ==========

    /** 数据是否已初始化完成（用于避免冷启动时从空列表闪烁到有数据） */
    private val _isDataInitialized = MutableStateFlow(false)
    val isDataInitialized: StateFlow<Boolean> = _isDataInitialized.asStateFlow()

    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    private val _filterStatus = MutableStateFlow(FilterStatus.ALL)
    val filterStatus: StateFlow<FilterStatus> = _filterStatus.asStateFlow()

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

    /** 过滤后的待办列表（组合 searchQuery + filterStatus + selectedCategoryId + sortType） */
    val filteredTodos: StateFlow<List<TodoItem>> =
        kotlinx.coroutines.flow.combine(
            _todos, _searchQuery, _filterStatus, _selectedCategoryId, _sortType
        ) { todos, query, filter, categoryId, sortType ->
            var result = todos

            // 应用分类过滤
            if (categoryId != null && categoryId > 0) {
                result = result.filter { it.categoryId == categoryId }
            } else if (categoryId != null && categoryId == 0L) {
                val validCategoryIds = categories.value.map { it.id }.toSet()
                result = result.filter { it.categoryId !in validCategoryIds }
            }

            // 应用搜索关键词过滤（支持纯文本 + 格式化内容搜索）
            if (query.isNotBlank()) {
                result = result.filter { todo ->
                    todo.title.contains(query, ignoreCase = true) ||
                    (todo.content?.contains(query, ignoreCase = true) ?: false) ||
                    /**
                     * 搜索格式化内容（contentFormat 字段）
                     *
                     * 使用 stripMarkdown() 剥离 Markdown 标记后进行文本匹配，
                     * 确保用户在编辑器中输入的 **粗体**、~~删除线~~ 等格式化内容
                     * 也能被首页搜索功能检索到。
                     */
                    (todo.contentFormat?.let { format ->
                        com.corgimemo.app.util.MarkdownParser.stripMarkdown(format)
                            .contains(query, ignoreCase = true)
                    } ?: false)
                }
            }

            // 应用状态过滤
            result = when (filter) {
                FilterStatus.ALL -> result
                FilterStatus.PENDING -> result.filter { it.status == 0 }
                FilterStatus.COMPLETED -> result.filter { it.status == 1 }
            }

            /** 应用排序 */
            result = when (sortType) {
                "updated_desc" -> result.sortedByDescending { it.updatedAt }
                "updated_asc" -> result.sortedBy { it.updatedAt }
                "created_desc" -> result.sortedByDescending { it.createdAt }
                "created_asc" -> result.sortedBy { it.createdAt }
                else -> result.sortedByDescending { it.updatedAt }
            }

            result
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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

    /** 完成操作倒计时任务（可取消）*/
    private var completeTimerJob: Job? = null

    /** 完成倒计时时长（3秒）*/
    private val COMPLETE_UNDO_DELAY_MS = 3000L

    // ========== 批量操作撤销相关 ==========

    /** 批量删除的待办列表临时存储（用于撤销）*/
    private val _pendingBatchDeletes = MutableStateFlow<List<TodoItem>?>(null)
    val pendingBatchDeletes: StateFlow<List<TodoItem>?> = _pendingBatchDeletes.asStateFlow()

    // ========== 拖拽排序撤销相关（多级撤销栈）==========

    /**
     * 拖拽排序撤销数据
     *
     * 记录排序操作前的位置映射，用于支持一键恢复原始顺序。
     *
     * **序列化**: 使用 kotlinx-serialization-json 序列化为 JSON，
     * 通过 DataStore 持久化到本地存储，应用重启后可恢复。
     *
     * @property fromIndex 拖拽起始索引
     * @property toIndex 拖拽目标索引
     * @property oldPositions 排序前的 id→position 映射（用于恢复）
     */
    @kotlinx.serialization.Serializable
    data class ReorderUndoData(
        val fromIndex: Int,
        val toIndex: Int,
        val oldPositions: Map<Long, Int>
    )

    /**
     * 多级撤销历史栈
     *
     * 使用 ArrayDeque 实现栈结构：
     * - 新操作通过 addFirst() 推入栈顶
     * - 撤销操作通过 removeFirst() 弹出栈顶
     * - 超过 MAX_UNDO_STACK_SIZE 时从栈底移除最旧记录
     *
     * 支持最多 10 次连续拖拽操作的撤销，
     * 覆盖绝大多数用户的误操作恢复需求。
     */
    private val _reorderUndoStack = MutableStateFlow(ArrayDeque<ReorderUndoData>())
    /** 撤销栈的公开只读接口（供 HomeScreen 监听）*/
    val reorderUndoStack: StateFlow<ArrayDeque<ReorderUndoData>> = _reorderUndoStack.asStateFlow()

    /** 撤销栈最大深度（防止内存无限增长）*/
    private val MAX_UNDO_STACK_SIZE = 10

    /** 排序撤销倒计时任务（可取消）*/
    private var reorderUndoTimerJob: Job? = null

    /** 排序倒计时时长（5秒，与删除撤销保持一致）*/
    private val UNDO_REORDER_DELAY_MS = 5000L

    /**
     * JSON 序列化器（用于撤销栈的 DataStore 持久化）
     *
     * 配置说明：
     * - ignoreUnknownKeys: 兼容未来字段扩展（旧版本可读取新版本数据）
     * - encodeDefaults: 不编码默认值，减小 JSON 体积
     */
    private val reorderUndoJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /**
     * ViewModel 初始化块
     *
     * 应用启动时从 ESP 恢复拖拽排序撤销栈，
     * 实现跨重启的撤销能力。
     *
     * **V2.8 变更**: ESP 已自动执行 AES-256-GCM 加解密，
     * 无需手动解密，直接读取明文 JSON 即可。
     */
    init {
        viewModelScope.launch {
            runCatching {
                val savedJson = corgiPreferences.getReorderUndoStack()
                if (!savedJson.isNullOrBlank()) {
                    val restoredStack = deserializeReorderUndoStack(savedJson)
                    if (restoredStack.isNotEmpty()) {
                        _reorderUndoStack.value = restoredStack
                        android.util.Log.d(
                            "HomeViewModel",
                            "已恢复拖拽撤销栈 (${restoredStack.size} 层，ESP 自动解密)"
                        )
                    }
                }
            }.onFailure { e ->
                android.util.Log.w(
                    "HomeViewModel",
                    "恢复拖拽撤销栈失败（可能为格式变更），已清空: ${e.message}"
                )
                corgiPreferences.clearReorderUndoStack()
            }
        }
    }

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
     * 设置过滤器状态
     */
    fun setFilterStatus(status: FilterStatus) {
        _filterStatus.value = status
        loadTodos()
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
     * 拖拽排序待办列表（支持撤销）
     *
     * 执行流程：
     * 1. 保存当前受影响项的位置映射（用于撤销）
     * 2. 计算新的位置编号
     * 3. 批量更新数据库
     * 4. 启动 5 秒撤销倒计时
     * 5. 重新加载列表
     *
     * **性能优化**:
     * - 仅重新编号受影响范围 [min(fromIndex, toIndex)..max(fromIndex, toIndex)] 内的项
     * - 使用 repository.batchUpdatePositions() 原生 SQL 单次执行
     * - 支持撤销操作（5 秒内可恢复原始顺序）
     *
     * **调用示例** (HomeScreen 中):
     * ```kotlin
     * ReorderableLazyColumn(
     *     items = viewModel.todos.collectAsState().value,
     *     onReorder = { from, to -> viewModel.reorderTodos(from, to) }
     * ) { ... }
     * ```
     *
     * @param fromIndex 拖拽项的原始索引位置
     * @param toIndex 拖拽项的目标索引位置
     */
    fun reorderTodos(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            /** 获取当前列表快照 */
            val currentList = _todos.value

            /** 边界检查：无效索引或空列表直接返回 */
            if (fromIndex == toIndex || currentList.isEmpty()) return@launch
            if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return@launch

            /** 构建新的位置映射：仅重编号受影响范围内的项 */
            val minIdx = minOf(fromIndex, toIndex)
            val maxIdx = maxOf(fromIndex, toIndex)

            /**
             * 保存旧位置映射（用于撤销）
             *
             * 在执行新排序之前，记录当前所有受影响项的 id→position 映射。
             * 这样用户点击"撤销"时可以恢复到排序前的状态。
             */
            val oldPositions = mutableMapOf<Long, Int>()
            for (i in minIdx..maxIdx) {
                oldPositions[currentList[i].id] = currentList[i].position
            }

            val positions = mutableMapOf<Long, Int>()
            for (i in minIdx..maxIdx) {
                positions[currentList[i].id] = i
            }

            /** 取消之前的撤销计时器（如果有）*/
            reorderUndoTimerJob?.cancel()

            /**
             * 推入撤销栈
             *
             * 创建新的 ArrayDeque 副本（StateFlow 不可变语义），
             * 将新操作数据 addFirst() 到栈顶，
             * 超过上限时从栈底 removeLast() 移除最旧记录。
             */
            val newUndoData = ReorderUndoData(
                fromIndex = fromIndex,
                toIndex = toIndex,
                oldPositions = oldPositions
            )
            val currentStack = ArrayDeque(_reorderUndoStack.value)
            currentStack.addFirst(newUndoData)

            /** 超出最大深度时移除最旧的记录*/
            while (currentStack.size > MAX_UNDO_STACK_SIZE) {
                currentStack.removeLast()
            }

            _reorderUndoStack.value = currentStack

            /** 持久化撤销栈到 DataStore（应用重启后可恢复）*/
            persistReorderUndoStack()

            /** 启动 5 秒撤销倒计时（仅栈非空时有效）*/
            reorderUndoTimerJob = launch {
                kotlinx.coroutines.delay(UNDO_REORDER_DELAY_MS)

                /**
                 * 倒计时结束：如果栈仍非空，清除最顶层的撤销数据
                 * （模拟自动确认：用户未在时限内点击撤销）
                 */
                val stack = ArrayDeque(_reorderUndoStack.value)
                if (stack.isNotEmpty()) {
                    stack.removeFirst()
                    _reorderUndoStack.value = stack
                    /** 定时器到期后同步持久化（栈可能已变空）*/
                    persistReorderUndoStack()
                }
                reorderUndoTimerJob = null
            }

            /** 批量更新到数据库（原生 SQL CASE WHEN，单次执行）*/
            todoRepository.batchUpdatePositions(positions)

            /** 重新加载列表以反映新顺序（确保 UI 与数据库同步）*/
            loadTodos()
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
     * 撤销拖拽排序（多级撤销栈版）
     *
     * 从撤销栈顶弹出最近一次排序操作，
     * 使用保存的 oldPositions 映射批量恢复原始位置值。
     *
     * **调用时机**: 用户在 Snackbar 中点击"撤销"按钮时触发
     *
     * **多级支持**: 连续点击撤销可依次恢复最多 10 次排序操作
     */

    // ========== 撤销栈序列化/持久化辅助方法 ==========

    /**
     * 将撤销栈序列化为 JSON 字符串
     *
     * 使用 kotlinx-serialization-json 将 ArrayDeque<ReorderUndoData>
     * 转换为 JSON 数组字符串，用于 DataStore 持久化。
     *
     * @param stack 当前撤销栈
     * @return JSON 字符串（如 "[{...},{...}]"）
     */
    private fun serializeReorderUndoStack(stack: ArrayDeque<ReorderUndoData>): String {
        return reorderUndoJson.encodeToString(stack.toList())
    }

    /**
     * 从 JSON 字符串反序列化为撤销栈
     *
     * 将 ESP 中保存的 JSON 还原为 ArrayDeque<ReorderUndoData>。
     *
     * @param json JSON 字符串
     * @return 反序列化后的撤销栈（空列表时返回空 ArrayDeque）
     */
    private fun deserializeReorderUndoStack(json: String): ArrayDeque<ReorderUndoData> {
        return try {
            ArrayDeque(reorderUndoJson.decodeFromString<List<ReorderUndoData>>(json))
        } catch (e: Exception) {
            /** 格式不兼容时返回空栈，由调用方决定是否清空 ESP 数据*/
            android.util.Log.w("HomeViewModel", "反序列化撤销栈失败: ${e.message}")
            ArrayDeque()
        }
    }

    /**
     * 持久化当前撤销栈到 ESP（自动 AES-256-GCM 加密）
     *
     * 处理流程：
     * 1. 序列化：ArrayDeque<ReorderUndoData> → JSON 字符串
     * 2. 存储：写入 ESP（ESP 自动执行 AES-256-GCM 加密）
     *
     * **V2.8 变更**: 移除手动 AES 加密层，
     * EncryptedSharedPreferences 在写入时自动加密所有数据。
     *
     * 此方法应在每次栈变更后调用：
     * - reorderTodos() 推入新记录后
     * - undoReorder() 弹出记录后
     * - 定时器到期自动移除记录后
     */
    private suspend fun persistReorderUndoStack() {
        val currentStack = _reorderUndoStack.value
        if (currentStack.isNotEmpty()) {
            val json = serializeReorderUndoStack(currentStack)
            corgiPreferences.saveReorderUndoStack(json) /** ESP 自动加密 */
        } else {
            corgiPreferences.clearReorderUndoStack()
        }
    }

    fun undoReorder() {
        viewModelScope.launch {
            /** 从栈顶弹出最近的撤销数据 */
            val currentStack = ArrayDeque(_reorderUndoStack.value)
            val reorderData = currentStack.removeFirst() ?: return@launch

            /** 1. 更新栈状态（移除已消费的撤销记录）*/
            _reorderUndoStack.value = currentStack

            /** 1.5 同步持久化撤销栈（用户撤销后栈可能已变空）*/
            persistReorderUndoStack()

            /** 2. 取消倒计时任务 */
            reorderUndoTimerJob?.cancel()
            reorderUndoTimerJob = null

            /** 3. 使用保存的旧位置映射恢复数据库中的位置值 */
            todoRepository.batchUpdatePositions(reorderData.oldPositions)

            /** 4. 重新加载列表以反映恢复后的顺序 */
            loadTodos()
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
     * 批量完成选中的待办
     */
    fun batchComplete() {
        val selectedIds = _selectedTodoIds.value
        if (selectedIds.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        viewModelScope.launch {
            selectedIds.forEach { id ->
                val todo = todoRepository.getTodoById(id)
                if (todo != null && todo.status == 0) {
                    val updatedTodo = todo.copy(
                        status = 1,
                        completedAt = currentTime,
                        updatedAt = currentTime
                    )
                    todoRepository.updateTodo(updatedTodo)
                    handleTaskCompleted(todo)
                }
            }
            exitBatchMode()
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
}
