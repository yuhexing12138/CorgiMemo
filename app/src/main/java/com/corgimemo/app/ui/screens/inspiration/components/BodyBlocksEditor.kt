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
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
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
private fun effectiveText(text: String): String = text.replace(ZWSP, "")
private fun isEffectivelyEmpty(text: String): Boolean = effectiveText(text).isEmpty()
private fun isEffectivelyEmpty(state: RichTextState): Boolean =
    isEffectivelyEmpty(state.annotatedString.text)

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
@OptIn(ExperimentalRichTextApi::class)
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
     */
    fun toMarkdown(): String =
        blocks.mapNotNull { block ->
            when (block) {
                /** 剥掉空块预置的 ZWSP，保证 markdown 往返不带噪音 */
                is BodyBlock.Text -> block.state.toMarkdown().replace(ZWSP, "")
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
        /** v2026-09-01 串联时间线改造：图片插入由 `state.history` 自身捕获（库 `insertImage`
         *  会被 recordHistory），所以这里不再压块快照、也不清 text history——
         *  这样"打字→撤回→逐字回退"与"打字→插图→撤回→逐字回退"两类需求共用同一个 history，
         *  按倒序自然衔接。块级快照栈留给"块拆分/合并/重排/删图"等真正改变块列表的操作。 */
        insertOneImageInternal(path)
    }

    /**
     * v2026-09-01 串联时间线改造：批量插入也是同一个用户操作（多选相册确认），
     * 全部走 `state.history`，不压块快照、整批 N 次连续记录，由 `history.coalesceWindowMs=0`
     * 保证 N 个独立 undo group，逐张也能撤销回去。
     */
    fun insertImagesAtFocused(paths: List<String>) {
        if (paths.isEmpty()) return
        suppressDocChanged = true
        try {
            paths.forEach { insertOneImageInternal(it) }
        } finally {
            suppressDocChanged = false
        }
        onDocChanged?.invoke()
    }

    /** 内部：插入一张图片（**不推快照**，**不拆块**）。state.history 已捕获 insertImage 本身，
     *  这里只需要在 blocks 列表里 focusedIdx+1 追加一个 Image 块作为渲染标记，
     *  真实图片内容由原 Text 块的 state 持有——state 包含 image span + 完整 history。
     *  撤销时 state 回滚 + observer 触发 [syncBlocksAfterState] 自动把 Image 标记块删掉。 */
    private fun insertOneImageInternal(path: String) {
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
                @OptIn(ExperimentalRichTextApi::class)
                focused.state.insertImage(model = path)
                /** v2026-09-01 串联时间线改造：不再调 replaceBlockWithParsed 拆块——
                 *  保留原 state（含 image span + 完整 history），只在 focusedIdx+1
                 *  追加一个 Image 块作为渲染标记。撤销时 state 回滚 + observer 触发
                 *  [syncBlocksAfterState] 自动把 Image 标记块删掉。 */
                blocks.add(focusedIdx + 1, BodyBlock.Image(newBodyBlockId(), path))
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

    /** 把 [index] 处的块替换为其 markdown 重解析出的块序列，并把焦点放到 [cursorOffset] 所在块 */
    private fun replaceBlockWithParsed(index: Int, markdown: String, cursorOffset: Int) {
        val newBlocks = parseMarkdownSegments(markdown)
            .flatMap { seg ->
                when (seg) {
                    is MdSegment.TextSeg -> seg.md.split("\n\n")
                        .mapNotNull { para ->
                            val trimmed = para.trim('\n')
                            if (trimmed.isNotBlank()) createTextBlock(trimmed) else null
                        }
                    is MdSegment.ImageSeg -> listOf(BodyBlock.Image(newBodyBlockId(), seg.path))
                }
            }
            .ifEmpty { listOf(createTextBlock("")) }

        blocks.removeAt(index)
        blocks.addAll(index, newBlocks)
        ensureTextBlock(atEnd = true)
        focusAtOffset(newBlocks, cursorOffset)
        if (!suppressDocChanged) onDocChanged?.invoke()
    }

    /**
     * v2026-09-01 串联时间线改造关键同步器：
     * 把 [stateBlockId] 对应 Text 块右侧的 Image 标记块数量与该 state 内 image span 数量对齐。
     *
     * 触发时机（由 BlockTextItem observer 检测 state 变化后调用）：
     * - state.insertImage 后：state 多了 image span、blocks 也多了 Image 块 → 数量已对齐，幂等
     * - state.history.undo 后：state 可能少了 image span（撤销插图）→ 删除对应数量的 Image 块
     * - state.history.redo 后：state 可能多了 image span → 补 Image 块
     *
     * 是"按倒序串联撤销"能成立的关键——state 回滚到无图时，块列表必须跟着把
     * Image 标记块删掉，否则视觉与内容不一致。
     */
    internal fun syncBlocksAfterState(stateBlockId: String) {
        val focusedIdx = blocks.indexOfFirst { it.id == stateBlockId }
        if (focusedIdx < 0) return
        val focused = blocks[focusedIdx] as? BodyBlock.Text ?: return

        /** 1. 从 state 里读出当前所有 image span，按顺序拿到 path 列表 */
        val imagePaths: List<String> = focused.state.styledRichSpanList
            .filter { it.richSpanStyle is RichSpanStyle.Image }
            .mapNotNull { (it.richSpanStyle as? RichSpanStyle.Image)?.model?.toString() }

        /** 2. 数 focusedIdx 之后连续的 Image 块数量（连续是因为我们约定"image 块紧跟所属 Text 块"） */
        var existingCount = 0
        var idx = focusedIdx + 1
        while (idx < blocks.size && blocks[idx] is BodyBlock.Image) {
            existingCount++
            idx++
        }

        /** 3. 少则补（用 state 里的 path）、多则删；用闭包式 while 保证幂等 */
        while (existingCount < imagePaths.size) {
            blocks.add(
                focusedIdx + 1 + existingCount,
                BodyBlock.Image(newBodyBlockId(), imagePaths[existingCount]),
            )
            existingCount++
        }
        while (existingCount > imagePaths.size) {
            blocks.removeAt(focusedIdx + 1 + imagePaths.size)
            existingCount--
        }
        onDocChanged?.invoke()
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
        pushBlockSnapshot()
        val idx = blocks.indexOfFirst { it.id == block.id }
        if (idx < 0) return
        val text = block.state.annotatedString.text
        val beforeMd = if (beforeEnd > 0) block.state.toMarkdown(TextRange(0, beforeEnd)) else ""
        val afterMd = if (afterStart < text.length) block.state.toMarkdown(TextRange(afterStart, text.length)) else ""

        block.state.setMarkdown(beforeMd)
        val newBlock = createTextBlock(afterMd)
        newBlock.state.selection = TextRange(0)
        blocks.add(idx + 1, newBlock)
        pendingFocusId = newBlock.id
        pendingFocusOffset = 0
        onDocChanged?.invoke()
    }

    /**
     * 归一化：块内出现 `\n`（软键盘回车 / 粘贴多行）时按行拆成段落块。
     * 末尾连续空行保留为一个空块（回车在段尾 = 新起一段）；其余空行丢弃。
     * 焦点落到原光标所在的新块，偏移按行内位置换算。
     */
    fun normalizeBlockParagraphs(block: BodyBlock.Text) {
        pushBlockSnapshot()
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

    // ---------- 块级撤销 / 重做（整篇 markdown + 光标快照栈） ----------

    /**
     * v2026-09-01 修订（撤销时光标位置）：
     * 旧快照是纯 markdown 字符串，undo/redo 后只 `focusFirstTextBlock()` → 永远回到
     * 第一个 Text 块 offset 0，丢失了用户操作前的光标位置（多选相册批量插图场景下
     * 撤销后光标跑到"12"最左端而非 1和2 之间）。
     *
     * 现在快照升级成 [BlockSnapshot]：
     * - `markdown` — 整篇文本（含图片语法），用于 [BodyBlocksController.initialize]
     * - `focusedText` — 快照时刻聚焦块的**有效文本**（剥 \U200B），用来在新块列表里匹配同块
     * - `cursorOffset` — 块内光标偏移（raw text 坐标，含 \U200B；新块的 state 也有 \U200B 所以一致）
     *
     * undo/redo 时调用 [restoreCursor]，在重建后的块列表里找 `focusedText` 匹配的第一个
     * Text 块，把光标设回 `cursorOffset`。多个块文本相同时取首个（罕见兜底）。
     */
    /**
     * v2026-09-01 第三次修订（保留空块结构）：
     * 旧快照用 `markdown: String`，但 `toMarkdown()` 会 `.filter { it.isNotBlank() }`
     * 把空块过滤掉、`initialize(markdown)` 又按 markdown 反向重建——所以
     * `[Text("12"), Text("")]` 的快照只存 `"12"`，撤销后空块丢失。
     *
     * 修复：快照升级成 [BlockSnapshot]（entries + 焦点索引 + 光标），
     * 每个 entry 显式标 Text（含空）/ Image，**空 Text 块不再丢**。
     * `restoreFromSnapshot` 按 entries 精确重建；`toMarkdown()` 不变（仍过滤空块，
     * 数据库内容不带 ZWSP 噪音），仅快照路径走新机制。
     *
     * 光标：用有效文本坐标（剥 \U200B），与上一轮一致——解决空块下光标跳末尾的另一个原因是
     * `initialize` 只重建到 Text("12")，待重建的块**数量和位置**都变了，需要重新匹配。
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

    private val blockUndoStack = ArrayDeque<BlockSnapshot>()
    private val blockRedoStack = ArrayDeque<BlockSnapshot>()
    private val maxBlockHistory = 50

    var canUndoBlocks by mutableStateOf(false)
        private set
    var canRedoBlocks by mutableStateOf(false)
        private set

    /** 结构变更前调用：压入当前块结构 + 光标（与栈顶 markdown 相同则跳过） */
    private fun pushBlockSnapshot() {
        val snapshot = captureCurrentSnapshot()
        if (blockUndoStack.lastOrNull()?.entries != snapshot.entries) {
            blockUndoStack.addLast(snapshot)
            if (blockUndoStack.size > maxBlockHistory) blockUndoStack.removeFirst()
            /** v2026-09-01 两类互斥：结构变更后清空所有 Text 块的 history，
             *  文本 undo 不能跨过结构变更（用户在 Text("12") 里打 "x" → 插图 → 撤销块级，
             *  不能继续撤销"x"，因为 history 已被清空） */
            blocks.forEach { block ->
                if (block is BodyBlock.Text) block.state.history.clear()
            }
        }
        blockRedoStack.clear()
        canUndoBlocks = blockUndoStack.isNotEmpty()
        canRedoBlocks = false
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

    /** 块级撤销：按结构快照重建块列表，并恢复光标 */
    fun undoBlocks(): Boolean {
        val snapshot = blockUndoStack.removeLastOrNull() ?: return false
        blockRedoStack.addLast(captureCurrentSnapshot())
        restoreFromSnapshot(snapshot)
        restoreCursor(snapshot)
        canUndoBlocks = blockUndoStack.isNotEmpty()
        canRedoBlocks = true
        return true
    }

    /** 块级重做 */
    fun redoBlocks(): Boolean {
        val snapshot = blockRedoStack.removeLastOrNull() ?: return false
        blockUndoStack.addLast(captureCurrentSnapshot())
        restoreFromSnapshot(snapshot)
        restoreCursor(snapshot)
        canUndoBlocks = true
        canRedoBlocks = blockRedoStack.isNotEmpty()
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
            pushBlockSnapshot()
            blocks.removeAt(idx)
            highlightedBlockId = null
            onDocChanged?.invoke()
        }
    }

    /** 按路径删除图片块（画廊删除入口），返回是否删除 */
    fun deleteImageByPath(path: String): Boolean {
        val idx = blocks.indexOfFirst { it is BodyBlock.Image && it.path == path }
        if (idx < 0) return false
        pushBlockSnapshot()
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
                pushBlockSnapshot()
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
        pushBlockSnapshot()
        val prevMd = prev.state.toMarkdown()
        val curMd = cur.state.toMarkdown()
        val junction = prev.state.annotatedString.text.length
        prev.state.setMarkdown(prevMd + curMd)
        prev.state.selection = TextRange(junction)
        val curIdx = blocks.indexOfFirst { it.id == cur.id }
        if (curIdx >= 0) blocks.removeAt(curIdx)
        pendingFocusId = prev.id
        pendingFocusOffset = junction
        onDocChanged?.invoke()
    }

    // ---------- 重排 / 焦点 ----------

    /** 拖拽排序回调 */
    fun moveBlock(from: Int, to: Int) {
        if (from == to || from !in blocks.indices || to !in blocks.indices) return
        pushBlockSnapshot()
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
     * 1. 块内出现 `\n` → 按段落拆块（Enter / 粘贴多行）
     * 2. 文本变短且退格前光标折叠在块首 → 与前一块合并 / 高亮前一个图片块
     * 3. **v2026-09-01 新增**：空块软键盘退格（state 由 ZWSP 唯一变成 ""）
     *    → 走 [BodyBlocksController.onBackspaceAtStart]；配合末尾的 ZWSP 不变量
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
                when {
                    text.contains('\n') -> controller.normalizeBlockParagraphs(block)
                    /** 块首退格：文本恰好丢掉首字符 + 退格前光标折叠在 0
                     *  （用精确前缀匹配，避免拆块/撤销等其他缩文本场景误判） */
                    lastText.isNotEmpty() && text == lastText.drop(1) &&
                        lastSelection.collapsed && lastSelection.start == 0 ->
                        controller.onBackspaceAtStart(block)
                    /** 空块软键盘退格：IME 在 ZWSP 唯一态调用 deleteSurroundingText
                     *  把 ZWSP 删掉，text 变 "" → 走 onBackspaceAtStart（与硬键盘同路径） */
                    lastText == ZWSP && text == "" ->
                        controller.onBackspaceAtStart(block)
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

    /** 内容变化 → 通知 controller 同步 ViewModel + 按需同步 Image 标记块 */
    LaunchedEffect(block.id) {
        /** v2026-09-01 串联时间线改造：image span 数量变化时（插图 undo/redo 引发）
         *  必须调 syncBlocksAfterState 把块列表对齐——这是"按倒序串联撤销"能成立的关键拼图 */
        var lastImageSpanCount = countImageSpans(state)
        snapshotFlow { state.annotatedString }
            .collect { annStr ->
                val currentImageSpanCount = countImageSpans(state)
                if (currentImageSpanCount != lastImageSpanCount) {
                    controller.syncBlocksAfterState(block.id)
                    lastImageSpanCount = currentImageSpanCount
                }
                controller.notifyBlockChanged()
            }
    }

    private fun countImageSpans(state: RichTextState): Int =
        state.styledRichSpanList.count { it.richSpanStyle is RichSpanStyle.Image }

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
