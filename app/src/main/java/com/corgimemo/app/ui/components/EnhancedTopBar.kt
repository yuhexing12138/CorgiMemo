package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

/** 顶部栏右侧图标类型 */
enum class RightIconType {
    /** 柯基爪子图标（我的页面） */
    CORGIE,
    /** 三点菜单图标（待办/灵感/日期页面） */
    MORE_MENU
}

/**
 * 增强标题栏组件（统一版）
 *
 * 包含左侧 ☰ 菜单按钮、中间标题、右侧可配置功能按钮+右侧图标按钮。
 * 三个核心页面（待办/灵感/日期）共用此组件，通过参数配置差异化内容。
 *
 * @param title 标题文字
 * @param onMenuClick 菜单按钮点击回调（打开侧滑导航栏）
 * @param onCorgiClick 柯基图标点击回调（进入柯基详情页）
 * @param actionButtons 右侧自定义功能按钮列表（右侧图标按钮之前显示）
 * @param modifier 修饰符
 * @param rightIconType 右侧图标类型，默认为柯基爪子图标
 * @param onMoreClick 三点菜单图标点击回调（可空，仅在 rightIconType 为 MORE_MENU 时使用）
 * @param dropdownContent 三点菜单触发的下拉内容（仅 MORE_MENU 模式生效）。
 *        关键：必须将 DropdownMenu 渲染在这里，而不是 TopBar 外部，
 *        否则 Material 3 DropdownMenu 会锚定到错误的父容器导致位置偏移。
 */
@Composable
fun EnhancedTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onCorgiClick: () -> Unit,
    actionButtons: List<@Composable () -> Unit> = emptyList(),
    modifier: Modifier = Modifier,
    rightIconType: RightIconType = RightIconType.CORGIE,
    onMoreClick: (() -> Unit)? = null,
    dropdownContent: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth().safeAreaForTopBar()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                @Suppress("DEPRECATION")
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "菜单",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(8.dp))

            actionButtons.forEach { button ->
                button()
            }

            when (rightIconType) {
                RightIconType.CORGIE -> {
                    IconButton(onClick = onCorgiClick) {
                        Icon(
                            imageVector = Icons.Default.Pets,
                            contentDescription = "柯基详情",
                            tint = UiColors.Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                RightIconType.MORE_MENU -> {
                    // 关键：把 IconButton 和 DropdownMenu 包在同一个 Box 中，
                    // 使 Material 3 DropdownMenu 的 Popup 锚定到这个 Box（即 IconButton）位置。
                    // 若 DropdownMenu 渲染在 TopBar 之外（如 HomeScreen 内容区），
                    // 它会锚定到外层 Box 的左下角，造成位置错误。
                    Box {
                        IconButton(onClick = { onMoreClick?.invoke() }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多功能",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        dropdownContent?.invoke()
                    }
                }
            }
        }
    }
}
