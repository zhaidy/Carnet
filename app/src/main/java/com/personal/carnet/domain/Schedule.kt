package com.personal.carnet.domain

import com.personal.carnet.data.Cycle
import com.personal.carnet.data.RecurringRule
import java.time.DayOfWeek
import java.time.LocalDate

/** 固定收支哪天发生 */
object Schedule {

    /** (after, until] 之间所有发生的日子，按先后排 */
    fun occurrences(rule: RecurringRule, after: LocalDate, until: LocalDate): List<LocalDate> {
        if (!until.isAfter(after)) return emptyList()
        val out = mutableListOf<LocalDate>()
        when (rule.cycle) {
            Cycle.MONTHLY -> {
                // 往前多看一个月：周末提前可能把下个月 1 号挪到这个月
                var month = after.withDayOfMonth(1).minusMonths(1)
                while (!month.isAfter(until.plusMonths(1))) {
                    out += shift(rule, month.withDayOfMonth(rule.day.coerceIn(1, month.lengthOfMonth())))
                    month = month.plusMonths(1)
                }
            }
            Cycle.WEEKLY -> {
                val dow = DayOfWeek.of(rule.day.coerceIn(1, 7))
                var d = after.minusDays(7)
                while (d.dayOfWeek != dow) d = d.plusDays(1)
                while (!d.isAfter(until.plusDays(7))) {
                    out += shift(rule, d)
                    d = d.plusWeeks(1)
                }
            }
            Cycle.YEARLY -> {
                val month = (rule.day / 100).coerceIn(1, 12)
                val dom = rule.day % 100
                for (year in after.year - 1..until.year + 1) {
                    val first = LocalDate.of(year, month, 1)
                    out += shift(rule, first.withDayOfMonth(dom.coerceIn(1, first.lengthOfMonth())))
                }
            }
        }
        return out.filter { it.isAfter(after) && !it.isAfter(until) }.distinct().sorted()
    }

    /** 从 [from]（含）起下一次 */
    fun next(rule: RecurringRule, from: LocalDate): LocalDate =
        occurrences(rule, from.minusDays(1), from.plusYears(2)).first()

    private fun shift(rule: RecurringRule, date: LocalDate): LocalDate {
        if (!rule.shiftWeekend) return date
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date.minusDays(1)
            DayOfWeek.SUNDAY -> date.minusDays(2)
            else -> date
        }
    }

    /** 列表里怎么写周期 */
    fun describe(rule: RecurringRule): String {
        val base = when (rule.cycle) {
            Cycle.MONTHLY -> if (rule.day >= 31) "每月月底" else "每月${rule.day}号"
            Cycle.WEEKLY -> "每周" + listOf("一", "二", "三", "四", "五", "六", "日")[rule.day.coerceIn(1, 7) - 1]
            Cycle.YEARLY -> "每年 ${rule.day / 100}/${rule.day % 100}"
        }
        return if (rule.shiftWeekend) "$base，碰上周末提前" else base
    }

    /** 折合每月多少（按原币） */
    fun perMonth(rule: RecurringRule): Long = when (rule.cycle) {
        Cycle.MONTHLY -> rule.amountCents
        Cycle.WEEKLY -> Math.round(rule.amountCents * 52 / 12.0)
        Cycle.YEARLY -> Math.round(rule.amountCents / 12.0)
    }
}
