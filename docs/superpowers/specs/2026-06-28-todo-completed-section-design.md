# 待办页面"已完成"折叠区域设计文档

> 文档日期：2026-06-28
> 状态：待批准
> 类型：UI交互重构

## 1. 背景与目标

### 1.1 背景
当前待办页面顶部有三个筛选按钮（"全部"、"待办"、"已完成"），用户需要在三个视图间切换查看不同状态的待办。这种设计存在以下问题：
- 三个按钮占据宝贵的垂直空间
- 用户无法同时看到未完成和已完成的待办
- 已完成项需要额外点击才能查看，路径冗长

### 1.2 目标
1. **移除三按钮筛选栏**：去掉"全部"、"待办"、"已完成"三个并排按钮
2. **默认聚焦未完成**：页面默认只展示未完成的待办事项
3. **可折叠已完成区域**：在未完成列表底部添加"已完成 N"切换按钮，点击展开/折叠已完成列表
4. **完整交互支持**：已完成待办支持拖拽排序、左滑操作、点击编辑等完整交互
5. **状态持久化**：展开/折叠状态在页面切换和应用重启后保持
6. **符合设计规范**：视觉风格与UI设计规范保持一致

### 1.3 参考图
参考提供的截图设计：
- **折叠状态**：显示 `⌄ 已完成 N`（向下箭头，表示可点击展开）
- **展开状态**：显示 `⌃ 已完成 N`（向上箭头，表示可点击折叠，与参考图一致）
- 展开后：已完成待办使用灰色降权样式（灰色checkbox、删除线文字、降低透明度）

## 2. 用户故事

| 角色 | 用户故事 | 验收标准 |
|------|---------|---------|
| 普通用户 | 我想默认只看到未完成的待办，这样能聚焦当前任务 | 进入待办页面，默认只显示未完成待办，底部有"已完成 N"按钮 |
| 普通用户 | 我想查看已完成的待办时，点击一下就能展开 | 点击"已完成"按钮，平滑展开已完成列表，按钮变为向上箭头 |
| 普通用户 | 我想隐藏已完成列表来减少干扰 | 再次点击按钮，平滑折叠已完成列表，按钮变为向下箭头 |
| 普通用户 | 我希望展开/折叠状态在我离开页面再回来时保持 | 切换到其他页面再回来，已完成区域保持上次的状态 |
| 普通用户 | 已完成的待办我也想重新排序 | 已完成区域内的待办支持长按拖拽排序 |
| 普通用户 | 我想对已完成的待办进行操作（删除/分享/置顶/编辑） | 已完成待办支持左滑显示操作按钮、点击进入编辑 |

## 3. 技术设计

### 3.1 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (HomeScreen.kt)                                    │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  ReorderableLazyColumn (DisplayItem 列表)               ││
│  │  ├─ DisplayItem.Todo(pendingTodos[0])                  ││
│  │  ├─ DisplayItem.Todo(pendingTodos[1])                  ││
│  │  ├─ DisplayItem.Todo(pendingTodos[n])                  ││
│  │  ├─ DisplayItem.CompletedDivider (不可拖拽)            ││
│  │  ├─ DisplayItem.Todo(completedTodos[0])                ││
│  │  ├─ DisplayItem.Todo(completedTodos[1])                ││
│  │  └─ DisplayItem.Todo(completedTodos[m])                ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                              ↕
┌─────────────────────────────────────────────────────────────┐
│  ViewModel Layer (HomeViewModel.kt)                          │
│  ├─ _showCompleted = MutableStateFlow(false)               │
│  ├─ pendingTodos: StateFlow<List<TodoItem>>                 │
│  ├─ completedTodos: StateFlow<List<TodoItem>>              │
│  ├─ completedCount: StateFlow<Int>                          │
│  └─ reorderPendingTodos(from, to) / reorderCompletedTodos  │
└─────────────────────────────────────────────────────────────┘
                              ↕
┌─────────────────────────────────────────────────────────────┐
│  Data Layer                                                  │
│  ├─ CorgiPreferences: save/showCompleted 持久化            │
│  └─ TodoRepository: updateSortOrder()                       │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 数据模型变更

