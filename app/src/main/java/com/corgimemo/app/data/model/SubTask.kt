package com.corgimemo.app.data.model

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
    val order: Int = 0
)
