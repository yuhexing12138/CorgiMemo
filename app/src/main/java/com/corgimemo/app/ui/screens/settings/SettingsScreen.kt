package com.corgimemo.app.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.screens.profile.components.SettingItem
import com.corgimemo.app.ui.screens.profile.components.SettingListCard
import com.corgimemo.app.ui.screens.profile.components.SettingSwitchGroupCard
import com.corgimemo.app.ui.screens.profile.components.SwitchItem
import com.corgimemo.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * 设置页面
 *
 * Phase 5 重构 + v1.1 调整：5 分组 LazyColumn 布局，统一视觉语言（SettingListCard / SettingSwitchGroupCard）
 * 1. 声音与反馈：音效 + 触觉开关（SettingSwitchGroupCard，直接展开不跳转）
 * 2. 通知：通知与提醒 → 跳转系统通知设置（SettingListCard）
 * 3. 身份与偏好：身份设置 + 最近操作 + 外观入口（SettingListCard，外观项跳 AppearanceScreen）
 * 4. 数据管理：备份与恢复 + 回收站 + 自动备份 + 导出 + 恢复（SettingListCard）
 * 5. 关于与帮助：使用帮助 + 意见反馈 + 隐私与协议（Snackbar 占位）+ 版本号水印
 *
 * v1.1 调整说明：
 * - 「外观」分组（深色模式 + 主题色）已拆出为独立 `AppearanceScreen`（`Screen.Appearance.route`）
 * - 入口保留在"身份与偏好"分组底部，点击跳 `Screen.Appearance.route`
 * - 详细设计见 `AppearanceScreen` 的 KDoc
 *
 * 迁移来源：原 ProfileScreen 的 ⑥⑦⑧ 三组入口（通知与提醒/震动与音效/备份与恢复/回收站/
 * 使用帮助/意见反馈/隐私与协议）已全部合并到本页，"我的"页不再展示这些入口。
 *
 * @param navController 导航控制器
 * @param viewModel SettingsViewModel（Hilt 注入）
 * @param onExportClick 导出回调
 * @param onImportClick 恢复回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
    onExportClick: (BackupManager.ExportFormat) -> Unit = {},
    onImportClick: () -> Unit = {}
) {
    // ========== 状态收集 ==========
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val userType by viewModel.userType.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    // 注：themeMode / themeColor 状态已迁移至 AppearanceScreen（v1.1）
    // 设置页不再承担外观切换，仅保留外观入口项指向 AppearanceScreen

    // ========== 弹窗状态 ==========
    var showUserTypeDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingUserType by remember { mutableStateOf<UserType?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showAutoBackupDialog by remember { mutableStateOf(false) }

    // ========== Snackbar（用于"关于与帮助"占位提示）==========
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ========== 版本号（PackageManager 读取，try-catch 兜底 "1.0.0"）==========
    // 适配说明：app/build.gradle.kts 未启用 buildFeatures.buildConfig = true，
    // BuildConfig 类不会生成，改用 PackageManager 读取版本号。
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    // ========== 备份消息弹窗 ==========
    if (backupMessage != null) {
        BackupMessageDialog(
            message = backupMessage!!,
            onDismiss = { viewModel.clearBackupMessage() }
        )
    }

    // ========== 处理中弹窗 ==========
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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ========== 分组 1：声音与反馈（直接展开为开关，不跳转）==========
            item {
                Column {
                    SettingSectionTitle("声音与反馈")
                    SettingSwitchGroupCard(
                        items = listOf(
                            SwitchItem(
                                icon = "🔊",
                                title = "音效反馈",
                                description = "触摸柯基时播放轻快音效",
                                checked = soundEnabled,
                                onCheckedChange = { viewModel.setSoundEnabled(it) }
                            ),
                            SwitchItem(
                                icon = "📳",
                                title = "触觉反馈",
                                description = "触摸柯基时震动",
                                checked = hapticEnabled,
                                onCheckedChange = { viewModel.setHapticEnabled(it) }
                            )
                        )
                    )
                }
            }

            // ========== 分组 2：通知（跳转系统通知设置）==========
            item {
                Column {
                    SettingSectionTitle("通知")
                    SettingListCard(
                        items = listOf(
                            SettingItem(icon = "🔔", title = "通知与提醒") {
                                // 跳转系统通知设置页（API 26+ 支持 EXTRA_APP_PACKAGE）
                                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        )
                    )
                }
            }

            // ========== 分组 3：身份与偏好 ==========
            item {
                Column {
                    SettingSectionTitle("身份与偏好")
                    SettingListCard(
                        items = listOf(
                            SettingItem(icon = "👤", title = "身份设置") {
                                showUserTypeDialog = true
                            },
                            SettingItem(icon = "📋", title = "最近操作") {
                                navController.navigate("operation_history")
                            },
                            // v1.1 新增：外观设置入口（深色模式 + 主题色）
                            // 切换功能已外移到独立 AppearanceScreen
                            SettingItem(icon = "🎨", title = "外观") {
                                navController.navigate(Screen.Appearance.route)
                            }
                        )
                    )
                }
            }

            // ========== 分组 4：数据管理（合并备份与恢复/回收站/自动备份/导出/恢复）==========
            item {
                Column {
                    SettingSectionTitle("数据管理")
                    SettingListCard(
                        items = listOf(
                            SettingItem(icon = "💾", title = "备份与恢复") {
                                navController.navigate(Screen.BackupHistory.route)
                            },
                            SettingItem(icon = "🗑", title = "回收站") {
                                navController.navigate(Screen.RecycleBin.createRoute("settings"))
                            },
                            SettingItem(icon = "🔄", title = "自动备份设置") {
                                showAutoBackupDialog = true
                            },
                            SettingItem(icon = "⬆️", title = "导出数据") {
                                showExportDialog = true
                            },
                            SettingItem(icon = "⬇️", title = "恢复数据") {
                                showImportConfirmDialog = true
                            }
                        )
                    )
                }
            }

            // ========== 分组 6：关于与帮助（Snackbar 占位）==========
            item {
                Column {
                    SettingSectionTitle("关于与帮助")
                    SettingListCard(
                        items = listOf(
                            SettingItem(icon = "📖", title = "使用帮助") {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "功能开发中",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            SettingItem(icon = "💬", title = "意见反馈") {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "功能开发中",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            SettingItem(icon = "📄", title = "隐私与协议") {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "功能开发中",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    )
                }
            }

            // ========== 版本号水印 ==========
            item {
                Text(
                    text = "CorgiMemo v$versionName · 治愈每一天",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }

    // ========== 导出格式弹窗 ==========
    if (showExportDialog) {
        ExportFormatDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { format ->
                showExportDialog = false
                onExportClick(format)
            }
        )
    }

    // ========== 恢复确认弹窗 ==========
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

    // ========== 身份选择弹窗 ==========
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

    // ========== 身份切换确认弹窗 ==========
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

    // ========== 自动备份设置弹窗 ==========
    if (showAutoBackupDialog) {
        AutoBackupSettingsDialog(
            onDismiss = { showAutoBackupDialog = false }
        )
    }
}

/**
 * 分组小标题
 * 用于设置页各分组的顶部标题，统一视觉风格：小字半粗体灰色
 *
 * @param title 标题文字
 */
@Composable
private fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
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
 * 单项开关卡片（保留供未来复用，当前主流程由 SettingSwitchGroupCard 替代）
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
 * 单项设置卡片（保留供未来复用，当前主流程由 SettingListCard 替代）
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
