package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.corgimemo.app.ui.theme.LocalContentTypography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.ui.components.InlineImagePreview
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.paragraph.type.OrderedListStyleType
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
 * internal：复选框块缩进载体解析用它读档位（v2026-09-08，见 [checkboxMarkdownInfo]）。
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
        /**
         * 复选框块勾选状态（v2026-09-07 新增）：null = 普通文本块；
         * false / true = 复选框块（未勾选 / 已勾选）。
         *
         * 勾选状态**不进块内 [RichTextState]**（前缀只在块边界处理，见
         * [CHECKBOX_MD_UNCHECKED]），渲染时由 BlockTextItem 在编辑器左侧画
         * 复选框、勾选态文字视觉降级；序列化走 [BodyBlocksController.toMarkdown]
         * 拼前缀 / [BodyBlocksController.initialize] 剥前缀。
         * 就地翻转（保持 state 对象与焦点稳定）走 [BodyBlocksController.setCheckboxChecked]。
         */
        val checked: Boolean? = null,
        /**
         * 复选框块的缩进档位（v2026-09-08 新增，1 = 无缩进，上限 [MAX_LIST_LEVEL]）。
         *
         * **为什么不走库的段落缩进（TextIndent）**：真机实测库对纯文本段的
         * TextIndent 渲染量与理论公式不符（约为公式 2 倍），编辑器外（Row 内）
         * 的复选框图标无法可靠对齐。改为 **App 布局级缩进**：复选框图标与编辑器
         * 在 Row 内各自加同一个 `indentLevel` 对应的 start padding——两者被同一
         * 个偏移量推动，**同步是布局恒等的**，与库渲染行为完全解耦（这也正是
         * 列表项 marker+文本整体缩进的行为模式）。
         *
         * 持久化载体：markdown 的 `- [ ] ` 前缀之后、内容之前的全角空格
         * （U+2003，每级 [PLAIN_INDENT_STEP] 个），由 [checkboxMarkdownInfo] 解析、
         * [BodyBlocksController.toMarkdown] 拼接——**只在块边界处理、不进 state**。
         * 缩进操作经 [BodyBlocksController.setBlockIndent] 就地换块对象
         * （state/history/光标无损）。
         */
        val indentLevel: Int = 1,
    ) : BodyBlock()

    class Image(
        override val id: String,
        val path: String,
    ) : BodyBlock()

    /**
     * 分割线块（v2026-09-07 新增）：无内容的纯视觉块（一条水平细线）。
     *
     * - markdown 载体为独占段 `"---"`（CommonMark thematic break，见 [DIVIDER_MD]）；
     * - 无 RichTextState，不参与字数统计（[BodyBlocksController.plainText] 只聚合 Text 块）；
     * - 交互（v2026-09-08 第三版）：
     *   - **点击** → 高亮 + 光标安置到「分割线之后最近的 Text 块」块首
     *     （[BodyBlocksController.onDividerTapped]）：焦点**始终留在文本世界**，
     *     软键盘不收起；选中期间光标颜色置透明（视觉上"光标消失"）；
     *   - **删除**：光标既在块首 → 退格落到 [BodyBlocksController.onBackspaceAtStart]
     *     的"前一块是分割线"分支 → 已高亮即删除（软/硬键盘同一路径）；
     *     光标存在时的两步删除（第一次高亮、第二次删除）原样保留；
     *   - 删除可撤销（[ReplaceBlocksCommand]）；
     * - 可参与拖拽排序（[MoveBlockCommand] 按块 id 移动，对此类型透明）。
     */
    class Divider(
        override val id: String,
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
         * 复选框块勾选状态（v2026-09-07 新增）：null = 普通文本块；false/true = 复选框块。
         * [markdown] 载荷为**剥掉复选框前缀**的内容（前缀不进块内 RichTextState），
         * 重建时由 [BodyBlocksController.rebuildBlock] 原样传给 createTextBlock。
         */
        val checked: Boolean? = null,
        /**
         * 复选框块缩进档位（v2026-09-08 新增，1 = 无缩进）：与 [checked] 成对出现；
         * [markdown] 载荷不含缩进（EM 前缀在 [BodyBlocksController.toMarkdown] 拼接、
         * [BodyBlocksController.initialize] 解析），state 干净、不参与库的段落缩进。
         */
        val indentLevel: Int = 1,
    ) : BlockSpec()

    /** Image 块：只有 uri 路径 */
    data class ImageSpec(override val id: String, val path: String) : BlockSpec()

    /** Divider 块（v2026-09-07）：无载荷（id 即全部，重建时零参数） */
    data class DividerSpec(override val id: String) : BlockSpec()
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
 * 唯一例外是 [normalizeBlockParagraphs] / [mergeTextBlocks] 里对「旧块」算折行接缝时用了
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
 * 复选框勾选状态切换命令（v2026-09-07）：点击复选框标识勾选 / 取消勾选。
 *
 * **不做块重建**：勾选切换是纯视觉属性翻转（state 对象与光标保持不动），
 * 经 [BodyBlocksController.setCheckboxChecked] 就地替换块对象——重建会让
 * 块内 history 丢失、光标重置，交互上不可接受。
 * apply/revert 对称翻转，撤销一步还原勾选态。
 */
class SetCheckboxCheckedCommand(
    val blockId: String,
    val oldChecked: Boolean,
    val newChecked: Boolean,
) : BodyBlocksCommand {

    override fun apply(controller: BodyBlocksController) {
        controller.setCheckboxChecked(blockId, newChecked)
        controller.afterCommandMutation()
    }

    override fun revert(controller: BodyBlocksController) {
        controller.setCheckboxChecked(blockId, oldChecked)
        controller.afterCommandMutation()
    }
}

/**
 * 块缩进档位变更命令（v2026-09-08）：「增加 / 减少缩进」按钮对**所有非列表块**
 * （普通文本块 + 复选框块）生效。
 *
 * **App 布局级缩进**：非列表块的缩进不走库的段落 TextIndent（其渲染量与理论公式
 * 不符、且与 Row 外复选框图标无法对齐），而是记在 [BodyBlock.Text.indentLevel] 上，
 * 渲染由 App 侧 start padding 承载（编辑器 / 复选框+编辑器）。命令只翻转档位
 * （就地换块对象，state / history / 光标无损）；markdown 载体（段首 EM 前缀）由
 * toMarkdown 按 indentLevel 生成，随 onDocChanged 保存。
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
 * 只识别整段**恰好**为 `"---"` 的形态（App 自身生成的唯一形态，保守避免把用户
 * 手输的 `***` / `- - -` 等变体误判成分割线）；编辑页 [BodyBlocksController.initialize]
 * 与详情页 [com.corgimemo.app.ui.screens.inspiration.components.InspirationViewCard]
 * 共用本判定，保证两端往返一致。
 */
internal const val DIVIDER_MD = "---"

/** 判断单段 markdown（已按 `\n\n` 拆出）是否为分割线段 */
internal fun isDividerMarkdown(para: String): Boolean = para.trim() == DIVIDER_MD

// ==================== 复选框（v2026-09-07） ====================

/**
 * 复选框块的 markdown 载体：GFM 任务列表语法，独占一个段落（块间以 `\n\n` 连接）。
 *
 * - 未勾选：`- [ ] 内容`；已勾选：`- [x] 内容`（小写 x 为 App 自生成形态）；
 * - **前缀只在块边界处理、绝不进块内 [RichTextState]**：库不认识 task list，
 *   直接喂会把 `- [ ]` 解析成无序列表 + 字面 `[ ]` 文本；因此勾选状态是
 *   [BodyBlock.Text.checked] 属性，序列化时由 [BodyBlocksController.toMarkdown]
 *   拼前缀、加载时由 [BodyBlocksController.initialize] 剥前缀；
 * - 只识别段首**恰好**为上述两个前缀的形态（App 自身生成的唯一形态，保守
 *   避免把用户手输的 `- [X] ` / `-[ ]` 等变体误判；与 [DIVIDER_MD] 同哲学）；
 * - 编辑页 [BodyBlocksController.initialize] 与详情页
 *   [com.corgimemo.app.ui.screens.inspiration.components.InspirationViewCard]
 *   共用本判定，保证两端往返一致。
 */
internal const val CHECKBOX_MD_UNCHECKED = "- [ ] "
internal const val CHECKBOX_MD_CHECKED = "- [x] "

