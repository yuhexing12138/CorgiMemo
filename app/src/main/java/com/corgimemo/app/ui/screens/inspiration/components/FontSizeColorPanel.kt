package com.corgimemo.app.ui.screens.inspiration.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 字号候选档位（sp，对照已审核原型 8 档），默认档 = [DEFAULT_BODY_SP]。
 */
val FONT_SIZE_TIERS = listOf(12, 14, 16, 18, 20, 24, 28, 32)

/** 正文默认字号（MaterialTheme bodyLarge = 16sp，与原型「正文默认 16sp」一致）。 */
const val DEFAULT_BODY_SP = 16

/**
 * 文字颜色预设条目（对照已审核原型 12 色板 + 默认项）。
 *
 * @param name 色名（面板头回显用）
 * @param color 颜色值；null = 「默认」项（不叠加 color SpanStyle，跟随主题文字色，
 *   色块渲染为白底斜杠圆）
 */
data class TextColorEntry(
    val name: String,
    val color: Color?
)

/** 文字颜色预设（第一项 = 默认；顺序与原型完全一致）。 */
val TEXT_COLORS = listOf(
    TextColorEntry("默认", null),
    TextColorEntry("墨黑", Color(0xFF1C1B1F)),
    TextColorEntry("深灰", Color(0xFF5F6368)),
    TextColorEntry("暖橙", Color(0xFFE88A4D)),
    TextColorEntry("砖红", Color(0xFFD93025)),
    TextColorEntry("玫红", Color(0xFFC2185B)),
    TextColorEntry("紫", Color(0xFF7B1FA2)),
    TextColorEntry("靛蓝", Color(0xFF1976D2)),
    TextColorEntry("青", Color(0xFF00796B)),
    TextColorEntry("绿", Color(0xFF2E7D32)),
    TextColorEntry("棕", Color(0xFF795548)),
    TextColorEntry("金", Color(0xFFF9A825))
)

/**
 * 编辑页「字号与颜色」面板（内联面板，非底部弹窗；v2026-09-04 按已审核原型落地）。
 *
 * 由 [InspirationEditBottomBar] 插入在「格式工具栏」与「相机行」之间，与
 * [FontPickerPanel] **互斥、占同一槽位、同高度**（面板高度 = 键盘高度，
 * 互斥切换不跳动）。
 *
 * **布局（对照原型）**：
 * - 面板头（40dp）：左「字号与颜色」+ 当前值回显（如「16sp · 默认」），右「完成」文字按钮
 * - 网格：每行固定 4 块（`GridCells.Fixed(4)`）、gap 8dp、左右内边距 16dp，内容超出纵向滚动
 * - 字号组：8 档，格内「Aa」按档位真实缩放 + 右侧 11sp 灰色数值（baseline 对齐）
 * - 颜色组：12 色 + 默认（白底斜杠圆块），居中 26dp 圆形色块
 * - 自定义颜色组：HSV 取色器（18dp 色相条 + 84dp SV 板，拖动取色）
 *
 * **生效语义（与原型一致）**：字号/颜色**点选即时生效**（无「应用」两段式），
 * 本面板只负责展示与回调，SpanStyle 写入由调用方（InspirationEditScreen）完成；
 * 面板头只保留「完成」收起。
 *
 * **选中态互斥**：自定义色生效（[customColorHex] != null）时预设色高亮全部让位；
 * 选预设色时取色器自动同步到该色（便于从预设微调）。
 *
 * @param panelHeight 面板总高度（= 键盘高度；内容超出纵向滚动）
 * @param currentFontSize 当前生效字号（sp；光标/选区未指定字号时 = [DEFAULT_BODY_SP]）
 * @param currentColorIdx 当前生效的预设色下标（0 = 默认；自定义色生效时高亮让位）
 * @param customColorHex 当前生效的自定义色（"#RRGGBB"）；null = 无自定义色
 * @param onFontSizeSelect 字号点击回调（参数为档位 sp 值；由调用方写 SpanStyle）
 * @param onPresetColorSelect 预设色点击回调（参数为 [TEXT_COLORS] 下标）
 * @param onCustomColorSelect 自定义取色回调（拖动过程中每帧回调，参数 "#RRGGBB"）
 * @param onDone 点击面板头「完成」（收起面板）
 */
