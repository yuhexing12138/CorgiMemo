// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationDetailImageStack.kt
package com.corgimemo.app.ui.screens.inspiration.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.corgimemo.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ============================================================================
// 常量
//
// 堆叠参数与灵感首页 SwipeableImageStack.kt 保持一致（visibleDepth / scaleStep /
// fanAngle / 顶卡阴影 / 过渡规格 / 拖拽阈值 / 弹性阻尼 / spring 规格），
// 尺寸改为按详情页内容宽度自适应。
// 用户 2026-08-29 定稿：堆叠卡片边长 200dp、yOffset 4dp、展开宽 = 内容宽、
// 展开收起按钮 = 胶囊形，其余取默认值；滑动翻牌与首页一致。
// ============================================================================

/** 堆叠态卡片边长（用户选定 200dp） */
private val StackCardSize = 200.dp

/** 堆叠层间距 yOffset（用户选定 4dp） */
private val StackOffsetY = 4.dp

/** 每层缩放步进（与 SwipeableImageStack.kt 一致） */
private const val ScaleStep = 0.05f

/** 堆叠可见深度（与 SwipeableImageStack.kt 默认值一致） */
private const val VisibleDepth = 4

/** 扇形总角：源码 fanAngle = −(visibleDepth − 1) × 15 → 逐层步进恒为 15° */
private const val FanAngleDeg = -(VisibleDepth - 1) * 15f

/** 展开态图片间距 cardGap（与 SwipeableImageStack.kt 一致） */
private val CardGap = 8.dp

/** 图片圆角 */
private val CardRadius = 12.dp

/** 堆叠态顶卡阴影（与 SwipeableImageStack.kt TopCardShadowElevation 一致），下层减半 */
private val TopCardShadow = 8.dp

/** 堆叠态 Stage 顶部留白（容纳扇形与阴影上溢） */
private val StageTopPad = 8.dp

/** 堆叠态 Stage 底部留白（顶卡 8dp 阴影扩散） */
private val StageBottomPadStack = 24.dp

/** 展开态 Stage 底部留白 */
private val StageBottomPadExpanded = 4.dp

/**
 * 布局空间预借量（左右各扩这么多 dp）
 *
 * v2026-08-29 改造：放弃 Popup 方案（Popup 走 WindowManager 异步更新，1-2 帧滞后 +
 * 创建/首帧闪烁，且无法与灵感首页 SwipeableImageStack 的手感对齐）。改为「布局空间预借」：
 * Stage 左右各扩 130dp，顶卡可在 layout 空间内自由平移，不被父级 Box/Column 裁剪。
 * 量级对齐 SwipeableImageStack.kt 的 StackLeftCompensation=130dp。
 */
private val StackHorizontalCompensation = 130.dp

/** 按钮高度 */
private val ButtonHeight = 32.dp

/** 胶囊圆角 = 高 × 11/28（沿用列表页原型比例） */
private val ButtonRadius = ButtonHeight * 11f / 28f

/** 按钮文字 / 图标尺寸 */
private val ButtonTextSize = 12.sp
private val ButtonIconSize = 14.dp

/** 按钮背景 / 前景（与列表页展开按钮一致：#F2F3F5 @ 55% + #4F5660） */
private val ButtonBg = Color(0xFFF2F3F5)
private const val ButtonBgAlpha = 0.55f
private val ButtonFg = Color(0xFF4F5660)

/** 过渡规格（与 SwipeableImageStack.kt TRANSITION_400_SPEC 一致） */
private val TRANSITION_400 = tween<Float>(
    durationMillis = 400,
    easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
)

/** 角标 / 按钮淡入淡出规格（与 SwipeableImageStack.kt OPACITY_200_SPEC 一致） */
private val OPACITY_200 = tween<Float>(durationMillis = 200)

/** 图片未加载完成时的兜底宽高比 */
private const val FallbackAspectRatio = 4f / 3f

// ---- 拖拽 / 翻牌（全部与 SwipeableImageStack.kt 对齐）----

/** 翻牌阈值：拖动距离超过此值即翻牌（源码 swipeThreshold 默认 10.dp，对齐原型 10px） */
private val SwipeThreshold = 10.dp

/** 弹性阻尼系数：越接近弹性边界阻力越大（源码公式 resistance = 1 − 0.3 × t^1.5） */
private const val ElasticDamping = 0.3f

/**
 * 拖拽中顶卡放大**系数**（源码 whileDrag: scale 1.05）
 *
 * 注意是**乘数**不是增量，且必须配合 [PRESS_SCALE_SPRING] 平滑过渡——
 * 直接加减常量会在按下/松手瞬间产生跳变（200dp 卡片上 5% ≈ 10dp，肉眼非常明显）。
 */
