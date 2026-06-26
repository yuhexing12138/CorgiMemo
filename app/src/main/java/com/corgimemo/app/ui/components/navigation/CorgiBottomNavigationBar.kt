package com.corgimemo.app.ui.components.navigation

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.corgimemo.app.ui.components.safeAreaForBottomBar /** 安全区域内边距（语义化封装）*/
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CorgiMemo 底部导航栏组件（4个Tab项 + 中央凸起编辑按钮）
 *
 * ══════════════════════════════════════════════════════════
 * 布局架构：三层无裁剪结构（解决 Compose Surface 固定高度裁剪问题）
 * ══════════════════════════════════════════════════════════
 *
 * 外层容器 Box（无固定高度、无裁剪、处理系统安全区域）
 * ├── Layer 1: 导航栏背景层（底部对齐，height=56dp）
 * │   ├── 顶部1dp分割线
 * │   └── Row 5等分: [待办] [灵感] [空白] [日期] [我的]
 * └── Layer 2: 中央按钮覆盖层（底部对齐，offset y=-28dp凸起）
 *     └── CenterEditButton (56dp圆形，上半部分浮于导航栏上方)
 *
 * 设计原则：
 * - 外层容器不设固定高度，由子元素自动撑开总高度
 * - 外层容器不裁剪，允许中央按钮向上溢出显示
 * - 系统导航栏安全区域统一在外层容器处理
 * - 导航栏背景仅覆盖图标+文字区域（~56dp）
 * - 中央按钮作为独立层叠加，实现"浮于导航栏上方"效果
 *
 * Material Design 3 规范参考：
 * - 底部导航栏标准高度：56dp（图标24dp + 文字 + 内边距）
 * - FAB/中央按钮标准尺寸：56dp（与导航栏等宽的触摸目标）
 * - 凸起量：约28dp（按钮直径的一半，使视觉重心在导航栏边缘）
 *
 * @param selectedTab 当前选中的 Tab
 * @param onTabSelected Tab 点击回调
 * @param isExpanded 中央按钮是否展开（显示 ✕）
 * @param onCenterButtonClick 中央按钮点击回调
 * @param modifier 修饰符
 */
