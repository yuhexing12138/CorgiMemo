# 撤销功能设计文档

> **日期**: 2026-05-25
> **状态**: 待审核
> **方案**: 内存 + 数据库混合架构
> **范围**: 完整实现（含操作历史记录表）

---

## 1. 功能概述

为 CorgiMemo 应用添加完整的撤销机制，支持：
- ✅ 删除待办后撤销（单个 + 批量）
- ✅ 完成待办后撤销
- ✅ 操作历史记录（数据库持久化）
- ✅ 设置页面查看和撤销历史操作

### 核心交互流程

```
用户删除待办
    ↓
1. 从数据库删除（立即生效）
2. 备份到内存状态
3. 显示 Snackbar（5秒倒计时）
    ↓
├─ 用户点击"撤销"
│   → 从内存恢复数据
│   → 重新插入数据库
│   → 写入操作日志（UNDO 记录）
│
└─ 5秒超时
    → 清除内存备份
    → 写入操作日志（确认删除记录）
```

---

## 2. 架构设计

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                      UI 层 (Composable)                       │
│                                                             │
│  HomeScreen                                                 │
│  ├─ Snackbar 显示（LaunchedEffect）                         │
│  └─ 操作按钮触发                                             │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   ViewModel 层                               │
│                                                             │
│  HomeViewModel                                              │
│  ├─ _pendingDeleteTodo: TodoItem?     ← 单个删除            │
│  ├─ _pendingCompleteTodo: TodoItem?   ← 单个完成            │
│  ├─ _pendingBatchDeletes: List<TodoItem>? ← 批量删除        │
│  ├─ deleteTimerJob: Job?             ← 倒计时任务           │
│  └─ completeTimerJob: Job?           ← 完成倒计时          │
│                                                             │
│  公开方法：                                                  │
│  ├─ deleteTodo(id) / deleteTodos(ids)                       │
│  ├─ undoDelete() / undoBatchDelete()                        │
│  ├─ toggleComplete(id)                                      │
│  └─ undoComplete()                                          │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  Repository 层                               │
│                                                             │
│  OperationLogRepository                                     │
│  ├─ insertLog(log: OperationLogEntity)                      │
│  ├─ getRecentLogs(limit: Int): Flow<List<OperationLog>>      │
│  └─ deleteLogsOlderThan(timestamp: Long)                    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 数据层 (Room Database)                        │
│                                                             │
│  operation_logs 表                                         │
│  ├─ id: INTEGER (PK, AutoIncrement)                        │
│  ├─ operation_type: TEXT (DELETE/COMPLETE/UNDO_DELETE/...)  │
│  ├─ target_id: INTEGER (待办 ID 或批量操作的 hash)          │
│  ├─ snapshot_json: TEXT (JSON 格式的完整数据备份)           │
│  ├─ batch_ids: TEXT (批量操作的 ID 列表，可选)              │
│  └─ created_at: INTEGER (时间戳，毫秒)                      │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 数据流图

```
┌──────────┐    deleteTodo()     ┌──────────────┐
│  用户点击  │ ─────────────────→ │ HomeViewModel │
│  删除按钮  │                    │              │
└──────────┘                    │ 1. 获取待办   │
                                │ 2. 删除 DB    │
                                │ 3. 存入内存   │
                                │ 4. 启动定时器  │
                                └──────┬───────┘
                                       │
                                       ▼
                              ┌────────────────┐
                              │ StateFlow 更新  │
                              │ pendingDeleted  │
                              └───────┬────────┘
                                      │
                                      ▼
                              ┌────────────────┐
                              │ LaunchedEffect  │
                              │ 监听状态变化    │
                              └───────┬────────┘
                                      │
                                      ▼
                              ┌────────────────┐
                              │ Snackbar 显示  │
                              │ "☑️ xxx 已删除" │
                              │ [撤销] 5秒      │
                              └───────┬────────┘
                                      │
                    ┌─────────────────┴─────────────────┐
                    ↓                                   ↓
            点击「撤销」                          5秒超时
                    ↓                                   ↓
        ┌────────────────┐                  ┌────────────────┐
        │ undoDelete()   │                  │ 定时器触发     │
        │ 1. 取消定时器  │                  │ 清空内存状态   │
        │ 2. 插入 DB     │                  │ 写入日志       │
        │ 3. 清空内存    │                  └────────────────┘
        │ 4. 写入日志    │
        └────────────────┘
```

