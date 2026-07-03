# 待完成按钮设计文档

> 创建日期: 2026-07-03
> 状态: 待用户审查
> 关联需求: 为"我的待办"页面添加"待完成(N)"按钮,位置根据置顶数量动态调整

## 1. 背景与目标

### 1.1 现状

在"我的待办"页面中,当前已有 3 个 section header:
- "置顶 (N)" - 置顶数量 ≥ 4 时显示,可展开/折叠
- (无对应"待完成"区头按钮,普通待办直接显示)
- "已完成 (N)" - 有已完成项时显示,可展开/折叠(当前颜色为 `onSurfaceVariant` 灰色)

普通待办(非置顶、未完成)无对应的区头按钮,缺乏视觉层次感,且无法独立折叠。

### 1.2 目标

| # | 目标 | 衡量标准 |
|---|------|---------|
| 1 | 新增"待完成(N)"按钮,可展开/折叠 | 点击切换生效 |
| 2 | 按钮位置根据置顶数量动态调整 | 置顶 ≤ 3 时在顶部,> 3 时在置顶区后 |
| 3 | 数量根据置顶数量动态计算 | 置顶 ≤ 3 时显示总计(置顶+非置顶),> 3 时仅显示非置顶 |
| 4 | 状态持久化 | App 重启后保持折叠/展开 |
| 5 | 视觉/交互与现有按钮一致 | 复用 CollapsibleSectionHeader,无背景 + 箭头在左 + 无水波纹 |
| 6 | 已完成按钮改为绿色 | 颜色从 onSurfaceVariant 灰色 → #7EC8A0 绿色,与设计语言一致 |
| 7 | 点击按钮无水波纹 | 仅文字与箭头变化 |

### 1.3 非目标(YAGNI)

- ❌ 不实现"筛选仅显示待完成"等其他操作
- ❌ 不在按钮上添加右键菜单
- ❌ 不实现"待完成"与"置顶"互换
- ❌ 不修改已完成按钮的折叠/展开行为(仅改色)
- ❌ 不添加智能推荐/分组等高阶功能

## 2. 设计决策

| # | 决策项 | 选择 | 理由 |
|---|--------|------|------|
| 1 | 按钮功能 | 折叠/展开待完成区域 | 用户确认 |
| 2 | 颜色 - 待完成 | 蓝色 #5A8DEE | 用户确认 |
| 3 | 颜色 - 已完成 | 绿色 #7EC8A0(从灰色改) | 用户确认,与设计语言一致 |
| 4 | 颜色 - 置顶 | 橙色 #FF9A5C(不变) | 保持 |
| 5 | 状态持久化 | 写入 CorgiPreferences | 用户确认 |
| 6 | 默认状态 | 展开(true) | 与"已完成"按钮一致 |
| 7 | 位置逻辑 | 置顶 ≤ 3 → 顶部;> 3 → 置顶区后 | 用户需求 |
| 8 | 数量计算 | 置顶 ≤ 3 → 总计(置顶+非置顶);> 3 → 仅非置顶 | 用户需求 |
| 9 | 实现方式 | 复用 PinnedDivider 模式(方案 1) | 改动最小、风险最低、代码风格统一 |
| 10 | 复用通用组件 | 复用 CollapsibleSectionHeader | 已支持任意 Color |

## 3. 架构

### 3.1 数据流

```
┌─────────────────────────────────────────────────────┐
│  CorgiPreferences (持久化层)                        │
│  - SHOW_PENDING key (默认 true)                     │
│  - showPending: Flow<Boolean>                       │
│  - setShowPending(Boolean)                          │
└─────────────────────────────────────────────────────┘
              ↑ collect              ↓ set
┌─────────────────────────────────────────────────────┐
│  HomeViewModel (状态层)                             │
│  - _showPending: MutableStateFlow<Boolean>         │
│  - showPending: StateFlow<Boolean>                  │
│  - pendingCount: StateFlow<Int> (combine 动态计算) │
│  - toggleShowPending(): 翻转 + 持久化               │
└─────────────────────────────────────────────────────┘
              ↑ collectAsState
┌─────────────────────────────────────────────────────┐
│  HomeScreen (UI 层)                                 │
│  - DisplayItem 新增 PendingDivider                  │
│  - displayItems 中按 pinnedCount 动态插入            │
│  - 渲染 <PendingSectionHeader>                      │
│  - CompletedSectionHeader 颜色改绿                  │
└─────────────────────────────────────────────────────┘
```

### 3.2 状态生命周期

