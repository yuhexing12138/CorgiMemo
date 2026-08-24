package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 堆叠中的单张卡片槽位
 *
 * - `stableId`：作为 Composable key 永不变化，避免重组时丢失状态
 * - `originalIndex`：图片在原始 `imageUris` 列表中的下标，
 *   滑动重排后保持不变，用于 `onCardSwiped` 回调让业务方知道是哪张图
 */
private data class CardSlot(val stableId: Long, val originalIndex: Int)

/**
 * 拖动手势方向约束
 *
 * - [Horizontal]：仅水平拖动。**推荐用于嵌入滚动列表的场景**（如时间线）。
 *   用 `detectHorizontalDragGestures` 天然不消费垂直分量，父级 `LazyColumn`
 *   仍可正常垂直滚动。代价是失去原型"对角线飞"效果，fly-out 强制水平。
 * - [Both]：全向拖动（水平+垂直），复刻 Web 端 `framer-motion` 原型效果。
 *   仅适合独立全屏场景（如未来"图片聚合页"），嵌入滚动列表会与父级
 *   滚动产生手势冲突。
 */
enum class SwipeDirection {
    Horizontal,
    Both
}

/**
 * 滑动堆叠图片组件
 *
 * **来源**：参考 `图片库/滑动堆叠.md`（Originkit Web 组件）的交互模式
 *
 * **特性**：
 * - 多张图片以扇形/堆叠方式展示，透视感（perspective=1000px）
 * - 顶卡可拖拽，释放时：
 *   - 拖动距离 > 阈值（默认 50dp）：飞出并重排（顶卡移到队尾）
 *   - 拖动距离 ≤ 阈值：spring 弹回中心
 * - 非顶卡按 index 依次缩小、旋转、偏移形成扇形
 * - 拖拽中：顶卡放大到 1.05x、旋转归零、zIndex 提升
 * - 双 API 兼容：
 *   - 简单调用：传 `imageUris`（默认用 Coil 3 SubcomposeAsyncImage 加载）
 *   - 高级调用：传 `customContent` slot（完全自定义卡片内容）
 *
 * **与本项目其它图片组件的对比**：
 * - `InlineImagePreview`：纵向单图预览，用于富文本块
 * - `InspirationImageGallery`：全屏 HorizontalPager + 双指缩放 + 二次确认删除
 * - `SwipeableImageStack`（本组件）：卡片式扇形堆叠 + 拖拽翻牌
 *
 * **接入建议**（不推荐接入现有页面）：
 * - ❌ 编辑页富文本流：堆叠遮挡破坏可读性
 * - ❌ 详情页卡片内：与阅读流冲突
 * - ✅ 未来"图片聚合页/精选集"：可作为浏览模式
 * - ✅ 纯组件形式沉淀为可复用 UI 资产
 *
 * @param imageUris 图片 Uri 路径列表（local path / content uri / http url 都可）
 * @param modifier Modifier（可选）
 * @param cardWidth 卡片宽度（默认 300.dp，嵌入时间线缩略图建议传 120.dp）
 * @param cardHeight 卡片高度（默认 400.dp，嵌入时间线缩略图建议传 120.dp）
 * @param cardRadius 卡片圆角（默认 16.dp，嵌入时间线缩略图建议传 12.dp）
 * @param swipeThreshold 拖动距离阈值（默认 50.dp）
 * @param maxElasticDistance 弹性边界（默认 0.dp = 自动：max(threshold*4, cardWidth)）；
 *                           显式传值时覆盖默认，便于调用方微调阻力边界
 * @param tiltAngle 堆叠末端旋转角度（默认 -12f，单位度）
 * @param tiltAngleStart 堆叠首端旋转角度（默认 0f）
 * @param xOffset 堆叠末端水平偏移（默认 60.dp，扇形展开幅度）
 * @param swipeDirection 拖动手势方向约束（默认 [SwipeDirection.Horizontal]，
 *                       嵌入滚动列表务必用 Horizontal 避免与父级手势冲突）
 * @param onCardSwiped 顶卡滑出回调（被滑出的图片在原列表中的索引）
 * @param onCardClick 顶卡点击回调，参数为顶卡对应的原始图片索引
 */