/**
 * 判断单段 markdown（已按 `\n\n` 拆出）是否为复选框段。
 *
 * 缩进载体：前缀之后的**前导全角空格**（U+2003，每级 [PLAIN_INDENT_STEP] 个）
 * 是复选框块的缩进档位（v2026-09-08，App 自管、不经库编码端）——解析后从内容里剥除，
 * 内容 markdown 保持干净（state / 详情页 RichText 都不应看到 EM 载体）。
 *
 * @return 命中时返回 (勾选状态, 缩进档位, 剥掉前缀与缩进载体后的内容 markdown)；
 *   未命中返回 null。内容为空串表示空复选框项（序列化输出即前缀本身，往返对称）。
 */
internal fun checkboxMarkdownInfo(para: String): Triple<Boolean, Int, String>? {
    val body = when {
        para.startsWith(CHECKBOX_MD_UNCHECKED) -> para.removePrefix(CHECKBOX_MD_UNCHECKED)
        para.startsWith(CHECKBOX_MD_CHECKED) -> para.removePrefix(CHECKBOX_MD_CHECKED)
        else -> return null
    }
    val indentLevel = body.countLeadingPlainIndentChars() / PLAIN_INDENT_STEP + 1
    return Triple(
        para.startsWith(CHECKBOX_MD_CHECKED),
        indentLevel,
        body.dropLeadingPlainIndent(),
    )
}

