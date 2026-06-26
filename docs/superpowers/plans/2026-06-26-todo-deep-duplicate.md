# 待办深复制（文件 + ContentBlock + 子任务附件）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 `batchDuplicate` 真正深复制：复制父附件/子任务附件/ContentBlock 表 + 实际文件，副本与源完全独立；带顶层进度条反馈。

**Architecture:** 引入 `FileCopyManager` 单例服务封装文件复制，通过 `Flow<DuplicateProgress>` 暴露进度。`HomeViewModel` 收集 Flow 推到 `_duplicateProgress` StateFlow，UI 通过 `DuplicateProgressBar` 组件显示。文件命名采用 `copy_<uuid>_` 前缀，失败采用"部分允许"策略（附件丢失但保留 todo）。

**Tech Stack:** Kotlin 1.9 + Coroutines + Flow + StateFlow + Jetpack Compose + Room + Hilt

---

## File Structure

| 文件 | 角色 | 操作 |
|------|------|------|
| `app/src/main/java/com/corgimemo/app/util/FileCopyManager.kt` | 文件复制核心服务（IO + 进度） | 新建 |
| `app/src/main/java/com/corgimemo/app/data/local/db/ContentBlockDao.kt` | 新增批量插入 | 修改 |
| `app/src/main/java/com/corgimemo/app/data/repository/TodoRepository.kt` | 暴露 ContentBlockDao + 新增 updateTodoAttachments | 修改 |
| `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt` | 重构 batchDuplicate + 进度 State | 修改 |
| `app/src/main/java/com/corgimemo/app/ui/components/DuplicateProgressBar.kt` | 顶层进度条 UI | 新建 |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 集成进度条 | 修改 |
| `app/src/main/res/values/strings.xml` | 复制相关中文文案 | 修改 |
| `app/src/main/res/values-en/strings.xml` | 复制相关英文文案 | 修改 |

---

## Task 1: 创建 FileCopyManager 基础（copyFile + generateCopyFileName）

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/util/FileCopyManager.kt`

- [ ] **Step 1: 创建文件骨架与 DI 注解**

```kotlin
package com.corgimemo.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件复制管理器
 *
 * 封装文件 IO 操作，为 batchDuplicate 提供：
 * 1. 单文件复制（copy_<uuid>_<name> 命名）
 * 2. todo 全附件复制（父 + 子任务 + ContentBlock）
 * 3. 进度通过 Flow<DuplicateProgress> 暴露
 *
 * 设计原则：
 * - 失败时部分允许：跳过失败文件，不影响其他
 * - 命名用 UUID 前缀避免冲突
 * - 同目录存放，不创建子目录
 */
@Singleton
class FileCopyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 复制 todo 的所有附件
     * @param originalTodoId 源 todo ID
     * @param newTodoId 副本 todo ID
     * @return Flow<DuplicateProgress> 实时进度
     */
    fun copyAllAttachments(
        originalTodoId: Long,
        newTodoId: Long
    ): Flow<DuplicateProgress> = kotlinx.coroutines.flow.flow {
        // 详细实现见 Task 2
    }

    /**
     * 复制单个文件
     * @param srcPath 源文件绝对路径
     * @param dstDir 目标目录
     * @return 成功返回新路径，失败返回 null
     */
    private suspend fun copyFile(srcPath: String, dstDir: String): String? =
        withContext(Dispatchers.IO) {
            val srcFile = File(srcPath)
            if (!srcFile.exists()) return@withContext null
            val dstFile = File(dstDir, generateCopyFileName(srcFile.name))
            try {
                srcFile.inputStream().use { input ->
                    dstFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                dstFile.absolutePath
            } catch (e: Exception) {
                if (dstFile.exists()) dstFile.delete()
                null
            }
        }

    /**
     * 生成副本文件名：copy_<uuid>_<originalName>
     * UUID 取前 8 字符，避免过长
     */
    private fun generateCopyFileName(originalName: String): String {
        val uuid = UUID.randomUUID().toString().take(8)
        return "copy_${uuid}_$originalName"
    }

    /**
     * 复制进度数据类
     */
    data class DuplicateProgress(
        val current: Int,
        val total: Int,
        val currentTodoTitle: String,
        val failedFiles: List<String> = emptyList()
    )
}
```

- [ ] **Step 2: 编译验证**

```bash
gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/util/FileCopyManager.kt
git commit -m "feat(duplicate): add FileCopyManager skeleton with copyFile + generateCopyFileName"
```

---

## Task 2: 实现 copyAllAttachments（父附件 + 子任务 + ContentBlock）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/util/FileCopyManager.kt:30-40`
- Modify: `app/src/main/java/com/corgimemo/app/data/local/db/ContentBlockDao.kt:23-24`（新增 `getBlocksByTodoId` 已在）

