package com.corgimemo.app.ui.screens.inspiration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.corgimemo.app.R
import com.corgimemo.app.ui.components.SkeletonButton
import com.corgimemo.app.ui.components.SkeletonSearchBar
import com.corgimemo.app.ui.components.SkeletonText

/**
 * 灵感页面专属骨架屏组件
 *
 * 模拟灵感页面的时间线布局结构，包括：
 * - 搜索栏（horizontal=20.dp，bottom=ui_search_bar_bottom_margin）
 * - 2 个时间线条目骨架（第一条带日期栏）
 *
 * 布局与真实 InspirationScreen 完全一致：
 * - 列表水平内边距 18.dp（← InspirationScreen LazyColumn padding）
 * - 列表项间距 18.dp（← spacedBy(18.dp)）
 * - 时间线日期栏宽度 50.dp（← TimelineInspirationItem.dateColumnWidth）
 * - 日期栏→节点间距 7.dp（← dateToNodeGap）
 * - 节点直径 6.dp（← nodeDiameter）
 * - 节点→内容区间距 7.dp（← nodeToContentGap）
 * - 竖线 X=60.dp（← nodeCenterX）
 * - 竖线向上延伸 18.dp 覆盖间距（← timelineLineOverlap）
 * - 节点 Y=11.dp 对齐标题中心（← nodeCenterY）
 * - 节点色 primary（← nodeColor）
 * - 竖线色 #EEEEEE（← timelineLineColor）
 * - 底部留白 80.dp
 *
 * @param modifier 修饰符
 */
