# 待办置顶按钮设计文档

> 创建日期: 2026-07-03
> 状态: 待用户审查
> 关联需求: 当已置顶待办数量 ≥ 4 时,动态展示"置顶(N)"按钮,可展开/折叠所有置顶待办

## 1. 背景与目标

### 1.1 现状

在"我的待办"页面中,当用户置顶待办后,所有置顶的待办会始终显示在列表最上方(`isPinned DESC, sortOrder ASC, createdAt DESC`)。当置顶数量较多时,会持续占用首屏空间,用户无法快速隐藏。

### 1.2 目标

| # | 目标 | 衡量标准 |
|---|------|---------|
| 1 | 置顶数量 ≥ 4 时显示"置顶(N)"按钮 | 满足阈值时立即出现 |
| 2 | 按钮可展开/折叠所有置顶待办 | 点击后实时切换 |
| 3 | 折叠/展开状态持久化 | 重启 App 后状态保持 |
| 4 | 视觉/交互与"已完成"按钮保持一致 | 同一设计语言 |
| 5 | 点击按钮无水波纹效果 | 仅文字与箭头变化 |

### 1.3 非目标(YAGNI)

- ❌ 不支持按"高/中/低优先级"分组折叠
- ❌ 不实现"折叠置顶"和"折叠已完成"的快捷开关
- ❌ 不在按钮上添加右键菜单(批量操作/移动分类等)
- ❌ 不修改置顶/取消置顶的交互方式

## 2. 设计决策

| # | 决策项 | 选择 | 理由 |
|---|--------|------|------|
| 1 | 按钮位置 | 方案 A:列表顶部独立 | 用户确认,与已完成按钮解耦,语义清晰 |
| 2 | 视觉样式 | 无背景 + 箭头在左 | 极简设计,避免视觉噪声 |
| 3 | 状态持久化 | 写入 CorgiPreferences | 与"已完成"按钮一致,符合项目规范 |
| 4 | 初始默认状态 | 展开(`true`) | 置顶是高优先级内容,应优先展示 |
| 5 | 触发阈值 | `pinnedCount >= 4` | 用户原始需求 |
| 6 | 实现方式 | 方案 2:通用 `CollapsibleSectionHeader` | 代码复用,设计语言统一 |
| 7 | 颜色 | 置顶用主色橙 `#FF9A5C`,已完成用绿 `#7EC8A0` | 与现有"已完成"颜色一致 |

## 3. 架构

### 3.1 数据流

```
┌─────────────────────────────────────────────────────┐
│  CorgiPreferences (持久化层)                        │
│  - SHOW_PINNED key (默认 true)                      │
│  - showPinned: Flow<Boolean>                        │
│  - setShowPinned(Boolean)                           │
└─────────────────────────────────────────────────────┘
              ↑ collect              ↓ set
┌─────────────────────────────────────────────────────┐
│  HomeViewModel (状态层)                             │
│  - _showPinned: MutableStateFlow<Boolean>           │
│  - showPinned: StateFlow<Boolean>                   │
│  - pinnedCount: StateFlow<Int>                      │
│  - toggleShowPinned(): suspend 翻转 + 持久化        │
└─────────────────────────────────────────────────────┘
              ↑ collectAsState
┌─────────────────────────────────────────────────────┐
│  HomeScreen (UI 层)                                 │
│  - displayItems 列表中按需插入 PinnedDivider         │
│  - 渲染 <PinnedSectionHeader> 组件                  │
└─────────────────────────────────────────────────────┘
```

### 3.2 状态生命周期

| 触发 | 动作 | 持久化 |
|------|------|--------|
| App 启动 | `viewModelScope.launch { corgiPreferences.showPinned.collect { _showPinned.value = it } }` | 读取 |
| 用户点击按钮 | `toggleShowPinned()` 翻转 + `corgiPreferences.setShowPinned()` | 写入 |
| 置顶数量变化 | `pinnedCount` 重新计算 | 不持久化(只持久化折叠状态) |
| 阈值跨过(3↔4) | 按钮自动出现/消失 | 不持久化(由当前数据决定) |

## 4. 组件设计

### 4.1 通用组件 `CollapsibleSectionHeader`

**位置**: `app/src/main/java/com/corgimemo/app/ui/components/CollapsibleSectionHeader.kt`

**签名**:

```kotlin
@Composable
fun CollapsibleSectionHeader(
    label: String,                       // "置顶" / "已完成"
    count: Int,                          // 实时数量
    isExpanded: Boolean,                 // 展开/折叠状态
    color: Color,                        // 主题色
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandedLabel: String? = null,       // 可选:展开时标签
    collapsedLabel: String? = null,      // 可选:折叠时标签
)
```

**渲染逻辑**:

| 状态 | 文字 | 箭头 |
|------|------|------|
| `isExpanded == true` | `expandedLabel ?: "$label ($count)"` | `▼` |
| `isExpanded == false` | `collapsedLabel ?: "$label ($count)"` | `▶`(实际是 `▼` 旋转 -90°) |

