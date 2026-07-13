package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardColorPicker
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleSelector
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleTab
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleTabs

/**
 * 主题选择底部弹窗
 *
 * 提供卡片样式和颜色选择功能，参考 SpecialDateCardStyleScreen 的实现。
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

    val sheetState = rememberModalBottomSheetState()
    var selectedTab by remember { mutableStateOf(DateCardStyleTab.STYLE) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    // 重置初始值
    LaunchedEffect(show) {
        if (show) {
            selectedStyle = initialStyle
            selectedColor = initialColor
            selectedTab = DateCardStyleTab.STYLE
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            // 标题
            Text(
                text = "选择主题",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
    }
}
