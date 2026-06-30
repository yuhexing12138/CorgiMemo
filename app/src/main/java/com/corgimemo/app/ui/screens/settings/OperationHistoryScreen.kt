package com.corgimemo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.corgimemo.app.ui.components.OperationLogItem
import com.corgimemo.app.viewmodel.OperationHistoryViewModel

/**
 * 操作历史页面
 * 显示最近的待办操作记录，支持撤销操作
 *
 * @param viewModel 操作历史 ViewModel
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationHistoryScreen(
    viewModel: OperationHistoryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onBack: () -> Unit = {},
    /** 编辑历史时间线入口回调（V2.5 双入口支持） */
    onEditHistory: () -> Unit = {}
) {
    /** 收集 UI 状态 */
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    /** 是否显示清空确认对话框 */
    var showClearDialog by remember { mutableStateOf(false) }

    /** 协程作用域用于调用 suspend 函数 */
    val coroutineScope = rememberCoroutineScope()

    /** 页面加载时自动获取数据 */
    LaunchedEffect(Unit) {
        viewModel.loadRecentLogs()
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "最近操作",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    /** 编辑历史时间线入口（V2.5 双入口：设置页 → 编辑历史） */
                    IconButton(
                        onClick = onEditHistory
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "编辑历史",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (logs.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "清空历史"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                /** 加载中状态 */
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                /** 空状态 */
                logs.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📋",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无操作记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "删除或完成的待办会显示在这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                /** 有数据：显示列表 */
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = logs,
                            key = { it.id }
                        ) { log ->
                            OperationLogItem(
                                log = log,
                                onUndo = { logId ->
                                    /** 执行撤销操作 */
                                    android.util.Log.d("OperationHistory", "Undo operation $logId")
                                    /** 在协程中执行撤销 */
                                    coroutineScope.launch {
                                        val success = viewModel.undoOperation(logId)
                                        if (success) {
                                            android.util.Log.d("OperationHistory", "Undo successful")
                                        } else {
                                            android.util.Log.e("OperationHistory", "Undo failed")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /** 清空确认对话框 */
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空操作历史") },
            text = { Text("确定要清空所有操作记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
