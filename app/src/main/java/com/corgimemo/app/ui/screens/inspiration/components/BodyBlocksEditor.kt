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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
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
 *
 * ## 撤销架构（v2026-09-02 方案A：自建 Command 命令栈，两套历史隔离）
 *
 * - **全局 Command 栈**（[BodyBlocksController.undoCommands] / [redoCommands]）：
 *   只存操作增量 [BodyBlocksCommand]，不存全量快照——管块的增删、拖拽排序、
 *   图片块属性编辑。controller 持有在 ViewModel（[InspirationEditViewModel]），
 *   屏幕旋转不丢历史。
 * - **块内富文本 history**（compose-rich-editor 自带 `RichTextState.history`）：
 *   每个Text块的打字 / 加粗 / 样式自己管自己，**不进全局栈**——避免每敲一个字
 *   把整个块列表压栈。
 * - **统一调度**（[BodyBlocksController.undo] / [redo]，焦点判断是核心）：
 *   聚焦块（未聚焦时回退首 Text 块）的 `history.canUndo` 非空 → 先回退块内文字；
 *   块内回退完（或本就为空）→ 走全局 Command 栈。这样按撤销时行为可预期：
 *   时而回退文字、时而回退块操作，但两者不会互相干扰。
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

// ==================== Command 体系（方案A：增量命令，不存全量快照） ====================

/**
 * 块的可重建描述——Command 的载荷。
 *
 * **绝不持有 Bitmap / 富文本 state 等重量级对象**（方案A坑点2）：
 * Text 只存 markdown 字符串，Image 只存 uri（path）——将来图片属性三件套
 * （裁剪 cropRect / 备注 note / 缩放 displayWidthRatio）落地时，沿用本模式
 * 把字段加进 [ImageSpec] 即可，Command 的重建逻辑不用动。
 */
sealed class BlockSpec {
    abstract val id: String

    /** Text 块：markdown 剥过 ZWSP（见 [BodyBlocksController.blockMarkdown]） */
    data class TextSpec(override val id: String, val markdown: String) : BlockSpec()

    /** Image 块：只有 uri 路径 */
    data class ImageSpec(override val id: String, val path: String) : BlockSpec()
}

/** 焦点落点描述：块 id + 块内有效文本偏移（剥 ZWSP 的坐标系） */
data class FocusSpec(val blockId: String, val offset: Int)

/**
 * 块文档操作命令：`apply` = 执行（首次执行与 redo 重放共用），`revert` = 撤销。
 *
 * 设计约定：
 * - 命令携带"操作前/后"的块描述（[BlockSpec]）与焦点（[FocusSpec]），
 *   通过 [controller] 提供的重建辅助落盘，自身不直接触碰 Composable；
 * - **redo 可达 ⇒ 各块当前内容 == 上次全局操作结束时的内容**（新编辑会清
 *   redo 栈，见 observer），因此重放记录的 spec 是安全的；
 * - undo 的对称语义由 Command 栈保证：revert 面对的列表 == 该命令 apply 后
 *   的列表（中间的命令已全部回退）。
 */
sealed interface BodyBlocksCommand {
    fun apply(controller: BodyBlocksController)
    fun revert(controller: BodyBlocksController)
}

/**
 * 区间替换命令：把 `[index, index + removedSpecs.size)` 的块替换为
 * `insertedSpecs` 重建的块——覆盖块级操作的全部形态：
 *
 * - 插图拆块：removed = [源Text]，inserted = [前半Text, Image, 后半Text]
 * - Enter 拆块：removed = [源Text(全文)]，inserted = [源Text(前半, 同id), 后半Text]
 * - 粘贴多行归一化：removed = [源Text]，inserted = [N 个段落 Text]
 * - 退格合并：removed = [前Text, 后Text]，inserted = [前Text(合并后, 同id)]
 * - 删除图片块 / 首空块退格删除：removed = [目标块]，inserted = []
 */
