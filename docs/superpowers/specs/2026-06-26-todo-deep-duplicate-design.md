# 待办深复制设计（文件 + ContentBlock + 子任务附件）

> 文档日期：2026-06-26
> 状态：已批准
> 类型：功能增强 + 体验优化

## 1. 背景与目标

### 1.1 背景

`batchDuplicate`（多选模式"创建副本"功能）当前只复制 `TodoItem` 主表 + `sub_tasks` 表的 `imagePaths` / `voicePaths` 路径字符串，**不复制文件本身**，也**不复制 ContentBlock 表**。

具体问题：

- `todo.imagePaths` / `todo.voiceNotePath` 复制的是路径字符串 → 原文件被删/移动时副本失效
- `sub_tasks.imagePaths` / `sub_tasks.voicePaths` 同上
- `content_blocks` 表（编辑器的图片/语音块）完全未复制
- 用户期望：副本与源"完全独立"，删除源不影响副本

### 1.2 目标

1. **完全深复制**：副本与源完全独立，删除源不影响副本
2. **文件复制**：父附件（imagePaths/voiceNotePath）、子任务附件（imagePaths/voicePaths）、ContentBlock 文件全部复制
3. **ContentBlock 表复制**：将 `content_blocks` 表的 `filePath` 更新为新副本的路径
4. **部分允许失败**：附件丢失不阻塞整个复制流程
5. **进度条反馈**：顶层 LinearProgressIndicator 实时显示
6. **保留主表数据**：文件复制失败不导致 todo 被回滚

## 2. 用户故事

| 编号 | 用户故事                                                            | 验收标准                                                  |
| ---- | ------------------------------------------------------------------- | --------------------------------------------------------- |
| US-1 | 作为用户，我希望创建副本后副本与源完全独立                          | 删除源 todo/源文件后，副本仍可正常查看图片、播放语音      |
| US-2 | 作为用户，我希望复制大量附件时能看到进度                            | 顶层进度条显示"X/Y"以及当前 todo 标题                   |
| US-3 | 作为用户，我希望某个文件复制失败时整个操作仍能继续                  | Snackbar 提示"已复制 N 项，部分附件复制失败"             |
| US-4 | 作为用户，我希望子任务附件也被复制                                  | 副本的子任务图片/语音仍可正常显示                         |
| US-5 | 作为用户，我希望 ContentBlock 表的图片/语音块也被复制               | 副本进入编辑页能看到完整的图片/语音块                    |

## 3. 设计方案

### 3.1 架构概览

```
┌──────────────────────────────────────────────────────────────┐
│  UI 层                                                        │
│  ├─ HomeScreen (LaunchedEffect 监听 _duplicateProgress)      │
│  └─ DuplicateProgressBar (顶层 LinearProgressIndicator)       │
└──────────────────────────────────────────────────────────────┘
                              ↓ collectAsState
┌──────────────────────────────────────────────────────────────┐
│  ViewModel 层                                                 │
│  ├─ HomeViewModel.batchDuplicate()                            │
│  │   └─ 调用 FileCopyManager.copyAllAttachments()             │
│  └─ _duplicateProgress: StateFlow<DuplicateProgress?>         │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│  Service 层                                                   │
│  └─ FileCopyManager (新)                                      │
│      ├─ copyAllAttachments(originalId, newId) → Flow          │
│      ├─ copyFile(srcPath, dstDir) → Result<String>            │
│      └─ generateCopyFileName(originalName) → String          │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│  数据层                                                       │
│  ├─ TodoDao (todos 表)                                       │
│  ├─ SubTaskDao (sub_tasks 表)                                 │
│  ├─ ContentBlockDao (content_blocks 表)                       │
│  └─ File System (pictures/ voice_notes/ 目录)                │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

#### 3.2.1 FileCopyManager（新增）

```kotlin
@Singleton
class FileCopyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todoDao: TodoDao,
    private val subTaskDao: SubTaskDao,
    private val contentBlockDao: ContentBlockDao
) {
    /** 复制进度数据类 */
    data class DuplicateProgress(
        val current: Int,                  // 当前复制到第几个文件
        val total: Int,                    // 总文件数
        val currentTodoTitle: String,      // 当前正在复制的 todo 标题
        val failedFiles: List<String> = emptyList()  // 失败的文件路径
    )
    
    /**
     * 复制 todo 的所有附件
     * - 父附件 (imagePaths / voiceNotePath)
     * - 子任务附件 (imagePaths / voicePaths)
     * - ContentBlock 表 + 文件
     * 
     * @return Flow<DuplicateProgress> 实时进度
     */
    fun copyAllAttachments(
        originalTodoId: Long,
        newTodoId: Long
    ): Flow<DuplicateProgress>
    
    /**
     * 复制单个文件到目标目录，生成 copy_<uuid>_<filename> 命名
     * @return 成功返回新路径，失败返回 null
     */
    private suspend fun copyFile(srcPath: String, dstDir: String): String?
    
    /**
     * 生成副本文件名：copy_<uuid>_<originalName>
     */
    private fun generateCopyFileName(originalName: String): String
}
```

#### 3.2.2 HomeViewModel 新增 State

```kotlin
private val _duplicateProgress = MutableStateFlow<DuplicateProgress?>(null)
val duplicateProgress: StateFlow<DuplicateProgress?> = _duplicateProgress.asStateFlow()

