package com.corgimemo.app.ui.screens.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corgimemo.app.R
import com.corgimemo.app.viewmodel.TodoViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 待办创建/编辑屏幕组件
 * 
 * @param todoId 待办ID（编辑模式时传入，新建时为null）
 * @param onBack 返回按钮回调
 * @param onSave 保存按钮回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    todoId: String? = null,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    // 使用 Koin 获取 ViewModel
    val viewModel: TodoViewModel = koinViewModel()
    
    // 收集 ViewModel 状态
    val uiState by viewModel.uiState.collectAsState()
    
    // 本地状态 - 表单输入
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    // 如果是编辑模式，加载待办数据
    LaunchedEffect(todoId) {
        if (!todoId.isNullOrEmpty()) {
            viewModel.loadTodo(todoId)
        }
    }
    
    // 当 ViewModel 数据更新时同步到本地状态
    LaunchedEffect(uiState.todo) {
        uiState.todo?.let { todo ->
            title = todo.title
            description = todo.description ?: ""
        }
    }
    
    Scaffold(
        // 顶部导航栏
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (todoId.isNullOrEmpty()) {
                            stringResource(id = R.string.todo_title)
                        } else {
                            stringResource(id = R.string.todo_edit_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    // 返回按钮
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.todo_cancel),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // 主内容区域
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // 标题输入框
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(id = R.string.todo_title_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true
                )
                
                // 描述输入框
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(id = R.string.todo_description_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 10
                )
                
                // 保存按钮
                Button(
                    onClick = {
                        viewModel.saveTodo(title, description, todoId)
                        onSave()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    enabled = title.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = stringResource(id = R.string.todo_save),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 取消按钮
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    enabled = true
                ) {
                    Text(
                        text = stringResource(id = R.string.todo_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}