private const val PressScaleFactor = 1.05f

/** 按压缩放 spring（源码：dampingRatio 0.866 / stiffness 300，保证进出都平滑无突变） */
private val PRESS_SCALE_SPRING = spring<Float>(dampingRatio = 0.866f, stiffness = 300f)

/** 回弹 spring（源码 BOUNCE_SPRING：dampingRatio 0.577 / stiffness 300） */
private val BOUNCE_SPRING = spring<Float>(dampingRatio = 0.577f, stiffness = 300f)

/** 翻牌 spring（源码 FLIP_SPRING：dampingRatio 0.577 / stiffness 300，带过冲 Q 弹） */
private val FLIP_SPRING = spring<Float>(dampingRatio = 0.577f, stiffness = 300f)

/** 下层重排 spring（源码：临界阻尼 dampingRatio = 1，零过冲，平稳顶进） */
private val REORDER_SPRING = spring<Float>(dampingRatio = 1f, stiffness = 300f)

/** 翻牌层级下沉 tween（源码 FLIP_ZINDEX_TWEEN：600ms LinearOutSlowIn） */
private val FLIP_ZINDEX_TWEEN = tween<Float>(durationMillis = 600, easing = LinearOutSlowInEasing)

// ============================================================================
// 几何计算（纯函数，便于核对）
// ============================================================================

/**
 * 图片区几何：堆叠态与展开态两端的尺寸与位置
 *
 * @property contentWidth 内容宽（= 详情页正文宽度，图片区宽度与之相等）
 * @property stackSize 堆叠态卡片边长（已按内容宽收敛，防窄屏溢出）
 * @property anchorY 堆叠态顶卡上边缘相对 Stage 顶部的偏移
 * @property stackedHeight 堆叠态 Stage 高度
 * @property expandedHeight 展开态 Stage 高度
 * @property expandedHeights 每张图展开态的高度（= 内容宽 / 原图宽高比）
 * @property expandedTops 每张图展开态的上边缘 y
 */
private data class StackGeom(
    val contentWidth: Dp,
    val stackSize: Dp,
    val anchorY: Dp,
    val stackedHeight: Dp,
    val expandedHeight: Dp,
    val expandedHeights: List<Dp>,
    val expandedTops: List<Dp>
)

/**
 * 计算图片区几何
 *
 * 展开态：宽 = 内容宽，高 = 宽 / 原图宽高比，纵向间距 [CardGap]。
 * 堆叠态：卡片 [StackCardSize] 见方，逐层上移 [StackOffsetY]、缩小 [ScaleStep]、
 * 旋转 fanAngle × (ei / (visibleDepth − 1))（分母用 visibleDepth 参数，逐层步进恒 15°）。
 * Stage 高度按扇形旋转包围盒 + 阴影余量推导，保证任何张数下都不裁剪。
 *
 * **索引约定**（翻牌后 order ≠ 原图顺序，两者必须分开）：
 * - [expandedHeights] 按**原图索引**存放各图高度
 * - [expandedTops] 按**显示顺序**（[order]）累加纵向位置
 *
 * @param order 当前堆叠顺序（displayIndex → 原图索引）
 */
private fun computeGeom(contentWidth: Dp, order: List<Int>, ratios: List<Float>): StackGeom {
    val cardCount = order.size
    val stackSize = minOf(StackCardSize, contentWidth)
    val denom = max(VisibleDepth - 1, 1)

    // ---- 展开态：纵向图片列 ----
    val expHeights = ratios.map { ar ->
        contentWidth / ar.coerceAtLeast(0.01f)
    }
    // 纵向位置必须按显示顺序累加：翻牌后第 displayIndex 张的高度是 expHeights[order[displayIndex]]
    val expTops = ArrayList<Dp>(cardCount)
    var y = 0.dp
    for (cardIdx in order) {
        expTops.add(y)
        y += expHeights[cardIdx] + CardGap
    }
    val expandedHeight = (y - CardGap).coerceAtLeast(0.dp) + StageBottomPadExpanded

    // ---- 堆叠态：扇形旋转包围盒 ----
    // 卡片上边缘 y = anchorY − ei × yOffset，中心 y（相对 anchorY）= stackSize/2 − ei × yOffset
    // 旋转 θ 后方卡半高 he = scale × stackSize × (cos|θ| + sin|θ|) / 2
    val m = min(VisibleDepth, max(1, cardCount))
    var maxUp = 0f
    var maxDown = 0f
    for (ei in 0 until m) {
        val scale = 1f - ei * ScaleStep
        val theta = FanAngleDeg * (ei.toFloat() / denom.toFloat())
        val rad = Math.toRadians(abs(theta).toDouble())
        val halfExtent = (scale * stackSize.value * (cos(rad) + sin(rad)) / 2f).toFloat()
        val centerY = stackSize.value / 2f - ei * StackOffsetY.value
        maxUp = max(maxUp, halfExtent - centerY)
        maxDown = max(maxDown, halfExtent + centerY)
    }
    val anchorY = StageTopPad + maxUp.dp
    val stackedHeight = anchorY + maxDown.dp + StageBottomPadStack

    return StackGeom(
        contentWidth = contentWidth,
        stackSize = stackSize,
        anchorY = anchorY,
        stackedHeight = stackedHeight,
        expandedHeight = expandedHeight,
        expandedHeights = expHeights,
        expandedTops = expTops
    )
}

