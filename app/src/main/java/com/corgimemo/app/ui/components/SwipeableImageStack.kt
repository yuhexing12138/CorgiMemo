package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.hypot
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
    cardRadius: Dp = 16.dp,
    swipeThreshold: Dp = 50.dp,
    tiltAngle: Float = -12f,
    tiltAngleStart: Float = 0f,
    xOffset: Dp = 60.dp,
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
    cardRadius: Dp = 16.dp,
    swipeThreshold: Dp = 50.dp,
    tiltAngle: Float = -12f,
    tiltAngleStart: Float = 0f,
    xOffset: Dp = 60.dp,
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

    val scope = rememberCoroutineScope()

    // 容器：宽 = cardWidth + xOffset（容纳扇形最末端 xOffset 的偏移），高 = cardHeight + 60dp（容纳 yStackOffset 上移）
    Box(
        modifier = modifier.size(cardWidth + xOffset, cardHeight + 60.dp),
        contentAlignment = Alignment.Center
    ) {
        // 透视感：父容器 cameraDistance 模拟 Web 端 perspective:1000
        // 数值越大透视越弱，1000 与 Web 1000px 对齐
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.cameraDistance = 12f * density.density
                },
            contentAlignment = Alignment.Center
        ) {
            // 渲染顺序：从底到顶（栈底先渲染，栈顶后渲染覆盖在前面）
            // order[0] 是当前顶卡，order.last] 是最底层
            order.forEachIndexed { stackIndex, slot ->
                key(slot.stableId) {
                    val isTopCard = stackIndex == 0
                    val cardStyle = computeCardStyle(
                        stackIndex = stackIndex,
                        totalCards = order.size,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        xOffset = xOffset,
                        tiltAngle = tiltAngle,
                        tiltAngleStart = tiltAngleStart
                    )
                    val rotationDeg = if (isTopCard && (dragOffsetX.value != 0f || dragOffsetY.value != 0f)) {
                        0f
                    } else {
                        cardStyle.rotation
                    }
                    val scale = if (isTopCard && (dragOffsetX.value != 0f || dragOffsetY.value != 0f)) {
                        1.05f
                    } else {
                        cardStyle.scale
                    }

                    val baseX = with(density) { cardStyle.x.toPx() }
                    val baseY = with(density) { cardStyle.y.toPx() }

                    val finalX = if (isTopCard) baseX + dragOffsetX.value else baseX
                    val finalY = if (isTopCard) baseY + dragOffsetY.value else baseY

                    Box(
                        modifier = Modifier
                            .size(cardWidth, cardHeight)
                            .offset { IntOffset(finalX.roundToInt(), finalY.roundToInt()) }
                            .graphicsLayer {
                                rotationZ = rotationDeg
                                scaleX = scale
                                scaleY = scale
                                // z 轴略向下推一格，让堆叠有层次感
                                translationZ = -stackIndex * 4f
                            }
                            .shadow(
                                elevation = if (isTopCard) 8.dp else 4.dp,
                                shape = RoundedCornerShape(cardRadius)
                            )
                            .clip(RoundedCornerShape(cardRadius))
                            .background(Color(0xFFF3EFFF))
                            .then(
                                if (isTopCard) {
                                    Modifier.pointerInput(slot.stableId, swipeDirection) {
                                        // 水平模式：用 detectHorizontalDragGestures，天然不消费垂直分量，
                                        // 父级 LazyColumn 仍可垂直滚动（关键解耦点）。
                                        // 全向模式：用 detectDragGestures 复刻原型，嵌入滚动列表会与父级冲突。
                                        if (swipeDirection == SwipeDirection.Horizontal) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    val dx = dragOffsetX.value
                                                    val distance = dx.absoluteValue
                                                    if (distance > thresholdPx) {
                                                        // 水平飞出 + 重排
                                                        scope.launch {
                                                            dragOffsetX.animateTo(
                                                                dx * 3f,
                                                                spring(dampingRatio = 0.75f, stiffness = 200f)
                                                            )
                                                            onCardSwiped?.invoke(slot.originalIndex)
                                                            val newOrder = order.toMutableList()
                                                            val top = newOrder.removeAt(0)
                                                            newOrder.add(top)
                                                            order = newOrder
                                                            dragOffsetX.snapTo(0f)
                                                            dragOffsetY.snapTo(0f)
                                                        }
                                                    } else {
                                                        // 弹回中心
                                                        scope.launch {
                                                            dragOffsetX.animateTo(
                                                                0f,
                                                                spring(dampingRatio = 0.55f, stiffness = 300f)
                                                            )
                                                            dragOffsetY.animateTo(
                                                                0f,
                                                                spring(dampingRatio = 0.55f, stiffness = 300f)
                                                            )
                                                        }
                                                    }
                                                }
                                            ) { change, dragAmount ->
                                                scope.launch {
                                                    dragOffsetX.snapTo(dragOffsetX.value + dragAmount)
                                                }
                                            }
                                        } else {
                                            // 全向模式
                                            detectDragGestures(
                                                onDragEnd = {
                                                    val dx = dragOffsetX.value
                                                    val dy = dragOffsetY.value
                                                    val distance = hypot(dx, dy)
                                                    if (distance > thresholdPx) {
                                                        val flyX = if (dx.absoluteValue >= dy.absoluteValue) {
                                                            dx * 3f
                                                        } else {
                                                            dx
                                                        }
                                                        val flyY = if (dy.absoluteValue > dx.absoluteValue) {
                                                            dy * 3f
                                                        } else {
                                                            dy
                                                        }
                                                        scope.launch {
                                                            dragOffsetX.animateTo(
                                                                flyX,
                                                                spring(dampingRatio = 0.75f, stiffness = 200f)
                                                            )
                                                            onCardSwiped?.invoke(slot.originalIndex)
                                                            val newOrder = order.toMutableList()
                                                            val top = newOrder.removeAt(0)
                                                            newOrder.add(top)
                                                            order = newOrder
                                                            dragOffsetX.snapTo(0f)
                                                            dragOffsetY.snapTo(0f)
                                                        }
                                                    } else {
                                                        scope.launch {
                                                            dragOffsetX.animateTo(
                                                                0f,
                                                                spring(dampingRatio = 0.55f, stiffness = 300f)
                                                            )
                                                            dragOffsetY.animateTo(
                                                                0f,
                                                                spring(dampingRatio = 0.55f, stiffness = 300f)
                                                            )
                                                        }
                                                    }
                                                }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                scope.launch {
                                                    dragOffsetX.snapTo(dragOffsetX.value + dragAmount.x)
                                                    dragOffsetY.snapTo(dragOffsetY.value + dragAmount.y)
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
                                    loading = { DefaultImagePlaceholder(cardRadius) },
                                    error = { DefaultImagePlaceholder(cardRadius) }
                                )
                            } else {
                                DefaultImagePlaceholder(cardRadius)
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
 * 顶卡位置/角度/缩放样式（按栈内 index 计算）
 */
private data class CardStyle(
    val x: Dp,
    val y: Dp,
    val rotation: Float,
    val scale: Float
)

private fun computeCardStyle(
    stackIndex: Int,
    totalCards: Int,
    cardWidth: Dp,
    cardHeight: Dp,
    xOffset: Dp,
    tiltAngle: Float,
    tiltAngleStart: Float
): CardStyle {
    // 顶卡居中，下层依次上移（y 减小）+ 缩小
    val stackOffsetY = -(stackIndex * 8).dp
    val scale = 1f - stackIndex * 0.05f
    val rotation = if (totalCards > 1) {
        tiltAngleStart + (stackIndex.toFloat() / (totalCards - 1)) * (tiltAngle - tiltAngleStart)
    } else {
        tiltAngleStart
    }
    val xOff = if (totalCards > 1) {
        (stackIndex.toFloat() / (totalCards - 1)) * xOffset.value
    } else 0f
    return CardStyle(
        x = xOff.dp,
        y = stackOffsetY,
        rotation = rotation,
        scale = scale
    )
}

/**
 * 图片占位符（加载中 / 加载失败 / 缺图时显示）
 *
 * 与项目内 `InlineImagePreview` 保持视觉一致：浅灰背景 + 相机 emoji
 */
@Composable
private fun DefaultImagePlaceholder(cardRadius: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEEEEE), RoundedCornerShape(cardRadius)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📷",
            style = TextStyle(fontSize = 48.sp),
            color = Color.Gray.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
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
