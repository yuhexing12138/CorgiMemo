package com.corgimemo.app.ui.components.navigation

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CorgiMemo 底部导航栏组件
 * 包含 5 个 Tab 项和中央编辑按钮
 *
 * @param selectedTab 当前选中的 Tab
 * @param onTabSelected Tab 点击回调
 * @param onCenterButtonClick 中央按钮点击回调
 * @param modifier 修饰符
 */
@Composable
fun CorgiBottomNavigationBar(
    selectedTab: TabItem,
    onTabSelected: (TabItem) -> Unit,
    onCenterButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)  // 固定高度 56dp（Material Design 标准底部导航栏高度）
            // 注意：不使用 navigationBarsPadding()
            // 原因：此组件作为 Scaffold.bottomBar 使用时，
            // Scaffold 会自动处理 Window Insets 并通过 paddingValues 传递给内容区域
            // 如果在此处再次添加 navigationBarsPadding()，会导致 double padding 和布局错乱
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 顶部1dp分割线（设计规范11.2.3）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE0E0E0))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Tab 1: 待办
            NavItem(
                icon = "📝",
                label = "待办",
                isSelected = selectedTab == TabItem.TODO,
                onClick = { onTabSelected(TabItem.TODO) }
            )

            // Tab 2: 灵感
            NavItem(
                icon = "💡",
                label = "灵感",
                isSelected = selectedTab == TabItem.INSPIRE,
                onClick = { onTabSelected(TabItem.INSPIRE) }
            )

            // 中央编辑按钮（突出导航栏8dp，设计规范11.2.3）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .offset(y = (-8).dp),  // 向上偏移8dp，突出导航栏
                contentAlignment = Alignment.Center
            ) {
                CenterEditButton(
                    isExpanded = false, // 基础状态，实际状态由 MainScreen 管理
                    onClick = onCenterButtonClick
                )
            }

            // Tab 4: 日期
            NavItem(
                icon = "📅",
                label = "日期",
                isSelected = selectedTab == TabItem.DATE,
                onClick = { onTabSelected(TabItem.DATE) }
            )

            // Tab 5: 我的
            NavItem(
                icon = "👤",
                label = "我的",
                isSelected = selectedTab == TabItem.PROFILE,
                onClick = { onTabSelected(TabItem.PROFILE) }
            )
        }
        }
    }
}

/**
 * 导航项子组件
 * 显示图标和文字，支持选中/未选中两种状态
 * 包含微交互动画：图标缩放、文字上移、颜色渐变
 *
 * @param icon 图标（使用 emoji）
 * @param label 标签文字
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun NavItem(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // 图标缩放动画：选中时 1.0 → 1.1（设计规范11.2.3：200ms ease-in-out）
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1.0f,
        animationSpec = tween(
            durationMillis = 200,  // 设计规范：200ms
            easing = EaseInOut      // 设计规范：ease-in-out
        ),
        label = "iconScale"
    )

    // 文字上移动画：选中时 0dp → (-4).dp（设计规范11.2.3：200ms ease-in-out）
    val textOffsetY by animateDpAsState(
        targetValue = if (isSelected) (-4).dp else 0.dp,
        animationSpec = tween(
            durationMillis = 200,  // 设计规范：200ms
            easing = EaseInOut      // 设计规范：ease-in-out
        ),
        label = "textOffsetY"
    )

    // 颜色动画目标值
    val iconColor = if (isSelected) Color(0xFFFF9A5C) else Color(0xFF999999)
    val textColor = if (isSelected) Color(0xFFFF9A5C) else Color(0xFF999999)

    Column(
        modifier = Modifier
            .semantics {
                contentDescription = if (isSelected) "${label}页面，已选中" else "切换到${label}页面"
                role = Role.Tab
            }
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图标（带缩放动画）
        Text(
            text = icon,
            fontSize = 24.sp,
            color = iconColor,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            }
        )

        // 标签文字（带上移动画）
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,  // 设计规范11.2.3：12sp Medium
            color = textColor,
            modifier = Modifier.offset(y = textOffsetY)
        )
    }
}
