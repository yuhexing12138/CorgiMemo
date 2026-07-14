// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationViewScreen.kt
package com.corgimemo.app.ui.screens.inspiration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.corgimemo.app.ui.components.AppSnackbarHost
import com.corgimemo.app.ui.screens.inspiration.components.InspirationImageGallery
import com.corgimemo.app.ui.screens.inspiration.components.InspirationViewCard
import com.corgimemo.app.ui.screens.inspiration.components.ShareInspirationSheet
import com.corgimemo.app.util.InspirationScreenshot
import com.corgimemo.app.viewmodel.InspirationViewModel
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 灵感展示页主屏幕
 *
 * 承载 TopBar + HorizontalPager（左右滑动切换所有灵感）+ 三个功能按钮（复制/编辑/分享）
 * 复用 InspirationViewModel 的 filteredDisplayInspirations 状态
 *
 * @param inspirationId 初始灵感 ID（来自路由参数）
 * @param navController 导航控制器
 * @param viewModel 灵感 ViewModel（通过 Hilt 自动注入）
 */
@Composable
fun InspirationViewScreen(
    inspirationId: Long,
    navController: NavController,
    viewModel: InspirationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 监听生命周期：从编辑页返回时（ON_RESUME）自动刷新数据
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 监听 ViewModel 状态：当前筛选+分组后的灵感列表
    val displayItems by viewModel.filteredDisplayInspirations.collectAsState()
    val inspirations = remember(displayItems) {
        displayItems.mapNotNull { it.inspiration }
    }

    // 找到 inspirationId 对应的初始页码
    val initialIndex = remember(inspirations, inspirationId) {
        inspirations.indexOfFirst { it.id == inspirationId }.coerceAtLeast(0)
    }

    // 初始化 Pager 状态，初始页 = 传入的灵感 ID 对应的索引
    val pagerState = rememberPagerState(initialPage = initialIndex) { inspirations.size }

    // 修正页码：filteredDisplayInspirations 初始值为空列表，导致 initialIndex 首次计算为 0，
    // 而 rememberPagerState 的 initialPage 只在首次创建时生效。数据异步加载后需手动滚动到正确页。
    LaunchedEffect(initialIndex) {
        if (pagerState.currentPage != initialIndex && inspirations.isNotEmpty()) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    val currentInspiration = inspirations.getOrNull(pagerState.currentPage)

    // 分享弹窗状态
    var showShareSheet by remember { mutableStateOf(false) }
    // 图片全屏预览状态
    var showImageGallery by remember { mutableStateOf(false) }
    var imageGalleryInitialIndex by remember { mutableStateOf(0) }
    var imageGalleryPaths by remember { mutableStateOf(emptyList<String>()) }

    // 截图录制层：跟踪当前显示页的 GraphicsLayer
    // 关键：每个 page 必须使用独立的 GraphicsLayer，否则 record() 会互相覆盖
    // 导致所有页面都渲染最后绘制的那个 page 的内容
    var currentPageLayer by remember { mutableStateOf<GraphicsLayer?>(null) }

    /**
     * 返回上一页辅助函数
     *
     * 在 popBackStack 之前设置 savedStateHandle["targetTab"] = "INSPIRE"，
     * 让 MainScreen 接收到返回事件后切换到灵感 tab，
     * 确保从灵感展示页退出后始终回到灵感页（而非待办页等其他 tab）。
     */
    val navigateBack: () -> Unit = {
        navController.previousBackStackEntry?.savedStateHandle?.set("targetTab", "INSPIRE")
        navController.popBackStack()
    }

    /**
     * 拦截系统返回事件（侧滑返回 / 系统返回键）
     *
     * 确保所有退出方式（应用内返回箭头、系统返回键）都经过 navigateBack()，
     * 统一设置 targetTab=INSPIRE，让 MainScreen 切换到灵感 tab。
     */
    BackHandler { navigateBack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),  // 由各 Composable 自行处理 WindowInsets
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopBar(
                onBack = { navigateBack() },
                onCopy = {
                    // 复制当前页灵感到剪贴板
                    val ins = currentInspiration ?: return@TopBar
                    val formattedDate = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                        .format(Date(ins.createdAt))
                    val text = InspirationTextUtils.buildInspirationPlainText(ins, formattedDate)
                    coroutineScope.launch {
                        val clipData = android.content.ClipData.newPlainText("inspiration", text)
                        clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(clipData))
                        snackbarHostState.showSnackbar("文本内容已复制到剪切板")
                    }
                },
                onEdit = {
                    // 跳转编辑页
                    val ins = currentInspiration ?: return@TopBar
                    navController.navigate("inspiration_edit/${ins.id}")
                },
                onShare = {
                    // 弹出分享底部菜单
                    if (currentInspiration != null) showShareSheet = true
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (inspirations.isEmpty()) {
                // 数据加载中显示菊花
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 水平 Pager：左右滑动浏览所有灵感
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 0,  // 不预渲染相邻页，提升性能
                        pageSpacing = 16.dp,
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val ins = inspirations[page]
                        // 缓存当前页的图片路径列表（用于图片预览）
                        val insImagePaths = remember(ins.imagePaths) {
                            if (ins.imagePaths.isBlank()) emptyList()
                            else try {
                                org.json.JSONArray(ins.imagePaths).let { arr ->
                                    (0 until arr.length()).map { arr.getString(it) }
                                }
                            } catch (e: Exception) { emptyList() }
                        }
                        // 每个 page 独立创建 GraphicsLayer
                        // 关键：不能共享同一个 layer，否则多个 page 的 record() 会互相覆盖，导致所有 Card 都渲染最后绘制的 page 内容
                        val pageLayer = rememberGraphicsLayer()
                        // 同步当前 page 的 layer 到外层 state，供截图回调使用
                        if (page == pagerState.currentPage) {
                            currentPageLayer = pageLayer
                        }
                        InspirationViewCard(
                            inspiration = ins,
                            onImageClick = { index ->
                                // 打开图片全屏预览
                                imageGalleryPaths = insImagePaths
                                imageGalleryInitialIndex = index
                                showImageGallery = true
                            },
                            // 传入当前 page 独立的 GraphicsLayer，使卡片内容被录制以供后续截图分享
                            graphicsLayer = pageLayer,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // 分享弹窗（接入截图：保存到相册 / 更多分享）
    if (showShareSheet && currentInspiration != null) {
        ShareInspirationSheet(
            onDismiss = { showShareSheet = false },
            onSaveToGallery = {
                showShareSheet = false
                coroutineScope.launch {
                    // 等待当前帧绘制完成
                    awaitFrame()
                    // 使用当前 page 的 GraphicsLayer 截图（位图放大 3x）
                    val layer = currentPageLayer ?: run {
                        snackbarHostState.showSnackbar("截图失败：未找到当前页面")
                        return@launch
                    }
                    val bitmap = InspirationScreenshot.captureAsBitmap(layer, scaleFactor = 3.0f)
                    // saveToGallery 现在返回 Uri（API 29+ MediaStore）
                    val uri = InspirationScreenshot.saveToGallery(context, bitmap)
                    if (uri != null) {
                        // 用 lastPathSegment 显示文件名（API 29+ MediaStore Uri 也支持）
                        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "图片"
                        snackbarHostState.showSnackbar("已保存到相册：$fileName")
                    } else {
                        snackbarHostState.showSnackbar("保存失败，请重试")
                    }
                }
            },
            onMoreShare = {
                showShareSheet = false
                coroutineScope.launch {
                    // 等待当前帧绘制完成
                    awaitFrame()
                    // 使用当前 page 的 GraphicsLayer 截图
                    val layer = currentPageLayer ?: run {
                        snackbarHostState.showSnackbar("截图失败：未找到当前页面")
                        return@launch
                    }
                    val bitmap = InspirationScreenshot.captureAsBitmap(layer, scaleFactor = 3.0f)
                    // saveToGallery 现在返回 Uri（API 29+ MediaStore）
                    val uri = InspirationScreenshot.saveToGallery(context, bitmap)
                    if (uri != null) {
                        // 启动系统分享面板（使用 Uri 重载）
                        val intent = InspirationScreenshot.createShareIntent(context, uri)
                        context.startActivity(intent)
                    } else {
                        snackbarHostState.showSnackbar("分享失败，请重试")
                    }
                }
            }
        )
    }

    // 图片全屏预览
    if (showImageGallery) {
        InspirationImageGallery(
            imagePaths = imageGalleryPaths,
            initialIndex = imageGalleryInitialIndex,
            onDismiss = { showImageGallery = false }
        )
    }
}

/**
 * 顶部导航栏
 * 左侧：返回箭头；右侧：复制/编辑/分享三个图标按钮
 * 高度 = 状态栏高度 + 64dp，跨设备一致
 *
 * @param onBack 返回回调
 * @param onCopy 复制回调
 * @param onEdit 编辑回调
 * @param onShare 分享回调
 */
@Composable
private fun TopBar(
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit
) {
    // 动态获取状态栏高度，跨设备一致
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 状态栏占位区域（透明，保留可点击性）
        Spacer(modifier = Modifier.height(statusBarHeight))
        // 实际内容区域（64dp）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            // 左侧返回箭头
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            // 右侧功能按钮组（复制 + 编辑 + 分享）
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
            ) {
                Row {
                    IconButton(onClick = onCopy) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "复制",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