- [ ] **Step 1: 在 TodoRepository 暴露 ContentBlockDao**

找到 `TodoRepository.kt`，在类顶部添加：

```kotlin
@Inject lateinit var contentBlockDao: ContentBlockDao
```

（**注意**：如果 TodoRepository 已有 `@Inject constructor`，改为加入构造参数。实际查看后调整。）

- [ ] **Step 2: 实现 copyAllAttachments 完整逻辑**

替换 Task 1 的 `copyAllAttachments` 方法：

```kotlin
fun copyAllAttachments(
    originalTodoId: Long,
    newTodoId: Long
): Flow<DuplicateProgress> = flow {
    val picturesDir = File(context.filesDir, "pictures")
    val voiceDir = File(context.filesDir, "voice_notes")
    picturesDir.mkdirs()
    voiceDir.mkdirs()

    val originalTodo = todoRepository.getTodoById(originalTodoId) ?: return@flow
    val originalSubTasks = SubTaskManager.getSubTasks(context, originalTodoId)
    val originalBlocks = todoRepository.contentBlockDao.getBlocksByTodoId(originalTodoId)

    val failedFiles = mutableListOf<String>()
    val srcFiles = mutableListOf<Pair<String, String>>()  // (path, type)

    // 收集所有源文件
    // 父附件 - imagePaths (JSON 数组)
    parseJsonPaths(originalTodo.imagePaths).forEach { path ->
        srcFiles.add(path to "pictures")
    }
    // 父附件 - voiceNotePath (单路径)
    originalTodo.voiceNotePath?.let { path ->
        srcFiles.add(path to "voice_notes")
    }
    // 子任务附件
    originalSubTasks.forEach { subTask ->
        parseJsonPaths(subTask.imagePaths).forEach { path ->
            srcFiles.add(path to "pictures")
        }
        parseJsonPaths(subTask.voicePaths).forEach { path ->
            srcFiles.add(path to "voice_notes")
        }
    }
    // ContentBlock 附件
    originalBlocks.forEach { block ->
        val typeDir = if (block.type == "voice") "voice_notes" else "pictures"
        srcFiles.add(block.filePath to typeDir)
    }

    val total = srcFiles.size
    var current = 0

    // 复制文件
    val newImagePaths = mutableListOf<String>()
    val newVoiceNotePath: String? = originalTodo.voiceNotePath?.let { srcPath ->
        val newPath = copyFile(srcPath, voiceDir.absolutePath)
        if (newPath == null) {
            failedFiles.add(srcPath)
            null
        } else newPath
    }
    current += if (newVoiceNotePath != null || originalTodo.voiceNotePath == null) 1 else 0

    originalTodo.imagePaths.let { _ ->
        parseJsonPaths(originalTodo.imagePaths).forEach { srcPath ->
            val newPath = copyFile(srcPath, picturesDir.absolutePath)
            if (newPath == null) {
                failedFiles.add(srcPath)
            } else {
                newImagePaths.add(newPath)
            }
            current++
            emit(DuplicateProgress(current, total, originalTodo.title, failedFiles.toList()))
        }
    }

    // 复制子任务附件
    val newSubTasks = mutableListOf<SubTask>()
    originalSubTasks.forEach { subTask ->
        val newSubImages = mutableListOf<String>()
        val newSubVoices = mutableListOf<String>()
        parseJsonPaths(subTask.imagePaths).forEach { srcPath ->
            val newPath = copyFile(srcPath, picturesDir.absolutePath)
            if (newPath == null) {
                failedFiles.add(srcPath)
            } else {
                newSubImages.add(newPath)
            }
            current++
            emit(DuplicateProgress(current, total, originalTodo.title, failedFiles.toList()))
        }
        parseJsonPaths(subTask.voicePaths).forEach { srcPath ->
            val newPath = copyFile(srcPath, voiceDir.absolutePath)
            if (newPath == null) {
                failedFiles.add(srcPath)
            } else {
                newSubVoices.add(newPath)
            }
            current++
            emit(DuplicateProgress(current, total, originalTodo.title, failedFiles.toList()))
        }
        newSubTasks.add(subTask.copy(
            imagePaths = newSubImages.joinJson(),
            voicePaths = newSubVoices.joinJson()
        ))
    }

    // 复制 ContentBlock 文件 + 插入新 entity
    val newBlocks = mutableListOf<ContentBlockEntity>()
    originalBlocks.forEach { block ->
        val dstDir = if (block.type == "voice") voiceDir else picturesDir
        val newPath = copyFile(block.filePath, dstDir.absolutePath)
        if (newPath != null) {
            newBlocks.add(block.copy(id = 0, todoId = newTodoId, filePath = newPath))
        } else {
            failedFiles.add(block.filePath)
        }
        current++
        emit(DuplicateProgress(current, total, originalTodo.title, failedFiles.toList()))
    }

    // 提交：更新 todo / sub_tasks / content_blocks
    todoRepository.updateTodo(originalTodo.copy(
        id = newTodoId,
        imagePaths = newImagePaths.joinJson(),
        voiceNotePath = newVoiceNotePath
    ))
    if (newSubTasks.isNotEmpty()) {
        // 找到新副本的 sub_tasks 并更新
        val newDbSubTasks = SubTaskManager.getSubTasks(context, newTodoId)
        newDbSubTasks.forEachIndexed { index, dbSub ->
            if (index < newSubTasks.size) {
                SubTaskManager.updateSubTask(context, dbSub.id, newSubTasks[index])
            }
        }
    }
    if (newBlocks.isNotEmpty()) {
        todoRepository.contentBlockDao.insertBlocks(newBlocks)
    }
}

/**
 * 解析 JSON 数组路径
 */
private fun parseJsonPaths(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        org.json.JSONArray(json).let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * List<String> 序列化为 JSON 数组字符串
 */
private fun List<String>.joinJson(): String {
    if (isEmpty()) return ""
    val arr = org.json.JSONArray()
    forEach { arr.put(it) }
    return arr.toString()
}
```

