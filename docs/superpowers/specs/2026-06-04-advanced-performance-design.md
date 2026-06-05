# CorgiMemo 高级性能优化设计文档

> **日期**：2026-06-04  
> **状态**：待审核  
> **版本**：v2.0（基于骨架屏+预加载 v1.0 的增强）  

---

## 1. 优化范围总览

| # | 优化项 | 优先级 | 复杂度 | 预计收益 |
|---|--------|--------|--------|---------|
| 1 | **Shimmer 动画效果** | P0 (立即) | 低 | 视觉体验显著提升 |
| 2 | **智能预加载策略** | P1 | 中 | 首帧时间缩短 30-50% |
| 3 | **Paging 3 集成** | P2 | 高 | 大数据量性能提升 10x |
| 4 | **离线缓存增强** | P3 | 中 | 离线访问速度提升 5x |

---

## 2. Phase 1: Shimmer 动画效果

### 2.1 目标
为骨架屏添加 shimmer 扫光动画效果，从静态灰色占位升级为动态渐变扫光。

### 2.2 当前状态
[Skeleton.kt](../../app/src/main/java/com/corgimemo/app/ui/components/Skeleton.kt) 已有 shimmer 基础实现：
```kotlin
fun Modifier.skeleton(
    shape: RoundedCornerShape = SkeletonDefaults.DefaultShape,
    shimmerEnabled: Boolean = false  // ← 默认关闭
): Modifier
```

### 2.3 实现方案

#### 方案 A: 全局开启 Shimmer（推荐）
在所有骨架屏组件中默认启用 shimmer，无需修改调用方代码。

**修改文件**：
- [Skeleton.kt](../../app/src/main/java/com/corgimemo/app/ui/components/Skeleton.kt) - 将 `shimmerEnabled` 默认值改为 `true`
- [TodoSkeleton.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/home/TodoSkeleton.kt) - 无需改动
- [InspirationSkeleton.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationSkeleton.kt) - 无需改动
- [SpecialDateSkeleton.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateSkeleton.kt) - 无需改动

#### 动画参数调优
```kotlin
// 推荐参数（已实现，仅需调整数值）
animationSpec = infiniteRepeatable(
    animation = tween(
        durationMillis = 1200,  // ✅ 保持：扫光周期
        easing = LinearEasing   // ✅ 保持：匀速运动
    ),
    repeatMode = RepeatMode.Restart  // ✅ 保持：循环播放
)
```

### 2.4 视觉效果对比

**当前（静态灰）**：
```
▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭
▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭
▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭
```

**优化后（Shimmer 动态扫光）**：
```
░░░░░▒▒▒▒▓▓▓▓█████▓▓▓▓▒▒▒▒░░░░  ← 光带从左到右扫描
▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭
▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭
```

### 2.5 性能影响
- **CPU 开销**：极低（仅 1 个无限循环动画）
- **内存开销**：无增加（复用现有 Brush 对象）
- **电池影响**：可忽略（动画在 compose 合成阶段执行）

---

## 3. Phase 2: 智能预加载策略

### 3.1 目标
根据用户历史行为预测常用页面，动态调整预加载优先级。

### 3.2 当前状态
[CorgiMemoApplication.kt](../../app/src/main/java/com/corgimemo/app/CorgiMemoApplication.kt) 采用固定顺序预加载：
```kotlin
todoRepository.getAllTodos().first()           // 1️⃣ 待办（固定第一）
inspirationRepository.getAllInspirations().first()  // 2️⃣ 灵感（固定第二）
specialDateRepository.allDates.first()          // 3️⃣ 日期（固定第三）
```

### 3.3 实现方案

#### 核心组件：UserBehaviorAnalyzer

