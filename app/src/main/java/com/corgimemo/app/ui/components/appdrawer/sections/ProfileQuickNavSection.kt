package com.corgimemo.app.ui.components.appdrawer.sections

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
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * @param navItems 已按 sortOrder ASC 排序的快速导航项列表（v2026-07-27 P8 Phase 5 新增）
 * @param onReorder 拖拽完成回调（v2026-07-27 P8 Phase 5 新增，参数为新顺序的 nav item 列表）
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
    // 🆕 v2026-07-27 P8 Phase 5 拖拽状态
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 复制完整 navItems，重排，通知外层
        val newList = navItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onReorder(newList)
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
            items(
                items = navItems,
                key = { it.id }
            ) { item ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = item.id
                ) { isDragging ->
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                scaleX = if (isDragging) 1.05f else 1f
                                scaleY = if (isDragging) 1.05f else 1f
                                shadowElevation = if (isDragging) 8f else 0f
                            }
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = InteractionType.TEXT_MOVE,
                                        enabled = true
                                    )
                                },
                                onDragStopped = {}
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
