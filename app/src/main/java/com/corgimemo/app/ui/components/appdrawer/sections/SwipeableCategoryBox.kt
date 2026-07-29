package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.corgimemo.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 弹性缓动函数（与 SwipeableTodoBox 一致，对应 Web 原型 cubic-bezier(0.34, 1.56, 0.64, 1)）
 *
 * 用于分组右滑展开/收起动画，确保与首页待办卡片左滑"滑出效果一致"。
 */
internal val CategoryElasticOutEasing: Easing = Easing { fraction ->
    val c1 = 1.56f
    val c3 = c1 + 1f
    1f + c3 * Math.pow(fraction - 1.0, 3.0).toFloat() +
        c1 * Math.pow(fraction - 1.0, 2.0).toFloat()
}

/**
 * 分组右滑按钮配置（仅图标，无文字）
 *
 * 与 [com.corgimemo.app.ui.components.SwipeButtonConfig] 区别：
 * - 无 `label` 字段（分组右滑按钮只显示图标）
 * - `internal` 可见性（仅 appdrawer 内部使用）
 *
 * @param backgroundColorRes 背景色资源 ID
 * @param icon Material 图标
 * @param zIndex z-index 值（从左到右递减：置顶=3, 编辑=2, 删除=1）
 * @param shape 按钮的圆角形状
 * @param actionType 按钮的语义动作
 */
internal data class CategorySwipeButtonConfig(
    val backgroundColorRes: Int,
    val icon: ImageVector,
    val zIndex: Float,
    val shape: RoundedCornerShape,
    val actionType: CategorySwipeActionType
)

/**
 * 分组右滑按钮的语义动作类型
 *
 * - PIN：置顶/取消置顶
 * - EDIT：编辑分组（触发 RenameCategoryDialog）
 * - DELETE：删除分组（触发 DeleteCategoryConfirmDialog）
 */
internal enum class CategorySwipeActionType { PIN, EDIT, DELETE }

/**
 * 分组右滑默认按钮配置（置顶 → 编辑 → 删除，从左到右）
 *
 * 与 [com.corgimemo.app.ui.components.defaultTodoButtons] 区别：
 * - 滑动方向：右滑（按钮在卡片左侧）
 * - 按钮顺序：置顶 → 编辑 → 删除（左到右）
 * - 按钮内容：仅图标，无文字
 * - 圆角方向：最左侧置顶按钮有左圆角（topStart/bottomStart）
 *
 * @param isPinned 当前是否已置顶（决定置顶按钮图标）
 * @param cornerRadiusDp 圆角 dp
 */
internal fun categoryDefaultButtons(
    isPinned: Boolean,
    cornerRadiusDp: androidx.compose.ui.unit.Dp = 16.dp
): List<CategorySwipeButtonConfig> = listOf(
    // 1. 置顶（最左侧，左圆角）
    CategorySwipeButtonConfig(
        backgroundColorRes = R.color.ui_primary,
        icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
        zIndex = 3f,
        shape = RoundedCornerShape(topStart = cornerRadiusDp, bottomStart = cornerRadiusDp),
        actionType = CategorySwipeActionType.PIN
    ),
    // 2. 编辑（中间，无圆角）
    CategorySwipeButtonConfig(
        backgroundColorRes = R.color.ui_swipe_edit,
        icon = Icons.Filled.Edit,
        zIndex = 2f,
        shape = RoundedCornerShape(0.dp),
        actionType = CategorySwipeActionType.EDIT
    ),
    // 3. 删除（最右侧，无圆角；卡片右滑后此按钮紧贴卡片左边缘）
    CategorySwipeButtonConfig(
        backgroundColorRes = R.color.ui_swipe_delete,
        icon = Icons.Outlined.Delete,
        zIndex = 1f,
        shape = RoundedCornerShape(0.dp),
        actionType = CategorySwipeActionType.DELETE
    )
)

/**
 * 可右滑展开操作区的分组容器组件
 *
 * **设计参考**：[com.corgimemo.app.ui.components.SwipeableTodoBox]（首页待办卡片左滑）
 *
 * **与 SwipeableTodoBox 的核心差异**：
 * 1. **滑动方向**：右滑（手指从左向右滑），卡片向右移动，按钮从左侧露出
 * 2. **按钮内容**：仅图标，无文字（按用户需求）
 * 3. **按钮顺序**：置顶 → 编辑 → 删除（从左到右）
 * 4. **简化项**：去除卡片缩放动画（pressFeedback）、速度跟踪（fling）、
 *    级联堆叠动画（staggerRatio=0），保留核心的弹性吸附 + 阈值判定
 * 5. **互斥展开**：由外部 `isExpanded` 控制，同一时间仅一个分组展开
 *
 * **动画参数**（与 SwipeableTodoBox 一致，确保"滑出效果一致"）：
 * - duration = 300ms
 * - thresholdRatio = 0.20（露出 20% 即吸附展开）
 * - easing = ElasticOutEasing（弹性回弹）
 *
 * **拖拽共存**：当 `isEnabled = false` 时禁用右滑手势，让长按拖拽排序独占手势。
 * 调用方在分组展开时设置 `isEnabled = false` 即可避免手势冲突。
 *
 * @param modifier 修饰符
 * @param isEnabled 是否启用右滑（展开时或拖拽排序激活时设为 false）
 * @param isExpanded 是否处于展开状态（父组件控制互斥）
 * @param isPinned 当前分组是否已置顶（用于动态切换置顶按钮图标）
 * @param onExpandChange 展开状态变化回调（true=展开, false=收起）
 * @param onPinClick 置顶按钮回调
 * @param onEditClick 编辑按钮回调
 * @param onDeleteClick 删除按钮回调
 * @param content 分组内容（通常是 CategoryItem）
 */
