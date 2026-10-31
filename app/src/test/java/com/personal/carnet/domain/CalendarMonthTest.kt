package com.personal.carnet.domain

import com.personal.carnet.data.Cycle
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthTest {

    @Test
    fun 周一开头排好() {
        // 2026 年 11 月 1 日是周日
        val weeks = CalendarMonth.weeks(YearMonth.of(2026, 11))
        assertEquals(6, weeks.size)
        assertEquals(List(6) { null }, weeks[0].take(6))
        assertEquals(LocalDate.of(2026, 11, 1), weeks[0][6])
        assertEquals(LocalDate.of(2026, 11, 30), weeks[5][0])
        assertNull(weeks[5][1])
        weeks.forEach { assertEquals(7, it.size) }
    }

    private fun rule(id: Long, name: String, day: Int, kind: RuleKind = RuleKind.EXPENSE, ledgerId: Long? = 1, active: Boolean = true) = RecurringRule(
        id = id, name = name, kind = kind, category = "home", amountCents = 150000, currency = Currency.SGD,
        cycle = Cycle.MONTHLY, day = day, accountId = 1, ledgerId = ledgerId, active = active,
        lastDoneEpochDay = LocalDate.of(2026, 10, 31).toEpochDay(),
    )

    @Test
    fun 以后的固定收支() {
        val rules = listOf(
            rule(1, "房租", 20),
            rule(2, "工资", 26, RuleKind.INCOME, ledgerId = null),
            rule(3, "停掉的", 5, active = false),
        )
        val today = LocalDate.of(2026, 11, 10)
        val nov = CalendarMonth.planned(rules, YearMonth.of(2026, 11), today)
        assertEquals(listOf("房租"), nov[LocalDate.of(2026, 11, 20)]?.map { it.name })
        assertEquals(listOf("工资"), nov[LocalDate.of(2026, 11, 26)]?.map { it.name })
        assertEquals(2, nov.size)
        // 只看账本 1：工资不算
        assertEquals(1, CalendarMonth.planned(rules, YearMonth.of(2026, 11), today, ledgerId = 1).size)
        // 过去的月份没有
        assertEquals(0, CalendarMonth.planned(rules, YearMonth.of(2026, 10), today).size)
    }

    @Test
    fun 金额缩写() {
        assertEquals("86", CalendarMonth.compact(8640))
        assertEquals("87", CalendarMonth.compact(8650))
        assertEquals("1.2k", CalendarMonth.compact(123400))
        assertEquals("2k", CalendarMonth.compact(200000))
        assertEquals("15k", CalendarMonth.compact(1520000))
    }
}
