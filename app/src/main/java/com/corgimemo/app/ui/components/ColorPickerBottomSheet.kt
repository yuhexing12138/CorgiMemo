package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * 颜色选项数据类
 */
data class ColorItem(
    val color: Color,
    val name: String,
    val isDefault: Boolean = false
)

/**
 * 背景色选择器底部弹窗
 *
 * 专属工具型底部弹窗，12 种预设颜色以 4×3 网格排列。
 * 选中色块显示暖橙粗边框 + ✓ 标记，点击即时生效。
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 *
 * @param sheetState BottomSheet 状态控制对象
 * @param selectedColor 当前选中的颜色值
 * @param onColorSelected 用户选择新颜色时的回调
 * @param onDismiss 关闭弹窗回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerBottomSheet(
    sheetState: androidx.compose.material3.SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    ),
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val presetColors = remember {
        listOf(
            ColorItem(Color(0xFFFFFFFF), "白色", isDefault = true),
            ColorItem(Color(0xFFFFF5F0), "暖白"),
            ColorItem(Color(0xFFFFE0C0), "浅橙"),
            ColorItem(Color(0xFFE3F2FD), "浅蓝"),
            ColorItem(Color(0xFFE8F5E9), "浅绿"),
            ColorItem(Color(0xFFFFF3E0), "暖黄"),
            ColorItem(Color(0xFFFCE4EC), "浅粉"),
            ColorItem(Color(0xFFF3E5F5), "浅紫"),
            ColorItem(Color(0xFFE0F7FA), "浅青"),
            ColorItem(Color(0xFFFFF9C4), "浅黄绿"),
            ColorItem(Color(0xFF37474F), "深灰"),
            ColorItem(Color(0xFF263238), "近黑")
        )
    }

    var selectedIndex by remember(selectedColor) {
        mutableIntStateOf(
            presetColors.indexOfFirst { it.color == selectedColor }.takeIf { it >= 0 } ?: 0
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
            TitleBar(title = "选择背景颜色", onDismiss = onDismiss)

            /** 标题下方分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(16.dp))

            /** 专属工具内容区：4×3 色块网格 */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                presetColors.chunked(4).forEach { rowColors ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowColors.forEachIndexed { columnIndex, colorItem ->
                            val globalIndex = presetColors.indexOf(colorItem)
                            val isSelected = globalIndex == selectedIndex

                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable {
                                        selectedIndex = globalIndex
                                        onColorSelected(colorItem.color)
                                    }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(colorItem.color)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 3.dp,
                                                    color = Color(0xFFFF9A5C),
                                                    shape = CircleShape
                                                )
                                            } else {
                                                Modifier.border(
                                                    width = 1.dp,
                                                    color = Color(0x33000000),
                                                    shape = CircleShape
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            color = if (
                                                colorItem.color == Color(0xFFFFFFFF) ||
                                                colorItem.color == Color(0xFFFFF5F0) ||
                                                colorItem.color == Color(0xFFFFE0C0) ||
                                                colorItem.color == Color(0xFFFCE4EC)
                                            ) {
                                                Color.DarkGray
                                            } else {
                                                Color.White
                                            },
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (columnIndex < 3) {
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
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
