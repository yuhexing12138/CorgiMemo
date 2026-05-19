package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.backup.BackupManager
import com.corgimemo.app.backup.BackupManager.ExportFormat
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.repository.CategoryRepository
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
    private val corgiPreferences: CorgiPreferences,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _userType = MutableStateFlow<UserType>(UserType.WORKER)
    val userType: StateFlow<UserType> = _userType.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _reminderAdvances = MutableStateFlow<Map<Long, Int?>>(emptyMap())
    val reminderAdvances: StateFlow<Map<Long, Int?>> = _reminderAdvances.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    init {
        loadSettings()
        loadCategories()
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
     * 加载所有分类
     */
    private fun loadCategories() {
        viewModelScope.launch {
            val categoriesList = categoryRepository.getAllCategoriesList()
            _categories.value = categoriesList
            loadAllReminderAdvances(categoriesList)
        }
    }

    /**
     * 加载所有分类的提醒提前量
     *
     * @param categories 分类列表
     */
    private fun loadAllReminderAdvances(categories: List<Category>) {
        viewModelScope.launch {
            val advances = mutableMapOf<Long, Int?>()
            categories.forEach { category ->
                val advance = corgiPreferences.getReminderAdvanceMinutes(category.id)
                advances[category.id] = advance
            }
            _reminderAdvances.value = advances
        }
    }

    /**
     * 保存指定分类的提醒提前量
     *
     * @param categoryId 分类 ID
     * @param minutes 提前分钟数（null 表示使用默认值）
     */
    fun saveReminderAdvance(categoryId: Long, minutes: Int?) {
        viewModelScope.launch {
            if (minutes != null) {
                corgiPreferences.saveReminderAdvanceMinutes(categoryId, minutes)
            } else {
                corgiPreferences.clearReminderAdvanceMinutes(categoryId)
            }

            val currentAdvances = _reminderAdvances.value.toMutableMap()
            currentAdvances[categoryId] = minutes
            _reminderAdvances.value = currentAdvances
        }
    }

    /**
     * 获取指定分类的提醒提前量显示文本
     *
     * @param category 分类
     * @return 显示文本
     */
    fun getReminderAdvanceText(category: Category): String {
        val minutes = _reminderAdvances.value[category.id]
            ?: getDefaultAdvanceMinutes(category.type)
        return when (minutes) {
            0 -> "不提前"
            in 1..59 -> "${minutes}分钟"
            else -> "${minutes / 60}小时"
        }
    }

    /**
     * 获取指定分类类型的默认提前量（分钟）
     * 学习：2小时(120分钟)、工作：30分钟、生活：1小时(60分钟)、自定义：30分钟
     *
     * @param categoryType 分类类型值
     * @return 默认提前分钟数
     */
    private fun getDefaultAdvanceMinutes(categoryType: Int): Int {
        return when (categoryType) {
            CategoryType.STUDY -> 120
            CategoryType.WORK -> 30
            CategoryType.LIFE -> 60
            CategoryType.CUSTOM -> 30
            else -> 30
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
