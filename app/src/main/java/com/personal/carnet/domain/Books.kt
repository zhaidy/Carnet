package com.personal.carnet.domain

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.Payer
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.Settlement

data class AccountView(val account: Account, val balanceCents: Long)

data class LedgerView(
    val ledger: Ledger,
    /** 共用账本：上次两清以来一共花了多少；个人账本：这个月花了多少。按账本货币 */
    val periodCents: Long,
    /** 共用账本：正数是你多垫了（她补你），负数是她多垫了。个人账本是 0 */
    val balanceCents: Long,
    val lastSettlement: Settlement?,
) {
    val currency: Currency get() = ledger.currency ?: MainCurrency
}

data class Snapshot(
    val accounts: List<AccountView>,
    val ledgers: List<LedgerView>,
    val entries: List<Entry>,
    val rate: FxRate,
    /** 以下都按主货币 */
    val netWorthCents: Long,
    val mainCashCents: Long,
    val cnyCashCents: Long,
    val cnyCashInMainCents: Long,
    val cardsOwedCents: Long,
    val sharedBalanceCents: Long,
    /** 这个月你的花销：个人账本 + 共用账本里你那份 */
    val monthMySpendingCents: Long,
    val rules: List<RecurringRule> = emptyList(),
    val plan: Plan = Plan(emptyList(), emptyList(), null, null, 0),
)

object Books {

    /** 这笔账让这个账户的余额变了多少（按账户币种） */
    fun delta(entry: Entry, accountId: Long): Long = when (entry.kind) {
        EntryKind.EXPENSE -> if (entry.accountId == accountId && entry.payer == Payer.ME) -entry.accountCents else 0
        EntryKind.INCOME -> if (entry.accountId == accountId) entry.accountCents else 0
        EntryKind.TRANSFER ->
            (if (entry.accountId == accountId) -entry.accountCents else 0) +
                (if (entry.toAccountId == accountId) entry.toCents else 0)
    }

    fun accountBalance(account: Account, entries: List<Entry>): Long =
        account.openingCents + entries.sumOf { delta(it, account.id) }

    fun lastSettlement(ledger: Ledger, settlements: List<Settlement>): Settlement? =
        settlements.filter { it.ledgerId == ledger.id }.maxByOrNull { it.timeMillis }

    /** 共用账本上次两清之后的开销 */
    fun openSharedExpenses(ledger: Ledger, entries: List<Entry>, settlements: List<Settlement>): List<Entry> {
        val since = lastSettlement(ledger, settlements)?.timeMillis ?: Long.MIN_VALUE
        return entries.filter { it.ledgerId == ledger.id && it.kind == EntryKind.EXPENSE && it.timeMillis > since }
    }

    /**
     * 两清改到 [to] 这一刻：那次两清的金额 = 上一次两清（[from]，不含）到 [to]（含）之间的共用开销算出来的。
     */
    fun balanceBetween(ledger: Ledger, entries: List<Entry>, from: Long, to: Long): Long =
        Split.balance(
            entries.filter { it.ledgerId == ledger.id && it.kind == EntryKind.EXPENSE && it.timeMillis > from && it.timeMillis <= to }
                .map { SharedExpense(it.ledgerCents, it.payer == Payer.ME, it.herCents) },
            ledger.myPercent,
        )

    fun sharedBalance(ledger: Ledger, entries: List<Entry>, settlements: List<Settlement>): Long =
        Split.balance(
            openSharedExpenses(ledger, entries, settlements).map { SharedExpense(it.ledgerCents, it.payer == Payer.ME, it.herCents) },
            ledger.myPercent,
        )

    fun snapshot(
        accounts: List<Account>,
        ledgers: List<Ledger>,
        entries: List<Entry>,
        settlements: List<Settlement>,
        rate: FxRate,
        monthStartMillis: Long,
    ): Snapshot {
        val accountViews = accounts.map { AccountView(it, accountBalance(it, entries)) }
        val ledgerViews = ledgers.map { ledger ->
            if (ledger.shared) {
                val open = openSharedExpenses(ledger, entries, settlements)
                LedgerView(
                    ledger = ledger,
                    periodCents = open.sumOf { it.ledgerCents },
                    balanceCents = sharedBalance(ledger, entries, settlements),
                    lastSettlement = lastSettlement(ledger, settlements),
                )
            } else {
                LedgerView(
                    ledger = ledger,
                    periodCents = entries
                        .filter { it.ledgerId == ledger.id && it.kind == EntryKind.EXPENSE && it.timeMillis >= monthStartMillis }
                        .sumOf { it.ledgerCents },
                    balanceCents = 0,
                    lastSettlement = null,
                )
            }
        }

        fun toMain(cents: Long, from: Currency) = rate.convert(cents, from, MainCurrency)

        val cash = accountViews.filter { it.account.type != AccountType.CREDIT }
        val mainCash = cash.filter { it.account.currency == MainCurrency }.sumOf { it.balanceCents }
        val cnyCash = cash.filter { it.account.currency == Currency.CNY }.sumOf { it.balanceCents }
        val cardsOwed = -accountViews.filter { it.account.type == AccountType.CREDIT }
            .sumOf { toMain(it.balanceCents, it.account.currency) }
        val shared = ledgerViews.filter { it.ledger.shared }.sumOf { toMain(it.balanceCents, it.currency) }
        val cnyInMain = toMain(cnyCash, Currency.CNY)

        val ledgersById = ledgers.associateBy { it.id }
        val monthSpending = entries
            .filter { it.kind == EntryKind.EXPENSE && it.timeMillis >= monthStartMillis }
            .sumOf { e ->
                val ledger = ledgersById[e.ledgerId] ?: return@sumOf 0L
                val currency = ledger.currency ?: MainCurrency
                val mine = if (ledger.shared) Split.myPart(e.ledgerCents, e.herCents, ledger.myPercent) else e.ledgerCents
                toMain(mine, currency)
            }

        return Snapshot(
            accounts = accountViews,
            ledgers = ledgerViews,
            entries = entries,
            rate = rate,
            netWorthCents = mainCash + cnyInMain - cardsOwed + shared,
            mainCashCents = mainCash,
            cnyCashCents = cnyCash,
            cnyCashInMainCents = cnyInMain,
            cardsOwedCents = cardsOwed,
            sharedBalanceCents = shared,
            monthMySpendingCents = monthSpending,
        )
    }
}