---

## 3. 详细设计

### 3.1 数据模型

#### OperationLogEntity（新增）

```kotlin
@Entity(tableName = "operation_logs")
data class OperationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 操作类型 */
    val operationType: String,  // "DELETE", "COMPLETE", "BATCH_DELETE", "UNDO"

    /** 目标待办 ID（单个操作） */
    val targetId: Long = 0,

    /** 批量操作的 ID 列表（JSON 数组格式） */
    val batchIdsJson: String? = null,

    /** 操作前的数据快照（JSON 格式） */
    val snapshotJson: String,

    /** 创建时间戳（毫秒） */
    val createdAt: Long = System.currentTimeMillis()
)
```

#### 操作类型枚举

```kotlin
object OperationType {
    const val DELETE = "DELETE"           // 删除单个待办
    const val COMPLETE = "COMPLETE"       // 完成待办
    const val BATCH_DELETE = "BATCH_DELETE" // 批量删除
    const val UNDO_DELETE = "UNDO_DELETE" // 撤销删除
    const val UNDO_COMPLETE = "UNDO_COMPLETE" // 撤销完成
}
```

### 3.2 ViewModel 设计

#### 新增状态变量

```kotlin
// ========== 删除相关 ==========
private val _pendingDeleteTodo = MutableStateFlow<TodoItem?>(null)
val pendingDeleteTodo: StateFlow<TodoItem?> = _pendingDeleteTodo.asStateFlow()

private val _pendingBatchDeletes = MutableStateFlow<List<TodoItem>?>(null)
val pendingBatchDeletes: StateFlow<List<TodoItem>?> = _pendingBatchDeletes.asStateFlow()

// ========== 完成相关 ==========
private val _pendingCompleteTodo = MutableStateFlow<Pair<TodoItem, Boolean>?>(null)
// Pair<todo, wasCompletedBefore>
val pendingCompleteTodo: StateFlow<Pair<TodoItem, Boolean>?> =
    _pendingCompleteTodo.asStateFlow()

// ========== 定时器任务 ==========
private var deleteTimerJob: Job? = null
private var completeTimerJob: Job? = null

// ========== 常量 ==========
private val UNDO_DELAY_MS = 5000L      // 删除撤销：5秒
private val COMPLETE_UNDO_DELAY_MS = 3000L  // 完成撤销：3秒
```

#### 核心方法签名

```kotlin
/** 删除单个待办（支持撤销）*/
fun deleteTodo(id: Long)

/** 批量删除待办（支持撤销）*/
fun deleteTodos(ids: List<Long>)

/** 撤销单个删除 */
fun undoDelete()

/** 撤销批量删除 */
fun undoBatchDelete()

/** 切换完成状态（支持撤销）*/
fun toggleComplete(id: Long)

/** 撤销完成操作 */
fun undoComplete()
```

### 3.3 UI 组件设计

#### Snackbar 消息格式

| 操作 | 消息内容 | 操作按钮 | 时长 |
|------|---------|---------|------|
| 删除单个 | `☑️ '{标题}' 已删除` | `[撤销]` | 5秒 |
| 批量删除 | `☑️ 已删除 {n} 个待办` | `[全部撤销]` | 5秒 |
| 完成 | `✅ '{标题}' 已完成` | `[撤销]` | 3秒 |

#### HomeScreen 中的监听逻辑

```kotlin
// 监听删除事件
LaunchedEffect(pendingDeleteTodo) {
    pendingDeleteTodo?.let { todo ->
        val result = snackbarHostState.showSnackbar(
            message = "☑️ '${todo.title}' 已删除",
            actionLabel = "撤销",
            duration = SnackbarDuration.Long  // 5秒
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        }
    }
}

// 监听完成事件
LaunchedEffect(pendingCompleteTodo) {
    pendingCompleteTodo?.let { (todo, _) ->
        val result = snackbarHostState.showSnackbar(
            message = "✅ '${todo.title}' 已完成",
            actionLabel = "撤销",
            duration = SnackbarDuration.Short  // 3秒
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoComplete()
        }
    }
}
```

### 3.4 数据库迁移

#### MIGRATION_13_14

```kotlin
private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS operation_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                operation_type TEXT NOT NULL,
                target_id INTEGER NOT NULL DEFAULT 0,
                batch_ids_json TEXT,
                snapshot_json TEXT NOT NULL,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
            )
        """.trimIndent())

        // 创建索引优化查询性能
        database.execSQL(
            "CREATE INDEX idx_operation_logs_created_at ON operation_logs(created_at)"
        )
    }
}
```

