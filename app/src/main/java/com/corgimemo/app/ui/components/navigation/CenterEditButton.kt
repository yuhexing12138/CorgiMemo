package com.corgimemo.app.ui.components.navigation

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tab 项枚举
 * 定义底部导航栏的所有可选项
 */
enum class TabItem {
    TODO,      // 待办
    INSPIRE,   // 灵感
    EDIT,      // 编辑（中央按钮，非真实 Tab）
    DATE,      // 日期
    PROFILE    // 我的
}

/**
 * TabItem 的 Saver（配合 rememberSaveable 跨导航/跨配置变更持久化状态）
 *
 * 解决：用户从 PROFILE/INSPIRE/DATE tab 跳转到子页面再返回时，selectedTab 被重置的 bug。
 * 实现：把 enum 转成 String 写入 Bundle，restore 时用 valueOf 反序列化。
 * 兜底：name 解析失败时回退到 TabItem.TODO（兼容首次进入 / Bundle 缺失 / enum 值被移除场景）。
 */
internal val TabItemSaver: Saver<TabItem, String> = Saver(
    save = { it.name },
    restore = { name ->
        runCatching { TabItem.valueOf(name) }.getOrDefault(TabItem.TODO)
    }
)

/**
 * 气泡类型枚举
 * 定义中央编辑按钮展开的三个气泡选项
 */
enum class BubbleType {
    CREATE_TODO,    // 创建待办
    RECORD_INSPIRE, // 记录灵感
    SPECIAL_DATE    // 特殊日期
}

/**
 * 中央编辑按钮组件
 * 支持展开/收起状态切换，带旋转动画
 *
 * @param isExpanded 是否展开（显示 ✕）
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun CenterEditButton(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 旋转动画：0° (⊕) → 45° (✕)
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = tween(
            durationMillis = 200,
            easing = EaseInOutCubic
        ),
        label = "centerButtonRotation"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .shadow(
                elevation = 6.dp,  // 设计规范11.2.3：中央按钮elevation 6dp
                shape = CircleShape,
                ambientColor = Color(0xFFFF9A5C).copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(Color(0xFFFF9A5C))
            .semantics {
                contentDescription = if (isExpanded) "关闭编辑菜单" else "打开编辑菜单"
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .graphicsLayer {
                rotationZ = rotation
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⊕",
            fontSize = 28.sp,
            color = Color.White
        )
    }
}
