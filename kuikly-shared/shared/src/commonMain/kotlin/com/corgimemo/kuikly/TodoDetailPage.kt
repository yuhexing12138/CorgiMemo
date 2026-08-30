package com.corgimemo.kuikly

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.*
import com.corgimemo.kuikly.base.BasePager

/**
 * 待办详情页（阶段三·完善）
 *
 * 通过 pageData.params 接收主工程传入的待办字段，支持四类写操作，均经 [CorgiBridgeModule]
 * 回写主工程 Room：
 * - 编辑标题 / 内容 → [CorgiBridgeModule.update]
 * - 标记完成 / 取消完成 → [CorgiBridgeModule.setStatus]
 * - 切换置顶 → [CorgiBridgeModule.togglePin]
 * - 删除 → [CorgiBridgeModule.delete]，随后 [CorgiBridgeModule.closePage] 关闭承载页
 *
 * 状态设计（关键点）：
 * - 状态类字段（完成状态 / 置顶 / 提示）用公开顶层 observable 委托声明，
 *   读取处自动订阅依赖，赋值后仅刷新受影响的局部节点。
 * - 采用**哨兵值 + 惰性读取**（而非在 body 中播种）：
 *   `curStatus = -1` / `curPinned = null` 表示"尚未变更"，读取时回退到入参。
 *   这样彻底避免在组合过程中写 observable（组合期写状态可能触发额外的
 *   notifyValueChange，依赖 PagerManager 当前观察者，时机不当会有风险）。
 * - 正在编辑的文本用**普通可空字段**保存（不用 observable）：若参与响应式，
 *   每次按键都会触发重组并把 text 回写给输入框，导致光标跳动与输入错乱。
 *
 * 入参（pageData.params，均为可选，缺省有兜底）：
 * - todoId: Long / title: String / content: String / status: Int（1=完成）/ isPinned: Boolean
 */
@Page("todoDetail", supportInLocal = true)
internal class TodoDetailPage : BasePager() {

    // ==================== 编辑中的文本（普通字段，避免输入时重组） ====================

    /** 编辑中的标题，由 Input 的 textDidChange 回写；null 表示尚未编辑 */
    private var editTitle: String? = null

    /** 编辑中的内容，由 TextArea 的 textDidChange 回写；null 表示尚未编辑 */
    private var editContent: String? = null

    // ==================== 响应式状态（哨兵值表示"未变更"） ====================

    /** 当前完成状态：1 = 已完成，0 = 未完成，-1 = 尚未变更（回退入参） */
    @Suppress("DEPRECATION")
    private var curStatus: Int by observable(-1)

    /** 当前是否置顶；null = 尚未变更（回退入参） */
    @Suppress("DEPRECATION")
    private var curPinned: Boolean? by observable(null)

    /** 操作结果提示（空串时不显示） */
    @Suppress("DEPRECATION")
    private var hint: String by observable("")

    // ==================== 惰性取值（读 observable 即完成依赖订阅） ====================

    /** 待办 ID（为 0 时原生侧会因查不到记录而忽略写操作） */
    private fun todoIdValue(): Long = pageData.params.optLong("todoId", 0L)

    /** 标题展示值：未编辑时用入参，编辑后用用户输入 */
    private fun titleValue(): String = editTitle ?: pageData.params.optString("title", "")

    /** 内容展示值：未编辑时用入参，编辑后用用户输入 */
    private fun contentValue(): String = editContent ?: pageData.params.optString("content", "")

    /** 完成状态：未变更时回退入参 */
    private fun statusValue(): Int =
        if (curStatus < 0) pageData.params.optInt("status", 0) else curStatus