### 3.5 设置页面入口

在设置页面添加新卡片：

```kotlin
/** 最近操作卡片 */
Card(
    onClick = { navController.navigate("operation_history") }
) {
    ListItem(
        headlineContent = { Text("最近操作") },
        supportingContent = { Text("查看和撤销最近的待办操作") },
        leadingContent = {
            Icon(Icons.Default.History, contentDescription = null)
        }
    )
}
```

#### 操作历史页面（OperationHistoryScreen）

```kotlin
@Composable
fun OperationHistoryScreen(
    viewModel: OperationHistoryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()

    LazyColumn {
        items(logs) { log ->
            OperationLogItem(
                log = log,
                onUndo = { viewModel.undoOperation(log.id) }
            )
        }
    }
}
```

---

## 4. 边界情况处理

### 4.1 并发操作场景

| 场景 | 处理方式 |
|------|---------|
| 快速连续删除多个待办 | 每次新操作取消前一个定时器，只保留最新的撤销状态 |
| 删除过程中旋转屏幕 | ViewModel 保存状态，Activity 重建后仍可撤销 |
| 撤销时网络断开 | 本地操作，无需网络，直接操作 Room 数据库 |

### 4.2 数据一致性

```kotlin
fun undoDelete() {
    viewModelScope.launch {
        // 1. 使用事务确保原子性
        withContext(Dispatchers.IO) {
            db.runInTransaction {
                // 重新插入待办
                todoDao.insert(todo)
                // 写入撤销日志
                operationLogDao.insert(undoLog)
            }
        }

        // 2. 清理内存状态
        _pendingDeleteTodo.value = null
        deleteTimerJob?.cancel()
    }
}
```

### 4.3 自动清理策略

```kotlin
/** 定期清理旧日志（保留最近 100 条）*/
private fun scheduleCleanup() {
    viewModelScope.launch {
        while (isActive) {
            delay(24 * 60 * 60 * 1000L)  // 每24小时清理一次
            val cutoffTime = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L  // 30天前
            operationLogRepository.deleteLogsOlderThan(cutoffTime)
        }
    }
}
```

---

## 5. 性能考虑

### 5.1 内存占用

| 数据类型 | 预估大小 | 说明 |
|---------|---------|------|
| pendingDeleteTodo | ~500 bytes | 单个 TodoItem 对象 |
| pendingBatchDeletes | ~5 KB | 最多 20 个待办 |
| pendingCompleteTodo | ~550 bytes | TodoItem + Boolean |
| **总计** | **~6 KB** | 可忽略不计 |

### 5.2 数据库性能

- ✅ 使用索引加速 `created_at` 查询
- ✅ 异步写入日志（不阻塞主线程）
- ✅ 限制查询数量（最近 100 条）

### 5.3 UI 响应性

- ✅ Snackbar 显示使用 `LaunchedEffect`（非阻塞）
- ✅ 撤销操作使用 `viewModelScope.launch`（后台执行）
- ✅ 状态更新通过 `StateFlow`（响应式）

---

## 6. 测试策略

### 6.1 单元测试

```kotlin
class UndoFeatureTest {

    @Test
    fun `deleteTodo should store backup in memory`() {
        // Given: 有一个待办
        // When: 调用 deleteTodo(id)
        // Then: pendingDeleteTodo 应该包含该待办
    }

    @Test
    fun `undoDelete should restore todo to database`() {
        // Given: 已删除的待办存储在 pendingDeleteTodo
        // When: 调用 undoDelete()
        // Then: 待办应该重新出现在数据库中
    }

    @Test
    fun `timer should clear pending state after 5 seconds`() {
        // Given: pendingDeleteTodo 不为空
        // When: 等待 5 秒
        // Then: pendingDeleteTodo 应该被清空
    }
}
```

### 6.2 UI 测试

```kotlin
@Test
fun `snackbar should show after delete`() {
    // Given: 在待办列表页
    // When: 左滑删除一个待办
    // Then: 应该显示 "☑️ 'xxx' 已删除" 的 Snackbar
}

@Test
fun `clicking undo should restore item`() {
    // Given: Snackbar 正在显示
    // When: 点击"撤销"按钮
    // Then: 待办应该重新出现在列表中
}
```

---

## 7. 文件变更清单

