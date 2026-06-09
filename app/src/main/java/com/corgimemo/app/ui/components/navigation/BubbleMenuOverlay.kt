package com.corgimemo.app.ui.components.navigation

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import android.content.Context
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 气泡菜单覆盖层组件
 * 显示半透明遮罩和三个扇形排列的气泡按钮
 *
 * @param isExpanded 是否展开
 * @param isFastCollapse 是否快速收起模式（设计规范11.2.4：切换页面时100ms）
 * @param onDismiss 关闭回调（点击遮罩或 ✕ 按钮）
 * @param onBubbleClick 气泡点击回调
 * @param navBarHeight 底部导航栏总高度（含安全区域，用于动态留白）
 * @param context Android 上下文（用于触觉反馈）
 * @param hapticEnabled 是否启用触觉反馈
 * @param modifier 修饰符
 */
@Composable
fun BubbleMenuOverlay(
    isExpanded: Boolean,
    isFastCollapse: Boolean = false,
    onDismiss: () -> Unit,
    onBubbleClick: (BubbleType) -> Unit,
    navBarHeight: Dp = 120.dp,
    context: Context,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 半透明遮罩层（带区域排除的点击检测）
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f))
            // 点击遮罩关闭气泡菜单，但排除底部导航栏区域
            // 设计意图：用户点击导航栏区域时不应关闭气泡菜单
            // 因为导航栏包含 ⊕/✕ 按钮和 Tab 切换按钮，
            // 这些操作应独立于遮罩的关闭行为
            // 实现方式：使用 pointerInput + detectTapGestures 获取点击坐标，
            // 仅当点击位置在导航栏区域上方时才触发 onDismiss
            .pointerInput(navBarHeight) {
                detectTapGestures { offset ->
                    // 获取屏幕高度用于计算导航栏区域的边界
                    val screenHeight = this.size.height.toFloat()
                    // 导航栏区域的高度阈值（dp 转 px）
                    val navBarThresholdPx = navBarHeight.toPx()

                    // 判断点击位置是否在导航栏区域之外
                    // 条件：点击 y 坐标 < 屏幕高度 - 导航栏高度
                    // 即：点击位置在导航栏上方 → 关闭气泡菜单
                    //     点击位置在导航栏内   → 不响应（穿透给下层）
                    if (offset.y < screenHeight - navBarThresholdPx) {
                        onDismiss()
                    }
                }
            }
    ) {
        /**
         * 气泡弧形容器
         *
         * 布局设计：三个气泡从中央 ⊕/✕ 按钮位置向上呈扇形（弧形）展开
         * - 待办：正上方（弧顶，最远）
         * - 灵感：左上方（弧左侧）
         * - 日期：右上方（弧右侧）
         *
         * 使用 Box + offset 实现绝对定位，每个气泡独立计算偏移量
         * 弧形参数：
         *   - 半径(R): 气泡到中央按钮的距离 ≈ 100dp
         *   - 待办角度: -90° (正上方)
         *   - 灵感角度: -155° (左上方)
         *   - 日期角度: -25° (右上方)
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarHeight)
        ) {
            // 弧形半径：气泡距离中央按钮的展开距离（贴近导航栏，单手可及）
            val arcRadius = 20.dp

            // ══════════════════════════════════════
            // 气泡1: 创建待办 — 正上方（弧顶，角度 -90°）
            // 位置：水平居中，垂直向上 arcRadius
            // ══════════════════════════════════════
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-arcRadius))
            ) {
                AnimatedBubble(
                    isVisible = isExpanded,
                    isFastCollapse = isFastCollapse,
                    delayMillis = 0,
                    collapseDelayMillis = 100,
                    context = context,
                    hapticEnabled = hapticEnabled
                ) {
                    BubbleItem(
                        icon = "📝",
                        text = "待办",
                        onClick = { onBubbleClick(BubbleType.CREATE_TODO) }
                    )
                }
            }

            // ══════════════════════════════════════
            // 气泡2: 记录灵感 — 左上方（弧左侧，角度约 -150°）
            // 位置：按比例缩小偏移量，保持弧形张角一致
            // ══════════════════════════════════════
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = (-80).dp, y = (-5).dp)
            ) {
                AnimatedBubble(
                    isVisible = isExpanded,
                    isFastCollapse = isFastCollapse,
                    delayMillis = 50,
                    collapseDelayMillis = 50,
                    context = context,
                    hapticEnabled = hapticEnabled
                ) {
                    BubbleItem(
                        icon = "💡",
                        text = "灵感",
                        onClick = { onBubbleClick(BubbleType.RECORD_INSPIRE) }
                    )
                }
            }

            // ══════════════════════════════════════
            // 气泡3: 特殊日期 — 右上方（弧右侧，角度约 -30°）
            // 位置：按比例缩小偏移量，保持弧形张角一致
            // ══════════════════════════════════════
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 80.dp, y = (-5).dp)
            ) {
                AnimatedBubble(
                    isVisible = isExpanded,
                    isFastCollapse = isFastCollapse,
                    delayMillis = 100,
                    collapseDelayMillis = 0,
                    context = context,
                    hapticEnabled = hapticEnabled
                ) {
                    BubbleItem(
                        icon = "📅",
                        text = "日期",
                        onClick = { onBubbleClick(BubbleType.SPECIAL_DATE) }
                    )
                }
            }

            // 中央锚点标记（不可见，仅用于定位参考）
            // 对应导航栏中 ⊕/✕ 按钮的位置
            // 所有气泡的弧形展开均以此为原点
            Spacer(modifier = Modifier.size(56.dp))
        }
    }
}

/**
 * 动画气泡组件
 * 控制单个气泡的弹出/收起动画效果
 * 使用 GPU 加速的 graphicsLayer 确保流畅性能
 * 弹性缓动曲线模拟"从按钮弹出"的自然感
 *
 * @param isVisible 是否可见（控制展开/收起）
 * @param isFastCollapse 是否快速收起模式（设计规范11.2.4：切换页面时100ms）
 * @param delayMillis 展开延迟时间（毫秒），用于错开多个气泡的弹出时机
 * @param collapseDelayMillis 收起延迟时间（毫秒），用于错开多个气泡的收起时机（反向顺序）
 * @param context Android 上下文（用于触觉反馈）
 * @param hapticEnabled 是否启用触觉反馈
 * @param content 气泡内容
 */
@Composable
private fun AnimatedBubble(
    isVisible: Boolean,
    isFastCollapse: Boolean = false,
    delayMillis: Long,
    collapseDelayMillis: Long = 0,
    context: Context,
    hapticEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    // 性能优化：使用 Animatable 实现更精细的动画控制
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            // 展开动画：先等待延迟时间，再执行弹性弹出
            delay(delayMillis)
            // 触觉反馈：气泡弹出时触发轻微震动（增强"弹出"操作感知）
            // 使用项目自建的 HapticFeedbackManager 替代 Compose LocalHapticFeedback（避免版本兼容问题）
            HapticFeedbackManager.performHapticFeedback(
                context = context,
                type = InteractionType.TEXT_MOVE,
                enabled = hapticEnabled
            )
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 350,  // 弹出时长350ms（略长以展现弹性）
                    easing = FastOutSlowInEasing  // 弹性缓动：先快后慢带微回弹
                )
            )
        } else {
            // 收起动画：按反向顺序依次收起
            delay(collapseDelayMillis)
            val collapseDuration = if (isFastCollapse) 100 else 150
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = collapseDuration,
                    easing = EaseInCubic
                )
            )
        }
    }

    // GPU 加速渲染：缩放 + 透明度 + 向上位移（沿弧线路径感）
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = animatable.value
            scaleY = animatable.value
            alpha = animatable.value
            translationY = (1f - animatable.value) * 16f  // 向上位移，增强"从中心弹出"感
        }
    ) {
        content()
    }
}

