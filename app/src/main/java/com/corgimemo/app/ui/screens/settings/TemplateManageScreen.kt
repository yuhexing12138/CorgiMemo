package com.corgimemo.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.UserTemplateEntity
import com.corgimemo.app.ui.components.TemplateEditDialog

/**
 * 模板管理页面
 * 显示用户创建的所有自定义模板，支持创建、编辑和删除操作
 *
 * @param onNavigateBack 返回上一页的回调
 * @param templateViewModel 模板 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateManageScreen(
    onNavigateBack: () -> Unit,
    templateViewModel: com.corgimemo.app.viewmodel.TemplateViewModel
) {
    /** 用户模板列表 */
    val userTemplates by templateViewModel.userTemplates.collectAsState(initial = emptyList())

    /** 是否显示创建/编辑对话框 */
    var showEditDialog by remember { mutableStateOf(false) }

    /** 当前编辑的模板（null 表示创建模式）*/
    var editingTemplate by remember { mutableStateOf<UserTemplateEntity?>(null) }

    /** 要删除的模板（用于确认对话框）*/
    var deletingTemplate by remember { mutableStateOf<UserTemplateEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模板管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTemplate = null
                    showEditDialog = true
                },
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "创建新模板")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (userTemplates.isEmpty()) {
                /** 空状态提示 */
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "📝", fontSize = 64.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "还没有自定义模板",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "点击右下角 + 按钮创建你的第一个模板",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                /** 模板网格列表 */
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(userTemplates, key = { it.id }) { template ->
                        UserTemplateCard(
                            template = template,
                            onEdit = {
                                editingTemplate = template
                                showEditDialog = true
                            },
                            onDelete = {
                                deletingTemplate = template
                            }
                        )
                    }
                }
            }

            /** 创建/编辑对话框 */
            if (showEditDialog) {
                TemplateEditDialog(
                    initialName = editingTemplate?.name ?: "",
                    initialIcon = editingTemplate?.icon ?: "☀️",
                    initialDescription = editingTemplate?.description ?: "",
                    initialTodos = parseTodosFromJson(editingTemplate?.todosJson ?: "[]"),
                    isEditMode = editingTemplate != null,
                    onSave = { name, icon, description, todosJson ->
                        if (editingTemplate != null) {
                            templateViewModel.updateTemplate(
                                id = editingTemplate!!.id,
                                name = name,
                                icon = icon,
                                description = description,
                                todosJson = todosJson
                            )
                        } else {
                            templateViewModel.createTemplate(
                                name = name,
                                icon = icon,
                                description = description,
                                todosJson = todosJson
                            )
                        }
                        showEditDialog = false
                        editingTemplate = null
                    },
                    onDismiss = {
                        showEditDialog = false
                        editingTemplate = null
                    }
                )
            }

            /** 删除确认对话框 */
            if (deletingTemplate != null) {
                AlertDialog(
                    onDismissRequest = { deletingTemplate = null },
                    title = { Text("删除模板") },
                    text = { Text("确定要删除「${deletingTemplate!!.name}」吗？此操作不可撤销。") },
                    confirmButton = {
                        Button(
                            onClick = {
                                templateViewModel.deleteTemplate(deletingTemplate!!)
                                deletingTemplate = null
                            }
                        ) {
                            Text("删除")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { deletingTemplate = null }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

/**
 * 解析 JSON 字符串为待办列表
 *
 * @param todosJson JSON 字符串
 * @return 待办列表
 */
private fun parseTodosFromJson(todosJson: String): List<String> {
    return try {
        kotlinx.serialization.json.Json.decodeFromString<List<String>>(todosJson)
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 用户模板卡片组件
 *
 * @param template 模板实体
 * @param onEdit 编辑回调
 * @param onDelete 删除回调
 */
@Composable
private fun UserTemplateCard(
    template: UserTemplateEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val todoCount = parseTodosFromJson(template.todosJson).size

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /** 图标 */
            Text(text = template.icon, fontSize = 36.sp)

            Spacer(modifier = Modifier.height(8.dp))

            /** 名称 */
            Text(
                text = template.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            /** 待办数量 */
            Text(
                text = "$todoCount 个待办",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            /** 操作按钮 */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                /** 编辑按钮 */
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "编辑",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                /** 删除按钮 */
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "删除",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
