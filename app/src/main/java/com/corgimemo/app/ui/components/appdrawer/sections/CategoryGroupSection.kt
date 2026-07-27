package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.draggableHandle
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 待办分组管理分区（侧边栏）
 *
 * 布局：
 * 1. "全部待办"项（selectedCategoryId == null）
 * 2. "未分类"项（selectedCategoryId == 0L）
 * 3. 自定义分类列表（来自 [categories]，按 sortOrder 排序，**v2026-07-27 起支持长按拖拽**）
 *
 * **v2026-07-27 调整**：删除内部"分组管理"标题 + 橙线，避免与上方
 * [DrawerSectionTab] Tab 切换器的"分组管理"文字重复。
 * 顶部 8dp 间距用 LazyColumn.padding(top) 替代原来的 Spacer。
 *
 * **v2026-07-27 P8 Phase 1 改造**：自定义分类列表接入 Reorderable 库
 * - 长按整行触发拖拽（与首页 todo 卡片拖拽一致）
 * - 拖拽中视觉：scale 1.05 + shadowElevation 8dp + zIndex 1f
 * - 触觉反馈：HapticFeedbackManager.TEXT_MOVE
 * - 顶部 2 个 fixed items（全部待办/未分类）**不可拖拽**（无 sortOrder）
 *
 * 点击自定义分类右侧三点菜单 → 触发 [CategoryAction.ShowMenu]（MainScreen 显示 BottomSheet）
 *
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * @param categories 自定义分类列表（已按 sortOrder 排序）
 * @param todoCountByCategory 各分类 ID → 待办数量映射（key=-1 表示全部，key=0 表示未分类）
 * @param selectedCategoryId 当前选中的分类 ID（null=全部, 0L=未分类, 其他=自定义分类 ID）
 * @param onCategoryClick 点击分类行回调（参数为分类 ID）
 * @param onCategoryAction 分组操作回调（ShowMenu / Pin / Rename / Delete）
 * @param onReorder 拖拽完成回调（参数为排序后的新分类列表）
 *   v2026-07-27 新增 — 接 Reorderable onMove 通知，委托外层 ViewModel 持久化
 * @param modifier 外部 Modifier
 */
@Composable
internal fun CategoryGroupSection(
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    selectedCategoryId: Long?,
    onCategoryClick: (Long?) -> Unit,
    onCategoryAction: (CategoryAction) -> Unit,
    onReorder: (List<Category>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 🆕 v2026-07-27 P8 Phase 1 拖拽状态
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 复制列表，重排，通知外层
        val newList = categories.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onReorder(newList)
    }

    Column(modifier = modifier) {
        // 分类列表（顶部 8dp 间距替代原"标题 + 橙线 + Spacer 8dp"）
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            // 1. "全部待办" 项（特殊 ID: -1L，不可拖拽）
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部待办",
                    count = todoCountByCategory[-1L] ?: 0,
                    isSelected = selectedCategoryId == null,
                    showMenu = false,
                    onClick = { onCategoryClick(null) }
                )
            }

            // 2. "未分类" 项（特殊 ID: 0L，不可拖拽）
            item {
                CategoryItem(
                    icon = DRAWER_ICON_UNCATEGORIZED,
                    name = "未分类",
                    count = todoCountByCategory[0L] ?: 0,
                    isSelected = selectedCategoryId == 0L,
                    showMenu = false,
                    onClick = { onCategoryClick(0L) }
                )
            }

            // 3. 自定义分类列表（v2026-07-27 起支持长按拖拽）
            //    注意：key 用 category.id（稳定主键），不是 index（拖拽中 index 会变）
            items(
                items = categories,
                key = { it.id }
            ) { category ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = category.id
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
                        val icon = categoryIcons[category.type] ?: "📂"
                        CategoryItem(
                            icon = icon,
                            name = category.name,
                            count = todoCountByCategory[category.id] ?: 0,
                            isSelected = selectedCategoryId == category.id,
                            showMenu = !category.isDefault,
                            onClick = { onCategoryClick(category.id) },
                            onMenuClick = {
                                onCategoryAction(
                                    CategoryAction.ShowMenu(category)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
