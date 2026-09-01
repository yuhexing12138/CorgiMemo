package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.ui.components.InlineImagePreview
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.mohamedrejeb.richeditor.ui.UndoBehavior
import sh.calvin.reorderable.ReorderableItem

// ==================== 零宽字符（用于软键盘空块退格检测） ====================

/**
 * v2026-09-01 新增（软键盘空块退格修复）：
 *
 * **问题**：软键盘在空 Text 块上按退格，IME 调用 `deleteSurroundingText(1, 0)`，
 * 但 text/selection 都不变 → snapshotFlow 不发射 → observer 触发不到
 * `onBackspaceAtStart`。硬键盘走 `onPreviewKeyEvent` 不受此限。
 *
 * **解法**：逻辑空块预置一个零宽字符 U+200B，让退格能产生一次
 * "ZWSP → \"\" 状态变化"供 observer 捕获，再走原 onBackspaceAtStart 路径。
 *
 * **状态不变量**（由 [BodyBlocksEditor] 的 LaunchedEffect 强制维持）：
 * - 逻辑空块：`state.text == ZWSP`，光标在 (1, 1)
 * - 逻辑非空块：`state.text == "<content>"`（不含 ZWSP；不在运行期强制追加）
 * - 任何 `setText(ZWSP)` 都会丢失内容，所以只能对真正空的（""）调用
 *
 * **输出剥**：toMarkdown / plainText / 高度 / 占位符判断都要看"有效文本"
 * （剥掉 ZWSP 后再判断），不让 ZWSP 泄漏到 markdown/UI 文本。
 */
private const val ZWSP = "\u200B"
private fun effectiveText(text: String): String =
    text.filterNot { it == ZWSP[0] || it == IMAGE_PLACEHOLDER_CHAR }
private fun isEffectivelyEmpty(text: String): Boolean = effectiveText(text).isEmpty()
private fun isEffectivelyEmpty(state: RichTextState): Boolean =
    isEffectivelyEmpty(state.annotatedString.text)

/**
 * 库图片占位符：image span 在 raw text 中恰好占一个 U+FFFD 字符（与库
 * `utils/InlineContent.kt` 的 `InlineContentPlaceholder` 同值——该常量是 internal，
 * app 模块不可见，这里按值对齐）。
 *
 * [effectiveText] 剥该字符，防止边缘路径（如粘贴含图 markdown）让占位符
 * 泄漏成可见文本。Text 块正常不持有 image span（插图即拆块）。
 */
private const val IMAGE_PLACEHOLDER_CHAR = '\uFFFD'

/**
 * 路线 4（v2026-09-01）：块级图片编辑器（图文交错）
 *
 * 图片从正文内联（RichSpanStyle.Image + 覆盖层绘制）改为**块级**：
 * 整篇 markdown 被切分为 [Text] / [Image] 交错块列表，每个块可独立拖拽；
 * 每个 Text 块是一个独立 [RichTextEditor]，图片块是普通 Composable。
 *
 * 块的粒度（经用户确认）：
 * - **每个段落一个块**：加载时按 `\n\n`（markdown 段落边界）切分；
 * - **Enter 拆块**：编辑中按回车，块在光标处拆成两个块（软键盘靠变更检测，
 *   硬键盘靠 onPreviewKeyEvent 拦截），焦点落到新块行首；
 * - 运行期任何块内出现 `\n`（粘贴多行等）都会被自动归一化拆块；
 * - 块内不再保留 `\n`（加载时单 `\n` 的软换行保留在块内，运行期产生的都拆块）。
 *
 * **单一真相源仍是整篇 Markdown**：
 * - 加载：markdown → [parseMarkdownSegments] → 块列表
 * - 保存：块列表 → [BodyBlocksController.toMarkdown] → markdown
 * - 语音 / 话题 / 关联 token 仍内联在 Text 块内部，行为不变（用户要求）
 *
 * 数据库（content_blocks 表）与共享模型（ContentBlock）零改动：
 * content_blocks 继续由保存链路 saveInlineMediaBlocks 从 markdown 反解析维护。
 */

// ==================== 块模型（UI 层，不影响共享的 ContentBlock） ====================

/** 生成稳定块 id：创建 / 加载 / 拆分时分配一次，此后不随编辑变化 */
fun newBodyBlockId(): String = java.util.UUID.randomUUID().toString()

/** 编辑器块：Text 承载富文本状态（含内联语音/话题 token），Image 是块级图片 */
sealed class BodyBlock {
    abstract val id: String

    class Text(
        override val id: String,
        val state: RichTextState,
        val focusRequester: FocusRequester = FocusRequester(),
    ) : BodyBlock()

    class Image(
        override val id: String,
        val path: String,
    ) : BodyBlock()
}

// ==================== Markdown ↔ 块列表 ====================

internal sealed class MdSegment {
    data class TextSeg(val md: String) : MdSegment()
    data class ImageSeg(val path: String) : MdSegment()
}

/**
 * 把整篇 markdown 按图片语法切成段。
 *
 * 只识别标准图片语法 `![任意alt](路径)`；语音/话题/关联是链接语法（无 `!`），
 * 不会被切开，留在所属 Text 段内（语音保持内联的关键）。
 */
internal fun parseMarkdownSegments(markdown: String): List<MdSegment> {
    val result = mutableListOf<MdSegment>()
    val regex = Regex("""!\[[^\]]*\]\(([^)]+)\)""")
    var cursor = 0
    for (match in regex.findAll(markdown)) {
        if (match.range.first > cursor) {
            result += MdSegment.TextSeg(markdown.substring(cursor, match.range.first))
        }
        val path = match.groupValues[1].trim()
        if (path.isNotBlank()) result += MdSegment.ImageSeg(path)
        cursor = match.range.last + 1
    }
    if (cursor < markdown.length) {
        result += MdSegment.TextSeg(markdown.substring(cursor))
    }
    return result
}

// ==================== 控制器 ====================

/**
 * 块列表控制器：持有块列表与全部块级操作，UI 层通过它读写。
 *
 * 不变量：**列表中至少存在一个 Text 块**（兼容层 focusedOrFirstTextState 依赖）；
 * 列表末尾**尽量**是一个 Text 块（保证图片后仍可继续输入文字）。
 */
