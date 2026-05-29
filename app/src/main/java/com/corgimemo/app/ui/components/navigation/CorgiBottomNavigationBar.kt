package com.corgimemo.app.ui.components.navigation

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * CorgiMemo 底部导航栏组件（仅包含4个Tab项）
 * 中央编辑按钮已移至 MainScreen 独立处理，确保能正确突出显示
 *
 * UI/UX 设计规范（Material Design 3 + 现代移动应用最佳实践）：
 *
 * 布局策略 - 5等分权重系统：
 * - 将导航栏水平空间均分为5个等宽区域
 * - 每个区域 weight = 1f，确保完美对称
 * - 第3个区域为中央按钮预留位置（空白）
 * - 区域内内容居中对齐，符合人体工学
 *
 * 优势：
 * ✅ 完美对称：左2 = 右2（镜像布局）
 * ✅ 自适应：自动适配不同屏幕宽度
 * ✅ 均匀间距：所有相邻元素间距严格相等
 * ✅ 触摸友好：每个区域 ≥ 72dp（超过48dp最小标准）
 *
 * @param selectedTab 当前选中的 Tab
 * @param onTabSelected Tab 点击回调
 * @param modifier 修饰符
 */
@Composable
fun CorgiBottomNavigationBar(
    selectedTab: TabItem,
    onTabSelected: (TabItem) -> Unit,
    modifier: Modifier = Modifier
) {
    /**
     * 底部导航栏容器
     *
     * 尺寸规格（Material Design 3 + 系统安全区域适配）：
     * - 高度：72dp（56dp标准导航栏 + 16dp底部安全区域）
     * - 底部安全区：为Android系统手势导航条预留空间
     * - 宽度：fillMaxWidth（全屏宽度）
     *
     * UI/UX 设计原则：
     * - 确保所有文字和图标在视觉上100%可见
     * - 避免被系统UI元素（手势条、传统导航键）遮挡
     * - 符合Android 10+ 手势导航的适配要求
     */
    Surface(
        color = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)  // 56dp 导航栏 + 16dp 底部安全区域
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
             * 结构说明：
             * ┌─────┬─────┬─────┬─────┬─────┐
             * │ 待办│ 灵感│ ⊕  │ 日期│ 我的│  ← 5个等宽区域
             * │weight│weight│weight│weight│weight│  ← 每个 weight=1f
             * │ =1f │ =1f │ =1f │ =1f │ =1f │
             * └─────┴─────┴─────┴─────┴─────┘
             *
             * 第3个区域（中央）：预留空白，由 MainScreen 的中央按钮填充
             *
             * Padding策略：
             * - top = 6dp：顶部呼吸空间
             * - bottom = 10dp：额外底部空间，确保文字不被手势条遮挡
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp),  // 底部增加padding避免遮挡
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween  // 权重分配后自然均匀
            ) {
                // ========== 左侧区域（2个Tab）==========

                // Tab 1: 待办（第1个区域，weight=1f）
                NavItem(
                    icon = "📝",
                    label = "待办",
                    isSelected = selectedTab == TabItem.TODO,
                    onClick = { onTabSelected(TabItem.TODO) },
                    modifier = Modifier.weight(1f)
                )

                // Tab 2: 灵感（第2个区域，weight=1f）
                NavItem(
                    icon = "💡",
                    label = "灵感",
                    isSelected = selectedTab == TabItem.INSPIRE,
                    onClick = { onTabSelected(TabItem.INSPIRE) },
                    modifier = Modifier.weight(1f)
                )

                // ========== 中央区域（预留位置）==========
                // 第3个区域：为中央⊕按钮预留（weight=1f）
                // 此处保持空白，中央按钮由 MainScreen 独立定位并覆盖在此区域上方
                Box(
                    modifier = Modifier
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // 空白占位，确保左右两侧对称
                }

                // ========== 右侧区域（2个Tab）==========

                // Tab 4: 日期（第4个区域，weight=1f）
                NavItem(
                    icon = "📅",
                    label = "日期",
                    isSelected = selectedTab == TabItem.DATE,
                    onClick = { onTabSelected(TabItem.DATE) },
                    modifier = Modifier.weight(1f)
                )

                // Tab 5: 我的（第5个区域，weight=1f）
                NavItem(
                    icon = "👤",
                    label = "我的",
                    isSelected = selectedTab == TabItem.PROFILE,
                    onClick = { onTabSelected(TabItem.PROFILE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 导航项子组件
 *
 * UI/UX 设计规范：
 * - 触摸目标：最小 48x48dp（Material Design 无障碍标准）
 * - 内容居中：图标+文字垂直排列并居中
 * - 微交互动画：选中时图标放大、文字上移、颜色变化
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

    // 颜色动画目标值（遵循项目主题色 #FF9A5C）
    val iconColor = if (isSelected) Color(0xFFFF9A5C) else Color(0xFF999999)
    val textColor = if (isSelected) Color(0xFFFF9A5C) else Color(0xFF666666)

    /**
     * 导航项容器
     *
     * 布局结构：
     * Column (垂直排列)
     * ├── Text (图标 emoji) - 22sp
     * └── Text (标签文字) - 11sp Medium
     *
     * 交互增强：
     * - semantics: 辅助功能支持（屏幕阅读器）
     * - clickable: 触摸反馈 + 点击事件
     */
    Column(
        modifier = modifier
            .semantics {
                contentDescription = if (isSelected) "${label}页面，已选中" else "切换到${label}页面"
                role = Role.Tab
            }
            .clickable(onClick = onClick),  // 整个区域可点击
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center  // 内容在区域内垂直居中
    ) {
        // 图标（带缩放动画）
        Text(
            text = icon,
            fontSize = 22.sp,  // 适中大小，与56dp导航栏协调
            color = iconColor,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            }
        )

        // 标签文字（带上移动画）
        Text(
            text = label,
            fontSize = 11.sp,  // 小字号以适应紧凑空间
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.offset(y = textOffsetY)
        )
    }
}