@Composable
fun InspirationSkeleton(
    modifier: Modifier = Modifier
) {
    // ===== 时间线布局常量（方案 A，必须与 TimelineInspirationItem 保持一致）=====
    val dateColumnWidth = 50.dp       // 左侧时间栏宽度
    val dateToNodeGap = 7.dp           // 时间栏右边缘到节点左边缘
    val nodeDiameter = 6.dp            // 节点直径
    val nodeToContentGap = 7.dp        // 节点右边缘到内容区左边缘
    val nodeCenterX = dateColumnWidth + dateToNodeGap + nodeDiameter / 2  // 60.dp
    val timelineLineOverlap = 18.dp    // 竖线向上延伸量（覆盖 LazyColumn 间距）
    val nodeCenterY = 11.dp            // 节点 Y 位置（对齐标题 16sp 中心）
    // 内容区垂直间距
    val titleToTimeGap = 4.dp
    val timeToContentGap = 9.dp
    val contentToTagGap = 7.dp
    // 颜色
    val nodeColor = MaterialTheme.colorScheme.primary
    val timelineLineColor = Color(0xFFEEEEEE)

    val density = LocalDensity.current
    val lineX = with(density) { nodeCenterX.toPx() }
    val overlapPx = with(density) { timelineLineOverlap.toPx() }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. 搜索栏骨架
        SkeletonSearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = dimensionResource(R.dimen.ui_search_bar_bottom_margin))
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. 时间线列表骨架
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),  // ← 必须与 InspirationScreen LazyColumn padding 一致
            verticalArrangement = Arrangement.spacedBy(18.dp)  // ← 必须与 spacedBy(18.dp) 一致
        ) {
            // 第一条：带日期栏
            item(key = "skeleton_timeline_1") {
                TimelineItemSkeleton(
                    showDate = true,
                    dateColumnWidth = dateColumnWidth,
                    dateToNodeGap = dateToNodeGap,
                    nodeDiameter = nodeDiameter,
                    nodeToContentGap = nodeToContentGap,
                    nodeCenterY = nodeCenterY,
                    nodeColor = nodeColor,
                    titleToTimeGap = titleToTimeGap,
                    timeToContentGap = timeToContentGap,
                    contentToTagGap = contentToTagGap,
                    lineX = lineX,
                    overlapPx = overlapPx,
                    timelineLineColor = timelineLineColor
                )
            }

            // 第二条：不带日期栏
            item(key = "skeleton_timeline_2") {
                TimelineItemSkeleton(
                    showDate = false,
                    dateColumnWidth = dateColumnWidth,
                    dateToNodeGap = dateToNodeGap,
                    nodeDiameter = nodeDiameter,
                    nodeToContentGap = nodeToContentGap,
                    nodeCenterY = nodeCenterY,
                    nodeColor = nodeColor,
                    titleToTimeGap = titleToTimeGap,
                    timeToContentGap = timeToContentGap,
                    contentToTagGap = contentToTagGap,
                    lineX = lineX,
                    overlapPx = overlapPx,
                    timelineLineColor = timelineLineColor
                )
            }

            // 底部留白
            item(key = "skeleton_footer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * 时间线条目骨架
 *
 * 模拟 TimelineInspirationItem 的布局：
 * [左侧日期栏 50dp] [间距 7dp] [节点 6dp] [间距 7dp] [右侧内容区]
 *
 * 竖线贯通整个 Item 高度 + 向上延伸 18dp 覆盖 LazyColumn 间距，
 * 实现连续不中断（与真实组件 drawBehind 行为一致）。
 *
 * @param showDate 是否显示左侧日期栏（同一天多条时仅第一条显示）
 * @param dateColumnWidth 日期栏宽度
 * @param dateToNodeGap 日期栏→节点间距
 * @param nodeDiameter 节点直径
 * @param nodeToContentGap 节点→内容区间距
 * @param nodeCenterY 节点 Y 位置
 * @param nodeColor 节点颜色
 * @param titleToTimeGap 标题→时间间距
 * @param timeToContentGap 时间→正文间距
 * @param contentToTagGap 正文→标签间距
 * @param lineX 竖线 X 坐标（px）
 * @param overlapPx 竖线向上延伸量（px）
 * @param timelineLineColor 竖线颜色
 */
@Composable
private fun TimelineItemSkeleton(
    showDate: Boolean,
    dateColumnWidth: Dp,
    dateToNodeGap: Dp,
    nodeDiameter: Dp,
    nodeToContentGap: Dp,
    nodeCenterY: Dp,
    nodeColor: Color,
    titleToTimeGap: Dp,
    timeToContentGap: Dp,
    contentToTagGap: Dp,
    lineX: Float,
    overlapPx: Float,
    timelineLineColor: Color
) {
    val density = LocalDensity.current
    val nodeY = with(density) { nodeCenterY.toPx() }
    val nodeRadius = with(density) { (nodeDiameter / 2).toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 竖线：贯通整个 Item 高度 + 向上延伸 18dp 覆盖间距
            // 与 TimelineInspirationItem.drawBehind 行为一致
            .drawBehind {
                val canvasHeight = this.size.height
                // 竖线起点 Y = -overlapPx（向上延伸），终点 Y = canvasHeight
                drawLine(
                    color = timelineLineColor,
                    start = Offset(lineX, -overlapPx),
                    end = Offset(lineX, canvasHeight),
                    strokeWidth = 2f
                )
                // 节点（实心圆，无 shimmer）
                drawCircle(
                    color = nodeColor,
                    radius = nodeRadius,
                    center = Offset(lineX, nodeY)
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧日期栏（50dp，仅首条显示文字占位）
            if (showDate) {
                Column(
                    modifier = Modifier.width(dateColumnWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 年月占位（12sp 高度）
                    SkeletonText(width = 0.6f, height = 12.dp)
                    Spacer(modifier = Modifier.height(2.dp))
                    // 日期数字占位（24sp 高度）
                    SkeletonText(width = 0.5f, height = 24.dp)
                }
            } else {
                // 非首条：保留 50dp 占位宽度，不绘制文字
                Spacer(modifier = Modifier.width(dateColumnWidth))
            }

            // 日期栏→节点间距
            Spacer(modifier = Modifier.width(dateToNodeGap))

            // 节点占位（节点已通过 drawBehind 绘制，此处留出宽度空间）
            Spacer(modifier = Modifier.width(nodeDiameter))

            // 节点→内容区间距
            Spacer(modifier = Modifier.width(nodeToContentGap))

            // 右侧内容区
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Top
            ) {
                // 标题行（85% 宽度，16dp 高度）
                SkeletonText(width = 0.85f, height = 16.dp)

                Spacer(modifier = Modifier.height(titleToTimeGap))

                // 时间行（30% 宽度，11dp 高度）
                SkeletonText(width = 0.3f, height = 11.dp)

                Spacer(modifier = Modifier.height(timeToContentGap))

                // 正文行1（100% 宽度，14dp 高度）
                SkeletonText(width = 1f, height = 14.dp)

                // 正文行2（70% 宽度，14dp 高度）
                SkeletonText(width = 0.7f, height = 14.dp)

                Spacer(modifier = Modifier.height(contentToTagGap))

                // 标签行（2 个标签占位）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SkeletonButton(width = 40.dp, height = 20.dp)
                    SkeletonButton(width = 48.dp, height = 20.dp)
                }
            }
        }
    }
}
