// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt
package com.corgimemo.app.ui.screens.inspiration.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.corgimemo.app.ui.components.AppSnackbarHost
import com.corgimemo.app.util.InspirationScreenshot
import compose.icons.LucideIcons
import compose.icons.lucideicons.RotateCcwSquare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 灵感图片全屏预览
 *
 * 使用独立 Dialog Window 渲染，覆盖全屏（含 MainScreen 的 AppBar/BottomBar/柯基悬浮球）。
 * 通过 DialogWindowProvider 将 Dialog Window 强制设为 MATCH_PARENT；内容延伸到系统栏
 * 后面（edge-to-edge）。
 *
 * 深色背景（黑色），使用 HorizontalPager 支持多图滑动翻页
 * 双指捏合缩放（1x~4x），双击放大/还原，点击右上角 X 按钮关闭
 *
 * v2026-07-24 新增：
 * - 左下角删除按钮（Material Icons Outlined.Delete）
 * - 点击删除弹出二次确认 AlertDialog（标题/说明/取消/红色删除按钮）
 * - onDeleteClick 为 null 时仅 Snackbar 提示「演示功能」，便于后续接入实际删除逻辑
 *
 * v2026-09-10 新增（对照原型）：
 * - 右下角「详情」按钮（保存左侧，Icons.Outlined.Info）：打开图片详情页
 *   [ImageDetailPage]——白底信息页，展示拍摄时间（EXIF）/ 文件信息 / 文件路径
 * - 竖屏：系统栏始终显示、UI 按 insets 避让（顶部留出**半个状态栏高度**的间距）
 * - 点击页面切换标题/页码/全部按钮显隐（翻页/缩放是拖拽，滑动不触发）
 * - 详情页打开时系统返回键先关详情页（BackHandler），不退出附件页
 *
 * v2026-09-10 横屏查看（**真旋转窗口**版，本次重做）：
 * - 右上「横屏查看」按钮（退出左侧，LucideIcons.RotateCcwSquare）：
 *   通过 `Activity.requestedOrientation` 让**宿主 Activity 真正转到横屏**，
 *   而不是把内容层旋转 90° 伪装横屏。好处：
 *   ① 横屏后 Pager 的翻页方向就是屏幕左右方向，符合直觉；
 *   ② 系统栏隐藏彻底（窗口本身就处于横屏配置）；
 *   ③ UI 布局无需再做「局部坐标 → 屏幕坐标」的旋转换算。
 * - 前置条件：MainActivity 已在清单声明 `configChanges`（orientation|screenSize|…），
 *   保证旋转时 Activity 不重建、本 Dialog 不被销毁。
 * - 退出附件页 / 退出横屏时，方向恢复为 `SCREEN_ORIENTATION_UNSPECIFIED`（跟随系统）。
 *
 * @param imagePaths 图片绝对路径列表
 * @param initialIndex 初始显示的图片索引
 * @param onDeleteClick 删除按钮点击回调（传入当前图片索引，可选）。
 *        为 null 时删除按钮仍显示，确认后只显示 Snackbar 提示，便于后续接入。
 * @param onDismiss 关闭回调
 */
