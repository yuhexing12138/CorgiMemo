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
 * 日期详情页 ViewModel
 *
 * 负责管理详情页的状态：日期列表加载、置顶、归档、删除、备注更新、样式/颜色更新等。
 * 支持左右滑动切换不同日期。
 *
 * @param savedStateHandle 保存状态句柄，用于获取导航参数 dateId
 * @param repository 特殊日期仓库
 */
@HiltViewModel
class SpecialDateDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpecialDateRepository
) : ViewModel() {

    private val initialDateId: Long = savedStateHandle["dateId"] ?: 0L

    private val _allDates = MutableStateFlow<List<SpecialDate>>(emptyList())
    val allDates: StateFlow<List<SpecialDate>> = _allDates.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAllDates()
    }

    /**
     * 加载所有未归档日期列表（用于左右滑动切换）
     */
    private fun loadAllDates() {
        viewModelScope.launch {
            repository.getActiveDates().collect { dates ->
                _allDates.value = dates
                _isLoading.value = false
            }
        }
    }

    /**
     * 切换置顶状态（指定日期ID）
     * 置顶为单选模式，自动取消其他日期的置顶
     *
     * @param id 日期ID
     */
    fun togglePinForDate(id: Long) {
        viewModelScope.launch {
            val date = _allDates.value.find { it.id == id } ?: return@launch
            if (date.isPinned) {
                repository.unpinDate(id)
            } else {
                repository.pinDate(id)
            }
        }
    }

    /**
     * 归档日期（指定日期ID）
     *
     * @param id 日期ID
     */
    fun archiveDate(id: Long) {
        viewModelScope.launch {
            repository.archive(id)
        }
    }

    /**
     * 删除日期
     *
     * @param date 要删除的日期实体
     */
    fun deleteDate(date: SpecialDate) {
        viewModelScope.launch {
            repository.delete(date)
        }
    }

    /**
     * 更新备注（指定日期ID）
     *
     * @param id 日期ID
     * @param content 新的备注内容
     */
    fun updateContentForDate(id: Long, content: String) {
        viewModelScope.launch {
            val date = _allDates.value.find { it.id == id } ?: return@launch
            repository.update(date.copy(content = content, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * 更新卡片样式（指定日期ID）
     *
     * @param id 日期ID
     * @param cardStyle 新的卡片样式
     */
    fun updateCardStyleForDate(id: Long, cardStyle: String) {
        viewModelScope.launch {
            val date = _allDates.value.find { it.id == id } ?: return@launch
            val updatedDate = date.copy(cardStyle = cardStyle, updatedAt = System.currentTimeMillis())
            // 先更新本地状态，实现即时UI响应
            _allDates.value = _allDates.value.map { if (it.id == id) updatedDate else it }
            // 再写入数据库
            repository.update(updatedDate)
        }
    }

    /**
     * 更新卡片颜色（指定日期ID）
     *
     * @param id 日期ID
     * @param cardColor 新的卡片颜色
     */
    fun updateCardColorForDate(id: Long, cardColor: String) {
        viewModelScope.launch {
            val date = _allDates.value.find { it.id == id } ?: return@launch
            val updatedDate = date.copy(cardColor = cardColor, updatedAt = System.currentTimeMillis())
            // 先更新本地状态，实现即时UI响应
            _allDates.value = _allDates.value.map { if (it.id == id) updatedDate else it }
            // 再写入数据库
            repository.update(updatedDate)
        }
    }
}