#### 3.2.1 CorgiPreferences 新增
- 新增Key：`SHOW_COMPLETED = "show_completed"`
- 新增Flow：`val showCompleted: Flow<Boolean> = booleanFlow(Keys.SHOW_COMPLETED, false)`
- 新增方法：`suspend fun setShowCompleted(show: Boolean)`

#### 3.2.2 HomeViewModel 变更
- **移除**：FilterStatus枚举（ALL/PENDING/COMPLETED）、_filterStatus、setFilterStatus()
- **新增**：`_showCompleted = MutableStateFlow(false)`
- **新增**：`val showCompleted: StateFlow<Boolean>`
- **新增**：`pendingTodos: StateFlow<List<TodoItem>>`（status=0的待办，按isPinned→sortOrder→createdAt排序）
- **新增**：`completedTodos: StateFlow<List<TodoItem>>`（status=1的待办，按isPinned→sortOrder→createdAt排序）
- **新增**：`completedCount: StateFlow<Int>`（已完成待办数量）
- **修改**：移除原filteredTodos中的状态过滤逻辑（不再依赖FilterStatus）
- **新增**：`fun toggleShowCompleted()` 切换展开/折叠
- **修改**：`init`中从CorgiPreferences读取showCompleted初始值
- **修改**：`reorderTodos()` 替换为区域内排序方法，根据分隔位置判断在哪个区域排序

### 3.3 DisplayItem 密封类

在HomeScreen.kt中定义UI层用的密封类：

```kotlin
private sealed interface DisplayItem {
    data class Todo(val item: TodoItem) : DisplayItem
    /**
     * 已完成区域分隔按钮
     * @param count 已完成待办数量
     * @param isExpanded 当前是否展开
     */
    data class CompletedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
}
```

### 3.4 ReorderableLazyColumn 组件扩展

对[ReorderableLazyColumn.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt)添加以下能力：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `isDraggable` | `(T) -> Boolean` | `{ true }` | 判断项是否可拖拽（CompletedDivider返回false） |

**手势处理逻辑变更**：
1. 长按检测时：如果按下位置的项`isDraggable(item) == false`，不进入拖拽模式
2. 交换目标检测`findSwapTarget`：过滤掉`isDraggable == false`的项（它们不可作为交换目标）
3. 交换执行时：不允许draggable项越过non-draggable项（CompletedDivider是不可逾越的"墙"）

### 3.5 列表数据构建

在HomeScreen.kt中组合displayList：

```kotlin
val showCompleted by viewModel.showCompleted.collectAsState()
val pendingTodos by viewModel.pendingTodos.collectAsState()
val completedTodos by viewModel.completedTodos.collectAsState()
val completedCount by viewModel.completedCount.collectAsState()

/**
 * 构建显示列表：
 * - 折叠时：pendingTodos + CompletedDivider
 * - 展开时：pendingTodos + CompletedDivider + completedTodos
 * CompletedDivider始终显示在pendingTodos之后，作为展开/折叠按钮
 */
val displayItems = remember(pendingTodos, completedTodos, showCompleted, completedCount) {
    buildList {
        pendingTodos.forEach { add(DisplayItem.Todo(it)) }
        // 只有存在已完成待办时才显示分隔按钮
        if (completedCount > 0) {
            add(DisplayItem.CompletedDivider(count = completedCount, isExpanded = showCompleted))
            if (showCompleted) {
                completedTodos.forEach { add(DisplayItem.Todo(it)) }
            }
        }
    }
}
```

这样CompletedDivider始终在未完成和已完成之间（折叠时在列表末尾，展开时在中间）。
当completedCount=0时，不显示CompletedDivider按钮。

### 3.6 CompletedDivider 按钮组件

新建Composable函数`CompletedSectionHeader`：

**视觉规格**：
| 属性 | 值 |
|------|-----|
| 高度 | 48dp（触控区域） |
| 水平padding | 12dp（与卡片水平padding一致） |
| 文字 | `"已完成 $count"` |
| 文字颜色 | #999999（提示文字色） |
| 文字大小 | 15sp |
| 箭头 | 基础字符为⌃，折叠时旋转180°显示为⌄（向下），展开时不旋转显示为⌃（向上） |
| 箭头大小 | 16sp |
| 箭头与文字间距 | 8dp |
| 点击波纹 | 使用`clickable`带ripple效果， bounded半径 |
| 点击动画 | 箭头使用`graphicsLayer.rotationZ`做180度旋转，300ms FastOutSlowInEasing |

