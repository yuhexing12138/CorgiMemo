# contentFormat 字段优化设计文档

> 日期：2026-06-03
> 状态：已批准
> 架构方案：C - ViewModel 居中

## 1. 背景与目标

contentFormat 字段（Markdown 格式）已在 V2.1 阶段完成基础存储闭环（保存 export + 加载 parse）。本次优化旨在解决 4 个遗留问题，提升编辑体验、性能和数据安全性。

### 1.1 当前问题清单

| # | 问题 | 严重度 | 影响 |
|---|------|--------|------|
| P1 | onValueChange 每次按键都调用 MarkdownParser.export() | 高 | 高频输入时 CPU 浪费，可能造成卡顿 |
| P2 | 无 Undo/Redo 功能 | 中 | 用户误操作格式后无法恢复 |
| P2 | 列表卡片仅显示纯文本 content | 中 | 编辑器中的格式在列表中完全丢失 |
| P3 | contentFormat 无校验机制 | 低 | 损坏数据可能导致 parse() 异常 |

### 1.2 设计决策记录

- **实施范围**：4 个功能全部实现
- **Undo/Redo 范围**：仅编辑器内（富文本操作），不含整个编辑页字段
- **预览渲染**：轻量行内渲染（保留粗体/斜体/删除线样式，maxLines=2）
- **防抖策略**：300ms debounce

## 2. 架构总览

采用 **ViewModel 居中** 方案，贴合现有 MVVM 架构：

```
┌──────────────────────────────────────────────────────┐
│                  TodoEditViewModel                    │
│                                                       │
│  ┌─────────────────────┐  ┌────────────────────────┐ │
│  │ 防抖导出 (Debounce)  │  │ Undo/Redo 双栈         │ │
│  │ _debounceJob +      │  │ undoStack / redoStack   │ │
│  │ scheduleFormatExport│  │ pushSnapshot()          │ │
│  └─────────────────────┘  │ undo() / redo()         │ │
│                           │ canUndo / canRedo       │ │
│  ┌─────────────────────┐  └────────────────────────┘ │
│  │ 校验 (Validation)    │                              │
│  │ validateAndSanitize  │  ← performSave() 调用       │
│  └─────────────────────┘                              │
└───────────────────┬───────────────────────────────────┘
                    ↑ ↓
┌───────────────────┴───────────────────────────────────┐
│                      UI Layer                          │
│                                                       │
│  TodoEditScreen.kt                                    │
│  ├── RichTextEditor.onValueChange                     │
│  │   → viewModel.setContent(text)        [立即]       │
│  │   → viewModel.scheduleFormatExport()  [防抖300ms]  │
│  │                                                     │
│  ├── EditToolbar 扩展                                   │
│  │   → ↩️ Undo 按钮 → viewModel.undo()                 │
│  │   → ↪️ Redo 按钮 → viewModel.redo()                │
│  │   → 格式按钮前 → viewModel.pushSnapshot()           │
│  │                                                     │
│  └── LaunchedEffect                                     │
│      → MarkdownParser.safeParse(contentFormat)  [容错] │
│                                                       │
│  TodoListItem.kt                                       │
│  └── MarkdownInlineText(contentFormat)     [轻量预览]  │
│                                                       │
│  工具类                                                │
│  ├── MarkdownParser.kt (+validateAndSanitize, safeParse)│
│  └── MarkdownInlineRenderer.kt (新建)                  │
└───────────────────────────────────────────────────────┘
```

## 3. 功能详细设计

### 3.1 防抖同步（P1）

#### 3.1.1 数据流

```
用户输入字符 "H"
  → onValueChange 触发
    → viewModel.setContent("H")              // 立即: 更新纯文本 StateFlow
    → viewModel.scheduleFormatExport(annotatedString)
      → cancel 旧 Job
      → launch { delay(300ms); export(); }   // 防抖: 300ms 后执行

用户输入字符 "e" (距上次输入 50ms)
  → onValueChange 触发
    → viewModel.setContent("He")
    → scheduleFormatExport()
      → cancel 上一个 delay(300ms) Job        // 取消未完成的导出
      → launch { delay(300ms); export(); }   // 重新计时

[用户停止输入 300ms 后]
  → delay 结束
  → MarkdownParser.export(annotatedString)
  → _contentFormat.value = markdown          // 最终更新
```

