package com.corgimemo.app.util

import android.content.Context
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.local.db.ContentBlockEntity
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件复制管理器
 *
 * 封装文件 IO 操作，为 batchDuplicate 提供：
 * 1. 单文件复制（copy_<uuid>_<name> 命名）
 * 2. todo 全附件复制（父 + 子任务 + ContentBlock）
 * 3. 进度通过 Flow<DuplicateEvent> 暴露（Progress + Result）
 *
 * 设计原则：
 * - 失败时部分允许：跳过失败文件，不影响其他
 * - 命名用 UUID 前缀避免冲突
 * - 同目录存放，不创建子目录
 * - 复制完成后通过 [DuplicateEvent.Result] 携带新路径，
 *   调用方负责回写 TodoItem / SubTask / ContentBlockEntity
 */
@Singleton
class FileCopyManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val todoRepository: TodoRepository
) {
    companion object {
        /**
         * 单文件大小上限（50 MB）
         *
         * 超过此大小的文件在复制时会被跳过，记入 failedFiles。
         * 50MB 足以覆盖日常图片/语音/小视频附件；
         * 超过此阈值的附件视为"非典型待办附件"，避免：
         * - 复制耗时过长
         * - 副本存储空间翻倍
         * - 移动端 OOM
         */
        const val MAX_FILE_SIZE_BYTES: Long = 50L * 1024 * 1024

        /**
         * 公开的路径列表 → JSON 序列化工具方法
         *
         * 供 HomeViewModel.batchDuplicate 在回写 TodoItem.imagePaths / SubTask.imagePaths
         * 时使用（私有扩展函数 [joinJson] 不可跨类访问）。
         *
         * @return JSON 数组字符串（与 [parseJsonPaths] 互逆）
         */
        @JvmStatic
        fun toJsonArray(paths: List<String>): String {
            val array = JSONArray()
            paths.forEach { array.put(it) }
            return array.toString()
        }
    }

    /**
     * 复制 todo 的所有附件
     *
     * 复制范围：
     * 1. 父待办 imagePaths（JSON 数组）中的图片
     * 2. 父待办 voiceNotePath 语音附件
     * 3. 所有子任务的 imagePaths / voicePaths 附件
     * 4. content_blocks 表中的文件（复制后插入新 todoId 关联的记录）
     *
     * 设计原则：
     * - 单文件失败不影响其他文件（追加到 failedFiles 后继续）
     * - 进度实时通过 Flow 推送 [DuplicateEvent.Progress]
     * - 复制完成后通过 [DuplicateEvent.Result] 一次性回传新路径
     *   由调用方（HomeViewModel.batchDuplicate）负责更新 TodoItem / SubTask / ContentBlock
     *
     * @param originalTodoId 源 todo ID
     * @param newTodoId 副本 todo ID（已存在数据库中）
     * @return Flow<DuplicateEvent> 实时进度 + 最终结果
     */
    fun copyAllAttachments(
        originalTodoId: Long,
        newTodoId: Long
    ): Flow<DuplicateEvent> = flow {
        // 收集所有待复制的源文件路径 → (源路径, 目标目录, 文件用途标签)
        val pendingFiles = mutableListOf<CopyTask>()

        // 1. 父待办的图片附件（imagePaths 是 JSON 数组字符串）
        val originalTodo: TodoItem? = withContext(Dispatchers.IO) {
            todoRepository.getTodoById(originalTodoId)
        }
        // 父待办原 imagePaths/voiceNotePath（用于在 Result 中按位置回填新路径）
        val parentImagePaths: List<String> = originalTodo?.let { todo ->
            parseJsonPaths(todo.imagePaths)
        } ?: emptyList()
        val parentVoicePath: String? = originalTodo?.voiceNotePath?.takeIf { it.isNotBlank() }

        parentImagePaths.forEach { path ->
            pendingFiles.add(CopyTask(srcPath = path, sourceRef = "parent"))
        }
        parentVoicePath?.let { path ->
            pendingFiles.add(CopyTask(srcPath = path, sourceRef = "parent"))
        }

        // 2. 子任务附件（imagePaths + voicePaths）
        val subTasks: List<SubTask> = withContext(Dispatchers.IO) {
            SubTaskManager.getSubTasks(context, originalTodoId)
        }
        subTasks.forEach { subTask ->
            parseJsonPaths(subTask.imagePaths).forEach { path ->
                pendingFiles.add(
                    CopyTask(
                        srcPath = path,
                        sourceRef = "subtask:${subTask.id}:image:${path}"
                    )
                )
            }
            parseJsonPaths(subTask.voicePaths).forEach { path ->
                pendingFiles.add(
                    CopyTask(
                        srcPath = path,
                        sourceRef = "subtask:${subTask.id}:voice:${path}"
                    )
                )
            }
        }

        // 3. ContentBlock 附件（独立表，文件需复制 + 插入新 todoId 记录）
        val contentBlocks: List<ContentBlockEntity> = withContext(Dispatchers.IO) {
            CorgiMemoDatabase.getDatabase(context).contentBlockDao()
                .getBlocksByTodoId(originalTodoId)
        }
        contentBlocks.forEach { block ->
            pendingFiles.add(
                CopyTask(
                    srcPath = block.filePath,
                    sourceRef = "block:${block.id}:${block.type}"
                )
            )
        }

        val total = pendingFiles.size
        if (total == 0) {
            // 没有附件：直接 emit 最终 Result（所有路径沿用原值）
            emit(
                DuplicateEvent.Result(
                    newParentImagePaths = parentImagePaths,
                    newParentVoicePath = parentVoicePath,
                    newSubTaskAttachments = emptyMap(),
                    failedFiles = emptyList()
                )
            )
            return@flow
        }

        // 失败的源文件路径（用于 UI 提示）
        val failedFiles = mutableListOf<String>()

        // 优化 5：分类跟踪已生成的文件，按用途区分
        // - parentGeneratedFiles：父附件复制成功文件（失败时需清理）
        // - subTaskGeneratedFiles：子任务附件复制成功文件（失败时需清理）
        // - blockGeneratedFiles：ContentBlock 复制成功文件（独立清理，避免误删父/子任务附件）
        val parentGeneratedFiles = mutableListOf<String>()
        val subTaskGeneratedFiles = mutableListOf<String>()
        val blockGeneratedFiles = mutableListOf<String>()

        // 父子任务附件的"旧路径 → 新路径"映射（用于回写 SubTask.imagePaths/voicePaths）
        val subTaskNewPaths: MutableMap<String, String> = mutableMapOf()
        // 子任务 ID 维度收集（最终汇总为 newSubTaskAttachments）
        // key = subTaskId (Long), value = Pair(imagePaths list, voicePaths list)
        val subTaskNewById: MutableMap<Long, Pair<MutableList<String>, MutableList<String>>> =
            mutableMapOf()

        // ContentBlock 复制结果：用于在 Result 中告知调用方（由调用方 insertBlocks）
        val newContentBlocks = mutableListOf<ContentBlockEntity>()

        // 复制当前 todo 标题（用于进度条显示）
        val currentTitle = originalTodo?.title.orEmpty()

        var current = 0
        for (task in pendingFiles) {
            val srcFile = File(task.srcPath)
            // 计算目标目录：与源文件同目录（避免跨目录权限问题）
            val dstDir = srcFile.parent ?: context.filesDir.absolutePath
            val newPath = withContext(Dispatchers.IO) {
                copyFile(task.srcPath, dstDir)
            }
            current += 1

            if (newPath == null) {
                // 复制失败（含源文件不存在 / 超过 50MB / IO 异常）：记录失败文件，继续下一个
                failedFiles.add(task.srcPath)
            } else {
                when {
                    task.sourceRef == "parent" -> {
                        parentGeneratedFiles.add(newPath)
                    }
                    task.sourceRef.startsWith("subtask:") -> {
                        subTaskGeneratedFiles.add(newPath)
                        // 解析 subTaskId 和类型
                        val parts = task.sourceRef.split(":", limit = 4)
                        if (parts.size >= 4) {
                            val subTaskId = parts[1].toLongOrNull()
                            val kind = parts[2]
                            val oldPath = parts[3]
                            if (subTaskId != null) {
                                val pair = subTaskNewById.getOrPut(subTaskId) {
                                    Pair(mutableListOf(), mutableListOf())
                                }
                                subTaskNewPaths[oldPath] = newPath
                                when (kind) {
                                    "image" -> pair.first.add(newPath)
                                    "voice" -> pair.second.add(newPath)
                                }
                            }
                        }
                    }
                    task.sourceRef.startsWith("block:") -> {
                        blockGeneratedFiles.add(newPath)
                        // 如果是 ContentBlock 的源文件，登记新块
                        val parts = task.sourceRef.split(":", limit = 3)
                        if (parts.size == 3) {
                            val originalBlockId = parts[1].toLongOrNull()
                            val originalType = parts[2]
                            val originalBlock = contentBlocks.firstOrNull { it.id == originalBlockId }
                            if (originalBlock != null) {
                                newContentBlocks.add(
                                    ContentBlockEntity(
                                        id = 0, // Room 自增
                                        todoId = newTodoId,
                                        type = originalType,
                                        filePath = newPath,
                                        duration = originalBlock.duration,
                                        orderIndex = originalBlock.orderIndex
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 实时推送进度
            emit(
                DuplicateEvent.Progress(
                    current = current,
                    total = total,
                    currentTodoTitle = currentTitle,
                    failedFiles = failedFiles.toList()
                )
            )
        }

        // 4. 批量插入新的 ContentBlock 记录（仅当存在成功复制的块时）
        if (newContentBlocks.isNotEmpty()) {
            try {
                withContext(Dispatchers.IO) {
                    CorgiMemoDatabase.getDatabase(context).contentBlockDao()
                        .insertBlocks(newContentBlocks)
                }
            } catch (e: Exception) {
                // 插入失败：仅清理本次复制的 ContentBlock 文件（不影响父/子任务附件）
                blockGeneratedFiles.forEach { path ->
                    runCatching { File(path).delete() }
                }
                newContentBlocks.forEach { block -> failedFiles.add(block.filePath) }
            }
        }

        // 5. 组装最终 Result：回传新路径供调用方写回数据库
        // - 父 imagePaths / voiceNotePath：用复制后的新路径替换原路径
        // - 子任务附件：按 subTaskId 汇总 imagePaths/voicePaths 列表
        val newParentImagePaths = parentImagePaths.map { oldPath ->
            subTaskNewPaths[oldPath] ?: oldPath
        }
        // 父 voiceNotePath：仅当其在 pendingFiles 中被复制过才使用新路径
        val newParentVoicePath = parentVoicePath?.let { oldPath ->
            subTaskNewPaths[oldPath] ?: oldPath
        }

        val newSubTaskAttachments: Map<Long, Pair<List<String>, List<String>>> =
            subTaskNewById.mapValues { (_, lists) -> Pair(lists.first.toList(), lists.second.toList()) }

        emit(
            DuplicateEvent.Result(
                newParentImagePaths = newParentImagePaths,
                newParentVoicePath = newParentVoicePath,
                newSubTaskAttachments = newSubTaskAttachments,
                newContentBlockFilePathMap = emptyMap(),
                failedFiles = failedFiles.toList()
            )
        )
    }

    /**
     * 复制单个文件
     *
     * 行为：
     * - 源文件不存在 → 返回 null
     * - 文件大小 > [MAX_FILE_SIZE_BYTES]（50MB）→ 跳过，返回 null
     * - 复制过程异常 → 删除已创建的副本文件，返回 null
     *
     * @param srcPath 源文件绝对路径
     * @param dstDir 目标目录
     * @return 成功返回新路径，失败返回 null
     */
    private suspend fun copyFile(srcPath: String, dstDir: String): String? =
        withContext(Dispatchers.IO) {
            val srcFile = File(srcPath)
            if (!srcFile.exists()) return@withContext null
            // 优化 6：超过 50MB 的文件跳过复制，避免存储翻倍 / OOM
            if (srcFile.length() > MAX_FILE_SIZE_BYTES) return@withContext null
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
     * 解析 JSON 数组字符串为路径列表
     *
     * 容错处理：
     * - null / 空字符串 → 空列表
     * - 非 JSON 格式（兼容旧数据）→ 视为单元素 [字符串]
     * - JSON 解析失败 → 空列表
     *
     * @param json JSON 数组字符串（允许为 null，避免调用方传 null 时 NPE）
     * @return 路径列表
     */
    private fun parseJsonPaths(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 将路径列表序列化为 JSON 数组字符串
     *
     * 与 [parseJsonPaths] 配对使用，遵循 [com.corgimemo.app.data.model.TodoItem.imagePaths] /
     * [com.corgimemo.app.data.model.SubTask.imagePaths] 的编码规则（org.json.JSONArray）。
     *
     * @return JSON 数组字符串
     */
    private fun List<String>.joinJson(): String {
        val array = JSONArray()
        forEach { array.put(it) }
        return array.toString()
    }

    /**
     * 复制事件 sealed class
     *
     * 用于 [copyAllAttachments] 通过 Flow 暴露两种事件：
     * - [Progress]：复制过程中实时推送的进度
     * - [Result]：复制完成后一次性回传新路径，由调用方回写数据库
     *
     * 引入 sealed class 的原因：
     * - 旧版 [DuplicateProgress] 只携带 current/total，无法承载"新路径"信息
     * - 调用方无法回写 TodoItem.imagePaths / SubTask.imagePaths / ContentBlock.filePath
     *   导致复制后的"半深复制"问题（物理文件已复制，数据库路径仍指向源 todo）
     *
     * 优化 1 核心：通过 [Result] 携带 newParentImagePaths / newParentVoicePath /
     * newSubTaskAttachments，由 HomeViewModel.batchDuplicate 统一写回。
     */
    sealed class DuplicateEvent {
        /**
         * 复制进度事件
         *
         * @property current 已复制文件数（含失败跳过）
         * @property total 总文件数
         * @property currentTodoTitle 当前正在复制的 todo 标题
         * @property failedFiles 复制失败的文件路径列表
         */
        data class Progress(
            val current: Int,
            val total: Int,
            val currentTodoTitle: String,
            val failedFiles: List<String> = emptyList()
        ) : DuplicateEvent()

        /**
         * 复制结果事件
         *
         * 在 Flow 末尾 emit 一次，包含所有新路径，供调用方更新数据库：
         * - newParentImagePaths：父待办 imagePaths 的新路径列表（按原顺序）
         * - newParentVoicePath：父待办 voiceNotePath 的新路径
         * - newSubTaskAttachments：子任务附件新路径，key = 原 SubTask.id，
         *   value = Pair(新 imagePaths 列表, 新 voicePaths 列表)
         * - newContentBlockFilePathMap：ContentBlock 新路径映射（key = 原 blockId,
         *   value = 新 filePath）。新版 ContentBlockEntity 已由 FileCopyManager 内部
         *   insertBlocks，本字段保留供将来调用方有自定义插入需求时使用。
         * - failedFiles：复制失败的文件路径列表
         *
         * 注：原 [DuplicateProgress] 已弃用，保留为类型别名（Deprecated）以兼容旧 UI 引用。
         */
        data class Result(
            val newParentImagePaths: List<String>,
            val newParentVoicePath: String?,
            val newSubTaskAttachments: Map<Long, Pair<List<String>, List<String>>>,
            val newContentBlockFilePathMap: Map<Long, String> = emptyMap(),
            val failedFiles: List<String> = emptyList()
        ) : DuplicateEvent()
    }

    /**
     * 复制进度数据类（兼容旧版 UI 引用）
     *
     * 新版请使用 [DuplicateEvent.Progress] / [DuplicateEvent.Result]。
     * 保留该类型是为了 DuplicateProgressBar 组件（已重构为消费 [DuplicateEvent.Progress]）
     * 之外的潜在调用点。
     */
    data class DuplicateProgress(
        val current: Int,
        val total: Int,
        val currentTodoTitle: String,
        val failedFiles: List<String> = emptyList()
    )

    /**
     * 单个复制任务：包含源路径和来源标签
     *
     * @property srcPath 源文件绝对路径
     * @property sourceRef 来源标识：
     *  - "parent"：父待办附件
     *  - "subtask:<id>:<image|voice>:<srcPath>"：子任务附件（含子任务 ID、类型、原路径）
     *  - "block:<id>:<type>"：ContentBlock 附件（含原 ID 用于重建新块）
     */
    private data class CopyTask(
        val srcPath: String,
        val sourceRef: String
    )
}
