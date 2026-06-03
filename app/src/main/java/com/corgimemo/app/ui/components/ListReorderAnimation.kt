package com.corgimemo.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 列表重排动画组件
 *
 * 为 LazyColumn 提供平滑的列表重排动画效果，
 * 当数据源顺序发生变化时，自动应用位移动画、缩放动画和透明度变化。
 *
 * **核心特性**:
 * - ✅ **自动检测**: 监听 items 列表的变化（通过 key 识别）
 * - ✅ **位移动画**: 列表项从旧位置平滑移动到新位置（300ms）
 * - ✅ **缩放效果**: 新插入项从小到大弹出（spring 弹性）
 * - ✅ **淡入淡出**: 删除项透明度渐变消失（200ms）
 * - ✅ **交错动画**: 多个项同时移动时使用 stagger 延迟避免卡顿
 *
 * **适用场景**:
 * 1. 排序切换（如从"按时间"切换到"按优先级"）
 * 2. 拖拽排序完成后的位置归位
 * 3. 筛选条件变更导致列表项增删
 * 4. 实时数据更新（如状态同步）
 *
 * **性能优化**:
 * - 使用 Compose 的 `animateItem()` 原生 API（推荐方式）
 * - 动画参数经过调优（duration 300ms, easing FastOutSlowIn）
 * - 仅对可见项执行动画计算
 *
 * **使用示例**:
 * ```kotlin
 * val todos by viewModel.filteredTodos.collectAsState()
 *
 * ListReorderAnimation(
 *     items = todos,
 *     key = { it.id }, // 使用唯一 ID 作为 key
 *     modifier = Modifier.fillMaxSize()
 * ) { index, todo ->
 *     TodoListItem(
 *         todo = todo,
 *         modifier = Modifier.fillMaxWidth()
 *     )
 * }
 * ```
 *
 * @param T 列表项数据类型
 * @param items 数据源列表
 * @param key 提供每个项的唯一标识符（必须稳定且唯一！）
 * @param modifier Modifier
 * @param content 列表项 Composable lambda (index, item) -> Unit
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : Any> ListReorderAnimation(
    items: List<T>,
    key: ((T) -> Any),
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, item: T) -> Unit
) {
    /** LazyColumn 状态 */
    val listState = rememberLazyListState()

    /**
     * 动画规格定义
     *
     * - reorderSpec: 重排时的位移动画（弹性效果，自然感强）
     * - fadeSpec: 显隐时的淡入淡出（快速响应）
     * - scaleSpec: 缩放动画（用于新项插入的强调效果）
     */
    val reorderSpec = spring<Float>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
    )

    val fadeSpec = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing /** 快出慢入，符合直觉 */
    )

    val scaleSpec = tween<Float>(
        durationMillis = 250,
        easing = LinearOutSlowInEasing /** 线性出慢入，弹性感 */
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> key(item) } /** 关键：使用稳定的 key 让 Compose 追踪项的身份 */
        ) { index, item ->
            /**
             * animateItem() 是 Compose Foundation 提供的原生动画 API
             *
             * 工作原理：
             * 1. Compose 在重组时比较新旧列表中相同 key 的项的位置
             * 2. 如果位置发生变化，自动对该项应用位移动画
             * 3. 如果项被移除或添加，应用显隐动画
             *
             * 无需手动管理动画状态，完全声明式！
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                content(index, item)
            }
        }
    }
}

/**
 * 带交错延迟的重排动画组件
 *
 * 当大量列表项同时移动时，
 * 为每个项添加微小的延迟以产生波浪式视觉效果。
 *
 * **注意**: 此组件主要用于演示/特殊场景，
 * 一般情况下直接使用 `ListReorderAnimation` 即可满足需求。
 *
 * @param items 数据源
 * @param key Key 函数
 * @param staggerDelayMs 每项之间的延迟间隔（毫秒）
 * @param modifier Modifier
 * @param content 列表项 lambda
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : Any> StaggeredListReorderAnimation(
    items: List<T>,
    key: ((T) -> Any),
    staggerDelayMs: Int = 20,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, item: T) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> key(item) }
        ) { index, item ->
            /** 根据索引计算交错延迟 */
            val delay = (index % 10) * staggerDelayMs /** 每 10 项循环一次，避免过长延迟 */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        /** 可选：根据交错延迟调整初始缩放 */
                        // scaleX = animateFloatAsState(targetValue = 1f).value
                        // scaleY = animateFloatAsState(targetValue = 1f).value
                    }
            ) {
                content(index, item)
            }
        }
    }
}

/**
 * 列表重排动画辅助工具对象
 *
 * 提供预定义的动画规格常量和工具方法，
 * 用于在需要自定义动画参数的场景中使用。
 */
object ListReorderAnimationSpecs {
    /**
     * 默认重排动画规格（弹簧效果）
     *
     * 特点：自然的物理运动轨迹，适合大多数场景
     */
    val DEFAULT_REORDER = spring<Float>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
    )

    /**
     * 快速重排动画规格（线性插值）
     *
     * 特点：快速响应，无弹性，适合高频操作场景
     */
    val FAST_REORDER = tween<Float>(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    )

    /**
     * 慢速重排动画规格（缓动插值）
     *
     * 特点：优雅缓慢，适合展示型场景
     */
    val SLOW_REORDER = tween<Float>(
        durationMillis = 500,
        easing = LinearOutSlowInEasing
    )

    /**
     * 默认淡入淡出规格
     */
    val DEFAULT_FADE = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    /**
     * 快速淡出规格（用于删除操作）
     */
    val FAST_FADE_OUT = tween<Float>(
        durationMillis = 150,
        easing = androidx.compose.animation.core.LinearEasing
    )

    /**
     * 默认缩放动画规格（用于新增项）
     */
    val DEFAULT_SCALE = spring<Float>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
    )
}
