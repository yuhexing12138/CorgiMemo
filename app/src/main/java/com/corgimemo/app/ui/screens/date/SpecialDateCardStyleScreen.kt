package com.corgimemo.app.ui.screens.date

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleRenderer
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleSelector
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleTab
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleTabs
import com.corgimemo.app.viewmodel.SaveState
import com.corgimemo.app.viewmodel.SpecialDateCardStyleViewModel
import kotlinx.coroutines.launch

/**
 * 日期卡片样式选择页（新建日期流程第二步）
 *
 * 页面结构（自上而下）：
 * 1. 顶部 TopAppBar：仅保留返回箭头，标题留空（Spacer 占位以保持居中布局风格）
 * 2. 卡片预览区：使用 [DateCardStyleRenderer] 渲染用户当前选中的大卡片样式
 * 3. 样式 / 颜色 Tab 切换条：当前阶段颜色 Tab 触发"开发中"占位
 * 4. 底部样式选择器（横向滚动）：[DateCardStyleSelector] 列出所有可用的 [DateCardStyle]
 * 5. 保存按钮：点击后调用 ViewModel 落库；期间显示 loading；成功/失败给出不同反馈
 *
 * 业务行为：
 * - 接收 QuickCreate 页传来的 4 个参数（title/dateMillis/category/isPinned）
 * - 用户选中的样式通过 [SpecialDateCardStyleViewModel.saveNewDate] 落库
 * - 落库成功后：写入 `previousBackStackEntry.savedStateHandle["date_saved"] = true`
 *   并 popBackStack 回到主页；SpecialDateScreen 通过 LaunchedEffect 监听此信号
 *
 * @param navController 导航控制器（用于 popBackStack + SavedStateHandle 通信）
 * @param title         来自 QuickCreate 的名称（已 URL 解码）
 * @param dateMillis    来自 QuickCreate 的目标日期时间戳（毫秒）
 * @param category      来自 QuickCreate 的分类（预设枚举名或自定义字符串）
 * @param isPinned      来自 QuickCreate 的置顶开关
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDateCardStyleScreen(
    navController: NavController,
    title: String,
    dateMillis: Long,
    category: String,
    isPinned: Boolean
) {
    /** Snackbar 宿主：用于显示保存失败 / 颜色 Tab 占位提示 */
    val snackbarHostState = remember { SnackbarHostState() }
    /** 协程作用域：用于在事件回调中启动 launch 显示 Snackbar */
    val coroutineScope = rememberCoroutineScope()
    /** 通过 Hilt 自动注入的 ViewModel：负责落库逻辑与保存状态机 */
    val viewModel: SpecialDateCardStyleViewModel = hiltViewModel()
    /** 订阅保存状态：Idle / Saving / Success / Error */
    val saveState by viewModel.saveState.collectAsState()

    /** 用户当前选中的卡片样式（本地状态，初始为 DateCardStyle.DEFAULT） */
    var selectedStyle by remember { mutableStateOf<DateCardStyle>(DateCardStyle.DEFAULT) }
    /** Tab 切换状态：默认停留在"样式"Tab */
    var selectedTab by remember { mutableStateOf(DateCardStyleTab.STYLE) }

    /**
     * 监听保存状态机的变化
     *
     * - Success：通过 previousBackStackEntry.savedStateHandle 向主页发"date_saved=true"
     *           信号后立即 popBackStack；主页在 SpecialDateScreen 中监听该信号
     * - Error：弹 Snackbar 提示"保存失败,请重试"
     * - Idle / Saving：无需响应
     *
     * 注意：使用 saveState 作为 key 触发；为防止屏幕旋转后重复回调，Success 之后
     * ViewModel 状态仍会停留在 Success 直到用户离开页面或再次保存，故此处依赖
     * popBackStack 自然结束。
     */
    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Success -> {
                // 向主页（SpecialDateScreen）发送保存成功信号
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("date_saved", true)
                // 返回上一页（SpecialDateCardStyleScreen 自己从栈顶弹出）
                navController.popBackStack()
            }
            is SaveState.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("保存失败,请重试")
                }
            }
            else -> { /* Idle / Saving 不需响应 */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Spacer(Modifier) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. 大卡片预览区（占据主要剩余空间）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                DateCardStyleRenderer(
                    style = selectedStyle,
                    title = title.ifBlank { "未命名" },
                    targetDateMillis = dateMillis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. 样式 / 颜色 Tab 切换条
            DateCardStyleTabs(
                selected = selectedTab,
                onTabChange = { tab ->
                    if (tab == DateCardStyleTab.COLOR) {
                        // 颜色功能当前阶段未实现，弹 Snackbar 占位
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("颜色功能开发中")
                        }
                    } else {
                        selectedTab = tab
                    }
                }
            )

            // 3. 底部样式选择器（横向滚动）
            DateCardStyleSelector(
                styles = DateCardStyle.all,
                selected = selectedStyle,
                onSelect = { selectedStyle = it },
                targetDateMillis = dateMillis,
                title = title.ifBlank { "未命名" }
            )

            // 与 QuickCreate "下一步" 按钮位置对齐:24dp 间距 + 底部按钮(无边距)
            Spacer(modifier = Modifier.height(24.dp))

            // 4. 保存按钮
            Button(
                onClick = {
                    // 防御：dateMillis 为 0 表示未传有效日期（极端深链场景），弹提示
                    if (dateMillis == 0L) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("请先返回选择日期")
                        }
                    } else {
                        // 正常路径：调用 ViewModel 落库
                        viewModel.saveNewDate(
                            title = title.ifBlank { "未命名" },
                            dateMillis = dateMillis,
                            category = category.ifBlank { "OTHER" },
                            isPinned = isPinned,
                            cardStyle = selectedStyle
                        )
                    }
                },
                // 保存中禁止重复点击
                enabled = saveState !is SaveState.Saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                // 保存中显示进度环
                if (saveState is SaveState.Saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (saveState is SaveState.Saving) "保存中..." else "保存",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
