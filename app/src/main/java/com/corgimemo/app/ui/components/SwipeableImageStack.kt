package com.corgimemo.app.ui.components

/**
 * 堆叠图组件：4 区 PINNED_PENDING / PENDING / PINNED_COMPLETED / COMPLETED 的图片/色块堆叠展示与展开。
 *
 * ## 当前架构（单共享实例动画）
 * - 单一 SharedCardRow → OneSharedCard(key=stableId) 循环驱动；Positional Memoization 跨堆叠/展开包裹层复用实例。
 * - 单一 expandProgress Animatable（0f↔1f）→ calcCardTarget 返回 x/y/rotationZ/scale/shadowElevation/zIndex。
 * - 三层绝对定位并列：Layer-1（卡片容器，双包裹切换）/ Layer-2（CollapseBtn 全局）/ Layer-3（ExpandBtn+Badge）；
 * - 方案A 手势互斥锁：堆叠态挂载扇形 pointerInput / 过渡中分离 / 展开态挂载单卡点击 pointerInput（三分支条件挂载）。
 *
 * ## 巨石组件拆分建议（当前 ≥ 1500 行，符合 .trae/rules/巨石组件拆分规范.md 触发条件）
 * 建议按 model / sections / dialogs 三包结构拆分：
 * ```
 * ui/components/SwipeableImageStack.kt        → 薄壳 Facade（< 150 行，typealias + 转发）
 * ui/components/swipeableimagestack/
 *     ├── model/                              # 纯数据
 *     │   ├── CardSlot.kt                     # data class CardSlot（当前 private data class）
 *     │   ├── CardTarget.kt                   # data class CardTarget + lerp
 *     │   ├── SwipeDirection.kt               # enum SwipeDirection
 *     │   └── StackAnimSpec.kt                # const val TRANSITION_400_SPEC / OPACITY_200_SPEC + 常量
 *     ├── sections/
 *     │   ├── SwipeableImageStackContentImpl.kt  # 主入口实现（Stage Box + 三层并列）
 *     │   ├── SharedCardRow.kt                # SharedCardRow + OneSharedCard 渲染层
 *     │   ├── Layer1CardWrapper.kt            # 双包裹切换（堆叠态/展开态）
 *     │   ├── Layer2CollapseButton.kt         # Layer-2 收起按钮
 *     │   └── Layer3ExpandBadge.kt            # Layer-3 展开按钮 + 图片角标
 *     └── dialogs/（若有长期菜单/配置弹窗才建，暂可空）
 * ```
 * 拆分收益：PR 冲突率低 / 每 section 可独立 preview 与单测 / 共享子组件跨分区复用 / 可读性显著提升。
 */

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import com.corgimemo.app.ui.theme.UiColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * V7.0 布局空间预借：堆叠图整条渲染链向左扩展的补偿量（dp）
 *
 * 背景：顶卡左滑依赖 graphicsLayer translationX（绘制层平移），内容会绘制到
 * 各级父容器（卡片 Box / Layer1 / Stage / WRAPPER / OUTER_BOX）的 layout bounds
 * 之外。V5.x~V6.x 尝试给整条链加 graphicsLayer{clip=false} 均无法消除裁剪
 * （clip=false 本就是 RenderNode 默认值，真正的裁剪机制不受它控制）。
 *
 * V7.0 思路：不再对抗裁剪系统，改为「布局空间预借」—— 把卡片 Box、Layer1、
 * Stage 三层容器的 layout bounds 向左扩展本常量，卡片内容用 offset 补偿回原
 * 视觉位置。这样顶卡左滑的整个轨迹（拖拽弹性最大 120dp + 卡片距屏幕左缘
 * ~102dp）始终落在各级容器的 layout bounds 之内，无论裁剪机制是什么都无从
 * 裁起（damage region / bounds 剔除 / 任何形式的裁剪都只作用于 bounds 之外）。
 *
 * 数值来源：卡片静止视觉位置距屏幕左缘 ≈102dp（88dp 内容区 + 14dp 居中偏移），
 * 加上弹性滑动余量，取 130dp（> 102 + 27 冗余）。
 *
 * 联动改动（见各使用处 V7.0 注释）：
 * - OneSharedCard：卡片 Box 宽度 + 本量，内容 offset 补偿
 * - Layer1CardWrapper：Layer1 宽度 + 本量、offset 左移，Center 对齐数学自洽
 * - Stage：宽度 + 本量、offset 左移，内容包装层 offset 补偿
 * - TimelineInspirationItem：OUTER_BOX offset(-18dp) 对齐屏幕左缘、
 *   WRAPPER 删除 translationX、SwipeableImageStack 传 stackStageOffsetX
 */
internal val StackLeftCompensation = 130.dp

/**
 * V7.6 展开态行滚动：越界橡皮筋阻尼系数
 *
 * 拖拽超出滚动有效区间 [minScroll, 0] 时，越界部分的位移按此比例衰减渲染——
 * 手指继续拖动位移增长变慢（拉伸感），松手后 spring 弹回有效边界。
 */
private const val ROW_OVERSCROLL_DAMPING = 0.35f

/**
 * V7.6 展开态行滚动：橡皮筋阻尼函数
 *
 * - raw ∈ [minScroll, 0]：正常滚动区间，原样返回
 * - raw > 0（左端越界）：越界部分 × [ROW_OVERSCROLL_DAMPING]
 * - raw < minScroll（右端越界）：越界部分 × [ROW_OVERSCROLL_DAMPING]
 */
private fun rubberBandRowScroll(raw: Float, minScroll: Float): Float = when {
    raw > 0f -> raw * ROW_OVERSCROLL_DAMPING
    raw < minScroll -> minScroll + (raw - minScroll) * ROW_OVERSCROLL_DAMPING
    else -> raw
}

/**
 * 堆叠中的单张卡片槽位
 *
 * - `stableId`：作为 Composable key 永不变化，避免重组时丢失状态
 * - `originalIndex`：图片在原始 `imageUris` 列表中的下标，
 *   滑动重排后保持不变，用于 `onCardSwiped` 回调让业务方知道是哪张图
 */
private data class CardSlot(val stableId: Long, val originalIndex: Int)

/**
 * 卡片目标变换状态
 *
 * 描述单张卡片在堆叠态/展开态混合插值过程中的目标位置和外观。
 * 所有字段统一描述「最终视觉目标」，由 [calcCardTarget] 根据 expandProgress 生成。
 *
 * @property x 水平位移（Dp）
 * @property y 垂直位移（Dp）
 * @property rotationZ 绕 Z 轴旋转角度（度）
 * @property scale 缩放比例（1.0 = 原始尺寸）
 * @property shadowElevation 阴影高度（Dp）
 * @property zIndex 层叠顺序（越大越在上层）
 */
private data class CardTarget(
    val x: Dp,
    val y: Dp,
    val rotationZ: Float,
    val scale: Float,
    val shadowElevation: Dp,
    val zIndex: Float,
)

/**
 * Dp 线性插值函数
 *
 * 在两个 Dp 值之间按比例 p 进行线性插值，结果自动 clamp 到 [0,1] 范围，
 * 避免因动画进度溢出导致的数值越界。
 *
 * @param a 起始 Dp 值
 * @param b 结束 Dp 值
 * @param p 插值进度 [0,1]
 * @return 插值后的 Dp 结果
 */
private fun lerp(a: Dp, b: Dp, p: Float): Dp =
    (a.value + (b.value - a.value) * p.coerceIn(0f, 1f)).dp

/**
 * 计算单张卡片在堆叠态↔展开态过渡过程中的目标变换
 *
 * 核心算法：
 * 1. 根据 displayIndex 分别计算「纯堆叠态 stacked」和「纯展开态 expanded」两个端点
 * 2. 以 expandProgress ∈ [0,1] 对两个端点的各字段分别做线性插值
 * 3. zIndex 在进度 < 0.5 时取堆叠态排序（顶卡在上），≥ 0.5 时取展开态同级（避免动画中层级跳变）
 *
 * 堆叠态端点公式：
 * - y 方向：每深一层向上偏移 stackOffsetDp（使卡片底部露出形成扇形底边）
 * - rotationZ：按可见深度线性分配旋转角（顶层 0°，底层 fanAngleDeg）
 * - scale：每深一层缩小 scaleStep
 * - shadowElevation：顶卡 8dp，其余 4dp
 * - zIndex：顶卡最高（visibleDepth - ei），保证堆叠顺序正确
 *
 * 展开态端点公式：
 * - x 方向：按卡片索引水平排列，索引 × (卡片宽度 + 间距)
 * - y/rotation/scale：全部归中（无旋转、原始尺寸、同一水平高度）
 * - shadowElevation：统一 4dp（展开态无顶卡/非顶卡视觉差异）
 * - zIndex：全部同级（展开态层叠顺序无意义）
 *
 * @param displayIndex 卡片在当前显示顺序中的索引（0 = 顶卡）
 * @param cardCount 卡片总数
 * @param expandProgress 展开进度 0f（堆叠）→ 1f（展开）
 * @param cardW 单张卡片宽度
 * @param cardGap 展开态卡片间距
 * @param visibleDepth 可见深度（最多参与扇形展开的卡片数）
 * @param stackOffsetDp 堆叠态每层垂直上移量
 * @param fanAngleDeg 堆叠态底层最大旋转角
 * @param scaleStep 堆叠态每层缩放步进（每深一层 scale 减少 scaleStep）
 * @return 插值后的卡片目标变换状态
 */
private fun calcCardTarget(
    displayIndex: Int,
    cardCount: Int,
    expandProgress: Float,
    cardW: Dp,
    cardGap: Dp,
    visibleDepth: Int,
    stackOffsetDp: Dp,
    fanAngleDeg: Float,
    scaleStep: Float,
): CardTarget {
    val ei = min(displayIndex, visibleDepth - 1)
    val denom = max(visibleDepth - 1, 1)
    val stacked = CardTarget(
        x = 0.dp,
        y = (-ei * stackOffsetDp.value).dp,
        rotationZ = fanAngleDeg * (ei.toFloat() / denom.toFloat()),
        scale = 1f - ei * scaleStep,
        shadowElevation = if (displayIndex == 0) 8.dp else 4.dp,
        zIndex = (visibleDepth - ei).toFloat()
    )
    val expanded = CardTarget(
        x = (displayIndex.toFloat() * (cardW.value + cardGap.value)).dp,
        y = 0.dp,
        rotationZ = 0f,
        scale = 1f,
        shadowElevation = 4.dp,
        zIndex = 1f
    )
    val p = expandProgress.coerceIn(0f, 1f)
    return CardTarget(
        x = lerp(stacked.x, expanded.x, p),
        y = lerp(stacked.y, expanded.y, p),
        rotationZ = stacked.rotationZ + (expanded.rotationZ - stacked.rotationZ) * p,
        scale = stacked.scale + (expanded.scale - stacked.scale) * p,
        shadowElevation = lerp(stacked.shadowElevation, expanded.shadowElevation, p),
        zIndex = if (p < 0.5f) stacked.zIndex else expanded.zIndex
    )
}

