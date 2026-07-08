package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 标签选择底部弹窗组件
 *
 * 用于灵感编辑页的标签管理，排布方式参考待办编辑页的 CategorySelectorDialog：
 * - 标签使用 FlowRow 流式布局（圆角矩形 Chip 样式，带 # 前缀）
 * - 长按标签触发删除确认对话框（非直接删除，防误触）
 * - 顶部输入框 + 添加按钮：添加新标签到本地暂存列表
 * - 历史标签区域：显示曾经使用过的标签，点击快速添加
 * - 底部"取消"/"确认"按钮：取消放弃本次更改，确认保存并关闭
 *
 * 本地暂存机制：
 * - 打开弹窗时以传入的 tags 初始化 localTags
 * - 增删操作只修改 localTags，不立即回调
 * - 点击"确认"：onTagsChange(localTags) + onDismiss()
 * - 点击"取消"：直接 onDismiss()（不回调，放弃更改）
 *
 * @param sheetState 底部弹窗状态控制对象
 * @param tags 当前标签列表（初始值）
 * @param savedTags 历史标签列表（从所有灵感聚合去重，用于快速选择）
 * @param onTagsChange 标签变更回调（仅点击确认时触发，传入完整新列表）
 * @param onDismiss 弹窗关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TagPickerSheet(
    sheetState: SheetState,
    tags: List<String>,
    savedTags: List<String> = emptyList(),
    onTagsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    /** 本地暂存标签列表（确认后才回调） */
    var localTags by remember { mutableStateOf(tags) }
    /** 当前输入的新标签内容 */
    var newTagText by remember { mutableStateOf("") }
    /** 待删除的标签（长按后弹出确认对话框） */
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            /** 自定义拖动指示器 */
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.width(36.dp)
                )
            }

            /** 标题栏：标题 + 关闭按钮 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "标签管理",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = UiColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            /** 自定义输入框 + 添加按钮 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    placeholder = {
                        Text("输入新标签...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                /** 添加按钮 */
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(UiColors.Primary)
                        .clickable {
                            val trimmed = newTagText.trim()
                            if (trimmed.isNotBlank() && trimmed !in localTags) {
                                localTags = localTags + trimmed
                            }
                            newTagText = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加标签",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            /** 标签流式排布区域（参考待办编辑页 CategorySelectorDialog 的 FlowRow 布局） */
            if (localTags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无标签，请在上方输入添加",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        localTags.forEach { tag ->
                            TagChip(
                                tag = tag,
                                onLongClick = { pendingDeleteTag = tag }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            /** 历史标签区域：显示曾经使用过的标签（排除已选中的），点击快速添加 */
            val availableSavedTags = savedTags.filter { it !in localTags }
            if (availableSavedTags.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "历史标签",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableSavedTags.forEach { tag ->
                            HistoryTagChip(
                                tag = tag,
                                onClick = {
                                    if (tag !in localTags) {
                                        localTags = localTags + tag
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            /** 底部按钮区：取消 | 确认 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onTagsChange(localTags)
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "确认",
                        color = UiColors.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    /** 长按标签删除确认对话框（防误触） */
    pendingDeleteTag?.let { targetTag ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("删除标签", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "确定要删除标签「#$targetTag」吗？",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        localTags = localTags - targetTag
                        pendingDeleteTag = null
                    }
                ) {
                    Text(
                        text = "删除",
                        color = Color(0xFFDC2626)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTag = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 标签 Chip 组件
 *
 * 圆角矩形样式，带 # 前缀，长按触发删除回调。
 * 样式参考待办编辑页 CategorySelectorDialog 的 CategoryTag。
 *
 * @param tag 标签文本
 * @param onLongClick 长按回调（触发删除确认对话框）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(
    tag: String,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .combinedClickable(
                onClick = { /* 点击无操作（仅展示） */ },
                onLongClick = onLongClick
            )
            .background(
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$tag",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = UiColors.Primary
        )
    }
}

/**
 * 历史标签 Chip 组件
 *
 * 用于显示曾经使用过的标签，点击后添加到当前标签列表。
 * 视觉上与已选中标签区分（使用 surfaceVariant 背景 + 灰色文字）。
 *
 * @param tag 标签文本
 * @param onClick 点击回调（添加到当前标签列表）
 */
@Composable
private fun HistoryTagChip(
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$tag",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