fun dismissDuplicateProgress() { _duplicateProgress.value = null }
```

#### 3.2.3 DuplicateProgressBar 组件（新增）

```kotlin
@Composable
fun DuplicateProgressBar(
    progress: DuplicateProgress,
    modifier: Modifier = Modifier
)
```

### 3.3 数据流

```
用户长按 → 进入批量模式 → 选 3 条 → MoreOptions → "创建副本"
    ↓
HomeViewModel.batchDuplicate()
    ↓
1. 主表复制（同步）：
   todos.insertAll(todo.copy(id=0, status=0, imagePaths="", voiceNotePath=null))
   → 立即获得 newId 列表
    ↓
2. 子任务表复制（同步）：
   sub_tasks.addSubTasks(newId, subTasks)
   → imagePaths/voicePaths 暂时保留原路径
    ↓
3. ContentBlock 表复制（同步）：
   content_blocks.getBlocksByTodoId(originalId) → 映射为 newTodoId
   → insertBlocks(newBlocks) 暂用原 filePath
    ↓
4. 文件复制（异步，带进度）：
   forEach todo (originalId, newId):
     fileCopyManager.copyAllAttachments(originalId, newId)
       .collect { progress -> _duplicateProgress.value = progress }
     ├── 父 imagePaths: copy_<uuid>_<name> 复制到 pictures/
     ├── 父 voiceNotePath: 复制到 voice_notes/
     ├── SubTask 附件: 同上
     └── ContentBlock: 复制文件 + 插入新 entity (newTodoId, newPath)
    ↓
5. 更新路径（异步）：
   todoRepository.updateTodo(todo.copy(imagePaths=newPaths, voiceNotePath=newPath))
   subTaskRepository.updateSubTask(subTask.copy(imagePaths=newPaths, voicePaths=newPaths))
   contentBlockRepository.replaceBlocksForTodo(newTodoId, newBlocks)
    ↓
6. _pendingBatchCompleteCount.value = N（带 Snackbar）
    ↓
