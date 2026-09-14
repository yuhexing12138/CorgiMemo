package com.corgimemo.app.ui.components

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import com.corgimemo.app.util.ClipboardImageHelper

/**
 * 把「粘贴图片」并入系统文本工具栏「粘贴」动作的 [TextToolbar] 装饰器。
 *
 * 行为（与粘贴文字完全同一逻辑，无任何额外浮层）：长按/点击光标倒水滴手柄弹出
 * 系统文本工具栏时，若剪贴板含图片，则把工具栏的「粘贴」项接管为插入图片；
 * 否则原样透传文本粘贴。复制 / 剪切 / 全选一律透传。
 *
 * 自动收起：系统实现（TextActionModeCallback.onActionItemClicked）在执行菜单项
 * 回调后自行 `mode?.finish()`，与粘贴文字的收起行为一致，本类无需干预。
 *
 * @param inner 被装饰的 TextToolbar（通常为主题层提供的 QuietTextToolbar）
 * @param context 上下文（检测剪贴板、弹 Snackbar）
 * @param onInsert 粘贴图片时的插入回调（灵感 insertImageAtFocused / 待办 addImageToFocusedLine）
 */
class ImagePasteTextToolbar(
    private val inner: TextToolbar,
    private val context: Context,
    private val onInsert: (String) -> Unit,
) : TextToolbar {

    override val status: TextToolbarStatus
        get() = inner.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        // 剪贴板含图片 → 「粘贴」接管为插入图片（贴完由系统自动收起工具栏）；
        // 无图片 → 原样透传，文本粘贴行为不变。回调保持非 null，「粘贴」项照常显示。
        val pasteAction: (() -> Unit)? =
            if (ClipboardImageHelper.hasImageInClipboard(context)) {
                { ClipboardImageHelper.pasteClipboardImage(context, onInsert) }
            } else {
                onPasteRequested
            }
        inner.showMenu(rect, onCopyRequested, pasteAction, onCutRequested, onSelectAllRequested)
    }

    override fun hide() {
        inner.hide()
    }
}