```kotlin
/**
 * 用户行为分析器
 *
 * 统计用户页面访问频率，
 * 动态计算预加载优先级。
 */
class UserBehaviorAnalyzer(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "user_behavior_prefs"
        private const val KEY_VISIT_COUNT_HOME = "visit_count_home"
        private const val KEY_VISIT_COUNT_INSPIRATION = "visit_count_inspiration"
        private const val KEY_VISIT_COUNT_DATE = "visit_count_date"
        private const val SAMPLE_SIZE = 20  // 最近 20 次访问作为样本
    }

    /**
     * 记录一次页面访问
     */
    fun recordPageVisit(page: PageType)

    /**
     * 获取预加载优先级排序
     *
     * @return 按访问频率降序排列的页面列表
     */
    fun getPreloadPriority(): List<PageType>

    /**
     * 获取最常访问的页面
     */
    fun getMostVisitedPage(): PageType?
}
```

#### 数据结构
```kotlin
enum class PageType {
    HOME,           // 待办页
    INSPIRATION,    // 灵感页
    SPECIAL_DATE    // 日期页
}

data class PageVisitRecord(
    val page: PageType,
    val timestamp: Long,
    val sessionDuration: Long  // 可选：页面停留时长
)
```

#### 预加载策略算法
```
输入：最近 N 次页面访问记录
输出：预加载顺序 [Page1, Page2, Page3]

算法：
1. 统计每个页面的访问频率 frequency(page)
2. 计算加权分数 score(page) = frequency × 0.7 + recency × 0.3
3. 按 score 降序排列
4. 返回排序后的页面列表
```

#### 集成到 CorgiMemoApplication
```kotlin
override fun onCreate() {
    super.onCreate()
    
    // 初始化行为分析器
    userBehaviorAnalyzer = UserBehaviorAnalyzer(this)
    
    ProcessLifecycleOwner.get().lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // 获取智能预加载顺序
                val priority = userBehaviorAnalyzer.getPreloadPriority()
                preloadData(priority)  // ← 传入动态顺序
            }
        }
    )
}

private fun preloadData(priority: List<PageType>) {
    CoroutineScope(Dispatchers.IO).launch {
        priority.forEach { page ->
            when (page) {
                HOME -> todoRepository.getAllTodos().first()
                INSPIRATION -> inspirationRepository.getAllInspirations().first()
                SPECIAL_DATE -> specialDateRepository.allDates.first()
            }
        }
    }
}
```

### 3.4 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `app/.../analytics/UserBehaviorAnalyzer.kt` | **新建** | 用户行为分析核心类 |
| `app/.../CorgiMemoApplication.kt` | **修改** | 集成智能预加载逻辑 |
| `app/.../MainScreen.kt` | **修改** | 在页面切换时记录访问 |

### 3.5 冷启动优化
首次安装或清除数据后无历史记录 → 使用**默认优先级**：
```kotlin
val DEFAULT_PRIORITY = listOf(
    PageType.HOME,          // 大多数用户首选待办页
    PageType.INSPIRATION,  // 灵感功能使用频率较高
    PageType.SPECIAL_DATE  // 日期功能相对低频
)
```

---

## 4. Phase 3: Paging 3 集成

### 4.1 目标
对于大数据量场景（100+ 条待办/灵感/日期），使用 Paging 3 替代全量加载。

### 4.2 当前问题
当数据量增大时：
- **内存占用高**：一次性加载所有数据到内存
- **查询慢**：Room 全表扫描耗时增长
- **UI 卡顿**：LazyColumn 渲染大量 item 时掉帧

### 4.3 适用场景判断

| 页面 | 典型数据量 | 是否需要 Paging |
|------|-----------|----------------|
| 待办页 | 10-100 条 | 可选（>50 条时建议开启） |
| 灵感页 | 20-500 条 | **推荐**（内容较长） |
| 日期页 | 5-50 条 | 不需要（数据量小） |

### 4.4 架构设计

