package com.corgimemo.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 柯基偏好设置管理器
 * 使用DataStore存储柯基名字、首次启动标志和反馈设置
 */
class CorgiPreferences(private val dataStore: DataStore<Preferences>) {

    // 创建单例DataStore
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "corgi_preferences")

        @Volatile
        private var instance: CorgiPreferences? = null

        fun getInstance(context: Context): CorgiPreferences {
            return instance ?: synchronized(this) {
                instance ?: CorgiPreferences(context.dataStore).also { instance = it }
            }
        }
    }

    // 定义存储键
    private object Keys {
        val CORGI_NAME = stringPreferencesKey("corgi_name")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val LAST_ACTIVE_TIMESTAMP = stringPreferencesKey("last_active_timestamp")
        val NIGHT_SLEEP_CHECKED_DATE = stringPreferencesKey("night_sleep_checked_date")
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val USER_TYPE = stringPreferencesKey("user_type")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_uri")
        val AUTO_BACKUP_PASSWORD = stringPreferencesKey("auto_backup_password")
        val AUTO_BACKUP_KEEP_COUNT = intPreferencesKey("auto_backup_keep_count")
        val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency")
        val AUTO_BACKUP_LAST_TIME = stringPreferencesKey("auto_backup_last_time")
        val BACKUP_HISTORY = stringPreferencesKey("backup_history")
    }

    /**
     * 获取柯基名字的Flow
     */
    val corgiName: Flow<String?> = dataStore.data
        .map { preferences: Preferences ->
            preferences[Keys.CORGI_NAME]
        }

    /**
     * 获取是否首次启动的Flow
     */
    val isFirstLaunch: Flow<Boolean> = dataStore.data
        .map { preferences: Preferences ->
            preferences[Keys.IS_FIRST_LAUNCH] ?: true
        }

    /**
     * 获取音效反馈开关的Flow
     */
    val soundEnabled: Flow<Boolean> = dataStore.data
        .map { preferences: Preferences ->
            preferences[Keys.SOUND_ENABLED] ?: true
        }

    /**
     * 获取触觉反馈开关的Flow
     */
    val hapticEnabled: Flow<Boolean> = dataStore.data
        .map { preferences: Preferences ->
            preferences[Keys.HAPTIC_ENABLED] ?: true
        }

    /**
     * 获取最后活跃时间戳的Flow
     */
    val lastActiveTimestamp: Flow<String?> = dataStore.data
        .map { preferences: Preferences ->
            preferences[Keys.LAST_ACTIVE_TIMESTAMP]
        }

    /**
     * 获取深夜入睡检查日期的Flow
     */
    val nightSleepCheckedDate: Flow<String?> = dataStore.data
        .map { preferences: Preferences ->
            preferences[Keys.NIGHT_SLEEP_CHECKED_DATE]
        }

    /**
     * 获取是否已完成首次引导的Flow
     */
    val isOnboardingCompleted: Flow<Boolean> = dataStore.data
        .map { preferences: Preferences ->
            preferences[Keys.IS_ONBOARDING_COMPLETED] ?: false
        }

    /**
     * 获取用户类型的Flow
     */
    val userType: Flow<String?> = dataStore.data
        .map { preferences: Preferences ->
            preferences[Keys.USER_TYPE]
        }

    /**
     * 保存柯基名字
     */
    suspend fun saveCorgiName(name: String) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[Keys.CORGI_NAME] = name
        }
    }

    /**
     * 设置首次启动标志为false
     */
    suspend fun setFirstLaunchDone() {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[Keys.IS_FIRST_LAUNCH] = false
        }
    }

    /**
     * 设置音效反馈开关
     */
    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[Keys.SOUND_ENABLED] = enabled
        }
    }

    /**
     * 设置触觉反馈开关
     */
    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[Keys.HAPTIC_ENABLED] = enabled
        }
    }

    /**
     * 保存最后活跃时间戳
     *
     * @param timestamp 时间戳（毫秒）
     */
    suspend fun saveLastActiveTimestamp(timestamp: Long) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[Keys.LAST_ACTIVE_TIMESTAMP] = timestamp.toString()
        }
    }

    /**
     * 获取最后活跃时间戳
     *
     * @return 时间戳（毫秒），如果没有记录则返回当前时间
     */
    suspend fun getLastActiveTimestamp(): Long {
        return dataStore.data.map { preferences ->
            preferences[Keys.LAST_ACTIVE_TIMESTAMP]?.toLongOrNull() ?: System.currentTimeMillis()
        }.first()
    }

    /**
     * 保存深夜入睡检查日期
     * 用于确保每天只检查一次
     *
     * @param date 日期字符串（yyyy-MM-dd）
     */
    suspend fun saveNightSleepCheckedDate(date: String) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[Keys.NIGHT_SLEEP_CHECKED_DATE] = date
        }
    }

    /**
     * 获取深夜入睡检查日期
     *
     * @return 日期字符串，如果没有记录则返回 null
     */
    suspend fun getNightSleepCheckedDate(): String? {
        return dataStore.data.map { preferences ->
            preferences[Keys.NIGHT_SLEEP_CHECKED_DATE]
        }.first()
    }

    /**
     * 设置首次引导完成标志
     */
    suspend fun setOnboardingCompleted() {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[Keys.IS_ONBOARDING_COMPLETED] = true
        }
    }

    /**
     * 保存用户类型
     *
     * @param userType 用户类型字符串
     */
    suspend fun saveUserType(userType: String) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[Keys.USER_TYPE] = userType
        }
    }

    /**
     * 获取提醒提前量的 Key
     *
     * @param categoryId 分类 ID
     * @return DataStore Key
     */
    private fun getReminderAdvanceKey(categoryId: Long) =
        intPreferencesKey("reminder_advance_$categoryId")

    /**
     * 获取指定分类的提醒提前量（Flow）
     *
     * @param categoryId 分类 ID
     * @return 提前分钟数的 Flow
     */
    fun getReminderAdvanceFlow(categoryId: Long): Flow<Int?> = dataStore.data
        .map { preferences ->
            preferences[getReminderAdvanceKey(categoryId)]
        }

    /**
     * 获取指定分类的提醒提前量（一次获取）
     *
     * @param categoryId 分类 ID
     * @return 提前分钟数，未设置则返回 null
     */
    suspend fun getReminderAdvanceMinutes(categoryId: Long): Int? {
        val key = getReminderAdvanceKey(categoryId)
        return dataStore.data.map { prefs ->
            prefs[key]
        }.first()
    }

    /**
     * 保存指定分类的提醒提前量
     *
     * @param categoryId 分类 ID
     * @param minutes 提前分钟数
     */
    suspend fun saveReminderAdvanceMinutes(categoryId: Long, minutes: Int) {
        val key = getReminderAdvanceKey(categoryId)
        dataStore.edit { prefs ->
            prefs[key] = minutes
        }
    }

    /**
     * 清除指定分类的提醒提前量设置
     *
     * @param categoryId 分类 ID
     */
    suspend fun clearReminderAdvanceMinutes(categoryId: Long) {
        val key = getReminderAdvanceKey(categoryId)
        dataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    /**
     * 获取节气卡片关闭状态的 Key
     * Key 格式：solar_term_card_dismissed_{solarTermId}_{yyyyMMdd}
     * 日期是为了确保每天只记录一次
     *
     * @param solarTermId 节气 ID
     * @param date 日期字符串（yyyyMMdd）
     * @return DataStore Key
     */
    private fun getSolarTermCardDismissedKey(solarTermId: String, date: String) =
        booleanPreferencesKey("solar_term_card_dismissed_${solarTermId}_$date")

    /**
     * 检查今天是否已关闭过某个节气的科普卡片
     *
     * @param solarTermId 节气 ID
     * @param today 今天的日期字符串（yyyyMMdd）
     * @return 是否已关闭
     */
    suspend fun isSolarTermCardDismissed(solarTermId: String, today: String): Boolean {
        val key = getSolarTermCardDismissedKey(solarTermId, today)
        return dataStore.data.map { prefs ->
            prefs[key] ?: false
        }.first()
    }

    /**
     * 保存节气卡片的关闭状态
     * 关闭后当天不再显示
     *
     * @param solarTermId 节气 ID
     * @param today 今天的日期字符串（yyyyMMdd）
     */
    suspend fun saveSolarTermCardDismissed(solarTermId: String, today: String) {
        val key = getSolarTermCardDismissedKey(solarTermId, today)
        dataStore.edit { prefs ->
            prefs[key] = true
        }
    }

    /**
     * 清理过期的节气卡片关闭状态
     * 保留最近 30 天的数据
     *
     * @param currentDate 当前日期（yyyyMMdd）
     */
    suspend fun cleanupExpiredSolarTermCardDismissedKeys(currentDate: String) {
        val currentDateInt = currentDate.toIntOrNull() ?: return
        val keysToRemove = mutableListOf<Preferences.Key<*>>()

        dataStore.data.collect { prefs ->
            prefs.asMap().keys.forEach { key ->
                val keyName = key.name
                if (keyName.startsWith("solar_term_card_dismissed_")) {
                    val parts = keyName.split("_")
                    if (parts.size >= 5) {
                        val dateStr = parts.last()
                        val dateInt = dateStr.toIntOrNull()
                        if (dateInt != null) {
                            val diff = currentDateInt - dateInt
                            if (diff > 30) {
                                keysToRemove.add(key)
                            }
                        }
                    }
                }
            }

            if (keysToRemove.isNotEmpty()) {
                dataStore.edit { mutablePrefs ->
                    keysToRemove.forEach { mutablePrefs.remove(it) }
                }
            }
        }
    }

    val autoBackupEnabled: Flow<Boolean> = dataStore.data
        .map { prefs ->
            prefs[Keys.AUTO_BACKUP_ENABLED] ?: false
        }

    val autoBackupUri: Flow<String?> = dataStore.data
        .map { prefs ->
            prefs[Keys.AUTO_BACKUP_URI]
        }

    val autoBackupPassword: Flow<String?> = dataStore.data
        .map { prefs ->
            prefs[Keys.AUTO_BACKUP_PASSWORD]
        }

    val autoBackupKeepCount: Flow<Int> = dataStore.data
        .map { prefs ->
            prefs[Keys.AUTO_BACKUP_KEEP_COUNT] ?: 5
        }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun saveAutoBackupUri(uri: String) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_URI] = uri
        }
    }

    suspend fun saveAutoBackupPassword(password: String) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_PASSWORD] = password
        }
    }

    suspend fun clearAutoBackupPassword() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.AUTO_BACKUP_PASSWORD)
        }
    }

    suspend fun saveAutoBackupKeepCount(count: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_KEEP_COUNT] = count
        }
    }

    // ==================== 自动备份频率 ====================

    /**
     * 获取自动备份频率的 Flow
     *
     * @return 频率字符串的 Flow（weekly/monthly）
     */
    val autoBackupFrequency: Flow<String> = dataStore.data
        .map { prefs ->
            prefs[Keys.AUTO_BACKUP_FREQUENCY] ?: "weekly"
        }

    /**
     * 获取自动备份频率（一次获取）
     *
     * @return 频率字符串（weekly/monthly）
     */
    suspend fun getAutoBackupFrequency(): String {
        return dataStore.data.map { prefs ->
            prefs[Keys.AUTO_BACKUP_FREQUENCY] ?: "weekly"
        }.first()
    }

    /**
     * 保存自动备份频率
     *
     * @param frequency 频率字符串（weekly/monthly）
     */
    suspend fun saveAutoBackupFrequency(frequency: String) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_FREQUENCY] = frequency
        }
    }

    // ==================== 上次备份时间 ====================

    /**
     * 获取上次自动备份时间的 Flow
     *
     * @return 时间戳的 Flow
     */
    val autoBackupLastTime: Flow<Long> = dataStore.data
        .map { prefs ->
            prefs[Keys.AUTO_BACKUP_LAST_TIME]?.toLongOrNull() ?: 0L
        }

    /**
     * 获取上次自动备份时间（一次获取）
     *
     * @return 时间戳
     */
    suspend fun getAutoBackupLastTime(): Long {
        return dataStore.data.map { prefs ->
            prefs[Keys.AUTO_BACKUP_LAST_TIME]?.toLongOrNull() ?: 0L
        }.first()
    }

    /**
     * 更新上次自动备份时间为当前时间
     */
    suspend fun updateAutoBackupLastTime() {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_LAST_TIME] = System.currentTimeMillis().toString()
        }
    }

    // ==================== 自动备份启用状态 ====================

    /**
     * 获取自动备份是否启用（一次获取）
     *
     * @return 是否启用
     */
    suspend fun getAutoBackupEnabled(): Boolean {
        return dataStore.data.map { prefs ->
            prefs[Keys.AUTO_BACKUP_ENABLED] ?: false
        }.first()
    }

    /**
     * 获取自动备份位置 URI（一次获取）
     *
     * @return 位置 URI，未设置则返回 null
     */
    suspend fun getAutoBackupUri(): String? {
        return dataStore.data.map { prefs ->
            prefs[Keys.AUTO_BACKUP_URI]
        }.first()
    }

    /**
     * 获取自动备份保留版本数（一次获取）
     *
     * @return 保留数量
     */
    suspend fun getAutoBackupKeepCount(): Int {
        return dataStore.data.map { prefs ->
            prefs[Keys.AUTO_BACKUP_KEEP_COUNT] ?: 5
        }.first()
    }

    // ==================== 备份历史 ====================

    /**
     * 获取备份历史的 Flow
     *
     * @return 备份历史 JSON 字符串的 Flow
     */
    val backupHistory: Flow<String?> = dataStore.data
        .map { prefs ->
            prefs[Keys.BACKUP_HISTORY]
        }

    /**
     * 获取备份历史（一次获取）
     *
     * @return 备份历史 JSON 字符串
     */
    suspend fun getBackupHistory(): String? {
        return dataStore.data.map { prefs ->
            prefs[Keys.BACKUP_HISTORY]
        }.first()
    }

    /**
     * 保存备份历史
     *
     * @param json 备份历史 JSON 字符串
     */
    suspend fun saveBackupHistory(json: String) {
        dataStore.edit { prefs ->
            prefs[Keys.BACKUP_HISTORY] = json
        }
    }

    /**
     * 清除备份历史
     */
    suspend fun clearBackupHistory() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.BACKUP_HISTORY)
        }
    }
}