@Composable
fun FontSizeColorPanel(
    panelHeight: Dp,
    currentFontSize: Int,
    currentColorIdx: Int,
    customColorHex: String?,
    onFontSizeSelect: (Int) -> Unit,
    onPresetColorSelect: (Int) -> Unit,
    onCustomColorSelect: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
        ) {
            /** ---- 面板头：标题 + 当前值 | 完成 ---- */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    /** 40dp = TextButton 最小触摸高度（与 FontPickerPanel 面板头同规格） */
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "字号与颜色",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentStyleLabel(currentFontSize, currentColorIdx, customColorHex),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

            /** ---- 网格：每行 4 块，内容超出纵向滚动 ---- */
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    /** weight 占据面板头以下的全部剩余高度（有界约束，Lazy 系列必需） */
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 组标题：字号（占满整行）
                item(key = "title_size", span = { GridItemSpan(maxLineSpan) }) {
                    GroupTitle(text = "字号", hint = "正文默认 ${DEFAULT_BODY_SP}sp")
                }
                items(
                    items = FONT_SIZE_TIERS,
                    key = { "size_$it" }
                ) { sp ->
                    FontSizeCell(
                        sp = sp,
                        selected = sp == currentFontSize,
                        onClick = { onFontSizeSelect(sp) }
                    )
                }

                // 组标题：颜色（占满整行）
                item(key = "title_color", span = { GridItemSpan(maxLineSpan) }) {
                    GroupTitle(text = "颜色", hint = "点选即时生效")
                }
                itemsIndexed(
                    items = TEXT_COLORS,
                    key = { index, _ -> "color_$index" }
                ) { index, entry ->
                    ColorSwatchCell(
                        entry = entry,
                        /** 自定义色生效时预设色高亮全部让位（二者互斥单选） */
                        selected = customColorHex == null && index == currentColorIdx,
                        onClick = { onPresetColorSelect(index) }
                    )
                }

                // 组标题：自定义颜色（占满整行）
                item(key = "title_custom", span = { GridItemSpan(maxLineSpan) }) {
                    GroupTitle(text = "自定义颜色", hint = "拖动取色")
                }
                item(key = "hsv_picker", span = { GridItemSpan(maxLineSpan) }) {
                    HsvPicker(
                        /** 选预设色/自定义色变化时取色器同步到该色（syncKey 变化重置内部 hsv） */
                        syncKey = customColorHex ?: "preset_$currentColorIdx",
                        initialHex = customColorHex
                            ?: TEXT_COLORS.getOrNull(currentColorIdx)?.color?.let(::colorToHex),
                        onColorChange = onCustomColorSelect
                    )
                }

                // 底部留白（滚动到底时与相机行保持间距）
                item(key = "bottom_pad", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/** 面板头当前值回显（对照原型「16sp · 默认」格式）。 */
private fun currentStyleLabel(
    currentFontSize: Int,
    currentColorIdx: Int,
    customColorHex: String?
): String {
    val colorLabel = customColorHex
        ?: TEXT_COLORS.getOrNull(currentColorIdx)?.let { if (it.color == null) "默认" else it.name }
        ?: "默认"
    return "${currentFontSize}sp · $colorLabel"
}

/**
 * 字号格：44dp，格内「Aa」按档位真实缩放（原型 1:1 映射）+ 右侧 11sp 数值，
 * baseline 对齐；选中态 Aa/数值变 primary-deep。
 */
@Composable
private fun FontSizeCell(
    sp: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    /** 选中态文字色 = primary-deep（对照原型 var(--cm-primary-deep) #E88A4D） */
    val aaColor = if (selected) Color(0xFFE88A4D) else Color(0xFF2D2D2D)
    val numColor = if (selected) Color(0xFFE88A4D).copy(alpha = 0.8f) else Color(0xFF9A9A9A)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            /** 去水波纹：indication = null 必须同时显式传 interactionSource 才生效 */
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row {
            Text(
                text = "Aa",
                /** 「Aa」按档位真实缩放，lineHeight 同步 = 字号避免 32sp 溢出 44dp 格 */
                style = TextStyle(fontSize = sp.sp, lineHeight = sp.sp),
                color = aaColor,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = sp.toString(),
                fontSize = 11.sp,
                color = numColor,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

/**
 * 颜色格：44dp，居中 26dp 圆形色块；「默认」项渲染为白底斜杠圆
 * （对照原型 .swatch.slashed）；选中态 = 格子 primary 边框 + primary 12% 背景。
 */
@Composable
private fun ColorSwatchCell(
    entry: TextColorEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (entry.color == null) {
            /** 「默认」项：白底斜杠圆（浅色可辨识，自带 1px 描边） */
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFD0D0D2), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 1.5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFFC4C4C6))
                        .rotate(-45f)
                )
            }
        } else {
            /** 预设色：实心圆块 + 10% 黑内描边（浅色也能看清轮廓） */
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(entry.color)
                    .border(1.dp, Color(0x1A000000), CircleShape)
            )
        }
    }
}

