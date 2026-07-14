// 回收站全屏页面（待办 + 灵感 + 日期三 Tab）
package com.corgimemo.app.ui.screens.recyclebin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.ui.components.AppSnackbarHost

/** 主题橙色，选中 Tab 使用 */
private val TabSelectedColor = Color(0xFFFF9A5C)

/**
 * 回收站全屏页面
 *
 * - 顶栏：返回 + 标题"回收站" + 清空全部（文字按钮，仅当有记录时显示）
 * - Tab 栏：待办 / 灵感 / 日期，放在 TopBar 下方
 * - 列表：按时间分组的卡片（待办 Tab 显示 DeletedTodoCard，灵感 Tab 显示 DeletedInspirationCard，日期 Tab 显示 DeletedDateCard）
 * - 空态：柯基表情 + "回收站是空的" + 返回按钮
 * - SnackBar：撤销删除（5s）
 * - 清空确认弹窗：包含待办、灵感和日期三个数量
 * - 退出时根据 source 参数设置 targetTab，确保返回正确的来源页面
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    navController: NavController,
    source: String,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * 返回上一页辅助函数
     *
     * 根据 source 参数设置 targetTab：
     * - source="inspiration" → targetTab="INSPIRE"（返回灵感页）
     * - source="date" → targetTab="DATE"（返回日期页）
     * - source="todo" 或其他 → targetTab="TODO"（返回待办页）
     * 让 MainScreen 接收到返回事件后切换到正确的 Tab。
     */
    val navigateBack: () -> Unit = {
        val targetTab = when (source) {
            "inspiration" -> "INSPIRE"
            "date" -> "DATE"
            else -> "TODO"
        }
        navController.previousBackStackEntry?.savedStateHandle?.set("targetTab", targetTab)
        navController.popBackStack()
    }

    /**
     * 拦截系统返回事件（侧滑返回 / 系统返回键）
     *
     * 确保所有退出方式（应用内返回按钮、空态返回按钮、系统返回）
     * 都经过 navigateBack()，统一设置 targetTab，
     * 让 MainScreen 切换到正确的来源 Tab。
     */
    BackHandler { navigateBack() }

    // 监听一次性 UI 事件（SnackBar）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecycleBinViewModel.UiEvent.ShowSnackBar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is RecycleBinViewModel.UiEvent.ShowSnackBarWithUndo -> {
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
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 仅当有待办、灵感或日期记录时显示"清空全部"按钮
                    if (uiState.todoTotalCount + uiState.inspirationTotalCount + uiState.dateTotalCount > 0) {
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
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.todoTotalCount + uiState.inspirationTotalCount + uiState.dateTotalCount == 0 -> EmptyState(onBack = navigateBack)
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Tab 栏：待办 / 灵感 / 日期
                        TabBar(
                            selectedTab = uiState.selectedTab,
                            onTabSelected = { viewModel.selectTab(it) },
                            todoCount = uiState.todoTotalCount,
                            inspirationCount = uiState.inspirationTotalCount,
                            dateCount = uiState.dateTotalCount
                        )
                        // 内容区：根据 Tab 显示对应列表
                        when (uiState.selectedTab) {
                            RecycleBinTab.TODO -> TodoList(
                                groups = uiState.todoGroups,
                                onRestore = viewModel::restoreTodo,
                                onPermanentDelete = viewModel::permanentlyDeleteTodo
                            )
                            RecycleBinTab.INSPIRATION -> InspirationList(
                                groups = uiState.inspirationGroups,
                                onRestore = viewModel::restoreInspiration,
                                onPermanentDelete = viewModel::permanentlyDeleteInspiration
                            )
                            RecycleBinTab.DATE -> DateList(
                                groups = uiState.dateGroups,
                                onRestore = viewModel::restoreDate,
                                onPermanentDelete = viewModel::permanentlyDeleteDate
                            )
                        }
                    }
                }
            }

            // 清空全部确认弹窗
            if (uiState.showClearAllDialog) {
                ClearAllConfirmDialog(
                    todoCount = uiState.todoTotalCount,
                    inspirationCount = uiState.inspirationTotalCount,
                    dateCount = uiState.dateTotalCount,
                    onConfirm = { viewModel.confirmClearAll() },
                    onDismiss = { viewModel.dismissClearAllDialog() }
                )
            }
        }
    }
}

