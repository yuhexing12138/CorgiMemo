package com.corgimemo.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.corgimemo.app.viewmodel.SpeechViewModel
import com.corgimemo.app.viewmodel.TodoEditViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 创建待办的底部弹窗组件
 * 使用 ModalBottomSheet 实现，让柯基在背景可见
 * 包含语音输入和位置输入功能
 *
 * @param sheetState 底部弹窗状态
 * @param viewModel TodoEditViewModel
 * @param onSave 保存回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun TodoCreateBottomSheet(
    sheetState: SheetState,
    viewModel: TodoEditViewModel,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 基础待办状态
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val priority by viewModel.priority.collectAsState()
    val dueDate by viewModel.dueDate.collectAsState()

    // 地理围栏相关状态
    val geofenceLat by viewModel.geofenceLat.collectAsState()
    val geofenceLng by viewModel.geofenceLng.collectAsState()
    val geofenceRadius by viewModel.geofenceRadius.collectAsState()
    val geofenceType by viewModel.geofenceType.collectAsState()
    val geofenceEnabled by viewModel.geofenceEnabled.collectAsState()
    val geofenceAddress by viewModel.geofenceAddress.collectAsState()

    val context = LocalContext.current
    val speechViewModel = remember { SpeechViewModel(context) }
    val isListening by speechViewModel.isListening.collectAsState()
    val isProcessing by speechViewModel.isProcessing.collectAsState()
    val speechResult by speechViewModel.resultText.collectAsState()
    val speechError by speechViewModel.errorMessage.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 录音权限请求 launcher
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

    // 语音识别结果自动填入标题
    if (speechResult.isNotEmpty()) {
        viewModel.setTitle(speechResult)
        speechViewModel.startListening()
    }

    // 语音识别错误提示
    if (speechError.isNotEmpty()) {
        LaunchedEffect(speechError) {
            snackbarHostState.showSnackbar(speechError)
            speechViewModel.resetError()
        }
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "新建待办",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 标题输入 + 语音输入按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = title,
                    onValueChange = { newTitle -> viewModel.setTitle(newTitle) },
                    label = { Text("标题") },
                    placeholder = { Text("请输入待办标题") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                // 语音输入按钮
                IconButton(
                    onClick = {
                        requestRecordAudioPermission(
                            context,
                            speechViewModel,
                            recordAudioPermissionLauncher
                        )
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

            // 录音状态显示和波形动画
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

            // 详情输入
            TextField(
                value = content,
                onValueChange = { newContent -> viewModel.setContent(newContent) },
                label = { Text("详情") },
                placeholder = { Text("请输入详细描述（可选）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                minLines = 2,
                maxLines = 4
            )

            // 优先级选择
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
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

            // 日期选择按钮
            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                val dateText = if (dueDate != null) {
                    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    format.format(dueDate ?: 0L)
                } else {
                    "选择截止日期（可选）"
                }
                Text(text = dateText)
            }

            // 位置选择器
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

            Spacer(modifier = Modifier.height(16.dp))

            // 取消和保存按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "取消")
                }

                Button(
                    onClick = {
                        if (viewModel.saveTodo()) {
                            onSave()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "保存")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // SnackbarHost 用于显示提示信息
    SnackbarHost(hostState = snackbarHostState)

    // 日期选择器弹窗
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDate = datePickerState.selectedDateMillis
                        viewModel.setDueDate(selectedDate)
                        showDatePicker = false
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
}

/**
 * 检查是否有录音权限
 *
 * @param context Context
 * @return 是否有权限
 */
private fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 请求录音权限
 *
 * @param context Context
 * @param speechViewModel SpeechViewModel
 * @param launcher 权限请求 launcher
 */
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

/**
 * 录音状态指示器
 *
 * @param isListening 是否正在录音
 */
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

/**
 * 录音波形动画
 *
 * @param isListening 是否正在录音
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RecordingWaveAnimation(isListening: Boolean) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..4) {
            Box(
                modifier = Modifier
                    .size(8.dp, if (isListening) (8 + (i * 4) % 24).dp else 8.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/**
 * 优先级选择按钮
 *
 * @param text 按钮文本
 * @param priority 优先级值
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
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
