# V2.3 深度优化设计文档（第 3 轮）

> 日期：2026-06-03
> 状态：已批准

## 1. 功能清单

| # | 功能 | 优先级 |
|---|------|--------|
| 1 | 搜索结果高亮（黄色背景，自适应容器颜色） | P0 |
| 2 | 浮动 Undo/Redo 快捷按钮（编辑器内右上角） | P1 |
| 3 | Undo 栈增量持久化（Append-Only 日志模式） | P2 |
| 4 | MarkdownInlineRenderer 虚拟化增强 | P2 |

## 2. 功能详细设计

### 2.1 搜索结果高亮

**新建** `HighlightUtil.kt`：`buildHighlightedText()` 函数
- 在文本中查找所有 query 出现位置（ignoreCase）
- 对匹配区间应用 `SpanStyle(background = highlightColor)`
- 自适应逻辑：深色/黄色背景时切换为橙色高亮

**修改** `TodoListItem.kt`：新增 `searchQuery` 参数，title 和 content 使用高亮文本
**修改** `HomeScreen.kt`：传入 `viewModel.searchQuery`

### 2.2 浮动 Undo/Redo 按钮

在 TodoEditScreen 的 RichTextEditor 区域上方叠加两个半透明浮动按钮：
- 右上角位置，编辑时显示，无历史时隐藏/禁用
- 使用 `Box` + `FloatingActionButton` 小尺寸变体
- 操作后自动淡出动画（可选）

### 2.3 Undo 增量持久化

改为 Append-Only 日志模式：
- DataStore 新增 UNDO_LOG / REDO_LOG key
- pushSnapshot 时 append 单条 JSON（而非全量替换）
- restore 时读取全部日志 → 反序列化 → 填充内存栈
- 超过 MAX_LOG_ENTRIES(100) 时裁剪旧条目

### 2.4 虚拟化增强

- MarkdownInlineRenderer 增加 `key` 参数提示
- 确保 LazyColumn item key 包含 contentFormat hash
