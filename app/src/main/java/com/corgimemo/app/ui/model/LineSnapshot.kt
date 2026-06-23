package com.corgimemo.app.ui.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 行级快照数据类（用于持久化存储）
 *
 * 将 TodoLine 的关键状态（包括附件）序列化为 JSON 格式，
 * 以便在保存待办时记录每行的完整状态，
 * 在重新打开待办时能精确恢复到对应行。
 *
 * 使用场景：
 * - 保存待办时：从 todoLines 列表构建快照列表，序列化为 JSON 存储
 * - 加载待办时：从 JSON 反序列化，用于恢复每行的附件数据
 *
 * 数据结构示例（JSON）：
 * ```json
 * [
 *   {
 *     "text": "测试1",
 *     "isSubTask": false,
 *     "subTaskId": 0,
 *     "imagePaths": ["/path/img1.jpg", "/path/img2.jpg"],
 *     "voiceAttachments": [{"path": "/path/voice.mp3", "duration": 10}]
 *   },
 *   {
 *     "text": "测试2",
 *     "isSubTask": true,
 *     "subTaskId": 38,
 *     "imagePaths": [],
 *     "voiceAttachments": []
 *   }
 * ]
 * ```
 */
data class LineSnapshot(
    /** 行文本内容（用于匹配对应的 TodoLine） */
    val text: String,
    /** 是否为子任务行 */
    val isSubTask: Boolean,
    /** 关联的子任务数据库 ID */
    val subTaskId: Long,
    /** 该行的图片附件路径列表 */
    val imagePaths: List<String> = emptyList(),
    /** 该行的语音附件列表 */
    val voiceAttachments: List<VoiceAttachmentSnapshot> = emptyList()
) {
    /**
     * 序列化为 JSONObject
     *
     * @return JSON 对象表示
     */
    fun toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        put("isSubTask", isSubTask)
        put("subTaskId", subTaskId)
        put("imagePaths", JSONArray(imagePaths))
        put("voiceAttachments", JSONArray(voiceAttachments.map { it.toJson() }))
    }

    companion object {
        /**
         * 从 JSONObject 反序列化
         *
         * @param json JSON 对象
         * @return LineSnapshot 实例
         */
        fun fromJson(json: JSONObject): LineSnapshot = LineSnapshot(
            text = json.optString("text", ""),
            isSubTask = json.optBoolean("isSubTask", false),
            subTaskId = json.optLong("subTaskId", 0L),
            imagePaths = json.optJSONArray("imagePaths")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            voiceAttachments = json.optJSONArray("voiceAttachments")?.let { arr ->
                (0 until arr.length()).map { VoiceAttachmentSnapshot.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList()
        )
    }
}

/**
 * 语音附件快照（用于序列化）
 */
data class VoiceAttachmentSnapshot(
    /** 语音文件路径 */
    val path: String,
    /** 语音时长（秒） */
    val duration: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path)
        if (duration != null) put("duration", duration)
    }

    companion object {
        fun fromJson(json: JSONObject): VoiceAttachmentSnapshot = VoiceAttachmentSnapshot(
            path = json.optString("path", ""),
            duration = if (json.has("duration")) json.getInt("duration") else null
        )
    }
}

/**
 * 行级快照列表的工具方法
 */
object LineSnapshotUtils {
    /**
     * 内容格式中的分隔符标识
     *
     * 用于在 contentFormat 字段中同时存储：
     * - 原始的 Markdown 富文本内容（用于显示）
     * - 行级附件快照 JSON 数据（用于恢复附件）
     *
     * 存储格式示例：
     * ```
     * {Markdown 内容文本}|||LINE_ATTACHMENTS|||[{...}, {...}]
     * ```
     *
     * 提取规则：
     * - 显示时：取分隔符前的部分作为 Markdown 内容
     * - 加载时：取分隔符后的部分作为快照 JSON
     */
    const val SEPARATOR = "|||LINE_ATTACHMENTS|||"

    /**
     * 从 TodoLine 列表构建快照列表
     *
     * @param lines 当前的 todoLines 列表
     * @return 快照列表
     */
    fun fromTodoLines(lines: List<TodoLine>): List<LineSnapshot> {
        return lines.map { line ->
            LineSnapshot(
                text = line.text,
                isSubTask = line.isSubTask,
                subTaskId = line.subTaskId,
                imagePaths = line.imagePaths.toList(),
                voiceAttachments = line.voiceAttachments.map { va ->
                    VoiceAttachmentSnapshot(path = va.path, duration = va.duration)
                }
            )
        }
    }

    /**
     * 将快照列表与原始内容合并序列化为字符串
     *
     * @param snapshots 快照列表
     * @param originalContent 原始的富文本格式内容（Markdown）
     * @return 合并后的字符串（包含原始内容和快照 JSON）
     */
    fun serialize(snapshots: List<LineSnapshot>, originalContent: String = ""): String {
        if (snapshots.isEmpty()) return originalContent
        val jsonArray = JSONArray(snapshots.map { it.toJson() })
        return "$originalContent$SEPARATOR${jsonArray.toString()}"
    }

