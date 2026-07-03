package com.corgimemo.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 待完成区头按钮
 *
 * 位置根据置顶数量动态调整：
 * - 置顶 ≤ 3 时，显示在列表最前
 * - 置顶 ≥ 4 时，显示在置顶区之后
 *
 * 基于 [CollapsibleSectionHeader] 实现，统一设计语言（无背景、箭头在左、无水波纹）。
 * 颜色 = [SectionHeaderColors.Pending]（蓝色），与置顶的 primary 色形成视觉区分。
 *
 * @param count 当前待完成数量（由 pendingCount 提供，动态计算）
 * @param isExpanded 是否展开
 * @param onClick 点击回调
 * @param modifier 外部 Modifier
 */
@Composable
fun PendingSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = CollapsibleSectionHeader(
    label = "待完成",
    count = count,
    isExpanded = isExpanded,
    color = SectionHeaderColors.Pending,
    expandedLabel = "收起待完成",
    collapsedLabel = "展开待完成",
    onClick = onClick,
    modifier = modifier,
)
