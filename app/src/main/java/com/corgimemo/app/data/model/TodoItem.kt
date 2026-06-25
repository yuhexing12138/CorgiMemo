package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo_items",
    indices = [
        Index(value = ["status", "createdAt"]),
        Index(value = ["categoryId", "status"]),
        Index(value = ["priority", "startDate"]),
        Index(value = ["hasSubTasks"]),
        Index(value = ["dueDate", "status"]),
        Index(value = ["isPinned"])
    ]
)
data class TodoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String? = null,
    val categoryId: Long,
    val priority: Int,
    val status: Int,
    val startDate: Long? = null,

    /** 截止时间（时间戳），用于设置任务必须完成的最后期限 */
    val dueDate: Long? = null,

    val estimatedDurationMinutes: Int? = null,
    val reminderTime: Long? = null,
    val repeatType: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val geofenceLat: Double? = null,
    val geofenceLng: Double? = null,
    val geofenceRadius: Float? = null,
    val geofenceType: Int = 0,
    val geofenceEnabled: Boolean = false,
    val geofenceAddress: String? = null,
    val hasSubTasks: Boolean = false,
    val voiceNotePath: String? = null,
    val voiceDuration: Int? = null,

    /** 本地图片路径 JSON数组，用于存储待办事项关联的图片 */
    @ColumnInfo(defaultValue = "")
    val imagePaths: String = "",

    /**
     * 背景颜色值（ARGB 整数）
     *
     * 用于持久化用户在编辑页选择的卡片背景色。
     * 存储为 Int 类型（ARGB 值），与 Compose Color 类原生兼容。
     *
     * **转换方式**:
     * - 保存: `Color.toArgb()` → Int → 数据库
     * - 读取: 数据库 Int → `Color(Int)` → Compose Color
     *
     * 默认值为 0xFFFFFFFF（白色/透明背景）
     */
    @ColumnInfo(defaultValue = "16777215") // 0xFFFFFFFF = 白色
    val backgroundColor: Int = -1, // -1 = 0xFFFFFFFF（不透明白色）

    /** 手动排序位置索引（从 0 开始，用于拖拽排序功能） */
    val position: Int = 0,

    /**
     * 富文本格式化内容（Markdown 格式）
     *
     * 存储完整的 Markdown 格式文本，保留 **粗体**、*斜体*、~~删除线~~、
     * 列表、标题等格式信息。用于在编辑页恢复富文本显示。
     *
     * **与 content 字段的关系**:
     * - `content`: 纯文本版本（用于搜索、统计字数、显示摘要）
     * - `content_format`: 完整格式版本（用于编辑器恢复显示）
     *
     * **转换方式**:
     * - 保存: AnnotatedString → MarkdownParser.export() → String → 数据库
     * - 读取: 数据库 String → MarkdownParser.parse() → AnnotatedString → 编辑器
     *
     * 默认值为空字符串（无格式化内容时等同于纯文本 content）
     */
    @ColumnInfo(defaultValue = "")
    val contentFormat: String = "",

    /**
     * 是否置顶
     *
     * 置顶的待办在列表中始终排在最前面（按 isPinned DESC 排序）。
     * 用于左滑"置顶"按钮的持久化状态。
     */
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false
)
