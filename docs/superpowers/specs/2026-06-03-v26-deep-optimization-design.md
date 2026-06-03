# V2.6 深度优化设计文档

> 日期: 2026-06-03
> 版本: V2.6 (基于 V2.5 的深度优化)
> 状态: 已批准，实施中

## 概述

在 V2.5（Redo对称菜单、LazyColumn虚拟化、FAB脉冲动画、逐区间高亮、编辑历史时间线）的基础上，
进行 3 项深度优化：

| # | 功能 | 优先级 | 复杂度 |
|---|------|--------|--------|
| F1 | 搜索高亮保留 Markdown 行内样式 | P0 | 中 |
| F2 | 时间线点击恢复编辑器状态（NavResult） | P1 | 低 |
| F3 | Undo 日志按 TodoId 隔离 | P0 | 中 |

## F1: 搜索高亮保留 Markdown 行内样式

### 问题
V2.5 的 `buildHighlightRanges()` 返回纯文本 `HighlightRange`，导致搜索模式下 Markdown 内容降级为纯文本+高亮，丢失粗体/斜体/删除线等格式。

### 解决方案
扩展 `HighlightRange` 为 `StyledHighlightRange`，携带可选 SpanStyle。

### 数据结构变更

```kotlin
/** 带样式的搜索高亮区间 */
data class StyledHighlightRange(
    val text: String,
    val isHighlight: Boolean,
    val startIndex: Int,
    val spanStyle: SpanStyle? = null  /** Markdown 解析出的行内样式（粗体/斜体/等）*/
)
```

### 新增方法
- `buildStyledHighlightRanges(markdown, keywords, color): Pair<List<StyledHighlightRange>, Color>`
  - 内部先调用 `MarkdownParser.safeParse()` 得到带样式的 AnnotatedString
  - 在 text 上执行关键词匹配，收集高亮区间
  - 按「SpanStyle 边界 ∪ 高亮边界」联合切分
  - 返回带样式的区间列表

### 渲染变更 (TodoListItem.kt)
- contentFormat 字段：使用 `buildStyledHighlightRanges()` → 每个 Text 带 style 参数
- title / content 字段：继续使用轻量版 `buildHighlightRanges()`（纯文本无需样式）

## F2: 时间线点击恢复编辑器状态

### 通信机制
Navigation Compose SavedStateHandle Result API。

### 流程
```
TimelineItem.onClick
  → savedStateHandle.set("restore_text", entry.fullText)
  → navController.popBackStack()

TodoEditScreen LaunchedEffect
  → 监听 restore_text 变化
  → 消费后填充到 editorState.textFieldValue
  → 清除 savedStateHandle 中的值（一次性消费）
```

### 文件变更
- EditHistoryScreen.kt: TimelineItem 增加 onClick + NavResult 写入
- TodoEditScreen.kt: 增加 LaunchedEffect 监听 restore_text
- AppNavHost.kt: EditHistory 路由可能需要调整以支持 result API

## F3: Undo 日志按 TodoId 隔离

### 问题
当前 UNDO_LOG/REDO_LOG 为全局共享 key，多 Todo 并发编辑时：
- 切换 Todo 时 clearUndoRedoStacks() 会清空其他 Todo 的日志
- 恢复时 todoId 不匹配则丢弃全部数据

### 目标架构
Key 格式从 `"undo_log"` 改为 `"undo_log_{todoId}"`。

### CorgiPreferences 变更
```kotlin
// 新增：动态 key 方法
suspend fun appendUndoLogEntry(todoId: Long, snapshotJson: String)
suspend fun getUndoLog(todoId: Long): String
suspend fun appendRedoLogEntry(todoId: Long, snapshotJson: String)
suspend fun getRedoLog(todoId: Long): String
suspend fun clearUndoLogs(todoId: Long)
```

### TodoEditViewModel 变更
- persistUndoRedoStacksAsync(): 使用 existingTodo?.id 构造 key
- restoreUndoStacks(todoId): 使用 {todoId}_undo_log key
- 不再需要 UNDO_STACK_TODO_ID 校验逻辑

### EditHistoryViewModel 变更
- loadTimeline(todoId: Long): 接收 todoId 参数，读取对应日志

### 向后兼容
检测旧格式 "undo_log" key 存在时迁移数据到 "{todoId}_undo_log" 格式。

## 文件变更清单

| 文件 | 操作 | 涉及功能 |
|------|------|----------|
| HighlightUtil.kt | 修改 | F1 (StyledHighlightRange + buildStyledHighlightRanges) |
| TodoListItem.kt | 修改 | F1 (Markdown内容使用styled渲染) |
| EditHistoryScreen.kt | 修改 | F2 (TimelineItem onClick + NavResult) |
| TodoEditScreen.kt | 修改 | F2 (LaunchedEffect监听restore_text) |
| CorgiPreferences.kt | 修改 | F3 (动态todoId key方法) |
| TodoEditViewModel.kt | 修改 | F3 (persist/restore使用todoId key) |
| EditHistoryViewModel.kt | 修改 | F3 (loadTimeline接收todoId参数) |
| AppNavHost.kt | 修改 | F2+F3 (路由参数传递) |
