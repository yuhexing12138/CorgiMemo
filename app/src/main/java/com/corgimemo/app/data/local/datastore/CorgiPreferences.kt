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
import kotlinx.coroutines.flow.map

/**
 * 柯基偏好设置管理器
 * 使用DataStore存储柯基名字和首次启动标志
 */
class CorgiPreferences(context: Context) {

    // 创建DataStore实例
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "corgi_preferences")

    private val dataStore: DataStore<Preferences> = context.dataStore

    // 定义存储键
    companion object {
        private val CORGI_NAME = stringPreferencesKey("corgi_name")
        private val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
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
}