@Composable
fun SwipeableImageStack(
    imageUris: List<String>,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 300.dp,
    cardHeight: Dp = 400.dp,
    cardRadius: Float = 4f,
    swipeThreshold: Dp = 50.dp,
    maxElasticDistance: Dp = 0.dp,
    tiltAngle: Float = -45f,
    tiltAngleStart: Float = 0f,
    xOffset: Dp = 200.dp,
    swipeDirection: SwipeDirection = SwipeDirection.Horizontal,
    onCardSwiped: ((originalIndex: Int) -> Unit)? = null,
    onCardClick: ((originalIndex: Int) -> Unit)? = null
) {
    SwipeableImageStack(
        imageUris = imageUris,
        modifier = modifier,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        cardRadius = cardRadius,
        swipeThreshold = swipeThreshold,
        maxElasticDistance = maxElasticDistance,
        tiltAngle = tiltAngle,
        tiltAngleStart = tiltAngleStart,
        xOffset = xOffset,
        swipeDirection = swipeDirection,
        onCardSwiped = onCardSwiped,
        onCardClick = onCardClick,
        customContent = null
    )
}

/**
 * 滑动堆叠图片组件（高级 API：自定义内容 slot）
 *
 * 通过 `customContent` 完全自定义每张卡片的渲染（例如带标题、标签、角标等），
 * 不再使用默认的 Coil 图片加载。`imageUris` 可传空列表，仅作为卡片数量依据。
 *
 * @param customContent 自定义每张卡片内容的 Composable。第一个参数是 `BoxScope`
 *                     （用于让上层 Composable 自定义子元素定位），第二个参数是当前卡片的栈内 index
 */
