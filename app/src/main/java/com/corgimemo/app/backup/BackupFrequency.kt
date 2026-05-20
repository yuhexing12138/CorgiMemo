package com.corgimemo.app.backup

/**
 * 自动备份频率枚举
 */
enum class BackupFrequency(val displayName: String, val value: String) {
    WEEKLY("每周", "weekly"),
    MONTHLY("每月", "monthly");

    companion object {
        /**
         * 从字符串值获取枚举
         *
         * @param value 字符串值
         * @return 枚举实例
         */
        fun fromValue(value: String): BackupFrequency {
            return entries.find { it.value == value } ?: WEEKLY
        }

        /**
         * 获取所有显示名称列表
         *
         * @return 显示名称列表
         */
        fun displayNames(): List<String> = entries.map { it.displayName }
    }
}