**关键实现**:

```kotlin
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
            indication = null,                     // 禁用默认水波纹
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
```

**无水波纹要点**:

- `clickable(indication = null, interactionSource = remember { MutableInteractionSource() })`
- 反馈依靠:箭头旋转 + 文字变化 + 短暂透明度(`Modifier.alpha(if (pressed) 0.6f else 1f)`)

### 4.2 专用封装 `PinnedSectionHeader`

**位置**: `app/src/main/java/com/corgimemo/app/ui/components/PinnedSectionHeader.kt`

```kotlin
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

### 4.3 `CompletedSectionHeader` 重构

**位置**: `HomeScreen.kt`(原内联,改为调用通用组件)

**改造前**: 直接实现 `Surface` + `Row` + `Text` + `Icon`(带箭头旋转)

**改造后**:

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
    // 不传 expandedLabel/collapsedLabel,保持原有"已完成 (N)"格式
)
```

**视觉差异**: 原"已完成"按钮有 `surfaceVariant` 半透明背景 + 圆角;改造后背景透明,与其他元素风格统一(已与用户确认:两个按钮均无背景)。

> **注**: 项目规则要求"参考已完成按钮",但您明确要求"去除背景色"。如果"已完成"按钮保留原背景而"置顶"按钮无背景,会破坏对称性。**建议两个按钮统一为无背景**(本设计采用此方案)。

## 5. ViewModel 实现

### 5.1 `HomeViewModel.kt` 新增

```kotlin
// 状态字段(放在 _showCompleted 附近)
private val _showPinned = MutableStateFlow(true)
val showPinned: StateFlow<Boolean> = _showPinned.asStateFlow()

// 数量计算(放在 completedCount 附近)
val pinnedCount: StateFlow<Int> = _todos.map { todos ->
    todos.count { it.isPinned && it.status == 0 }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

// 切换方法(放在 toggleShowCompleted 附近)
fun toggleShowPinned() {
    val newVal = !_showPinned.value
    _showPinned.value = newVal
    viewModelScope.launch {
        corgiPreferences.setShowPinned(newVal)
    }
}
```

### 5.2 init 块订阅

```kotlin
init {
    // ... 已有代码 ...
    viewModelScope.launch {
        corgiPreferences.showPinned.collect { _showPinned.value = it }
    }
}
```

## 6. CorgiPreferences 实现

### 6.1 新增键

```kotlin
private object Keys {
    // ... 已有 ...
    /** V2.10: 待办页"置顶"区域展开状态(默认展开) */
    const val SHOW_PINNED = "show_pinned"
}
```

### 6.2 Getter / Setter

```kotlin
/** V2.10: 获取待办页"置顶"区域展开状态的Flow(默认展开) */
val showPinned: Flow<Boolean> = booleanFlow(Keys.SHOW_PINNED, true)

suspend fun setShowPinned(show: Boolean) = withContext(Dispatchers.IO) {
    esp.edit().putBoolean(Keys.SHOW_PINNED, show).apply()
}
```

### 6.3 迁移列表更新

`migrateFromDataStoreIfNeeded` 中的布尔类型键列表新增 `Keys.SHOW_PINNED`,确保从旧 DataStore 升级时不丢失设置(虽然新功能首次安装时此键不存在,但保留迁移能力以防未来需求变化)。

## 7. HomeScreen 实现

### 7.1 `DisplayItem` 扩展

```kotlin
private sealed interface DisplayItem {
    data class Todo(val item: TodoItem) : DisplayItem
    data class PinnedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class CompletedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
}
```

### 7.2 状态订阅

```kotlin
val showPinned by viewModel.showPinned.collectAsState()
val pinnedCount by viewModel.pinnedCount.collectAsState()
```

### 7.3 `displayItems` 构建

