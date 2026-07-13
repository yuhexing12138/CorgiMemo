package com.corgimemo.app.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.GreetingManager
import com.corgimemo.app.backup.BackupManager
import com.corgimemo.app.model.UserType
import com.corgimemo.app.viewmodel.SettingsViewModel

/**
 * 设置页面
 * 管理音效反馈、触觉反馈、用户身份设置和数据备份
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
    onExportClick: (BackupManager.ExportFormat) -> Unit = {},
    onImportClick: () -> Unit = {}
) {
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val userType by viewModel.userType.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()

    var showUserTypeDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingUserType by remember { mutableStateOf<UserType?>(null) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showAutoBackupDialog by remember { mutableStateOf(false) }

    if (backupMessage != null) {
        BackupMessageDialog(
            message = backupMessage!!,
            onDismiss = { viewModel.clearBackupMessage() }
        )
    }

    if (isProcessing) {
        ProcessingDialog(message = "处理中...")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "应用设置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingSwitchCard(
                title = "音效反馈",
                description = "触摸柯基时播放轻快音效",
                checked = soundEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setSoundEnabled(enabled)
                }
            )

            SettingSwitchCard(
                title = "触觉反馈",
                description = "触摸柯基时震动",
                checked = hapticEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setHapticEnabled(enabled)
                }
            )

            SettingItemCard(
                title = "身份设置",
                description = "当前身份：${getUserTypeName(userType)}",
                onClick = {
                    showUserTypeDialog = true
                }
            )

            SettingItemCard(
                title = "🧠 智能分类设置",
                description = "管理智能分类关键词",
                onClick = {
                    navController.navigate("smart_category_settings")
                }
            )

            SettingItemCard(
                title = "📋 最近操作",
                description = "查看和撤销最近的待办操作",
                onClick = {
                    navController.navigate("operation_history")
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "🎨 外观设置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "深色模式",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeOption(
                            text = "🌓 跟随系统",
                            selected = themeMode == "system",
                            onClick = { viewModel.setThemeMode("system") },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            text = "☀️ 亮色",
                            selected = themeMode == "light",
                            onClick = { viewModel.setThemeMode("light") },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            text = "🌙 深色",
                            selected = themeMode == "dark",
                            onClick = { viewModel.setThemeMode("dark") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "主题色",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ThemeColorOption(
                            color = Color(0xFFFF9A5C),
                            name = "橙色",
                            selected = themeColor == "orange",
                            onClick = { viewModel.setThemeColor("orange") }
                        )
                        ThemeColorOption(
                            color = Color(0xFF4ECDC4),
                            name = "薄荷",
                            selected = themeColor == "mint",
                            onClick = { viewModel.setThemeColor("mint") }
                        )
                        ThemeColorOption(
                            color = Color(0xFF5BA8E0),
                            name = "天空",
                            selected = themeColor == "sky",
                            onClick = { viewModel.setThemeColor("sky") }
                        )
                        ThemeColorOption(
                            color = Color(0xFFFF9AA2),
                            name = "樱花",
                            selected = themeColor == "sakura",
                            onClick = { viewModel.setThemeColor("sakura") }
                        )
                        ThemeColorOption(
                            color = Color(0xFFB39DDB),
                            name = "薰衣草",
                            selected = themeColor == "lavender",
                            onClick = { viewModel.setThemeColor("lavender") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "自动备份",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingItemCard(
                title = "🔄 自动备份设置",
                description = "自动备份数据到 Downloads 目录",
                onClick = {
                    showAutoBackupDialog = true
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "数据管理",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingItemCard(
                title = "⬆️ 导出数据",
                description = "导出为 JSON 或 CSV 格式",
                onClick = {
                    showExportDialog = true
                }
            )

            SettingItemCard(
                title = "📚 备份历史",
                description = "查看和管理自动备份记录",
                onClick = {
                    navController.navigate("backup_history")
                }
            )

            SettingItemCard(
                title = "⬇️ 恢复数据",
                description = "从备份文件恢复数据",
                onClick = {
                    showImportConfirmDialog = true
                }
            )
        }
    }

    if (showExportDialog) {
        ExportFormatDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { format ->
                showExportDialog = false
                onExportClick(format)
            }
        )
    }

    if (showImportConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text("恢复数据") },
            text = {
                Column {
                    Text("恢复数据将覆盖当前所有数据，包括：")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• 待办列表")
                    Text("• 分类设置")
                    Text("• 柯基成长数据")
                    Text("• 心情历史记录")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("确定要继续吗？")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportConfirmDialog = false
                        onImportClick()
                    }
                ) {
                    Text("继续")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showImportConfirmDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showUserTypeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUserTypeDialog = false },
            title = {
                Text(
                    text = "选择身份",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserTypeSelectCard(
                        userType = UserType.WORKER,
                        isSelected = userType == UserType.WORKER,
                        onClick = {
                            if (userType != UserType.WORKER) {
                                pendingUserType = UserType.WORKER
                                showUserTypeDialog = false
                                showConfirmDialog = true
                            }
                        }
                    )
                    UserTypeSelectCard(
                        userType = UserType.STUDENT,
                        isSelected = userType == UserType.STUDENT,
                        onClick = {
                            if (userType != UserType.STUDENT) {
                                pendingUserType = UserType.STUDENT
                                showUserTypeDialog = false
                                showConfirmDialog = true
                            }
                        }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showUserTypeDialog = false }
                ) {
                    Text(text = "取消")
                }
            }
        )
    }

    if (showConfirmDialog && pendingUserType != null) {
        val newUserType = pendingUserType!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                pendingUserType = null
            },
            title = {
                Text(
                    text = "切换身份",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "确定要切换为「${getUserTypeName(newUserType)}」吗？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "切换后的问候语预览",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = GreetingManager.getIdentityPreviewGreeting(newUserType),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setUserType(newUserType)
                        showConfirmDialog = false
                        pendingUserType = null
                    }
                ) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showConfirmDialog = false
                        showUserTypeDialog = true
                        pendingUserType = null
                    }
                ) {
                    Text(text = "取消")
                }
            }
        )
    }

    if (showAutoBackupDialog) {
        AutoBackupSettingsDialog(
            onDismiss = { showAutoBackupDialog = false }
        )
    }
}

/**
 * 导出格式选择对话框
 *
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调，携带选择的格式
 */
