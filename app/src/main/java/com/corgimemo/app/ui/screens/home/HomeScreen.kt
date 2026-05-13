package com.corgimemo.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.ui.components.CorgiCompanion
import com.corgimemo.app.ui.components.CorgiNamerDialog
import com.corgimemo.app.ui.components.EmptyState
import com.corgimemo.app.ui.components.TodoListItem
import com.corgimemo.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val todos by viewModel.todos.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val corgiData by viewModel.corgiData.collectAsState()
    val showNamerDialog by viewModel.showNamerDialog.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "待办事项", color = MaterialTheme.colorScheme.onSurface)
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("todo_edit") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Todo")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                CorgiCompanion(corgiData = corgiData)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterButton(
                        text = "全部",
                        isSelected = filterStatus == HomeViewModel.FilterStatus.ALL,
                        onClick = { viewModel.setFilterStatus(HomeViewModel.FilterStatus.ALL) }
                    )
                    FilterButton(
                        text = "待办",
                        isSelected = filterStatus == HomeViewModel.FilterStatus.PENDING,
                        onClick = { viewModel.setFilterStatus(HomeViewModel.FilterStatus.PENDING) }
                    )
                    FilterButton(
                        text = "已完成",
                        isSelected = filterStatus == HomeViewModel.FilterStatus.COMPLETED,
                        onClick = { viewModel.setFilterStatus(HomeViewModel.FilterStatus.COMPLETED) }
                    )
                }

                if (todos.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(todos, key = { it.id }) { todo ->
                            TodoListItem(
                                todo = todo,
                                onToggleComplete = { id, isChecked -> viewModel.toggleTodoStatus(id, isChecked) },
                                onDelete = { viewModel.deleteTodo(it) },
                                onClick = { navController.navigate("todo_edit/${todo.id}") }
                            )
                        }
                    }
                }
            }
        }

        if (showNamerDialog) {
            CorgiNamerDialog(
                onConfirm = { name -> viewModel.saveCorgiName(name) },
                onDismiss = { viewModel.dismissNamerDialog() }
            )
        }
    }
}

@Composable
fun FilterButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    ) {
        Text(text = text)
    }
}
