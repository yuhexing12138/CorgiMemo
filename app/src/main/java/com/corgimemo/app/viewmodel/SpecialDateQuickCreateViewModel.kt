package com.corgimemo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.SpecialDateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 日期快速创建/编辑页 ViewModel
 *
 * 负责管理创建/编辑页的数据加载和保存操作。
 *
 * @param repository 特殊日期仓库
 */
@HiltViewModel
class SpecialDateQuickCreateViewModel @Inject constructor(
    private val repository: SpecialDateRepository
) : ViewModel() {

    private val _loadedDate = MutableStateFlow<SpecialDate?>(null)
    val loadedDate: StateFlow<SpecialDate?> = _loadedDate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /**
     * 根据ID加载日期数据（编辑模式）
     *
     * @param id 日期ID
     */
    fun loadDate(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            val date = repository.getById(id)
            _loadedDate.value = date
            _isLoading.value = false
        }
    }

    /**
     * 更新日期（编辑模式保存）
     *
     * @param date 更新后的日期实体
     */
    fun updateDate(date: SpecialDate) {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                repository.update(date)
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "保存失败")
            }
        }
    }

    /**
     * 重置保存状态
     */
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
