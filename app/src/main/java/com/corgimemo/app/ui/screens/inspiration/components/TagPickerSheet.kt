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

/**
 * 标签选择底部弹窗组件
 *
 * 带搜索选择器型变体，用于灵感编辑页的标签管理。
 * FlowRow Chip 布局 + 输入框添加 + 历史标签快速选择 + 取消/确认按钮。
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 *
 * @param sheetState 底部弹窗状态控制对象
 * @param tags 当前标签列表（初始值）
 * @param savedTags 历史标签列表（从所有灵感聚合去重）
 * @param onTagsChange 标签变更回调（仅点击确认时触发）
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
    var localTags by remember { mutableStateOf(tags) }
    var newTagText by remember { mutableStateOf("") }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            /** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
            DragHandle()

            /** 标题栏：左对齐标题 + 右侧圆形关闭按钮 */
            TitleBar(title = "标签管理", onDismiss = onDismiss)

            /** 标题下方分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(12.dp))

            /** 输入框 + 添加按钮 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    placeholder = {
                        Text("输入新标签...", color = Color(0xFF999999), fontSize = 14.sp)
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
                        .background(Color(0xFFFF9A5C))
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

            /** 标签流式排布区域 */
            if (localTags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无标签，请在上方输入添加",
                        color = Color(0xFF999999),
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

            /** 历史标签区域 */
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
                        color = Color(0xFF888888),
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
                        color = Color(0xFF888888),
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
                        color = Color(0xFFFF9A5C),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    /** 长按标签删除确认对话框 */
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
                    Text("删除", color = Color(0xFFDC2626))
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

/** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE0E0E0))
        )
    }
}

/** 标题栏：左对齐标题 + 右侧圆形关闭按钮 */
@Composable
private fun TitleBar(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2D2D2D),
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFFFF0E5))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color(0xFFFF9A5C),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 标签 Chip 组件
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
                onClick = { /* 点击无操作 */ },
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
            color = Color(0xFFFF9A5C)
        )
    }
}

/**
 * 历史标签 Chip 组件
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
                color = Color(0xFFF0F0F2),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$tag",
            fontSize = 14.sp,
            color = Color(0xFF888888)
        )
    }
}