/** HSV 取色器内部状态（h ∈ [0,360)，s/v ∈ [0,1]）。 */
private data class Hsv(val h: Float, val s: Float, val v: Float)

/** 取色器初值（对照原型 hsv = { h:24, s:0.62, v:0.91 } ≈ #E88A4D 暖橙）。 */
private val DEFAULT_HSV = Hsv(h = 24f, s = 0.62f, v = 0.91f)

/** [Hsv] → Compose [Color]（走平台 HSVToColor，低 32 位即 ARGB，交给 `Color(Long)` 打包）。 */
private fun hsvToColor(hsv: Hsv): Color =
    Color(AndroidColor.HSVToColor(floatArrayOf(hsv.h, hsv.s, hsv.v)).toLong() and 0xFFFFFFFFL)

/** 色相条渐变停靠色（红→黄→绿→青→蓝→品红→红，对照原型 CSS 六段渐变）。 */
private val HUE_COLORS = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { h ->
    hsvToColor(Hsv(h = h, s = 1f, v = 1f))
}

/**
 * HSV 自定义取色器（对照原型：18dp 色相条 + 84dp SV 板）。
 *
 * **状态与同步**：内部自持 hsv 状态，[syncKey] 变化（选了预设色 / 自定义色被外部改变）时
 * 重置为 [initialHex] 对应色——与原型「选预设色后取色器同步到该色」一致；
 * 拖动/点按更新 hsv 并以 HEX 每帧回调 [onColorChange]（颜色即时生效）。
 *
 * **闭包新鲜度（关键实现细节）**：`pointerInput(Unit)` 只在首次组合安装手势，
 * 闭包必须通过 `var hsv by remember` 的 delegate 读取——delegate 每次读都取最新值，
 * 避免「拖动中读到旧快照」的陈旧闭包 bug。
 *
 * @param syncKey 同步键（变化时 hsv 重置为 [initialHex] 对应色）
 * @param initialHex 重置基准色（"#RRGGBB"）；null = 用 [DEFAULT_HSV]
 * @param onColorChange 取色回调（拖动过程中每帧回调）
 */
