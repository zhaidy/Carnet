package com.personal.carnet.domain

import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import java.time.LocalDate
import java.time.YearMonth

/** 日历页：一个月按周排好，以后的日子上标出固定收支 */
object CalendarMonth {

    /** 周一开头，每行 7 格；不属于这个月的格子是 null */
    fun weeks(month: YearMonth): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        return cells.chunked(7).map { it + List(7 - it.size) { null } }
    }

    /** 这个月里今天之后要发生的固定收支（暂停的不算）；ledgerId 不是 null 时只看记到这个账本的支出 */
    fun planned(rules: List<RecurringRule>, month: YearMonth, today: LocalDate, ledgerId: Long? = null): Map<LocalDate, List<RecurringRule>> {
        val end = month.atEndOfMonth()
        if (end <= today) return emptyMap()
        val after = maxOf(today, month.atDay(1).minusDays(1))
        return rules
            .filter { it.active && (ledgerId == null || (it.kind == RuleKind.EXPENSE && it.ledgerId == ledgerId)) }
            .flatMap { rule ->
                val from = maxOf(after, LocalDate.ofEpochDay(rule.lastDoneEpochDay))
                Schedule.occurrences(rule, from, end).map { it to rule }
            }
            .groupBy({ it.first }, { it.second })
    }

    /** 格子里放得下的金额：86、1.2k、12k（按元，四舍五入） */
    fun compact(cents: Long): String {
        val yuan = Math.round(cents / 100.0)
        return when {
            yuan < 1000 -> yuan.toString()
            yuan < 10_000 -> String.format(java.util.Locale.ROOT, "%.1fk", yuan / 1000.0).replace(".0k", "k")
            else -> "${Math.round(yuan / 1000.0)}k"
        }
    }
}