class ReplaceBlocksCommand(
    /** 被替换区间在 apply 前列表中的锚定索引 */
    val index: Int,
    /** 操作前的块描述（revert 的恢复目标） */
    val removedSpecs: List<BlockSpec>,
    /** 操作后的块描述（apply / redo 的重放目标） */
    val insertedSpecs: List<BlockSpec>,
    /** 操作前焦点（revert 后恢复） */
    val focusBefore: FocusSpec?,
    /** 操作后焦点（apply 后落点） */
    val focusAfter: FocusSpec?,
) : BodyBlocksCommand {
    /**
     * 被替换的**原始块对象**（带各自 RichTextState 历史）的暂存。
     *
     * 修复方案A已知边界（setMarkdown 会清块内 history）：块级命令在 apply 时经
     * setMarkdown 重建文字块会清空其库内 history（打字历史丢失）。若 revert 也走
     * "从 markdown 重建"，则撤销命令后还原出的文字块历史已空，canUndo 仅靠命令栈
     * （此时已空）判定 → 撤销键提前变灰、无法继续撤销到文字清空。
     * 因此 apply 时把被替换的原始块对象整体暂存，revert 时**原样还原**这些对象
     * （不再重建），保留用户在命令前打过的字，使"继续撤销直至文字消失"成为可能。
     *
     * 注意：暂存的是块对象引用（含 RichTextState），非 Bitmap；与 [BlockSpec]
     * "绝不持 Bitmap" 的约束不冲突——那是 Command 载荷，此处是回收原始对象。
     * 仅首次 apply 捕获；redo 重放不再覆盖（保留初值，避免把重放态误存为原始态）。
     */
    var stashedRemoved: List<BodyBlock>? = null
        private set

    override fun apply(controller: BodyBlocksController) {
        /** 当前列表 index 处应是"被替换区间"（首次 = removed 原状，重放 = removed 已恢复） */
        val idx = controller.locateRangeStart(removedSpecs.firstOrNull()?.id, index)
        /** 首次执行时暂存被替换的原始块；redo 重放不再覆盖（保留初值） */
        if (stashedRemoved == null) {
            stashedRemoved = controller.blocks
                .subList(idx, (idx + removedSpecs.size).coerceAtMost(controller.blocks.size))
                .toList()
        }
        controller.replaceBlockRange(idx, removedSpecs.size, insertedSpecs)
        focusAfter?.let { controller.focusSpec(it) } ?: controller.focusFirstTextBlock()
        controller.afterCommandMutation()
    }

    override fun revert(controller: BodyBlocksController) {
        /** 当前列表 index 处应是"命令产物区间" */
        val idx = controller.locateRangeStart(insertedSpecs.firstOrNull()?.id, index)
        /**
         * 先把产物块内未撤销的富文本编辑"显式回退到底"（drain 库内 history），
         * 再替换——避免块级 undo 静默丢弃用户在产物块里打过的字
         * （回退可见地发生，而非内容凭空消失）。
         */
        insertedSpecs.filterIsInstance<BlockSpec.TextSpec>().forEach {
            controller.drainBlockHistory(it.id)
        }
        val stash = stashedRemoved
        if (stash != null) {
            /** 原样还原暂存的原始块（保留其 RichTextState 历史），而非从 markdown 重建 */
            controller.restoreBlockRange(idx, insertedSpecs.size, stash)
        } else {
            /** 兜底（暂存缺失）：退回旧行为——从 markdown 重建（历史将丢失，与修复前一致） */
            controller.replaceBlockRange(idx, insertedSpecs.size, removedSpecs)
        }
        focusBefore?.let { controller.focusSpec(it) } ?: controller.focusFirstTextBlock()
        controller.afterCommandMutation()
    }
}

/**
 * 拖拽排序命令。
 *
 * **方案A坑点3**：拖拽过程中不压栈（预览由 ReorderableColumn 自行渲染），
 * 只在 onSettle（手指抬起、落定）时由 [BodyBlocksController.moveBlock] 构造
 * 一次本命令——一步拖拽恰好一条撤销记录。
 *
 * 索引防御：undo/redo 均先按 [blockId] 定位当前真实索引，再移动到目标索引；
 * 正常路径下（栈式回退不变量）两者一致。
 */
class MoveBlockCommand(
    val blockId: String,
    val fromIndex: Int,
    val toIndex: Int,
) : BodyBlocksCommand {

    override fun apply(controller: BodyBlocksController) {
        controller.moveBlockById(blockId, toIndex)
        controller.afterCommandMutation()
    }

    override fun revert(controller: BodyBlocksController) {
        controller.moveBlockById(blockId, fromIndex)
        controller.afterCommandMutation()
    }
}

/**
 * 图片块属性编辑命令（当前只有 path 可改；**暂无 UI 调用入口**，
 * 作为将来裁剪 / 缩放 / 备注三件套的 Command 模板保留——
 * 届时把属性集扩进 spec 即可，撤销语义不变）。
 */
class UpdateImageBlockCommand(
    val blockId: String,
    val oldPath: String,
    val newPath: String,
) : BodyBlocksCommand {

    override fun apply(controller: BodyBlocksController) {
        controller.updateImageBlockPath(blockId, newPath)
        controller.afterCommandMutation()
    }

    override fun revert(controller: BodyBlocksController) {
        controller.updateImageBlockPath(blockId, oldPath)
        controller.afterCommandMutation()
    }
}

/**
 * 复合命令（方案A坑点5）：把多个原子命令打包成**一个撤销单位**——
 * 批量插图（多选相册一次确认）、将来的批量删除等。
 * apply 顺序执行，revert 逆序回退。
 */
