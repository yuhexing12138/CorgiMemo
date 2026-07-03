package com.corgimemo.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 置顶区头按钮(方案 A:列表顶部独立)
 *
 * 在已置顶待办数量 ≥ 4 时显示。点击可展开/折叠所有置顶待办。
 * 基于 [CollapsibleSectionHeader] 实现,统一设计语言。
 *
 * @param count 当前置顶待办数量
 * @param isExpanded 是否展开
 * @param onClick 点击回调
 * @param modifier 外部 Modifier
 */
@Composable
fun PinnedSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = CollapsibleSectionHeader(
    label = "置顶",
    count = count,
    isExpanded = isExpanded,
    color = MaterialTheme.colorScheme.primary,
    expandedLabel = "收起置顶",
    collapsedLabel = "展开置顶",
    onClick = onClick,
    modifier = modifier,
)
