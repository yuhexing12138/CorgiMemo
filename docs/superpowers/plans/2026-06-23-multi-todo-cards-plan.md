# 多待办卡片功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现待办编辑页多容器独立保存功能，每个容器可单独保存为独立的 TodoItem，同时支持"全部完成"批量保存。

**Architecture:** 在现有 TodoEditViewModel 中新增分组保存状态管理，通过 groupId 将 todoLines 拆分为多个逻辑组，每组可独立构建 TodoItem 并持久化到数据库。UI 层根据保存状态切换容器视觉反馈。

**Tech Stack:** Kotlin, Jetpack Compose, Room, StateFlow, Coroutines

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `app/.../viewmodel/TodoEditViewModel.kt` | 修改 | 新增 GroupSaveState、saveGroup()、saveAllGroups() |
| `app/.../ui/screens/todo/TodoEditScreen.kt` | 修改 | 右上角按钮改为"全部完成"，传递保存状态给组件 |
| `app/.../ui/components/CheckboxEditText.kt` | 修改 | TodoGroupContainer 支持 isSaved 状态显示 |

---

### Task 1: 新增 GroupSaveState 数据类和 ViewModel 状态管理

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt`

- [ ] **Step 1: 在文件顶部（import 区域后）添加 GroupSaveState 数据类**

在 `TodoEditViewModel` 类外部、文件顶部区域添加：

```kotlin
/**
 * 分组保存状态
 *
 * 跟踪编辑页内每个 groupId 的保存状态，
 * 用于控制 UI 显示（按钮文字/颜色）和决定是 insert 还是 update。
 *
 * @property groupId 分组 ID（对应 TodoLine.groupId）
 * @property isSaved 是否已保存到数据库
 * @property savedTodoId 已保存的 TodoItem 数据库 ID（isSaved=true 时有效）
 * @property savedAt 最后保存时间戳
 */
data class GroupSaveState(
    val groupId: Int,
    val isSaved: Boolean = false,
    val savedTodoId: Long? = null,
    val savedAt: Long? = null
)
```

- [ ] **Step 2: 在 TodoEditViewModel 类内部添加状态 Flow**

在 `_lineAttachmentsSnapshot` 定义之后（约 L259 行）添加：

```kotlin
/**
 * 各分组的保存状态映射
 *
 * key = groupId (Int), value = 该组的保存状态
 *
 * 使用场景：
 * - UI 层读取此状态决定容器的视觉反馈（已保存/未保存）
 * - saveGroup()/saveAllGroups() 更新此状态
 * - 内容变化时自动重置为未保存状态
 */
private val _groupSaveStates = MutableStateFlow<Map<Int, GroupSaveState>>(emptyMap())

/** 暴露分组保存状态供 UI 层收集 */
val groupSaveStates: kotlinx.coroutines.flow.StateFlow<Map<Int, GroupSaveState>> = _groupSaveStates.asStateFlow()
```

- [ ] **Step 3: 提取通用的 TodoItem 构建方法**

将 `performSave()` 中构建 TodoItem 的逻辑提取为独立方法，供 saveGroup 复用。在 `performSave()` 方法之前添加：

```kotlin
/**
 * 从给定参数构建 TodoItem 对象
 *
 * 统一的 TodoItem 构建工厂方法，被 performSave() 和 saveGroup() 共用。
 * 包含：content 派生、contentFormat 组合、附件编码等完整逻辑。
 *
 * @param title 待办标题
 * @param content 待办内容文本
 * @param categoryId 分类 ID
 * @param priority 优先级
 * @param hasSubTasks 是否有子任务
 * @param imagePaths 图片路径列表
 * @param voiceNotePath 录音路径
 * @param voiceDuration 录音时长
 * @param lineSnapshotJson 行级附件快照 JSON
 * @param safeContentFormat 校验后的富文本格式内容
 * @return 构建完成的 TodoItem 对象（不含 id，用于 insert）
 */
