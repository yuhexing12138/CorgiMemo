// 最近删除全屏页面
package com.corgimemo.app.ui.screens.recentlydeleted

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 最近删除全屏页面
 *
 * - 顶栏：返回 + 标题 + 清空全部（文字按钮）
 * - 列表：按时间分组的卡片
 * - 空态：柯基表情 + 提示 + 返回按钮
 * - SnackBar：撤销删除（5s）
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    onBack: () -> Unit,
    viewModel: RecentlyDeletedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 监听一次性 UI 事件（SnackBar）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecentlyDeletedViewModel.UiEvent.ShowSnackBar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is RecentlyDeletedViewModel.UiEvent.ShowSnackBarWithUndo -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = "撤销",
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoLastDelete()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("最近删除") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.totalCount > 0) {
                        TextButton(onClick = { viewModel.showClearAllDialog() }) {
                            Text("清空全部")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.totalCount == 0 -> EmptyState(onBack = onBack)
                else -> RecentlyDeletedList(
                    groups = uiState.groups,
                    onRestore = viewModel::restoreTodo,
                    onPermanentDelete = viewModel::permanentlyDelete
                )
            }

            if (uiState.showClearAllDialog) {
                ClearAllConfirmDialog(
                    count = uiState.totalCount,
                    onConfirm = { viewModel.confirmClearAll() },
                    onDismiss = { viewModel.dismissClearAllDialog() }
                )
            }
        }
    }
}

/**
 * 加载中视图：居中显示 CircularProgressIndicator
 */
@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * 空态视图：柯基表情 + 提示 + 返回按钮
 */
@Composable
private fun EmptyState(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🐕", fontSize = 96.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "最近没有删除的待办",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack) {
            Text("返回")
        }
    }
}

/**
 * 列表视图：按时间分组的卡片
 */
@Composable
private fun RecentlyDeletedList(
    groups: List<DeletedTodoGroup>,
    onRestore: (Long) -> Unit,
    onPermanentDelete: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            item(key = "header_${group.kind}") {
                GroupHeader(title = group.title)
            }
            items(group.items, key = { it.id }) { item ->
                DeletedTodoCard(
                    item = item,
                    onRestore = { onRestore(item.id) },
                    onPermanentDelete = { onPermanentDelete(item.id) }
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * 分组标题
 */
@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 清空全部确认弹窗
 */
@Composable
private fun ClearAllConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空所有最近删除？") },
        text = { Text("将永久删除 $count 条记录，且无法恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清空", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
