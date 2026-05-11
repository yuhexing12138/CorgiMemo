package com.corgimemo.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corgimemo.app.R
import com.corgimemo.app.viewmodel.HomeViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 首页屏幕组件
 * 
 * 显示待办事项列表，提供添加待办和导航到个人页面的入口
 * 
 * @param onAddTodo 点击添加按钮的回调
 * @param onEditTodo 点击待办项的回调，传入待办ID
 * @param onProfile 点击个人图标按钮的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTodo: () -> Unit,
    onEditTodo: (String) -> Unit,
    onProfile: () -> Unit
) {
    // 使用 Koin 获取 ViewModel
    val viewModel: HomeViewModel = koinViewModel()
    
    // 收集 ViewModel 状态
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        // 顶部导航栏
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.home_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    // 个人页面入口
                    IconButton(onClick = onProfile) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(id = R.string.profile_title),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        // 浮动操作按钮 - 添加待办
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTodo,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.home_add_todo),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { paddingValues ->
        // 主内容区域
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            // 根据状态显示不同内容
            when {
                // 加载中
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // 待办列表为空
                uiState.todos.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                        Text(
                            text = stringResource(id = R.string.home_no_todo),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                // 显示待办列表
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.todos) { todo ->
                            TodoItem(
                                title = todo.title,
                                description = todo.description,
                                isCompleted = todo.isCompleted,
                                onClick = { onEditTodo(todo.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 待办项组件
 * 
 * @param title 待办标题
 * @param description 待办描述
 * @param isCompleted 是否已完成
 * @param onClick 点击回调
 */
@Composable
fun TodoItem(
    title: String,
    description: String?,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 2.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.Bold,
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}