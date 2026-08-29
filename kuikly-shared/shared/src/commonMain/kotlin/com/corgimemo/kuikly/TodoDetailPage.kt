package com.corgimemo.kuikly

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.views.*
import com.corgimemo.kuikly.base.BasePager

/**
 * 待办详情页（第一个真实 Kuikly 页面，阶段二）
 *
 * 通过 pageData.params 接收主工程传入的待办字段，渲染标题/内容/状态；
 * 「标记完成 / 取消完成」按钮经 [CorgiBridgeModule] 把操作回写主工程 Room。
 *
 * 入参（pageData.params，均为可选，缺省有兜底）：
 * - todoId: Long    待办 ID
 * - title: String   标题
 * - content: String 内容（纯文本）
 * - dueDate: Long   截止时间（时间戳，0 表示无）
 * - status: Int     当前状态 1=完成 0=未完成
 * - isPinned: Boolean 是否置顶
 */
@Page("todoDetail", supportInLocal = true)
internal class TodoDetailPage : BasePager() {

    /** 待办 ID（无则 0，按钮点击后原生侧会因查不到记录而忽略）*/
    private val todoId: Long
        get() = pageData.params.optLong("todoId", 0L)

    private val title: String
        get() = pageData.params.optString("title", "")

    private val content: String
        get() = pageData.params.optString("content", "")

    private val status: Int
        get() = pageData.params.optInt("status", 0)

    private val isPinned: Boolean
        get() = pageData.params.optBoolean("isPinned", false)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFFFFFFF.toInt()))
                flexDirectionColumn()
                padding(16f)
            }

            Text {
                attr {
                    text(ctx.title.ifEmpty { "待办详情" })
                    fontSize(22f)
                    color(Color(0xFF000000.toInt()))
                    fontWeightBold()
                }
            }

            Text {
                attr {
                    text(if (ctx.status == 1) "状态：已完成" else "状态：未完成")
                    fontSize(16f)
                    color(Color(0xFF888888.toInt()))
                    marginTop(8f)
                }
            }

            if (ctx.isPinned) {
                Text {
                    attr {
                        text("已置顶")
                        fontSize(14f)
                        color(Color(0xFF888888.toInt()))
                        marginTop(4f)
                    }
                }
            }

            Text {
                attr {
                    text(if (ctx.content.isNotEmpty()) ctx.content else "（无内容）")
                    fontSize(16f)
                    color(Color(0xFF000000.toInt()))
                    marginTop(12f)
                }
            }

            // 操作按钮：用 View 容器 + event.click 实现（避免依赖 Button 组件）
            View {
                attr {
                    marginTop(24f)
                    padding(12f)
                    borderRadius(8f)
                    backgroundColor(Color(0xFF2196F3.toInt()))
                    allCenter()
                }
                event {
                    click {
                        // 经 CorgiBridgeModule 回写主工程：切换为相反状态
                        val nextStatus = if (ctx.status == 1) 0 else 1
                        ctx.acquireModule<CorgiBridgeModule>("KRCorgiBridgeModule")
                            ?.setTodoStatus(ctx.todoId, nextStatus)
                    }
                }
                Text {
                    attr {
                        text(if (ctx.status == 1) "取消完成" else "标记完成")
                        fontSize(16f)
                        color(Color(0xFFFFFFFF.toInt()))
                    }
                }
            }
        }
    }
}