/**
 * 气泡项 UI 组件（圆环形）
 * 显示单个可点击的圆形气泡按钮
 * 图标在上、文字在下，垂直居中排列
 * 包含点击反馈动画：1.0 → 1.2 → 0.8（设计规范11.2.4）
 *
 * @param icon 图标（emoji）
 * @param text 标签文字
 * @param onClick 点击回调
 */
@Composable
private fun BubbleItem(
    icon: String,
    text: String,
    onClick: () -> Unit
) {
    // 点击缩放动画状态
    val scaleAnimatable = remember { Animatable(1f) }
    var hasClicked by remember { mutableStateOf(false) }

    // 点击触发动画序列：1.0 → 1.2 → 0.8
    LaunchedEffect(hasClicked) {
        if (hasClicked) {
            scaleAnimatable.animateTo(
                targetValue = 1.2f,
                animationSpec = tween(50, easing = EaseOutCubic)
            )
            scaleAnimatable.animateTo(
                targetValue = 0.8f,
                animationSpec = tween(100, easing = EaseInCubic)
            )
            // 执行跳转回调
            onClick()
            hasClicked = false
        }
    }

    Surface(
        shape = CircleShape,  // 圆环形按钮
        border = androidx.compose.foundation.BorderStroke(
            width = 2.5.dp,
            color = androidx.compose.ui.graphics.Color(0xFFFF9A5C)
        ),
        color = androidx.compose.ui.graphics.Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(56.dp)  // 固定圆形尺寸
            .semantics {
                contentDescription = "创建${text}"
                role = Role.Button
            }
            .clickable {
                if (!hasClicked) {
                    hasClicked = true
                }
            }
            .graphicsLayer {
                scaleX = scaleAnimatable.value
                scaleY = scaleAnimatable.value
            }
    ) {
        // 垂直居中布局：图标 + 文字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 22.sp,
                lineHeight = 24.sp
            )
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF555555)
            )
        }
    }
}
