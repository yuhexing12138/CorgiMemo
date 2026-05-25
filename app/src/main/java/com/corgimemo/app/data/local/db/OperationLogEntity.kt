package com.corgimemo.app.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 操作日志实体类
 * 用于记录待办操作历史，支持撤销功能
 *
 * @param id 日志唯一标识（自增主键）
 * @param operationType 操作类型（DELETE/COMPLETE/BATCH_DELETE/UNDO_DELETE/UNDO_COMPLETE）
 * @param targetId 目标待办 ID（单个操作时使用）
 * @param batchIdsJson 批量操作的 ID 列表（JSON 数组格式）
 * @param snapshotJson 操作前的数据快照（JSON 格式，用于恢复）
 * @param createdAt 创建时间戳（毫秒）
 */
@Entity(
    tableName = "operation_logs",
    indices = [Index("created_at")]
)
data class OperationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "operation_type")
    val operationType: String,

    @ColumnInfo(name = "target_id", defaultValue = "0")
    val targetId: Long = 0,

    @ColumnInfo(name = "batch_ids_json")
    val batchIdsJson: String? = null,

    @ColumnInfo(name = "snapshot_json")
    val snapshotJson: String,

    @ColumnInfo(name = "created_at", defaultValue = "(strftime('%s','now') * 1000)")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 操作类型常量
 */
object OperationType {
    const val DELETE = "DELETE"
    const val COMPLETE = "COMPLETE"
    const val BATCH_DELETE = "BATCH_DELETE"
    const val UNDO_DELETE = "UNDO_DELETE"
    const val UNDO_COMPLETE = "UNDO_COMPLETE"
}
