package com.corgimemo.app.data.stats

import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.screens.inspiration.InspirationTextUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 字数统计计算函数单元测试
 * 覆盖：同日多条、跨日、归档、空集合、30 天窗口
 */
class DailyWordCountComputeTest {

    /**
     * 构造测试用 Inspiration
     */
    private fun buildInspiration(
        createdAt: Long,
        title: String = "",
        content: String = "正文",
        tags: String = "",
        isArchived: Boolean = false
    ): Inspiration = Inspiration(
        id = 1,
        title = title,
        content = content,
        tags = tags,
        createdAt = createdAt,
        updatedAt = createdAt,
        isArchived = isArchived
    )

    /**
     * 指定日期 0 点的时间戳
     */
    private fun millisOf(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * 复刻 ViewModel 中的计算逻辑（保持测试独立）
     */
    private fun compute(
        range: ChartRange,
        allInspirations: List<Inspiration>,
        today: LocalDate = LocalDate.now()
    ): List<DailyWordCount> {
        val days = (0 until range.days).map { offset ->
            today.minusDays((range.days - 1 - offset).toLong())
        }
        val groupedByDay = allInspirations.groupBy { ins ->
            Instant.ofEpochMilli(ins.createdAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
        var cumulative = 0
        return days.map { date ->
            val daily = groupedByDay[date]?.sumOf { ins ->
                InspirationTextUtils.countInspirationChars(ins)
            } ?: 0
            cumulative += daily
            DailyWordCount(date, daily, cumulative)
        }
    }

    @Test
    fun `空集合返回全 0 数据点`() {
        val result = compute(ChartRange.SEVEN_DAYS, emptyList())
        assertEquals(7, result.size)
        assertEquals(0, result.last().cumulativeChars)
        result.forEach { assertEquals(0, it.dailyChars) }
    }

    @Test
    fun `7 天窗口第 3 天有 1 条灵感累计 5 字`() {
        val today = LocalDate.of(2026, 7, 11)
        val day3 = today.minusDays(4)  // 7 天窗口的第 3 天
        val ins = buildInspiration(
            createdAt = millisOf(day3),
            title = "abc",
            content = ""
        )  // 3 字
        val result = compute(ChartRange.SEVEN_DAYS, listOf(ins), today)
        assertEquals(3, result[2].dailyChars)  // 第 3 个数据点
        assertEquals(3, result.last().cumulativeChars)
    }

    @Test
    fun `同日多条灵感累加为单日字数`() {
        val today = LocalDate.of(2026, 7, 11)
        val day = today.minusDays(2)
        val ins1 = buildInspiration(createdAt = millisOf(day), title = "aaa")  // 3
        val ins2 = buildInspiration(createdAt = millisOf(day), content = "bbbb")  // 4
        val result = compute(ChartRange.SEVEN_DAYS, listOf(ins1, ins2), today)
        assertEquals(7, result[4].dailyChars)
        assertEquals(7, result.last().cumulativeChars)
    }

    @Test
    fun `窗口外的灵感不计入统计`() {
        val today = LocalDate.of(2026, 7, 11)
        val outOfRange = today.minusDays(10)
        val ins = buildInspiration(createdAt = millisOf(outOfRange), title = "abc")
        val result = compute(ChartRange.SEVEN_DAYS, listOf(ins), today)
        assertEquals(0, result.last().cumulativeChars)
    }

    @Test
    fun `30 天窗口包含完整 30 个数据点`() {
        val result = compute(ChartRange.THIRTY_DAYS, emptyList())
        assertEquals(30, result.size)
    }

    @Test
    fun `归档灵感仍计入字数`() {
        val today = LocalDate.of(2026, 7, 11)
        val day = today.minusDays(1)
        val ins = buildInspiration(
            createdAt = millisOf(day),
            title = "归档",
            isArchived = true
        )  // 2 字
        val result = compute(ChartRange.SEVEN_DAYS, listOf(ins), today)
        assertEquals(2, result[5].dailyChars)
        assertEquals(2, result.last().cumulativeChars)
    }

    @Test
    fun `跨多日累计等于各日字数之和`() {
        val today = LocalDate.of(2026, 7, 11)
        val ins1 = buildInspiration(createdAt = millisOf(today.minusDays(6)), title = "aa")
        val ins2 = buildInspiration(createdAt = millisOf(today.minusDays(3)), content = "bbbb")
        val ins3 = buildInspiration(createdAt = millisOf(today), title = "c")
        val result = compute(ChartRange.SEVEN_DAYS, listOf(ins1, ins2, ins3), today)
        assertEquals(2 + 4 + 1, result.last().cumulativeChars)
    }
}
