package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 子任务实体类
 *
 * @property id 子任务 ID
 * @property todoId 所属父待办 ID
 * @property title 子任务标题
 * @property isCompleted 是否已完成
 * @property createdAt 创建时间
 * @property completedAt 完成时间（可为 null）
 * @property order 排序顺序
 * @property imagePaths 子任务图片附件路径 JSON 数组
 * @property voicePaths 子任务语音附件路径 JSON 数组
 */
@Entity(
    tableName = "sub_tasks",
    indices = [
        Index(value = ["todoId"]),
        Index(value = ["todoId", "order"])
    ]
)
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
