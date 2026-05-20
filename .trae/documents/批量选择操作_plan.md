# 批量选择操作实现计划

## 项目调研结论

### 现有代码结构

**HomeViewModel**（[HomeViewModel.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt)）

* 管理待办列表状态（`_todos: MutableStateFlow<List<TodoItem>>`）

* 已有单个操作方法：`toggleTodoStatus()`、`deleteTodo()`、`undoDelete()`

* 使用 `TodoRepository` 进行数据库操作

**HomeScreen**（[HomeScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt)）

* 使用 `Scaffold` 布局，已有顶部 `TopAppBar`

* 使用 `LazyColumn` 渲染待办列表

* 已有底部 `SnackbarHost`，可用于操作提示

**TodoListItem**（[TodoListItem.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt)）

* 已有左滑删除手势

* 已有点击编辑和复选框完成功能

* 使用 `Card` 布局，支持点击交互

### 依赖情况

* Compose Animation：已引入（`AnimatedVisibility`、`slideInVertically` 等）

* Material3：已有复选框、按钮等组件

* 无需新增依赖

***

## 需要修改/新增的文件

### 1. ViewModel 层

* **修改**：`HomeViewModel.kt`

  * 新增批量模式状态和方法

### 2. UI 层

* **修改**：`HomeScreen.kt`

  * 新增批量模式顶部工具栏

  * 新增批量模式底部操作栏

  * 修改列表项渲染，支持批量模式

  * 添加返回键拦截处理

  * 添加分类选择弹窗

* **修改**：`TodoListItem.kt`

  * 新增批量模式参数

  * 新增长按事件

  * 新增选中状态 UI

  * 添加复选框和选中状态动画

### 3. 数据层

* **检查**：`TodoRepository.kt`

  * 确认是否有批量操作方法

  * 如有需要，新增批量操作方法

***

## 实现步骤

### 步骤 1：ViewModel 批量模式状态和方法

**文件**：`HomeViewModel.kt`

**新增状态**：

```kotlin
// 批量模式状态
private val _isBatchMode = MutableStateFlow(false)
val isBatchMode: StateFlow<Boolean> = _isBatchMode.asStateFlow()

// 选中的待办 ID 集合
private val _selectedTodoIds = MutableStateFlow<Set<Long>>(emptySet())
val selectedTodoIds: StateFlow<Set<Long>> = _selectedTodoIds.asStateFlow()
```

**新增方法**：

| 方法                              | 说明            |
| ------------------------------- | ------------- |
| `enterBatchMode(todoId: Long)`  | 进入批量模式，选中指定待办 |
| `exitBatchMode()`               | 退出批量模式，清空选择   |
| `toggleSelection(todoId: Long)` | 切换选中状态        |
| `selectAll()`                   | 全选当前可见待办      |
| `batchComplete()`               | 批量完成选中项       |
| `batchMove(categoryId: Long)`   | 批量移动到指定分类     |
| `batchDelete()`                 | 批量删除选中项       |

**新增辅助方法**：

* `getSelectedTodos()`: 获取选中的待办列表

* `hasSelection()`: 是否有选中项

***

### 步骤 2：TodoListItem 组件改造

**文件**：`TodoListItem.kt`

**新增参数**：

```kotlin
@Composable
fun TodoListItem(
    todo: TodoItem,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleComplete: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},  // 新增：长按进入批量模式
    onSelectClick: () -> Unit = {}  // 新增：批量模式下点击
)
```

**UI 变更**：

* 普通模式：保持现有布局

* 批量模式：

  * 左侧显示复选框（独立于完成复选框）

  * 选中时背景变色

  * 长按触发 `onLongClick`

  * 禁用左滑删除

  * 点击触发 `onSelectClick` 而非 `onClick`

**动画效果**：

* 复选框出现/消失：缩放动画（`animateContentSize`）

* 选中状态：背景色渐变 + 复选框缩放

***

### 步骤 3：HomeScreen 批量模式工具栏和操作栏

**文件**：`HomeScreen.kt`

**收集新增状态**：

```kotlin
val isBatchMode by viewModel.isBatchMode.collectAsState()
val selectedTodoIds by viewModel.selectedTodoIds.collectAsState()
```

