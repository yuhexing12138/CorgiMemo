package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.model.UserType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首次引导 ViewModel
 *
 * 管理引导流程的状态，包括用户选择、页面跳转、数据保存
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val corgiPreferences: CorgiPreferences,
    private val corgiRepository: CorgiRepository
) : ViewModel() {

    /**
     * 当前页面索引（0-4 对应 5 个引导页面）
     */
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    /**
     * 用户选择的身份类型
     */
    private val _selectedUserType = MutableStateFlow<UserType?>(null)
    val selectedUserType: StateFlow<UserType?> = _selectedUserType.asStateFlow()

    /**
     * 用户输入的柯基名字
     */
    private val _corgiName = MutableStateFlow("")
    val corgiName: StateFlow<String> = _corgiName.asStateFlow()

    /**
     * 是否正在完成引导
     */
    private val _isCompleting = MutableStateFlow(false)
    val isCompleting: StateFlow<Boolean> = _isCompleting.asStateFlow()

    /**
     * 总页面数
     */
    val totalPages = 5

    /**
     * 是否在最后一页
     */
    val isLastPage: Boolean
        get() = _currentPage.value == totalPages - 1

    /**
     * 是否可以进入下一页
     */
    fun canGoNext(): Boolean {
        return when (_currentPage.value) {
            1 -> _selectedUserType.value != null  // 身份选择页需要选择
            2 -> _corgiName.value.isNotEmpty()    // 命名页需要输入名字
            else -> true
        }
    }

    /**
     * 进入下一页
     */
    fun nextPage() {
        if (_currentPage.value < totalPages - 1 && canGoNext()) {
            _currentPage.value++
        }
    }

    /**
     * 返回上一页
     */
    fun prevPage() {
        if (_currentPage.value > 0) {
            _currentPage.value--
        }
    }

    /**
     * 跳转到指定页面
     *
     * @param page 目标页面索引
     */
    fun goToPage(page: Int) {
        if (page in 0 until totalPages) {
            _currentPage.value = page
        }
    }

    /**
     * 设置用户身份类型
     *
     * @param type 用户类型（上班族/学生）
     */
    fun setUserType(type: UserType) {
        _selectedUserType.value = type
    }

    /**
     * 设置柯基名字
     *
     * @param name 柯基名字
     */
    fun setCorgiName(name: String) {
        if (name.length <= 8) {
            _corgiName.value = name
        }
    }

    /**
     * 验证柯基名字是否有效
     *
     * @return true 如果名字在 1-8 个字符之间
     */
    fun isValidName(): Boolean {
        return _corgiName.value.length in 1..8
    }

    /**
     * 完成引导流程
     * 保存用户设置并标记引导完成
     *
     * @param onComplete 完成后的回调
     */
    fun completeOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isCompleting.value = true

            try {
                val userType = _selectedUserType.value ?: UserType.WORKER
                val corgiName = _corgiName.value.ifEmpty { "小柯基" }

                corgiPreferences.saveUserType(userType.typeValue)
                corgiPreferences.saveCorgiName(corgiName)
                corgiPreferences.setFirstLaunchDone()
                corgiPreferences.setOnboardingCompleted()

                if (corgiRepository.getCorgiData() == null) {
                    corgiRepository.insertCorgi(
                        CorgiData(
                            name = corgiName,
                            level = 1,
                            experience = 0,
                            moodValue = 50,
                            lastActiveDate = System.currentTimeMillis().toString()
                        )
                    )
                }

                onComplete()
            } finally {
                _isCompleting.value = false
            }
        }
    }

    /**
     * 跳过引导
     * 使用默认值完成引导
     *
     * @param onComplete 完成后的回调
     */
    fun skipOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isCompleting.value = true

            try {
                corgiPreferences.saveUserType(UserType.WORKER.typeValue)
                corgiPreferences.saveCorgiName("小柯基")
                corgiPreferences.setFirstLaunchDone()
                corgiPreferences.setOnboardingCompleted()

                if (corgiRepository.getCorgiData() == null) {
                    corgiRepository.insertCorgi(
                        CorgiData(
                            name = "小柯基",
                            level = 1,
                            experience = 0,
                            moodValue = 50,
                            lastActiveDate = System.currentTimeMillis().toString()
                        )
                    )
                }

                onComplete()
            } finally {
                _isCompleting.value = false
            }
        }
    }
}