```kotlin
@Composable
private fun CompletedSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 180f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "completedArrowRotation"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color(0xFFFF9A5C).copy(alpha = 0.12f)),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "⌃",
            fontSize = 16.sp,
            color = Color(0xFF999999),
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "已完成 $count",
            fontSize = 15.sp,
            color = Color(0xFF999999),
            fontWeight = FontWeight.Medium
        )
    }
}
```

### 3.7 排序回调索引转换

当`onReorder(fromIndex, toIndex, crossedPinnedZone)`被触发时：

1. 查找CompletedDivider在displayItems中的索引：`dividerIndex = displayItems.indexOfFirst { it is CompletedDivider }`
2. 判断被拖项在哪个区域：
   - 如果`fromIndex < dividerIndex`：在pending区域内排序
     - 区域内索引 = fromIndex, toIndex（不需要偏移，因为pending在divider之前）
     - 但要确保`toIndex < dividerIndex`（不能越过divider）
     - 调用`viewModel.reorderPendingTodos(adjustedFrom, adjustedTo, crossedPinnedZone)`
   - 如果`fromIndex > dividerIndex`：在completed区域内排序
     - 区域内索引 = fromIndex - (dividerIndex + 1), toIndex - (dividerIndex + 1)（减去divider前面的项数+1）
     - 但要确保`toIndex > dividerIndex`（不能越过divider）
     - 调用`viewModel.reorderCompletedTodos(adjustedFrom, adjustedTo, crossedPinnedZone)`

### 3.8 ViewModel 排序方法

现有`reorderTodos`方法操作的是filteredTodos列表。需要改为：

1. **reorderPendingTodos(from, to, crossedPinnedZone)**：
   - 获取pendingTodos列表
   - 执行removeAt/insert操作
   - 重新分配sortOrder（仅对pending列表）
   - 注意：pending列表中的置顶项和非置顶项的sortOrder需要在pending分区内独立计算

2. **reorderCompletedTodos(from, to, crossedPinnedZone)**：
   - 获取completedTodos列表
   - 执行removeAt/insert操作
   - 重新分配sortOrder（仅对completed列表）

**sortOrder分配策略**：
为了简化，在区域内排序时，我们需要考虑：
- 整个列表的排序顺序是：pending(pinned→normal) → completed(pinned→normal)
- 我们可以给每个区域分配独立的sortOrder值范围，或者重新计算全局sortOrder

**更简单的方案**：每次重排后，重新计算所有待办的全局sortOrder：
1. 未完成置顶项：sortOrder从0开始递增
2. 未完成普通项：sortOrder从pendingPinnedCount开始递增
3. 已完成置顶项：sortOrder从pendingTotalCount开始递增
4. 已完成普通项：sortOrder从pendingTotalCount + completedPinnedCount开始递增

这样sortOrder始终保持全局有序，但在区域内拖拽时只影响该区域内的相对顺序。

### 3.9 展开/折叠动画

展开/折叠已完成区域时，由于我们在LazyColumn中使用`animateItem()`修饰符，新添加/移除的items会自动有平滑的动画效果。CompletedDivider本身作为列表项，其位置移动也会由animateItem处理。

箭头旋转动画由CompletedSectionHeader组件内部的`animateFloatAsState`处理。

### 3.10 待办项渲染

displayItems中的`DisplayItem.Todo`项继续使用现有的`SwipeableTodoBox` + `TodoListItem`渲染，不做改动。

`isPinned`参数需要适配DisplayItem：
- 对于DisplayItem.Todo，返回`item.isPinned`
- 对于DisplayItem.CompletedDivider，返回false（不参与置顶逻辑）

`key`参数需要适配：
- Todo项使用`item.id`
- CompletedDivider使用固定key如`"completed_divider"`

### 3.11 需要修改/新增的文件清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| [CorgiPreferences.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt) | 修改 | 新增SHOW_COMPLETED键、flow、setter |
| [HomeViewModel.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) | 修改 | 移除FilterStatus，新增showCompleted/pendingTodos/completedTodos，修改排序方法 |
| [HomeScreen.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) | 修改 | 移除FilterButton，新增DisplayItem、CompletedSectionHeader，修改列表构建逻辑 |
| [ReorderableLazyColumn.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt) | 修改 | 新增isDraggable参数，修改手势/交换逻辑 |