#### 4.4.1 分层架构
```
┌─────────────────────────────────────────────┐
│              UI Layer (Compose)              │
│  ┌───────────┬──────────┬────────────────┐  │
│  │ LazyColumn│ Pager    │ collectAsLazy  │  │
│  │ .items()  │ (Paging3)│ PagingItems() │  │
│  └───────────┴──────────┴────────────────┘  │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│            ViewModel Layer                   │
│  ┌──────────────────────────────────────┐   │
│  │ Pager<Int, TodoItem>                 │   │
│  │   .flow                              │   │
│  │   .cachedIn(viewModelScope)          │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│           Repository Layer                   │
│  ┌──────────────────────────────────────┐   │
│  │ PagingSource<Int, TodoItem>          │   │
│  │   .load(LoadParams)                  │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│             Data Layer (Room)                │
│  ┌──────────────────────────────────────┐   │
│  │ @Query("SELECT * FROM todos LIMIT    │   │
│  │   :limit OFFSET :offset")            │   │
│  │ suspend fun getTodosPaging(           │   │
│  │   limit: Int, offset: Int            │   │
│  │ ): List<TodoItem>                    │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

#### 4.4.2 核心 API 设计

**TodoPagingSource 示例**：
```kotlin
/**
 * 待办列表分页数据源
 *
 * 从 Room 数据库按页加载数据，
 * 支持按状态过滤和排序。
 */
