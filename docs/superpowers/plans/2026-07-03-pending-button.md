# 待完成按钮实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在"我的待办"页面新增"待完成(N)"按钮,位置根据置顶数量动态调整(< 4 时在顶部,> 3 时在置顶区后),数量根据位置动态计算;同步将已完成按钮颜色从灰色改为绿色。

**Architecture:** 复用 PinnedDivider 模式 - 抽取 `PendingSectionHeader` + `DisplayItem.PendingDivider` + `pendingCount` 动态计算 + 持久化键。已完成按钮通过修改 `CompletedSectionHeader` 调用点改色。新增 `SectionHeaderColors` 集中定义三个 section 的颜色。

**Tech Stack:** Kotlin · Jetpack Compose · Material3 · StateFlow · Coroutines (combine) · JUnit5 + MockK

---

## File Structure

| 操作 | 路径 | 职责 |
|------|------|------|
| 新增 | `app/src/main/java/com/corgimemo/app/ui/components/PendingSectionHeader.kt` | 待完成专用封装,基于 CollapsibleSectionHeader,颜色 = `SectionHeaderColors.Pending` |
| 新增 | `app/src/main/java/com/corgimemo/app/ui/components/SectionHeaderColors.kt` | 集中定义三个 section header 的颜色常量 |
| 新增 | `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPendingButtonTest.kt` | 待完成按钮 ViewModel 逻辑单元测试 |
| 修改 | `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt` | 新增 `SHOW_PENDING` 键 + `showPending` Flow + `setShowPending()` 写入器 + 迁移列表 |
| 修改 | `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` | 1) `_showPending`/`showPending` 状态;2) `pendingCount` combine 派生;3) `toggleShowPending()`;4) init 订阅 |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 1) `DisplayItem` 新增 `PendingDivider`;2) 状态订阅新增;3) `displayItems` 双分支逻辑(Case A/B);4) 渲染分支;5) `ReorderableLazyColumn.key`;6) `CompletedSectionHeader` 改色;7) `SectionHeaderColors` import |

---

## Task 1: 在 CorgiPreferences 中添加 showPending 持久化支持

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt:161-162` (Keys 新增,SHOW_PINNED 之后)
- Modify: `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt:464-470` (新增 getter/setter,setShowPinned 之后)
- Modify: `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt:228` (迁移列表新增)

- [ ] **Step 1: 在 Keys object 中新增 SHOW_PENDING 常量**

在 `private object Keys` 中,**在 `SHOW_PINNED` 之后**新增:

```kotlin
/** V2.11: 待办页"待完成"区域展开状态(默认展开) */
const val SHOW_PENDING = "show_pending"
```

参考现有 `SHOW_PINNED` (line 161-162) 的位置和格式。

- [ ] **Step 2: 在 CorgiPreferences 类中新增 showPending getter/setter**

**位置**: 在 `setShowPinned` 函数之后(line 462-464) 新增:

```kotlin
/** V2.11: 获取待办页"待完成"区域展开状态的Flow(默认展开) */
val showPending: Flow<Boolean> = booleanFlow(Keys.SHOW_PENDING, true)

/** V2.11: 设置待办页"待完成"区域展开状态 */
suspend fun setShowPending(show: Boolean) = withContext(Dispatchers.IO) {
    esp.edit().putBoolean(Keys.SHOW_PENDING, show).apply()
}
```

- [ ] **Step 3: 在迁移列表中新增 SHOW_PENDING**

**位置**: `migrateFromDataStoreIfNeeded` 方法(line 222-233) 的**布尔类型键列表**中,在 `Keys.SHOW_PINNED` 之后新增 `Keys.SHOW_PENDING`:

```kotlin
listOf(
    Keys.IS_FIRST_LAUNCH, Keys.SOUND_ENABLED, Keys.HAPTIC_ENABLED,
    Keys.IS_ONBOARDING_COMPLETED, Keys.AUTO_BACKUP_ENABLED,
    Keys.SHOW_COMPLETED, Keys.FIRST_GUIDE_SHOWN,
    Keys.SHOW_PINNED, Keys.SHOW_PENDING
).forEach { key ->
    val value = legacyPrefs[booleanPreferencesKey(key)]
    if (value != null) {
        esp.edit().putBoolean(key, value).apply()
        migratedCount++
    }
}
```

- [ ] **Step 4: 验证代码格式**

对照 `showCompleted` / `setShowCompleted` / `SHOW_PENDING` (line 459-464) 的格式,确认:
- `booleanFlow(Keys.SHOW_PENDING, true)` - 默认值与设计文档一致(`true` = 展开)
- `withContext(Dispatchers.IO)` 包裹
- `esp.edit().putBoolean(...).apply()`

- [ ] **Step 5: 提交**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt
echo "feat(prefs): 新增 showPending 持久化键(默认展开)" > commit-msg.txt
git commit -F commit-msg.txt
Remove-Item commit-msg.txt
```

