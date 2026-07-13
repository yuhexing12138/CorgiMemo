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
import com.corgimemo.app.animation.HapticFeedbackController
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
import com.corgimemo.app.data.repository.MoodHistoryRepository
import com.corgimemo.app.data.repository.RepeatTaskManager
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.data.local.db.OperationLogEntity
import com.corgimemo.app.data.repository.OperationLogRepository
import com.corgimemo.app.data.repository.TaskDailyStatsRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.ui.components.TodoZone
import com.corgimemo.app.ui.components.ZoneDragResult
import com.corgimemo.app.util.FileCopyManager
import com.corgimemo.app.animation.BehaviorType
import com.corgimemo.app.animation.CorgiBehaviorManager
import com.corgimemo.app.animation.DynamicGreetingManager
import com.corgimemo.app.animation.GreetingContext
import com.corgimemo.app.animation.TimeSlot
import com.corgimemo.app.data.weather.WeatherManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    private val achievementChecker: AchievementChecker,
    private val achievementRepository: AchievementRepository,
    private val corgiPreferences: CorgiPreferences,
    private val moodHistoryRepository: MoodHistoryRepository,
    private val operationLogRepository: OperationLogRepository,
    private val taskDailyStatsRepository: TaskDailyStatsRepository,
    private val fileCopyManager: FileCopyManager,
    private val hapticFeedbackController: HapticFeedbackController,
    @param:ApplicationContext private val context: Context
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

    /** V2.10: "置顶"区域是否展开(从持久化加载,默认展开) */
    private val _showPinned = MutableStateFlow(true)
    val showPinned: StateFlow<Boolean> = _showPinned.asStateFlow()

    /** V2.11: "待完成"区域是否展开(从持久化加载,默认展开) */
    private val _showPending = MutableStateFlow(true)
    val showPending: StateFlow<Boolean> = _showPending.asStateFlow()

    /** 待办卡片简化显示（隐藏详情） */
    private val _hideDetails = MutableStateFlow(false)
    val hideDetails: StateFlow<Boolean> = _hideDetails.asStateFlow()

    /** 请求滚动到指定待办（消费后自动重置为 null） */
    private val _scrollToTodoId = MutableStateFlow<Long?>(null)
    val scrollToTodoId: StateFlow<Long?> = _scrollToTodoId.asStateFlow()

    /** 隐藏所有已完成项 */
    private val _hideCompletedItems = MutableStateFlow(false)
    val hideCompletedItems: StateFlow<Boolean> = _hideCompletedItems.asStateFlow()

    /** 三点功能菜单展开状态 */
    private val _menuExpanded = MutableStateFlow(false)
    val menuExpanded: StateFlow<Boolean> = _menuExpanded.asStateFlow()

    /** 排序弹窗显示状态（提升至 ViewModel，供 MainScreen.dropdownContent 触发） */
    private val _showSortSheet = MutableStateFlow(false)
    val showSortSheet: StateFlow<Boolean> = _showSortSheet.asStateFlow()

    // ========== 4 个独立区域 StateFlow（按 TodoZone 拆分） ==========
    // 新 flow 按 zone 分段（PINNED_PENDING=0-9999, PENDING=10000-19999,
    // PINNED_COMPLETED=20000-29999, COMPLETED=30000-39999）纯粹按 sortOrder 排序。

    /** PINNED_PENDING 区：置顶待完成（isPinned=true, status=0） */
    val pinnedPendingTodos: StateFlow<List<TodoItem>> = _todos.map { todos ->
        todos.filter { it.isPinned && it.status == 0 }
            .sortedBy { it.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * PENDING 区：普通待完成（isPinned=false, status=0）
     *
     * 按 zone 分段：sortOrder 范围 10000..19999。
     */
    val pendingTodos: StateFlow<List<TodoItem>> = _todos.map { todos ->
        todos.filter { !it.isPinned && it.status == 0 }
            .sortedBy { it.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** PINNED_COMPLETED 区：置顶已完成（含 30 天过滤） */
    val pinnedCompletedTodos: StateFlow<List<TodoItem>> = _todos.map { todos ->
        val thirtyDaysAgo = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
        todos.filter {
            it.isPinned && it.status == 1 &&
                it.completedAt != null && it.completedAt >= thirtyDaysAgo
        }.sortedBy { it.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** COMPLETED 区：普通已完成（含 30 天过滤） */
    val completedTodos: StateFlow<List<TodoItem>> = _todos.map { todos ->
        val thirtyDaysAgo = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
        todos.filter {
            !it.isPinned && it.status == 1 &&
                it.completedAt != null && it.completedAt >= thirtyDaysAgo
        }.sortedBy { it.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 已完成待办总数（用于分隔按钮显示） */
    val completedCount: StateFlow<Int> = _todos.map { todos ->
        todos.count { it.status == 1 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** V2.10: 置顶待办总数(仅未完成,用于触发"置顶(N)"按钮显示) */
    val pinnedCount: StateFlow<Int> = _todos.map { todos ->
        todos.count { it.isPinned && it.status == 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * 待完成待办总数（仅非置顶）
     *
     * 新逻辑：pinnedCount >= 1 时显示 PinnedDivider，PendingDivider 始终只代表非置顶待完成。
     * - pinnedCount >= 1：PendingDivider 在置顶区后，count = 非置顶待完成数
     * - pinnedCount == 0：无 PinnedDivider，PendingDivider 代表所有待完成（此时都是非置顶）
     *
     * 两种情况 count 都是 "非置顶待完成数"，逻辑统一。
     */
    val pendingCount: StateFlow<Int> = _todos.map { todos ->
        todos.count { !it.isPinned && it.status == 0 }
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

    /**
     * 当前可见待办列表（基于 4 个 zone StateFlow 合并，应用搜索/分类过滤）
     *
     * 合并顺序：PINNED_PENDING → PENDING → PINNED_COMPLETED → COMPLETED
     * - hideCompletedItems=true：仅返回 pending 区（含置顶）
     * - showCompleted=false：仅返回 pending 区（含置顶）
     * - 否则：返回 4 个 zone 合并列表
     */
    val filteredTodos: StateFlow<List<TodoItem>> = run {
        val baseFlow = kotlinx.coroutines.flow.combine(
            pinnedPendingTodos,
            pendingTodos,
            pinnedCompletedTodos,
            completedTodos
        ) { pinnedPending, pending, pinnedCompleted, completed ->
            pinnedPending + pending + pinnedCompleted + completed
        }
        kotlinx.coroutines.flow.combine(
            baseFlow, _searchQuery, _selectedCategoryId, _showCompleted, _hideCompletedItems
        ) { baseList, query, categoryId, showCompleted, hideCompletedItems ->
            // 先按展开状态过滤
            var result = if (hideCompletedItems || !showCompleted) {
                baseList.filter { it.status == 0 }
            } else {
                baseList
            }

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
                    (todo.contentFormat.let { format ->
                        com.corgimemo.app.util.MarkdownParser.stripMarkdown(format)
                            .contains(query, ignoreCase = true)
                    })
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
     * 触觉反馈
     *
     * 通过注入的 [HapticFeedbackController] 调用，提升可测试性
     * （测试中可注入 stub 实现避免真实 Vibrator 调用）。
     */

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

        viewModelScope.launch {
            corgiPreferences.showCompleted.collect { _showCompleted.value = it }
        }
        viewModelScope.launch {
            corgiPreferences.showPinned.collect { _showPinned.value = it }
        }
        viewModelScope.launch {
            corgiPreferences.hideDetails.collect { _hideDetails.value = it }
        }
        viewModelScope.launch {
            corgiPreferences.hideCompletedItems.collect { _hideCompletedItems.value = it }
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


    // ========== reorderOnDragResult（基于 ZoneDragResult 的拖拽持久化） ==========

    /**
     * 应用拖拽结果到数据层（新版，基于 [ZoneDragResult]）
     *
     * 设计要点：
     * 1. 区域 List 单一来源：4 个独立 StateFlow（[pinnedPendingTodos] /
     *    [pendingTodos] / [pinnedCompletedTodos] / [completedTodos]），
     *    不再依赖 displayItems 全局索引，避免 divider 位置变更带来的偏差。
     * 2. zone 内排序按 sortOrder 分段（PINNED_PENDING=0-9999, PENDING=10000-19999,
     *    PINNED_COMPLETED=20000-29999, COMPLETED=30000-39999）。
     * 3. 单次 [TodoRepository.updateTodos] 批量持久化，避免 N 次 Flow 推送。
     * 4. completedAt 副作用：跨入完成区设为 now，跨出完成区置 null，
     *    直接合并到 finalItem 中（不依赖 repository.updateCompletedAt）。
     * 5. 跨入完成区时触发 [handleTaskCompleted]（柯基庆祝/成就/经验值）。
     *
     * 设计特点：
     * - 不需要 dividerIndex / pendingStartIndex / midPendingDividerIndex 等 displayItems 索引
     * - 由调用方传入 [draggedTodo]（DisplayItem 是 HomeScreen 的 private sealed interface，
     *   这里用 TodoItem 直接接收，避免反射或类型提升）
     *
     * @param draggedItemId 被拖项 ID（用于从原区域 List 移除）
     * @param draggedTodo 被拖项原始数据（用于 copy 出 finalItem）
     * @param dragResult [DragZoneStateMachine.endDrag] 输出的最终状态
     * @param targetZoneRelativeIndex 目标 zone 列表内的相对索引（0..size）
     */
    fun reorderOnDragResult(
        draggedItemId: Long,
        draggedTodo: TodoItem,
        dragResult: ZoneDragResult,
        targetZoneRelativeIndex: Int
    ) {
        viewModelScope.launch {
            val originalZone = dragResult.originalZone
            val targetZone = dragResult.currentZone

            // ① 从原区域 List 移除被拖项
            val originalList = getListForZone(originalZone).toMutableList()
            originalList.removeAll { it.id == draggedItemId }

            // ② 应用 ZoneDragResult 的 finalIsPinned / finalStatus + completedAt 副作用
            val now = System.currentTimeMillis()
            val fromCompleted = originalZone == TodoZone.PINNED_COMPLETED ||
                originalZone == TodoZone.COMPLETED
            val toCompleted = targetZone == TodoZone.PINNED_COMPLETED ||
                targetZone == TodoZone.COMPLETED
            val newCompletedAt: Long? = when {
                !fromCompleted && toCompleted -> now
                fromCompleted && !toCompleted -> null
                else -> draggedTodo.completedAt
            }
            val finalItem = draggedTodo.copy(
                isPinned = dragResult.finalIsPinned,
                status = dragResult.finalStatus,
                completedAt = newCompletedAt,
                updatedAt = now
            )

            // ③ 插入到目标区域 List 的相对位置
            val targetList = getListForZone(targetZone).toMutableList()
            // 防御：若被拖项原本就在 target zone（同区拖拽），先移除避免重复
            targetList.removeAll { it.id == draggedItemId }
            val insertIdx = targetZoneRelativeIndex.coerceIn(0, targetList.size)
            targetList.add(insertIdx, finalItem)

            // ④ 重新分配 sortOrder（仅受影响区域，按 zone 分段）
            reassignSortOrder(targetList, targetZone)
            if (originalZone != targetZone) {
                reassignSortOrder(originalList, originalZone)
            }

            // ⑤ 单次批量持久化
            val allToUpdate = if (originalZone != targetZone) {
                targetList + originalList
            } else {
                targetList
            }
            todoRepository.updateTodos(allToUpdate)

            // ⑥ 跨区副作用：完成↔未完成时触发柯基庆祝/成就/经验值
            if (!fromCompleted && toCompleted) {
                handleTaskCompleted(finalItem)
            }
        }
    }

    /**
     * 获取指定 zone 对应的当前 List 快照
     *
     * 注：使用 value 取 StateFlow 当前值（受 WhileSubscribed 订阅影响，
     * 调用方应确保 UI 层有活跃订阅）。
     */
    private fun getListForZone(zone: TodoZone): List<TodoItem> {
        return when (zone) {
            TodoZone.PINNED_PENDING -> pinnedPendingTodos.value
            TodoZone.PENDING -> pendingTodos.value
            TodoZone.PINNED_COMPLETED -> pinnedCompletedTodos.value
            TodoZone.COMPLETED -> completedTodos.value
        }
    }

    /**
     * 按 zone 分段重新分配 sortOrder
     *
     * 分段规则（与 Migration 31→32 一致）：
     * - PINNED_PENDING: 0..9999
     * - PENDING: 10000..19999
     * - PINNED_COMPLETED: 20000..29999
     * - COMPLETED: 30000..39999
     *
     * 仅当 sortOrder 实际变化时才创建新对象，减少不必要的 copy。
     */
    private fun reassignSortOrder(list: MutableList<TodoItem>, zone: TodoZone) {
        val baseSortOrder = when (zone) {
            TodoZone.PINNED_PENDING -> 0
            TodoZone.PENDING -> 10000
            TodoZone.PINNED_COMPLETED -> 20000
            TodoZone.COMPLETED -> 30000
        }
        list.forEachIndexed { index, item ->
            val expected = baseSortOrder + index
            if (item.sortOrder != expected) {
                list[index] = item.copy(sortOrder = expected)
            }
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
     * V2.10: 切换"置顶"区域展开/折叠状态
     */
    fun toggleShowPinned() {
        val newVal = !_showPinned.value
        _showPinned.value = newVal
        viewModelScope.launch {
            corgiPreferences.setShowPinned(newVal)
        }
    }

    /**
     * V2.11: 切换"待完成"区域展开/折叠状态
     */
    fun toggleShowPending() {
        val newVal = !_showPending.value
        _showPending.value = newVal
        viewModelScope.launch {
            corgiPreferences.setShowPending(newVal)
        }
    }

    /** 切换待办卡片简化显示 */
    fun toggleHideDetails() {
        val newVal = !_hideDetails.value
        _hideDetails.value = newVal
        viewModelScope.launch {
            corgiPreferences.setHideDetails(newVal)
        }
    }

    /** 切换隐藏所有已完成项 */
    fun toggleHideCompletedItems() {
        val newVal = !_hideCompletedItems.value
        _hideCompletedItems.value = newVal
        viewModelScope.launch {
            corgiPreferences.setHideCompletedItems(newVal)
        }
    }

    /** 设置三点功能菜单展开状态 */
    fun setMenuExpanded(expanded: Boolean) {
        _menuExpanded.value = expanded
    }

    /** 设置排序弹窗显示状态 */
    fun setShowSortSheet(show: Boolean) {
        _showSortSheet.value = show
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
                hapticFeedbackController.perform(
                    type = InteractionType.ACHIEVEMENT_UNLOCK,
                    enabled = hapticEnabled.value
                )
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

            // 自动收起：所有子任务都完成时收起子待办列表
            //
            // 触发条件：result.parentTodoCompleted == true
            //   - 表示本次切换导致父任务被自动完成
            //   - 隐含语义：所有子任务都已完成
            //   - 与用户需求"仅全部完成时收起"完全吻合
            //
            // 实现机制：
            //   - 调用 toggleExpand(todoId) 将 expandedTodos 中的 todoId 移除
            //   - TodoListItem 的 isExpanded 变为 false
            //   - AnimatedVisibility 触发 exit 动画（shrinkVertically + fadeOut）
            //   - 子待办列表平滑收起
            //
            // 顺序设计：先收起再触发父任务完成逻辑，
            //   这样用户在看到完成庆祝动画前，先看到子待办列表平滑收起，
            //   视觉体验更连贯（"先清场，再庆祝"）。
            if (result.parentTodoCompleted) {
                result.updatedSubTask?.let { subTask ->
                    toggleExpand(subTask.todoId)
                }
            }

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
        hapticFeedbackController.perform(
            type = InteractionType.TASK_COMPLETE,
            enabled = hapticEnabled.value
        )

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
     *
     * 注意：本协程运行在 [Dispatchers.Default] 上而非默认的 Main dispatcher。
     * 原因：
     * 1. 避免在单元测试中因 [Dispatchers.setMain] 注入 TestDispatcher 后，
     *    runTest 会为完成 delay(1000) 而自动推进虚拟时间，但 while(isActive)
     *    让循环无限继续，导致 runTest 无法正常结束。
     * 2. 让空闲检测在后台线程运行，避免阻塞 UI 线程。
     *
     * 取消机制：调用 [stopIdleDetection] 或 viewModel.onCleared() 会通过
     * Job.cancel() 取消本协程，cancellation 通过 Job 层级传播，与 dispatcher 无关。
     * triggerYawn() 内部仍用 viewModelScope.launch（Main dispatcher）更新 UI 状态，
     * 确保线程安全。
     */
    private fun startIdleDetection() {
        idleCheckJob = viewModelScope.launch(Dispatchers.Default) {
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
     * 测试中也可手动调用以避免 while(isActive) 无限循环阻塞 runTest
     */
    @androidx.annotation.VisibleForTesting
    fun stopIdleDetection() {
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
            hapticFeedbackController.perform(
                type = InteractionType.TASK_COMPLETE,
                enabled = hapticEnabled.value
            )

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
     * 批量更新选中待办的提醒和截止日期
     *
     * 合并提醒时间和截止日期为单次循环，减少数据库往返。
     * 在批量设置提醒弹窗中，用户可以同时设置提醒和截止日期。
     * - reminderTime 为 null 时清除提醒，dueDate 为 null 时清除截止日期
     *
     * @param reminderTime 提醒时间戳，null 清除
     * @param repeatType 重复类型
     * @param dueDate 截止日期时间戳，null 清除
     */
    fun batchUpdateReminderAndDueDate(reminderTime: Long?, repeatType: Int, dueDate: Long?) {
        val selectedIds = _selectedTodoIds.value
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            selectedIds.forEach { id ->
                todoRepository.getTodoById(id)?.let { todo ->
                    todoRepository.updateTodo(
                        todo.copy(
                            reminderTime = reminderTime,
                            repeatType = repeatType,
                            dueDate = dueDate,
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

    // ========== 日历相关数据方法 ==========

    /**
     * 获取指定月份每天有待办的条数（用于日历圆点显示）
     *
     * 基于 reminderTime 字段过滤，仅统计 reminderTime 不为 null 且落在指定年月的待办。
     * 优先使用提醒时间，因为日历弹窗的语义是"那天提醒我的待办"；
     * 若 reminderTime 为 null 则回退到 startDate。
     *
     * @param year 年
     * @param month 月（1-12）
     * @return 日期 -> 条数 的映射
     */
    fun getCalendarTodoCount(year: Int, month: Int): Map<Int, Int> {
        return _todos.value
            .filter { todo ->
                val timestamp = todo.reminderTime ?: todo.startDate
                timestamp != null && run {
                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) + 1 == month
                }
            }
            .groupBy { todo ->
                val timestamp = todo.reminderTime ?: todo.startDate
                val cal = Calendar.getInstance().apply { timeInMillis = timestamp!! }
                cal.get(Calendar.DAY_OF_MONTH)
            }
            .mapValues { it.value.size }
    }

    /**
     * 获取指定日期的所有待办（基于 reminderTime，含已完成）
     *
     * 优先使用提醒时间过滤，若 reminderTime 为 null 则回退到 startDate。
     * 排序规则：未完成在前，已完成在后；同状态内按提醒时间 ASC。
     *
     * @param year 年
     * @param month 月（1-12）
     * @param day 日
     * @return 当天的待办列表
     */
    fun getTodosByDate(year: Int, month: Int, day: Int): List<TodoItem> {
        return _todos.value
            .filter { todo ->
                val timestamp = todo.reminderTime ?: todo.startDate
                timestamp != null && run {
                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    cal.get(Calendar.YEAR) == year &&
                    cal.get(Calendar.MONTH) + 1 == month &&
                    cal.get(Calendar.DAY_OF_MONTH) == day
                }
            }
            .sortedWith(
                compareByDescending<TodoItem> { it.status == 0 }
                    .thenBy(nullsLast()) { it.reminderTime ?: it.startDate }
            )
    }

    /**
     * 请求滚动到指定待办
     *
     * 由 MainScreen 中 TodoCalendarDialog 的 onTodoClick 回调触发，
     * HomeScreen 中 LaunchedEffect 监听 scrollToTodoId 变化执行滚动。
     *
     * @param id 目标待办的 ID
     */
    fun requestScrollToTodo(id: Long) {
        _scrollToTodoId.value = id
    }

    /**
     * 重置滚动目标（滚动完成后调用）
     */
    fun clearScrollToTodo() {
        _scrollToTodoId.value = null
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