    /**
     * 从合并字符串中提取原始的 Markdown 内容（用于显示）
     *
     * @param combined 合并后的字符串
     * @return 纯净的 Markdown 内容（不包含快照数据）
     */
    fun extractDisplayContent(combined: String?): String {
        if (combined.isNullOrBlank()) return ""
        val separatorIndex = combined.indexOf(SEPARATOR)
        return if (separatorIndex >= 0) {
            combined.substring(0, separatorIndex)
        } else {
            combined  // 无分隔符，返回原值（兼容旧数据）
        }
    }

    /**
     * 从合并字符串中反序列化快照列表（用于恢复附件）
     *
     * @param combined 合并后的字符串
     * @return 快照列表，如果解析失败或无快照则返回空列表
     */
    fun deserialize(combined: String?): List<LineSnapshot> {
        if (combined.isNullOrBlank()) return emptyList()

        // 查找分隔符位置
        val separatorIndex = combined.indexOf(SEPARATOR)
        if (separatorIndex < 0 || separatorIndex + SEPARATOR.length >= combined.length) {
            // 无分隔符或分隔符后无数据，尝试直接解析（兼容旧格式）
            if (combined.contains("LINE_ATTACHMENTS")) {
                // 旧格式兼容：移除前缀后解析
                val jsonData = combined.removePrefix("#LINE_ATTACHMENTS#")
                return try {
                    val jsonArray = JSONArray(jsonData)
                    (0 until jsonArray.length()).map { LineSnapshot.fromJson(jsonArray.getJSONObject(it)) }
                } catch (e: Exception) { emptyList() }
            }
            return emptyList()
        }

        // 提取分隔符后的 JSON 数据
        val jsonData = combined.substring(separatorIndex + SEPARATOR.length)

        return try {
            val jsonArray = JSONArray(jsonData)
            (0 until jsonArray.length()).map { LineSnapshot.fromJson(jsonArray.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 根据快照列表恢复 TodoLine 的附件数据
     *
     * 匹配策略（按优先级）：
     * 1. subTaskId 匹配：最精确，适用于有数据库 ID 的子任务
     * 2. 文本 + isSubTask 匹配：适用于新建的子任务
     * 3. 行索引匹配：最后的回退方案
     *
     * @param lines 当前的 todoLines 列表（已初始化文本和结构）
     * @param snapshots 快照列表（包含附件信息）
     * @return 恢复了附件的新 todoLines 列表
     */
    fun restoreAttachmentsToLines(
        lines: List<TodoLine>,
        snapshots: List<LineSnapshot>
    ): List<TodoLine> {
        if (snapshots.isEmpty()) return lines

        val result = lines.toMutableList()

        // 为每个 snapshot 找到对应的行并恢复附件
        snapshots.forEachIndexed { snapshotIndex, snapshot ->
            // 查找目标行的索引
            val targetLineIndex = findMatchingLineIndex(lines, snapshot, snapshotIndex)

            if (targetLineIndex in result.indices) {
                val currentLine = result[targetLineIndex]
                var updatedLine = currentLine

                // 恢复图片附件
                if (snapshot.imagePaths.isNotEmpty() && currentLine.imagePaths.isEmpty()) {
                    updatedLine = updatedLine.copy(imagePaths = snapshot.imagePaths)
                }

                // 恢复录音附件
                if (snapshot.voiceAttachments.isNotEmpty() && currentLine.voiceAttachments.isEmpty()) {
                    val restoredVoices = snapshot.voiceAttachments.map { va ->
                        com.corgimemo.app.ui.model.VoiceAttachment(
                            path = va.path,
                            duration = va.duration
                        )
                    }
                    updatedLine = updatedLine.copy(voiceAttachments = restoredVoices)
                }

                if (updatedLine != currentLine) {
                    result[targetLineIndex] = updatedLine
                }
            }
        }

        return result
    }

    /**
     * 查找与快照匹配的行索引
     *
     * @param lines 当前行列表
     * @param snapshot 要匹配的快照
     * @param fallbackIndex 回退索引（当无法精确匹配时使用）
     * @return 匹配的行索引
     */
    private fun findMatchingLineIndex(
        lines: List<TodoLine>,
        snapshot: LineSnapshot,
        fallbackIndex: Int
    ): Int {
        // 策略 1：subTaskId 精确匹配（最可靠）
        if (snapshot.subTaskId > 0L) {
            val found = lines.indexOfFirst { it.subTaskId == snapshot.subTaskId }
            if (found >= 0) return found
        }

        // 策略 2：文本 + isSubTask 组合匹配
        val found = lines.indexOfFirst { line ->
            line.text == snapshot.text && line.isSubTask == snapshot.isSubTask
        }
        if (found >= 0) return found

        // 策略 3：回退到索引位置
        return fallbackIndex.coerceIn(lines.indices)
    }
}
