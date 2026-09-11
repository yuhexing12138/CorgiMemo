package com.corgimemo.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.corgimemo.app.util.ClipboardImageHelper
import kotlin.math.roundToInt

/**
 * 在系统文本工具栏之外，附加「粘贴图片」浮动按钮的 TextToolbar 装饰器。
 *
 * 行为：复制图片后，在编辑区长按或点击光标倒水滴手柄弹出系统文本工具栏时，
 * 若系统剪贴板含图片，则在光标/选区下方出现「粘贴图片」选项（与复制文字后出现的
 * 粘贴选项同处编辑区），点击把图片插入当前聚焦块/行；标准复制/剪切/粘贴/全选
 * 仍由内部 [inner]（QuietTextToolbar → AndroidTextToolbar）原样呈现。
 *
 * 设计要点：不再依赖编辑页顶栏的独立粘贴按钮——粘贴入口只在「光标工具栏已弹出」
 * 这一自然时机出现，与文字粘贴的体验一致。
 *
 * @param inner 被装饰的 TextToolbar（通常为主题层提供的 QuietTextToolbar）
 * @param context 上下文（读剪贴板、弹 Snackbar）
 * @param onInsert 粘贴图片时的插入回调（灵感 insertImageAtFocused / 待办 addImageToFocusedLine）
 */
class ImagePasteTextToolbar(
    private val inner: TextToolbar,
    private val context: Context,
    private val onInsert: (String) -> Unit,
) : TextToolbar {

    /** 图片粘贴浮动按钮显隐状态：非 null 时持有光标/选区矩形 [Rect]。 */
    val imagePasteState: MutableState<Rect?> = mutableStateOf(null)

    override val status: TextToolbarStatus
        get() = inner.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        // 原样呈现系统文本工具栏（复制 / 剪切 / 粘贴 / 全选）
        inner.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
        // 剪贴板含图片时，在光标下方叠加「粘贴图片」入口；否则确保隐藏（避免残留旧状态）
        imagePasteState.value =
            if (ClipboardImageHelper.hasImageInClipboard(context)) rect else null
    }

    override fun hide() {
        inner.hide()
        imagePasteState.value = null
    }

    /** 点击「粘贴图片」：读剪贴板图片插入编辑页，并收起浮动按钮与系统工具栏。 */
    fun pasteImage() {
        ClipboardImageHelper.pasteClipboardImage(context, onInsert)
        imagePasteState.value = null
        inner.hide()
    }
}

/**
 * 随 [ImagePasteTextToolbar.imagePasteState] 显隐的「粘贴图片」浮动按钮。
 *
 * 定位（坐标系修正，v2026-09-11）：[TextToolbar.showMenu] 的 rect 是**窗口全局坐标**
 * （见 androidx.compose.ui.platform.TextToolbar 源码注释 "in global coordinates system"），
 * 而 `Popup(alignment + offset)` 的 offset 相对**锚点父容器**定位——按钮嵌套在编辑区内时
 * 会把父容器自身的窗口偏移叠加一遍，导致按钮远离光标。因此改用 [PopupPositionProvider]：
 * 其 [PopupPositionProvider.calculatePosition] 的返回值即**窗口相对坐标**，与 rect 同一坐标系。
 *
 * 跟随：Popup 实现对 calculatePosition 内的快照状态读取做了观察（snapshotStateObserver），
 * 光标/选区移动导致 imagePasteState 变化时会自动重定位，无需重建 Popup。
 *
 * 点击调用 [ImagePasteTextToolbar.pasteImage]；focusable=false 不抢焦点、不影响软键盘；
 * clippingEnabled=false 允许浮层越出窗口边界绘制（配合下方 clamp 保证可见性）。
 */
@Composable
fun ImagePasteFloatingButton(toolbar: ImagePasteTextToolbar) {
    // 组合期读取显隐状态：null 时不组合任何内容（Popup 卸载）
    val rect = toolbar.imagePasteState.value ?: return
    Popup(
        popupPositionProvider = remember(toolbar) { CursorRectPopupPositionProvider(toolbar) },
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Row(
            modifier = Modifier
                .clickable { toolbar.pasteImage() }
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = "粘贴图片",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text("粘贴图片", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 光标 rect（窗口坐标）→ 浮动按钮窗口坐标的定位器。
 *
 * 在 [PopupPositionProvider.calculatePosition] 内读取 [ImagePasteTextToolbar.imagePasteState]，
 * 由 Popup 的快照观察机制驱动光标移动后的自动重定位；返回前把位置 clamp 进窗口，
 * 保证按钮始终可见（右侧/底部贴边时收进来）。
 */
private class CursorRectPopupPositionProvider(
    private val toolbar: ImagePasteTextToolbar,
) : PopupPositionProvider {

    /**
     * 计算浮动按钮在窗口中的位置：水平对齐光标左缘，垂直落在光标/选区下缘 + 8px。
     *
     * @param anchorBounds 锚点（父容器）的窗口相对 bounds（本实现不使用，定位只依赖 rect）
     * @param windowSize 窗口尺寸（clamp 边界）
     * @param layoutDirection 锚点布局方向（左对齐逻辑下不使用）
     * @param popupContentSize 按钮实测尺寸（clamp 右/下边界用）
     * @return 按钮左上角的窗口相对坐标
     */
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val rect = toolbar.imagePasteState.value ?: return IntOffset.Zero
        val x = rect.left.roundToInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (rect.bottom + 8f).roundToInt()
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
