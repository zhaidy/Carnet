package com.personal.carnet.domain

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Cycle
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CashflowTest {

    private val today = LocalDate.of(2026, 10, 31)
    private val rate = FxRate(5.30)
    private val bank = Account(id = 1, name = "储蓄卡", type = AccountType.DEBIT, currency = Currency.SGD, openingCents = 0)
    private val cardA = Account(id = 2, name = "卡A", type = AccountType.CREDIT, currency = Currency.SGD, openingCents = 0, inCashflow = false)
    private val cardB = Account(id = 3, name = "卡B", type = AccountType.CREDIT, currency = Currency.SGD, openingCents = 0, inCashflow = false)
    private val icbc = Account(id = 4, name = "工行卡", type = AccountType.DEBIT, currency = Currency.CNY, openingCents = 0, inCashflow = false)
    private val accounts = listOf(bank, cardA, cardB, icbc)
    private val balances = mapOf(1L to 842050L, 2L to -110230L, 3L to -64280L, 4L to 1230000L)
    private val us = Ledger(id = 2, name = "我们俩", currency = Currency.SGD, shared = true)

    private val lastDone = today.toEpochDay()

    private fun rule(
        name: String, kind: RuleKind, day: Int, amount: Long = 0, account: Long = 1, to: Long? = null,
        category: String = "more", shift: Boolean = false, auto: Boolean = true, ledger: Long? = null, cycle: Cycle = Cycle.MONTHLY,
    ) = RecurringRule(
        name = name, kind = kind, category = category, amountCents = amount, currency = Currency.SGD,
        cycle = cycle, day = day, shiftWeekend = shift, accountId = account, toAccountId = to, ledgerId = ledger,
        auto = auto, lastDoneEpochDay = lastDone,
    )

    private val salary = rule("工资", RuleKind.INCOME, 26, 580000, category = "salary", shift = true)
    private val rent = rule("房租", RuleKind.EXPENSE, 20, 180000, auto = false)
    private val payA = rule("还卡A", RuleKind.CARD_PAYMENT, 13, to = 2)
    private val payB = rule("还卡B", RuleKind.CARD_PAYMENT, 26, to = 3)

    @Test
    fun 发薪前还能花扣掉房租和两张卡() {
        val plan = Cashflow.plan(listOf(salary, rent, payA, payB), accounts, balances, listOf(us), rate, today)
        assertEquals(LocalDate.of(2026, 11, 26), plan.payday)
        // 842,050 − 房租 180,000 − 卡A 110,230 − 卡B（发薪当天）64,280
        assertEquals(487540L, plan.spendableCents)
    }

    @Test
    fun 没设工资算不出来() {
        assertNull(Cashflow.plan(listOf(rent), accounts, balances, listOf(us), rate, today).spendableCents)
    }

    @Test
    fun 先提醒我的过了日子没确认就挂在待确认里() {
        val overdue = rent.copy(lastDoneEpochDay = LocalDate.of(2026, 10, 19).toEpochDay())
        val plan = Cashflow.plan(listOf(salary, overdue), accounts, balances, listOf(us), rate, today)
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), plan.pending.map { it.date })
        // 10/20 没交的和 11/20 的都要扣
        assertEquals(842050L - 360000, plan.spendableCents)
    }

    @Test
    fun 接下来的按日子排() {
        val plan = Cashflow.plan(listOf(salary, rent, payA, payB), accounts, balances, listOf(us), rate, today)
        assertEquals(listOf("还卡A", "房租"), plan.upcoming.take(2).map { it.rule.name })
        assertEquals(setOf("还卡B", "工资"), plan.upcoming.drop(2).take(2).map { it.rule.name }.toSet())
        assertEquals(plan.upcoming.map { it.date }.sorted(), plan.upcoming.map { it.date })
        // 还卡按卡上现在欠的全额估
        assertEquals(110230L, plan.upcoming.first().cents)
    }

    @Test
    fun 每月固定支出共用的只算你那份() {
        val netflix = rule("Netflix", RuleKind.EXPENSE, 12, 1998, ledger = us.id)
        val cloud = rule("云盘", RuleKind.EXPENSE, 314, 9900, cycle = Cycle.YEARLY)
        val plan = Cashflow.plan(listOf(rent, netflix, cloud, salary), accounts, balances, listOf(us), rate, today)
        assertEquals(180000L + 999 + 825, plan.monthlyFixedCents)
    }
}
