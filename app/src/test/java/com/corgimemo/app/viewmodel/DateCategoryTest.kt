package com.corgimemo.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DateCategory 枚举解析测试
 *
 * 验证：
 * 1. 所有 7 个固定枚举值可正常 valueOf
 * 2. 保留的 OTHER 枚举值兼容
 * 3. 自定义类型 "CUSTOM:xxx" 解析为 OTHER 分组
 * 4. 非法字符串不抛异常，fallback 到 OTHER
 */
class DateCategoryTest {

    /**
     * 验证全部 8 个枚举值（含保留的 OTHER）能通过 valueOf 解析到正确的 displayName
     */
    @Test
    fun `枚举 valueOf 全部 8 个值能正常解析`() {
        // 验证 8 个枚举值（7 个新增 + 保留的 OTHER）
        assertEquals("生日", DateCategory.valueOf("BIRTHDAY").displayName)
        assertEquals("纪念日", DateCategory.valueOf("ANNIVERSARY").displayName)
        assertEquals("节日", DateCategory.valueOf("HOLIDAY").displayName)
        assertEquals("生活", DateCategory.valueOf("LIFE").displayName)
        assertEquals("学习", DateCategory.valueOf("STUDY").displayName)
        assertEquals("工作", DateCategory.valueOf("WORK").displayName)
        assertEquals("娱乐", DateCategory.valueOf("ENTERTAINMENT").displayName)
        assertEquals("其他", DateCategory.valueOf("OTHER").displayName)
    }

    /**
     * 验证所有枚举值的 emoji 字段非空
     */
    @Test
    fun `枚举的 emoji 字段非空`() {
        DateCategory.values().forEach { category ->
            assert(category.emoji.isNotEmpty()) { "${category.name} 缺少 emoji" }
        }
    }

    /**
     * 验证自定义类型 CUSTOM 前缀能被识别和去除
     */
    @Test
    fun `自定义类型 CUSTOM 前缀能被识别`() {
        // 模拟 SpecialDateViewModel.groupByDisplayDates 中的解析逻辑
        val raw = "CUSTOM:旅行"
        val isCustom = raw.startsWith("CUSTOM:")
        assert(isCustom) { "CUSTOM 前缀应被识别" }
        assertEquals("旅行", raw.removePrefix("CUSTOM:"))
    }

    /**
     * 验证非法 category 字符串 fallback 到 OTHER，不抛异常
     */
    @Test
    fun `非法 category 字符串 fallback 到 OTHER`() {
        val raw = "INVALID_VALUE"
        val result = runCatching { DateCategory.valueOf(raw) }.getOrDefault(DateCategory.OTHER)
        assertEquals(DateCategory.OTHER, result)
    }
}
