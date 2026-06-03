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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
 *
 * 表示颜色选择器中的一个可选颜色项，
 * 包含颜色值、显示名称和是否为默认选中状态。
 *
 * @param color 颜色值（Color 对象）
 * @param name 颜色的中文名称（用于无障碍描述和调试）
 * @param isDefault 是否为默认选中颜色（默认 false）
 */
data class ColorItem(
    val color: Color,
    val name: String,
    val isDefault: Boolean = false
)

/**
 * 背景色选择器底部弹出面板
 *
 * 提供预设的 12 种背景色供用户选择，
 * 使用 Material3 ModalBottomSheet 实现，
 * 符合项目 UI 设计规范（暖橙色主题、大圆角）。
 *
 * **功能特性**:
 * - ✅ 12 种预设颜色（暖色系为主，适合待办卡片）
 * - ✅ 4×3 网格布局，直观易用
 * - ✅ 当前选中色显示 ✓ 对勾标记
 * - ✅ 点击即时应用到内容区背景（无需确认按钮）
 * - ✅ 自定义颜色入口（预留扩展）
 * - ✅ 平滑的进入/退出动画
 *
 * **颜色方案** (12 种预设色):
 * | 序号 | 颜色值 | 名称 | 用途 |
 * |------|--------|------|------|
 * | 1 | #FFFFFF | 白色 | 默认（干净清爽） |
 * | 2 | #FFF5F0 | 暖白 | 温柔舒适 |
 * | 3 | #FFE0C0 | 浅橙 | 活力温暖 |
 * | 4 | #E3F2FD | 浅蓝 | 清新宁静 |
 * | 5 | #E8F5E9 | 浅绿 | 自然生机 |
 * | 6 | #FFF3E0 | 暖黄 | 温馨明亮 |
 * | 7 | #FCE4EC | 浅粉 | 柔和浪漫 |
 * | 8 | #F3E5F5 | 浅紫 | 优雅神秘 |
 * | 9 | #E0F7FA | 浅青 | 清爽透彻 |
 * | 10 | #FFF9C4 | 浅黄绿 | 春意盎然 |
 * | 11 | #37474F | 深灰 | 专业沉稳 |
 * | 12 | #263238 | 近黑 | 极简高级 |
 *
 * **UI 布局结构**:
 * ```
 * ┌─────────────────────────────────┐
 * │  ════════════════════════════   │  ← 拖拽手柄
 * │                                 │
 * │         选择背景颜色              │  ← 标题
 * │                                 │
 * │  ┌───┐ ┌───┐ ┌───┐ ┌───┐       │
 * │  │ ○ │ │ ● │ │ ○ │ │ ○ │       │  ← 4×3 网格布局
 * │  └───┘ └───┘ └───┘ └───┘       │
 * │  ┌───┐ ┌───┐ ┌───┐ ┌───┐       │
 * │  │ ○ │ │ ○ │ │ ○ │ │ ○ │       │
 * │  └───┘ └───┘ └───┘ └───┘       │
 * │  ┌───┐ ┌───┐ ┌───┐ ┌───┐       │
 * │  │ ○ │ │ ○ │ │ ○ │ │ ○ │       │
 * │  └───┘ └───┘ └───┘ └───┘       │
 * │                                 │
 * │  ┌─────────────────────────┐    │
 * │  │    🎨 自定义颜色...      │    │  ← 高级选项（预留）
 * │  └─────────────────────────┘    │
 * │                                 │
 * └─────────────────────────────────┘
 * ```
 *
 * **使用示例**:
 * ```kotlin
 * var backgroundColor by remember { mutableStateOf(Color.White) }
 * var showColorPicker by remember { mutableStateOf(false) }
 *
 * if (showColorPicker) {
 *     ColorPickerBottomSheet(
 *         sheetState = rememberModalBottomSheetState(),
 *         selectedColor = backgroundColor,
 *         onColorSelected = { color ->
 *             backgroundColor = color
 *             showColorPicker = false // 选择后自动关闭
 *         },
 *         onDismiss = { showColorPicker = false }
 *     )
 * }
 * ```
 *
 * @param sheetState BottomSheet 的状态控制对象
 * @param selectedColor 当前选中的颜色值
 * @param onColorSelected 用户选择新颜色时的回调（参数为新选择的 Color）
 * @param onDismiss 底部面板关闭回调（点击外部区域或返回键）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerBottomSheet(
    sheetState: androidx.compose.material3.SheetState = rememberModalBottomSheetState(),
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    /**
     * 预设颜色列表
     * 12 种精心挑选的背景色，以暖色系为主，符合项目整体设计风格
     */
    val presetColors = remember {
        listOf(
            ColorItem(Color(0xFFFFFFFF), "白色", isDefault = true),   // 1. 白色（默认）
            ColorItem(Color(0xFFFFF5F0), "暖白"),                     // 2. 暖白色
            ColorItem(Color(0xFFFFE0C0), "浅橙"),                     // 3. 浅橙色
            ColorItem(Color(0xFFE3F2FD), "浅蓝"),                     // 4. 浅蓝色
            ColorItem(Color(0xFFE8F5E9), "浅绿"),                     // 5. 浅绿色
            ColorItem(Color(0xFFFFF3E0), "暖黄"),                     // 6. 暖黄色
            ColorItem(Color(0xFFFCE4EC), "浅粉"),                     // 7. 浅粉色
            ColorItem(Color(0xFFF3E5F5), "浅紫"),                     // 8. 浅紫色
            ColorItem(Color(0xFFE0F7FA), "浅青"),                     // 9. 浅青色
            ColorItem(Color(0xFFFFF9C4), "浅黄绿"),                   // 10. 浅黄绿色
            ColorItem(Color(0xFF37474F), "深灰"),                     // 11. 深灰色
            ColorItem(Color(0xFF263238), "近黑")                      // 12. 近黑色
        )
    }

    /** 根据当前选中颜色找到对应的索引位置 */
    var selectedIndex by remember(selectedColor) {
        mutableIntStateOf(
            presetColors.indexOfFirst { it.color == selectedColor }.takeIf { it >= 0 } ?: 0
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface, /** 背景色使用主题表面色 */
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), /** 顶部大圆角 */
        dragHandle = {
            /** 自定义拖拽手柄 */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /** 拖拽指示条 */
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Spacer(modifier = Modifier.height(16.dp))

                /** 面板标题 */
                Text(
                    text = "选择背景颜色",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /**
                 * 颜色网格：4 列 × 3 行
                 * 使用嵌套 Row/Column 实现网格布局
                 */
                presetColors.chunked(4).forEach { rowColors ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowColors.forEachIndexed { columnIndex, colorItem ->
                            /** 计算该颜色在列表中的全局索引 */
                            val globalIndex = presetColors.indexOf(colorItem)
                            /** 是否为当前选中项 */
                            val isSelected = globalIndex == selectedIndex

                            /**
                             * 单个颜色选择按钮
                             *
                             * 视觉效果：
                             * - 未选中：纯色圆形 + 细边框
                             * - 选中：粗边框（暖橙色）+ 右下角 ✓ 对勾
                             */
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable {
                                        /** 更新选中索引并触发回调 */
                                        selectedIndex = globalIndex
                                        onColorSelected(colorItem.color)
                                    }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                /** 颜色圆形 */
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(colorItem.color)
                                        .then(
                                            if (isSelected) {
                                                /** 选中态：添加暖橙色粗边框 */
                                                Modifier
                                                    .border(
                                                        width = 3.dp,
                                                        color = Color(0xFFFF9A5C), /** 暖橙色 */
                                                        shape = CircleShape
                                                    )
                                            } else {
                                                /** 未选中态：添加细灰色边框 */
                                                Modifier
                                                    .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                        shape = CircleShape
                                                    )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    /** 选中时显示 ✓ 对勾标记 */
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            color = if (colorItem.color == Color.White ||
                                                colorItem.color == Color(0xFFFFF5F0) ||
                                                colorItem.color == Color(0xFFFFE0C0) ||
                                                colorItem.color == Color(0xFFFCE4EC)
                                            ) {
                                                Color.DarkGray /** 浅色背景用深色对勾 */
                                            } else {
                                                Color.White /** 深色背景用白色对勾 */
                                            },
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            /** 列间距（最后一列不加） */
                            if (columnIndex < 3) {
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                /**
                 * 自定义颜色入口按钮（预留功能）
                 *
                 * P3 阶段实现：点击后打开系统颜色选择器或自定义取色器
                 * 当前版本灰显或隐藏，提示"即将推出"
                 */
                /*
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable(enabled = false) { /* TODO: P3 实现自定义颜色 */ }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🎨",
                            fontSize = 18.sp
                        )
                        Text(
                            text = "自定义颜色...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                 */
            }
        }
    )
}