class BodyBlocksController(
    /** 给每个新建 Text 块的 state 注册 trigger（hashtag/mention/voice），由页面注入 */
    private val registerTriggers: (RichTextState) -> Unit,
) {
    /** 块列表（快照状态，增删自动触发重组） */
    val blocks = mutableStateListOf<BodyBlock>()

    /** 当前聚焦的文本块 id（null = 尚未聚焦过） */
    private var focusedBlockId by mutableStateOf<String?>(null)

    /** 两步删除：当前高亮的块 id（仅图片块会被高亮） */
    var highlightedBlockId by mutableStateOf<String?>(null)
        private set

    /** 请求聚焦的块 id 与光标位置（由块 Composable 消费后置空） */
    internal var pendingFocusId by mutableStateOf<String?>(null)
    internal var pendingFocusOffset by mutableStateOf(0)

    /** 任何块内容/结构变化后的回调（页面用它同步 ViewModel） */
    var onDocChanged: (() -> Unit)? = null

    init {
        /** 构造时先放一个空 Text 块，保证兼容层任何时刻都能拿到非空状态 */
        blocks += createTextBlock("")
    }

    // ---------- 构建块 ----------

    fun createTextBlock(markdown: String): BodyBlock.Text {
        val state = RichTextState()
        /** v2026-09-01 两类互斥配套：禁用库默认 500ms 合并窗口，每个字符都是独立 undo group，
         *  否则用户连打"abc"在 500ms 内会被合并成 1 个 group，undo 一次就把整段全删了——不是逐字回退 */
        state.history.coalesceWindowMs = 0L
        /** v2026-09-01 关闭编辑态内联图片渲染（防御性）：插图已改为拆块
         *  （Text 块 state 不持有 image span），正常路径覆盖层无图可画；
         *  关掉开关可兜底边缘路径（如粘贴含图 markdown 进块内），避免覆盖层
         *  在 Text 块内画出真图与 Image 块重复。 */
        state.inlineImageRendering = false
        registerTriggers(state)
        if (markdown.isNotEmpty()) {
            state.setMarkdown(markdown)
        } else {
            /** 空块预置 ZWSP + 光标 (1, 1)：让软键盘退格能产生状态变化被 observer 捕获 */
            state.setText(ZWSP)
            state.selection = TextRange(1)
        }
        return BodyBlock.Text(newBodyBlockId(), state)
    }

    // ---------- 加载 / 导出 ----------

    /** 用整篇 markdown 重建块列表（初始化与历史恢复共用） */
    fun initialize(markdown: String) {
        blocks.clear()
        parseMarkdownSegments(markdown).forEach { seg ->
            when (seg) {
                is MdSegment.TextSeg ->
                    // Text 段按 \n\n 再拆成段落块；单 \n 的软换行保留在块内；
                    // 空白段丢弃（图片两侧的换行、空段落），整篇为空时由 ensureTextBlock 兜底
                    seg.md.split("\n\n").forEach { para ->
                        val trimmed = para.trim('\n')
                        if (trimmed.isNotBlank()) blocks += createTextBlock(trimmed)
                    }
                is MdSegment.ImageSeg -> blocks += BodyBlock.Image(newBodyBlockId(), seg.path)
            }
        }
        ensureTextBlock()
        focusedBlockId = null
        highlightedBlockId = null
        onDocChanged?.invoke()
    }

    /**
     * 组装整篇 markdown：Text 用 toMarkdown()，Image 还原为 `![](path)`，块间以空行连接。
     *
     * 加载时按 \n\n 切段 + 此处按 \n\n 拼接，保证已有文档往返一致。
     *
     * **v2026-09-01 串联时间线改造**：Text 块 state 可能持有 image span
     * （插图不再拆块，图片渲染交给紧跟的 Image 标记块）——`state.toMarkdown()`
     * 会把这些 image 也输出成 `![](path)`，与 Image 块的输出**重复**。
     * 这里对 Text 块输出做"剥 image 段"处理：`parseMarkdownSegments` 切段后
     * 只保留 TextSeg，段间以 `\n\n` 拼接（image 原本独占段落，语义等价）。
     */
    fun toMarkdown(): String =
        blocks.mapNotNull { block ->
            when (block) {
                is BodyBlock.Text -> {
                    /** 剥掉空块预置的 ZWSP，保证 markdown 往返不带噪音 */
                    val raw = block.state.toMarkdown().replace(ZWSP, "")
                    parseMarkdownSegments(raw)
                        .filterIsInstance<MdSegment.TextSeg>()
                        .map { it.md.trim('\n') }
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                        .ifEmpty { null }
                }
                is BodyBlock.Image -> "![](${block.path})"
            }
        }.filter { it.isNotBlank() }
            .joinToString("\n\n")

    /** 纯文本（字数统计 / 复制全文 / 同步 _content 用） */
    fun plainText(): String =
        blocks.filterIsInstance<BodyBlock.Text>()
            .joinToString("\n") { effectiveText(it.state.annotatedString.text) }

    // ---------- 兼容层：原单编辑器状态 → 聚焦块状态 ----------

    /**
     * 返回"当前应被工具栏 / 触发弹窗 / 语音插入作用"的富文本状态：
     * 聚焦的 Text 块优先，否则回退第一个 Text 块。不变量保证结果非空。
     */
    fun focusedOrFirstTextState(): RichTextState {
        focusedBlockId?.let { id ->
            val focused = blocks.firstOrNull { it.id == id }
            if (focused is BodyBlock.Text) return focused.state
        }
        return (blocks.firstOrNull { it is BodyBlock.Text } as BodyBlock.Text).state
    }

    // ---------- 图片插入 ----------

    /**
     * 在当前聚焦块的光标处插入图片并拆块。
     *
     * - 聚焦块是 Text → 调用库专用的 [RichTextState.insertImage]，
     *   它手工构造一个带 ▢ 占位的 Image 段，并把原段落拆成 [前半段, Image段, 后半段]。
     *   之后用整篇 markdown 重解析，得到 [Text, Image, Text] 三块序列；
     * - 聚焦块是 Image 或从未聚焦 → 插到列表末尾（末尾 Text 块之前）。
     * 插入后焦点移到图片后的第一个 Text 块。
     *
     * **不要用 `insertMarkdownAfterSelection("![](path)")`**：
     * 库的 markdown encoder 对空 alt 的 `![](path)` 走 `onText("")` 后立即
     * early-return，跳过 `text = ▢` 的赋值，导致 Image span text 为空、
     * toMarkdown → parseMarkdownSegments 拆块错位，表现为 "图片后" 丢失 "后"字、
     * 出现 ▢ 占位字符。
     */
    fun insertImageAtFocused(path: String) {
        insertOneImageInternal(path)
    }

    /**
     * 批量插入（多选相册一次确认）= **一个撤销单位**：只压一次时间线快照，
     * 整批连续拆块插入；撤销一次全部回退。
     */
    fun insertImagesAtFocused(paths: List<String>) {
        if (paths.isEmpty()) return
        pushTimelineSnapshot()
        suppressDocChanged = true
        /** 操作引发的 observer 文本差分不压栈（统一时间线只记这一个用户动作） */
        val was = suppressTimeline
        suppressTimeline = true
        try {
            paths.forEach { insertOneImageInternal(path = it, pushSnapshot = false) }
        } finally {
            suppressTimeline = was
            suppressDocChanged = false
        }
        onDocChanged?.invoke()
    }

    /**
     * 内部：在聚焦 Text 块的**光标处**插入图片并拆块——
     * 光标前后的文字各自成段：`[前半Text, Image, 后半Text]`。
     *
     * **v2026-09-01 自实现拆块**：不调 [RichTextState.insertImage]（其内部段计数
     *  与 selection 重置在某些路径下产生不确定输出），而是直接读 [RichTextState.selection]、
     *  用 [RichTextState.toMarkdown]（[TextRange] 重载）按光标精确拆段。
     * 内部 [RichTextState.extractRangeState] 按段落裁剪 spans，结果完全可预测且稳定。
     *
     * Text 块 state 不残留 image span（不调 [RichTextState.insertImage] 也就没有 ▢ 占位符），
     * 无空隙，真实图片完全由 Image 块渲染。
     *
     * 插入后：焦点移到图片后的第一个 Text 块 offset 0，并同步 [focusedBlockId]
     * （批量插入时下一张图以此为锚点，保证多张图连续排列在光标处）。
     *
     * @param pushSnapshot 单张调用为 true（压撤销快照）；批量插入由
     *   [insertImagesAtFocused] 统一压一次，传 false。
     */
    private fun insertOneImageInternal(path: String, pushSnapshot: Boolean = true) {
        val focusedIdx = focusedBlockId
            ?.let { id -> blocks.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }

        if (focusedIdx == null) {
            insertImageAtEnd(path)
            return
        }

        when (val focused = blocks[focusedIdx]) {
            is BodyBlock.Image -> insertImageAtEnd(path)
            is BodyBlock.Text -> {
                if (pushSnapshot) pushTimelineSnapshot()
                /** 操作期间的 state 变化不作为"用户文本编辑"压栈 */
                val was = suppressTimeline
                suppressTimeline = true
                try {
                    val state = focused.state
                    val rawText = state.annotatedString.text
                    val cursor = state.selection.start.coerceIn(0, rawText.length)

                    /** v2026-09-01 自实现拆块：完全绕开 [RichTextState.insertImage]，
                     *  按真实 [state.selection] 用 [RichTextState.toMarkdown] 拆段。
                     *
                     *  原路径（state.insertImage + replaceBlockWithParsed）**偶尔**产生错误
                     *  拆分（如 [Image(A), Image(B), Text("12")]）——根因是库内
                     *  `updateRichParagraphList` 会基于 `beforeTextLength` 重置
                     *  `textFieldValue.selection`，而 `beforeTextLength` 在某些段落计数
                     *  路径下计算异常，导致切段后 selection 落在意外位置；批量第二张图
                     *  立即读到的 cursor 就是错的。
                     *
                     *  新路径直接读 `state.selection`（用户实际光标），调用
                     *  [RichTextState.toMarkdown]（[TextRange] 重载）按光标精确拆段，
                     *  内部 `extractRangeState` 按段落裁剪 spans——结果完全可预测且稳定，
                     *  与库内段落计数无关。
                     *  - cursor=0：[Image, Text("12")]（用户在块首点击 → 图片插在块前）
                     *  - cursor=1（"1"和"2"字符间）：[Text("1"), Image, Text("2")] ✓
                     *  - cursor=末尾：[Text("12"), Image]
                     */
                    val beforeMd = if (cursor > 0) state.toMarkdown(TextRange(0, cursor)) else ""
                    val afterMd = if (cursor < rawText.length)
                        state.toMarkdown(TextRange(cursor, rawText.length)) else ""

                    val newBlocks = mutableListOf<BodyBlock>()
                    if (beforeMd.isNotBlank()) newBlocks += createTextBlock(beforeMd)
                    newBlocks += BodyBlock.Image(newBodyBlockId(), path)
                    if (afterMd.isNotBlank()) newBlocks += createTextBlock(afterMd)

                    blocks.removeAt(focusedIdx)
                    blocks.addAll(focusedIdx, newBlocks)
                    ensureTextBlock(atEnd = true)

                    /** 焦点/聚焦块同步到图片后的第一个 Text 块（批量插入的锚点）。
                     *  关键：必须**立即**同步 [state.selection]——pendingFocusId 由
                     *  LaunchedEffect 异步消费，批量循环同步执行，下一张图立即读
                     *  `state.selection` 仍可能拿到上一轮末尾 → 图片错位。 */
                    val imgPos = focusedIdx + newBlocks.indexOfFirst { it is BodyBlock.Image }
                    val nextText = blocks.drop(imgPos + 1)
                        .firstOrNull { it is BodyBlock.Text } as? BodyBlock.Text
                    if (nextText != null) {
                        focusedBlockId = nextText.id
                        pendingFocusId = nextText.id
                        pendingFocusOffset = 0
                        nextText.state.selection = TextRange(0)
                    }
                } finally {
                    suppressTimeline = was
                }
                if (!suppressDocChanged) onDocChanged?.invoke()
            }
        }
    }

    /**
     * 批量插入期间抑制 [onDocChanged]，由 [insertImagesAtFocused] 在末尾统一调一次。
     * （不然每张图都会触发一次 ViewModel.setContent/setContentFormat + 一次重组。）
     */
    private var suppressDocChanged: Boolean = false

    private fun insertImageAtEnd(path: String) {
        ensureTextBlock(atEnd = true)
        blocks.add(blocks.size - 1, BodyBlock.Image(newBodyBlockId(), path))
        pendingFocusId = (blocks.last() as BodyBlock.Text).id
        pendingFocusOffset = 0
        if (!suppressDocChanged) onDocChanged?.invoke()
    }

    /**
     * 在 [newBlocks]（刚替换进列表的块序列）中找到 [cursorOffset] 落在的 Text 块并请求聚焦。
     *
     * **v2026-09-01 修复（光标落在"图片"和"后"之间）**：
     * [cursorOffset] 来自库 raw text 坐标系——相邻段落之间有 1 个连接空格
     * （`updateRichParagraphList` 的 `append(' ')`），且图片占位符 ▢ 占 1 字符。
     * 旧实现 `consumed` 只累加块自身长度，与 raw 坐标系差"段间空格"，插图后
     * 光标被算到后半块 offset 2（"图片"和"后"之间）而非 0。
     * 修复：每跨过一个块，`consumed` 额外 +1（该块与其后块的段间连接空格）；
     * 求出的偏移再 `coerceIn(0, len)` 兜住空块等边界（尾插时光标落空 Text 块
     * 会算出 -1，coerce 回 0）。
     */
    private fun focusAtOffset(newBlocks: List<BodyBlock>, cursorOffset: Int) {
        var consumed = 0
        for (b in newBlocks) {
            val len = when (b) {
                is BodyBlock.Text -> b.state.annotatedString.text.length
                is BodyBlock.Image -> 1
            }
            if (b is BodyBlock.Text && cursorOffset < consumed + len) {
                pendingFocusId = b.id
                pendingFocusOffset = (cursorOffset - consumed).coerceIn(0, len)
                return
            }
            /** 跨块：下一块前有一个段间连接空格（raw text 坐标系） */
            consumed += len + 1
        }
        (newBlocks.lastOrNull() as? BodyBlock.Text)?.let {
            pendingFocusId = it.id
            pendingFocusOffset = it.state.annotatedString.text.length
        }
    }

    // ---------- 语音（保持内联，行为不变） ----------

    /** 在聚焦块光标处插入语音 token markdown */
    fun insertVoiceToken(markdownLink: String) {
        focusedOrFirstTextState().insertMarkdownAfterSelection(markdownLink)
        onDocChanged?.invoke()
    }

    /** 迁移用：把一段媒体 markdown 追加到末尾 Text 块（按 path 去重由调用方负责） */
    fun appendMediaMarkdown(markdown: String) {
        ensureTextBlock(atEnd = true)
        val last = blocks.last() as BodyBlock.Text
        val existing = last.state.toMarkdown()
        last.state.setMarkdown(if (existing.isBlank()) markdown else "$existing\n\n$markdown")
        onDocChanged?.invoke()
    }

    // ---------- Enter 拆块 ----------

    /**
     * 硬键盘回车（尚未插入 \n）：在光标处拆成两个块。
     * 用库的 toMarkdown(range)（纯文本坐标）做精确的"光标 → markdown"映射。
     */
    fun splitTextBlockAtCursor(block: BodyBlock.Text) {
        val cursor = block.state.selection.start
        splitTextBlock(block, cursor, cursor)
    }

    /**
     * 把块拆为 [0, beforeEnd) 与 [afterStart, len) 两段 markdown。
     * beforeEnd / afterStart 都是纯文本偏移；afterStart 通常等于 beforeEnd
     * （硬回车未插入换行）或 beforeEnd + 1（软键盘已插入 `\n`，跳过它）。
     */
    private fun splitTextBlock(block: BodyBlock.Text, beforeEnd: Int, afterStart: Int) {
        pushTimelineSnapshot()
        val idx = blocks.indexOfFirst { it.id == block.id }
        if (idx < 0) return
        val text = block.state.annotatedString.text
        val beforeMd = if (beforeEnd > 0) block.state.toMarkdown(TextRange(0, beforeEnd)) else ""
        val afterMd = if (afterStart < text.length) block.state.toMarkdown(TextRange(afterStart, text.length)) else ""

        /** 操作引发的 state 变化不作为"用户文本编辑"压栈（快照已在上面压过） */
        val was = suppressTimeline
        suppressTimeline = true
        try {
            block.state.setMarkdown(beforeMd)
            val newBlock = createTextBlock(afterMd)
            newBlock.state.selection = TextRange(0)
            blocks.add(idx + 1, newBlock)
        } finally {
            suppressTimeline = was
        }
        pendingFocusId = newBlockId(blocks, idx + 1)
        pendingFocusOffset = 0
        onDocChanged?.invoke()
    }

    /** 取 [index] 处块 id（拆块后焦点定位用，越界返回 null） */
    private fun newBlockId(blocks: List<BodyBlock>, index: Int): String? =
        blocks.getOrNull(index)?.id

    /**
     * 归一化：块内出现 `\n`（软键盘回车 / 粘贴多行）时按行拆成段落块。
     * 末尾连续空行保留为一个空块（回车在段尾 = 新起一段）；其余空行丢弃。
     * 焦点落到原光标所在的新块，偏移按行内位置换算。
     *
     * **不压撤销快照**：调用方是 observer（检测到 `\n`），observer 在调用前已把
     * "变化前"快照压入统一时间线——那才是正确的撤销目标。这里若再压会压到
     * "含 \n 的单块中间态"，撤销恢复它后 observer 又检测到 `\n` 又拆块，死循环。
     */
    fun normalizeBlockParagraphs(block: BodyBlock.Text) {
        val idx = blocks.indexOfFirst { it.id == block.id }
        if (idx < 0) return
        val text = block.state.annotatedString.text
        if (!text.contains('\n')) return

        val cursor = block.state.selection.start.coerceIn(0, text.length)

        /** 行区间 [start, end)；end 为 exclusive */
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = 0
        for (i in text.indices) {
            if (text[i] == '\n') {
                ranges += start to i
                start = i + 1
            }
        }
        ranges += start to text.length

        /** 最后一个非空行的下标 */
        val lastContent = ranges.indexOfLast { (s, e) -> e > s && text.substring(s, e).isNotBlank() }
        if (lastContent < 0) {
            /** 整块都是空白（如全选删除后残留换行）→ 退化为单个空块 */
            val empty = createTextBlock("")
            blocks[idx] = empty
            pendingFocusId = empty.id
            pendingFocusOffset = 0
            onDocChanged?.invoke()
            return
        }

        val newBlocks = mutableListOf<BodyBlock>()
        for (i in 0..lastContent) {
            val (s, e) = ranges[i]
            if (e > s) {
                val md = block.state.toMarkdown(TextRange(s, e))
                if (md.isNotBlank()) newBlocks += createTextBlock(md)
                /** 行内空段（双回车产生的中间空行）丢弃：markdown 渲染中 \n\n 只是段落分隔，不是可见空行 */
            }
        }
        /** 末尾有连续空行 = 回车在段尾 → 保留一个空块作为新段落 */
        val trailingBlanks = ranges.size - 1 - lastContent
        if (trailingBlanks > 0) newBlocks += createTextBlock("")
        if (newBlocks.isEmpty()) newBlocks += createTextBlock("")

        blocks.removeAt(idx)
        blocks.addAll(idx, newBlocks)
        ensureTextBlock(atEnd = true)

        /** 焦点：原光标所在行对应的新块 */
        var consumed = 0
        var placed = false
        for (b in newBlocks) {
            if (b !is BodyBlock.Text) {
                consumed += 1
                continue
            }
            val len = b.state.annotatedString.text.length
            if (cursor <= consumed + len) {
                pendingFocusId = b.id
                pendingFocusOffset = (cursor - consumed).coerceIn(0, len)
                placed = true
                break
            }
            consumed += len + 1
        }
        if (!placed) {
            (newBlocks.lastOrNull() as? BodyBlock.Text)?.let {
                pendingFocusId = it.id
                pendingFocusOffset = it.state.annotatedString.text.length
            }
        }
        onDocChanged?.invoke()
    }

    // ---------- 统一时间线撤销 / 重做 ----------

    /**
     * v2026-09-01 第四次修订（统一时间线）：
     *
     * 快照仍是结构化 [BlockSnapshot]（entries + 焦点索引 + 有效坐标光标，保留空块），
     * 但栈的语义升级：**文本编辑与结构变更共用同一个撤销栈，天然按时间倒序串联**——
     * - 每次用户文本变化（打字/删除/粘贴）由 BlockTextItem observer 压"变化前"快照
     *   （[pushTextUndoSnapshot]），撤销一步回一个字符；
     * - 每次结构变更（插图拆块 / Enter 拆块 / 合并 / 重排 / 删图）由操作入口压快照
     *   （[pushTimelineSnapshot]）。
     * 撤销 = 无脑 pop 栈顶恢复，"打字 → 插图 → 再打字"按倒序逐级回退，
     * 不再依赖库 `state.history` 做跨块串联，也不需要"两类互斥"。
     *
     * **去重**：结构操作先压快照、操作本身又引发 observer 压"变化前"快照，
     * 两者内容相同 → [BlockSnapshot] data class equals 去重，不会重复入栈。
     *
     * **suppressTimeline**：undo/redo 的恢复动作会改块内容，observer 若不拦截会把
     * "恢复前状态"误压回撤销栈（多一步空撤销），恢复期间置 true。
     */
    private sealed class BlockSnapshotEntry {
        data class TextEntry(val effectiveText: String) : BlockSnapshotEntry()
        data class ImageEntry(val path: String) : BlockSnapshotEntry()
    }

    private data class BlockSnapshot(
        val entries: List<BlockSnapshotEntry>,
        val focusedEntryIndex: Int,
        val cursorOffset: Int,
    )

    private val undoTimeline = ArrayDeque<BlockSnapshot>()
    private val redoTimeline = ArrayDeque<BlockSnapshot>()

    /** 逐字快照耗栈快（每字符一条），深度放宽到 200 */
    private val maxTimelineDepth = 200

    /** 恢复操作期间为 true：observer 不压栈（否则把恢复前状态误压回撤销栈） */
    internal var suppressTimeline = false
        private set

    var canUndoTimeline by mutableStateOf(false)
        private set
    var canRedoTimeline by mutableStateOf(false)
        private set

    /** 结构变更前调用：压入当前块结构 + 光标（与栈顶完全相同则跳过） */
    private fun pushTimelineSnapshot() {
        val snapshot = captureCurrentSnapshot()
        if (undoTimeline.lastOrNull() != snapshot) {
            undoTimeline.addLast(snapshot)
            if (undoTimeline.size > maxTimelineDepth) undoTimeline.removeFirst()
        }
        redoTimeline.clear()
        canUndoTimeline = undoTimeline.isNotEmpty()
        canRedoTimeline = false
    }

    /**
     * 用户文本变化时由 BlockTextItem observer 调用：压"变化前"快照。
     *
     * [textBefore]/[selectionBefore] 是 observer 差分保存的变化前本块文本与光标；
     * 其他块未受本次编辑影响，直接捕获当前状态即可。
     * 本块不重建对象，只在快照数据里用 [BlockSnapshotEntry.TextEntry] 表达。
     *
     * 与栈顶完全相同则跳过（结构操作已压过同一份"变化前"快照）。
     */
    internal fun pushTextUndoSnapshot(blockId: String, textBefore: String, selectionBefore: TextRange) {
        if (suppressTimeline) return
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return
        val entries = blocks.mapIndexed { i, block ->
            when (block) {
                is BodyBlock.Text ->
                    if (i == idx) BlockSnapshotEntry.TextEntry(effectiveText(textBefore))
                    else BlockSnapshotEntry.TextEntry(effectiveText(block.state.annotatedString.text))
                is BodyBlock.Image -> BlockSnapshotEntry.ImageEntry(block.path)
            }
        }
        /** 变化前光标（有效文本坐标，与 captureCurrentSnapshot 同坐标系） */
        val cursorEffective = effectiveText(
            textBefore.substring(0, selectionBefore.start.coerceIn(0, textBefore.length))
        ).length
        val snapshot = BlockSnapshot(entries, idx, cursorEffective)
        if (undoTimeline.lastOrNull() != snapshot) {
            undoTimeline.addLast(snapshot)
            if (undoTimeline.size > maxTimelineDepth) undoTimeline.removeFirst()
        }
        redoTimeline.clear()
        canUndoTimeline = undoTimeline.isNotEmpty()
        canRedoTimeline = false
    }

    /** 捕获当前状态（块结构 + 聚焦 entry + 光标）作为快照 */
    private fun captureCurrentSnapshot(): BlockSnapshot {
        val entries = blocks.map { block ->
            when (block) {
                is BodyBlock.Text -> BlockSnapshotEntry.TextEntry(
                    effectiveText(block.state.annotatedString.text)
                )
                is BodyBlock.Image -> BlockSnapshotEntry.ImageEntry(block.path)
            }
        }
        val focusedIdx = blocks.indexOfFirst { it.id == focusedBlockId }
        val (focusedEntryIdx, cursorOffset) = if (focusedIdx >= 0) {
            val focused = blocks[focusedIdx]
            if (focused is BodyBlock.Text) {
                val rawText = focused.state.annotatedString.text
                val rawCursor = focused.state.selection.start.coerceIn(0, rawText.length)
                /** 光标从 raw 坐标映射到有效坐标（剥 ZWSP） */
                val effectiveCursor = effectiveText(rawText.substring(0, rawCursor)).length
                focusedIdx to effectiveCursor
            } else {
                /** 聚焦在 Image 上 → 用首 Text 块、offset 0 兜底 */
                val firstTextIdx = blocks.indexOfFirst { it is BodyBlock.Text }
                if (firstTextIdx >= 0) firstTextIdx to 0 else -1 to -1
            }
        } else {
            /** 无聚焦块 → 用首 Text 块、offset 0 */
            val firstTextIdx = blocks.indexOfFirst { it is BodyBlock.Text }
            if (firstTextIdx >= 0) firstTextIdx to 0 else -1 to -1
        }
        return BlockSnapshot(entries, focusedEntryIdx, cursorOffset)
    }

    /** 按 [snapshot.entries] 精确重建块列表（**保留空 Text 块**，markdown 做不到这点） */
    private fun restoreFromSnapshot(snapshot: BlockSnapshot) {
        blocks.clear()
        snapshot.entries.forEach { entry ->
            when (entry) {
                is BlockSnapshotEntry.TextEntry -> {
                    /** 空 effectiveText = 空 Text 块（createTextBlock("") 走 ZWSP 分支）；
                     *  非空走 setMarkdown 分支（state 不带 ZWSP，与原一致） */
                    blocks += if (entry.effectiveText.isEmpty()) {
                        createTextBlock("")
                    } else {
                        createTextBlock(entry.effectiveText)
                    }
                }
                is BlockSnapshotEntry.ImageEntry -> {
                    blocks += BodyBlock.Image(newBodyBlockId(), entry.path)
                }
            }
        }
        ensureTextBlock()
        focusedBlockId = null
        highlightedBlockId = null
        onDocChanged?.invoke()
    }

    /** 统一时间线撤销：按快照重建块列表，并恢复光标 */
    fun undoTimeline(): Boolean {
        val snapshot = undoTimeline.removeLastOrNull() ?: return false
        redoTimeline.addLast(captureCurrentSnapshot())
        suppressTimeline = true
        try {
            restoreFromSnapshot(snapshot)
            restoreCursor(snapshot)
        } finally {
            suppressTimeline = false
        }
        canUndoTimeline = undoTimeline.isNotEmpty()
        canRedoTimeline = true
        return true
    }

    /** 统一时间线重做 */
    fun redoTimeline(): Boolean {
        val snapshot = redoTimeline.removeLastOrNull() ?: return false
        undoTimeline.addLast(captureCurrentSnapshot())
        suppressTimeline = true
        try {
            restoreFromSnapshot(snapshot)
            restoreCursor(snapshot)
        } finally {
            suppressTimeline = false
        }
        canUndoTimeline = true
        canRedoTimeline = redoTimeline.isNotEmpty()
        return true
    }

    /**
     * 在重建后的块列表里找到 [snapshot.focusedEntryIndex] 处的 Text 块，
     * 把光标设回 [snapshot.cursorOffset]。索引越界或该 entry 不是 Text → 兜底首 Text 块 + 0。
     */
    private fun restoreCursor(snapshot: BlockSnapshot) {
        val idx = snapshot.focusedEntryIndex
        if (idx < 0 || idx >= blocks.size) {
            focusFirstTextBlock()
            return
        }
        val target = blocks[idx]
        if (target !is BodyBlock.Text) {
            focusFirstTextBlock()
            return
        }
        /** cursorOffset 是有效文本坐标（剥 ZWSP），用 effective length 做上界 coerceIn——
         *  空块场景会先临时落在 (0, 0)，再被 BlockTextItem 的 ZWSP 维护 LaunchedEffect 推到 (1, 1) */
        val effLen = effectiveText(target.state.annotatedString.text).length
        pendingFocusId = target.id
        pendingFocusOffset = snapshot.cursorOffset.coerceIn(0, effLen)
    }

    /** 兜底：把焦点落到第一个 Text 块。 */
    private fun focusFirstTextBlock() {
        val firstText = blocks.firstOrNull { it is BodyBlock.Text } as? BodyBlock.Text ?: return
        pendingFocusId = firstText.id
        pendingFocusOffset = 0
    }

    // ---------- 删除 / 合并 ----------

    /** 按 id 删除图片块（两步删除的确认步） */
    fun deleteImageBlock(blockId: String) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx >= 0 && blocks[idx] is BodyBlock.Image) {
            pushTimelineSnapshot()
            blocks.removeAt(idx)
            highlightedBlockId = null
            onDocChanged?.invoke()
        }
    }

    /** 按路径删除图片块（画廊删除入口），返回是否删除 */
    fun deleteImageByPath(path: String): Boolean {
        val idx = blocks.indexOfFirst { it is BodyBlock.Image && it.path == path }
        if (idx < 0) return false
        pushTimelineSnapshot()
        blocks.removeAt(idx)
        highlightedBlockId = null
        onDocChanged?.invoke()
        return true
    }

    /**
     * 退格合并（在块首 / 已折叠光标处按退格时由调用方调用）：
     * - 前一块是 Text → 合并（拼接 markdown，焦点与光标落接缝）
     * - 前一块是 Image → 两步删除（第一次高亮，第二次删除）
     *
     * v2026-09-01 修订（首空块退格）：原来 `if (idx <= 0) return` 把首块退格整段吞了。
     * 现在按 idx 区分：
     * - `idx > 0` → 走"前一块"逻辑（合并 Text / 两步删除 Image），与原行为一致
     * - `idx == 0 && 自身空` → **删除自身**，把焦点放到下一 Text 块首（让首空块可被退格消掉）
     * - `idx == 0 && 自身非空` → 没前驱可合，啥都不做（库默认 no-op）
     *
     * 已知限制（软键盘在空块上按退格）：snapshotFlow 观察者靠 `lastText.isNotEmpty()` +
     * `text == lastText.drop(1)` 检测"首字符被删"——空块上退格 text/selection 都不变，observer
     * 不发射，`onBackspaceAtStart` 不会被调。要彻底解决需给空 Text 块预置零宽字符 \u200B
     * （让退格能"删"出一个状态变化），改造面较大不在本期。硬键盘走 onPreviewKeyEvent 不受此限。
     */
    fun onBackspaceAtStart(block: BodyBlock.Text) {
        val idx = blocks.indexOfFirst { it.id == block.id }
        if (idx < 0) return

        if (idx == 0) {
            if (isEffectivelyEmpty(block.state)) {
                pushTimelineSnapshot()
                blocks.removeAt(0)
                /** invariant: 至少一个 Text 块——若列表空了，重新加一个空块 */
                ensureTextBlock(atEnd = true)
                /** 把焦点放回当前首 Text 块（通常是下一块，可能是新建的空块）。
                 *  id 不同才设 pendingFocus，否则 LaunchedEffect 不会触发。 */
                val firstText = blocks.firstOrNull { it is BodyBlock.Text } as? BodyBlock.Text
                if (firstText != null && firstText.id != block.id) {
                    pendingFocusId = firstText.id
                    pendingFocusOffset = 0
                }
                onDocChanged?.invoke()
            }
            return
        }

        when (val prev = blocks[idx - 1]) {
            is BodyBlock.Text -> mergeTextBlocks(prev, block)
            is BodyBlock.Image -> {
                if (highlightedBlockId == prev.id) deleteImageBlock(prev.id)
                else highlightedBlockId = prev.id
            }
        }
    }

    /** 硬键盘 Delete（块尾）：若下一块是图片 → 两步删除 */
    fun onDeleteAtEnd(block: BodyBlock.Text) {
        val idx = blocks.indexOfFirst { it.id == block.id }
        if (idx < 0 || idx == blocks.lastIndex) return
        val next = blocks[idx + 1]
        if (next is BodyBlock.Image) {
            if (highlightedBlockId == next.id) deleteImageBlock(next.id)
            else highlightedBlockId = next.id
        }
    }

    private fun mergeTextBlocks(prev: BodyBlock.Text, cur: BodyBlock.Text) {
        pushTimelineSnapshot()
        val prevMd = prev.state.toMarkdown()
        val curMd = cur.state.toMarkdown()
        val junction = prev.state.annotatedString.text.length
        /** 合并引发的 state 变化不作为"用户文本编辑"压栈（快照已在上面压过） */
        val was = suppressTimeline
        suppressTimeline = true
        try {
            prev.state.setMarkdown(prevMd + curMd)
            prev.state.selection = TextRange(junction)
            val curIdx = blocks.indexOfFirst { it.id == cur.id }
            if (curIdx >= 0) blocks.removeAt(curIdx)
        } finally {
            suppressTimeline = was
        }
        pendingFocusId = prev.id
        pendingFocusOffset = junction
        onDocChanged?.invoke()
    }

    // ---------- 重排 / 焦点 ----------

    /** 拖拽排序回调 */
    fun moveBlock(from: Int, to: Int) {
        if (from == to || from !in blocks.indices || to !in blocks.indices) return
        pushTimelineSnapshot()
        val block = blocks.removeAt(from)
        blocks.add(to, block)
        onDocChanged?.invoke()
    }

    /** 块获得焦点时回调（由块 Composable 的 onFocusChanged 触发） */
    fun onBlockFocused(blockId: String) {
        focusedBlockId = blockId
        if (highlightedBlockId != null) highlightedBlockId = null
    }

    /** 块内容变化时回调（由块 Composable 的观察者触发） */
    fun notifyBlockChanged() {
        onDocChanged?.invoke()
    }

    // ---------- 内部 ----------

    /**
     * 保证存在 Text 块。
     * @param atEnd true 时额外保证**末尾**是 Text 块（图片后可继续输入）
     */
    private fun ensureTextBlock(atEnd: Boolean = false) {
        if (blocks.none { it is BodyBlock.Text }) {
            blocks += createTextBlock("")
            return
        }
        if (atEnd && blocks.last() !is BodyBlock.Text) {
            blocks += createTextBlock("")
        }
    }
}

