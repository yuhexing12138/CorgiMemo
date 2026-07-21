package com.corgimemo.app.ui.screens.date

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.data.model.screenBackgroundColor
import com.corgimemo.app.ui.components.AppSnackbarHost
import com.corgimemo.app.ui.components.LinkedCardsRow /** v2026-07-22 新增：关联卡片 Chip 流展示组件 */
import com.corgimemo.app.ui.components.LinkedCardPreviewDialog /** v2026-07-22 新增：关联卡片预览弹窗 */
import com.corgimemo.app.ui.components.RelationPickerBottomSheet /** v2026-07-22 新增：多选关联选择 BottomSheet */
import com.corgimemo.app.ui.components.EnhancedTopBar
import com.corgimemo.app.ui.components.LeftIconType
import com.corgimemo.app.ui.components.RightIconType
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.screens.date.components.DateDetailBottomToolbar
import com.corgimemo.app.ui.screens.date.components.DateDetailMenuDropdown
import com.corgimemo.app.ui.screens.date.components.NoteEditDialog
import com.corgimemo.app.ui.screens.date.components.ShareDateSheet
import com.corgimemo.app.ui.screens.date.components.ThemePickerBottomSheet
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleRenderer
import com.corgimemo.app.util.DateCardScreenshot
import com.corgimemo.app.viewmodel.SpecialDateDetailViewModel
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/**
 * 日期详情页
 *
 * 展示日期详情卡片，支持左右滑动切换不同日期，提供置顶、归档、删除、备注编辑、主题选择、编辑、分享等操作。
 *
 * @param navController 导航控制器
 * @param dateId 初始日期ID
 * @param viewModel ViewModel（通过 Hilt 注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDateDetailScreen(
    navController: NavController,
    dateId: Long,
    viewModel: SpecialDateDetailViewModel = hiltViewModel()
) {
    val allDates by viewModel.allDates.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val configuration = LocalConfiguration.current
    val cardWidth = configuration.screenWidthDp.dp * 0.75f
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 找到初始日期的索引
    val initialIndex = remember(allDates, dateId) {
        allDates.indexOfFirst { it.id == dateId }.coerceAtLeast(0)
    }

    // Pager 状态
    val pagerState = rememberPagerState(initialPage = initialIndex) { allDates.size }

    // 数据加载完成后滚动到初始页
    LaunchedEffect(initialIndex) {
        if (pagerState.currentPage != initialIndex && allDates.isNotEmpty()) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    // 当前显示的日期
    val currentDate = allDates.getOrNull(pagerState.currentPage)

    // 三点弹窗状态
    var showMenu by remember { mutableStateOf(false) }
    // 删除确认对话框状态
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 备注编辑对话框状态
    var showNoteEdit by remember { mutableStateOf(false) }
    // 主题选择底部弹窗状态
    var showThemePicker by remember { mutableStateOf(false) }
    // 分享弹窗状态
    var showShareSheet by remember { mutableStateOf(false) }

    // ========== v2026-07-22 新增：关联管理状态 ==========
    /** 关联列表（按当前日期 id 加载） */
    val relations by viewModel.relations.collectAsState()
    /** 关联ID → 标题映射（由 ViewModel 异步加载并缓存） */
    val relationTitles by viewModel.relationTitles.collectAsState()
    /** 当前预览卡片的详情（供 LinkedCardPreviewDialog 展示） */
    val cardDetail by viewModel.cardDetail.collectAsState()
    /** 卡片详情加载中标志 */
    val cardDetailLoading by viewModel.cardDetailLoading.collectAsState()
    /** 关联预览 Dialog 状态（null=关闭，非null=显示该关联的预览） */
    var previewingRelation by remember { mutableStateOf<CardRelation?>(null) }
    /** 关联选择 BottomSheet 状态 */
    var showRelationPicker by remember { mutableStateOf(false) }

    /**
     * v2026-07-22 新增：监听当前日期变化，自动加载关联列表
     *
     * HorizontalPager 切换 page 时 currentDate 变化，
     * 触发 loadRelations 重新加载当前 page 日期的关联。
     */
    LaunchedEffect(currentDate?.id) {
        val dateId = currentDate?.id
        if (dateId != null && dateId > 0L) {
            viewModel.loadRelations(dateId)
        }
    }

    /**
     * v2026-07-22 新增：监听 previewingRelation 变化，自动加载/清空卡片详情
     */
    LaunchedEffect(previewingRelation) {
        val relation = previewingRelation
        if (relation != null) {
            viewModel.loadCardDetail(relation.targetType, relation.targetId)
        } else {
            viewModel.clearCardDetail()
        }
    }

    // 当前页的 GraphicsLayer（用于截图分享）
    // 关键：每个 page 独立创建 GraphicsLayer，跟踪当前页的 layer
    var currentPageLayer by remember { mutableStateOf<GraphicsLayer?>(null) }

    /**
     * 返回上一页辅助函数
     * 设置 targetTab=DATE，确保返回后 MainScreen 切换到日期 tab
     */
    val navigateBack: () -> Unit = {
        navController.previousBackStackEntry?.savedStateHandle?.set("targetTab", TabItem.DATE.name)
        navController.popBackStack()
    }

    /**
     * 拦截系统返回事件
     */
    BackHandler { navigateBack() }

    // 计算屏幕背景色（实时跟随当前卡片颜色变化）
    val cardColor = currentDate?.let { DateCardColor.fromSerialName(it.cardColor) }
    val backgroundColor = if (cardColor != null) {
        screenBackgroundColor(cardColor) ?: MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.background
    }

    Scaffold(
        topBar = {
            EnhancedTopBar(
                title = "",
                onMenuClick = { /* 详情页不需要菜单 */ },
                onCorgiClick = { /* 详情页不需要柯基 */ },
                rightIconType = RightIconType.MORE_MENU,
                onMoreClick = { showMenu = true },
                dropdownContent = {
                    currentDate?.let { date ->
                        DateDetailMenuDropdown(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            isPinned = date.isPinned,
                            onTogglePin = { viewModel.togglePinForDate(date.id) },
                            onArchive = {
                                viewModel.archiveDate(date.id)
                                navigateBack()
                            },
                            onDelete = { showDeleteConfirm = true }
                        )
                    }
                },
                leftIconType = LeftIconType.BACK,
                onLeftIconClick = navigateBack
            )
        },
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading || allDates.isEmpty() -> {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("日期不存在或已被删除")
                            Text(
                                "返回",
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 水平 Pager：左右滑动浏览所有日期
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 0,
                            pageSpacing = 16.dp,
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val date = allDates[page]
                            val dateCardStyle = DateCardStyle.fromSerialName(date.cardStyle)
                            val dateCardColor = DateCardColor.fromSerialName(date.cardColor)

                            // 每个 page 独立创建 GraphicsLayer
                            val pageLayer = rememberGraphicsLayer()
                            // 同步当前 page 的 layer 到外层 state，供截图回调使用
                            if (page == pagerState.currentPage) {
                                currentPageLayer = pageLayer
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Spacer(modifier = Modifier.weight(0.6f))

                                // 日期卡片（使用 GraphicsLayer 录制以供截图分享）
                                Box(
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .drawWithContent {
                                            pageLayer.record {
                                                this@drawWithContent.drawContent()
                                            }
                                            drawLayer(pageLayer)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    DateCardStyleRenderer(
                                        style = dateCardStyle,
                                        title = date.title,
                                        targetDateMillis = date.targetDate,
                                        cardColor = dateCardColor,
                                        isThumbnail = false
                                    )
                                }

                                // 备注行（最多显示6行，超出显示省略号）
                                if (date.content.isNotBlank()) {
                                    Text(
                                        text = date.content,
                                        modifier = Modifier
                                            .padding(top = 16.dp, start = 32.dp, end = 32.dp),
                                        maxLines = 6,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // v2026-07-22 新增：关联卡片 Chip 流展示（位于备注下方）
                                LinkedCardsRow(
                                    relations = relations,
                                    groupId = 0,
                                    relationTitles = relationTitles,
                                    onAddClick = { showRelationPicker = true },
                                    onChipClick = { relation -> previewingRelation = relation },
                                    onChipDelete = { relationId, _ -> viewModel.deleteRelation(relationId) },
                                    modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
                                )

                                Spacer(modifier = Modifier.weight(1.4f))

                                // 底部工具栏
                                DateDetailBottomToolbar(
                                    onNoteClick = { showNoteEdit = true },
                                    onThemeClick = { showThemePicker = true },
                                    onEditClick = {
                                        navController.navigate(
                                            Screen.SpecialDateEditWithId.createRoute(date.id)
                                        )
                                    },
                                    onShareClick = { showShareSheet = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除确认") },
            text = { Text("确定要删除这个特殊日期吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    currentDate?.let { viewModel.deleteDate(it) }
                    navigateBack()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 备注编辑对话框
    currentDate?.let { date ->
        NoteEditDialog(
            show = showNoteEdit,
            initialContent = date.content,
            onDismiss = { showNoteEdit = false },
            onConfirm = { content ->
                viewModel.updateContentForDate(date.id, content)
                showNoteEdit = false
            }
        )
    }

    // 主题选择底部弹窗
    currentDate?.let { date ->
        ThemePickerBottomSheet(
            show = showThemePicker,
            initialStyle = DateCardStyle.fromSerialName(date.cardStyle),
            initialColor = DateCardColor.fromSerialName(date.cardColor),
            title = date.title,
            targetDateMillis = date.targetDate,
            onDismiss = { showThemePicker = false },
            onConfirm = { style, color ->
                viewModel.updateCardStyleForDate(date.id, style.serialName)
                viewModel.updateCardColorForDate(date.id, color.serialName)
            }
        )
    }

    // 分享底部弹窗
    if (showShareSheet) {
        ShareDateSheet(
            onDismiss = { showShareSheet = false },
            onSaveToGallery = {
                showShareSheet = false
                coroutineScope.launch {
                    awaitFrame()
                    try {
                        val layer = currentPageLayer ?: run {
                            snackbarHostState.showSnackbar("截图失败：未找到当前页面")
                            return@launch
                        }
                        val bitmap = DateCardScreenshot.captureAsBitmap(
                            layer = layer,
                            scaleFactor = 2.0f
                        )
                        val uri = DateCardScreenshot.saveToGallery(context, bitmap)
                        if (uri != null) {
                            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "图片"
                            snackbarHostState.showSnackbar("已保存到相册：$fileName")
                        } else {
                            snackbarHostState.showSnackbar("保存失败，请重试")
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("保存失败：${e.message}")
                    }
                }
            },
            onMoreShare = {
                showShareSheet = false
                coroutineScope.launch {
                    awaitFrame()
                    try {
                        val layer = currentPageLayer ?: run {
                            snackbarHostState.showSnackbar("截图失败：未找到当前页面")
                            return@launch
                        }
                        val bitmap = DateCardScreenshot.captureAsBitmap(
                            layer = layer,
                            scaleFactor = 2.0f
                        )
                        val uri = DateCardScreenshot.saveToGallery(context, bitmap)
                        if (uri != null) {
                            val intent = DateCardScreenshot.createShareIntent(context, uri)
                            context.startActivity(intent)
                        } else {
                            snackbarHostState.showSnackbar("分享失败，请重试")
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("分享失败：${e.message}")
                    }
                }
            }
        )
    }

    // v2026-07-22 新增：关联选择 BottomSheet（多选模式，跨待办/灵感/日期三种类型）
    if (showRelationPicker) {
        val dateId = currentDate?.id ?: 0L
        // 排除已关联的卡片，避免重复添加
        val excludeIds = relations
            .map { it.targetType to it.targetId }
            .toSet()
        RelationPickerBottomSheet(
            visible = true,
            excludeIds = excludeIds,
            onDismiss = { showRelationPicker = false },
            onConfirm = { selectedCards ->
                // 批量添加选中的关联（双向关联：自动插入 A→B 和 B→A）
                val cards = selectedCards.map { it.cardType to it.cardId }
                viewModel.addRelations(dateId, cards)
                showRelationPicker = false
            },
            searchCards = { query, callback -> viewModel.searchCards(query, callback) }
        )
    }

    // v2026-07-22 新增：关联预览 Dialog（点击 Chip 后弹出，按类型差异化展示详情）
    previewingRelation?.let { relation ->
        LinkedCardPreviewDialog(
            relation = relation,
            cardDetail = cardDetail,
            isLoading = cardDetailLoading,
            onDismiss = { previewingRelation = null },
            onUnlink = { relationId ->
                viewModel.deleteRelation(relationId)
                previewingRelation = null
            },
            onJumpToDetail = { cardType, cardId ->
                // 根据卡片类型路由到对应详情/编辑页（压栈跳转，返回时回到日期详情页）
                when (cardType) {
                    "todo" -> navController.navigate("todo_edit/$cardId")
                    "inspiration" -> navController.navigate("inspiration_edit/$cardId")
                    "date" -> navController.navigate("date_detail/$cardId")
                }
                previewingRelation = null
            }
        )
    }
}