@Composable
fun CorgiBottomNavigationBar(
    selectedTab: TabItem,
    onTabSelected: (TabItem) -> Unit,
    isExpanded: Boolean = false,
    onCenterButtonClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    /**
     * 外层容器 — 三层布局的核心
     *
     * 关键设计决策：
     * - 使用 Box 而非 Surface：Box 不强制裁剪子元素
     * - 无固定高度：总高度由子元素自动计算
     * - safeAreaForBottomBar 放在 Layer1 上（而非此处），确保只有导航栏区域被推高
     */
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // ═══════════════════════════════════════════
        // Layer 1: 导航栏背景层（底部对齐 + 系统导航栏安全区域）
        //
        // 使用 Column（自动高度）替代固定高度 Box：
        // - 高度由内容自动决定：图标(~30dp) + 文字(~14dp) + 内边距 + 安全区域
        // - safeAreaForBottomBar 在此处理，Column 自动扩展以容纳安全区域
        // - 不存在"固定高度被padding压缩"的问题
        // ═══════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)  // 锚定在容器底部
                .safeAreaForBottomBar()  // 系统导航栏安全区域（自动推高整个导航栏）
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部1dp分割线（设计规范11.2.3）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE0E0E0))
            )

            /**
             * 5等分行布局系统
             *
             * ┌─────┬─────┬─────┬─────┬─────┐
             * │ 待办│ 灵感│  ⊕  │ 日期│ 我的│
             * │w=1f │w=1f │w=1f │w=1f │w=1f │
             * └─────┴─────┴─────┴─────┴─────┘
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),  // 底部4dp：紧贴导航栏，safeArea处理安全区域
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ========== 左侧区域（2个Tab）==========

                NavItem(
                    icon = "📝",
                    label = "待办",
                    isSelected = selectedTab == TabItem.TODO,
                    onClick = { onTabSelected(TabItem.TODO) },
                    modifier = Modifier.weight(1f)
                )

                NavItem(
                    icon = "💡",
                    label = "灵感",
                    isSelected = selectedTab == TabItem.INSPIRE,
                    onClick = { onTabSelected(TabItem.INSPIRE) },
                    modifier = Modifier.weight(1f)
                )

                // ========== 中央区域（空白占位）==========
                // 为Layer 2的CenterEditButton预留水平空间
                Box(modifier = Modifier.weight(1f))

                // ========== 右侧区域（2个Tab）==========

                NavItem(
                    icon = "📅",
                    label = "日期",
                    isSelected = selectedTab == TabItem.DATE,
                    onClick = { onTabSelected(TabItem.DATE) },
                    modifier = Modifier.weight(1f)
                )

                NavItem(
                    icon = "👤",
                    label = "我的",
                    isSelected = selectedTab == TabItem.PROFILE,
                    onClick = { onTabSelected(TabItem.PROFILE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }  // ← 关闭 Column（导航栏背景层）

        // ═══════════════════════════════════════════
        // Layer 2: 中央编辑按钮覆盖层（始终可见）
        //
        // 核心设计：同一按钮原地切换图标（⊕ ↔ ✕），不隐藏/不消失
        // - 收起态: 显示 ⊕（加号）
        // - 展开态: 显示 ✕（关闭）+ 旋转动画
        // 按钮位置固定在导航栏中央，offset(y=-36dp) 使其凸起于导航栏上方
        // ═══════════════════════════════════════════
        CenterEditButton(
            isExpanded = isExpanded,  // 控制图标切换 ⊕↔✕ + 内部旋转动画
            onClick = onCenterButtonClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-36).dp)
        )
    }
}

/**
 * 导航项子组件
 *
 * UI/UX 设计规范：
 * - 触摸目标：最小 48×48dp（Material Design 无障碍标准，超过44×44px最低要求）
 * - 内容居中：图标+文字垂直排列并居中
 * - 微交互动画：选中时图标放大、文字上移、颜色变化
 * - 点击反馈：主题色涟漪效果（20%透明度 #FF9A5C）
 *
 * 动画规格（设计规范11.2.3）：
 * - 图标缩放：1.0 → 1.1（200ms, ease-in-out）
 * - 文字上移：0dp → (-4).dp（200ms, ease-in-out）
 * - 颜色渐变：灰色 → 主题色（200ms）
 *
 * @param icon 图标（使用 emoji）
 * @param label 标签文字
 * @param isSelected 是否选中
 * @param onClick 点击回调
 * @param modifier 修饰符（包含 weight 用于5等分布局）
 */
@Composable
private fun NavItem(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /** 图标缩放动画：选中时 1.0 → 1.1（设计规范11.2.3：200ms ease-in-out）*/
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1.0f,
        animationSpec = tween(
            durationMillis = 200,
            easing = EaseInOut
        ),
        label = "iconScale"
    )

    /** 文字上移动画：选中时 0dp → (-4).dp（设计规范11.2.3：200ms ease-in-out）*/
    val textOffsetY by animateDpAsState(
        targetValue = if (isSelected) (-4).dp else 0.dp,
        animationSpec = tween(
            durationMillis = 200,
            easing = EaseInOut
        ),
        label = "textOffsetY"
    )

    /** 颜色动画目标值（遵循项目主题色 #FF9A5C）*/
    val iconColor = if (isSelected) Color(0xFFFF9A5C) else Color(0xFF999999)
    val textColor = if (isSelected) Color(0xFFFF9A5C) else Color(0xFF666666)

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)  // 最小触摸高度48dp（符合Material Design无障碍标准44×44px）
            .semantics {
                contentDescription = if (isSelected) "${label}页面，已选中" else "切换到${label}页面"
                role = Role.Tab
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(  // 主题色涟漪效果（设计规范：点击视觉反馈）
                    bounded = true,  // 涟漪限制在触摸目标48dp内（视觉更克制，不会扩散到图标/文字之外）
                    color = Color(0xFFFF9A5C).copy(alpha = 0.2f)  // 20%透明度主题色
                ),
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标（带缩放动画）
        Text(
            text = icon,
            fontSize = 22.sp,
            color = iconColor,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            }
        )

        // 标签文字（带上移动画）
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.offset(y = textOffsetY)
        )
    }
}