/**
 * 单张卡片在某一态下的目标值
 *
 * @property zIndex 绘制层级，仅用于决定放置顺序（顶卡最后绘制 → 在最上层）
 */
private data class CardTarget(
    val width: Dp,
    val height: Dp,
    val x: Dp,
    val y: Dp,
    val rotationZ: Float,
    val scale: Float,
    val shadow: Dp,
    val zIndex: Float
)

/** 堆叠态目标值（与 SwipeableImageStack.kt calcCardTarget 的 stacked 分支一致） */
private fun stackedTarget(index: Int, g: StackGeom): CardTarget {
    val ei = min(index, VisibleDepth - 1)
    val denom = max(VisibleDepth - 1, 1)
    // V8.8 超深卡片沉底：index ≥ visibleDepth 的卡片夹在栈底
    val overflowDepth = max(0, index - (VisibleDepth - 1))
    return CardTarget(
        width = g.stackSize,
        height = g.stackSize,
        x = (g.contentWidth - g.stackSize) / 2f,
        y = (g.anchorY.value - ei * StackOffsetY.value).dp,
        rotationZ = FanAngleDeg * (ei.toFloat() / denom.toFloat()),
        scale = 1f - ei * ScaleStep,
        shadow = if (index == 0) TopCardShadow else TopCardShadow * 0.5f,
        zIndex = (VisibleDepth - ei - overflowDepth).toFloat()
    )
}

/**
 * 展开态目标值：宽 = 内容宽、高按原图比例、无旋转无缩放无阴影
 *
 * **两个索引必须分开用**（翻牌后 order ≠ 原图顺序，混用会导致图片高度错位）：
 * - [displayIndex]：决定**纵向位置**（图片列的排列顺序）
 * - [cardIndex]：决定**高度**（[StackGeom.expandedHeights] 按原图索引排列）
 *
 * @param displayIndex 显示顺序下标（受翻牌 order 影响）
 * @param cardIndex 原图在 imagePaths 中的索引
 */
private fun expandedTarget(displayIndex: Int, cardIndex: Int, g: StackGeom): CardTarget = CardTarget(
    width = g.contentWidth,
    height = g.expandedHeights[cardIndex],
    x = 0.dp,
    y = g.expandedTops[displayIndex],
    rotationZ = 0f,
    scale = 1f,
    shadow = 0.dp,
    zIndex = 1f
)

/** 两个目标值之间插值（zIndex 线性插值，用于展开/收起过渡） */
private fun lerpTarget(a: CardTarget, b: CardTarget, t: Float): CardTarget = CardTarget(
    width = lerp(a.width, b.width, t),
    height = lerp(a.height, b.height, t),
    x = lerp(a.x, b.x, t),
    y = lerp(a.y, b.y, t),
    rotationZ = a.rotationZ + (b.rotationZ - a.rotationZ) * t,
    scale = a.scale + (b.scale - a.scale) * t,
    shadow = lerp(a.shadow, b.shadow, t),
    zIndex = a.zIndex + (b.zIndex - a.zIndex) * t
)

/**
 * 翻牌过渡状态（对齐 SwipeableImageStack V8.6：从松手位置 spring 滑入队尾，不飞出屏幕）
 *
 * @property flippedCard 正在翻牌的卡片（原图索引）
 * @property from 松手瞬间的视觉快照（含 dragOffset 与按压缩放）
 * @property oldIndex 每张卡重排前的 displayIndex，供下层卡片平稳顶进
 */
private data class FlipState(
    val flippedCard: Int,
    val from: CardTarget,
    val oldIndex: Map<Int, Int>
)

// ============================================================================
// 组件
// ============================================================================