### 新增文件（7 个）

| 文件路径 | 功能说明 |
|---------|---------|
| `data/model/OperationLogEntity.kt` | 操作日志实体类 |
| `data/local/db/OperationLogDao.kt` | 操作日志 DAO 接口 |
| `data/repository/OperationLogRepository.kt` | 操作日志 Repository |
| `viewmodel/OperationHistoryViewModel.kt` | 操作历史页面 ViewModel |
| `ui/screens/settings/OperationHistoryScreen.kt` | 操作历史页面 UI |
| `ui/components/OperationLogItem.kt` | 操作日志列表项组件 |

### 修改文件（4 个）

| 文件路径 | 改动内容 |
|---------|---------|
| `viewmodel/HomeViewModel.kt` | 新增完成撤销、批量删除撤销、日志记录方法 |
| `ui/screens/home/HomeScreen.kt` | 增强 Snackbar 监听逻辑（完成 + 批量）|
| `data/local/db/CorgiMemoDatabase.kt` | 注册 OperationLogEntity + Migration 13→14 |
| `ui/screens/settings/SettingsScreen.kt` | 添加"最近操作"入口卡片 |

---

## 8. 实施计划概览

### Phase 1: 数据层基础（预计改动量：小）
1. 创建 `OperationLogEntity` 数据模型
2. 创建 `OperationLogDao` DAO 接口
3. 实现 `MIGRATION_13_14` 迁移脚本
4. 注册到 `CorgiMemoDatabase`

### Phase 2: 业务逻辑层（预计改动量：中）
5. 创建 `OperationLogRepository`
6. 扩展 `HomeViewModel`：
   - 新增 `_pendingCompleteTodo` 状态
   - 新增 `_pendingBatchDeletes` 状态
   - 重构 `toggleComplete()` 方法
   - 新增 `undoComplete()` 方法
   - 新增 `deleteTodos()` 和 `undoBatchDelete()` 方法
   - 集成日志记录

### Phase 3: UI 层实现（预计改动量：中）
7. 增强 `HomeScreen` 的 Snackbar 逻辑：
   - 监听 `pendingCompleteTodo`
   - 监听 `pendingBatchDeletes`
   - 优化消息格式（显示待办标题）
8. 创建 `OperationHistoryScreen` 页面
9. 创建 `OperationLogItem` 组件
10. 在 `SettingsScreen` 添加入口

### Phase 4: 测试与优化（预计改动量：小）
11. 添加单元测试
12. 性能测试与优化
13. 边界情况验证

---

## 9. 风险与缓解措施

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 数据库版本升级失败 | APP 无法启动 | 充分测试迁移脚本；提供 fallback 方案 |
| 内存泄漏（定时器未取消） | 内存持续增长 | 在 `onCleared()` 中取消所有 Job |
| 并发写入冲突 | 数据不一致 | 使用 Room 事务保证原子性 |
| 日志表无限增长 | 存储空间耗尽 | 自动清理策略（30天 / 100条上限）|

---

## 10. 成功标准

✅ **功能完整性**
- 删除单个待办后可撤销（5秒内）
- 批量删除后可撤销（5秒内）
- 完成待办后可撤销（3秒内）
- 可在设置页查看最近 100 条操作记录
- 可从历史记录中撤销操作

✅ **用户体验**
- Snackbar 消息清晰显示待办标题
- 撤销操作流畅无卡顿
- 无感知的数据持久化

✅ **技术质量**
- 通过所有单元测试
- 无内存泄漏
- 数据库迁移成功率 100%
- UI 响应时间 < 100ms

---

## 附录 A: 技术选型理由

### 为什么选择 StateFlow 而不是 LiveData？

| 特性 | StateFlow | LiveData |
|------|----------|---------|
| 协程原生支持 | ✅ | ⚠️ 需要 lifecycle-aware |
| 冷启动行为 | ✅ 可控 | ❌ 需要额外处理 |
| 操作符丰富度 | ✅ 支持 flow 操作符 | ❌ 有限制 |
| Compose 集成 | ✅ 原生支持 collectAsState() | ⚠️ 需要 observeAsState() |

### 为什么选择 5 秒作为默认时长？

参考 Material Design 3 规范：
- **Short**: 4 秒（适合简单提示）
- **Long**: 10 秒（适合重要操作）
- **自定义**: 我们选择 **5 秒**（平衡用户体验和误操作风险）

---

*文档结束*
