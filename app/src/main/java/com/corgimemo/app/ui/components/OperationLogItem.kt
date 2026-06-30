package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corgimemo.app.data.local.db.OperationLogEntity
import com.corgimemo.app.data.local.db.OperationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 操作日志列表项组件
 * 显示单条操作记录的详细信息，支持撤销操作
 *
 * @param log 操作日志实体
 * @param onUndo 撤销操作回调
 */
@Composable
fun OperationLogItem(
    log: OperationLogEntity,
    onUndo: (Long) -> Unit
) {
    val operationDisplay = when (log.operationType) {
        OperationType.DELETE -> "删除待办" to "🗑️"
        OperationType.COMPLETE -> "完成待办" to "✅"
        OperationType.BATCH_DELETE -> "批量删除" to "📦"
        OperationType.UNDO_DELETE -> "撤销删除" to "↩️"
        OperationType.UNDO_COMPLETE -> "撤销完成" to "↩️"
        else -> "未知操作" to "❓"
    }

    val (operationText, operationIcon) = operationDisplay

    /** 格式化时间戳为可读时间字符串 */
    val timeFormatted = remember(log.createdAt) {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(log.createdAt))
    }

    /** 从快照中提取待办标题（如果有的话）*/
    val todoTitle = remember(log.snapshotJson) {
        extractTitleFromSnapshot(log.snapshotJson)
            ?: if (log.operationType == OperationType.BATCH_DELETE) {
                "${extractCountFromBatchIds(log.batchIdsJson)} 个待办"
            } else {
                "未知待办"
            }
    }

    /** 判断是否可以撤销（只有 DELETE、COMPLETE、BATCH_DELETE 可以撤销）*/
    val canUndo = log.operationType in listOf(
        OperationType.DELETE,
        OperationType.COMPLETE,
        OperationType.BATCH_DELETE
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /** 左侧：操作图标 */
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            /** 中间：操作详情 */
            Column(
                modifier = Modifier.weight(1f)
            ) {
                /** 操作类型 + 待办标题 */
                Text(
                    text = "$operationIcon $operationText: $todoTitle",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                /** 时间戳 */
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            /** 右侧：撤销按钮（仅对可撤销的操作显示）*/
            if (canUndo) {
                IconButton(
                    onClick = { onUndo(log.id) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "撤销此操作",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 从 JSON 快照中提取待办标题
 *
 * @param snapshotJson JSON 字符串
 * @return 待办标题，提取失败返回 null
 */
private fun extractTitleFromSnapshot(snapshotJson: String): String? {
    return try {
        val pattern = "\"title\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        regex.find(snapshotJson)?.groupValues?.get(1)
    } catch (e: Exception) {
        null
    }
}

/**
 * 从批量 ID JSON 中提取数量
 *
 * @param batchIdsJson 批量 ID 的 JSON 数组字符串
 * @return 数量
 */
private fun extractCountFromBatchIds(batchIdsJson: String?): Int {
    return try {
        batchIdsJson
            ?.removeSurrounding("[", "]")
            ?.split(",")
            ?.size
            ?: 0
    } catch (e: Exception) {
        0
    }
}
