package com.corgimemo.app.ui.screens.common

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.corgimemo.app.ui.components.safeAreaForTopBar /** 安全区域内边距：顶栏状态栏*/
import com.corgimemo.app.ui.components.safeAreaForBottomBar /** 安全区域内边距：底栏导航栏*/
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.corgimemo.app.util.ImageUtils

/**
 * 图片全屏预览页面
 * 提供沉浸式的图片浏览体验，支持左右滑动切换、双指缩放、删除操作
 *
 * 功能特性：
 * - ✅ 沉浸式全屏显示（隐藏系统UI栏）
 * - ✅ HorizontalPager 左右滑动切换多张图片
 * - ✅ 双指捏合缩放（支持 1x-3x 缩放范围）
 * - ✅ 底部页码指示器（如 "2 / 5"）
 * - ✅ 顶部关闭按钮（×）
 * - ✅ 右上角删除按钮（可选，用于编辑模式）
 * - ✅ 使用 Coil AsyncImage 加载高清原图
 *
 * 使用场景：
 * - 从待办/灵感/日期编辑页的缩略图点击进入
 * - 支持查看已插入的所有关联图片
 *
 * @param imagePaths 要预览的图片路径列表（内部存储绝对路径）
 * @param initialIndex 初始显示的图片索引位置（默认为0，即第一张）
 * @param onDeleteClick 删除按钮点击回调（传入当前图片索引，可选）
 * @param onDismiss 页面关闭回调（返回键或点击关闭按钮时触发）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImagePreviewScreen(
    imagePaths: List<String>,
    initialIndex: Int = 0,
    onDeleteClick: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    /**
     * 沉浸式全屏模式生命周期管理
     *
     * 技术原理：
     * 1. setDecorFitsSystemWindows(false) → 启用 edge-to-edge，内容可延伸到系统栏下方
     * 2. hide(systemBars()) → 隐藏状态栏 + 导航栏，实现纯黑全屏
     * 3. BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE → 用户从边缘滑动时可临时唤出系统栏
     * 4. onDispose → 退出预览时自动恢复系统栏和正常布局（防止泄漏到其他页面）
     */
    DisposableEffect(Unit) {
        val window = (context as Activity).window

        /** 启用 edge-to-edge 模式：让内容绘制区域延伸到状态栏/导航栏下方 */
        WindowCompat.setDecorFitsSystemWindows(window, false)

        /** 获取系统栏控制器 */
        val controller = WindowCompat.getInsetsController(window, view)

        /** 隐藏状态栏和导航栏（进入纯沉浸模式） */
        controller.hide(WindowInsetsCompat.Type.systemBars())
        /**
         * 设置系统栏交互行为：
         * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE - 用户从屏幕边缘滑动时临时显示系统栏，
         *   短暂显示后自动再次隐藏（类似微信相册/Google Photos 的体验）
         */
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            /** 退出预览时恢复系统栏显示 */
            controller.show(WindowInsetsCompat.Type.systemBars())
            /** 恢复正常布局模式（内容不再延伸到系统栏下方） */
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    /**
     * HorizontalPager 状态管理
     * 记录当前页面位置、滑动动画等状态
     */
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imagePaths.size }
    )

    /** 当前显示的图片索引（0-based） */
    var currentPage by remember { mutableStateOf(initialIndex) }

    /** 监听 Pager 页面变化，同步更新 currentPage */
    LaunchedEffect(pagerState.currentPage) {
        currentPage = pagerState.currentPage
    }

    /**
     * 全屏容器
     * 黑色背景 + 层叠布局（底层图片 + 上层控制按钮）
     */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) /** 纯黑背景，突出图片 */
            .clickable { onDismiss() } /** 点击空白区域关闭预览 */
    ) {
        /**
         * HorizontalPager 核心组件
         * 实现左右滑动切换图片的交互
         */
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            /** 单张图片预览项 */
            PreviewImageItem(
                imagePath = imagePaths[page],
                onPageChanged = { /** 页面变化时可执行额外操作 */ }
            )
        }

        /**
         * 顶部操作栏
         * 包含：左侧关闭按钮 | 右侧删除按钮（可选）
         * 使用 safeAreaForTopBar 动态适配刘海屏/挖孔屏等异形屏幕
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .safeAreaForTopBar()
                .padding(horizontal = 16.dp)
        ) {
            /** 关闭按钮（左上角） */
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭预览",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            /** 删除按钮（右上角，仅在编辑模式下显示） */
            if (onDeleteClick != null) {
                IconButton(
                    onClick = { onDeleteClick(currentPage) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除图片",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        /**
         * 底部页码指示器
         * 显示格式："当前页 / 总数"（如 "2 / 5"）
         * 使用 safeAreaForBottomBar 动态适配不同导航模式（手势导航/三键导航）
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .safeAreaForBottomBar(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "${currentPage + 1} / ${imagePaths.size}",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 单张图片预览项组件
 * 支持双指捏合缩放交互（1x - 3x 范围）
 *
 * 技术实现：
 * - 使用 detectTransformGestures 手势检测器捕获缩放手势
 * - 通过 graphicsLayer.scaleX/Y 应用缩放变换
 * - 边界限制：最小1x（原始大小），最大3x（3倍放大）
 * - 使用 Coil AsyncImage 加载高质量原图
 *
 * @param imagePath 图片绝对路径
 * @param onPageChanged 页面切换回调（预留扩展）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewImageItem(
    imagePath: String,
    onPageChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    /** 当前缩放比例（初始值为 1.0，表示原始大小） */
    var scale by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                /**
                 * 手势检测器：监听双指捏合缩放动作
                 * onGesture 回调参数：
                 * - centroid: 两指中心点位置
                 * - pan: 平移偏移量
                 * - zoom: 缩放因子（>1 表示放大，<1 表示缩小）
                 */
                detectTransformGestures(
                    onGesture = { centroid, pan, zoom, _ ->
                        /** 计算新的缩放值，限制在 1x - 3x 范围内 */
                        val newScale = (scale * zoom).coerceIn(1f, 3f)
                        scale = newScale
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        /**
         * 高清图片加载
         * 使用 Coil AsyncImage 加载原始尺寸图片（非缩略图）
         * 配置说明：
         * - crossfade(true): 启用淡入过渡效果（提升视觉体验）
         * - ContentScale.Fit: 保持宽高比适配容器（避免变形裁切）
         */
        AsyncImage(
            model = coil3.request.ImageRequest.Builder(LocalContext.current)
                .data(imagePath)
                .crossfade(true) /** 启用渐入动画 */
                .build(),
            contentDescription = "预览图片",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale, /** 应用 X 轴缩放 */
                    scaleY = scale  /** 应用 Y 轴缩放 */
                ),
            contentScale = ContentScale.Fit /** 保持比例适配（适合预览场景）*/
        )
    }
}
