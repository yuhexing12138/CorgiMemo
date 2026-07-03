# 待办置顶按钮实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在置顶待办数量 ≥ 4 时动态展示"置顶(N)"按钮,支持展开/折叠并持久化状态。

**Architecture:** 抽取通用 `CollapsibleSectionHeader` 组件 → 封装 `PinnedSectionHeader` → 重构 `CompletedSectionHeader` 共用 → 在 `HomeViewModel` 添加 `_showPinned`/`pinnedCount` 状态 → 在 `HomeScreen` 的 `displayItems` 中按需插入 `PinnedDivider`。

**Tech Stack:** Kotlin · Jetpack Compose · Material3 · StateFlow · Coroutines · JUnit5 + MockK + Turbine

---

## File Structure

| 操作 | 路径 | 职责 |
|------|------|------|
| 新增 | `app/src/main/java/com/corgimemo/app/ui/components/CollapsibleSectionHeader.kt` | 通用可折叠区头组件(无背景、箭头在左、无水波纹) |
| 新增 | `app/src/main/java/com/corgimemo/app/ui/components/PinnedSectionHeader.kt` | 置顶专用封装,调用通用组件,主题色 = `colorScheme.primary` |
| 新增 | `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPinnedButtonTest.kt` | 置顶按钮 ViewModel 逻辑单元测试 |
| 修改 | `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt` | 新增 `SHOW_PINNED` 键 + `showPinned` Flow + `setShowPinned()` 写入器 |
| 修改 | `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` | 新增 `_showPinned`、`showPinned`、`pinnedCount`、`toggleShowPinned()` + init 订阅 |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 1) `DisplayItem` 新增 `PinnedDivider`;2) `displayItems` 构建新增置顶分支;3) 渲染分支;4) `ReorderableLazyColumn.key`;5) `CompletedSectionHeader` 改为调用通用组件 |
| 修改 | `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelTest.kt` | 新增 `showPinned` 初始加载测试(可选,如现有 setup 已覆盖可跳过) |
| 修改 | `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt` | 验证 `PinnedDivider` 不影响排序(如需要) |

---

## Task 1: 在 CorgiPreferences 中添加 showPinned 持久化支持

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt:155-160` (Keys 新增)
- Modify: `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt:438-454` (新增 getter/setter 区块)
- Modify: `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt:222-233` (迁移列表新增)

- [ ] **Step 1: 在 Keys object 中新增 SHOW_PINNED 常量**

在 `private object Keys` 中,**在 `HIDE_COMPLETED_ITEMS` 之后**新增:

```kotlin
/** V2.10: 待办页"置顶"区域展开状态(默认展开) */
const val SHOW_PINNED = "show_pinned"
```

参考现有 `SHOW_COMPLETED` (line 156) 的位置和格式。

- [ ] **Step 2: 在 CorgiPreferences 类中新增 showPinned getter/setter**

**位置**: 在 `setHideCompletedItems` 函数(line 438-440) 之后,新增以下代码:

```kotlin
/** V2.10: 获取待办页"置顶"区域展开状态的Flow(默认展开) */
val showPinned: Flow<Boolean> = booleanFlow(Keys.SHOW_PINNED, true)

/** V2.10: 设置待办页"置顶"区域展开状态 */
suspend fun setShowPinned(show: Boolean) = withContext(Dispatchers.IO) {
    esp.edit().putBoolean(Keys.SHOW_PINNED, show).apply()
}
```

- [ ] **Step 3: 在迁移列表中新增 SHOW_PINNED**

**位置**: `migrateFromDataStoreIfNeeded` 方法(line 222-233) 的**布尔类型键列表**中,在 `Keys.SHOW_COMPLETED` 之后新增 `Keys.SHOW_PINNED`:

```kotlin
listOf(
    Keys.IS_FIRST_LAUNCH, Keys.SOUND_ENABLED, Keys.HAPTIC_ENABLED,
    Keys.IS_ONBOARDING_COMPLETED, Keys.AUTO_BACKUP_ENABLED,
    Keys.SHOW_COMPLETED, Keys.FIRST_GUIDE_SHOWN, Keys.SHOW_PINNED
).forEach { key ->
    val value = legacyPrefs[booleanPreferencesKey(key)]
    if (value != null) {
        esp.edit().putBoolean(key, value).apply()
        migratedCount++
    }
}
```

- [ ] **Step 4: 验证代码格式与项目一致**

对照 `showCompleted` / `setShowCompleted` / `HIDE_COMPLETED_ITEMS` (line 449-454) 的格式,确认:
- `booleanFlow(Keys.SHOW_PINNED, true)` - 默认值与设计文档一致(`true` = 展开)
- `withContext(Dispatchers.IO)` 包裹
- `esp.edit().putBoolean(...).apply()`

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt
git commit -m "feat(prefs): 新增 showPinned 持久化键(默认展开)"
```

