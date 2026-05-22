package com.corgimemo.app.ui.screens.todo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.InteractiveCorgi
import com.corgimemo.app.data.repository.RepeatTaskManager
import com.corgimemo.app.ui.components.CategorySelector
import com.corgimemo.app.ui.components.KeywordSelectionDialog
import com.corgimemo.app.ui.components.LocationPicker
import com.corgimemo.app.ui.components.RecommendationChip
import com.corgimemo.app.ui.components.SubTaskList
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.viewmodel.SpeechViewModel
import com.corgimemo.app.viewmodel.TodoEditViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun TodoEditScreen(
    navController: NavController,
    todoId: Long? = null,
    viewModel: TodoEditViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val priority by viewModel.priority.collectAsState()
    val categoryId by viewModel.categoryId.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val recommendedCategory by viewModel.recommendedCategory.collectAsState()
    val hasManuallySelectedCategory by viewModel.hasManuallySelectedCategory.collectAsState()
    val showKeywordSelection by viewModel.showKeywordSelection.collectAsState()
    val extractedKeywords by viewModel.extractedKeywords.collectAsState()
    val isCategoriesLoaded by viewModel.isCategoriesLoaded.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val estimatedDurationMinutes by viewModel.estimatedDurationMinutes.collectAsState()
    val repeatType by viewModel.repeatType.collectAsState()

    // 地理围栏相关状态
    val geofenceLat by viewModel.geofenceLat.collectAsState()
    val geofenceLng by viewModel.geofenceLng.collectAsState()
    val geofenceRadius by viewModel.geofenceRadius.collectAsState()
    val geofenceType by viewModel.geofenceType.collectAsState()
    val geofenceEnabled by viewModel.geofenceEnabled.collectAsState()
    val geofenceAddress by viewModel.geofenceAddress.collectAsState()

    // 提醒时间相关状态
    val reminderTime by viewModel.reminderTime.collectAsState()
    val recommendedReminderTime by viewModel.recommendedReminderTime.collectAsState()
    val showReminderRecommendation by viewModel.showReminderRecommendation.collectAsState()

    // 子任务相关状态
    val subTasks by viewModel.subTasks.collectAsState()

    // 柯基相关状态
    val corgiData by homeViewModel.corgiData.collectAsState()
    val currentPose by homeViewModel.currentPose.collectAsState()
    val currentMood by homeViewModel.currentMood.collectAsState()
    val currentOutfit by homeViewModel.currentOutfit.collectAsState()
    val soundEnabled by homeViewModel.soundEnabled.collectAsState()
    val hapticEnabled by homeViewModel.hapticEnabled.collectAsState()

    val context = LocalContext.current
    val speechViewModel = remember { SpeechViewModel(context) }
    val isListening by speechViewModel.isListening.collectAsState()
    val isProcessing by speechViewModel.isProcessing.collectAsState()
    val speechResult by speechViewModel.resultText.collectAsState()
    val speechError by speechViewModel.errorMessage.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    val startTimePickerState = rememberTimePickerState(
        initialHour = 9,
        initialMinute = 0,
        is24Hour = true
    )
    var estimatedHours by remember { mutableStateOf("") }
    var estimatedMinutes by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = 9,
        initialMinute = 0,
        is24Hour = true
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            speechViewModel.setPermissionGranted(granted)
            if (granted) {
                speechViewModel.startListening()
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("请在设置中开启录音权限")
                }
            }
        }
    )

    if (todoId != null && todoId > 0) {
        viewModel.loadTodo(todoId)
    }

    LaunchedEffect(Unit) {
        homeViewModel.setPoseForCreating()
        viewModel.loadCategories()
    }

    DisposableEffect(Unit) {
        onDispose {
            homeViewModel.resetPoseToDefault()
        }
    }

    LaunchedEffect(estimatedDurationMinutes) {
        val duration = estimatedDurationMinutes
        if (duration != null) {
            val hours = duration / 60
            val minutes = duration % 60
            estimatedHours = if (hours > 0) hours.toString() else ""
            estimatedMinutes = if (minutes > 0) minutes.toString() else ""
        }
    }

    if (speechResult.isNotEmpty()) {
        viewModel.setTitle(speechResult)
        viewModel.triggerRecommendation()
        speechViewModel.startListening()
    }

    if (speechError.isNotEmpty()) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(speechError)
            speechViewModel.resetError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (todoId != null && todoId > 0) "编辑待办" else "新建待办",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            val corgi = corgiData
            if (corgi != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF3E0)),
                    contentAlignment = Alignment.Center
                ) {
                    InteractiveCorgi(
                        pose = currentPose,
                        mood = currentMood,
                        corgiName = corgi.name,
                        level = corgi.level,
                        outfitId = currentOutfit,
                        soundEnabled = soundEnabled,
                        hapticEnabled = hapticEnabled,
                        showText = false
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = title,
                    onValueChange = { viewModel.setTitleWithRecommendation(it) },
                    label = { Text("标题") },
                    placeholder = { Text("请输入待办标题") },
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = {
                        requestRecordAudioPermission(context, speechViewModel, recordAudioPermissionLauncher)
                    },
                    enabled = !isListening && !isProcessing,
                    modifier = Modifier
                        .size(56.dp)
                        .wrapContentWidth()
                ) {
                    AnimatedVisibility(visible = isListening || isProcessing) {
                        RecordingIndicator(isListening = isListening)
                    }
                    AnimatedVisibility(visible = !isListening && !isProcessing) {
                        Icon(
                            imageVector = if (hasRecordAudioPermission(context)) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (hasRecordAudioPermission(context)) "语音输入" else "需要录音权限",
                            tint = if (hasRecordAudioPermission(context)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isListening || isProcessing) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isListening) "正在聆听..." else "正在识别...",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                    RecordingWaveAnimation(isListening = isListening)
                }
            }

            TextField(
                value = content,
                onValueChange = { viewModel.setContent(it) },
                label = { Text("详情") },
                placeholder = { Text("请输入详细描述（可选）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                minLines = 3,
                maxLines = 5
            )

            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = "优先级",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    PriorityChip(
                        text = "低",
                        priority = 0,
                        isSelected = priority == 0,
                        onClick = { viewModel.setPriority(0) }
                    )
                    PriorityChip(
                        text = "中",
                        priority = 1,
                        isSelected = priority == 1,
                        onClick = { viewModel.setPriority(1) }
                    )
                    PriorityChip(
                        text = "高",
                        priority = 2,
                        isSelected = priority == 2,
                        onClick = { viewModel.setPriority(2) }
                    )
                }
            }

            AnimatedVisibility(visible = recommendedCategory != null) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    recommendedCategory?.let { category ->
                        RecommendationChip(
                            category = category,
                            onClick = { viewModel.acceptRecommendation() }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = recommendedCategory == null && title.isNotBlank() && !hasManuallySelectedCategory) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "需要分类",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "请为这条待办选择一个分类",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            CategorySelector(
                categories = categories,
                selectedCategoryId = categoryId,
                onCategorySelected = { viewModel.setCategoryId(it) },
                modifier = Modifier.padding(top = 8.dp)
            )

            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                val dateText = if (startDate != null) {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    format.format(Date(startDate!!))
                } else {
                    "选择开始日期时间（可选）"
                }
                Text(text = dateText)
            }

            // 预计完成时长选择
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = "预计完成时长",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = estimatedHours,
                        onValueChange = {
                            estimatedHours = it.filter { char -> char.isDigit() }.take(3)
                            val hours = estimatedHours.toIntOrNull() ?: 0
                            val minutes = estimatedMinutes.toIntOrNull() ?: 0
                            val totalMinutes = hours * 60 + minutes
                            viewModel.setEstimatedDurationMinutes(if (totalMinutes > 0) totalMinutes else null)
                        },
                        label = { Text("小时") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextField(
                        value = estimatedMinutes,
                        onValueChange = {
                            val newMinutes = it.filter { char -> char.isDigit() }.take(2)
                            val minutes = newMinutes.toIntOrNull() ?: 0
                            estimatedMinutes = if (minutes < 60) newMinutes else estimatedMinutes
                            val hours = estimatedHours.toIntOrNull() ?: 0
                            val finalMinutes = estimatedMinutes.toIntOrNull() ?: 0
                            val totalMinutes = hours * 60 + finalMinutes
                            viewModel.setEstimatedDurationMinutes(if (totalMinutes > 0) totalMinutes else null)
                        },
                        label = { Text("分钟") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            // 提醒时间选择区域
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = "提醒时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    val timeText = if (reminderTime != null) {
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        format.format(Date(reminderTime ?: 0L))
                    } else {
                        "点击选择提醒时间（可选）"
                    }
                    Text(text = timeText)
                }

                // 推荐提醒时间标签
                AnimatedVisibility(visible = showReminderRecommendation) {
                    recommendedReminderTime?.let { time ->
                        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        val timeText = format.format(Date(time))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                                .clickable { viewModel.acceptReminderRecommendation() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\uD83D\uDCA1 推荐提醒：$timeText",
                                fontSize = 14.sp,
                                color = Color(0xFF1565C0)
                            )
                        }
                    }
                }
            }

            // 重复类型选择
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = "重复",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    val repeatOptions = RepeatTaskManager.getRepeatTypeOptions()
                    repeatOptions.forEach { (type, name) ->
                        RepeatTypeChip(
                            text = name,
                            type = type,
                            isSelected = repeatType == type,
                            onClick = { viewModel.setRepeatType(type) }
                        )
                    }
                }
            }

            LocationPicker(
                lat = geofenceLat,
                lng = geofenceLng,
                radius = geofenceRadius,
                type = geofenceType,
                address = geofenceAddress,
                enabled = geofenceEnabled,
                onLocationChange = { lat, lng, address ->
                    viewModel.setGeofenceLat(lat)
                    viewModel.setGeofenceLng(lng)
                    viewModel.setGeofenceAddress(address)
                },
                onRadiusChange = { radius ->
                    viewModel.setGeofenceRadius(radius)
                },
                onTypeChange = { type ->
                    viewModel.setGeofenceType(type)
                },
                onEnabledChange = { enabled ->
                    viewModel.setGeofenceEnabled(enabled)
                }
            )

            // 子任务列表
            SubTaskList(
                subTasks = subTasks,
                onAddSubTask = { viewModel.addSubTask(it) },
                onToggleSubTask = { viewModel.toggleSubTaskCompletion(it) },
                onDeleteSubTask = { viewModel.deleteSubTask(it) },
                modifier = Modifier.padding(top = 16.dp)
            )

            Button(
                onClick = {
                    if (viewModel.saveTodo()) {
                        homeViewModel.setPoseForLoading()
                        homeViewModel.refreshSubTaskProgress()
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = "保存")
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDate = datePickerState.selectedDateMillis
                        if (selectedDate != null) {
                            selectedDateMillis = selectedDate
                            showDatePicker = false
                            showStartTimePicker = true
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("选择开始时间") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = selectedDateMillis ?: System.currentTimeMillis()
                        cal.set(Calendar.HOUR_OF_DAY, startTimePickerState.hour)
                        cal.set(Calendar.MINUTE, startTimePickerState.minute)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        viewModel.setStartDate(cal.timeInMillis)
                        showStartTimePicker = false
                        selectedDateMillis = null
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) {
                    Text("取消")
                }
            },
            text = {
                TimePicker(state = startTimePickerState)
            }
        )
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = startDate ?: System.currentTimeMillis()
                        cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        cal.set(Calendar.MINUTE, timePickerState.minute)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        viewModel.setReminderTime(cal.timeInMillis)
                        showTimePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    if (showKeywordSelection) {
        KeywordSelectionDialog(
            title = title,
            keywords = extractedKeywords,
            categories = categories,
            onConfirm = { keyword, categoryId ->
                if (viewModel.confirmKeywordSelection(keyword, categoryId)) {
                    homeViewModel.setPoseForLoading()
                    homeViewModel.refreshSubTaskProgress()
                    navController.popBackStack()
                }
            },
            onSkip = {
                viewModel.skipKeywordSelection()
                homeViewModel.setPoseForLoading()
                homeViewModel.refreshSubTaskProgress()
                navController.popBackStack()
            },
            onDismiss = { viewModel.cancelKeywordSelection() }
        )
    }
}

private fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

private fun requestRecordAudioPermission(
    context: Context,
    speechViewModel: SpeechViewModel,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (hasRecordAudioPermission(context)) {
        speechViewModel.startListening()
    } else {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RecordingIndicator(isListening: Boolean) {
    Icon(
        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
        contentDescription = "录音中",
        tint = androidx.compose.ui.graphics.Color(0xFFDC2626),
        modifier = Modifier.size(24.dp)
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RecordingWaveAnimation(isListening: Boolean) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..4) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(8.dp, if (isListening) (8 + (i * 4) % 24).dp else 8.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun PriorityChip(text: String, priority: Int, isSelected: Boolean, onClick: () -> Unit) {
    val (color, bgColor) = when (priority) {
        2 -> Pair(
            androidx.compose.ui.graphics.Color(0xFFDC2626),
            androidx.compose.ui.graphics.Color(0xFFFFE4E6)
        )
        1 -> Pair(
            androidx.compose.ui.graphics.Color(0xFFD97706),
            androidx.compose.ui.graphics.Color(0xFFFFF3E0)
        )
        else -> Pair(
            androidx.compose.ui.graphics.Color(0xFF16A34A),
            androidx.compose.ui.graphics.Color(0xFFECFDF5)
        )
    }

    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isSelected) bgColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(text = text)
    }
}

/**
 * 重复类型选择芯片组件
 *
 * @param text 显示文本
 * @param type 重复类型值
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
fun RepeatTypeChip(text: String, type: Int, isSelected: Boolean, onClick: () -> Unit) {
    val (color, bgColor) = when (type) {
        com.corgimemo.app.data.repository.RepeatType.DAILY -> Pair(
            androidx.compose.ui.graphics.Color(0xFF2563EB),
            androidx.compose.ui.graphics.Color(0xFFDBEAFE)
        )
        com.corgimemo.app.data.repository.RepeatType.WEEKLY -> Pair(
            androidx.compose.ui.graphics.Color(0xFF7C3AED),
            androidx.compose.ui.graphics.Color(0xFFEDE9FE)
        )
        com.corgimemo.app.data.repository.RepeatType.MONTHLY -> Pair(
            androidx.compose.ui.graphics.Color(0xFFDB2777),
            androidx.compose.ui.graphics.Color(0xFFFCE7F3)
        )
        com.corgimemo.app.data.repository.RepeatType.WEEKDAYS -> Pair(
            androidx.compose.ui.graphics.Color(0xFF0891B2),
            androidx.compose.ui.graphics.Color(0xFFCFFAFE)
        )
        com.corgimemo.app.data.repository.RepeatType.WEEKENDS -> Pair(
            androidx.compose.ui.graphics.Color(0xFF65A30D),
            androidx.compose.ui.graphics.Color(0xFFECFCCB)
        )
        else -> Pair(
            androidx.compose.ui.graphics.Color(0xFF6B7280),
            androidx.compose.ui.graphics.Color(0xFFF3F4F6)
        )
    }

    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isSelected) bgColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(text = text)
    }
}