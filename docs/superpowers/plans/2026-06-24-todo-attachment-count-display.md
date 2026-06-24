# 待办卡片附件数量可视化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 TodoListItem 卡片上显示"🎤×N 🖼×M"格式的附件计数（父卡聚合 + 子任务行独立），并扩展 SubTask 数据模型以支持按行分摊附件。

**Architecture:** 单次 5 文件协同改动。SubTask 新增 JSON 数组字段承载图片/语音路径；DB 通过 MIGRATION_27_28 平滑升级；编辑器保存逻辑按行分摊附件（父行→TodoItem，子任务行→SubTask）；UI 层通过顶层辅助函数解析 JSON 字符串为计数。

**Tech Stack:** Jetpack Compose / Material 3 / Room / Kotlin

**关联设计文档**：[2026-06-24-todo-attachment-count-display-design.md](../specs/2026-06-24-todo-attachment-count-display-design.md)

---

## 任务清单

> **任务数优化说明**：原设计计划 5 个文件（SubTask / Database / SubTaskManager / ViewModel / TodoListItem）。经审查，`SubTaskManager` 的扩展属于"无调用方"的死代码（ViewModel 现有架构直接构造 `SubTask` 并调用 `SubTaskDao.insertAll`），按 YAGNI 原则删除。最终改动 **4 个文件**，**6 个代码 commit**（+2 docs commit）。

### Task 1: SubTask 模型扩展

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/data/model/SubTask.kt`

- [ ] **Step 1: 在 SubTask 数据类中新增字段**

打开 `SubTask.kt`，在 `data class SubTask(` 内部、`val order: Int = 0` 之后追加：

```kotlin
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

并在文件顶部 import 区域确认（已存在则跳过）：
```kotlin
import androidx.room.ColumnInfo
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过。Room 编译时会为新字段生成对应列，IDE 会提示 "warning: field is never used" 但不影响编译。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/data/model/SubTask.kt
git commit -m "feat(todo): add imagePaths and voicePaths fields to SubTask model"
```

---

### Task 2: DB Migration 27 → 28

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt`

- [ ] **Step 1: 修改 version 27 → 28**

打开 `CorgiMemoDatabase.kt`，找到 `@Database(version = 27,`（约第 32 行）改为：

```kotlin
@Database(
    entities = [...],
    version = 28,
    exportSchema = false
)
```

- [ ] **Step 2: 在 addMigrations 链尾追加 MIGRATION_27_28**

找到 `addMigrations(MIGRATION_2_3, ..., MIGRATION_26_27)`（约第 93 行），将末尾的 `MIGRATION_26_27)` 改为：

```kotlin
.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28)
```

- [ ] **Step 3: 在 companion object 末尾添加 MIGRATION_27_28 定义**

找到 `MIGRATION_26_27` 块的**结束大括号**之后（companion object 闭合之前，约第 779 行附近 `// companion object 闭合` 之前），插入：

```kotlin
    private val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 为 sub_tasks 添加附件字段（默认空字符串，向后兼容旧数据）
            database.execSQL("ALTER TABLE sub_tasks ADD COLUMN imagePaths TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE sub_tasks ADD COLUMN voicePaths TEXT NOT NULL DEFAULT ''")
        }
    }
```

- [ ] **Step 4: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过。Room 编译期会校验 migration 与 entity 字段一致。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt
git commit -m "feat(db): add MIGRATION_27_28 to add imagePaths and voicePaths to sub_tasks"
```

---

### Task 3: TodoEditViewModel 保存逻辑按行分摊

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt:940-955`

> **设计简化说明**：原设计计划在 `SubTaskManager` 中新增 `addSubTasks(..., subTaskData)` 重载与 `SubTaskSaveData` 数据类，但 ViewModel 现有架构是直接构造 `SubTask` 并通过 `SubTaskDao.insertAll` 批量插入，无需经过 `SubTaskManager`。新增重载会形成"无调用方"的死代码。因此本计划**跳过 Repository 扩展**，仅修改 ViewModel 的 `SubTask` 构造与附件收集逻辑。

- [ ] **Step 1: 修改 SubTask 构造调用与附件收集逻辑**

