# 待办卡片附件数量可视化

**日期**：2026-06-24
**类型**：UI 增强 + 数据模型扩展 + 编辑流改造
**状态**：已批准，待实施

## 一、背景与目标

### 1.1 现状

- `TodoItem` 持 `imagePaths: String`（JSON 数组，默认 `""`）与 `voiceNotePath: String?` / `voiceDuration: Int?`
- `SubTask` **不持任何附件字段**（仅 `id, todoId, title, isCompleted, createdAt, completedAt, order`）
- `TodoLine`（编辑页内模型）已支持每行 `imagePaths: List<String>` + `voiceAttachments: List<VoiceAttachment>`
- **保存逻辑** [TodoEditViewModel.kt:952-955](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt#L952-L955) 把所有行的附件**汇总**到父待办：
  ```kotlin
  val allImagePaths = groupLines.flatMap { it.imagePaths }.distinct()
  val firstVoice = groupLines.flatMap { it.voiceAttachments }.firstOrNull()
  ```
- 卡片显示层（[TodoListItem.kt:387-402](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L387-L402)）仅在"分类行"中显示 `🎤 Ns`（仅父待办）
- 子任务行（[TodoListItem.kt:655-690](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L655-L690)）**不显示任何附件信息**

### 1.2 目标

1. **SubTask 模型扩展**：支持图片 + 语音附件
2. **保存逻辑改造**：每行附件落到对应实体（父行 → TodoItem；子任务行 → SubTask）
3. **卡片显示**：
   - 父卡：聚合显示（父自身 + 所有子任务），格式 `🎤×N 📷×M`，位于提醒行右侧
   - 子任务行：独立显示自身附件计数
4. **DB 平滑升级**：MIGRATION_27_28 添加新列，向后兼容

### 1.3 设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 无 reminder 时的位置 | 与 reminder 同 Row，无 reminder 时左对齐显示附件 | 视觉一致性，行为可预测 |
| 附件统计粒度 | 1 个 path = 1 个附件（每行多张/多条独立计数） | 用户确认："同一行有两条则算两条" |
| 汇总 vs 拆分 | 父行附件只算父；子任务行附件只算各自；汇总 = 父 + 所有子任务相加 | 用户确认 |
| 图标选择 | 🎤（语音）+ 🖼（图片）Emoji | 与现有"分类行" Emoji 风格一致 |
| SubTask 语音字段 | `voicePaths: String`（JSON 数组） | 与 `imagePaths` 对称，支持多条 |
| 父 TodoItem 语音字段 | 保持 `voiceNotePath: String?` + `voiceDuration: Int?` | 不动既有数据；汇总时按 1 条计算 |

## 二、范围

### 2.1 包含

- `SubTask.kt`：新增 `imagePaths: String = ""` + `voicePaths: String = ""` 两字段
- `CorgiMemoDatabase.kt`：MIGRATION_27_28 升级 sub_tasks 表
- `SubTaskManager.kt`：扩展 `addSubTasks` 接受附件；新增 `updateSubTaskAttachments`
- `TodoEditViewModel.kt`：保存逻辑按行分摊附件（父行→TodoItem，子任务行→SubTask）
- `TodoListItem.kt`：附件计数 UI（父卡 + 子任务行）
- 新增 `TodoListItem.kt` 顶层辅助函数 `parseImagePathsCount` / `parseVoicePathsCount`

### 2.2 不包含（明确边界）

- **不动** `TodoItem` 数据模型（沿用既有 `imagePaths` + `voiceNotePath/voiceDuration`）
- **不动** `TodoLine`（编辑器内模型已支持）
- **不动** `CheckboxEditText` 行的 UI（编辑页本身已支持每行附件）
- **不动** 提醒行 / 子任务进度 / 优先级竖条 / 卡片其他布局
- **不实现** 附件点击交互（保持静态显示）
- **不修改** `CorgiMemoApplication.kt` / `BackupManager.kt` / 导出器（备份兼容是后续工作）

## 三、架构与组件

### 3.1 改动文件清单

| 文件 | 改动类型 | 改动点 |
|------|---------|--------|
| [SubTask.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/SubTask.kt) | 修改 | 新增 imagePaths / voicePaths 字段 |
| [CorgiMemoDatabase.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt) | 修改 | version 27→28 + MIGRATION_27_28 + 链上 addMigrations |
| [SubTaskManager.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/SubTaskManager.kt) | 修改 | 新增 SubTaskSaveData 数据类 + 扩展 addSubTasks |
| [TodoEditViewModel.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt) | 修改 | 拆分附件分摊到对应实体（核心逻辑） |
| [TodoListItem.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt) | 修改 | 父卡附件 Row + 子任务行附件 Text + 辅助函数 |

### 3.2 改动 1：SubTask 数据模型

**位置**：[SubTask.kt:25-34](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/SubTask.kt#L25-L34)

**新增字段**（在现有 7 字段后追加）：

```kotlin
data class SubTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val todoId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val order: Int = 0,

    /**
     * 子任务图片附件路径 JSON 数组
     *
     * 与 [TodoItem.imagePaths] 编码规则一致：org.json.JSONArray 序列化的字符串。
     * 空字符串表示无图片。
     */
    @ColumnInfo(defaultValue = "")
    val imagePaths: String = "",

    /**
     * 子任务语音附件路径 JSON 数组
     *
     * 支持同一子任务挂载多条语音（每条 = 1 个附件计数）。
     * 与图片字段对称，采用 JSON 数组而非单字符串。
     * 空字符串表示无语音。
     */
    @ColumnInfo(defaultValue = "")
    val voicePaths: String = ""
)
```

**说明**：
- 故意不引入 `voiceDuration`：JSON 数组中只存 path，时长由 UI 层在播放时按需读取（与 `TodoItem.voiceDuration` 单条兼容路径不同）
- 旧数据迁移：`imagePaths` / `voicePaths` 默认 `""`，Room migration 添加 `DEFAULT ''` 即可

### 3.3 改动 2：DB Migration

**位置**：[CorgiMemoDatabase.kt:32-33](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt#L32-L33)（version）

**修改 1**：version `27` → `28`

**修改 2**：在 [Line 93](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt#L93) 的 `addMigrations(...)` 链尾追加 `MIGRATION_27_28`：

```kotlin
.addMigrations(MIGRATION_2_3, ..., MIGRATION_26_27, MIGRATION_27_28)
```

**修改 3**：在 [Line 779](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt#L779) 附近（companion object 闭合前）新增：

```kotlin
private val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 为 sub_tasks 添加附件字段（默认空字符串，向后兼容旧数据）
        database.execSQL("ALTER TABLE sub_tasks ADD COLUMN imagePaths TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE sub_tasks ADD COLUMN voicePaths TEXT NOT NULL DEFAULT ''")
    }
}
```

### 3.4 改动 3：Repository 扩展

**位置**：[SubTaskManager.kt:78](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/SubTaskManager.kt#L78) 附近

**新增数据类**（Top-level，文件顶部）：

```kotlin
/**
 * 子任务保存数据：标题 + 附件元数据
 *
 * 用于把 TodoLine（含附件）转换为 SubTask 数据库行。
 */
data class SubTaskSaveData(
    /** 子任务 ID（>0 = 已存在；0 = 新建） */
    val id: Long = 0L,
    val title: String,
    val isCompleted: Boolean = false,
    val order: Int = 0,
    /** 图片路径 JSON 字符串（已编码） */
    val imagePaths: String = "",
    /** 语音路径 JSON 字符串（已编码） */
    val voicePaths: String = ""
)
```

**扩展 `addSubTasks`**（在现有 [Line 78-95](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/SubTaskManager.kt#L78-L95) 之后新增重载）：

```kotlin
/**
 * 添加子任务（带附件元数据）
 *
 * 取代旧的 `addSubTasks(context, todoId, titles: List<String>)`。
 * 编辑器按行分摊附件时调用。
 */
suspend fun addSubTasks(context: Context, todoId: Long, subTaskData: List<SubTaskSaveData>) {
    val database = CorgiMemoDatabase.getDatabase(context)
    val maxOrder = database.subTaskDao().getMaxOrder(todoId) ?: 0
    val subTasks = subTaskData.mapIndexed { index, data ->
        SubTask(
            id = if (data.id > 0L) data.id else 0L,
            todoId = todoId,
            title = data.title,
            isCompleted = data.isCompleted,
            order = if (data.order > 0) data.order else maxOrder + index + 1,
            createdAt = System.currentTimeMillis(),
            imagePaths = data.imagePaths,
            voicePaths = data.voicePaths
        )
    }
    database.subTaskDao().insertAll(subTasks)
}
```

> 原 `addSubTasks(context, todoId, titles: List<String>)` 保留**不删除**（其他调用方可能仍在用，避免破坏性改动）；如确认无引用可清理，本次保留。

### 3.5 改动 4：ViewModel 保存逻辑

**位置**：[TodoEditViewModel.kt:940-948](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com\corgimemo/app/viewmodel/TodoEditViewModel.kt#L940-L948)

**修改**：`SubTask(...)` 构造扩展 + 附件分摊

**修改前**：
```kotlin
val subTaskLines = groupLines.filter { it.isSubTask && it.text.isNotBlank() }
val subTasks = subTaskLines.map { line ->
    SubTask(
        id = if (line.subTaskId > 0L) line.subTaskId else 0L,
        todoId = 0L,
        title = line.text.trim(),
        isCompleted = line.isChecked,
        order = line.order
    )
}
val hasSubTasks = subTasks.isNotEmpty()

// 4. 收集附件
val allImagePaths = groupLines.flatMap { it.imagePaths }.distinct()
val firstVoice = groupLines.flatMap { it.voiceAttachments }.firstOrNull()
val voicePath = firstVoice?.path
val voiceDuration = firstVoice?.duration
```

**修改后**：
```kotlin
val subTaskLines = groupLines.filter { it.isSubTask && it.text.isNotBlank() }
val subTasks = subTaskLines.map { line ->
    SubTask(
        id = if (line.subTaskId > 0L) line.subTaskId else 0L,
        todoId = 0L,
        title = line.text.trim(),
        isCompleted = line.isChecked,
        order = line.order,
        imagePaths = encodePaths(line.imagePaths),
        voicePaths = encodePaths(line.voiceAttachments.map { it.path })
    )
}
val hasSubTasks = subTasks.isNotEmpty()

// 4. 收集附件（分摊到对应实体）
//    - 父行（首行）附件 → TodoItem（沿用 voiceNotePath 单值）
//    - 子任务行附件 → 对应 SubTask（已在 subTasks 构造时赋值）
val firstLine = groupLines.firstOrNull { !it.isSubTask && it.text.isNotBlank() }
val allImagePaths = firstLine?.imagePaths ?: emptyList()
val firstVoice = firstLine?.voiceAttachments?.firstOrNull()
val voicePath = firstVoice?.path
val voiceDuration = firstVoice?.duration
```

**关键变更**：
- `allImagePaths` 从"所有行汇总"改为"仅父行"
- `firstVoice` 从"所有行取第一个"改为"仅父行取第一个"
- 子任务行附件已经通过 `SubTask(...)` 构造参数持久化

**注**：此变更影响既有"附件汇总到父"的旧行为。如有用户依赖旧行为，回归测试需关注。

### 3.6 改动 5：TodoListItem 辅助函数

**位置**：[TodoListItem.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt) 文件底部（Top-level 私有函数）

**新增**（参考 [formatCompletedTime](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L569) 风格）：

```kotlin
/**
 * 解析 JSON 数组格式的图片路径字符串，返回图片数量
 *
 * @param imagePathsJson org.json.JSONArray 序列化的字符串
 * @return 图片数量（解析失败返回 0）
 */
private fun parseImagePathsCount(imagePathsJson: String): Int {
    if (imagePathsJson.isBlank()) return 0
    return try {
        org.json.JSONArray(imagePathsJson).length()
    } catch (e: Exception) {
        0
    }
}

/**
 * 解析 JSON 数组格式的语音路径字符串，返回语音数量
 *
 * @param voicePathsJson org.json.JSONArray 序列化的字符串
 * @return 语音数量（解析失败返回 0）
 */
private fun parseVoicePathsCount(voicePathsJson: String): Int {
    if (voicePathsJson.isBlank()) return 0
    return try {
        org.json.JSONArray(voicePathsJson).length()
    } catch (e: Exception) {
        0
    }
}

/**
 * 聚合待办卡片附件数量（含父自身 + 所有子任务）
 *
 * @param todo 父待办
 * @param subTasks 子任务列表
 * @return Pair(图片总数, 语音总数)
 */
private fun aggregateAttachmentCounts(
    todo: TodoItem,
    subTasks: List<SubTask>
): Pair<Int, Int> {
    // 图片：父自身 imagePaths + 所有 SubTask.imagePaths
    val imageCount = parseImagePathsCount(todo.imagePaths) +
            subTasks.sumOf { parseImagePathsCount(it.imagePaths) }
    // 语音：父 voiceNotePath 计 1 + 所有 SubTask.voicePaths 数量
    val voiceCount = (if (todo.voiceNotePath != null) 1 else 0) +
            subTasks.sumOf { parseVoicePathsCount(it.voicePaths) }
    return imageCount to voiceCount
}
```

### 3.7 改动 6：父卡附件行渲染

**位置**：[TodoListItem.kt:339-363](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L339-L363) 的"提醒时间"块

**修改**：将 `if (todo.reminderTime != null) { ... }` 改为 `if (todo.reminderTime != null || aggregateCount != (0,0)) { ... }` 并在 Text 后追加附件 Text

**修改前**（[Line 339-363](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L339-L363)）：
```kotlin
// 提醒时间（与编辑页 formatReminderDisplay 渲染规则一致）
if (todo.reminderTime != null) {
    val reminder = formatReminderDisplay(todo.reminderTime)
    Row(...) {
        Icon(Icons.Default.Alarm, ...)
        Spacer(...)
        Text(reminder.text, ...)
    }
}
```

**修改后**：
```kotlin
// 提醒时间 + 附件数量（聚合：父 + 所有子任务）
val aggregateCounts = aggregateAttachmentCounts(todo, subTasks)
if (todo.reminderTime != null || aggregateCounts.first > 0 || aggregateCounts.second > 0) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        if (todo.reminderTime != null) {
            val reminder = formatReminderDisplay(todo.reminderTime)
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = if (reminder.isOverdue) "已过期提醒" else "提醒",
                tint = if (reminder.isOverdue) Color(0xFFDC2626)
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = reminder.text,
                fontSize = 12.sp,
                color = if (reminder.isOverdue) Color(0xFFDC2626)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (reminder.isOverdue)
                    androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
            )
            Spacer(modifier = Modifier.width(8.dp))   // 提醒与附件之间 1 个空格的间距（8dp 约等于 12sp 文字下 1 个中文字符宽度）
        }

        // 附件计数（图片 + 语音）
        if (aggregateCounts.first > 0 || aggregateCounts.second > 0) {
            val attachmentText = buildString {
                if (aggregateCounts.second > 0) append("🎤×${aggregateCounts.second}")
                if (aggregateCounts.first > 0 && aggregateCounts.second > 0) append(" ")  // 两种附件间 1 个空格
                if (aggregateCounts.first > 0) append("🖼×${aggregateCounts.first}")
            }
            Text(
                text = attachmentText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

**格式样例**：
- 仅语音：`🎤×2`
- 仅图片：`🖼×3`
- 都有：`🎤×2 🖼×3`
- 有提醒+附件：`今天09:25  🎤×2 🖼×3`（提醒与附件间 8dp 间距，符合"1 个空格的间距"）
- 有提醒无附件：`今天09:25`（与原行为完全一致）

### 3.8 改动 7：子任务行附件显示

**位置**：[TodoListItem.kt:655-690](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L655-L690) 的 `SubTaskInTodoListItem`

**修改**：在子任务标题 Text 后追加附件计数 Text（仅在该 SubTask 有附件时显示）

**修改后**：
```kotlin
@Composable
private fun SubTaskInTodoListItem(
    subTask: SubTask,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubTaskCheckbox(...)
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = subTask.title,
            fontSize = 14.sp,
            color = if (subTask.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (subTask.isCompleted) {
                TextDecoration.LineThrough
            } else {
                TextDecoration.None
            },
            modifier = Modifier.weight(1f)
        )

        // 子任务自身附件计数（独立于父卡聚合）
        val imageCount = parseImagePathsCount(subTask.imagePaths)
        val voiceCount = parseVoicePathsCount(subTask.voicePaths)
        if (imageCount > 0 || voiceCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            val text = buildString {
                if (voiceCount > 0) append("🎤×$voiceCount")
                if (imageCount > 0 && voiceCount > 0) append(" ")
                if (imageCount > 0) append("🖼×$imageCount")
            }
            Text(
                text = text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

## 四、数据流

### 保存路径
```
TodoEditScreen (用户操作)
    ↓
TodoLine[].imagePaths / voiceAttachments (编辑器内状态)
    ↓ saveTodoForGroup
SubTask(..., imagePaths=JSON, voicePaths=JSON)  ← 新行为：按行分摊
TodoItem(..., imagePaths=JSON, voiceNotePath=first.path)  ← 新行为：仅父行
    ↓
SubTaskDao.insertAll / TodoDao.insert
    ↓
SQLite (sub_tasks.imagePaths / sub_tasks.voicePaths)
```

### 读取路径
```
HomeScreen → TodoListItem(todo, subTasks)
    ↓
aggregateAttachmentCounts(todo, subTasks)   ← 新增辅助函数
    ↓
UI 渲染：🎤×N 🖼×M
```

## 五、错误处理

- **JSON 解析失败**：`parseImagePathsCount` / `parseVoicePathsCount` 返回 0，不抛异常
- **DB migration 失败**：用户可重装应用（迁移脚本 `ALTER TABLE ... DEFAULT ''` 标准 Room 模式）
- **旧数据兼容**：旧 SubTask 行的 `imagePaths=''` / `voicePaths=''` 自然显示 0 附件，无 NPE 风险

## 六、测试与验证

### 6.1 编译验证
- 增量编译 + Lint：`.\gradlew :app:assembleDebug` 通过
- 重点关注：`SubTask` 新字段对 `BackupManager` / `IcsExporter` / `CsvSerializer` / `ImageExporter` 的影响
  - 序列化器若使用 `data class copy()` 或反射读写字段，会自动包含新字段（**预期**）
  - 若使用 `SELECT` 列举字段，需手动补全（需检查）

### 6.2 视觉验证清单

| 场景 | 期望结果 |
|------|---------|
| 父待办有提醒 + 1 图 1 音 | `🔔 今天HH:MM  🎤×1 🖼×1`（12sp 灰） |
| 父待办有提醒 + 0 附件 | `🔔 今天HH:MM`（无附件文本） |
| 父待办无提醒 + 2 图 | `🖼×2`（无 🔔，左对齐） |
| 父待办无提醒 + 0 附件 | 整行不渲染 |
| 父待办有提醒 + 已过期 | `🔔 ...已过期  🎤×1`（红色文字） |
| 2 个子任务各 1 图 | 父行 `🖼×2`；每个子行 `🖼×1` |
| 子任务 1 音 2 图 | 子行 `🎤×1 🖼×2` |
| 子任务无附件 | 子行仅显示标题 |

### 6.3 数据迁移验证
- 安装新版本前存在 `sub_tasks` 表的老用户：升级后 `imagePaths=''` `voicePaths=''`，显示 0 附件，无 crash
- 老 TodoItem 中 `imagePaths` 已存的 JSON 数据不变
- 编辑老 todo 时，附件按新规则分摊到对应 SubTask/父

### 6.4 回归验证
- 右滑删除、批量选择、长按菜单、拖拽手柄等交互不受影响
- 主标题、分类、语音备注、关联提示、开始时间、倒计时、截止时间、进度条、PriorityBar 等元素位置与样式不变
- 编辑页（CheckboxEditText）的图片/语音 UI 完全不变（编辑页本身已支持每行）

## 七、风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| 保存逻辑拆分导致老数据丢失 | 中 | 旧 `imagePaths` 字段在 TodoItem 仍保留；用户重新保存前子任务附件计数为 0（已存在附件不影响显示） |
| 备份/导出器未识别新字段 | 中 | 编译时即发现；本次先做 UI 与模型，备份适配作为后续独立任务 |
| SubTask 附件数据迁移遗漏 | 低 | 默认 `''`，不需主动迁移 |
| Emoji 渲染跨设备差异 | 低 | 项目现有"分类行"已用 Emoji，未发现问题 |
| DB 迁移失败导致 crash | 低 | 使用 Room 标准 `ALTER TABLE ... DEFAULT ''` 模式，与既有 25 次迁移一致 |
