# V2.2 contentFormat 深度优化设计文档

> 日期：2026-06-03
> 状态：已批准
> 前置依赖：V2.1 contentFormat 基础闭环 + V2.1 优化（防抖/校验/预览/Undo）

## 1. 功能清单

| # | 功能 | 优先级 | 复杂度 |
|---|------|--------|--------|
| 1 | MarkdownInlineRenderer 缓存 (remember) | P0 | 低（1行） |
| 2 | 富文本搜索增强 (contentFormat 搜索) | P0 | 低（3行） |
| 3 | 键盘快捷键 Ctrl+Z / Ctrl+Y | P1 | 中 |
| 4 | Undo 栈持久化 (AnnotatedString JSON 序列化) | P2 | 高 |

## 2. 功能详细设计

### 2.1 MarkdownInlineRenderer 缓存

**文件**: `MarkdownInlineRenderer.kt`
**改动**: `safeParse()` 调用包裹 `remember(markdown)`

```kotlin
val cachedAnnotatedString = remember(markdown) {
    com.corgimemo.app.util.MarkdownParser.safeParse(markdown)
}
```

### 2.2 富文本搜索增强

**文件**: `HomeViewModel.kt` 第 171-176 行
**改动**: 搜索过滤增加 `contentFormat.stripMarkdown()` 匹配

### 2.3 键盘快捷键

**文件**: `RichTextEditor.kt`
**改动**: 新增可选参数 `onUndo`/`onRedo` + `Modifier.onPreviewKeyEvent`

**文件**: `TodoEditScreen.kt`
**改动**: 传入 onUndo/onRedo 回调绑定到 ViewModel

### 2.4 Undo 栈持久化

**新建文件**: `AnnotatedStringSerializer.kt` (~120行)
- serialize/deserialize AnnotatedString ↔ JSON
- serializeList/deserializeList List<AnnotatedString> ↔ JSON

**修改文件**: `CorgiPreferences.kt` (+20行)
- 新增 UNDO_STACK / UNDO_STACK_TODO_ID key
- saveUndoStack / getUndoStack 方法

**修改文件**: `TodoEditViewModel.kt` (+40行)
- pushSnapshot/undo/redo 后异步保存到 DataStore
- loadTodo() 时从 DataStore 恢复 undoStack
- DisposableEffect.onDispose 最终保存

## 3. 文件变更清单

| 文件 | 操作 | 改动量 |
|------|------|--------|
| `MarkdownInlineRenderer.kt` | 修改 | +1 行 |
| `HomeViewModel.kt` | 修改 | +3 行 |
| `RichTextEditor.kt` | 修改 | +25 行 |
| `TodoEditScreen.kt` | 修改 | +8 行 |
| **`AnnotatedStringSerializer.kt`** | **新建** | ~120 行 |
| `CorgiPreferences.kt` | 修改 | +20 行 |
| `TodoEditViewModel.kt` | 修改 | +40 行 |

## 4. 实施顺序

1. 缓存 → 2. 搜索 → 3. 快捷键 → 4. 持久化
