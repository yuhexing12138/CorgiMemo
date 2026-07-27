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
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.ui.components.appdrawer.model.DateTypeAction
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.DateCategory
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 日期类型筛选分区（侧边栏）
 *
 * 布局：
 * 1. 标题"📅 类型筛选" + 橙色横线
 * 2. "全部日期" 项
 * 3. 8 个内置 [DateCategory]（BIRTHDAY / ANNIVERSARY / HOLIDAY / ...）
 * 4. 自定义类型列表（来自 [customDateTypes]，带菜单按钮可重命名/删除，**v2026-07-27 P8 Phase 3 起支持长按拖拽**）
 *
 * **注意**：添加类型按钮由外层 AppDrawerContent 统一放置（与待办页/灵感页一致），
 * 避免内部 LazyColumn 无 weight 占据全部空间导致按钮不可见。
 *
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * **v2026-07-27 P8 Phase 3 改造**：
 * - 自定义类型列表接入 Reorderable 库
 * - 拖拽中视觉：scale 1.05 + shadowElevation 8dp + zIndex 1f
 * - 触觉反馈：HapticFeedbackManager.TEXT_MOVE
 * - 顶部"全部日期" + 8 个内置 DateCategory 仍**不可拖拽**（顺序由 enum 声明决定）
 *
 * @param selectedDateCategory 当前选中的类型（null=全部, "BIRTHDAY"=内置, "CUSTOM:42"=自定义）
 * @param dateCountByCategory 每个类型对应的日期计数
 * @param customDateTypes 自定义类型列表（已按 sortOrder ASC 排序，可拖拽）
 * @param onDateCategoryClick 类型点击回调
 * @param onCustomTypeAction 自定义类型操作回调（ShowMenu / Rename / Delete）
 * @param onReorder 自定义类型拖拽完成回调（v2026-07-27 P8 Phase 3 新增，参数为排序后的新列表）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun DateTypeFilterSection(
    selectedDateCategory: String?,
    dateCountByCategory: Map<String, Int>,
    customDateTypes: List<CustomDateType>,
    onDateCategoryClick: (String?) -> Unit,
    onCustomTypeAction: (DateTypeAction) -> Unit,
    onReorder: (List<CustomDateType>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 🆕 v2026-07-27 P8 Phase 3 拖拽状态
    // 固定项偏移：LazyColumn 前 9 项（"全部日期" + 8 个 DateCategory）不可拖拽
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 全局索引 → customDateTypes 子列表索引（减去固定项偏移 9）
        val fromIndex = from.index - 9
        val toIndex = to.index - 9
        if (fromIndex !in customDateTypes.indices || toIndex !in customDateTypes.indices) {
            return@rememberReorderableLazyListState
        }
        val newList = customDateTypes.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        onReorder(newList)
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
            // "全部日期" 项（不可拖拽）
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部日期",
                    count = dateCountByCategory.values.sum(),
                    isSelected = selectedDateCategory == null,
                    showMenu = false,
                    onClick = { onDateCategoryClick(null) }
                )
            }

            // 8 个内置类型（DateCategory 枚举，不可拖拽 — 顺序由 enum 声明决定）
            items(DateCategory.entries.toList()) { dateCategory ->
                CategoryItem(
                    icon = dateCategory.emoji,
                    name = dateCategory.displayName,
                    count = dateCountByCategory[dateCategory.name] ?: 0,
                    isSelected = selectedDateCategory == dateCategory.name,
                    showMenu = false,
                    onClick = { onDateCategoryClick(dateCategory.name) }
                )
            }

            // 自定义类型（v2026-07-27 P8 Phase 3 起支持长按拖拽）
            //    注意：key 用 customType.id（稳定主键），不是 index
            items(
                items = customDateTypes,
                key = { it.id }
            ) { customType ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = customType.id
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
                            icon = customType.emoji,
                            name = customType.name,
                            count = dateCountByCategory["CUSTOM:${customType.id}"] ?: 0,
                            isSelected = selectedDateCategory == "CUSTOM:${customType.id}",
                            showMenu = true,
                            onClick = { onDateCategoryClick("CUSTOM:${customType.id}") },
                            onMenuClick = {
                                onCustomTypeAction(DateTypeAction.ShowMenu(customType))
                            }
                        )
                    }
                }
            }
        }
    }
}