## 4. 视觉设计规范

### 4.1 颜色（遵循UI设计规范）
- 按钮文字/箭头：#999999（提示文字色）
- 背景：透明（与页面背景一致，不额外添加背景）
- 点击波纹：主题色（#FF9A5C）12%透明度

### 4.2 间距
- 按钮与上方最后一个pending卡片间距：8dp
- 按钮与下方第一个completed卡片间距：0dp（按钮自身有底部padding？）
- 按照参考图，按钮紧贴在pending列表之后

### 4.3 交互状态
- **折叠状态**：箭头向下⌄，文字"已完成 N"，已完成列表隐藏
- **展开状态**：箭头向上⌃，文字"已完成 N"，已完成列表显示
- **点击反馈**：箭头旋转动画 + ripple波纹效果
- **已完成待办样式**：保持现有已完成降权样式（灰色checkbox #BDBDBD、灰色文字 #888888、删除线、整体透明度0.6）

## 5. 边界情况处理

| 场景 | 处理方式 |
|------|---------|
| 没有已完成待办 | 不显示CompletedDivider按钮 |
| 没有未完成待办，只有已完成 | 仍显示CompletedDivider（默认折叠，点击展开） |
| 拖拽pending项到divider位置 | 不允许，divider不可跨越（toIndex被限制在dividerIndex之前） |
| 拖拽completed项到divider位置 | 不允许，toIndex被限制在dividerIndex之后 |
| 折叠状态下标记一个pending为completed | 该项立即从pending列表消失，completedCount加1，列表平滑动画 |
| 展开状态下标记一个pending为completed | 该项移动到completed区域，列表平滑动画 |
| 展开状态下标记一个completed为pending | 该项移动到pending区域，列表平滑动画 |
| 展开状态下删除一个completed项 | 该项移除，completedCount减1，列表平滑动画 |
| 应用重启 | 从CorgiPreferences读取showCompleted状态，保持上次选择 |

## 6. 测试要点

### 6.1 单元测试
- ViewModel中toggleShowCompleted()状态切换正确
- pendingTodos和completedTodos分离正确
- reorderPendingTodos/reorderCompletedTodos索引计算正确
- showCompleted持久化保存/读取正确

### 6.2 UI测试/手工验证
- [ ] 进入待办页面，默认只显示未完成待办，底部有"已完成 N"按钮（向下箭头）
- [ ] 点击"已完成"按钮，按钮箭头旋转向上，已完成列表平滑展开
- [ ] 再次点击按钮，箭头旋转向下，已完成列表平滑折叠
- [ ] 已完成待办显示为灰色降权样式（灰色勾选框、灰色删除线文字）
- [ ] 已完成待办支持左滑显示置顶/分享/删除按钮
- [ ] 已完成待办点击可进入编辑页面
- [ ] 未完成待办之间可以拖拽排序
- [ ] 已完成待办之间可以拖拽排序
- [ ] 拖拽未完成待办不能越过"已完成"按钮进入已完成区域
- [ ] 拖拽已完成待办不能越过"已完成"按钮进入未完成区域
- [ ] 切换到其他标签页再回来，展开/折叠状态保持
- [ ] 杀掉应用重启，展开/折叠状态保持
- [ ] 没有已完成待办时，不显示"已完成"按钮
- [ ] 标记待办为已完成/未完成时，列表平滑过渡
- [ ] 批量模式下，CompletedDivider按钮不响应点击
- [ ] 拖拽进行中，CompletedDivider按钮不响应点击

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| ReorderableLazyColumn添加isDraggable参数可能影响现有拖拽行为 | 中 | 确保默认值`{ true }`，现有不传isDraggable的调用方行为不变 |
| 索引转换错误导致排序错乱 | 高 | 在ViewModel中添加索引转换的单元测试，边界条件（divider在列表开头/结尾）覆盖 |
| 展开/折叠动画不流畅 | 低 | 使用LazyColumn.animateItem()，Compose原生支持；如有问题改用AnimatedVisibility |
| CompletedDivider作为列表项影响性能 | 低 | 仅增加一个列表项，对性能无影响 |
| showCompleted状态与列表数据不一致 | 中 | LaunchedEffect中初始加载时从CorgiPreferences读取，状态变更时立即保存 |