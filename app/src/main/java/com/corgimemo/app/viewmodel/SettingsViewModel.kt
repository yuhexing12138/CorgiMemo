package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * 管理音效反馈、触觉反馈和用户身份设置
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

            // 加载用户类型
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
}
