package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.viewmodel.RelationListViewModel

/**
 * 关联列表 BottomSheet（v2026-07-22 新增）
 *
 * **用途**：
 * 点击首页/灵感页 todo 卡片 / 灵感卡片上的 🔗×N 徽章后弹出，
 * 展示该卡片作为 source 的全部关联（按 groupId=0 主分组），
 * 支持点击列表项跳转详情、右侧 × 按钮解除关联。
 *
 * **数据流**：
 * ```
 * 调用方传入 visible=true + sourceType + sourceId
 *   → LaunchedEffect 触发 RelationListViewModel.loadRelations(...)
 *     → 仓库层 getRelationsBlocking() 拉所有 CardRelation
 *     → 异步并发加载每条 relation 目标卡片的标题
 *   → 列表渲染
 * 用户点 × → viewModel.unlink(relationId)
 *   → 仓库 removeRelationById (双向删除)
 *   → viewModel 内部 _relations 过滤
 *   → 触发 onUnlinked() 回调，外层调用 ViewModel.refreshRelationCounts(...)
 * ```
 *
 * **关联类型颜色**（参考 LinkedCardPreviewDialog）：
 * - todo → 背景 #E3F2FD + 📝 + "待办"
 * - inspiration → 背景 #FFF3E0 + 💡 + "灵感"
 * - date → 背景 #FCE4EC + 📅 + "日期"
 *
 * **与其他组件的关系**：
 * - 与 [LinkedCardPreviewDialog] 的区别：本组件展示关联**列表**（管理向），
 *   那个 Dialog 展示关联**单卡片详情**（查看向）
 * - 与 [RelationPickerBottomSheet] 的区别：本组件是**查看已关联**，
 *   那个是**选择要关联**的
 *
 * @param visible 是否显示
 * @param sourceType 关联发起方类型 ("todo" | "inspiration" | "date")
 * @param sourceId   关联发起方 ID
 * @param groupId    分组 ID（todo 多分组时用，其他类型传 0）
 * @param onItemClick 点击列表项回调，参数为 (cardType, cardId)，由父级负责跳转详情
 * @param onDismiss   关闭弹窗回调（父级置 visible=false + 清空 sourceId）
 * @param onUnlinked  解除关联后回调（父级重新查询 relationCountMap）
 * @param viewModel   Hilt 注入的 ViewModel（默认 hiltViewModel()）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationListBottomSheet(
    visible: Boolean,
    sourceType: String,
    sourceId: Long,
    groupId: Int = 0,
    onItemClick: (cardType: String, cardId: Long) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
    onUnlinked: () -> Unit = {},
    viewModel: RelationListViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val relations by viewModel.relations.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    /**
     * 弹窗打开 / 关闭时同步 ViewModel 状态
     *
     * - visible=true：重新 loadRelations
     * - visible=false：clear() 清空 StateFlow，避免下次打开看到旧数据
     *
     * 使用 key=visible, sourceType, sourceId, groupId：当这些参数变化时
     * 重新触发 effect，避免列表错乱。
     */
    LaunchedEffect(visible, sourceType, sourceId, groupId) {
        if (visible) {
            viewModel.loadRelations(sourceType, sourceId, groupId)
        } else {
            viewModel.clear()
        }
    }

    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ========== 标题行 ==========
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "关联卡片（${relations.size}）",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D2D2D),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFF999999)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ========== 列表内容 ==========
            when {
                isLoading && relations.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFFF9A5C)
                        )
                    }
                }
                relations.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无关联卡片",
                            fontSize = 14.sp,
                            color = Color(0xFF999999)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(
                            items = relations,
                            key = { relation -> relation.id }
                        ) { relation ->
                            RelationListItem(
                                relation = relation,
                                targetTitle = titles[relation.id] ?: "加载中…",
                                onClick = {
                                    onItemClick(relation.targetType, relation.targetId)
                                },
                                onUnlink = {
                                    viewModel.unlink(relation.id)
                                    onUnlinked()
                                }
                            )
                            HorizontalDivider(
                                color = Color(0xFFEEEEEE),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }

            // 底部安全间距
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 关联列表项
 *
 * 布局：[类型图标 36x36] | [标题 + 类型标签] | [× 解除按钮]
 *
 * - 点击整行触发 onClick（跳转详情）
 * - 右侧 × IconButton 触发 onUnlink（解绑）
 * - 标题过长截断 + 省略号
 *
 * @param relation 关联实体
 * @param targetTitle 目标卡片标题（来自 ViewModel.titles 缓存）
 * @param onClick 点击整行回调
 * @param onUnlink 解除关联回调
 */
@Composable
private fun RelationListItem(
    relation: CardRelation,
    targetTitle: String,
    onClick: () -> Unit,
    onUnlink: () -> Unit
) {
    // 类型视觉规范（与 LinkedCardPreviewDialog 对齐）
    val typeBg = when (relation.targetType) {
        "todo" -> Color(0xFFE3F2FD)
        "inspiration" -> Color(0xFFFFF3E0)
        "date" -> Color(0xFFFCE4EC)
        else -> Color(0xFFF5F5F5)
    }
    val typeIconText = when (relation.targetType) {
        "todo" -> "📝"
        "inspiration" -> "💡"
        "date" -> "📅"
        else -> "📎"
    }
    val typeLabel = when (relation.targetType) {
        "todo" -> "待办"
        "inspiration" -> "灵感"
        "date" -> "日期"
        else -> "未知"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 类型图标
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(typeBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = typeIconText, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        // 标题 + 类型标签
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = targetTitle,
                fontSize = 14.sp,
                color = Color(0xFF2D2D2D),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = typeLabel,
                fontSize = 11.sp,
                color = Color(0xFF999999)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // 解除关联 × 按钮
        IconButton(
            onClick = onUnlink,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "解除关联",
                tint = Color(0xFFBBBBBB),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