/**
 * 灵感详情页图片区：默认堆叠，点按钮向下展开为图片列
 *
 * **交互分工（迁移时易踩，两条链分开注册）**：
 * - 点**按钮** → 展开 / 收起
 * - 点**图片** → 走 [onImageClick] 进入图片附件页，**不**触发展开
 * - 堆叠态**左右拖动** → 翻牌（顶卡移到队尾），与灵感首页
 *   [com.corgimemo.app.ui.components.SwipeableImageStack] 行为一致
 *
 * 堆叠参数与灵感首页 SwipeableImageStack 对齐（visibleDepth / scaleStep / fanAngle /
 * 顶卡阴影 / 400ms 过渡 / 10dp 翻牌阈值 / 弹性阻尼 / spring 规格），
 * 展开态改为**纵向列**且恢复图片原始宽高比，宽度按详情页内容宽自适应。
 *
 * 手势用 `detectHorizontalDragGestures`：只消费水平分量，不拦截垂直事件，
 * 因此父级详情页卡片的 `verticalScroll` 仍可正常上下滚动（与首页嵌入时间线的做法一致）。
 *
 * @param imagePaths 图片路径列表
 * @param onImageClick 点击图片回调，参数为图片在 [imagePaths] 中的原始索引
 */
@Composable
fun InspirationDetailImageStack(
    imagePaths: List<String>,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * v2026-08-30 新增：拖动状态回调（true=正在拖动卡片）。
     * 父级（HorizontalPager 等）可用此回调临时禁用同级水平手势，
     * 避免卡片 pointerInput 与 Pager draggable 同时响应导致的「卡一下再回底」。
     * 默认空实现，不影响其他调用方。
     */
    onDragStateChange: (Boolean) -> Unit = {}
) {
    val count = imagePaths.size
    if (count == 0) return

    // 单张恒展开（与 SwipeableImageStack V8.10 singleCardMode 一致），无收起语义
    val singleMode = count == 1
    var expanded by remember { mutableStateOf(false) }
    val isExpanded = expanded || singleMode

    val progress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = TRANSITION_400,
        label = "detailImageStackProgress"
    )

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 堆叠顺序：displayIndex → 原图索引。翻牌时队首移到队尾（与首页 order 语义一致）
    var order by remember(imagePaths) { mutableStateOf(imagePaths.indices.toList()) }

    // 顶卡拖拽位移（px）
    val dragOffsetX = remember { Animatable(0f) }
    var isPressed by remember { mutableStateOf(false) }

    // 拖拽放大：必须是 spring 动画而非瞬时切换，否则按下/松手瞬间会看到卡片「跳一下」
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) PressScaleFactor else 1f,
        animationSpec = PRESS_SCALE_SPRING,
        label = "detailPressScale"
    )

    // 翻牌过渡：flipProgress 驱动被翻卡、reorderProgress 驱动下层顶进、flipZBoost 驱动层级下沉
    var flipState by remember { mutableStateOf<FlipState?>(null) }
    val flipProgress = remember { Animatable(1f) }
    // 关键修复：掐掉翻牌过冲「外插」。
    // 本组件堆叠态 x 与 index 无关、dyPx 仅 3×StackOffsetY，Δ 极小，pVelocity = (v·Δ)/|Δ|²
    // 在快速松手时会被放得极大，使 flipProgress 冲到负值 → 顶卡被放大到 1.3~1.6 倍并向外飞，
    // 表现为「快速甩一下闪一下」。加 bounds[0,1] 后即使 pVelocity 极大也只停在 0，
    // 卡片从松手位置平滑 spring 回队尾，不再放大/飞出。
    flipProgress.updateBounds(0f, 1f)
    val reorderProgress = remember { Animatable(1f) }
    val flipZBoost = remember { Animatable(0f) }

    // 为每张图创建 painter：key(path) 保证路径变化时正确重建
    val painters = imagePaths.map { path ->
        key(path) {
            rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(path)
                    .crossfade(true)
                    .build()
            )
        }
    }
    // 宽高比取值优先级：
    //   ① 文件头解析（inJustDecodeBounds，只读尺寸不解码，开销极低且必定触发重组）
    //   ② Coil painter 加载完成后的 intrinsicSize
    //   ③ 兜底 FallbackAspectRatio
    val fileRatios = imagePaths.map { path ->
        key(path) { rememberFileAspectRatio(path) }
    }
    val ratios = painters.mapIndexed { index, painter ->
        fileRatios[index] ?: painterAspectRatio(painter) ?: FallbackAspectRatio
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 展开 / 收起按钮：图片区上方、水平居中（单张不显示）
        if (!singleMode) {
            PillToggleButton(
                expanded = isExpanded,
                count = count,
                onClick = { expanded = !expanded }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Box(
            // v2026-08-30 修复：BoxWithConstraints 自身 RenderNode 默认 clip=true，
            // 会把 GalleryStage 拖出 BoxWithConstraints 边界的顶卡裁掉（出现
            // 「卡在 BoxWithConstraints 左缘再回底」的现象）。外层包一个
            // graphicsLayer{clip=false} 的 Box，让 GalleryStage 可溢出 BoxWithConstraints
            // bounds 继续绘制到 Column/InspirationViewCard 边界（形成完整不裁剪链）。
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { this.clip = false }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val geom = computeGeom(maxWidth, order, ratios)
                GalleryStage(
                progress = progress,
                geom = geom,
                order = order,
                painters = painters,
                onImageClick = onImageClick,
                badgeVisible = !isExpanded && count >= 2,
                count = count,
                // ---- 拖拽 / 翻牌状态 ----
                dragEnabled = !singleMode && !isExpanded,
                dragOffsetX = dragOffsetX.value,
                isPressed = isPressed,
                pressScale = pressScale,
                flipState = flipState,
                flipProgress = flipProgress.value,
                reorderProgress = reorderProgress.value,
                flipZBoost = flipZBoost.value,
                onTopCardDrag = { deltaPx ->
                    // 弹性阻尼：t = |offset| / maxElastic，resistance = 1 − 0.3 × t^1.5
                    val thresholdPx = with(density) { SwipeThreshold.toPx() }
                    val maxElasticPx = max(thresholdPx * 4f, with(density) { geom.stackSize.toPx() })
                    val t = (abs(dragOffsetX.value) / maxElasticPx).coerceIn(0f, 1f)
                    val resistance = 1f - ElasticDamping * t.pow(1.5f)
                    dragOffsetX.snapTo(dragOffsetX.value + deltaPx * resistance)
                },
                onDragStart = {
                    isPressed = true
                    dragOffsetX.snapTo(0f)
                },
                onDragEnd = { velocityX -> // px/s，用于翻牌 spring 初速度
                    // 先快照按压缩放：必须在 isPressed=false 触发 PRESS_SCALE_SPRING 回落之前读取，
                    // 否则翻牌起点 scale 会少一截，与最后一帧实际渲染不一致 → 松手瞬间小跳。
                    val pressedScale = pressScale
                    isPressed = false
                    val thresholdPx = with(density) { SwipeThreshold.toPx() }
                    val distance = abs(dragOffsetX.value)
                    // flipState != null 表示上一次翻牌尚未结束，此时只回弹不重排，
                    // 避免覆盖进行中的过渡状态造成视觉跳变
                    if (distance > thresholdPx && count > 1 && flipState == null) {
                        scope.launch {
                            val oldIndexMap = order.withIndex().associate { it.value to it.index }
                            val topCard = order.first()
                            val topTarget = stackedTarget(0, geom)
                            // ① 快照松手位置的视觉状态（含 dragOffset 与按压缩放）
                            val snapshot = topTarget.copy(
                                x = topTarget.x + with(density) { dragOffsetX.value.toDp() },
                                rotationZ = 0f,
                                // 用松手前快照的按压缩放（pressedScale），与拖拽中的视觉连续，避免起点跳变
                                scale = topTarget.scale * pressedScale
                            )
                            // ② 层级补偿：起点保持顶卡层级，终点沉到队尾层级
                            val tailZ = stackedTarget(count - 1, geom).zIndex
                            // ③ 松手速度映射到 p 空间（对齐首页 V8.7c）：
                            //    p 空间初速度 = (v·Δ) / |Δ|²，Δ = 队尾 − 快照（px）
                            val tailTarget = stackedTarget(count - 1, geom)
                            val dxPx = with(density) { (tailTarget.x - snapshot.x).toPx() }
                            val dyPx = with(density) { (tailTarget.y - snapshot.y).toPx() }
                            val denom = dxPx * dxPx + dyPx * dyPx
                            // 同上：本组件 Δ 极小，denom 天然极小，首页公式会把 pVelocity 放得极大。
                            // flipProgress 已被 bounds 掐掉外插，这里再对 pVelocity 限幅，
                            // 让慢速/快速松手下的翻牌节奏一致（不会因手速突兀地猛甩一下）。
                            val rawPV = if (denom > 1f) (velocityX * dxPx) / denom else 0f
                            val pVelocity = rawPV.coerceIn(-16f, 16f)

                            flipProgress.snapTo(0f)
                            reorderProgress.snapTo(0f)
                            flipZBoost.snapTo(topTarget.zIndex - tailZ)
                            flipState = FlipState(topCard, snapshot, oldIndexMap)

                            // ④ 立即重排：顶卡移到队尾（视觉由快照插值保持连续，不飞出屏幕）
                            val newOrder = order.toMutableList()
                            newOrder.add(newOrder.removeAt(0))
                            order = newOrder
                            dragOffsetX.snapTo(0f)

                            // ⑤ 位置插值与层级下沉并行
                            coroutineScope {
                                val j1 = launch { flipProgress.animateTo(1f, FLIP_SPRING, pVelocity) }
                                val j2 = launch { reorderProgress.animateTo(1f, REORDER_SPRING) }
                                launch { flipZBoost.animateTo(0f, FLIP_ZINDEX_TWEEN) }
                                joinAll(j1, j2)
                            }
                            flipState = null
                        }
                    } else {
                        // 未超阈值：spring 弹回中心
                        scope.launch { dragOffsetX.animateTo(0f, BOUNCE_SPRING) }
                    }
                },
                // v2026-08-30 透传拖动状态回调到 GalleryStage → pointerInput
                onDragStateChange = onDragStateChange,
                modifier = Modifier
            )
        }
        }  // 关闭外层 graphicsLayer{clip=false} 的 Box（与 464 行 Box( 对应）
    }
}

