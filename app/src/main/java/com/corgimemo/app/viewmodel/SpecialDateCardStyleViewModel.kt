package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.SpecialDateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 保存状态机
 *
 * 描述 SpecialDateCardStyleViewModel 写入新日期时的阶段性状态:
 * - [Idle]:    初始状态,可发起新的保存
 * - [Saving]:  正在执行 repository.insert,此时拒绝重复触发
 * - [Success]: 写入成功,UI 收到信号后跳转回主页并清空表单
 * - [Error]:   写入失败,内含 message 供 UI 弹 Snackbar
 */
sealed class SaveState {
    /** 初始状态:未触发保存 */
    object Idle : SaveState()

    /** 正在保存:已发起 repository.insert 协程,期间忽略后续点击 */
    object Saving : SaveState()

    /** 保存成功:SpecialDate 已落库,通知主页更新列表 */
    object Success : SaveState()

    /** 保存失败:携带错误信息(优先使用异常 message) */
    data class Error(val message: String) : SaveState()
}

/**
 * 日期卡片样式选择页 ViewModel
 *
 * 负责将 QuickCreate 表单数据(标题/日期/分类/置顶)与用户选中的卡片样式
 * 封装为 [SpecialDate] 实体并落库到 Room。
 *
 * 调用入口为 `SpecialDateCardStyleScreen` 的「完成」按钮。
 * 主页 `SpecialDateScreen` 通过 SavedStateHandle("date_saved") 监听写入结果并刷新列表。
 */
@HiltViewModel
class SpecialDateCardStyleViewModel @Inject constructor(
    private val repository: SpecialDateRepository
) : ViewModel() {

    /** 内部可变状态流,仅本类内可写 */
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)

    /** 对外暴露的只读状态流,UI 端用 collectAsState 订阅 */
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /**
     * 保存新日期
     *
     * 把 QuickCreate 表单数据 + 卡片样式 + 卡片颜色组合成一个完整的 [SpecialDate] 实体并写入 Room。
     * 写入过程通过 [SaveState] 暴露,写入期间(状态为 [SaveState.Saving])拒绝重复触发。
     *
     * @param title      名称(来自 QuickCreate)
     * @param dateMillis 目标日期时间戳(毫秒,来自 QuickCreate)
     * @param category   分类(预设枚举名如 "BIRTHDAY",或自定义字符串)
     * @param isPinned   是否置顶(来自 QuickCreate)
     * @param cardStyle  用户在样式选择页选中的 [DateCardStyle](存库时用其 serialName 字符串)
     * @param cardColor  用户在样式选择页选中的 [DateCardColor](存库时用其 serialName 字符串,默认 DEFAULT)
     */
    fun saveNewDate(
        title: String,
        dateMillis: Long,
        category: String,
        isPinned: Boolean,
        cardStyle: DateCardStyle,
        cardColor: DateCardColor = DateCardColor.DEFAULT  // ← 新增,默认 DEFAULT 保持向后兼容
    ) {
        // 1. 重复触发防护:正在保存时,直接丢弃后续点击,避免重复插入导致数据错乱
        if (_saveState.value is SaveState.Saving) return

        // 2. 启动协程写入,状态先切到 Saving(占位,UI 可显示 loading)
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                // 3. 构造实体:cardStyle/cardColor 存 serialName 字符串,createdAt/updatedAt 均取当前时间
                val now = System.currentTimeMillis()
                val newDate = SpecialDate(
                    title = title,
                    targetDate = dateMillis,
                    category = category,
                    countMode = 0,
                    repeatType = 0,
                    reminderDays = 0,
                    content = "",
                    tags = "",
                    imagePaths = "",
                    imageUrls = "",
                    isPinned = isPinned,
                    isArchived = false,
                    cardStyle = cardStyle.serialName,
                    cardColor = cardColor.serialName,  // ← 新增
                    createdAt = now,
                    updatedAt = now
                )
                // 4. 写入 Room(suspend,由 Hilt 注入的 repository 在 IO 调度器执行)
                repository.insert(newDate)
                // 5. 写入成功:切到 Success,UI 收到后跳转并清栈
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                // 6. 写入失败:捕获任意异常,使用异常的 message,缺省时给出兜底文案
                _saveState.value = SaveState.Error(e.message ?: "保存失败")
            }
        }
    }
}
