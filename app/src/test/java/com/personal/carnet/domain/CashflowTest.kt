package com.personal.carnet.domain

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Cycle
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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

    private val zone = ZoneId.of("Asia/Singapore")
    private fun at(date: String) = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun spend(id: Long, date: String, cents: Long) = Entry(
        id = id, kind = EntryKind.EXPENSE, timeMillis = at(date), ledgerId = 1, category = "food",
        amountCents = cents, currency = Currency.SGD, accountId = 2, accountCents = cents, ledgerCents = cents,
    )
    private fun repay(id: Long, date: String, cents: Long) = Entry(
        id = id, kind = EntryKind.TRANSFER, timeMillis = at(date), ledgerId = null, category = "card_payment",
        amountCents = cents, currency = Currency.SGD, accountId = 1, accountCents = cents, ledgerCents = 0, toAccountId = 2, toCents = cents,
    )

    @Test
    fun 账单日在还款日之前最近的那天() {
        val card = cardA.copy(statementDay = 20)
        assertEquals(LocalDate.of(2026, 10, 20), Cashflow.statementDate(card, LocalDate.of(2026, 11, 13)))
        assertEquals(LocalDate.of(2026, 11, 1), Cashflow.statementDate(card.copy(statementDay = 1), LocalDate.of(2026, 11, 26)))
        // 31 号出账单，碰上小月按月底
        assertEquals(LocalDate.of(2026, 9, 30), Cashflow.statementDate(card.copy(statementDay = 31), LocalDate.of(2026, 10, 13)))
        assertNull(Cashflow.statementDate(cardA, LocalDate.of(2026, 11, 13)))
    }

    @Test
    fun 设了账单日只还到账单日为止的() {
        val card = cardA.copy(statementDay = 20)
        val entries = listOf(
            spend(1, "2026-10-05", 30000),
            spend(2, "2026-10-18", 20000),
            // 账单日之后刷的，下一期再还
            spend(3, "2026-10-25", 15000),
            // 账单日之后先还了一部分
            repay(4, "2026-10-28", 10000),
        )
        val owedNow = -Books.accountBalance(card, entries)
        assertEquals(55000L, owedNow)
        val balances = mapOf(2L to -owedNow)
        val due = LocalDate.of(2026, 11, 13)
        // 50000 出账，已经还了 10000
        assertEquals(40000L, Cashflow.cardDue(payA, due, card, balances, entries, today, zone))
        assertEquals(true, Cashflow.cardDueKnown(card, due, today))
        // 没设账单日：还全部
        assertEquals(55000L, Cashflow.cardDue(payA, due, cardA, balances, entries, today, zone))
        // 账单还没出（今天就是账单日）：先按全部估
        assertEquals(55000L, Cashflow.cardDue(payA, due, card, balances, entries, LocalDate.of(2026, 10, 20), zone))
    }

    @Test
    fun 按账算现在欠() {
        // 10/19 出账、10/26 还清；现在欠的就是 10/19 之后刷的
        val entries = listOf(
            spend(1, "2026-10-10", 50000),
            spend(2, "2026-10-21", 12000),
            spend(3, "2026-10-30", 8107),
            repay(4, "2026-10-26", 50000),
        )
        assertEquals(LocalDate.of(2026, 10, 19), Cashflow.lastStatement(19, today))
        assertEquals(LocalDate.of(2026, 9, 30), Cashflow.lastStatement(31, LocalDate.of(2026, 10, 15)))
        assertNull(Cashflow.lastStatement(null, today))
        // 10/26 那笔还款还的是上一期，不算：现在欠 = 10/19 之后刷的
        assertEquals(20107L, Cashflow.owedSince(2, entries, LocalDate.of(2026, 10, 19), zone))
        // 退款（进卡里的收入）要减掉
        val refund = spend(5, "2026-10-31", 1000).copy(kind = EntryKind.INCOME)
        assertEquals(19107L, Cashflow.owedSince(2, entries + refund, LocalDate.of(2026, 10, 19), zone))
    }

    @Test
    fun 对账的一期() {
        // 账单日 19 号，今天 10/31：这期是 (9/19, 10/19]
        assertEquals(LocalDate.of(2026, 9, 19) to LocalDate.of(2026, 10, 19), Cashflow.statementPeriod(19, today))
        assertEquals(LocalDate.of(2026, 8, 19) to LocalDate.of(2026, 9, 19), Cashflow.statementPeriod(19, today, back = 1))
        // 没设账单日：上个自然月
        assertEquals(LocalDate.of(2026, 9, 30) to LocalDate.of(2026, 10, 31), Cashflow.statementPeriod(null, LocalDate.of(2026, 11, 5)))
        val entries = listOf(
            spend(1, "2026-09-19", 1000),
            spend(2, "2026-09-20", 2000),
            spend(3, "2026-10-19", 3000),
            spend(4, "2026-10-20", 4000),
            repay(5, "2026-10-10", 9999),
        )
        val (from, to) = Cashflow.statementPeriod(19, today)
        assertEquals(listOf(2L, 3L), Cashflow.cardCharges(2, entries, from, to, zone).map { it.id })
    }
}
