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
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.draw.clipToBounds
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
 * @param shape 按钮的形状（分组条无圆角，统一使用 RectangleShape）
 * @param actionType 按钮的语义动作
 */
internal data class CategorySwipeButtonConfig(
    val backgroundColorRes: Int,
    val icon: ImageVector,
    val zIndex: Float,
    val shape: androidx.compose.ui.graphics.Shape,
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
 * - 圆角：分组条无圆角，按钮也不需要圆角（与首页待办卡片左滑有圆角不同）
 * - zIndex：与 SwipeableTodoBox 镜像对称
 *   - SwipeableTodoBox：堆叠在删除位置（最右），分享(offset最大)zIndex最高
 *   - SwipeableCategoryBox：堆叠在置顶位置（最左），删除(offset绝对值最大)zIndex最高
 *   原因：offset最大的按钮最先离开堆叠位置，zIndex最高确保展开时露出下层按钮
 *
 * @param isPinned 当前是否已置顶（决定置顶按钮图标）
 */
internal fun categoryDefaultButtons(
    isPinned: Boolean
): List<CategorySwipeButtonConfig> = listOf(
    // 1. 置顶（最左侧，堆叠位置，offset=0，不移动）
    //    zIndex 最低（1f）：被上层按钮覆盖，展开后最后露出
    CategorySwipeButtonConfig(
        backgroundColorRes = R.color.ui_primary,
        icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
        zIndex = 1f,
        shape = RectangleShape,
        actionType = CategorySwipeActionType.PIN
    ),
    // 2. 编辑（中间，offset=-1*buttonWidth）
    //    zIndex 中等（2f）
    CategorySwipeButtonConfig(
        backgroundColorRes = R.color.ui_swipe_edit,
        icon = Icons.Filled.Edit,
        zIndex = 2f,
        shape = RectangleShape,
        actionType = CategorySwipeActionType.EDIT
    ),
    // 3. 删除（最右侧，贴近卡片左边缘，offset=-2*buttonWidth，移动最快）
    //    zIndex 最高（3f）：在堆叠位置时最上层，展开时最先离开，露出下层
    CategorySwipeButtonConfig(
        backgroundColorRes = R.color.ui_swipe_delete,
        icon = Icons.Outlined.Delete,
        zIndex = 3f,
        shape = RectangleShape,
        actionType = CategorySwipeActionType.DELETE
    )
)

/**
 * 可右滑展开操作区的分组容器组件（飞书风格级联重叠堆叠动效）
 *
 * **设计参考**：[com.corgimemo.app.ui.components.SwipeableTodoBox]（首页待办卡片左滑）
 *
 * **与 SwipeableTodoBox 的核心差异**：
 * 1. **滑动方向**：右滑（手指从左向右滑），卡片向右移动，按钮从左侧露出
 * 2. **按钮内容**：仅图标，无文字（按用户需求）
 * 3. **按钮顺序**：置顶 → 编辑 → 删除（从左到右）
 * 4. **级联效果**：与 SwipeableTodoBox 一致的堆叠展开动效
 *    - 所有按钮初始堆叠在删除位置（最贴近卡片左边缘）
 *    - 右滑展开时依次展开到各自原位
 *    - offset = (buttons.size - 1 - index) * buttonWidthPx
 * 5. **互斥展开**：由外部 `isExpanded` 控制，同一时间仅一个分组展开
 * 6. **简化项**：去除卡片缩放动画（pressFeedback）、速度跟踪（fling）
 *
 * **动画参数**（与 SwipeableTodoBox 一致，确保"滑出效果一致"）：
 * - duration = 300ms
 * - staggerRatio = 0.00（同步移动，与 SwipeableTodoBox 默认一致）
 * - thresholdRatio = 0.20（露出 20% 即吸附展开）
 * - easing = ElasticOutEasing（弹性回弹）
 *
 * **拖拽共存**：当 `isEnabled = false` 时禁用右滑手势，让长按拖拽排序独占手势。
 * 调用方在分组展开时设置 `isEnabled = false` 即可避免手势冲突。
 *
 * **互斥恢复机制**（v2026-07-29 新增）：
 * - `onExpandStart` 在首次右滑时被调用，用于通知父组件清除其他展开的分组
 * - 与 `onExpandChange(true)` 区别：不改变当前组件的 isExpanded，避免 pointerInput 重启
 * - 父组件在 onExpandStart 中设置 swipeExpandedCategoryId = null（不设为当前ID），
 *   上一个分组的 isExpanded 变 false 后通过 LaunchedEffect 开始归位
 * - 当前组件的 isExpanded 保持 false 直到 onDragEnd 阈值判定后才通过 onExpandChange(true) 设置
 *
 * @param modifier 修饰符
 * @param isEnabled 是否启用右滑（展开时或拖拽排序激活时设为 false）
 * @param isExpanded 是否处于展开状态（父组件控制互斥）
 * @param isPinned 当前分组是否已置顶（用于动态切换置顶按钮图标）
 * @param onExpandChange 展开状态变化回调（true=展开, false=收起，仅在 onDragEnd/buttonClick 时调用）
 * @param onExpandStart 开始展开回调（首次右滑时调用，用于通知父组件清除其他展开的分组）
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
    onExpandStart: () -> Unit = {},
    onPinClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    /**
     * 级联延迟比例（与 SwipeableTodoBox 一致，默认 0.00）
     * - 0.00：所有按钮同步移动
     * - >0：按钮依次展开（飞书风格级联）
     */
    staggerRatio: Float = 0.00f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // 几何参数
    // - 单按钮宽度 56dp（Material 3 列表项标准高度）：
    //   24dp 图标 + 16dp × 2 左右留白 = 56dp，满足 WCAG 移动端触摸目标 ≥ 48dp
    // - 3 个按钮总宽 168dp（v2026-07-30 从 216dp 缩窄 22%，更紧凑更"现代化"）
    // - 与 SwipeableTodoBox 保持区别：待办左滑 72dp（主要操作 / 完整图标+文字），
    //   分组右滑 56dp（次要操作 / 仅图标），体现层级差异
    val buttonWidthDp = 56.dp
    val actionsWidthDp = buttonWidthDp * 3 // 168dp
    val buttonWidthPx = with(density) { buttonWidthDp.toPx() }
    val actionsWidthPx = with(density) { actionsWidthDp.toPx() }
    val thresholdRatio = 0.20f
    val thresholdPx = actionsWidthPx * thresholdRatio
    val durationMs = 300

    // 卡片位移状态（px，范围 0..actionsWidthPx，正数表示右移）
    val cardOffsetX = remember { Animatable(0f) }

    // 恢复动画协程引用：防止 drag / onDragEnd 重复启动新协程
    val restoreJob = remember { mutableStateOf<Job?>(null) }

    // 左滑意图跟踪：onDrag 中任何 dragAmount < 0 都标记为左滑意图，
    // 用于 onDragEnd 判断是否需要"抬手总关闭"（即使慢速左滑也关闭）
    // 镜像 SwipeableTodoBox 的 hadRightDrag（右滑→关闭）
    var hadLeftDrag by remember { mutableStateOf(false) }

    // 右滑展开通知标记：首次检测到 dragAmount > 0 时调用 onExpandChange(true)，
    // 让父组件立即清除上一个展开的分组（swipeExpandedCategoryId = currentId），
    // 上一个分组 isExpanded 变 false 后通过 LaunchedEffect 立即开始左滑归位动画
    // 不等到 onDragEnd 阈值判定，实现"其他分组开始右滑时上一个立马恢复"
    var hasNotifiedExpand by remember { mutableStateOf(false) }

    // 速度跟踪器：用于检测"快速左滑"（fling left）手势以关闭已展开的卡片
    // 镜像 SwipeableTodoBox 的 velocityTracker（右滑 fling→关闭）
    val velocityTracker = remember { VelocityTracker() }
    // 快速左滑速度阈值：x 方向 < -800 px/s 视为 fling
    val flingVelocityThresholdPx = with(density) { 800.dp.toPx() }

    // 按钮配置（根据 isPinned 切换置顶图标）
    // 分组条无圆角，按钮也不需要圆角
    val buttons = remember(isPinned) {
        categoryDefaultButtons(isPinned)
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
    // 分组条无圆角，不需要 .clip(RoundedCornerShape(...))
    Layout(
        modifier = modifier
            // clipToBounds：限定 layout 渲染区域在 [0, cardWidth] 内
            // - 防止内容右移后溢出（[cardOffsetX, cardWidth + cardOffsetX]）影响视觉
            // - 与 SwipeableTodoBox 的 .clip(RoundedCornerShape) 行为等价
            .clipToBounds()
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
            // 镜像 SwipeableTodoBox 实现：
            // - 内容 measure **完整宽度**，不压缩
            // - 内容用 .offset { IntOffset(cardOffsetX, 0) } 向右平移
            //   - 部分展开时内容向右移动，layout 内左侧 [0, cardOffsetX] 区域露出按钮
            //   - 完全展开时 (cardOffsetX=actionsWidthPx=168dp) 内容完全移出 layout 左侧，按钮区域 [0, 168] 全部露出
            // - 内容 Box 添加 .background(Color.White) 避免非置顶分组（背景透明）时
            //   内容覆盖按钮后透出底下 LazyColumn 的其他分组卡片
            // - 移除原"measurePolicy 压缩内容宽度"方案：
            //   该方案会导致内容 zIndex(10f) > 按钮 zIndex(1f/2f/3f) 时内容覆盖按钮，
            //   而非置顶分组 CategoryItem 背景透明 → 透出其他分组
            Box(
                modifier = Modifier
                    // 关键：内容向右平移（镜像 SwipeableTodoBox 的向左平移）
                    .offset { IntOffset(cardOffsetX.value.roundToInt(), 0) }
                    // v2026-07-30 修复：内容白底，避免非置顶分组透出底层卡片
                    // 镜像 SwipeableTodoBox：TodoListItem 本身是 Card（自带白底）
                    .background(Color.White)
                    .pointerInput(isEnabled, isExpanded) {
                        if (!isEnabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = {
                                // 开始新一轮拖动：
                                // 1. 重置速度跟踪器 + 左滑意图 + 展开通知标记
                                // 2. 取消正在跑的恢复动画 → 避免新 snapTo 与旧 animateTo 争抢 cardOffsetX
                                //    （保留回弹效果让"跟手"更自然）
                                // 镜像 SwipeableTodoBox 的 onDragStart
                                velocityTracker.resetTracking()
                                hadLeftDrag = false
                                hasNotifiedExpand = false
                                restoreJob.value?.cancel()
                                restoreJob.value = null
                            },
                            onDragEnd = {
                                // 关键：恢复动画进行中，仅等待其完成，不要启动新动画
                                if (restoreJob.value != null) {
                                    coroutineScope.launch { restoreJob.value?.join() }
                                } else {
                                    // 计算抬手时的 x 方向速度（px/s）
                                    val velocity = velocityTracker.calculateVelocity()
                                    // 关键：左滑意图（hadLeftDrag）或快速左滑（fling）时，立即关闭卡片
                                    // - hadLeftDrag：onDrag 中任何 dragAmount < 0 都标记，实现"跟手 + 抬手总关闭"
                                    // - velocity.x < -flingVelocityThresholdPx：高速左滑 fling 不依赖跟手
                                    // 镜像 SwipeableTodoBox：hadRightDrag || velocity.x > flingVelocityThresholdPx
                                    if (hadLeftDrag || velocity.x < -flingVelocityThresholdPx) {
                                        // 存入 restoreJob 防止与正在跑的动画冲突
                                        restoreJob.value = coroutineScope.launch {
                                            cardOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(
                                                    durationMillis = durationMs,
                                                    easing = CategoryElasticOutEasing
                                                )
                                            )
                                            onExpandChange(false)
                                            restoreJob.value = null
                                        }
                                    } else {
                                        // 普通抬手：按阈值吸附
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
                            },
                            onDragCancel = {
                                // 关键：恢复动画进行中，仅等待其完成
                                if (restoreJob.value != null) {
                                    coroutineScope.launch { restoreJob.value?.join() }
                                } else {
                                    // 取消手势时同样按"左滑意图或快速左滑"判断（极少见，但保持一致）
                                    val velocity = velocityTracker.calculateVelocity()
                                    if (hadLeftDrag || velocity.x < -flingVelocityThresholdPx) {
                                        restoreJob.value = coroutineScope.launch {
                                            cardOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(
                                                    durationMillis = durationMs,
                                                    easing = CategoryElasticOutEasing
                                                )
                                            )
                                            onExpandChange(false)
                                            restoreJob.value = null
                                        }
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
                            }
                        ) { change, dragAmount ->
                            // 记录每个 pointer 事件的位置和时间，用于计算抬手时的速度
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            // 首次检测到右滑（dragAmount > 0）时，立即通知父组件"我开始展开了"
                            // 父组件会清除其他展开的分组（设置 swipeExpandedCategoryId = null，不设为当前ID），
                            // 上一个展开的分组 isExpanded 立即变 false，通过 LaunchedEffect 开始左滑归位
                            // 实现"其他分组开始右滑时上一个立马恢复"，不等 onDragEnd 阈值判定
                            //
                            // 关键：调用 onExpandStart 而非 onExpandChange(true)
                            // 原因：onExpandChange(true) 会改变当前组件的 isExpanded 从 false→true，
                            // 导致 pointerInput(isEnabled, isExpanded) 重启，中断正在进行的拖拽手势
                            // onExpandStart 只通知父组件清除其他展开的分组，不改变当前组件的 isExpanded
                            if (dragAmount > 0f && !hasNotifiedExpand) {
                                onExpandStart()
                                hasNotifiedExpand = true
                            }

                            // 计算本帧 snapTo 后的目标位置
                            val newOffset = (cardOffsetX.value + dragAmount)
                                .coerceIn(0f, actionsWidthPx)

                            // 左滑意图跟踪：dragAmount < 0 时标记，
                            // 用于 onDragEnd 判断是否需要"抬手总关闭"
                            // 实现"左滑跟手 + 抬手总关闭"语义
                            // 镜像 SwipeableTodoBox：dragAmount > 0 → hadRightDrag
                            if (dragAmount < 0f) {
                                hadLeftDrag = true
                            } else if (dragAmount > 0f && hadLeftDrag) {
                                // 左滑后"反悔"语义：用户右滑到完全展开位置时清除左滑意图，
                                // 抬手后按阈值吸附保持展开（不再触发总关闭）
                                // 需求："左滑后中途右滑到最大位置松手应保持展开"
                                // 镜像 SwipeableTodoBox：右滑后中途左滑到最大位置清除右滑意图
                                if (newOffset >= actionsWidthPx) {
                                    hadLeftDrag = false
                                }
                            }

                            // 关键：左滑"跟手"（snapTo），不再立即触发关闭动画
                            // onDragEnd 会根据 hadLeftDrag / velocity 决定是否关闭
                            // 这样用户能看到卡片跟随手指位置移动，抬手时由 onDragEnd 启动关闭动画
                            coroutineScope.launch {
                                cardOffsetX.snapTo(newOffset)
                            }
                        }
                    }
                    // zIndex(10f) 确保内容层始终在按钮层之上
                    // - 与 SwipeableTodoBox 一致：内容覆盖按钮区域时按钮不可见
                    // - 部分展开时：内容覆盖按钮的某部分，飞书风格级联效果由 zIndex 低的按钮"未覆盖"部分呈现
                    // - 完全展开时：内容完全移出 layout 左侧，按钮全部可见
                    .zIndex(10f)
            ) {
                content()
            }

            // === 操作层（measurables[1..3]）===
            // 按钮在卡片左侧，从左到右：置顶 → 编辑 → 删除
            // 级联堆叠动效（飞书风格，与 SwipeableTodoBox 镜像对称）：
            // - 未展开时：所有按钮堆叠在置顶位置（x=0，最左，最贴近屏幕左边缘）
            // - 右滑展开时：按钮依次从堆叠位置向右展开到各自原位
            //
            // 与 SwipeableTodoBox（左滑）的镜像对称关系：
            // - SwipeableTodoBox：按钮在右侧，初始堆叠在删除位置（最右，贴近卡片右边缘）
            //   offset = (size-1-index) * bwPx，translateX = offset * (1-localProgress)
            // - SwipeableCategoryBox：按钮在左侧，初始堆叠在置顶位置（最左，远离卡片）
            //   offset = -index * bwPx（向左偏移到置顶位置），translateX = offset * (1-localProgress)
            //
            // 偏移量（未展开时，translateX = offset）：
            // - 置顶(index=0): offset = 0（不动，原位就在堆叠位置）
            // - 编辑(index=1): offset = -1*buttonWidthPx（向左偏移到置顶位置）
            // - 删除(index=2): offset = -2*buttonWidthPx（向左偏移到置顶位置）
            //
            // 展开过程（localProgress: 0→1，translateX: offset→0）：
            // 按钮依次从堆叠位置向右移动到各自原位
            buttons.forEachIndexed { index, btnConfig ->
                // 级联算法：计算本地进度（与 SwipeableTodoBox 1:1 对齐）
                val localStart = index * staggerRatio
                val denom = 1f - localStart
                val localProgress = if (denom > 0f) {
                    ((revealProgress - localStart) / denom).coerceIn(0f, 1f)
                } else {
                    if (revealProgress >= localStart) 1f else 0f
                }
                // 偏移量：未展开时按钮向左偏移堆叠在置顶位置（x=0）
                // - 置顶(index=0): offset = 0（不动）
                // - 编辑(index=1): offset = -1*buttonWidthPx（向左偏移 1 个按钮宽）
                // - 删除(index=2): offset = -2*buttonWidthPx（向左偏移 2 个按钮宽）
                val offset = -index * buttonWidthPx
                val translateX = offset * (1f - localProgress)
                // opacity 二元化：无淡入淡出（与 SwipeableTodoBox 一致）
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
                    // 按钮 zIndex 必须 < 内容层 zIndex(10f)，确保内容始终在按钮之上
                    // - 内容右移后，按钮在 [0, cardOffsetX] 露出自然可见
                    // - 按钮延伸到内容区域的部分会被内容覆盖，不会遮挡文字
                    modifier = Modifier.zIndex(btnConfig.zIndex)
                )
            }
        },
        measurePolicy = { measurables, constraints ->
            // 镜像 SwipeableTodoBox 实现：
            // - 内容 measure **完整宽度**（不压缩）
            //   - 内容在 Box 内通过 .offset { IntOffset(cardOffsetX, 0) } 向右平移
            // - 内容 measure 完整宽度后，layout 实际尺寸 = cardWidth
            //   - 部分展开时：内容向右平移 cardOffsetX，按钮在 layout 内 [0, cardOffsetX] 区域可见
            //   - 完全展开时 (cardOffsetX=actionsWidthPx=168dp)：
            //     内容完全移出 layout 左侧 ([0, cardWidth+168])，
            //     按钮在 layout 内 [0, 168] 区域全部可见
            // - 按钮初始位置 x = index * buttonWidthPx (最左)
            //   - i=0 (置顶): x = 0
            //   - i=1 (编辑): x = 56 (单按钮宽 buttonWidthDp = 56dp)
            //   - i+2 (删除): x = 112
            // - 按钮通过 graphicsLayer translationX 实现级联堆叠动效
            val contentPlaceable = measurables[0].measure(constraints)
            val cardWidth = contentPlaceable.width
            val cardHeight = contentPlaceable.height
            val singleButtonWidthPx = with(density) { buttonWidthDp.roundToPx() }

            // 分别测量三个按钮，每个按钮强制固定宽度=56dp、高度=卡片实际高度
            val actionPlaceables = List(buttons.size) { i ->
                measurables[1 + i].measure(
                    Constraints.fixed(
                        width = singleButtonWidthPx,
                        height = cardHeight
                    )
                )
            }

            layout(cardWidth, cardHeight) {
                // 内容 placeRelative(0, 0)，由 Box 的 .offset 平移视觉位置
                contentPlaceable.placeRelative(0, 0)
                // 按钮放在最左 [0, 168] 区域（镜像 SwipeableTodoBox 的最右 [cardWidth-216, cardWidth]）
                actionPlaceables.forEachIndexed { index, placeable ->
                    val x = index * singleButtonWidthPx
                    placeable.placeRelative(x = x, y = 0)
                }
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
