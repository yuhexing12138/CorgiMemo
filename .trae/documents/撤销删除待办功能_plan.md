# 撤销删除待办功能实现计划

## 一、需求分析

### 1.1 功能需求

| 需求 | 说明 |
|------|------|
| Snackbar 显示 | 用户删除待办后，底部显示 Snackbar |
| Snackbar 内容 | "已删除" + "撤销" 按钮 |
| 自动消失 | 倒计时 5 秒后自动消失 |
| 撤销恢复 | 点击"撤销"按钮，待办恢复 |
| 计时器重置 | 倒计时期间新删除会重置计时器 |

### 1.2 技术要求

- 使用 Material 3 自带的 Snackbar 组件
- 临时存储待办数据（5 秒内有效）

## 二、现有代码分析

### 2.1 相关文件

| 文件路径 | 职责 | 关键代码 |
|----------|------|----------|
| `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` | 管理待办状态、删除逻辑 | `deleteTodo(id)` 方法（第 738-742 行） |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 首页 UI、待办列表展示 | `Scaffold` 组件（第 124 行起） |
| `app/src/main/java/com/corgimemo/app/data/repository/TodoRepository.kt` | 数据库操作 | `getTodoById()`、`insertTodo()` |

### 2.2 现有删除逻辑

```kotlin
// HomeViewModel.kt 第 738-742 行
fun deleteTodo(id: Long) {
    viewModelScope.launch {
        todoRepository.deleteTodoById(id)
    }
}
```

当前逻辑：直接从数据库删除，无法撤销。

### 2.3 可用的 Repository 方法

| 方法 | 用途 |
|------|------|
| `getTodoById(todoId: Long): TodoItem?` | 根据 ID 获取待办（用于临时存储） |
| `insertTodo(todo: TodoItem): Long` | 插入待办（用于恢复） |
| `deleteTodoById(todoId: Long)` | 删除待办（用于先删除） |

## 三、实现方案

### 3.1 整体架构

```
用户点击删除
    ↓
deleteTodo(id)  [ViewModel]
    ↓
getTodoById(id) 获取完整待办对象
    ↓
todoRepository.deleteTodoById(id) 从数据库删除（列表立即更新）
    ↓
pendingDeletedTodo = todo 临时存储
    ↓
取消之前的计时器（如有）
    ↓
启动新计时器：delay(5000)
    ↓
├─ 倒计时结束：pendingDeletedTodo = null
└─ 或用户点击撤销：todoRepository.insertTodo(todo) 恢复
```

### 3.2 状态设计

#### ViewModel 层新增状态

| 状态/方法 | 类型 | 说明 |
|-----------|------|------|
| `pendingDeletedTodo` | `TodoItem?` | 临时存储待删除的待办 |
| `deleteTimerJob` | `Job?` | 倒计时任务（可取消） |
| `deleteTodo(id)` | 修改现有方法 | 先获取待办，删除并存储，启动倒计时 |
| `undoDelete()` | 新增方法 | 撤销删除：重新插入待办，取消倒计时 |

#### UI 层新增状态

| 状态 | 类型 | 说明 |
|------|------|------|
| `SnackbarHostState` | Compose State | 控制 Snackbar 显示/隐藏 |
| `LaunchedEffect` | Compose 副作用 | 监听待办删除事件来显示 Snackbar |

## 四、详细实现步骤

### 步骤 1：修改 HomeViewModel.kt

#### 1.1 添加状态变量

```kotlin
// 待删除待办的临时存储
private val _pendingDeletedTodo = MutableStateFlow<TodoItem?>(null)
val pendingDeletedTodo: StateFlow<TodoItem?> = _pendingDeletedTodo.asStateFlow()

// 倒计时任务
private var deleteTimerJob: Job? = null

// 倒计时时长（5秒）
private val UNDO_DELETE_DELAY_MS = 5000L
```

#### 1.2 修改 deleteTodo 方法

```kotlin
/**
 * 删除待办（支持撤销）
 * 
 * @param id 待办 ID
 */
fun deleteTodo(id: Long) {
    viewModelScope.launch {
        // 1. 先获取完整的待办对象（用于撤销）
        val todo = todoRepository.getTodoById(id) ?: return@launch
        
        // 2. 立即从数据库删除（让列表立即更新）
        todoRepository.deleteTodoById(id)
        
        // 3. 临时存储待办
        _pendingDeletedTodo.value = todo
        
        // 4. 取消之前的倒计时任务（如果有）
        deleteTimerJob?.cancel()
        
        // 5. 启动新的倒计时
        deleteTimerJob = launch {
            delay(UNDO_DELETE_DELAY_MS)
            // 倒计时结束，清除临时数据
            _pendingDeletedTodo.value = null
        }
    }
}
```

