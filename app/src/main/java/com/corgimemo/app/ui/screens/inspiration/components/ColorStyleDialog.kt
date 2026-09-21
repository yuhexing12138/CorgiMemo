package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.theme.ThemeManager

/**
 * 颜色对话框（底部工具栏「A」按钮弹出，v2026-09-21 重构）
 *
 * 承载**四组**色板，按作用维度分两段：
 *
 * | 分组 | 名称 | 作用范围 | 下行通道 / 值 |
 * | --- | --- | --- | --- |
 * | 行内 | 选中文字色 | 当前选区内的文字 | `format("textColor", hex\|"default")` |
 * | 行内 | 选中背景色 | 当前选区内的文字底 | `format("backgroundColor", hex\|"default")` |
 * | 块级 | 段落文字色 | 光标所在**整段** | `setBlockColor(textColor = 色名\|"default")` |
 * | 块级 | 段落背景色 | 光标所在**整段**的底 | `setBlockColor(backgroundColor = 色名\|"default")` |
 *
 * 前两组的命名由来：它们只影响"已经选中的那段文字"，与「段落」维度相对，
 * 故 v2026-09-21 由「文字颜色 / 背景颜色」改名为「选中文字色 / 选中背景色」。
 * 后两组是从 ⋮ 菜单**整体移入**的块级颜色入口（原名称「背景色 / 文字色」），
 * 按要求更名为「段落文字色 / 段落背景色」，⋮ 菜单里不再保留入口。
 *
 * 四组色板的**样式完全一致**（用户要求）：标签 11sp + 一行 24dp 色点、
 * 「默认」画斜杠表示清除、当前色以暖橙 2dp 描边标记，色值同源于
 * [BlockColorPalette]（BlockNote 官方明暗两套色）——见 [BlockColorRow]。
 *
 * **点选即关闭对话框**：与"选中色"组原有行为一致，四组统一；需要连续调整时
 * 再次点「A」即可（弹窗内保持打开会让"点选后没反馈"的错觉更明显）。
 *
 * @param currentBlockTextColor 当前段落的文字色名（空串 / "default" = 未设置，用于回显选中态）
 * @param currentBlockBackgroundColor 当前段落的背景色名（语义同上）
 * @param onPickInlineTextColor 选中文字色点选回调；参数为 "#RRGGBB"，null = 恢复默认
 * @param onPickInlineBackgroundColor 选中背景色点选回调；参数为 "#RRGGBB"，null = 恢复默认
 * @param onPickBlockTextColor 段落文字色点选回调；参数为 BlockNote 色名，null = 清除
 * @param onPickBlockBackgroundColor 段落背景色点选回调；参数为 BlockNote 色名，null = 清除
 * @param onDismiss 关闭请求（点遮罩 / 返回键 / 「关闭」按钮）
 */
@Composable
internal fun ColorStyleDialog(
    currentBlockTextColor: String,
    currentBlockBackgroundColor: String,
    onPickInlineTextColor: (String?) -> Unit,
    onPickInlineBackgroundColor: (String?) -> Unit,
    onPickBlockTextColor: (String?) -> Unit,
    onPickBlockBackgroundColor: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    /**
     * 明暗判定口径与 [com.corgimemo.app.ui.screens.probe.BlockNoteEditorWebView] 及
     * 原 ⋮ 菜单色板保持一致：`"dark" / "light"` 是显式指定，其余（如 system）跟随系统。
     * 在本组件内自行订阅而不由宿主传入，是为了让「颜色对话框」自洽——
     * 宿主接线时无需再引一次 ThemeManager / isSystemInDarkTheme。
     */
    val themeMode by ThemeManager.themeMode.collectAsState()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "颜色") },
        text = {
            /**
             * 四组色板纵向排列，内容较高（窄屏 / 大字号时可能超过弹窗可用高度），
             * 故加高度上限 + 纵向滚动兜底——M3 的 AlertDialog 内容区**不会**自动滚动，
             * 不给上限时末尾的「段落背景色」会被裁掉。
             */
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                /**
                 * 第一组：选中文字色（行内）
                 * `showSelection = false`——宿主拿不到"光标处已有的行内色"（桥协议未上行
                 * 行内样式），强行传 current 只会让「默认」恒定高亮造成误导。
                 * 块级两组有 `blockState` 回显，故正常显示选中态。
                 */
                BlockColorRow(
                    label = "选中文字色",
                    current = "",
                    isDark = isDark,
                    picker = { name, dark -> BlockColorPalette.text(name, dark) },
                    onPick = { name ->
                        onPickInlineTextColor(
                            if (name == "default") null else blockColorHexOf(name, isDark, false)
                        )
                    },
                    showSelection = false
                )
                /** 第二组：选中背景色（行内） */
                BlockColorRow(
                    label = "选中背景色",
                    current = "",
                    isDark = isDark,
                    picker = { name, dark -> BlockColorPalette.background(name, dark) },
                    onPick = { name ->
                        onPickInlineBackgroundColor(
                            if (name == "default") null else blockColorHexOf(name, isDark, true)
                        )
                    },
                    showSelection = false
                )

                /** 分组分隔：以上为"选中（行内）"，以下为"段落（块级）" */
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                /** 第三组：段落文字色（块级，回显当前段落文字色） */
                BlockColorRow(
                    label = "段落文字色",
                    current = currentBlockTextColor,
                    isDark = isDark,
                    picker = { name, dark -> BlockColorPalette.text(name, dark) },
                    onPick = { name ->
                        onPickBlockTextColor(if (name == "default") null else name)
                    }
                )
                /** 第四组：段落背景色（块级，回显当前段落背景色） */
                BlockColorRow(
                    label = "段落背景色",
                    current = currentBlockBackgroundColor,
                    isDark = isDark,
                    picker = { name, dark -> BlockColorPalette.background(name, dark) },
                    onPick = { name ->
                        onPickBlockBackgroundColor(if (name == "default") null else name)
                    }
                )
                /** 底部补一点内边距，避免最后一行的色点紧贴滚动边界 */
                Spacer(modifier = Modifier.height(6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "关闭",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}
