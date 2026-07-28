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
import com.corgimemo.app.data.model.ProfileNavItem
import com.corgimemo.app.ui.theme.UiColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * "我的"页快捷导航分区（侧边栏）
 *
 * 布局：
 * 1. 标题"🔗 快捷导航" + 橙色横线
 * 2. 3 个 nav item（从 [navItems] 读取，可拖拽排序）
 *
 * **v2026-07-27 P8 Phase 5 改造**：
 * - 由"硬编码 3 个 CategoryItem"改为"数据驱动 + Reorderable 拖拽"
 * - 接受 [navItems] 参数（来自 ProfileViewModel.navItems，按 sortOrder ASC 排序）
 * - 接受 [onReorder] 回调，拖拽后委托外层 ViewModel 持久化到 profile_nav_items 表
 * - 拖拽视觉：scale 1.05 + shadowElevation 8dp + zIndex 1f（与其他 4 个 section 一致）
 * - 触觉反馈：HapticFeedbackManager.TEXT_MOVE
 * - 点击行为：当前只有 "settings" id 触发 onSettingsClick，其他保留 TODO 占位
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
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * @param navItems 已按 sortOrder ASC 排序的快速导航项列表（v2026-07-27 P8 Phase 5 新增）
 * @param onReorder 拖拽结束回调（v2026-07-28 Plan A：仅在拖拽结束时调用，参数为最终的新 nav item 列表）
 *   原 v2026-07-27 P8 Phase 5 接 Reorderable onMove 通知
 * @param onSettingsClick "设置"项点击回调（MainScreen 负责导航到 Screen.Settings）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun ProfileQuickNavSection(
    navItems: List<ProfileNavItem>,
    onReorder: (List<ProfileNavItem>) -> Unit = {},
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 🆕 v2026-07-28 拖拽埋点（诊断"残影/闪烁"问题，调试代码不入仓）
    //   7 类埋点：onRecompose / onMove / onDragStarted / onDragStopped /
    //             onGraphicsLayerChange / onLayoutChange / onReorderSubmit
    val diag = rememberReorderableDiagnostics("Profile")
    // 重组埋点：函数体每次重组都自增
    diag.onRecompose()

    // 🆕 v2026-07-28 Plan A：拖拽中暂存新顺序，拖拽结束才提交外层 ViewModel
    //   原因：避免每次 onMove 都触发 ViewModel → Repository → DAO → StateFlow 整条链路更新，
    //         导致整列表重组（实测单次拖拽重组 200-400 次）。
    //   行为：拖拽过程中仅更新本地 pendingReorder，onDragStopped 才回调 onReorder。
    var pendingReorder by remember { mutableStateOf<List<ProfileNavItem>?>(null) }
    // 拖拽中显示 pendingReorder（如果有），否则显示原始 navItems
    val displayNavItems = pendingReorder ?: navItems

    // 🆕 v2026-07-27 P8 Phase 5 拖拽状态
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 埋点 #2：位置交换
        diag.onMove(from = from.index, to = to.index, listSize = displayNavItems.size, isDragging = true)
        val currentList = pendingReorder ?: navItems
        if (from.index !in currentList.indices || to.index !in currentList.indices) {
            return@rememberReorderableLazyListState
        }
        // Plan A：仅更新本地 pendingReorder，不触发外层 ViewModel
        pendingReorder = currentList.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        // 埋点：onMove 后的 pendingReorder 快照
        diag.onMoveSnapshot(from = from.index, to = to.index, snapshot = pendingReorder!!)
    }

    // 埋点 #6：LazyColumn 布局变化
    TrackLazyColumnLayout(listState, diag)

    // 埋点：items() 列表 key 顺序变化
    LaunchedEffect(displayNavItems) {
        diag.onListKeysChange(displayNavItems.map { it.id })
    }

    Column(modifier = modifier) {
        // 1. 标题
        Text(
            text = "🔗 快捷导航",
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

        // 3. nav item 列表（可滚动，可拖拽）
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth()
        ) {
            // v2026-07-27 P8 Phase 5 起支持长按拖拽
            //    key 用 nav item 的 id 字符串（稳定主键）
            //    v2026-07-28 Plan A：items() 使用 displayNavItems，拖拽中显示 pendingReorder
            items(
                items = displayNavItems,
                key = { it.id }
            ) { item ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = item.id
                ) { isDragging ->
                    val context = LocalContext.current
                    // 埋点：ReorderableItem 创建/销毁
                    DisposableEffect(item.id) {
                        diag.onItemEnter(item.id)
                        onDispose {
                            diag.onItemExit(item.id)
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
                                    diag.onDragStarted(item.id)
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
                                    diag.onDragStopped(item.id, listSize = displayNavItems.size)
                                }
                            )
                    ) {
                        CategoryItem(
                            icon = item.icon,
                            name = item.name,
                            count = 0,
                            isSelected = false,
                            showMenu = false,
                            onClick = {
                                when (item.id) {
                                    "settings" -> onSettingsClick()
                                    "stats" -> { /* TODO: 导航到统计页 */ }
                                    "achievement" -> { /* TODO: 导航到成就页 */ }
                                    // 后续扩展项在此追加
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
