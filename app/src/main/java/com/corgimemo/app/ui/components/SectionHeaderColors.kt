package com.corgimemo.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 待办页三个 section header 的颜色常量
 *
 * - Pinned:置顶(主色橙,跟随主题)
 * - Pending:待完成(固定蓝色,跨主题保持一致以提升品牌识别)
 * - Completed:已完成(固定绿色,跨主题保持一致以表示"已处理"语义)
 *
 * 集中定义便于统一调整和未来扩展(如有深色模式特定色,只需修改此处)
 */
object SectionHeaderColors {
    /** 置顶区头颜色(主色橙) */
    val Pinned: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    /** 待完成区头颜色(固定蓝色) */
    val Pending: Color = Color(0xFF5A8DEE)

    /** 已完成区头颜色(固定绿色) */
    val Completed: Color = Color(0xFF7EC8A0)
}
