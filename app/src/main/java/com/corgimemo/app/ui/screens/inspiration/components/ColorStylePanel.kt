package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.theme.ThemeManager

/**
 * 编辑页「颜色」面板（内联面板，非弹窗；v2026-09-21 由弹窗 ColorStyleDialog 改造而来）
 *
 * 由 [InspirationEditBottomBar] 插入在「格式工具栏」与「相机行」之间，与
 * [FontPickerPanel]（T）、[HeadingPanel]（H）**三者互斥、占同一槽位、同高度**
 * （面板高度 = 键盘高度，互斥切换不跳动）——即与 T / H 完全一致的展示形态。
 *
 * **布局（与 [FontPickerPanel] 同规格）**：
 * - 面板头（40dp）：左「颜色」标题 + 「点选即时生效」提示，右「完成」文字按钮
 * - 分隔线（1dp，outlineVariant 50%）
 * - 内容区：`weight(1f)` 占满剩余高度并纵向滚动，内含四组色板
 *
 * **四组色板**（均复用 [BlockColorRow]，样式与 ⋮ 菜单原色板完全一致：
 * 默认斜杠点 + 9 个 BlockNote 官方预设色 + 暖橙 2dp 选中描边）：
 *
 * | 分组 | 名称 | 作用范围 | 下行通道 / 值 |
 * | --- | --- | --- | --- |
 * | 行内 | 选中文字色 | 当前选区内的文字 | `format("textColor", hex｜"default")` |
 * | 行内 | 选中背景色 | 当前选区内的文字底 | `format("backgroundColor", hex｜"default")` |
 * | 块级 | 段落文字色 | 光标所在**整段** | `setBlockColor(textColor = 色名｜"default")` |
 * | 块级 | 段落背景色 | 光标所在**整段**的底 | `setBlockColor(backgroundColor = 色名｜"default")` |
 *
 * **生效语义**（与 H 面板一致）：点选**即时生效且不收起面板**，便于连续微调；
 * 收起由面板头「完成」或再点一次工具栏「A」按钮触发。故本面板只负责展示与回调，
 * 真正的 SpanStyle / 块 props 写入由调用方（InspirationEditScreen）完成。
 *
 * **选中回显**：段落两组读 `blockState` 的当前块色名回显；行内两组无状态上行
 * （宿主拿不到"光标处已有的行内色"），传 `showSelection = false` 关闭回显，
 * 否则「默认」项会恒定高亮造成误导。
 *
 * @param panelHeight 面板总高度（= 键盘高度；内容超出纵向滚动）
 * @param currentBlockTextColor 当前段落的文字色名（空串 / "default" = 未设置，用于回显）
 * @param currentBlockBackgroundColor 当前段落的背景色名（语义同上）
 * @param onPickInlineTextColor 选中文字色点选回调；参数为 "#RRGGBB"，null = 恢复默认
 * @param onPickInlineBackgroundColor 选中背景色点选回调；参数为 "#RRGGBB"，null = 恢复默认
 * @param onPickBlockTextColor 段落文字色点选回调；参数为 BlockNote 色名，null = 清除
 * @param onPickBlockBackgroundColor 段落背景色点选回调；参数为 BlockNote 色名，null = 清除
 * @param onDone 点击面板头「完成」（收起面板）
 * @param enabled 面板**内容区**是否可用（v2026-09-21 新增）：宿主锁定态传 false——
 *   四组色板整片降到 38% 不透明度，并在 `PointerEventPass.Initial` 阶段拦截点击
 *   （与格式工具栏的 `toolbarEnabled` 口径一致）。⚠️ 面板头「完成」不受影响。
 * @param modifier Modifier
 */
@Composable
internal fun ColorStylePanel(
    panelHeight: Dp,
    currentBlockTextColor: String,
    currentBlockBackgroundColor: String,
    onPickInlineTextColor: (String?) -> Unit,
    onPickInlineBackgroundColor: (String?) -> Unit,
    onPickBlockTextColor: (String?) -> Unit,
    onPickBlockBackgroundColor: (String?) -> Unit,
    onDone: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    /**
     * 禁用态拦截（仅内容区）：锁定态下四组色板不接受点击。
     *
     * ⚠️ 必须用 `PointerEventPass.Initial`（父→子阶段）——色点的 `clickable` 在
     * `Main` 阶段（子→父）响应，在 `Main` 阶段拦不住子级，
     * 与 [RichTextFormatToolbar] 的 `disabledBlocker` 同一范式。
     */
    val contentBlocker = if (enabled) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    }

    /**
     * 明暗判定口径与 [com.corgimemo.app.ui.screens.probe.BlockNoteEditorWebView] 及
     * 原 ⋮ 菜单色板保持一致：`"dark" / "light"` 是显式指定，其余（如 system）跟随系统。
     * 在本组件内自行订阅而不由宿主传入，是为了让「颜色面板」自洽——
     * 宿主接线时无需再引一次 ThemeManager / isSystemInDarkTheme。
     */
    val themeMode by ThemeManager.themeMode.collectAsState()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
        ) {
            /** ---- 面板头：标题 + 提示 | 完成（40dp = TextButton 最小触摸高度，与另两个面板同规格） ---- */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "颜色",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "点选即时生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDone) {
                    Text(
                        text = "完成",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            /**
             * ---- 四组色板 ----
             *
             * 纵向滚动兜底：面板高度 = 键盘高度（约 290dp），40dp 面板头 + 四组色板
             * 在常规字体下刚好放得下；但窄屏 / 大字号下 [BlockColorRow] 的色点行会
             * 折成两行，此时靠滚动保证「段落背景色」不被裁掉。
             */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
                    /** 锁定态视觉降级：与格式工具栏 disabled 态同款 38% 不透明度 */
                    .alpha(if (enabled) 1f else 0.38f)
                    .then(contentBlocker)
            ) {
                /** 第一组：选中文字色（行内）——无状态上行，关闭选中回显 */
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
                /** 第二组：选中背景色（行内）——同上 */
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
            }
        }
    }
}
