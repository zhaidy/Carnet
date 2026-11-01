package com.personal.carnet.data

import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryFactoryTest {
    private val bank = Account(id = 1, name = "储蓄卡", type = AccountType.DEBIT, currency = Currency.SGD, openingCents = 0)
    private val rent = Ledger(id = 3, name = "房租", currency = Currency.SGD, shared = true, herFixedCents = 120000)

    @Test
    fun 账本设了固定金额就默认带上() {
        val e = EntryFactory.spending(EntryKind.EXPENSE, rent, bank, Payer.ME, "home", "", 330000, Currency.SGD, FxRate(5.3))
        assertEquals(120000L, e.herCents)
        // 记账时明确选了「按比例」就不带
        assertNull(EntryFactory.spending(EntryKind.EXPENSE, rent, bank, Payer.ME, "home", "", 330000, Currency.SGD, FxRate(5.3), herCents = null).herCents)
        // 不是共用账本的不带
        assertNull(EntryFactory.spending(EntryKind.EXPENSE, rent.copy(shared = false), bank, Payer.ME, "home", "", 330000, Currency.SGD, FxRate(5.3)).herCents)
    }
}
