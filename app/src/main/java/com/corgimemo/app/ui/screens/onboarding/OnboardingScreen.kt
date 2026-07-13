package com.corgimemo.app.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

/**
 * 首次引导主页面
 *
 * 使用 HorizontalPager 实现 10 个引导页面的滑动翻页
 * 分为 3 个阶段：
 * - 阶段1（Step 1-3）：个性化设置（欢迎/身份选择/柯基命名）
 * - 阶段2（Step 4-8）：功能探索（功能概览/待办/灵感/日期/柯基养成）
 * - 阶段3（Step 9-10）：权限与完成（权限请求/完成总结）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 10 })
    val currentPage by viewModel.currentPage.collectAsState()
    val isCompleting by viewModel.isCompleting.collectAsState()
    // 使用响应式 StateFlow，保证输入变化时按钮能实时更新启用状态
    val canGoNext by viewModel.canGoNext.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 跳过确认对话框状态
    var showSkipDialog by remember { mutableStateOf(false) }

    // 同步 ViewModel 页面索引到 PagerState
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) {
            pagerState.animateScrollToPage(currentPage)
        }
    }

    // 同步 PagerState 滑动到 ViewModel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.goToPage(pagerState.currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部区域：Step 4-8 显示"跳过功能介绍"按钮
            // 使用 statusBarsPadding 让按钮自动避开系统状态栏，避免重叠
            if (currentPage in 3..7) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showSkipDialog = true }
                    ) {
                        Text(
                            text = "跳过功能介绍",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 非功能介绍页也预留状态栏高度，保持顶部视觉一致性
                Spacer(modifier = Modifier.statusBarsPadding())
            }

            // HorizontalPager 主体
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    // 阶段1：个性化设置（Step 1-3）
                    0 -> WelcomePage()
                    1 -> UserTypePage(viewModel = viewModel)
                    2 -> CorgiNamingPage(viewModel = viewModel)
                    // 阶段2：功能探索（Step 4-8）
                    3 -> FunctionOverviewPage(
                        onSkip = { showSkipDialog = true }
                    )
                    4 -> TodoFeaturePage(viewModel = viewModel)
                    5 -> InspirationFeaturePage(viewModel = viewModel)
                    6 -> DateFeaturePage()
                    7 -> CorgiSystemPage(viewModel = viewModel)
                    // 阶段3：权限与完成（Step 9-10）
                    8 -> PermissionPage(
                        viewModel = viewModel,
                        onComplete = {
                            viewModel.nextPage()
                        },
                        isCompleting = isCompleting
                    )
                    9 -> CompletionPage(
                        viewModel = viewModel,
                        onComplete = {
                            viewModel.completeOnboarding {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Onboarding.route) {
                                        inclusive = true
                                    }
                                }
                            }
                        },
                        isCompleting = isCompleting
                    )
                }
            }

            // 底部控制栏
            // 上下两行布局：第一行页面指示器，第二行操作按钮，避免重叠
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 第一行：页码指示器（居中显示，单独一行）
                PageIndicator(
                    currentPage = currentPage,
                    totalPages = 10
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 第二行：上一步/下一步按钮（单独一行，靠右对齐）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCompleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        // 上一步按钮（非第一页显示，最后一页由 CompletionPage 自己处理）
                        if (currentPage > 0 && currentPage < 9) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.prevPage()
                                    }
                                },
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(text = "上一步")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        // 下一步按钮（最后一页由 CompletionPage 自己处理）
                        if (currentPage < 9) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.nextPage()
                                    }
                                },
                                enabled = canGoNext,
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (canGoNext) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Text(
                                    text = "下一步",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 跳过功能介绍确认对话框
    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            title = { Text("跳过功能介绍？") },
            text = {
                Text("你可以随时在APP中探索这些功能。跳过后将直接进入权限设置。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSkipDialog = false
                        coroutineScope.launch {
                            viewModel.skipToPermission()
                        }
                    }
                ) {
                    Text("跳过")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSkipDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 页面指示器
 *
 * @param currentPage 当前页面索引
 * @param totalPages 总页面数
 */
@Composable
private fun PageIndicator(
    currentPage: Int,
    totalPages: Int
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            val isSelected = index == currentPage
            val indicatorWidth = if (isSelected) 24.dp else 8.dp
            val indicatorColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(indicatorWidth)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )

            if (index < totalPages - 1) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}