/**
 * 图片舞台：堆叠 / 展开两态共用一批卡片，按 [progress] 插值位置、尺寸、旋转、缩放与阴影
 *
 * 用 Layout 精确控制每张卡片的测量尺寸与放置位置：
 * - 尺寸：不可用非等比 scaleX/scaleY 模拟，否则图片内容被拉伸变形；
 *   这里按插值后的真实宽高 measure，配合 ContentScale.Crop 裁剪填充，视觉不变形。
 * - 变换：rotation / scale / 阴影走 placeWithLayer（绘制层），不改变布局。
 */
@Composable
private fun GalleryStage(
    progress: Float,
    geom: StackGeom,
    order: List<Int>,
    painters: List<Painter>,
    onImageClick: (Int) -> Unit,
    badgeVisible: Boolean,
    count: Int,
    dragEnabled: Boolean,
    dragOffsetX: Float,
    isPressed: Boolean,
    pressScale: Float,
    flipState: FlipState?,
    flipProgress: Float,
    reorderProgress: Float,
    flipZBoost: Float,
    onTopCardDrag: suspend (Float) -> Unit,
    onDragStart: suspend () -> Unit,
    onDragEnd: suspend (velocityX: Float) -> Unit,
    // v2026-08-30 新增：透传拖动状态回调，供 pointerInput lambda 通知父级 HorizontalPager
    onDragStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragOffsetDp = with(density) { dragOffsetX.toDp() }
    // 展开态（progress ≥ 0.5）不叠加拖拽位移，与展开态禁用拖拽的判定保持一致
    val isStackedMode = progress < 0.5f

    // ---- v2026-08-30 多层 RenderNode clip=false 链（修复居中失败与裁剪卡顿）----
    // v2026-08-29 第一版「布局空间预借」用 Stage `offset(-comp) + requiredWidth(contentWidth+comp*2)`
    // 组合（130dp 水平补偿 + clipRect 限位），但有两个未发现的裁剪源导致真机仍卡：
    //   ① requiredWidth(>父 maxWidth=contentWidth) 被 BoxWithConstraints 截断到 contentWidth，
    //      而 offset(-comp) 让 Stage 渲染位置左移 130dp → **堆叠区偏左 130dp**（居中失败）；
    //   ② Column（InspirationViewCard）/ BoxWithConstraints / ContentWrap 三层 RenderNode
    //      自身默认 clip=true，按层级链依次裁掉卡片溢出边界的部分 → 拖到一定距离时
    //      卡片在裁剪边界被突然截断（"卡一下再回底"），clipRect 拓到 ±130dp 反而成为
    //      新的视觉突变点。
    // 修复：放弃 offset/requiredWidth 预借，让 Stage `fillMaxWidth()` 与父级 content 居中；
    // 在 Column → Box(wrap BoxWithConstraints) → Stage Box → ContentWrap Box 四层
    // 全部加 `graphicsLayer { clip = false }`，彻底打通不裁剪链；卡片可自由拖到
    // InspirationViewCard 边界（再外被 Pager 等兜底裁回页面边界，不会污染屏幕其他区域）。
    // 单一同步渲染路径 + 父链全不裁 → 与灵感首页 SwipeableImageStack 手感完全一致。
    val stageHeight = lerp(geom.stackedHeight, geom.expandedHeight, progress)

    // 逐卡目标值
    val targets = order.mapIndexed { displayIndex, cardIdx ->
        val stacked = stackedTarget(displayIndex, geom)
        val expanded = expandedTarget(displayIndex, cardIdx, geom)
        val base = lerpTarget(stacked, expanded, progress)

        // ---- 翻牌过渡优先 ----
        val fs = flipState
        if (fs != null) {
            if (cardIdx == fs.flippedCard) {
                // 被翻的卡：松手快照 → 队尾新位置（FLIP_SPRING，带过冲）
                val t = lerpTarget(fs.from, base, flipProgress)
                return@mapIndexed t.copy(zIndex = base.zIndex + flipZBoost)
            }
            val dOld = fs.oldIndex[cardIdx]
            if (dOld != null && dOld != displayIndex) {
                // 下层卡片：旧位置 → 新位置（临界阻尼，平稳顶进无过冲）
                val oldBase = lerpTarget(
                    stackedTarget(dOld, geom),
                    expandedTarget(dOld, cardIdx, geom),
                    progress
                )
                return@mapIndexed lerpTarget(oldBase, base, reorderProgress)
            }
        }

        // ---- 顶卡：叠加拖拽位移 + 按下放大（spring 平滑）+ 旋转归零 ----
        if (displayIndex == 0 && isStackedMode) {
            return@mapIndexed base.copy(
                x = base.x + dragOffsetDp,
                rotationZ = if (isPressed) 0f else base.rotationZ,
                // 乘 pressScale（1 → 1.05 由 spring 驱动），不要直接加减常量
                scale = base.scale * pressScale
            )
        }
        base
    }
    // 绘制顺序：zIndex 升序 → 顶卡最后绘制，位于最上层（与源码 zIndex 语义一致）
    val drawOrder = order.indices.sortedBy { targets[it].zIndex }

    // 顶卡「静止锚点」：不含拖拽位移、也不参与翻牌过渡插值。
    // 角标定位用它 —— 保证角标固定不动、拖拽起点位置正确。
    // 顶卡恒无旋转（ei=0），视觉矩形 = 中心不变、边长 × scale
    val topStill = lerpTarget(
        stackedTarget(0, geom),
        expandedTarget(0, order.first(), geom),
        progress
    )

    // 角标「当前顶卡序号/总数」：位置固定在顶卡静止锚点的右下角（不随拖拽移动），
    // 但**计数跟随翻牌变化**——order.first() 是当前顶卡的原图索引，+1 转 1-based。
    val centerX = topStill.x + topStill.width / 2f
    val visualHalfH = topStill.height * topStill.scale / 2f
    val badgeY = topStill.y + topStill.height / 2f + visualHalfH - 18.dp
    val badgeX = centerX + topStill.width * topStill.scale / 2f + 3.dp
    val badgeAlpha by animateFloatAsState(
        targetValue = if (badgeVisible) 1f else 0f,
        animationSpec = OPACITY_200,
        label = "detailBadgeAlpha"
    )

    // Stage Box：与父级 content 居中对齐（fillMaxWidth = contentWidth），最外层 RenderNode
    // 不裁剪。卡片溢出 Column/BoxWithConstraints/Stage 边界的部分会沿 clip=false 链
    // 一直绘制到 InspirationViewCard Box 边界（再外被 Pager 等兜底裁回页面边界，
    // 不会污染屏幕其他区域）。
    Box(
        modifier = modifier
            .fillMaxWidth()  // = contentWidth，与父级 content 居中对齐
            .requiredHeight(stageHeight)
            .graphicsLayer { this.clip = false }
    ) {
        // 内容包装层：fillMaxWidth = Stage 宽。graphicsLayer{clip=false} 让其子
        // （Layout + placeables）可溢出 ContentWrap bounds 继续绘制。不再用 clipRect：
        // 之前 clipRect 拓到 -130dp 是为了在父级裁剪存在时给卡片留溢出空间，但水平
        // 方向反而成为新的"卡顿点"（拖到 clipRect 边界外卡片被裁断）。现在父链完全
        // 不裁，卡片可自由拖到 InspirationViewCard 边界。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(stageHeight)
                .graphicsLayer { this.clip = false }
        ) {
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    order.forEachIndexed { displayIndex, cardIdx ->
                        val dragModifier = if (dragEnabled && displayIndex == 0) {
                            Modifier.pointerInput(order.first(), dragEnabled) {
                                val velocityTracker = VelocityTracker()
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        velocityTracker.resetTracking()
                                        // v2026-08-30 新增：通知父级「正在拖动」，
                                        // 父级 HorizontalPager 临时禁用 userScrollEnabled，
                                        // 避免双层水平手势竞争导致的「卡一下再回底」
                                        onDragStateChange(true)
                                        scope.launch { onDragStart() }
                                    },
                                    onDragEnd = {
                                        val vx = velocityTracker.calculateVelocity().x
                                        onDragStateChange(false)
                                        scope.launch { onDragEnd(vx) }
                                    },
                                    onDragCancel = {
                                        onDragStateChange(false)
                                        scope.launch { onDragEnd(0f) }
                                    }
                                ) { change, dragAmount ->
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    // snapTo 是 suspend，用外部 scope 启动
                                    scope.launch { onTopCardDrag(dragAmount) }
                                }
                            }
                        } else {
                            Modifier
                        }
                        Image(
                            painter = painters[cardIdx],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // pointerInput 先于 clickable 应用 → 水平拖拽优先消费，
                            // 短按（未超 touchSlop）仍会走到 clickable 打开附件页
                            modifier = dragModifier
                                .clickable { onImageClick(cardIdx) }
                        )
                    }
                }
            ) { measurables, constraints ->
                // 按插值后的真实宽高测量（measure 阶段可读取 Density 与 State）
                val placeables = measurables.mapIndexed { index, measurable ->
                    val t = targets[index]
                    measurable.measure(
                        Constraints.fixed(
                            width = t.width.roundToPx(),
                            height = t.height.roundToPx()
                        )
                    )
                }
                // 阴影需要 px，提前在 Density 可用的作用域内换算
                val shadowPx = targets.map { it.shadow.toPx() }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    drawOrder.forEach { index ->
                        val t = targets[index]
                        placeables[index].placeWithLayer(
                            x = t.x.roundToPx(),
                            y = t.y.roundToPx()
                        ) {
                            rotationZ = t.rotationZ
                            scaleX = t.scale
                            scaleY = t.scale
                            transformOrigin = TransformOrigin.Center
                            shape = RoundedCornerShape(CardRadius)
                            // 只裁图片到圆角，卡片平移超出 Layout bounds 不被裁（布局空间预借）
                            clip = true
                            shadowElevation = shadowPx[index]
                        }
                    }
                }
            }

            // 角标（位于内容包装层内，坐标与卡片同一参考系）
            CountBadge(
                text = stringResource(
                    R.string.inspiration_view_image_counter,
                    order.first() + 1,
                    count
                ),
                modifier = Modifier
                    .graphicsLayer { alpha = badgeAlpha }
                    .offsetBadge(x = badgeX, y = badgeY)
            )
        }
    }
}