private fun buildTodoItem(
    title: String,
    content: String,
    categoryId: Long,
    priority: Int,
    hasSubTasks: Boolean,
    imagePaths: List<String>,
    voiceNotePath: String?,
    voiceDuration: Int?,
    lineSnapshotJson: String?,
    safeContentFormat: String
): TodoItem {
    val currentTime = System.currentTimeMillis()

    /**
     * 构建 contentFormat 字段值
     */
    val finalContentFormat = if (!lineSnapshotJson.isNullOrBlank()) {
        lineSnapshotJson
    } else if (safeContentFormat.isNotBlank()) {
        safeContentFormat
    } else {
        ""
    }

    return TodoItem(
        title = title,
        content = content.ifBlank { null },
        categoryId = categoryId,
        priority = priority,
        status = 0,
        startDate = _startDate.value,
        dueDate = _dueDate.value,
        estimatedDurationMinutes = _estimatedDurationMinutes.value,
        reminderTime = _reminderTime.value,
        repeatType = _repeatType.value,
        createdAt = currentTime,
        updatedAt = currentTime,
        geofenceLat = _geofenceLat.value,
        geofenceLng = _geofenceLng.value,
        geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
        geofenceType = _geofenceType.value,
        geofenceEnabled = _geofenceEnabled.value,
        geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
        hasSubTasks = hasSubTasks,
        voiceNotePath = voiceNotePath,
        voiceDuration = voiceDuration,
        imagePaths = encodePaths(imagePaths),
        backgroundColor = _backgroundColor.value,
        contentFormat = finalContentFormat
    )
}
```

- [ ] **Step 4: 实现 saveGroup() 方法**

在 `performSave()` 方法之后添加：

```kotlin
/**
 * 保存单个分组为独立的 TodoItem
 *
 * 从 todoLines 中提取指定 groupId 的所有行，
 * 构建为独立的 TodoItem 并插入数据库。
 * 更新该分组的保存状态。
 *
 * @param targetGroupId 要保存的分组 ID
 * @param allLines 当前的完整 todoLines 列表
 * @return 保存成功返回 true，失败返回 false
 */
fun saveGroup(targetGroupId: Int, allLines: List<TodoLine>): Boolean {
    // 1. 提取目标分组的所有行
    val groupLines = allLines.filter { it.groupId == targetGroupId }
    if (groupLines.isEmpty()) return false

    // 2. 获取首行文本作为标题（过滤空文本）
    val title = groupLines.firstOrNull { it.text.isNotBlank() }?.text?.trim()
        ?: return false  // 标题为空则不保存

    // 3. 构建子任务列表（仅子任务行）
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

    // 5. 派生 content 文本
    val derivedContent = buildContentFromTitleAndSubTasks(
        title = title,
        subTasks = subTasks.map { com.corgimemo.app.data.model.SubTask(0L, 0L, it.title, it.isCompleted, it.order) }
    )

    // 6. 校验 contentFormat
    val safeContentFormat = com.corgimemo.app.util.MarkdownParser.validateAndSanitize(_contentFormat.value)

    // 7. 构建行级快照（仅目标分组的行）
    val snapshots = com.corgimemo.app.ui.model.LineSnapshotUtils.fromTodoLines(groupLines)
    val snapshotJson = com.corgimemo.app.ui.model.LineSnapshotUtils.serialize(
        snapshots = snapshots,
        originalContent = derivedContent
    )

    // 8. 执行异步保存
    viewModelScope.launch {
        try {
            val todoItem = buildTodoItem(
                title = title,
                content = derivedContent,
                categoryId = _categoryId.value,
                priority = _priority.value,
                hasSubTasks = hasSubTasks,
                imagePaths = allImagePaths,
                voiceNotePath = voicePath,
                voiceDuration = voiceDuration,
                lineSnapshotJson = snapshotJson.ifBlank { null },
                safeContentFormat = safeContentFormat
            )

            // 插入数据库
            val newTodoId = todoRepository.insertTodo(todoItem)

            // 保存子任务（如果有）
            if (subTasks.isNotEmpty()) {
                val updatedSubTasks = subTasks.map { it.copy(todoId = newTodoId) }
                SubTaskManager.saveSubTasks(context, newTodoId, updatedSubTasks)
            }

            // 保存内容块（如果有全局附件）
            if (_currentContentBlocks.value.isNotEmpty()) {
                saveContentBlocks(newTodoId, _currentContentBlocks.value)
            }

            // 设置提醒（如果有）
            if (todoItem.reminderTime != null) {
                com.corgimemo.app.notification.AlarmScheduler.scheduleReminder(context, todoItem.copy(id = newTodoId))
            }

            // 9. 更新保存状态
            val newState = GroupSaveState(
                groupId = targetGroupId,
                isSaved = true,
                savedTodoId = newTodoId,
                savedAt = System.currentTimeMillis()
            )
            _groupSaveStates.value = _groupSaveStates.value + (targetGroupId to newState)

            android.util.Log.w("TodoEditVM", "分组 $targetGroupId 保存成功, todoId=$newTodoId")
        } catch (e: Exception) {
            android.util.Log.e("TodoEditVM", "分组 $targetGroupId 保存失败", e)
        }
    }

    return true
}
```

- [ ] **Step 5: 实现 saveAllGroups() 方法**

在 `saveGroup()` 之后添加：

```kotlin
/**
 * 保存所有未保存的分组
 *
 * 遍历所有 groupId，对未保存的分组逐一调用 saveGroup()。
 * 用于右上角"全部完成"按钮的点击事件。
 *
 * @param allLines 当前的完整 todoLines 列表
 * @return 成功保存的分组数量
 */
