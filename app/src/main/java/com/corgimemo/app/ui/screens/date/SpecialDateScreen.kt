package com.corgimemo.app.ui.screens.date

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.ui.screens.date.components.DateGroupHeader
import com.corgimemo.app.ui.screens.date.components.SpecialDateCard
import com.corgimemo.app.ui.screens.date.components.SpecialDateEmptyState
import com.corgimemo.app.viewmodel.GroupType
import com.corgimemo.app.viewmodel.SpecialDateViewModel

/**
 * 特殊日期列表主页面
 * 管理和展示所有特殊日期记录，支持分组显示、搜索和快速添加功能
 *
 * 功能说明：
 * - 顶部标题栏："📅 特殊日期"，带搜索图标切换搜索栏
 * - 搜索功能：支持按标题关键词实时过滤特殊日期
 * - 分组展示：按GroupType分为三组（UPCOMING/CELEBRATING/EXPIRED）
 * - FAB按钮：跳转到日期编辑页（新建模式）
 * - 空状态：无日期时显示引导界面
 * - 卡片交互：点击进入编辑，长按显示操作菜单
 *
 * @param navController 导航控制器，用于页面跳转
 * @param viewModel 特殊日期视图模型（通过 Hilt 自动注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDateScreen(
    navController: NavController,
    viewModel: SpecialDateViewModel = hiltViewModel()
) {
    /** 收集 ViewModel 中的分组数据 */
    val groupedDates by viewModel.groupedDates.collectAsState()

    /** 收集搜索查询状态 */
    val searchQuery by viewModel.searchQuery.collectAsState()

    /** 控制搜索栏的显示/隐藏状态 */
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            /** 顶部应用栏：显示页面标题 + 搜索图标 */
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        /** 搜索激活状态：显示搜索输入框 */
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { newQuery ->
                                /** 更新搜索关键词并执行过滤 */
                                viewModel.updateSearchQuery(newQuery)
                            },
                            placeholder = {
                                Text(
                                    text = "搜索日期...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    } else {
                        /** 默认状态：显示页面标题 */
                        Text(
                            text = "📅 特殊日期",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    /** 搜索切换按钮 */
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            /** 关闭搜索时清空查询 */
                            viewModel.updateSearchQuery("")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = if (isSearchActive) "关闭搜索" else "搜索",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            /** 浮动操作按钮：跳转到日期编辑页（新建模式） */
            FloatingActionButton(
                onClick = {
                    /** 导航到日期编辑页面（新建模式） */
                    navController.navigate("date_edit")
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加日期"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            /** 判断是否显示空状态或列表内容 */
            if (groupedDates.isEmpty()) {
                /** 空状态：显示特殊日期引导界面 */
                SpecialDateEmptyState(
                    onAddClick = {
                        /** 点击 CTA 按钮导航到编辑页 */
                        navController.navigate("date_edit")
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                /** 特殊日期分组列表 */
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    /** 按固定顺序遍历三种分组类型 */
                    GroupType.entries.forEach { groupType ->
                        /** 获取当前分组的日期列表 */
                        val datesInGroup = groupedDates[groupType] ?: emptyList()

                        /** 仅当该组有数据时才渲染 */
                        if (datesInGroup.isNotEmpty()) {
                            /** 分组标题 */
                            item(key = "header_${groupType.name}") {
                                DateGroupHeader(groupType = groupType)
                            }

                            /** 该分组下的日期卡片列表 */
                            items(
                                items = datesInGroup,
                                key = { date -> "date_${date.id}" }
                            ) { date ->
                                SpecialDateCard(
                                    date = date,
                                    onClick = {
                                        /** 点击卡片：导航到编辑页（传入日期ID） */
                                        navController.navigate("date_edit/${date.id}")
                                    },
                                    onLongClick = {
                                        /** 长按卡片：TODO 显示操作菜单（删除/置顶/归档） */
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
