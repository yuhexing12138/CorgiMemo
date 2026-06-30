package com.corgimemo.app.ui.screens.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.backup.BackupHistoryManager
import com.corgimemo.app.backup.BackupManager
import com.corgimemo.app.backup.BackupRecord
import com.corgimemo.app.ui.components.BackupRecordCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 备份历史页面
 *
 * @param onBack 点击返回按钮的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var backupRecords by remember { mutableStateOf<List<BackupRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf<BackupRecord?>(null) }
    var showRestoreConfirm by remember { mutableStateOf<BackupRecord?>(null) }

    // 加载备份记录
    LaunchedEffect(Unit) {
        loadRecords(context) { records ->
            backupRecords = records
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "备份历史",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFF9A5C)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (isLoading) {
                // 加载中
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "加载中...",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            } else if (backupRecords.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📦",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无备份记录",
                        fontSize = 16.sp,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "在设置中开启自动备份或手动导出备份",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            } else {
                // 备份记录列表
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(backupRecords) { record ->
                        BackupRecordCard(
                            record = record,
                            onRestore = {
                                showRestoreConfirm = record
                            },
                            onDelete = {
                                showDeleteConfirm = record
                            },
                            onShare = {
                                shareBackup(context, record)
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("删除此备份后无法恢复，确定要删除吗？") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        scope.launch {
                            showDeleteConfirm?.let { record ->
                                BackupHistoryManager.deleteRecord(context, record.id)
                                loadRecords(context) { records -> backupRecords = records }
                            }
                            showDeleteConfirm = null
                        }
                    }
                ) {
                    Text("删除", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteConfirm = null }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 恢复确认对话框
    if (showRestoreConfirm != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("确认恢复") },
            text = { Text("恢复此备份将覆盖当前所有数据，确定要恢复吗？") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        scope.launch {
                            showRestoreConfirm?.let { record ->
                                restoreBackup(context, record)
                                loadRecords(context) { records -> backupRecords = records }
                            }
                            showRestoreConfirm = null
                        }
                    }
                ) {
                    Text("恢复", color = Color(0xFF3B82F6))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showRestoreConfirm = null }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 加载备份记录
 *
 * @param context 上下文
 * @param onResult 结果回调
 */
private suspend fun loadRecords(
    context: Context,
    onResult: (List<BackupRecord>) -> Unit
) {
    val records = BackupHistoryManager.getRecords(context)
    onResult(records)
}

/**
 * 分享备份文件
 *
 * @param context 上下文
 * @param record 备份记录
 */
private fun shareBackup(context: Context, record: BackupRecord) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(record.fileUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "分享备份文件")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(
            context,
            "分享失败：${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * 恢复备份
 *
 * @param context 上下文
 * @param record 备份记录
 */
private suspend fun restoreBackup(context: Context, record: BackupRecord) {
    try {
        val uri = Uri.parse(record.fileUri)
        
        val result = withContext(Dispatchers.IO) {
            BackupManager.restoreData(
                context = context,
                uri = uri,
                password = null
            )
        }

        when (result) {
            is BackupManager.RestoreResult.Success -> {
                android.widget.Toast.makeText(
                    context,
                    "恢复成功：${result.todoCount} 条待办",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            is BackupManager.RestoreResult.Error -> {
                android.widget.Toast.makeText(
                    context,
                    "恢复失败：${result.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            is BackupManager.RestoreResult.WrongPassword -> {
                android.widget.Toast.makeText(
                    context,
                    "恢复失败：密码错误",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            is BackupManager.RestoreResult.VersionIncompatible -> {
                android.widget.Toast.makeText(
                    context,
                    "恢复失败：版本不兼容",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(
            context,
            "恢复失败：${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
