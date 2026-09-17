package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.model.ContentBlock
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.paragraph.type.OrderedListStyleType

/**
 * # 正文块控制器（数据层）
 *
 * BlockNote 迁移（2026-09-17）后本文件仅保留**数据层**：块模型、命令栈、
 * markdown 序列化/解析工具与 [BodyBlocksController]。
 *
 * **原 UI 渲染方 `BodyBlocksEditor` 已删除**（正文编辑器已切换为 BlockNote WebView，
 * 见 `ui/screens/probe/BlockNoteEditorWebView.kt`）。备份见
 * 「弃用文件/Compose编辑器-弃用备份/」。
 *
 * 本文件之所以未随 UI 一起删除，是因为 ViewModel 的数据链路仍在依赖：
 * - 图片备注 / 缩放属性持久化（[BodyBlocksController.blocks] / applyImageProps）
 * - 旧数据媒体迁移（appendMediaMarkdown）
 * - 语音 token 插入（insertVoiceToken）
 * - 图片删除（deleteImageByPath）
 * - 格式工具栏激活态回显（focusedOrFirstTextState）
 *
 * 上述链路完成 BlockNote 侧接管后，本文件方可整体下线。
 */

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
 * **状态不变量**（原先由 `BodyBlocksEditor` 的 LaunchedEffect 强制维持，
 * BlockNote 迁移后不再有编辑器消费，保留供数据层序列化约定参考）：
 * - 逻辑空块：`state.text == ZWSP`，光标在 (1, 1)
 * - 逻辑非空块：`state.text == "<content>"`（不含 ZWSP；不在运行期强制追加）
 * - 任何 `setText(ZWSP)` 都会丢失内容，所以只能对真正空的（""）调用
 *
 * **输出剥**：toMarkdown / plainText / 高度 / 占位符判断都要看"有效文本"
 * （剥掉 ZWSP 后再判断），不让 ZWSP 泄漏到 markdown/UI 文本。
 */
private const val ZWSP = "\u200B"
/**
 * 空白块占位符（U+00A0 不换行空格）。
 *
 * 空块在编辑态以 ZWSP 预置退格锚点，其 toMarkdown 输出剥 ZWSP 后为空串。
 * 若直接把空串写进整篇 markdown，会与相邻图片块（块间以 `\n\n` 分隔）的「段间空段」
 * 混为一体，导致重新进入编辑页时图片边界多/少空块。故空块序列化时用 NBSP 占位，
 * 让该段在 markdown 中保持非空、块边界无歧义；加载时再把「整段恰为 NBSP」的段
 * 还原为空块（与编辑态空块语义一致）。
 */
private const val EMPTY_BLOCK_PLACEHOLDER = "\u00A0"

/**
 * 列表层级缩进步长（sp）：二级起每级缩进 30sp ≈ 正文 15sp 的两字符（「增加缩进」需求）。
 * 一级列表 base = 30 × (1-1) = 0，仍贴左缘（「列表无缩进」需求不变）。
 * 库公式见 `OrderedList.getNewParagraphStyle`（base = indent × (level-1)）。
 */
internal const val LIST_LEVEL_INDENT_SP = 30

/** 列表层级上限：一级贴左缘（0 缩进）+ 最多 5 次连续「增加缩进」（第 2~6 级），
 *  每级 [LIST_LEVEL_INDENT_SP]（30sp ≈ 两字符）逐级累加；到顶后再点无效。 */
private const val MAX_LIST_LEVEL = 6

/**
 * 纯文本整段缩进（v2026-09-07 第二版，取代首版 EM 文本前缀方案）：
 * 缩进是**段落属性**（库 [DefaultParagraph.level]，样式 TextIndent firstLine = restLine，
 * 整段左移），不是文本字符——EM 文本前缀只能实现首行缩进（折行后第二行顶格），
 * 不符合「整体缩进」需求。
 *
 * markdown 持久化载体仍为段首全角空格（[PLAIN_INDENT_CHAR]，每级 [PLAIN_INDENT_STEP] 个），
 * 由**库编解码两端透明处理**（编码端 appendParagraphStartText 输出前缀 / 解码端 PARAGRAPH
 * 钩子剥前缀还原 level）——拆块/合并/保存/加载等一切 markdown 路径自动往返，App 只需：
 * - 缩进按钮调库 API [RichTextState.setParagraphIndent]；
 * - 层级读 markdown 前缀（[plainIndentLevelOfMd]，与 listLevelOfMd 同款模式）。
 * U+2003 是普通文本字符，CommonMark 不视作缩进/代码块前缀，持久化无损。
 */
private const val PLAIN_INDENT_CHAR = '\u2003'

/** 纯文本缩进步长：每个缩进层级对应的段首全角空格个数（≈ 两字符宽）。
 *  internal：复选框块缩进档位的载体步长（v2026-09-08，渲染侧按它换算每级 padding） */
internal const val PLAIN_INDENT_STEP = 2

/**
 * 读 markdown 的列表层级（1 起）：库对嵌套列表按每级 2 空格前缀编码
 * （`"  ".repeat(type.level - 1) + marker`，见 MarkdownParser 的
 * appendParagraphStartText），故前导空格数 / 2 + 1 即层级。
 *
 * internal：编辑页块与详情页段落的复选框标识缩进换算共用（v2026-09-07）。
 */
internal fun listLevelOfMd(md: String): Int {
    val leading = md.indexOfFirst { it != ' ' }.let { if (it < 0) md.length else it }
    return leading / 2 + 1
}

/**
 * 单行列表段判定（v2026-09-08）：段首可选半角空格缩进 + 列表 marker + 空格。
 * marker 形态与库编码端/渲染端一致：`- `/`* `/`+ `（无序）、`N. `/`N) `（有序）。
 *
 * 用于加载端（[BodyBlocksController.initialize]）与详情页渲染
 * （InspirationBodyParagraph）识别「孤立列表行」——**不匹配多行段**（调用方需
 * 另行排除 `\n`，多行嵌套段的列表结构由 AST 完整解析，前缀不可剥）。
 */
internal val SingleLineListMdRegex = Regex("""^\s*(?:[-*+] |\d+[.)] )""")

/**
 * 读 markdown 的有序编号（字面数字）：先剥层级缩进前导空格再匹配 `^(\d+)\.`
 * （库对有序列表始终序列化为 `"N. "` 字面编号；嵌套行带前导空格，正则须容忍）。
 *
 * 顶层 internal：编辑页（拆块续号/层级还原）与详情页（列表段 setListMarker 回填）
 * 共用（v2026-09-08 由 controller 内 private 提升）。
 *
 * @return 当前编号（如 `"  2. 测试"` → 2）；无编号前缀返回 null。
 */
internal fun orderedNumberOfMd(md: String): Int? =
    Regex("^(\\d+)\\.").find(md.trimStart(' '))?.groupValues?.get(1)?.toIntOrNull()

/**
 * 读 markdown 的纯文本缩进层级（1 = 无缩进；段首全角空格每级 2 个，
 * 由库编码端对 [DefaultParagraph.level]>1 输出）。
 * internal：普通段落缩进载体（EM 前缀）解析用它读档位（v2026-09-08）。
 */
internal fun plainIndentLevelOfMd(md: String): Int =
    md.countLeadingPlainIndentChars() / PLAIN_INDENT_STEP + 1

/** 纯文本缩进的 markdown 前缀：level 级 = 段首 EM×2×(level-1)（与库编码端同款）。
 *  internal：复选框块缩进载体（v2026-09-08）拼接/解析共用 */
internal fun plainIndentPrefix(level: Int): String =
    PLAIN_INDENT_CHAR.toString().repeat(PLAIN_INDENT_STEP * (level - 1))

/**
 * 空白行判定：剥 ZWSP / 图片占位后剩余内容全是空白字符（含软键盘回车插入的
 * `\n`）——「空行」可携带缩进属性，供空缩进行回车逐级返回的判定使用
 * （[isEffectivelyEmpty] 不剥 `\n`，软键盘回车后的空块文本 "\u200B\n" 判不出来）。
 */
private fun isEffectivelyBlankLine(state: RichTextState): Boolean =
    effectiveText(state.annotatedString.text).isBlank()

/** 读 markdown 的前导全角空格（U+2003）个数 */
private fun String.countLeadingPlainIndentChars(): Int {
    var n = 0
    for (ch in this) {
        if (ch != PLAIN_INDENT_CHAR) break
        n++
    }
    return n
}

/** 剥掉 markdown 的纯文本缩进前缀（退格合并时丢弃后块缩进，见 [BodyBlocksController.mergeTextBlocks]） */
/** 剥掉 markdown 的纯文本缩进前缀（复选框块缩进载体解析也用它，v2026-09-08） */
internal fun String.dropLeadingPlainIndent(): String =
    drop(countLeadingPlainIndentChars())

/** 二级 marker：带括号阿拉伯数字 `"(1) "`（用户指定层级循环 1./(1)/①/a./Ⅰ./i.） */
private object ParenthesizedDecimalStyle : OrderedListStyleType {
    override fun format(number: Int, listLevel: Int): String = "($number)"
    override fun getSuffix(listLevel: Int): String = " "
}

/** 三级 marker：带圈数字 `"① "`（U+2460 起，1~20；超出范围按 ⑳ 封顶） */
private object CircledDecimalStyle : OrderedListStyleType {
    override fun format(number: Int, listLevel: Int): String =
        (0x2460 + number.coerceIn(1, 20) - 1).toChar().toString()
    override fun getSuffix(listLevel: Int): String = " "
}

/**
 * 有序列表 marker 按层级循环样式（v2026-09-05 缩进按钮需求，用户指定）：
 * 一级 `1.` / 二级 `(1)` / 三级 `①` / 四级 `a.` / 五级 `Ⅰ.` / 六级 `i.`
 * （更深 clamp 到最后一级）。
 * 库默认 Multiple(Decimal, LowerRoman, LowerAlpha) 的二级是小写罗马 "i."，不符用户预期，故覆盖。
 * 注意：markdown 序列化恒为字面 `"N. "`（库编码器硬编码，与样式无关），样式纯视觉、
 * 由 config 在渲染端还原；编号（位置语义）与层级（前缀空格）照常持久化。
 */
internal val AppOrderedListStyleType: OrderedListStyleType =
    OrderedListStyleType.Multiple(
        OrderedListStyleType.Decimal,
        ParenthesizedDecimalStyle,
        CircledDecimalStyle,
        OrderedListStyleType.LowerAlpha,
        OrderedListStyleType.UpperRoman,
        OrderedListStyleType.LowerRoman,
    )

/**
 * 剥除"非用户可见内容"字符后取有效文本，用于空行判定 / 字数统计 / 复制全文：
 * - ZWSP：空块退格锚点 + 任务列表段落的 **marker 零宽占位字符**（v2026-09-16 从
 *   NBSP 换成 ZWSP 以消除行 0 缩进错位，见库 `TaskList.TaskListMarkerText`）——它
 *   出现在 `annotatedString` 里（每个任务列表段一个），但不该被算进正文文本；
 * - [IMAGE_PLACEHOLDER_CHAR]：库的图片内联占位符；
 * - **NBSP（U+00A0）**：空块的 markdown 序列化占位段（[EMPTY_BLOCK_PLACEHOLDER]）
 *   与旧版任务列表 marker（存量文本），一并剥除；
 * - [PLAIN_INDENT_CHAR]（EM）：缩进载体。普通段落的段首缩进在 markdown 层（加载时
 *   已剥），但「相邻文本块合并」会把后块的缩进差异转写成**块内行首 EM**
 *   （v2026-09-15），故一并剥除，避免污染字数统计与空行判定。
 */