**顶部工具栏**：

* 使用 `AnimatedVisibility` 根据 `isBatchMode` 显示/隐藏

* 内容：

  * 左侧：返回箭头（退出批量模式）

  * 中间："已选择 N 项"

  * 右侧：全选按钮（可选）

**底部操作栏**：

* 使用 `AnimatedVisibility` 显示/隐藏

* 固定在底部

* 四个按钮：

  * ✅ 全部完成

  * 📂 移动

  * 🗑️ 删除

  * ✕ 取消

* 按钮状态：选中 0 项时禁用（除取消外）

**动画**：

* 顶部工具栏：`slideInVertically(initialOffsetY = { -it })`

* 底部操作栏：`slideInVertically(initialOffsetY = { it })`

***

### 步骤 4：列表项渲染改造

**文件**：`HomeScreen.kt`

**改造 LazyColumn items**：

```kotlin
items(todos, key = { it.id }) { todo ->
    TodoListItem(
        todo = todo,
        isBatchMode = isBatchMode,
        isSelected = selectedTodoIds.contains(todo.id),
        onToggleComplete = { id, isChecked -> ... },
        onDelete = { id -> ... },
        onClick = { ... },
        onLongClick = { viewModel.enterBatchMode(todo.id) },
        onSelectClick = { viewModel.toggleSelection(todo.id) }
    )
}
```

***

### 步骤 5：操作确认和反馈

**批量删除确认**：

* 点击删除按钮时显示确认对话框

* 标题："删除选中项"

* 内容："确定要删除已选择的 N 个待办吗？此操作不可撤销。"

* 按钮：取消 / 删除

**批量移动分类选择**：

* 点击移动按钮时显示分类选择弹窗

* 使用 `ModalBottomSheet` 或 `AlertDialog`

* 显示所有分类列表供选择

* 选择后执行批量移动

**操作后反馈**：

* 使用 `Snackbar` 显示操作结果

* 例如："已完成 5 个待办"、"已删除 3 个待办"

* 批量删除后可提供撤销功能（复杂，可选实现）

***

### 步骤 6：返回键拦截

**文件**：`HomeScreen.kt`

**功能**：

* 在批量模式下，按返回键退出批量模式而非返回上一页

* 使用 `BackHandler` 或 `OnBackPressedDispatcher`

***

### 步骤 7：测试用例

| 测试项         | 期望结果               |
| ----------- | ------------------ |
| 长按待办        | 进入批量模式，选中该待办       |
| 点击待办（批量模式）  | 切换选中状态             |
| 点击复选框（批量模式） | 切换选中状态             |
| 点击完成按钮      | 所有选中项标记为已完成，退出批量模式 |
| 点击移动按钮      | 弹出分类选择，选择后批量移动     |
| 点击删除按钮      | 弹出确认，确认后删除所有选中项    |
| 点击取消按钮      | 退出批量模式，清空选择        |
| 按返回键（批量模式）  | 退出批量模式             |
| 选中 0 项      | 完成/移动/删除按钮禁用       |

***

## 潜在依赖和考虑

### 风险点

1. **左滑删除与长按冲突**

   * 解决：批量模式下禁用左滑删除手势

2. **完成复选框与选择复选框冲突**

   * 解决：批量模式下隐藏/禁用完成复选框，或使用独立的选择复选框

3. **列表项复用问题**

   * 解决：确保 key 唯一，动画正确应用

4. **批量删除撤销**

   * 考虑：批量删除的撤销逻辑复杂，可暂缓实现或只提示数量

### 可选优化

| 优化项      | 优先级 |
| -------- | --- |
| 全选/反选按钮  | 中   |
| 批量删除撤销   | 低   |
| 选择数量上限提示 | 低   |

***

## 实施顺序

1. ✅ 步骤 1：ViewModel 批量模式状态和方法
2. ✅ 步骤 2：TodoListItem 组件改造
3. ✅ 步骤 3：HomeScreen 工具栏和操作栏
4. ✅ 步骤 4：列表项渲染改造
5. ✅ 步骤 5：操作确认和反馈
6. ✅ 步骤 6：返回键拦截
7. ✅ 步骤 7：测试验证

