package com.doit.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsSeriesTest {

    @Test
    fun `本周范围取周一到周日，跨月时仍属于同一周`() {
        // 2026-06-01 是周一，2026-06-07 是周日
        val monday = LocalDate.of(2026, 6, 1)
        for (day in 1..7) {
            val today = monday.plusDays((day - 1).toLong())
            val (start, end) = rangeStartEnd(StatRange.WEEK, today)
            assertEquals("今天 $today 的本周一", monday, start)
            assertEquals("今天 $today 的本周日", monday.plusDays(6), end)
        }
        // 跨月的一周：9/28(一) ~ 10/4(日)
        val span = rangeStartEnd(StatRange.WEEK, LocalDate.of(2026, 9, 30))
        assertEquals(LocalDate.of(2026, 9, 28), span.first)
        assertEquals(LocalDate.of(2026, 10, 4), span.second)
    }

    @Test
    fun `自然月范围取当月一号到月末，天数随月长变化`() {
        val cases = listOf(
            LocalDate.of(2026, 2, 14) to 28, // 平年 2 月
            LocalDate.of(2024, 2, 14) to 29, // 闰年 2 月
            LocalDate.of(2026, 4, 30) to 30, // 小月
            LocalDate.of(2026, 8, 1) to 31,  // 大月
            LocalDate.of(2026, 1, 1) to 31,  // 跨年月的起点
            LocalDate.of(2026, 12, 31) to 31,
        )
        cases.forEach { (today, days) ->
            val (start, end) = rangeStartEnd(StatRange.MONTH, today)
            assertEquals("${today} 所在月首日", today.withDayOfMonth(1), start)
            assertEquals("${today} 所在月末日", today.withDayOfMonth(days), end)
            assertEquals(days, start.until(end, java.time.temporal.ChronoUnit.DAYS).toInt() + 1)
        }
    }

    @Test
    fun `跨年时周与月都落在正确的一年，不会滚到去年最后一天`() {
        val newYear = LocalDate.of(2027, 1, 1) // 周五
        val week = rangeStartEnd(StatRange.WEEK, newYear)
        assertEquals(LocalDate.of(2026, 12, 28), week.first)
        assertEquals(LocalDate.of(2027, 1, 3), week.second)
        val month = rangeStartEnd(StatRange.MONTH, newYear)
        assertEquals(LocalDate.of(2027, 1, 1), month.first)
        assertEquals(LocalDate.of(2027, 1, 31), month.second)
    }

    @Test
    fun `年折线按时间顺序回溯十二个月，跨年不错位`() {
        val today = LocalDate.of(2026, 11, 12)
        val counts = mapOf(
            LocalDate.of(2025, 12, 3) to 2, // 去年 12 月
            LocalDate.of(2026, 1, 5) to 1,
            LocalDate.of(2026, 11, 1) to 4,
        )
        val (values, labels) = yearMonthlySeries(counts, today)

        assertEquals(12, values.size)
        assertEquals(12, labels.size)
        // 首项是去年 12 月，末项是当月，而不是从 1 月开头
        assertEquals("12月", labels.first())
        assertEquals("11月", labels.last())
        assertEquals(listOf("12月", "1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月"), labels)

        assertEquals(2, values.first())
        assertEquals(1, values[1])
        assertEquals(4, values.last())
    }

    @Test
    fun `年折线把整月完成数加总，不重复也不漏`() {
        val today = LocalDate.of(2026, 6, 15)
        val counts = buildMap {
            for (day in 1..30) put(LocalDate.of(2026, 5, day), 1) // 5 月 30 天
            put(LocalDate.of(2026, 6, 1), 5)                       // 6 月只统计已过天数
        }
        val (values, labels) = yearMonthlySeries(counts, today)
        assertEquals(30, values[labels.indexOf("5月")])
        assertEquals(5, values.last())
        assertEquals(12, values.size)
    }

    @Test
    fun `年热力图刻度从右往左保留放得下的月份，首列是月日`() {
        val start = LocalDate.of(2025, 11, 13)
        val startDow = start.dayOfWeek.value - 1
        val days = 365
        val cols = ((startDow + days) + 6) / 7
        val marks = monthMarks(start, startDow, cols, days)

        assertTrue(marks.isNotEmpty())
        assertEquals(0, marks.first().first)
        assertTrue(marks.first().second.contains("/")) // 首列「11/13」
        // 列号严格递增，文案按时间顺序
        marks.zipWithNext().forEach { (a, b) -> assertTrue(b.first > a.first) }
        assertEquals("11月", marks.first { it.second == "11月" }.second)
    }
}