/** 页面侧 remember 工厂 */
@Composable
fun rememberBodyBlocksController(
    registerTriggers: (RichTextState) -> Unit,
): BodyBlocksController = remember { BodyBlocksController(registerTriggers) }

// ==================== UI ====================

/**
 * 块编辑器主体：Text / Image 交错渲染。
 *
 * 拖拽：每个块右侧有拖拽手柄（长按手柄拖动），文本区长按仍是文本选择，互不冲突。
 */
@Composable
fun BodyBlocksEditor(
    controller: BodyBlocksController,
    isLocked: Boolean,
    onImageTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BlocksReorderableColumn(
        items = controller.blocks.toList(),
        onReorder = { from, to -> controller.moveBlock(from, to) },
        modifier = modifier.fillMaxWidth(),
    ) { _, block, isDragging, dragHandleModifier ->
        when (block) {
            is BodyBlock.Text -> BlockTextItem(
                controller = controller,
                block = block,
                isLocked = isLocked,
                isDragging = isDragging,
                dragHandleModifier = dragHandleModifier,
            )
            is BodyBlock.Image -> BlockImageItem(
                controller = controller,
                block = block,
                isDragging = isDragging,
                isLocked = isLocked,
                onImageTap = onImageTap,
                dragHandleModifier = dragHandleModifier,
            )
        }
    }
}

