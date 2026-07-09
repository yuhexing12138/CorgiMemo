package com.corgimemo.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * InspirationViewModel.aggregateSavedTags 单元测试
 *
 * 验证历史标签聚合逻辑：
 * - 将多个灵感的标签列表扁平化
 * - 去重
 * - 按字典序排序
 *
 * 用于 TagPickerSheet 的「历史标签快速添加」区域。
 */
class InspirationSavedTagsTest {

    @Test
    fun `空列表返回空`() {
        val result = InspirationViewModel.aggregateSavedTags(emptyList())
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `单个灵感返回其标签列表`() {
        val result = InspirationViewModel.aggregateSavedTags(
            listOf(listOf("工作", "想法"))
        )
        assertEquals(listOf("工作", "想法"), result)
    }

    @Test
    fun `多个灵感扁平化所有标签`() {
        val result = InspirationViewModel.aggregateSavedTags(
            listOf(
                listOf("工作", "想法"),
                listOf("生活", "灵感"),
                emptyList()
            )
        )
        assertEquals(listOf("工作", "想法", "生活", "灵感"), result)
    }

    @Test
    fun `重复标签去重`() {
        val result = InspirationViewModel.aggregateSavedTags(
            listOf(
                listOf("工作", "想法"),
                listOf("工作", "生活"),
                listOf("想法", "灵感")
            )
        )
        assertEquals(listOf("工作", "想法", "生活", "灵感"), result)
    }

    @Test
    fun `标签按字典序排序`() {
        val result = InspirationViewModel.aggregateSavedTags(
            listOf(
                listOf("zebra", "apple"),
                listOf("mango", "banana")
            )
        )
        assertEquals(listOf("apple", "banana", "mango", "zebra"), result)
    }

    @Test
    fun `中文标签按Unicode序排序`() {
        val result = InspirationViewModel.aggregateSavedTags(
            listOf(
                listOf("工作", "生活"),
                listOf("想法", "灵感")
            )
        )
        // Unicode 序：工=0x5DE5, 想=0x60F3, 灵=0x7075, 生=0x751F
        assertEquals(listOf("工作", "想法", "灵感", "生活"), result)
    }
}