/** 复选框块的 markdown 前缀（按勾选状态）；[checked]=null 返回空串（普通块无前缀） */
internal fun checkboxMdPrefix(checked: Boolean?): String =
    if (checked == null) "" else if (checked) CHECKBOX_MD_CHECKED else CHECKBOX_MD_UNCHECKED

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

    /** 两步删除 / 点击选中的高亮块 id（图片块与分割线块，v2026-09-07 起含分割线） */
    var highlightedBlockId by mutableStateOf<String?>(null)
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
         * 复选框块勾选状态（v2026-09-07 新增）：null = 普通文本块；false/true = 复选框块。
         * 注意 [markdown] 参数必须是**已剥掉复选框前缀**的内容
         * （前缀由调用方 [BodyBlocksController.initialize] 处理，不进块内 state）。
         */
        checked: Boolean? = null,
        /**
         * 复选框块缩进档位（v2026-09-08 新增，1 = 无缩进）：App 布局级缩进，
         * 不调库 setParagraphIndent（TextIndent 渲染与复选框无法对齐）；
         * [markdown] 参数必须是**已剥掉缩进载体（EM 前缀）**的内容。
         */
        indentLevel: Int = 1,
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
             * 非空块 effective == raw，[normalizeBlockParagraphs] / [mergeTextBlocks] 里对
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
        /** 复选框块：checked / indentLevel 属性随块对象携带（markdown 已由调用方剥掉
         *  复选框前缀与缩进载体，v2026-09-07 / v2026-09-08） */
        return BodyBlock.Text(id, state, checked = checked, indentLevel = indentLevel).also { block ->
            /**
             * 组合态归位（v2026-09-08）：复选框块叠加列表段落（如历史数据
             * "- [ ] ␣␣- 内容" 的二级列表编码）时，库列表 TextIndent 会与 App
             * 布局级缩进双源叠加（缩进键动文本不动复选框）。加载时把库层级归 1
             * （marker 变一级形态，commitHistory=false），缩进量由 indentLevel
             * 全权承载——与 [BodyBlocksController.indentFocusedBlock] 的迁移对称。
             */
            if (checked != null && state.isList) {
                val listLevel = listLevelOfMd(state.toMarkdown())
                if (listLevel > 1) {
                    state.setListMarker(
                        level = 1,
                        number = orderedNumberOfMd(state.toMarkdown()) ?: 1,
                        commitHistory = false,
                    )
                }
            }
        }
    }

    /** Text 块 → [BlockSpec.TextSpec]（markdown 剥 ZWSP；checked / indentLevel 随 spec，Command 载荷统一出口） */
    private fun textSpec(block: BodyBlock.Text): BlockSpec.TextSpec =
        BlockSpec.TextSpec(
            block.id,
            blockMarkdown(block.state),
            checked = block.checked,
            indentLevel = block.indentLevel,
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
                            // 分割线段（"---"，v2026-09-07）：重建为 Divider 块（无富文本状态）
                            blocks += BodyBlock.Divider(newBodyBlockId())
                        } else if (checkboxMarkdownInfo(trimmed) != null) {
                            // 复选框段（v2026-09-07，"- [ ] 内容" / "- [x] 内容"）：
                            // 剥掉前缀与缩进载体（EM，v2026-09-08）后建 Text 块并携带
                            // 勾选状态与缩进档位（均不进块内 RichTextState）
                            val (wasChecked, wasIndentLevel, contentMd) = checkboxMarkdownInfo(trimmed)!!
                            blocks += createTextBlock(
                                contentMd,
                                checked = wasChecked,
                                indentLevel = wasIndentLevel,
                            )
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
        ensureTextBlock()
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
                    if (block.checked != null) {
                        /**
                         * 复选框块（v2026-09-07）：输出 GFM 任务列表前缀 + 缩进载体
                         * （EM×2×(indentLevel-1)，App 自管档位，v2026-09-08）+ 内容。
                         * 空 checkbox 块（内容为空串）输出前缀+载体本身——段落非空、
                         * 勾选/缩进属性随段落往返（加载侧 [checkboxMarkdownInfo] 还原）。
                         */
                        val body = if (raw.isEmpty()) {
                            ""
                        } else {
                            parseMarkdownSegments(raw)
                                .filterIsInstance<MdSegment.TextSeg>()
                                .map { it.md.trim('\n') }
                                .joinToString("\n\n")
                        }
                        checkboxMdPrefix(block.checked) + plainIndentPrefix(block.indentLevel) + body
                    } else if (block.indentLevel > 1) {
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
                /** 分割线块：输出独占段 `---`（thematic break），与 [initialize] 识别对称 */
                is BodyBlock.Divider -> DIVIDER_MD
            }
        }
        // 不再过滤空段：块间以空行连接，空白块对应一个非空占位段，保证往返对称
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
         */
        if (block.checked != null) {
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
            if (block.checked != null || !block.state.isList) {
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
            if (block.checked != null || !block.state.isList) {
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
     * 结构性读取）均为快照状态；勾选切换经 [setCheckboxChecked] 替换块对象（结构性
     * 写入）同样可追踪——聚焦块切换 / 勾选翻转都会触发读取方重组刷新。
     */
    val isFocusedBlockCheckbox: Boolean
        get() {
            val block = focusedBlockId
                ?.let { id -> blocks.firstOrNull { it.id == id } }
            return block is BodyBlock.Text && block.checked != null
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
        /** 拆出的前后半继承源块的复选框属性与缩进档位（v2026-09-08 修复遗漏） */
        val inheritedChecked = focused.checked
        val inheritedIndentLevel = focused.indentLevel
        if (beforeMd.isNotBlank()) {
            inserted += BlockSpec.TextSpec(
                newBodyBlockId(),
                beforeMd,
                checked = inheritedChecked,
                indentLevel = inheritedIndentLevel,
            )
        }
        val imgSpec = BlockSpec.ImageSpec(newBodyBlockId(), path)
        inserted += imgSpec
        if (afterMd.isNotBlank()) {
            inserted += BlockSpec.TextSpec(
                newBodyBlockId(),
                afterMd,
                checked = inheritedChecked,
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
        /** 拆出的前后半继承源块的复选框属性与缩进档位（v2026-09-08 修复：分割线
         *  拆块曾丢失 checked/indentLevel，导致上一行复选框消失） */
        val inheritedChecked = focused.checked
        val inheritedIndentLevel = focused.indentLevel
        if (beforeMd.isNotBlank()) {
            inserted += BlockSpec.TextSpec(
                newBodyBlockId(),
                beforeMd,
                checked = inheritedChecked,
                indentLevel = inheritedIndentLevel,
            )
        }
        inserted += dividerSpec
        if (afterMd.isNotBlank()) {
            inserted += BlockSpec.TextSpec(
                newBodyBlockId(),
                afterMd,
                checked = inheritedChecked,
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
     * 撤销即原位恢复分割线（revert 用 [ReplaceBlocksCommand.focusBefore] 回到删除前落点）。
     *
     * 副作用：新建的空块会按 ZWSP 不变量预置退格锚点，序列化时走 NBSP 占位段
     * （见 [EMPTY_BLOCK_PLACEHOLDER]），与既有空块行为一致。
     */
    fun deleteDividerBlock(blockId: String) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0 || blocks.getOrNull(idx) !is BodyBlock.Divider) return
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
                removedSpecs = listOf(BlockSpec.DividerSpec(blockId)),
                insertedSpecs = listOf(emptySpec),
                focusBefore = currentFocusSpec(),
                /** 空块 raw 长度 1（ZWSP），偏移 0 由 ZWSP 不变量维护推到 (1, 1) */
                focusAfter = FocusSpec(emptySpec.id, 0),
            )
        )
    }

    /**
     * 点击分割线（块 Composable 入口）：**切换选中态**，选中时把光标安置到
     * 「分割线之后最近的 Text 块」块首（[focusAfterDividerSelection]）。
     *
     * **设计要点（v2026-09-08 第三版，取代上一版的"焦点迁移到分割线"）**：
     * - 焦点**始终留在 Text 块** ⇒ 软键盘不收起（上一版夺焦点会让键盘消失，
     *   触屏用户反而按不到删除键）；
     * - 光标**视觉消失**由 `cursorColor = Transparent` 实现（[BlockTextItem] 按
     *   [selectedAnchorTextId] 切换），而非真的失焦；
     * - 光标被安置到分割线**之后**那行的行首 ⇒ 退格落在"块首"语义上，由
     *   [onBackspaceAtStart] 命中"前一块是已高亮的分割线"分支 → 一次退格即删
     *   （软键盘：空块走 ZWSP 差分检测；硬键盘：走 atContentStart 拦截）。
     *
     * 光标存在时的两步删除（[onBackspaceAtStart] / [onDeleteAtEnd]）保留不变。
     */
    fun onDividerTapped(blockId: String) {
        if (highlightedBlockId == blockId) {
            /** 再次点击同一条 = 取消选中：光标恢复可见，焦点/键盘保持不动 */
            clearBlockSelection()
            return
        }
        highlightedBlockId = blockId
        selectedAnchorTextId = focusAfterDividerSelection(blockId)
    }

    /**
     * 选中分割线后的光标安置：优先落到**分割线之后**最近的 Text 块块首（offset 0）；
     * 分割线是最后一块时退到**之前**最近的 Text 块块尾（此时软键盘退格会删字，
     * 属既有死区；硬键盘 Delete 走 [onDeleteAtEnd] 仍可删）。
     *
     * @return 被安置的 Text 块 id（作为 [selectedAnchorTextId]）；无 Text 块时 null
     */
    private fun focusAfterDividerSelection(dividerId: String): String? {
        val idx = blocks.indexOfFirst { it.id == dividerId }
        if (idx < 0) return null
        val next = blocks.drop(idx + 1).firstOrNull { it is BodyBlock.Text } as? BodyBlock.Text
        if (next != null) {
            focusSpec(FocusSpec(next.id, 0))
            return next.id
        }
        val prev = blocks.take(idx).lastOrNull { it is BodyBlock.Text } as? BodyBlock.Text
        if (prev != null) {
            focusSpec(FocusSpec(prev.id, prev.state.annotatedString.text.length))
            return prev.id
        }
        return null
    }

    // ---------- 复选框（v2026-09-07） ----------

    /**
     * 工具栏「复选框」按钮入口：聚焦块在 复选框块 ↔ 普通文本块 之间切换。
     *
     * - 聚焦 Text 块 → 整块转换（[buildToggleCheckboxCommand]，同 [ReplaceBlocksCommand]
     *   撤销时 stash 原块对象、内容与块内 history 无损）；
     * - 未聚焦 / 聚焦块是图片或分割线（不持有光标）→ 尾插一个空复选框项
     *   （[buildAppendCheckboxCommand]，焦点落到新项）。
     */
    fun toggleCheckboxAtFocused() {
        val focusedIdx = focusedBlockId
            ?.let { id -> blocks.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }

        if (focusedIdx == null) {
            executeAndPush(buildAppendCheckboxCommand())
            return
        }
        when (val focused = blocks[focusedIdx]) {
            is BodyBlock.Text -> executeAndPush(buildToggleCheckboxCommand(focused, focusedIdx))
            /** 图片/分割线块不持有光标：尾插空复选框项 */
            else -> executeAndPush(buildAppendCheckboxCommand())
        }
    }

    /**
     * 聚焦 Text 块的整体转换命令：普通块 → 复选框块（checked=false），
     * 复选框块 → 普通块（checked=null）。内容 markdown 不变，仅翻转换属性；
     * 焦点保持当前光标位置（重建块文本与原块完全一致，raw 偏移仍有效）。
     */
    private fun buildToggleCheckboxCommand(
        focused: BodyBlock.Text,
        focusedIdx: Int,
    ): ReplaceBlocksCommand {
        val newChecked = if (focused.checked == null) false else null
        return ReplaceBlocksCommand(
            index = focusedIdx,
            removedSpecs = listOf(textSpec(focused)),
            insertedSpecs = listOf(textSpec(focused).copy(checked = newChecked)),
            focusBefore = currentFocusSpec(),
            focusAfter = currentFocusSpec(),
        )
    }

    /**
     * 尾插空复选框项（未聚焦 / 聚焦块非 Text 时）：插在末尾 Text 块之前，
     * 焦点落到新项（可直接输入待办内容）。
     */
    private fun buildAppendCheckboxCommand(): ReplaceBlocksCommand {
        val checkboxSpec = BlockSpec.TextSpec(newBodyBlockId(), "", checked = false)
        /** 末尾是 Text → 插在它前面（保留「末尾可继续输入」不变量）；否则直接追加 */
        val index = if (blocks.lastOrNull() is BodyBlock.Text) blocks.size - 1 else blocks.size
        return ReplaceBlocksCommand(
            index = index.coerceAtLeast(0),
            removedSpecs = emptyList(),
            insertedSpecs = listOf(checkboxSpec),
            focusBefore = currentFocusSpec(),
            /** 空 checkbox 块 raw 长度 1（ZWSP）；偏移 0 由 ZWSP 不变量维护兜底推到 (1, 1) */
            focusAfter = FocusSpec(checkboxSpec.id, 0),
        )
    }

    /**
     * 点击复选框标识（块 Composable 入口）：勾选 / 取消勾选。
     * 走 [SetCheckboxCheckedCommand]（一步一撤销），markdown 随 onDocChanged 链路
     * 自动同步保存（`- [ ] ` ↔ `- [x] ` 前缀翻转）。
     */
    fun toggleCheckboxChecked(blockId: String) {
        val block = blocks.firstOrNull { it.id == blockId } as? BodyBlock.Text ?: return
        val current = block.checked ?: return
        executeAndPush(SetCheckboxCheckedCommand(blockId, current, !current))
    }

    /**
     * 勾选状态落盘（[SetCheckboxCheckedCommand] 用）：就地替换块对象，
     * **保持 state / focusRequester 引用不变**——不触发 observer 重启、不丢块内
     * 编辑历史、光标位置不动。
     */
    internal fun setCheckboxChecked(blockId: String, checked: Boolean) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(idx)
        if (block is BodyBlock.Text && block.checked != checked) {
            blocks[idx] = BodyBlock.Text(
                block.id,
                block.state,
                block.focusRequester,
                checked,
                block.indentLevel,
            )
        }
    }

    /**
     * 缩进档位落盘（[SetBlockIndentCommand] 用，v2026-09-08）：就地替换块对象，
     * **保持 state / focusRequester 引用不变**——不触发 observer 重启、不丢块内
     * 编辑历史、光标位置不动。markdown 载体随 onDocChanged 链路自动保存。
     */
    internal fun setBlockIndent(blockId: String, indentLevel: Int) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(idx)
        if (block is BodyBlock.Text && block.indentLevel != indentLevel) {
            blocks[idx] = BodyBlock.Text(
                block.id,
                block.state,
                block.focusRequester,
                block.checked,
                indentLevel,
            )
        }
    }

    /**
     * 复选框块内容起点退格（[onBackspaceAtStart] 的复选框分支实现）：
     * **退出复选框**（checked = null，文字保留）——与空列表项退格退出列表同语义；
     * 第二次退格按普通块继续走合并 / 删除。
     */
    private fun convertCheckboxBlockToText(block: BodyBlock.Text) {
        val idx = blocks.indexOfFirst { it.id == block.id }
        if (idx < 0) return
        executeAndPush(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(textSpec(block)),
                /** 退出复选框（v2026-09-08）：checked 置空 + 缩进档位归 1（转普通块） */
                insertedSpecs = listOf(textSpec(block).copy(checked = null, indentLevel = 1)),
                focusBefore = currentFocusSpec(),
                focusAfter = currentFocusSpec(),
            )
        )
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
     * 空列表项回车的统一分支（硬键盘 [splitTextBlock] / 软键盘 [normalizeBlockParagraphs]
     * 共用，v2026-09-07 起按层级区分行为，取代旧版"一刀切退出列表"）：
     *
     * - **一级**空列表项回车 = 退出列表 → 整块替换为普通空块（原行为不变）；
     * - **二级及以上**空列表项回车 = 降一级（dedent，Word/Notion 标准行为）→ 整块替换为
     *   上一层级**同类型**空列表项。有序编号无需手动续算：[executeAndPush] 内的
     *   [renumberOrderedBlocks] 会按位置语义自动重排——如 "1. 测试1" 下空的 "(1)" 回车
     *   后新块显示 "2."；无序则回到上一级 bullet 形态。
     *
     * 两条路径都必须**整体替换**而不能走拆两块流程：拆块会把 marker 前后切开，
     * afterMd 带出 marker-only 的 `"- "` markdown，setMarkdown 重建出**残留空列表项**，
     * 且此时 listForNewBlocks=null 跳过 refocusListBlock、光标落 0 在 bullet 之前——
     * 正是真机日志序列②「多一行空白 + 光标在 bullet 前」两个缺陷的共同根因。
     *
     * 撤销语义：[ReplaceBlocksCommand.revert] 经 stash 原样还原旧块（层级/类型无损），
     * 撤销后 renumberOrderedBlocks 幂等收敛编号。
     */
    private fun exitListOrDedentEmptyListItem(block: BodyBlock.Text, idx: Int) {
        /** 源块层级（listLevelOfMd 按每级 2 空格前缀解码；块序列化恒带层级前缀） */
        val srcLevel = listLevelOfMd(blockMarkdown(block.state))
        if (srcLevel <= 1) {
            /** 一级：退出列表 → 普通空块（ZWSP 退格锚点） */
            executeAndPush(
                ReplaceBlocksCommand(
                    index = idx,
                    removedSpecs = listOf(textSpec(block)),
                    insertedSpecs = listOf(BlockSpec.TextSpec(block.id, "")),
                    focusBefore = currentFocusSpec(),
                    /** 普通空块 raw 长度 1（ZWSP），偏移 1 = ZWSP 之后，与空块光标约定 (1,1) 一致 */
                    focusAfter = FocusSpec(block.id, 1),
                )
            )
            return
        }
        /** 二级及以上：降一级，重建为上一层级同类型空列表项（空 markdown → ZWSP 退格锚点） */
        val listType = if (block.state.isOrderedList) {
            InheritedListType.Ordered
        } else {
            InheritedListType.Unordered
        }
        executeAndPush(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(textSpec(block)),
                insertedSpecs = listOf(
                    BlockSpec.TextSpec(
                        block.id,
                        "",
                        initialListType = listType,
                        listLevel = srcLevel - 1,
                    )
                ),
                focusBefore = currentFocusSpec(),
                /** 降级块是列表块，精确落点随后由 [refocusListBlock] 修正到 marker 之后 */
                focusAfter = FocusSpec(block.id, 0),
            )
        )
        /** 光标修正到 marker 之后（内容起点），避免后续输入插到 marker 前面 */
        refocusListBlock(block.id)
    }

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

        /**
         * 空缩进行回车（普通 / 复选框 / 组合态，v2026-09-08 App 布局级）= **减一级
         * 缩进**（原地，不拆块，与内容起点退格对称的 Word 标准行为）：逐级返回，
         * 到一级后空复选框项回车才退出复选框（见下方分支）、无缩进空行回车才正常
         * 新起一行（走下方主路径）。硬键盘 text 无 \n；软键盘（text 已含 \n）由
         * [normalizeBlockParagraphs] 的同款前置分支处理。
         */
        if (isEffectivelyBlankLine(block.state) && block.indentLevel > 1) {
            dedentBlockAtContentStart(block)
            return
        }

        /**
         * 空复选框项回车（v2026-09-07）= **退出复选框** → 普通空块（与空列表项
         * 回车退出列表同语义）。口径用 [isEffectivelyBlankLine]（剥 ZWSP 外还兼容
         * 软键盘已插入的 `\n`），硬键盘（本函数）与软键盘（[normalizeBlockParagraphs]
         * 的同款前置分支）两路共用。
         */
        if (block.checked != null && isEffectivelyBlankLine(block.state)) {
            executeAndPush(
                ReplaceBlocksCommand(
                    index = idx,
                    removedSpecs = listOf(textSpec(block)),
                    insertedSpecs = listOf(BlockSpec.TextSpec(block.id, "")),
                    focusBefore = currentFocusSpec(),
                    /** 普通空块 raw 长度 1（ZWSP），偏移 1 = ZWSP 之后，与空块光标约定一致 */
                    focusAfter = FocusSpec(block.id, 1),
                )
            )
            return
        }

        /**
         * 空列表项回车（v2026-09-07 按层级区分）：一级 = 退出列表变普通空块；
         * 二级及以上 = 降一级为上一层级同类型空列表项（如空的 "(1)" 回车 → "2."）。
         * 统一走 [exitListOrDedentEmptyListItem]，不能走下面的拆两块流程——
         * 拆块会把 marker 前后切开，afterMd 带出 marker-only 的 `"- "` markdown，
         * setMarkdown 重建出**残留空列表项**，且此时 listForNewBlocks=null 跳过
         * refocusListBlock、光标落 0 在 bullet 之前（真机日志序列②缺陷根因）。
         */
        if (isEmptyListItem(block.state)) {
            exitListOrDedentEmptyListItem(block, idx)
            return
        }

        val text = block.state.annotatedString.text
        val beforeMd = if (beforeEnd > 0) block.state.toMarkdown(TextRange(0, beforeEnd)).replace(ZWSP, "") else ""

        val newId = newBodyBlockId()
        /** 列表续行：源块为列表类型（空列表项回车已在上方前置分支退出列表/降级，不会走到这里） */
        val srcListType: InheritedListType? = when {
            block.state.isUnorderedList -> InheritedListType.Unordered
            block.state.isOrderedList -> InheritedListType.Ordered
            else -> null
        }
        val listForNewBlocks = srcListType
        /** 有序列表续号：取源块字面编号（以 markdown 序列化结果为准，避免 marker 缺空格失配），
         *  新块起始编号 = 源块编号 +1。 */
        val srcOrderedNumber = currentOrderedNumber(block.state)
        /**
         * 源块列表层级（v2026-09-08 修复「无序列表回车掉级」）：**有序/无序通用**，
         * 显式随 spec 走、由 [createTextBlock] 的 setListMarker 还原。此前只传有序
         * （srcOrderedLevel），无序列表回车后前/后块都掉回一级——列表块的 markdown
         * 前缀在拆块路径上不可靠：range 版 toMarkdown 对「仅分隔符入范围」的段落会
         * 退化为无前缀的默认段落，且独立块前缀还会被 stripListLevelPrefix 剥掉
         * （≥4 空格防缩进代码块），层级只能靠 spec.listLevel 显式携带。
         */
        val srcListLevel = if (block.state.isList) listLevelOfMd(blockMarkdown(block.state)) else null

        /**
         * 拆块尾部 markdown 归一化（真机 logcat 证实的不递增根因）：
         * 列表块带尾随 ZWSP（refocusListBlock 把光标放在 marker 之后、ZWSP 之前，
         * 用户打字后 ZWSP 恒尾随、光标恒在 ZWSP 前），行尾回车时 afterStart 落在
         * ZWSP 之前 → `afterStart < text.length` 恒成立，range 版 toMarkdown 对
         * 「只剩 ZWSP 的尾部」产出 **marker-only** 的 `"2. "`/`"• "`。直接拿它建块：
         * setMarkdown 已带旧编号使 isList=true，**绕过 orderedStartNumber 续号分支**
         * （新行显示源块旧编号，如 "2.测试2" 回车得 "2.测试3"），且新块缺 ZWSP 退格锚点。
         * 处理：
         * ① marker-only / 空白尾部 → 视为「行尾回车」（afterMd=""），走标准续行路径
         *   （orderedStartNumber 续号 + 空列表项补尾随 ZWSP）。
         * ② 非 marker-only 尾部（行中间回车）→ 剥掉 range 编码拼接的列表 marker 前缀
         *   （该前缀携带的是**源块**编号），交由 initialListType/orderedStartNumber 统一重建，
         *   保证行中间拆分同样递增编号。
         */
        /**
         * 源块缩进档位（v2026-09-08，App 布局级，1 = 无缩进；列表块恒 1，层级走
         * listForNewBlocks 体系）。拆出的两块继承（原为读库 EM 载体，载体已改块属性）。
         */
        val srcIndentLevel = if (!block.state.isList) block.indentLevel else 1

        val rawAfterMd = if (afterStart < text.length)
            block.state.toMarkdown(TextRange(afterStart, text.length)).replace(ZWSP, "") else ""
        val afterMd = when {
            /**
             * 行尾回车：afterMd 置空（缩进档位走块属性 [indentLevel] 继承，不写
             * EM 载体进 state——v2026-09-08 起 EM 载体只在块边界拼/剥）。
             */
            rawAfterMd.isEmpty() -> ""
            isMarkerOnlyMarkdown(rawAfterMd) -> ""
            listForNewBlocks != null -> rawAfterMd.replaceFirst(ListMarkerPrefixRegex, "")
            else -> rawAfterMd
        }

        executeAndPush(
            ReplaceBlocksCommand(
                index = idx,
                removedSpecs = listOf(textSpec(block)),
                insertedSpecs = listOf(
                    BlockSpec.TextSpec(
                        block.id,
                        beforeMd,
                        listForNewBlocks,
                        /** 源块层级显式随 spec（有序/无序通用，回车续行继承层级，v2026-09-08） */
                        listLevel = srcListLevel,
                        /** 复选框块拆块（v2026-09-07）：前半块保留源勾选状态 */
                        checked = block.checked,
                        /** 缩进档位（v2026-09-08）：前半块保留源档位 */
                        indentLevel = block.indentLevel,
                    ),
                    BlockSpec.TextSpec(
                        newId,
                        afterMd,
                        listForNewBlocks,
                        orderedStartNumber = srcOrderedNumber?.plus(1),
                        /** 续行块继承源块层级（回车后下一行 = 上一行的层级，v2026-09-08） */
                        listLevel = srcListLevel,
                        /** 复选框续行（v2026-09-07，用户确认）：回车新行 = 未勾选的复选框项；
                         *  非复选框块保持 null */
                        checked = if (block.checked != null) false else null,
                        /** 缩进档位（v2026-09-08）：续行块继承源档位（列表项续行同缩进惯例） */
                        indentLevel = block.indentLevel,
                    ),
                ),
                focusBefore = currentFocusSpec(),
                focusAfter = FocusSpec(newId, 0),
            )
        )
        /** 修正新列表块光标：落在 marker 之后（默认偏移 0 会落在 bullet 之前）。
         *  仅在继续列表（listForNewBlocks != null）时调用；退出列表的普通空块无需修正。 */
        if (listForNewBlocks != null) refocusListBlock(newId)
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

        /**
         * 空列表项内出现换行（软键盘回车/粘贴）= 与 [splitTextBlock] 的空列表项分支同语义
         * （v2026-09-07 按层级区分：一级退出列表 / 二级及以上降一级），否则按行拆分时
         * marker-only 行会产出 `"- "` markdown 重建出残留 bullet 块。
         */
        if (isEmptyListItem(block.state)) {
            exitListOrDedentEmptyListItem(block, idx)
            return
        }

        /**
         * 空缩进行回车（软键盘，普通 / 复选框 / 组合态，v2026-09-08 App 布局级）=
         * 减一级缩进（与 [splitTextBlock] 的空缩进行分支同语义，逐级返回）。
         * 空白口径用 [isEffectivelyBlankLine]（含 \n）：软键盘回车已在文本中插入 \n。
         */
        if (isEffectivelyBlankLine(block.state) && block.indentLevel > 1) {
            dedentBlockAtContentStart(block)
            return
        }

        /**
         * 空复选框项回车（软键盘，v2026-09-07）= 退出复选框 → 普通空块。
         * 与 [splitTextBlock] 的空复选框分支同语义（口径用 [isEffectivelyBlankLine]：
         * 软键盘回车已在文本中插入 `\n`，isEffectivelyEmpty 判不出来）。
         */
        if (block.checked != null && isEffectivelyBlankLine(block.state)) {
            executeAndPush(
                ReplaceBlocksCommand(
                    index = idx,
                    removedSpecs = listOf(textSpec(block)),
                    insertedSpecs = listOf(BlockSpec.TextSpec(block.id, "")),
                    focusBefore = currentFocusSpec(),
                    focusAfter = FocusSpec(block.id, 1),
                )
            )
            return
        }

        /** 列表续行：软键盘回车/粘贴多行拆块时，新行继承源块列表类型
         *  （空列表项已在上方前置分支退出列表/降级，不会走到这里）。 */
        val srcListType: InheritedListType? = when {
            block.state.isUnorderedList -> InheritedListType.Unordered
            block.state.isOrderedList -> InheritedListType.Ordered
            else -> null
        }
        /**
         * 源块列表层级（v2026-09-08 修复「无序列表回车掉级」，与 [splitTextBlock] 同源）：
         * 行 md 前缀不可靠时（range 版 toMarkdown 对「仅分隔符入范围」的段落会退化为
         * 无前缀的默认段落）由 spec.listLevel 显式还原。
         */
        val srcListLevel = if (block.state.isList) listLevelOfMd(blockMarkdown(block.state)) else null
        /** 最后一个已确定层级的行（尾块继承「最后内容行」层级 = 行尾回车续行语义） */
        var lastSpecLevel: Int? = null
        /**
         * 复选框属性（v2026-09-07）：源块的勾选状态。行拆分时首块继承（含勾选态），
         * 续行块 = 未勾选的复选框项（与回车续行一致）；非复选框块保持 null。
         * 缩进档位（v2026-09-08）：各行继承源档位（与列表项续行同缩进惯例）。
         */
        val srcChecked = block.checked
        val srcIndentLevel = block.indentLevel
        val listForNewBlocks = if (srcListType != null && !isEmptyListItem(block.state)) {
            srcListType
        } else {
            null
        }

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
                    /**
                     * 行层级（v2026-09-08 修复无序列表回车掉级）：行 md 自带层级前缀
                     * （多行嵌套段，每行按自身 type.level 编码）时信前缀；行无前缀
                     * （range 版 toMarkdown 对「仅分隔符入范围」的段落会退化为无前缀的
                     * 默认段落）时继承源块层级——回车续行 = 上一行的层级。
                     * [lastSpecLevel] 供尾部空行块继承「最后内容行」层级。
                     */
                    val lineLevel = listLevelOfMd(md)
                    val specLevel = when {
                        !block.state.isList -> null
                        lineLevel > 1 -> lineLevel
                        else -> srcListLevel
                    }
                    if (specLevel != null) lastSpecLevel = specLevel
                    inserted += BlockSpec.TextSpec(
                        specId,
                        md,
                        listForNewBlocks,
                        /** 行层级：前缀优先、无前缀继承源块（见上方 specLevel 注释） */
                        listLevel = specLevel,
                        /** 复选框拆行（v2026-09-07）：首块继承源勾选态，续行块 = 未勾选复选框项 */
                        checked = when {
                            inserted.isEmpty() -> srcChecked
                            srcChecked != null -> false
                            else -> null
                        },
                        /** 缩进档位（v2026-09-08）：各行继承源档位 */
                        indentLevel = srcIndentLevel,
                    )
                    /**
                     * 光标落点：cursor 落在这一行 → 该块 + 行内有效偏移。
                     * （区间连续覆盖全文，cursor <= e 时必有 cursor >= s，substring 安全）
                     *
                     * 注意：focusBlockId 对应的块是 [BlockSpec.TextSpec](md) 经 setMarkdown 重建的
                     * 非空块——**不含前导 ZWSP**，故其 effective == raw。
                     * 此处用 effectiveText(substring).length 算出的「旧块该行有效字数」恰好等于
                     * 「新块（无 ZWSP）的 raw 偏移」，属有意换算，不要误改成对旧块 text 取 raw
                     * 坐标（旧块带 ZWSP 会导致偏移整体 -1）。
                     */
                    if (focusBlockId == null && cursor <= e) {
                        focusBlockId = specId
                        focusOffset = effectiveText(text.substring(s, cursor)).length
                    }
                    /** 行内空段（双回车产生的中间空行）丢弃：markdown 渲染中 \n\n 只是段落分隔，不是可见空行 */
                }
            }
        }
        /** 末尾有连续空行 = 回车在段尾 → 保留一个空块作为新段落
         *  （缩进档位走块属性继承 v2026-09-08，tailMd 不再写 EM 载体进 state） */
        val trailingBlanks = ranges.size - 1 - lastContent
        if (trailingBlanks > 0 || inserted.isEmpty()) {
            inserted += BlockSpec.TextSpec(
                newBodyBlockId(),
                "",
                listForNewBlocks,
                /** 尾块继承最后内容行的列表层级（行尾回车 = 续行，v2026-09-08；
                 *  此前漏传 → 无序列表回车后新行掉回一级） */
                listLevel = lastSpecLevel,
                /** 源块为复选框时（v2026-09-07）：尾部空行 = 未勾选的空复选框项（继续待办） */
                checked = if (srcChecked != null) false else null,
                /** 缩进档位（v2026-09-08）：尾部空行继承源档位 */
                indentLevel = srcIndentLevel,
            )
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
        /** 修正新列表块光标：若焦点落在新列表块（继续列表）上，移到 marker 之后；
         *  普通块（isList=false）refocusListBlock 内部直接跳过，无副作用。 */
        refocusListBlock(focusBlockId)
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
            /** 结构变更（拆块/合并/退列表/插图等）后按位置语义重排连续有序块编号，
             *  在 replaying 门控内执行，避免被重写块的 observer 误清 redo 栈。 */
            renumberOrderedBlocks()
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
            checked = spec.checked,
            indentLevel = spec.indentLevel,
        )
        is BlockSpec.ImageSpec -> BodyBlock.Image(spec.id, spec.path)
        is BlockSpec.DividerSpec -> BodyBlock.Divider(spec.id)
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

    /** Command 落盘后的通用收尾：两步删除的高亮态随结构变化清除（含选中态锚点） */
    internal fun afterCommandMutation() {
        clearBlockSelection()
    }

    // ---------- 非文本块「点选」态（v2026-09-08 第三版） ----------

    /**
     * 「点选非文本块」态下，光标被安置到的那个 Text 块 id（null = 不在该态）。
     *
     * **为什么需要它**：点击分割线要让"光标消失 + 键盘不消失 + 按键能删分割线"，
     * 而 Android 软键盘（IME）只跟随**聚焦的编辑框**——焦点离开 TextField 键盘必然收起，
     * 且软键盘退格走 `InputConnection.deleteSurroundingText`（Compose 的
     * `onPreInterceptKeyBeforeSoftKeyboard` 按官方注释只拦硬件键盘转交给 IME 的事件，
     * 拦不住屏幕键盘自身的删除）。故唯一可行解是：**焦点留在 Text 块**，用
     * `cursorColor = Transparent` 让光标视觉消失，并把光标安置到"分割线之后最近 Text
     * 块的块首"——那里退格无字可删，会被引擎的块首退格检测捕获（空块走 ZWSP 差分、
     * 硬键盘走 atContentStart），进而命中 [onBackspaceAtStart] 的分割线分支。
     *
     * 该字段同时是 UI 隐藏光标的开关（快照状态，变化触发重组）与焦点归属的判据：
     * 焦点回到本块 = 选中流程自身的落焦（不清高亮）；落到其它块 = 用户去编辑别的块
     * （退出选中态）。
     */
    internal var selectedAnchorTextId by mutableStateOf<String?>(null)
        private set

    /**
     * 退出「点选非文本块」态：清高亮 + 清锚点 → 光标恢复可见（焦点/键盘不动）。
     * 再次点击同一分割线、点击其它 Text 块、开始输入字符、执行任何命令都会走到这里。
     */
    fun clearBlockSelection() {
        if (highlightedBlockId == null && selectedAnchorTextId == null) return
        highlightedBlockId = null
        selectedAnchorTextId = null
    }

    /**
     * 「焦点迁移期间保持光标隐藏」的目标块 id（v2026-09-08 光标跳变修复）。
     *
     * **问题**：删除分割线后焦点要从"下一行"迁到"新空行"，但焦点迁移是**异步**的
     * （[focusSpec] 只写 [pendingFocus]，真正的 `requestFocus` 由块 Composable 的
     * [LaunchedEffect] 在下一帧执行）；而 [afterCommandMutation] 是**同步**的——
     * 它在命令 apply 的末尾就把选中态清掉、让 `cursorColor` 恢复不透明。于是中间
     * 那一帧焦点仍在旧块上、光标却已可见 ⇒ 用户看到"光标在下一行行首亮一下，
     * 再跳到分割线行行首"的跳变。
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
     * 点选非文本块期间（[selectedAnchorTextId]）或焦点迁移期间
     * （[hideCursorUntilFocusBlockId]，且目标块仍在列表中）。
     */
    val isCursorVisuallyHidden: Boolean
        get() = selectedAnchorTextId != null ||
            (hideCursorUntilFocusBlockId?.let { id -> blocks.any { it.id == id } } == true)

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
     * 缩进块内容起点退格：减一级缩进（[onBackspaceAtStart] 的缩进分支实现，
     * v2026-09-08 改 App 布局级）。
     *
     * 走 [SetBlockIndentCommand] 就地换块对象（indentLevel -1，state / history /
     * 光标无损，一步撤销）——不再经 ReplaceBlocksCommand 重建（旧版 EM+ZWSP
     * 重建方案随库 TextIndent 缩进一并废弃）。
     */
    private fun dedentBlockAtContentStart(block: BodyBlock.Text) {
        if (block.indentLevel <= 1) return
        executeAndPush(
            SetBlockIndentCommand(block.id, block.indentLevel, block.indentLevel - 1)
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
         * 复选框块（已到一级缩进）块首退格：**退出复选框**（checked = null，文字
         * 保留，缩进同步归位）——与空列表项退格退出列表同语义；再退格按普通块
         * 继续走下方合并 / 删除逻辑。
         */
        if (block.checked != null) {
            convertCheckboxBlockToText(block)
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
                else highlightedBlockId = prev.id
            }
            /**
             * 前一块是分割线（v2026-09-07）：两步删除——第一次退格先高亮
             * （视觉确认目标），第二次退格删除（可撤销）。点击选中已高亮后
             * 退格一次即删（与图片块交互语义一致）。
             */
            is BodyBlock.Divider -> {
                if (highlightedBlockId == prev.id) deleteDividerBlock(prev.id)
                else highlightedBlockId = prev.id
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
                else highlightedBlockId = next.id
            }
            is BodyBlock.Divider -> {
                if (highlightedBlockId == next.id) deleteDividerBlock(next.id)
                else highlightedBlockId = next.id
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
                        checked = prev.checked,
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
     * 拖拽排序回调（ReorderableColumn 的 onSettle——**手指抬起落定后才到达这里**，
     * 方案A坑点3：拖拽过程零压栈，一步拖拽恰好一条 [MoveBlockCommand] 撤销记录）。
     */
    fun moveBlock(from: Int, to: Int) {
        if (from == to || from !in blocks.indices || to !in blocks.indices) return
        val blockId = blocks[from].id
        executeAndPush(MoveBlockCommand(blockId = blockId, fromIndex = from, toIndex = to))
    }

    /**
     * 块获得焦点时回调（由块 Composable 的 onFocusChanged 触发）。
     *
     * **选中态特判（v2026-09-08 第三版）**：点选分割线后本回调会被"安置落焦"触发
     * ——那是选中流程自己的落焦，不能当作"用户去编辑别的块"而清掉高亮（否则高亮
     * 刚点亮就熄灭）。判据是 [selectedAnchorTextId]：焦点回到安置块 ⇒ 保留；
     * 落到**其它**块 ⇒ 用户开始编辑别处，退出选中态（高亮清、光标恢复可见）。
     */
    fun onBlockFocused(blockId: String) {
        focusedBlockId = blockId
        /**
         * 焦点迁移落定 ⇒ 解除"迁移期隐藏光标"（[hideCursorUntilFocusBlockId]）：
         * 无条件解除——无论焦点最终落在预期目标还是别的块，光标都该在**新位置**
         * 显示出来，绝不该留在旧位置闪现。
         */
        if (hideCursorUntilFocusBlockId != null) hideCursorUntilFocusBlockId = null
        val anchor = selectedAnchorTextId
        if (anchor != null) {
            if (blockId != anchor) clearBlockSelection()
            return
        }
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
            is BodyBlock.Divider -> BlockDividerItem(
                controller = controller,
                block = block,
                isDragging = isDragging,
                isLocked = isLocked,
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

    /**
     * 复选框图标的**布局级缩进偏移**（v2026-09-08，仅复选框块非零）：
     * `(indentLevel - 1) × LIST_LEVEL_INDENT_SP`。
     *
     * 缩进时复选框图标与文本必须同步位移——但库对纯文本段的 TextIndent 渲染量
     * 与理论公式不符（真机实测约 2 倍），Row 外图标的任何"估算/测量对齐"都不可靠
     * （公式、getLineLeft、getHorizontalPosition 三轮均失败）。最终方案：**缩进
     * 不进库排版**（记在 [BodyBlock.Text.indentLevel]，markdown 载体由 App 自管），
     * 渲染时把偏移量加到**复选框图标的 start padding** 上——复选框右移会经 Row
     * 布局**自然推动**其后的编辑器与文本同距右移，间距恒定；这正是列表项
     * 「marker + 文本整体缩进」的行为模式。
     *
     * ⚠️ **切勿再给编辑器额外加同一 padding**（v2026-09-08 踩坑）：编辑器排在
     * 复选框之后，复选框的 padding 已经推了它；再叠一次文本就位移 2 倍、间距
     * 增大 P（真机日志证实：render padding=90dp 正确，但文本移了 180dp）。
     * sp→dp 在组合期转换（LocalDensity）。
     */
    val checkboxIndentPadding = if (block.checked != null) {
        with(LocalDensity.current) {
            ((block.indentLevel - 1) * LIST_LEVEL_INDENT_SP).sp.toDp()
        }
    } else {
        0.dp
    }

    /**
     * **普通文本块**（checked == null）的布局级缩进偏移（v2026-09-08）：公式同上，
     * 加在编辑器的 start padding 上——普通块没有复选框占位，缩进全靠编辑器自身
     * padding（文本整体右移，每级 [LIST_LEVEL_INDENT_SP]）。列表块恒 0
     * （缩进走库 setListMarker 的 TextIndent 体系）。
     */
    val plainBlockIndentPadding = if (block.checked == null && !block.state.isList) {
        with(LocalDensity.current) {
            ((block.indentLevel - 1) * LIST_LEVEL_INDENT_SP).sp.toDp()
        }
    } else {
        0.dp
    }

    /** 组合期捕获 density（对齐跟随的 onTextLayout 回调内不可读 CompositionLocal） */
    val density = LocalDensity.current

    /**
     * 复选框跟随**对齐**的偏移（v2026-09-08）：对齐按钮改变段落 textAlign 后，
     * 文本行的实际左缘随之变化（Start = 0 / Center = 居中偏移 / End = 靠右偏移），
     * 由 [RichTextEditor.onTextLayout] 实测 `getLineLeft(0)` 回写，复选框用
     * `offset`（绘制偏移，不影响布局）同步跟随；toggle 取消对齐后自动归 0。
     *
     * 说明：textAlign 的行盒测量与 TextIndent **机制不同**——前者由排版的对齐
     * 处理、行盒随对齐整体移动（getLineLeft 反映真实偏移）；后者只移字形起点
     * （getLineLeft 恒 0，缩进场景不可用、已由布局级缩进替代）。
     * 初值 0dp（默认左对齐），onTextLayout 首次布局后即刷新。
     */
    var alignmentOffset by remember(block.state) { mutableStateOf(0.dp) }

    /** 软键盘控制器：用于"焦点已在目标块"时显式唤起键盘（见下方 LaunchedEffect） */
    val keyboardController = LocalSoftwareKeyboardController.current

    /** 聚焦到本块（拆分 / 合并 / 插图 / 撤销后由 controller.pendingFocus 驱动） */
    LaunchedEffect(controller.pendingFocus) {
        val pf = controller.pendingFocus ?: return@LaunchedEffect
        /** 非命中块直接跳过，不可取走——否则会误清空其它块的待落焦请求 */
        if (pf.blockId != block.id) return@LaunchedEffect
        /** 取走即清空：命中块只消费一次后置空，避免同帧其它块 effect 读到过期/重复值 */
        controller.takePendingFocus()
        block.state.selection = TextRange(pf.offset)
        block.focusRequester.requestFocus()
        /**
         * 点选分割线落焦时**显式唤起软键盘**（v2026-09-08 修复"收键盘后点分割线不出键盘"）。
         *
         * 原因：手动收起键盘（返回键 / 收起键）**只隐藏 IME，不会让 TextField 失焦**——
         * 焦点仍在本块上，于是 `requestFocus()` 是空操作、不产生"焦点变化事件"；
         * 而系统键盘是**由 BasicTextField 的焦点事件自动显示/隐藏**的
         * （`SoftwareKeyboardController.show()` 官方文档：手动 hide 之后不会再自动显示），
         * 所以键盘不会再弹出来，用户也就按不到删除键。
         *
         * 解法：命中"点选态的安置块"时在 requestFocus 之后显式 `show()`。
         * 先等一帧（`withFrameNanos`）确保焦点事务已生效——`show()` 在文本框未聚焦时
         * 会被系统静默忽略（文档原文：never show if there is no composable that will
         * accept text input）。键盘本来就在时 show() 是空操作，无副作用。
         */
        if (controller.selectedAnchorTextId == block.id) {
            withFrameNanos { }
            keyboardController?.show()
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
    /**
     * key 用 [block.state]（对象身份）而非 block.id：Command 重建（拆块前半 / 合并 / 退块等）
     * 会**复用 id 换新 RichTextState**，以 id 为 key 时 effect 不重启、闭包仍持旧 state——
     * TextField 渲染新 state 而 observer 监听旧 state，重建块上的退格合并 / 换行拆块检测
     * 全部失灵（真机症状：退出列表后的空行退格无反应、光标留在空行）。
     * 以 state 为 key，重建即重启并按新 state 重新锚定 lastText / lastMarkdown 基线；
     * 正常打字期间 state 对象不变，重启不发生，行为与按 id 一致。
     */
    LaunchedEffect(block.state) {
        var lastText = state.annotatedString.text
        var lastMarkdown = state.toMarkdown()
        var lastSelection = state.selection
        /** 本块列表成员基线：删除 marker 字符（库自动退列表）/ 工具栏加列表 / 打字自动识别
         *  "N. " 都会翻转 isOrderedList → 触发跨块有序重编号（renumberOrderedBlocks） */
        var lastIsOl = state.isOrderedList
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
                    /**
                     * 开始输入 ⇒ 退出「点选非文本块」态（v2026-09-08 第三版）：
                     * 选中分割线期间光标是透明的，用户一旦打字就该恢复光标、取消选中。
                     * 判据用**文本变长**（输入 / 上屏），退格变短不触发——否则会先清掉
                     * 高亮再进 [BodyBlocksController.onBackspaceAtStart]，两步删除退化成
                     * "永远只能高亮、删不掉"。IME 组合中间态跳过，等上屏那轮再清。
                     */
                    if (text.length > lastText.length && composition == null) {
                        controller.clearBlockSelection()
                    }
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
                    /** 列表成员变化（删 marker 退列表 / 工具栏切换 / 打字自动识别）：
                     *  跨块有序重编号。composition != null 时跳过（IME 组合中间态不稳定，
                     *  等组合结束后的下一轮 collect 再处理）。 */
                    val isOl = state.isOrderedList
                    if (isOl != lastIsOl && composition == null) {
                        controller.renumberOrderedBlocks()
                    }
                    lastIsOl = isOl
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

    /** 内容变化 → 通知 controller 同步 ViewModel（key 同 observer：用 state 对象身份，防重建后失联） */
    LaunchedEffect(block.state) {
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

        /**
         * 复选框标识（v2026-09-07）：checked != null（复选框块）时渲染在编辑器左侧。
         * 点击切换勾选（[BodyBlocksController.toggleCheckboxChecked]，一步一撤销，
         * markdown 前缀 `- [ ] ` ↔ `- [x] ` 随 onDocChanged 链路自动保存）；
         * 锁定态不可点击；start padding = 基准 2dp + 布局级缩进偏移
         * （[checkboxIndentPadding]），与编辑器同偏移推动 → 同步位移、间距恒定；
         * top padding 让 18dp 框体与第一行文字中线对齐。
         */
        /**
         * 复选框标识（v2026-09-07）：checked != null（复选框块）时渲染在编辑器左侧。
         * 点击切换勾选（[BodyBlocksController.toggleCheckboxChecked]，一步一撤销，
         * markdown 前缀 `- [ ] ` ↔ `- [x] ` 随 onDocChanged 链路自动保存）；
         * 锁定态不可点击。
         *
         * **左缘对齐（v2026-09-08）**：start padding = 16dp（= 编辑器 contentPadding，
         * 即普通段落文本左缘）+ 布局级缩进偏移 [checkboxIndentPadding]——复选框标识
         * 左缘与普通段落文本左缘同列（缩进档位相同时精确对齐）；top padding 让 18dp
         * 框体与第一行文字中线对齐。复选框右移同时经 Row 布局自然推动编辑器。
         */
        if (block.checked != null) {
            CheckboxBoxIcon(
                checked = block.checked,
                onClick = if (isLocked) null else ({ controller.toggleCheckboxChecked(block.id) }),
                modifier = Modifier
                    .offset(x = alignmentOffset)
                    .padding(start = 16.dp + checkboxIndentPadding, top = 2.dp),
            )
        }

        RichTextEditor(
            state = state,
            modifier = Modifier
                .weight(1f)
                .padding(start = plainBlockIndentPadding)
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
                            /**
                             * 内容起点退格（v2026-09-07 扩展，原条件 sel.start == 0）：
                             * 光标之前无有效文本（raw 0，或前导 ZWSP 之后的 raw 1）即视为
                             * 块首退格——缩进块逐级减缩进、无缩进块走合并/删除语义。
                             * 拦截发生在删除之前（return true 吞掉事件），文本无损。
                             */
                            val atContentStart = sel.collapsed &&
                                sel.start <= state.annotatedString.text.length &&
                                effectiveText(state.annotatedString.text.substring(0, sel.start)).isEmpty()
                            if (atContentStart) {
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
                        style = LocalContentTypography.current.bodyLarge.copy(
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
            textStyle = LocalContentTypography.current.bodyLarge.copy(
                /** 勾选态文字视觉降级（v2026-09-07，与确认截图图三一致）：40% 透明度 */
                color = if (block.checked == true) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
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
            /**
             * 光标颜色：选中非文本块（分割线）期间置**透明**（v2026-09-08 第三版）。
             *
             * 目的：满足"点选分割线后光标消失"的预期，同时**不夺焦点**——焦点一离开
             * TextField，软键盘立刻收起，触屏用户就没法按删除键删分割线了。这里用
             * "聚焦但光标透明"两全：TextField 仍持有输入连接（键盘不收），只是看不见
             * 光标；按退格由 [BodyBlocksController.onBackspaceAtStart] 接管删除分割线。
             * 取消选中 / 编辑其它块 / 开始输入都会清掉选中态，光标随即恢复。
             *
             * 判据用 [BodyBlocksController.isCursorVisuallyHidden]：除"点选态"外还覆盖
             * **焦点迁移期**（删除分割线后焦点从下一行迁到新空行的跨帧间隙），
             * 避免光标先在旧位置闪一下再跳走（v2026-09-08 跳变修复）。
             */
            colors = RichTextEditorDefaults.richTextEditorColors(
                containerColor = Color.Transparent,
                cursorColor = if (controller.isCursorVisuallyHidden) {
                    Color.Transparent
                } else {
                    Color(0xFFFF9A5C)
                },
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            onTextLayout = { textLayoutResult ->
                /**
                 * 复选框跟随对齐（v2026-09-08）：实测文本**首行左缘**并回写。
                 * textAlign（Start/Center/End）由排版的对齐处理、行盒随对齐整体
                 * 移动，`getLineLeft(0)` 反映真实偏移（与 TextIndent 只移字形起点的
                 * 机制不同，后者 getLineLeft 恒 0）。对齐变化触发重排 → 回调刷新 →
                 * 复选框同步。非复选框块跳过。density 已在组合期捕获。
                 */
                if (block.checked != null) {
                    alignmentOffset = with(density) { textLayoutResult.getLineLeft(0).toDp() }
                }
            },
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

// ==================== Divider 块（v2026-09-07） ====================

/** 分割线块高亮（选中态）颜色：主题 primary 暖橙，与工具栏激活态一致 */
private val DividerHighlightColor = Color(0xFFFF9A5C)

/**
 * 分割线块：拖拽手柄 + 一条水平细线（可点击切换高亮）。
 *
 * - **常态**：1dp 细线，`onSurfaceVariant` 35% 透明度（与拖拽手柄同灰调）；
 * - **高亮态**（点击选中 / 退格两步删除的第一步）：2dp 主题暖橙线，参照已确认交互稿；
 * - **点击**：整行热区（固定 25dp 高的线体容器）切换高亮（[BodyBlocksController.onDividerTapped]），
 *   去水波纹（`indication = null` 必须显式传 `interactionSource`，否则不生效）；
 * - **焦点不迁移（v2026-09-08 第三版）**：本块**不**申请焦点——焦点一离开 TextField
 *   软键盘就收起，触屏用户反而按不到删除键。改为：点击后由控制器把光标安置到
 *   「分割线之后最近 Text 块的块首」（[BodyBlocksController.onDividerTapped]），
 *   焦点始终在文本世界 ⇒ 键盘不收；光标**视觉消失**由该 Text 块的 cursorColor
 *   置透明实现（[BlockTextItem] 读 [BodyBlocksController.selectedAnchorTextId]）；
 * - **删除**：光标既在块首 ⇒ 退格命中 [BodyBlocksController.onBackspaceAtStart] 的
 *   "前一块是已高亮分割线"分支，一次退格即删；两步删除（第一次高亮、第二次删除）
 *   保留；两者都走 [BodyBlocksController.deleteDividerBlock]（行变空行、可撤销）；
 * - 锁定态（isLocked）不可点击；拖拽时 60% 透明度（与 Text/Image 块一致）；
 * - **零位移约束**：高亮增厚（1dp→2dp）时容器高度恒定、线居中扩展——块总高不变，
 *   不会推挤下方内容（v2026-09-07 用户反馈修复）。
 */
@Composable
private fun BlockDividerItem(
    controller: BodyBlocksController,
    block: BodyBlock.Divider,
    isDragging: Boolean,
    isLocked: Boolean,
    dragHandleModifier: Modifier,
) {
    /** 是否处于高亮（选中）态：点击切换 / 退格第一步点亮，随 controller 状态响应式刷新 */
    val highlighted = controller.highlightedBlockId == block.id
    Row(verticalAlignment = Alignment.CenterVertically) {
        BlockDragHandle(dragHandleModifier)

        /**
         * 线体容器：clickable 在 padding 之前声明，让「线上下 12dp」整体作为点击热区
         * （细线本体 1dp 无法指头点中）；graphicsLayer 只影响绘制不影响点击，
         * 拖拽置灰照常生效。
         *
         * **高度恒定（v2026-09-07 位移修复）**：容器固定 25dp（= 常态 1dp 线 + 上下
         * 12dp padding 的总高）。高亮时线厚 1dp→2dp 若不锁高，整行会变高 1dp、把下方
         * 内容推下去（可见位移）；固定后线在容器内**居中增厚**（中心不动、上下各多
         * 0.5dp 仍留在容器内），块总高恒定 → 零位移。
         */
        Box(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    if (isDragging) {
                        alpha = 0.6f
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isLocked,
                ) { controller.onDividerTapped(block.id) }
                .height(25.dp),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) {
                    DividerHighlightColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
            )
        }
    }
}
