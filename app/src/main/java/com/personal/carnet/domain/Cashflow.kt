package com.personal.carnet.domain

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
    /** 下次发薪前（没设工资就到月底）还要付的固定支出：你那份、主货币；不含还卡（卡上欠的已经算进净资产了） */
    val committedCents: Long = 0,
    val committed: List<Occurrence> = emptyList(),
    val committedUntil: LocalDate? = null,
)

object Cashflow {

    fun isSalary(rule: RecurringRule) = rule.kind == RuleKind.INCOME && rule.category == "salary"

    /** 还卡金额：卡上现在欠多少（全额） */
    fun cardOwed(rule: RecurringRule, balances: Map<Long, Long>): Long =
        (-(balances[rule.toAccountId] ?: 0L)).coerceAtLeast(0)

    /** 在 [due] 那天还的这一期，账单日是哪天：[due] 之前最近的那个账单日；没设账单日是 null */
    fun statementDate(card: Account, due: LocalDate): LocalDate? {
        val day = card.statementDay ?: return null
        var month = YearMonth.from(due)
        repeat(3) {
            val date = month.atDay(day.coerceIn(1, month.lengthOfMonth()))
            if (date.isBefore(due)) return date
            month = month.minusMonths(1)
        }
        return null
    }

    /**
     * 在 [due] 那天要还多少：
     * - 设了账单日、账单已经出了：账单日那天结束时欠的，减掉账单日之后已经还进卡里的
     * - 没设账单日，或者账单还没出：按卡上现在欠的全部估
     */
    fun cardDue(
        rule: RecurringRule, due: LocalDate, card: Account?, balances: Map<Long, Long>, entries: List<Entry>,
        today: LocalDate, zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val owedNow = cardOwed(rule, balances)
        if (card == null) return owedNow
        val statement = statementDate(card, due) ?: return owedNow
        if (!statement.isBefore(today)) return owedNow
        val cut = statement.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        var atCut = card.openingCents
        var paidAfter = 0L
        for (e in entries) {
            val d = Books.delta(e, card.id)
            if (d == 0L) continue
            if (e.timeMillis < cut) atCut += d else if (d > 0) paidAfter += d
        }
        return (-atCut - paidAfter).coerceIn(0, owedNow)
    }

    /** 最近一次已经出了的账单日（今天之前）；没设账单日是 null */
    fun lastStatement(statementDay: Int?, today: LocalDate): LocalDate? {
        val day = statementDay ?: return null
        var month = YearMonth.from(today)
        repeat(2) {
            val date = month.atDay(day.coerceIn(1, month.lengthOfMonth()))
            if (date.isBefore(today)) return date
            month = month.minusMonths(1)
        }
        return null
    }

    /**
     * 信用卡按账算现在欠多少：[clearedThrough] 那天（含）之前刷的都还清了，现在欠的 = 之后刷的减掉退款。
     * 之后的还款不算：那多半是还上一期的（比如 10/19 出账、10/26 还清），再扣就扣两次了
     */
    fun owedSince(cardId: Long, entries: List<Entry>, clearedThrough: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        -cardCharges(cardId, entries, clearedThrough, null, zone).sumOf { Books.delta(it, cardId) }

    /** 这张卡在 (after, through] 之间刷的和退的（不含还款）；through 是 null 表示到现在 */
    fun cardCharges(cardId: Long, entries: List<Entry>, after: LocalDate, through: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): List<Entry> {
        val from = after.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = through?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        return entries.filter {
            it.timeMillis in from until to &&
                Books.delta(it, cardId) != 0L &&
                !(it.kind == EntryKind.TRANSFER && it.toAccountId == cardId)
        }
    }

    /**
     * 对账用的一期：(上上次账单日, 上次账单日]；[back] 往前翻几期。
     * 没设账单日就按自然月：上个月 1 号到月底
     */
    fun statementPeriod(statementDay: Int?, today: LocalDate, back: Int = 0): Pair<LocalDate, LocalDate> {
        val last = lastStatement(statementDay, today)
        if (last == null || statementDay == null) {
            val month = YearMonth.from(today).minusMonths(1L + back)
            return month.atDay(1).minusDays(1) to month.atEndOfMonth()
        }
        val endMonth = YearMonth.from(last).minusMonths(back.toLong())
        val prevMonth = endMonth.minusMonths(1)
        return prevMonth.atDay(statementDay.coerceIn(1, prevMonth.lengthOfMonth())) to
            endMonth.atDay(statementDay.coerceIn(1, endMonth.lengthOfMonth()))
    }

    /** 账单已经出了的那一期，金额是准的 */
    fun cardDueKnown(card: Account?, due: LocalDate, today: LocalDate): Boolean =
        card?.let { statementDate(it, due) }?.isBefore(today) == true

    fun occurrence(
        rule: RecurringRule, date: LocalDate, accounts: Map<Long, Account>, balances: Map<Long, Long>,
        entries: List<Entry> = emptyList(), today: LocalDate = LocalDate.now(),
    ): Occurrence =
        if (rule.kind == RuleKind.CARD_PAYMENT) {
            val card = accounts[rule.toAccountId]
            Occurrence(
                rule, date, cardDue(rule, date, card, balances, entries, today), card?.currency ?: rule.currency,
                estimate = !cardDueKnown(card, date, today),
            )
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
        entries: List<Entry> = emptyList(),
    ): Plan {
        val active = rules.filter { it.active }
        val byId = accounts.associateBy { it.id }
        fun occ(rule: RecurringRule, d: LocalDate) = occurrence(rule, d, byId, balances, entries, today)

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

        // 净资产要扣的：发薪前（没设工资就到月底）还没付的固定支出，共用账本只算你那份
        val until = payday ?: YearMonth.from(today).atEndOfMonth()
        val committed = (pending + upcoming).filter { it.rule.kind == RuleKind.EXPENSE && !it.date.isAfter(until) }
        val committedCents = committed.sumOf { occ ->
            val ledger = ledgerById[occ.rule.ledgerId]
            val mine = if (ledger?.shared == true) Split.myShare(occ.cents, ledger.myPercent) else occ.cents
            rate.convert(mine, occ.currency, MainCurrency)
        }

        return Plan(pending, upcoming, payday, spendable, monthly, committedCents, committed, until)
    }
}