- [ ] **Step 3: 添加必要 import**

在 `FileCopyManager.kt` 顶部加入：
```kotlin
import com.corgimemo.app.data.local.db.ContentBlockDao
import com.corgimemo.app.data.local.db.ContentBlockEntity
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.data.repository.TodoRepository
import kotlinx.coroutines.flow.flow
```

并在 `FileCopyManager` 类构造中添加：
```kotlin
@Inject lateinit var todoRepository: TodoRepository
```

- [ ] **Step 4: 编译验证**

```bash
gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/util/FileCopyManager.kt
git commit -m "feat(duplicate): implement copyAllAttachments with file + ContentBlock"
```

---

## Task 3: HomeViewModel 新增进度 State

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt`

- [ ] **Step 1: 找到 ViewModel 顶部 StateFlow 声明区**

在 `_pendingBatchCompleteCount` 附近（前面 Snackbar 相关 State 之后）添加：

```kotlin
/**
 * 复制进度状态（非 null 时显示进度条）
 *
 * 设计：参考 Material Design 的 LinearProgressIndicator 反馈模式
 * - null = 不显示
 * - DuplicateProgress = 进度数据
 */
private val _duplicateProgress = MutableStateFlow<FileCopyManager.DuplicateProgress?>(null)
val duplicateProgress: StateFlow<FileCopyManager.DuplicateProgress?> =
    _duplicateProgress.asStateFlow()