在 [TodoEditViewModel.kt:940](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt#L940) 附近找到 `val subTaskLines = groupLines.filter { it.isSubTask && it.text.isNotBlank() }`，**整段替换**后续的 SubTask 构造 + 附件收集代码：

**原代码**（Line 940-955）：
```kotlin
        val subTaskLines = groupLines.filter { it.isSubTask && it.text.isNotBlank() }
        val subTasks = subTaskLines.map { line ->
            SubTask(
                id = if (line.subTaskId > 0L) line.subTaskId else 0L,
                todoId = 0L, // 新建的待办，暂时无 ID
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

**替换为**：
```kotlin
        val subTaskLines = groupLines.filter { it.isSubTask && it.text.isNotBlank() }
        val subTasks = subTaskLines.map { line ->
            SubTask(
                id = if (line.subTaskId > 0L) line.subTaskId else 0L,
                todoId = 0L, // 新建的待办，暂时无 ID
                title = line.text.trim(),
                isCompleted = line.isChecked,
                order = line.order,
                // 附件按行分摊到对应 SubTask（每张图/每条语音独立计数）
                imagePaths = encodePaths(line.imagePaths),
                voicePaths = encodePaths(line.voiceAttachments.map { it.path })
            )
        }
        val hasSubTasks = subTasks.isNotEmpty()

        // 4. 收集附件（父行 → TodoItem；子任务行 → 对应 SubTask）
        //    注意：附件不再汇总到父，父只取自身首行的附件
        val firstLine = groupLines.firstOrNull { !it.isSubTask && it.text.isNotBlank() }
        val allImagePaths = firstLine?.imagePaths ?: emptyList()
        val firstVoice = firstLine?.voiceAttachments?.firstOrNull()
        val voicePath = firstVoice?.path
        val voiceDuration = firstVoice?.duration
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过。`encodePaths` 已在该 ViewModel 中定义（Line 2336），可直接使用。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt
git commit -m "refactor(todo): split attachments per-line in save logic (parent vs subtask)"
```

---

### Task 4: TodoListItem 顶层辅助函数

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`（文件底部 Top-level 私有函数区）

- [ ] **Step 1: 在文件底部（`formatDateTime` 函数之后）插入 3 个辅助函数**

在 [TodoListItem.kt:740+](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L740) 附近（`formatDateTime` 私有函数之后）插入：

```kotlin
/**
 * 解析 JSON 数组格式的图片路径字符串，返回图片数量
 *
 * @param imagePathsJson org.json.JSONArray 序列化的字符串
 * @return 图片数量（解析失败或为空返回 0）
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
 * @return 语音数量（解析失败或为空返回 0）
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
 * 聚合待办卡片附件数量（父自身 + 所有子任务）
 *
 * @param todo 父待办
 * @param subTasks 子任务列表
 * @return Pair(图片总数, 语音总数)
 */
private fun aggregateAttachmentCounts(
    todo: TodoItem,
    subTasks: List<SubTask>
): Pair<Int, Int> {
    val imageCount = parseImagePathsCount(todo.imagePaths) +
            subTasks.sumOf { parseImagePathsCount(it.imagePaths) }
    val voiceCount = (if (todo.voiceNotePath != null) 1 else 0) +
            subTasks.sumOf { parseVoicePathsCount(it.voicePaths) }
    return imageCount to voiceCount
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): add attachment count helpers (parseImagePathsCount, aggregateAttachmentCounts)"
```

---

### Task 5: 父卡提醒/附件行改造

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:339-363`

- [ ] **Step 1: 替换"提醒时间"块为"提醒 + 附件"块**

在 [TodoListItem.kt:339](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L339) 附近找到 `// 提醒时间（与编辑页 formatReminderDisplay 渲染规则一致）` 注释开始的整段 if 块，**整段替换**。

**原代码**（Line 339-363，25 行）：
```kotlin
                            // 提醒时间（与编辑页 formatReminderDisplay 渲染规则一致）
                            if (todo.reminderTime != null) {
                                val reminder = formatReminderDisplay(todo.reminderTime)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
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
                                }
                            }
```

**替换为**：
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
                                        Spacer(modifier = Modifier.width(8.dp))   // 提醒与附件间 1 个空格的间距
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

- [ ] **Step 2: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): render attachment count next to reminder on parent card"
```

---

### Task 6: 子任务行附件显示

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:655-690`（`SubTaskInTodoListItem` 函数）

- [ ] **Step 1: 在子任务标题 Text 后插入附件计数 Text**

找到 `SubTaskInTodoListItem` 函数（[Line 655-690](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L655-L690)），在标题 Text 之后（`modifier = Modifier.weight(1f)` 闭合大括号之后，函数结束大括号之前）插入：