@Composable
internal fun SwipeableCategoryBox(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isExpanded: Boolean = false,
    isPinned: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    onPinClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // 几何参数（与 SwipeableTodoBox 一致：3 个按钮，每个 72dp）
    val buttonWidthDp = 72.dp
    val actionsWidthDp = buttonWidthDp * 3 // 216dp
    val buttonWidthPx = with(density) { buttonWidthDp.toPx() }
    val actionsWidthPx = with(density) { actionsWidthDp.toPx() }
    val thresholdRatio = 0.20f
    val thresholdPx = actionsWidthPx * thresholdRatio
    val cornerRadiusDp = 16.dp
    val durationMs = 300

    // 卡片位移状态（px，范围 0..actionsWidthPx，正数表示右移）
    val cardOffsetX = remember { Animatable(0f) }

    // 恢复动画协程引用：防止 drag / onDragEnd 重复启动新协程
    val restoreJob = remember { mutableStateOf<Job?>(null) }

    // 按钮配置（根据 isPinned 切换置顶图标）
    val buttons = remember(isPinned) {
        categoryDefaultButtons(isPinned, cornerRadiusDp)
    }

    // 父组件强制收起时同步动画
    LaunchedEffect(isExpanded, isEnabled) {
        if (!isExpanded && cardOffsetX.value > 0f) {
            cardOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMs, easing = CategoryElasticOutEasing)
            )
        }
    }

    // revealProgress 连续函数（与 SwipeableTodoBox 对齐）
    val revealPx = cardOffsetX.value.coerceIn(0f, actionsWidthPx)
    val revealProgress = if (actionsWidthPx > 0f) revealPx / actionsWidthPx else 0f

    // 按钮点击后收起的公共逻辑
    val onButtonClicked: () -> Unit = {
        coroutineScope.launch {
            cardOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMs, easing = CategoryElasticOutEasing)
            )
            onExpandChange(false)
        }
    }

    // 双层叠加 Layout：内容层(z=10) + 操作层(z=1)
    Layout(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadiusDp))
            // 在外层拦截 down 事件，阻止父级 ModalNavigationDrawer 看到 down 后启动 Drawer 打开手势
            // 仅在卡片展开期间激活，避免影响其他区域
            .pointerInput(isEnabled, isExpanded) {
                if (!isEnabled || !isExpanded) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 智能分流：down 落在按钮区域则放行，让事件透传给按钮 clickable
                    // 按钮在左侧，buttonAreaEndX = actionsWidthPx
                    val isButtonArea = down.position.x <= actionsWidthPx
                    if (!isButtonArea) {
                        down.consume()
                    }
                }
            },
        content = {
            // === 内容层（measurables[0]）===
            Box(
                modifier = Modifier
                    .offset { IntOffset(cardOffsetX.value.roundToInt(), 0) }
                    .pointerInput(isEnabled, isExpanded) {
                        if (!isEnabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = {
                                // 取消正在跑的恢复动画，避免新 snapTo 与旧 animateTo 争抢
                                restoreJob.value?.cancel()
                                restoreJob.value = null
                            },
                            onDragEnd = {
                                if (restoreJob.value != null) {
                                    coroutineScope.launch { restoreJob.value?.join() }
                                } else {
                                    // 普通抬手：按阈值吸附
                                    // 右滑语义：右滑超过阈值则展开，否则收起
                                    val currentReveal = cardOffsetX.value
                                    val target = if (currentReveal >= thresholdPx) {
                                        actionsWidthPx
                                    } else {
                                        0f
                                    }
                                    restoreJob.value = coroutineScope.launch {
                                        cardOffsetX.animateTo(
                                            targetValue = target,
                                            animationSpec = tween(
                                                durationMillis = durationMs,
                                                easing = CategoryElasticOutEasing
                                            )
                                        )
                                        onExpandChange(target > 0f)
                                        restoreJob.value = null
                                    }
                                }
                            },
                            onDragCancel = {
                                if (restoreJob.value != null) {
                                    coroutineScope.launch { restoreJob.value?.join() }
                                } else {
                                    val currentReveal = cardOffsetX.value
                                    val target = if (currentReveal >= thresholdPx) {
                                        actionsWidthPx
                                    } else {
                                        0f
                                    }
                                    restoreJob.value = coroutineScope.launch {
                                        cardOffsetX.animateTo(
                                            targetValue = target,
                                            animationSpec = tween(
                                                durationMillis = durationMs,
                                                easing = CategoryElasticOutEasing
                                            )
                                        )
                                        onExpandChange(target > 0f)
                                        restoreJob.value = null
                                    }
                                }
                            }
                        ) { _, dragAmount ->
                            // 右滑：dragAmount > 0 时卡片右移；左滑：dragAmount < 0 时卡片左移（收起）
                            val newOffset = (cardOffsetX.value + dragAmount)
                                .coerceIn(0f, actionsWidthPx)
                            coroutineScope.launch {
                                cardOffsetX.snapTo(newOffset)
                            }
                        }
                    }
                    .zIndex(10f)
            ) {
                content()
            }

            // === 操作层（measurables[1..3]）===
            // 按钮在卡片左侧，从左到右：置顶 → 编辑 → 删除
            // 按钮总是渲染：未展开时被卡片覆盖（不可见），展开时从卡片下露出
            // isEnabled 仅控制手势检测，不影响按钮渲染
            buttons.forEachIndexed { index, btnConfig ->
                // staggerRatio = 0：所有按钮同步移动（与 SwipeableTodoBox 默认一致）
                val localProgress = revealProgress.coerceIn(0f, 1f)
                // 偏移量：初始堆叠在最左侧（紧贴卡片左边缘）→ 终态回到原始位置
                // 与 SwipeableTodoBox 对称：右滑时按钮从左侧"堆叠态"展开到各自位置
                val offset = index * buttonWidthPx
                val translateX = offset * (1f - localProgress)
                val alpha = if (revealPx > 0f) 1f else 0f

                val clickAction: () -> Unit = when (btnConfig.actionType) {
                    CategorySwipeActionType.PIN -> {
                        { onPinClick(); onButtonClicked() }
                    }
                    CategorySwipeActionType.EDIT -> {
                        { onEditClick(); onButtonClicked() }
                    }
                    CategorySwipeActionType.DELETE -> {
                        { onDeleteClick(); onButtonClicked() }
                    }
                }

                CategorySwipeActionButton(
                    config = btnConfig,
                    translateX = translateX,
                    alpha = alpha,
                    onClick = clickAction,
                    modifier = Modifier.zIndex(btnConfig.zIndex)
                )
            }
        },
        measurePolicy = { measurables, constraints ->
            val contentPlaceable = measurables[0].measure(constraints)
            val cardWidth = contentPlaceable.width
            val cardHeight = contentPlaceable.height
            val singleButtonWidthPx = with(density) { buttonWidthDp.roundToPx() }

            // 分别测量三个按钮，每个按钮强制固定宽度=72dp、高度=卡片实际高度
            // 按钮总是测量：未展开时被卡片覆盖（不可见），展开时露出
            val actionPlaceables = List(buttons.size) { i ->
                measurables[1 + i].measure(
                    Constraints.fixed(
                        width = singleButtonWidthPx,
                        height = cardHeight
                    )
                )
            }

            layout(cardWidth, cardHeight) {
                // 按钮在卡片左侧：从左到右依次放置（置顶在最左 x=0，删除 x=2*buttonWidth）
                // 卡片初始位置 x=0 覆盖按钮；卡片右移后按钮从左侧露出
                actionPlaceables.forEachIndexed { index, placeable ->
                    val x = index * singleButtonWidthPx
                    placeable.placeRelative(x = x, y = 0)
                }
                // 内容卡片放在最上层（覆盖按钮）
                contentPlaceable.placeRelative(0, 0)
            }
        }
    )
}

/**
 * 单个右滑操作按钮（仅图标，无文字）
 *
 * 与 [com.corgimemo.app.ui.components.SwipeableTodoBox] 中的 SwipeActionButton 区别：
 * - 无 Text 组件（按用户需求"只需要图标，不需要文字"）
 * - 图标尺寸 24dp，居中
 *
 * @param config 按钮配置（包含背景色、图标、圆角形状等）
 * @param translateX 横向偏移量（级联堆叠动画）
 * @param alpha 透明度（二元化）
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun CategorySwipeActionButton(
    config: CategorySwipeButtonConfig,
    translateX: Float,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = colorResource(id = config.backgroundColorRes)

    Box(
        modifier = modifier
            .graphicsLayer {
                this.translationX = translateX
                this.alpha = alpha
            }
            .background(backgroundColor, shape = config.shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = config.icon,
            contentDescription = null, // 仅图标，无文字描述
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