/**
 * Tab 栏：待办 / 灵感 / 日期
 *
 * 使用 OutlinedButton 实现，选中态使用橙色主题色，未选中态使用默认样式
 */
@Composable
private fun TabBar(
    selectedTab: RecycleBinTab,
    onTabSelected: (RecycleBinTab) -> Unit,
    todoCount: Int,
    inspirationCount: Int,
    dateCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 待办 Tab
        OutlinedButton(
            onClick = { onTabSelected(RecycleBinTab.TODO) },
            shape = RoundedCornerShape(20.dp),
            colors = if (selectedTab == RecycleBinTab.TODO) {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = TabSelectedColor.copy(alpha = 0.15f),
                    contentColor = TabSelectedColor
                )
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
            border = if (selectedTab == RecycleBinTab.TODO) {
                BorderStroke(1.dp, TabSelectedColor)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            }
        ) {
            Text(if (todoCount > 0) "待办 ($todoCount)" else "待办")
        }
        // 灵感 Tab
        OutlinedButton(
            onClick = { onTabSelected(RecycleBinTab.INSPIRATION) },
            shape = RoundedCornerShape(20.dp),
            colors = if (selectedTab == RecycleBinTab.INSPIRATION) {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = TabSelectedColor.copy(alpha = 0.15f),
                    contentColor = TabSelectedColor
                )
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
            border = if (selectedTab == RecycleBinTab.INSPIRATION) {
                BorderStroke(1.dp, TabSelectedColor)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            }
        ) {
            Text(if (inspirationCount > 0) "灵感 ($inspirationCount)" else "灵感")
        }
        // 日期 Tab
        OutlinedButton(
            onClick = { onTabSelected(RecycleBinTab.DATE) },
            shape = RoundedCornerShape(20.dp),
            colors = if (selectedTab == RecycleBinTab.DATE) {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = TabSelectedColor.copy(alpha = 0.15f),
                    contentColor = TabSelectedColor
                )
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
            border = if (selectedTab == RecycleBinTab.DATE) {
                BorderStroke(1.dp, TabSelectedColor)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            }
        ) {
            Text(if (dateCount > 0) "日期 ($dateCount)" else "日期")
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
 * 空态视图：柯基表情 + "回收站是空的" + 返回按钮
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
            "回收站是空的",
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
 * 待办列表视图：按时间分组的卡片
 */
@Composable
private fun TodoList(
    groups: List<DeletedTodoGroup>,
    onRestore: (Long) -> Unit,
    onPermanentDelete: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            item(key = "todo_header_${group.kind}") {
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
 * 灵感列表视图：按时间分组的卡片
 */
@Composable
private fun InspirationList(
    groups: List<DeletedInspirationGroup>,
    onRestore: (Long) -> Unit,
    onPermanentDelete: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            item(key = "inspiration_header_${group.kind}") {
                GroupHeader(title = group.title)
            }
            items(group.items, key = { it.id }) { item ->
                DeletedInspirationCard(
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
 * 日期列表视图：按时间分组的卡片
 */
@Composable
private fun DateList(
    groups: List<DeletedDateGroup>,
    onRestore: (Long) -> Unit,
    onPermanentDelete: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            item(key = "date_header_${group.kind}") {
                GroupHeader(title = group.title)
            }
            items(group.items, key = { it.id }) { item ->
                DeletedDateCard(
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
 *
 * 显示待办、灵感和日期三个数量，提示"将永久删除 N 条待办、M 条灵感和 K 条日期记录，且无法恢复"
 */
@Composable
private fun ClearAllConfirmDialog(
    todoCount: Int,
    inspirationCount: Int,
    dateCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空回收站？") },
        text = {
            Text("将永久删除 $todoCount 条待办、$inspirationCount 条灵感和 $dateCount 条日期记录，且无法恢复。")
        },
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