**定位锚点**（原代码）：
```kotlin
        Text(
            text = subTask.title,
            fontSize = 14.sp,
            color = ...,
            textDecoration = ...,
            modifier = Modifier.weight(1f)
        )
    }
}
```

**新增**（在 `}` 之前插入）：
```kotlin
        // 子任务自身附件计数（独立于父卡聚合）
        val subImageCount = parseImagePathsCount(subTask.imagePaths)
        val subVoiceCount = parseVoicePathsCount(subTask.voicePaths)
        if (subImageCount > 0 || subVoiceCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            val text = buildString {
                if (subVoiceCount > 0) append("🎤×$subVoiceCount")
                if (subImageCount > 0 && subVoiceCount > 0) append(" ")
                if (subImageCount > 0) append("🖼×$subImageCount")
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

完整函数替换参考（替换 Line 655-690 整段）：

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
        // 圆形复选框
        SubTaskCheckbox(
            isCompleted = subTask.isCompleted,
            onClick = onToggleComplete
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 子任务标题
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
        val subImageCount = parseImagePathsCount(subTask.imagePaths)
        val subVoiceCount = parseVoicePathsCount(subTask.voicePaths)
        if (subImageCount > 0 || subVoiceCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            val text = buildString {
                if (subVoiceCount > 0) append("🎤×$subVoiceCount")
                if (subImageCount > 0 && subVoiceCount > 0) append(" ")
                if (subImageCount > 0) append("🖼×$subImageCount")
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

- [ ] **Step 2: 编译验证**

```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): show attachment count on each sub-task row"
```

---

### Task 7: 全量编译 + 推送

- [ ] **Step 1: 全量编译（包含 lint）**

```bash
.\gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL。**重点关注**：
- `BackupManager` / `IcsExporter` / `CsvSerializer` / `ImageExporter` 是否仍能正确处理 `SubTask` 新字段（Room 反序列化自动兼容，序列化器若用反射则自动包含新字段）
- 若有 `Unresolved reference` 错误，定位后修复

- [ ] **Step 2: 检查 git 状态**

```bash
git status
```

预期：working tree clean（除既有 `.trae/rules/` 与 `.kotlin/` 缓存外）。本地有 6 个新 commit + 之前的 docs commit。

- [ ] **Step 3: 查看 commit 列表**

```bash
git log --oneline -10
```

预期应包含：
```
docs(todo): add implementation plan for attachment count display     ← 本 plan
docs(todo): add design spec for attachment count display             ← 上一轮
feat(todo): show attachment count on each sub-task row                ← Task 6
feat(todo): render attachment count next to reminder on parent card   ← Task 5
feat(todo): add attachment count helpers (...)                       ← Task 4
refactor(todo): split attachments per-line in save logic             ← Task 3
feat(db): add MIGRATION_27_28 ...                                    ← Task 2
feat(todo): add imagePaths and voicePaths fields to SubTask model    ← Task 1
```

- [ ] **Step 4: 推送至远端**

```bash
git push origin master
```

预期：推送成功。

---

## 验证清单（人工目视）

| 场景 | 期望结果 |
|------|---------|
| 父待办有提醒 + 1 图 1 音 | `🔔 今天HH:MM  🎤×1 🖼×1`（12sp 灰） |
| 父待办有提醒 + 0 附件 | `🔔 今天HH:MM`（无附件文本，与改动前一致） |
| 父待办无提醒 + 2 图 | `🖼×2`（无 🔔，左对齐独立显示） |
| 父待办无提醒 + 0 附件 | 整行不渲染 |
| 父待办有提醒 + 已过期 | `🔔 ...已过期  🎤×1`（红色文字） |
| 2 个子任务各 1 图 | 父行 `🖼×2`；每个子行 `🖼×1` |
| 子任务 1 音 2 图 | 子行 `🎤×1 🖼×2` |
| 子任务无附件 | 子行仅显示标题 |

## 风险与回退

- **DB migration 失败**：使用 Room 标准 `ALTER TABLE ... DEFAULT ''` 模式，与既有 25 次迁移一致；失败时用户可卸载重装（数据不可恢复，但生产环境通常会自动 fallback）
- **保存逻辑拆分是行为变更**：旧 todo 重新保存前子任务附件计数为 0；如需回退到原"汇总到父"行为，`git revert <commit>` 即可
- **每个 commit 独立可回退**：7 个 commit 形成完整链路