#### 3.1.2 接口定义

**TodoEditViewModel 新增**:

```kotlin
/** 防抖任务引用 */
private var _debounceJob: Job? = null

/**
 * 防抖调度：延迟 300ms 后将 AnnotatedString 导出为 Markdown
 *
 * 每次调用会取消上一次未完成的防抖任务，
 * 确保只有用户停止输入后的最终状态会被导出。
 *
 * @param annotatedString 当前的富文本内容
 */
fun scheduleFormatExport(annotatedString: AnnotatedString) {
    _debounceJob?.cancel()
    _debounceJob = viewModelScope.launch {
        delay(300L)
        val markdown = MarkdownParser.export(annotatedString)
        _contentFormat.value = markdown
    }
}
```

**TodoEditScreen 改动** (`onValueChange` 回调):

```kotlin
onValueChange = { newValue ->
    // 立即同步纯文本（用于实时显示和保存兜底）
    viewModel.setContent(newValue.text)
    editorState.textFieldValue = newValue

    // 防抖导出 Markdown（不再每次按键都 export）
    viewModel.scheduleFormatExport(newValue.annotatedString)

    // @关联弹窗逻辑保持不变...
}
```

**performSave 兜底逻辑**: 保存前强制执行一次即时 export，确保最终数据完整。

### 3.2 Undo/Redo（P2）

#### 3.2.1 数据结构

```kotlin
/** Undo 栈：存储可撤销的历史快照 */
private val _undoStack = ArrayDeque<AnnotatedString>()

/** Redo 栈：存储可重做的被撤销快照 */
private val _redoStack = ArrayDeque<AnnotatedString>()

/** 最大历史深度 */
private const val MAX_UNDO_DEPTH = 50
```

#### 3.2.2 快照时机

| 操作类型 | 是否触发快照 | 原因 |
|----------|-------------|------|
| applyBoldFormat | 是 | 格式变更操作 |
| applyItalicFormat | 是 | 格式变更操作 |
| applyStrikethroughFormat | 是 | 格式变更操作 |
| insertUnorderedList | 是 | 插入结构化内容 |
| clearAllFormats | 是 | 清除所有格式 |
| 文本输入（打字） | 否 | 会导致栈爆炸，每字一条记录 |

#### 3.2.3 操作流程

```
用户点击"加粗"按钮:
  ① viewModel.pushSnapshot(currentAnnotatedString)  // 保存当前状态到 undoStack
  ② applyBoldFormat(editorState)                     // 执行加粗操作
  ③ _redoStack.clear()                               // 清空 redo 栈（新操作使 redo 失效）

用户点击 Undo:
  ① pushSnapshot(currentAnnotatedString) to redoStack // 当前状态移到 redo
  ② pop undoStack → restore AnnotatedString           // 恢复上一个状态

用户点击 Redo:
  ① pushSnapshot(currentAnnotatedString) to undoStack  // 当前状态移到 undo
  ② pop redoStack → restore AnnotatedString            // 恢复被撤销的状态
```

#### 3.2.4 接口定义

**TodoEditViewModel 新增**:

