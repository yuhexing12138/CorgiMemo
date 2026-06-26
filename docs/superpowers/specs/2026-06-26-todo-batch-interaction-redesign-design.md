# 待办卡片交互重构设计（多选模式直达 + MoreOptions 菜单）

> 文档日期：2026-06-26
> 状态：已批准
> 类型：交互优化 + UI 重构

## 1. 背景与目标

### 1.1 背景

当前待办卡片长按交互存在以下问题：

- 长按后松手才弹底部菜单（TodoActionSheet），再点"批量选择"才进入批量模式 —— **路径冗长**
- 批量模式底部操作栏使用 4 个文本按钮（"✅ 全部完成 / 📂 移动 / 🗑️ 删除 / ✕ 取消"），视觉杂乱且不符合现代移动端设计
- 待办已完成态下的 Checkbox 颜色与设计规范不完全一致
- MoreOptions（三个点）入口缺失，导致置顶、优先级、提醒时间、创建副本、转换为灵感等次要操作需要多个入口分散

### 1.2 目标

1. **长按直达批量模式**：长按任意待办 → 松手 → 立即进入批量模式并选中该条（缩短操作路径）
2. **重构多选页 UI**：顶部"选中X项"+ 底部"全选+4图标"（分享/移动/删除/更多），对齐参考图一
3. **新增 MoreOptions 弹窗**：点击 ⋮ 显示完整菜单（图二），优先级/提醒时间走底部弹窗选择
4. **复用橙色 Checkbox**：使用 `CircularCheckbox`（已为 #FF9A5C 橙色），保持视觉一致
5. **移除冗余代码**：长按松手弹窗逻辑、TodoActionSheet 引用全部移除（按用户要求"完成后需删除相关代码"）

## 2. 用户故事

| 编号 | 用户故事                                                           | 验收标准                                  |
| ---- | ------------------------------------------------------------------ | ----------------------------------------- |
| US-1 | 作为用户，我希望长按任意待办立即进入多选模式                       | 长按松手后进入批量模式 + 该条已选中       |
| US-2 | 作为用户，我希望在多选页内能通过点击切换每条选中                   | 点击切换已选/未选；多选页 Checkbox 为橙色 |
| US-3 | 作为用户，我希望看到当前选中数量                                   | 顶部显示"选中 X 项"，X 实时同步           |
| US-4 | 作为用户，我希望一键全选/取消全选                                  | 底左"全选"按钮，已全选时再点变取消全选    |
| US-5 | 作为用户，我希望快速执行：分享/移动/删除                           | 底右 3 个图标，点击触发对应操作           |
| US-6 | 作为用户，我希望次要操作（置顶/优先级/提醒/复制/转灵感）不挤占底部 | 底右第 4 个 ⋮ 弹 MoreOptions 弹窗        |
| US-7 | 作为用户，我希望优先级/提醒时间选择不退出多选                      | 弹子底部弹窗选择，保留多选状态            |

## 3. 设计方案

### 3.1 架构概览

| 层     | 组件                             | 职责                                                                                  |
| ------ | -------------------------------- | ------------------------------------------------------------------------------------- |
| UI 层  | `TodoListItem.kt`              | 渲染待办卡片；处理长按/点击/多选；持有 0 弹窗状态                                     |
| UI 层  | `HomeScreen.kt`                | 多选页骨架：自定义顶部栏 + 底部操作栏 + MoreOptions 弹窗                              |
| UI 层  | `MoreOptionsSheet.kt`（新）    | 6 项菜单底部弹窗                                                                      |
| UI 层  | `PriorityPickerSheet.kt`（新） | 优先级选择小弹窗（4 选 1）                                                            |
| 状态层 | `HomeViewModel.kt`             | 复用现有 batch* 方法；新增 batchUpdatePriority / batchUpdateReminder / batchDuplicate |
| 持久化 | `TodoRepository`               | 直接使用现有 update / insert 方法                                                     |

### 3.2 交互流程图

```
普通模式长按 ──┐
                ├──► onLongClick: enterBatchMode(todoId)  ──► 立即进入批量模式 + 选中该条
                │
                ▼
        ┌───────────────┐
        │  批量模式 UI  │ ◄──────────────┐
        │  顶部:选中X项 │                │
        │  列表:每条可点 │                │
        │  底左:全选    │                │
        │  底右:4图标   │                │
        └──────┬────────┘                │
               │                         │
       ┌───────┴───────┐                 │
       ▼               ▼                 │
   点击 ◀ 退出      点击 ⋮ ──► MoreOptions 弹窗
                    ├── 完成 ──► batchComplete ──► 退出批量模式
                    ├── 置顶 ──► 全部置顶 ──► 关闭弹窗
                    ├── 优先级 ──► PriorityPickerSheet ──► batchUpdatePriority
                    ├── 提醒时间 ──► ReminderPickerBottomSheet ──► batchUpdateReminder
                    ├── 创建副本 ──► batchDuplicate ──► 关闭弹窗
                    └── 转换为灵感 ──► Toast "功能开发中" ──► 关闭弹窗
                                                │
                  (优先级/提醒完成后) ─────────┘
```

