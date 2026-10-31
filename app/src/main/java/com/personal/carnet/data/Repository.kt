package com.personal.carnet.data

import com.personal.carnet.domain.Books
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId

class Repository(private val dao: CarnetDao, private val prefs: Prefs) {

    val defaultRate: StateFlow<FxRate> get() = prefs.defaultRate

    val snapshot: Flow<Snapshot> = combine(
        dao.accounts(), dao.ledgers(), dao.entries(), dao.settlements(), prefs.defaultRate,
    ) { accounts, ledgers, entries, settlements, rate ->
        Books.snapshot(accounts, ledgers, entries, settlements, rate, monthStartMillis())
    }

    /** 第一次打开：建好「个人」和「我们俩」两个账本 */
    suspend fun seedIfEmpty() {
        if (dao.ledgerCount() > 0) return
        dao.insert(Ledger(name = "个人", currency = null, shared = false, createdAt = 1))
        dao.insert(Ledger(name = "我们俩", currency = com.personal.carnet.domain.Currency.SGD, shared = true, createdAt = 2))
    }

    suspend fun addAccount(account: Account) = dao.insert(account)

    suspend fun addLedger(ledger: Ledger) = dao.insert(ledger)

    suspend fun addEntry(entry: Entry) = dao.insert(entry)

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)

    suspend fun settle(ledgerId: Long, cents: Long) =
        dao.insert(Settlement(ledgerId = ledgerId, timeMillis = System.currentTimeMillis(), cents = cents))

    fun setDefaultRate(rate: FxRate) = prefs.setDefaultRate(rate)

    private fun monthStartMillis(): Long =
        LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
