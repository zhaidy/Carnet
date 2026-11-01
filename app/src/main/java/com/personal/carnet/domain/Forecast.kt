package com.personal.carnet.domain

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Entry
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.RuleKind
import com.personal.carnet.data.SettleMode
import java.time.LocalDate
import java.time.YearMonth

/** 还款预测里的一项：哪天、什么、进出多少（主货币，进是正）、这之后能用的钱还剩多少 */
data class ForecastItem(
    val date: LocalDate,
    val kind: Kind,
    val name: String,
    val cents: Long,
    val balance: Long,
) {
    enum class Kind { FIXED, CARD, SETTLE }
}

data class Forecast(val start: Long, val items: List<ForecastItem>, val until: LocalDate) {
    /** 第一次不够的那一项；都够是 null */
    val firstShort: ForecastItem? get() = items.firstOrNull { it.balance < 0 }

    /** 钱最少的时候：余额最低的那一项；比现在还多（只进不出）是 null */
    val lowest: ForecastItem? get() = items.minByOrNull { it.balance }?.takeIf { it.balance < start }

    /** 不够之后，哪一项过后又够了 */
    val recovered: ForecastItem? get() {
        val short = firstShort ?: return null
        return items.dropWhile { it !== short }.firstOrNull { it.balance >= 0 }
    }
}

/**
 * 还款预测：从能用的账户现在的钱开始，把接下来的进出按日子排开，看每个还款日那天够不够。
 * - 固定收支：从能用的账户进出的（工资、房租、话费、建了规则的还卡）
 * - 没建还卡规则的信用卡：按卡上的还款日、账单日算这期要还多少；再下一期按现在欠的剩下的估
 * - 共用账本：按账本的结算日，她补你（或你补她）现在的差额，加上到那天为止固定支出里她那份
 * 同一天先进后出（工资、她转的钱一般早上就到）
 */
object Forecasts {

    fun build(
        plan: Plan,
        accounts: List<Account>,
        balances: Map<Long, Long>,
        ledgers: List<LedgerView>,
        rate: FxRate,
        today: LocalDate,
        entries: List<Entry>,
        days: Long = 40,
    ): Forecast {
        val until = today.plusDays(days)
        val cash = accounts.filter { it.inCashflow && it.type != AccountType.CREDIT }
        val cashIds = cash.map { it.id }.toSet()
        val start = cash.sumOf { rate.convert(balances[it.id] ?: 0, it.currency, MainCurrency) }
        data class Event(val date: LocalDate, val kind: ForecastItem.Kind, val name: String, val cents: Long)
        val events = mutableListOf<Event>()

        val occs = (plan.pending + plan.upcoming).filter { !it.date.isAfter(until) }
        for (o in occs) {
            if (o.rule.accountId !in cashIds) continue
            val cents = rate.convert(o.cents, o.currency, MainCurrency)
            val kind = if (o.rule.kind == RuleKind.CARD_PAYMENT) ForecastItem.Kind.CARD else ForecastItem.Kind.FIXED
            events += Event(maxOf(o.date, today), kind, o.rule.name, if (o.inflow) cents else -cents)
        }

        // 没建还卡规则的信用卡
        val ruled = occs.filter { it.rule.kind == RuleKind.CARD_PAYMENT }.mapNotNull { it.rule.toAccountId }.toSet() +
            plan.upcoming.filter { it.rule.kind == RuleKind.CARD_PAYMENT }.mapNotNull { it.rule.toAccountId }
        for (card in accounts.filter { it.type == AccountType.CREDIT && it.id !in ruled }) {
            val owedNow = (-(balances[card.id] ?: 0L)).coerceAtLeast(0)
            val first = Cashflow.nextDueDate(card, today) ?: continue
            if (owedNow == 0L || first.isAfter(until)) continue
            val firstAmount = Cashflow.cardDueOn(card, first, owedNow, entries, today)
            if (firstAmount > 0) events += Event(first, ForecastItem.Kind.CARD, card.name, -rate.convert(firstAmount, card.currency, MainCurrency))
            val nextMonth = YearMonth.from(first).plusMonths(1)
            val second = nextMonth.atDay((card.dueDay ?: first.dayOfMonth).coerceIn(1, nextMonth.lengthOfMonth()))
            val rest = owedNow - firstAmount
            if (!second.isAfter(until) && rest > 0) events += Event(second, ForecastItem.Kind.CARD, card.name, -rate.convert(rest, card.currency, MainCurrency))
        }

        // 共用账本到结算日：她补你（正）/ 你补她（负）
        for (view in ledgers.filter { it.ledger.shared }) {
            val l = view.ledger
            val settle = settleDate(l, today) ?: continue
            if (settle.isAfter(until)) continue
            val ledgerCurrency = l.currency ?: MainCurrency
            val herShares = occs.filter { it.rule.kind == RuleKind.EXPENSE && it.rule.ledgerId == l.id && !it.date.isAfter(settle) }
                .sumOf { o ->
                    val inLedger = rate.convert(o.cents, o.currency, ledgerCurrency)
                    val her = o.rule.herCents?.let { rate.convert(it, o.currency, ledgerCurrency) } ?: l.herFixedCents
                    inLedger - Split.myPart(inLedger, her, l.myPercent)
                }
            val total = view.balanceCents + herShares
            if (total != 0L) events += Event(settle, ForecastItem.Kind.SETTLE, l.name, rate.convert(total, ledgerCurrency, MainCurrency))
        }

        var balance = start
        val items = events.sortedWith(compareBy<Event>({ it.date }, { -it.cents })).map { e ->
            balance += e.cents
            ForecastItem(e.date, e.kind, e.name, e.cents, balance)
        }
        return Forecast(start, items, until)
    }

    /** (after, through] 之间所有的结算日，按先后排；想结就结的没有 */
    fun settleDatesBetween(l: Ledger, after: LocalDate, through: LocalDate): List<LocalDate> {
        if (!through.isAfter(after)) return emptyList()
        return when (l.settleMode) {
            SettleMode.MANUAL -> emptyList()
            SettleMode.DATE -> listOfNotNull(l.settleEpochDay?.let(LocalDate::ofEpochDay)?.takeIf { it.isAfter(after) && !it.isAfter(through) })
            SettleMode.MONTHLY -> {
                val out = mutableListOf<LocalDate>()
                var m = YearMonth.from(after)
                while (!m.isAfter(YearMonth.from(through))) {
                    val d = m.atDay(l.settleDay.coerceIn(1, m.lengthOfMonth()))
                    if (d.isAfter(after) && !d.isAfter(through)) out += d
                    m = m.plusMonths(1)
                }
                out
            }
        }
    }

    /** 共用账本下一次结算的日子（今天也算）；想结就结的是 null */
    fun settleDate(l: Ledger, today: LocalDate): LocalDate? = when (l.settleMode) {
        SettleMode.MANUAL -> null
        SettleMode.DATE -> l.settleEpochDay?.let(LocalDate::ofEpochDay)?.takeIf { !it.isBefore(today) }
        SettleMode.MONTHLY -> {
            fun on(m: YearMonth) = m.atDay(l.settleDay.coerceIn(1, m.lengthOfMonth()))
            val thisMonth = on(YearMonth.from(today))
            if (!thisMonth.isBefore(today)) thisMonth else on(YearMonth.from(today).plusMonths(1))
        }
    }
}