@Composable
fun ExportFormatDialog(
    onDismiss: () -> Unit,
    onConfirm: (BackupManager.ExportFormat) -> Unit
) {
    var selectedFormat by remember { mutableStateOf<BackupManager.ExportFormat?>(null) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导出格式") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedFormat == BackupManager.ExportFormat.JSON) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFormat = BackupManager.ExportFormat.JSON }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📄 JSON",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedFormat == BackupManager.ExportFormat.JSON) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (selectedFormat == BackupManager.ExportFormat.JSON) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "✓",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Text(
                            text = "完整数据备份，可恢复",
                            fontSize = 13.sp,
                            color = if (selectedFormat == BackupManager.ExportFormat.JSON) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedFormat == BackupManager.ExportFormat.CSV) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFormat = BackupManager.ExportFormat.CSV }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📊 CSV",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedFormat == BackupManager.ExportFormat.CSV) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (selectedFormat == BackupManager.ExportFormat.CSV) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "✓",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Text(
                            text = "仅待办列表，Excel 兼容",
                            fontSize = 13.sp,
                            color = if (selectedFormat == BackupManager.ExportFormat.CSV) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedFormat == BackupManager.ExportFormat.ICAL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFormat = BackupManager.ExportFormat.ICAL }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📅 iCal",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedFormat == BackupManager.ExportFormat.ICAL) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (selectedFormat == BackupManager.ExportFormat.ICAL) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "✓",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Text(
                            text = "导出到日历应用",
                            fontSize = 13.sp,
                            color = if (selectedFormat == BackupManager.ExportFormat.ICAL) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedFormat == BackupManager.ExportFormat.IMAGE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFormat = BackupManager.ExportFormat.IMAGE }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🖼️ 图片",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedFormat == BackupManager.ExportFormat.IMAGE) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (selectedFormat == BackupManager.ExportFormat.IMAGE) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "✓",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Text(
                            text = "分享到社交媒体",
                            fontSize = 13.sp,
                            color = if (selectedFormat == BackupManager.ExportFormat.IMAGE) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (selectedFormat == BackupManager.ExportFormat.JSON) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = usePassword,
                            onCheckedChange = { usePassword = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "密码保护",
                            fontSize = 14.sp
                        )
                    }
                    if (usePassword) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("输入密码") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("确认密码") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedFormat != null,
                onClick = {
                    selectedFormat?.let { format ->
                        onConfirm(format)
                    }
                }
            ) {
                Text("下一步")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 备份消息对话框
 *
 * @param message 消息内容
 * @param onDismiss 关闭回调
 */
@Composable
fun BackupMessageDialog(
    message: String,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提示") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

/**
 * 处理中对话框
 *
 * @param message 消息内容
 */
@Composable
fun ProcessingDialog(message: String) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 设置开关卡片
 *
 * @param title 标题
 * @param description 描述
 * @param checked 是否开启
 * @param onCheckedChange 开关状态变化回调
 */
@Composable
fun SettingSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * 设置项卡片
 * 显示标题和描述，可点击
 *
 * @param title 标题
 * @param description 描述
 * @param onClick 点击回调
 */
@Composable
fun SettingItemCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = "›",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 身份选择卡片
 *
 * @param userType 用户类型
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
fun UserTypeSelectCard(
    userType: UserType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val descriptionColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (userType) {
                    UserType.WORKER -> "💼"
                    UserType.STUDENT -> "📚"
                },
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = getUserTypeName(userType),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = when (userType) {
                        UserType.WORKER -> "职场打工人专属问候"
                        UserType.STUDENT -> "学生党专属鼓励"
                    },
                    fontSize = 13.sp,
                    color = descriptionColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (isSelected) {
                Text(
                    text = "✓",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

/**
 * 获取用户类型的显示名称
 *
 * @param userType 用户类型
 * @return 显示名称
 */
fun getUserTypeName(userType: UserType): String {
    return when (userType) {
        UserType.WORKER -> "上班族"
        UserType.STUDENT -> "学生"
    }
}

@Composable
fun ThemeModeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ThemeColorOption(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(40.dp)
            ) {
                drawCircle(color = color)
            }
            if (selected) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(44.dp).padding(2.dp)
                ) {
                    drawCircle(
                        color = Color.White,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                }
                Text(
                    text = "✓",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Text(
            text = name,
            fontSize = 11.sp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