```kotlin
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

### 7.5 `ReorderableLazyColumn` 集成

| 项 | 处理 |
|----|------|
| `key` | `is DisplayItem.PinnedDivider -> "pinned_divider"`,其他不变 |
| `isDraggable` | `it is DisplayItem.Todo`(原逻辑已正确) |
| `isPinned` | `(it as? DisplayItem.Todo)?.item?.isPinned ?: false`(原逻辑已正确) |

> 关键: 折叠时(`showPinned == false`),置顶项不出现在 `displayItems` 中,所以拖拽只能在普通待办之间进行,符合"置顶不可降级"规则。

## 8. 边界情况

| # | 场景 | 行为 | 验证 |
|---|------|------|------|
| 1 | 置顶 0~3 个 | 按钮不出现,置顶项直接显示在列表顶部 | ✅ |
| 2 | 置顶第 4 个 | 按钮动态出现,默认展开,显示所有置顶 | ✅ |
| 3 | 点击"收起置顶" | 按钮文字变"展开置顶(4) ▶",置顶项平滑收起 | ✅ |
| 4 | 再次点击 | 按钮恢复"收起置顶(4) ▼",置顶项平滑展开 | ✅ |
| 5 | 取消置顶(4→3) | 按钮消失,3 个置顶项直接显示 | ✅ |
| 6 | 重新置顶(3→4) | 按钮出现,继承上次的折叠/展开状态 | ✅ |
| 7 | App 重启 | 折叠/展开状态从偏好恢复 | ✅ |
| 8 | 配合"已完成"按钮 | 两者独立,互不干扰 | ✅ |
| 9 | 搜索/分类过滤 | 按钮始终显示总数,过滤后无匹配时按钮仍存在 | ✅ |
| 10 | 批量选择模式 | 按钮可点击,不影响批量操作 | ✅ |
| 11 | 拖拽排序 | 折叠时不可拖到 Divider 上方(因置顶项不在列表中) | ✅ |
| 12 | 点击反馈 | 无水波纹,仅文字/箭头变化 + 透明度反馈 | ✅ |
| 13 | 深色模式 | 颜色自动适配,对比度充足 | ✅ |
| 14 | 6 种主题色 | 置顶按钮跟随 `colorScheme.primary` | ✅ |
| 15 | 已完成所有置顶项 | 按钮消失(因为 `pinnedCount` 排除 `status=1`) | ✅ |

## 9. 测试

### 9.1 单元测试

**新增 `HomeViewModelPinnedButtonTest.kt`**:

```kotlin
class HomeViewModelPinnedButtonTest {
    // 状态切换
    @Test fun `toggleShowPinned 翻转状态并触发持久化`()
    @Test fun `showPinned 默认值为 true`()
    @Test fun `_showPinned 变更触发 showPinned StateFlow 更新`()

    // 数量计算
    @Test fun `pinnedCount 仅统计 status=0 且 isPinned=true 的待办`()
    @Test fun `pinnedCount 不包含已完成的置顶待办`()
    @Test fun `pinnedCount 在 _todos 变化时实时更新`()

    // 持久化
    @Test fun `toggleShowPinned 调用 corgiPreferences.setShowPinned`()
}
```

### 9.2 现有测试更新

- `HomeViewModelTest.kt` - 验证 `showPinned` 初始加载逻辑
- `HomeViewModelReorderTest.kt` - 验证 `PinnedDivider` 不影响排序逻辑

### 9.3 手动测试清单

| # | 场景 | 预期 |
|---|------|------|
| 1 | 置顶 0~3 个 | 按钮不出现,置顶项正常显示 |
| 2 | 置顶第 4 个 | 按钮出现,默认展开 |
| 3 | 点击"收起置顶" | 折叠成功,文字/箭头变化 |
| 4 | 再次点击 | 展开成功 |
| 5 | 取消置顶 4→3 | 按钮消失 |
| 6 | 重新置顶 3→4 | 按钮出现,状态继承 |
| 7 | App 重启 | 状态保持 |
| 8 | 配合"已完成" | 独立工作 |
| 9 | 搜索/分类 | 按钮正常显示 |
| 10 | 批量选择 | 按钮可点击 |
| 11 | 拖拽排序 | 不破坏置顶区 |
| 12 | 点击反馈 | 无水波纹 |
| 13 | 深色模式 | 颜色适配 |
| 14 | 主题切换 | 颜色跟随 |

## 10. 验收标准

- ✅ 编译通过,无新增警告
- ✅ 单元测试覆盖率 ≥ 80%(针对新增 ViewModel 逻辑)
- ✅ 14 个手动测试场景全部通过
- ✅ 视觉与可交互原型一致(http://localhost:53001/interactive.html)
- ✅ 现有"已完成"按钮行为不变(功能与外观),仅内部实现改为通用组件

## 11. 风险与权衡

| 风险 | 缓解措施 |
|------|---------|
| `CompletedSectionHeader` 重构破坏现有功能 | 完整手动测试 + 保留 `surfaceVariant` 背景的备用方案 |
| `PinnedDivider` 干扰拖拽排序 | `isDraggable` 仅 Todo,Divider 不可拖 |
| `pinnedCount` 频繁更新影响性能 | 使用 `stateIn` + `WhileSubscribed(5000)` |
| 折叠状态迁移混乱 | 旧用户默认值 `true`,不读取旧 DataStore(无历史) |

## 12. 变更文件清单

| 操作 | 路径 |
|------|------|
| 新增 | `app/src/main/java/com/corgimemo/app/ui/components/CollapsibleSectionHeader.kt` |
| 新增 | `app/src/main/java/com/corgimemo/app/ui/components/PinnedSectionHeader.kt` |
| 新增 | `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelPinnedButtonTest.kt` |
| 修改 | `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` |
| 修改 | `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt` |
| 修改 | `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelTest.kt` |
| 修改 | `app/src/test/java/com/corgimemo/app/viewmodel/HomeViewModelReorderTest.kt` |
