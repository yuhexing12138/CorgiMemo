package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
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
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardColorPicker
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleSelector
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleTab
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleTabs

/**
 * 主题选择底部弹窗
 *
 * 专属工具型底部弹窗，提供卡片样式和颜色选择功能。
 * 内置 Tab 切换（样式/颜色），选中即回调。
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 *
 * @param show 是否显示弹窗
 * @param initialStyle 初始卡片样式
 * @param initialColor 初始卡片颜色
 * @param title 标题（用于缩略图渲染）
 * @param targetDateMillis 目标日期（用于缩略图渲染）
 * @param onDismiss 关闭弹窗回调
 * @param onConfirm 确认回调，参数为新的样式和颜色
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerBottomSheet(
    show: Boolean,
    initialStyle: DateCardStyle,
    initialColor: DateCardColor,
    title: String,
    targetDateMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (style: DateCardStyle, color: DateCardColor) -> Unit
) {
    if (!show) return

    var selectedTab by remember { mutableStateOf(DateCardStyleTab.STYLE) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    LaunchedEffect(show) {
        if (show) {
            selectedStyle = initialStyle
            selectedColor = initialColor
            selectedTab = DateCardStyleTab.STYLE
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            TitleBar(title = "选择主题", onDismiss = onDismiss)

            /** 标题下方分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(12.dp))

            /** 专属工具内容区：保留原宽度，居中对齐 */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tab 切换
                DateCardStyleTabs(
                    selected = selectedTab,
                    onTabChange = { selectedTab = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 根据 Tab 显示不同内容
                when (selectedTab) {
                    DateCardStyleTab.STYLE -> {
                        DateCardStyleSelector(
                            styles = DateCardStyle.all,
                            selected = selectedStyle,
                            onSelect = {
                                selectedStyle = it
                                onConfirm(it, selectedColor)
                            },
                            targetDateMillis = targetDateMillis,
                            title = title,
                            cardColor = selectedColor
                        )
                    }
                    DateCardStyleTab.COLOR -> {
                        DateCardColorPicker(
                            selected = selectedColor,
                            onSelect = {
                                selectedColor = it
                                onConfirm(selectedStyle, it)
                            },
                            onRainbowClick = { /* Rainbow 暂不支持 */ }
                        )
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