private fun effectiveText(text: String): String = text.filterNot { char ->
    when (char) {
        ZWSP[0] -> true
        IMAGE_PLACEHOLDER_CHAR -> true
        '\u00A0' -> true
        PLAIN_INDENT_CHAR -> true
        else -> false
    }
}
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
        /**
         * 缩进档位（v2026-09-08 新增，1 = 无缩进，上限 [MAX_LIST_LEVEL]）。
         *
         * **为什么不走库的段落缩进（TextIndent）**：真机实测库对纯文本段的
         * TextIndent 渲染量与理论公式不符（约为公式 2 倍），故**普通段落**缩进改用
         * **App 布局级缩进**：编辑器在 Row 内加 `indentLevel` 对应的 start padding，
         * 与库渲染行为完全解耦。
         *
         * ⚠️ v2026-09-15 起本字段**只服务普通段落**：复选框（任务列表）已下沉为库的
         * 段落类型（`TaskList`），勾选框与缩进都由库按段落承载（markdown 前缀
         * `  - [ ] `），不再走本字段，也不再需要块级勾选态。
         *
         * 持久化载体：markdown 内容之前的全角空格（U+2003，每级 [PLAIN_INDENT_STEP] 个），
         * 由 [plainIndentLevelOfMd] 解析、[BodyBlocksController.toMarkdown] 拼接
         * ——**只在块边界处理、不进 state**。缩进操作经
         * [BodyBlocksController.setBlockIndent] 就地换块对象（state/history/光标无损）。
         */
        val indentLevel: Int = 1,
        /**
         * 是否为「图片载体空块」（v2026-09-10 新增）：编辑器为「两图之间可输入」自动补的
         * 空 Text 块标记为 true；用户手打的空行恒为 false。
         *
         * **为什么不进 markdown**：载体与用户空行在序列化里同为 NBSP 占位段
         * （[EMPTY_BLOCK_PLACEHOLDER]），文本层面无法区分；标记只是编辑会话的内存身份，
         * 载入时由 [BodyBlocksController.initialize] 按**位置**（恰好夹在两张图片之间）
         * 认领回来，命令重建往返由 [BlockSpec.TextSpec.isImageSeparator] 透传。
         *
         * **用途**：维持不变量「载体数 == 图片相邻对数，且每个都夹在两图之间」。
         * 拖拽换位后旧载体若漂到图片组外侧，[BodyBlocksController.normalizeImageSeparators]
         * 会把它删掉（可撤销）——否则每交换一次空行就多一行（"图片上下空行越来越多"）。
         *
         * ⚠️ **身份与内容绑定（v2026-09-10 修复）**：用户往载体里打了字，它就不再是载体，
         * 必须由 [BodyBlocksController.onBlockContentChanged] **即时清掉**本标记，
         * 否则换位时会被当成"漂移载体"连同用户输入一起删掉。
         * [BodyBlocksController.normalizeImageSeparators] 另有一条"有内容一律不删"的防御判据。
         */
        val isImageSeparator: Boolean = false,
    ) : BodyBlock()

    /**
     * 图片块（v2026-09-09 扩展：备注 + 缩小态）。
     *
     * @param note 图片备注（null = 未添加）。编辑页图片下方降级小字显示；
     *   由「备注」按钮创建/编辑，onValueChange 直写（与正文文字同级，不进全局命令栈）。
     *   **暂为编辑会话内存态**——markdown/Room 落库待既定的图片属性 migration 接入。
     * @param shrunk 是否处于缩小态（宽度 = 原尺寸的一半）。「缩小/恢复原尺寸」按钮切换，
     *   走 [UpdateImagePropsCommand] 可撤销。
     */
    class Image(
        override val id: String,
        val path: String,
        val note: String? = null,
        val shrunk: Boolean = false,
    ) : BodyBlock()

    /**
     * 分割线块（v2026-09-07 新增；v2026-09-11 样式化）：无内容的纯视觉块（一条水平线）。
     *
     * - markdown 载体为独占段（CommonMark thematic break，按 [style] 取
     *   [DividerStyle.markdown]，默认 `"---"`；见 [DIVIDER_MD]）；
     * - 无 RichTextState，不参与字数统计（[BodyBlocksController.plainText] 只聚合 Text 块）；
     * - 交互（v2026-09-08 第四版：删除走悬浮按钮）：
     *   - **点击** → 切换高亮（[BodyBlocksController.onDividerTapped]），全程不动焦点；
     *   - **删除** → 高亮时悬浮在分割线上方的"删除"按钮（[BlockDividerItem]，x 跟随
     *     点击手指位置）→ [BodyBlocksController.deleteDividerBlock]（那一行变空行、
     *     焦点落空行行首、可撤销）；
     *   - **光标存在时**（[BodyBlocksController.onBackspaceAtStart] /
     *     [BodyBlocksController.onDeleteAtEnd]）→ 两步删除：第一次退格/删除先高亮，
     *     第二次删除，语义与图片块一致；
     *   - **样式**（v2026-09-11）→ 工具条虚线/波浪线按钮切换（[BodyBlocksController.toggleDividerStyle]，
     *     可撤销），激活态再点恢复实线；
     * - 可参与拖拽排序（[MoveBlockCommand] 按块 id 移动，对此类型透明）。
     */
    class Divider(
        override val id: String,
        /** 线条样式（v2026-09-11）：实线（默认）/ 虚线 / 波浪线，随 markdown 往返 */
        val style: DividerStyle = DividerStyle.SOLID,
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
/**
 * 列表类型（拆块时用于让新行继续列表）。
 * 与 [com.mohamedrejeb.richeditor.model.RichTextState] 的列表样式对应，
 * 在此处独立定义以避免 App 层在 spec 中直接流转子模块内部类型。
 */
enum class InheritedListType {
    Unordered,
    Ordered,
}

sealed class BlockSpec {
    abstract val id: String

    /**
     * Text 块：markdown 剥过 ZWSP（见 [BodyBlocksController.blockMarkdown]）
     */
    data class TextSpec(
        override val id: String,
        val markdown: String,
        /** 拆块/归一化时新块继承的列表类型；null = 不强制，沿用 markdown 自带样式 */
        val initialListType: InheritedListType? = null,
        /** 拆块续行时新有序列表块的起始编号（源块编号 +1）；null = 不指定，沿用默认/字面编号 */
        val orderedStartNumber: Int? = null,
        /** 块的列表层级（1 起，源块层级，有序/无序通用）；null = 一级。
         *  层级不依赖 markdown 前缀重建（独立块 ≥4 空格前缀会被 CommonMark 当代码块），
         *  而是重建后经 [RichTextState.setListMarker] 直接设置。 */
        val listLevel: Int? = null,
        /**
         * 缩进档位（v2026-09-08 新增，1 = 无缩进；v2026-09-15 起**只服务普通段落**）：
         * [markdown] 载荷**不含**缩进（EM 前缀在 [BodyBlocksController.toMarkdown] 拼接、
         * [BodyBlocksController.initialize] 解析），state 干净、不参与库的段落缩进。
         * 复选框（任务列表）的缩进改由库的段落层级承载，与本字段无关。
         */
        val indentLevel: Int = 1,
        /**
         * 是否为「图片载体空块」（v2026-09-10 新增）：随 spec 往返，保证
         * 撤销/重做重建块时不丢载体身份（重建走 [BodyBlocksController.rebuildBlock]）。
         * 该字段**不进 markdown**（载体与用户空行同为 NBSP 占位段），也不参与序列化。
         */
        val isImageSeparator: Boolean = false,
    ) : BlockSpec()

    /**
     * Image 块：路径 + 备注 + 缩小态（v2026-09-09 扩展后两者随命令 spec 往返，
     * 防止重建路径丢属性；既有构造点默认值不变）。
     */
    data class ImageSpec(
        override val id: String,
        val path: String,
        val note: String? = null,
        val shrunk: Boolean = false,
    ) : BlockSpec()

    /**
     * Divider 块（v2026-09-07；v2026-09-11 加样式载荷）：样式随 spec 往返——
     * 删除带样式分割线后撤销还原（[ReplaceBlocksCommand] 用 removedSpecs 重建）
     * 不丢虚线/波浪；既有构造点默认值（实线）不变。
     */
    data class DividerSpec(
        override val id: String,
        val style: DividerStyle = DividerStyle.SOLID,
    ) : BlockSpec()
}

/**
 * 焦点落点描述：块 id + 块内光标偏移。
 *
 * **offset 一律为原始坐标（raw offset）**——直接索引进对应块的
 * [RichTextState.annotatedString.text]（含前导 ZWSP 偏移），
 * [focusSpec] / [applyFocusAndCursor] / [BlockTextItem] 的 LaunchedEffect 都按 raw 直接写
 * [RichTextState.selection]。
 *
 * ⚠️ 历史坑（方案A撤销回归 #2）：曾把 offset 存成「有效坐标（剥 ZWSP）」，导致直接打字产生的块
 * （如 `\u200B一二`）撤销后光标错位到『一』左边。故：本类所有生产者（[currentFocusSpec]、
 * 各 Command 构造）都必须输出 raw；消费者无需换算。
 *
 * 唯一例外是 [mergeTextBlocks] 里对「旧块」算折行接缝时用了
 * [effectiveText]，但那是把旧块有效字数映射到「重建后的无 ZWSP 新块」的 raw 偏移，属有意换算，
 * 不要误改成对旧块取 raw（旧块带 ZWSP 会整体 -1）。
 */
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
 * 图片载体空块插入命令（v2026-09-09 新增）：在 [index] 处插入一个**空 Text 块**。
 *
 * **用途**：保证任意两个 Image 块之间都留有一行可输入的空白块——插入图片 /
 * 拖拽排序都可能造成「图-图相邻」，此时用户无法在两图之间打字。
 *
 * 与 [ReplaceBlocksCommand] 的三点差异（这正是另开一个命令类的原因）：
 * 1. **完全不动焦点**：拖拽落定后不该抢焦点、弹软键盘（[ReplaceBlocksCommand]
 *    会按 focusAfter 落焦、缺失时回退 [BodyBlocksController.focusFirstTextBlock]）；
 * 2. **不暂存原始块**：唯一产物就是那个空 Text 块，revert 直接按 [spec.id] 移除；
 * 3. **不重建任何已有块**：纯插入，不影响其它块的 RichTextState 历史。
 *
 * 生成来源（v2026-09-11 懒插入改版，两类）：
 * 1. [BodyBlocksController.normalizeImageSeparators] 补插那一支——两图直接相邻时自动补；
 * 2. [BodyBlocksController.insertEdgeSeparator]——首/尾图前/后由用户点击边缘空白**懒插入**。
 * 配套的 [RemoveImageSeparatorCommand] 负责删除漂移/多余的载体，两者共同维持
 * 「两图之间 / 首图之前 / 尾图之后（若已点出）各有恰好一个载体」的不变量。
 */
class InsertImageSeparatorCommand(
    /** 插入位置（插到该索引**之前**，即「后一张图」的索引） */
    val index: Int,
    /** 空 Text 块描述（markdown 为空 + isImageSeparator=true → 重建为带标记的载体空块） */
    val spec: BlockSpec.TextSpec,
) : BodyBlocksCommand {
    override fun apply(controller: BodyBlocksController) {
        controller.insertBlockAt(index, spec)
        controller.afterCommandMutation()
    }

    override fun revert(controller: BodyBlocksController) {
        controller.removeBlockById(spec.id)
        controller.afterCommandMutation()
    }
}

/**
 * 图片载体空块删除命令（v2026-09-10 新增）：[InsertImageSeparatorCommand] 的对称操作。
 *
 * **用途**：清理两类"不该存在"的载体空块——
 * 1. **漂移载体**：拖拽换位后旧载体留在原索引（图片走了）→ 落到了图片组外侧；
 * 2. **间隙冗余**：同一处「图-图间隙」里多于一个空白块（历史堆积）。
 *
 * 与 [InsertImageSeparatorCommand] 对称：
 * - `apply` 按 id 移除（顺序无关，因此一批删除命令的先后不影响结果）；
 * - `revert` 按 [index] 插回并**复用 [spec.id]**，索引经
 *   [BodyBlocksController.locateRangeStart] 做防御性定位（先按 id 找，找不到才用记录的索引）。
 *
 * 与插入命令一样**完全不动焦点**、不重建任何已有块。
 */
class RemoveImageSeparatorCommand(
    /** 被删载体的块 id */
    val blockId: String,
    /** 被删载体的描述（revert 时按原 id 原样重建） */
    val spec: BlockSpec.TextSpec,
    /** 删除时的位置（revert 还原用；正常路径下由栈式不变量保证仍然正确） */
    val index: Int,
) : BodyBlocksCommand {
    override fun apply(controller: BodyBlocksController) {
        controller.removeBlockById(blockId)
        controller.afterCommandMutation()
    }

    override fun revert(controller: BodyBlocksController) {
        controller.insertBlockAt(controller.locateRangeStart(blockId, index), spec)
        controller.afterCommandMutation()
    }
}

/**
 * 图片块属性更新命令（v2026-09-09 新增 UI 入口：缩小/恢复原尺寸）。
 *
 * 缩小（shrunk）是显式用户操作，与既有块级操作同级——进全局命令栈、可撤销；
 * apply / revert 均为就地换块对象（Image 块无 RichTextState，重建零损失）。
 * 备注（note）不走本命令：备注输入与正文打字同级，onValueChange 直写、不进全局栈。
 */
class UpdateImagePropsCommand(
    val blockId: String,
    val oldNote: String?,
    val oldShrunk: Boolean,
    val newNote: String?,
    val newShrunk: Boolean,
) : BodyBlocksCommand {

    override fun apply(controller: BodyBlocksController) {
        controller.updateImagePropsById(blockId, newNote, newShrunk)
        controller.afterCommandMutation()
    }

    override fun revert(controller: BodyBlocksController) {
        controller.updateImagePropsById(blockId, oldNote, oldShrunk)
        controller.afterCommandMutation()
    }
}

/**
 * 分割线样式切换命令（v2026-09-11 新增 UI 入口：工具条虚线/波浪线按钮）。
 *
 * 与 [UpdateImagePropsCommand] 同模式：显式用户操作、进全局命令栈可撤销；
 * apply / revert 均为就地换块对象（Divider 块无 RichTextState，重建零损失）。
 */
class UpdateDividerStyleCommand(
    val blockId: String,
    val oldStyle: DividerStyle,
    val newStyle: DividerStyle,
) : BodyBlocksCommand {

    override fun apply(controller: BodyBlocksController) {
        controller.updateDividerStyleById(blockId, newStyle)
        controller.afterCommandMutation()
    }

    override fun revert(controller: BodyBlocksController) {
        controller.updateDividerStyleById(blockId, oldStyle)
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
 * 块缩进档位变更命令（v2026-09-08）：「增加 / 减少缩进」按钮对**普通段落**生效。
 *
 * **App 布局级缩进**：普通段落的缩进不走库的段落 TextIndent（其渲染量与理论公式
 * 不符），而是记在 [BodyBlock.Text.indentLevel] 上，渲染由 App 侧 start padding
 * 承载。命令只翻转档位（就地换块对象，state / history / 光标无损）；markdown 载体
 * （段首 EM 前缀）由 toMarkdown 按 indentLevel 生成，随 onDocChanged 保存。
 *
 * ⚠️ 复选框（任务列表）不适用本命令：其缩进由**库**的段落层级承载（v2026-09-15）。
 */
class SetBlockIndentCommand(
    val blockId: String,
    val oldLevel: Int,
    val newLevel: Int,
) : BodyBlocksCommand {

    override fun apply(controller: BodyBlocksController) {
        controller.setBlockIndent(blockId, newLevel)
        controller.afterCommandMutation()
    }

    override fun revert(controller: BodyBlocksController) {
        controller.setBlockIndent(blockId, oldLevel)
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

// ==================== 分割线（v2026-09-07） ====================

/**
 * 分割线的 markdown 载体：CommonMark thematic break，独占一个段落（块间以 `\n\n` 连接）。
 *
 * 只识别 App 自身生成的三种形态（v2026-09-11 样式化扩展）：`"---"`（实线默认）、
 * `"--- dashed"`（虚线）、`"--- wavy"`（波浪线）——后缀即样式标记，保守避免把用户
 * 手输的 `***` / `- - -` 等变体误判成分割线；编辑页 [BodyBlocksController.initialize]
 * 与详情页 [com.corgimemo.app.ui.screens.inspiration.components.InspirationViewCard]
 * 共用本判定，保证两端往返一致。
 */
internal const val DIVIDER_MD = "---"

/** 虚线分割线的 markdown 载体（`---` + 样式后缀，v2026-09-11） */
internal const val DIVIDER_MD_DASHED = "--- dashed"

/** 波浪线分割线的 markdown 载体（`---` + 样式后缀，v2026-09-11） */
internal const val DIVIDER_MD_WAVY = "--- wavy"

/**
 * 分割线样式（v2026-09-11 工具条切换）：实线（默认）/ 虚线 / 波浪线。
 * [markdown] 为对应的序列化载体段（与 [parseDividerStyle] 解析对称）。
 */
enum class DividerStyle(val markdown: String) {
    SOLID(DIVIDER_MD),
    DASHED(DIVIDER_MD_DASHED),
    WAVY(DIVIDER_MD_WAVY),
}

/** 判断单段 markdown（已按 `\n\n` 拆出）是否为分割线段（三种样式形态任一） */
internal fun isDividerMarkdown(para: String): Boolean = parseDividerStyle(para) != null

/**
 * 解析单段 markdown 的分割线样式；非分割线段返回 null。
 * 加载（[BodyBlocksController.initialize]）与预览渲染按此取样式。
 */
internal fun parseDividerStyle(para: String): DividerStyle? = when (para.trim()) {
    DIVIDER_MD -> DividerStyle.SOLID
    DIVIDER_MD_DASHED -> DividerStyle.DASHED
    DIVIDER_MD_WAVY -> DividerStyle.WAVY
    else -> null
}

// ==================== 任务列表 / 复选框（v2026-09-15：下沉到库的段落类型） ====================

/**
 * GFM 任务列表段前缀：`- [ ] `（未勾选）/ `- [x] `（已勾选），独占一个段落
 * （块间以 `\n\n` 连接）。
 *
 * **v2026-09-15 起的职责划分**：任务列表已下沉为 richeditor 的 `TaskList` 段落类型——
 * 前缀的**产出与解析都由库完成**（编码端 `appendParagraphStartText`，解码端按
 * `LIST_ITEM` 源码前缀识别），勾选框绘制、勾选态、层级缩进也全在库内；块内多行时
 * 每行都会自动获得自己的勾选框（与列表 bullet 完全同机制）。
 *
 * App 侧只保留两个纯文本工具（供加载路径使用）：
 * - [isTaskListMd]：判断一段 markdown 是否为任务列表段；
 * - [normalizeTaskListMd]：把**旧数据**的 EM 缩进载体折算成库认识的层级空格前缀。
 *
 * 保守只认段首**恰好**为这两种前缀的形态（App 与库自身生成的唯一形态；与
 * [DIVIDER_MD] 同哲学），避免把用户手输的 `- [X] ` / `-[ ]` 等变体误判。
 */
private val TASK_LIST_MD_REGEX = Regex("""^\s*(- \[[ xX]\] )(.*)$""", RegexOption.DOT_MATCHES_ALL)

/** 任务项空行的**光标占位字符**（NBSP，v2026-09-16）：任务段落的行内没有实宽字符时
 *  （回车产生的空行、markdown 空任务项），行宽为 0、光标退化到行盒左缘（勾选框
 *  左侧）；NBSP 有宽度且不可见（[isSkipChar] 剥除、库 `trim()` 不剥——非
 *  `Char.isWhitespace`），撑起光标与行几何。 */
private const val TASK_ITEM_NBSP = "\u00A0"

/** 未勾选任务列表项的 markdown 前缀（与库的编码形态一致，v2026-09-15）。
 *
 * v2026-09-16：尾随一个 **NBSP**——children 全空 + 零宽 marker 时行宽为 0，光标
 * 定位退化到行盒左缘（跑到勾选框左侧，真机实测）；NBSP 有宽度且不可见，把光标
 * 撑到框右侧、与上下文对齐。App 字数统计（isSkipChar）/库 trim 都不剥它，随
 * markdown 往返稳定；正式内容由用户输入后插在 NBSP 之前/之后均可。
 */
internal const val TASK_LIST_MD_UNCHECKED = "- [ ] " + TASK_ITEM_NBSP

/** 是否为任务列表段（`- [ ] ` / `- [x] ` 开头，v2026-09-15 加载路径用） */
internal fun isTaskListMd(para: String): Boolean = TASK_LIST_MD_REGEX.containsMatchIn(para)

/**
 * 任务列表段 markdown 归一（v2026-09-15 加载路径）。
 *
 * **背景**：库的 `TaskList` 段落把缩进编码为段首空格前缀（每级 2 个，与列表一致），
 * 而旧数据用的是 App 自管的 EM 载体（`- [ ] ` 之后的全角空格，每级
 * [PLAIN_INDENT_STEP] 个）。这里把后者折算成前者，使缩进改由库的段落层级承载——
 * 块内多行时每行自动缩进，比原来"整块布局 padding"更正确。
 *
 * @param para 单段 markdown（已按 `\n\n` 拆出，且 [isTaskListMd] 为 true）。
 * @return 归一后的 markdown；新数据（库直接产出、已是空格前缀）原样返回。
 */
internal fun normalizeTaskListMd(para: String): String {
    val match = TASK_LIST_MD_REGEX.find(para) ?: return para
    val prefix = match.groupValues[1]
    val body = match.groupValues[2]

    val indentLevel = body.countLeadingPlainIndentChars() / PLAIN_INDENT_STEP + 1
    /** 剥历史数据尾部 NBSP（v2026-09-16 空任务项光标占位），内容非空时保持干净 */
    val content = (if (indentLevel > 1) body.dropLeadingPlainIndent() else body)
        .trimEnd('\u00A0')

    /** 层级空格前缀：库解码端按「源码行首缩进 ÷ 2」还原 level（与列表同款）。
     * 空任务项补回 NBSP（v2026-09-16，见 [TASK_ITEM_NBSP]——光标占位）。 */
    return "  ".repeat(indentLevel - 1) + prefix +
        if (content.isEmpty()) TASK_ITEM_NBSP else content
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
    /**
     * 是否 debug 构建（v2026-09-10 新增）：开启后会在每次载体归一化后做**不变量自检**
     * （见 [checkImageSeparatorInvariant]），违反时只写 Logcat 日志。
     *
     * 项目**没有 BuildConfig**（见项目约定：版本走 getPackageInfo().versionName），
     * 故由 ViewModel 用 `ApplicationInfo.FLAG_DEBUGGABLE` 判定后传入；默认 false =
     * 不检（release 与测试代码无需关心）。
     */
    private val isDebugBuild: Boolean = false,
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

    /**
     * 图片「缩小/恢复」动画是否已脱离加载回填期（v2026-09-11 修复重进页闪烁）：
     *
     * **为什么需要它**：[BodyBlock.Image.shrunk] 是内存字段，持久化真相在 DB 的
     * `displayWidthRatio`。[initialize] 解析 markdown 只还原 path（shrunk=false），
     * 随后 [applyImageProps] 才把 shrunk 回填为 true——这次**后置翻转**会被
     * [BlockImageItem] 的 `animateFloatAsState` 误判为「值变化」而播放 1f→0.5f
     * 的缩小动画，表现为重进页时缩小图「从大到小变一下」。
     *
     * 解法：加载回填期间本标志为 false，`animateFloatAsState` 用 [snap]（瞬时、
     * 不播动画），回填造成的 shrunk 翻转直接定格；回填完成（[markImagePropsRestored]）
     * 后本标志置 true，用户主动点「缩小/恢复」才走 [tween] 平滑缩放。
     */
    internal var loadRestoreComplete by mutableStateOf(false)
        private set

    /** 加载回填结束：放开「缩小/恢复」的平滑动画（见 [loadRestoreComplete] 注释） */
    internal fun markImagePropsRestored() {
        loadRestoreComplete = true
    }

    /**
     * 加载回填开始前重置（v2026-09-11）：ViewModel 若跨「退出再进入」被保留，
     * [loadRestoreComplete] 可能残留为 true，会让第二次进入的回填翻转又走 [tween]
     * 复现闪烁。每次进入都先关掉平滑动画，回填造成的 shrunk 翻转走 [snap] 瞬时定格。
     */
    internal fun resetImagePropsRestore() {
        loadRestoreComplete = false
    }

    /** 两步删除 / 点击选中的高亮块 id（图片块与分割线块，v2026-09-07 起含分割线） */
    var highlightedBlockId by mutableStateOf<String?>(null)
        private set

    /**
     * 点选（点击分割线）时的手指 x（px，相对分割线行）；null = 高亮**非**来自点击。
     *
     * 它同时承担"高亮来源"的判据（v2026-09-08）：退格 / Delete 两步删除点亮的高亮
     * **不弹悬浮删除按钮**（[highlightForTwoStepDelete] 置 null），避免按钮凭空出现在
     * 行首或上次点击的旧位置；只有 [onDividerTapped] 点亮才记录手指位置并弹按钮。
     * [BlockDividerItem] 以 `highlightedBlockId == 本块 && highlightedTapX != null`
     * 决定是否渲染按钮。
     */
    var highlightedTapX by mutableStateOf<Float?>(null)
        private set

    /**
     * 图片点选态（悬浮工具栏可见）的块 id（v2026-09-09）。
     *
     * 与 [highlightedBlockId] 分离的原因：退格 / Delete 两步删除也会高亮图片块
     * （[highlightForTwoStepDelete]，只设 highlightedBlockId），但那不是点选——
     * **不应弹出工具栏**（与分割线用 highlightedTapX 区分高亮来源同理，图片
     * 没有 tapX 需求，用独立字段表达）。UI 以 `imageToolbarBlockId == 本块 id`
     * 决定是否渲染工具栏。
     */
    var imageToolbarBlockId by mutableStateOf<String?>(null)
        private set

    /**
     * 待落焦描述（块 id + 光标偏移，原子打包）。
     * 原实现用两个独立 [mutableStateOf]（pendingFocusId / pendingFocusOffset），
     * [LaunchedEffect] 以 id 为 key 重发射、再读 offset——两条独立状态在快照边界上可能
     * 读到过期 offset（旧交互残留的 0），把已正确设好的光标覆盖成 0。
     * 改为单一 [FocusSpec]，id 变化与 offset 是同一次写入，从根上消除该竞态。
     *
     * 消费方式：块 Composable 通过 [takePendingFocus]「取走即清空」——仅命中块（id 匹配）
     * 取走一次后把本字段置空，避免同帧其它块 effect 读到过期/重复值。
     */
    internal var pendingFocus by mutableStateOf<FocusSpec?>(null)

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
    /** 列表 marker 前缀（markdown 形态）：`"- "`/`"* "`/`"+ "`/`"1. "`/`"1) "`，允许前导缩进与可选空格 */
    private val ListMarkerPrefixRegex = Regex("^\\s*([-*+]|\\d+[.)])\\s*")

    /**
     * 判断列表块是否为“空列表项”：仅有 marker（• / 1. 等）而无实际文字。
     * 用于回车退出列表的判断（空列表项回车 → 普通空行）。
     * 用 markdown 去掉列表前缀后判断是否还剩文字，规避 marker 作为内联文本
     * 导致 [RichTextState.annotatedString] 非空造成的误判。
     */
    private fun isEmptyListItem(state: RichTextState): Boolean {
        if (!state.isList) return false
        val md = state.toMarkdown().replace(ZWSP, "").trim()
        return isMarkerOnlyMarkdown(md)
    }

    /**
     * 判断 markdown 片段是否「仅列表 marker（或纯空白）」：去掉可选 marker 前缀后内容为空白。
     * 与 [isEmptyListItem] 同口径，但作用于 markdown 字符串而非 state——用于拆块时识别
     * range 版 toMarkdown 对 ZWSP-only 尾部产出的 marker-only 片段（如 `"2. "`/`"• "`）。
     */
    private fun isMarkerOnlyMarkdown(md: String): Boolean =
        md.replaceFirst(ListMarkerPrefixRegex, "").isBlank()

    /**
     * 列表续行后纠正新列表块光标：Command 落地时默认 raw 偏移 0 会落在 bullet（marker）
     * 之前，导致后续输入字符插到 bullet 前面（如 "a• "）。此处把光标移到 marker 之后
     * （内容起点）。
     *
     * marker 长度用块的 [RichTextState.annotatedString] 前缀正则反推，不依赖子模块
     * internal 字段（richParagraphList / startText 均不可从 App 访问）。
     * 覆盖全部层级 marker 形态（v2026-09-05 缩进按钮层级样式）：
     * 一级 `1.` / 二级 `(1)` / 三级 `①` / 四级 `a.` / 五级 `Ⅰ.` / 六级 `i.` + 无序
     * 符号（正则失配时 markerEnd 回退 0，光标会落在 marker 左侧——层级样式加入后
     * `(2)` 曾因此失配，光标落在括号左侧）。
     *
     * 无序符号（v2026-09-08）：库默认符号表已改为单元素 `•`，**各层级 marker 恒为
     * 黑色圆点**（缩进只移动位置），字符类仍保留 `◦ ▪` 以兼容自定义符号表与旧数据。
     *
     * v2026-09-07 修正：库的罗马数字（LowerRoman/UpperRoman）用 **ASCII 字母**拼写
     * （"i"/"I"、"ii"/"II"，见库 `OrderedListStyleType.formatToRomanNumber`），并非
     * Unicode 罗马字符 ⅰ/Ⅰ——旧正则 `[Ⅰ-Ⅿ]`（U+2160-216F）匹配不了 ASCII "I."，
     * `[a-z]+` 又只覆盖小写，五级 "I." 两头落空失配 → 光标落在 marker 左侧
     * （六级 "i." 空项回车降入五级时暴露）。现合并为 `[a-zA-Z]+`（同时覆盖四级
     * `a.` 与大小写字母样式）；Unicode 罗马分支删除（库从不输出该形态）。
     */
    private fun refocusListBlock(blockId: String) {
        val block = blocks.firstOrNull { it is BodyBlock.Text && it.id == blockId } as? BodyBlock.Text
            ?: return
        if (!block.state.isList) return
        val text = block.state.annotatedString.text
        val markerEnd = Regex(
            "^\\s*(?:[•◦▪]\\s|\\(\\d+\\)\\s|[①-⑳]\\s|\\d+\\.\\s|[a-zA-Z]+\\.\\s)"
        ).find(text)?.range?.last?.plus(1) ?: 0
        focusSpec(FocusSpec(blockId, markerEnd))
    }

    /**
     * 取当前有序列表块的字面编号（用于拆块续行时计算下一行编号）。
     *
     * 以 [blockMarkdown]（即库的 [RichTextState.toMarkdown]）为准：库对有序列表
     * 始终序列化为 `"N. "` 字面编号，与列表缩进/视觉前缀格式无关。
     *
     * 注意：不能直接读 [RichTextState.annotatedString] 的 marker 前缀——marker 可能
     * 不含结尾空格或带层级缩进前缀，用带 `\s` 锚定的正则会失配回退，导致编号不递增。
     *
     * @return 当前编号（如 `"2. 测试"` → 2）；非有序列表返回 null。
     */
    private fun currentOrderedNumber(state: RichTextState): Int? {
        if (!state.isOrderedList) return null
        return orderedNumberOfMd(blockMarkdown(state))
    }

    /**
     * 跨块有序列表重编号（层级感知的位置语义）：连续的有序 Text 块视为一个列表 run，
     * 按块序编 1..n；任何非有序块（普通文本 / 空块 / 图片块 / 无序列表块）都会打断
     * run、清空全部计数。
     *
     * v2026-09-05 升级为**层级感知**（配合缩进按钮）：嵌套项在父项下重新从 1 编号——
     * `1. a / [缩进]1. b / 2. c` 中 b 是 a 的子项（编号 1）、c 与 a 同级（编号 2）。
     * 每层独立计数，出现更浅层时清掉更深层计数（兄弟层切换）。
     *
     * 位置语义是块序列的**确定性函数**（编号 = 连续序位），因此撤销/重做/命令重放后
     * 再跑一遍即自动收敛一致，无需为重编号单独设计撤销。
     *
     * 调用约定：
     * - 必须在 replaying 门控内调用（executeAndPush / undo / redo 内部已满足），
     *   避免被重写块的 observer 把重编号误判为新编辑清掉 redo 栈；
     * - 打字路径（删 marker / 工具栏切换列表）由块 observer 检测 isOrderedList 翻转
     *   时调用（此时不在门控内，redo 本就应因新编辑失效，语义正确）；
     * - [initialize] 加载不调用——外部 markdown 的字面起始编号在首次编辑前保持原样。
     *
     * 只在编号与序位不一致时才重写（打字等常规路径零开销）。
     */
    fun renumberOrderedBlocks() {
        /** 层级 → 该层当前计数（1 起） */
        val counters = mutableMapOf<Int, Int>()
        for (block in blocks) {
            val textBlock = block as? BodyBlock.Text
            if (textBlock == null || !textBlock.state.isOrderedList) {
                counters.clear()
                continue
            }
            val state = textBlock.state
            val md = blockMarkdown(state)
            val level = listLevelOfMd(md)
            /** 更浅层出现 = 同层的兄弟序列推进 → 清掉所有更深层计数 */
            counters.keys.retainAll { it <= level }
            val expected = (counters[level] ?: 0) + 1
            counters[level] = expected
            if (orderedNumberOfMd(md) != expected) {
                rewriteOrderedMarker(state, level, expected)
            }
        }
    }

    /**
     * 就地把有序块的 marker 改写为 [level] 层的编号 [n]（v2026-09-05 改版：直接走库
     * [RichTextState.setListMarker] 改段落类型，**不再经 setMarkdown 重建**）。
     *
     * 为什么不能走 markdown：层级前缀（每级 2 空格）+ 字面编号经 setMarkdown 往返时，
     * 独立块的 ≥4 空格前缀（三级及以上）会被 CommonMark 解析为缩进代码块，层级直接丢失
     * （真机表现：连续缩进后 marker 在 "i."/"1." 间循环、同级后续项编号被错误改写）。
     * setListMarker 只换段落类型（marker 文本 + 缩进样式，updateParagraphType 自动校正
     * 光标），内容与光标完全不动；commitHistory = false 不产生块内撤销步（撤销收敛由
     * 位置语义重编号保证）。
     */
    private fun rewriteOrderedMarker(state: RichTextState, level: Int, n: Int) {
        state.setListMarker(level = level, number = n, commitHistory = false)
    }

    /**
     * 新建 Text 块。
     *
     * @param id 块 id：Command 重放时传入原 id 复用（焦点/外部引用保持稳定），
     *   缺省生成新 id。
     * @param initialListType 拆块/归一化时新块应继承的列表类型；null = 沿用 markdown 自带样式。
     *   非空 markdown 若已含列表 marker（如软键盘切分），创建后 [RichTextState.isList] 已为真，
     *   内部会跳过避免重复添加 marker。
     */
    fun createTextBlock(
        markdown: String,
        id: String = newBodyBlockId(),
        initialListType: InheritedListType? = null,
        /** 有序列表续行时新块的起始编号（源块编号 +1）；null = 不指定，交库默认/字面编号决定 */
        orderedStartNumber: Int? = null,
        /** 块的列表层级（1 起，源块层级，有序/无序通用）；null = 一级。
         *  经 [RichTextState.setListMarker] 直接设置（不走 markdown 前缀往返）。 */
        listLevel: Int? = null,
        /** 是否剥离 markdown 的层级缩进前导空格（每级 2 空格）。命令重建路径传 true：
         *  独立块的前缀经 setMarkdown 往返时 ≥4 空格会被 CommonMark 解析成缩进代码块
         *  导致层级丢失，故剥离后经 [RichTextState.setListMarker] 直接设置层级。
         *  initialize 加载传 false（默认）：多行嵌套段依赖前缀解码，且 ≤3 空格可安全解码。 */
        stripListLevelPrefix: Boolean = false,
        /**
         * 缩进档位（v2026-09-08 新增，1 = 无缩进；v2026-09-15 起**只服务普通段落**）：
         * App 布局级缩进，不调库的段落缩进（库 TextIndent 对纯文本段的渲染量实测不准）；
         * [markdown] 参数必须是**已剥掉缩进载体（EM 前缀）**的内容。
         * 复选框（任务列表）的缩进由库的段落层级承载，与本参数无关。
         */
        indentLevel: Int = 1,
        /**
         * 是否为「图片载体空块」（v2026-09-10 新增）：仅由
         * [normalizeImageSeparators] 补块与 [rebuildBlock]（命令重建）传入 true；
         * 其余所有构造点默认 false（用户手打的空行、拆块产生的空块等）。
         */
        isImageSeparator: Boolean = false,
    ): BodyBlock.Text {
        val state = RichTextState()
        /**
         * 列表缩进配置（v2026-09-05 缩进按钮需求）：
         * - 一级列表仍贴左缘（库公式 base = indent × (level-1)，level=1 时 base=0），
         *   保持「列表无缩进」需求不变；
         * - 二级起每级缩进 [LIST_LEVEL_INDENT_SP]（30sp ≈ 正文 15sp 的两字符），
         *   供工具栏「增加/减少缩进」按钮产生可见层级。
         *  注意不能用 listIndent=0（它是 ordered/unordered 的快捷 setter，会把层级缩进一并清零）。
         */
        state.config.orderedListIndent = LIST_LEVEL_INDENT_SP
        state.config.unorderedListIndent = LIST_LEVEL_INDENT_SP
        /** marker 按层级循环（1./(1)/①/a./Ⅰ./i.，见 [AppOrderedListStyleType]） */
        state.config.orderedListStyleType = AppOrderedListStyleType
        /** v2026-09-01 关闭编辑态内联图片渲染（防御性）：插图已改为拆块
         *  （Text 块 state 不持有 image span），正常路径覆盖层无图可画；
         *  关掉开关可兜底边缘路径（如粘贴含图 markdown 进块内），避免覆盖层
         *  在 Text 块内画出真图与 Image 块重复。 */
        state.inlineImageRendering = false
        registerTriggers(state)
        /**
         * 层级前缀剥离（仅单行）：命令重建路径的 markdown 可能带「每级 2 空格」层级前缀，
         * 独立块 setMarkdown 时 ≥4 空格会落入缩进代码块，故剥离后经 setListMarker 还原层级。
         * 多行 markdown（initialize 的嵌套段落）保留前缀——多行上下文里解码器按 AST+源码缩进
         * 正确还原嵌套。
         */
        val isSingleLine = !markdown.contains('\n')
        val effectiveMarkdown = if (stripListLevelPrefix && isSingleLine) markdown.trimStart(' ') else markdown
        if (effectiveMarkdown.isNotEmpty()) {
            /** 非空块经 setMarkdown 重建，**不含**前导 ZWSP：
             * 这是 [FocusSpec.offset] 统一为 raw 坐标的前提——由 [BlockSpec.TextSpec] 重建的
             * 非空块 effective == raw，[mergeTextBlocks] 里对
             * 旧块用 [effectiveText] 算出的「有效字数」可直接当新块的 raw 偏移。 */
            state.setMarkdown(effectiveMarkdown)
        } else {
            /** 空块预置 ZWSP + 光标 (1, 1)：让软键盘退格能产生状态变化被 observer 捕获 */
            state.setText(ZWSP)
            state.selection = TextRange(1)
        }
        /** 列表续行：源块为列表且新块自身尚未带列表样式时，继承为同类型列表项
         *  （实现“回车后下一行继续列表”）。
         *  非空 markdown 若已含列表 marker（如软键盘切分），state.isList 已为真，跳过避免重复添加。 */
        if (initialListType != null && !state.isList) {
            when (initialListType) {
                InheritedListType.Unordered -> state.addUnorderedList()
                InheritedListType.Ordered -> {
                    /** 有序列表续号：每个块是独立的 RichTextState（单段落），库默认把新块渲染成 "1."。
                     *  此处用字面编号 markdown 续号——库解析器保留 markdown 字面数字（#734），
                     *  使新行显示 "2."/"3."… 且序列化（toMarkdown）也输出正确编号。
                     *  orderedStartNumber 仅在「拆块续行」时由调用方传入（源块编号 +1）；
                     *  其余场景（markdown 已自带编号、或空 markdown 无编号）走 addOrderedList 默认 1。 */
                    val looksOrdered = Regex("^\\s*\\d+\\.\\s").containsMatchIn(effectiveMarkdown)
                    if (orderedStartNumber != null && !looksOrdered) {
                        val n = orderedStartNumber
                        state.setMarkdown(if (effectiveMarkdown.isNotEmpty()) "$n. $effectiveMarkdown" else "$n. ")
                        if (effectiveMarkdown.isEmpty()) {
                            /** 空有序列表项：在保留列表类型的前提下尾随 ZWSP，
                             *  与无序列表 '• ␣' 一致，使软键盘退格可被 observer 检测并触发合并退出。
                             *  （不能用 setText——它会重置段落类型丢失 "N." 编号；
                             *  用 addTextAtIndex 在 marker 之后显式插入，位置确定不依赖当前 selection） */
                            state.addTextAtIndex(state.annotatedString.text.length, ZWSP)
                            state.selection = TextRange(state.annotatedString.text.length)
                        }
                    } else {
                        state.addOrderedList()
                    }
                }
            }
        }
        /**
         * 层级还原（缩进按钮需求）：单行块经剥离后层级归一为 1，此处按 [listLevel] 直接设置。
         * 编号保留 markdown 字面值（setListMarker 只改层级时不传 number 会重置为 1，故显式回填）。
         * commitHistory = false：程序性重建不产生块内撤销步。
         */
        if (state.isList && isSingleLine) {
            val lvl = (listLevel ?: 1).coerceAtLeast(1)
            if (lvl > 1) {
                val num = orderedNumberOfMd(state.toMarkdown()) ?: 1
                state.setListMarker(level = lvl, number = num, commitHistory = false)
            }
        }
        /** indentLevel（普通段落缩进）/ isImageSeparator（载体身份）随块对象携带 */
        android.util.Log.d(
            "SoftLineBreak",
            "createTextBlock md=${markdown.replace("\n", "⏎")}" +
                " tree=${state.toText().replace("\n", "⏎")}" +
                " as=${state.annotatedString.text.replace("\n", "⏎")}",
        )
        return BodyBlock.Text(
            id,
            state,
            indentLevel = indentLevel,
            isImageSeparator = isImageSeparator,
        )
    }

    /** Text 块 → [BlockSpec.TextSpec]（markdown 剥 ZWSP；indentLevel / isImageSeparator
     *  随 spec，Command 载荷统一出口） */
    private fun textSpec(block: BodyBlock.Text): BlockSpec.TextSpec =
        BlockSpec.TextSpec(
            block.id,
            blockMarkdown(block.state),
            indentLevel = block.indentLevel,
            /** 载体身份随 spec 往返（v2026-09-10）：撤销重建后仍是载体，下次换位仍能被归一化清理 */
            isImageSeparator = block.isImageSeparator,
            /** 撤销还原用（v2026-09-08）：层级显式随 spec——removed 块经 rebuildBlock 重建时
             *  会剥掉 markdown 层级前缀（stripListLevelPrefix=true），不传层级则撤销后掉回一级 */
            listLevel = listLevelOfMd(blockMarkdown(block.state)).takeIf { block.state.isList },
        )

    /** 块的 markdown 输出（剥 ZWSP——与 [toMarkdown] 的输出约定一致） */
    internal fun blockMarkdown(state: RichTextState): String =
        state.toMarkdown().replace(ZWSP, "")

    // ---------- 加载 / 导出 ----------

    /**
     * 用整篇 markdown 重建块列表（初始化与历史恢复共用）。
     *
     * @param markdown           整篇 Markdown 源。
     * @param triggerDocChanged  是否触发 onDocChanged 通知（默认 true）。
     *        - 初始化载入（新建灵感 / loadInspiration 回填）传 false：
     *          此时只是把已保存内容还原成块列表，并非用户编辑，不应把
     *          `_isDirty` 误置为 true（否则未编辑就按返回会误弹"放弃修改？"）。
     *        - 历史恢复（如系统重建后从 savedStateHandle 恢复文本）传 true：
     *          恢复内容属于内容变化，应正常置脏以便保存链路感知。
     */
    fun initialize(markdown: String, triggerDocChanged: Boolean = true) {
        blocks.clear()
        val segs = parseMarkdownSegments(markdown)
        segs.forEachIndexed { index, seg ->
            when (seg) {
                is MdSegment.TextSeg -> {
                    // Text 段按 \n\n 再拆成段落块；单 \n 的软换行保留在块内。
                    // 段内真实多段（非空）与独立的空白块都要保留；
                    // 但紧贴图片块的「边界空段」只是块间 `\n\n` 分隔符的副作用，
                    // 并非真空白块——丢弃它，否则图片相邻会凭空多出空块。
                    val prevIsImage = index > 0 && segs[index - 1] is MdSegment.ImageSeg
                    val nextIsImage = index < segs.lastIndex && segs[index + 1] is MdSegment.ImageSeg
                    val paras = seg.md.split("\n\n")
                    paras.forEachIndexed { pIdx, para ->
                        val trimmed = para.trim('\n')
                        val isImageLeadingBoundary = pIdx == 0 && prevIsImage
                        val isImageTrailingBoundary = pIdx == paras.lastIndex && nextIsImage
                        // 图片边界的空段 → 跳过（分隔符，非空白块）
                        if ((isImageLeadingBoundary || isImageTrailingBoundary) && trimmed.isEmpty()) {
                            return@forEachIndexed
                        }
                        // 空白段（空段落 / 旧格式空段 / NBSP 占位符）重建为空白块，
                        // 否则按原样建块；整篇为空时由 ensureTextBlock 兜底生成一个空块。
                        if (trimmed.isEmpty() || trimmed == EMPTY_BLOCK_PLACEHOLDER) {
                            // 空白块：createTextBlock("") 预置 ZWSP 退格锚点，与编辑态空块语义一致
                            blocks += createTextBlock("")
                        } else if (isDividerMarkdown(trimmed)) {
                            // 分割线段（v2026-09-11 样式化：实线/虚线/波浪按 markdown 后缀解析）
                            blocks += BodyBlock.Divider(
                                newBodyBlockId(),
                                parseDividerStyle(trimmed) ?: DividerStyle.SOLID,
                            )
                        } else if (isTaskListMd(trimmed)) {
                            /**
                             * 任务列表段（v2026-09-15 改造）：`- [ ] ` / `- [x] ` 整段交由
                             * **库**解析成 `TaskList` 段落——勾选框由库绘制、勾选态与层级由
                             * 段落类型承载，App 不再把它当作"块级复选框"处理。
                             *
                             * 旧数据的 EM 缩进载体（`- [ ] ` 之后的全角空格）由
                             * [normalizeTaskListMd] 折算成库认识的层级空格前缀，缩进不丢。
                             */
                            blocks += createTextBlock(normalizeTaskListMd(trimmed))
                        } else if (!trimmed.contains('\n') && SingleLineListMdRegex.containsMatchIn(trimmed)) {
                            /**
                             * 列表段（v2026-09-08 修复「多级列表保存后加载掉级/字面化」）：
                             * 孤立缩进列表行经库 decode **不可靠**——层级前缀每级 2 空格，
                             * ≥4 空格（三级起）会被 CommonMark 解析成**缩进代码块**
                             * （CODE_LINE 字面输出，如 "    - 第三行" 渲染成字面 "- 第三行"，
                             * 不再是列表 marker；真机截图：多级无序列表重进编辑页后
                             * 三级及以下全部变成字面文本）。
                             * 与命令重建路径（stripListLevelPrefix）同款：剥前缀 +
                             * [createTextBlock] 的 listLevel 经 setListMarker 显式还原，
                             * 层级不再依赖 markdown 前缀往返。有序段剥前缀后字面编号
                             * 由库保留（#734），编号/层级都无损。
                             */
                            blocks += createTextBlock(
                                trimmed.trimStart(' '),
                                listLevel = listLevelOfMd(trimmed),
                                stripListLevelPrefix = true,
                            )
                        } else {
                            // 普通段（v2026-09-08）：缩进载体（EM，App 自管）解析后剥除，
                            // state 恒干净无 TextIndent；渲染由 App 侧 start padding 承载。
                            // 兼容旧数据（库 TextIndent 编码的 EM 形态相同，直接沿用）。
                            val plainLevel = plainIndentLevelOfMd(trimmed)
                            val plainContent = if (plainLevel > 1) {
                                trimmed.dropLeadingPlainIndent()
                            } else {
                                trimmed
                            }
                            blocks += createTextBlock(plainContent, indentLevel = plainLevel)
                        }
                    }
                }
                is MdSegment.ImageSeg -> blocks += BodyBlock.Image(newBodyBlockId(), seg.path)
            }
        }
        /**
         * 认领「图片载体空块」（v2026-09-10）：markdown 里载体与用户手打的空行同为
         * NBSP 占位段（[EMPTY_BLOCK_PLACEHOLDER]），文本层面无法区分，**位置是唯一可靠判据**——
         * 恰好夹在两张图片之间（前后紧邻都是 Image）的空白块只可能是编辑器为
         * "两图之间可输入"补的载体：那个位置本来就有载体，用户不会也不能在两图之间再塞一个。
         *
         * 认领后才能维持不变量：换位时旧载体漂到图片组外侧 → [normalizeImageSeparators]
         * 按标记删除，空行数不再随交换次数增长。
         */
        for (i in blocks.indices) {
            val text = blocks[i] as? BodyBlock.Text ?: continue
            if (text.isImageSeparator || !isEffectivelyEmpty(text.state)) continue
            val betweenImages = i > 0 && i < blocks.lastIndex &&
                blocks[i - 1] is BodyBlock.Image && blocks[i + 1] is BodyBlock.Image
            if (betweenImages) {
                /** 就地换块对象：复用同一 RichTextState / FocusRequester（载入期无块内历史，零损失） */
                blocks[i] = BodyBlock.Text(
                    text.id,
                    text.state,
                    text.focusRequester,
                    indentLevel = text.indentLevel,
                    isImageSeparator = true,
                )
            }
        }
        ensureTextBlock()

        /**
         * 相邻文本块**自动合并**（v2026-09-15）：旧数据是"每行一个块"（块间 `\n\n` 分隔），
         * 在"换行不新建块"的新模型下应收敛为一个块。
         *
         * 属**加载规范化**，不进命令栈（用户确认口径）——否则用户一进页面就能撤销回旧结构。
         * 在 replaying 门控内执行，避免新建块的 observer 误判成"新编辑"而清 redo 栈。
         */
        replaying = true
        try {
            /** 走统一入口但**跳过载体归一化**（加载只做文本块合并，载体身份由中段的
             *  "按位置认领"逻辑负责，这里不动它）；方法内部已 apply，无需再执行 */
            normalizeStructureInto(mutableListOf(), tag = "加载", normalizeSeparators = false)
        } finally {
            replaying = false
        }

        focusedBlockId = null
        highlightedBlockId = null
        /** 换了整篇文档：命令栈与块内 history 一并作废（新块的 history 本就是空的） */
        clearCommandStacks()
        hasInitialized = true
        /** 仅当内容确实由编辑/历史恢复触发时才通知；初始化载入不应置脏 */
        if (triggerDocChanged) onDocChanged?.invoke()
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
        blocks.map { block ->
            when (block) {
                is BodyBlock.Text -> {
                    /** 剥掉空块预置的 ZWSP，保证 markdown 往返不带噪音 */
                    val raw = block.state.toMarkdown().replace(ZWSP, "")
                    /**
                     * 缩进段落（v2026-09-08；v2026-09-15 起**只服务普通段落**）：
                     * 输出 EM 前缀载体。
                     *
                     * 复选框（任务列表）的 `- [ ] ` / `- [x] ` 前缀与层级空格由**库**的
                     * `state.toMarkdown()` 自带（见 richeditor 的 appendParagraphStartText），
                     * App 不再拼接；旧数据的 EM 缩进在加载时已折算成库层级。
                     */
                    if (block.indentLevel > 1) {
                        /**
                         * 普通块带缩进（v2026-09-08）：缩进载体（EM 前缀）由 App 拼，
                         * state 恒无 TextIndent——与复选框块同款载体，加载侧统一剥除。
                         */
                        val body = parseMarkdownSegments(raw)
                            .filterIsInstance<MdSegment.TextSeg>()
                            .map { it.md.trim('\n') }
                            .joinToString("\n\n")
                        plainIndentPrefix(block.indentLevel) + body
                    } else if (raw.isEmpty()) {
                        /** 空白块：用 NBSP 占位符序列化，使该段在 markdown 中非空，
                         * 避免与相邻图片块（块间以 `\n\n` 分隔）的「段间空段」混为一体，
                         * 导致重新进入时图片边界多/少空块；加载侧再把占位符还原为空块。 */
                        EMPTY_BLOCK_PLACEHOLDER
                    } else {
                        parseMarkdownSegments(raw)
                            .filterIsInstance<MdSegment.TextSeg>()
                            .map { it.md.trim('\n') }
                            // 不再丢弃空白段（空段落）：空白块需参与序列化，
                            // 否则保存后再进入会丢失；与 initialize 重建空块对称
                            .joinToString("\n\n")
                    }
                }
                is BodyBlock.Image -> "![](${block.path})"
                /** 分割线块：输出独占段（thematic break，样式随 [DividerStyle.markdown]），与 [initialize] 识别对称 */
                is BodyBlock.Divider -> block.style.markdown
            }
        }
        // 不再过滤空段：块间以空行连接，空白块对应一个非空占位段，保证往返对称
        .joinToString("\n\n")

    /**
     * 纯文本（字数统计 / 复制全文 / 同步 _content 用）。
     *
     * **v2026-09-09 跳过"图片间载体空行"**：编辑页为「两图之间可输入」强制插入的
     * 空 Text 块（[InsertImageSeparatorCommand]）只对编辑态有意义——首页时间线的
     * 正文预览（content 纯文本）不该出现这行空白。判定：块为空白 Text 块
     * （剥 ZWSP 后 isBlank），且**向前 / 向后最近的非空白块都是 Image** → 不输出行。
     * 非图片间的空白块（用户主动留的空段落）照常输出，行为不变。
     * 注意：content 在保存时落库，本修复只对**之后保存**的笔记生效（用户已确认
     * 不做旧数据迁移）。
     */
    fun plainText(): String {
        /** 块的有效文本（Text 剥 ZWSP）；非 Text 块返回 null（不参与行输出） */
        fun textOf(block: BodyBlock): String? =
            (block as? BodyBlock.Text)?.let { effectiveText(it.state.annotatedString.text) }

        /** 是否"空白块"：仅 Text 块可能空白（Image / Divider 恒为非空白，会阻断配对） */
        fun isBlankAt(i: Int): Boolean = textOf(blocks[i])?.isBlank() == true

        /** 从 from 出发按 step 方向找最近的非空白块（越界返回 null） */
        fun nearestNonBlank(from: Int, step: Int): BodyBlock? {
            var i = from + step
            while (i in blocks.indices) {
                if (!isBlankAt(i)) return blocks[i]
                i += step
            }
            return null
        }

        return blocks.mapIndexedNotNull { i, block ->
            val text = textOf(block) ?: return@mapIndexedNotNull null
            /**
             * 空文本块且（**带载体标记**，或旧数据按位置判定：前后最近的非空白块都是图片）
             * → 图片间载体空行，纯文本不输出。
             *
             * v2026-09-10 加标记分支：载体被换位甩到图片组外侧时位置判定会失效（前后不再是两图），
             * 但它本质仍是编辑器的排版产物、不该进正文；标记分支把它一并排除
             * （这对等待归一化清理的中间态同样成立）。
             */
            val marked = (block as? BodyBlock.Text)?.isImageSeparator == true
            val isImageGapFiller = text.isBlank() && (
                marked ||
                    (nearestNonBlank(i, -1) is BodyBlock.Image && nearestNonBlank(i, +1) is BodyBlock.Image)
                )
            if (isImageGapFiller) null else text
        }.joinToString("\n")
    }

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

    /**
     * 工具栏「增加/减少缩进」（v2026-09-05）：对聚焦块做列表层级缩进。
     *
     * 层级变更**直接走库 API**（[RichTextState.setListMarker] / add/removeXxxList，
     * 均为 recordHistory Structural——块内 history 可撤销；updateParagraphType 自动换
     * marker 文本/缩进样式并校正光标），**不经 markdown 前缀往返**：独立块的 ≥4 空格
     * 前缀会被 CommonMark 解析成缩进代码块导致层级丢失（真机表现：连续缩进在
     * "i."/"1." 间循环、「3.测试3」被错误改写为「1.测试3」）。
     *
     * 规则：
     * - 列表行：加缩进 = 层级 +1（[MAX_LIST_LEVEL] 封顶 = 一级贴左缘 + 最多 5 次缩进，
     *   每级 [LIST_LEVEL_INDENT_SP] ≈ 两字符；到顶后点击无效果）；减缩进 = 层级 -1，
     *   一级 = 「未缩进」底线，再减无效果（不退出列表）——列表归属由有序/无序按钮管理，
     *   缩进按钮只调层级（按有序按钮产生的一级 1./2. 不会被减少缩进退掉）。
     * - 普通文本行 / 复选框块（v2026-09-08 统一为 App 布局级缩进）：就地换块对象的
     *   [BodyBlock.Text.indentLevel]（[SetBlockIndentCommand]，不进库排版）；渲染由
     *   App 侧 start padding 承载（每级 [LIST_LEVEL_INDENT_SP]），跨段左缘精确同列；
     *   层级封顶 [MAX_LIST_LEVEL]（与列表一致）。
     *
     * 变更后调 [renumberOrderedBlocks]（层级感知位置语义）收敛编号；重编号对其他块的
     * 改写会使 markdown 变化 → observer 自动清全局 redo 栈（与本操作是真实编辑一致）。
     * 撤销：块内 history 撤销后由 [undo] 尾部的重编号收敛回位置语义。
     */
    fun indentFocusedBlock(delta: Int) {
        /** 解析焦点 Text 块（与 [focusedOrFirstTextState] 同源：聚焦优先，回退首块） */
        val block = focusedBlockId?.let { id -> blocks.firstOrNull { it.id == id } }
            ?.let { it as? BodyBlock.Text }
            ?: (blocks.firstOrNull { it is BodyBlock.Text } as? BodyBlock.Text)
            ?: return

        /**
         * **复选框块（v2026-09-08 扩展到组合态）：App 布局级缩进**——不调库
         * setParagraphIndent / setListMarker（其 TextIndent 渲染量与 Row 外复选框
         * 图标无法对齐，真机实测约 2 倍），改就地换块对象的
         * [BodyBlock.Text.indentLevel]（[SetBlockIndentCommand]，state / 块内 history /
         * 光标无损）；渲染由 App 侧 start padding 承载，跨段左缘精确同列。
         *
         * **组合态（复选框 + 列表段落）迁移**：复选框块上叠加列表（isList=true）时，
         * 库列表自身的 TextIndent 会与 App padding 双源叠加——缩进操作时把库层级
         * 归 1（marker 变一级形态，commitHistory=false 不产生块内撤销步），缩进量
         * 由 App 档位全权承载。加载侧在 [createTextBlock] 做同样归位。
         *
         * v2026-09-15：块级 `checked` 已移除，条件改用库的段落类型判定。
         */
        if (block.state.isTaskList) {
            if (block.state.isList && listLevelOfMd(blockMarkdown(block.state)) > 1) {
                block.state.setListMarker(
                    level = 1,
                    number = orderedNumberOfMd(blockMarkdown(block.state)) ?: 1,
                    commitHistory = false,
                )
            }
            val newLevel = (block.indentLevel + delta).coerceIn(1, MAX_LIST_LEVEL)
            if (newLevel != block.indentLevel) {
                executeAndPush(SetBlockIndentCommand(block.id, block.indentLevel, newLevel))
            }
            return
        }

        val state = block.state
        val md = blockMarkdown(state)

        val level = listLevelOfMd(md)
        val newLevel = level + delta
        if (delta > 0 && newLevel > MAX_LIST_LEVEL) return
        /** 一级 = 「未缩进」底线（用户 18:05 修正）：再减无效果、**不退出列表**——
         *  列表归属由有序/无序按钮管理，按有序按钮产生的一级 1./2. 不会被减少缩进退掉 */
        if (newLevel < 1) return
        /**
         * 空列表项（如回车产生的 "2. ␣"）同样正确缩进（用户 17:39 明确）：
         * 层级变化会同时改变 marker 形态（2. → (2) → ① …）与段落缩进，视觉效果明确，
         * 不可忽略。ZWSP 退格锚点在 raw 内容里，setListMarker 只换段落类型不碰内容，
         * 锚点保持有效。
         *
         * **无序列表例外（v2026-09-08）**：库默认符号表已改为单元素 `•`，层级变化
         * 只改段落缩进、marker 恒为黑色圆点（用户明确要求「缩进不换标识」）。
         */
        state.setListMarker(
            level = newLevel,
            number = orderedNumberOfMd(md) ?: 1,
        )
        renumberOrderedBlocks()
    }

    /**
     * 聚焦块当前是否可「增加缩进」（工具栏按钮置灰用，视觉降级）：
     * - 纯文本行（v2026-09-07）：前导全角空格级数未到 [MAX_LIST_LEVEL] 才可（封顶置灰）；
     * - 列表行：层级未到 [MAX_LIST_LEVEL] 才可（到顶后按钮置灰）。
     *
     * 快照响应式：`focusedBlockId` / `annotatedString` / `toMarkdown()`（读段落树）均为
     * 快照读取，组合中注册依赖后可在焦点切换、内容/层级变化时自动触发重组刷新。
     * **必须显式读 [RichTextState.annotatedString]**：段落 `type` 是普通 var（非快照状态），
     * setListMarker 换层级只写 annotatedString/textFieldValue——不读它，到顶置灰不会刷新
     * （真机表现：缩进按钮到顶不置灰而减少按钮正常）。
     */
    val canIncreaseIndent: Boolean
        get() {
            val block = focusedBlockId?.let { id -> blocks.firstOrNull { it.id == id } }
                as? BodyBlock.Text
                ?: (blocks.firstOrNull { it is BodyBlock.Text } as? BodyBlock.Text)
                ?: return false
            /** 复选框块（**含列表组合态**，v2026-09-08）与非列表块：缩进走 App 布局级
             *  档位（读块对象 indentLevel；组合块的库层级已归 1，不能读它——
             *  否则增加键永不置灰、减少键恒灰）。就地换块对象 = blocks 结构性写入，
             *  读它会随缩进重组刷新 */
            if (!block.state.isList) {
                return block.indentLevel < MAX_LIST_LEVEL
            }
            /** 纯列表块：setListMarker 只写 annotatedString（段落 type 非快照），
             *  必须显式读它，到顶置灰才会刷新（v2026-09-07 同款坑） */
            block.state.annotatedString
            return listLevelOfMd(blockMarkdown(block.state)) < MAX_LIST_LEVEL
        }

    /**
     * 聚焦块当前是否可「减少缩进」（工具栏按钮置灰用，视觉降级）：
     * - 纯文本行（v2026-09-07）：有前导全角空格即可减，否则置灰；
     * - 列表行：仅**层级 ≥ 2** 可减（一级 = 「未缩进」底线，再减无效果、不退出列表——
     *   列表归属由有序/无序按钮管理）。
     * 依赖注册同 [canIncreaseIndent]（显式读 annotatedString）。
     */
    val canDecreaseIndent: Boolean
        get() {
            val block = focusedBlockId?.let { id -> blocks.firstOrNull { it.id == id } }
                as? BodyBlock.Text
                ?: (blocks.firstOrNull { it is BodyBlock.Text } as? BodyBlock.Text)
                ?: return false
            /** 复选框块（**含列表组合态**，v2026-09-08）与非列表块：缩进走 App 布局级
             *  档位（读块对象 indentLevel；组合块的库层级已归 1，不能读它） */
            if (!block.state.isList) {
                return block.indentLevel > 1
            }
            /** 纯列表块：同上，显式读 annotatedString 保证刷新 */
            block.state.annotatedString
            return listLevelOfMd(blockMarkdown(block.state)) > 1
        }

    /**
     * 聚焦块当前是否为复选框块（v2026-09-07，工具栏复选框按钮激活态高亮用）。
     *
     * 快照响应式：[focusedBlockId]（mutableStateOf）与 [blocks]（SnapshotStateList
     * 结构性读取）均为快照状态；v2026-09-15 起勾选态由**库**的段落类型承载（结构性
     * 写入）同样可追踪——聚焦块切换 / 勾选翻转都会触发读取方重组刷新。
     */
    val isFocusedBlockCheckbox: Boolean
        get() {
            val block = focusedBlockId
                ?.let { id -> blocks.firstOrNull { it.id == id } }
            /** v2026-09-15：勾选态改由**库**的段落类型承载（`state.isTaskList`），
             *  不再有块级 checked 属性 */
            return block is BodyBlock.Text && block.state.isTaskList
        }

    // ---------- 图片插入 ----------

    /**
     * 在当前聚焦块的光标处插入图片并拆块（单张 = 一条 [ReplaceBlocksCommand]）。
     *
     * v2026-09-09：插图后若与**前一张图片贴在一起**（典型场景：光标停在两图
     * 之间的空行行首再插一张），自动在两图之间补一个空 Text 块——两图之间必须
     * 有可输入的一行。补块与本命令打包成 [CompositeCommand]，一次撤销整体回退。
     */
    fun insertImageAtFocused(path: String) {
        executeAndPushWithImageSeparators(buildInsertImageCommand(path))
    }

    /**
     * 批量插入（多选相册一次确认）= **一个撤销单位**（方案A坑点5）：
     * 逐张"计算 + 立即应用"（下一张依赖上一张落定后的焦点位置），
     * 全部命令打包进一个 [CompositeCommand] 后只 push 一次——撤销一步全部回退。
     *
     * v2026-09-09：整批插完后再统一跑一次「两图相邻」校验——批量插入的落点是
     * "上一张图后的 Text 块行首"，连插多张天然形成 `[图,图,图]`，必须等全部插完
     * 才补空行（逐张补会让下一张的插入锚点错位）。补块同样并入同一个 Composite。
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
            /** 整批落定后统一跑结构归一化（此时才形成最终的相邻关系）：
             *  载体空块 + 相邻文本块合并，与其它入口共用 [normalizeStructureInto] */
            normalizeStructureInto(commands, tag = "批量插图")
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
            is BodyBlock.Text -> buildInsertInTextCommand(focused, focusedIdx, path)
            /** 图片 / 分割线块（v2026-09-07）不持有光标：插图退化为尾插 */
            else -> buildInsertImageAtEndCommand(path)
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
        /** 拆出的前后半继承源块的缩进档位（v2026-09-15：块级复选框属性已移除） */
        val inheritedIndentLevel = focused.indentLevel
        if (beforeMd.isNotBlank()) {
            inserted += BlockSpec.TextSpec(
                newBodyBlockId(),
                beforeMd,
                indentLevel = inheritedIndentLevel,
            )
        }
        val imgSpec = BlockSpec.ImageSpec(newBodyBlockId(), path)
        inserted += imgSpec
        if (afterMd.isNotBlank()) {
            inserted += BlockSpec.TextSpec(
                newBodyBlockId(),
                afterMd,
                indentLevel = inheritedIndentLevel,
            )
        }
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
     * 图片载体空块的**归一化**：维护不变量
     * 「载体空块数 == 图片相邻对数，且每个都恰好夹在两张图片之间」。
     *
     * v2026-09-10 新增（修复「反复交换图片后上下空行越来越多」）：
     * 旧实现只有 [InsertImageSeparatorCommand]（**只增不减**），而拖拽换位是
     * "移除 + 插入"——被拖图片走了、它原来的载体空块留在原索引上 → 漂到图片组
     * 外侧成为多余空行；下次换位又补一个新载体 → **每次交换空块 +1**，视觉上
     * 就是图片上下空行越堆越多（还会出现两个空行贴在一起）。
     *
     * 三条合法位置（v2026-09-11 扩展，按 [BodyBlock.Text.isImageSeparator] 区分载体与
     * **用户手打的空白块**）：两图之间 / 首图之前（最前块是图）/ 尾图之后（最后块是图）。
     * 两条规则：
     * 1. **删漂移载体**：标记为载体、**且仍然是空白**（`isEffectivelyEmpty`）、但不在
     *    任一合法位置的块 → 删除。带内容的一律不删（防御：正常路径下用户一打字，
     *    [onBlockContentChanged] 就已把标记清掉，它不再是载体）；
     * 2. **补插**：仅**两图直接相邻**处补一个载体（标记为 true）。
     *    v2026-09-11 懒插入改版：首图前/尾图后**不再自动补**（避免一进编辑页首图
     *    上方就多出一条空行），改为用户点击首图前/尾图后空白时经
     *    [BodyBlocksController.insertEdgeSeparator] 懒插入（可撤销）。此处点出的载体
     *    与两图之间的载体一样落在①的合法位置白名单内，不会被误删；用户在文档
     *    开头/结尾的有意留白安全（不误标、不误删）。
     *
     * **用户手打的空白块永不触碰（v2026-09-10 用户要求）**：无标记的空白块既不删也不
     * 压缩——哪怕同一图-图间隙里有两个。它们天然承担了"隔开两张图片"的职责，
     * 规则 2 只在**两图直接相邻**时才补载体，不会多插。
     * 图片组外侧的历史空行同理不动（无法区分"漂移载体"与"用户有意留白"）。
     *
     * **返回顺序即 apply 顺序**（调用方 `onEach { it.apply(this) }` 即可）：
     * 先删除（索引自后向前）、后插入（索引自后向前）。删除先行保证插入命令的索引
     * 落在"删除之后"的坐标系上；各自自后向前保证前面的操作不影响已算好的更大索引。
     * 撤销时 `CompositeCommand` 逆序回退：插入先按 id 移除（顺序无关），
     * 删除再按升序索引插回（顺序正确）→ 列表精确还原。
     *
     * 只处理"直接相邻"语义：两图之间要求直接相邻；首/尾载体要求首/尾块**就是**图片——
     * Divider 等非文本块与图片相邻属用户主动排版，不强拆、也不在其外侧补载体。
     */
    private fun normalizeImageSeparators(): List<BodyBlocksCommand> {
        val deletions = mutableListOf<RemoveImageSeparatorCommand>()

        /**
         * ① 漂移载体：带标记 + **仍为空白** + 不在任一合法位置 → 删。
         * 合法位置（v2026-09-11 懒插入改版 v3）：**紧邻至少一个不可输入块（图片 /
         * 分割线）且不与另一载体相邻**——覆盖两图之间 / 首图前 / 尾图后 / 分割线
         * 上下（工具条 toggle 产物）/ 首尾边缘（tap 条懒插入产物）；载体并排 ⇒
         * 整串全灭，由补插收敛回恰好一行（两图之间恰好一个空行的语义不破坏）。
         * 带内容的块一律不删：即便标记因某条罕见路径残留下来，也绝不能连用户输入一起删掉
         * （正常路径下 [onBlockContentChanged] 已在第一次输入时就清掉了标记）。
         */
        for (i in blocks.indices.reversed()) {
            val text = blocks[i] as? BodyBlock.Text ?: continue
            if (!text.isImageSeparator) continue
            if (!isEffectivelyEmpty(text.state)) continue
            /** 合法位置 v3（与 [checkImageSeparatorInvariant] 同源）：紧邻图/线、不邻载体 */
            val prev = if (i > 0) blocks[i - 1] else null
            val next = if (i < blocks.lastIndex) blocks[i + 1] else null
            val neighborNonInput = prev is BodyBlock.Image || prev is BodyBlock.Divider ||
                next is BodyBlock.Image || next is BodyBlock.Divider
            val neighborSeparator = (prev as? BodyBlock.Text)?.isImageSeparator == true ||
                (next as? BodyBlock.Text)?.isImageSeparator == true
            if (!neighborNonInput || neighborSeparator) {
                deletions += RemoveImageSeparatorCommand(text.id, textSpec(text), i)
            }
        }

        val deletedIds = deletions.map { it.blockId }.toMutableSet()

        /**
         * ② 补插：先在"删除已生效"的虚拟列表上找出所有需要载体的位置，索引即删除后的坐标系。
         * 遍历**插入候选位置**（index = size..0，自后向前），两图直接相邻 → 补插
         * （判据是"**直接**相邻"，图-图之间有用户手打的空白块时无需补，也不会多插）。
         * 自后向前保证先算好的更大索引不被前面的插入影响。
         *
         * v2026-09-11 懒插入改版：首/尾图前/后的载体**不再在此自动补插**——首图前/
         * 尾图后默认无载体，用户点击边缘空白时经 [BodyBlocksController.insertEdgeSeparator]
         * 懒插入（可撤销）。
         */
        val remaining = blocks.filterNot { it.id in deletedIds }
        val insertions = mutableListOf<InsertImageSeparatorCommand>()
        for (index in remaining.size downTo 0) {
            val atGap = index > 0 && index < remaining.size &&
                remaining[index] is BodyBlock.Image && remaining[index - 1] is BodyBlock.Image
            if (atGap) {
                insertions += InsertImageSeparatorCommand(
                    index = index,
                    spec = BlockSpec.TextSpec(newBodyBlockId(), "", isImageSeparator = true),
                )
            }
        }

        /** 删除按索引降序（撤销时升序插回，位置才对称），随后才是插入命令 */
        val commands = mutableListOf<BodyBlocksCommand>()
        deletions.sortedByDescending { it.index }.forEach { commands += it }
        insertions.forEach { commands += it }
        return commands
    }

    /**
     * 归一化载体空块并**立即落盘**（三个结构性入口共用：批量插图 / 插入图片 / 拖拽落位）。
     *
     * @param into 该入口自己的命令收集列表（命令按顺序追加；何时压栈由调用方决定，
     *   从而与主命令打包成同一个撤销单位）
     * @param tag 自检日志的来源标记
     */
    private fun normalizeImageSeparatorsInto(into: MutableList<BodyBlocksCommand>, tag: String) {
        into += normalizeImageSeparators().onEach { it.apply(this) }
        checkImageSeparatorInvariant(tag)
    }

    /**
     * **结构归一化的唯一入口**（v2026-09-15 收敛）：依次跑全部不变量维护。
     *
     * 1. [normalizeImageSeparatorsInto]：载体空块不变量（两图之间恰好一个可输入空块）；
     * 2. [normalizeAdjacentTextBlocksInto]：相邻普通文本块合并。
     *
     * ⚠️ **两个方法都在收集时 apply 自己的命令**——这是本项目约定：调用方拿到 `into`
     * 之后只用 [pushExecuted] 压栈，而它**只记账、不执行**命令。新增归一化规则时务必照
     * 这个模式写（`into += cmd` 之后立刻 `cmd.apply(this)`），否则规则会"只进撤销栈、
     * 从不真正生效"——历史上已经踩过一次（只有加载时才收敛）。
     *
     * @param normalizeSeparators 是否跑载体归一化（图片 / 分割线相关命令传 true；
     *   纯文本类命令传 false，省一次与图片无关的扫描）。
     */
    private fun normalizeStructureInto(
        into: MutableList<BodyBlocksCommand>,
        tag: String,
        normalizeSeparators: Boolean = true,
    ) {
        if (normalizeSeparators) normalizeImageSeparatorsInto(into, tag = tag)
        normalizeAdjacentTextBlocksInto(into)
    }

    /**
     * 相邻文本块**自动合并**（v2026-09-15，用户确认时机：加载 + 运行时）。
     *
     * **不变量**：块列表里不出现"两个相邻的普通 Text 块"。换行不再新建块之后，相邻
     * 文本块只可能来自历史数据（旧版每行一块）或删图 / 删分割线 / 拖拽重排后的残留，
     * 这里统一收成一个块，与「块 = 一段连续文本」的模型一致。
     *
     * **接缝用单 `\n`**（= 块内软换行，与"回车不拆块"同一语义）。**不能写 `\n\n`**：
     * 那是块与块之间的 markdown 分隔符，写进块内在下次加载时会被重新拆成两块（等于没合并）。
     *
     * **后块缩进差异（用户确认口径）**：后块缩进 ≠ 前块时，把后块的缩进载体（EM 前缀）
     * 转写到块内该行行首——信息不丢（往返稳定），但渲染出来是**可见宽空格**而不是真缩进
     * （块缩进是块级布局属性，块内无法逐行缩进）。
     *
     * **载体空块不参与合并**：两图之间"可输入空行"的不变量依赖它独立存在。
     *
     * 命令形态 [ReplaceBlocksCommand]（removed = 段内**全部**文本块，inserted = 合并块），
     * 撤销一步即拆回原样的多块（各自原 id，属性随 spec 还原）。
     *
     * @param into 收集归一化命令（调用方决定打包进 CompositeCommand 还是直接 apply）。
     */
    private fun normalizeAdjacentTextBlocksInto(into: MutableList<BodyBlocksCommand>) {
        /** 可参与合并的块：**纯普通段落**的 Text 块（载体空块跳过；图片 / 分割线块天然阻断） */
        fun isMergeable(index: Int): Boolean {
            val block = blocks[index]
            if (block !is BodyBlock.Text || block.isImageSeparator) return false
            /**
             * 含列表 / 任务列表段落的块**不参与合并**（v2026-09-15 按行勾选）：任务列表
             * 块每行独立（各自的勾选框与勾选状态），合并会把多行压回一个段落、勾选框
             * 失去行粒度。
             */
            val md = blockMarkdown(block.state)
            return md.lineSequence().none { line ->
                val t = line.trimStart()
                t.startsWith("- [ ] ") || t.startsWith("- [x] ") ||
                    t.startsWith("- ") || t.startsWith("* ") || t.startsWith("> ") ||
                    (t.length > 2 && t[0].isDigit() && (t[1] == '.' || t[1] == ')'))
            }
        }

        /** 先切出所有「连续文本块段」（被图片 / 分割线 / 载体空块阻断），再**倒序**生成命令
         *  —— 倒序保证前面的段合并后不会让后面段的 index 失效（各段删除的块数不同）。 */
        val segments = mutableListOf<Pair<Int, Int>>()
        var cursor = 0
        while (cursor < blocks.size) {
            val start = cursor
            while (cursor < blocks.size && isMergeable(cursor)) cursor++
            if (cursor - start >= 2) segments += start to cursor
            /** 非文本块（或单个文本块）：前进一格继续找下一段 */
            if (cursor == start) cursor++
        }
        if (segments.isEmpty()) return

        val focusBefore = currentFocusSpec()

        segments.asReversed().forEach { (start, endExclusive) ->
            val group = (start until endExclusive).map { blocks[it] as BodyBlock.Text }
            val head = group.first()

            val sb = StringBuilder()
            /** 焦点若落在段内某块：换算成它在合并块里的 raw 偏移（-1 = 焦点不在段内） */
            var focusOffsetInMerged = -1

            group.forEachIndexed { idx, block ->
                if (idx > 0) {
                    sb.append('\n')
                    /** 非首块的缩进差异 → 该行行首 EM 前缀（信息不丢；渲染为可见宽空格） */
                    if (block.indentLevel != head.indentLevel) {
                        sb.append(plainIndentPrefix(block.indentLevel))
                    }
                }
                if (focusBefore != null && focusBefore.blockId == block.id) {
                    /**
                     * 偏移换算（与 [mergeTextBlocks] 同款口径）：入参 offset 是 **raw**
                     * （含 ZWSP），而 sb 里累加的是剥过 ZWSP 的 markdown，故按「有效字数」
                     * 折算——clamp 到该块有效长度即可覆盖"光标在块尾"（raw 长度 = 有效 + 1）。
                     */
                    val effectiveLength = effectiveText(block.state.annotatedString.text).length
                    focusOffsetInMerged = sb.length + focusBefore.offset.coerceIn(0, effectiveLength)
                }
                sb.append(blockMarkdown(block.state))
            }

            val mergeCommand = ReplaceBlocksCommand(
                index = start,
                removedSpecs = group.map { textSpec(it) },
                insertedSpecs = listOf(
                    BlockSpec.TextSpec(
                        head.id,
                        sb.toString(),
                        /** 合并块继承**首块**的缩进档位（与退格合并一致） */
                        indentLevel = head.indentLevel,
                        isImageSeparator = false,
                    )
                ),
                focusBefore = focusBefore,
                focusAfter = if (focusOffsetInMerged >= 0) {
                    FocusSpec(head.id, focusOffsetInMerged)
                } else {
                    focusBefore ?: FocusSpec(head.id, 0)
                },
            )
            /**
             * **立即执行**（与 [normalizeImageSeparatorsInto] 同一约定，v2026-09-15 修复）。
             *
             * ⚠️ 调用方拿到命令后只做 [pushExecuted]，而它**只压栈、不执行**命令（其注释
             * 明确写着"命令已被调用方 apply 过"）。此前这里只 `into += command` 而没有
             * apply，导致合并命令**只进撤销栈、从未真正生效**——表现就是"编辑过程中不合并，
             * 直到下次加载才收敛"。
             */
            mergeCommand.apply(this)
            into += mergeCommand
        }
    }

    /**
     * 首/尾图前/后载体的**懒插入**入口（v2026-09-11 交互改版）：
     * 首块是**图片或分割线**时，点击首块上方空白 → 在文档最前插入一个载体空块；
     * 尾块对称——点击尾块下方空白 → 在文档最后追加一个。
     *
     * 归一化（[normalizeImageSeparators]）**不再自动补**首/尾载体（避免一进编辑页
     * 首图上方就多出一条空行），只有用户主动点边缘空白时才插入。插入经
     * [executeAndPush] 压栈——可撤销（撤销 = 按块 id 移除该载体，列表精确还原）；
     * 随后写 [pendingFocus] 让新载体块在下一帧落焦、弹软键盘，直接进入编辑态。
     *
     * @param head true = 首块前插入（index 0）；false = 尾块后插入（blocks.size）
     */
    fun insertEdgeSeparator(head: Boolean) {
        /**
         * 守卫与幂等（v2026-09-11 补强，防连点/误点）：
         * - 边缘块**已是载体**（上次点击已插入）→ 不重复插入，直接把焦点落回它
         *   （连点两次若都插入，旧载体会被挤成漂移块、下次归一化删除，撤销栈变脏）；
         * - 边缘块**是图片或分割线** → 插入载体（本交互的主路径；分割线同图属
         *   不可输入块，v2026-09-11 扩展）；
         * - 其它（无块 / 纯文本等）→ 无操作（与原纯空白 Spacer 行为一致——
         *   此时插出的载体不在合法位置，必被归一化删掉，等于无效操作）。
         */
        val edgeBlock = if (head) blocks.firstOrNull() else blocks.lastOrNull()
        when {
            edgeBlock is BodyBlock.Text && edgeBlock.isImageSeparator ->
                /** 已有载体：聚焦它（空块 offset 0 即行首），不重复插入 */
                pendingFocus = FocusSpec(edgeBlock.id, 0)
            edgeBlock is BodyBlock.Image || edgeBlock is BodyBlock.Divider -> {
                /** 首图前插到最前；尾图后追加到最后（[insertBlockAt] 内部对越界索引做收敛） */
                val index = if (head) 0 else blocks.size
                val command = InsertImageSeparatorCommand(
                    index = index,
                    spec = BlockSpec.TextSpec(newBodyBlockId(), "", isImageSeparator = true),
                )
                executeAndPush(command)
                /** 下一帧落到新载体块行首并弹软键盘（与其它插入路径同一焦点机制） */
                pendingFocus = FocusSpec(command.spec.id, 0)
            }
            /** 其它边缘块形态 → 无操作 */
            else -> Unit
        }
    }

    /**
     * 图片载体空块**不变量自检**（仅 [isDebugBuild] 生效，v2026-09-10）：
     * 1. 不允许存在两张**直接相邻**的图片（两者之间必须有可输入的块）；
     * 2. 每个带标记的载体都必须在合法位置（**紧邻图或分割线、且不与另一载体相邻**，
     *    v2026-09-11 v3），不得漂到不可输入块够不着的地方。
     *    懒插入改版：首/尾图前/后**没有**载体不算违规——默认无载体，用户点击边缘
     *    空白 / 分割线工具条按钮才插入，归一化只负责把"点出来的"载体维持住。
     *
     * 只在载体归一化之后调用，所以此时若仍违反 ⇒ 归一化漏了，是**真 bug**：
     * 前者对应"该补的载体没补"（两图直接相邻无法输入），后者对应"该删的漂移载体没删"
     * （空行堆积）。违反时只写 Logcat（`BlockSeparators` tag）、不抛异常——
     * 编辑过程不该因为自检崩掉。
     *
     * @param tag 触发来源，便于在日志里区分是哪个入口
     */
    private fun checkImageSeparatorInvariant(tag: String) {
        if (!isDebugBuild) return
        var adjacentImages = 0
        var straySeparators = 0
        for (i in blocks.indices) {
            if (i > 0 && blocks[i] is BodyBlock.Image && blocks[i - 1] is BodyBlock.Image) adjacentImages++
            val text = blocks[i] as? BodyBlock.Text
            if (text?.isImageSeparator == true) {
                /**
                 * 合法位置（与 [normalizeImageSeparators] 的删除判据同源，v3）：
                 * 紧邻至少一个不可输入块（图/线）且不与另一载体相邻。
                 */
                val prev = if (i > 0) blocks[i - 1] else null
                val next = if (i < blocks.lastIndex) blocks[i + 1] else null
                val neighborNonInput = prev is BodyBlock.Image || prev is BodyBlock.Divider ||
                    next is BodyBlock.Image || next is BodyBlock.Divider
                val neighborSeparator = (prev as? BodyBlock.Text)?.isImageSeparator == true ||
                    (next as? BodyBlock.Text)?.isImageSeparator == true
                if (!neighborNonInput || neighborSeparator) straySeparators++
            }
        }
        if (adjacentImages != 0 || straySeparators != 0) {
            android.util.Log.w(
                "BlockSeparators",
                "[$tag] 载体空块不变量被破坏：直接相邻图片 $adjacentImages 处、漂移载体 $straySeparators 个；" +
                    "块序列 = ${blocks.joinToString(" | ") { block ->
                        when (block) {
                            is BodyBlock.Image -> "图"
                            is BodyBlock.Divider -> "线"
                            is BodyBlock.Text -> if (block.isImageSeparator) "载体" else "文"
                        }
                    }}",
            )
        }
    }

    /**
     * 批量插入期间抑制 [onDocChanged]，由 [insertImagesAtFocused] 在末尾统一调一次。
     * （不然每张图都会触发一次 ViewModel.setContent/setContentFormat + 一次重组。）
     */
    private var suppressDocChanged: Boolean = false

    // ---------- 分割线插入 / 删除 / 高亮（v2026-09-07） ----------

    /**
     * 在聚焦块的光标处插入分割线（工具栏按钮入口）。
     *
     * 与插图同构的 [ReplaceBlocksCommand]：光标处拆块 `[前半Text, Divider, 后半Text]`；
     * 未聚焦 / 聚焦块是图片 / 分割线时退化为尾插。撤销/重做由命令栈自动承载。
     */
    fun insertDividerAtFocused() {
        val focusedIdx = focusedBlockId
            ?.let { id -> blocks.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }

        if (focusedIdx == null) {
            executeAndPush(buildInsertDividerAtEndCommand())
            return
        }
        when (val focused = blocks[focusedIdx]) {
            is BodyBlock.Text -> executeAndPush(buildInsertDividerInTextCommand(focused, focusedIdx))
            /** 图片/分割线块不持有光标：分割线插到文档末尾（末尾 Text 块之前） */
            else -> executeAndPush(buildInsertDividerAtEndCommand())
        }
    }

    /**
     * 在聚焦 Text 块的**光标处**插入分割线并拆块——
     * 光标前后的文字各自成段：`[前半Text, Divider, 后半Text]`。
     *
     * 拆段方式与 [buildInsertInTextCommand] 完全同源（[RichTextState.toMarkdown] 的
     * [TextRange] 重载按光标精确拆段），差异只在焦点落点：
     * - 光标在段中间：焦点落拆出的后半块 offset 0；
     * - 光标在段尾且原列表后方已有 Text 块（如「测试一|」+「测试二」两块）：
     *   **不补空尾块**，分割线直接落在两块之间，焦点落到已有块 offset 0
     *   （视觉与参考交互一致：分割线紧贴前后文字，无空行）；
     * - 光标在段尾且后方无 Text 块：补一个空尾块（保证分割线后仍可继续输入）。
     */
    private fun buildInsertDividerInTextCommand(
        focused: BodyBlock.Text,
        focusedIdx: Int,
    ): ReplaceBlocksCommand {
        val state = focused.state
        val rawText = state.annotatedString.text
        val cursor = state.selection.start.coerceIn(0, rawText.length)

        val beforeMd = if (cursor > 0) state.toMarkdown(TextRange(0, cursor)).replace(ZWSP, "") else ""
        val afterMd = if (cursor < rawText.length)
            state.toMarkdown(TextRange(cursor, rawText.length)).replace(ZWSP, "") else ""

        val dividerSpec = BlockSpec.DividerSpec(newBodyBlockId())
        val inserted = mutableListOf<BlockSpec>()
        /** 拆出的前后半继承源块的缩进档位（v2026-09-08；v2026-09-15 移除块级复选框属性） */
        val inheritedIndentLevel = focused.indentLevel
        if (beforeMd.isNotBlank()) {
            inserted += BlockSpec.TextSpec(
                newBodyBlockId(),
                beforeMd,
                indentLevel = inheritedIndentLevel,
            )
        }
        inserted += dividerSpec
        if (afterMd.isNotBlank()) {
            inserted += BlockSpec.TextSpec(
                newBodyBlockId(),
                afterMd,
                indentLevel = inheritedIndentLevel,
            )
        }

        /** 焦点落点：分割线之后的第一个 Text 块（含兜底补的空尾块） */
        val focusId: String
        val nextExistingText = blocks.drop(focusedIdx + 1).filterIsInstance<BodyBlock.Text>().firstOrNull()
        if (inserted.last() is BlockSpec.TextSpec) {
            /** 段中间拆块：后半块必为列表尾（afterMd 非空），焦点落它 */
            focusId = (inserted.last() as BlockSpec.TextSpec).id
        } else if (nextExistingText != null) {
            /** 段尾且原列表后方已有 Text 块：不补空块，焦点落到该已有块 */
            focusId = nextExistingText.id
        } else {
            /** 段尾且后方无 Text 块（文档末尾）：补空尾块保证线后可输入 */
            val tail = BlockSpec.TextSpec(newBodyBlockId(), "")
            inserted += tail
            focusId = tail.id
        }

        return ReplaceBlocksCommand(
            index = focusedIdx,
            removedSpecs = listOf(textSpec(focused)),
            insertedSpecs = inserted,
            focusBefore = currentFocusSpec(),
            focusAfter = FocusSpec(focusId, 0),
        )
    }

    /**
     * 尾插分割线（未聚焦 / 聚焦块非 Text 时）：插在末尾 Text 块之前。
     * 末尾已是 Text → 焦点落它（分割线与末块之间继续输入）；否则补空尾块。
     */
    private fun buildInsertDividerAtEndCommand(): ReplaceBlocksCommand {
        val dividerSpec = BlockSpec.DividerSpec(newBodyBlockId())
        val inserted = mutableListOf<BlockSpec>(dividerSpec)
        val focusId: String
        if (blocks.lastOrNull() is BodyBlock.Text) {
            focusId = blocks.last<BodyBlock>().id
        } else {
            val tail = BlockSpec.TextSpec(newBodyBlockId(), "")
            inserted += tail
            focusId = tail.id
        }
        /**
         * 插入锚点（与 [buildInsertImageAtEndCommand] 同款）：
         * - 末尾已是 Text（inserted 只有 Divider）：插在它前面 → index = size - 1
         * - 需补尾块（inserted = [Divider, 空Text]）：等效末尾追加 → index = size
         */
        val index = if (inserted.size == 1) (blocks.size - 1).coerceAtLeast(0) else blocks.size
        return ReplaceBlocksCommand(
            index = index,
            removedSpecs = emptyList(),
            insertedSpecs = inserted,
            focusBefore = currentFocusSpec(),
            focusAfter = FocusSpec(focusId, 0),
        )
    }

    /**
     * 按 id 删除分割线块（两步删除的确认步 / 点选后按键删除）。
     *
     * **删除语义（v2026-09-08 修订）：分割线那一行变成空行，而非整行消失**——
     * removed = [DividerSpec]，inserted = [空 TextSpec]，即「分割线块 → 空 Text 块」
     * 的就地替换；焦点落到该空行**行首**（offset 0），用户可直接接着输入
     * （行首 = 原分割线所在位置，视觉上就是"分割线变成了空行"）。
     *
     * 撤销即原位恢复分割线（revert 用 [ReplaceBlocksCommand.focusBefore] 回到删除前落点），
     * 样式随 [BlockSpec.DividerSpec.style] 一并往返（v2026-09-11 样式切换），恢复不丢。
     *
     * 副作用：新建的空块会按 ZWSP 不变量预置退格锚点，序列化时走 NBSP 占位段
     * （见 [EMPTY_BLOCK_PLACEHOLDER]），与既有空块行为一致。
     */
    fun deleteDividerBlock(blockId: String) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        /** 捕获被删分割线：样式随 removedSpecs 往返，撤销原位恢复时不丢样式（v2026-09-11） */
        val removed = blocks.getOrNull(idx) as? BodyBlock.Divider ?: return
        val emptySpec = BlockSpec.TextSpec(newBodyBlockId(), "")
        /**
         * 焦点迁移期保持光标隐藏（v2026-09-08）：删除后焦点要从"下一行"迁到这条
         * 新空行，迁移跨帧完成；若此刻就让光标可见，会先在下一行行首闪一下再跳过来。
         * 由 [onBlockFocused] 在焦点落定后解除（[hideCursorUntilFocusBlockId]）。
         */
        hideCursorUntilFocusBlockId = emptySpec.id
        executeAndPush(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(BlockSpec.DividerSpec(blockId, removed.style)),
                insertedSpecs = listOf(emptySpec),
                focusBefore = currentFocusSpec(),
                /** 空块 raw 长度 1（ZWSP），偏移 0 由 ZWSP 不变量维护推到 (1, 1) */
                focusAfter = FocusSpec(emptySpec.id, 0),
            )
        )
    }

    /**
     * 分割线邻接载体行**查询**（v2026-09-11 工具条接线）：本分割线 [above] 方向的
     * 相邻块是否是载体空块——分割线工具条「向上/向下添加载体行」按钮的**激活态依据**
     * （相邻已是载体 = 已添加 → 图标呈激活色；再点即取消）。读 [blocks]（state），
     * toggle 后随重组自动刷新。
     */
    fun hasDividerNeighborSeparator(blockId: String, above: Boolean): Boolean {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return false
        val neighbor = if (above) blocks.getOrNull(idx - 1) else blocks.getOrNull(idx + 1)
        return neighbor is BodyBlock.Text && neighbor.isImageSeparator
    }

    /**
     * 分割线邻接载体行 **toggle**（v2026-09-11 工具条按钮）：在 [above] 方向相邻位置
     * 添加或取消一行载体空块——
     * - 相邻块**是载体** → 删除它（取消添加；[RemoveImageSeparatorCommand] 撤销可原位恢复）；
     * - 相邻块**不是载体**（文本 / 图片 / 分割线 / 越界）→ 插入载体（[above] 插到本
     *   分割线之前、否则之后；[InsertImageSeparatorCommand] 撤销可移除）。
     * 每个方向最多一行：按钮本身是 toggle（有则删、无则加），不会重复堆叠。
     *
     * **高亮保持**：[executeAndPush] 内部的 [afterCommandMutation] 会清点选态——
     * 命令同步执行完毕后立即恢复本分割线的高亮与手指位置（同一帧内完成，无闪烁），
     * 工具条不消失，用户可连续 toggle 上/下或点删除。
     * **不动焦点**：工具条是 Popup（focusable=false），焦点/软键盘全程不受影响。
     */
    fun toggleDividerNeighborSeparator(blockId: String, above: Boolean) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0 || blocks.getOrNull(idx) !is BodyBlock.Divider) return
        /** 保存点选态：命令收尾（afterCommandMutation）会清，执行后恢复，工具条不闪没 */
        val savedTapX = highlightedTapX
        val neighborIndex = if (above) idx - 1 else idx + 1
        val neighbor = blocks.getOrNull(neighborIndex)
        if (neighbor is BodyBlock.Text && neighbor.isImageSeparator) {
            /** 已有载体行 → 取消：删除并压栈（撤销 = 原位插回） */
            executeAndPush(
                RemoveImageSeparatorCommand(neighbor.id, textSpec(neighbor), neighborIndex)
            )
        } else {
            /** 无载体行 → 添加：above 插到分割线之前（index=idx），below 插到之后（idx+1） */
            val insertIndex = if (above) idx else idx + 1
            executeAndPush(
                InsertImageSeparatorCommand(
                    index = insertIndex,
                    spec = BlockSpec.TextSpec(newBodyBlockId(), "", isImageSeparator = true),
                )
            )
        }
        /** 恢复点选态：分割线保持高亮、工具条保持在场（同一帧，无闪烁） */
        highlightedBlockId = blockId
        highlightedTapX = savedTapX
    }

    /**
     * 就地更新分割线样式（v2026-09-11 样式切换，[UpdateDividerStyleCommand] 的底层）：
     * Divider 块无 RichTextState，**就地换块对象零损失**（仿 [UpdateImagePropsCommand]
     * 的重建策略）——以同 id + 新样式构造 [BodyBlock.Divider] 替换列表项，
     * 拖拽 key（块 id）不变，ReorderableColumn 状态不受扰动。
     *
     * ⚠️ 可见性必须为 internal（同 [updateImagePropsById]）：apply/revert 在**类外**
     * 顶层 Command 中调用，private 会报 "it is private" 编译错误（v2026-09-11 实证）。
     */
    internal fun updateDividerStyleById(blockId: String, style: DividerStyle) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0 || blocks.getOrNull(idx) !is BodyBlock.Divider) return
        blocks[idx] = BodyBlock.Divider(blockId, style)
    }

    /**
     * 分割线样式 **toggle**（v2026-09-11 工具条虚线/波浪线按钮）：
     * 当前样式等于 [style] → 回到默认实线（[DividerStyle.SOLID]）；否则切到 [style]。
     * 压栈 [UpdateDividerStyleCommand]（撤销 = 回旧样式，再重做 = 回新样式）。
     *
     * **高亮保持**：与 [toggleDividerNeighborSeparator] 同款——命令收尾
     * （[afterCommandMutation]）会清点选态，命令同步执行完毕后立即恢复本分割线的
     * 高亮与手指位置（同一帧内完成，无闪烁），工具条不消失，用户可连续切换样式
     * （实线→虚线→实线 / 实线→波浪→实线）。
     * **不动焦点**：工具条是 Popup（focusable=false），焦点/软键盘全程不受影响。
     */
    fun toggleDividerStyle(blockId: String, style: DividerStyle) {
        val current = blocks.firstOrNull { it.id == blockId } as? BodyBlock.Divider ?: return
        /** 保存点选态：命令收尾（afterCommandMutation）会清，执行后恢复，工具条不闪没 */
        val savedTapX = highlightedTapX
        val target = if (current.style == style) DividerStyle.SOLID else style
        executeAndPush(UpdateDividerStyleCommand(blockId, current.style, target))
        /** 恢复点选态：分割线保持高亮、工具条保持在场（同一帧，无闪烁） */
        highlightedBlockId = blockId
        highlightedTapX = savedTapX
    }

    /**
     * 两步删除第一步（相邻块退格 / Delete 点亮）：**只高亮，不弹悬浮按钮**——
     * 高亮来源是键盘操作而非点击，没有"手指位置"可言；按钮若在此时弹出，会
     * 凭空出现在行首（tapX=0）或上次点击的旧位置，观感突兀（v2026-09-08 用户反馈）。
     * 图片与分割线的高亮统一走此入口，[highlightedTapX] 置 null 以示区分。
     */
    private fun highlightForTwoStepDelete(blockId: String) {
        highlightedBlockId = blockId
        highlightedTapX = null
        /** 两步删除高亮不是点选 → 不弹图片工具栏（若此前点选过本块则一并收起） */
        imageToolbarBlockId = null
    }

    /**
     * 点击分割线（块 Composable 入口）：**切换选中态**（v2026-09-08 第四版）。
     * 未高亮 → 高亮；已高亮 → 取消。**全程不动焦点**——焦点留在原 Text 块，
     * 软键盘不收起。
     *
     * 删除入口为高亮态的**悬浮"删除"按钮**（[BlockDividerItem] 用 `Popup`
     * 渲染，x 跟随本参数 tapX 手指位置）——软键盘退格无法拦截（IME 走
     * `deleteSurroundingText`，Compose 无对应 API），触屏删除改走按钮。
     * 光标存在时的两步删除（[onBackspaceAtStart] / [onDeleteAtEnd]）保留不变，
     * 其点亮路径见 [highlightForTwoStepDelete]（不弹按钮）。
     *
     * 已高亮时的再点击语义按来源区分：
     * - 点选态（有按钮）→ 取消选中；
     * - 退格两步删除点亮（无按钮）→ **补弹按钮**（把点击位置交给按钮），再点才取消。
     */
    fun onDividerTapped(blockId: String, tapX: Float) {
        if (highlightedBlockId == blockId) {
            if (highlightedTapX != null) {
                /** 已是点选态（按钮在场）→ 再点分割线 = 取消选中 */
                clearBlockSelection()
            } else {
                /** 退格两步删除点亮的高亮（无按钮）→ 点击补弹按钮，不取消 */
                highlightedTapX = tapX
            }
            return
        }
        highlightedBlockId = blockId
        highlightedTapX = tapX
    }

    /**
     * 点击图片块（块 Composable 入口）：**切换选中态**（v2026-09-09）。
     * 未高亮 → 高亮；已高亮 → 取消。**全程不动焦点**——焦点留在原 Text 块，
     * 软键盘不收起。
     *
     * v2026-09-09 行为变更：图片点击**不再打开全屏图片查看器**（用户要求移除
     * 该入口），改为与分割线一致的点选高亮。与分割线的两点差异：
     * - **没有悬浮删除按钮**——删除仍走光标退格 / Delete 的两步删除
     *   （[highlightForTwoStepDelete] 点亮，再按一次删除）；
     * - 退格两步删除点亮后**再点图片 = 直接取消选中**（无按钮可补弹，
     *   与分割线"补弹按钮"分支不同）。
     */
    fun onImageBlockTapped(blockId: String) {
        if (highlightedBlockId == blockId && imageToolbarBlockId == blockId) {
            /** 已是点选态（工具栏在场）→ 再点图片 = 取消选中 */
            clearBlockSelection()
        } else {
            /** 未选中 / 仅被两步删除点亮 → 进入点选态（工具栏出现） */
            highlightedBlockId = blockId
            imageToolbarBlockId = blockId
            highlightedTapX = null
        }
    }

    // ---------- 任务列表 / 复选框（v2026-09-15：改由库的段落类型承载） ----------

    /**
     * 工具栏「复选框」按钮入口：对聚焦块光标所在**行**切换任务行标记。
     *
     * v2026-09-15 改道：块级 `checked` → 库的段落类型（marker、层级缩进、勾选框
     * 绘制与文字降级全由库同步；块对象、块内 history 与光标完全不动）。
     * v2026-09-16「仅光标行转换」：调库的
     * `RichTextState.toggleTaskListAtTextOffset(光标)`——普通多行段落里**只有光标行**
     * 变任务行（段落切为 TaskList + `taskLines` 单行集合，**不拆段**），其余行保持
     * 普通文本（无勾选框、markdown 裸行）；再点一次取消该行（段落因此无任务行时
     * 整段退回 Default）。纯任务块（段落级）回车续行仍是全行任务项（旧行为）。
     *
     * 未聚焦 / 聚焦块是图片或分割线（不持有光标）→ 尾插一个任务列表空项
     * （[buildAppendTaskListCommand]，焦点落到新项）。
     */
    fun toggleCheckboxAtFocused() {
        val focusedIdx = focusedBlockId
            ?.let { id -> blocks.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }

        val focused = focusedIdx?.let { blocks[it] }
        if (focused !is BodyBlock.Text) {
            executeAndPush(buildAppendTaskListCommand())
            return
        }

        focused.state.toggleTaskListAtTextOffset(focused.state.selection.min)
        onDocChanged?.invoke()
    }

    /**
     * 尾插**任务列表空项**（未聚焦 / 聚焦块非 Text 时）：插在末尾 Text 块之前，
     * 焦点落到新项（可直接输入待办内容）。
     *
     * v2026-09-15 改道：不再用块级 `checked`，而是把 `- [ ] ` 作为块的 markdown
     * 载荷——库解码后即为 `TaskList` 段落（与在已有块上 toggle 等价）。
     */
    private fun buildAppendTaskListCommand(): ReplaceBlocksCommand {
        val taskListSpec = BlockSpec.TextSpec(newBodyBlockId(), TASK_LIST_MD_UNCHECKED)
        /** 末尾是 Text → 插在它前面（保留「末尾可继续输入」不变量）；否则直接追加 */
        val index = if (blocks.lastOrNull() is BodyBlock.Text) blocks.size - 1 else blocks.size
        return ReplaceBlocksCommand(
            index = index.coerceAtLeast(0),
            removedSpecs = emptyList(),
            insertedSpecs = listOf(taskListSpec),
            focusBefore = currentFocusSpec(),
            /** 光标落 NBSP 之后（offset 1）：框右侧、与上文对齐（v2026-09-16） */
            focusAfter = FocusSpec(taskListSpec.id, 1),
        )
    }

    /**
     * 缩进档位落盘（[SetBlockIndentCommand] 用，v2026-09-08）：就地替换块对象，
     * **保持 state / focusRequester 引用不变**——不触发 observer 重启、不丢块内
     * 编辑历史、光标位置不动。markdown 载体随 onDocChanged 链路自动保存。
     *
     * 只服务普通段落；任务列表（复选框）的缩进由库的段落层级承载。
     */
    internal fun setBlockIndent(blockId: String, indentLevel: Int) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(idx)
        if (block is BodyBlock.Text && block.indentLevel != indentLevel) {
            blocks[idx] = BodyBlock.Text(
                block.id,
                block.state,
                block.focusRequester,
                indentLevel = indentLevel,
                /** 就地换对象也要带上载体身份（v2026-09-10） */
                isImageSeparator = block.isImageSeparator,
            )
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
     * （退格合并——重放不该再触发），但保留 ZWSP 不变量维护。
     *
     * ⚠️ v2026-09-15 起回车不再拆块（换行留在块内），"结构检测"只剩退格合并路径。
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
    /**
     * **统一的「执行主命令 → 结构归一化 → 压栈」入口**（v2026-09-15 收敛）。
     *
     * 顺序固定：
     * 1. 在 [replaying] 门控内 **apply 主命令**，随后 [renumberOrderedBlocks]；
     * 2. 跑结构归一化 [normalizeStructureInto]（**它自己 apply 自己的命令**）；
     * 3. 主命令与归一化命令打包成一个 [CompositeCommand]，由 [pushExecuted] 压栈
     *    ——⚠️ [pushExecuted] **只记账、不执行**命令。
     *
     * **为什么要收敛**：此前 `executeAndPush` / `executeAndPushWithImageSeparators` /
     * `moveBlock` / `insertImagesAtFocused` 各自手写这套流程——新增一条归一化规则要逐个
     * 入口补调用，而且"归一化命令要不要自己 apply"两者约定还不一致，出过"只有加载时才
     * 归一化、编辑过程中静默失效"的缺陷。现在需要维护的只有本函数与 [normalizeStructureInto]。
     *
     * @param normalizeSeparators 是否跑载体空块归一化：图片 / 分割线相关命令传 true；
     *   纯文本类命令传 false——文本变更不改变图片相邻关系，省一次无意义扫描。
     * @param tag 归一化来源标签（载体不变量自检日志用）。
     */
    private fun executeWithNormalization(
        command: BodyBlocksCommand,
        normalizeSeparators: Boolean,
        tag: String,
    ) {
        val extra = mutableListOf<BodyBlocksCommand>()
        replaying = true
        try {
            command.apply(this)
            /** 结构变更（合并/退列表/插图等）后按位置语义重排连续有序块编号，
             *  在 replaying 门控内执行，避免被重写块的 observer 误清 redo 栈。 */
            renumberOrderedBlocks()
            normalizeStructureInto(extra, tag = tag, normalizeSeparators = normalizeSeparators)
            if (extra.isNotEmpty()) renumberOrderedBlocks()
        } finally {
            replaying = false
        }
        pushExecuted(if (extra.isEmpty()) command else CompositeCommand(listOf(command) + extra))
    }

    /**
     * 执行并压栈一条**不涉及图片相邻关系**的命令（打字 / 退格 / 缩进 / 插入分割线等）：
     * 只跑「相邻文本块合并」归一化，跳过载体空块扫描。
     *
     * ⚠️ undo / redo 不经过本入口——撤销后不会被立刻又合并回去（否则撤销失去意义）。
     */
    private fun executeAndPush(command: BodyBlocksCommand) {
        executeWithNormalization(command = command, normalizeSeparators = false, tag = "命令")
    }

    /**
     * 执行并压栈一条与**图片 / 分割线**相关的命令（插图 / 删图 / 删分割线 / 拖拽落位）：
     * 走 [executeWithNormalization]，除「相邻文本块合并」外**额外跑载体空块归一化**——
     * **两图直接相邻处补空 Text 块；带标记的空白块不再夹在两图之间则删**
     * （v2026-09-09 插图路径；v2026-09-11 起删除图片路径同样使用——
     * 删中间图后 1[空][空]3 → 清两个漂移载体 + 补一个 → 1[空]3）。
     *
     * 归一化命令与主命令打包成一个 [CompositeCommand]：**一次撤销整体回退**
     * （revert 逆序执行 → 先移除补的空块，再回退主命令），用户不会看到
     * "撤销一次只撤掉空行、顺序还没变"的中间态。
     */
    private fun executeAndPushWithImageSeparators(command: BodyBlocksCommand) {
        executeWithNormalization(command = command, normalizeSeparators = true, tag = "图片相关")
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
            /** 撤销恢复的 specs 带历史编号，按位置语义重排收敛（确定性函数，幂等） */
            renumberOrderedBlocks()
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
            /** 与 undoBlocks 对称：重放后按位置语义收敛有序块编号 */
            renumberOrderedBlocks()
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
                val result = state.history.undo()
                /** 块内 history 撤销可能恢复/移除列表 marker（如撤销"删 marker 退列表"），
                 *  重排后续连续有序块编号以收敛到位置语义（其余场景为幂等空转） */
                renumberOrderedBlocks()
                return result
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
                val result = state.history.redo()
                /** 与 undo 对称：块内 history 重做后按位置语义收敛有序块编号 */
                renumberOrderedBlocks()
                return result
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

    /** 按 [BlockSpec] 重建块（Text 走 setMarkdown 还原富文本样式；Image 只存 uri；
     *  Divider 零参数重建）。命令重建路径剥离层级前缀（stripListLevelPrefix=true），
     *  层级经 setListMarker 还原。复选框块（v2026-09-07）checked 随 spec 透传，
     *  spec.markdown 已剥复选框前缀（不进块内 state）。 */
    private fun rebuildBlock(spec: BlockSpec): BodyBlock = when (spec) {
        is BlockSpec.TextSpec -> createTextBlock(
            spec.markdown,
            spec.id,
            spec.initialListType,
            spec.orderedStartNumber,
            spec.listLevel,
            stripListLevelPrefix = true,
            indentLevel = spec.indentLevel,
            /** 载体身份随 spec 还原（v2026-09-10），否则撤销后重建的载体被判成"用户空行"而不再清理 */
            isImageSeparator = spec.isImageSeparator,
        )
        is BlockSpec.ImageSpec -> BodyBlock.Image(spec.id, spec.path, spec.note, spec.shrunk)
        is BlockSpec.DividerSpec -> BodyBlock.Divider(spec.id, spec.style)
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
            /** 就地换对象：保留 note / shrunk（v2026-09-09 起图片块携带属性） */
            blocks[idx] = BodyBlock.Image(blockId, path, block.note, block.shrunk)
        }
    }

    /**
     * 图片块属性落盘原语（[UpdateImagePropsCommand] 的 apply/revert 共用）：
     * 就地换块对象（Image 块无 RichTextState，重建零损失）。
     */
    internal fun updateImagePropsById(blockId: String, note: String?, shrunk: Boolean) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(idx) as? BodyBlock.Image ?: return
        if (block.note != note || block.shrunk != shrunk) {
            blocks[idx] = BodyBlock.Image(blockId, block.path, note, shrunk)
        }
    }

    /**
     * 工具栏「缩小 / 恢复原尺寸」入口（v2026-09-09）：翻转 [BodyBlock.Image.shrunk]，
     * 走 [UpdateImagePropsCommand] 可撤销；缩放动作后<b>退出选中</b>（原型交互：
     * 工具栏消失，用户再次点击缩小图重新选中，见 [onImageBlockTapped]）。
     */
    fun toggleImageShrunk(blockId: String) {
        val block = blocks.firstOrNull { it.id == blockId } as? BodyBlock.Image ?: return
        executeAndPush(
            UpdateImagePropsCommand(
                blockId = blockId,
                oldNote = block.note,
                oldShrunk = block.shrunk,
                newNote = block.note,
                newShrunk = !block.shrunk,
            )
        )
    }

    /**
     * 图片备注编辑（工具栏「备注」入口后的输入，v2026-09-09）：
     * onValueChange 直写块对象并触发文档保存——与正文打字同级，**不进全局命令栈**
     * （正文文字也走块内 history 而非命令栈；图片无库内 history，备注撤销暂不支持）。
     * 空白文本归一化为 null（未添加备注）。
     */
    fun updateImageNote(blockId: String, text: String) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(idx) as? BodyBlock.Image ?: return
        val newNote = text.takeIf { it.isNotBlank() }
        if (block.note != newNote) {
            blocks[idx] = BodyBlock.Image(blockId, block.path, newNote, block.shrunk)
            notifyBlockChanged()
        }
    }

    /**
     * 按图片路径回填持久化属性（备注 / 显示宽度比例，v2026-09-09）。
     *
     * 编辑页加载时调用：`initialize` 解析 markdown 只能还原 path（note / 缩放态
     * 不在 markdown 里），需要把 content_blocks 表（v57 预留列）读出的属性
     * 按 **path**（图片文件路径天然唯一）回填到对应 Image 块。
     *
     * - ratio < 1.0 → 缩小态（[com.corgimemo.app.ui.model.IMAGE_SHRUNK_WIDTH_RATIO]）
     * - 回填**不触发** onDocChanged（还原持久化状态不算编辑）
     * - markdown 里没有的 path 自然不命中，旧数据零影响
     */
    fun applyImageProps(props: Map<String, com.corgimemo.app.ui.model.ContentBlock.Image>) {
        for (i in blocks.indices) {
            val block = blocks[i] as? BodyBlock.Image ?: continue
            val prop = props[block.path] ?: continue
            val newNote = prop.note?.takeIf { it.isNotBlank() }
            val newShrunk = (prop.displayWidthRatio ?: 1f) < 1f
            if (block.note != newNote || block.shrunk != newShrunk) {
                blocks[i] = BodyBlock.Image(block.id, block.path, newNote, newShrunk)
            }
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

    /**
     * 在 [index] 处插入一个由 [spec] 重建的块（[InsertImageSeparatorCommand] 的落盘原语）。
     * 索引钳到 `[0, size]`，保证命令 apply/revert 不因边界越界抛错。
     */
    internal fun insertBlockAt(index: Int, spec: BlockSpec) {
        blocks.add(index.coerceIn(0, blocks.size), rebuildBlock(spec))
    }

    /** 按 id 移除块（[InsertImageSeparatorCommand.revert] 的落盘原语；已不存在则忽略） */
    internal fun removeBlockById(blockId: String) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx >= 0) blocks.removeAt(idx)
    }

    /** Command 落盘后的通用收尾：两步删除的高亮态随结构变化清除（含选中态锚点） */
    internal fun afterCommandMutation() {
        clearBlockSelection()
    }

    // ---------- 非文本块「点选」态（v2026-09-08 第四版：删除走悬浮按钮） ----------

    /**
     * 「焦点迁移期间保持光标隐藏」的目标块 id（v2026-09-08 光标跳变修复）。
     *
     * **问题**：删除分割线后焦点要从"当前位置"迁到"新空行"，但焦点迁移是**异步**的
     * （[focusSpec] 只写 [pendingFocus]，真正的 `requestFocus` 由块 Composable 的
     * [LaunchedEffect] 在下一帧执行）；而 [afterCommandMutation] 是**同步**的——
     * 它在命令 apply 的末尾就把选中态清掉。若不加处理，用户会看到"光标在旧位置
     * 亮一下，再跳到分割线行行首"的跳变。
     *
     * **解法**：删除时把新块 id 记在这里，光标隐藏状态延续到**焦点真正落定**
     * （[onBlockFocused]）才解除——整段迁移期光标都不可见，落定后在目标行首
     * 直接出现，视觉上不跳。
     *
     * 兜底：[isCursorVisuallyHidden] 会校验该块是否还存在于 [blocks]
     * （块被移除则自动恢复），[onBlockFocused] 亦无条件解除（任何块获焦都恢复）。
     */
    internal var hideCursorUntilFocusBlockId by mutableStateOf<String?>(null)
        private set

    /**
     * 光标是否应**视觉隐藏**（[BlockTextItem] 的 `cursorColor` 判据）：
     * 仅在焦点迁移期间（[hideCursorUntilFocusBlockId] 非空且目标块仍在列表）。
     */
    val isCursorVisuallyHidden: Boolean
        get() = hideCursorUntilFocusBlockId?.let { id -> blocks.any { it.id == id } } == true

    // ---------- 跨块文字选择操作（v2026-09-11 新增） ----------

    /**
     * 退出「点选非文本块」态：清高亮 + 清点选手指位置（焦点/键盘不动）。
     * 开始输入字符（文本变长）、执行任何命令（[afterCommandMutation]）、
     * 点击其它文本块（[onBlockFocused]）都会走到这里。
     */
    fun clearBlockSelection() {
        if (highlightedBlockId == null && highlightedTapX == null && imageToolbarBlockId == null) return
        highlightedBlockId = null
        highlightedTapX = null
        imageToolbarBlockId = null
    }

    /**
     * Text 块被按下（v2026-09-08）：**任何**按下动作都退出分割线 / 图片选中态。
     *
     * 为什么需要它：点击**光标所在的已聚焦块**不产生焦点变化
     * （[onBlockFocused] 不会被调用），高亮原本会残留；由 [BlockTextItem] 的
     * Row 用非消费的 `awaitFirstDown` 观测按下并调到这里（不影响 TextField
     * 自身的点击定位 / 长按选择手势）。
     */
    fun onTextBlockPressed() {
        clearBlockSelection()
    }

    /**
     * 捕获当前焦点落点（Command 构造时的 focusBefore / focusAfter）。
     *
     * **返回原始坐标（raw offset，直接索引 [RichTextState.annotatedString.text]）**。
     * 这是 [FocusSpec.offset] 的统一语义——[applyFocusAndCursor]、[focusSpec]、
     * 以及 [BlockTextItem] 的 [LaunchedEffect] 都把 offset 当 raw 直接写进
     * [RichTextState.selection]。
     *
     * 关键 bug 来源（见方案A撤销回归 #2）：之前这里把光标「映射到有效坐标
     * （effectiveText(...).length，剥 ZWSP）」再存储。但对直接打字产生的块
     * （如 \u200B一二，带前导 ZWSP），effective 1 ≠ raw 1——还原时把「有效 1」当
     * raw 1 写进 selection，落点变成「ZWSP 与『一』之间 = 『一』左边」，撤销图片后
     * 光标错位。改为直接存 raw 坐标，落点即用户真实的光标位置。
     */
    private fun currentFocusSpec(): FocusSpec? {
        val focusKey = focusedBlockId ?: pendingFocus?.blockId ?: return null
        val focused = blocks.firstOrNull { it.id == focusKey } as? BodyBlock.Text ?: return null
        val rawText = focused.state.annotatedString.text
        val rawCursor = focused.state.selection.start.coerceIn(0, rawText.length)
        return FocusSpec(focused.id, rawCursor)
    }

    /** 按落点描述恢复焦点与光标（Command 的 focusBefore / focusAfter 落地） */
    internal fun focusSpec(spec: FocusSpec) {
        val target = blocks.firstOrNull { it.id == spec.blockId } as? BodyBlock.Text
        if (target == null) {
            focusFirstTextBlock()
            return
        }
        /** offset 是 raw 坐标（索引 annotatedString.text）；按 raw 长度夹取而非有效长度 */
        val rawLen = target.state.annotatedString.text.length
        applyFocusAndCursor(target, spec.offset.coerceIn(0, rawLen))
    }

    /**
     * 把焦点与光标**同步**落到 [target] 的 [offset]。
     *
     * [offset] 的语义是**原始坐标（raw）**——直接索引进 [target.state.annotatedString.text]，
     * 与 [FocusSpec.offset] 的统一定义一致（[currentFocusSpec] 也返回 raw）。
     *
     * 三处一起写，缺一不可：
     * 1. [pendingFocus]（[FocusSpec]，原子打包块 id + 偏移）——供块 Composable 的
     *    [LaunchedEffect] 申请真实焦点（[FocusRequester.requestFocus] 只能异步执行）；
     *    用单一状态而非两个独立 [mutableStateOf]，避免 LaunchedEffect 以 id 为 key 重发射时
     *    读到过期的 offset（把已设好的光标覆盖成 0）；
     * 2. [focusedBlockId]——undo/redo 后的命令焦点依赖它定位焦点块，
     *    为 null 会导致 [currentFocusSpec] 捕获到错误落点；
     * 3. [RichTextState.selection]——同步写入（避免 [focusSpec] 后续 coerce 上界异步读到初始值）；
     *    只改 selection 不改 text，不会触发 BlockTextItem observer 的结构检测
     *    （markdown 不含光标信息）；重放期间 [replaying] 亦为 true。
     *
     * 空块（text = ZWSP）由块内 ZWSP 维护 [LaunchedEffect] 兜底推到 (1, 1)；
     * 非空块带前导 ZWSP（如直接打字产生的 \u200B一二）时，raw offset 已正确表达
     * 「ZWSP 之后的真实位置」，无需再做有效/原始换算。
     */
    private fun applyFocusAndCursor(target: BodyBlock.Text, offset: Int) {
        pendingFocus = FocusSpec(target.id, offset)
        focusedBlockId = target.id
        val rawLen = target.state.annotatedString.text.length
        target.state.selection = TextRange(offset.coerceIn(0, rawLen))
    }

    /**
     * 原子地取走并清空待落焦描述（[pendingFocus]）。
     *
     * 供块 Composable 在 [LaunchedEffect] 中做「取走即清空」式消费：命中块（id 匹配）
     * 取走一次后即把字段置空，避免同帧其它块 effect 读到过期/重复值、或在此后某次
     * 重组重发射时二次应用光标。返回 null 表示当前无待落焦请求。
     *
     * 注意：调用方须先确认 [FocusSpec.blockId] 命中本块再调用，避免误清空其它块的请求。
     */
    internal fun takePendingFocus(): FocusSpec? {
        val pf = pendingFocus
        pendingFocus = null
        return pf
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

    /**
     * 「标题区换行」的落点入口（v2026-09-15）：把光标交到**正文首块的首行最前面**。
     *
     * 标题是单行输入，用户在标题里换行时不该在标题内产生第二行，而应直接进入正文书写，
     * 故由编辑页在换行时调用本方法（硬键盘回车经 `onPreviewKeyEvent` 拦截、
     * 软键盘换行与多行粘贴经标题文本变更检测兜住）。
     *
     * 落点分两种情形——首图前默认没有载体空块（懒插入改版），首块是图片时无法在
     * 「正文最前面」落笔，所以要新建一个空块：
     * - 首块是 Text 块 → 落焦到它的**内容起点**（见下），保证落下去就能直接打字；
     * - 首块是图片 / 分割线等**不可输入块** → 复用 [insertEdgeSeparator] 在文档最前插入
     *   一个载体空块并落焦（自带幂等守卫 + 一步可撤销；载体一有内容即自动降级为普通块）。
     *
     * **落点必须是「内容起点」而不是 raw 0**（v2026-09-15）：库里列表 marker（`• ` / `1. `）
     * 与空块的 ZWSP 占位都是**内联文本**，raw 0 落在它们**之前**——光标画在那儿与
     * 内容起点视觉无差别，但一打字就会插到 marker 左边，得到 `a• ` / `a\u200B` 这类坏结构：
     * - 逻辑空块（`text` 以 ZWSP 开头）→ 越过 ZWSP，落 1（与块内 ZWSP 维护兜底同值，显式写可免一次矫正）；
     * - 列表块 → 交给 [refocusListBlock] 用 marker 前缀反推的精确落点（非列表块内部直接跳过）；
     * - 其余 → raw 0 即内容起点。
     */
    fun focusBodyFirstLine() {
        val first = blocks.firstOrNull()
        if (first !is BodyBlock.Text) {
            /** 图片 / 分割线：在 index 0 懒插入载体空块（其内部已写到 pendingFocus，下一帧落焦） */
            insertEdgeSeparator(head = true)
            return
        }
        val text = first.state.annotatedString.text
        applyFocusAndCursor(first, if (text.startsWith(ZWSP)) 1 else 0)
        /** 列表块：marker 之后才是内容起点（覆盖刚写入的 0；非列表块不进任何分支） */
        refocusListBlock(first.id)
    }

    // ---------- 删除 / 合并 ----------

    /**
     * 按 id 删除图片块（两步删除的确认步 / 工具栏「删除图片」按钮）。
     * v2026-09-02 Command 化：removed = [ImageSpec]，inserted = []。
     * v2026-09-11 归一化：改走 [executeAndPushWithImageSeparators]——删除后若造成
     * 两图相邻（如 1[空]2[空]3 删 2 → 1[空][空]3），[normalizeImageSeparators] 会
     * 清掉漂移载体再补插**恰好一个**空块（1[空]3），载体空块不变量始终成立；
     * 补块/清块与主删除打包成同一个 [CompositeCommand]，撤销一步整体回退。
     */
    fun deleteImageBlock(blockId: String) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(idx)
        if (block !is BodyBlock.Image) return
        executeAndPushWithImageSeparators(
            ReplaceBlocksCommand(
                index = idx,
                /** removedSpecs 带 note / shrunk（v2026-09-09），兜底重建路径不丢属性 */
                removedSpecs = listOf(BlockSpec.ImageSpec(block.id, block.path, block.note, block.shrunk)),
                insertedSpecs = emptyList(),
                focusBefore = currentFocusSpec(),
                /** 删图时焦点本就不在图片上，保持当前落点即可 */
                focusAfter = currentFocusSpec(),
            )
        )
    }

    /** 按路径删除图片块（画廊删除入口），返回是否删除。删除后同步归一化载体空块（见 [deleteImageBlock]） */
    fun deleteImageByPath(path: String): Boolean {
        val idx = blocks.indexOfFirst { it is BodyBlock.Image && it.path == path }
        if (idx < 0) return false
        val block = blocks[idx] as BodyBlock.Image
        executeAndPushWithImageSeparators(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(BlockSpec.ImageSpec(block.id, block.path, block.note, block.shrunk)),
                insertedSpecs = emptyList(),
                focusBefore = currentFocusSpec(),
                focusAfter = currentFocusSpec(),
            )
        )
        return true
    }

    /**
     * 缩进块"减一级缩进"：[onBackspaceAtStart] 的缩进分支实现（v2026-09-08 改 App 布局级），
     * 并被**回车路径**复用——空缩进行回车 = 减一级（Word 标准）。
     *
     * v2026-09-15 起由编辑层直接调用：硬键盘在 `onPreviewKeyEvent` 里调，软键盘经
     * [dedentBlankIndentLineAfterSoftEnter] 调。缩进是 **App 布局级属性、库不知情**
     * （历史原因：库对纯文本段 TextIndent 的渲染量实测不准），因此该语义必须留在
     * App 侧，不能交给库的段落类型。
     *
     * 走 [SetBlockIndentCommand] 就地换块对象（indentLevel -1，state / history /
     * 光标无损，一步撤销）——不再经 ReplaceBlocksCommand 重建（旧版 EM+ZWSP
     * 重建方案随库 TextIndent 缩进一并废弃）。
     */
    internal fun dedentBlockAtContentStart(block: BodyBlock.Text) {
        if (block.indentLevel <= 1) return
        executeAndPush(
            SetBlockIndentCommand(block.id, block.indentLevel, block.indentLevel - 1)
        )
    }

    /**
     * 软键盘在**空缩进行**回车后的补偿（v2026-09-15 取消拆块配套）。
     *
     * 库已把 `\n` 写进块内，这里把该块的文本恢复为空行（撤掉那个 `\n`）并把缩进降一级
     * ——与硬键盘 `onPreviewKeyEvent` 的同名分支语义一致（空缩进行回车 = 减一级缩进）。
     * 整块重建（[ReplaceBlocksCommand]，文本取自 spec），因此一步可撤销。
     *
     * 仅 `indentLevel > 1` 时生效；普通空行回车（插入 `\n`）由调用方放行。
     */
    internal fun dedentBlankIndentLineAfterSoftEnter(block: BodyBlock.Text) {
        val idx = blocks.indexOfFirst { it.id == block.id }
        if (idx < 0) return
        if (block.indentLevel <= 1) return

        executeAndPush(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(textSpec(block)),
                /** 内容回到空行 + 缩进降一级（空块 ZWSP 与光标 (1,1) 由重建路径负责） */
                insertedSpecs = listOf(
                    BlockSpec.TextSpec(block.id, "", indentLevel = block.indentLevel - 1)
                ),
                focusBefore = currentFocusSpec(),
                /** 空块 raw 长度 1（ZWSP）：偏移 1 = ZWSP 之后，与空块光标约定一致 */
                focusAfter = FocusSpec(block.id, 1),
            )
        )
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

        /**
         * 缩进块（普通 / 复选框 / 组合态，v2026-09-08 App 布局级）：内容起点退格 =
         * **减一级缩进**（Word/Notion 标准行为，一级一级回退），不删字、不合并；
         * 减到一级后回落下方语义（复选框块退出复选框 / 普通块合并删除）。
         */
        if (block.indentLevel > 1) {
            dedentBlockAtContentStart(block)
            return
        }

        /**
         * 任务列表项（复选框）块首退格：**退出任务列表**（段落还原为普通段落，文字保留）
         * ——与空列表项退格退出列表同语义；再退格按普通块继续走下方合并 / 删除逻辑。
         *
         * v2026-09-15 改道：调**库** API `RichTextState.removeTaskList()`，不再做块级
         * `checked` 翻转与整块重建（段落类型在库内切换，块对象与块内 history 不动）。
         */
        if (block.state.isTaskList) {
            block.state.removeTaskList()
            onDocChanged?.invoke()
            return
        }

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
                else highlightForTwoStepDelete(prev.id)
            }
            /**
             * 前一块是分割线（v2026-09-07）：两步删除——第一次退格先高亮
             * （视觉确认目标，**不弹悬浮按钮**，见 [highlightForTwoStepDelete]），
             * 第二次退格删除（可撤销）。点选（带按钮）高亮后退格一次即删。
             */
            is BodyBlock.Divider -> {
                if (highlightedBlockId == prev.id) deleteDividerBlock(prev.id)
                else highlightForTwoStepDelete(prev.id)
            }
        }
    }

    /** 硬键盘 Delete（块尾）：若下一块是图片 / 分割线 → 两步删除 */
    fun onDeleteAtEnd(block: BodyBlock.Text) {
        val idx = blocks.indexOfFirst { it.id == block.id }
        if (idx < 0 || idx == blocks.lastIndex) return
        when (val next = blocks[idx + 1]) {
            is BodyBlock.Image -> {
                if (highlightedBlockId == next.id) deleteImageBlock(next.id)
                else highlightForTwoStepDelete(next.id)
            }
            is BodyBlock.Divider -> {
                if (highlightedBlockId == next.id) deleteDividerBlock(next.id)
                else highlightForTwoStepDelete(next.id)
            }
            else -> Unit
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
        /**
         * 后块 markdown 剥掉纯文本缩进前缀（v2026-09-07 整段缩进）：合并是 markdown 直接
         * 拼接（prevMd + curMd），后块的段首 EM 前缀拼到前块末尾后不再是"段首"，解码端
         * 不会剥除、会残留成可见宽空格——故合并时丢弃后块缩进（与 Word「合并到前段格式」
         * 一致）；前块缩进前缀保留（合并块继承前块缩进）。
         */
        val curMd = blockMarkdown(cur.state).dropLeadingPlainIndent()
        /**
         * 接缝光标（raw 偏移，作用于重建后的 prev 块）。
         * 此处用 effectiveText 剥掉旧 prev 块的前导 ZWSP 得到「有效字数」，恰好等于
         * 新 prev 块（[BlockSpec.TextSpec](prevMd + curMd)，经 setMarkdown 重建、不含 ZWSP）
         * 的 raw 长度——即前块内容末尾、后块内容起始的接缝位置。
         * 属有意换算，不要误改成对旧 prev 块 text 取 raw 坐标（旧块带 ZWSP 会使接缝整体 -1）。
         */
        val junction = effectiveText(prev.state.annotatedString.text).length
        executeAndPush(
            ReplaceBlocksCommand(
                index = prevIdx,
                removedSpecs = listOf(textSpec(prev), textSpec(cur)),
                /** 合并块继承前块的复选框属性（v2026-09-07）：前块普通 → 后块 checkbox
                 *  标记消失、内容并入（Notion 同款语义）；前块 checkbox → 合并块仍是 checkbox。
                 *  缩进档位（v2026-09-08）同样继承前块。 */
                insertedSpecs = listOf(
                    BlockSpec.TextSpec(
                        prev.id,
                        prevMd + curMd,
                        indentLevel = prev.indentLevel,
                    )
                ),
                focusBefore = FocusSpec(cur.id, 0),
                focusAfter = FocusSpec(prev.id, junction),
            )
        )
    }

    // ---------- 重排 / 焦点 ----------

    /**
     * 拖拽排序回调（**手指抬起落定**时到达这里，方案A坑点3：拖拽过程零压栈，
     * 一步拖拽恰好一条 [MoveBlockCommand] 撤销记录）。
     *
     * v2026-09-10 时序变更：底层换成本项目 fork 的
     * `com.corgimemo.app.ui.components.reorderable.BlocksReorderableColumn` 后，
     * 本回调由「落位滑行播完之后」提前到「**手指抬起的同一帧**」——
     * 原库 `settle()` 是先 `animateTo` 播 300~500ms 弹簧滑行再回调 onSettle，
     * 导致"图先滑到位、停顿、空行才弹出"。现在换位与补空行同步生效，
     * 落位滑行由 fork 内的 `BlocksGlideController` 在重排后接续。
     *
     * **前提**：本函数必须在 `onSettle` 里被调用（即提交必须发生在落位瞬间），
     * 才能保证"换位 + 空行"同帧；这也是 fork 存在的唯一理由。
     */
    /**
     * 拖拽落位：移动 + 「相邻图片补空行」打包成**一个撤销单位**（v2026-09-09）。
     *
     * 为什么必须补：把图片拖到另一张图旁边会形成 `[图,图]`，两图之间没有
     * 可以打字的位置，用户只能再拖一次才能插入文字。
     *
     * v2026-09-15 收敛：改走通用的 [executeAndPushWithImageSeparators]——"基于新顺序补块"
     * 与"相邻文本块合并"都由 [normalizeStructureInto] 统一负责，不必再自组装命令与手动压栈。
     */
    fun moveBlock(from: Int, to: Int) {
        if (from == to || from !in blocks.indices || to !in blocks.indices) return
        val blockId = blocks[from].id
        executeAndPushWithImageSeparators(
            MoveBlockCommand(blockId = blockId, fromIndex = from, toIndex = to)
        )
    }

    /**
     * 块获得焦点时回调（由块 Composable 的 onFocusChanged 触发）。
     *
     * 焦点落定 ⇒ 解除"迁移期隐藏光标"（[hideCursorUntilFocusBlockId]，无条件——
     * 无论焦点最终落在预期目标还是别的块，光标都该在**新位置**显示出来）；
     * 同时清除分割线/图片的选中高亮（用户回到文本编辑，选中态随之结束）。
     */
    fun onBlockFocused(blockId: String) {
        focusedBlockId = blockId
        if (hideCursorUntilFocusBlockId != null) hideCursorUntilFocusBlockId = null
        clearBlockSelection()
    }

    /** 块内容变化时回调（由块 Composable 的观察者触发） */
    fun notifyBlockChanged() {
        onDocChanged?.invoke()
    }

    /**
     * 块内容变化回调（v2026-09-10 扩展，块 Composable 的 snapshotFlow 观察者调用）：
     *
     * 1. **载体身份与内容绑定**：块里一旦有内容，它就不再是"图片载体空块"——
     *    就地清掉 [BodyBlock.Text.isImageSeparator]。否则用户往载体空行里打了字，
     *    换位时 [normalizeImageSeparators] 会把它当"漂移载体"删除，**用户输入随之消失**。
     * 2. 通知 ViewModel 同步（原 [notifyBlockChanged] 的职责）。
     *
     * 代价可控：只在"带标记"时才换块对象，一次翻转后不再触发（普通块零开销）。
     */
    fun onBlockContentChanged(blockId: String) {
        demoteImageSeparatorIfFilled(blockId)
        notifyBlockChanged()
    }

    /**
     * 载体降级原语：带标记的块只要不再是空白，就地换成"无标记"的新块对象
     * （**保持 state / focusRequester 引用不变**——不触发块内 observer 重启、
     * 不丢编辑历史、光标不动，与缩进档位落盘同款手法）。
     */
    private fun demoteImageSeparatorIfFilled(blockId: String) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(idx) as? BodyBlock.Text ?: return
        if (!block.isImageSeparator) return
        if (isEffectivelyEmpty(block.state)) return
        blocks[idx] = BodyBlock.Text(
            block.id,
            block.state,
            block.focusRequester,
            indentLevel = block.indentLevel,
            /** 身份与内容绑定：有内容 ⇒ 永久退出载体身份（v2026-09-10） */
            isImageSeparator = false,
        )
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

// ==================== 分割线绘制工具（编辑页/阅读态共用） ====================

/**
 * 虚线绘制（v2026-09-11 样式切换）：沿水平中线画 6dp 划 / 4dp 空的虚线段，
 * [strokeWidth] 为线厚。**编辑页与阅读态共用**（[DividerLine] /
 * InspirationViewCard），保证两处视觉一致。
 *
 * BlockNote 迁移（2026-09-17）后编辑页不再使用，仅保留供
 * [com.corgimemo.app.ui.screens.inspiration.components.InspirationViewCard]
 * 阅读态渲染虚线分割线。
 */
internal fun DrawScope.drawDashedDivider(size: Size, color: Color, strokeWidth: Float) {
    /** 虚线节奏：6dp 实段 + 4dp 空档（视觉密度与 Ellipsis 图标的点距近似） */
    val dash = 6.dp.toPx()
    val gap = 4.dp.toPx()
    val y = size.height / 2
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = strokeWidth,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap)),
    )
}

