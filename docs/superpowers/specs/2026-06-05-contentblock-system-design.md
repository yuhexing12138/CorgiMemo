# ContentBlock 系统优化设计文档

> 日期: 2026-06-05
> 状态: 已批准，待实现

## 1. 背景与目标

当前 TodoEditScreen 的内容块（图片/语音）以静态方式渲染在编辑器上方，存在以下问题：
- 无法动态插入到内容流中任意位置
- 无法通过键盘删除
- 无拖拽排序能力
- 操作无法撤销/重做
- 数据持久化依赖分散字段（imagePaths JSON + voiceNotePath 单字段）

**目标**: 构建统一的 ContentBlock 动态内容流系统，支持拖拽排序、两步删除、全操作撤销、独立表持久化。

## 2. 需求确认记录

| 决策项 | 用户选择 |
|--------|----------|
| 持久化方案 | 新建 `content_blocks` 独立表 |
| 拖拽交互 | 长按拖拽（不显示6点 DragHandle 图标） |
| 删除方式 | 光标位置感知（首尾检测）+ 两步删除（高亮→确认） |
| 撤销粒度 | 全操作记录（文本/删块/插块/排序/格式化） |

## 3. 功能设计

### 3.1 ContentBlock 持久化（新建独立表）

#### 数据库变更

**新 Entity: ContentBlockEntity**

```kotlin
@Entity(tableName = "content_blocks", indices = [Index(value = ["todoId"])])
data class ContentBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val todoId: Long,              // 关联的待办ID
    val type: String,             // "image" | "voice"
    val filePath: String,         // 文件存储路径
    val duration: Int? = null,    // 语音时长(秒)，仅voice类型有效
    val orderIndex: Int = 0       // 排序索引
)
```

**DAO 核心方法**

| 方法 | 说明 |
|------|------|
| `getBlocksByTodoId(todoId): List<ContentBlockEntity>` | 按 orderIndex 升序查询某待办的所有内容块 |
| `insertBlocks(blocks: List<ContentBlockEntity>)` | 批量插入 |
| `deleteByTodoId(todoId: Int)` | 删除某待办所有块（保存时先清后写） |
| `deleteBlock(blockId: Long)` | 删除单个块 |
| `updateOrderIndices(blocks: List<ContentBlockEntity>)` | 批量更新排序索引 |

**Migration 25 → 26**
```kotlin
database.execSQL("""
    CREATE TABLE IF NOT EXISTS content_blocks (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        todoId INTEGER NOT NULL,
        type TEXT NOT NULL,
        filePath TEXT NOT NULL,
        duration INTEGER,
        orderIndex INTEGER NOT NULL DEFAULT 0
    )
""")
database.execSQL("CREATE INDEX IF NOT EXISTS index_content_blocks_todoId ON content_blocks (todoId)")
```

#### 保存流程

`performSave()` 中新增步骤：
1. `contentBlockDao.deleteByTodoId(todoId)` — 先清除旧数据
2. 将当前 `contentBlocks` 列表转为 Entity 列表
3. `contentBlockDao.insertBlocks(entities)` — 批量写入

### 3.2 拖拽排序（长按拖拽，无可见图标）

#### 组件选型

复用现有 **ReorderableColumn**（非 LazyColumn 版本），适合少量固定项场景。

#### 交互设计

| 状态 | 视觉表现 |
|------|----------|
| 正常 | 内容块正常显示，无额外图标 |
| 长按触发 | 被拖项：放大 1.05x + 阴影 + 浮起；其他项：半透明 |
| 拖拽移动 | 实时跟随手指，越过其他项时自动腾出空间 |
| 释放落地 | spring 动画归位 |

#### 回调处理

```kotlin
onReorder = { fromIndex, toIndex ->
    // 推送旧顺序快照到撤销栈
    viewModel.pushBlockOperation(EditOperation.BlocksReordered(contentBlocks.toList()))
    // 更新列表顺序
    val moved = contentBlocks.removeAt(fromIndex)
    contentBlocks.add(toIndex, moved)
}
```

### 3.3 Undo/Redo 统一操作记录

#### 数据结构

```kotlin
/** 统一编辑操作 - 封装所有可撤销的操作类型 */
sealed class EditOperation {
    /** 文本变更（格式化、输入等） */
    data class TextChange(val before: AnnotatedString) : EditOperation()
    /** 内容块被删除 */
    data class BlockDeleted(val blocks: List<ContentBlock>, val index: Int) : EditOperation()
    /** 内容块被插入 */
    data class BlockInserted(val index: Int) : EditOperation()
    /** 内容块排序变更 */
    data class BlocksReordered(val oldOrder: List<ContentBlock>) : EditOperation()
}
```

#### 撤销栈改造

将现有的 `_undoStack: ArrayDeque<AnnotatedString>` 扩展为 `_undoStack: ArrayDeque<EditOperation>`。

| 操作类型 | push 快照 | undo 行为 | redo 行为 |
|----------|-----------|-----------|-----------|
| 文本编辑 | TextChange(旧文本) | 恢复旧 AnnotatedString | 重做为新文本 |
| 删除块 | BlockDeleted(被删块+位置) | 在原位重新插入 | 再次删除 |
| 插入块 | BlockInserted(位置) | 移除该位置的块 | 重新插入 |
| 排序 | BlocksReordered(旧列表) | 恢复原排列顺序 | 恢复新排列顺序 |

#### 兼容性处理

为保持向后兼容：
- 旧的 DataStore 持久化数据（AnnotatedString 格式）在迁移时自动包装为 `TextChange`
- 新操作使用新的序列化格式

### 3.4 光标位置感知两步删除

#### 增强逻辑

```
用户按下 Backspace 或 Delete 键
    │
    ├─ 光标在编辑器开头 + 按 Backspace？
    │   └─ 是 → 处理前方相邻块（最后一个非Text块）
    │       ├─ 已高亮？→ 确认删除 ✓
    │       └─ 未高亮？→ 高亮该块（暖黄边框）
    │
    ├─ 光标在编辑器末尾 + 按 Delete？
    │   └─ 是 → 处理后方相邻块（第一个非Text块）
    │       ├─ 已高亮？→ 确认删除 ✓
    │       └─ 未高亮？→ 高亮该块（暖黄边框）
    │
    ├─ 编辑器为空 + 有内容块？
    │   └─ 是 → 直接走删除逻辑（忽略光标位置）
    │
    └─ 其他情况 → 不拦截，交给系统默认行为
```

#### 边界条件

- 文本输入时立即清除 `highlightedIndex`
- 拖拽排序时立即清除 `highlightedIndex`
- 插入新内容块时立即清除 `highlightedIndex`

## 4. 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `data/local/db/ContentBlockEntity.kt` | **新建** | 内容块实体类 |
| `data/local/db/ContentBlockDao.kt` | **新建** | 内容块 DAO |
| `data/local/db/AppDatabase.kt` | 修改 | 添加 content_blocks 表 + Migration 25→26 |
| `viewmodel/TodoEditViewModel.kt` | 修改 | 内容块 CRUD + EditOperation 撤销栈 + 保存集成 |
| `ui/screens/todo/TodoEditScreen.kt` | 修改 | ReorderableColumn 包裹 + 增强删除逻辑 + Undo/Redo 回调更新 |

## 5. 实现优先级

1. **P0 - 数据库层**: Entity + DAO + Migration（基础）
2. **P0 - ViewModel层**: CRUD 方法 + 撤销栈扩展 + 保存流程
3. **P1 - UI 拖拽**: ReorderableColumn 集成
4. **P1 - UI 删除增强**: 光标感知删除
