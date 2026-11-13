package com.quandian.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsSeriesTest {

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