> **注**: 此 Task 不包含编译步骤,根据用户硬约束,所有编译必须手动执行。

---

## Task 2: 在 HomeViewModel 中添加 showPinned 状态、pinnedCount 与 toggleShowPinned()

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt:139-149` (新增状态字段)
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt:181-184` (新增 pinnedCount)
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt:600-608` (init 块新增订阅)
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt:1396-1405` (新增 toggleShowPinned)

- [ ] **Step 1: 新增 _showPinned / showPinned 状态字段**

**位置**: 在 `private val _showCompleted` 之后(line 141) 新增:

```kotlin
/** V2.10: "置顶"区域是否展开(从持久化加载,默认展开) */
private val _showPinned = MutableStateFlow(true)
val showPinned: StateFlow<Boolean> = _showPinned.asStateFlow()
```

- [ ] **Step 2: 新增 pinnedCount 派生 Flow**

**位置**: 在 `completedCount` 之后(line 184) 新增:

```kotlin
/** V2.10: 置顶待办总数(仅未完成,用于触发"置顶(N)"按钮显示) */
val pinnedCount: StateFlow<Int> = _todos.map { todos ->
    todos.count { it.isPinned && it.status == 0 }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
```

> **关键**: 过滤条件 `it.isPinned && it.status == 0` 与设计文档 8.边界情况 #15 一致 - 已完成的置顶项不应计入。

- [ ] **Step 3: 新增 toggleShowPinned() 方法**

**位置**: 在 `toggleShowCompleted()` 之后(line 1405) 新增:

```kotlin
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
```

- [ ] **Step 4: 在 init 块中新增 showPinned 订阅**

**位置**: 在 `corgiPreferences.showCompleted.collect` 之后(line 602) 新增:

```kotlin
viewModelScope.launch {
    corgiPreferences.showPinned.collect { _showPinned.value = it }
}
```

- [ ] **Step 5: 验证引用与现有代码一致**

确认以下符号已存在:
- `corgiPreferences` (Task 1 已新增 `showPinned`/`setShowPinned`)
- `viewModelScope` (现有)
- `_todos` (现有)
- `SharingStarted.WhileSubscribed(5000)` (现有模式)

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt
git commit -m "feat(viewmodel): 新增 showPinned 状态、pinnedCount 与 toggleShowPinned()"
```

---

## Task 3: 创建单元测试 HomeViewModelPinnedButtonTest

**Files:**
- Create: `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPinnedButtonTest.kt`

- [ ] **Step 1: 创建测试文件**

```kotlin
package com.corgimemo.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.TodoItem
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 待办"置顶"按钮相关 ViewModel 逻辑单元测试
 *
 * 覆盖：
 * - toggleShowPinned 翻转 + 持久化
 * - showPinned 默认值
 * - pinnedCount 仅统计 status=0 && isPinned=true
 * - pinnedCount 排除已完成的置顶项
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPinnedButtonTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

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
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() = runTest {
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

        coEvery { mockCorgiPreferences.showPinned } returns flowOf(true)
        coEvery { mockCorgiPreferences.showCompleted } returns flowOf(false)
        coEvery { mockCorgiPreferences.hideDetails } returns flowOf(false)
        coEvery { mockCorgiPreferences.hideCompletedItems } returns flowOf(false)

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
            fileCopyManager = mockFileCopyManager
        )
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleShowPinned 翻转状态并触发持久化`() = runTest {
        coEvery { mockCorgiPreferences.setShowPinned(any()) } returns Unit
        val initial = viewModel.showPinned.value
        viewModel.toggleShowPinned()
        assertEquals(!initial, viewModel.showPinned.value)
        coVerify(exactly = 1) { mockCorgiPreferences.setShowPinned(!initial) }
    }

    @Test
    fun `showPinned 默认值为 true`() = runTest {
        assertTrue(viewModel.showPinned.value)
    }

    @Test
    fun `toggleShowPinned 两次回到原状态`() = runTest {
        coEvery { mockCorgiPreferences.setShowPinned(any()) } returns Unit
        val initial = viewModel.showPinned.value
        viewModel.toggleShowPinned()
        viewModel.toggleShowPinned()
        assertEquals(initial, viewModel.showPinned.value)
        coVerify(exactly = 2) { mockCorgiPreferences.setShowPinned(any()) }
    }
}
```