class TodoPagingSource(
    private val todoDao: TodoDao,
    private val filterStatus: FilterStatus
) : PagingSource<Int, TodoItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TodoItem> {
        return try {
            val page = params.key ?: 0
            val limit = params.loadSize
            
            // 从 Room 加载当前页数据
            val todos = when (filterStatus) {
                FilterStatus.ALL -> todoDao.getTodosPaging(limit, page * limit)
                FilterStatus.PENDING -> todoDao.getPendingTodosPaging(limit, page * limit)
                FilterStatus.COMPLETED -> todoDao.getCompletedTodosPaging(limit, page * limit)
            }

            LoadResult.Page(
                data = todos,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (todos.size < limit) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, TodoItem>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}
```

**ViewModel 集成示例**：
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val todoRepository: TodoRepository
) : ViewModel() {

    /** 分页待办列表流 */
    @OptIn(ExperimentalPagingApi::class)
    val todoPager: Flow<PagingData<TodoItem>> = Pager(
        config = PagingConfig(
            pageSize = 20,           // 每页 20 条
            enablePlaceholders = true,
            maxSize = 100,           // 最大缓存 100 条
            prefetchDistance = 10    // 距底部 10 条时预加载下一页
        )
    ) {
        TodoPagingSource(todoRepository.todoDao, filterStatus.value)
    }.flow.cachedIn(viewModelScope)
}
```

**UI 层集成示例**：
```kotlin
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val todoItems by viewModel.todoPager.collectAsLazyPagingItems()

    LazyColumn {
        items(todoItems.itemCount) { index ->
            todoItems[index]?.let { todo ->
                TodoListItem(todo = todo, ...)
            }
        }

        // 加载中指示器
        item {
            if (todoItems.loadState.append is LoadState.Loading) {
                CircularProgressIndicator()
            }
        }
    }
}
```

### 4.5 Room DAO 变更

需要在以下 DAO 中新增分页查询方法：

| DAO | 新增方法 |
|-----|---------|
| [TodoDao](../../app/src/main/java/com/corgimemo/app/data/local/dao/TodoDao.kt) | `getTodosPaging(limit, offset)` |
| [InspirationDao](../../app/src/main/java/com/corgimemo/app/data/local/dao/InspirationDao.kt) | `getInspirationsPaging(limit, offset)` |

### 4.6 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `app/build.gradle.kts` | **修改** | 添加 `paging-runtime` 和 `paging-compose` 依赖 |
| `gradle/libs.versions.toml` | **修改** | 添加 paging 版本定义 |
| `app/.../data/local/dao/TodoDao.kt` | **修改** | 新增分页查询 SQL |
| `app/.../data/local/dao/InspirationDao.kt` | **修改** | 新增分页查询 SQL |
| `app/.../paging/TodoPagingSource.kt` | **新建** | 待办分页数据源 |
| `app/.../paging/InspirationPagingSource.kt` | **新建** | 灵感分页数据源 |
| `app/.../viewmodel/HomeViewModel.kt` | **修改** | 集成 Pager |
| `app/.../viewmodel/InspirationViewModel.kt` | **修改** | 集成 Pager |
| `app/.../ui/screens/home/HomeScreen.kt` | **修改** | 使用 collectAsLazyPagingItems |
| `app/.../ui/screens/inspiration/InspirationScreen.kt` | **修改** | 使用 collectAsLazyPagingItems |

---

## 5. Phase 4: 离线缓存增强

### 5.1 目标
构建**双层缓存架构**（内存 + 磁盘），加速离线场景下的数据访问。

### 5.2 当前状态
项目已有存储方案：
- **Room 数据库**：持久化存储（主数据源）
- **EncryptedSharedPreferences (ESP)**：加密偏好设置（替代旧 DataStore）

### 5.3 双层缓存架构

```
┌─────────────────────────────────────────────────────────────┐
│                      缓存层次结构                            │
│                                                             │
│  L1: 内存缓存 (In-Memory Cache)                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Map<String, CachedData>                              │   │
│  │ - 响应时间: < 1ms                                    │   │
│  │ - 容量限制: 最多 1000 条                             │   │
│  │ - 过期策略: LRU + TTL (5分钟)                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↕ Hit/Miss                        │
│  L2: 磁盘缓存 (Disk Cache via ESP)                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ EncryptedSharedPreferences                           │   │
│  │ - 响应时间: ~10ms                                    │   │
│  │ - 容量限制: 无（受磁盘空间限制）                     │   │
│  │ - 过期策略: TTL (24小时)                             │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↕ Miss                            │
│  L3: 数据库 (Room Database)                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ SQLite                                               │   │
│  │ - 响应时间: ~50-200ms                                │   │
│  │ - 永久存储                                           │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 5.4 核心组件设计

#### 5.4.1 CacheManager
```kotlin
/**
 * 双层缓存管理器
 *
 * 协调 L1 内存缓存和 L2 磁盘缓存的读写。
 */
class CacheManager(private val context: Context) {

    // L1: 内存缓存
    private val memoryCache = LruCache<String, CachedData>(maxSize = 1000)

    // L2: 磁盘缓存 (ESP)
    private val diskCache = EncryptedPreferencesCache(context)

    /**
     * 读取缓存（L1 → L2 → L3 回退）
     */
    suspend fun <T> get(key: String, serializer: Serializer<T>): T?

    /**
     * 写入缓存（同时写入 L1 和 L2）
     */
    suspend fun <T> put(key: String, data: T, serializer: Serializer<T>)

    /**
     * 使缓存失效
     */
    suspend fun invalidate(key: String)

    /**
     * 清除所有缓存
     */
    suspend fun clearAll()
}
```

#### 5.4.2 缓存键设计
```kotlin
object CacheKeys {
    // 待办相关
    const val TODOS_ALL = "cache:todos:all"
    const val TODOS_PENDING = "cache:todos:pending"
    const val TODOS_COMPLETED = "cache:todos:completed"

    // 灵感相关
    const val INSPIRATIONS_ALL = "cache:inspirations:all"

    // 日期相关
    const val DATES_ALL = "cache:dates:all"

    // 通用前缀
    private const val PREFIX = "cache"
}
```

#### 5.4.3 Repository 层集成
```kotlin
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
    private val cacheManager: CacheManager  // 注入缓存管理器
) {

    /**
     * 获取待办列表（带缓存）
     *
     * 读取顺序: L1 内存 → L2 磁盘 → L3 数据库
     */
    fun getAllTodos(): Flow<List<TodoItem>> = callbackFlow {
        // 1. 尝试从 L1 读取
        cacheManager.get<List<TodoItem>>(CacheKeys.TODOS_ALL, TodoListSerializer())
            ?.let { trySend(it); return@callbackFlow }

        // 2. 尝试从 L2 读取
        cacheManager.getFromDisk<List<TodoItem>>(CacheKeys.TODOS_ALL, TodoListSerializer())
            ?.let { trySend(it); return@callbackFlow }

        // 3. 从数据库读取并回填缓存
        todoDao.getAllTodos().collect { todos ->
            cacheManager.put(CacheKeys.TODOS_ALL, todos, TodoListSerializer())
            trySend(todos)
        }
    }
}
```

### 5.5 序列化方案
由于 ESP 仅支持基本类型，需自定义序列化：

```kotlin
interface Serializer<T> {
    fun serialize(data: T): String
    fun deserialize(json: String): T
}

object TodoListSerializer : Serializer<List<TodoItem>> {
    override fun serialize(data: List<TodoItem>): String {
        return Json.encodeToString(data)
    }

    override fun deserialize(json: String): List<TodoItem> {
        return Json.decodeFromString(json)
    }
}
```

### 5.6 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `app/.../cache/CacheManager.kt` | **新建** | 双层缓存管理器 |
| `app/.../cache/LruMemoryCache.kt` | **新建** | L1 内存缓存实现 |
| `app/.../cache/DiskCache.kt` | **新建** | L2 磁盘缓存实现 |
| `app/.../cache/Serializer.kt` | **新建** | 序列化接口与实现 |
| `app/.../cache/CacheKeys.kt` | **新建** | 缓存键定义 |
| `app/.../di/AppModule.kt` | **修改** | 提供 CacheManager 单例 |
| `app/.../repository/TodoRepository.kt` | **修改** | 集成缓存层 |
| `app/.../repository/InspirationRepository.kt` | **修改** | 集成缓存层 |
| `app/.../repository/SpecialDateRepository.kt` | **修改** | 集成缓存层 |

---

## 6. 实施计划总览

### 6.1 时间线

```
Week 1:
├── Day 1-2:  Phase 1 - Shimmer 动画效果（已完成基础，仅需开启）
├── Day 3-4:  Phase 2 - 智能预加载策略
└── Day 5:    测试 & 验证

Week 2:
├── Day 1-3:  Phase 3 - Paging 3 集成（仅待办+灵感页）
├── Day 4:    Phase 4 - 离线缓存增强（L1 内存缓存）
└── Day 5:    集成测试 & 性能基准测试
```

### 6.2 依赖关系图

```
Phase 1 (Shimmer) 
    ↓ 独立
Phase 2 (智能预加载)
    ↓ 独立
Phase 3 (Paging 3)
    ↓ 可选配合
Phase 4 (离线缓存) ← 可增强 Phase 2 的效果
```

### 6.3 验收标准

| Phase | 验收指标 | 测试方法 |
|-------|---------|---------|
| **Shimmer** | 骨架屏显示动态扫光效果 | 视觉验证 + 录屏对比 |
| **智能预加载** | 首次加载时间减少 ≥30% | Systrace / Perfetto 分析 |
| **Paging 3** | 1000 条数据下 FPS ≥55 | Android Studio Profiler |
| **离线缓存** | 断网后数据可立即显示 | 飞行模式测试 |

---

## 7. 风险评估与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| Shimmer 动画导致低端机卡顿 | 中 | 低 | 提供 `shimmerEnabled` 参数，可关闭 |
| 用户行为统计隐私顾虑 | 低 | 低 | 数据仅本地存储，不上传服务器 |
| Paging 3 与现有架构冲突 | 高 | 中 | 渐进式迁移，保留全量加载作为 fallback |
| 缓存数据不一致 | 高 | 中 | 设置合理 TTL，数据库变更时主动失效 |
| 序列化/反序列化性能瓶颈 | 中 | 低 | 使用 Kotlin Serialization（比 Gson 快 3x） |

---

## 8. 后续扩展方向（不在本次范围）

1. **CDN 边缘缓存**：云端同步数据的边缘加速
2. **Predictive Prefetching**：基于 ML 模型预测用户下一步操作
3. **增量同步**：仅同步变更部分的数据（而非全量）
4. **多进程缓存共享**：Widget/主进程缓存一致性

---

*文档结束*
