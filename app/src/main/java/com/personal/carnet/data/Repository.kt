package com.personal.carnet.data

import com.personal.carnet.domain.Books
import com.personal.carnet.domain.Cashflow
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.Occurrence
import com.personal.carnet.domain.Schedule
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.importer.AccountChoice
import com.personal.carnet.importer.ImportOutcome
import com.personal.carnet.importer.ImportedRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
            plan = Cashflow.plan(rules, snap.accounts.map { it.account }, balances, snap.ledgers.map { it.ledger }, snap.rate, LocalDate.now(), snap.entries),
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

    /** 删掉一笔后通知同步，云端留删除标记 */
    var onEntryDeleted: ((Entry, Ledger?) -> Unit)? = null

    suspend fun deleteEntry(id: Long) {
        val entry = dao.entry(id) ?: return
        dao.deleteEntry(id)
        val ledger = entry.ledgerId?.let { lid -> dao.ledgersNow().firstOrNull { it.id == lid } }
        onEntryDeleted?.invoke(entry, ledger)
    }

    /** 本机改了一笔同步过的账后通知同步 */
    var onEntryUpdated: ((Entry, Ledger?) -> Unit)? = null

    /**
     * 改一笔账。换了账本的话，原来账本那边当作删掉（云端留删除标记），新账本那边当作新记的；
     * 没换账本就原样更新，同步过的整笔重新传。
     */
    suspend fun updateEntry(updated: Entry) {
        val old = dao.entry(updated.id) ?: return
        val ledgers = dao.ledgersNow()
        if (old.ledgerId != updated.ledgerId) {
            dao.update(updated.copy(syncId = null))
            if (old.syncId != null) onEntryDeleted?.invoke(old, ledgers.firstOrNull { it.id == old.ledgerId })
        } else {
            val entry = updated.copy(syncId = old.syncId)
            dao.update(entry)
            if (entry.syncId != null) onEntryUpdated?.invoke(entry, ledgers.firstOrNull { it.id == entry.ledgerId })
        }
    }

    suspend fun updateAccount(account: Account) = dao.update(account)

    /** 有账、固定收支用到的账户删不了，返回 false */
    suspend fun deleteAccount(id: Long): Boolean {
        if (dao.entriesUsingAccount(id) > 0 || dao.rulesUsingAccount(id) > 0) return false
        dao.deleteAccount(id)
        return true
    }

    suspend fun accountInUse(id: Long): Boolean = dao.entriesUsingAccount(id) > 0 || dao.rulesUsingAccount(id) > 0

    suspend fun updateLedger(ledger: Ledger) = dao.update(ledger)

    /** 账本里还有账、固定收支记到这里、或者是最后一个个人账本时删不了 */
    /** 删不了的原因；能删返回 null。里面有账也能删（连账一起删） */
    suspend fun ledgerDeleteBlock(id: Long): String? {
        val ledgers = dao.ledgersNow()
        val ledger = ledgers.firstOrNull { it.id == id } ?: return "找不到这个账本"
        if (!ledger.shared && ledgers.count { !it.shared } <= 1) return "这是最后一个个人账本，收入都记在这里"
        if (dao.rulesUsingLedger(id) > 0) return "有固定收支记到这个账本，先去「固定支出和工资」里改掉或删掉"
        return null
    }

    suspend fun entriesInLedger(id: Long): Int = dao.entriesInLedger(id)

    /**
     * 连同里面的账和两清记录一起删。只删这台手机上的：同步的账本不会在云端留删除标记，对方那边不受影响
     */
    suspend fun deleteLedger(id: Long): Boolean {
        if (ledgerDeleteBlock(id) != null) return false
        dao.deleteEntriesIn(id)
        dao.deleteSettlementsIn(id)
        dao.deleteLedger(id)
        return true
    }

    suspend fun updateRule(rule: RecurringRule) = dao.update(rule)

    suspend fun snapshotNow(): Snapshot = snapshot.first()

    /** 前后 3 分钟内同一个账户同样金额的，当作同一笔（钱包有时会重发通知） */
    suspend fun hasSimilar(accountId: Long, cents: Long, timeMillis: Long): Boolean =
        dao.countSimilar(accountId, cents, timeMillis - 180_000, timeMillis + 180_000) > 0

    /** 把一笔改记到另一个账本，记进账本的金额按这一笔当时的汇率重新换算 */
    suspend fun moveToLedger(entryId: Long, ledgerId: Long) {
        val entry = dao.entry(entryId) ?: return
        val ledger = dao.ledgersNow().firstOrNull { it.id == ledgerId } ?: return
        val rate = entry.rate?.let { FxRate(it) } ?: prefs.defaultRate.value
        val ledgerCurrency = ledger.currency ?: com.personal.carnet.domain.MainCurrency
        updateEntry(entry.copy(ledgerId = ledger.id, ledgerCents = rate.convert(entry.amountCents, entry.currency, ledgerCurrency)))
    }

    /** 自动记账「算我们俩的」：放进第一个共用账本 */
    suspend fun moveToSharedLedger(entryId: Long) {
        val shared = dao.ledgersNow().filter { it.shared }.minByOrNull { it.createdAt } ?: return
        moveToLedger(entryId, shared.id)
    }

    /** 改了、撤销了一次两清后通知同步（第三个参数：是不是撤销） */
    var onSettlementChanged: ((Settlement, Ledger?, Boolean) -> Unit)? = null

    /** 把一次两清改成两清到 [date] 那天为止（含那天），金额按新的区间重算 */
    suspend fun moveSettlement(id: Long, date: LocalDate) {
        val s = dao.settlement(id) ?: return
        val ledger = dao.ledgersNow().firstOrNull { it.id == s.ledgerId } ?: return
        val time = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val previous = dao.settlementsInNow(s.ledgerId).filter { it.id != id && it.timeMillis < time }.maxOfOrNull { it.timeMillis } ?: Long.MIN_VALUE
        val moved = s.copy(timeMillis = time, cents = Books.balanceBetween(ledger, dao.entriesNow(), previous, time))
        dao.update(moved)
        onSettlementChanged?.invoke(moved, ledger, false)
    }

    /** 补记一次以前的两清：[date] 那天（含）之前的账都算结过了，金额按上一次两清到那天算 */
    suspend fun settleUntil(ledgerId: Long, date: LocalDate) {
        val ledger = dao.ledgersNow().firstOrNull { it.id == ledgerId } ?: return
        val time = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val previous = dao.settlementsInNow(ledgerId).filter { it.timeMillis < time }.maxOfOrNull { it.timeMillis } ?: Long.MIN_VALUE
        dao.insert(Settlement(ledgerId = ledgerId, timeMillis = time, cents = Books.balanceBetween(ledger, dao.entriesNow(), previous, time)))
    }

    /** 撤销一次两清：这之前的账重新算进去 */
    suspend fun undoSettlement(id: Long) {
        val s = dao.settlement(id) ?: return
        dao.deleteSettlement(id)
        onSettlementChanged?.invoke(s, dao.ledgersNow().firstOrNull { it.id == s.ledgerId }, true)
    }

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

    /**
     * 导入历史账。导进来的是以前的账，所以账户「现在的余额」不变：把这些账对余额的影响从建账户时的余额里扣回去。
     * 同一个账本里时间、金额都一样的算重复，跳过。settled 时把这些账标成已两清。
     */
    suspend fun importRows(
        rows: List<ImportedRow>,
        choices: Map<String, AccountChoice>,
        ledgerId: Long,
        settled: Boolean,
    ): ImportOutcome = dueLock.withLock {
        val ledgers = dao.ledgersNow()
        val ledger = ledgers.first { it.id == ledgerId }
        val personal = ledgers.filter { !it.shared }.minByOrNull { it.createdAt } ?: ledger
        val rate = prefs.defaultRate.value
        val existing = dao.entriesNow().map { Triple(it.timeMillis, it.amountCents, it.ledgerId) }.toHashSet()

        val accountIdFor = HashMap<String, Long?>()
        for ((name, choice) in choices) {
            accountIdFor[name] = when (choice) {
                is AccountChoice.Existing -> choice.accountId
                AccountChoice.Hers -> null
                is AccountChoice.Create -> {
                    val currency = rows.firstOrNull { it.account == name }?.currency ?: Currency.SGD
                    dao.insert(
                        Account(
                            name = name, type = choice.type, currency = currency, openingCents = 0,
                            inCashflow = choice.type != AccountType.CREDIT && currency == com.personal.carnet.domain.MainCurrency,
                        ),
                    )
                }
            }
        }
        val accounts = dao.accountsNow().associateBy { it.id }

        var duplicates = 0
        val entries = rows.mapNotNull { row ->
            val target = if (row.kind == EntryKind.INCOME) personal else ledger
            if (!existing.add(Triple(row.timeMillis, row.cents, target.id))) { duplicates++; return@mapNotNull null }
            val hers = choices[row.account] == AccountChoice.Hers
            EntryFactory.spending(
                kind = row.kind, ledger = target, account = accountIdFor[row.account]?.let(accounts::get),
                payer = if (hers) Payer.HER else Payer.ME, category = row.category, note = row.note,
                amountCents = row.cents, currency = row.currency, rate = rate, timeMillis = row.timeMillis,
            )
        }

        // 现在的余额不变
        for (account in accounts.values) {
            val delta = entries.sumOf { Books.delta(it, account.id) }
            if (delta != 0L) dao.update(account.copy(openingCents = account.openingCents - delta))
        }
        dao.insertAll(entries)

        if (settled && ledger.shared && entries.isNotEmpty()) {
            val shared = entries.filter { it.ledgerId == ledger.id && it.kind == EntryKind.EXPENSE }
            val balance = com.personal.carnet.domain.Split.balance(
                shared.map { com.personal.carnet.domain.SharedExpense(it.ledgerCents, it.payer == Payer.ME) }, ledger.myPercent,
            )
            dao.insert(Settlement(ledgerId = ledger.id, timeMillis = entries.maxOf { it.timeMillis }, cents = balance))
        }
        ImportOutcome(entries.size, duplicates)
    }

    private class Context(
        val accounts: Map<Long, Account>,
        val ledgers: Map<Long, Ledger>,
        val personal: Ledger?,
        val balances: MutableMap<Long, Long>,
        val rate: FxRate,
        val entries: MutableList<Entry>,
    ) {
        fun apply(entry: Entry) {
            for (id in balances.keys.toList()) balances[id] = balances.getValue(id) + Books.delta(entry, id)
            entries += entry
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
            entries = entries.toMutableList(),
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
                val owed = Cashflow.cardDue(rule, date, card, ctx.balances, ctx.entries, LocalDate.now())
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
