package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.theme.UiColors
import java.util.Calendar

/**
 * 时间线灵感条目组件（参考图规范版）
 *
 * 布局结构：
 * ```
 * [左侧时间栏 50dp] [间距 7dp] [节点 6dp] [间距 7dp] [右侧内容区]
 *   "2026.07"+"08"                                标题/时分时间/正文/标签
 * ```
 *
 * 字号体系（与 PRD 参考图一致）：
 * - 左侧时间栏：年月 12sp / 大号日期数字 24sp Medium
 * - 右侧内容区：标题 16sp Medium / 时分时间 11sp / 正文 14sp / 标签 11sp
 * - 中文字间距统一 +0.5sp
 *
 * 间距体系：
 * - 标题 → 时分时间：4dp
 * - 时分时间 → 正文：9dp
 * - 正文 → 标签：7dp
 * - 正文行高：21sp
 * - 标签内边距：水平 0.5dp / 垂直 0dp（紧凑型）
 * - 标签 lineHeight：11sp（等于 fontSize，最小行高）
 *
 * 横向边距：
 * - 时间栏宽度：50dp（精确匹配"2026.07"实际宽度，"2026.07"居中后右边距 = 0）
 * - 时间栏右边缘 → 节点左边缘：7dp
 * - 节点直径：6dp
 * - 节点右边缘 → 内容区左边缘：7dp
 * - 节点中心 X 坐标：60dp（=50+7+3）
 * - 内容区起始 X 坐标：70dp（=60+3+7）
 * - 时间栏内部 Column 水平居中：让"2026.07"和"08"视觉中心在同一垂直线
 * - **关键**：两个 7dp 间距视觉上相等（"2026.07"到节点 = 节点到内容区 = 7dp）
 *
 * 节点 Y 位置：固定 11dp，对齐"灵感标题"16sp Medium 中心，
 * 让"2026.07"、节点、"灵感标题"在第一行同一水平线上
 *
 * 节点显示规则：
 * - 节点（橙黄色圆点）每条灵感都显示（包括同一天内的非首条）
 * - 左侧"2026.07"+"08"日期栏仅在每天第一条灵感显示（showDate=true）
 *
 * 竖线连续性：
 * - 竖线起点 Y = -18dp（向上延伸 18dp），终点 Y = Item 高度
 * - 延伸 18dp 用于覆盖 LazyColumn.verticalArrangement = spacedBy(18.dp) 的间距
 * - 这样竖线在 Item 顶部之上 18dp 到 Item 底部范围内连续绘制，不被 Item 间距中断
 * - 由于 LazyColumn 顶部边界裁剪，第一个 Item 顶部之上 18dp 不会显示，但其他 Item 之间的间距被完整覆盖
 *
 * @param inspiration 灵感实体数据
 * @param tags 标签列表
 * @param imagePaths 图片路径列表
 * @param formattedTime 格式化后的时间字符串（如 "09:00"）
 * @param showDate 是否显示左侧日期列（同一天多条时仅第一条显示）
 * @param isPinnedItem 是否为置顶项
 * @param hideDetails 是否隐藏详情（时分时间、正文、标签、图片），仅显示标题
 * @param isBatchMode 是否为批量选择模式（节点变为 16dp 空心圆，选中后填充并打√）
 * @param isSelected 批量模式下当前条目是否被选中
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 * @param onImageClick 点击图片回调，参数为图片在 imagePaths 中的索引（用于打开全屏预览）
 * @param modifier 修饰符
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineInspirationItem(
    inspiration: Inspiration,
    tags: List<String>,
    imagePaths: List<String>,
    formattedTime: String,
    showDate: Boolean = true,
    isPinnedItem: Boolean = false,
    hideDetails: Boolean = false,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    /**
     * 关联卡片数量（v2026-07-21 新增）
     *
     * 该灵感作为源卡片（sourceType="inspiration"）的 groupId=0 关联数量。
     * 当值 > 0 时，在标签右侧显示 Link 图标 + ×N（简略信息）。
     * 由 InspirationScreen 从 [InspirationViewModel.relationCountMap] 传入。
     */
    relationCount: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onImageClick: (Int) -> Unit = {},
    /**
     * 关联数量徽章点击回调（v2026-07-22 新增）
     *
     * 当 [relationCount] > 0 时，标签行右侧的 🔗×N 区域变成可点击入口。
     * 点击后弹出 [com.corgimemo.app.ui.components.RelationListBottomSheet]，
     * 由父级 InspirationScreen 决定如何展示。
     *
     * 实现细节：在标签行 Row 内对 Icon+Text 包一层 clickable。
     * 外层 combinedClickable 仍负责卡片整体的长按/单击，
     * 内层 clickable 局部消费 tap 事件避免冒泡。
     */
    onRelationCountClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // ===== 横向布局常量 =====
    // 时间栏宽度 50dp：精确匹配"2026.07" 12sp 实际渲染宽度（约 50dp），
    // 让"2026.07"在 Column 内居中后右边距 = 0，时间栏右边缘紧贴"2026.07"右边缘。
    // 这样"2026.07" → 节点视觉距离 = 节点 → 内容区 = 7dp，两个间距视觉相等。
    val dateColumnWidth = 50.dp                 // 左侧时间栏宽度（v1.12 从 56dp 改为 50dp）
    val dateToNodeGap = 7.dp                    // 时间栏右边缘到节点左边缘（用户要求 7dp）
    val nodeDiameter = 6.dp                     // 节点直径（用户要求 6dp）
    val nodeToContentGap = 7.dp                 // 节点右边缘到内容区左边缘（用户要求 7dp）
    val nodeCenterX = dateColumnWidth + dateToNodeGap + nodeDiameter / 2  // 50 + 7 + 3 = 60dp
    val nodeRadius = nodeDiameter / 2                      // 3dp
    val contentStartX = nodeCenterX + nodeRadius + nodeToContentGap  // 60 + 3 + 7 = 70dp
    val timelineLineX = nodeCenterX                        // 竖线 X = 60dp

    // ===== 垂直间距常量 =====
    val titleToTimeGap = 4.dp                   // 标题 → 时分时间
    val timeToContentGap = 9.dp                 // 时分时间 → 正文
    val contentToTagGap = 7.dp                  // 正文 → 标签
    val tagToImageGap = 4.dp                    // 标签 → 图片
    val lazyColumnItemGap = 18.dp               // LazyColumn 相邻 Item 间距（与 InspirationScreen.kt 保持一致）
    val timelineLineOverlap = lazyColumnItemGap // 竖线向上延伸量，覆盖 Item 间 18dp 间距实现连续

    // ===== 中文字间距 =====
    val chineseLetterSpacing = 0.5.sp

    // ===== 节点 Y 位置：固定对齐"灵感标题"16sp Medium 中心 =====
    // 16sp Medium 默认 lineHeight ≈ 22dp，文字中心 y = 11dp
    // 这样节点与"2026.07"和"灵感标题"在第一行同一水平线上
    val nodeCenterY = 11.dp
    val nodeTopY = (nodeCenterY - nodeRadius).coerceAtLeast(0.dp)

    // ===== 批量模式节点尺寸 =====
    // 批量模式下节点放大为 16dp 空心圆，便于点击选择；普通模式保持 6dp 实心圆点
    // 批量模式下节点从原时间节点中心向外扩大，中心位置保持与标题对齐不变
    val nodeSize = if (isBatchMode) 16.dp else nodeDiameter
    val effectiveNodeCenterY = nodeCenterY

    // ===== 颜色 =====
    val nodeColor = if (isPinnedItem) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.primary
    val timelineLineColor = Color(0xFFEEEEEE)

    // combinedClickable 移至外层 Box，覆盖整张卡片区域，
    // 解决内容少时卡片空白处无法点击进入详情页的问题。
    // 图片区域拥有独立的 clickable，会优先消费 tap 事件，
    // 触发 onImageClick（预览页）而非外层 onClick（详情页）。
    // 长按事件由外层 combinedClickable 统一处理，弹窗逻辑保持不变。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            // 竖线贯通整个 Item 高度 + 向上延伸 18dp 覆盖 LazyColumn 间距，实现连续不中断
            // 批量模式下在空心圆节点区域留出间隙，避免竖线穿过空心圆内部
            .drawBehind {
                val x = timelineLineX.toPx()
                val startY = -timelineLineOverlap.toPx()  // 向上延伸 18dp
                if (isBatchMode) {
                    // 批量模式：竖线分两段，跳过节点区域
                    val nodeGapRadius = (nodeSize / 2).toPx()
                    val nodeCenterYPx = nodeCenterY.toPx()
                    // 上段：从顶部延伸到节点顶部
                    drawLine(
                        color = timelineLineColor,
                        start = Offset(x, startY),
                        end = Offset(x, nodeCenterYPx - nodeGapRadius),
                        strokeWidth = 2.dp.toPx()
                    )
                    // 下段：从节点底部到 Item 底部
                    drawLine(
                        color = timelineLineColor,
                        start = Offset(x, nodeCenterYPx + nodeGapRadius),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                } else {
                    // 普通模式：竖线连续绘制
                    drawLine(
                        color = timelineLineColor,
                        start = Offset(x, startY),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
    ) {
        // ========== 左侧时间栏（年月 + 大号日期）==========
        // Column 水平居中：让"2026.07"和"08"在同一垂直中线
        // - "2026.07" 12sp 宽度约 50dp，56dp 内居中
        // - "08" 24sp 宽度约 26dp，56dp 内居中
        // - 两者视觉中心都在 Column 宽度（56dp）的中点 X=28dp
        if (showDate) {
            // 日期列：外层 Box 已统一处理点击/长按，无需重复设置 clickable
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(dateColumnWidth)
                    .align(Alignment.TopStart)
            ) {
                // 年月文本（12sp 灰色）
                Text(
                    text = String.format("%04d.%02d", getYear(inspiration.createdAt), getMonth(inspiration.createdAt)),
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    letterSpacing = chineseLetterSpacing
                )
                // 大号日期数字（20sp 黑色 Medium）
                Text(
                    text = String.format("%02d", getDay(inspiration.createdAt)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = chineseLetterSpacing
                )
            }
        }

        // ========== 节点（批量模式变大空心+打√，普通模式 6dp 实心圆点）==========
        // 节点 Y 中心固定 11dp（批量模式 18dp），与"2026.07"、"灵感标题"在同一水平线
        // 节点始终显示：每条灵感都有时间节点（包括同一天内的非首条）
        Box(
            modifier = Modifier
                .offset(x = nodeCenterX - nodeSize / 2, y = effectiveNodeCenterY - nodeSize / 2)
                .size(nodeSize)
                .then(
                    if (isBatchMode) {
                        if (isSelected) {
                            Modifier.background(UiColors.Primary, CircleShape)
                        } else {
                            Modifier.border(2.dp, Color(0xFFCCCCCC), CircleShape)
                        }
                    } else {
                        Modifier.background(nodeColor, CircleShape)
                    }
                )
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            if (isBatchMode && isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // ========== 右侧内容区（标题、时分时间、正文、标签、图片）==========
        Column(
            modifier = Modifier
                .padding(start = contentStartX)
                .align(Alignment.TopStart)
        ) {
            // 文本内容区域：点击/长按由外层 Box 统一处理，水波纹效果保留
            Column {
                // 标题（16sp Medium）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPinnedItem) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "已置顶",
                            tint = Color(0xFFFF9A5C),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = inspiration.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = chineseLetterSpacing
                    )
                }

                // ===== 以下内容在隐藏详情模式下不显示 =====
                if (!hideDetails) {
                    // 标题 → 时分时间 间距
                    Spacer(modifier = Modifier.height(titleToTimeGap))

                    // 时分时间（11sp 灰色）
                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        color = Color(0xFF999999),
                        letterSpacing = chineseLetterSpacing
                    )

                    // 时分时间 → 正文 间距
                    Spacer(modifier = Modifier.height(timeToContentGap))

                    // 正文（14sp，行高 21sp，最多6行超出省略）
                    if (inspiration.content.isNotBlank()) {
                        val plainContent = removeHtmlTags(inspiration.content)
                        Text(
                            text = plainContent,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = Color(0xFF666666),
                            letterSpacing = chineseLetterSpacing,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 正文 → 标签 间距
                    // v2026-07-21: 条件增加 relationCount > 0，无标签但有关联时也显示关联数量
                    if (tags.isNotEmpty() || relationCount > 0) {
                        Spacer(modifier = Modifier.height(contentToTagGap))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 最多显示 3 个标签
                            tags.take(3).forEach { tag ->
                                Text(
                                    text = "#$tag",
                                    fontSize = 11.sp,
                                    lineHeight = 11.sp,  // 压缩行高到 fontSize，减小标签上下间距
                                    color = UiColors.Primary,
                                    letterSpacing = chineseLetterSpacing,
                                    modifier = Modifier
                                        .background(
                                            color = Color(0xFFFFF3E0),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 1.dp, vertical = 0.dp)
                                )
                            }
                            // 超出 3 个显示 "+N"
                            if (tags.size > 3) {
                                Text(
                                    text = "+${tags.size - 3}",
                                    fontSize = 11.sp,
                                    lineHeight = 11.sp,  // 压缩行高到 fontSize，减小标签上下间距
                                    color = Color(0xFF999999),
                                    letterSpacing = chineseLetterSpacing,
                                    modifier = Modifier
                                        .background(
                                            color = Color(0xFFF5F5F5),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 1.dp, vertical = 0.dp)
                                )
                            }
                            // v2026-07-21 新增：关联数量简略显示（🔗×N）
                            // 用独立 Row 包裹 Icon+Text，内部 2dp 间距，与标签间 4dp 间距
                            // v2026-07-22 增强：Row 整体可点击，触发 onRelationCountClick
                            // - 使用 Modifier.clickable 局部消费 tap 事件
                            // - 外层 combinedClickable 不会同时触发（事件已被消费）
                            if (relationCount > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .clickable { onRelationCountClick() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Link,
                                        contentDescription = "关联卡片（点击查看）",
                                        tint = Color(0xFFFF9A5C),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "×$relationCount",
                                        fontSize = 11.sp,
                                        lineHeight = 11.sp,
                                        color = Color(0xFFFF9A5C),
                                        letterSpacing = chineseLetterSpacing
                                    )
                                }
                            }
                        }
                    }
                } // end if (!hideDetails)
            } // end 文本内容 Column

            // 图片区域：拥有独立的 clickable，优先消费 tap 事件，
            // 点击图片触发 onImageClick（进入预览页）而非外层 onClick（进入详情页）
            if (!hideDetails && imagePaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(tagToImageGap))
                /**
                 * 横向滚动图片区（LazyRow）：
                 * - 固定高度 120dp，宽度按原图比例自适应（最大 200dp）
                 * - 使用 SubcomposeAsyncImage 通过 state.painter.intrinsicSize
                 *   获取原图真实宽高比，确保不拉伸
                 * - 点击图片触发 onImageClick 回调（进入全屏预览）
                 * - 横向滑动 LazyRow 不会触发外层 LazyColumn 滚动
                 */
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = imagePaths,
                        key = { index, path -> "img_${inspiration.id}_${index}_$path" }
                    ) { index, path ->
                        InspirationTimelineImage(
                            path = path,
                            onClick = { onImageClick(index) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 从时间戳提取年份
 */
private fun getYear(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.YEAR)
}

/**
 * 从时间戳提取月份（1-12）
 */
private fun getMonth(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.MONTH) + 1
}

/**
 * 从时间戳提取日（1-31）
 */
private fun getDay(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.DAY_OF_MONTH)
}

/**
 * 时间线单张图片组件
 *
 * - 固定 120dp × 120dp 方形 box，图片用 ContentScale.Crop 自动裁剪保留中间主体
 * - 使用 ImageRequest.Builder 包装路径（与项目内 InlineImagePreview/DraggableImageAttachment 一致）
 * - 圆角 12dp + 浅灰背景
 * - 点击图片触发 onClick 回调（不冒泡到外层整行点击）
 * - 通过 onState 回调记录加载状态到 logcat，便于排查图片加载失败问题
 *
 * @param path 图片路径（绝对路径或 Uri 字符串）
 * @param onClick 图片点击回调
 */
@Composable
private fun InspirationTimelineImage(
    path: String,
    onClick: () -> Unit
) {
    // 固定 120dp × 120dp 方形
    val boxSize: Dp = 120.dp

    // 获取当前 context，用于 ImageRequest.Builder
    val context = androidx.compose.ui.platform.LocalContext.current

    AsyncImage(
        model = coil3.request.ImageRequest.Builder(context)
            .data(path)
            .crossfade(true)
            .build(),
        contentDescription = "灵感图片",
        contentScale = ContentScale.Crop,
        /**
         * 监听加载状态：
         * - 加载失败时输出详细日志（path + 文件是否存在 + 异常堆栈）
         * - 便于在 logcat 中通过 "InspirationImage" tag 过滤排查
         */
        onState = { state ->
            val file = java.io.File(path)
            val exists = file.exists()
            val length = if (exists) file.length() else -1L
            when (state) {
                is AsyncImagePainter.State.Loading -> {
                    android.util.Log.d(
                        "InspirationImage",
                        "Loading: $path | file.exists=$exists"
                    )
                }
                is AsyncImagePainter.State.Success -> {
                    val painterSize = state.painter.intrinsicSize
                    android.util.Log.d(
                        "InspirationImage",
                        "Success: $path | file.exists=$exists | painterSize=$painterSize"
                    )
                }
                is AsyncImagePainter.State.Error -> {
                    val throwable = state.result.throwable
                    val throwableSimpleName = throwable.javaClass.simpleName
                    android.util.Log.e(
                        "InspirationImage",
                        "Load failed: $path | file.exists=$exists length=$length | " +
                            "error=$throwableSimpleName: ${throwable.message}",
                        throwable
                    )
                }
                else -> {
                    android.util.Log.d(
                        "InspirationImage",
                        "Other state for $path | file.exists=$exists"
                    )
                }
            }
        },
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            // 使用 clickable 处理图片点击（进入全屏预览）
            // 图片区域的 clickable 会优先消费 tap 事件，阻止冒泡到外层 combinedClickable，
            // 从而实现点击图片进入预览页、点击其他位置进入详情页的区分
            .clickable(onClick = onClick)
    )
}

/**
 * 去除HTML标签工具函数
 */
private fun removeHtmlTags(html: String): String {
    return html
        .replace("<[^>]*>".toRegex(), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()
}