class CompositeCommand(
    val commands: List<BodyBlocksCommand>,
) : BodyBlocksCommand {

    override fun apply(controller: BodyBlocksController) {
        commands.forEach { it.apply(controller) }
    }

    override fun revert(controller: BodyBlocksController) {
        commands.asReversed().forEach { it.revert(controller) }
    }
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

    /**
     * 是否已完成整篇初始化（旋转后不重跑 initialize 的关键）：
     * 标志随 controller 存活在 ViewModel——旋转时 Screen 的 remember 全部丢失，
     * 但 controller 的块列表与命令栈都还在，若重跑 [initialize] 会把它们清空
     * （方案A坑点4 的破坏者）。Screen 的 LaunchedEffect 以此为守卫。
     */
    internal var hasInitialized by mutableStateOf(false)
        private set

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

    /**
     * 新建 Text 块。
     *
     * @param id 块 id：Command 重放时传入原 id 复用（焦点/外部引用保持稳定），
     *   缺省生成新 id。
     */
    fun createTextBlock(markdown: String, id: String = newBodyBlockId()): BodyBlock.Text {
        val state = RichTextState()
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
        return BodyBlock.Text(id, state)
    }

    /** Text 块 → [BlockSpec.TextSpec]（markdown 剥 ZWSP，Command 载荷统一出口） */
    private fun textSpec(block: BodyBlock.Text): BlockSpec.TextSpec =
        BlockSpec.TextSpec(block.id, blockMarkdown(block.state))

    /** 块的 markdown 输出（剥 ZWSP——与 [toMarkdown] 的输出约定一致） */
    internal fun blockMarkdown(state: RichTextState): String =
        state.toMarkdown().replace(ZWSP, "")

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
        /** 换了整篇文档：命令栈与块内 history 一并作废（新块的 history 本就是空的） */
        clearCommandStacks()
        hasInitialized = true
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
     * 在当前聚焦块的光标处插入图片并拆块（单张 = 一条 [ReplaceBlocksCommand]）。
     */
    fun insertImageAtFocused(path: String) {
        executeAndPush(buildInsertImageCommand(path))
    }

    /**
     * 批量插入（多选相册一次确认）= **一个撤销单位**（方案A坑点5）：
     * 逐张"计算 + 立即应用"（下一张依赖上一张落定后的焦点位置），
     * 全部命令打包进一个 [CompositeCommand] 后只 push 一次——撤销一步全部回退。
     */
    fun insertImagesAtFocused(paths: List<String>) {
        if (paths.isEmpty()) return
        if (paths.size == 1) {
            insertImageAtFocused(paths.first())
            return
        }
        val commands = mutableListOf<BodyBlocksCommand>()
        replaying = true
        suppressDocChanged = true
        try {
            paths.forEach { path ->
                val cmd = buildInsertImageCommand(path)
                cmd.apply(this)
                commands += cmd
            }
        } finally {
            suppressDocChanged = false
            replaying = false
        }
        pushExecuted(CompositeCommand(commands))
    }

    /** 计算一次插图对应的替换命令（不落盘；[executeAndPush] 负责 apply + push） */
    private fun buildInsertImageCommand(path: String): BodyBlocksCommand {
        val focusedIdx = focusedBlockId
            ?.let { id -> blocks.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }

        if (focusedIdx == null) return buildInsertImageAtEndCommand(path)

        return when (val focused = blocks[focusedIdx]) {
            is BodyBlock.Image -> buildInsertImageAtEndCommand(path)
            is BodyBlock.Text -> buildInsertInTextCommand(focused, focusedIdx, path)
        }
    }

    /**
     * 在聚焦 Text 块的**光标处**插入图片并拆块——
     * 光标前后的文字各自成段：`[前半Text, Image, 后半Text]`。
     *
     * **v2026-09-01 自实现拆块**：不调 [RichTextState.insertImage]（其内部段计数
     *  与 selection 重置在某些路径下产生不确定输出），而是直接读 [RichTextState.selection]、
     *  用 [RichTextState.toMarkdown]（[TextRange] 重载）按光标精确拆段。
     * 内部 [RichTextState.extractRangeState] 按段落裁剪 spans，结果完全可预测且稳定。
     *
     * 拆分结果进 [ReplaceBlocksCommand]：removed = [源块]，inserted = 产物序列
     * （光标末尾无后半段时，补一个空尾块保证"图片后仍可输入"——ensureTextBlock
     * 的产物也纳入 inserted，保证 undo/redo 对称）。
     */
    private fun buildInsertInTextCommand(
        focused: BodyBlock.Text,
        focusedIdx: Int,
        path: String,
    ): ReplaceBlocksCommand {
        val state = focused.state
        val rawText = state.annotatedString.text
        val cursor = state.selection.start.coerceIn(0, rawText.length)

        val beforeMd = if (cursor > 0) state.toMarkdown(TextRange(0, cursor)).replace(ZWSP, "") else ""
        val afterMd = if (cursor < rawText.length)
            state.toMarkdown(TextRange(cursor, rawText.length)).replace(ZWSP, "") else ""

        val inserted = mutableListOf<BlockSpec>()
        if (beforeMd.isNotBlank()) inserted += BlockSpec.TextSpec(newBodyBlockId(), beforeMd)
        val imgSpec = BlockSpec.ImageSpec(newBodyBlockId(), path)
        inserted += imgSpec
        if (afterMd.isNotBlank()) inserted += BlockSpec.TextSpec(newBodyBlockId(), afterMd)
        /** 末块不是 Text（光标在段尾）→ 补空尾块（等价旧 ensureTextBlock(atEnd=true)） */
        val needsTrailingText = inserted.last() !is BlockSpec.TextSpec
        if (needsTrailingText) inserted += BlockSpec.TextSpec(newBodyBlockId(), "")

        /** 焦点 → 图片后的第一个 Text 块 offset 0（批量插入时下一张以此为锚点） */
        val focusAfter = FocusSpec(
            blockId = (inserted.drop(inserted.indexOf(imgSpec) + 1)
                .filterIsInstance<BlockSpec.TextSpec>()
                .firstOrNull() ?: inserted.filterIsInstance<BlockSpec.TextSpec>().last()).id,
            offset = 0,
        )

        return ReplaceBlocksCommand(
            index = focusedIdx,
            removedSpecs = listOf(textSpec(focused)),
            insertedSpecs = inserted,
            focusBefore = currentFocusSpec(),
            focusAfter = focusAfter,
        )
    }

    /**
     * 尾插图片：插在末尾 Text 块之前（图片后保留输入位）。
     * 覆盖：从未聚焦 / 聚焦块是 Image 两种场景。
     */
    private fun buildInsertImageAtEndCommand(path: String): ReplaceBlocksCommand {
        val needsTrailingText = blocks.lastOrNull() !is BodyBlock.Text
        val imgId = newBodyBlockId()
        val trailingId = if (needsTrailingText) newBodyBlockId() else null
        val inserted = buildList {
            add(BlockSpec.ImageSpec(imgId, path))
            if (trailingId != null) add(BlockSpec.TextSpec(trailingId, ""))
        }
        /**
         * 插入锚点（与旧 insertImageAtEnd 的 add(blocks.size - 1) 行为一致）：
         * - 需补尾块：等效在原列表末尾追加 [Image, 空Text] → index = size
         * - 末尾已是 Text：Image 插在它前面 → index = size - 1
         */
        val index = if (needsTrailingText) blocks.size else (blocks.size - 1).coerceAtLeast(0)
        val focusAfter = FocusSpec(
            blockId = if (needsTrailingText) trailingId!! else blocks.last<BodyBlock>().id,
            offset = 0,
        )
        return ReplaceBlocksCommand(
            index = index,
            removedSpecs = emptyList(),
            insertedSpecs = inserted,
            focusBefore = currentFocusSpec(),
            focusAfter = focusAfter,
        )
    }

    /**
     * 批量插入期间抑制 [onDocChanged]，由 [insertImagesAtFocused] 在末尾统一调一次。
     * （不然每张图都会触发一次 ViewModel.setContent/setContentFormat + 一次重组。）
     */
    private var suppressDocChanged: Boolean = false

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
     *
     * v2026-09-02 Command 化：拆块 = [ReplaceBlocksCommand]
     * （removed = [源块全文]，inserted = [前半（复用源 id）, 后半（新 id）]）。
     */
    private fun splitTextBlock(block: BodyBlock.Text, beforeEnd: Int, afterStart: Int) {
        val idx = blocks.indexOfFirst { it.id == block.id }
        if (idx < 0) return
        val text = block.state.annotatedString.text
        val beforeMd = if (beforeEnd > 0) block.state.toMarkdown(TextRange(0, beforeEnd)).replace(ZWSP, "") else ""
        val afterMd = if (afterStart < text.length)
            block.state.toMarkdown(TextRange(afterStart, text.length)).replace(ZWSP, "") else ""

        val newId = newBodyBlockId()
        executeAndPush(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(textSpec(block)),
                insertedSpecs = listOf(
                    BlockSpec.TextSpec(block.id, beforeMd),
                    BlockSpec.TextSpec(newId, afterMd),
                ),
                focusBefore = currentFocusSpec(),
                focusAfter = FocusSpec(newId, 0),
            )
        )
    }

    /**
     * 归一化：块内出现 `\n`（软键盘回车 / 粘贴多行）时按行拆成段落块。
     * 末尾连续空行保留为一个空块（回车在段尾 = 新起一段）；其余空行丢弃。
     * 焦点落到原光标所在的新块，偏移按行内位置换算。
     *
     * v2026-09-02 Command 化（拆块结果进 [ReplaceBlocksCommand]，"整块空白退化"
     * 分支同样是替换命令）。调用方是 observer（检测到 `\n` 时）——Command 的
     * apply 在 [replaying] 抑制下执行，重建的新块不含 `\n`，不会再次触发本路径。
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
            /** 整块都是空白（如全选删除后残留换行）→ 退化为单个空块（复用原 id） */
            executeAndPush(
                ReplaceBlocksCommand(
                    index = idx,
                    removedSpecs = listOf(textSpec(block)),
                    insertedSpecs = listOf(BlockSpec.TextSpec(block.id, "")),
                    focusBefore = currentFocusSpec(),
                    focusAfter = FocusSpec(block.id, 0),
                )
            )
            return
        }

        val inserted = mutableListOf<BlockSpec>()
        var focusBlockId: String? = null
        var focusOffset = 0
        for (i in 0..lastContent) {
            val (s, e) = ranges[i]
            if (e > s) {
                val md = block.state.toMarkdown(TextRange(s, e)).replace(ZWSP, "")
                if (md.isNotBlank()) {
                    val specId = if (inserted.isEmpty()) block.id else newBodyBlockId()
                    inserted += BlockSpec.TextSpec(specId, md)
                    /** 光标落点：cursor 落在这一行 → 该块 + 行内有效偏移
                     *  （区间连续覆盖全文，cursor <= e 时必有 cursor >= s，substring 安全） */
                    if (focusBlockId == null && cursor <= e) {
                        focusBlockId = specId
                        focusOffset = effectiveText(text.substring(s, cursor)).length
                    }
                    /** 行内空段（双回车产生的中间空行）丢弃：markdown 渲染中 \n\n 只是段落分隔，不是可见空行 */
                }
            }
        }
        /** 末尾有连续空行 = 回车在段尾 → 保留一个空块作为新段落 */
        val trailingBlanks = ranges.size - 1 - lastContent
        if (trailingBlanks > 0 || inserted.isEmpty()) {
            inserted += BlockSpec.TextSpec(newBodyBlockId(), "")
        }
        if (focusBlockId == null) {
            focusBlockId = (inserted.last() as BlockSpec.TextSpec).id
            focusOffset = 0
        }

        executeAndPush(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(textSpec(block)),
                insertedSpecs = inserted,
                focusBefore = currentFocusSpec(),
                focusAfter = FocusSpec(focusBlockId, focusOffset),
            )
        )
    }

    // ---------- Command 命令栈（方案A：两套历史隔离） ----------

    /**
     * **全局命令栈**：只存操作增量 [BodyBlocksCommand]（不存全量快照）——
     * 管块的增删、拖拽排序、图片块属性编辑。
     *
     * **方案A坑点4（屏幕旋转）**：controller 由 ViewModel 持有
     * （[com.corgimemo.app.viewmodel.InspirationEditViewModel]），不用 remember——
     * 配置变更（旋转）时 ViewModel 存活，命令栈与块内 history 都不丢。
     *
     * **块内富文本历史**（库自带 `RichTextState.history`）不进本栈：
     * 打字 / 加粗 / 样式由每个 Text 块自己管理，避免每敲一个字把整个块列表压栈。
     */
    private val undoCommands = ArrayDeque<BodyBlocksCommand>()
    private val redoCommands = ArrayDeque<BodyBlocksCommand>()

    /** 命令是轻量增量（对比旧快照栈），100 条深度足够 */
    private val maxCommandDepth = 100

    /**
     * Command 重放（apply / revert）与块内 history undo-redo 期间为 true。
     * BlockTextItem observer 读它：跳过"新编辑清 redo 栈"与结构检测
     * （\n 拆块 / 退格合并——重放不该再触发），但保留 ZWSP 不变量维护。
     */
    internal var replaying = false
        private set

    var canUndoBlocks by mutableStateOf(false)
        private set
    var canRedoBlocks by mutableStateOf(false)
        private set

    /**
     * 统一调度入口的可见状态：块级命令栈 ∨ 聚焦块（未聚焦时回退首块）的库内 history。
     * 与 [undo] / [redo] 的调度对象同源（focusedOrFirstTextState），按钮点亮状态
     * 与实际撤销行为严格一致。history.canUndo 是快照状态（mutableStateOf 驱动），
     * 聚焦块切换 / 块内编辑都会触发重组刷新。
     */
    val canUndo: Boolean
        get() = canUndoBlocks || focusedOrFirstTextState().history.canUndo
    val canRedo: Boolean
        get() = canRedoBlocks || focusedOrFirstTextState().history.canRedo

    /**
     * 执行并压栈一条命令（所有结构操作入口的统一出口）：
     * apply 期间 [replaying] = true，命令自身引发的状态差分不会被 observer
     * 误判为"新编辑"（不清刚清空的 redo 栈、不二次触发结构检测）。
     */
    private fun executeAndPush(command: BodyBlocksCommand) {
        replaying = true
        try {
            command.apply(this)
        } finally {
            replaying = false
        }
        pushExecuted(command)
    }

    /** 只压栈不执行（命令已被调用方 apply 过——批量插图的循环路径） */
    private fun pushExecuted(command: BodyBlocksCommand) {
        undoCommands.addLast(command)
        if (undoCommands.size > maxCommandDepth) undoCommands.removeFirst()
        redoCommands.clear()
        canUndoBlocks = true
        canRedoBlocks = false
        onDocChanged?.invoke()
    }

    /** 块级命令撤销（不含块内文字调度——那是 [undo] 的职责） */
    private fun undoBlocks(): Boolean {
        val command = undoCommands.removeLastOrNull() ?: return false
        replaying = true
        try {
            command.revert(this)
        } finally {
            replaying = false
        }
        redoCommands.addLast(command)
        canUndoBlocks = undoCommands.isNotEmpty()
        canRedoBlocks = true
        onDocChanged?.invoke()
        return true
    }

    /** 块级命令重做 */
    private fun redoBlocks(): Boolean {
        val command = redoCommands.removeLastOrNull() ?: return false
        replaying = true
        try {
            command.apply(this)
        } finally {
            replaying = false
        }
        undoCommands.addLast(command)
        canUndoBlocks = true
        canRedoBlocks = redoCommands.isNotEmpty()
        onDocChanged?.invoke()
        return true
    }

    /**
     * **统一撤销调度（方案A坑点1：焦点判断是核心）**——
     * 聚焦块（未聚焦时回退首块）的库内 history 非空 → 先回退块内富文本
     * （打字 / 删除 / 加粗等，按库的合并组粒度）；块内回退完 → 走全局命令栈。
     *
     * 这样按撤销时行为可预期：时而回退文字、时而回退块操作，但两套历史互不干扰、
     * 不会交替错乱。块内 history.undo() 由库恢复光标（快照含 selection）；
     * 命令 revert 后的焦点由各命令的 focusBefore（如 [ReplaceBlocksCommand]）决定。
     */
    fun undo(): Boolean {
        val state = focusedOrFirstTextState()
        if (state.history.canUndo) {
            replaying = true
            try {
                return state.history.undo()
            } finally {
                replaying = false
            }
        }
        return undoBlocks()
    }

    /** 统一重做调度（与 [undo] 对称：块内优先，空则命令栈） */
    fun redo(): Boolean {
        val state = focusedOrFirstTextState()
        if (state.history.canRedo) {
            replaying = true
            try {
                return state.history.redo()
            } finally {
                replaying = false
            }
        }
        return redoBlocks()
    }

    /**
     * 新的用户编辑使全局重做历史失效（BlockTextItem observer 在检测到
     * **非重放**的 markdown 变化时调用）。
     * 与库 history 的 redoStack.clear() 行为对齐——保证"redo 可达 ⇒ 各块当前
     * 内容 == 上次全局操作结束时的内容"，命令重放的记录值因此是安全的。
     */
    internal fun clearGlobalRedo() {
        if (redoCommands.isEmpty()) return
        redoCommands.clear()
        canRedoBlocks = false
    }

    /** 清空命令栈（initialize / 换文档时） */
    private fun clearCommandStacks() {
        undoCommands.clear()
        redoCommands.clear()
        canUndoBlocks = false
        canRedoBlocks = false
    }

    // ---------- Command 落盘辅助 ----------

    /**
     * 定位命令锚定的区间起点。
     *
     * 正常路径（栈式回退不变量：revert 面对的列表 == 该命令 apply 后的列表）
     * 下 [index] 处就是区间第一个块；防御性按 [anchorId]（当前列表中应存在的
     * 区间首块 id）全局搜索定位，找不到才退回 [index]。
     */
    internal fun locateRangeStart(anchorId: String?, index: Int): Int {
        if (anchorId != null) {
            val idx = blocks.indexOfFirst { it.id == anchorId }
            if (idx >= 0) return idx
        }
        return index.coerceIn(0, blocks.size)
    }

    /**
     * 把 `[index, index + removeCount)` 的块替换为 [insertSpecs] 重建的块
     * （[ReplaceBlocksCommand] 的落盘原语；id 复用保证焦点/外部引用稳定）。
     */
    internal fun replaceBlockRange(index: Int, removeCount: Int, insertSpecs: List<BlockSpec>) {
        val safeIndex = index.coerceIn(0, blocks.size)
        val safeCount = removeCount.coerceAtMost(blocks.size - safeIndex).coerceAtLeast(0)
        repeat(safeCount) { blocks.removeAt(safeIndex) }
        blocks.addAll(safeIndex, insertSpecs.map { rebuildBlock(it) })
    }

    /**
     * 把 `[index, index + removeCount)` 的块移除，并**原样插入** [restored]（[ReplaceBlocksCommand.revert]
     * 的落盘原语）。与 [replaceBlockRange] 不同：此处插入的是**已有的块对象**（带各自
     * RichTextState 历史），不做 setMarkdown 重建——从而保留命令前的块内编辑历史，
     * 修复"撤销块级命令后无法继续撤销文字"的问题。
     */
    internal fun restoreBlockRange(index: Int, removeCount: Int, restored: List<BodyBlock>) {
        val safeIndex = index.coerceIn(0, blocks.size)
        val safeCount = removeCount.coerceAtMost(blocks.size - safeIndex).coerceAtLeast(0)
        repeat(safeCount) { blocks.removeAt(safeIndex) }
        blocks.addAll(safeIndex, restored)
    }

    /** 按 [BlockSpec] 重建块（Text 走 setMarkdown 还原富文本样式；Image 只存 uri） */
    private fun rebuildBlock(spec: BlockSpec): BodyBlock = when (spec) {
        is BlockSpec.TextSpec -> createTextBlock(spec.markdown, spec.id)
        is BlockSpec.ImageSpec -> BodyBlock.Image(spec.id, spec.path)
    }

    /**
     * 把块的库内 history 撤销到底（回到命令产物的初值）。
     * [ReplaceBlocksCommand.revert] 在删除产物块前调用——让用户在产物块里
     * 尚未撤销的编辑**显式回退**（文字逐步消失可见），而不是被块级 undo 静默丢弃。
     * 必须在 [replaying] 抑制下执行（触发 observer 状态差分）。
     */
    internal fun drainBlockHistory(blockId: String) {
        val block = blocks.firstOrNull { it.id == blockId } as? BodyBlock.Text ?: return
        while (block.state.history.undo()) {
            // 撤到底：撤销期间 observer 读 replaying = true，不会误触发结构检测
        }
    }

    /** 图片块属性编辑落盘（[UpdateImageBlockCommand] 用；将来三件套沿此扩展） */
    internal fun updateImageBlockPath(blockId: String, path: String) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(idx)
        if (block is BodyBlock.Image && block.path != path) {
            blocks[idx] = BodyBlock.Image(blockId, path)
        }
    }

    /** 拖拽排序落盘（[MoveBlockCommand] 用；按 id 定位防御索引漂移） */
    internal fun moveBlockById(blockId: String, targetIndex: Int) {
        val from = blocks.indexOfFirst { it.id == blockId }
        if (from < 0) return
        val to = targetIndex.coerceIn(0, blocks.lastIndex)
        if (from == to) return
        val block = blocks.removeAt(from)
        blocks.add(to, block)
    }

    /** Command 落盘后的通用收尾：两步删除的高亮态随结构变化清除 */
    internal fun afterCommandMutation() {
        if (highlightedBlockId != null) highlightedBlockId = null
    }

    /** 捕获当前焦点落点（Command 构造时的 focusBefore） */
    private fun currentFocusSpec(): FocusSpec? {
        val focusKey = focusedBlockId ?: pendingFocusId ?: return null
        val focused = blocks.firstOrNull { it.id == focusKey } as? BodyBlock.Text ?: return null
        val rawText = focused.state.annotatedString.text
        val rawCursor = focused.state.selection.start.coerceIn(0, rawText.length)
        /** 光标从 raw 坐标映射到有效坐标（剥 ZWSP） */
        return FocusSpec(focused.id, effectiveText(rawText.substring(0, rawCursor)).length)
    }

    /** 按落点描述恢复焦点与光标（Command 的 focusBefore / focusAfter 落地） */
    internal fun focusSpec(spec: FocusSpec) {
        val target = blocks.firstOrNull { it.id == spec.blockId } as? BodyBlock.Text
        if (target == null) {
            focusFirstTextBlock()
            return
        }
        val effLen = effectiveText(target.state.annotatedString.text).length
        applyFocusAndCursor(target, spec.offset.coerceIn(0, effLen))
    }

    /**
     * 把焦点与光标**同步**落到 [target] 的 [offset]（有效文本坐标）。
     *
     * 三处一起写，缺一不可：
     * 1. [pendingFocusId] / [pendingFocusOffset]——供块 Composable 的 LaunchedEffect
     *    申请真实焦点（[FocusRequester.requestFocus] 只能异步执行）；
     * 2. [focusedBlockId]——undo/redo 后的命令焦点依赖它定位焦点块，
     *    为 null 会导致 [currentFocusSpec] 捕获到错误落点；
     * 3. [RichTextState.selection]——同步读取（如 [focusSpec] 的 coerce 上界）
     *    需要立即生效的值，等异步则会读到初始 selection。
     *
     * 只改 selection 不改 text，不会触发 BlockTextItem observer 的结构检测
     * （markdown 不含光标信息）；重放期间 [replaying] 亦为 true。
     */
    private fun applyFocusAndCursor(target: BodyBlock.Text, offset: Int) {
        pendingFocusId = target.id
        pendingFocusOffset = offset
        focusedBlockId = target.id
        /** 与 BlockTextItem 消费 pendingFocusOffset 的口径保持一致：有效坐标直接作为 raw 偏移，
         *  空块（text = ZWSP）由块内 ZWSP 维护 LaunchedEffect 兜底推到 (1, 1) */
        val rawLen = target.state.annotatedString.text.length
        target.state.selection = TextRange(offset.coerceIn(0, rawLen))
    }

    /**
     * 兜底：把焦点落到第一个 Text 块。
     * 可见性为 internal——除被本类内部的 [focusSpec] 调用外，
     * 还被同包的顶层 [ReplaceBlocksCommand]（apply/revert 中通过 controller 引用）跨类调用。
     */
    internal fun focusFirstTextBlock() {
        val firstText = blocks.firstOrNull { it is BodyBlock.Text } as? BodyBlock.Text ?: return
        applyFocusAndCursor(firstText, 0)
    }

    // ---------- 删除 / 合并 ----------

    /**
     * 按 id 删除图片块（两步删除的确认步）。
     * v2026-09-02 Command 化：removed = [ImageSpec]，inserted = []。
     */
    fun deleteImageBlock(blockId: String) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(idx)
        if (block !is BodyBlock.Image) return
        executeAndPush(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(BlockSpec.ImageSpec(block.id, block.path)),
                insertedSpecs = emptyList(),
                focusBefore = currentFocusSpec(),
                /** 删图时焦点本就不在图片上，保持当前落点即可 */
                focusAfter = currentFocusSpec(),
            )
        )
    }

    /** 按路径删除图片块（画廊删除入口），返回是否删除 */
    fun deleteImageByPath(path: String): Boolean {
        val idx = blocks.indexOfFirst { it is BodyBlock.Image && it.path == path }
        if (idx < 0) return false
        val block = blocks[idx] as BodyBlock.Image
        executeAndPush(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(BlockSpec.ImageSpec(block.id, block.path)),
                insertedSpecs = emptyList(),
                focusBefore = currentFocusSpec(),
                focusAfter = currentFocusSpec(),
            )
        )
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
                /** 下一 Text 块；列表只剩这个空块时用它换成一个新空块（保不变量） */
                val nextText = blocks.drop(1).firstOrNull { it is BodyBlock.Text } as? BodyBlock.Text
                val inserted = if (nextText == null) {
                    listOf(BlockSpec.TextSpec(newBodyBlockId(), ""))
                } else emptyList()
                executeAndPush(
                    ReplaceBlocksCommand(
                        index = 0,
                        removedSpecs = listOf(textSpec(block)),
                        insertedSpecs = inserted,
                        focusBefore = currentFocusSpec(),
                        focusAfter = if (nextText != null) {
                            FocusSpec(nextText.id, 0)
                        } else {
                            FocusSpec((inserted.first() as BlockSpec.TextSpec).id, 0)
                        },
                    )
                )
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

    /**
     * 退格合并：前块吸收后块（[ReplaceBlocksCommand]）——
     * removed = [前块, 后块]，inserted = [前块（同 id，拼接后 markdown）]。
     * undo 拆回两块（各自原 id），焦点回后块块首；redo 重放合并，焦点回接缝。
     */
    private fun mergeTextBlocks(prev: BodyBlock.Text, cur: BodyBlock.Text) {
        val prevIdx = blocks.indexOfFirst { it.id == prev.id }
        if (prevIdx < 0) return
        val prevMd = blockMarkdown(prev.state)
        val curMd = blockMarkdown(cur.state)
        /** 接缝光标（有效坐标，剥 ZWSP） */
        val junction = effectiveText(prev.state.annotatedString.text).length
        executeAndPush(
            ReplaceBlocksCommand(
                index = prevIdx,
                removedSpecs = listOf(textSpec(prev), textSpec(cur)),
                insertedSpecs = listOf(BlockSpec.TextSpec(prev.id, prevMd + curMd)),
                focusBefore = FocusSpec(cur.id, 0),
                focusAfter = FocusSpec(prev.id, junction),
            )
        )
    }

    // ---------- 重排 / 焦点 ----------

    /**
     * 拖拽排序回调（ReorderableColumn 的 onSettle——**手指抬起落定后才到达这里**，
     * 方案A坑点3：拖拽过程零压栈，一步拖拽恰好一条 [MoveBlockCommand] 撤销记录）。
     */
    fun moveBlock(from: Int, to: Int) {
        if (from == to || from !in blocks.indices || to !in blocks.indices) return
        val blockId = blocks[from].id
        executeAndPush(MoveBlockCommand(blockId = blockId, fromIndex = from, toIndex = to))
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

/**
 * v2026-09-02 方案A：controller 不再由 UI 层 remember——由
 * [com.corgimemo.app.viewmodel.InspirationEditViewModel] 持有（屏幕旋转不丢
 * 命令栈与块内 history），Screen 直接读 `viewModel.bodyBlocks`。
 */

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
     * 变更观察者（软键盘无按键事件，全部靠状态差分）。v2026-09-02 方案A改造：
     *
     * 0. **文字 / 样式的撤销不再进全局栈**——库内 `state.history` 自动记录
     *    （打字按合并组、加粗 / 列表按格式化单步），observer 只负责两件事：
     *    a. 检测到**真实新编辑**（markdown 变化且非重放）→ 清空全局 redo 栈
     *       （新编辑使命令重放不安全，与库 history 的 redoStack.clear 对齐）。
     *       **仅移动光标不清栈**：markdown 不含光标信息，selection 变化不触发；
     *    b. 结构检测：块内出现 `\n` → 拆块；块首退格 → 合并 / 两步删除；
     *       空块软键盘退格（ZWSP 唯一态变 ""）→ 走 [BodyBlocksController.onBackspaceAtStart]。
     *    两者在 [BodyBlocksController.replaying]（命令重放 / 块内 history 恢复）
     *    期间全部跳过——重放不该再触发结构检测或误清 redo 栈。
     *    ZWSP 不变量维护**不受 replaying 门控**（恢复出的空块也要预置退格锚点）。
     */
    LaunchedEffect(block.id) {
        var lastText = state.annotatedString.text
        var lastMarkdown = state.toMarkdown()
        var lastSelection = state.selection
        /** v2026-09-02：差分对象从 `annotatedString.text` 换成**整个 annotatedString**——
         *  只读 `.text` 无法感知 SpanStyle 变化，加粗/斜体/列表这类"只改样式、不改字符"
         *  的操作不会触发 collect；读整个 annotatedString 才会被 snapshot 系统追踪到。 */
        snapshotFlow { Triple(state.annotatedString, state.selection, state.composition) }
            .collect { (annotated, selection, composition) ->
                val text = annotated.text
                /** toMarkdown() 会把 SpanStyle 序列化成 `**粗体**` 等语法，
                 *  因此格式化操作也会让 markdown 变化 → 被下方条件捕获。 */
                val markdown = state.toMarkdown()
                if (isLocked) {
                    lastText = text
                    lastMarkdown = markdown
                    lastSelection = selection
                    return@collect
                }
                val backspaceMerge = lastText.isNotEmpty() && text == lastText.drop(1) &&
                    lastSelection.collapsed && lastSelection.start == 0
                val emptyBackspace = lastText == ZWSP && text == ""
                if (!controller.replaying) {
                    /** 新编辑（非命令重放、非块内 history 恢复、非 IME 组合中间态）
                     *  → 全局 redo 栈失效。退格合并 / 空块删除路径不在此清——
                     *  onBackspaceAtStart 入口的命令会统一清（pushExecuted）。 */
                    if (markdown != lastMarkdown && !backspaceMerge && !emptyBackspace &&
                        composition == null
                    ) {
                        controller.clearGlobalRedo()
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
                lastMarkdown = markdown
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
                        /** 方案A：物理键盘撤销 / 重做统一调度到 controller（焦点判断：块内
                         *  富文本 history 优先，空则全局命令栈）——与屏幕按钮同一入口，
                         *  避免快捷键绕过两套历史的调度逻辑。 */
                        Key.Z -> if (keyEvent.isCtrlPressed) {
                            if (keyEvent.isShiftPressed) controller.redo() else controller.undo()
                            true
                        } else {
                            false
                        }
                        Key.Y -> if (keyEvent.isCtrlPressed) {
                            controller.redo()
                            true
                        } else {
                            false
                        }
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
            /** v2026-09-02 方案A：库的 undo 快捷键拦截仍禁用——物理键盘 Ctrl+Z 由上方
             *  onPreviewKeyEvent 统一调度到 controller.undo()（块内 history 与全局
             *  命令栈的两套历史入口），避免快捷键绕过焦点判断直接走单块 history。 */
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
