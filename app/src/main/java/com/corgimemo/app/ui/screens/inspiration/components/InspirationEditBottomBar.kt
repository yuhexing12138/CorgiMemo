package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * 灵感编辑页底部导航栏
 *
 * 布局结构：
 * - 上行一（可折叠）：RichTextFormatToolbar（仅当 isFormatExpanded=true 时显示）
 * - 上行二（可折叠）：FontPickerPanel 字体选择面板（仅当 isFontPanelOpen=true 时显示，
 *   高度 = 软键盘高度；展开时相机行被向下推开，见原型「工具栏/灵感编辑页字体选择面板.html」）
 * - 上行二b（可折叠）：FontSizeColorPanel 字号与颜色面板（v2026-09-04 新增，
 *   仅当 isSizeColorPanelOpen=true 时显示；与字体面板**互斥、占同一槽位、同高度**；
 *   字号/颜色点选即时生效，面板头只保留「完成」收起）
 * - 下行（始终显示）：6 个核心按钮
 *   - 📷 相机（onPhotoClick）
 *   - 🎤 麦克风（onVoiceClick）
 *   - # 标签（onTagClick）—— v2026-07-22 改造：原"位置"按钮改为"添加标签"功能
 *   - @ 关联（onMentionClick）—— v2026-07-22 改造：触发 RelationPickerBottomSheet 多选弹窗（与待办编辑页一致）
 *   - 📍 位置（onLocationClick）—— v2026-07-22 新增：独立位置按钮，使用 Icons.Default.LocationOn 图标
 *   - ⋮ 格式（onFormatToggleClick，切换上行展开/折叠）
 *
 * **交互规则**：
 * - 只有 ⋮ 按钮切换工具栏展开/折叠
 * - 字体选择按钮（工具栏 B 左侧）切换字体面板展开/收起，同时由调用方收起软键盘
 * - 字号与颜色按钮（字体按钮与 B 之间）切换字号颜色面板展开/收起，与字体面板互斥，同时由调用方收起软键盘
 * - 其他按钮的操作不影响工具栏状态
 * - 默认折叠（isFormatExpanded=false）
 *
 * @param isFormatExpanded 格式工具栏是否展开
 * @param isFontPanelOpen 字体选择面板是否展开
 * @param isSizeColorPanelOpen 字号与颜色面板是否展开（与字体面板互斥，由调用方保证）
 * @param currentCjkId 当前灵感的中文字体 id（面板回显选中态；空 = 系统默认字体）
 * @param currentLatinId 当前灵感的英文/数字字体 id（空 = 跟随中文，无选中高亮）
 * @param hasPendingChange 字体面板是否存在「已点选未应用」的改动
 *   （true = 面板头按钮显示「应用」且点击后不收起；false = 显示「完成」且点击收起）
 * @param currentFontSize 当前生效字号（sp；字号颜色面板高亮回显，未指定 = DEFAULT_BODY_SP）
 * @param currentColorIdx 当前生效的预设色下标（0 = 默认；自定义色生效时高亮让位）
 * @param customColorHex 当前生效的自定义色（"#RRGGBB"）；null = 无自定义色
 * @param richTextState 库的 RichTextState 实例（传给 RichTextFormatToolbar）
 * @param onPhotoClick 相机按钮回调
 * @param onVoiceClick 麦克风按钮回调
 * @param onTagClick 标签按钮回调（v2026-07-22 新增：原 onLocationClick 拆分而来，触发 TagPickerSheet）
 * @param onMentionClick 关联按钮回调（v2026-07-22 改造：触发 RelationPickerBottomSheet）
 * @param onLocationClick 位置按钮回调（v2026-07-22 新增：触发位置提醒弹窗）
 * @param onFormatToggleClick 格式按钮回调（切换展开/折叠）
 * @param onFontPickerClick 字体选择按钮回调（切换面板展开/收起；调用方同时收起软键盘）
 * @param onFontPanelDismiss 字体面板头按钮回调（「应用」= 应用字体不收起；「完成」= 收起面板）
 * @param onSizeColorPanelClick 字号与颜色按钮回调（切换面板展开/收起；调用方同时收起软键盘、关字体面板）
 * @param onSizeColorPanelDismiss 字号颜色面板头「完成」回调（收起面板）
 * @param onFontSizeSelect 字号点选回调（参数为档位 sp；调用方写 fontSize SpanStyle，点选即时生效）
 * @param onPresetColorSelect 预设色点选回调（参数为 TEXT_COLORS 下标；调用方写 color SpanStyle）
 * @param onCustomColorSelect 自定义取色回调（拖动每帧回调，参数 "#RRGGBB"）
 * @param onCjkFontSelect 中文字体选择回调（参数为字体 id）
 * @param onLatinFontSelect 英文/数字字体选择回调；再点已选项时回调空串表示取消（跟随中文）
 * @param onSetFontWeight 设置字重档位回调（参数为当前字体 FontEntry.boldTiers 候选档位；
 *      其中经像素探测无独立字形的档位在工具栏中置灰禁用）
 * @param onToggleItalic 斜体回调
 * @param onToggleUnderline 下划线回调
 * @param onToggleStrikethrough 删除线回调
 * @param onInsertUnorderedList 无序列表回调
 * @param onInsertOrderedList 有序列表回调
 * @param onAlignLeft 左对齐回调
 * @param onAlignCenter 居中回调
 * @param onAlignRight 右对齐回调
 * @param onInsertLink 插入链接回调
 * @param onToggleCodeSpan 代码块回调
 * @param modifier Modifier
 * @param backgroundColor 工具栏背景色
 */
