package com.corgimemo.kuikly

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.views.*
import com.corgimemo.kuikly.base.BasePager

@Page("router", supportInLocal = true)
internal class RouterPage : BasePager() {

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color.WHITE)
                allCenter()
            }
            Text {
                attr {
                    text("hello kuikly")
                    fontSize(20f)
                    color(Color.GREEN)
                }
            }
        }
    }
}