/** 角标定位（y 可能为负时钳制到 0，避免溢出 Stage 顶部） */
private fun Modifier.offsetBadge(x: Dp, y: Dp): Modifier =
    this.offset(x = x, y = y.coerceAtLeast(0.dp))

/** 张数角标：1/N */
@Composable
private fun CountBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier = modifier
            .background(
                color = Color(0xFF000000).copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * 读取图片文件的原始宽高比（异步）
 *
 * 用 `BitmapFactory` + `inJustDecodeBounds = true` **只解析图片尺寸、不解码整图**，
 * 开销极低。灵感图片均为本地文件路径（与图片附件页 `BitmapFactory.decodeFile` 用法一致）。
 *
 * 走 [produceState]：解析完成后赋值必定触发重组，展开态高度随即更新为真实比例。
 *
 * @return 宽高比（width / height）；路径不可用或解析失败时返回 null
 */
@Composable
private fun rememberFileAspectRatio(path: String): Float? {
    return produceState<Float?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.outWidth.toFloat() / options.outHeight.toFloat()
                } else {
                    null
                }
            }.getOrNull()
        }
    }.value
}

/**
 * 从 Coil painter 读取宽高比（图片加载完成后可用）
 *
 * 作为 [rememberFileAspectRatio] 的兜底：painter 尚未就绪时返回 null。
 */
private fun painterAspectRatio(painter: Painter): Float? {
    val size = painter.intrinsicSize
    val w = size.width
    val h = size.height
    return if (w.isFinite() && h.isFinite() && w > 0f && h > 0f) w / h else null
}

/**
 * 胶囊形展开 / 收起按钮
 *
 * 内容 = 文字 + 张数 + 箭头（「展开 4」/「收起 4」），宽度由内容撑开、高度固定。
 * 圆角 = [ButtonRadius]（高 × 11/28），展开态箭头旋转 180°（↓ → ↑）。
 */
@Composable
private fun PillToggleButton(
    expanded: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (expanded) {
        stringResource(R.string.inspiration_view_collapse_images, count)
    } else {
        stringResource(R.string.inspiration_view_expand_images, count)
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = TRANSITION_400,
        label = "detailToggleArrow"
    )

    Row(
        modifier = modifier
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(ButtonBg.copy(alpha = ButtonBgAlpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            fontSize = ButtonTextSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            color = ButtonFg
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = ButtonFg,
            modifier = Modifier
                .size(ButtonIconSize)
                .graphicsLayer { rotationZ = arrowRotation }
        )
    }
}
