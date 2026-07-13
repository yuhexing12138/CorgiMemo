package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.InspirationRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.model.UserType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 权限类型枚举
 * 定义引导流程中需要请求的权限类型
 */
enum class PermissionType(val permission: String, val displayName: String) {
    /** 通知权限（Android 13+ 为 POST_NOTIFICATIONS） */
    NOTIFICATION(android.Manifest.permission.POST_NOTIFICATIONS, "通知权限"),
    /** 存储权限（Android 13+ 为 READ_MEDIA_IMAGES） */
    STORAGE(android.Manifest.permission.READ_MEDIA_IMAGES, "存储权限"),
    /** 麦克风权限 */
    MICROPHONE(android.Manifest.permission.RECORD_AUDIO, "麦克风权限"),
    /** 位置权限 */
    LOCATION(android.Manifest.permission.ACCESS_COARSE_LOCATION, "位置权限"),
    /** 相机权限 */
    CAMERA(android.Manifest.permission.CAMERA, "相机权限")
}

/**
 * 权限状态枚举
 */
enum class PermissionState {
    /** 未请求 */
    NOT_REQUESTED,
    /** 已授权 */
    GRANTED,
    /** 已拒绝 */
    DENIED,
    /** 永久拒绝（勾选了"不再询问"） */
    PERMANENTLY_DENIED
}