/**
 * 默认色块图片（与 Originkit 原型 `DEFAULT_IMAGES` 兜底逻辑对齐）
 *
 * - 原型使用 4 张外网图片（imagedelivery.net）作为默认占位
 * - Android 端改用本地色块（用户决策），避免网络依赖
 * - 4 色覆盖暖→冷光谱，与项目 [Color.kt] 色板一致
 *
 * 触发条件：调用方未传 `imageUris` 且未传 `customContent` 时自动启用，
 * 严格对齐原型 `Array.isArray(images) && images.length > 0 ? images : DEFAULT_IMAGES` 行为
 */
private val DEFAULT_IMAGE_COLORS = listOf(
    Color(0xFFFF9A5C),  // 暖橙
    Color(0xFFFFB5C2),  // 粉
    Color(0xFF7EC8A0),  // 绿
    Color(0xFF7EB8DA)   // 蓝
)

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
 * @param cardRadius 卡片圆角（默认 2f，嵌入时间线缩略图建议传 12.dp）
 * @param swipeThreshold 拖动距离阈值（默认 10.dp，对齐原型 10px；mdpi 设备 1dp=1px）
 * @param maxElasticDistance 弹性边界（默认 0.dp = 自动：max(threshold*4, cardWidth)）；
 *                           显式传值时覆盖默认，便于调用方微调阻力边界
 * @param tiltAngleStart 堆叠首端旋转角度（默认 0f）
 * @param xOffset 堆叠末端水平偏移（默认 0.dp，扇形展开幅度）
 * @param yOffset 堆叠态每层垂直上移量（默认 4.dp，原型对齐参数）
 * @param visibleDepth 可见深度：最多参与扇形展开的卡片数（默认 4，固定上限 4）。
 *                     仅前 M = min(visibleDepth, cardCount) 张展开成扇形，超出部分夹在栈底。
 *                     堆叠末端旋转角由可见张数派生：M=1→0°、M=2→-15°、M=3→-30°、M=4→-45°
 *                     （即 effectiveTiltAngle = -(M-1) * 15°），不再由外部固定 tiltAngle 参数传入。
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
    cardRadius: Float = 2f,
    swipeThreshold: Dp = 10.dp,
    maxElasticDistance: Dp = 0.dp,
    tiltAngleStart: Float = 0f,
    xOffset: Dp = 0.dp,
    yOffset: Dp = 4.dp,
    visibleDepth: Int = 4,
    swipeDirection: SwipeDirection = SwipeDirection.Horizontal,
    countBadge: Boolean = false,
    showExpandButton: Boolean = false,
    cardGap: Dp = 8.dp,                                  // 透传：展开态卡片间距（堆叠态不使用）
    onExpandStateChange: ((Boolean) -> Unit)? = null,    // 透传：展开/收起状态变化回调
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
        tiltAngleStart = tiltAngleStart,
        xOffset = xOffset,
        yOffset = yOffset,
        visibleDepth = visibleDepth,
        swipeDirection = swipeDirection,
        countBadge = countBadge,
        showExpandButton = showExpandButton,
        cardGap = cardGap,                                // 透传
        onExpandStateChange = onExpandStateChange,       // 透传
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
    cardRadius: Float = 2f,
    swipeThreshold: Dp = 10.dp,
    maxElasticDistance: Dp = 0.dp,
    tiltAngleStart: Float = 0f,
    xOffset: Dp = 0.dp,
    yOffset: Dp = 4.dp,
    visibleDepth: Int = 4,
    swipeDirection: SwipeDirection = SwipeDirection.Horizontal,
    countBadge: Boolean = false,
    showExpandButton: Boolean = false,
    // ↓↓↓ 本次新增 ↓↓↓
    cardGap: Dp = 8.dp,                                  // 展开态卡片间距（堆叠态不使用）
    onExpandStateChange: ((Boolean) -> Unit)? = null,    // 展开/收起状态变化回调
    /** 展开态整行起始 padding（与标题/时间/标签行的左侧对齐基准）
     *  - TimelineInspirationItem 场景应传入 contentStartX（≈70dp），
     *    使展开行起点 = 标题行起点，视觉上纵向对齐同一列。
     *  - 堆叠态不使用此值（完全保留 V1.0 坐标，Badge/ExpandBtn 位置不变）。*/
    expandedContentStartPadding: Dp = 0.dp,
    /** 外部 isExpanded 状态托管（可选）。
     *  - 传 null（默认）：组件内部 remember 自管（向后兼容所有调用点）。
     *  - 传 MutableState：调用方（如 TimelineInspirationItem）统一托管状态，
     *    便于在组件**外部**放置 CollapseBtn（独立一行，padding start 与标题一致）。*/
    expandedState: MutableState<Boolean>? = null,
    /** 是否渲染内置 CollapseBtn。
     *  - 当外部自己放了 CollapseBtn（expandedState 托管 + 独立行）时，传 false 去重。*/
    showInnerCollapseButton: Boolean = true,
    /** V7.0 布局空间预借：Stage 在「展开态」相对父容器的水平偏移（视觉基准位置）。
     *  - 堆叠态 Stage 实际 offset = 本值 - [StackLeftCompensation]（左扩 130dp 覆盖滑动轨迹），
     *    展开态 = 本值（视觉与 V6.x 完全一致）。
     *  - TimelineInspirationItem 场景传入 contentStartX + 18.dp（70+18=88dp：
     *    88dp = 原 Stage 视觉左缘；+18.dp 补偿 OUTER_BOX 的 offset(-18dp) 左移）。
     *  - 默认 0.dp 向后兼容其他调用点（无左扩需求时视觉不变）。*/
    stackStageOffsetX: Dp = 0.dp,
    /** V7.8：展开态视口右缘延伸量（对齐调用方外层容器边缘）。
     *  - 默认视口右缘 = Stage 右缘 − stackStageOffsetX（即父容器右缘）。
     *  - 当调用方图片行 Box 因左滑预借 offset 左移（如 TimelineInspirationItem
     *    的 offset(-18dp)）导致 Box 右缘比外层容器左移 18dp 时，传 18.dp
     *    让展开行视口右缘对齐「灵感条最外层容器」右缘。
     *  - 堆叠态不使用此值。默认 0.dp 向后兼容其他调用点。*/
    expandedViewportRightExtension: Dp = 0.dp,
    // ↑↑↑ 本次新增 ↑↑↑
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
    //
    // 与原型 `Array.isArray(images) && images.length > 0 ? images : DEFAULT_IMAGES` 兜底一致：
    // 调用方未传 imageUris 且未传 customContent 时，启用 [DEFAULT_IMAGE_COLORS] 作默认占位
    val useDefaultColors = imageUris.isEmpty() && customContent == null
    val cardCount = if (useDefaultColors) DEFAULT_IMAGE_COLORS.size else imageUris.size
    if (cardCount == 0) return  // customContent != null 但 imageUris 为空时返回（调用方责任）

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

    // 400ms 过渡动画规格：cubic-bezier(0.22, 1, 0.36, 1)，与原型 CSS transition 对齐
    val TRANSITION_400_SPEC = tween<Float>(
        durationMillis = 400,
        easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    )
    // 200ms 透明度淡入淡出规格：用于按钮、角标的 appear/disappear
    val OPACITY_200_SPEC = tween<Float>(durationMillis = 200)
    // V7.6 展开态行滚动回弹 spring 规格：松手后越界位移弹回有效区间（两端都有弹簧感）
    val ROW_SCROLL_SPRING = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // 初始展开态：优先取外部托管 expandedState，否则取 false（组件自管首次启动默认堆叠）
    // 注：此处不能引用后面 L498 才声明的 isExpanded，否则 Kotlin 前向引用未定义报错
    val initialExpanded = remember { expandedState?.value ?: false }
    // 展开进度 Animatable：0f = 纯堆叠态，1f = 纯展开态，中间值为过渡混合态
    val expandProgress = remember { Animatable(initialValue = if (initialExpanded) 1f else 0f) }

    // 动画是否正在运行：用于互斥锁（动画中禁止拖拽、禁止重复触发切换）
    val isAnimating by remember { derivedStateOf { expandProgress.isRunning } }
    // 是否已稳定进入堆叠态：进度 < 0.01 且无动画（用于开启堆叠态专属手势）
    val isInStackedMode by remember { derivedStateOf { expandProgress.value < 0.01f && !isAnimating } }
    // 是否已稳定进入展开态：进度 > 0.99 且无动画（用于开启展开态专属滚动/点击）
    val isInExpandedMode by remember { derivedStateOf { expandProgress.value > 0.99f && !isAnimating } }
    // 推导当前展开布尔值：以 0.5 为阈值，动画中途正确反映当前"多数派"状态
    val derivedIsExpanded by remember { derivedStateOf { expandProgress.value >= 0.5f } }

    /**
     * 单入口展开/收起切换：通过全局 expandProgress Animatable 400ms 贝塞尔动画
     *
     * 作用：
     * 1. 先调用 stop() 打断运行中动画，避免重复点击导致状态错乱（幂等安全）
     * 2. animateTo 到目标值（true→1f / false→0f），使用 TRANSITION_400_SPEC 贝塞尔缓动
     * 3. 动画结束后，将 derivedIsExpanded（对外 Boolean 快照）同步给外部托管 expandedState 与外部 Boolean isExpanded 参数
     */
    suspend fun setExpanded(targetExpand: Boolean) {
        if (expandProgress.isRunning) return
        val targetValue = if (targetExpand) 1f else 0f
        if (expandProgress.value == targetValue && !expandProgress.isRunning) return // 幂等跳过
        expandProgress.stop()
        expandProgress.animateTo(targetValue, TRANSITION_400_SPEC)
        // 结束后同步：derivedIsExpanded 会自动由 progress≥0.5 反映，此处仅同步外部托管
        expandedState?.value = targetExpand
        // 注：外部 isExpanded 参数是 Composable 调用侧快照，不支持双向写；若原代码有 isExpanded = true/false 的 MutableState
        // 写法，先统一 Grep 所有出现点，逐一替换为 scope.launch { setExpanded(true/false) }
    }

    // ============ 共享状态（与 Originkit 原型一致）============
    // isPressed：组件级"是否有顶卡正在被按住"标志
    // - 用于顶卡 scale +0.05、rotation 归零的视觉反馈
    // - 原型 const [isPressed, setIsPressed] = useState(false)
    val isPressed = remember { mutableStateOf(false) }
    // shouldReturnToCenter：短距离拖动后回中动画的标志
    // - true 期间顶卡 x/y/rotate 强制为 0（不再叠加扇形偏移）
    // - 1s 后自动复位（与原型 setTimeout 1s 一致）
    val shouldReturnToCenter = remember { mutableStateOf(false) }

    // ============ 展开态状态（设计文档 §3.3）============
    // isExpanded：堆叠/展开模式开关
    // - 优先使用调用方 expandedState（外部托管，便于在组件外放 CollapseBtn）
    // - 否则组件内部 remember 自管（向后兼容）
    val _expandedInternal = remember { mutableStateOf(false) }
    var isExpanded: Boolean by (expandedState ?: _expandedInternal)

    // ============ 展开态行滚动状态（V7.6：替代 horizontalScroll）============
    // 为什么不能用 horizontalScroll：卡片展开位置是 graphicsLayer translationX 实现的，
    // 不参与布局测量 → 滚动容器测得的内容宽度只有单卡宽（cardWidth）< 视口宽 →
    // 无内容可滚（展开后图片行完全无法左右滑动的根因）。
    // 自绘滚动方案：
    // - rowScrollX：渲染用滚动偏移（px，Animatable 支持 spring 回弹动画）
    // - rawRowScroll：拖拽累计原始值（px，橡皮筋阻尼前的值；松手 clamp 回有效区间）
    // - viewportWidthPx：展开态视口宽（px，onSizeChanged 实测）
    // 滚动有效区间 [minScroll, 0]：0 = 第一张贴视口左缘；minScroll = -(行宽-视口宽)
    val rowScrollX = remember { Animatable(0f) }
    val rawRowScroll = remember { mutableFloatStateOf(0f) }
    val viewportWidthPx = remember { mutableFloatStateOf(0f) }

    // 自动重置：每当 isExpanded 变为 false，
    // 行滚动、顶卡拖拽偏移一起归零（下次展开视觉干净；兼容外部托管独立收起按钮行场景）
    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            // 并行归位动画：X 拖拽 + Y 拖拽（行滚动 snap 归零，跟随收起动画即可）
            listOf(
                async { dragOffsetX.animateTo(0f) },
                async { dragOffsetY.animateTo(0f) }
            ).awaitAll()
            rawRowScroll.floatValue = 0f
            rowScrollX.snapTo(0f)
        }
    }

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

    // ============ 动态计算旋转后堆叠区的包围盒 ============
    // 遍历所有可见卡片（ei = 0 到 M-1），计算 scale + rotation + translation 后的包围盒。
    // 用于：
    // 1. 调整外层 Box 尺寸，确保旋转后的卡片不被裁剪
    // 2. 计算内层 Box 的 translationX/Y 偏移，使堆叠区左上角对齐 Box 左上角
    //    （进而与标题行、时分时间、正文、标签行的左边缘对齐）
    //
    // 单位策略：与现有卡片 translation 一致，均用 dp-as-px（cardWidth.value 等），
    // 在 mdpi 设备上 1dp = 1px，高密度设备上视觉与 Web CSS px 一致。
    //
    // 旋转矩形包围盒公式（绕中心旋转 θ 后）：
    //   rotHalfW = halfW * |cos(θ)| + halfH * |sin(θ)|
    //   rotHalfH = halfW * |sin(θ)| + halfH * |cos(θ)|
    // 其中 halfW/halfH 是缩放后的卡片半宽/半高。
    //
    // 动态适应：当 visibleDepth、tiltAngleStart、cardWidth/cardHeight 变化时，
    // 包围盒自动重算，顶点位置自动更新。
    val M_bbox = minOf(visibleDepth, cardCount).coerceIn(1, 4)
    val denom_bbox = max(M_bbox - 1, 1)
    val effectiveTilt_bbox = -(M_bbox - 1) * 15f

    val cardWVal = cardWidth.value
    val cardHVal = cardHeight.value
    val stackVPVal = stackVerticalPadding.value

    var bboxLeft = Float.MAX_VALUE
    var bboxTop = Float.MAX_VALUE
    var bboxRight = -Float.MAX_VALUE
    var bboxBottom = -Float.MAX_VALUE

    for (ei in 0 until M_bbox) {
        // 每张卡片的 scale、rotation、translation（与主渲染逻辑严格一致）
        val s = 1f - ei * 0.05f
        val thetaDeg = if (M_bbox > 1) {
            tiltAngleStart + (ei.toFloat() / denom_bbox) * (effectiveTilt_bbox - tiltAngleStart)
        } else {
            tiltAngleStart
        }
        val thetaRad = thetaDeg * Math.PI / 180.0
        val cosT = abs(cos(thetaRad)).toFloat()
        val sinT = abs(sin(thetaRad)).toFloat()

        // 缩放后卡片半宽/半高
        val halfW = s * cardWVal / 2f
        val halfH = s * cardHVal / 2f
        // 旋转后包围盒半宽/半高（绕中心旋转）
        val rotHalfW = halfW * cosT + halfH * sinT
        val rotHalfH = halfW * sinT + halfH * cosT

        // 卡片平移（与主渲染逻辑一致：tx 用 xOffset.value，ty 用 yOffset.value per index）
        val tx = if (M_bbox > 1) ei.toFloat() / denom_bbox * xOffset.value else 0f
        val ty = -(ei * yOffset.value)

        // 累积包围盒边界（相对于卡片中心）
        bboxLeft = minOf(bboxLeft, tx - rotHalfW)
        bboxTop = minOf(bboxTop, ty - rotHalfH)
        bboxRight = maxOf(bboxRight, tx + rotHalfW)
        bboxBottom = maxOf(bboxBottom, ty + rotHalfH)
    }

    // 包围盒尺寸
    val bboxWidth = bboxRight - bboxLeft
    val bboxHeight = bboxBottom - bboxTop

    // ============ 堆叠卡片容器定位（左对齐外层 Box 左上角）============
    // 用户方案：堆叠图在包围盒里左对齐，角标右对齐，无需依赖 Center + translationX/Y
    //
    // 卡片容器（内部放所有卡片 Box，size = 包围盒尺寸，contentAlignment = Center）
    // 容器自身放在外层 Box 的 TopStart，容器左上角 = 堆叠区左上角
    // 因为堆叠区左边缘 = 容器中心（Center 对齐） + bboxLeft
    // 要求堆叠区左边缘 = 外层 Box 左边缘（0）
    // → 容器中心 x = -bboxLeft
    // → 容器左边缘 x = 容器中心 - bboxWidth/2 = -bboxLeft - bboxWidth/2
    // 同理：容器 top = -bboxTop - bboxHeight/2
    //
    // 这样堆叠区左上角恰好对齐外层 Box 左上角（0,0），与标题行等左边缘严格对齐
    val cardBoxStartX = -bboxLeft - bboxWidth / 2f
    val cardBoxStartY = -bboxTop - bboxHeight / 2f

    // ============ 角标 & 展开按钮对 Box 右边缘的扩宽 ============
    // countBadge 角标：放在外层 Box 右下角（BottomEnd），位于顶卡右边缘右侧（位置保持不变）
    // showExpandButton "展开 N" 按钮：放在角标的右侧，比角标大一号，紧凑显示
    // 两者均需动态扩宽外层 Box 右边缘，保证完全不被裁剪、不与图片重叠
    // 左边缘保持不动（堆叠区左上角始终对齐外层 Box 左上角）
    // 展开态下不显示角标（设计文档 §3.4 / R6 需求）
    val showCountBadge = countBadge && cardCount > visibleDepth && !isExpanded
    // 展开态下展开按钮隐藏（设计文档 §3.4，由收起按钮取代）
    val showExpand = showExpandButton && cardCount >= 2 && !isExpanded  // ≥ 2 张且非展开态时显示
    // 角标估算宽度：最多 5 字符 "100/100" 10sp ≈ 35dp + 水平 padding 8dp ≈ 43dp
    val badgeEstWidth = if (showCountBadge) 45f else 0f
    // 展开按钮（比角标大一号）：
    // - 文本 "展开 100" 12sp ≈ 50dp + 水平 padding 10dp + 图标 16dp ≈ 76dp
    // - 胶囊高度 ≈ 22dp（比角标 16dp 高 6dp）
    // 展开按钮估算宽度（76dp 留足给 "展开 99 ›" 场景）
    val expandEstWidth = if (showExpand) 76f else 0f
    // ============ 按钮距「顶卡右边缘」的统一基准间距 ============
    // 根因：之前有角标/无角标分别用不同基准，导致两套视觉距离不一致：
    //   - 无角标：顶卡右 + 4dp
    //   - 有角标：顶卡右 + 45dp(估算角标宽) + 6dp → 估算比实际 20dp 宽 ≈25dp，视觉偏约 21dp
    // 修复：无论有无角标，按钮起点都相对于「顶卡右边缘」这一视觉基准；
    //       有角标时再增加一个 gap（角标→按钮），角标实际宽度虽略小于估算，但差异可控
    val expandMarginToCardWithBadge = if (showExpand && showCountBadge) {
        28f  // 有角标：顶卡右 → 按钮起点 = 28dp（≈角标宽 20dp + 角标到按钮 8dp，视觉协调）
    } else 0f
    val expandMarginToCardNoBadge = if (showExpand && !showCountBadge) {
        28f  // 无角标：与有角标情况相同的 28dp 基准，保证视觉一致
    } else 0f
    // 顶卡左边缘 & 右边缘（外层 Box 坐标）
    val topCardCenterX_box = -bboxLeft
    val topCardLeft_box = topCardCenterX_box - cardWVal / 2f
    val topCardRightX_box = topCardLeft_box + cardWVal
    // 顶卡底边缘（外层 Box 坐标）
    val topCardCenterY_box = -bboxTop
    val topCardTop_box = topCardCenterY_box - cardHVal / 2f
    val topCardBottomY_box = topCardTop_box + cardHVal
    // 角标右边缘位置：顶卡右 + 0dp（用户要求去掉间距，角标贴紧顶卡右边缘）
    val badgeRightEdge = if (showCountBadge) {
        topCardRightX_box + 0f + badgeEstWidth
    } else 0f
    // 按钮右边缘：顶卡右边缘 + 统一基准间距（无论有无角标，视觉上按钮与顶卡距离一致）
    val expandRightEdge = if (showExpand) {
        if (showCountBadge) {
            topCardRightX_box + expandMarginToCardWithBadge + expandEstWidth
        } else {
            topCardRightX_box + expandMarginToCardNoBadge + expandEstWidth
        }
    } else 0f
    // 外层 Box 右边缘：至少到各元素右边缘 + 4dp（不贴边）
    // 角标左边缘已贴紧顶卡右边缘（badgeRightEdge - badgeEstWidth = topCardRightX_box + 0f）
    val requireBadgeRight = if (showCountBadge) badgeRightEdge + 4f else 0f
    val requireExpandRight = if (showExpand) expandRightEdge + 4f else 0f
    val badgeRequiredRight = maxOf(requireBadgeRight, requireExpandRight)

    // ==============================
    // Stage 外层尺寸（永远 = 堆叠态需求；isExpanded 任何变化都不改变此值）
    // 设计文档 §9.4.1：保持堆叠态最大值恒定不变
    // → 保证 Badge(1/N) / ExpandBtn("展开 N") / 堆叠图本身 位置绝对不变（用户硬约束）
    // ==============================
    // 计算数值：Float（与项目全局 px-as-dp 单位策略一致，数值直接当 dp 使用）
    // 修复：使用 bboxSize + maxOf(cardBoxStart, 0f) 替代 bboxSize + maxOf(-cardBoxStart, 0f)
    // 当 cardBoxStart > 0 时，卡片容器有正偏移，Stage 需扩展以容纳容器底部/右侧
    // 阴影补偿：顶卡 shadowElevation=8.dp，阴影在卡片四周绘制，需要额外空间
    val shadowPadding = 8f  // 最大阴影高度（顶卡 8.dp）
    // 滑动补偿：顶卡左右滑动时的最大位移（双侧各一份），让 Stage 宽度足够容纳滑动过程
    // 注意：TimelineInspirationItem 外包一层 wrapContentWidth(unbounded=true) 保证此宽度不被压缩
    val slideCompensationDp = with(density) { maxElasticDistancePx.toDp() }.value
    val stageBoxWidthDpFloat: Float = maxOf(
        cardWVal + xOffset.value + slideCompensationDp * 2,  // 扇形水平摊开 + 双侧滑动补偿
        bboxWidth + maxOf(cardBoxStartX, 0f) + shadowPadding + slideCompensationDp * 2,  // 包围盒宽 + 右侧延伸量 + 阴影 + 双侧滑动补偿
        badgeRequiredRight                                       // 1/N 角标右端 或 展开按钮右端
    )
    val stageBoxHeightDpFloat: Float = maxOf(
        cardHVal + stackVPVal + shadowPadding,               // 扇形垂直摊开 + 阴影
        bboxHeight + maxOf(cardBoxStartY, 0f) + shadowPadding  // 包围盒高 + 底部延伸量 + 阴影
    )
    // V5.8 埋点：输出 Stage 尺寸计算的关键参数
    // 与 [STAGE_BOX] actualSize 对比，确认 stageBoxWidthDpFloat 是否真的被 Stage 容器使用
    // (V5.8 埋点已移除)
    // 类型转换为 Dp：供 Modifier.size() 使用
    val stageBoxWidthDp: Dp = stageBoxWidthDpFloat.dp
    val stageBoxHeightDp: Dp = stageBoxHeightDpFloat.dp
    // 像素尺寸：供布局计算使用
    val stageBoxWidthPx: Int = with(density) { stageBoxWidthDp.roundToPx() }
    val stageBoxHeightPx: Int = with(density) { stageBoxHeightDp.roundToPx() }
    // 向后兼容别名（原代码大量使用 boxWidth/boxHeight Dp 类型；后续逐步替换）
    val boxWidth: Dp = stageBoxWidthDp
    val boxHeight: Dp = stageBoxHeightDp

    /**
     * 堆叠态顶卡（stackIndex=0）左上角相对 Stage(0,0) 的偏移
     * = CardContainer Center 对齐后，用 graphicsLayer 平移 cardBoxStartX/Y 到 Stage(0,0)
     * 展开态 Row 必须使用相同偏移 padding(start/top)，保证顶卡左上角像素级一致
     */
    // 顶卡左上角 X（Dp）：cardBoxStartX（Float）− bboxLeft（Float）→ 转为 Dp
    val topCardLeftInStageDp: Dp = (cardBoxStartX - bboxLeft).dp
    // 顶卡左上角 Y（Dp）：cardBoxStartY（Float）→ 转为 Dp
    val topCardTopInStageDp: Dp = cardBoxStartY.dp
    // V7.3 锚点：堆叠态/展开态统一的顶卡视觉 Top
    // 堆叠态：Layer1 的 container 使用 Center 对齐（size = bboxHeight），所以
    //   顶卡视觉 Top = 容器 top + (bboxHeight - cardHeight) / 2（Center 对齐的上下留白）
    //            = cardBoxStartY + (bboxHeight - cardHeight) / 2
    // 展开态：给卡片行追加 offset(y = topCardAnchorY)，让展开态顶卡视觉 Top
    //   与堆叠态完全一致，消除分支切换时的向上跳变。
    val topCardAnchorY: Float = cardBoxStartY + (bboxHeight - cardHeight.value) / 2f
    // V7.4 锚点：展开态整行向左平移量（水平方向连续性）
    // - 堆叠态顶卡视觉左缘 S = cardBoxStartX + (bboxWidth - cardWidth) / 2（Center 留白）
    // - 展开态顶卡视觉左缘 E = 0（horizontalScroll Box 顶对齐 TopStart，无偏移）
    // - 两态差值 J = S - E > 0（包围盒因卡片旋转比单卡宽）→ 原先动画结束瞬间
    //   分支切换时整行突然左移 J（向左跳变）
    // 修复：展开动画期间随 expandProgress 把 -J 平滑注入 Layer1 的 offset.x，
    // 动画结束时堆叠分支与展开分支的顶卡视觉左缘严格相等 → 左移融入动画，零跳变。
    val expandedRowShiftX: Float = cardBoxStartX + (bboxWidth - cardWidth.value) / 2f

    // ==============================
    // 展开态独立尺寸（不绑定 stageBoxWidth）
    // ==============================
    // ScrollArea 最大宽度（独立上限，保证 5 张图×120dp + 4×8dp = 632dp 有充足空间）
    val EXPANDED_SCROLL_MAX_WIDTH_DP: Dp = 480.dp
    // 卡片横向行总宽度：N × (W + G) − G（原型 expand-collapse-stack.html L456 公式）
    val cardRowWidthPxFloat: Float = if (cardCount > 0) {
        cardCount.toFloat() * (cardWidth.value + cardGap.value) - cardGap.value
    } else 0f
    val cardRowWidthDp: Dp = cardRowWidthPxFloat.dp

    // ============================================================
    // P2 新增：共享卡片渲染层（堆叠/展开共用，Positional Memoization 复用）
    // 本阶段仅保证堆叠态等价，calcCardTarget expandProgress 固定传 0f
    // ============================================================
    @Composable
    fun OneSharedCard(displayIndex: Int, card: CardSlot) {
        // P2：固定 progress=0f（堆叠态），保证 calcCardTarget 输出与原硬编码完全等价
        val target = calcCardTarget(
            displayIndex = displayIndex,
            cardCount = order.size,
            expandProgress = expandProgress.value,
            cardW = cardWidth,
            cardGap = cardGap,
            visibleDepth = visibleDepth,
            stackOffsetDp = yOffset,
            fanAngleDeg = -(visibleDepth - 1).toFloat() * 15f,
            scaleStep = 0.05f,
        )
        // 每张卡独立的飞离动画 Animatable（仅堆叠态顶卡飞离时非零，其余卡恒 0）
        // 原堆叠态 order.forEach 内 remember，抽函数后需在 OneSharedCard 内同步声明
        val positionX = remember { Animatable(0f) }
        val positionY = remember { Animatable(0f) }
        // 每张卡独立的 zIndex 动画：顶卡被飞离时临时 snapTo 1f，叠加到 target.zIndex 上保证飞离过程始终在最顶层
        val zIndexAnim = remember { Animatable(0f) }
        // ========================================
        // Fix B：displayIndex 变化（order 翻牌）立即清零这张卡的所有残留 Animatable
        // 根治：positionX / positionY / zIndexAnim 脏值累积导致的「多群分裂/图片倒置/遮挡混乱」
        // ========================================
        LaunchedEffect(displayIndex) {
            positionX.snapTo(0f)
            positionY.snapTo(0f)
            zIndexAnim.snapTo(0f)
        }
        // 拖拽偏移：堆叠态 displayIndex==0 顶卡叠加 dragOffset；其它卡或非堆叠态恒 0
        // V7.0 单位修复：dragOffsetX.value 是「像素值」（detectHorizontalDragGestures 的
        // dragAmount 累计 px），旧代码 .dp 直接把 px 数值当 dp，导致渲染平移量被放大
        // density 倍（≈2.75x），手指滑 1px 卡片动 2.75px 且埋点坐标与真实渲染不一致。
        // 正确换算：px → dp 用 toDp()（÷density），渲染处再 toPx() 还原。
        val dragX = if (displayIndex == 0) with(density) { dragOffsetX.value.toDp() } else 0.dp
        val dragY = if (displayIndex == 0) with(density) { dragOffsetY.value.toDp() } else 0.dp
        val isTopCard = displayIndex == 0

        // ========== 有无图判断 ==========
        val hasImage = when {
            customContent != null -> true
            useDefaultColors -> true
            else -> imageUris.getOrNull(card.originalIndex)?.isNotBlank() == true
        }

        // (V5.8 LaunchedEffect 埋点已移除)

        Box(
            modifier = Modifier
                // V7.0 布局空间预借：卡片 Box 宽度向左扩展 StackLeftCompensation
                // （堆叠态全额扩展，展开态随 expandProgress 插值归零，过渡平滑）。
                // 图片内容由下方「内容包装 Box」的 offset 补偿回原视觉位置。
                // 效果：顶卡左滑的 translation 轨迹始终落在卡片 Box 自身的 layout
                // bounds 内 —— 不再依赖任何溢出渲染，从根源上免疫一切裁剪机制。
                .size(
                    width = cardWidth + StackLeftCompensation * (1f - expandProgress.value),
                    height = cardHeight
                )
                // 堆叠排序 + 顶卡飞离时临时上抬 zIndex（保证飞离过程始终覆盖其他卡片）
                .zIndex(target.zIndex + zIndexAnim.value)
                // (V5.8 onGloballyPositioned 埋点已移除)
                // V5.7 修复：在 V5.5.5 graphicsLayer clip=false 基础上，给 shadow 加 clip=false
                // - 根因：
                //   - V5.5.5 修复：graphicsLayer RenderNode 加 this.clip = false，但 Modifier.shadow
                //     内部会再创建一个 RenderNode（用 graphicsLayer 实现 shadowElevation + shape），
                //     默认 clip=true。顶卡左滑时 shadow RenderNode 在 graphicsLayer 内部
                //     接收到 transform 后的内容，clip=true 会裁剪到 120x120 范围 → 左滑被裁
                //   - V5.6 修复：把 .shadow()/.clip() 移到 .graphicsLayer() 之前，让 shadow
                //     在父 draw scope 中画阴影、graphicsLayer 整体平移结果
                //     - 但 shadow RenderNode 仍在 graphicsLayer RenderNode 之外（layout=120x120,
                //       clip=true），graphicsLayer 平移的是 shadow 输出（已被裁剪到 120x120），
                //       → 实际并未真正解决裁剪问题，且让 4 张图倾斜堆叠的视觉效果出现回归
                // - 正确修复：保持 V5.5.5 modifier 顺序（graphicsLayer 在 shadow/clip 之前），
                //   给 shadow 传 clip=false，让 shadow RenderNode 不裁剪，graphicsLayer RenderNode
                //   也保持 clip=false → 顶卡左滑时双层 RenderNode 都不裁剪，4 张图堆叠视觉保留
                // - 视觉不变：静止时 translationX=0，clip=false 不影响任何渲染
                // - 9 层 clip=false 链完整：OneSharedCard 内部 graphicsLayer + shadow 都 clip=false
                // - V7.0：shadow/clip 已移至下方「内容包装 Box」（跟随图片区域而非扩宽后的卡片 bounds，
                //   避免阴影绘制到卡片左侧空白补偿区）
                .graphicsLayer {
                    this.clip = false
                    scaleX = target.scale
                    scaleY = target.scale
                    rotationZ = target.rotationZ
                    // V7.0 变换中心补偿：卡片 Box 扩宽后默认 TransformOrigin.Center 右移，
                    // 会让 rotationZ/scale 绕错误中心旋转缩放（破坏 4 张卡倾斜堆叠视觉）。
                    // 显式把 pivot 钉在「图片视觉中心」，保证旋转/缩放行为与 V6.x 像素级一致。
                    val comp = StackLeftCompensation.value * (1f - expandProgress.value)
                    val totalW = cardWidth.value + comp
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (comp + cardWidth.value / 2f) / totalW,
                        pivotFractionY = 0.5f
                    )
                    // 四元叠加：堆叠基础位移 target.x + 顶卡按住拖拽 dragX + 顶卡飞离动画 positionX
                    // （V7.0 单位修复后 dragX 已是正确 dp，toPx() 还原为真实像素位移）
                    translationX = density.run { (target.x + dragX + positionX.value.dp).toPx() }
                    translationY = density.run { (target.y + dragY + positionY.value.dp).toPx() }
                }
        ) {
            // ============ V7.0 内容包装 Box：图片实际渲染区域 ============
            // - offset 把图片推到卡片 Box 右端（原视觉位置），卡片左侧扩出的
            //   StackLeftCompensation 区域是「空 bounds」，专供左滑 translation 使用
            // - shadow / clip / 占位背景 / 拖拽手势 从外层卡片 Box 移到这里：
            //   ① 阴影/圆角裁剪只作用于图片区域（不出现在左侧空白区）
            //   ② 手势区域 = 图片区域（不拦截卡片左侧空白区上方的父级点击/滑动）
            // - 随 expandProgress 插值归零（展开态卡片恢复原尺寸 120dp）
            Box(
                modifier = Modifier
                    .offset(x = StackLeftCompensation * (1f - expandProgress.value))
                    .size(cardWidth, cardHeight)
                    .shadow(
                        elevation = target.shadowElevation,
                        shape = RoundedCornerShape(radiusPx),
                        clip = false,  // V5.7 修复：让 shadow RenderNode 不裁剪，顶卡左滑时不被 shadow 节点裁剪
                    )
                    .clip(RoundedCornerShape(radiusPx))
                    .then(
                        if (hasImage) {
                            Modifier
                        } else {
                            Modifier
                                .background(
                                    color = Color(0xFFF3EFFF).copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(radiusPx)
                                )
                                .blur(10.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = Color(0xFF9967FF),
                                    shape = RoundedCornerShape(radiusPx)
                                )
                        }
                    )
                .then(
                    when {
                        isTopCard && isInStackedMode -> {
                            Modifier.pointerInput(card.stableId, swipeDirection) {
                                if (swipeDirection == SwipeDirection.Horizontal) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            isPressed.value = true
                                            shouldReturnToCenter.value = false
                                            scope.launch {
                                                positionX.snapTo(0f)
                                                positionY.snapTo(0f)
                                            }
                                            // (V5.8 drag start 埋点已移除)
                                        },
                                        onDragEnd = {
                                            isPressed.value = false
                                            val dx = dragOffsetX.value
                                            val distance = dx.absoluteValue
                                            // (V5.8 drag end 埋点已移除)
                                            if (distance > thresholdPx && order.size > 1) {
                                                scope.launch {
                                                    onCardSwiped?.invoke(card.originalIndex)
                                                    zIndexAnim.snapTo(1f) // 飞离过程始终置顶，确保盖住其它卡
                                                    val direction = sign(dx).let { if (it == 0f) 1f else it }
                                                    val cardWidPx = density.run { cardWidth.toPx() }
                                                    val flyTargetPx = direction * cardWidPx * 1.6f // 飞出 1.6 卡宽，视觉上出边界
                                                    // ========================================
                                                    // Fix C：先起 300ms 飞离动画，用户看到顶卡滑出再翻牌
                                                    // （旧实现缺 animateTo，直接 snapTo+改 order → 卡停在半路形成分裂群）
                                                    // ========================================
                                                    positionX.animateTo(
                                                        targetValue = flyTargetPx / density.density,
                                                        animationSpec = tween(300, easing = FastOutLinearInEasing)
                                                    )
                                                    // 动画完成后 order 才旋转：顶卡移到堆叠尾部
                                                    val newOrder = order.toMutableList()
                                                    val top = newOrder.removeAt(0)
                                                    newOrder.add(top)
                                                    order = newOrder
                                                    // 清零 dragOffset；Fix B 的 LaunchedEffect(displayIndex) 会自动清 positionX/zIndexAnim
                                                    dragOffsetX.snapTo(0f)
                                                    dragOffsetY.snapTo(0f)
                                                    shouldReturnToCenter.value = false
                                                    isPressed.value = false
                                                }
                                            } else {
                                                shouldReturnToCenter.value = true
                                                scope.launch {
                                                    delay(1000)
                                                    shouldReturnToCenter.value = false
                                                }
                                                scope.launch {
                                                    dragOffsetX.animateTo(0f, BOUNCE_SPRING)
                                                    dragOffsetY.animateTo(0f, BOUNCE_SPRING)
                                                }
                                            }
                                        }
                                    ) { change, dragAmount ->
                                        val currentOffset = dragOffsetX.value.absoluteValue
                                        val t = (currentOffset / maxElasticDistancePx).coerceIn(0f, 1f)
                                        val resistance = 1f - 0.3f * t.pow(1.5f)
                                        val elasticAmount = dragAmount * resistance
                                        scope.launch {
                                            dragOffsetX.snapTo(dragOffsetX.value + elasticAmount)
                                        }
                                        // (V5.8 dragOffset 埋点已移除)
                                    }
                                } else {
                                    detectDragGestures(
                                        onDragStart = {
                                            isPressed.value = true
                                            shouldReturnToCenter.value = false
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
                                            if (distance > thresholdPx && order.size > 1) {
                                                scope.launch {
                                                    onCardSwiped?.invoke(card.originalIndex)
                                                    zIndexAnim.snapTo(1f) // 飞离过程始终置顶，确保盖住其它卡
                                                    val direction = sign(dx).let { if (it == 0f) 1f else it }
                                                    val cardWidPx = density.run { cardWidth.toPx() }
                                                    val flyTargetPx = direction * cardWidPx * 1.6f // 飞出 1.6 卡宽，视觉上出边界
                                                    // ========================================
                                                    // Fix C：先起 300ms 飞离动画，用户看到顶卡滑出再翻牌
                                                    // （旧实现缺 animateTo，直接 snapTo+改 order → 卡停在半路形成分裂群）
                                                    // ========================================
                                                    positionX.animateTo(
                                                        targetValue = flyTargetPx / density.density,
                                                        animationSpec = tween(300, easing = FastOutLinearInEasing)
                                                    )
                                                    // 动画完成后 order 才旋转：顶卡移到堆叠尾部
                                                    val newOrder = order.toMutableList()
                                                    val top = newOrder.removeAt(0)
                                                    newOrder.add(top)
                                                    order = newOrder
                                                    // 清零 dragOffset；Fix B 的 LaunchedEffect(displayIndex) 会自动清 positionX/zIndexAnim
                                                    dragOffsetX.snapTo(0f)
                                                    dragOffsetY.snapTo(0f)
                                                    shouldReturnToCenter.value = false
                                                    isPressed.value = false
                                                }
                                            } else {
                                                shouldReturnToCenter.value = true
                                                scope.launch {
                                                    delay(1000)
                                                    shouldReturnToCenter.value = false
                                                }
                                                scope.launch {
                                                    dragOffsetX.animateTo(0f, BOUNCE_SPRING)
                                                    dragOffsetY.animateTo(0f, BOUNCE_SPRING)
                                                }
                                            }
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
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
                        }
                        else -> Modifier
                    }
                )
                .then(
                    when {
                        isInExpandedMode -> {
                            Modifier
                        }
                        else -> Modifier
                    }
                )
        ) {
            when {
                customContent != null -> customContent(displayIndex)
                useDefaultColors -> DefaultColorCard(displayIndex, radiusPx)
                else -> {
                    val imageUri = imageUris.getOrNull(card.originalIndex)
                    if (!imageUri.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUri)
                                .crossfade(true)
                                .scale(Scale.FIT)
                                .build(),
                            contentDescription = "图片 ${card.originalIndex + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = { DefaultImageLoading(radiusPx, displayIndex) },
                            error = { DefaultImageLoading(radiusPx, displayIndex) }
                        )
                    } else {
                        DefaultImageText(displayIndex)
                    }
                }
            }

            if (isTopCard && onCardClick != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(card.stableId) {
                            detectTapGestures(
                                onTap = { onCardClick(card.originalIndex) }
                            )
                        }
                )
            }
            }  // V7.0：内容包装 Box 闭合（图片区域）
        }      // V7.0：外层卡片 Box（扩宽 bounds + graphicsLayer 平移）闭合
    }

    @Composable
    fun SharedCardRow() {
        order.forEachIndexed { displayIndex, card ->
            key(card.stableId) {
                OneSharedCard(displayIndex, card)
            }
        }
    }

    @Composable
    fun BoxScope.Layer1CardWrapper() {
        if (isInExpandedMode) {
            // ==================== V7.6 展开态：自绘行滚动 ====================
            // 两个问题一次修复（共用同一套结构，互不冲突）：
            //
            // 问题①：顶卡左侧阴影被裁剪
            //   根因（与堆叠态左滑裁剪同类——容器裁剪越界内容，但容器不同）：
            //   旧实现用 horizontalScroll，滚动容器必须裁剪内容到自身 bounds；
            //   顶卡（第一张）位于视口 x=0，其阴影向左延伸 ~4dp 超出视口左缘 → 被切。
            //   修复：弃用 horizontalScroll，改 graphicsLayer translationX 滚动 +
            //   全链 clip=false，阴影自由渲染。
            //
            // 问题②：展开后图片行无法左右滑动
            //   根因：卡片展开位置是 graphicsLayer translationX 实现的（不参与布局
            //   测量）→ horizontalScroll 测得内容宽 = 单卡宽(cardWidth) < 视口宽 →
            //   无内容可滚。
            //   修复：自绘滚动——拖拽累计 rawRowScroll → 橡皮筋阻尼 → rowScrollX
            //   渲染平移；松手 spring 回弹（左右滑到头都有弹簧动画）。
            //
            // 视口右缘 = Stage 右缘 − stackStageOffsetX（即外层容器边缘，不贴屏幕边）：
            //   Stage 展开态 fillMaxWidth（= 外层容器宽），左缘在 stackStageOffsetX
            //   处 → padding(end = stackStageOffsetX) 把视口右缘收到外层容器右缘。
            //
            // 旧 widthIn(min = stageBoxWidthDp) 已删除：V7.2 后 WRAPPER 展开态传有界
            // 约束，Stage fillMaxWidth 不再依赖子内容撑宽；该 min 反而让 Layer1
            // 比视口宽 ~180dp，破坏滚动边界计算。
            //
            // 滚动有效区间 [minScroll, 0]：
            //   0 = 第一张贴视口左缘（与 V7.4 展开锚点连续）
            //   minScroll = -(行宽 − 视口宽) = 最后一张贴视口右缘（外层容器边缘）
            val rowWidthPx = with(density) { cardRowWidthDp.toPx() }
            // V7.8：视口右缘内缩量 = stackStageOffsetX − 右缘延伸量（对齐外层容器边缘）
            val viewportEndInset = (stackStageOffsetX - expandedViewportRightExtension)
                .coerceAtLeast(0.dp)
            val viewportEndInsetPx = with(density) { viewportEndInset.toPx() }
            // 实时计算滚动下界：minScroll = −(行宽 − 视口内容区宽)
            // 视口内容区宽 = Layer1 全宽（onSizeChanged 链首实测）− 右缘内缩量
            // （lambda 内读取 viewportWidthPx State——pointerInput(Unit) 闭包不随重组重启）
            val minScrollNow: () -> Float = {
                -(rowWidthPx - (viewportWidthPx.floatValue - viewportEndInsetPx))
                    .coerceAtLeast(0f)
            }
            // V7.7：fling 惯性衰减曲线（@Composable 必须在组合作用域声明，
            // pointerInput lambda 内无法调用 Composable——LazyRow 同款官方曲线）
            val flingDecay = rememberSplineBasedDecay<Float>()
            Box(
                modifier = Modifier
                    // V7.8 修复：onSizeChanged 必须放在链首——报告整个 Layer1 的全宽
                    //（fillMaxWidth 固定约束，与内部内容无关）；若放在 padding 之后，
                    // 会报告内容自适应宽（≈单卡 120dp，卡片展开位移不参与布局测量），
                    // 视口宽被严重低估 → 滚动边界失真 → 甩动时卡片飞过容器边缘、
                    // 直到物理屏幕边缘才消失（用户反馈的"右缘裁剪位置在屏幕边缘"）
                    .onSizeChanged { viewportWidthPx.floatValue = it.width.toFloat() }
                    .fillMaxWidth()
                    // V7.8：视口右缘 = 外层容器右缘（默认 Stage 右缘 − stackStageOffsetX，
                    // 按 expandedViewportRightExtension 再向右延伸对齐调用方容器边缘）
                    .padding(end = viewportEndInset)
                    // 不裁剪：顶卡左缘阴影自由渲染（问题①修复的关键）
                    .graphicsLayer { this.clip = false }
                    // V7.8：仅右缘裁剪——超出视口右缘（外层容器边缘）的卡片在此被切，
                    // 不再飞到物理屏幕边缘才消失；左/上/下方向不裁剪
                    //（左侧不裁剪保留顶卡左缘阴影溢出渲染，与问题①修复不互斥）
                    .drawWithContent {
                        clipRect(
                            left = -100000f,
                            top = -100000f,
                            right = size.width,
                            bottom = 100000f
                        ) {
                            drawContent()
                        }
                    }
                    // 自绘行滚动手势（问题②修复的关键）
                    // V7.7：加 fling 惯性——参考待办编辑页图片行（LazyRow 自带 fling），
                    // 自绘滚动用标准基建组合：VelocityTracker 测松手初速度 →
                    // splineBasedDecay 惯性衰减 → 越界时 spring 弹回（保留弹簧手感）。
                    .pointerInput(Unit) {
                        // 速度追踪器：fling 初速度来源（松手前最后几帧的滑动速度）
                        val velocityTracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = { velocityTracker.resetTracking() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    change.position
                                )
                                // 累计原始位移（不阻尼），渲染值经橡皮筋阻尼
                                rawRowScroll.floatValue += dragAmount
                                val damped = rubberBandRowScroll(rawRowScroll.floatValue, minScrollNow())
                                scope.launch { rowScrollX.snapTo(damped) }
                            },
                            onDragEnd = {
                                // 松手初速度（px/s）：负 = 向左甩（继续向左滚），正 = 向右甩
                                val velocity = velocityTracker.calculateVelocity().x
                                // 无速度或极慢：维持 V7.6 行为（clamp + spring 回弹）
                                if (abs(velocity) < 200f) {
                                    val minScroll = minScrollNow()
                                    rawRowScroll.floatValue = rawRowScroll.floatValue.coerceIn(minScroll, 0f)
                                    scope.launch {
                                        rowScrollX.animateTo(rawRowScroll.floatValue, ROW_SCROLL_SPRING)
                                    }
                                    return@detectHorizontalDragGestures
                                }
                                // 有速度：fling 惯性滚动
                                scope.launch {
                                    // 惯性滚动到衰减尽头（或被下一个手势打断）
                                    rowScrollX.animateDecay(velocity, flingDecay)
                                    // 衰减尽头仍可能越界（惯性冲过头）：clamp + spring 弹回
                                    val minScroll = minScrollNow()
                                    val settled = rowScrollX.value.coerceIn(minScroll, 0f)
                                    if (settled != rowScrollX.value) {
                                        rawRowScroll.floatValue = settled
                                        rowScrollX.animateTo(settled, ROW_SCROLL_SPRING)
                                    } else {
                                        // 未越界：同步 raw 值（下次拖拽从当前视觉位置继续）
                                        rawRowScroll.floatValue = settled
                                    }
                                }
                            },
                            onDragCancel = {
                                // 取消（如被父级手势抢占）：与慢速松手同处理
                                val minScroll = minScrollNow()
                                rawRowScroll.floatValue = rawRowScroll.floatValue.coerceIn(minScroll, 0f)
                                scope.launch {
                                    rowScrollX.animateTo(rawRowScroll.floatValue, ROW_SCROLL_SPRING)
                                }
                            }
                        )
                    }
            ) {
                // V7.3 修复：展开态固定顶卡视觉 Y，消除"展开后向上跳变"
                //
                // 根因：堆叠态 vs 展开态"顶卡视觉 Top 相对于 Stage(0,0)"的锚点不一致。
                // - 堆叠态：顶卡放在「包围盒尺寸 + Center 对齐」的容器中，顶卡视觉 Top =
                //   cardBoxStartY + (bboxHeight - cardHeight) / 2（下方留白 > 上方，顶卡视觉上偏下）
                // - 展开态：容器顶对齐放 SharedCardRow()，顶卡视觉 Top = 0
                //   → 分支切换（animateTo 结束帧后下一次重组）瞬间，顶卡视觉 Y 突然上跳
                //   约 (bboxHeight-cardHeight)/2 像素，产生截图中的"上跳"视觉差
                //
                // 修复：展开态给卡片行追加 offset(y = topCardAnchorY)，把顶卡视觉 Top
                // 平移到与堆叠态完全相同的锚点值，分支切换时顶卡 Y 零跳变。
                //
                // V7.6：行滚动 translationX 挂在本层 graphicsLayer（clip=false，
                // 滚动平移的卡片 + 阴影都不被裁剪）
                Box(
                    modifier = Modifier
                        .offset(y = topCardAnchorY.dp)
                        .graphicsLayer {
                            this.clip = false
                            translationX = rowScrollX.value
                        }
                ) {
                    SharedCardRow()
                }
            }
        } else {
            // V5.2 修复：使用 offset 定位（不使用 padding）
            // - offset 不参与布局测量，配合 Stage 尺寸补偿解决裁剪
            // - graphicsLayer 负责 clip=false 和 3D 透视
            // V7.0 布局空间预借：
            // - offset.x 减去 StackLeftCompensation（堆叠态全额左移，随 expandProgress
            //   插值归零，展开/收起过渡平滑）
            // - size 宽度加上 StackLeftCompensation（右缘保持不变）
            // - 效果：Layer1 的 layout bounds 左缘 ≈ 屏幕左缘外 42dp，顶卡左滑轨迹
            //   （图片部分）全程落在 Layer1 bounds 内，不再溢出
            // - Center 对齐数学自洽：卡片与 Layer1 同时 +E 宽 → 卡片相对 Layer1 的
            //   Center 偏移仍为 (bbox-card)/2 ≈ 14dp，视觉位置零变化
            // V7.4 修复：offset.x 额外减去 expandedRowShiftX * expandProgress——
            // 堆叠态顶卡视觉左缘 = cardBoxStartX + (bboxWidth-cardW)/2，展开态 = 0，
            // 原先动画结束分支切换时整行突然左移该差值；现在把左移量随 expandProgress
            // 平滑注入动画过程（与卡片扇形展开同一贝塞尔曲线），结束时两分支严格衔接。
            // 注意：堆叠态稳定时（p=0）该项为 0，顶卡左滑轨迹的预借数学不受影响。
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (cardBoxStartX
                                - StackLeftCompensation.value * (1f - expandProgress.value)
                                - expandedRowShiftX * expandProgress.value).dp,
                        y = cardBoxStartY.dp
                    )
                    .size(
                        width = (bboxWidth + StackLeftCompensation.value * (1f - expandProgress.value)).dp,
                        height = bboxHeight.dp
                    )
                    // (V5.8 onGloballyPositioned 埋点已移除)
                    .graphicsLayer {
                        this.clip = false
                        this.cameraDistance = 1000f / density.density
                    },
                contentAlignment = Alignment.Center
            ) {
                SharedCardRow()
            }
        }
    }

    Box(
        modifier = modifier
            // V7.0 布局空间预借：Stage 相对父容器的水平偏移（layout 移动，非绘制平移）
            // - 堆叠态：stackStageOffsetX - StackLeftCompensation（左扩 130dp，
            //   Stage layout bounds 左缘覆盖到屏幕左缘外，顶卡左滑轨迹全程在 Stage 内）
            // - 展开态：stackStageOffsetX（视觉与 V6.x 完全一致）
            // - 随 expandProgress 插值，展开/收起过渡平滑
            // - offset 放在 size 之前不影响测量（offset 只平移 layout 位置）
            .offset(
                x = stackStageOffsetX - StackLeftCompensation * (1f - expandProgress.value)
            )
            .then(
                if (isExpanded) {
                    Modifier
                        .fillMaxWidth()
                        .height(stageBoxHeightDp)
                } else {
                    // V7.0：堆叠态宽度 + StackLeftCompensation（右缘保持不变）
                    Modifier.size(
                        width = stageBoxWidthDp + StackLeftCompensation,
                        height = stageBoxHeightDp
                    )
                }
            )
            // (V5.8 onGloballyPositioned 埋点已移除)
            .graphicsLayer { this.clip = false }
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 400,
                    easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                )
            )
    ) {
        // ============ V7.0 Stage 内容包装层 ============
        // Stage 自身左移了 StackLeftCompensation，用本包装层把内容推回原视觉位置：
        // - 堆叠态：offset(+130dp) → 内容视觉左缘 = 原 Stage 左缘（88dp），零变化
        // - 展开态：offset(0) → 内容 = Stage 左缘（原视觉位置），零变化
        // - 随 expandProgress 插值，切换动画期间内容视觉位置恒定
        //
        // V7.1 修复：包装层尺寸策略必须分态 ——
        // - 堆叠态：Stage 有显式 .size()，用 matchParentSize 锚定角标/收起/展开按钮
        // - 展开态：不能用 matchParentSize！
        //   根因：Stage 展开态是 fillMaxWidth()，但父级 WRAPPER_BOX 是
        //   wrapContentWidth(unbounded=true)（无限宽约束），fillMaxWidth 在无限宽
        //   约束下失效 → Stage 宽度回落为「内容自适应」；而 matchParentSize 子项
        //   不参与父级尺寸测量（Compose 规定），包装层是 Stage 唯一子项 →
        //   Stage 宽度塌缩为 0 → 展开动画播完后图片区整体空白、收起按钮不可见。
        //   修复：展开态用默认 wrap-content，让 Layer1 的 widthIn(min=stageBoxWidthDp)
        //   撑起 Stage 宽度（与 V6.x 包装层不存在时的结构完全等价）。
        Box(
            modifier = Modifier
                .offset(x = StackLeftCompensation * (1f - expandProgress.value))
                .then(
                    if (isExpanded) {
                        // 展开态：wrap-content（内容撑宽 Stage，修复宽度塌缩 0 的空白 bug）
                        Modifier
                    } else {
                        // 堆叠态：matchParentSize（Stage 有显式 size，锚定内部绝对定位元素）
                        Modifier.matchParentSize()
                    }
                )
        ) {
                // 展开按钮：堆叠态(derivedIsExpanded=false) 显示，展开态消失
                val expandBtnAlpha by animateFloatAsState(
                    targetValue = if (derivedIsExpanded) 0f else 1f,
                    animationSpec = OPACITY_200_SPEC,
                    label = "expandBtnAlpha",
                )
                // 图片数量 Badge：同 ExpandBtn 生命周期
                val badgeAlpha by animateFloatAsState(
                    targetValue = if (derivedIsExpanded) 0f else 1f,
                    animationSpec = OPACITY_200_SPEC,
                    label = "badgeAlpha",
                )
        Layer1CardWrapper()

        // ============ 图片计数角标（countBadge）============
        // 样式与灵感首页标签保持一致（主色文字 + 浅橙底 + 10dp 圆角）：
        // - 与 TimelineInspirationItem tags Row 对齐，视觉体系统一
        //
        // 当启用 countBadge 且图片张数超过可见深度时，在堆叠区右下角显示
        // "当前位置/总数" 格式的角标（如 "1/6"），让用户直观了解当前浏览进度。
        // 角标放在外层的 Box 内，与堆叠区捆绑为一个整体，确保未来移动堆叠区时角标同步移动。
        //
        // 显示条件：
        // - countBadge = true 启用
        // - cardCount > visibleDepth 才显示（≤ visibleDepth 时角标无意义，自动隐藏）
        //
        // 定位方式（用户方案：堆叠图左对齐，角标右对齐）：
        // - 角标左边缘 = 顶卡右边缘（0dp 间距，用户要求紧贴）
        // - 角标底边缘 = 顶卡底边缘（严格对齐）
        // - 外层 Box 右边缘已动态扩宽（含展开按钮），角标完全在 Box 内
        //
        // 实现：用 TopStart + 绝对 padding 定位，不依赖 boxHeight/bottomEdge 反算（避免 BottomEnd
        // 与 translationY 联动时测量不一致导致的偏移）。
        //
        // 角标高估算：10sp 字体 + 0dp 垂直 padding ≈ 行高 10dp ≈ 视觉高度 12dp
        // 为保证底边缘严格对齐，角标 top = 顶卡底 - 估算高度
        // V1.1 修复：角标仅堆叠态可见（展开态隐藏，避免与 CollapseBtn 视觉冲突）
        // 动画：移除 `!isExpanded` 硬条件（改为常驻 Composition），
        //       通过 animateFloatAsState alpha 1f→0f 淡出，对齐原型 opacity 0.2s
        if (showCountBadge) {
            val currentPosition = order[0].originalIndex + 1  // 1-based 当前位置
            val totalCount = cardCount

            // 精确坐标（外层 Box TopStart 坐标系）
            val badgeStartX = topCardRightX_box  // 0dp 间距：左边缘 = 顶卡右边缘
            val badgeEstH = 12f  // 10sp 字体行高 ≈ 10~12dp
            val badgeTopY = topCardBottomY_box - badgeEstH

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = badgeStartX.dp, top = badgeTopY.dp)
                    // 透明度动画：堆叠时显示，展开时淡出（避免与收起按钮视觉冲突）
                    .graphicsLayer { alpha = badgeAlpha }
            ) {
                // 与灵感首页 #标签 样式一致：
                // - 背景色：0xFFFFF3E0（浅橙底）
                // - 圆角：10.dp
                // - 文字色：UiColors.Primary（项目主题主色）
                // - 字号：10sp / lineHeight = 10sp（最小行高，紧凑型）
                // - padding：水平 1dp / 垂直 0dp（紧凑型）
                Text(
                    text = "$currentPosition/$totalCount",
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    color = UiColors.Primary,
                    modifier = Modifier
                        .background(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 1.dp, vertical = 0.dp)
                )
            }
        }

        // ============ 展开按钮（showExpandButton）============
        // 仅 UI，后续再接入点击展开逻辑。
        // 样式：灰底胶囊（比角标大一号）+ "展开 N" 文本 + 右箭头图标。
        //
        // 显示条件：showExpandButton = true && cardCount >= 2（超过 1 张就显示）
        //
        // 定位：
        // - 水平：统一以「顶卡右边缘」为基准，按钮起点 = 顶卡右 + 28dp（无论有无角标都用同一间距，
        //   保证视觉一致；有角标时 28dp 含角标宽 + 角标→按钮间距）
        // - 角标位置保持不变（顶卡右贴紧）
        // - 垂直：按钮中心 = 顶卡垂直中心（以堆叠图中的顶层图片为基准垂直居中）
        // V1.1 修复：展开按钮仅堆叠态可见（展开态隐藏，显示 CollapseBtn）
        // 动画：移除 `!isExpanded` 硬条件（改为常驻 Composition），
        //       通过 animateFloatAsState alpha 1f→0f 淡出，对齐原型 opacity 0.2s
        if (showExpand) {
            val totalCount = cardCount
            // 按钮 start（左）：统一以「顶卡右边缘」为基准，无论有无角标都使用相同的基准间距
            // 保证 3 张图（无角标）/ 10 张图（有角标）展开按钮距顶卡右边缘的视觉距离一致
            val buttonStartX = topCardRightX_box + if (showCountBadge) {
                expandMarginToCardWithBadge
            } else {
                expandMarginToCardNoBadge
            }
            val topCardCenterY = (topCardTop_box + topCardBottomY_box) / 2f
            // 按钮垂直居中：先把 wrapper Box 左上角放在「顶卡中心 y」处，
            // 再通过 onSizeChanged 读取按钮实际高度后用 graphicsLayer.translationY 回移一半，
            // 彻底摆脱 buttonEstH 估算不准导致的视觉不居中问题（之前估算 22dp vs 实际≈19dp，
            // 造成按钮中心高出顶卡中心约 1.5dp）。
            var buttonSize by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current
            val btnActualHalfOffsetDp = with(density) {
                if (buttonSize == IntSize.Zero) 0.dp
                else (buttonSize.height / 2).toDp()
            }

            // 透明时（alpha≈0）禁用 clickable，避免透明按钮还能响应点击（挡在其他元素上面）
            val isBtnClickable = expandBtnAlpha > 0.01f && isInStackedMode && !isAnimating

            // 用 TopStart + padding 精确定位：
            // - top = topCardCenterY：wrapper Box 左上角在顶卡中心线上
            // - translationY = -btnActualHalfOffsetDp：上移实际高的一半 → 真正居中
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = buttonStartX.dp, top = topCardCenterY.dp)
                    .graphicsLayer {
                        translationY = -btnActualHalfOffsetDp.toPx()
                    }
                    // 透明度动画：堆叠时显示，展开时淡出（与收起按钮互斥可见）
                    .graphicsLayer { alpha = expandBtnAlpha }
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            color = Color(0xFFF2F3F5).copy(alpha = 0.55f),
                            shape = RoundedCornerShape(11.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .onSizeChanged { buttonSize = it }
                        // 仅非透明状态才挂载 clickable，避免透明时抢点击事件
                        .then(
                            if (isBtnClickable) {
                                Modifier.clickable {
                                    // ===== V1.1 修复：展开前 dragOffset 动画归零（保险层，防堆叠态残留）=====
                                    // - Task4 已把 finalX/Y 加 !isExpanded 锁（展开态不叠加 dragOffset），
                                    //   但切换瞬间（isExpanded=true 生效前的最后一帧）若 dragOffset≠0，
                                    //   顶卡会在切换动画前先看到偏移跳变。这里动画归零，切换更平滑。
                                    // - 用 scope.launch（rememberCoroutineScope 同文件顶部）并行清零，不阻塞点击
                                    scope.launch {
                                        dragOffsetX.animateTo(0f, TRANSITION_SPRING)
                                        dragOffsetY.animateTo(0f, BOUNCE_SPRING)
                                    }
                                    // 切换到展开态 + 回调
                                    scope.launch { setExpanded(true) }
                                    onExpandStateChange?.invoke(true)
                                }
                            } else {
                                // 透明时不响应任何指针输入，让事件穿透给下层元素
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "展开 $totalCount",
                        color = Color(0xFF4F5660),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "展开全部图片",
                        tint = Color(0xFF4F5660),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ============ V1.1 修复：收起按钮（CollapseBtn）已迁移到展开态 Row 首元素 ============
        // 旧代码块（Stage 顶层 Box 的 isExpanded 分支绝对定位）已删除：
        //   - 问题：原实现在可滚动的外层 Box 内，用户左右滑动时收起按钮跟随滚动，造成"展开按钮看不到/找不到"bug
        //   - 新位置：Task 2/5 中 Row([CollapseBtn] + [ScrollArea]) 的 Row 首元素，通过 translationX 左飞出 Row
        //            独立于 ScrollArea（仅卡片区滚动），CollapseBtn 永远可见不滚走
        //            参见 L484 附近展开态分支 Row 第一子元素（Task 5 填入样式）
        }  // V7.0：Stage 内容包装层 Box 闭合（内容视觉位置补偿）

        // ============ Layer-2：全局收起按钮（V7.5b 修复：补偿 Stage 自身偏移）============
        // 根因链（V7.5 修复不彻底的原因）：
        // - V7.5 把按钮从包装层（右缘 W+216）移到 Stage TopEnd —— 但展开态 Stage 有
        //   stackStageOffsetX(88dp) 的 offset，fillMaxWidth 宽 = W-36 → Stage 右缘 =
        //   88 + W-36 = W+52，TopEnd 锚点仍在屏幕外 ~52dp，按钮照旧不可见
        //   （clip=false 无法对抗物理屏幕边界）
        // - 修复：TopEnd 锚定后追加 offset(-stackStageOffsetX × p) 补偿——展开态(p=1)
        //   按钮右缘 = W+52-88 = W-36，落在屏幕内 ✓
        // - 堆叠态(p=0)补偿为 0，按钮 alpha=0 不可见，锚点位置无影响
        // 当前位置为调试用临时位（Stage 右上角可见区、end=8dp、top=顶卡视觉 Top），
        // 后续再按设计确定最终位置。
        val collapseBtnAlpha by animateFloatAsState(
            targetValue = if (derivedIsExpanded) 1f else 0f,
            animationSpec = OPACITY_200_SPEC,
            label = "collapseBtnAlpha",
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                // 补偿 Stage 自身 stackStageOffsetX 偏移，把 TopEnd 锚点拉回屏幕内
                .offset(x = -stackStageOffsetX * expandProgress.value)
                .padding(top = topCardTopInStageDp, end = 8.dp)
        ) {
            if (showInnerCollapseButton) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = collapseBtnAlpha }
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFF2F3F5).copy(alpha = 0.55f),
                                shape = RoundedCornerShape(11.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .clickable {
                                scope.launch {
                                    // V7.6：行滚动 spring 归零后再收起（与自绘滚动配套，
                                    // 原 expandedScrollState.animateScrollTo(0) 已随 horizontalScroll 移除）
                                    rawRowScroll.floatValue = 0f
                                    rowScrollX.animateTo(0f, ROW_SCROLL_SPRING)
                                    setExpanded(false)
                                    onExpandStateChange?.invoke(false)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "收起",
                            color = Color(0xFF4F5660),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "收起图片堆叠",
                            tint = Color(0xFF4F5660),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 共享单张图片卡片渲染组件（单循环合并准备）
 *
 * Task 1 Step 4：仅预定义空壳占位，供 Task 3 平移两套循环的实现。
 * 作用域约束：Task 3 合并时，调用方必须保证 key(slot.stableId) + forEachIndexed
 * 且在同一 Composable 作用域内传入 5 个 Animatable，保证卡片实例不跨帧重建。
 *
 * @param slot 当前卡槽（含 stableId / originalIndex）
 * @param stackIndex 当前堆叠顺序（顶卡=0，越往下越大；同时是展开态 TopStart X 的 index）
 */
@Composable
private fun SwipeableImageStackCard(
    slot: CardSlot,
    stackIndex: Int,
    modifier: Modifier = Modifier
) {
    // Task 3 时平移原两套 forEachIndexed 的核心实现到这里
    // 目前仅占位：不渲染任何内容，保证 Task 1 无行为改变
}

/**
 * 动画规格常量（重排过渡 spring）
 *
 * 原型 `transition = { type: "spring", stiffness: 300, damping: 30 }`
 * 严格换算 dampingRatio ≈ 0.866，但 framer-motion 与 Compose 的 spring 底层实现不同，
 * 同参数下 Compose 过冲仅 0.5%（视觉几乎无弹簧感），framer-motion 过冲更明显。
 *
 * 用户决策：降低 dampingRatio 到 0.5（过冲 ~16%），让多张卡重排时也有明显弹簧效果，
 * 与一张卡回中的 BOUNCE_SPRING(dampingRatio=0.577) 视觉一致。
 *
 * 整体行为：spring stiffness=300, dampingRatio=0.5 → 单次动画约 500-600ms，过冲明显
 */
private val TRANSITION_SPRING = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)

/**
 * 回弹动画规格（与 Originkit 原型 `dragTransition = { bounceStiffness: 300, bounceDamping: 20 }` 对齐）
 *
 * 用于 shouldReturnToCenter=true 时顶卡 dragOffset 回到 0 的回弹动画。
 *
 * - 原型 `bounceStiffness: 300, bounceDamping: 20` 是 framer-motion drag 的回弹 spring 参数
 * - 转换：dampingRatio = 20 / (2 × sqrt(1 × 300)) = 20 / 34.64 ≈ 0.577
 * - 比 [TRANSITION_SPRING] 更软（dampingRatio 0.577 < 0.866），回弹更"Q弹"
 * - 严格对齐原型：短距离拖动后回中用 bounce spring，而非普通 transition spring
 */
private val BOUNCE_SPRING = spring<Float>(dampingRatio = 0.577f, stiffness = 300f)

/**
 * zIndex / 3D 纵深过渡动画（与 Originkit 原型 `{ duration: 0.3, ease: "easeOut" }` 对齐）
 *
 * - 原型在 zIndex 和 z 两个属性上都用 0.3s easeOut
 * - LinearOutSlowInEasing ≈ easeOut（开始快、结束慢）
 * - 用 tween 而非 spring：避免 spring 阻尼震荡影响 zIndex 的整数跳变
 */
private val ZINDEX_TWEEN = tween<Float>(durationMillis = 300, easing = LinearOutSlowInEasing)

/**
 * 无图占位文字（hasImage=false 时卡片内容）
 *
 * 与 Originkit 原型 `<p>` 标签语义一致：
 * - fontSize 14, color #9967FF, font-weight 300, padding 20, textAlign center
 * - 文本：`{card.content} — Add images in Content` = `Card N — Add images in Content`
 *
 * **注意**：此 composable 仅显示文字部分，背景+border+blur 由卡片层级渲染
 * （对齐原型 `backgroundColor / border / backdropFilter` 在 motion.div 的 style 上，
 * 而非内层 `<p>` 上）
 *
 * @param stackIndex 卡片在堆叠中的索引（用于生成 "Card N" 文本）
 */
@Composable
private fun DefaultImageText(stackIndex: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Card ${stackIndex + 1} — Add images in Content",
            color = Color(0xFF9967FF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,  // font-weight: 300
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 加载/错误占位（SubcomposeAsyncImage 异步加载期间临时显示）
 *
 * 与 Originkit 原型无图占位的视觉一致：
 * - background: rgba(243, 239, 255, 0.8) 浅紫半透明
 * - border: 1.5dp solid #9967FF 紫色边框
 * - backdropFilter: blur(10dp) 毛玻璃
 * - 文字：与 [DefaultImageText] 一致（fontSize 14, color #9967FF, weight 300）
 *
 * **与 [DefaultImageText] 的区别**：本 composable 自带背景+border+blur，
 * 用于卡片层级 hasImage=true（图片存在但加载中/失败）时避免白屏；
 * hasImage=false（图片缺失）时卡片层级已绘制背景，使用 [DefaultImageText]
 *
 * @param cardRadius 卡片圆角（与卡片层级 RoundedCornerShape 半径一致）
 * @param stackIndex 卡片在堆叠中的索引（用于生成 "Card N" 文本）
 */
@Composable
private fun DefaultImageLoading(
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

/**
 * 默认色块卡片（useDefaultColors 时卡片内容）
 *
 * 替代 Originkit 原型 `DEFAULT_IMAGES` 的 4 张外网图（imagedelivery.net），
 * Android 端改用本地 [DEFAULT_IMAGE_COLORS] 色块（用户决策，避免网络依赖）
 *
 * 视觉与原型卡片"有图"状态一致：
 * - 背景由卡片层级 transparent 接管，此处仅绘制色块
 * - 居中显示 "Card N" 文字（fontSize 32, color White, weight SemiBold）
 *   - 对齐原型 `fontSize: "32px"` 容器字号 + `card.content = Card N` 显示语义
 *
 * @param stackIndex 卡片在堆叠中的索引（用于色块循环 + "Card N" 文本）
 * @param cardRadius 卡片圆角（与卡片层级 RoundedCornerShape 半径一致，色块裁切）
 */
@Composable
private fun DefaultColorCard(
    stackIndex: Int,
    cardRadius: Dp
) {
    val color = DEFAULT_IMAGE_COLORS[stackIndex % DEFAULT_IMAGE_COLORS.size]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cardRadius))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Card ${stackIndex + 1}",
            color = Color.White,
            fontSize = 32.sp,  // 原型 fontSize: "32px"
            fontWeight = FontWeight.SemiBold
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
            visibleDepth = 4,
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
            visibleDepth = 4,
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

// ===========================================================================
// 缩略图 xOffset 候选值对比 Preview（28 / 50 / 80 / 100）
// ---------------------------------------------------------------------------
// 在 IDE 右侧 Preview 面板中通过文件名旁的图标切换 4 个 Preview，
// 或在文件结构窗格中点击不同函数名。哪个扇形最接近原型（卡片明显错开、
// 后层卡可见宽度 ≥ 20dp），xOffset 就用哪个。
// ===========================================================================

/** 缩略图预览用的色板（暖色 + 冷色 + 中性，5 张区分清晰） */
private val xOffsetPreviewPalette = listOf(
    Color(0xFFFF9A5C),  // 暖橙
    Color(0xFFFFB5C2),  // 粉
    Color(0xFF7EC8A0),  // 绿
    Color(0xFF7EB8DA),  // 蓝
    Color(0xFFB8A9D9)   // 紫
)

/**
 * 缩略图 xOffset 候选值预览（共享 Composable）
 *
 * @param xOffset 候选水平偏移值（28 / 50 / 80 / 100）
 * @param visibleDepth 可见深度（默认 4，最多 4 张扇形展开；tiltAngle 由可见张数派生）
 */
@Composable
private fun SwipeableImageStackPreviewThumb(xOffset: Dp, visibleDepth: Int = 4) {
    // tiltAngle 由可见张数派生：M=1→0°, M=2→-15°, M=3→-30°, M=4→-45°
    val derivedTilt = -((minOf(visibleDepth, xOffsetPreviewPalette.size)).coerceIn(1, 4) - 1) * 15
    Box(
        modifier = Modifier
            .size(280.dp)  // 比缩略图稍大，便于看清
            .background(Color(0xFFFFFBF5)),
        contentAlignment = Alignment.Center
    ) {
        // 顶部标签：显示当前 xOffset 值
        Text(
            text = "xOffset = ${xOffset.value.toInt()}dp · visibleDepth = ${visibleDepth} · tilt = ${derivedTilt}°",
            color = Color(0xFF666666),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        )
        // 中央堆叠
        SwipeableImageStack(
            imageUris = List(xOffsetPreviewPalette.size) { "" },
            cardWidth = 120.dp,
            cardHeight = 120.dp,
            cardRadius = 12f,
            visibleDepth = visibleDepth,
            xOffset = xOffset,
            swipeDirection = SwipeDirection.Horizontal,
            customContent = { stackIndex ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(xOffsetPreviewPalette[stackIndex % xOffsetPreviewPalette.size]),
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

/** 候选 1：xOffset = 28dp（当前 TimelineInspirationItem.kt 实际使用的值） */
@androidx.compose.ui.tooling.preview.Preview(
    name = "xOffset=28dp (当前值)",
    widthDp = 280,
    heightDp = 280
)
@Composable
private fun SwipeableImageStackPreviewThumb_28() {
    SwipeableImageStackPreviewThumb(xOffset = 28.dp)
}

/** 候选 2：xOffset = 50dp（中等扇形） */
@androidx.compose.ui.tooling.preview.Preview(
    name = "xOffset=50dp",
    widthDp = 280,
    heightDp = 280
)
@Composable
private fun SwipeableImageStackPreviewThumb_50() {
    SwipeableImageStackPreviewThumb(xOffset = 50.dp)
}

/** 候选 3：xOffset = 80dp（接近原型比例 0.67） */
@androidx.compose.ui.tooling.preview.Preview(
    name = "xOffset=80dp (推荐)",
    widthDp = 280,
    heightDp = 280
)
@Composable
private fun SwipeableImageStackPreviewThumb_80() {
    SwipeableImageStackPreviewThumb(xOffset = 80.dp)
}

/** 候选 4：xOffset = 100dp（夸张扇形） */
@androidx.compose.ui.tooling.preview.Preview(
    name = "xOffset=100dp",
    widthDp = 280,
    heightDp = 280
)
@Composable
private fun SwipeableImageStackPreviewThumb_100() {
    SwipeableImageStackPreviewThumb(xOffset = 100.dp)
}
