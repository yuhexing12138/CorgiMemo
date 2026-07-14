package com.corgimemo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.SpecialDateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
     * 自定义日期类型列表（与侧滑栏、数据统计页共享同一数据源）
     *
     * 用于类型选择弹窗（DateTypePickerBottomSheet）展示已有自定义类型，
     * 确保新建/编辑日期时可以选择侧滑栏中添加的自定义类型。
     */
    val customDateTypes: StateFlow<List<CustomDateType>> = repository.allCustomDateTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 新建自定义日期类型
     *
     * 在新建/编辑日期页中通过"自定义"输入创建新类型时调用。
     * 创建后返回类型 ID，调用方可用于构建 "CUSTOM:<id>" 存储格式。
     *
     * @param name 类型名称
     * @param emoji 类型 emoji（默认 📅）
     * @return 新建类型的 ID
     */
    suspend fun addCustomType(name: String, emoji: String = "📅"): Long {
        return repository.insertCustomDateType(name, emoji)
    }

    /**
     * 根据 ID 获取自定义类型
     *
     * 用于编辑模式下加载日期时，解析 "CUSTOM:<id>" 格式获取类型名称和 emoji。
     *
     * @param id 自定义类型 ID
     * @return 对应的 CustomDateType，不存在返回 null
     */
    fun getCustomTypeById(id: Long): CustomDateType? {
        return customDateTypes.value.find { it.id == id }
    }

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