### 3.3 多选页 UI 规范

#### 3.3.1 顶部栏（自定义 Row，非 TopAppBar）

```
┌────────────────────────────────────────┐
│  ◀    选中 1 项                          │
└────────────────────────────────────────┘
```

- 高度：56dp
- 左侧 IconButton：`Icons.Default.ArrowBack`（24dp），点击 → `exitBatchMode()`
- 标题文字：`"选中 ${selectedTodoIds.size} 项"`，字号 18sp，FontWeight.SemiBold，颜色 `onSurface`
- 背景：`MaterialTheme.colorScheme.surface`（与原 TopAppBar 一致）

#### 3.3.2 底部操作栏（Surface + Row）

```
┌────────────────────────────────────────┐
│  全选              🖼   ➡️   🗑   ⋮     │
└────────────────────────────────────────┘
```

- 高度：64dp + 系统导航栏安全区
- 背景：`MaterialTheme.colorScheme.surface`，阴影 `shadowElevation = 8.dp`
- 左下：`TextButton` 显示"全选"（橙色 `UiColors.Primary` 文字，13sp）
- 右下 4 个 `IconButton`（40dp 圆形可点击区域）：| 顺序 | 图标                             | 颜色               | 行为                              |
  | ---- | -------------------------------- | ------------------ | --------------------------------- |
  | 1    | `Icons.Outlined.Share`         | `onSurface`      | 遍历选中项调 `shareTodoAsImage` |
  | 2    | `Icons.Outlined.DriveFileMove` | `onSurface`      | 显示 `showBatchMoveDialog`      |
  | 3    | `Icons.Outlined.Delete`        | `UiColors.Error` | 显示 `showBatchDeleteDialog`    |
  | 4    | `Icons.Default.MoreVert`       | `onSurface`      | 弹出 MoreOptions 弹窗             |

### 3.4 批量模式 Checkbox

复用 `CircularCheckbox` 组件（已为 #FF9A5C 橙色）：