@Composable
private fun HsvPicker(
    syncKey: String,
    initialHex: String?,
    onColorChange: (String) -> Unit
) {
    val density = LocalDensity.current
    var hsv by remember(syncKey) {
        mutableStateOf(initialHex?.let(::hexToHsv) ?: DEFAULT_HSV)
    }

    /** 色相条 / SV 板实时像素尺寸（onSizeChanged 记录，供手势换算与把手定位） */
    var barWidthPx by remember { mutableStateOf(0) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val knobHalfPx = with(density) { 3.dp.toPx() }   // 色相把手 6dp 宽的一半
    val dotHalfPx = with(density) { 7.dp.toPx() }    // SV 圆点 14dp 的一半

    /** 更新 hsv 并以 HEX 回调（拖动过程中每帧回调，颜色即时生效，与原型一致） */
    fun update(new: Hsv) {
        hsv = new
        onColorChange(hsvToHex(new))
    }

    Column {
        /** ---- 色相条：18dp 高、9dp 圆角，六段渐变；白色把手按 h 定位 ---- */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.horizontalGradient(HUE_COLORS))
                .onSizeChanged { barWidthPx = it.width }
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        update(
                            hsv.copy(
                                h = (off.x / barWidthPx.coerceAtLeast(1) * 360f).coerceIn(0f, 360f)
                            )
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        update(
                            hsv.copy(
                                h = (change.position.x / barWidthPx.coerceAtLeast(1) * 360f)
                                    .coerceIn(0f, 360f)
                            )
                        )
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (barWidthPx * hsv.h / 360f).roundToInt() - knobHalfPx.roundToInt(),
                            (-3.dp.toPx()).roundToInt()
                        )
                    }
                    .size(width = 6.dp, height = 24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0x4D000000), RoundedCornerShape(3.dp))
            )
        }

        /**
         * ---- SV 板：84dp 高、8dp 圆角 ----
         * 三层背景自下而上：当前色相纯色 → 白→透明（水平，S 轴）→ 透明→黑（垂直，V 轴），
         * 对应原型 .sv-pad + .sv-white + .sv-black；圆点按 (s, 1-v) 定位。
         */
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(hsvToColor(Hsv(h = hsv.h, s = 1f, v = 1f)))
                .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp)
                )
                .onSizeChanged { padSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        update(
                            hsv.copy(
                                s = (off.x / padSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                v = (1f - off.y / padSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            )
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        update(
                            hsv.copy(
                                s = (change.position.x / padSize.width.coerceAtLeast(1))
                                    .coerceIn(0f, 1f),
                                v = (1f - change.position.y / padSize.height.coerceAtLeast(1))
                                    .coerceIn(0f, 1f)
                            )
                        )
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (hsv.s * padSize.width).roundToInt() - dotHalfPx.roundToInt(),
                            ((1f - hsv.v) * padSize.height).roundToInt() - dotHalfPx.roundToInt()
                        )
                    }
                    .size(14.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

/** Compose [Color] → "#RRGGBB"（取色回调与回显用）。 */
private fun colorToHex(color: Color): String =
    String.format(Locale.US, "#%06X", color.toArgb() and 0xFFFFFF)

/** HSV → "#RRGGBB"（走平台 HSVToColor，避免手写转换公式）。 */
private fun hsvToHex(hsv: Hsv): String =
    String.format(
        Locale.US,
        "#%06X",
        AndroidColor.HSVToColor(floatArrayOf(hsv.h, hsv.s, hsv.v)) and 0xFFFFFF
    )

/** "#RRGGBB" → [Hsv]（走平台 colorToHSV；解析失败回落 [DEFAULT_HSV]）。 */
private fun hexToHsv(hex: String): Hsv {
    return runCatching {
        val hsvOut = FloatArray(3)
        AndroidColor.colorToHSV(AndroidColor.parseColor(hex), hsvOut)
        Hsv(hsvOut[0], hsvOut[1], hsvOut[2])
    }.getOrDefault(DEFAULT_HSV)
}

/** 网格组标题（占满整行的小节标题；[hint] 可选，右对齐灰色提示）。 */
@Composable
private fun GroupTitle(text: String, hint: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hint != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
