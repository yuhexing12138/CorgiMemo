package com.corgimemo.app.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.corgimemo.app.ui.components.navigation.BubbleMenuOverlay
import com.corgimemo.app.ui.components.navigation.BubbleType
import com.corgimemo.app.ui.components.navigation.CorgiBottomNavigationBar
import com.corgimemo.app.ui.components.navigation.CenterEditButton
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.screens.date.SpecialDateScreen
import com.corgimemo.app.ui.screens.home.HomeScreen
import com.corgimemo.app.ui.screens.inspiration.InspirationScreen
import com.corgimemo.app.ui.screens.profile.ProfileScreen

/**
 * 主屏幕容器
 * 管理底部导航栏、页面切换和气泡菜单状态
 *
 * 布局架构说明（Material Design 3 Bottom App Bar + FAB 模式）：
 *
 * 渲染顺序（Z轴从底到顶）：
 * 1. 页面内容区域（底层）
 * 2. 底部导航栏（中层）
 * 3. 中央编辑按钮（顶层 - 覆盖在导航栏上层）
 * 4. 气泡菜单覆盖层（最顶层）
 *
 * 关键设计决策：
 * ✅ 不使用 Scaffold.bottomBar 和 floatingActionButton
 * ✅ 原因：Scaffold 的 FAB 会自动添加底部间距，无法与导航栏重叠
 * ✅ 改为手动控制渲染顺序：导航栏先渲染 → 按钮后渲染（自然在上层）
 * ✅ 结果：按钮覆盖在导航栏上，形成"突出"效果
 *
 * 视觉效果：
 * ┌─────────────────────────────────────┐
 * │         页面内容区域                 │
 * ├─────────────────────────────────────┤ ← 导航栏顶部边缘
 * │  📝   💡  ╭──╮  📅   👤          │
 * │  待办 灵感 │⊕│ 日期 我的          │ ← ⊕ 按钮约50%重叠在导航栏上
 * └─────────────────────────────────────┘
 *
 * @param navController 导航控制器
 */
@Composable
fun MainScreen(navController: NavController) {
    // 当前选中的 Tab
    var selectedTab by remember { mutableStateOf(TabItem.TODO) }

    // 气泡菜单是否展开
    var isBubbleExpanded by remember { mutableStateOf(false) }

    // 快速收起模式（设计规范11.2.4：切换页面时100ms快速收起）
    var isFastCollapse by remember { mutableStateOf(false) }

    // 防抖：记录上次点击时间
    var lastClickTime by remember { mutableLongStateOf(0L) }

    // 返回键拦截：气泡展开时只收起气泡，不退出应用
    BackHandler(enabled = isBubbleExpanded) {
        isBubbleExpanded = false
    }

    /**
     * 主屏幕容器布局
     *
     * 设计原则（Material Design 3 Bottom App Bar + Centered FAB）：
     *
     * 架构选择：
     * - 使用空 Scaffold（不使用 bottomBar 和 floatingActionButton）
     * - 原因：需要 FAB 与 BottomBar 有重叠效果，Scaffold 默认行为不满足
     * - 改为在 content 内手动管理导航栏和按钮的渲染顺序
     *
     * Z轴层级控制：
     * - Compose 中后渲染的组件在 Z轴上层
     * - 渲染顺序：内容 → 导航栏 → 中央按钮 → 气泡菜单
     * - 按钮最后渲染 → 自然覆盖在导航栏之上
     */
    Scaffold(
        // 不使用 topBar、bottomBar、floatingActionButton
        // 所有底部组件在 content 内手动布局，以实现精确的重叠控制
    ) { paddingValues ->
        /**
         * 根容器：覆盖整个屏幕（包含 paddingValues 处理顶部系统栏）
         */
        Box(modifier = Modifier.fillMaxSize()) {
            /**
             * 页面内容区域（底层）
             * - 使用 paddingValues 处理顶部状态栏等系统 inset
             * - 额外添加 bottom = 72.dp 为底部导航栏预留空间
             */
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(bottom = 72.dp)  // 为导航栏预留 72dp 空间
            ) {
                when (selectedTab) {
                    TabItem.TODO -> HomeScreen(navController)
                    TabItem.INSPIRE -> InspirationScreen(navController)
                    TabItem.DATE -> SpecialDateScreen(navController)
                    TabItem.PROFILE -> ProfileScreen(navController)
                    TabItem.EDIT -> { /* 中央编辑按钮不是真实 Tab */ }
                }
            }

            /**
             * 底部导航栏（中层）
             * 固定定位在屏幕底部
             */
            CorgiBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (isBubbleExpanded) {
                        isFastCollapse = true
                        isBubbleExpanded = false
                    }
                    selectedTab = tab
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            /**
             * 中央编辑按钮（顶层 - 覆盖在导航栏上）
             *
             * 定位策略（Material Design 3 Bottom App Bar + FAB 规范）：
             *
             * 位置计算：
             * - 按钮尺寸：56dp（圆形）
             * - 导航栏高度：72dp
             * - 目标效果：按钮约 50% 重叠在导航栏上
             * - offset(y = -28.dp)：向上偏移约半个按钮高度
             *
             * 视觉效果：
             * - 按钮下半部分（约28dp）与导航栏上半部分重叠
             * - 按钮上半部分（约28dp）突出于导航栏上方
             * - 形成经典的"从导航栏中突出"的 FAB 效果
             *
             * Z轴层级保证：
             * - 此 Box 在 CorgiBottomNavigationBar 之后声明
             * - Compose 渲染顺序：后声明的组件在上层
             * - 因此按钮自然覆盖在导航栏 Surface 之上
             */
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = (-28).dp),  // 向上偏移28dp，形成50%重叠效果
                contentAlignment = Alignment.Center
            ) {
                CenterEditButton(
                    isExpanded = isBubbleExpanded,
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime > 300) {
                            lastClickTime = now
                            isBubbleExpanded = !isBubbleExpanded
                        }
                    }
                )
            }

            // 气泡菜单覆盖层（最顶层，仅在展开时显示）
            if (isBubbleExpanded) {
                BubbleMenuOverlay(
                    isExpanded = isBubbleExpanded,
                    isFastCollapse = isFastCollapse,
                    onDismiss = {
                        isBubbleExpanded = false
                        isFastCollapse = false
                    },
                    onBubbleClick = { bubbleType ->
                        isBubbleExpanded = false
                        isFastCollapse = false
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
}