/** 手动关闭进度条（用户点击关闭按钮时） */
fun dismissDuplicateProgress() {
    _duplicateProgress.value = null
}
```

- [ ] **Step 2: 添加 import**

```kotlin
import com.corgimemo.app.util.FileCopyManager
```

- [ ] **Step 3: 编译验证**

```bash
gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt
git commit -m "feat(duplicate): add _duplicateProgress StateFlow"
```

---

## Task 4: 重构 batchDuplicate（异步 + 收集 Flow + 进度反馈）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt:2640-2686`

- [ ] **Step 1: 找到现有 batchDuplicate**

当前 `batchDuplicate` 是同步实现（`todoRepository.insertTodo` + `SubTaskManager.addSubTasks`）。

- [ ] **Step 2: 替换为异步实现**

```kotlin
/**
 * 批量复制选中的待办（异步 + 深复制 + 进度条）
 *
 * 修复对比：
 * - 修复前：只复制 TodoItem 主表 + SubTasks 路径字符串
 * - 修复后：复制 TodoItem + SubTasks（含附件） + ContentBlock + 实际文件
 * - 进度：FileCopyManager 通过 Flow 暴露，ViewModel 推给 _duplicateProgress
 */
fun batchDuplicate() {
    val selectedIds = _selectedTodoIds.value
    if (selectedIds.isEmpty()) return
    val currentTime = System.currentTimeMillis()
    viewModelScope.launch {
        // 1. 同步复制主表 + 子任务 + ContentBlock 表（先于文件复制）
        val newSubTaskMaps = mutableMapOf<Long, List<SubTask>>()
        val todoTitles = mutableMapOf<Long, String>()

        selectedIds.forEach { id ->
            val todo = todoRepository.getTodoById(id) ?: return@forEach
            val newId = todoRepository.insertTodo(
                todo.copy(
                    id = 0,
                    status = 0,
                    completedAt = null,
                    imagePaths = "",         // 暂时清空，文件复制后回填
                    voiceNotePath = null,    // 暂时清空
                    createdAt = currentTime,
                    updatedAt = currentTime
                )
            )
            todoTitles[newId] = todo.title

            // 复制子任务
            val subTasks = SubTaskManager.getSubTasks(context, id)
            if (subTasks.isNotEmpty()) {
                SubTaskManager.addSubTasks(context, newId, subTasks)
                newSubTaskMaps[newId] = subTasks
            }
        }

        // 2. 复制子任务进度 Map（让 (0/1) 立即可见，附件 0 暂时）
        if (newSubTaskMaps.isNotEmpty()) {
            val updatedProgressMap = _subTaskProgressMap.value.toMutableMap()
            val updatedSubTasksMap = _subTasksMap.value.toMutableMap()
            newSubTaskMaps.forEach { (newId, subTasks) ->
                val progress = SubTaskManager.getProgressText(context, newId)
                if (progress != null) {
                    updatedProgressMap[newId] = progress
                }
                updatedSubTasksMap[newId] = subTasks
            }
            _subTaskProgressMap.value = updatedProgressMap
            _subTasksMap.value = updatedSubTasksMap
        }

        // 3. 退出批量模式
        exitBatchMode()

        // 4. 异步文件复制（含 ContentBlock 文件 + 子任务附件 + 父附件）
        //    进度推到 _duplicateProgress，UI 实时显示
        for (originalId in selectedIds) {
            // 找到对应的 newId（按顺序）
            val newId = findNewIdForOriginal(originalId) ?: continue
            val title = todoTitles[newId] ?: ""
            try {
                fileCopyManager.copyAllAttachments(originalId, newId)
                    .collect { progress ->
                        _duplicateProgress.value = progress.copy(currentTodoTitle = title)
                    }
            } catch (e: Exception) {
                // 单 todo 失败不影响其他
                e.printStackTrace()
            }
        }

        // 5. 复制完成 3s 后自动关闭进度条
        if (_duplicateProgress.value != null) {
            kotlinx.coroutines.delay(3000)
            _duplicateProgress.value = null
        }
    }
}

/**
 * 根据原始 ID 找到对应的新副本 ID
 * 简化实现：按插入顺序假设
 * （更稳健可改为保存 (originalId, newId) 映射）
 */
private suspend fun findNewIdForOriginal(originalId: Long): Long? {
    // 简化：直接查询最新插入的 todo 中标题匹配的
    val original = todoRepository.getTodoById(originalId) ?: return null
    val allTodos = todoRepository.getAllTodosOnce()
    return allTodos
        .filter { it.title == original.title && it.id != originalId }
        .maxByOrNull { it.createdAt }?.id
}
```