/**
 * 块级重排列：与全局 [com.corgimemo.app.ui.components.ReorderableColumn] 行为一致，
 * 差异在于把手 modifier 交给每个块的 content 自行放置（挂在手柄图标上而非整块），
 * 避免长按拖拽与文本长按选择冲突。
 */
@Composable
private fun <T> BlocksReorderableColumn(
    items: List<T>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, item: T, isDragging: Boolean, dragHandleModifier: Modifier) -> Unit,
) {
    val context = LocalContext.current
    sh.calvin.reorderable.ReorderableColumn(
        list = items,
        onSettle = { fromIndex, toIndex ->
            if (fromIndex != toIndex) {
                HapticFeedbackManager.performHapticFeedback(
                    context = context,
                    type = InteractionType.CONFIRM,
                    enabled = true,
                )
                onReorder(fromIndex, toIndex)
            }
        },
        modifier = modifier,
    ) { index, item, isDragging ->
        ReorderableItem {
            content(index, item, isDragging, Modifier.longPressDraggableHandle())
        }
    }
}

/** 拖拽手柄（竖排圆点），长按拖动；所有块共用视觉 */
@Composable
private fun BlockDragHandle(dragHandleModifier: Modifier) {
    Box(
        modifier = dragHandleModifier
            .width(20.dp)
            .heightIn(min = 24.dp)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .heightIn(min = 24.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}

// ==================== Text 块 ====================

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BlockTextItem(
    controller: BodyBlocksController,
    block: BodyBlock.Text,
    isLocked: Boolean,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
) {
    val state = block.state

    /** 聚焦到本块（拆分 / 合并 / 插图后由 controller.pendingFocusId 驱动） */
    LaunchedEffect(controller.pendingFocusId) {
        if (controller.pendingFocusId == block.id) {
            block.state.selection = TextRange(controller.pendingFocusOffset)
            block.focusRequester.requestFocus()
            controller.pendingFocusId = null
        }
    }

    /**
     * 变更观察者（软键盘无按键事件，全部靠状态差分）：
     * 0. **v2026-09-01 统一时间线**：用户文本变化 → 压"变化前"快照到撤销栈
     *    （[BodyBlocksController.pushTextUndoSnapshot]），撤销一步回一个字符；
     * 1. 块内出现 `\n` → 按段落拆块（Enter / 粘贴多行）
     * 2. 文本变短且退格前光标折叠在块首 → 与前一块合并 / 高亮前一个图片块
     * 3. 空块软键盘退格（state 由 ZWSP 唯一变成 ""）→ 走
     *    [BodyBlocksController.onBackspaceAtStart]；配合末尾的 ZWSP 不变量
     *    维护 LaunchedEffect，软键盘场景也能删除空块。
     */
    LaunchedEffect(block.id) {
        var lastText = state.annotatedString.text
        var lastSelection = state.selection
        snapshotFlow { Pair(state.annotatedString.text, state.selection) }
            .collect { (text, selection) ->
                if (isLocked) {
                    lastText = text
                    lastSelection = selection
                    return@collect
                }
                /** 统一时间线压栈：文本真变了（且非恢复操作、非 IME 组合中间态）→
                 *  压"变化前"快照。与栈顶相同（结构操作已压过同一份）自动去重。
                 *  退格合并 / 空块删除不在此压——onBackspaceAtStart 入口统一压
                 *  （硬键盘路径 text 未变、observer 不触发，入口压栈才能覆盖两条路径）。
                 *  已知取舍：中文 IME 组合结束后压的快照，"变化前"是组合最后一个
                 *  中间态（如 "nihao"），撤销一步先回到拼音残迹再回空——后续可优化。 */
                val backspaceMerge = lastText.isNotEmpty() && text == lastText.drop(1) &&
                    lastSelection.collapsed && lastSelection.start == 0
                val emptyBackspace = lastText == ZWSP && text == ""
                if (text != lastText && !backspaceMerge && !emptyBackspace &&
                    !controller.suppressTimeline && state.composition == null
                ) {
                    controller.pushTextUndoSnapshot(block.id, lastText, lastSelection)
                }
                when {
                    text.contains('\n') -> controller.normalizeBlockParagraphs(block)
                    /** 块首退格：文本恰好丢掉首字符 + 退格前光标折叠在 0
                     *  （用精确前缀匹配，避免拆块/撤销等其他缩文本场景误判） */
                    backspaceMerge -> controller.onBackspaceAtStart(block)
                    /** 空块软键盘退格：IME 在 ZWSP 唯一态调用 deleteSurroundingText
                     *  把 ZWSP 删掉，text 变 "" → 走 onBackspaceAtStart（与硬键盘同路径） */
                    emptyBackspace -> controller.onBackspaceAtStart(block)
                }
                /** ZWSP 不变量维护：空块恢复 \u200B + 光标 (1, 1)，否则软键盘退格下一次又无法检测。
                 *  注意：observer 条件检查必须在 setText 之前——否则 setText 让 text 从 "" 变 "\u200B"
                 *  时，下一轮 collect 用 lastText == ZWSP 判断就漏判了（lastText 此时还是 ""）。 */
                if (text.isEmpty()) {
                    state.setText(ZWSP)
                    state.selection = TextRange(1)
                } else if (text == ZWSP && selection.start == 0) {
                    /** 用户点击到 (0, 0)（ZWSP 之前），重置回 (1, 1) 让下一次退格能起作用。
                     *  （0, 0）/ (1, 1) 对 \u200B 视觉都在「块起始」位置，不会有可见跳动。） */
                    state.selection = TextRange(1)
                }
                lastText = text
                lastSelection = selection
            }
    }

    /** 内容变化 → 通知 controller 同步 ViewModel */
    LaunchedEffect(block.id) {
        snapshotFlow { state.annotatedString }
            .collect {
                controller.notifyBlockChanged()
            }
    }

    /**
     * v2026-09-01 块间距 = 块内行距：
     * - 手柄去掉自身垂直 padding（旧 10dp×2 + 24dp = 44dp 把整行撑高），
     *   只保留 `minHeight = 24.dp`，作为「顶上把手」贴在块左上角；
     * - Row 改为 `verticalAlignment = Top`：多行块也不把手柄浮到中间，
     *   永远对齐第一行文本——这是 Notion / Linear / Capacities 等块编辑器
     *   的把手标准对齐方式；
     * - 编辑器 `minHeight = 0.dp`（库新增参数）+ contentPadding 垂直 = 0
     *   → 块高 = 行数 × 行距，块间无额外间距；
     * - 视觉上"两块文本之间的行距"与"块内两行之间的行距"完全相同；
     *   后续调整 textStyle.lineHeight 会同时作用于块内行高与块间，
     *   天然联动。
     */
    Row(verticalAlignment = Alignment.Top) {
        BlockDragHandle(dragHandleModifier)

        RichTextEditor(
            state = state,
            modifier = Modifier
                .weight(1f)
                .heightIn(
                    min = if (controller.blocks.size == 1 && isEffectivelyEmpty(state)) {
                        160.dp
                    } else {
                        /** 非初始空块：由 minLines=1 兜底一行高，不强制更大 */
                        0.dp
                    }
                )
                .focusRequester(block.focusRequester)
                .onFocusChanged { if (it.isFocused) controller.onBlockFocused(block.id) }
                .graphicsLayer {
                    if (isDragging) {
                        alpha = 0.6f
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    if (isLocked) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            /** 硬回车：拦截，直接在光标处拆块（\n 不进文本） */
                            controller.splitTextBlockAtCursor(block)
                            true
                        }
                        Key.Backspace -> {
                            val sel = state.selection
                            if (sel.start == 0 && sel.end == 0) {
                                controller.onBackspaceAtStart(block)
                                true
                            } else {
                                false
                            }
                        }
                        Key.Delete -> {
                            val len = state.annotatedString.text.length
                            val sel = state.selection
                            if (sel.start == len && sel.end == len) {
                                controller.onDeleteAtEnd(block)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                },
            placeholder = if (controller.blocks.size == 1 && isEffectivelyEmpty(state)) {
                {
                    Text(
                        text = "请在这里输入内容...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            } else {
                null
            },
            readOnly = isLocked,
            /** v2026-09-01 统一时间线：禁用库内 undo（含物理键盘 Ctrl+Z 快捷键拦截），
             *  撤销/重做全部走 controller 的统一时间线快照栈 */
            undoBehavior = UndoBehavior.Disabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            /** 块高 = 内容行数 × 行距（消除库默认 56dp 强制最小高度） */
            minHeight = 0.dp,
            /** 垂直 padding 归零：块间距完全由行距决定（水平保留 16dp 与正文对齐） */
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 0.dp,
                end = 16.dp,
                bottom = 0.dp,
            ),
            colors = RichTextEditorDefaults.richTextEditorColors(
                containerColor = Color.Transparent,
                cursorColor = Color(0xFFFF9A5C),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )
    }
}

// ==================== Image 块 ====================

@Composable
private fun BlockImageItem(
    controller: BodyBlocksController,
    block: BodyBlock.Image,
    isDragging: Boolean,
    isLocked: Boolean,
    onImageTap: (String) -> Unit,
    dragHandleModifier: Modifier,
) {
    Row(verticalAlignment = Alignment.Top) {
        BlockDragHandle(dragHandleModifier)

        InlineImagePreview(
            imageUri = block.path,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .graphicsLayer {
                    if (isDragging) {
                        alpha = 0.6f
                    }
                },
            isHighlighted = controller.highlightedBlockId == block.id,
            onClick = if (isLocked) null else {
                { onImageTap(block.path) }
            },
        )
    }
}
