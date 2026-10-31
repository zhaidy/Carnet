package com.personal.carnet.domain

import com.personal.carnet.data.Cycle
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {

    private fun rule(cycle: Cycle, day: Int, shift: Boolean = false, amount: Long = 1000) = RecurringRule(
        name = "x", kind = RuleKind.EXPENSE, category = "more", amountCents = amount, currency = Currency.SGD,
        cycle = cycle, day = day, shiftWeekend = shift, accountId = 1, lastDoneEpochDay = 0,
    )

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun 每月几号() {
        val got = Schedule.occurrences(rule(Cycle.MONTHLY, 13), d(2026, 10, 31), d(2027, 1, 31))
        assertEquals(listOf(d(2026, 11, 13), d(2026, 12, 13), d(2027, 1, 13)), got)
    }

    @Test
    fun 没有31号的月份按最后一天() {
        val got = Schedule.occurrences(rule(Cycle.MONTHLY, 31), d(2027, 1, 31), d(2027, 3, 31))
        assertEquals(listOf(d(2027, 2, 28), d(2027, 3, 31)), got)
    }

    @Test
    fun 工资碰上周末提前到周五() {
        // 2026-12-26 是周六，提前到 12-25 周五；2027-09-26 是周日，提前到 9-24
        assertEquals(listOf(d(2026, 12, 25)), Schedule.occurrences(rule(Cycle.MONTHLY, 26, shift = true), d(2026, 12, 1), d(2026, 12, 31)))
        assertEquals(listOf(d(2027, 9, 24)), Schedule.occurrences(rule(Cycle.MONTHLY, 26, shift = true), d(2027, 9, 1), d(2027, 9, 30)))
    }

    @Test
    fun 不含开始那天含结束那天() {
        val r = rule(Cycle.MONTHLY, 20)
        assertEquals(listOf(d(2026, 11, 20)), Schedule.occurrences(r, d(2026, 10, 20), d(2026, 11, 20)))
    }

    @Test
    fun 每周和每年() {
        // 2026-11-01 是周日
        assertEquals(listOf(d(2026, 11, 1), d(2026, 11, 8)), Schedule.occurrences(rule(Cycle.WEEKLY, 7), d(2026, 10, 31), d(2026, 11, 10)))
        assertEquals(listOf(d(2027, 3, 14)), Schedule.occurrences(rule(Cycle.YEARLY, 314), d(2026, 10, 31), d(2027, 10, 31)))
    }

    @Test
    fun 下一次() {
        assertEquals(d(2026, 11, 5), Schedule.next(rule(Cycle.MONTHLY, 5), d(2026, 10, 31)))
        assertEquals(d(2026, 10, 31), Schedule.next(rule(Cycle.MONTHLY, 31), d(2026, 10, 31)))
    }

    @Test
    fun 折合每月() {
        assertEquals(825L, Schedule.perMonth(rule(Cycle.YEARLY, 314, amount = 9900)))
        assertEquals(4333L, Schedule.perMonth(rule(Cycle.WEEKLY, 1, amount = 1000)))
    }
}
