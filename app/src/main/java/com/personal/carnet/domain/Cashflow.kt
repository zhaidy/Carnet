package com.personal.carnet.domain

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import java.time.LocalDate

/** 一次将要发生（或该确认还没确认）的固定收支 */
data class Occurrence(
    val rule: RecurringRule,
    val date: LocalDate,
    /** 按 [currency]；还卡是卡上现在欠的，所以是估的 */
    val cents: Long,
    val currency: Currency,
    val estimate: Boolean,
) {
    val inflow: Boolean get() = rule.kind == RuleKind.INCOME
}

data class Plan(
    /** 到日子了、还没记的（只有「先提醒我」的规则才会有） */
    val pending: List<Occurrence>,
    /** 往后 40 天 */
    val upcoming: List<Occurrence>,
    val payday: LocalDate?,
    /** 发薪前还能花，按主货币；没设工资时是 null */
    val spendableCents: Long?,
    /** 每月固定支出里你那份，按主货币 */
    val monthlyFixedCents: Long,
)

object Cashflow {

    fun isSalary(rule: RecurringRule) = rule.kind == RuleKind.INCOME && rule.category == "salary"

    /** 还卡金额：卡上现在欠多少（全额） */
    fun cardOwed(rule: RecurringRule, balances: Map<Long, Long>): Long =
        (-(balances[rule.toAccountId] ?: 0L)).coerceAtLeast(0)

    fun occurrence(rule: RecurringRule, date: LocalDate, accounts: Map<Long, Account>, balances: Map<Long, Long>): Occurrence =
        if (rule.kind == RuleKind.CARD_PAYMENT) {
            val card = accounts[rule.toAccountId]
            Occurrence(rule, date, cardOwed(rule, balances), card?.currency ?: rule.currency, estimate = true)
        } else {
            Occurrence(rule, date, rule.amountCents, rule.currency, estimate = rule.approximate)
        }

    fun plan(
        rules: List<RecurringRule>,
        accounts: List<Account>,
        balances: Map<Long, Long>,
        ledgers: List<Ledger>,
        rate: FxRate,
        today: LocalDate,
    ): Plan {
        val active = rules.filter { it.active }
        val byId = accounts.associateBy { it.id }
        fun occ(rule: RecurringRule, d: LocalDate) = occurrence(rule, d, byId, balances)

        val pending = active.filter { !it.auto }.flatMap { rule ->
            Schedule.occurrences(rule, LocalDate.ofEpochDay(rule.lastDoneEpochDay), today).map { occ(rule, it) }
        }.sortedBy { it.date }

        val horizon = today.plusDays(40)
        val upcoming = active.flatMap { rule ->
            Schedule.occurrences(rule, maxOf(today, LocalDate.ofEpochDay(rule.lastDoneEpochDay)), horizon).map { occ(rule, it) }
        }.sortedBy { it.date }

        val payday = active.filter(::isSalary)
            .mapNotNull { rule -> Schedule.occurrences(rule, maxOf(today, LocalDate.ofEpochDay(rule.lastDoneEpochDay)), horizon).firstOrNull() }
            .minOrNull()

        val spendable = payday?.let { pay ->
            val cash = accounts.filter { it.inCashflow && it.type != AccountType.CREDIT }
            val cashIds = cash.map { it.id }.toSet()
            val have = cash.sumOf { rate.convert(balances[it.id] ?: 0, it.currency, MainCurrency) }
            // 发薪前（含发薪当天）从这些账户出去的：没确认的 + 接下来的
            val out = (pending + upcoming)
                .filter { !it.inflow && !it.date.isAfter(pay) && it.rule.accountId in cashIds }
                .sumOf { rate.convert(it.cents, it.currency, MainCurrency) }
            have - out
        }

        val ledgerById = ledgers.associateBy { it.id }
        val monthly = active.filter { it.kind == RuleKind.EXPENSE }.sumOf { rule ->
            val ledger = ledgerById[rule.ledgerId]
            val perMonth = Schedule.perMonth(rule)
            val mine = if (ledger?.shared == true) Split.myShare(perMonth, ledger.myPercent) else perMonth
            rate.convert(mine, rule.currency, MainCurrency)
        }

        return Plan(pending, upcoming, payday, spendable, monthly)
    }
}
