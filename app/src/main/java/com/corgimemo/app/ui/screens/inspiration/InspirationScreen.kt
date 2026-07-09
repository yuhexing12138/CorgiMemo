package com.corgimemo.app.ui.screens.inspiration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.components.SearchBar
import com.corgimemo.app.ui.components.UnifiedEmptyState
import com.corgimemo.app.ui.components.ReminderPickerBottomSheet
import com.corgimemo.app.ui.screens.inspiration.components.InspirationLongPressSheet
import com.corgimemo.app.ui.screens.inspiration.components.InspirationDateTimePreview
import com.corgimemo.app.ui.screens.inspiration.components.TagPickerSheet
import com.corgimemo.app.ui.screens.inspiration.components.TimelineInspirationItem
import com.corgimemo.app.viewmodel.InspirationViewModel
import androidx.compose.material3.rememberModalBottomSheetState
import java.util.Calendar

/**
 * 灵感记录列表页面（时间线版）
 *
 * 展示所有灵感记录的时间线列表，支持搜索、分组展示和快速添加功能。
 * 顶部导航栏和侧滑导航栏由 MainScreen 统一管理。
 *
 * 功能说明：
 * - 搜索栏：支持关键词实时搜索灵感
 * - 时间线布局：左侧日期列 + 右侧内容区
 * - 置顶区域：置顶灵感显示在最顶部
 * - 长按操作：置顶/标签/改日期/删除
 * - 日历弹窗：从顶部日期点击展开，查看每天的灵感
 * - FAB按钮：跳转到灵感编辑页
 *
 * @param navController 导航控制器，用于页面跳转
 * @param onFabClick FAB按钮点击回调（由 MainScreen 传入）
 * @param viewModel 灵感视图模型（通过 Hilt 自动注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    viewModel: InspirationViewModel = hiltViewModel()
) {
    val displayItems by viewModel.displayInspirations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDataInitialized by viewModel.isDataInitialized.collectAsState()

    // 弹窗状态
    var showLongPressSheet by remember { mutableStateOf(false) }
    var longPressedInspiration by remember { mutableStateOf<Inspiration?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索框（保留现有）
            SearchBar(
                query = searchQuery,
                onQueryChange = { newQuery ->
                    viewModel.search(newQuery)
                },
                onClear = {
                    viewModel.clearSearch()
                },
                placeholder = "搜索灵感...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = dimensionResource(com.corgimemo.app.R.dimen.ui_search_bar_bottom_margin))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 内容区域
            if (!isDataInitialized) {
                InspirationSkeleton(groupCount = 1, itemsPerGroup = 2)
            } else if (displayItems.isEmpty()) {
                UnifiedEmptyState(
                    icon = "💡",
                    title = "还没有灵感记录~",
                    subtitle = "点击右下角按钮记录你的第一个灵感吧！",
                    ctaText = "💡 记录灵感",
                    onCtaClick = onFabClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(
                        items = displayItems,
                        key = { "inspiration_${it.inspiration.id}" }
                    ) { item ->
                        val inspiration = item.inspiration
                        val tags = viewModel.decodeTags(inspiration.tags)
                        val imagePaths = viewModel.decodePaths(inspiration.imagePaths)
                        val formattedTime = viewModel.formatTime(inspiration.createdAt)

                        TimelineInspirationItem(
                            inspiration = inspiration,
                            tags = tags,
                            imagePaths = imagePaths,
                            formattedTime = formattedTime,
                            showDate = item.showDate,
                            isPinnedItem = item.isPinned,
                            onClick = {
                                navController.navigate("inspiration_edit/${inspiration.id}")
                            },
                            onLongClick = {
                                longPressedInspiration = inspiration
                                showLongPressSheet = true
                            }
                        )
                    }

                    // 底部留白
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // 加载指示器
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // FAB 按钮
        FloatingActionButton(
            onClick = onFabClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "记录灵感",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // 长按操作面板
    if (showLongPressSheet && longPressedInspiration != null) {
        InspirationLongPressSheet(
            isPinned = longPressedInspiration!!.isPinned,
            onPinClick = {
                viewModel.togglePin(longPressedInspiration!!.id)
                showLongPressSheet = false
                longPressedInspiration = null
            },
            onTagClick = {
                // 关闭长按面板，打开标签管理弹窗（保留 longPressedInspiration 供 TagPickerSheet 使用）
                showLongPressSheet = false
                showTagPicker = true
            },
            onDateClick = {
                // 关闭长按面板，打开日期时间选择器（保留 longPressedInspiration 供 ReminderPickerBottomSheet 使用）
                showLongPressSheet = false
                showDateTimePicker = true
            },
            onDeleteClick = {
                showDeleteConfirm = true
                showLongPressSheet = false
            },
            onDismiss = {
                showLongPressSheet = false
                longPressedInspiration = null
            }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirm && longPressedInspiration != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条灵感吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteInspiration(longPressedInspiration!!.id)
                    showDeleteConfirm = false
                    longPressedInspiration = null
                }) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 标签管理弹窗
    if (showTagPicker && longPressedInspiration != null) {
        val tagSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val savedTags by viewModel.savedTags.collectAsState()
        TagPickerSheet(
            sheetState = tagSheetState,
            tags = viewModel.decodeTags(longPressedInspiration!!.tags),
            savedTags = savedTags,
            onTagsChange = { newTags ->
                viewModel.updateTags(longPressedInspiration!!.id, newTags)
            },
            onDismiss = {
                showTagPicker = false
                longPressedInspiration = null
            }
        )
    }

    // 日期时间修改弹窗
    // 关键架构：使用 androidx.compose.ui.window.Dialog 将弹窗渲染至独立窗口层级，
    // 逃脱 InspirationScreen 父容器（MainScreen 的 Scaffold content + padding）约束，
    // 从而实现与 TodoEditScreen 弹窗一致的整屏遮罩覆盖效果（含 topBar 和 bottomBar）。
    // 若直接在 @Composable 层渲染，父容器 padding 会限制遮罩范围，
    // 导致弹窗位置偏下、被导航栏遮挡。
    if (showDateTimePicker && longPressedInspiration != null) {
        val inspiration = longPressedInspiration!!
        // 使用系统当前时间（非灵感原始 createdAt）
        val nowCalendar = remember { Calendar.getInstance() }

        // 弹窗出现时隐藏键盘（作用于原页面的输入框焦点）
        androidx.compose.ui.platform.LocalFocusManager.current.clearFocus()

        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showDateTimePicker = false
                longPressedInspiration = null
            },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,         // 返回键关闭弹窗
                dismissOnClickOutside = false,     // 由遮罩自行处理点击关闭
                usePlatformDefaultWidth = false,   // 允许弹窗宽度撑满屏幕
                decorFitsSystemWindows = false     // 允许内容延伸至系统栏区域（导航栏/状态栏）
            )
        ) {
            // 计算屏幕高度用于4:1比例定位（上边距:下边距=4:1）
            // Dialog 内容获得整屏约束，screenHeightPx 即真实屏幕高度
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val screenDensity = androidx.compose.ui.platform.LocalDensity.current
            val screenHeightPx = remember(configuration) {
                with(screenDensity) { configuration.screenHeightDp.dp.toPx().toInt() }
            }

            // 全屏半透明遮罩，点击关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .background(Color(0x99000000))
                    .clickable {
                        showDateTimePicker = false
                        longPressedInspiration = null
                    }
            ) {
                // 弹窗容器：固定高度屏幕90%，按4:1比例定位（上边距:下边距=4:1）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(
                            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                            indication = null,
                            onClick = {}
                        )
                        .layout { measurable, constraints ->
                            // Dialog 内 constraints.maxHeight 即屏幕高度，
                            // 但保留 Infinity 兜底以防 Dialog 未正确传递约束
                            val screenH = if (constraints.maxHeight != androidx.compose.ui.unit.Constraints.Infinity) {
                                constraints.maxHeight
                            } else {
                                screenHeightPx
                            }
                            // 固定弹窗高度：屏幕高度的 90%
                            val dialogHeight = if (screenH > 0) (screenH * 0.9).toInt() else 900
                            // 按 4:1 比例分配上下空白：B=(H-h)/5, T=4(H-h)/5
                            val bottomSpace = (screenH - dialogHeight) / 5
                            val topSpace = bottomSpace * 4
                            val fixedConstraints = constraints.copy(
                                minHeight = dialogHeight,
                                maxHeight = dialogHeight
                            )
                            val placeable = measurable.measure(fixedConstraints)
                            layout(placeable.width, dialogHeight) {
                                placeable.placeRelative(0, topSpace.coerceAtLeast(0))
                            }
                        }
                ) {
                    // 弹窗内容：淡入+缩放动画
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.spring(
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        ) + androidx.compose.animation.scaleIn(
                            initialScale = 0.9f,
                            animationSpec = androidx.compose.animation.core.spring(
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        )
                    ) {
                        ReminderPickerBottomSheet(
                            initialDateMillis = System.currentTimeMillis(),  // 系统当前时间
                            initialHour = nowCalendar.get(Calendar.HOUR_OF_DAY),
                            initialMinute = nowCalendar.get(Calendar.MINUTE),
                            showAdvancedOptions = false,        // 隐藏重复提醒和农历
                            title = "修改日期时间",
                            rowLabel = "日期时间",
                            calendarRowSpacing = 4.dp,          // 日历间距 4dp
                            inspirationPreview = { selectedDate, selectedHour, selectedMinute ->
                                // 灵感预览区：静态字段来自 inspiration，动态字段来自参数
                                InspirationDateTimePreview(
                                    inspiration = inspiration,
                                    date = selectedDate,
                                    hour = selectedHour,
                                    minute = selectedMinute
                                )
                            },
                            onDismiss = {
                                showDateTimePicker = false
                                longPressedInspiration = null
                            },
                            onConfirm = { dateMillis, _, _, _, _ ->
                                if (dateMillis != null) {
                                    viewModel.updateInspirationDateTime(inspiration.id, dateMillis)
                                }
                                showDateTimePicker = false
                                longPressedInspiration = null
                            }
                        )
                    }
                }
            }
        }
    }
}