```kotlin
/** 是否可以撤销 */
private val _canUndo = MutableStateFlow(false)
val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

/** 是否可以重做 */
private val _canRedo = MutableStateFlow(false)
val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

/**
 * 推送当前编辑器状态快照到 Undo 栈
 *
 * 在执行格式变更操作前调用，用于支持后续撤销。
 * 超过最大深度时自动丢弃最旧的记录。
 *
 * @param currentText 当前的 AnnotatedString 状态
 */
fun pushSnapshot(currentText: AnnotatedString) {
    _undoStack.addLast(currentText)
    if (_undoStack.size > MAX_UNDO_DEPTH) {
        _undoStack.removeFirst()
    }
    _canUndo.value = _undoStack.isNotEmpty()
    /** 新操作清空 Redo 栈 */
    _redoStack.clear()
    _canRedo.value = false
}

/**
 * 撤销上一操作
 *
 * 将当前状态推入 Redo 栈，从 Undo 栈弹出并返回上一状态。
 *
 * @return 上一状态的 AnnotatedString，无历史记录时返回 null
 */
fun undo(): AnnotatedString? {
    if (_undoStack.isEmpty()) return null
    /** TODO: 需要从 UI 层传入当前状态，或由 ViewModel 维护当前快照 */
    val previous = _undoStack.removeLast()
    _canUndo.value = _undoStack.isNotEmpty()
    return previous
}

/**
 * 重做被撤销的操作
 *
 * @return 被撤销状态的 AnnotatedString，无重做记录时返回 null
 */
fun redo(): AnnotatedString? {
    if (_redoStack.isEmpty()) return null
    val restored = _redoStack.removeLast()
    _canRedo.value = _redoStack.isNotEmpty()
    return restored
}
```

**UI 层集成 (TodoEditScreen)**:
- EditToolbar 区域新增 Undo/Redo 图标按钮
- 绑定 `viewModel.canUndo/canRedo` 控制启用状态
- 格式操作函数包裹 `pushSnapshot()` 调用

### 3.3 轻量行内富文本预览（P2）

#### 3.3.1 渲染策略

```
TodoListItem 内容显示区域:

if (todo.contentFormat.isNotBlank()) {
    // 有格式化内容 → 使用轻量 Markdown 渲染
    MarkdownInlineText(
        markdown = todo.contentFormat,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
} else {
    // 无格式化内容 → 保持原有纯文本行为
    Text(
        text = todo.content ?: "",
        maxLines = 1,
        ...
    )
}
```

#### 3.3.2 样式映射表

| Markdown 语法 | Compose 渲染 | 备注 |
|--------------|-------------|------|
| `**text**` | FontWeight.Bold | 粗体 |
| `*text*` | FontStyle.Italic | 斜体 |
| `~~text~~` | TextDecoration.LineThrough | 删除线 |
| `# Heading` | 剥离标记，显示文本 | 不渲染标题字号变化 |
| `- list item` | 剥除标记，显示文本 | 不渲染列表缩进 |
| `1. ordered` | 剥离标记，显示文本 | 不渲染序号 |

#### 3.3.3 新建文件: MarkdownInlineRenderer.kt

位置: `app/src/main/java/com/corgimemo/app/ui/components/MarkdownInlineRenderer.kt`

核心 Composable 函数:

```kotlin
/**
 * 轻量级 Markdown 行内文本渲染组件
 *
 * 用于待办卡片列表等空间受限场景的富文本预览。
 * 仅渲染行内样式（粗体/斜体/删除线），不渲染块级元素。
 *
 * @param markdown Markdown 格式文本
 * @param maxLines 最大显示行数
 * @param overflow 超出处理方式
 * @param style 基础文字样式
 */
@Composable
fun MarkdownInlineText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = TextStyle.Default
)
```

内部实现：
1. 调用 `MarkdownParser.parseInlineOnly(markdown)` — 仅解析行内格式（需新增方法或修改现有 parse）
2. 使用 `Text(composable)` 渲染 AnnotatedString
3. 应用 `maxLines` 和 `overflow` 参数截断

#### 3.3.4 TodoListItem 改动点

文件: `TodoListItem.kt` 第 249-256 行

替换前:
```kotlin
} else if (!todo.content.isNullOrBlank()) {
    Text(
        text = todo.content,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}
```

替换后:
```kotlin
} else if (!todo.content.isNullOrBlank() || todo.contentFormat.isNotBlank()) {
    if (todo.contentFormat.isNotBlank()) {
        MarkdownInlineText(
            markdown = todo.contentFormat,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    } else {
        Text(
            text = todo.content ?: "",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
```

### 3.4 contentFormat 校验（P3）

#### 3.4.1 两层防护

| 层级 | 时机 | 方法 | 行为 |
|------|------|------|------|
| 第一层 | 保存前 | `validateAndSanitize()` | 检测+修复损坏标记 |
| 第二层 | 加载时 | `safeParse()` | try-catch 容错回退 |

#### 3.4.2 校验规则