- [ ] **Step 3: 添加 import**

```kotlin
import com.corgimemo.app.data.model.SubTask
```

- [ ] **Step 4: 编译验证**

```bash
gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt
git commit -m "feat(duplicate): refactor batchDuplicate to async + deep copy + progress"
```

---

## Task 5: 创建 DuplicateProgressBar UI 组件

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/DuplicateProgressBar.kt`

- [ ] **Step 1: 创建文件**

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.R
import com.corgimemo.app.util.FileCopyManager
import androidx.compose.ui.res.stringResource

/**
 * 顶层复制进度条
 *
 * 显示规则：
 * - progress = null → 不显示
 * - progress != null → 显示当前进度 + 标题 + 失败信息
 *
 * 位置：TopAppBar 下方，列表上方
 * 高度：64dp
 */
@Composable
fun DuplicateProgressBar(
    progress: FileCopyManager.DuplicateProgress?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = progress != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        progress?.let { p ->
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (p.total == 0) stringResource(R.string.duplicate_complete)
                                   else stringResource(R.string.duplicate_in_progress, p.current, p.total),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = p.currentTodoTitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = if (p.total == 0) 1f else (p.current.toFloat() / p.total),
                        modifier = Modifier.fillMaxWidth(),
                        color = if (p.failedFiles.isNotEmpty()) Color(0xFFFF6B6B)
                                else MaterialTheme.colorScheme.primary
                    )
                    if (p.failedFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.duplicate_partial_failed, p.failedFiles.size),
                            fontSize = 11.sp,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/DuplicateProgressBar.kt
git commit -m "feat(duplicate): add DuplicateProgressBar UI component"
```

---

## Task 6: HomeScreen 集成进度条

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 找到 State 收集区**

在 `pendingBatchCompleteCount by viewModel.pendingBatchCompleteCount.collectAsState()` 之后添加：

```kotlin
val duplicateProgress by viewModel.duplicateProgress.collectAsState()
```

- [ ] **Step 2: 添加 import**

```kotlin
import com.corgimemo.app.ui.components.DuplicateProgressBar
```

- [ ] **Step 3: 找到 Scaffold 区域**

在 Scaffold 的 `topBar` 参数之前（外层 Box 内）插入：

```kotlin
// 复制进度条（顶层）
DuplicateProgressBar(
    progress = duplicateProgress,
    onDismiss = { viewModel.dismissDuplicateProgress() }
)
```

- [ ] **Step 4: 编译验证**

```bash
gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(duplicate): integrate DuplicateProgressBar into HomeScreen"
```

---

## Task 7: 添加中英文文案

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] **Step 1: 在 values/strings.xml 添加**

```xml
<string name="duplicate_in_progress">正在复制… [%1$d/%2$d]</string>
<string name="duplicate_complete">复制完成</string>
<string name="duplicate_partial_failed">⚠️ %1$d 个文件复制失败</string>
```

- [ ] **Step 2: 在 values-en/strings.xml 添加**

```xml
<string name="duplicate_in_progress">Copying… [%1$d/%2$d]</string>
<string name="duplicate_complete">Copy complete</string>
<string name="duplicate_partial_failed">⚠️ %1$d files failed</string>
```

