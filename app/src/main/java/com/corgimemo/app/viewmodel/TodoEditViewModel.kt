package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.CategoryKeywordRepository
import com.corgimemo.app.data.repository.CategoryMatcher
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.RepeatTaskManager
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.domain.ReminderRecommender
import com.corgimemo.app.model.UserType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 待办编辑 ViewModel
 */
@HiltViewModel
class TodoEditViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryKeywordRepository: CategoryKeywordRepository,
    private val categoryMatcher: CategoryMatcher,
    private val corgiPreferences: CorgiPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var recommendationJob: Job? = null
    private val reminderRecommender = ReminderRecommender()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _categoryId = MutableStateFlow(0L)
    val categoryId: StateFlow<Long> = _categoryId.asStateFlow()

    private val _priority = MutableStateFlow(1)
    val priority: StateFlow<Int> = _priority.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    val startDate: StateFlow<Long?> = _startDate.asStateFlow()

    private val _estimatedDurationMinutes = MutableStateFlow<Int?>(null)
    val estimatedDurationMinutes: StateFlow<Int?> = _estimatedDurationMinutes.asStateFlow()

    private val _repeatType = MutableStateFlow(0)
    val repeatType: StateFlow<Int> = _repeatType.asStateFlow()

    // 地理围栏相关字段
    private val _geofenceLat = MutableStateFlow<Double?>(null)
    val geofenceLat: StateFlow<Double?> = _geofenceLat.asStateFlow()

    private val _geofenceLng = MutableStateFlow<Double?>(null)
    val geofenceLng: StateFlow<Double?> = _geofenceLng.asStateFlow()

    private val _geofenceRadius = MutableStateFlow<Float?>(100f)
    val geofenceRadius: StateFlow<Float?> = _geofenceRadius.asStateFlow()

    private val _geofenceType = MutableStateFlow(0)
    val geofenceType: StateFlow<Int> = _geofenceType.asStateFlow()

    private val _geofenceEnabled = MutableStateFlow(false)
    val geofenceEnabled: StateFlow<Boolean> = _geofenceEnabled.asStateFlow()

    private val _geofenceAddress = MutableStateFlow<String?>(null)
    val geofenceAddress: StateFlow<String?> = _geofenceAddress.asStateFlow()

    // 子任务相关
    private val _subTasks = MutableStateFlow<List<SubTask>>(emptyList())
    val subTasks: StateFlow<List<SubTask>> = _subTasks.asStateFlow()

    // 分类相关
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _recommendedCategory = MutableStateFlow<Category?>(null)
    val recommendedCategory: StateFlow<Category?> = _recommendedCategory.asStateFlow()

    private val _hasManuallySelectedCategory = MutableStateFlow(false)
    val hasManuallySelectedCategory: StateFlow<Boolean> = _hasManuallySelectedCategory.asStateFlow()

    private val _showKeywordSelection = MutableStateFlow(false)
    val showKeywordSelection: StateFlow<Boolean> = _showKeywordSelection.asStateFlow()

    private val _extractedKeywords = MutableStateFlow<List<String>>(emptyList())
    val extractedKeywords: StateFlow<List<String>> = _extractedKeywords.asStateFlow()

    private val _isCategoriesLoaded = MutableStateFlow(false)
    val isCategoriesLoaded: StateFlow<Boolean> = _isCategoriesLoaded.asStateFlow()

    // 提醒时间相关状态
    private val _reminderTime = MutableStateFlow<Long?>(null)
    val reminderTime: StateFlow<Long?> = _reminderTime.asStateFlow()

    private val _recommendedReminderTime = MutableStateFlow<Long?>(null)
    val recommendedReminderTime: StateFlow<Long?> = _recommendedReminderTime.asStateFlow()

    private val _showReminderRecommendation = MutableStateFlow(false)
    val showReminderRecommendation: StateFlow<Boolean> = _showReminderRecommendation.asStateFlow()

    // 语音备注相关状态
    private val _voiceNotePath = MutableStateFlow<String?>(null)
    val voiceNotePath: StateFlow<String?> = _voiceNotePath.asStateFlow()

    private val _voiceDuration = MutableStateFlow<Int?>(null)
    val voiceDuration: StateFlow<Int?> = _voiceDuration.asStateFlow()

    private var existingTodo: TodoItem? = null

    fun setTitle(title: String) {
        _title.value = title
    }

    /**
     * 设置标题并触发智能分类推荐（带防抖）
     */
    fun setTitleWithRecommendation(title: String) {
        _title.value = title
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch {
            delay(300)
            triggerRecommendation()
        }
    }

    fun setContent(content: String) {
        _content.value = content
    }

    fun setCategoryId(categoryId: Long) {
        _categoryId.value = categoryId
        _hasManuallySelectedCategory.value = true
        updateReminderRecommendation()
    }

    fun setPriority(priority: Int) {
        _priority.value = priority
    }

    fun setStartDate(startDate: Long?) {
        _startDate.value = startDate
        updateReminderRecommendation()
    }

    fun setEstimatedDurationMinutes(minutes: Int?) {
        _estimatedDurationMinutes.value = minutes
        updateReminderRecommendation()
    }

    fun setRepeatType(repeatType: Int) {
        _repeatType.value = repeatType
    }

    // 地理围栏相关方法
    fun setGeofenceLat(lat: Double?) {
        _geofenceLat.value = lat
    }

    fun setGeofenceLng(lng: Double?) {
        _geofenceLng.value = lng
    }

    fun setGeofenceRadius(radius: Float) {
        _geofenceRadius.value = radius
    }

    fun setGeofenceType(type: Int) {
        _geofenceType.value = type
    }

    fun setGeofenceEnabled(enabled: Boolean) {
        _geofenceEnabled.value = enabled
    }

    fun setGeofenceAddress(address: String?) {
        _geofenceAddress.value = address
    }

    // 子任务相关方法

    /**
     * 添加子任务
     *
     * @param title 子任务标题
     */
    fun addSubTask(title: String) {
        if (title.isBlank()) return
        val currentList = _subTasks.value
        val newSubTask = SubTask(
            id = 0,
            todoId = existingTodo?.id ?: 0,
            title = title,
            isCompleted = false,
            order = currentList.size + 1
        )
        _subTasks.value = currentList + newSubTask
    }

    /**
     * 删除子任务
     *
     * @param subTask 要删除的子任务
     */
    fun removeSubTask(subTask: SubTask) {
        val currentList = _subTasks.value
        _subTasks.value = currentList.filter { it.id != subTask.id || it.order != subTask.order }
    }

    /**
     * 切换子任务完成状态（仅在编辑已有待办时持久化到数据库）
     * 如果所有子任务完成，会自动完成父任务
     *
     * @param subTask 子任务
     */
    fun toggleSubTaskCompletion(subTask: SubTask) {
        val currentList = _subTasks.value
        val updatedList = currentList.map {
            if (it.id == subTask.id || (it.id == 0L && it.order == subTask.order)) {
                it.copy(isCompleted = !it.isCompleted)
            } else {
                it
            }
        }
        _subTasks.value = updatedList

        if (existingTodo != null && subTask.id > 0) {
            viewModelScope.launch {
                SubTaskManager.toggleSubTaskCompletion(context, subTask.id)
            }
        }
    }

    /**
     * 加载待办及子任务
     *
     * @param todoId 待办 ID
     */
    fun loadTodo(todoId: Long) {
        viewModelScope.launch {
            todoRepository.getTodoById(todoId)?.let { todo ->
                existingTodo = todo
                _title.value = todo.title
                _content.value = todo.content ?: ""
                _categoryId.value = todo.categoryId
                _hasManuallySelectedCategory.value = todo.categoryId > 0
                _priority.value = todo.priority
                _startDate.value = todo.startDate
                _estimatedDurationMinutes.value = todo.estimatedDurationMinutes
                _repeatType.value = todo.repeatType
                _geofenceLat.value = todo.geofenceLat
                _geofenceLng.value = todo.geofenceLng
                _geofenceRadius.value = todo.geofenceRadius
                _geofenceType.value = todo.geofenceType
                _geofenceEnabled.value = todo.geofenceEnabled
                _geofenceAddress.value = todo.geofenceAddress

                _reminderTime.value = todo.reminderTime

                // 加载语音备注
                _voiceNotePath.value = todo.voiceNotePath
                _voiceDuration.value = todo.voiceDuration

                val subTasks = SubTaskManager.getSubTasks(context, todoId)
                _subTasks.value = subTasks
            }
        }
    }

    /**
     * 保存待办
     *
     * @return 是否成功保存
     */
    fun saveTodo(): Boolean {
        if (_title.value.isBlank()) {
            return false
        }

        if (!_hasManuallySelectedCategory.value) {
            val categoriesList = _categories.value
            
            if (categoriesList.isEmpty()) {
                performSave()
                return true
            }

            val keywords = com.corgimemo.app.data.util.KeywordExtractor.extractKeywords(_title.value)
            if (keywords.isNotEmpty()) {
                _extractedKeywords.value = keywords
                _showKeywordSelection.value = true
                return false
            }
        }

        performSave()
        return true
    }

    private fun performSave() {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val hasSubTasks = _subTasks.value.isNotEmpty()

            val todoId: Long = if (existingTodo != null) {
                val todo = existingTodo!!.copy(
                    title = _title.value,
                    content = if (_content.value.isBlank()) null else _content.value,
                    categoryId = _categoryId.value,
                    priority = _priority.value,
                    startDate = _startDate.value,
                    estimatedDurationMinutes = _estimatedDurationMinutes.value,
                    reminderTime = _reminderTime.value,
                    repeatType = _repeatType.value,
                    updatedAt = currentTime,
                    geofenceLat = _geofenceLat.value,
                    geofenceLng = _geofenceLng.value,
                    geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                    geofenceType = _geofenceType.value,
                    geofenceEnabled = _geofenceEnabled.value,
                    geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
                    hasSubTasks = hasSubTasks,
                    voiceNotePath = _voiceNotePath.value,
                    voiceDuration = _voiceDuration.value
                )
                todoRepository.updateTodo(todo)
                existingTodo!!.id
            } else {
                val todo = TodoItem(
                    title = _title.value,
                    content = if (_content.value.isBlank()) null else _content.value,
                    categoryId = _categoryId.value,
                    priority = _priority.value,
                    status = 0,
                    startDate = _startDate.value,
                    estimatedDurationMinutes = _estimatedDurationMinutes.value,
                    reminderTime = _reminderTime.value,
                    repeatType = _repeatType.value,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    geofenceLat = _geofenceLat.value,
                    geofenceLng = _geofenceLng.value,
                    geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                    geofenceType = _geofenceType.value,
                    geofenceEnabled = _geofenceEnabled.value,
                    geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
                    hasSubTasks = hasSubTasks,
                    voiceNotePath = _voiceNotePath.value,
                    voiceDuration = _voiceDuration.value
                )
                todoRepository.insertTodo(todo)
            }

            saveSubTasks(todoId)
        }
    }

    /**
     * 确认关键词选择并保存
     *
     * @param selectedKeyword 用户选择的关键词
     * @param selectedCategoryId 用户选择的分类 ID
     * @return 是否成功
     */
    fun confirmKeywordSelection(selectedKeyword: String, selectedCategoryId: Long): Boolean {
        if (selectedKeyword.isBlank() || selectedCategoryId <= 0) {
            return false
        }

        viewModelScope.launch {
            val selectedCategory = _categories.value.find { it.id == selectedCategoryId }
            selectedCategory?.let { category ->
                categoryKeywordRepository.addUserKeyword(
                    keyword = selectedKeyword,
                    categoryType = category.type
                )
            }

            _categoryId.value = selectedCategoryId
            _hasManuallySelectedCategory.value = true
            _showKeywordSelection.value = false

            performSave()
        }

        return true
    }

    /**
     * 跳过关键词添加，直接保存
     */
    fun skipKeywordSelection() {
        _showKeywordSelection.value = false
        _hasManuallySelectedCategory.value = true
        performSave()
    }

    /**
     * 取消关键词选择
     */
    fun cancelKeywordSelection() {
        _showKeywordSelection.value = false
    }

    /**
     * 关闭关键词选择对话框
     */
    fun dismissKeywordSelection() {
        _showKeywordSelection.value = false
    }

    /**
     * 保存子任务（编辑模式下先删除旧子任务再添加新的）
     * 并同步更新待办的 hasSubTasks 字段
     *
     * @param todoId 待办 ID
     */
    private suspend fun saveSubTasks(todoId: Long) {
        val currentSubTasks = _subTasks.value

        if (existingTodo != null) {
            SubTaskManager.deleteAllSubTasks(context, todoId)
        }

        if (currentSubTasks.isNotEmpty()) {
            val titles = currentSubTasks.map { it.title }
            SubTaskManager.addSubTasks(context, todoId, titles)
        }
    }

    /**
     * 删除子任务并同步到数据库（编辑已有待办时）
     *
     * @param subTask 要删除的子任务
     */
    fun deleteSubTask(subTask: SubTask) {
        removeSubTask(subTask)

        if (existingTodo != null && subTask.id > 0) {
            viewModelScope.launch {
                SubTaskManager.deleteSubTask(context, subTask.id)
            }
        }
    }

    /**
     * 加载分类列表并设置默认分类
     */
    fun loadCategories() {
        viewModelScope.launch {
            try {
                android.util.Log.d("TodoEditVM", "开始加载分类...")
                categoryRepository.initDefaultCategories()

                val allCategories = categoryRepository.getAllCategoriesList()
                android.util.Log.d("TodoEditVM", "加载到 ${allCategories.size} 个分类: $allCategories")
                _categories.value = allCategories

                if (existingTodo == null && _categoryId.value == 0L) {
                    val userTypeValue = corgiPreferences.userType.first()
                    val userType = UserType.fromValue(userTypeValue)
                    val defaultCategory = when (userType) {
                        UserType.WORKER -> allCategories.find { it.type == CategoryType.WORK }
                        UserType.STUDENT -> allCategories.find { it.type == CategoryType.STUDY }
                        else -> allCategories.firstOrNull()
                    }
                    defaultCategory?.let {
                        _categoryId.value = it.id
                        android.util.Log.d("TodoEditVM", "设置默认分类: ${it.name} (ID=${it.id})")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TodoEditVM", "加载分类失败", e)
                e.printStackTrace()
            } finally {
                _isCategoriesLoaded.value = true
                android.util.Log.d("TodoEditVM", "分类加载完成, isCategoriesLoaded=true, categories数量=${_categories.value.size}")
            }
        }
    }

    /**
     * 触发分类推荐
     */
    fun triggerRecommendation() {
        viewModelScope.launch {
            android.util.Log.d("TodoEditVM", "触发推荐, title='${_title.value}', hasManuallySelectedCategory=${_hasManuallySelectedCategory.value}")
            
            val recommendation = categoryMatcher.recommendCategory(
                title = _title.value,
                content = _content.value.takeIf { it.isNotBlank() }
            )

            android.util.Log.d("TodoEditVM", "推荐结果: $recommendation")

            if (recommendation != null) {
                val category = _categories.value.find { it.type == recommendation.categoryType }
                android.util.Log.d("TodoEditVM", "匹配到分类: $category")
                _recommendedCategory.value = category
            } else {
                _recommendedCategory.value = null
                android.util.Log.d("TodoEditVM", "无匹配推荐, title=${_title.value}, recommendedCategory=null, hasManuallySelected=${_hasManuallySelectedCategory.value}")
            }
        }
    }

    /**
     * 接受推荐的分类
     */
    fun acceptRecommendation() {
        _recommendedCategory.value?.let { category ->
            _categoryId.value = category.id
            _hasManuallySelectedCategory.value = true
            _recommendedCategory.value = null
        }
    }

    // ==================== 提醒时间推荐相关方法 ====================

    /**
     * 设置提醒时间
     * 用户在 TimePicker 中手动确认时间后调用
     *
     * @param time 用户选择的提醒时间（毫秒时间戳）
     */
    fun setReminderTime(time: Long) {
        _reminderTime.value = time
        _showReminderRecommendation.value = false
    }

    /**
     * 接受推荐的提醒时间
     * 用户点击推荐标签后调用
     */
    fun acceptReminderRecommendation() {
        val recommended = _recommendedReminderTime.value ?: return
        _reminderTime.value = recommended
        _showReminderRecommendation.value = false
    }

    /**
     * 更新提醒时间推荐
     * 当开始时间或分类变化时自动触发
     */
    private fun updateReminderRecommendation() {
        val startDate = _startDate.value
        val categoryId = _categoryId.value
        val category = _categories.value.find { it.id == categoryId }

        val recommended = reminderRecommender.recommend(
            categoryType = category?.type,
            startDate = startDate
        )

        _recommendedReminderTime.value = recommended

        val isShow = recommended != null &&
                (_reminderTime.value == null || _reminderTime.value != recommended)
        _showReminderRecommendation.value = isShow
    }

    // ==================== 语音备注相关方法 ====================

    /**
     * 设置语音备注
     *
     * @param path 语音文件路径
     * @param duration 语音时长（秒）
     */
    fun setVoiceNote(path: String?, duration: Int?) {
        _voiceNotePath.value = path
        _voiceDuration.value = duration
    }

    /**
     * 清除语音备注
     */
    fun clearVoiceNote() {
        _voiceNotePath.value = null
        _voiceDuration.value = null
    }
}
