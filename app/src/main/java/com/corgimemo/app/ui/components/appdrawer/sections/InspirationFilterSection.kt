package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.TagFilterMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 灵感标签筛选分区（侧边栏）
 *
 * 布局：
 * 1. 标题"🏷️ 标签筛选" + 橙色横线
 * 2. 搜索框（本地状态，模糊匹配标签名）
 * 3. 筛选模式芯片：OR（任一匹配） / AND（全部匹配） / NOT（排除）
 * 4. "全部灵感" 项 + 过滤后的标签列表
 *
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * **v2026-07-27 P8 Phase 4 改造**：
 * - 接受 [orderedTags] 参数（来自 InspirationViewModel.orderedTags，已按用户拖拽顺序）
 * - 接受 [onReorder] 回调，拖拽后委托外层 ViewModel 持久化到 inspiration_tag_order 表
 * - 拖拽视觉：scale 1.05 + shadowElevation 8dp + zIndex 1f
 * - 触觉反馈：HapticFeedbackManager.TEXT_MOVE
 * - 搜索框过滤时仍隐藏部分 tag，但拖拽基于完整 orderedTags（与首页 todo 卡片风格一致）
 *
 * **v2026-07-28 Plan A 改造**：onReorder 调用时机从 onMove 改为 onDragStopped
 * - 原：每次 onMove 都回调 onReorder → 整条 StateFlow 链路更新 → 单次拖拽重组 200-400 次
 * - 现：拖拽中仅更新本地 pendingReorder，拖拽结束才回调 onReorder
 * - 预期：单次拖拽重组 < 10 次
 *
 * **v2026-07-28 Plan D 改造**：graphicsLayer 参数改用 animateFloatAsState
 * - 原：scale/shadow 突变
 * - 现：scale/shadow 用 120ms tween 平滑过渡
 * - 目的：消除 scale/shadow 突变导致的残影/闪烁
 *
 * @param orderedTags 已按用户拖拽顺序排列的完整标签列表（v2026-07-27 P8 Phase 4 新增，替代原 savedTags）
 * @param selectedTags 当前选中的标签集合（多选）
 * @param filterMode 标签筛选模式（OR / AND / NOT）
 * @param tagCounts 每个标签对应的灵感数量
 * @param totalInspirationCount 灵感总数（用于"全部灵感"项计数）
 * @param onTagClick 标签点击回调（传入标签名，由调用方切换选中状态）
 * @param onFilterModeChange 筛选模式切换回调
 * @param onClearTagSelection 清空所有选中标签回调（"全部灵感"点击时调用）
 * @param onReorder 拖拽结束回调（v2026-07-28 Plan A：仅在拖拽结束时调用，参数为最终的新标签列表）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun InspirationFilterSection(
    orderedTags: List<String>,
    selectedTags: Set<String>,
    filterMode: TagFilterMode,
    tagCounts: Map<String, Int>,
    totalInspirationCount: Int,
    onTagClick: (String) -> Unit,
    onFilterModeChange: (TagFilterMode) -> Unit,
    onClearTagSelection: () -> Unit,
    onReorder: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 搜索框本地状态（不持久化，关闭侧边栏后清空）
    var searchQuery by remember { mutableStateOf("") }

    // 🆕 v2026-07-28 拖拽埋点（诊断"残影/闪烁"问题，调试代码不入仓）
    //   7 类埋点：onRecompose / onMove / onDragStarted / onDragStopped /
    //             onGraphicsLayerChange / onLayoutChange / onReorderSubmit
    val diag = rememberReorderableDiagnostics("Inspiration")
    // 重组埋点：函数体每次重组都自增
    diag.onRecompose()

    // 根据搜索词过滤标签列表（忽略大小写的模糊匹配）
    // 注意：基于 orderedTags（用户排序后），不是 savedTags（字母序）
    val filteredTags = if (searchQuery.isBlank()) {
        orderedTags
    } else {
        orderedTags.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    // 🆕 v2026-07-28 Plan A：拖拽中暂存新顺序，拖拽结束才提交外层 ViewModel
    //   原因：避免每次 onMove 都触发 ViewModel → Repository → DAO → StateFlow 整条链路更新，
    //         导致整列表重组（实测单次拖拽重组 200-400 次）。
    //   行为：拖拽过程中仅更新本地 pendingReorder，onDragStopped 才回调 onReorder。
    var pendingReorder by remember { mutableStateOf<List<String>?>(null) }
    // 拖拽中显示 pendingReorder（如果有），否则显示过滤后的 filteredTags
    val displayTags = pendingReorder ?: filteredTags

    // 🆕 v2026-07-27 P8 Phase 4 拖拽状态
    // 固定项偏移：LazyColumn 第 0 项（"全部灵感"）不可拖拽
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 埋点 #2：位置交换（仅记录，不回调外层）
        diag.onMove(from = from.index, to = to.index, listSize = displayTags.size + 1, isDragging = true)
        // 全局索引 → orderedTags 子列表索引（减去固定项偏移 1）
        val fromIndex = from.index - 1
        val toIndex = to.index - 1
        val currentList = pendingReorder ?: filteredTags
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) {
            return@rememberReorderableLazyListState
        }
        // Plan A：仅更新本地 pendingReorder，不触发外层 ViewModel
        pendingReorder = currentList.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        // 埋点：onMove 后的 pendingReorder 快照
        diag.onMoveSnapshot(from = fromIndex, to = toIndex, snapshot = pendingReorder!!)
    }

    // 埋点 #6：LazyColumn 布局变化
    TrackLazyColumnLayout(listState, diag)

    // 埋点：items() 列表 key 顺序变化
    LaunchedEffect(displayTags) {
        diag.onListKeysChange(displayTags)
    }

    Column(modifier = modifier) {
        // 1. 标题
        Text(
            text = "🏷️ 标签筛选",
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

        // 3. 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "搜索标签",
                    fontSize = 14.sp,
                    color = Color(0xFF79747E)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF79747E),
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除搜索",
                            tint = Color(0xFF79747E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiColors.Primary,
                unfocusedBorderColor = Color(0xFFE0E0E0)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 4. 筛选模式芯片（OR / AND / NOT）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == TagFilterMode.OR,
                onClick = { onFilterModeChange(TagFilterMode.OR) },
                label = { Text("OR", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = UiColors.PrimaryLight,
                    selectedLabelColor = UiColors.Primary
                )
            )
            FilterChip(
                selected = filterMode == TagFilterMode.AND,
                onClick = { onFilterModeChange(TagFilterMode.AND) },
                label = { Text("AND", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = UiColors.PrimaryLight,
                    selectedLabelColor = UiColors.Primary
                )
            )
            FilterChip(
                selected = filterMode == TagFilterMode.NOT,
                onClick = { onFilterModeChange(TagFilterMode.NOT) },
                label = { Text("NOT", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = UiColors.PrimaryLight,
                    selectedLabelColor = UiColors.Primary
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 5. 标签列表（可滚动，可拖拽）
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth()
        ) {
            // "全部灵感" 选项（无选中标签时高亮，不可拖拽）
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部灵感",
                    count = totalInspirationCount,
                    isSelected = selectedTags.isEmpty(),
                    showMenu = false,
                    onClick = { onClearTagSelection() }
                )
            }

            // 过滤后的标签列表（v2026-07-27 P8 Phase 4 起支持长按拖拽）
            //    key 用 tag 字符串本身（稳定主键）
            //    v2026-07-28 Plan A：items() 使用 displayTags，拖拽中显示 pendingReorder
            items(
                items = displayTags,
                key = { it }
            ) { tag ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = tag
                ) { isDragging ->
                    val context = LocalContext.current
                    // 埋点：ReorderableItem 创建/销毁
                    DisposableEffect(tag) {
                        diag.onItemEnter(tag)
                        onDispose {
                            diag.onItemExit(tag)
                        }
                    }
                    // 埋点 #4：graphicsLayer 参数变化
                    LaunchedEffect(isDragging) {
                        diag.onGraphicsLayerChange(
                            isDragging = isDragging,
                            scaleX = if (isDragging) 1.05f else 1f,
                            scaleY = if (isDragging) 1.05f else 1f,
                            shadowElevation = if (isDragging) 8f else 0f,
                            zIndex = if (isDragging) 1f else 0f
                        )
                    }
                    // 🆕 v2026-07-28 Plan D：graphicsLayer 参数动画过渡
                    //   即使 isDragging 切换，scale/shadow 也是平滑过渡，不会瞬间跳变
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.05f else 1f,
                        animationSpec = tween(durationMillis = 120),
                        label = "scale"
                    )
                    val shadow by animateFloatAsState(
                        targetValue = if (isDragging) 8f else 0f,
                        animationSpec = tween(durationMillis = 120),
                        label = "shadow"
                    )
                    // 埋点：拖拽中 scale/shadow 实际值
                    LaunchedEffect(isDragging) {
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
                                    // Plan A：拖拽开始时清空 pendingReorder，确保新拖拽干净起步
                                    pendingReorder = null
                                    // 埋点 #1：拖拽开始
                                    diag.onDragStarted(tag)
                                    HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = InteractionType.TEXT_MOVE,
                                        enabled = true
                                    )
                                },
                                onDragStopped = {
                                    // Plan A：拖拽结束才提交到外层 ViewModel
                                    pendingReorder?.let { finalList ->
                                        // 埋点 #7：实际触发 ViewModel 更新
                                        diag.onReorderSubmit(finalList.size)
                                        onReorder(finalList)
                                    }
                                    pendingReorder = null
                                    // 埋点 #3：拖拽结束
                                    diag.onDragStopped(tag, listSize = displayTags.size + 1)
                                }
                            )
                    ) {
                        CategoryItem(
                            icon = "#",
                            name = tag,
                            count = tagCounts[tag] ?: 0,
                            isSelected = tag in selectedTags,
                            showMenu = false,
                            onClick = { onTagClick(tag) }
                        )
                    }
                }
            }
        }
    }
}
