package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.ui.components.appdrawer.model.DateTypeAction
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.SpecialDateViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 日期类型筛选分区（侧边栏）
 *
 * 布局：
 * 1. 标题"📅 类型筛选" + 橙色横线
 * 2. "全部日期" 项（**不可拖**）
 * 3. 8 个内置 [DateCategory] + 自定义类型混排（**v2026-07-28 P8.6 起全部可拖**，支持跨组）
 * 4. 添加类型按钮由外层 AppDrawerContent 统一放置
 *
 * **v2026-07-28 P8.6 重大改造**：
 * - 内置 8 个 DateCategory 也支持长按拖拽（之前 P8 Phase 3 不可拖）
 * - 支持跨组混排（内置与自定义之间可互换位置）
 * - 数据源改为 `dateTypeOrder: List<DateTypeEntry>` 统一列表（来自 ViewModel）
 * - 持久化拆分：内置走 ESP，自定义走 Room
 * - 应用 Plan A（pendingReorder）+ Plan D（动画过渡）+ 方案 C（ViewModel 同步 emit + LaunchedEffect 监听）
 * - 集成 11 类 ReorderableDiagnostics 埋点
 *
 * @param selectedDateCategory 当前选中的类型（null=全部, "BIRTHDAY"=内置, "CUSTOM:42"=自定义）
 * @param dateCountByCategory 每个类型对应的日期计数
 * @param dateTypeOrder 统一日期类型列表（内置 8 个 + 自定义，已按持久化顺序，可拖拽）
 * @param onDateCategoryClick 类型点击回调
 * @param onCustomTypeAction 自定义类型操作回调（ShowMenu / Rename / Delete）
 * @param onReorder 拖拽完成回调（v2026-07-28 P8.6 改造：参数改为 List<DateTypeEntry>，支持跨组）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun DateTypeFilterSection(
    selectedDateCategory: String?,
    dateCountByCategory: Map<String, Int>,
    dateTypeOrder: List<SpecialDateViewModel.DateTypeEntry>,
    onDateCategoryClick: (String?) -> Unit,
    onCustomTypeAction: (DateTypeAction) -> Unit,
    onReorder: (List<SpecialDateViewModel.DateTypeEntry>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // ========== 拖拽埋点（v2026-07-28 P8.6 新增）==========
    val diag = rememberReorderableDiagnostics("DateType")
    diag.onRecompose()

    // ========== Plan A：拖拽中暂存新顺序，不主动清空 ==========
    var pendingReorder by remember { mutableStateOf<List<SpecialDateViewModel.DateTypeEntry>?>(null) }
    val displayEntries = pendingReorder ?: dateTypeOrder

    // 监听 ViewModel 数据同步后自动清空（方案 C 配套）
    LaunchedEffect(dateTypeOrder, pendingReorder) {
        if (pendingReorder != null && pendingReorder == dateTypeOrder) {
            pendingReorder = null
        }
    }

    // 拖拽状态
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 全局索引偏移：第 0 项是"全部日期"（不可拖）
        // 拖拽只允许在 [1, displayEntries.size] 范围内进行
        if (from.index == 0 || to.index == 0) {
            return@rememberReorderableLazyListState
        }
        val currentList = pendingReorder ?: dateTypeOrder
        val fromIndex = from.index - 1
        val toIndex = to.index - 1
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) {
            return@rememberReorderableLazyListState
        }
        val newList = currentList.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        // 埋点 #2 + #4：onMove 触发
        diag.onMove(from = from.index, to = to.index, listSize = displayEntries.size + 1, isDragging = true)
        pendingReorder = newList
    }

    // 埋点 #6：布局变化
    TrackLazyColumnLayout(listState, diag)

    // 埋点：列表 key 变化
    LaunchedEffect(displayEntries) {
        diag.onListKeysChange(displayEntries.map { it.key })
    }

    Column(modifier = modifier) {
        // 1. 标题
        Text(
            text = "📅 类型筛选",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // 2. 橙色分割线
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .fillMaxWidth()
                .background(UiColors.Primary)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. 类型列表
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth()
        ) {
            // "全部日期" 项（**不可拖拽**）
            item(key = "ALL_DATES_HEADER") {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部日期",
                    count = dateCountByCategory.values.sum(),
                    isSelected = selectedDateCategory == null,
                    showMenu = false,
                    onClick = { onDateCategoryClick(null) }
                )
            }

            // 8 个内置 + 自定义类型（v2026-07-28 P8.6 起全部可拖，支持跨组）
            //    key 用 entry.key（稳定主键）
            items(
                items = displayEntries,
                key = { it.key }
            ) { entry ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = entry.key
                ) { isDragging ->
                    val context = LocalContext.current
                    // 埋点：item 创建/销毁
                    DisposableEffect(entry.key) {
                        diag.onItemEnter(entry.key)
                        onDispose { diag.onItemExit(entry.key) }
                    }

                    // ========== Plan D：动画过渡 ==========
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.05f else 1f,
                        animationSpec = tween(durationMillis = 120),
                        label = "dateTypeScale"
                    )
                    val shadow by animateFloatAsState(
                        targetValue = if (isDragging) 8f else 0f,
                        animationSpec = tween(durationMillis = 120),
                        label = "dateTypeShadow"
                    )

                    // 埋点 #4：graphicsLayer 参数变化
                    LaunchedEffect(isDragging) {
                        diag.onGraphicsLayerChange(
                            isDragging = isDragging,
                            scaleX = scale,
                            scaleY = scale,
                            shadowElevation = shadow,
                            zIndex = if (isDragging) 1f else 0f
                        )
                        if (isDragging) {
                            snapshotFlow { scale to shadow }
                                .collect { (s, sh) ->
                                    diag.onScaleFrame(scale = s, shadow = sh, isDragging = isDragging)
                                }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = shadow
                            }
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = InteractionType.TEXT_MOVE,
                                        enabled = true
                                    )
                                    // 埋点 #1：拖拽开始
                                    diag.onDragStarted(entry.key)
                                    // 开始新拖拽时清空 pending（防止上一次拖拽的残留）
                                    pendingReorder = null
                                },
                                onDragStopped = {
                                    // Plan A+：松手时调用 onReorder，**不**主动清空 pendingReorder
                                    //   - ViewModel 端通过 _dateTypeOrderOverride 同步 emit 新顺序
                                    //   - Section 端 LaunchedEffect 监听 dateTypeOrder == pendingReorder 时
                                    //     才清空 pendingReorder（避免松手瞬间回弹到旧顺序残影）
                                    pendingReorder?.let { finalList ->
                                        // 埋点 #7：实际触发 ViewModel 更新
                                        diag.onReorderSubmit(finalList.size)
                                        onReorder(finalList)
                                    }
                                    // 埋点 #3：拖拽结束
                                    diag.onDragStopped(entry.key, listSize = displayEntries.size + 1)
                                }
                            )
                    ) {
                        DateTypeEntryItem(
                            entry = entry,
                            isSelected = selectedDateCategory == entry.categoryId,
                            count = dateCountByCategory[entry.categoryId] ?: 0,
                            onClick = { onDateCategoryClick(entry.categoryId) },
                            onMenuClick = if (!entry.isCustom) null else {
                                { type -> onCustomTypeAction(DateTypeAction.ShowMenu(type)) }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 渲染 [SpecialDateViewModel.DateTypeEntry] 为 [CategoryItem]
 *
 * 统一处理内置与自定义的展示，自定义类型额外展示菜单按钮。
 */
@Composable
private fun DateTypeEntryItem(
    entry: SpecialDateViewModel.DateTypeEntry,
    isSelected: Boolean,
    count: Int,
    onClick: () -> Unit,
    onMenuClick: ((com.corgimemo.app.data.model.CustomDateType) -> Unit)?
) {
    when (entry) {
        is SpecialDateViewModel.DateTypeEntry.Builtin -> {
            CategoryItem(
                icon = entry.emoji,
                name = entry.displayName,
                count = count,
                isSelected = isSelected,
                showMenu = false,
                onClick = onClick
            )
        }
        is SpecialDateViewModel.DateTypeEntry.Custom -> {
            CategoryItem(
                icon = entry.emoji,
                name = entry.displayName,
                count = count,
                isSelected = isSelected,
                showMenu = true,
                onClick = onClick,
                onMenuClick = { onMenuClick?.invoke(entry.type) }
            )
        }
    }
}