- 待办未完成（status==0）：显示 `UiColors.Primary` (#FF9A5C) 橙色勾选
- 待办已完成（status==1）：显示 `CompletedColors.CheckboxBgDim` 浅橙（保持橙色系，降深度）
- 未选中：显示边框色 `UiColors.Outline` (#BDBDBD)

**关键决策**：直接复用现有 CircularCheckbox 而非新增，理由：

1. 颜色已是 #FF9A5C 橙色
2. 已支持 dimmed 状态，符合"已完成态视觉降权"设计原则
3. 减少组件数量

### 3.5 MoreOptions 弹窗

**触发**：点击底部 ⋮ 图标
**样式**：使用现有 `ActionBottomSheet` 组件（`ModalBottomSheet` + `ActionItem`）

**菜单项**（从上到下）：

| # | 标题       | 图标                               | 行为                                      | 颜色          |
| - | ---------- | ---------------------------------- | ----------------------------------------- | ------------- |
| 1 | 完成       | `Icons.Default.Check`            | `viewModel.batchComplete()` + 退出批量  | `onSurface` |
| 2 | 置顶       | `Icons.Default.PushPin`          | 遍历设置 `isPinned=1` + 关闭弹窗        | `onSurface` |
| 3 | 优先级     | `Icons.Default.Flag`             | 弹 `PriorityPickerSheet`                | `onSurface` |
| 4 | 提醒时间   | `Icons.Default.Alarm`            | 弹 `ReminderPickerBottomSheet`          | `onSurface` |
| 5 | 创建副本   | `Icons.Default.ContentCopy`      | `viewModel.batchDuplicate()` + 关闭弹窗 | `onSurface` |
| 6 | 转换为灵感 | `Icons.Default.LightbulbOutline` | Toast "功能开发中" + 关闭弹窗             | `onSurface` |

> **优先级弹窗子项**：无 / 低 / 中 / 高（对应 priority 0/1/2/3）
> **提醒时间弹窗**：复用现有 `ReminderPickerBottomSheet`（传入首次选中待办的 reminderTime 作为初始值）
> **"完成"与"置顶"作用范围**：仅作用于未完成项；"置顶"统一为"置顶态"（不切换），因为批量场景下用户期望"全置顶"而非混合态

### 3.6 ViewModel 方法

#### 3.6.1 复用现有

- `enterBatchMode(todoId)` ✅
- `exitBatchMode()` ✅
- `toggleSelection(todoId)` ✅
- `selectAll()` ✅
- `clearSelection()` ✅
- `batchComplete()` ✅
- `batchMove(categoryId)` ✅
- `batchDelete()` ✅
- `togglePin(todoId)` ✅（需新增批量版本）

#### 3.6.2 新增方法

```kotlin
/**
 * 批量置顶选中的待办（统一置顶）
 */
fun batchPin() {
    val selectedIds = _selectedTodoIds.value
    if (selectedIds.isEmpty()) return
    viewModelScope.launch {
        selectedIds.forEach { id ->
            todoRepository.getTodoById(id)?.let { todo ->
                if (!todo.isPinned) {
                    todoRepository.updateTodo(todo.copy(
                        isPinned = true,
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
        }
    }
}

/**
 * 批量设置优先级
 * @param priority 0=无 1=低 2=中 3=高
 */
fun batchUpdatePriority(priority: Int) {
    val selectedIds = _selectedTodoIds.value
    if (selectedIds.isEmpty()) return
    viewModelScope.launch {
        selectedIds.forEach { id ->
            todoRepository.getTodoById(id)?.let { todo ->
                todoRepository.updateTodo(todo.copy(
                    priority = priority,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }
}

/**
 * 批量设置提醒时间
 *
 * 注意：ReminderPickerBottomSheet 的 `calendarEnabled` 参数仅是 UI 内部状态（用于切换农历显示），
 * 不持久化到 TodoItem 字段（TodoItem 没有 calendarEnabled 字段）。本方法仅更新 reminderTime 与 repeatType。
 *
 * @param reminderTime 提醒时间戳（null 表示清除提醒）
 * @param repeatType 重复类型（0=不重复，1=每天，2=每周，3=每月，4=周一至周五，5=每年）
 */
fun batchUpdateReminder(reminderTime: Long?, repeatType: Int) {
    val selectedIds = _selectedTodoIds.value
    if (selectedIds.isEmpty()) return
    viewModelScope.launch {
        selectedIds.forEach { id ->
            todoRepository.getTodoById(id)?.let { todo ->
                todoRepository.updateTodo(todo.copy(
                    reminderTime = reminderTime,
                    repeatType = repeatType,
                    updatedAt = System.currentTimeMillis()
                ))
                // 提醒的 Alarm 调度由 updateTodo 内部统一处理（TodoRepository.updateTodo
                // 已包含 AlarmScheduler.rescheduleReminder 逻辑），无需在此处重复调度
            }
        }
    }
}

/**
 * 批量复制选中的待办
 * 每条新待办 id=null, createdAt/updatedAt = now
 */
fun batchDuplicate() {
    val selectedIds = _selectedTodoIds.value
    if (selectedIds.isEmpty()) return
    viewModelScope.launch {
        selectedIds.forEach { id ->
            todoRepository.getTodoById(id)?.let { todo ->
                todoRepository.insertTodo(todo.copy(
                    id = 0,  // Room 自增
                    title = todo.title,
                    content = todo.content,
                    status = 0,  // 复制为未完成
                    completedAt = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }
}
```

> **TODO**：如果 TodoRepository 没有 `setPinned(id, isPinned)` 公开方法，需要在 Repository 层补充

### 3.7 文件变更清单

| 文件                            | 操作 | 说明                                                                                                                                                                                                                                 |
| ------------------------------- | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `TodoListItem.kt`             | 修改 | 删除长按弹窗相关 state/回调（showLongPressMenu, actionSheetState, 底部 `if (showLongPressMenu) TodoActionSheet(...)` 块）；简化 `onLongClick` 逻辑（普通模式 = `enterBatchMode`）；批量模式 Checkbox 改用 `CircularCheckbox` |
| `HomeScreen.kt`               | 修改 | 顶部栏改自定义 Row（移除原 TopAppBar）；底部操作栏重写为"全选+4图标"；新增 MoreOptions 弹窗 + PriorityPickerSheet 调用                                                                                                               |
| `HomeViewModel.kt`            | 修改 | 新增 `batchPin`/`batchUpdatePriority`/`batchUpdateReminder`/`batchDuplicate`（直接使用现有 `todoRepository.updateTodo`/`insertTodo`，无 Repository 改动）                                                                |
| 新增 `MoreOptionsSheet.kt`    | 新建 | 6 项菜单弹窗                                                                                                                                                                                                                         |
| 新增 `PriorityPickerSheet.kt` | 新建 | 4 选 1 优先级选择小弹窗                                                                                                                                                                                                              |
| 删除 `TodoActionSheet.kt`     | 删除 | 移除冗余文件                                                                                                                                                                                                                         |

## 4. 边缘情况

| 场景                              | 处理                                                                 |
| --------------------------------- | -------------------------------------------------------------------- |
| 长按时多选已开启                  | `onLongClick` 切换该条选中状态（与点击同效果）                     |
| 全选状态下点"全选"                | 取消全选（`clearSelection()`）                                     |
| 全选状态下再点一条 → 取消该条    | 退化为非全选，"全选"按钮恢复初始文本                                 |
| 选中项中混入已完成项              | Checkbox 显示浅橙降权态；批量操作（完成/置顶）仅作用未完成项         |
| 优先级/提醒子弹窗未点确认直接返回 | 视为取消，不更新数据                                                 |
| 分享按钮在批量模式下              | 循环调 `shareTodoAsImage`，系统 share sheet 让用户选择             |
| 移动到分组弹窗                    | 复用现有 `showBatchMoveDialog` + `batchMove`，与单条删除弹窗共存 |
| 删除确认弹窗                      | 复用现有 `showBatchDeleteDialog` + `batchDelete`                 |

## 5. 风险与缓解

| 风险                                                                 | 影响     | 缓解                                                                                |
| -------------------------------------------------------------------- | -------- | ----------------------------------------------------------------------------------- |
| 删除 TodoActionSheet 后其他地方仍在引用                              | 编译失败 | 实施前全局 Grep 引用点；已确认仅 TodoListItem.kt 引用（仅长按松手弹窗逻辑使用）     |
| `setPinned` Repository 方法不存在                                  | 编译失败 | 已确认无此公开方法。`batchPin` 改为循环 set `isPinned = true` 并 `updateTodo` |
| AlarmScheduler API 签名变更                                          | 编译失败 | 复用 `updateTodo` 内部已包含的 `AlarmScheduler.rescheduleReminder` 逻辑         |
| 批量复制后 id 冲突                                                   | 数据异常 | 强制 `id=0` 由 Room 自增                                                          |
| 顶部栏 SolarTermCard / SearchBar / FilterButton 在批量模式下显示混乱 | 视觉干扰 | 保留现有行为（用户在批量模式时仍可见搜索/过滤），不在本次范围                       |

## 6. 测试要点

| #  | 测试场景                      | 期望                               |
| -- | ----------------------------- | ---------------------------------- |
| 1  | 长按未选中待办                | 立即进入批量模式 + 该条已选中      |
| 2  | 长按已选中待办（多选模式下）  | 切换为未选中                       |
| 3  | 批量模式点击未选中待办        | 切换为已选中                       |
| 4  | 批量模式点击已选中待办        | 切换为未选中                       |
| 5  | 全选按钮                      | 选中所有可见项                     |
| 6  | 全选状态下点"全选"            | 取消全选                           |
| 7  | 顶部"选中X项"数字             | 实时同步 selectedTodoIds.size      |
| 8  | 底部分享按钮                  | 循环调 shareTodoAsImage            |
| 9  | 底部移动按钮                  | 弹出分类选择弹窗                   |
| 10 | 底部删除按钮                  | 弹出二次确认弹窗                   |
| 11 | 底部⋮按钮                    | 弹出 MoreOptions 弹窗              |
| 12 | MoreOptions - 完成            | 批量完成 + 退出批量模式            |
| 13 | MoreOptions - 置顶            | 批量置顶                           |
| 14 | MoreOptions - 优先级          | 弹子弹窗 + 设置后批量更新          |
| 15 | MoreOptions - 提醒时间        | 弹 ReminderPicker + 设置后批量更新 |
| 16 | MoreOptions - 创建副本        | 复制所有选中项                     |
| 17 | MoreOptions - 转换为灵感      | Toast "功能开发中"                 |
| 18 | 批量模式 Checkbox 颜色        | 橙色 #FF9A5C                       |
| 19 | 已完成待办在批量模式 Checkbox | 浅橙色（降权态）                   |
| 20 | 返回箭头                      | 退出批量模式 + 清空选择            |

## 7. 不在本次范围

- "转换为灵感"具体跳转实现（用户明确要求）
- 现有左滑操作（分享/置顶/删除三个按钮，保留独立工作）
- 拖拽排序（已移除）
- 太阳能卡片在批量模式下的显隐
- 批量复制时是否复制子任务/附件（**保持现状**：复制 TodoItem 主表记录，不复制 ContentBlock；后续迭代再处理）
- 选中项的"取消当前项选中"长按弹窗等附加交互

## 8. 后续优化建议

- 批量复制时同时复制 ContentBlock（图片/语音）
- 批量操作进度提示（>10 项时显示 Snackbar）
- 优先级选择支持多级筛选
- 多选模式下"筛选当前选中"的快捷操作
- MoreOptions 菜单项的"折叠最近使用"
