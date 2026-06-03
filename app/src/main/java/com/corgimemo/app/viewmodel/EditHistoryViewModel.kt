package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.util.AnnotatedStringSerializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.text.AnnotatedString
import javax.inject.Inject

/**
 * 编辑历史时间线 ViewModel
 *
 * 聚合当前编辑会话的 Undo/Redo 持久化日志数据，
 * 构建垂直时间线供 EditHistoryScreen 展示。
 *
 * **数据来源**: DataStore 中的 UNDO_LOG / REDO_LOG（Append-Only 增量日志）
 * **与 TodoEditViewModel 的关系**:
 * - TodoEditViewModel 管理内存中的实时 Undo/Redo 栈（用于编辑器内操作）
 * - EditHistoryViewModel 读取持久化日志（用于跨页面历史时间线展示）
 * - 两者数据同源但用途不同：实时操作 vs 历史浏览
 *
 * @param preferences DataStore 偏好管理器，用于读取持久化日志
 */
@HiltViewModel
class EditHistoryViewModel @Inject constructor(
    private val preferences: CorgiPreferences
) : ViewModel() {

    /** 时间线条目数据流 */
    private val _timelineEntries = MutableStateFlow<List<EditTimelineEntry>>(emptyList())
    val timelineEntries: StateFlow<List<EditTimelineEntry>> = _timelineEntries.asStateFlow()

    /** 数据加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 加载编辑历史时间线数据（V2.6: 按TodoId隔离）
     *
     * 从 DataStore 读取指定 TodoId 的 UNDO_LOG / REDO_LOG（Append-Only 增量日志），
     * 反序列化为 AnnotatedString 列表，构建时间线条目。
     *
     * **V2.6 变更**: 使用 `{todoId}_undo_log` / `{todoId}_redo_log` key，
     * 仅加载当前 Todo 的编辑历史，不与其他 Todo 混淆。
     *
     * @param todoId 目标 Todo 的 ID
     */
    fun loadTimeline(todoId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                /** 读取指定 Todo 的 Undo 和 Redo 增量日志 */
                val undoLogJson = preferences.getUndoLog(todoId)
                val redoLogJson = preferences.getRedoLog(todoId)

                /** 解析 Undo 日志条目 */
                val undoEntries = parseLogEntries(undoLogJson, TimelineType.UNDO_SNAPSHOT)
                /** 解析 Redo 日志条目 */
                val redoEntries = parseLogEntries(redoLogJson, TimelineType.REDO_SNAPSHOT)

                /** 合并所有条目：Undo 在前，Redo 在后 */
                val allEntries = mutableListOf<EditTimelineEntry>()
                allEntries.addAll(undoEntries)
                allEntries.addAll(redoEntries)

                /** 如果有数据，将最后一条标记为当前状态 */
                if (allEntries.isNotEmpty()) {
                    val lastIndex = allEntries.lastIndex
                    allEntries[lastIndex] = allEntries[lastIndex].copy(isCurrent = true)
                }

                _timelineEntries.value = allEntries
            } catch (e: Exception) {
                /** 解析失败时返回空列表 */
                _timelineEntries.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 清空指定 Todo 的编辑历史（V2.6: 按TodoId隔离）
     *
     * @param todoId 目标 Todo 的 ID
     */
    fun clearHistory(todoId: Long) {
        viewModelScope.launch {
            preferences.clearUndoRedoLogs(todoId)
            _timelineEntries.value = emptyList()
        }
    }

    /**
     * 解析 JSON 日志字符串为时间线条目列表
     *
     * @param logJson JSON 数组字符串（每元素为一个 AnnotatedString 序列化结果）
     * @param type 条目类型（UNDO_SNAPSHOT 或 REDO_SNAPSHOT）
     * @return 解析后的时间线条目列表
     */
    private fun parseLogEntries(logJson: String, type: TimelineType): List<EditTimelineEntry> {
        if (logJson.isBlank()) return emptyList()

        return try {
            val array = org.json.JSONArray(logJson)
            val entries = mutableListOf<EditTimelineEntry>()

            for (i in 0 until array.length()) {
                val jsonStr = array.getString(i)
                val annotatedString = AnnotatedStringSerializer.deserialize(jsonStr)

                entries.add(
                    EditTimelineEntry(
                        id = i,
                        timestamp = null, /** 日志条目不包含时间戳 */
                        contentPreview = if (annotatedString.text.length > 30)
                            "${annotatedString.text.take(30)}..." else annotatedString.text,
                        fullText = annotatedString.text,
                        /** V2.7: 保存原始序列化 JSON，用于恢复完整 AnnotatedString（含 SpanStyle） */
                        annotatedJson = jsonStr,
                        type = type,
                        isCurrent = false
                    )
                )
            }

            entries
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * 编辑时间线条目数据模型
 *
 * 表示时间线上的单个节点，对应一次文本快照状态。
 *
 * @property id 条目序号（在列表中的位置）
 * @property timestamp 时间戳（日志中未记录，暂为 null；未来可扩展）
 * @property contentPreview 内容预览（截取前 30 字符，用于时间线卡片显示）
 * @property fullText 完整纯文本内容（用于显示和搜索）
 * @property annotatedJson V2.7: 原始 AnnotatedString 序列化 JSON（用于恢复完整格式）
 * @property type 条目类型（撤销快照 / 重做快做）
 * @property 是否为当前状态（时间线上高亮显示）
 */
data class EditTimelineEntry(
    val id: Int,
    val timestamp: Long?,
    val contentPreview: String,
    val fullText: String,
    /** V2.7: 保存原始序列化 JSON，恢复时反序列化为完整 AnnotatedString（含 SpanStyle） */
    val annotatedJson: String = "",
    val type: TimelineType,
    val isCurrent: Boolean = false
)

/**
 * 时间线条目类型枚举
 */
enum class TimelineType {
    /** 撤销快照（Undo 栈中的历史状态） */
    UNDO_SNAPSHOT,
    /** 重做快照（Redo 栈中的可恢复状态） */
    REDO_SNAPSHOT
}
