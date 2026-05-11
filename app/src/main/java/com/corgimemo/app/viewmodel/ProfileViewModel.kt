package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 个人页面 ViewModel
 * 
 * 管理用户设置相关的状态和逻辑
 */
class ProfileViewModel : ViewModel() {

    /**
     * UI 状态类
     * 
     * @property isDarkMode 是否启用深色模式
     * @property error 错误信息（可选）
     */
    data class UiState(
        val isDarkMode: Boolean = false,
        val error: String? = null
    )

    // 内部可变状态
    private val _uiState = MutableStateFlow(UiState())
    
    // 对外暴露的不可变状态
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 切换深色模式
     */
    fun toggleDarkMode() {
        viewModelScope.launch {
            try {
                val currentMode = _uiState.value.isDarkMode
                _uiState.value = _uiState.value.copy(isDarkMode = !currentMode)
                
                // 这里可以添加保存到 DataStore 的逻辑
                // saveDarkModeSetting(!currentMode)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /**
     * 加载用户设置（从 DataStore 读取）
     */
    fun loadSettings() {
        viewModelScope.launch {
            try {
                // 这里可以添加从 DataStore 读取深色模式设置的逻辑
                // val darkMode = dataStoreRepository.getDarkMode()
                // _uiState.value = _uiState.value.copy(isDarkMode = darkMode)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}