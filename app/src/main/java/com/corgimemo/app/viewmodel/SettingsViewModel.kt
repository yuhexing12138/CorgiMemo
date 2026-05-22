package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.backup.BackupManager
import com.corgimemo.app.backup.BackupManager.ExportFormat
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.model.UserType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面视图模型
 * 管理音效反馈、触觉反馈、用户身份设置和数据备份
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val corgiPreferences: CorgiPreferences
) : ViewModel() {

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _userType = MutableStateFlow<UserType>(UserType.WORKER)
    val userType: StateFlow<UserType> = _userType.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            _soundEnabled.value = corgiPreferences.soundEnabled.first()
            _hapticEnabled.value = corgiPreferences.hapticEnabled.first()

            val userTypeValue = corgiPreferences.userType.first()
            _userType.value = UserType.fromValue(userTypeValue)
        }
    }

    /**
     * 设置音效反馈开关
     */
    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _soundEnabled.value = enabled
            corgiPreferences.setSoundEnabled(enabled)
        }
    }

    /**
     * 设置触觉反馈开关
     */
    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _hapticEnabled.value = enabled
            corgiPreferences.setHapticEnabled(enabled)
        }
    }

    /**
     * 设置用户类型
     *
     * @param userType 新的用户类型
     */
    fun setUserType(userType: UserType) {
        viewModelScope.launch {
            _userType.value = userType
            corgiPreferences.saveUserType(userType.typeValue)
        }
    }

    /**
     * 清除备份消息
     */
    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    /**
     * 导出数据
     *
     * @param context 上下文
     * @param uri 文件 URI
     * @param format 导出格式
     * @param password 密码（可选）
     */
    fun exportData(
        context: android.content.Context,
        uri: android.net.Uri,
        format: ExportFormat,
        password: String? = null
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            val result = BackupManager.exportData(context, uri, format, password)
            when (result) {
                is BackupManager.ExportResult.Success -> {
                    _backupMessage.value = "导出成功！"
                }
                is BackupManager.ExportResult.Error -> {
                    _backupMessage.value = "导出失败：${result.message}"
                }
            }
            _isProcessing.value = false
        }
    }

    /**
     * 恢复数据
     *
     * @param context 上下文
     * @param uri 文件 URI
     * @param password 密码（可选）
     * @param onSuccess 成功回调
     */
    fun restoreData(
        context: android.content.Context,
        uri: android.net.Uri,
        password: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            val result = BackupManager.restoreData(context, uri, password)
            when (result) {
                is BackupManager.RestoreResult.Success -> {
                    _backupMessage.value = "恢复成功！已恢复 ${result.todoCount} 个待办"
                    onSuccess()
                }
                is BackupManager.RestoreResult.Error -> {
                    _backupMessage.value = "恢复失败：${result.message}"
                }
                BackupManager.RestoreResult.WrongPassword -> {
                    _backupMessage.value = "密码错误"
                }
                BackupManager.RestoreResult.VersionIncompatible -> {
                    _backupMessage.value = "备份文件版本过高，请升级应用"
                }
            }
            _isProcessing.value = false
        }
    }
}