7. exitBatchMode()
```

### 3.4 进度数据类

```kotlin
data class DuplicateProgress(
    val current: Int,                 // 已完成文件数
    val total: Int,                   // 总文件数
    val currentTodoTitle: String,     // 当前 todo 标题
    val failedFiles: List<String> = emptyList()
)
```

**进度计算**：
- 总文件数 = `sum(父 imagePaths + voiceNotePath + SubTask imagePaths + SubTask voicePaths + ContentBlock filePath)`
- current 每次 emit 后 +1
- 失败的文件不计入 current，但加入 failedFiles

### 3.5 进度条 UI

```
┌────────────────────────────────────────┐
│ ⏳ 正在复制... [3/12]                    │
│ ━━━━━━━━━━━━━━━━━━━━░░░░░░░ 60%         │
│ 当前：测试 2                              │
└────────────────────────────────────────┘
```

- 高度：64dp
- 位置：TopAppBar 下方，列表上方
- 动画：AnimatedVisibility 渐入渐出
- 完成 3s 后自动消失
- `failedFiles.isNotEmpty()` 时显示"⚠️ N 个文件复制失败"红色文字

### 3.6 文件命名规则

```
原文件：pictures/abc123.jpg
副本文件：pictures/copy_<uuid>_abc123.jpg
```

- UUID 由 `java.util.UUID.randomUUID().toString().take(8)` 生成（8 字符）
- 前缀 `copy_` 区分原文件和副本
- 保持原扩展名（.jpg / .mp4 / .m4a）
- 同名文件不会冲突（UUID 保证唯一）

### 3.7 ContentBlock 复制策略

| 步骤 | 操作 |
|------|------|
| 1 | `contentBlockDao.getBlocksByTodoId(originalId)` 获取所有 block |
| 2 | 对每个 block 调用 `fileCopyManager.copyFile(block.filePath, targetDir)` |
| 3 | 复制成功 → 构造新 `ContentBlockEntity(todoId=newId, filePath=newPath, ...)` |
| 4 | 复制失败 → 跳过该 block，不插入新 entity |
| 5 | 全部处理完 → `contentBlockDao.insertBlocks(newBlocks)` 一次性插入 |

### 3.8 错误处理

| 场景 | 处理 |
|------|------|
| 主表插入失败 | 跳过该 todo，不影响其他 |
| 子任务插入失败 | 跳过（不影响主表），记日志 |
| ContentBlock 文件复制失败 | 跳过该 block，不插入新 entity |
| ContentBlock 插入失败 | 跳过，不影响其他 |
| 文件不存在（已删除） | 跳过，记录 failedFiles |
| 磁盘满 | 跳过，记录 failedFiles |
| 权限丢失 | 跳过，记录 failedFiles |
| 文件复制完成但 entity 插入失败 | 删除已复制的文件（避免遗留） |

### 3.9 进度反馈完整流程

```
batchDuplicate 启动
    ↓
_duplicateProgress = DuplicateProgress(0, total, "测试 2")
    ↓
[UI 立即显示进度条]
    ↓
文件复制进行中... 每次完成 emit(newCurrent)
    ↓
所有文件复制完成 _duplicateProgress = DuplicateProgress(total, total, "测试 2", failedFiles)
    ↓
[UI 显示 "已完成" 3s]
    ↓
dismissDuplicateProgress() → _duplicateProgress = null
    ↓
