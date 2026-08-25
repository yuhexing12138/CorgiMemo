package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import com.corgimemo.app.ui.theme.UiColors

/**
 * 堆叠中的单张卡片槽位
 *
 * - `stableId`：作为 Composable key 永不变化，避免重组时丢失状态
 * - `originalIndex`：图片在原始 `imageUris` 列表中的下标，
 *   滑动重排后保持不变，用于 `onCardSwiped` 回调让业务方知道是哪张图
 */
private data class CardSlot(val stableId: Long, val originalIndex: Int)

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
    visibleDepth: Int = 4,
    swipeDirection: SwipeDirection = SwipeDirection.Horizontal,
    countBadge: Boolean = false,
    showExpandButton: Boolean = false,
    // ↓↓↓ 本次新增 ↓↓↓
    cardGap: Dp = 8.dp,                                  // 展开态卡片间距（堆叠态不使用）
    onExpandStateChange: ((Boolean) -> Unit)? = null,    // 展开/收起状态变化回调
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
    // isExpanded：组件内部管理的"堆叠/展开"模式开关
    // - false：堆叠态（现有扇形布局）
    // - true：展开态（横向排列 + horizontalScroll）
    // 由展开按钮和收起按钮点击切换，同时通过 onExpandStateChange 通知调用方
    var isExpanded by remember { mutableStateOf(false) }

    // expandedScrollState：展开态的横向滚动状态
    // - 展开时挂载到外层 Box 的 horizontalScroll
    // - 收起时通过 animateScrollTo(0) 归零
    val expandedScrollState = rememberScrollState()

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

        // 卡片平移（与主渲染逻辑一致：tx 用 xOffset.value，ty 用 8f per index）
        val tx = if (M_bbox > 1) ei.toFloat() / denom_bbox * xOffset.value else 0f
        val ty = -(ei * 8f)

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
    val stageBoxWidthDpFloat: Float = maxOf(
        cardWVal + xOffset.value,           // 扇形水平摊开
        bboxWidth,                          // 旋转+scale 包围盒
        badgeRequiredRight,                 // 1/N 角标右端
        cardBoxStartX + bboxWidth           // 顶卡左上角 + 包围盒
    )
    val stageBoxHeightDpFloat: Float = maxOf(
        cardHVal + stackVPVal,              // 扇形垂直摊开
        bboxHeight                          // 旋转+scale 包围盒高
    )
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

    Box(
        modifier = modifier
            // ==============================
            // V2.0 修复（方案A）：
            //   - 不裁剪溢出（左侧展开态的收起按钮 / 右侧展开态都不被截断）
            //   - 正确 API：Modifier.graphicsLayer { this.clip = false }（Modifier.clip 只接受 Shape）
            //   - size 永远 = stageBoxWidthDp/stageBoxHeightDp（Dp 类型），恒定不变
            // ==============================
            .graphicsLayer { this.clip = false }
            .size(stageBoxWidthDp, stageBoxHeightDp)
            // outer Stage 永远不挂 horizontalScroll（仅展开态内部 ScrollArea 独立层才挂）
        // 外层 Box 不指定 contentAlignment，使用默认 TopStart
        // - 堆叠卡片容器：align(Alignment.TopStart) + padding，左对齐外层 Box 左边缘
        // - 角标：align(Alignment.BottomEnd) + padding，右对齐外层 Box 右边缘
    ) {
        // ============ V2.0 修复（方案A）：堆叠态 / 展开态 分支结构（严格对应原型三层 DOM）============
        // 原型 Stage(外层) → stage-content{Row[CollapseBtn + ScrollArea{CardContainer}]}
        // -------------------------------------------------------------------
        // 堆叠态（!isExpanded）：完全保留 V1.0 的 CardContainer 实现（Task 3 合并单循环）
        //         保证：堆叠图位置 / "1/N" 角标位置 / "展开 N" 按钮位置绝对不变
        // 展开态（isExpanded）：Row([CollapseBtn] + Spacer(8dp) + [ScrollArea{CardContainer}])
        //         - CollapseBtn 在 Row 首元素，**不飞出（不使用负 translationX）**，
        //           整 Row 通过 padding(start=topCardLeftInStageDp, top=topCardTopInStageDp)
        //           精确定位，顶卡左上角与堆叠态顶卡左上角**像素级吻合**，
        //           CollapseBtn 不超出 Stage 左边界，不与 x=60dp 竖向时间线重叠（Bug①修复）
        //         - ScrollArea：**maxWidth=EXPANDED_SCROLL_MAX_WIDTH_DP（480dp 独立上限）**，
        //           不再绑定 stageBoxWidth≈200dp，5 张图宽度 632dp 充足（Bug②修复）
        //         - 仅 ScrollArea 挂 horizontalScroll，CollapseBtn 永远可见不跟随滚动
        if (isExpanded) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    // ==============================
                    // V2.0 修复（方案A）：精确定位 Row 起点
                    //   顶卡左上角必须精确落在 = 堆叠态顶卡左上角像素坐标
                    //   → Row(0,0) padding 偏移 = topCardLeftInStageDp / topCardTopInStageDp
                    //   → CollapseBtn 在 Row 首元素，位于顶卡左侧但不飞出 Stage，不碰时间线
                    // ==============================
                    .padding(
                        start = topCardLeftInStageDp,
                        top = topCardTopInStageDp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                // CollapseBtn 与 ScrollArea 间距 = 8dp
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- CollapseBtn（收起按钮）：独立于 ScrollArea，永远可见不滚走 ---
                // 样式：与 ExpandBtn 完全一致（半透明胶囊 #F2F3F5@55% + 11sp Medium + ChevronRight）
                // V2.0 修复（方案A）：**取消 translationX = -(76+8) = -84dp 飞出**，
                //   避免按钮飞出 Stage 左边界覆盖 x=60dp 的竖向时间线（Bug①修复）
                Box(
                    modifier = Modifier
                        // 不再飞出！保持 Row 首元素正常位置，不超出 Stage 左边界
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFF2F3F5).copy(alpha = 0.55f),
                                shape = RoundedCornerShape(11.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .clickable {
                                // 点击收起按钮 → 切换回堆叠态
                                // 设计文档 §9.8 数据流：先滚动归零，再 isExpanded=false
                                scope.launch {
                                    expandedScrollState.animateScrollTo(0)
                                    isExpanded = false
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

                // --- ScrollArea（唯一挂 horizontalScroll 的层）：仅包裹 CardContainer ---
                // V2.0 修复（方案A）：
                //   - **取消 weight(1f)**（原先=stageBoxWidth≈200dp 太窄，造成空白/重叠）
                //   - 改为 **maxWidth=EXPANDED_SCROLL_MAX_WIDTH_DP（480dp 独立上限）**，
                //     保证 5 张图×120dp+4×8dp=632dp 有充足空间（Bug②修复）
                //   - height=cardHeight（固定卡片高度，不裁剪）
                //   - contentAlignment=TopStart（CardContainer 从 ScrollArea(0,0) 开始排）
                Box(
                    modifier = Modifier
                        .widthIn(max = EXPANDED_SCROLL_MAX_WIDTH_DP)
                        .height(cardHeight)
                        .horizontalScroll(expandedScrollState),
                    contentAlignment = Alignment.TopStart
                ) {
                    // ==============================
                    // CardContainer（展开态）：V2.0 方案A
                    // - size = cardRowWidthDp × cardHeight（Task1 预计算，N 张完整排开）
                    // - contentAlignment = Alignment.TopStart（严格对齐原型 expand-collapse-stack.html）
                    //   配合 graphicsLayer targetX = stackIndex*(W+G)，顶卡左上角精确落在 (0,0)
                    // - 内部：单渲染循环（目标值 if(isExpanded) 双分支版本，见下）
                    // ==============================
                    Box(
                        modifier = Modifier
                            .size(
                                width = cardRowWidthDp,   // N 张完整排开宽度（独立上限 480dp）
                                height = cardHeight
                            )
                            .graphicsLayer {
                                this.cameraDistance = 1000f / density.density
                                translationX = 0f
                                translationY = 0f
                            },
                        contentAlignment = Alignment.TopStart  // 展开态 TopStart 锚点（★ Bug③修复核心）
                    ) {
                        // ==============================
                        // 单渲染循环：展开态/堆叠态共用一套模板（与堆叠态 else 分支结构完全一致）
                        // - 目标值双分支：isExpanded ? TopStart公式 : 扇形公式
                        // - 5 个 Animatable + LaunchedEffect 含 isExpanded key
                        // - finalX/Y/scale/rotation 拖拽互斥锁 !isExpanded
                        // - pointerInput 仅堆叠态顶卡挂载（避免与 horizontalScroll 冲突）
                        // ==============================
                        order.forEachIndexed { stackIndex, slot ->
                key(slot.stableId) {
                    val isTopCard = stackIndex == 0

                    // ========== 有无图判断（两种状态完全相同） ==========
                    val hasImage = when {
                        customContent != null -> true
                        useDefaultColors -> true
                        else -> imageUris.getOrNull(slot.originalIndex)?.isNotBlank() == true
                    }

                    // ========== 扇形深度常量（两种状态都计算，保证 remember key 稳定不重建） ==========
                    val M = minOf(visibleDepth, cardCount).coerceIn(1, 4)
                    val ei = minOf(stackIndex, M - 1)         // 有效层夹取索引（超出 visibleDepth 夹栈底）
                    val denom = max(M - 1, 1)
                    val effectiveTiltAngle = -(M - 1) * 15f
                    val cardWGap = cardWidth.value + cardGap.value

                    // ========== 目标值：if(isExpanded) 双分支（★ Bug③修复核心） ==========
                    // - 展开态：严格 TopStart 公式 = stackIndex*(W+G)（原型 expand-collapse-stack.html L358-361）
                    //   顶卡 stackIndex=0 → targetX=0 → 左上角 = CardContainer(0,0) = 堆叠态顶卡左上角
                    // - 堆叠态：扇形公式（完全保留 V1.0 像素级一致；保证堆叠态位置 100% 不变）
                    val targetX: Float
                    val targetY: Float
                    val targetScale: Float
                    val targetRotation: Float
                    val targetZIndex: Float
                    if (isExpanded) {
                        targetX = stackIndex.toFloat() * cardWGap   // ★ 展开态 TopStart 公式（Bug③修复）
                        targetY = 0f
                        targetScale = 1f
                        targetRotation = 0f
                        targetZIndex = 1f
                    } else {
                        targetX = if (M > 1) ei.toFloat() / denom * xOffset.value else 0f
                        targetY = -(ei * 8f)
                        targetScale = 1f - ei * 0.05f
                        targetRotation = if (M > 1) {
                            tiltAngleStart + (ei.toFloat() / denom) * (effectiveTiltAngle - tiltAngleStart)
                        } else {
                            tiltAngleStart
                        }
                        targetZIndex = (order.size - stackIndex).toFloat()
                    }

                    // ========== 5 个独立 Animatable（remember key 稳定，卡片实例跨重组保持） ==========
                    val positionX = remember { Animatable(targetX) }
                    val positionY = remember { Animatable(targetY) }
                    val scaleAnim = remember { Animatable(targetScale) }
                    val rotationAnim = remember { Animatable(targetRotation) }
                    val zIndexAnim = remember { Animatable(targetZIndex) }

                    // stackIndex 变化时（即 order 重排），每张卡各自平滑过渡到新目标值
                    // 关键：这是解决"卡一下"的核心——新顶卡从扇形位置平滑滑到中央，旧顶卡从中央滑到队尾
                    // 用 TRANSITION_SPRING 加速：300ms 而非 600ms，整体"翻牌"从 1100ms 降至 500ms
                    LaunchedEffect(stackIndex, order.size, isExpanded) {
                        positionX.animateTo(targetX, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size, isExpanded) {
                        positionY.animateTo(targetY, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size, isExpanded) {
                        scaleAnim.animateTo(targetScale, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size, isExpanded) {
                        rotationAnim.animateTo(targetRotation, TRANSITION_SPRING)
                    }
                    // 原型 zIndex: { duration: 0.3, ease: "easeOut" }，按下时跳 1000
                    // V1.1 修复：拖拽互斥锁 !isExpanded（展开态不允许按下抬升 zIndex）
                    LaunchedEffect(stackIndex, order.size, isExpanded, isPressed.value, isTopCard) {
                        val target = if (!isExpanded && isPressed.value && isTopCard) 1000f else targetZIndex
                        zIndexAnim.animateTo(target, ZINDEX_TWEEN)
                    }
                    // 原型 z: { duration: 0.3, ease: "easeOut" } 因 Compose 无 translationZ API 已移除，
                    // 纵深由 scaleX/Y + cameraDistance 承担（视觉差异 <1%）

                    // 渲染时：顶卡额外叠加 dragOffset；非顶卡只用 Animatable
                    // 用 isPressed（而非 dragOffsetX != 0）判断拖动状态：
                    // - 旧实现 isDragging = isTopCard && (dragOffsetX != 0) 会把"动画回零"误判为拖动中
                    // - 新顶卡重排后 dragOffsetX 还在 animateTo(0) 期间，isPressed=false
                    //   finalScale 跟随 scaleAnim 平滑过渡（0.95→1.0），不再有"从大变小"突变
                    //
                    // shouldReturnToCenter 不再强制 finalX/finalY = 0（严格对齐原型）：
                    // - 原型 getCardStyle 中 shouldReturn 对顶卡是 no-op（顶卡 index=0，
                    //   xOffsetValue=0, stackOffset=0, rotationValue=tiltAngleStart=0，
                    //   目标本来就是 (0,0,0)），1s setTimeout 仅作锁定期
                    // - 原型松手后的弹回由 framer-motion 的 drag 系统自动处理
                    //   （dragConstraints={0,0,0,0} + dragTransition.bounceStiffness/Damping）
                    // - Compose 用 dragOffsetX/Y 的 BOUNCE_SPRING 动画模拟弹回，
                    //   旧实现 shouldReturn 强制 finalX/Y=0 会覆盖该动画，导致顶卡"瞬移"到中心
                    // - 修复后：finalX/Y = positionX/Y + dragOffsetX/Y，
                    //   松手后 dragOffsetX/Y 通过 BOUNCE_SPRING 平滑回零，
                    //   顶卡视觉上从拖动位置平滑过渡到 (0,0)，与原型一致
                    // - shouldReturnToCenter 保留用于 onDragStart 重置 + finalRotation 锁定
                    // V1.1 修复：拖拽互斥锁 !isExpanded（展开态不叠加 dragOffset，避免堆叠态残留造成重叠）
                    val finalX = if (!isExpanded && isTopCard) positionX.value + dragOffsetX.value else positionX.value
                    val finalY = if (!isExpanded && isTopCard) positionY.value + dragOffsetY.value else positionY.value
                    // whileDrag 严格只作用于顶卡（与原型 drag={isTopCard} 一致）：
                    // - 原型 whileDrag={{ scale: 1.05, rotate: tiltAngleStart, zIndex: 1000 }}
                    //   因 drag={isTopCard}，只有顶卡在拖动时应用 whileDrag 样式
                    // - 下层卡片 animate 目标不变（cards 数组没变），保持原位不动
                    // - 旧实现 isPressed 是全局状态，导致拖动顶卡时所有卡片的
                    //   finalRotation=0 + finalScale+=0.05，下层卡片立即旋转归零（错误）
                    // - 修复：isTopCard && isPressed 才应用 whileDrag 样式
                    // V1.1 修复：拖拽互斥锁 !isExpanded（展开态不放大、不旋转归零）
                    val finalScale = if (!isExpanded && isTopCard && isPressed.value) scaleAnim.value + 0.05f else scaleAnim.value
                    // V1.1 修复：拖拽互斥锁 !isExpanded（展开态顶卡也不走归零逻辑）
                    val finalRotation = when {
                        !isExpanded && isTopCard && isPressed.value -> 0f
                        !isExpanded && isTopCard && shouldReturnToCenter.value -> 0f
                        else -> rotationAnim.value
                    }

                    Box(
                        modifier = Modifier
                            .size(cardWidth, cardHeight)
                            // zIndex 由 zIndexAnim 驱动：静止时 (order.size - stackIndex)，按下时 1000
                            // 用 Animatable + 0.3s easeOut 过渡，与原型 { duration: 0.3, ease: "easeOut" } 一致
                            .zIndex(zIndexAnim.value)
                            .graphicsLayer {
                                // 严格对齐原型 CSS transform 顺序：scale → rotate → translate
                                // - 原型 motion.div 的 transform: translate(x,y) rotate(r) scale(s)
                                //   CSS 从右到左应用：先 scale（围绕原位置中心缩放），
                                //   再 rotate（围绕原位置中心旋转），最后 translate（平移）
                                // - Compose graphicsLayer 内部顺序也是 scale → rotate → translate
                                // - 旧实现用 .offset() 在 graphicsLayer 之前，导致先平移再旋转
                                //   （围绕平移后的新位置中心旋转），与原型旋转点视觉位置不同
                                // - 修复：用 translationX/Y 代替 offset，让旋转围绕原位置中心进行
                                //   下层卡片上移时旋转点与原型严格一致
                                scaleX = finalScale
                                scaleY = finalScale
                                rotationZ = finalRotation
                                translationX = finalX
                                translationY = finalY
                            }
                            .shadow(
                                elevation = if (isTopCard) 8.dp else 4.dp,
                                shape = RoundedCornerShape(radiusPx)
                            )
                            .clip(RoundedCornerShape(radiusPx))
                            // 与原型严格对齐：`cardImage ? "transparent" : "rgba(243, 239, 255, 0.8)"`
                            // - hasImage=true：背景 transparent，不绘制任何背景色
                            // - hasImage=false：rgba(243, 239, 255, 0.8) 半透明紫色 + border 1.5dp solid #9967FF + backdropFilter blur(10dp)
                            // 顺序：background → blur → border（blur 模糊背景层，border 绘制清晰边框在上层）
                            .then(
                                if (hasImage) {
                                    Modifier
                                } else {
                                    Modifier
                                        .background(
                                            color = Color(0xFFF3EFFF).copy(alpha = 0.8f),  // rgba(243, 239, 255, 0.8)
                                            shape = RoundedCornerShape(radiusPx)
                                        )
                                        .blur(10.dp)  // backdropFilter: blur(10px) 毛玻璃
                                        .border(
                                            width = 1.5.dp,
                                            color = Color(0xFF9967FF),
                                            shape = RoundedCornerShape(radiusPx)
                                        )
                                }
                            )
                            .then(
                                if (isTopCard && !isExpanded) {
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
                                                    if (distance > thresholdPx && order.size > 1) {
                                                        // 严格对齐原型：重排后每张卡位置独立过渡
                                                        // - 原型：A 从拖动位置 spring 到队尾，B/C/D 从旧位置 spring 到新位置
                                                        // - 旧实现：dragOffsetX animateTo(0) 会导致两个跳变：
                                                        //   1) A 变非顶卡后 finalX=positionX(0)，丢失 dragOffset(100)，从 100 跳到 0
                                                        //   2) B 变新顶卡后 finalX=positionX+dragOffsetX，继承 A 残留，从 66.67 跳到 166.67
                                                        // - 修复：把 dragOffset 转移到 A 的 positionX/Y 上，再 dragOffset 归零
                                                        //   - A：positionX 从 0+100=100 开始，LaunchedEffect animateTo(200)，100→200 平滑飞出
                                                        //   - B：finalX=positionX(66.67)+0=66.67，无跳变，LaunchedEffect animateTo(0)，平滑过渡
                                                        scope.launch {
                                                            onCardSwiped?.invoke(slot.originalIndex)
                                                            // 1. zIndex 立即变成 1（从底层插入，不遮挡其他卡片）
                                                            // - 原型 zIndex 从 1000 过渡到 1（0.3s easeOut），但过渡过程中
                                                            //   zIndex 远大于其他卡片（2,3,4），导致顶卡在飞出过程中遮挡其他卡片
                                                            // - 修复：snapTo(1f) 立即变成最底层，从下方穿过，
                                                            //   符合"从所有底层图片下方插入最底层"的视觉
                                                            // - 重排后 targetZIndex 也是 1，LaunchedEffect 不会触发额外动画
                                                            zIndexAnim.snapTo(1f)
                                                            // 2. dragOffset 转移到 positionX/Y（A 的飞出起点 = 拖动位置，对齐原型）
                                                            positionX.snapTo(positionX.value + dragOffsetX.value)
                                                            positionY.snapTo(positionY.value + dragOffsetY.value)
                                                            // 3. dragOffset 归零（避免新顶卡 B 继承 A 的拖动残留）
                                                            dragOffsetX.snapTo(0f)
                                                            dragOffsetY.snapTo(0f)
                                                            // 4. 重排：A 的 stackIndex 0→N-1，LaunchedEffect 触发 positionX animateTo(新 targetX)
                                                            val newOrder = order.toMutableList()
                                                            val top = newOrder.removeAt(0)
                                                            newOrder.add(top)
                                                            order = newOrder
                                                        }
                                                    } else {
                                                        // 弹回中心：与原型 setShouldReturnToCenter(true) + setTimeout(1s) 一致
                                                        // dragOffset 回弹用 BOUNCE_SPRING（对齐原型 dragTransition.bounce: damping=20）
                                                        // - 一张卡时（order.size==1）也走此分支：
                                                        //   原型中一张卡重排后 framer-motion drag 系统自动回弹到 (0,0)
                                                        //   Compose 中 LaunchedEffect(stackIndex, order.size) 的 key 未变不触发
                                                        //   需手动用 BOUNCE_SPRING 回中，否则顶卡停在拖动位置(bug)
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
                                                    if (distance > thresholdPx && order.size > 1) {
                                                        // 严格对齐原型（同水平模式）：dragOffset 转移到 positionX/Y 后归零
                                                        // - A 从拖动位置 spring 到队尾（全向：拖动方向可能是斜向）
                                                        // - B/C/D 从旧位置 spring 到新位置，不继承 A 的 dragOffset 残留
                                                        scope.launch {
                                                            onCardSwiped?.invoke(slot.originalIndex)
                                                            // 1. zIndex 立即变成 1（从底层插入，不遮挡其他卡片，同水平模式）
                                                            zIndexAnim.snapTo(1f)
                                                            // 2. dragOffset 转移到 positionX/Y（A 的飞出起点 = 拖动位置）
                                                            positionX.snapTo(positionX.value + dragOffsetX.value)
                                                            positionY.snapTo(positionY.value + dragOffsetY.value)
                                                            // 3. dragOffset 归零（避免新顶卡 B 继承 A 的拖动残留）
                                                            dragOffsetX.snapTo(0f)
                                                            dragOffsetY.snapTo(0f)
                                                            // 4. 重排：LaunchedEffect 触发各卡 positionX/Y animateTo(新 targetX/Y)
                                                            val newOrder = order.toMutableList()
                                                            val top = newOrder.removeAt(0)
                                                            newOrder.add(top)
                                                            order = newOrder
                                                        }
                                                    } else {
                                                        // 弹回中心：与原型 setShouldReturnToCenter(true) + setTimeout(1s) 一致
                                                        // dragOffset 回弹用 BOUNCE_SPRING（对齐原型 dragTransition.bounce: damping=20）
                                                        // - 一张卡时（order.size==1）也走此分支：
                                                        //   原型中一张卡重排后 framer-motion drag 系统自动回弹到 (0,0)
                                                        //   Compose 中 LaunchedEffect(stackIndex, order.size) 的 key 未变不触发
                                                        //   需手动用 BOUNCE_SPRING 回中，否则顶卡停在拖动位置(bug)
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
                                    // 非顶卡 或 展开态：不挂载拖拽
                                    // - 非顶卡：保持现有行为
                                    // - 展开态：禁用翻牌手势，避免与 horizontalScroll 横向滑动冲突（设计文档 §4.6）
                                    Modifier
                                }
                            )
                    ) {
                        // ============ 卡片内容渲染 ============
                        // 三分支与原型对齐：
                        // 1. customContent != null：调用方完全自定义
                        // 2. useDefaultColors：本地色块兜底（替代原型 DEFAULT_IMAGES 网络图）
                        // 3. 真实图片：Coil 异步加载 imageUris[originalIndex]
                        //    - imageUri 非空非 blank：SubcomposeAsyncImage，loading/error 用 [DefaultImageLoading]（带背景+border+blur）
                        //    - imageUri null/blank：罕见情况，调用 [DefaultImageText]（仅文字，背景在卡片层级）
                        when {
                            customContent != null -> customContent(stackIndex)
                            useDefaultColors -> DefaultColorCard(stackIndex, radiusPx)
                            else -> {
                                val imageUri = imageUris.getOrNull(slot.originalIndex)
                                if (!imageUri.isNullOrBlank()) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(imageUri)
                                            .crossfade(true)
                                            .scale(Scale.FIT)
                                            .build(),
                                        contentDescription = "图片 ${slot.originalIndex + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        // loading/error 占位：带紫色背景+border+blur（卡片层级 transparent，需独立背景避免白屏）
                                        loading = { DefaultImageLoading(radiusPx, stackIndex) },
                                        error = { DefaultImageLoading(radiusPx, stackIndex) }
                                    )
                                } else {
                                    // hasImage=false 时卡片层级已绘制背景+border+blur，此处仅显示文字
                                    DefaultImageText(stackIndex)
                                }
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
                        }                        // ---- [24空格] 关闭 if (isTopCard && onCardClick != null) ----
                    }                        // ---- [20空格] 关闭 卡片 Box（content lambda，堆叠/展开的每张卡片内容闭包）----
                }                            // ---- [16空格] 关闭 key(slot.stableId) { ----
            }                                // ---- [12空格] 关闭 order.forEachIndexed { lambda（展开态渲染循环）----
                    }                        // ---- [20空格] 关闭 CardContainer Box（与 L564 Box( 20空格前缀严格对齐）----
                }                            // ---- [16空格] 关闭 ScrollArea Box（与 L544 Box( 16空格前缀严格对齐）----
            }                                // ---- [12空格] 关闭 Row([CollapseBtn] + [ScrollArea])（与 L485 Row( 12空格前缀严格对齐）----
        }                                    // ---- [8空格] 关闭 if (isExpanded) 展开态分支（与 L484 if 8空格前缀严格对齐）----
        else {                                // ---- [8空格] else 堆叠态分支（与 if 同级，8空格前缀严格对齐）----
        // ===== 堆叠态分支：CardContainer 完全保留 V1.0（保证堆叠视觉和位置100%不变）=====
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(bboxWidth.dp, bboxHeight.dp)
                .graphicsLayer {
                    this.cameraDistance = 1000f / density.density
                    // 堆叠态平移到正确位置（使旋转后最左/最上顶角恰好对齐 Stage 左上角）
                    translationX = cardBoxStartX
                    translationY = cardBoxStartY
                },
            contentAlignment = Alignment.Center // 堆叠态 Center 锚点（与展开态统一）
        ) {
            // ===== 堆叠态渲染循环（完全保留 V1.0 代码，与展开态分开分支）=====
            order.forEachIndexed { stackIndex, slot ->
                key(slot.stableId) {
                    val isTopCard = stackIndex == 0

                    // ========== 有无图判断（与展开态循环结构完全一致） ==========
                    val hasImage = when {
                        customContent != null -> true
                        useDefaultColors -> true
                        else -> imageUris.getOrNull(slot.originalIndex)?.isNotBlank() == true
                    }

                    // ========== 扇形深度常量（与展开态循环完全一致，保证 remember key 稳定） ==========
                    val M = minOf(visibleDepth, cardCount).coerceIn(1, 4)
                    val ei = minOf(stackIndex, M - 1)
                    val denom = max(M - 1, 1)
                    val effectiveTiltAngle = -(M - 1) * 15f
                    val cardWGap = cardWidth.value + cardGap.value

                    // ========== 目标值：if(isExpanded) 双分支（与展开态循环结构完全一致） ==========
                    // - 堆叠态分支：isExpanded=false → 走扇形公式（完全保留 V1.0 像素级一致）
                    // - 展开态分支：理论上此分支永远不触发（因为在 else 分支内），仅为结构一致性保留
                    val targetX: Float
                    val targetY: Float
                    val targetScale: Float
                    val targetRotation: Float
                    val targetZIndex: Float
                    if (isExpanded) {
                        targetX = stackIndex.toFloat() * cardWGap
                        targetY = 0f
                        targetScale = 1f
                        targetRotation = 0f
                        targetZIndex = 1f
                    } else {
                        targetX = if (M > 1) ei.toFloat() / denom * xOffset.value else 0f
                        targetY = -(ei * 8f)
                        targetScale = 1f - ei * 0.05f
                        targetRotation = if (M > 1) {
                            tiltAngleStart + (ei.toFloat() / denom) * (effectiveTiltAngle - tiltAngleStart)
                        } else {
                            tiltAngleStart
                        }
                        targetZIndex = (order.size - stackIndex).toFloat()
                    }

                    // ========== 5 个独立 Animatable（与展开态循环完全一致） ==========
                    val positionX = remember { Animatable(targetX) }
                    val positionY = remember { Animatable(targetY) }
                    val scaleAnim = remember { Animatable(targetScale) }
                    val rotationAnim = remember { Animatable(targetRotation) }
                    val zIndexAnim = remember { Animatable(targetZIndex) }

                    // 5 个 LaunchedEffect（含 isExpanded key，切换状态立即触发过渡）
                    LaunchedEffect(stackIndex, order.size, isExpanded) {
                        positionX.animateTo(targetX, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size, isExpanded) {
                        positionY.animateTo(targetY, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size, isExpanded) {
                        scaleAnim.animateTo(targetScale, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size, isExpanded) {
                        rotationAnim.animateTo(targetRotation, TRANSITION_SPRING)
                    }
                    LaunchedEffect(stackIndex, order.size, isExpanded, isPressed.value, isTopCard) {
                        val target = if (!isExpanded && isPressed.value && isTopCard) 1000f else targetZIndex
                        zIndexAnim.animateTo(target, ZINDEX_TWEEN)
                    }

                    // =================== finalX/Y/scale/rotation：统一 !isExpanded 拖拽互斥锁 ===================
                    // V2.0 修复（堆叠态循环缺失补上）：
                    // - finalScale：原堆叠态缺少 `!isExpanded`，展开态切回堆叠态瞬间若 isPressed=true 会误放大
                    // - finalRotation：原堆叠态缺少 `!isExpanded`，切换瞬间会误旋转归零
                    val finalX = if (!isExpanded && isTopCard) positionX.value + dragOffsetX.value else positionX.value
                    val finalY = if (!isExpanded && isTopCard) positionY.value + dragOffsetY.value else positionY.value
                    val finalScale = if (!isExpanded && isTopCard && isPressed.value) scaleAnim.value + 0.05f else scaleAnim.value
                    val finalRotation = when {
                        !isExpanded && isTopCard && isPressed.value -> 0f
                        !isExpanded && isTopCard && shouldReturnToCenter.value -> 0f
                        else -> rotationAnim.value
                    }

                    Box(
                        modifier = Modifier
                            .size(cardWidth, cardHeight)
                            .zIndex(zIndexAnim.value)
                            .graphicsLayer {
                                scaleX = finalScale
                                scaleY = finalScale
                                rotationZ = finalRotation
                                translationX = finalX
                                translationY = finalY
                            }
                            .shadow(
                                elevation = if (isTopCard) 8.dp else 4.dp,
                                shape = RoundedCornerShape(radiusPx)
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
                                if (isTopCard && !isExpanded) {
                                    Modifier.pointerInput(slot.stableId, swipeDirection) {
                                        if (swipeDirection == SwipeDirection.Horizontal) {
                                            detectHorizontalDragGestures(
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
                                                    val distance = dx.absoluteValue
                                                    if (distance > thresholdPx && order.size > 1) {
                                                        scope.launch {
                                                            onCardSwiped?.invoke(slot.originalIndex)
                                                            zIndexAnim.snapTo(1f)
                                                            positionX.snapTo(positionX.value + dragOffsetX.value)
                                                            positionY.snapTo(positionY.value + dragOffsetY.value)
                                                            dragOffsetX.snapTo(0f)
                                                            dragOffsetY.snapTo(0f)
                                                            val newOrder = order.toMutableList()
                                                            val top = newOrder.removeAt(0)
                                                            newOrder.add(top)
                                                            order = newOrder
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
                                                            onCardSwiped?.invoke(slot.originalIndex)
                                                            zIndexAnim.snapTo(1f)
                                                            positionX.snapTo(positionX.value + dragOffsetX.value)
                                                            positionY.snapTo(positionY.value + dragOffsetY.value)
                                                            dragOffsetX.snapTo(0f)
                                                            dragOffsetY.snapTo(0f)
                                                            val newOrder = order.toMutableList()
                                                            val top = newOrder.removeAt(0)
                                                            newOrder.add(top)
                                                            order = newOrder
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
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        when {
                            customContent != null -> customContent(stackIndex)
                            useDefaultColors -> DefaultColorCard(stackIndex, radiusPx)
                            else -> {
                                val imageUri = imageUris.getOrNull(slot.originalIndex)
                                if (!imageUri.isNullOrBlank()) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(imageUri)
                                            .crossfade(true)
                                            .scale(Scale.FIT)
                                            .build(),
                                        contentDescription = "图片 ${slot.originalIndex + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        loading = { DefaultImageLoading(radiusPx, stackIndex) },
                                        error = { DefaultImageLoading(radiusPx, stackIndex) }
                                    )
                                } else {
                                    DefaultImageText(stackIndex)
                                }
                            }
                        }

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
        } // ===== 堆叠态 Box(CardContainer) 闭合
    } // ===== else 堆叠态分支 闭合

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
        // V1.1 修复：角标仅堆叠态显示（展开态隐藏，避免与 CollapseBtn 视觉冲突）
        if (!isExpanded && showCountBadge) {
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
        // V1.1 修复：展开按钮仅堆叠态显示（展开态隐藏，显示 CollapseBtn）
        if (!isExpanded && showExpand) {
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
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            color = Color(0xFFF2F3F5).copy(alpha = 0.55f),
                            shape = RoundedCornerShape(11.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .onSizeChanged { buttonSize = it }
                        .clickable {
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
                            isExpanded = true
                            onExpandStateChange?.invoke(true)
                        },
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