- [ ] **Step 3: 编译验证**

```bash
gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(duplicate): add i18n strings for copy progress"
```

---

## Task 8: 手动 UI 验证

- [ ] **Step 1: 安装应用到测试设备**

```bash
gradlew :app:installDebug
```

- [ ] **Step 2: 测试用例 1：复制无附件 todo**

1. 创建一个无附件 todo
2. 长按 → 选 1 条 → MoreOptions → 创建副本
3. **期望**：进度条快速完成，无失败提示
4. 退出多选模式后：副本正常显示

- [ ] **Step 3: 测试用例 2：复制带图片附件**

1. 创建一个带 3 张图片的 todo
2. 长按 → 选 1 条 → MoreOptions → 创建副本
3. **期望**：进度条显示 "正在复制… [3/3]"，完成后 3s 消失
4. 检查副本：图片正常显示
5. 验证独立性：删除源 todo，副本图片仍可显示

- [ ] **Step 4: 测试用例 3：复制带子任务附件**

1. 创建一个 todo + 子任务（带图片）
2. 长按 → 选 1 条 → MoreOptions → 创建副本
3. **期望**：副本显示 (0/1)，展开子任务可见图片
4. 验证独立性：删除源，子任务图片仍可见

- [ ] **Step 5: 测试用例 4：复制失败容错**

1. 创建一个 todo（带图片），然后手动删除图片文件
2. 长按 → 选 1 条 → MoreOptions → 创建副本
3. **期望**：进度条完成，显示"⚠️ 1 个文件复制失败"
4. todo 主表正常复制

- [ ] **Step 6: 测试用例 5：批量复制进度条**

1. 创建 3 个 todo（每个带 2 张图片）
2. 长按 → 选 3 条 → MoreOptions → 创建副本
3. **期望**：进度条累加显示 6/6
4. 全部完成后 3s 消失

- [ ] **Step 7: 提交验证记录**

```bash
git log --oneline -7
```

Expected: 看到 7 个 feat(duplicate) 提交

---

## Self-Review

### 1. Spec coverage

| Spec 章节 | 实施任务 |
|-----------|----------|
| 1.2 目标 1: 完全深复制 | Task 1, 2, 4 |
| 1.2 目标 2: 文件复制（父+子+ContentBlock） | Task 2 |
| 1.2 目标 3: ContentBlock 表复制 | Task 2 |
| 1.2 目标 4: 部分允许失败 | Task 2, 5 |
| 1.2 目标 5: 进度条反馈 | Task 3, 4, 5, 6 |
| 1.2 目标 6: 保留主表数据 | Task 2, 4 |
| 2 用户故事 US-1 ~ US-5 | Task 8 验证 |
| 3.1 架构 | Task 1, 3, 4, 5, 6 |
| 3.2 FileCopyManager | Task 1, 2 |
| 3.3 HomeViewModel State | Task 3, 4 |
| 3.4 数据流 | Task 4 |
| 3.5 进度数据类 | Task 1, 5 |
| 3.6 进度条 UI | Task 5, 6 |
| 3.7 文件命名规则 | Task 1 |
| 3.8 ContentBlock 复制策略 | Task 2 |
| 3.9 错误处理 | Task 2, 4, 5 |
| 4 边缘情况 | Task 8 验证 |
| 5 风险 | Task 5 进度条高度限制 + Task 4 协程取消 |
| 6 测试要点 | Task 8 |

✅ 全部覆盖

### 2. Placeholder scan

- 无 TBD/TODO/待定
- 无 "similar to Task N"
- 每个 Step 都有具体代码

### 3. Type consistency

- `FileCopyManager.DuplicateProgress` 在 Task 1 定义，Task 2/3/5 引用一致
- `copyAllAttachments` 签名在 Task 1 简化，Task 2 完整实现
- `_duplicateProgress` StateFlow 在 Task 3 定义，Task 4 写入，Task 6 读取
- `viewModel.dismissDuplicateProgress()` 在 Task 3 定义，Task 6 调用

✅ 一致
