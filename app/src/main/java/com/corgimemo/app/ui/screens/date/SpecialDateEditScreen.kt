package com.corgimemo.app.ui.screens.date

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.model.SpecialDateRelation
import com.corgimemo.app.ui.screens.date.components.CountModeSwitch
import com.corgimemo.app.ui.screens.date.components.DateCategoryPicker
import com.corgimemo.app.ui.screens.date.components.ReminderSetting
import com.corgimemo.app.ui.screens.date.components.RepeatTypePicker
import com.corgimemo.app.ui.screens.inspiration.components.ImagePicker
import com.corgimemo.app.ui.screens.inspiration.components.RelationSelector
import com.corgimemo.app.ui.screens.inspiration.components.TagInputField
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.DateCategory
import com.corgimemo.app.viewmodel.SpecialDateViewModel
import java.util.Calendar
import java.util.Locale

/**
 * 特殊日期编辑/创建页面
 * 用于新建或编辑特殊日期记录，包含完整的表单字段：
 * - 标题、目标日期、分类、计时模式、重复类型、提醒设置
 * - 备注内容、标签、图片、关联关系
 *
 * @param navController 导航控制器，用于返回上一页
 * @param specialDateId 要编辑的日期ID（null表示新建模式）
 * @param viewModel 特殊日期ViewModel（Hilt自动注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDateEditScreen(
    navController: NavController,
    specialDateId: Long? = null,
    viewModel: SpecialDateViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    /** 表单状态管理 */
    var title by remember { mutableStateOf("") }
    var targetDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedCategory by remember { mutableStateOf(DateCategory.OTHER) }
    var countMode by remember { mutableIntStateOf(0) }
    var repeatType by remember { mutableIntStateOf(0) }
    var reminderDays by remember { mutableIntStateOf(0) }
    var content by remember { mutableStateOf("") }

    /** 标签列表状态 */
    val tags = remember { mutableStateListOf<String>() }

    /** 图片路径列表状态 */
    val imagePaths = remember { mutableStateListOf<String>() }

    /** 关联关系列表状态 */
    val relations = remember { mutableStateListOf<SpecialDateRelation>() }

    /** 控制日期选择对话框显示 */
    var showDatePicker by remember { mutableStateOf(false) }

    /**
     * 处理保存操作
     * 验证表单数据完整性，调用ViewModel保存方法，然后返回上一页
     */
    val handleSave: () -> Unit = {
        /** 验证标题不能为空 */
        if (title.isNotBlank()) {
            /**
             * 调用ViewModel的saveDate方法
             * 传入所有表单字段数据
             */
            viewModel.saveDate(
                id = specialDateId,
                title = title,
                targetDate = targetDate,
                category = selectedCategory.name,
                countMode = countMode,
                repeatType = repeatType,
                reminderDays = reminderDays,
                content = content,
                tags = tags.toList(),
                imagePaths = imagePaths.toList()
            )

            /** 保存成功后返回上一页 */
            navController.popBackStack()
        }
    }

    /** 判断是否为编辑模式 */
    val isEditMode = specialDateId != null

    /**
     * 页面加载时：如果传入specialDateId，则加载已有的日期数据
     * 通过ViewModel的editingDate流获取数据并填充表单字段
     */
    LaunchedEffect(specialDateId) {
        if (specialDateId != null) {
            viewModel.setEditingDate(
                viewModel.specialDates.value.find { it.id == specialDateId }
            )
        }
    }

    /**
     * 监听ViewModel的editingDate变化
     * 当数据加载完成后，更新表单字段的状态
     */
    viewModel.editingDate.value?.let { date ->
        LaunchedEffect(date) {
            title = date.title
            targetDate = date.targetDate
            selectedCategory = try { DateCategory.valueOf(date.category) } catch (e: Exception) { DateCategory.OTHER }
            countMode = date.countMode
            repeatType = date.repeatType
            reminderDays = date.reminderDays
            content = date.content

            /** 解析标签JSON字符串为列表 */
            tags.clear()
            tags.addAll(viewModel.decodeTags(date.tags))

            /** 解析图片路径JSON字符串为列表 */
            imagePaths.clear()
            imagePaths.addAll(viewModel.decodePaths(date.imagePaths))
        }

        /** 监听关联关系变化 */
        LaunchedEffect(viewModel.relations.value) {
            relations.clear()
            relations.addAll(viewModel.relations.value)
        }
    }

    /**
     * 页面销毁时清理ViewModel中的编辑状态
     * 防止返回后残留编辑数据影响其他操作
     */
    DisposableEffect(Unit) {
        onDispose {
            if (!isEditMode) {
                viewModel.setEditingDate(null)
            }
        }
    }

    Scaffold(
        topBar = {
            /**
             * 顶部应用栏
             * 包含：返回按钮（取消）| 标题 | 保存按钮
             */
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "编辑日期" else "新建日期",
                        color = Color(0xFF1C1B1F)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "取消",
                            tint = Color(0xFF1C1B1F)
                        )
                    }
                },
                actions = {
                    /**
                     * 保存按钮
                     * 点击时验证表单并保存数据
                     */
                    Text(
                        text = "保存",
                        color = Color(0xFFFF9A5C),
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable { handleSave() },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        /**
         * 可滚动的表单列
         * 使用verticalScroll支持长表单滚动
         */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ========== 1. 标题输入框 ==========
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                placeholder = { Text("例如：小明生日") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // ========== 2. 目标日期选择 ==========
            OutlinedTextField(
                value = formatDate(targetDate),
                onValueChange = {},
                label = { Text("目标日期") },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                shape = RoundedCornerShape(12.dp)
            )

            // ========== 3. 分类选择 ==========
            Column {
                Text(
                    text = "分类",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                DateCategoryPicker(
                    selected = selectedCategory,
                    onSelected = { category -> selectedCategory = category }
                )
            }

            // ========== 4. 计时模式切换 ==========
            Column {
                Text(
                    text = "计时模式",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                CountModeSwitch(
                    selectedMode = countMode,
                    onModeChanged = { mode -> countMode = mode }
                )
            }

            // ========== 5. 重复类型选择 ==========
            Column {
                Text(
                    text = "重复类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                RepeatTypePicker(
                    selectedType = repeatType,
                    onTypeChanged = { type ->
                        repeatType = type
                        /** 切换到不重复时重置提醒天数 */
                        if (type == 0) {
                            reminderDays = 0
                        }
                    }
                )
            }

            // ========== 6. 提醒设置（仅当repeatType != 0时显示）==========
            if (repeatType != 0) {
                ReminderSetting(
                    reminderDays = reminderDays,
                    onDaysChanged = { days -> reminderDays = days }
                )
            }

            // ========== 7. 备注内容 ==========
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("备注") },
                placeholder = { Text("准备什么礼物好呢...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // ========== 8. 标签输入 ==========
            TagInputField(
                tags = tags.toList(),
                onTagsChange = { newTags ->
                    tags.clear()
                    tags.addAll(newTags)
                }
            )

            // ========== 9. 图片选择 ==========
            ImagePicker(
                imagePaths = imagePaths.toList(),
                onImagesChange = { newPaths ->
                    imagePaths.clear()
                    imagePaths.addAll(newPaths)
                }
            )

            // ========== 10. 关联选择 ==========
            RelationSelector(
                relations = relations.toList().map { relation ->
                    /** 将SpecialDateRelation转换为InspirationRelation格式以适配组件接口 */
                    com.corgimemo.app.data.model.InspirationRelation(
                        id = relation.id,
                        inspirationId = relation.specialDateId,
                        targetType = relation.targetType,
                        targetId = relation.targetId
                    )
                },
                onRelationAdd = { targetType, targetId ->
                    /** 添加新的关联关系 */
                    val newRelation = SpecialDateRelation(
                        id = System.currentTimeMillis(),
                        specialDateId = specialDateId ?: 0,
                        targetType = targetType,
                        targetId = targetId
                    )
                    relations.add(newRelation)
                    viewModel.addRelation(newRelation)
                },
                onRelationDelete = { relationId ->
                    /** 删除关联关系 */
                    val relation = relations.find { it.id == relationId }
                    if (relation != null) {
                        relations.remove(relation)
                        viewModel.removeRelation(relation.targetType, relation.targetId)
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    /**
     * 日期选择对话框
     * 当用户点击目标日期输入框时触发显示
     * 使用Android原生DatePickerDialog
     */
    if (showDatePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = targetDate }
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                /** 用户选择日期后的回调：更新targetDate时间戳 */
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                targetDate = selectedCalendar.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

/**
 * 格式化时间戳为可读的日期字符串
 *
 * @param timestamp 时间戳（毫秒）
 * @return 格式化后的日期字符串（如：2026年05月27日）
 */
private fun formatDate(timestamp: Long): String {
    return try {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        String.format(
            Locale.getDefault(),
            "%04d年%02d月%02d日",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    } catch (e: Exception) {
        ""
    }
}