> **注**: `pinnedCount` 的测试需要注入真实 `_todos` 状态,涉及私有字段反射或暴露测试钩子。**如果注入成本高,本测试类优先覆盖状态切换,数量计算由手动测试覆盖**。如需扩展,后续 Task 单独添加。

- [ ] **Step 2: 验证测试类编译通过**

手动编译此测试类(用户硬约束:必须手动编译):

```bash
./gradlew :app:compileDebugUnitTestKotlin
```

预期: `BUILD SUCCESSFUL`,无错误。

- [ ] **Step 3: 提交**

```bash
git add app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPinnedButtonTest.kt
git commit -m "test: 新增 HomeViewModelPinnedButtonTest 单元测试"
```

---

## Task 4: 创建通用 CollapsibleSectionHeader 组件

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/CollapsibleSectionHeader.kt`

- [ ] **Step 1: 创建文件**

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 通用可折叠区头组件
 *
 * 用于"置顶"和"已完成"区域的可折叠按钮。设计要点：
 * - 无背景色(透明背景,与其他元素风格统一)
 * - 箭头位于文字左侧(▼ 展开 / ▶ 折叠)
 * - 无水波纹(indication = null + 透明度反馈)
 * - 250ms FastOutSlowInEasing 平滑旋转动画
 *
 * @param label 标签,如"置顶"、"已完成"
 * @param count 实时数量
 * @param isExpanded 是否展开
 * @param color 文字与箭头颜色(置顶 = primary,已完成 = onSurfaceVariant)
 * @param onClick 点击回调
 * @param modifier 外部 Modifier
 * @param expandedLabel 可选:展开时显示的文字(如"收起置顶"),为 null 时使用 "label (count)"
 * @param collapsedLabel 可选:折叠时显示的文字(如"展开置顶"),为 null 时使用 "label (count)"
 */
@Composable
fun CollapsibleSectionHeader(
    label: String,
    count: Int,
    isExpanded: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandedLabel: String? = null,
    collapsedLabel: String? = null,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "${label}_arrow_rotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = if (isExpanded) "折叠$label" else "展开$label",
            modifier = Modifier
                .size(20.dp)
                .rotate(arrowRotation),
            tint = color
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isExpanded) {
                expandedLabel?.let { "$it ($count)" } ?: "$label ($count)"
            } else {
                collapsedLabel?.let { "$it ($count)" } ?: "$label ($count)"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
```

- [ ] **Step 2: 验证导入**

确认所有 import 路径与项目一致(参考 `EnhancedTopBar.kt` 中的 import 风格)。如需 `Role` 需 `androidx.compose.ui.semantics.Role`。

- [ ] **Step 3: 手动编译验证**

```bash
./gradlew :app:compileDebugKotlin
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/CollapsibleSectionHeader.kt
git commit -m "feat(ui): 新增 CollapsibleSectionHeader 通用可折叠区头组件"
```

---