@Composable
fun InspirationEditBottomBar(
    isFormatExpanded: Boolean,
    isFontPanelOpen: Boolean,
    isSizeColorPanelOpen: Boolean,
    currentCjkId: String,
    currentLatinId: String,
    hasPendingChange: Boolean,
    currentFontSize: Int,
    currentColorIdx: Int,
    customColorHex: String?,
    richTextState: RichTextState,
    onPhotoClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onTagClick: () -> Unit,
    onMentionClick: () -> Unit,
    onLocationClick: () -> Unit,
    onFormatToggleClick: () -> Unit,
    onFontPickerClick: () -> Unit,
    onFontPanelDismiss: () -> Unit,
    onSizeColorPanelClick: () -> Unit,
    onSizeColorPanelDismiss: () -> Unit,
    onFontSizeSelect: (Int) -> Unit,
    onPresetColorSelect: (Int) -> Unit,
    onCustomColorSelect: (String) -> Unit,
    onCjkFontSelect: (String) -> Unit,
    onLatinFontSelect: (String) -> Unit,
    onSetFontWeight: (Int) -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onInsertUnorderedList: () -> Unit,
    onInsertOrderedList: () -> Unit,
    /** 增加缩进回调（v2026-09-05）：透传给 [RichTextFormatToolbar] */
    onIncreaseIndent: () -> Unit = {},
    /** 减少缩进回调（v2026-09-05）：透传给 [RichTextFormatToolbar] */
    onDecreaseIndent: () -> Unit = {},
    /** 是否可增加缩进（v2026-09-05 视觉降级）：列表到顶时置灰，透传给 [RichTextFormatToolbar] */
    canIncreaseIndent: Boolean = true,
    /** 是否可减少缩进（v2026-09-05 视觉降级）：非列表行置灰，透传给 [RichTextFormatToolbar] */
    canDecreaseIndent: Boolean = true,
    onAlignLeft: () -> Unit = {},
    onAlignCenter: () -> Unit = {},
    onAlignRight: () -> Unit = {},
    onInsertLink: () -> Unit = {},
    onToggleCodeSpan: () -> Unit = {},
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    /**
     * 软键盘高度记录（字体面板高度 = 键盘高度，用户决策 2）。
     *
     * `WindowInsets.ime` 在键盘收起时归零，为保留「最近一次完整键盘高度」，
     * 只在 ime 增大时更新（取 max）：键盘弹出动画递增→记录完整值；
     * 收起动画递减→不覆盖，面板高度保持稳定。键盘从未弹出过时兜底 291dp
     * （Android 中文键盘典型高度，与原型一致）。
     */
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    var keyboardHeight by remember { mutableStateOf(291.dp) }
    if (imeBottomPx > 0) {
        val imeDp = with(density) { imeBottomPx.toDp() }
        if (imeDp > keyboardHeight) keyboardHeight = imeDp
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        shadowElevation = 4.dp,
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Column {
            /** 上行一：可折叠的格式工具栏 */
            AnimatedVisibility(
                visible = isFormatExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                RichTextFormatToolbar(
                    state = richTextState,
                    isFontPanelOpen = isFontPanelOpen,
                    onFontPickerClick = onFontPickerClick,
                    isSizeColorPanelOpen = isSizeColorPanelOpen,
                    onSizeColorPanelClick = onSizeColorPanelClick,
                    onSetFontWeight = onSetFontWeight,
                    onToggleItalic = onToggleItalic,
                    onToggleUnderline = onToggleUnderline,
                    onToggleStrikethrough = onToggleStrikethrough,
                    onInsertUnorderedList = onInsertUnorderedList,
                    onInsertOrderedList = onInsertOrderedList,
                    onIncreaseIndent = onIncreaseIndent,
                    onDecreaseIndent = onDecreaseIndent,
                    canIncreaseIndent = canIncreaseIndent,
                    canDecreaseIndent = canDecreaseIndent,
                    onAlignLeft = onAlignLeft,
                    onAlignCenter = onAlignCenter,
                    onAlignRight = onAlignRight,
                    onInsertLink = onInsertLink,
                    onToggleCodeSpan = onToggleCodeSpan
                )
            }

            /**
             * 上行二：字体选择面板（v2026-09-03 新增，用户决策后按原型落地）。
             *
             * 展开时占据「格式工具栏与相机行之间」，把相机行向下推开：
             * 键盘收起让出 ime inset + 面板占据自身高度，相机行从键盘上方
             * 下移到屏幕底部（位移 = 键盘高度），与原型交互一致。
             */
            AnimatedVisibility(
                visible = isFontPanelOpen,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                FontPickerPanel(
                    panelHeight = keyboardHeight,
                    currentCjkId = currentCjkId,
                    currentLatinId = currentLatinId,
                    hasPendingChange = hasPendingChange,
                    onCjkSelect = onCjkFontSelect,
                    onLatinSelect = onLatinFontSelect,
                    onDone = onFontPanelDismiss
                )
            }

            /**
             * 上行二b：字号与颜色面板（v2026-09-04 新增，按已审核原型落地）。
             *
             * 与字体面板**互斥、占同一槽位、同高度**（同用 keyboardHeight，互斥切换不跳动）；
             * 字号/颜色点选即时生效（SpanStyle 由调用方写入），面板头只保留「完成」收起。
             */
            AnimatedVisibility(
                visible = isSizeColorPanelOpen,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                FontSizeColorPanel(
                    panelHeight = keyboardHeight,
                    currentFontSize = currentFontSize,
                    currentColorIdx = currentColorIdx,
                    customColorHex = customColorHex,
                    onFontSizeSelect = onFontSizeSelect,
                    onPresetColorSelect = onPresetColorSelect,
                    onCustomColorSelect = onCustomColorSelect,
                    onDone = onSizeColorPanelDismiss
                )
            }

            /**
             * 下行：6 个核心按钮（始终显示）
             *
             * v2026-07-22 改造：Row 内部加 navigationBarsPadding()，
             * 让按钮自动上移避开系统手势条（与 safeAreaForEditBar 配套）。
             * Surface 容器本身紧贴屏幕底端，圆角矩形（米黄色背景）填满到屏幕底边缘。
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarButton(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "照片",
                    onClick = onPhotoClick
                )
                BottomBarButton(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "语音",
                    onClick = onVoiceClick
                )
                /**
                 * # 标签按钮（v2026-07-22 改造）：
                 * - 原"位置"按钮：图标沿用 Icons.Default.Tag，contentDescription 由"位置"改为"标签"
                 * - 回调由 onLocationClick 重命名为 onTagClick
                 * - 触发灵感独有功能 TagPickerSheet（添加/编辑标签）
                 */
                BottomBarButton(
                    imageVector = Icons.Default.Tag,
                    contentDescription = "标签",
                    onClick = onTagClick
                )
                /**
                 * @ 关联按钮（v2026-07-22 改造）：
                 * - 由 MentionTriggerPopup（单选）升级为 RelationPickerBottomSheet（多选）
                 * - 行为与待办编辑页 @ 按钮保持一致
                 */
                BottomBarButton(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = "关联",
                    onClick = onMentionClick
                )
                /**
                 * 📍 位置按钮（v2026-07-22 新增）：
                 * - 从原 # 位置按钮中独立出来，使用 Icons.Default.LocationOn
                 * - 触发位置提醒弹窗（LocationPicker + Geofence）
                 */
                BottomBarButton(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "位置",
                    onClick = onLocationClick
                )
                /** 格式按钮：高亮显示当工具栏展开时 */
                BottomBarButton(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "格式",
                    onClick = onFormatToggleClick,
                    tint = if (isFormatExpanded) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 底部栏图标按钮
 *
 * @param imageVector 图标
 * @param contentDescription 无障碍描述
 * @param onClick 点击回调
 * @param tint 图标颜色
 */
@Composable
private fun BottomBarButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
