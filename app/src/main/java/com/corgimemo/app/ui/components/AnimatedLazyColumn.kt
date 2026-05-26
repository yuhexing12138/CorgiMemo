package com.corgimemo.app.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 带有进入动画的 LazyColumn items 扩展函数
 *
 * 为每个列表项添加渐入+上移动画效果，
 * 每个项目依次延迟 50ms，营造错落进入的视觉效果。
 *
 * @param T 数据类型
 * @param items 数据列表
 * @param key 提供唯一键的 lambda（可选）
 * @param itemContent 项目内容 Composable
 */
fun <T : Any> LazyListScope.animatedItems(
    items: List<T>,
    key: ((T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(item: T, index: Int) -> Unit
) {
    items(
        count = items.size,
        key = if (key != null) { index -> key(items[index]) } else null
    ) { index ->
        val animationProgress by remember {
            mutableFloatStateOf(0f)
        }
        
        val alpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 200,
                delayMillis = (index * 50).coerceAtMost(500)
            ),
            label = "fadeIn"
        )
        
        val offsetY by androidx.compose.animation.core.animateFloatAsState(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 200,
                delayMillis = (index * 50).coerceAtMost(500)
            ),
            label = "slideIn"
        )

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    translationY = offsetY * 20f // 从下方 20dp 处滑入（近似值）
                }
        ) {
            itemContent(items[index], index)
        }
    }
}