> **注**: 此 Task 不包含编译步骤,根据用户硬约束,所有编译必须手动执行。

---

## Task 2: 在 HomeViewModel 中添加 showPending 状态、pendingCount 与 toggleShowPending()

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt:143-145` (新增状态字段,showPinned 之后)
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt:190-193` (新增 pendingCount,pinnedCount 之后)
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt:612-614` (init 块新增订阅)
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt:1419-1428` (新增 toggleShowPinned 之后)

- [ ] **Step 1: 新增 _showPending / showPending 状态字段**

**位置**: 在 `private val _showPinned` 块(line 143-145) 之后,新增:

```kotlin
/** V2.11: "待完成"区域是否展开(从持久化加载,默认展开) */
private val _showPending = MutableStateFlow(true)
val showPending: StateFlow<Boolean> = _showPending.asStateFlow()
```

- [ ] **Step 2: 确认 combine 操作符已导入**

用 Grep 工具在 HomeViewModel.kt 中搜索 `import kotlinx.coroutines.flow.combine`:

```bash
grep "import kotlinx.coroutines.flow.combine" app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt
```

如果未导入,在 import 区域添加:

```kotlin
import kotlinx.coroutines.flow.combine
```

**位置**: 找到 `import kotlinx.coroutines.flow.map` 附近,按字母顺序插入(应放在 map 之前)。

- [ ] **Step 3: 新增 pendingCount 派生 Flow**

**位置**: 在 `pinnedCount` 之后(line 190-193) 新增:

```kotlin
/** V2.11: 待完成待办总数(动态计算:置顶 ≤ 3 时含置顶,> 3 时仅非置顶) */
val pendingCount: StateFlow<Int> = combine(
    _todos, pinnedCount
) { todos, pinnedN ->
    val nonPinned = todos.count { !it.isPinned && it.status == 0 }
    if (pinnedN <= 3) {
        // Case B(置顶 ≤ 3):待完成按钮在最前,代表所有待完成(含置顶)
        todos.count { it.status == 0 }
    } else {
        // Case A(置顶 ≥ 4):待完成按钮在置顶区后,仅代表非置顶
        nonPinned
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
```

**关键**:
- 使用 `combine(_todos, pinnedCount)` 监听两个数据源
- `pinnedCount` 已在 Task 2(置顶按钮) 中定义,可直接引用
- 过滤条件 `!it.isPinned && it.status == 0` 排除置顶和已完成

- [ ] **Step 4: 新增 toggleShowPending() 方法**

**位置**: 在 `toggleShowPinned()` 之后(line 1419-1428) 新增:

```kotlin
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
```

- [ ] **Step 5: 在 init 块中新增 showPending 订阅**

**位置**: 在 init 块的 `corgiPreferences.showPinned.collect` 调用(line 612-614) 之后,新增:

```kotlin
viewModelScope.launch {
    corgiPreferences.showPending.collect { _showPending.value = it }
}
```

- [ ] **Step 6: 验证引用与现有代码一致**

确认以下符号已存在:
- `corgiPreferences` (Task 1 已新增 `showPending`/`setShowPending`)
- `viewModelScope` (现有)
- `_todos` (现有)
- `pinnedCount` (Task 2 置顶按钮已定义)
- `SharingStarted.WhileSubscribed(5000)` (现有模式)