```kotlin
/**
 * 校验并清理 Markdown 文本中的常见损坏模式
 *
 * 检测并修复的问题：
 * 1. 未闭合的粗体标记 (**text → **text** 或剥离尾部 *)
 * 2. 未闭合的斜体标记 (*text → *text* 或剥离尾部 *)
 * 3. 未闭合的删除线标记 (~~text → ~~text~~ 或剥离尾部 ~)
 * 4. 连续的特殊字符（如 **** → 剥离为空）
 *
 * @param markdown 待校验的 Markdown 文本
 * @return 修复后的安全 Markdown 文本
 */
fun validateAndSanitize(markdown: String): String {
    var result = markdown.trimEnd()

    // 修复未闭合的成对标记（从长到短检查，避免短标记误匹配）
    result = fixUnclosedTag(result, "**")   // 粗体
    result = fixUnclosedTag(result, "~~")   // 删除线
    result = fixUnclosedTag(result, "*")    // 斜体（单星号，需排除已匹配的双星号）

    return result
}

/**
 * 安全解析 Markdown（带异常容错）
 *
 * 当 parse() 过程中出现任何异常时，
 * 自动回退为剥离所有标记的纯文本。
 *
 * @param markdown Markdown 文本
 * @return 解析成功的 AnnotatedString，异常时返回纯文本版本
 */
fun safeParse(markdown: String): AnnotatedString {
    return try {
        val sanitized = validateAndSanitize(markdown)
        if (sanitized.isBlank()) AnnotatedString("")
        else parse(sanitized)
    } catch (e: Exception) {
        android.util.Log.w("MarkdownParser", "safeParse 异常，回退纯文本", e)
        AnnotatedString(stripMarkdown(markdown))
    }
}
```

#### 3.4.3 performSave 集成

```kotlin
// TodoEditViewModel.performSave() 中
// 保存前对 contentFormat 进行校验和修复
val safeContentFormat = com.corgimemo.app.util.MarkdownParser.validateAndSanitize(_contentFormat.value)

// 使用修复后的值
contentFormat = safeContentFormat
```

#### 3.4.4 LaunchedEffect 集成

```kotlin
// TodoEditScreen 的 LaunchedEffect 中
val targetText = if (contentFormat.isNotBlank()) {
    com.corgimemo.app.util.MarkdownParser.safeParse(contentFormat)  // 改用 safeParse
} else {
    androidx.compose.ui.text.AnnotatedString(content)
}
```

## 4. 文件变更清单

| 文件 | 操作 | 改动规模 | 说明 |
|------|------|----------|------|
| `TodoEditViewModel.kt` | 修改 | +80 行 | 防抖 Job + Undo/Redo 栈 + 校验集成 |
| `TodoEditScreen.kt` | 修改 | +40 行 | onValueChange 改防抖 + Undo/Redo 按钮 + safeParse |
| `MarkdownParser.kt` | 修改 | +60 行 | validateAndSanitize() + safeParse() |
| `TodoListItem.kt` | 修改 | +15 行 | 条件渲染 MarkdownInlineText |
| `MarkdownInlineRenderer.kt` | **新建** | ~80 行 | 轻量行内 Markdown 渲染组件 |

## 5. 实施顺序

按优先级从高到低：

1. **P1 防抖同步** — 立竿见影的性能优化
2. **P3 contentFormat 校验** — 数据安全保障（改动小，先做）
3. **P2 轻量行内预览** — 用户体验提升
4. **P2 Undo/Redo** — 最复杂，放最后

## 6. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 防抖导致保存时 contentFormat 不是最新 | 丢失最后 300ms 内的格式 | performSave 中立即执行一次 export 作为兜底 |
| Undo 栈内存占用过大 | OOM 风险 | 限制 MAX_UNDO_DEPTH=50；每个快照仅存 AnnotatedString 引用 |
| MarkdownInlineRenderer 在列表滚动时频繁 parse | 列表卡顿 | 考虑使用 `remember` 缓存解析结果，或使用 derivedStateOf |
| safeParse 的 try-catch 性能开销 | 微乎其微 | 仅在异常路径触发，正常流程无额外开销 |