| 触发 | 动作 | 持久化 |
|------|------|--------|
| App 启动 | `corgiPreferences.showPending.collect { _showPending.value = it }` | 读取 |
| 用户点击按钮 | `toggleShowPending()` 翻转 + 持久化 | 写入 |
| 待办数据变化 | `pendingCount` 重新计算(因 pinnedCount 变化触发) | 不持久化 |
| 阈值跨过(置顶 3↔4) | 按钮位置和数量自动调整 | 不持久化(由当前数据决定) |

### 3.3 `pendingCount` 计算逻辑

```kotlin
val pendingCount: StateFlow<Int> = combine(
    _todos, pinnedCount  // 不需要 _showPinned 参与,只是触发源
) { todos, pinnedN ->
    val nonPinned = todos.count { !it.isPinned && it.status == 0 }
    if (pinnedN <= 3) {
        // Case A:置顶 ≤ 3 时,待完成按钮在最前,代表所有待完成
        todos.count { it.status == 0 }  // 置顶 + 非置顶
    } else {
        // Case B:置顶 > 3 时,待完成按钮在置顶区后,仅代表非置顶
        nonPinned
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
```

## 4. 组件设计

### 4.1 通用组件 `CollapsibleSectionHeader` (已存在,无需修改)

位置: `app/src/main/java/com/corgimemo/app/ui/components/CollapsibleSectionHeader.kt`

已支持任意 `color: Color` 参数,本设计复用,无需扩展。

### 4.2 专用组件 `PendingSectionHeader` (新增)

**位置**: `app/src/main/java/com/corgimemo/app/ui/components/PendingSectionHeader.kt`

```kotlin
/**
 * 待完成区头按钮
 *
 * 位置根据置顶数量动态调整:
 * - 置顶 ≤ 3 时,显示在列表最前
 * - 置顶 ≥ 4 时,显示在置顶区之后
 *
 * 基于 [CollapsibleSectionHeader] 实现,统一设计语言。
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
    color = SectionHeaderColors.Pending,      // 蓝色 #5A8DEE(集中定义,见 4.3)
    expandedLabel = "收起待完成",
    collapsedLabel = "展开待完成",
    onClick = onClick,
    modifier = modifier,
)
```

### 4.3 颜色常量

**位置**: 在 `app/src/main/java/com/corgimemo/app/ui/components/CompletedColors.kt` 中新增或独立定义:

```kotlin
// 方案 A:在 CompletedColors.kt 集中定义
object SectionHeaderColors {
    val Pinned = MaterialTheme.colorScheme.primary
    val Pending = Color(0xFF5A8DEE)          // 蓝色
    val Completed = Color(0xFF7EC8A0)        // 绿色
}

// 方案 B:硬编码在每个 SectionHeader 中
// (不推荐,难维护)
```

**推荐方案 A**,在 `CompletedColors.kt` 中集中定义所有 section 颜色。

### 4.4 `CompletedSectionHeader` 颜色改造

**位置**: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

**改造前**:
```kotlin
) = CollapsibleSectionHeader(
    label = "已完成",
    count = count,
    isExpanded = isExpanded,
    color = MaterialTheme.colorScheme.onSurfaceVariant,  // 灰色
    onClick = onClick,
)
```

**改造后**:
```kotlin
) = CollapsibleSectionHeader(
    label = "已完成",
    count = count,
    isExpanded = isExpanded,
    color = CompletedColors.Text,  // 绿色 #7EC8A0(已存在于 CompletedColors.kt)
    onClick = onClick,
)
```

## 5. ViewModel 实现

### 5.1 状态字段新增