/**
 * 首次引导 ViewModel
 *
 * 管理引导流程的状态，包括 10 页导航、用户选择、
 * 创建首个待办/灵感、权限请求状态管理
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val corgiPreferences: CorgiPreferences,
    private val corgiRepository: CorgiRepository,
    private val todoRepository: TodoRepository,
    private val inspirationRepository: InspirationRepository
) : ViewModel() {

    /** 总页面数（10 页，索引 0-9） */
    val totalPages = 10

    /** 当前页面索引（0-9） */
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    /** 用户选择的身份类型（仅 STUDENT 可选） */
    private val _selectedUserType = MutableStateFlow<UserType?>(null)
    val selectedUserType: StateFlow<UserType?> = _selectedUserType.asStateFlow()

    /** 用户输入的柯基名字 */
    private val _corgiName = MutableStateFlow("")
    val corgiName: StateFlow<String> = _corgiName.asStateFlow()

    /** 引导中创建的待办 */
    private val _createdTodoCount = MutableStateFlow(0)
    val createdTodoCount: StateFlow<Int> = _createdTodoCount.asStateFlow()

    /** 引导中创建的灵感 */
    private val _createdInspirationCount = MutableStateFlow(0)
    val createdInspirationCount: StateFlow<Int> = _createdInspirationCount.asStateFlow()

    /** 权限状态映射 */
    private val _permissionStates = MutableStateFlow<Map<PermissionType, PermissionState>>(
        PermissionType.entries.associateWith { PermissionState.NOT_REQUESTED }
    )
    val permissionStates: StateFlow<Map<PermissionType, PermissionState>> = _permissionStates.asStateFlow()

    /** 是否正在完成引导 */
    private val _isCompleting = MutableStateFlow(false)
    val isCompleting: StateFlow<Boolean> = _isCompleting.asStateFlow()

    /** 是否在最后一页 */
    val isLastPage: Boolean
        get() = _currentPage.value == totalPages - 1

    /**
     * 是否可以进入下一页（响应式 StateFlow）
     * 根据当前页面 + 身份 + 名字 + 权限状态派生
     * UI 应订阅此 StateFlow 而非调用 canGoNext() 方法
     */
    val canGoNext: StateFlow<Boolean> = combine(
        _currentPage,
        _selectedUserType,
        _corgiName,
        _permissionStates
    ) { page, userType, name, perms ->
        when (page) {
            1 -> userType == UserType.STUDENT  // 身份选择页：仅学生可选
            2 -> name.isNotEmpty()             // 命名页：需要输入名字
            8 -> perms.values.all { it != PermissionState.NOT_REQUESTED } // 权限页：所有权限需请求过
            else -> true
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    /**
     * 是否可以进入下一页（同步方法版本）
     * 内部 nextPage() 使用此方法进行校验
     * UI 层应使用 canGoNext StateFlow
     *
     * @return true 如果可以进入下一页
     */
    fun canGoNext(): Boolean {
        return when (_currentPage.value) {
            1 -> _selectedUserType.value == UserType.STUDENT  // 身份选择页：仅学生可选
            2 -> _corgiName.value.isNotEmpty()                 // 命名页：需要输入名字
            8 -> allPermissionsRequested()                     // 权限页：所有权限需请求过
            else -> true
        }
    }

    /**
     * 进入下一页
     * 检查 canGoNext 后递增页面索引
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
     * 跳过功能介绍，直接跳到权限请求页（Step 9，索引 8）
     */
    fun skipToPermission() {
        _currentPage.value = 8
    }

    /**
     * 设置用户身份类型
     * 仅接受 STUDENT，其他类型忽略
     *
     * @param type 用户类型
     */
    fun setUserType(type: UserType) {
        if (type == UserType.STUDENT) {
            _selectedUserType.value = type
        }
    }

    /**
     * 设置柯基名字
     * 限制最多 8 个字符
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
     * 创建首个待办
     * 在引导流程中创建并保存到 Room 数据库
     *
     * @param title 待办标题
     * @param priority 优先级（0=低, 1=中, 2=高）
     */
    fun createFirstTodo(title: String, priority: Int) {
        if (title.isBlank()) return

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val todo = TodoItem(
                    title = title,
                    categoryId = 0L,
                    priority = priority,
                    status = 0,
                    repeatType = 0,
                    createdAt = now,
                    updatedAt = now
                )
                todoRepository.insertTodo(todo)
                _createdTodoCount.value = _createdTodoCount.value + 1
            } catch (e: Exception) {
                // 创建失败静默处理，用户可跳过
            }
        }
    }

    /**
     * 创建首个灵感
     * 在引导流程中创建并保存到 Room 数据库
     *
     * @param title 灵感标题
     * @param content 灵感内容
     */
    fun createFirstInspiration(title: String, content: String) {
        if (title.isBlank()) return

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val inspiration = Inspiration(
                    title = title,
                    content = content,
                    createdAt = now,
                    updatedAt = now
                )
                inspirationRepository.insert(inspiration)
                _createdInspirationCount.value = _createdInspirationCount.value + 1
            } catch (e: Exception) {
                // 创建失败静默处理，用户可跳过
            }
        }
    }

    /**
     * 更新权限状态
     *
     * @param type 权限类型
     * @param state 权限状态
     */
    fun updatePermissionState(type: PermissionType, state: PermissionState) {
        _permissionStates.value = _permissionStates.value.toMutableMap().apply {
            this[type] = state
        }
    }

    /**
     * 检查所有权限是否都已请求过
     *
     * @return true 如果所有权限都不是 NOT_REQUESTED 状态
     */
    fun allPermissionsRequested(): Boolean {
        return _permissionStates.value.values.all { it != PermissionState.NOT_REQUESTED }
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
                val userType = _selectedUserType.value ?: UserType.STUDENT
                val corgiName = _corgiName.value.ifEmpty { "小柯基" }

                corgiPreferences.saveUserType(userType.typeValue)
                corgiPreferences.saveCorgiName(corgiName)
                corgiPreferences.setFirstLaunchDone()
                corgiPreferences.setOnboardingCompleted()
                corgiPreferences.setFirstGuideShown()
                corgiPreferences.saveGuideCompletedAt(System.currentTimeMillis())

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
                corgiPreferences.saveUserType(UserType.STUDENT.typeValue)
                corgiPreferences.saveCorgiName("小柯基")
                corgiPreferences.setFirstLaunchDone()
                corgiPreferences.setOnboardingCompleted()
                corgiPreferences.setFirstGuideShown()

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