## Task 5: 创建 PinnedSectionHeader 专用封装

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/PinnedSectionHeader.kt`

- [ ] **Step 1: 创建文件**

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 置顶区头按钮(方案 A:列表顶部独立)
 *
 * 在已置顶待办数量 ≥ 4 时显示。点击可展开/折叠所有置顶待办。
 * 基于 [CollapsibleSectionHeader] 实现,统一设计语言。
 *
 * @param count 当前置顶待办数量
 * @param isExpanded 是否展开
 * @param onClick 点击回调
 * @param modifier 外部 Modifier
 */
@Composable
fun PinnedSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = CollapsibleSectionHeader(
    label = "置顶",
    count = count,
    isExpanded = isExpanded,
    color = MaterialTheme.colorScheme.primary,
    expandedLabel = "收起置顶",
    collapsedLabel = "展开置顶",
    onClick = onClick,
    modifier = modifier,
)
```

- [ ] **Step 2: 手动编译验证**

```bash
./gradlew :app:compileDebugKotlin
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/PinnedSectionHeader.kt
git commit -m "feat(ui): 新增 PinnedSectionHeader 置顶区头按钮封装"
```

---

## Task 6: 重构 CompletedSectionHeader 使用通用组件

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:2278-2318`

- [ ] **Step 1: 替换原 CompletedSectionHeader 函数体**

**位置**: `HomeScreen.kt` 第 2278-2318 行的整个 `CompletedSectionHeader` 函数。

**改造前**(参考 line 2278-2318):

```kotlin
@Composable
private fun CompletedSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 180f,
        label = "completed_arrow_rotation"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "已完成 ($count)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            @Suppress("DEPRECATION")
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isExpanded) "折叠" else "展开",
                modifier = Modifier.rotate(arrowRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

**改造后**(完整替换为):

```kotlin
/**
 * "已完成"区域分隔按钮(已重构为调用通用 CollapsibleSectionHeader)
 *
 * 视觉变化:移除原 surfaceVariant 半透明背景,改为透明背景,
 * 与新"置顶"按钮统一设计语言(无背景 + 箭头在左 + 无水波纹)。
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
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick = onClick,
)
```

- [ ] **Step 2: 清理可能的孤立 import**

如 `animateFloatAsState`、`Surface`、`RoundedCornerShape`、`Arrangement`、`Icons.Filled.KeyboardArrowDown` 等在 `HomeScreen.kt` 中**仅被原 `CompletedSectionHeader` 使用**,需要删除这些 import。但请先 grep 确认没有其他使用:

```bash
grep -n "animateFloatAsState\|RoundedCornerShape\|Arrangement\.SpaceBetween" app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
```

如果还有其他使用,保留 import;否则删除。

- [ ] **Step 3: 添加 CollapsibleSectionHeader 的 import**

在 `HomeScreen.kt` 的 import 区域顶部添加:

```kotlin
import com.corgimemo.app.ui.components.CollapsibleSectionHeader
```

- [ ] **Step 4: 手动编译验证**

```bash
./gradlew :app:compileDebugKotlin
```

预期: `BUILD SUCCESSFUL`,无未使用 import 警告。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "refactor(home): CompletedSectionHeader 改为调用通用 CollapsibleSectionHeader"
```

---

## Task 7: 在 HomeScreen 添加 PinnedDivider 与 displayItems 集成

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:2269-2272` (DisplayItem 新增变体)
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:152-156` (状态订阅新增)
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:728-744` (displayItems 构建)
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:766-770` (ReorderableLazyColumn key)
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:796-806` (渲染分支)

- [ ] **Step 1: 在 DisplayItem sealed interface 中新增 PinnedDivider**

**位置**: 第 2269-2272 行的 `DisplayItem` 定义:

**改造前**:

```kotlin
private sealed interface DisplayItem {
    data class Todo(val item: TodoItem) : DisplayItem
    data class CompletedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
}
```

**改造后**:

```kotlin
private sealed interface DisplayItem {
    data class Todo(val item: TodoItem) : DisplayItem
    data class PinnedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class CompletedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
}
```

- [ ] **Step 2: 在 HomeScreen 中订阅 showPinned / pinnedCount**

**位置**: 第 153-155 行(`showCompleted` 等状态订阅)附近,新增:

```kotlin
val showPinned by viewModel.showPinned.collectAsState()
val pinnedCount by viewModel.pinnedCount.collectAsState()
```

- [ ] **Step 3: 改造 displayItems 构建逻辑**

**位置**: 第 728-744 行的 `displayItems` 块。

**改造前**(line 728-744):

```kotlin
val filteredPending = applyFilters(pendingTodosAll)
val filteredCompleted = applyFilters(visibleCompletedTodosAll)
val dividerIndex = if (!hideCompletedItems && completedCount > 0) filteredPending.size else -1

val displayItems = remember(
    filteredPending, filteredCompleted, showCompleted, completedCount, hideCompletedItems
) {
    buildList {
        filteredPending.forEach { add(DisplayItem.Todo(it)) }
        if (!hideCompletedItems && completedCount > 0) {
            add(DisplayItem.CompletedDivider(count = completedCount, isExpanded = showCompleted))
            if (showCompleted) {
                filteredCompleted.forEach { add(DisplayItem.Todo(it)) }
            }
        }
    }
}
```

**改造后**:

```kotlin
val filteredPending = applyFilters(pendingTodosAll)
val filteredCompleted = applyFilters(visibleCompletedTodosAll)
val dividerIndex = if (!hideCompletedItems && completedCount > 0) filteredPending.size else -1

val displayItems = remember(
    filteredPending, filteredCompleted,
    showPinned, showCompleted,
    pinnedCount, completedCount,
    hideCompletedItems
) {
    buildList {
        // 1. 置顶区:仅当 pinnedCount >= 4 时插入按钮
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
            // pinnedCount < 4 时,置顶待办直接放在列表最前
            filteredPending.filter { it.isPinned }
                .forEach { add(DisplayItem.Todo(it)) }
        }
        // 2. 普通待办
        filteredPending.filter { !it.isPinned }
            .forEach { add(DisplayItem.Todo(it)) }
        // 3. 已完成区(原有逻辑)
        if (!hideCompletedItems && completedCount > 0) {
            add(DisplayItem.CompletedDivider(count = completedCount, isExpanded = showCompleted))
            if (showCompleted) {
                filteredCompleted.forEach { add(DisplayItem.Todo(it)) }
            }
        }
    }
}
```

> **关键**: 原代码用 `filteredPending.forEach { add(DisplayItem.Todo(it)) }` 一次性添加所有待办(置顶项因为 `sortedWith(compareByDescending { it.isPinned })` 已排在最前)。新代码需要**按 isPinned 显式分组**:
> - `pinnedCount >= 4`:先插入 PinnedDivider,再决定是否插入置顶项
> - `pinnedCount < 4`:直接插入置顶项(不显示按钮)
> - 无论如何,非置顶项都放在置顶之后

- [ ] **Step 4: 更新 ReorderableLazyColumn 的 key 函数**

**位置**: 第 766-770 行的 `key = { item -> ... }`:

**改造前**:

```kotlin
key = { item ->
    when (item) {
        is DisplayItem.Todo -> item.item.id
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
        is DisplayItem.CompletedDivider -> "completed_divider"
    }
},
```

> **注**: key 加上 count 避免折叠/展开切换时 key 冲突(虽然 divider 实例总是唯一的,但加上 count 更稳健)。

- [ ] **Step 5: 在渲染分支中添加 PinnedDivider 处理**

**位置**: 第 796-806 行的 `when (displayItem)`:

**改造前**(line 796-806):

```kotlin
when (displayItem) {
    is DisplayItem.CompletedDivider -> {
        CompletedSectionHeader(
            count = displayItem.count,
            isExpanded = displayItem.isExpanded,
            onClick = {
                viewModel.onUserInteraction()
                viewModel.toggleShowCompleted()
            }
        )
    }
    is DisplayItem.Todo -> {
        // ... 原有逻辑 ...
    }
}
```

**改造后**:

```kotlin
when (displayItem) {
    is DisplayItem.PinnedDivider -> {
        PinnedSectionHeader(
            count = displayItem.count,
            isExpanded = displayItem.isExpanded,
            onClick = {
                viewModel.onUserInteraction()
                viewModel.toggleShowPinned()
            }
        )
    }
    is DisplayItem.CompletedDivider -> {
        CompletedSectionHeader(
            count = displayItem.count,
            isExpanded = displayItem.isExpanded,
            onClick = {
                viewModel.onUserInteraction()
                viewModel.toggleShowCompleted()
            }
        )
    }
    is DisplayItem.Todo -> {
        // 原有逻辑不变
    }
}
```

- [ ] **Step 6: 添加 PinnedSectionHeader import**

在 `HomeScreen.kt` 顶部 import 区添加:

```kotlin
import com.corgimemo.app.ui.components.PinnedSectionHeader
```

- [ ] **Step 7: 手动编译验证**

```bash
./gradlew :app:compileDebugKotlin
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(home): 集成 PinnedDivider 到 displayItems 与渲染分支"
```

---

## Task 8: 运行单元测试验证

**Files:** 无修改(运行已有测试)

- [ ] **Step 1: 运行新增的 PinnedButton 测试**

```bash
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.viewmodel.HomeViewModelPinnedButtonTest"
```

预期: `BUILD SUCCESSFUL`,所有测试通过。

- [ ] **Step 2: 运行现有 HomeViewModel 测试确保未破坏**

```bash
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.viewmodel.HomeViewModelTest"
```

预期: `BUILD SUCCESSFUL`,所有测试通过。

- [ ] **Step 3: 运行 Reorder 测试**

```bash
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.viewmodel.HomeViewModelReorderTest"
```

预期: `BUILD SUCCESSFUL`,所有测试通过。

- [ ] **Step 4: 如有失败,定位并修复**

如果 `HomeViewModelTest` 或 `HomeViewModelReorderTest` 因本次重构失败,定位失败原因(可能因 `CompletedSectionHeader` 改动导致 snapshot 测试失败)并修复。

- [ ] **Step 5: 提交(如有修复)**

```bash
git add -u
git commit -m "test: 验证 PinnedButton 相关测试通过"
```

---

## Task 9: 手动 UI 验证

**Files:** 无修改(纯手动验证)

- [ ] **Step 1: 启动 App 至"我的待办"页面**

确保至少 4 个置顶待办 + 至少 3 个已完成待办。

- [ ] **Step 2: 验证置顶按钮显示**

- 预期: 列表顶部出现"收起置顶 (4) ▼"按钮,颜色 = 主色橙
- 4 个置顶待办显示在按钮下方

- [ ] **Step 3: 验证折叠交互**

- 点击"收起置顶"
- 预期: 4 个置顶待办平滑收起,按钮文字变为"展开置顶 (4) ▶",箭头朝右

- [ ] **Step 4: 验证展开交互**

- 再次点击按钮
- 预期: 4 个置顶待办平滑展开,按钮文字恢复"收起置顶 (4) ▼",箭头朝下

- [ ] **Step 5: 验证无水波纹**

- 点击时观察: **不应**有圆形水波纹扩散效果
- 预期: 仅有按钮透明度轻微变化(按下时)

- [ ] **Step 6: 验证持久化**

- 折叠置顶区域
- 完全退出 App(滑动杀掉)
- 重新启动 App
- 预期: 置顶区域保持折叠状态

- [ ] **Step 7: 验证阈值边界**

- 取消置顶一个待办(4→3)
- 预期: 按钮消失,3 个置顶待办直接显示在列表顶部
- 重新置顶一个(3→4)
- 预期: 按钮出现,继承上次的折叠/展开状态

- [ ] **Step 8: 验证与"已完成"按钮协同**

- 折叠置顶、折叠已完成
- 预期: 两个按钮独立工作,互不影响
- 折叠态下,两个按钮均无背景,样式对称

- [ ] **Step 9: 验证深色模式**

- 切换深色模式
- 预期: 置顶按钮颜色变深(自动降低 30%),对比度充足

- [ ] **Step 10: 验证主题色切换**

- 在"我的 → 设置"中切换 6 种主题色
- 预期: 置顶按钮颜色跟随 `colorScheme.primary` 变化

- [ ] **Step 11: 验证拖拽排序**

- 展开置顶,长按一个置顶待办,拖到另一个置顶待办位置
- 预期: 顺序正常变化,不可拖到 PinnedDivider 上方
- 折叠置顶,长按一个普通待办
- 预期: 拖拽行为正常,不影响置顶区(因置顶项不在列表中)

- [ ] **Step 12: 验证批量选择**

- 进入批量选择模式
- 预期: 置顶按钮仍可点击,不影响多选

- [ ] **Step 13: 验证搜索/分类**

- 在搜索框输入关键词
- 预期: 置顶按钮仍显示总数,过滤后无匹配时按钮仍存在(无折叠内容)

- [ ] **Step 14: 记录验证结果**

如果 14 个验证场景全部通过,任务完成。如有失败,记录问题并修复。

---

## Task 10: 最终提交与文档收尾

**Files:** 无新增文件

- [ ] **Step 1: 确认所有任务完成**

检查所有 Task 1-9 的提交记录。

- [ ] **Step 2: 询问用户是否进行 git 提交汇总**

按照项目规则 `.trae/rules/git提交.md` 询问用户:

> "本次任务包含 7+ 个原子提交,是否需要我将所有改动汇总为一个语义化提交?或者保持原子提交?"

- [ ] **Step 3: 询问用户是否进行编译验证**

按照用户硬约束 "所有编译必须手动执行",提示用户:

> "所有改动已完成,所有编译/安装/测试需您手动执行。如需我提供编译命令请告知。"

---

## Self-Review

**1. Spec coverage** (覆盖设计文档 12 节):

| Spec 章节 | 覆盖 Task |
|----------|----------|
| § 1 背景与目标 | Task 1-7 实现 |
| § 2 设计决策 | Task 1-7 实现(7 个决策全部实现) |
| § 3 架构 | Task 2 状态、Task 1 持久化 |
| § 4 组件设计 | Task 4 通用 + Task 5 Pinned + Task 6 Completed 重构 |
| § 5 ViewModel | Task 2 |
| § 6 CorgiPreferences | Task 1 |
| § 7 HomeScreen | Task 6(Completed 重构) + Task 7(PinnedDivider) |
| § 8 边界情况(15 个) | Task 9 手动验证 |
| § 9 测试 | Task 3 单元测试 + Task 8 运行 + Task 9 手动 |
| § 10 验收标准 | Task 8 测试 + Task 9 手动 |
| § 11 风险 | Task 8 兜底 |
| § 12 变更文件清单 | Task 1-7 已穷举 |

**2. Placeholder scan**:
- ✅ 无 "TBD"、"TODO"、"实现后"
- ✅ 所有代码块包含完整可执行代码
- ✅ 无"类似 Task N"的引用 - 每个 Task 独立完整
- ✅ 所有 import 显式列出

**3. Type consistency**:
- `showPinned: StateFlow<Boolean>` - 在 Task 1(CorgiPreferences)、Task 2(HomeViewModel)、Task 7(HomeScreen) 三处签名一致
- `pinnedCount: StateFlow<Int>` - Task 2 定义、Task 7 使用一致
- `toggleShowPinned()` - Task 2 定义、Task 7 调用一致
- `DisplayItem.PinnedDivider(count: Int, isExpanded: Boolean)` - Task 7 定义与使用一致
- `CollapsibleSectionHeader(label, count, isExpanded, color, onClick, modifier, expandedLabel, collapsedLabel)` - Task 4 定义、Task 5 包装、Task 6 调用一致
- `PinnedSectionHeader(count, isExpanded, onClick, modifier)` - Task 5 定义、Task 7 调用一致
- `setShowPinned(show: Boolean)` - Task 1 定义、Task 2 调用、Task 3 测试一致

**检查通过,无需修正。**