[UI 渐出]
```

## 4. 边缘情况

| 场景 | 处理 |
|------|------|
| 源文件已删除 | 跳过该文件，记 failedFiles |
| 源 todo 无任何附件 | 进度条快速完成（total=0），无文件复制 |
| 复制中途 App 被杀 | ViewModel 销毁，进度条消失；已复制的文件保留（不会回滚，因为是部分允许失败） |
| 同一 todo 重复复制 | 每次复制都生成新 UUID，不会冲突 |
| 复制时源 todo 被编辑 | 复制的是 read-only 快照（id=0），无冲突 |
| ContentBlock 0 个 | insertBlocks 跳过（空列表） |
| 文件路径为 `null` | 跳过（不计入 total） |

## 5. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 复制大量文件导致存储翻倍 | 存储空间 | 限制单文件大小（>50MB 警告）+ 显示提示 |
| 复制失败时已有文件残留 | 存储浪费 | 失败时调用 `File.delete()` 清理 |
| 进度条与 Snackbar 同时显示 | UI 拥挤 | 进度条优先，Snackbar 延后（参考之前"等待弹窗关闭"模式） |
| 升级弹窗与进度条同时显示 | UI 冲突 | 升级弹窗覆盖进度条（弹窗优先） |
| 大量复制阻塞主线程 | 卡顿 | `withContext(Dispatchers.IO)` |
| 复制过程中 ViewModel 被销毁 | 数据不一致 | ViewModel scope 取消时，复制任务也取消（coroutine cancellation） |

## 6. 测试要点

| # | 测试场景 | 期望 |
|---|----------|------|
| 1 | 复制无附件 todo | 进度条快速完成，无失败 |
| 2 | 复制带 imagePaths 的 todo | 文件被复制，新路径写入 todo |
| 3 | 复制带 voiceNotePath 的 todo | 文件被复制 |
| 4 | 复制带子任务附件的 todo | 子任务附件被复制 |
| 5 | 复制带 ContentBlock 的 todo | ContentBlock 表 + 文件被复制 |
| 6 | 复制时源文件被删 | 跳过该文件，记 failedFiles |
| 7 | 进度条实时更新 | current/total 数字正确 |
| 8 | 进度条 + 升级弹窗时序 | 弹窗关闭后进度条/ Snackbar 正常 |
| 9 | 删除源 todo | 副本不受影响（完全独立） |
| 10 | 删除副本 todo | 仅副本文件被删，源文件保留 |
| 11 | 复制中途 App 被杀 | 已复制文件保留，未复制文件丢失（部分允许） |
| 12 | 同批次多 todo 复制 | 进度条累加正确 |

## 7. 不在本次范围

- 文件复制进度细粒度（每个文件的进度百分比）—— 本期只显示总进度
- 视频/大文件流式复制 —— 本期使用一次性 read/write
- 文件压缩 —— 复制保持原质量
- 异步任务（WorkManager）—— 本期用 ViewModel coroutine
- 单条复制 UI（首页单条 todo 的"复制"按钮）—— 本期只做批量复制
- 复制时的存储空间预估 —— 后续迭代

## 8. 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `util/FileCopyManager.kt` | 新建 | 文件复制核心服务 |
| `data/local/db/ContentBlockDao.kt` | 修改 | 新增 `insertBlocksForDuplicate(blocks)` 方法 |
| `data/repository/TodoRepository.kt` | 修改 | 新增 `updateTodoImagePaths(id, imagePaths, voiceNotePath)` |
| `data/repository/SubTaskRepository.kt` | 新建/修改 | 暴露 `updateSubTaskAttachments(id, imagePaths, voicePaths)` |
| `viewmodel/HomeViewModel.kt` | 修改 | 新增 `_duplicateProgress` State + `batchDuplicate` 异步重构 |
| `ui/screens/home/HomeScreen.kt` | 修改 | 收集 `_duplicateProgress` + 显示 `DuplicateProgressBar` |
| `ui/components/DuplicateProgressBar.kt` | 新建 | 顶层进度条组件 |
| `di/AppModule.kt` | 修改 | 提供 `FileCopyManager`（如需要） |
| `res/values/strings.xml` | 修改 | 新增复制相关中文文案 |
| `res/values-en/strings.xml` | 修改 | 新增复制相关英文文案 |

## 9. 设计权衡

| 选择 | 替代方案 | 选择理由 |
|------|----------|----------|
| 同步插入主表+异步复制文件 | 全异步（WorkManager） | 简化实现，避免后台任务复杂度 |
| 一次性 emit total | 增量 emit total | 减少 UI 重组 |
| 复制失败时跳过（部分允许） | 完全回滚 | 用户选择：附件丢失但保留 todo |
| 顶层进度条 | Dialog 进度条 | 不阻塞其他操作，UI 更轻量 |
| `copy_<uuid>_` 前缀 | 子目录 `copies/<newTodoId>/` | 简单，不破坏现有目录结构 |
| `FileCopyManager` 服务化 | ViewModel 内联 | 复用 + 测试性 + 解耦 |
| 路径在复制后更新 | 复制前先清空再回填 | 保持数据一致性（写库时确保路径有效） |