@Composable
fun InspirationImageGallery(
    imagePaths: List<String>,
    initialIndex: Int,
    onDeleteClick: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    // 空列表直接关闭
    if (imagePaths.isEmpty()) {
        onDismiss()
        return
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) { imagePaths.size }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /**
     * 二次确认对话框显示状态
     * 点击左下角删除按钮时置 true，用户点击「取消」或「删除」后置 false
     * 用于实现"删除图片？"的二次确认交互，避免误触
     */
    var showDeleteConfirm by remember { mutableStateOf(false) }

    /**
     * 图片详情页显示状态（v2026-09-10 新增）
     * 点右下角「详情」按钮打开，展示当前图片的拍摄时间 / 文件信息 / 文件路径；
     * 顶栏返回箭头或系统返回键关闭后回到沉浸预览。
     */
    var showDetail by remember { mutableStateOf(false) }

    /**
     * 横屏查看状态（v2026-09-10 重做：**真旋转窗口**）
     * true = 让宿主 Activity 真正转到横屏（`requestedOrientation = LANDSCAPE`）
     * + 彻底隐藏系统栏；再点按钮转回竖屏（`UNSPECIFIED`，恢复跟随系统）。
     *
     * 不再使用「把内容层旋转 90°」的伪横屏方案——那样翻页方向会被换算成屏幕上下滑动，
     * 与用户直觉相反，且无法真正隐藏系统栏。
     */
    var landscape by remember { mutableStateOf(false) }

    /**
     * 浏览层 UI（标题/页码/全部按钮）显隐（v2026-09-10）：
     * 点击页面切换；翻页/缩放是拖拽不是点击，**滑动不触发出现**。
     */
    var chromeVisible by remember { mutableStateOf(true) }

    /** 打开详情页瞬间的图片路径（在点击回调里定格，避免之后 pager 翻页 /
     * 列表变化导致覆盖层取错图）。 */
    var detailImagePath by remember { mutableStateOf<String?>(null) }

    // 详情页打开时，系统返回键先关详情页而不是退出附件页
    BackHandler(enabled = showDetail) { showDetail = false }

    // 下载当前显示的图片到相册：使用 BitmapFactory.decodeFile 直接解码（比 Coil 更直接，无压缩），再复用 InspirationScreenshot 保存到系统相册
    // IO 线程执行避免主线程阻塞，Snackbar 反馈结果
    fun downloadCurrentImage() {
        val path = imagePaths.getOrNull(pagerState.currentPage) ?: return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap == null) {
                        Log.w("InspirationImageGallery", "图片不存在或无法解码: $path")
                        return@withContext false
                    }
                    InspirationScreenshot.saveToGallery(context, bitmap) != null
                } catch (e: Exception) {
                    Log.e("InspirationImageGallery", "下载失败: $path", e)
                    false
                }
            }
            snackbarHostState.showSnackbar(
                if (saved) "已保存到相册" else "保存失败"
            )
        }
    }

    // 独立 Dialog Window 渲染：覆盖全屏（Activity 内的所有内容均不可见）
    // 关键：通过 DialogWindowProvider 强制设置 Window 尺寸为 MATCH_PARENT（Dialog 默认 wrap_content），
    // 并对 Dialog Window 应用粘性沉浸式（不是 Activity Window），避免与系统状态栏/导航栏重叠
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 关键：LocalView.current 在 Dialog content 中就是 DialogLayout 自身，
        // DialogLayout extends AbstractComposeView implements DialogWindowProvider，
        // 所以 view 自己就是 DialogWindowProvider；view.parent 是 Dialog 的 content FrameLayout，
        // 不是 DialogWindowProvider。
        val view = LocalView.current
        val dialogWindow = remember(view) {
            (view as? DialogWindowProvider)?.window
        }
        /**
         * 宿主 Activity（v2026-09-10 提前声明）：
         * 「横屏查看」需要真正改它的屏幕方向，同时它也是系统栏高度/挖孔安全区的可靠来源
         * （Dialog 子窗口上报的 insets 在部分 ROM 上不可靠，见下方 insets 读取段）。
         */
        val activity = LocalContext.current.findActivity()
        /**
         * 沉浸式控制器（v2026-09-10 提为共享实例）：
         * 附件页隐藏系统栏 ↔ 详情页显示系统栏（白底 → 深色图标）两处共用。
         */
        val insetsController = remember(dialogWindow) {
            dialogWindow?.let { WindowInsetsControllerCompat(it, it.decorView) }
        }
        DisposableEffect(dialogWindow) {
            val window = dialogWindow
            if (window == null) {
                onDispose { }
            } else {
                // 强制 Dialog Window 尺寸为 MATCH_PARENT x MATCH_PARENT
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // 让 Dialog 内容延伸到系统栏后面（黑底铺到状态栏/手势条后面）
                WindowCompat.setDecorFitsSystemWindows(window, false)
                /**
                 * v2026-09-10 改为**竖屏下始终显示系统栏**（不再无条件沉浸隐藏）：
                 * 实测部分 ROM 在 hide() 后仍绘制状态栏/手势条，但窗口上报的 insets
                 * 已归零——「看得见的栏 + 为零的 insets」，UI 无法避让 → 重叠。
                 * 始终显示后 insets 永远真实，UI 按 insets 动态避让（见 uiSafePadding）。
                 * （横屏下的"彻底隐藏"在下方 LaunchedEffect(landscape) 单独处理。）
                 */
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
                insetsController?.isAppearanceLightStatusBars = false
                insetsController?.isAppearanceLightNavigationBars = false
                onDispose {
                    insetsController?.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        /**
         * 横屏查看的方向控制（v2026-09-10 重做：**真旋转窗口**）：
         * - landscape = true  → 请求宿主 Activity 转到横屏（系统窗口本身横过来，
         *   而不是把内容层旋转 90° 伪装横屏）；
         * - landscape = false → 恢复 `UNSPECIFIED`（交还系统自动旋转策略）。
         *
         * 前置条件：MainActivity 已在清单声明 `configChanges`（orientation|screenSize|…），
         * 否则该请求会重建 Activity，把本 Dialog 一并销毁。
         */
        LaunchedEffect(activity, landscape) {
            try {
                activity?.requestedOrientation = if (landscape) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            } catch (_: Exception) {
                // Activity 正在销毁时设置方向可能抛异常，忽略（退出路径还有兜底恢复）
            }
        }

        /**
         * 离开附件页（Dialog 销毁）时的兜底恢复：
         * - 屏幕方向恢复 `UNSPECIFIED`，避免把宿主 Activity 永久锁在横屏；
         * - 宿主 Activity 窗口的系统栏恢复显示，避免残留沉浸态影响后续页面。
         */
        DisposableEffect(activity) {
            onDispose {
                try {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    activity?.window?.let { w ->
                        WindowInsetsControllerCompat(w, w.decorView)
                            .show(WindowInsetsCompat.Type.systemBars())
                    }
                } catch (_: Exception) {
                    // 忽略 Activity 已销毁的异常
                }
            }
        }

        // 系统栏显隐与图标明暗（v2026-09-10 重做：横屏作用于「Dialog 窗口 + 宿主窗口」双通道）：
        // - 横屏：**彻底隐藏**状态栏与手势条——同时请求 Dialog 子窗口与宿主 Activity 窗口，
        //   个别 ROM 只认 Activity 窗口的隐藏请求，双通道可保证「彻底」；
        //   配 BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE，边缘误触后短暂出现再自动收回。
        // - 竖屏：始终显示；附件页黑底 → 浅色图标，详情页白底 → 深色图标。
        //   （宿主窗口只做 show/hide，**不改**图标明暗，避免退出后残留错误明暗）
        LaunchedEffect(showDetail, landscape, insetsController, activity) {
            val hostController = activity?.window?.let { w ->
                WindowInsetsControllerCompat(w, w.decorView)
            }
            if (landscape) {
                insetsController?.run {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                hostController?.run {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                insetsController?.run {
                    show(WindowInsetsCompat.Type.systemBars())
                    isAppearanceLightStatusBars = showDetail
                    isAppearanceLightNavigationBars = showDetail
                }
                hostController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        /**
         * 当前窗口配置：作为 insets 重读的 key —— 真横屏后窗口配置变化，
         * 需要重新测量系统栏高度与挖孔安全区（同时 Compose 借此自动重组本页）。
         */
        val configuration = LocalConfiguration.current

        /**
         * 旋转后重新声明 Dialog Window 尺寸（v2026-09-10）：
         * Dialog Window 的 LayoutParams 已是 MATCH_PARENT，但个别 ROM 在配置变化后
         * 会把子窗口尺寸回退成 wrap_content，这里再声明一次做保险，避免横屏后
         * 预览区缩成一小块。
         */
        LaunchedEffect(dialogWindow, configuration) {
            dialogWindow?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        /**
         * 系统栏 / 挖孔安全边距（v2026-09-10 第三次调整）：
         *
         * **为什么从 Activity 主窗口读，而不是 Dialog 自己的 WindowInsets**：
         * Compose Dialog 是子窗口，实测部分 ROM/系统组合下派发给它的
         * statusBars/navigationBars insets 为 0（系统栏却仍绘制）→ 在 Dialog 里
         * 读 insets 这条路不可靠。系统栏高度是**全局显示状态**，Activity 主窗口的
         * rootWindowInsets 永远是真实值，所以经 context 解包拿到 Activity 再读。
         *
         * 读取时机：进附件页时 + 每次横竖屏切换（[configuration] 变化）时各读一次。
         */
        var statusBarTopPx by remember { mutableStateOf(0) }
        var navBarBottomPx by remember { mutableStateOf(0) }
        /** 挖孔（刘海）安全区四边：横屏时系统栏已隐藏，只有挖孔仍需避让 */
        var cutoutLeftPx by remember { mutableStateOf(0) }
        var cutoutTopPx by remember { mutableStateOf(0) }
        var cutoutRightPx by remember { mutableStateOf(0) }
        var cutoutBottomPx by remember { mutableStateOf(0) }
        /** 读取系统栏 / 挖孔安全边距（以 Activity 主窗口为准，原因见上方注释） */
        fun readSafeInsets() {
            val root = activity?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
                ?: return
            /**
             * 系统栏高度**仅在竖屏时更新**，且用「> 0」守卫：
             * 横屏下系统栏已 hide，insets 归零；退出横屏的一瞬间系统栏可能还没 show 回来，
             * 若直接覆盖会让竖屏 UI 贴到屏幕顶 —— 保留上一次的真实高度做兜底。
             */
            if (!landscape) {
                val bars = root.getInsets(WindowInsetsCompat.Type.systemBars())
                if (bars.top > 0) statusBarTopPx = bars.top
                if (bars.bottom > 0) navBarBottomPx = bars.bottom
            }
            /** 挖孔安全区与系统栏显隐无关，横竖屏都读（横屏时挖孔位于屏幕左/右侧） */
            val cutout = root.getInsets(WindowInsetsCompat.Type.displayCutout())
            cutoutLeftPx = cutout.left
            cutoutTopPx = cutout.top
            cutoutRightPx = cutout.right
            cutoutBottomPx = cutout.bottom
        }
        DisposableEffect(activity, landscape, configuration) {
            readSafeInsets()
            onDispose { }
        }
        /**
         * 旋转动画结束后再补读一次：系统旋转是异步的（约 300ms 完成），
         * 进入横屏的瞬间读到的仍是旋转前的挖孔位置，补读一次可拿到正确值。
         */
        LaunchedEffect(activity, landscape) {
            delay(350)
            readSafeInsets()
        }
        val density = LocalDensity.current
        val statusTopPadding = with(density) { statusBarTopPx.toDp() }
        val navBottomPadding = with(density) { navBarBottomPx.toDp() }
        /**
         * UI 子层的安全边距（v2026-09-10 重做：真横屏后**不再需要坐标系换算**）：
         *
         * - 竖屏：系统栏始终显示。顶部让出「状态栏高度 + 半个状态栏高度」，
         *   使标题/页码/按钮的**顶边**正好落在状态栏下方**半个状态栏高度**处
         *   （用户指定）。三者自带 16dp 的内边距，故此处减去 16dp 保持严格对齐；
         *   底部让出导航栏高度。
         * - 横屏：系统栏已被彻底隐藏 → 只避让挖孔安全区，让内容不压到挖孔上。
         */
        val uiSafePadding = if (landscape) {
            PaddingValues(
                start = with(density) { cutoutLeftPx.toDp() },
                top = with(density) { cutoutTopPx.toDp() },
                end = with(density) { cutoutRightPx.toDp() },
                bottom = with(density) { cutoutBottomPx.toDp() },
            )
        } else {
            PaddingValues(
                top = (statusTopPadding * 1.5f - 16.dp).coerceAtLeast(0.dp),
                bottom = navBottomPadding,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            /**
             * 浏览层（v2026-09-10 重做：真横屏后已**取消整体旋转**）。
             *
             * 此前把 Pager + 图片 + UI 一起 rotate(90°) 伪装横屏，代价是：
             * ① 翻页方向被换算成屏幕上下滑动；② 系统栏无法真正隐藏。
             * 现在窗口本身会跟随 requestedOrientation 转到横屏，
             * Pager 的翻页方向天然就是屏幕左右方向，无需任何坐标系换算。
             */
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            ) {
                /**
                 * 多图 Pager。
                 *
                 * 翻页位置阈值（用户反馈「横屏滑动距离太远」的修复点）：
                 * 该阈值 = 需要滑过「页宽」的百分比才把这一页吸附到下一张。
                 * 横屏时页宽是屏幕**长边**（≈812dp），沿用默认 50% 要滑 ≈400dp 才翻页 ——
                 * 这就是"滑很远都不翻"的根因。现降到 8%（≈65dp，约一个拇指宽度）；
                 * 竖屏页宽较短（≈375dp），同步从 50% 降到 35%（≈131dp）。
                 *
                 * 注：该阈值只影响**慢速拖动**的落点判定，快速轻扫仍由速度阈值直接翻页。
                 */
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = if (landscape) 0.08f else 0.35f,
                    ),
                ) { page ->
                    /**
                     * 单击图片 → 切换标题/页码/全部按钮的显隐。
                     *
                     * 关键：点击检测必须放在**图片自身的手势检测器**里，与「双击缩放」
                     * 共用同一个 detectTapGestures。
                     * 之前挂在父级 Box 上是无效的 —— detectTapGestures 内部的
                     * awaitFirstDown() 要求「未被消费的按下事件」，而子节点（本组件）
                     * 收到按下的瞬间就 consume() 了，父级永远拿不到 → 点击不生效。
                     * 同一检测器内 onTap / onDoubleTap 互斥判定后，单击与双击都不会冲突；
                     * 拖拽（翻页/缩放）会自动取消 tap，因此**滑动不会触发出现**。
                     */
                    ZoomableImage(
                        path = imagePaths[page],
                        onSingleTap = { chromeVisible = !chromeVisible },
                    )
                }

                /**
                 * UI 子层（标题/页码/全部按钮）：**点击页面切换显隐**（180ms 淡入淡出），
                 * 显隐开关由图片自身的点击手势回调（[ZoomableImage] 的 onSingleTap）。
                 * 本层不含任何点击处理，只负责显示，因此不会拦截下方 Pager 的滑动。
                 * 套安全边距——按钮/文字与状态栏、导航栏、挖孔**不重叠**；
                 * 只有位置让位，按钮/文字**尺寸不变**。
                 */
                androidx.compose.animation.AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 180)),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(uiSafePadding)
                    ) {
                        // 标题（左上角）："图片附件"——半透明黑底胶囊，与各按钮背景一致（v2026-09-10）
                        // top 与右上按钮组的 16dp 严格一致：三者的顶边因此落在同一条水平线上，
                        // 即「状态栏下方半个状态栏高度」处（该间距由外层 uiSafePadding 统一给出）
                        Text(
                            text = "图片附件",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 16.dp, top = 16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )

                        // 页码（顶部居中）——同款半透明黑底胶囊，顶边对齐标题/按钮
                        Text(
                            text = "${pagerState.currentPage + 1}/${imagePaths.size}",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )

                        // 右上角按钮组（v2026-09-10 对照原型）：横屏查看（退出左侧）+ 退出
                        // 两枚均为圆形 40x40 + 半透明黑色背景（alpha 0.5）+ 白色 24dp 图标，间距 12dp
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 横屏查看（v2026-09-10 重做）：真正把窗口转到横屏 / 转回竖屏。
                            // 按钮自身消费点击，不会透传到图片的单击显隐手势
                            IconButton(
                                onClick = { landscape = !landscape },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = LucideIcons.RotateCcwSquare,
                                    contentDescription = if (landscape) "退出横屏查看" else "横屏查看",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // 关闭按钮（右上角）
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "关闭",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // 右下角按钮组（v2026-09-10 对照原型）：详情（保存左侧）+ 保存
                        // 两枚均为圆形 40x40 + 半透明黑色背景 + 白色 24dp 图标，间距 12dp
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 详情按钮（新增）：打开图片详情页 [ImageDetailPage]
                            // 路径在点击瞬间定格存入 detailImagePath，防 pager 翻页后取错图
                            IconButton(
                                onClick = {
                                    detailImagePath = imagePaths.getOrNull(pagerState.currentPage)
                                    showDetail = true
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "详情",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // 下载按钮：Coil 同步加载 + InspirationScreenshot 保存到相册 + Snackbar 反馈
                            IconButton(
                                onClick = ::downloadCurrentImage,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = "保存到相册",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // v2026-07-24 新增：删除按钮（左下角）
                        // 与原型 .img-btn.delete 一致：bottom:40px; left:16px;
                        // 圆形 40x40 + 半透明黑色背景（alpha 0.5）+ 红色 Delete 图标
                        // 图标色 0xFFFF6B6B 与 AlertDialog 删除按钮文字色呼应，强化视觉警示
                        // 与右下角下载按钮对称布局，点击触发二次确认对话框（不直接删除，防误触）
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 24.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除图片",
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } // 安全边距结束
                } // UI 子层结束（点击显隐 + 系统栏安全边距）
            } // 浏览层结束（Pager + 图片 + UI，横屏整体旋转）

            // Snackbar：不随横屏旋转——提示文字始终正向，便于阅读
            // 底部居中，离底 80dp 避开下载按钮
            // 在 Dialog Window 内显示，z 轴层级高于 Activity 内的柯基悬浮球
            // 使用 AppSnackbarHost 统一全项目 Snackbar 样式（柯基图标 + 文字 + 按钮）
            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )

            // v2026-07-24 新增：删除图片二次确认对话框
            // 视觉样式与 VoicePreviewDialog 中的删除确认弹窗保持一致：
            // - title 使用 FontWeight.SemiBold
            // - 删除按钮文字颜色 0xFFFF6B6B（与录音弹窗一致，非原型 #DC2626）
            // - 取消按钮使用默认主题色，不自定义颜色
            // - 不自定义 containerColor/titleContentColor/textContentColor，跟随 Material3 主题
            // 文案风格对齐：「删除图片」+「确定要删除这张图片吗？删除后不可恢复。」
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = {
                        Text(
                            text = "删除图片",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    text = {
                        Text("确定要删除这张图片吗？删除后不可恢复。")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                val currentIndex = pagerState.currentPage
                                if (onDeleteClick != null) {
                                    onDeleteClick(currentIndex)
                                } else {
                                    // 演示模式：未接入实际删除逻辑时仅 Snackbar 提示
                                    scope.launch {
                                        snackbarHostState.showSnackbar("演示功能：实际删除逻辑待接入")
                                    }
                                }
                            }
                        ) {
                            Text("删除", color = Color(0xFFFF6B6B))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirm = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }

            // v2026-09-10 新增：图片详情页（白底信息页，对照原型）
            // 放在 Box 最后一个子项 → 绘制在黑色沉浸预览与全部按钮之上；
            // 返回箭头 / 系统返回键（BackHandler）关闭后回到附件页
            if (showDetail && detailImagePath != null) {
                ImageDetailPage(
                    imagePath = detailImagePath!!,
                    onBack = { showDetail = false }
                )
            }
        }
    }
}

/**
 * 可缩放/平移的单张图片
 * - 双指捏合：缩放（1x~4x）
 * - 单指拖动：仅在缩放 > 1x 时平移
 * - 单击：回调 [onSingleTap]（切换标题/页码/全部按钮显隐）
 * - 双击：放大/还原
 *
 * @param path 图片绝对路径
 * @param onSingleTap 单击图片回调（切换浏览层 UI 显隐）
 *
 * **点击检测为何放在这里**（v2026-09-10 修复）：`detectTapGestures` 在处理按下事件时
 * 会立即 `consume()` 掉它；若把「点击页面切换显隐」的检测挂在**父级**节点上，
 * 父级 `awaitFirstDown()` 因要求「未被消费的按下事件」而永远拿不到该事件，
 * 点击彻底失效。放进图片自身的手势检测器后，`onTap` 与 `onDoubleTap` 由同一个
 * 检测器互斥判定，单击/双击都正确，且拖拽会自动取消 tap（滑动不触发显隐切换）。
 *
 * **横屏下的拖动方向为何不用换算**：真横屏后窗口本身已旋转，本节点就处于横屏坐标系，
 * pan 位移与 translationX/Y 同坐标系，直接累加即可。
 */
@Composable
private fun ZoomableImage(
    path: String,
    onSingleTap: () -> Unit,
) {
    // 缩放比例（1f ~ 4f）
    var scale by remember { mutableFloatStateOf(1f) }
    // 平移偏移
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(path)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                // 关键：pointerInput 依赖 scale，scale 变化时重启
                // scale = 1f（未放大）时不消费指针，让 HorizontalPager 接收单指 pan 用于翻页
                // scale > 1f（已放大）时消费指针处理平移，让用户能拖动查看图片细节
                .pointerInput(scale) {
                    if (scale > 1f) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                // 横屏无需换算：pan 已在本节点局部坐标系（见 KDoc）
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    }
                }
                // 点击手势：单击切换 UI 显隐 + 双击放大/还原（同一检测器内互斥判定）
                .pointerInput(Unit) {
                    detectTapGestures(
                        /**
                         * 单击：切换标题/页码/全部按钮的显隐。
                         * 与 onDoubleTap 共存时，单击需等待双击判定超时（系统约 300ms）才触发
                         * ——这是区分单击/双击的必然代价，否则双击缩放会顺带误触发显隐。
                         */
                        onTap = { onSingleTap() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                // 当前已放大：还原
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                // 当前未放大：放大到 2x
                                scale = 2f
                            }
                        }
                    )
                }
                // 应用缩放与平移
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

/**
 * 从 Context 逐层解包拿到宿主 [Activity]（Dialog 的 context 通常是
 * ContextThemeWrapper 包着 Activity，用于读取 Activity 主窗口的真实 insets）。
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
