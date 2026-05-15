package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面视图模型
 * 管理音效反馈和触觉反馈开关状态
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val corgiPreferences: CorgiPreferences
) : ViewModel() {

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

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
}