/**
 * 波浪线绘制（v2026-09-11 样式切换）：沿水平中线画振幅 2dp、波长 10dp 的
 * 二次贝塞尔波浪（上下半波交替、圆头收尾）。**编辑页与阅读态共用**——
 * 波形几何与描边宽度解耦，增厚只改 [strokeWidth]。
 *
 * BlockNote 迁移（2026-09-17）后编辑页不再使用，仅保留供
 * [com.corgimemo.app.ui.screens.inspiration.components.InspirationViewCard]
 * 阅读态渲染波浪分割线。
 */
internal fun DrawScope.drawWavyDivider(size: Size, color: Color, strokeWidth: Float) {
    val halfWave = 10.dp.toPx() / 2   /** 半波长（每段二次贝塞尔横跨的距离） */
    val amplitude = 2.dp.toPx()       /** 振幅（控制点偏离中线的距离） */
    val y = size.height / 2
    val path = Path()
    path.moveTo(0f, y)
    var x = 0f
    var up = true
    /** 逐半波推进：上拱/下拱交替，最后不足半波的残段收在中线上 */
    while (x < size.width) {
        val endX = (x + halfWave).coerceAtMost(size.width)
        val ctrlY = y + if (up) -amplitude else amplitude
        path.quadraticBezierTo(x + halfWave / 2, ctrlY, endX, y)
        x = endX
        up = !up
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}
