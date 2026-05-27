package com.corgimemo.app.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.corgimemo.app.ui.components.navigation.BubbleMenuOverlay
import com.corgimemo.app.ui.components.navigation.BubbleType
import com.corgimemo.app.ui.components.navigation.CorgiBottomNavigationBar
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.screens.date.SpecialDateScreen
import com.corgimemo.app.ui.screens.home.HomeScreen
import com.corgimemo.app.ui.screens.inspiration.InspirationScreen
import com.corgimemo.app.ui.screens.profile.ProfileScreen

/**
 * 主屏幕容器
 * 管理底部导航栏、页面切换和气泡菜单状态
 *
 * 注意：此组件不包含 topBar，因为各子页面（HomeScreen、ProfileScreen等）
 * 已自行管理各自的顶部导航栏，避免重复显示
 *
 * @param navController 导航控制器
 */
@Composable
fun MainScreen(navController: NavController) {
    // 当前选中的 Tab
    var selectedTab by remember { mutableStateOf(TabItem.TODO) }

    // 气泡菜单是否展开
    var isBubbleExpanded by remember { mutableStateOf(false) }

    // 防抖：记录上次点击时间
    var lastClickTime by remember { mutableLongStateOf(0L) }

    // 返回键拦截：气泡展开时只收起气泡，不退出应用
    BackHandler(enabled = isBubbleExpanded) {
        isBubbleExpanded = false
    }

    Scaffold(
        // 移除 topBar，由各子页面自行管理顶部导航栏
        bottomBar = {
            CorgiBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    // 切换 Tab 时自动收起气泡
                    isBubbleExpanded = false
                    selectedTab = tab
                },
                onCenterButtonClick = {
                    // 防抖处理：300ms 内的重复点击将被忽略
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime > 300) {
                        lastClickTime = now
                        isBubbleExpanded = !isBubbleExpanded
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 根据选中的 Tab 显示对应页面内容
            when (selectedTab) {
                TabItem.TODO -> HomeScreen(navController)
                TabItem.INSPIRE -> InspirationScreen(navController)
                TabItem.DATE -> SpecialDateScreen(navController)
                TabItem.PROFILE -> ProfileScreen(navController)
                TabItem.EDIT -> { /* 中央编辑按钮不是真实 Tab */ }
            }
        }

        // 气泡菜单覆盖层（仅在展开时显示）
        if (isBubbleExpanded) {
            BubbleMenuOverlay(
                isExpanded = isBubbleExpanded,
                onDismiss = { isBubbleExpanded = false },
                onBubbleClick = { bubbleType ->
                    // 收起气泡菜单
                    isBubbleExpanded = false
                    // 根据气泡类型导航到对应页面
                    when (bubbleType) {
                        BubbleType.CREATE_TODO -> navController.navigate("todo_edit")
                        BubbleType.RECORD_INSPIRE -> navController.navigate("inspiration_edit")
                        BubbleType.SPECIAL_DATE -> navController.navigate("date_edit")
                    }
                }
            )
        }
    }
}