@Composable
fun SwipeableImageStack(
    imageUris: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    cardWidth: Dp = 300.dp,
    cardHeight: Dp = 400.dp,
    cardRadius: Float = 4f,
    swipeThreshold: Dp = 50.dp,
    maxElasticDistance: Dp = 0.dp,
    tiltAngle: Float = -45f,
    tiltAngleStart: Float = 0f,
    xOffset: Dp = 200.dp,
    swipeDirection: SwipeDirection = SwipeDirection.Horizontal,
    onCardSwiped: ((originalIndex: Int) -> Unit)? = null,
    onCardClick: ((originalIndex: Int) -> Unit)? = null,
    customContent: (@Composable BoxScope.(stackIndex: Int) -> Unit)? = null
) {
    // 维护一个 (stableId, originalIndex) 列表：
    // - stableId 永不变化，作为 key
    // - originalIndex 是图片在原始 imageUris 中的下标，用于回调
    //
    // 滑动后：队首移到队尾，原 order = [0,1,2,3] → [1,2,3,0]
    //
    // key(cardCount)：当图片数量变化时（例如新插入/删除）整体重置 order，
    // 否则会残留旧 CardSlot 引用，导致 stableId 重复或漏渲染。
    val cardCount = imageUris.size
    if (cardCount == 0) return

    var order by remember(cardCount) {
        mutableStateOf(List(cardCount) { CardSlot(stableId = it.toLong(), originalIndex = it) })
    }

    // 顶卡拖拽状态：顶卡相对于其静止位置的实时偏移（像素）
    // - 拖拽中：随手指 snapTo 更新
    // - 释放后：animateTo 到 0（回中）或飞出方向（飞离屏幕）
    val dragOffsetX = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }

    val density = LocalDensity.current
    val thresholdPx = with(density) { swipeThreshold.toPx() }
    // 真实弹性边界：
    // - 显式传值（> 0.dp）：用调用方的设定，便于特殊场景微调
    // - 默认（<= 0.dp）：max(thresholdPx*4, cardWidth.toPx())
    //   - 120dp 缩略图场景：thresholdPx*4 = 200px，cardWidth.toPx() ≈ 240px（@1x），取 cardWidth（视觉上更跟手）
    //   - 300dp 大图场景：thresholdPx*4 = 200px，cardWidth.toPx() ≈ 600px（@2x），取 cardWidth
    //   - 即缩略图/大图都自动适配，且下限 = 4x 阈值（保证 50dp 阈值场景阻力边界至少 200dp）
    // - offset = 0 → 乘数 = 1.0（无阻力）
    // - offset = maxDistance → 乘数 = 0.7（阻力最大，与原型 dragElastic 数值一致）
    // - offset > maxDistance → 乘数 ≤ 0.7（clamp 后保持不再增加阻力）
    val maxElasticDistancePx = if (maxElasticDistance > 0.dp) {
        with(density) { maxElasticDistance.toPx() }
    } else {
        val cardWidthPx = with(density) { cardWidth.toPx() }
        max(thresholdPx * 4f, cardWidthPx)
    }

    val scope = rememberCoroutineScope()

    // ============ 共享状态（与 Originkit 原型一致）============
    // isPressed：组件级"是否有顶卡正在被按住"标志
    // - 用于顶卡 scale +0.05、rotation 归零的视觉反馈
    // - 原型 const [isPressed, setIsPressed] = useState(false)
    val isPressed = remember { mutableStateOf(false) }
    // shouldReturnToCenter：短距离拖动后回中动画的标志
    // - true 期间顶卡 x/y/rotate 强制为 0（不再叠加扇形偏移）
    // - 1s 后自动复位（与原型 setTimeout 1s 一致）
    val shouldReturnToCenter = remember { mutableStateOf(false) }

    // 容器：宽 = cardWidth + xOffset（容纳扇形最末端 xOffset 的偏移），
    // 高 = cardHeight + 15% cardHeight（容纳 yStackOffset 上移，按比例适配不同尺寸）
    // - 用 cardHeight * 0.15 而非固定 60dp：120dp 缩略图场景只需 ~18dp 余量，400dp 详情页场景需 60dp 余量
    val stackVerticalPadding = cardHeight * 0.15f

    // 圆角算法（与原型一致：cardRadius 0-20 滑块 → 实际圆角 = (cardRadius/20) × min(W,H)/2）
    // 原型：const radiusPx = (cardRadius / 20) * (Math.min(cardWidth, cardHeight) / 2)
    // cardRadius=0 → 0dp（boxy），cardRadius=20 → 完全圆角（min(W,H)/2）
    // 用 if 而非 minOf：60fps 下每帧重组时少一次函数调用
    val smallerSide = if (cardWidth <= cardHeight) cardWidth else cardHeight
    val radiusPx = with(density) {
        ((cardRadius / 20f) * (smallerSide.toPx() / 2f)).toDp()
    }

    Box(
        modifier = modifier.size(cardWidth + xOffset, cardHeight + stackVerticalPadding),
        contentAlignment = Alignment.Center
    ) {
        // 透视感：父容器 cameraDistance 模拟 Web 端 perspective: 1000px
        // - 原型 `perspective: ${PERSPECTIVE}px` (PERSPECTIVE = 1000)
        // - Compose cameraDistance 默认单位是 dp（实际 px = cameraDistance × density）
        // - 为与 Web 1000px 严格一致：cameraDistance = 1000 / density.density (dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.cameraDistance = 1000f / density.density
                },
            contentAlignment = Alignment.Center
        ) {
            // 渲染顺序：从底到顶（栈底先渲染，栈顶后渲染覆盖在前面）
            // order[0] 是当前顶卡，order.last] 是最底层
            //
            // FLIP 翻牌动画实现：每张卡维护 4 个 Animatable（positionX, positionY, scale, rotation），
            // 当 stackIndex 变化时（即重排），LaunchedEffect 启动 animateTo 到新目标值，
            // 视觉上呈现"流水翻牌"效果——不再有"卡一下"的视觉跳变
            order.forEachIndexed { stackIndex, slot ->
                key(slot.stableId) {
                    val isTopCard = stackIndex == 0

                    // =================== 4 个独立 Animatable ===================
                    // 每张卡片的 x/y/scale/rotation 各自有独立的 Animatable，初始值 = stackIndex 目标值
                    // 后续通过 LaunchedEffect(stackIndex) 自动过渡到新目标
                    val targetX = with(density) {
                        if (order.size > 1) (stackIndex.toFloat() / (order.size - 1) * xOffset.value).dp.toPx() else 0f
                    }
                    val targetY = with(density) { -(stackIndex * 8).dp.toPx() }
                    val targetScale = 1f - stackIndex * 0.05f
                    val targetRotation = if (order.size > 1) {
                        tiltAngleStart + (stackIndex.toFloat() / (order.size - 1)) * (tiltAngle - tiltAngleStart)
                    } else {
                        tiltAngleStart
                    }
                    // zIndex 目标：与原型 cards.length - index 严格一致
                    val targetZIndex = (order.size - stackIndex).toFloat()
                    // 注：原型的 3D 纵深 z: -index * 10px 因 GraphicsLayerScope 没有 translationZ 属性而无法实现
                    // 当前视觉由 cameraDistance（父容器 perspective: 1000px）+ 每张卡 scale = 1 - index*0.05 接管
                    // 仍保留 zIndexAnim 的 0.3s easeOut 过渡，与原型 zIndex 动画一致

                    val positionX = remember { Animatable(targetX) }
                    val positionY = remember { Animatable(targetY) }
                    val scaleAnim = remember { Animatable(targetScale) }
                    val rotationAnim = remember { Animatable(targetRotation) }
                    val zIndexAnim = remember { Animatable(targetZIndex) }

                    // stackIndex 变化时（即 order 重排），每张卡各自平滑过渡到新目标值
                    // 关键：这是解决"卡一下"的核心——新顶卡从扇形位置平滑滑到中央，旧顶卡从中央滑到队尾
                    // 用 TRANSITION_SPRING 加速：300ms 而非 600ms，整体"翻牌"从 1100ms 降至 500ms
                    LaunchedEffect(stackIndex, order.size) {
                        positionX.animateTo(targetX, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size) {
                        positionY.animateTo(targetY, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size) {
                        scaleAnim.animateTo(targetScale, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size) {
                        rotationAnim.animateTo(targetRotation, TRANSITION_SPRING)
                    }
                    // 原型 zIndex: { duration: 0.3, ease: "easeOut" }，按下时跳 1000
                    LaunchedEffect(stackIndex, order.size, isPressed.value, isTopCard) {
                        val target = if (isPressed.value && isTopCard) 1000f else targetZIndex
                        zIndexAnim.animateTo(target, ZINDEX_TWEEN)
                    }

                    // 渲染时：顶卡额外叠加 dragOffset；非顶卡只用 Animatable
                    // 用 isPressed（而非 dragOffsetX != 0）判断拖动状态：
                    // - 旧实现 isDragging = isTopCard && (dragOffsetX != 0) 会把"动画回零"误判为拖动中
                    // - 新顶卡重排后 dragOffsetX 还在 animateTo(0) 期间，isPressed=false
                    //   finalScale 跟随 scaleAnim 平滑过渡（0.95→1.0），不再有"从大变小"突变
                    //
                    // shouldReturnToCenter（与原型一致）：短距离拖动后回中时，1s 内顶卡 x/y/rotate 强制为 0
                    // - 配合 setTimeout 1s 自动复位
                    // - 视觉：顶卡先瞬移到中央，再用 spring 平滑过渡回扇形位置
                    val finalX = when {
                        isTopCard && shouldReturnToCenter.value -> 0f
                        isTopCard -> positionX.value + dragOffsetX.value
                        else -> positionX.value
                    }
                    val finalY = when {
                        isTopCard && shouldReturnToCenter.value -> 0f
                        isTopCard -> positionY.value + dragOffsetY.value
                        else -> positionY.value
                    }
                    // 原型 whileDrag: { scale: 1.05 } → 当前帧 = scaleAnim + 0.05
                    val finalScale = if (isPressed.value) scaleAnim.value + 0.05f else scaleAnim.value
                    // 原型 whileDrag: { rotate: tiltAngleStart = 0 } → 拖动时归零
                    // 原型 shouldReturn 时也强制 rotate = 0
                    val finalRotation = when {
                        isPressed.value -> 0f
                        isTopCard && shouldReturnToCenter.value -> 0f
                        else -> rotationAnim.value
                    }

                    Box(
                        modifier = Modifier
                            .size(cardWidth, cardHeight)
                            // zIndex 由 zIndexAnim 驱动：静止时 (order.size - stackIndex)，按下时 1000
                            // 用 Animatable + 0.3s easeOut 过渡，与原型 { duration: 0.3, ease: "easeOut" } 一致
                            .zIndex(zIndexAnim.value)
                            .offset { IntOffset(finalX.roundToInt(), finalY.roundToInt()) }
                            .graphicsLayer {
                                // 注：原型的 3D 纵深 z: -index * 10px 因 GraphicsLayerScope 没有 translationZ 属性而无法实现
                                // 当前由父容器的 cameraDistance (perspective: 1000px) + 每张卡 scale = 1 - index*0.05 接管纵深感
                                rotationZ = finalRotation
                                scaleX = finalScale
                                scaleY = finalScale
                            }
                            .shadow(
                                elevation = if (isTopCard) 8.dp else 4.dp,
                                shape = RoundedCornerShape(radiusPx)
                            )
                            .clip(RoundedCornerShape(radiusPx))
                            .background(Color(0xFFF3EFFF))
                            .then(
                                if (isTopCard) {
                                    Modifier.pointerInput(slot.stableId, swipeDirection) {
                                        // 水平模式：用 detectHorizontalDragGestures，天然不消费垂直分量，
                                        // 父级 LazyColumn 仍可垂直滚动（关键解耦点）。
                                        // 全向模式：用 detectDragGestures 复刻原型，嵌入滚动列表会与父级冲突。
                                        if (swipeDirection == SwipeDirection.Horizontal) {
                                            detectHorizontalDragGestures(
                                                onDragStart = {
                                                    // 标记进入"用户拖动"状态（用于 finalScale = scaleAnim + 0.05 的相对放大）
                                                    isPressed.value = true
                                                    // 取消未完成的回中动画
                                                    // - 避免 1s 回中期内又开始拖新顶卡时，新顶卡从 (0,0) 跳到 dragOffset 起点
                                                    // - 必须放在 isPressed=true 之后：否则 finalX 仍受 shouldReturnToCenter 控制
                                                    shouldReturnToCenter.value = false
                                                    // 把顶卡的 positionX/Y 强制归零
                                                    // - 保留这个 snapTo 防止上次重排动画把 positionX 留在中间值
                                                    // - 移除 scaleAnim.snapTo(1.05f) 和 rotationAnim.snapTo(0f)：
                                                    //   这两个 snapTo 会与重排后的入场动画（0.95→1.0, -4°→0°）打架，
                                                    //   导致用户立即开始拖新顶卡时出现"scale 突变"和"rotation 跳变"
                                                    //   现在 finalScale/finalRotation 自动用 scaleAnim.value + 0.05 / 0f，无需 snapTo
                                                    scope.launch {
                                                        positionX.snapTo(0f)
                                                        positionY.snapTo(0f)
                                                    }
                                                },
                                                onDragEnd = {
                                                    // 标记离开"用户拖动"状态
                                                    isPressed.value = false
                                                    val dx = dragOffsetX.value
                                                    val distance = dx.absoluteValue
                                                    if (distance > thresholdPx) {
                                                        // 关键优化：立即重排（不等飞出完成），把"两段式"改为"并行"
                                                        // 旧顶卡现在 stackIndex=N-1，LaunchedEffect 自动启动 positionX 0→xOffset
                                                        // 同时 dragOffsetX 从当前 dx animateTo 到 0（旧顶卡"从屏外滑到队尾"）
                                                        // 视觉：顶卡飞出去的同时新顶卡从扇形位滑到中央，全部 300ms 内完成
                                                        scope.launch {
                                                            // 1. 立即重排（不等飞出完成）
                                                            onCardSwiped?.invoke(slot.originalIndex)
                                                            val newOrder = order.toMutableList()
                                                            val top = newOrder.removeAt(0)
                                                            newOrder.add(top)
                                                            order = newOrder
                                                            // 2. dragOffset 从 dx 飞回到 0（旧顶卡跟随位置变化，finalX = positionX + dragOffsetX 平滑过渡）
                                                            dragOffsetX.animateTo(0f, TRANSITION_SPRING)
                                                            dragOffsetY.animateTo(0f, TRANSITION_SPRING)
                                                        }
                                                    } else {
                                                        // 弹回中心：与原型 setShouldReturnToCenter(true) + setTimeout(1s) 一致
                                                        shouldReturnToCenter.value = true
                                                        scope.launch {
                                                            delay(1000)
                                                            shouldReturnToCenter.value = false
                                                        }
                                                        scope.launch {
                                                            dragOffsetX.animateTo(0f, TRANSITION_SPRING)
                                                            dragOffsetY.animateTo(0f, TRANSITION_SPRING)
                                                        }
                                                    }
                                                }
                                            ) { change, dragAmount ->
                                                // 非线性弹性（参考 framer-motion dragElastic 真实曲线，越远阻力增长越陡）：
                                                //   elasticAmount = dragAmount * (1 - 0.3 * t^1.5)
                                                // - t = |offset| / maxDistance，t^1.5 让阻力增长介于线性和 t^2 之间
                                                //   - t=0.0 → 1.0（无阻力）
                                                //   - t=0.5 → 1 - 0.3 * 0.354 = 0.894（轻微阻力）
                                                //   - t=1.0 → 0.7（与原型 dragElastic 数值边界一致）
                                                // - 用 pow(1.5f) 而非 pow(2f)：t^2 阻力增长过陡，缩略图场景手感过重
                                                val currentOffset = dragOffsetX.value.absoluteValue
                                                val t = (currentOffset / maxElasticDistancePx).coerceIn(0f, 1f)
                                                val resistance = 1f - 0.3f * t.pow(1.5f)
                                                val elasticAmount = dragAmount * resistance
                                                scope.launch {
                                                    dragOffsetX.snapTo(dragOffsetX.value + elasticAmount)
                                                }
                                            }
                                        } else {
                                            // 全向模式
                                            detectDragGestures(
                                                onDragStart = {
                                                    isPressed.value = true
                                                    // 取消未完成的回中动画（同水平模式，避免新顶卡从 (0,0) 跳到 dragOffset 起点）
                                                    shouldReturnToCenter.value = false
                                                    // 同样：移除 scaleAnim.snapTo(1.05f) 和 rotationAnim.snapTo(0f)，
                                                    // 保留 positionX/Y.snapTo(0f) 防上次重排动画残留
                                                    scope.launch {
                                                        positionX.snapTo(0f)
                                                        positionY.snapTo(0f)
                                                    }
                                                },
                                                onDragEnd = {
                                                    isPressed.value = false
                                                    val dx = dragOffsetX.value
                                                    val dy = dragOffsetY.value
                                                    val distance = hypot(dx, dy)
                                                    if (distance > thresholdPx) {
                                                        // 关键优化：立即重排（不等飞出完成），与水平模式同样的并行策略
                                                        scope.launch {
                                                            onCardSwiped?.invoke(slot.originalIndex)
                                                            val newOrder = order.toMutableList()
                                                            val top = newOrder.removeAt(0)
                                                            newOrder.add(top)
                                                            order = newOrder
                                                            // dragOffset 从飞出方向回到 0（旧顶卡 finalX 平滑过渡到队尾）
                                                            dragOffsetX.animateTo(0f, TRANSITION_SPRING)
                                                            dragOffsetY.animateTo(0f, TRANSITION_SPRING)
                                                        }
                                                    } else {
                                                        // 弹回中心：与原型 setShouldReturnToCenter(true) + setTimeout(1s) 一致
                                                        shouldReturnToCenter.value = true
                                                        scope.launch {
                                                            delay(1000)
                                                            shouldReturnToCenter.value = false
                                                        }
                                                        scope.launch {
                                                            dragOffsetX.animateTo(0f, TRANSITION_SPRING)
                                                            dragOffsetY.animateTo(0f, TRANSITION_SPRING)
                                                        }
                                                    }
                                                }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                // 非线性弹性（全向模式，参考 framer-motion dragElastic）：
                                                //   elasticAmount = dragAmount * (1 - 0.3 * t^1.5)
                                                //   t = hypot(dragOffsetX, dragOffsetY) / maxElasticDistancePx
                                                val currentDistance = hypot(dragOffsetX.value, dragOffsetY.value)
                                                val t = (currentDistance / maxElasticDistancePx).coerceIn(0f, 1f)
                                                val resistance = 1f - 0.3f * t.pow(1.5f)
                                                val elasticAmount = dragAmount * resistance
                                                scope.launch {
                                                    dragOffsetX.snapTo(dragOffsetX.value + elasticAmount.x)
                                                    dragOffsetY.snapTo(dragOffsetY.value + elasticAmount.y)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        // ============ 卡片内容渲染 ============
                        if (customContent != null) {
                            // 高级 API：调用方完全自定义
                            customContent(stackIndex)
                        } else {
                            // 默认 API：Coil 加载 imageUris[originalIndex]
                            val imageUri = imageUris.getOrNull(slot.originalIndex)
                            if (imageUri != null) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(imageUri)
                                        .crossfade(true)
                                        .scale(Scale.FIT)
                                        .build(),
                                    contentDescription = "图片 ${slot.originalIndex + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    loading = { DefaultImagePlaceholder(radiusPx) },
                                    error = { DefaultImagePlaceholder(radiusPx) }
                                )
                            } else {
                                DefaultImagePlaceholder(radiusPx)
                            }
                        }

                        // 顶卡额外支持点击
                        // 使用 detectTapGestures 的 onTap：只在快速点击（无拖动）时触发，
                        // 拖动事件已被外层 drag gesture 消费，两者不冲突。
                        if (isTopCard && onCardClick != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(slot.stableId) {
                                        detectTapGestures(
                                            onTap = { onCardClick(slot.originalIndex) }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 动画规格常量（与 Originkit 原型 `transition = { type: "spring", stiffness: 300, damping: 30 }` 对齐）
 *
 * framer-motion 的 damping 是物理阻尼系数（不是 dampingRatio），转换公式：
 *   dampingRatio = damping / (2 × sqrt(mass × stiffness))
 *   = 30 / (2 × sqrt(1 × 300))
 *   = 30 / 34.64
 *   ≈ 0.866
 *
 * 整体行为：spring stiffness=300, dampingRatio=0.866 → 单次动画约 500-600ms
 * 原型在松手时立即重排（无飞出），所以总延迟 < 200ms
 */
private val TRANSITION_SPRING = spring<Float>(dampingRatio = 0.866f, stiffness = 300f)

/**
 * zIndex / 3D 纵深过渡动画（与 Originkit 原型 `{ duration: 0.3, ease: "easeOut" }` 对齐）
 *
 * - 原型在 zIndex 和 z 两个属性上都用 0.3s easeOut
 * - LinearOutSlowInEasing ≈ easeOut（开始快、结束慢）
 * - 用 tween 而非 spring：避免 spring 阻尼震荡影响 zIndex 的整数跳变
 */
private val ZINDEX_TWEEN = tween<Float>(durationMillis = 300, easing = LinearOutSlowInEasing)

/**
 * 图片占位符（加载中 / 加载失败 / 缺图时显示）
 *
 * 与 Originkit 原型一致：
 * - background: rgba(243, 239, 255, 0.8)（浅紫半透明）
 * - border: 1.5.dp solid #9967FF（紫色边框）
 * - backdropFilter: blur(10.dp)（毛玻璃）
 * - 提示文字 "Card N — Add images in Content"（font-size 14, color #9967FF, font-weight 300）
 */
@Composable
private fun DefaultImagePlaceholder(
    cardRadius: Dp,
    stackIndex: Int = 0
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFFF3EFFF).copy(alpha = 0.8f),  // rgba(243, 239, 255, 0.8)
                shape = RoundedCornerShape(cardRadius)
            )
            .border(
                width = 1.5.dp,
                color = Color(0xFF9967FF),
                shape = RoundedCornerShape(cardRadius)
            )
            .blur(10.dp),  // backdropFilter: blur(10px) 毛玻璃
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Card ${stackIndex + 1} — Add images in Content",
            color = Color(0xFF9967FF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,  // 300
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(20.dp)
        )
    }
}

// =============================================================================
// Preview
// =============================================================================

/**
 * Preview: 3 张网络图片（用于 IDE 实时预览）
 */
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SwipeableImageStackPreview3() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBF5)),
        contentAlignment = Alignment.Center
    ) {
        SwipeableImageStack(
            imageUris = listOf(
                "https://picsum.photos/seed/corgi1/600/800",
                "https://picsum.photos/seed/corgi2/600/800",
                "https://picsum.photos/seed/corgi3/600/800"
            )
        )
    }
}

/**
 * Preview: 5 张图片（更密集的堆叠）
 */
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SwipeableImageStackPreview5() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBF5)),
        contentAlignment = Alignment.Center
    ) {
        SwipeableImageStack(
            imageUris = listOf(
                "https://picsum.photos/seed/a/600/800",
                "https://picsum.photos/seed/b/600/800",
                "https://picsum.photos/seed/c/600/800",
                "https://picsum.photos/seed/d/600/800",
                "https://picsum.photos/seed/e/600/800"
            )
        )
    }
}

/**
 * Preview: 自定义内容 slot（高级 API 示例：带编号和颜色色块）
 */
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SwipeableImageStackPreviewCustom() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBF5)),
        contentAlignment = Alignment.Center
    ) {
        SwipeableImageStack(
            imageUris = listOf("", "", "", ""),
            customContent = { index ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = listOf(
                                Color(0xFFFF9A5C),
                                Color(0xFFFFB5C2),
                                Color(0xFF7EC8A0),
                                Color(0xFF7EB8DA)
                            )[index % 4]
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Card ${index + 1}",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

/**
 * Preview: 本地色块（无网络依赖，IDE 始终可预览）
 *
 * 适配首页时间线缩略图场景：120×120 + 5 张色块 + 水平模式
 * - 与线上 picsum Preview 对照：可调试 stackVerticalPadding 比例、xOffset 效果
 * - 无网络/弱网环境也能在 IDE 中正常看到堆叠视觉
 */
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SwipeableImageStackPreviewLocal() {
    // 与项目 Color.kt 一致的色卡（暖色 + 冷色 + 中性）
    val palette = listOf(
        Color(0xFFFF9A5C),  // 暖橙（主色）
        Color(0xFFFFB5C2),  // 粉
        Color(0xFF7EC8A0),  // 绿
        Color(0xFF7EB8DA),  // 蓝
        Color(0xFFB8A9D9)   // 紫
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBF5)),
        contentAlignment = Alignment.Center
    ) {
        SwipeableImageStack(
            imageUris = List(palette.size) { "" },  // 空 Uri，仅作数量依据
            cardWidth = 120.dp,
            cardHeight = 120.dp,
            cardRadius = 12f,
            tiltAngle = -8f,
            xOffset = 28.dp,
            swipeDirection = SwipeDirection.Horizontal,
            customContent = { stackIndex ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette[stackIndex % palette.size]),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${stackIndex + 1}",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

/**
 * Preview: 本地色块 - 大图场景（300×400 + 3 张色块 + 全向模式）
 *
 * 复刻 Web 原型效果，验证 SwipeDirection.Both 的对角线飞牌体验。
 */
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SwipeableImageStackPreviewLocalLarge() {
    val palette = listOf(
        Color(0xFFFF9A5C),
        Color(0xFF7EC8A0),
        Color(0xFF7EB8DA)
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBF5)),
        contentAlignment = Alignment.Center
    ) {
        SwipeableImageStack(
            imageUris = List(palette.size) { "" },
            cardWidth = 300.dp,
            cardHeight = 400.dp,
            cardRadius = 16f,
            tiltAngle = -12f,
            xOffset = 60.dp,
            swipeDirection = SwipeDirection.Both,
            customContent = { stackIndex ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette[stackIndex % palette.size]),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Card ${stackIndex + 1}",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}
