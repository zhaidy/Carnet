package com.personal.carnet.data

import com.personal.carnet.domain.Books
import com.personal.carnet.domain.Cashflow
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.Occurrence
import com.personal.carnet.domain.Schedule
import com.personal.carnet.domain.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class Repository(private val dao: CarnetDao, private val prefs: Prefs) {

    private val dueLock = Mutex()

    val defaultRate: StateFlow<FxRate> get() = prefs.defaultRate

    private val base: Flow<Snapshot> = combine(
        dao.accounts(), dao.ledgers(), dao.entries(), dao.settlements(), prefs.defaultRate,
    ) { accounts, ledgers, entries, settlements, rate ->
        Books.snapshot(accounts, ledgers, entries, settlements, rate, monthStartMillis())
    }

    val snapshot: Flow<Snapshot> = combine(base, dao.rules()) { snap, rules ->
        val balances = snap.accounts.associate { it.account.id to it.balanceCents }
        snap.copy(
            rules = rules,
            plan = Cashflow.plan(rules, snap.accounts.map { it.account }, balances, snap.ledgers.map { it.ledger }, snap.rate, LocalDate.now()),
        )
    }

    /** 第一次打开：建好「个人」和「我们俩」两个账本 */
    suspend fun seedIfEmpty() {
        if (dao.ledgerCount() > 0) return
        dao.insert(Ledger(name = "个人", currency = null, shared = false, createdAt = 1))
        dao.insert(Ledger(name = "我们俩", currency = Currency.SGD, shared = true, createdAt = 2))
    }

    suspend fun addAccount(account: Account) = dao.insert(account)

    suspend fun addLedger(ledger: Ledger) = dao.insert(ledger)

    suspend fun addEntry(entry: Entry) = dao.insert(entry)

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)

    suspend fun settle(ledgerId: Long, cents: Long) =
        dao.insert(Settlement(ledgerId = ledgerId, timeMillis = System.currentTimeMillis(), cents = cents))

    fun setDefaultRate(rate: FxRate) = prefs.setDefaultRate(rate)

    suspend fun addRule(rule: RecurringRule) = dao.insert(rule)

    suspend fun setRuleActive(rule: RecurringRule, active: Boolean) =
        dao.update(rule.copy(active = active, lastDoneEpochDay = maxOf(rule.lastDoneEpochDay, LocalDate.now().toEpochDay())))

    suspend fun deleteRule(id: Long) = dao.deleteRule(id)

    /** 到日子的自动规则记上账；打开应用、回到前台时调用 */
    suspend fun processDue(today: LocalDate = LocalDate.now()) = dueLock.withLock {
        val rules = dao.rulesNow().filter { it.active && it.auto }
        if (rules.isEmpty()) return@withLock
        val ctx = context()
        for (rule in rules) {
            val dates = Schedule.occurrences(rule, LocalDate.ofEpochDay(rule.lastDoneEpochDay), today)
            for (date in dates) {
                val entry = entryFor(rule, date, ctx) ?: continue
                dao.insert(entry)
                ctx.apply(entry)
            }
            if (rule.lastDoneEpochDay < today.toEpochDay()) dao.update(rule.copy(lastDoneEpochDay = today.toEpochDay()))
        }
    }

    /** 「先提醒我」的规则：确认记下 */
    suspend fun confirm(occurrence: Occurrence) = dueLock.withLock {
        val rule = occurrence.rule
        val entry = entryFor(rule, occurrence.date, context())
        if (entry != null) dao.insert(entry)
        dao.update(rule.copy(lastDoneEpochDay = maxOf(rule.lastDoneEpochDay, occurrence.date.toEpochDay())))
    }

    /** 「先提醒我」的规则：这次不记 */
    suspend fun skip(occurrence: Occurrence) = dueLock.withLock {
        val rule = occurrence.rule
        dao.update(rule.copy(lastDoneEpochDay = maxOf(rule.lastDoneEpochDay, occurrence.date.toEpochDay())))
    }

    private class Context(
        val accounts: Map<Long, Account>,
        val ledgers: Map<Long, Ledger>,
        val personal: Ledger?,
        val balances: MutableMap<Long, Long>,
        val rate: FxRate,
    ) {
        fun apply(entry: Entry) {
            for (id in balances.keys.toList()) balances[id] = balances.getValue(id) + Books.delta(entry, id)
        }
    }

    private suspend fun context(): Context {
        val accounts = dao.accountsNow()
        val ledgers = dao.ledgersNow()
        val entries = dao.entriesNow()
        return Context(
            accounts = accounts.associateBy { it.id },
            ledgers = ledgers.associateBy { it.id },
            personal = ledgers.filter { !it.shared }.minByOrNull { it.createdAt },
            balances = accounts.associate { it.id to Books.accountBalance(it, entries) }.toMutableMap(),
            rate = prefs.defaultRate.value,
        )
    }

    private fun entryFor(rule: RecurringRule, date: LocalDate, ctx: Context): Entry? {
        val time = date.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val account = ctx.accounts[rule.accountId]
        return when (rule.kind) {
            RuleKind.EXPENSE, RuleKind.INCOME -> {
                val ledger = (if (rule.kind == RuleKind.EXPENSE) ctx.ledgers[rule.ledgerId] else null) ?: ctx.personal ?: return null
                EntryFactory.spending(
                    kind = if (rule.kind == RuleKind.EXPENSE) EntryKind.EXPENSE else EntryKind.INCOME,
                    ledger = ledger, account = account, payer = Payer.ME, category = rule.category,
                    note = rule.name, amountCents = rule.amountCents, currency = rule.currency, rate = ctx.rate, timeMillis = time,
                )
            }
            RuleKind.CARD_PAYMENT -> {
                val card = ctx.accounts[rule.toAccountId] ?: return null
                val from = account ?: return null
                val owed = Cashflow.cardOwed(rule, ctx.balances)
                if (owed <= 0) return null
                val fromCents = ctx.rate.convert(owed, card.currency, from.currency)
                EntryFactory.transfer(
                    from, card, fromCents, owed, if (from.currency != card.currency) ctx.rate else null, rule.name,
                    timeMillis = time, category = "card_payment",
                )
            }
        }
    }

    private fun monthStartMillis(): Long =
        LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
