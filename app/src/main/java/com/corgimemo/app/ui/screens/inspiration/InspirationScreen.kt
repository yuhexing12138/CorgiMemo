package com.corgimemo.app.ui.screens.inspiration

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.components.SearchBar
import com.corgimemo.app.ui.screens.inspiration.components.InspirationCard
import com.corgimemo.app.ui.screens.inspiration.components.InspirationEmptyState
import com.corgimemo.app.ui.screens.inspiration.components.TimelineGroupHeader
import com.corgimemo.app.viewmodel.InspirationViewModel

/**
 * 灵感记录列表页面（Phase 3）
 * 展示所有灵感记录的时间线列表，支持搜索、分组展示和快速添加功能
 *
 * 功能说明：
 * - 顶部标题栏："💡 我的灵感"
 * - 搜索栏：支持关键词实时搜索灵感
 * - 时间线分组：按创建日期分组显示灵感卡片
 * - FAB按钮：跳转到灵感编辑页
 * - 空状态：无灵感时显示引导界面
 *
 * @param navController 导航控制器，用于页面跳转
 * @param viewModel 灵感视图模型（通过 Hilt 自动注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationScreen(
    navController: NavController,
    viewModel: InspirationViewModel = hiltViewModel()
) {
    /** 收集 ViewModel 中的状态数据 */
    val groupedInspirations by viewModel.groupedInspirations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            /** 顶部应用栏：显示页面标题 */
            TopAppBar(
                title = {
                    Text(
                        text = "💡 我的灵感",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        },
        floatingActionButton = {
            /** 浮动操作按钮：跳转到灵感编辑页 */
            FloatingActionButton(
                onClick = {
                    /** 导航到灵感编辑页面（新建模式） */
                    navController.navigate("inspiration_edit")
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "记录灵感"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                /** 搜索栏组件 */
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { newQuery ->
                        /** 更新搜索关键词并执行搜索 */
                        viewModel.search(newQuery)
                    },
                    onClear = {
                        /** 清空搜索并重新加载全部灵感 */
                        viewModel.clearSearch()
                    },
                    placeholder = "搜索灵感...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                /** 判断是否显示空状态或列表内容 */
                if (groupedInspirations.isEmpty()) {
                    /** 空状态：显示灵感引导界面 */
                    InspirationEmptyState(
                        onAddClick = {
                            /** 点击 CTA 按钮导航到编辑页 */
                            navController.navigate("inspiration_edit")
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    /** 灵感时间线列表 */
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        /** 遍历日期分组 */
                        groupedInspirations.forEach { (dateKey, inspirationsInGroup) ->

                            /** 时间线分组标题 */
                            item(key = "header_$dateKey") {
                                TimelineGroupHeader(dateText = dateKey)
                            }

                            /** 该日期下的灵感卡片列表 */
                            items(
                                items = inspirationsInGroup,
                                key = { inspiration -> "inspiration_${inspiration.id}" }
                            ) { inspiration ->
                                /** 解析 JSON 格式的标签和图片路径 */
                                val tags = viewModel.decodeTags(inspiration.tags)
                                val imagePaths = viewModel.decodePaths(inspiration.imagePaths)
                                val formattedTime = viewModel.formatTime(inspiration.createdAt)

                                InspirationCard(
                                    inspiration = inspiration,
                                    tags = tags,
                                    imagePaths = imagePaths,
                                    formattedTime = formattedTime,
                                    relationHint = null,
                                    onClick = {
                                        /** 点击卡片：导航到编辑页（传入灵感ID） */
                                        navController.navigate("inspiration_edit/${inspiration.id}")
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

            /** 加载状态指示器（可选扩展） */
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
