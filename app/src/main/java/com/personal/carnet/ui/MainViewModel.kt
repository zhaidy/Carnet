package com.personal.carnet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.carnet.data.Account
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.Payer
import com.personal.carnet.data.Repository
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Snapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repo: Repository) : ViewModel() {

    val snapshot: StateFlow<Snapshot?> = repo.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val defaultRate: StateFlow<FxRate> get() = repo.defaultRate

    init {
        viewModelScope.launch { repo.seedIfEmpty() }
    }

    /**
     * 记一笔支出或收入。amountCents 是原币金额；账户扣多少、记进账本多少，按这一笔的汇率换算。
     */
    fun saveSpending(
        kind: EntryKind,
        ledger: Ledger,
        account: Account?,
        payer: Payer,
        category: String,
        note: String,
        amountCents: Long,
        currency: Currency,
        rate: FxRate,
    ) {
        val paidByHer = kind == EntryKind.EXPENSE && ledger.shared && payer == Payer.HER
        val accountCents = if (paidByHer || account == null) 0 else rate.convert(amountCents, currency, account.currency)
        val ledgerCurrency = ledger.currency ?: MainCurrency
        val converted = (account != null && !paidByHer && account.currency != currency) || ledgerCurrency != currency
        val entry = Entry(
            kind = kind,
            timeMillis = System.currentTimeMillis(),
            ledgerId = ledger.id,
            category = category,
            note = note.trim(),
            payer = if (paidByHer) Payer.HER else Payer.ME,
            amountCents = amountCents,
            currency = currency,
            accountId = if (paidByHer) null else account?.id,
            accountCents = accountCents,
            ledgerCents = rate.convert(amountCents, currency, ledgerCurrency),
            rate = if (converted) rate.cnyPerSgd else null,
        )
        viewModelScope.launch { repo.addEntry(entry) }
    }

    fun saveTransfer(from: Account, to: Account, fromCents: Long, toCents: Long, rate: FxRate?, note: String) {
        val entry = Entry(
            kind = EntryKind.TRANSFER,
            timeMillis = System.currentTimeMillis(),
            ledgerId = null,
            category = "transfer",
            note = note.trim(),
            amountCents = fromCents,
            currency = from.currency,
            accountId = from.id,
            accountCents = fromCents,
            ledgerCents = 0,
            toAccountId = to.id,
            toCents = toCents,
            rate = rate?.cnyPerSgd,
        )
        viewModelScope.launch { repo.addEntry(entry) }
    }

    fun deleteEntry(id: Long) = viewModelScope.launch { repo.deleteEntry(id) }

    fun addAccount(account: Account) = viewModelScope.launch { repo.addAccount(account) }

    fun addLedger(ledger: Ledger) = viewModelScope.launch { repo.addLedger(ledger) }

    fun settle(ledgerId: Long, cents: Long) = viewModelScope.launch { repo.settle(ledgerId, cents) }

    fun setDefaultRate(rate: FxRate) = repo.setDefaultRate(rate)
}
