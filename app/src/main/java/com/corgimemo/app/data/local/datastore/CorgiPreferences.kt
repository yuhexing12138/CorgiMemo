package com.corgimemo.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 柯基偏好设置管理器
 * 使用DataStore存储柯基名字、首次启动标志和反馈设置
 */
class CorgiPreferences(context: Context) {

    // 创建DataStore实例
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "corgi_preferences")

    private val dataStore: DataStore<Preferences> = context.dataStore

    // 定义存储键
    companion object {
        private val CORGI_NAME = stringPreferencesKey("corgi_name")
        private val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        private val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        // 行为追踪相关键
        private val LAST_ACTIVE_TIMESTAMP = stringPreferencesKey("last_active_timestamp")
        private val NIGHT_SLEEP_CHECKED_DATE = stringPreferencesKey("night_sleep_checked_date")
    }

    /**
     * 获取柯基名字的Flow
     */
    val corgiName: Flow<String?> = dataStore.data
        .map { preferences: Preferences ->
            preferences[CORGI_NAME]
        }

    /**
     * 获取是否首次启动的Flow
     */
    val isFirstLaunch: Flow<Boolean> = dataStore.data
        .map { preferences: Preferences ->
            preferences[IS_FIRST_LAUNCH] ?: true
        }

    /**
     * 获取音效反馈开关的Flow
     */
    val soundEnabled: Flow<Boolean> = dataStore.data
        .map { preferences: Preferences ->
            preferences[SOUND_ENABLED] ?: true
        }

    /**
     * 获取触觉反馈开关的Flow
     */
    val hapticEnabled: Flow<Boolean> = dataStore.data
        .map { preferences: Preferences ->
            preferences[HAPTIC_ENABLED] ?: true
        }

    /**
     * 获取最后活跃时间戳的Flow
     */
    val lastActiveTimestamp: Flow<String?> = dataStore.data
        .map { preferences: Preferences ->
            preferences[LAST_ACTIVE_TIMESTAMP]
        }

    /**
     * 获取深夜入睡检查日期的Flow
     */
    val nightSleepCheckedDate: Flow<String?> = dataStore.data
        .map { preferences: Preferences ->
            preferences[NIGHT_SLEEP_CHECKED_DATE]
        }

    /**
     * 保存柯基名字
     */
    suspend fun saveCorgiName(name: String) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[CORGI_NAME] = name
        }
    }

    /**
     * 设置首次启动标志为false
     */
    suspend fun setFirstLaunchDone() {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[IS_FIRST_LAUNCH] = false
        }
    }

    /**
     * 设置音效反馈开关
     */
    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[SOUND_ENABLED] = enabled
        }
    }

    /**
     * 设置触觉反馈开关
     */
    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[HAPTIC_ENABLED] = enabled
        }
    }

    /**
     * 保存最后活跃时间戳
     *
     * @param timestamp 时间戳（毫秒）
     */
    suspend fun saveLastActiveTimestamp(timestamp: Long) {
        dataStore.edit { preferences: MutablePreferences ->
            preferences[LAST_ACTIVE_TIMESTAMP] = timestamp.toString()
        }
    }

    /**
     * 获取最后活跃时间戳
     *
     * @return 时间戳（毫秒），如果没有记录则返回当前时间
     */
    suspend fun getLastActiveTimestamp(): Long {
        return dataStore.data.map { preferences ->
            preferences[LAST_ACTIVE_TIMESTAMP]?.toLongOrNull() ?: System.currentTimeMillis()
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
            preferences[NIGHT_SLEEP_CHECKED_DATE] = date
        }
    }

    /**
     * 获取深夜入睡检查日期
     *
     * @return 日期字符串，如果没有记录则返回 null
     */
    suspend fun getNightSleepCheckedDate(): String? {
        return dataStore.data.map { preferences ->
            preferences[NIGHT_SLEEP_CHECKED_DATE]
        }.first()
    }
}