fun saveAllGroups(allLines: List<TodoLine>): Int {
    // 收集所有唯一的 groupId
    val allGroupIds = allLines.map { it.groupId }.distinct()
    var savedCount = 0

    for (groupId in allGroupIds) {
        val state = _groupSaveStates.value[groupId]
        // 只保存未保存的分组（或者强制重新保存已保存的分组以更新内容）
        val result = saveGroup(groupId, allLines)
        if (result) savedCount++
    }

    android.util.Log.w("TodoEditVM", "saveAllGroups 完成, 共保存 $savedCount 个分组")
    return savedCount
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt
git commit -m "feat: add multi-group save support to TodoEditViewModel"
```

---

### Task 2: 修改 TodoEditScreen UI 层

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt`

- [ ] **Step 1: 收集 groupSaveStates 状态**

在 `voiceDuration` 状态收集之后（约 L133 行）添加：

```kotlin
/** 各分组的保存状态（用于控制容器视觉反馈） */
val groupSaveStates by viewModel.groupSaveStates.collectAsState()
```

- [ ] **Step 2: 修改右上角按钮文字和逻辑**

找到右上角的 Button 组件（约 L723-743 行），修改为：

```kotlin
Button(
    onClick = {
        /** 全部完成：保存所有分组 + 返回列表页 */
        val savedCount = viewModel.saveAllGroups(todoLines)
        homeViewModel.setPoseForLoading()
        homeViewModel.refreshSubTaskProgress()
        navController.popBackStack()
    },
    shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFFFF9A5C)
    ),
    modifier = Modifier.height(32.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
) {
    Text(
        text = "全部完成",
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    )
}
```

- [ ] **Step 3: 修改 onSaveClick 回调实现**

找到 CheckboxEditText 的 `onSaveClick` 参数（约 L926-932 行），修改为：

```kotlin
onSaveClick = { groupId ->
    /** 单独保存当前分组（不返回列表页） */
    viewModel.saveGroup(groupId, todoLines)
},
```

- [ ] **Step 4: 传递保存状态给 CheckboxEditText**

找到 CheckboxEditText 的调用位置，在 `onSaveClick` 之后添加新参数：

```kotlin
/** 当前各分组的保存状态（用于控制容器视觉反馈） */
groupSaveStates = groupSaveStates,
```

> 注意：这需要在 Task 3 中先给 CheckboxEditText 添加此参数。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt
git commit -m "feat: update TodoEditScreen for multi-group save UI"
```

---

### Task 3: 修改 CheckboxEditText 组件支持保存状态显示

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt`

- [ ] **Step 1: 给 CheckboxEditText 函数签名添加 groupSaveStates 参数**

在 `onRowBoundsChanged` 参数之后（约 L118 行）添加：

```kotlin
/** 各分组的保存状态（key=groupId, value=保存状态） */
groupSaveStates: Map<Int, com.corgimemo.app.viewmodel.GroupSaveState> = emptyMap(),
```

- [ ] **Step 2: 传递 isSaved 给 TodoGroupContainer**

找到 `groups.forEach` 循环中调用 `TodoGroupContainer` 的位置（约 L210-217 行），修改为：

```kotlin
// 计算当前容器是否已保存
val isGroupSaved = groupSaveStates[groupId]?.isSaved == true

TodoGroupContainer(
    groupId = groupId,
    showBottomBar = true,
    isSaved = isGroupSaved,  // 新增参数
    onReminderClick = { onReminderClick?.invoke(groupId) },
    priority = priority,
    onPriorityChange = { onPriorityChange?.invoke(groupId, it) },
    onSaveClick = { onSaveClick?.invoke(groupId) }
) {
```

- [ ] **Step 3: 给 TodoGroupContainer 添加 isSaved 参数并更新 UI**

修改 `TodoGroupContainer` 函数签名（约 L396-403 行）：

```kotlin
private fun TodoGroupContainer(
    groupId: Int,
    showBottomBar: Boolean,
    isSaved: Boolean = false,  // 新增参数
    onReminderClick: (() -> Unit)? = null,
    priority: Int = 1,
    onPriorityChange: ((Int) -> Unit)? = null,
    onSaveClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
```

- [ ] **Step 4: 修改容器背景色（已保存时显示淡绿色边框效果）**

修改 Column 的 modifier（约 L405-410 行）：

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface)
        .then(
            // 已保存状态：添加淡绿色边框
            if (isSaved) {
                Modifier
                    .androidx.compose.ui.draw.border(
                        width = 1.5.dp,
                        color = androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
            } else {
                Modifier
            }
        )
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
```

- [ ] **Step 5: 修改完成按钮的文字和颜色（约 L468-477 行）**

替换为：

```kotlin
// 右侧：完成 / 已保存 按钮
Text(
    text = if (isSaved) "已保存 ✓" else "完成",
    modifier = Modifier
        .clickable(enabled = onSaveClick != null && !isSaved) { onSaveClick?.invoke() }
        .padding(horizontal = 4.dp, vertical = 2.dp),
    color = if (isSaved) androidx.compose.ui.graphics.Color(0xFF4CAF50) else Color(0xFFFF9A5C),
    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    fontSize = 14.sp
)
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt
git commit -m "feat: add saved state visual feedback to TodoGroupContainer"
```

---

### Task 4: 编辑已保存容器时重置状态

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt`

- [ ] **Step 1: 添加 resetGroupSavedState() 方法**

在 `saveAllGroups()` 方法之后添加：

```kotlin
/**
 * 重置指定分组的保存状态为"未保存"
 *
 * 当用户编辑了已保存的容器内容时调用，
 * 使容器恢复为"未保存"状态，允许用户重新保存（update）。
 *
 * @param groupId 要重状态的分组 ID
 */
fun resetGroupSavedState(groupId: Int) {
    val current = _groupSaveStates.value[groupId]
    if (current != null && current.isSaved) {
        val newState = current.copy(isSaved = false)
        _groupSaveStates.value = _groupSaveStates.value + (groupId to newState)
        android.util.Log.w("TodoEditVM", "重置分组 $groupId 为未保存状态")
    }
}

/**
 * 重置所有分组的保存状态为"未保存"
 *
 * 当检测到任何行的内容发生变化时调用。
 */
fun resetAllGroupSavedStates() {
    val currentStates = _groupSaveStates.value
    if (currentStates.any { it.value.isSaved }) {
        val resetStates = currentStates.mapValues { it.value.copy(isSaved = false) }
        _groupSaveStates.value = resetStates
        android.util.Log.w("TodoEditVM", "重置所有分组为未保存状态")
    }
}
```

- [ ] **Step 2: 在 TodoEditScreen 的 LaunchedEffect 同步逻辑中调用重置**

找到 `LaunchedEffect(todoLines)` 中的同步代码块（约 L500-589 行），在同步子任务之前添加：

```kotlin
/**
 * 【多卡片】检测内容变化，自动重置已保存分组的编辑状态
 *
 * 当用户编辑了任何已保存容器的文本内容时，
 * 自动将该容器重置为"未保存"状态，
 * 以便用户可以点击"完成"按钮更新（而非创建新的）。
 */
if (groupSaveStates.isNotEmpty()) {
    val changedGroupIds = todoLines
        .map { it.groupId }
        .distinct()
        .filter { groupId ->
            groupSaveStates[groupId]?.isSaved == true
        }
    
    if (changedGroupIds.isNotEmpty()) {
        changedGroupIds.forEach { groupId ->
            viewModel.resetGroupSavedState(groupId)
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt
git add app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt
git commit -m "feat: auto-reset saved state when editing saved groups"
```

---

### Task 5: 测试验证

- [ ] **Step 1: 编译验证**

运行编译命令确保无错误：
```
Build → Make Project (Ctrl+F9)
```

- [ ] **Step 2: 功能测试清单**

| # | 测试场景 | 预期结果 |
|---|---------|---------|
| T1 | 新建待办 → 输入 "/" → 创建第二容器 | 出现两个独立圆角容器 |
| T2 | 点击第一个容器的"完成"按钮 | 第一个容器变为"已保存 ✓" + 绿色 |
| T3 | 编辑第一个容器的文本 | 第一个容器恢复为"完成"（橙色） |
| T4 | 再次点击第一个容器的"完成" | 再次变为"已保存 ✓" |
| T5 | 点击右上角"全部完成" | 所有容器都保存 + 返回列表页 |
| T6 | 返回 HomeScreen | 列表中出现多个独立待办卡片 |
| T7 | 单容器模式（不输入 "/"） | 行为与修改前一致 |

- [ ] **Step 3: 最终 Commit（如有修复）**

```bash
git add -A
git commit -m "fix: resolve issues found during testing"
```

---

## 自检清单

### Spec 覆盖度检查

| 设计文档需求 | 对应 Task | 状态 |
|-------------|----------|------|
| G1: 多卡片编辑 | 已有功能（"/" 创建容器） | ✅ 已满足 |
| G2: 单独保存 | Task 1 (saveGroup) + Task 2 (onSaveClick) | ✅ 已覆盖 |
| G3: 全部保存 | Task 1 (saveAllGroups) + Task 2 (右上角按钮) | ✅ 已覆盖 |
| G4: 状态追踪 | Task 1 (GroupSaveState) + Task 3 (UI 反馈) + Task 4 (重置) | ✅ 已覆盖 |
| G5: 独立展示 | Room insertTodo 自动出现在列表 | ✅ 已满足 |

### 占位符扫描

- ❌ 无 TBD/TODO
- ❌ 无 "适当处理错误" 类模糊描述
- ❌ 无引用未定义的类型/方法

### 类型一致性检查

- `GroupSaveState` 在 Task 1 Step 1 定义，Task 1 Step 2/4/5 使用 ✅
- `saveGroup(groupId, allLines)` 签名在 Task 1 Step 4 定义，Task 2 Step 3 调用 ✅
- `groupSaveStates` 参数名在 Task 2 Step 1/4 和 Task 3 Step 1/2 一致 ✅