- [ ] **Step 7: 提交**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt
echo "feat(viewmodel): 新增 showPending 状态、pendingCount 与 toggleShowPending()" > commit-msg.txt
git commit -F commit-msg.txt
Remove-Item commit-msg.txt
```

---

## Task 3: 创建单元测试 HomeViewModelPendingButtonTest

**Files:**
- Create: `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPendingButtonTest.kt`

- [ ] **Step 1: 读取现有测试以了解 mock 模式**

用 Read 工具读取:
- `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPinnedButtonTest.kt` (上次创建,作为模板)
- 确认 12 个 mock 字段的命名和顺序

- [ ] **Step 2: 创建测试文件**

**路径**: `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPendingButtonTest.kt`

**完整代码**:

```kotlin
package com.corgimemo.app.viewmodel

import android.content.Context
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.repository.AchievementChecker
import com.corgimemo.app.data.repository.AchievementRepository
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.DeletedTodoRepository
import com.corgimemo.app.data.repository.MoodHistoryRepository
import com.corgimemo.app.data.repository.OperationLogRepository
import com.corgimemo.app.data.repository.TaskDailyStatsRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.util.FileCopyManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 待办"待完成"按钮相关 ViewModel 逻辑单元测试
 *
 * 覆盖:
 * - showPending 默认值
 * - toggleShowPending 翻转 + 持久化
 * - toggleShowPending 两次回到原状态
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPendingButtonTest {

    private lateinit var mockTodoRepository: TodoRepository
    private lateinit var mockCorgiRepository: CorgiRepository
    private lateinit var mockCategoryRepository: CategoryRepository
    private lateinit var mockDeletedTodoRepository: DeletedTodoRepository
    private lateinit var mockAchievementChecker: AchievementChecker
    private lateinit var mockAchievementRepository: AchievementRepository
    private lateinit var mockCorgiPreferences: CorgiPreferences
    private lateinit var mockMoodHistoryRepository: MoodHistoryRepository
    private lateinit var mockOperationLogRepository: OperationLogRepository
    private lateinit var mockTaskDailyStatsRepository: TaskDailyStatsRepository
    private lateinit var mockFileCopyManager: FileCopyManager
    private lateinit var mockContext: Context
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() = runTest {
        // 创建 12 个 relaxed mock(11 依赖 + Context)
        mockTodoRepository = mockk(relaxed = true)
        mockCorgiRepository = mockk(relaxed = true)
        mockCategoryRepository = mockk(relaxed = true)
        mockDeletedTodoRepository = mockk(relaxed = true)
        mockAchievementChecker = mockk(relaxed = true)
        mockAchievementRepository = mockk(relaxed = true)
        mockCorgiPreferences = mockk(relaxed = true)
        mockMoodHistoryRepository = mockk(relaxed = true)
        mockOperationLogRepository = mockk(relaxed = true)
        mockTaskDailyStatsRepository = mockk(relaxed = true)
        mockFileCopyManager = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)

        // 桩:todoRepository 的 Flow,避免 filteredTodos 初始化 NPE
        coEvery { mockTodoRepository.getAllTodos() } returns flowOf(emptyList())
        coEvery { mockTodoRepository.observeAllSorted() } returns flowOf(emptyList())

        // 桩:corgiPreferences 的所有 booleanFlow
        coEvery { mockCorgiPreferences.showCompleted } returns flowOf(false)
        coEvery { mockCorgiPreferences.showPinned } returns flowOf(true)
        coEvery { mockCorgiPreferences.showPending } returns flowOf(true)  // 新增
        coEvery { mockCorgiPreferences.hideDetails } returns flowOf(false)
        coEvery { mockCorgiPreferences.hideCompletedItems } returns flowOf(false)

        // 必须在 viewModel 构造前 setMain
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // 构造 ViewModel,命名参数顺序与 HomeViewModel 构造函数完全一致
        viewModel = HomeViewModel(
            todoRepository = mockTodoRepository,
            corgiRepository = mockCorgiRepository,
            categoryRepository = mockCategoryRepository,
            deletedTodoRepository = mockDeletedTodoRepository,
            achievementChecker = mockAchievementChecker,
            achievementRepository = mockAchievementRepository,
            corgiPreferences = mockCorgiPreferences,
            moodHistoryRepository = mockMoodHistoryRepository,
            operationLogRepository = mockOperationLogRepository,
            taskDailyStatsRepository = mockTaskDailyStatsRepository,
            fileCopyManager = mockFileCopyManager,
            context = mockContext
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 场景:ViewModel 初始化,corgiPreferences.showPending 默认发射 true
     * 预期:viewModel.showPending.value == true
     */
    @Test
    fun `showPending 默认值为 true`() = runTest {
        assertTrue(viewModel.showPending.value)
    }

    /**
     * 场景:用户点击待完成按钮
     * 预期:
     * - showPending 状态翻转
     * - corgiPreferences.setShowPending 被调用一次,参数为翻转后的值
     */
    @Test
    fun `toggleShowPending 翻转状态并触发持久化`() = runTest {
        coEvery { mockCorgiPreferences.setShowPending(any()) } returns Unit
        val initial = viewModel.showPending.value
        viewModel.toggleShowPending()
        assertEquals(!initial, viewModel.showPending.value)
        coVerify(exactly = 1) { mockCorgiPreferences.setShowPending(!initial) }
    }

    /**
     * 场景:用户连续点击两次待完成按钮
     * 预期:
     * - showPending 状态回到初始值
     * - corgiPreferences.setShowPending 被调用 2 次
     */
    @Test
    fun `toggleShowPending 两次回到原状态`() = runTest {
        coEvery { mockCorgiPreferences.setShowPending(any()) } returns Unit
        val initial = viewModel.showPending.value
        viewModel.toggleShowPending()
        viewModel.toggleShowPending()
        assertEquals(initial, viewModel.showPending.value)
        coVerify(exactly = 2) { mockCorgiPreferences.setShowPending(any()) }
    }
}
```

**注意事项**:
- 12 个 mock 字段(11 依赖 + Context)与现有 HomeViewModelPinnedButtonTest 一致
- 必须 stub 5 个 booleanFlow(新增 `showPending`)
- 构造函数使用命名参数,顺序与 HomeViewModel 一致
- `setShowPending` 是 suspend 函数,需用 `coEvery`/`coVerify`

- [ ] **Step 3: 手动编译验证**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
./gradlew :app:compileDebugUnitTestKotlin
```

预期: `BUILD SUCCESSFUL`,无错误。

- [ ] **Step 4: 提交**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPendingButtonTest.kt
echo "test: 新增 HomeViewModelPendingButtonTest 单元测试" > commit-msg.txt
git commit -F commit-msg.txt
Remove-Item commit-msg.txt
```

---

## Task 4: 创建 SectionHeaderColors 集中定义

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/SectionHeaderColors.kt`

- [ ] **Step 1: 创建文件**

**路径**: `app/src/main/java/com/corgimemo/app/ui/components/SectionHeaderColors.kt`

**完整代码**:

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 待办页三个 section header 的颜色常量
 *
 * - Pinned:置顶(主色橙,跟随主题)
 * - Pending:待完成(固定蓝色,跨主题保持一致以提升品牌识别)
 * - Completed:已完成(固定绿色,跨主题保持一致以表示"已处理"语义)
 *
 * 集中定义便于统一调整和未来扩展(如有深色模式特定色,只需修改此处)
 */
object SectionHeaderColors {
    /** 置顶区头颜色(主色橙) */
    val Pinned: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    /** 待完成区头颜色(固定蓝色) */
    val Pending: Color = Color(0xFF5A8DEE)

    /** 已完成区头颜色(固定绿色) */
    val Completed: Color = Color(0xFF7EC8A0)
}
```

**设计要点**:
- Pinned 用 `MaterialTheme.colorScheme.primary`(跟随主题切换)
- Pending 和 Completed 使用固定色值(跨主题保持稳定)
- 使用 `@Composable @ReadOnlyComposable` 标注 Pinned getter,允许在 Composable 中调用

- [ ] **Step 2: 验证文件**

用 Read 工具检查文件,确保:
- import 路径正确
- `@Composable @ReadOnlyComposable` 标注正确

- [ ] **Step 3: 手动编译验证**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
./gradlew :app:compileDebugKotlin
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/SectionHeaderColors.kt
echo "feat(ui): 新增 SectionHeaderColors 集中定义三个 section 颜色" > commit-msg.txt
git commit -F commit-msg.txt
Remove-Item commit-msg.txt
```

---

## Task 5: 创建 PendingSectionHeader 专用封装

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/PendingSectionHeader.kt`

- [ ] **Step 1: 创建文件**

**路径**: `app/src/main/java/com/corgimemo/app/ui/components/PendingSectionHeader.kt`

**完整代码**:

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 待完成区头按钮
 *
 * 位置根据置顶数量动态调整:
 * - 置顶 ≤ 3 时,显示在列表最前
 * - 置顶 ≥ 4 时,显示在置顶区之后
 *
 * 基于 [CollapsibleSectionHeader] 实现,统一设计语言(无背景、箭头在左、无水波纹)。
 * 颜色 = [SectionHeaderColors.Pending](蓝色)。
 *
 * @param count 当前待完成数量(由 pendingCount 提供,动态计算)
 * @param isExpanded 是否展开
 * @param onClick 点击回调
 * @param modifier 外部 Modifier
 */
@Composable
fun PendingSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = CollapsibleSectionHeader(
    label = "待完成",
    count = count,
    isExpanded = isExpanded,
    color = SectionHeaderColors.Pending,
    expandedLabel = "收起待完成",
    collapsedLabel = "展开待完成",
    onClick = onClick,
    modifier = modifier,
)
```

- [ ] **Step 2: 手动编译验证**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
./gradlew :app:compileDebugKotlin
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/PendingSectionHeader.kt
echo "feat(ui): 新增 PendingSectionHeader 待完成区头按钮封装" > commit-msg.txt
git commit -F commit-msg.txt
Remove-Item commit-msg.txt
```

---

## Task 6: 改造 CompletedSectionHeader 改用绿色(并切换到 SectionHeaderColors)

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:2284-2290` (CompletedSectionHeader 函数体)

- [ ] **Step 1: 替换 CompletedSectionHeader 函数体**

**位置**: `HomeScreen.kt` line 2278-2290 的 `CompletedSectionHeader` 函数。

**改造前**:

```kotlin
@Composable
private fun CompletedSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) = CollapsibleSectionHeader(
    label = "已完成",
    count = count,
    isExpanded = isExpanded,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick = onClick,
)
```

**改造后**(完整替换为):

```kotlin
/**
 * "已完成"区域分隔按钮
 *
 * 颜色已统一使用 [SectionHeaderColors.Completed](绿色 #7EC8A0),
 * 与新"置顶"/"待完成"按钮形成完整的设计语言。
 */
@Composable
private fun CompletedSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) = CollapsibleSectionHeader(
    label = "已完成",
    count = count,
    isExpanded = isExpanded,
    color = SectionHeaderColors.Completed,
    onClick = onClick,
)
```

- [ ] **Step 2: 验证并补充 import**

用 Read 工具检查 `HomeScreen.kt` 顶部 import 区域,确认 `SectionHeaderColors` 已导入。

如果未导入,在 `import com.corgimemo.app.ui.components.CollapsibleSectionHeader` 之后按字母顺序添加:

```kotlin
import com.corgimemo.app.ui.components.SectionHeaderColors
```

**位置**: `CollapsibleSectionHeader` (C) < `SectionHeaderColors` (S),所以 SectionHeaderColors 在 CollapsibleSectionHeader 之后。

- [ ] **Step 3: 手动编译验证**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
./gradlew :app:compileDebugKotlin
```

预期: `BUILD SUCCESSFUL`,无未使用 import 警告。

- [ ] **Step 4: 提交**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
echo "refactor(home): CompletedSectionHeader 改用 SectionHeaderColors.Completed(绿色)" > commit-msg.txt
git commit -F commit-msg.txt
Remove-Item commit-msg.txt
```

---

## Task 7: HomeScreen 集成 PendingDivider (5 处修改)

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:2303-2307` (DisplayItem 新增)
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:155-156` (状态订阅)
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:734-770` (displayItems 双分支)
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:788-794` (ReorderableLazyColumn key)
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:819-829` (渲染分支)
- Modify: import 区域(添加 `PendingSectionHeader`、`SectionHeaderColors`)

- [ ] **Step 1: 在 DisplayItem 中新增 PendingDivider**

**位置**: line 2303-2307 附近的 `private sealed interface DisplayItem` 定义。

**改造前**:

```kotlin
private sealed interface DisplayItem {
    data class Todo(val item: TodoItem) : DisplayItem
    data class PinnedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class CompletedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
}
```

**改造后**:

```kotlin
private sealed interface DisplayItem {
    data class Todo(val item: TodoItem) : DisplayItem
    data class PinnedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class PendingDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class CompletedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
}
```

- [ ] **Step 2: 状态订阅新增 showPending / pendingCount**

**位置**: 在 HomeScreen 中现有的 `val showPinned by viewModel.showPinned.collectAsState()` 之后,新增:

```kotlin
val showPending by viewModel.showPending.collectAsState()
val pendingCount by viewModel.pendingCount.collectAsState()
```

- [ ] **Step 3: 改造 displayItems 构建逻辑(关键:双分支)**

**位置**: 第 728-770 行附近的 `displayItems` 块。

**改造前**(参考结构):

```kotlin
val displayItems = remember(
    filteredPending, filteredCompleted,
    showPinned, showCompleted,
    pinnedCount, completedCount,
    hideCompletedItems
) {
    buildList {
        if (pinnedCount >= 4) {
            add(DisplayItem.PinnedDivider(
                count = pinnedCount,
                isExpanded = showPinned
            ))
            if (showPinned) {
                filteredPending.filter { it.isPinned }
                    .forEach { add(DisplayItem.Todo(it)) }
            }
        } else {
            filteredPending.filter { it.isPinned }
                .forEach { add(DisplayItem.Todo(it)) }
        }
        filteredPending.filter { !it.isPinned }
            .forEach { add(DisplayItem.Todo(it)) }
        if (!hideCompletedItems && completedCount > 0) {
            add(DisplayItem.CompletedDivider(
                count = completedCount,
                isExpanded = showCompleted
            ))
            if (showCompleted) {
                filteredCompleted.forEach { add(DisplayItem.Todo(it)) }
            }
        }
    }
}
```

**改造后**:

```kotlin
val displayItems = remember(
    filteredPending, filteredCompleted,
    showPinned, showPending, showCompleted,
    pinnedCount, pendingCount, completedCount,
    hideCompletedItems
) {
    buildList {
        if (pinnedCount >= 4) {
            // ===== Case A:置顶 ≥ 4 =====
            // 1. 置顶区(顶部)
            add(DisplayItem.PinnedDivider(
                count = pinnedCount,
                isExpanded = showPinned
            ))
            if (showPinned) {
                filteredPending.filter { it.isPinned }
                    .forEach { add(DisplayItem.Todo(it)) }
            }
            // 2. 待完成区(置顶区之后)
            add(DisplayItem.PendingDivider(
                count = pendingCount,
                isExpanded = showPending
            ))
            if (showPending) {
                filteredPending.filter { !it.isPinned }
                    .forEach { add(DisplayItem.Todo(it)) }
            }
        } else {
            // ===== Case B:置顶 < 4 =====
            // 待完成按钮在最前(代表所有待完成)
            add(DisplayItem.PendingDivider(
                count = pendingCount,
                isExpanded = showPending
            ))
            if (showPending) {
                // 置顶先,非置顶后
                filteredPending.filter { it.isPinned }
                    .forEach { add(DisplayItem.Todo(it)) }
                filteredPending.filter { !it.isPinned }
                    .forEach { add(DisplayItem.Todo(it)) }
            }
        }
        // 3. 已完成区(原有逻辑)
        if (!hideCompletedItems && completedCount > 0) {
            add(DisplayItem.CompletedDivider(
                count = completedCount,
                isExpanded = showCompleted
            ))
            if (showCompleted) {
                filteredCompleted.forEach { add(DisplayItem.Todo(it)) }
            }
        }
    }
}
```

- [ ] **Step 4: 更新 ReorderableLazyColumn 的 key 函数**

**位置**: 第 788-794 行附近的 `key = { item -> ... }` lambda。

**改造前**:

```kotlin
key = { item ->
    when (item) {
        is DisplayItem.Todo -> item.item.id
        is DisplayItem.PinnedDivider -> "pinned_divider_${item.count}"
        is DisplayItem.CompletedDivider -> "completed_divider"
    }
},
```

**改造后**:

```kotlin
key = { item ->
    when (item) {
        is DisplayItem.Todo -> item.item.id
        is DisplayItem.PinnedDivider -> "pinned_divider_${item.count}"
        is DisplayItem.PendingDivider -> "pending_divider_${item.count}"
        is DisplayItem.CompletedDivider -> "completed_divider"
    }
},
```

- [ ] **Step 5: 在渲染分支中添加 PendingDivider 处理**

**位置**: 第 819-829 行附近的 `when (displayItem) { ... }` 块。

**改造前**:

```kotlin
when (displayItem) {
    is DisplayItem.PinnedDivider -> {
        PinnedSectionHeader(...)
    }
    is DisplayItem.CompletedDivider -> {
        CompletedSectionHeader(...)
    }
    is DisplayItem.Todo -> {
        // 原有 Todo 渲染
    }
}
```

**改造后**(在 PinnedDivider **之后**添加 PendingDivider 分支):

```kotlin
when (displayItem) {
    is DisplayItem.PinnedDivider -> {
        PinnedSectionHeader(...)
    }
    is DisplayItem.PendingDivider -> {
        PendingSectionHeader(
            count = displayItem.count,
            isExpanded = displayItem.isExpanded,
            onClick = {
                viewModel.onUserInteraction()
                viewModel.toggleShowPending()
            }
        )
    }
    is DisplayItem.CompletedDivider -> {
        CompletedSectionHeader(...)
    }
    is DisplayItem.Todo -> {
        // 原有 Todo 渲染不变
    }
}
```

- [ ] **Step 6: 添加 PendingSectionHeader import**

在 import 区域,找到 `import com.corgimemo.app.ui.components.PinnedSectionHeader` 这一行,**在它之后**按字母顺序添加:

```kotlin
import com.corgimemo.app.ui.components.PendingSectionHeader
```

字母顺序:`PendingSectionHeader` > `PinnedSectionHeader`,所以应放在 PinnedSectionHeader 之后。

- [ ] **Step 7: 手动编译验证**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
./gradlew :app:compileDebugKotlin
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 8: 提交**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
echo "feat(home): 集成 PendingDivider 到 displayItems 与渲染分支" > commit-msg.txt
git commit -F commit-msg.txt
Remove-Item commit-msg.txt
```

---

## Task 8: 运行单元测试 + 手动 UI 验证

**Files:** 无修改

- [ ] **Step 1: 运行新增的 PendingButton 单元测试**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.viewmodel.HomeViewModelPendingButtonTest"
```

预期: `BUILD SUCCESSFUL`,所有测试通过。

- [ ] **Step 2: 运行现有 HomeViewModel 测试确保未破坏**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
./gradlew :app:testDebugUnitTest
```

预期: 所有测试通过(含 PinnedButton、Reorder、PendingButton)。

- [ ] **Step 3: 启动 App,准备测试数据**

- 准备数据:至少 4 个置顶待办 + 至少 3 个普通(非置顶)待办 + 至少 2 个已完成
- 启动 App 至"我的待办"页面

- [ ] **Step 4: 验证 Case A(置顶 ≥ 4)**

- 预期: 顶部"收起置顶 (4) ▼" → 4 个置顶待办 → "收起待完成 (N) ▼"(蓝色,N = 非置顶数)→ N 个普通待办 → "展开已完成 (M) ▶"(绿色)

- [ ] **Step 5: 验证 Case B(置顶 ≤ 3)**

- 取消置顶 1 个待办(4→3)
- 预期: 置顶按钮消失;"待完成"按钮移到最前,显示 总计(3 个置顶 + N 个非置顶);3 个置顶待办 + N 个非置顶待办依次显示

- [ ] **Step 6: 验证折叠交互**

- 点击"收起待完成"
- 预期: 折叠区域(置顶 ≤ 3 时为全部,置顶 > 3 时为非置顶)平滑收起,按钮变"展开待完成 ▶",箭头朝右

- [ ] **Step 7: 验证展开交互**

- 再次点击按钮
- 预期: 平滑展开,按钮恢复"收起待完成 ▼",箭头朝下

- [ ] **Step 8: 验证无水波纹**

- 点击时观察
- 预期: **无**圆形水波纹扩散,仅有透明度变化

- [ ] **Step 9: 验证已完成按钮颜色**

- 观察"已完成"按钮颜色
- 预期: **绿色 #7EC8A0**(对比之前的灰色)

- [ ] **Step 10: 验证持久化**

- 折叠待完成区域
- 完全退出 App
- 重新启动
- 预期: 待完成区域保持折叠状态

- [ ] **Step 11: 验证三个按钮协同**

- 折叠置顶、折叠待完成、折叠已完成
- 预期: 三个按钮独立工作,互不影响

- [ ] **Step 12: 验证阈值边界 3↔4**

- 从 Case B 重新置顶 1 个待办(3→4)
- 预期: 待完成按钮从顶部移到置顶区后,数量从 总计 变为 仅非置顶

- [ ] **Step 13: 验证深色模式**

- 切换深色模式
- 预期: 蓝色待完成按钮在深色背景下对比度充足,绿色已完成按钮也清晰

- [ ] **Step 14: 验证主题色切换**

- 在"我的 → 设置"中切换 6 种主题色
- 预期: 置顶按钮颜色跟随 `colorScheme.primary` 变化;待完成蓝色 + 已完成绿色保持不变

- [ ] **Step 15: 验证拖拽排序**

- 折叠待完成,长按一个待办拖拽
- 预期: 折叠时不可拖到 Divider 上方(因待办不在列表中);展开时正常拖拽

- [ ] **Step 16: 验证批量选择**

- 进入批量选择模式
- 预期: 待完成按钮可点击,不影响多选

- [ ] **Step 17: 验证搜索/分类**

- 搜索关键词
- 预期: 待完成按钮仍显示,过滤后无匹配时按钮仍存在

- [ ] **Step 18: 记录验证结果**

- 16 个手动测试场景全部通过 → 任务完成
- 如有失败,记录问题并修复

---

## Self-Review

**1. Spec coverage**:

| Spec 章节 | 覆盖 Task |
|----------|----------|
| § 1 背景与目标 | Task 1-7 实现 |
| § 2 设计决策 | Task 1-7 实现(10 个决策全部实现) |
| § 3 架构 | Task 1-2 状态 + 持久化 |
| § 4 组件设计 | Task 4(SectionHeaderColors) + Task 5(Pending) + Task 6(Completed 改色) |
| § 5 ViewModel | Task 2 |
| § 6 CorgiPreferences | Task 1 |
| § 7 HomeScreen | Task 7 集成 |
| § 8 边界情况(17 个) | Task 8 手动验证 |
| § 9 测试 | Task 3 单元测试 + Task 8 运行 + Task 8 手动 |
| § 10 验收标准 | Task 8 测试 + Task 8 手动 |
| § 11 风险 | Task 8 兜底 |
| § 12 变更文件清单 | Task 1-7 已穷举 |

**2. Placeholder scan**:
- ✅ 无 "TBD"、"TODO"、"实现后"
- ✅ 所有代码块包含完整可执行代码
- ✅ 无"类似 Task N"的引用 - 每个 Task 独立完整
- ✅ 所有 import 显式列出

**3. Type consistency**:
- `showPending: StateFlow<Boolean>` - Task 1(CorgiPreferences)、Task 2(HomeViewModel)、Task 7(HomeScreen) 三处签名一致
- `pendingCount: StateFlow<Int>` - Task 2 定义、Task 7 使用一致
- `toggleShowPending()` - Task 2 定义、Task 7 调用一致
- `DisplayItem.PendingDivider(count: Int, isExpanded: Boolean)` - Task 7 定义与使用一致
- `CollapsibleSectionHeader(...)` - 复用 Task 4-5 现有
- `PendingSectionHeader(count, isExpanded, onClick, modifier)` - Task 5 定义、Task 7 调用一致
- `setShowPending(show: Boolean)` - Task 1 定义、Task 2 调用、Task 3 测试一致
- `SectionHeaderColors.Pending/Completed` - Task 4 定义、Task 5/6 使用一致

**检查通过,无需修正。**
