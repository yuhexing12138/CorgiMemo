package com.corgimemo.app.backup.data

import kotlinx.serialization.Serializable

/**
 * 备份元数据
 * 存储备份的版本信息、导出时间等
 *
 * @property version 备份格式版本号
 * @property exportTime 导出时间（ISO 8601 格式）
 * @property appVersion 导出时的应用版本号
 * @property encrypted 是否加密
 */
@Serializable
data class BackupMeta(
    val version: Int = CURRENT_VERSION,
    val exportTime: String,
    val appVersion: String,
    val encrypted: Boolean = false
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