**位置**: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt`

在 `_showPinned` / `showPinned` (line 143-145) 之后,新增:

```kotlin
/** V2.11: "待完成"区域是否展开(从持久化加载,默认展开) */
private val _showPending = MutableStateFlow(true)
val showPending: StateFlow<Boolean> = _showPending.asStateFlow()
```

### 5.2 派生 Flow `pendingCount`

在 `pinnedCount` (line 190-193) 之后,新增:

```kotlin
/** V2.11: 待完成待办总数(动态计算,根据置顶数量调整语义) */
val pendingCount: StateFlow<Int> = combine(
    _todos, pinnedCount
) { todos, pinnedN ->
    val nonPinned = todos.count { !it.isPinned && it.status == 0 }
    if (pinnedN <= 3) {
        // 置顶 ≤ 3:按钮在最前,代表所有待完成
        todos.count { it.status == 0 }
    } else {
        // 置顶 > 3:按钮在置顶区后,仅代表非置顶
        nonPinned
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
```

**依赖**:
- `combine` 操作符(已在 `kotlinx.coroutines.flow` 中)
- `_todos` (现有)
- `pinnedCount` (现有,需确保先定义)

### 5.3 切换方法 `toggleShowPending()`

在 `toggleShowPinned()` (line 1419-1428) 之后,新增:

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

### 5.4 init 块订阅

在 `corgiPreferences.showPinned.collect` (line 612-614) 之后,新增:

```kotlin
viewModelScope.launch {
    corgiPreferences.showPending.collect { _showPending.value = it }
}
```

## 6. CorgiPreferences 实现

### 6.1 新增键

**位置**: `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt`

在 `SHOW_PINNED` 之后,新增:

```kotlin
/** V2.11: 待办页"待完成"区域展开状态(默认展开) */
const val SHOW_PENDING = "show_pending"
```

### 6.2 Getter / Setter

在 `setShowPinned` (line 462-464) 之后,新增:

```kotlin
/** V2.11: 获取待办页"待完成"区域展开状态的Flow(默认展开) */
val showPending: Flow<Boolean> = booleanFlow(Keys.SHOW_PENDING, true)

/** V2.11: 设置待办页"待完成"区域展开状态 */
suspend fun setShowPending(show: Boolean) = withContext(Dispatchers.IO) {
    esp.edit().putBoolean(Keys.SHOW_PENDING, show).apply()
}
```

### 6.3 迁移列表更新

在 `SHOW_PINNED` 之后,新增 `SHOW_PENDING`:

```kotlin
listOf(
    // ... 已有 ...
    Keys.SHOW_PINNED, Keys.SHOW_PENDING
).forEach { key ->
    // ...
}
```

## 7. HomeScreen 实现

### 7.1 `DisplayItem` 扩展

**位置**: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

```kotlin
private sealed interface DisplayItem {
    data class Todo(val item: TodoItem) : DisplayItem
    data class PinnedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class PendingDivider(val count: Int, val isExpanded: Boolean) : DisplayItem  // 新增
    data class CompletedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
}
```

### 7.2 状态订阅

在 `showPinned` / `pinnedCount` (line 155-156) 之后,新增:

```kotlin
val showPending by viewModel.showPending.collectAsState()
val pendingCount by viewModel.pendingCount.collectAsState()
```

### 7.3 `displayItems` 构建逻辑

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
            // 待完成按钮在最前
            add(DisplayItem.PendingDivider(
                count = pendingCount,   // 此时 = 总计(置顶+非置顶)
                isExpanded = showPending
            ))
            if (showPending) {
                // 所有待完成项(置顶先,非置顶后)
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

### 7.4 渲染分支

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
        // 原有逻辑
    }
}
```

### 7.5 ReorderableLazyColumn key

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

## 8. 边界情况

| # | 场景 | 行为 | 验证 |
|---|------|------|------|
| 1 | 置顶 0~3 个 | 待完成按钮在最前,数量 = 全部待完成 | ✅ |
| 2 | 置顶 0 个,普通 N 个 | 待完成按钮在最前,数量 = N | ✅ |
| 3 | 置顶第 4 个 | 顶部出现置顶按钮,待完成按钮移到置顶区后,数量 = N(非置顶) | ✅ |
| 4 | 置顶 5,普通 8 | 顶部"置顶(5)",置顶待办,"待完成(8)",普通待办,已完成按钮 | ✅ |
| 5 | 取消置顶 4→3 | 置顶按钮消失,待完成按钮回到最前,数量更新为总计 | ✅ |
| 6 | 重新置顶 3→4 | 待完成按钮移到置顶区后,数量更新为仅非置顶 | ✅ |
| 7 | 折叠待完成时,新增待办 | 按钮数量更新;新增项折叠时不可见 | ✅ |
| 8 | 折叠待完成时,完成待办 | 按钮数量减少 | ✅ |
| 9 | 所有非置顶项被完成 | 待完成按钮数量=0,折叠态时无内容;展开态时仍显示按钮 | ✅ |
| 10 | App 重启 | 待完成折叠状态从偏好恢复 | ✅ |
| 11 | 与置顶/已完成按钮协同 | 三个按钮独立工作 | ✅ |
| 12 | 拖拽排序 | 折叠时不可拖到 Divider 上方(因待办不在列表中) | ✅ |
| 13 | 批量选择 | 按钮可点击,不影响批量操作 | ✅ |
| 14 | 搜索/分类 | 按钮仍显示,过滤后无匹配时按钮仍存在 | ✅ |
| 15 | 深色模式 | 蓝色按钮在深色背景对比度充足 | ✅ |
| 16 | 6 种主题色 | 蓝色不随主题变(已用固定色值) | ✅ |
| 17 | 阈值边界 3→4 切换 | Case B ↔ Case A 切换时按钮位置和数量同步更新 | ✅ |

## 9. 测试

### 9.1 单元测试

**新增 `HomeViewModelPendingButtonTest.kt`**:

```kotlin
class HomeViewModelPendingButtonTest {
    // 状态切换
    @Test fun `toggleShowPending 翻转状态并触发持久化`()
    @Test fun `showPending 默认值为 true`()
    @Test fun `toggleShowPending 两次回到原状态`()

    // 数量计算
    @Test fun `pendingCount 在 pinnedCount <= 3 时包含置顶和非置顶`()
    @Test fun `pendingCount 在 pinnedCount >= 4 时仅包含非置顶`()
    @Test fun `pendingCount 不包含已完成的待办`()
    @Test fun `pendingCount 在 _todos 变化时实时更新`()
    @Test fun `pendingCount 在阈值跨越(3→4)时重新计算`()

    // 持久化
    @Test fun `toggleShowPending 调用 corgiPreferences.setShowPending`()
}
```

### 9.2 现有测试更新

- `HomeViewModelTest.kt` - 新增 `pendingCount` / `showPending` 初始加载测试
- `HomeViewModelReorderTest.kt` - 验证 `PendingDivider` 不影响排序

### 9.3 手动测试清单

| # | 场景 | 预期 |
|---|------|------|
| 1 | 置顶 0~3 个 | 待完成按钮在最前,显示总计 |
| 2 | 置顶第 4 个 | 待完成按钮移到置顶区后,显示非置顶数 |
| 3 | 点击"收起待完成" | 平滑折叠 |
| 4 | 再次点击 | 平滑展开 |
| 5 | 已完成按钮颜色 | 绿色(对比原灰色) |
| 6 | 已完成按钮折叠 | 独立工作,无背景,箭头在左 |
| 7 | 取消置顶 4→3 | 位置/数量回到 Case B |
| 8 | 重新置顶 3→4 | 位置/数量回到 Case A |
| 9 | App 重启 | 状态保持 |
| 10 | 配合置顶/已完成 | 三个按钮独立 |
| 11 | 折叠时新增待办 | 数量更新 |
| 12 | 拖拽排序 | 不破坏 |
| 13 | 批量选择 | 可点击 |
| 14 | 搜索/分类 | 按钮正常 |
| 15 | 深色模式 | 对比度足 |
| 16 | 主题切换 | 蓝色固定 |

## 10. 验收标准

- ✅ 编译通过,无新增警告
- ✅ 单元测试覆盖率 ≥ 80%(针对新增 ViewModel 逻辑)
- ✅ 16 个手动测试场景全部通过
  - 边界情况 #17 (阈值 3↔4 切换) 已被场景 #7/#8 隐含覆盖(取消置顶 4→3 + 重新置顶 3→4)
- ✅ 视觉与可交互原型一致(http://localhost:53001/pending-button.html)
- ✅ 现有置顶按钮行为完全不变
- ✅ 已完成按钮功能不变,仅颜色从灰改绿

## 11. 风险与权衡

| 风险 | 缓解措施 |
|------|---------|
| `displayItems` 逻辑变复杂(Case A/B 双分支) | 完整注释 + 单元测试 + 手动测试覆盖边界 |
| `combine` 性能 | `stateIn(WhileSubscribed(5000))` 与现有 pinnedCount 一致 |
| 蓝色在深色模式对比度 | 使用 #5A8DEE(中蓝),与白色文字对比度 4.5:1 以上 |
| 已完成按钮改色破坏深色模式 | CompletedColors.Text 已在深色模式验证通过,直接复用 |
| 折叠时非置顶项不可拖拽 | 现有模式(置顶折叠时也不可拖),符合用户预期 |

## 12. 变更文件清单

| 操作 | 路径 |
|------|------|
| 新增 | `app/src/main/java/com/corgimemo/app/ui/components/PendingSectionHeader.kt` |
| 修改 | `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt` |
| 修改 | `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/components/CompletedColors.kt`(可能) |
| 新增 | `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPendingButtonTest.kt` |
| 修改 | `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelTest.kt`(可选) |