    /** 置顶状态：未变更时回退入参 */
    private fun pinnedValue(): Boolean =
        curPinned ?: pageData.params.optBoolean("isPinned", false)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFFFFFFF.toInt()))
                flexDirectionColumn()
                padding(16f)
            }

            // ---------- 标题 ----------
            ctx.sectionLabel(this, "标题")
            View {
                attr {
                    marginTop(6f)
                    padding(10f)
                    borderRadius(8f)
                    backgroundColor(Color(0xFFF2F2F2.toInt()))
                }
                Input {
                    attr {
                        // 注：Kuikly 2.26.0 的 Input 在首帧组合时，`text()` 写入的 "text" prop
                        // 可能因原生 EditText 尚未就绪而未生效（TextArea 走 shadow 路径无此问题），
                        // 表现为标题区空白。这里额外用 `textInputState` 作为初始值通道，
                        // 它在原生侧经 setTextInputState（带 isSettingTextInputState 防回环标志）
                        // 原子地写入文本与光标位置，可稳定首帧显示。
                        text(ctx.titleValue())
                        textInputState(TextInputState(text = ctx.titleValue()))
                        placeholder("请输入标题")
                        fontSize(16f)
                        color(Color(0xFF000000.toInt()))
                    }
                    event {
                        textDidChange { params -> ctx.editTitle = params.text }
                    }
                }
            }

            // ---------- 内容（多行） ----------
            ctx.sectionLabel(this, "内容")
            View {
                attr {
                    marginTop(6f)
                    height(120f)
                    padding(10f)
                    borderRadius(8f)
                    backgroundColor(Color(0xFFF2F2F2.toInt()))
                }
                TextArea {
                    attr {
                        text(ctx.contentValue())
                        placeholder("请输入内容")
                        fontSize(15f)
                        color(Color(0xFF000000.toInt()))
                    }
                    event {
                        textDidChange { params -> ctx.editContent = params.text }
                    }
                }
            }

            // ---------- 状态行 ----------
            Text {
                attr {
                    text(if (ctx.statusValue() == 1) "状态：已完成" else "状态：未完成")
                    fontSize(15f)
                    color(Color(0xFF666666.toInt()))
                    marginTop(14f)
                }
            }

            if (ctx.pinnedValue()) {
                Text {
                    attr {
                        text("已置顶")
                        fontSize(14f)
                        color(Color(0xFFFF9800.toInt()))
                        marginTop(4f)
                    }
                }
            }

            if (ctx.hint.isNotEmpty()) {
                Text {
                    attr {
                        text(ctx.hint)
                        fontSize(14f)
                        color(Color(0xFF4CAF50.toInt()))
                        marginTop(8f)
                    }
                }
            }

            // ---------- 操作按钮 ----------
            ctx.actionButton(this, "保存修改", 0xFF2196F3.toInt()) {
                ctx.bridge()?.update(
                    mapOf(
                        "todoId" to ctx.todoIdValue(),
                        "title" to ctx.titleValue(),
                        "content" to ctx.contentValue()
                    )
                )
                ctx.hint = "已保存"
            }

            ctx.actionButton(
                this,
                if (ctx.statusValue() == 1) "取消完成" else "标记完成",
                0xFF4CAF50.toInt()
            ) {
                val next = if (ctx.statusValue() == 1) 0 else 1
                ctx.bridge()?.setStatus(ctx.todoIdValue(), next)
                ctx.curStatus = next
                ctx.hint = if (next == 1) "已标记为完成" else "已取消完成"
            }

            ctx.actionButton(this, "切换置顶", 0xFFFF9800.toInt()) {
                val next = !ctx.pinnedValue()
                ctx.bridge()?.togglePin(ctx.todoIdValue())
                ctx.curPinned = next
                ctx.hint = if (next) "已置顶" else "已取消置顶"
            }

            ctx.actionButton(this, "删除待办", 0xFFF44336.toInt()) {
                ctx.bridge()?.delete(ctx.todoIdValue())
                // 删除后页面失去意义，关闭承载页回到列表（列表由 Room 自动刷新）
                ctx.bridge()?.closePage()
            }
        }
    }

    // ==================== 内部工具 ====================

    /** 获取统一桥接 Module（未注册时返回 null，调用处用 ?. 安全调用） */
    private fun bridge(): CorgiBridgeModule? = acquireModule<CorgiBridgeModule>("CorgiBridge")

    /** 小节标题：灰色小字 */
    private fun sectionLabel(container: ViewContainer<*, *>, label: String) {
        container.Text {
            attr {
                text(label)
                fontSize(13f)
                color(Color(0xFF888888.toInt()))
                marginTop(14f)
            }
        }
    }

    /** 操作按钮：圆角色块 + 点击回调 + 居中白色文字 */
    private fun actionButton(
        container: ViewContainer<*, *>,
        label: String,
        bgColor: Int,
        onClick: () -> Unit
    ) {
        container.View {
            attr {
                marginTop(12f)
                padding(12f)
                borderRadius(8f)
                backgroundColor(Color(bgColor))
                allCenter()
            }
            event {
                click { onClick() }
            }
            Text {
                attr {
                    text(label)
                    fontSize(16f)
                    color(Color(0xFFFFFFFF.toInt()))
                }
            }
        }
    }
}