#### 1.3 新增 undoDelete 方法

```kotlin
/**
 * 撤销删除
 * 将临时存储的待办重新插入数据库
 */
fun undoDelete() {
    viewModelScope.launch {
        val todo = _pendingDeletedTodo.value ?: return@launch
        
        // 1. 取消倒计时任务
        deleteTimerJob?.cancel()
        deleteTimerJob = null
        
        // 2. 重新插入待办（id 设为 0 让 Room 自动生成新 ID，保持不变的话需要特殊处理）
        todoRepository.insertTodo(todo)
        
        // 3. 清除临时数据
        _pendingDeletedTodo.value = null
    }
}
```

#### 1.4 ViewModel 清理时取消任务

```kotlin
override fun onCleared() {
    super.onCleared()
    stopIdleDetection()
    // 取消删除倒计时任务
    deleteTimerJob?.cancel()
}
```

### 步骤 2：修改 HomeScreen.kt

#### 2.1 添加必要的导入

```kotlin
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

#### 2.2 添加状态变量

在 `HomeScreen` 函数内添加：

```kotlin
// Snackbar 状态
val snackbarHostState = remember { SnackbarHostState() }
val pendingDeletedTodo by viewModel.pendingDeletedTodo.collectAsState()
val coroutineScope = rememberCoroutineScope()
```

#### 2.3 添加 LaunchedEffect 监听删除事件

```kotlin
// 监听待办删除事件，显示 Snackbar
LaunchedEffect(pendingDeletedTodo) {
    if (pendingDeletedTodo != null) {
        val result = snackbarHostState.showSnackbar(
            message = "已删除",
            actionLabel = "撤销",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        }
    }
}
```

#### 2.4 修改 Scaffold 添加 SnackbarHost

```kotlin
Scaffold(
    topBar = { ... },
    floatingActionButton = { ... },
    snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    },
    content = { ... }
)
```

#### 2.5 添加必要的导入（SnackbarResult）

```kotlin
import androidx.compose.material3.SnackbarResult
```

## 五、注意事项

### 5.1 待办 ID 问题

**问题**：`TodoItem` 的 `id` 可能是自动生成的。如果待办被删除后，重新插入时：
- 如果 `id = 0`，Room 会自动生成新 ID
- 如果 `id` 保持原值，可能会因为主键唯一而冲突

**解决方案**：
- 方案 A（推荐）：删除时记录完整 `TodoItem`，恢复时 `id` 设为 0 重新插入
- 方案 B：如果需要保持原 ID，需要在 `TodoDao` 中添加 `insertOrReplace` 方法

当前计划采用方案 A（简单有效）。

### 5.2 计时器重置逻辑

**需求**：倒计时期间新删除会重置计时器

**实现方式**：
- `deleteTodo` 方法中先 `deleteTimerJob?.cancel()` 取消旧任务
- 再启动新的 `delay(5000)` 任务
- 这样连续删除时，每次都会重新开始 5 秒倒计时

### 5.3 Snackbar 显示时机

- 使用 `LaunchedEffect(pendingDeletedTodo)` 监听
- 当 `pendingDeletedTodo` 从 `null` 变为非 `null` 时触发 Snackbar
- 用户点击撤销或倒计时结束后，`pendingDeletedTodo` 变回 `null`

### 5.4 多次删除的处理

**场景**：用户快速删除多个待办

**当前设计**：
- 每次删除都会覆盖 `pendingDeletedTodo`（只保留最后一个）
- 计时器每次都会重置
- 只有最后一个待办可以撤销

**如果需要支持多待办撤销队列**：
- 使用 `List<TodoItem>` 代替 `TodoItem?`
- Snackbar 显示 "已删除 N 个待办"
- 点击撤销恢复所有待办

当前计划采用"只保留最后一个"的简化设计（符合大多数 App 的行为）。

## 六、需要修改的文件总结

| 文件 | 修改类型 | 具体变更 |
|------|----------|----------|
| `HomeViewModel.kt` | 修改 | 添加临时存储状态、倒计时 Job、修改 deleteTodo、新增 undoDelete |
| `HomeScreen.kt` | 修改 | 添加 SnackbarHostState、LaunchedEffect 监听、Scaffold 配置 |

## 七、验证清单

| 验证项 | 预期结果 |
|--------|----------|
| 删除待办 | 列表立即更新，底部显示 Snackbar |
| Snackbar 内容 | 显示"已删除"和"撤销"按钮 |
| 倒计时自动消失 | 5 秒后 Snackbar 自动消失 |
| 点击撤销 | 待办重新出现在列表中 |
| 连续删除 | 每次删除重置计时器，Snackbar 保持显示 |
| 重复撤销 | 撤销后再次删除，功能正常 |
