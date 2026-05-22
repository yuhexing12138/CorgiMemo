package com.corgimemo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeManager {

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _themeColor = MutableStateFlow("orange")
    val themeColor: StateFlow<String> = _themeColor.asStateFlow()

    fun initTheme(mode: String, color: String) {
        _themeMode.value = mode
        _themeColor.value = color
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    fun setThemeColor(color: String) {
        _themeColor.value = color
    }

    @Composable
    fun isDarkTheme(): Boolean {
        return when (_themeMode.value) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    }
}
