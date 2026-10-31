package com.personal.carnet.domain

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.Payer
import com.personal.carnet.data.Settlement
import org.junit.Assert.assertEquals
import org.junit.Test

class BooksTest {

    private val rate = FxRate(5.30)
    private val bank = Account(id = 1, name = "储蓄卡", type = AccountType.DEBIT, currency = Currency.SGD, openingCents = 842050)
    private val card = Account(id = 2, name = "卡A", type = AccountType.CREDIT, currency = Currency.SGD, openingCents = -128640)
    private val icbc = Account(id = 3, name = "工行卡", type = AccountType.DEBIT, currency = Currency.CNY, openingCents = 1230000)
    private val personal = Ledger(id = 1, name = "个人", currency = null, shared = false)
    private val us = Ledger(id = 2, name = "我们俩", currency = Currency.SGD, shared = true)
    private val trip = Ledger(id = 3, name = "回国旅行", currency = Currency.CNY, shared = true)

    private fun expense(
        id: Long, ledger: Ledger, cents: Long, account: Account?, payer: Payer = Payer.ME,
        accountCents: Long = cents, currency: Currency = ledger.currency ?: Currency.SGD, time: Long = 1000 + id,
    ) = Entry(
        id = id, kind = EntryKind.EXPENSE, timeMillis = time, ledgerId = ledger.id, category = "food",
        payer = payer, amountCents = cents, currency = currency, accountId = account?.id,
        accountCents = if (payer == Payer.HER) 0 else accountCents, ledgerCents = cents,
    )

    @Test
    fun 共用账本平分算出谁多垫了() {
        val entries = listOf(
            expense(1, us, 8600, card),
            expense(2, us, 2800, null, Payer.HER),
        )
        // 火锅 86 你付，你那份 43，她补你 43；电影 28 她付，你补她 14
        assertEquals(2900L, Books.sharedBalance(us, entries, emptyList()))
    }

    @Test
    fun 按比例分() {
        val ratio = us.copy(myPercent = 60)
        val entries = listOf(expense(1, ratio, 10000, card))
        assertEquals(4000L, Books.sharedBalance(ratio, entries, emptyList()))
    }

    @Test
    fun 两清之后重新算() {
        val entries = listOf(expense(1, us, 8600, card, time = 100), expense(2, us, 2000, card, time = 300))
        val settled = listOf(Settlement(ledgerId = us.id, timeMillis = 200, cents = 4300))
        assertEquals(1000L, Books.sharedBalance(us, entries, settled))
    }

    @Test
    fun 刷卡和她付的对账户余额的影响() {
        val entries = listOf(expense(1, us, 8600, card), expense(2, us, 2800, null, Payer.HER))
        assertEquals(-128640L - 8600, Books.accountBalance(card, entries))
    }

    @Test
    fun 换汇转账两边余额都变() {
        val transfer = Entry(
            id = 9, kind = EntryKind.TRANSFER, timeMillis = 5, ledgerId = null, category = "transfer",
            amountCents = 100000, currency = Currency.SGD, accountId = bank.id, accountCents = 100000,
            ledgerCents = 0, toAccountId = icbc.id, toCents = 528000, rate = 5.28,
        )
        assertEquals(742050L, Books.accountBalance(bank, listOf(transfer)))
        assertEquals(1758000L, Books.accountBalance(icbc, listOf(transfer)))
    }

    @Test
    fun 净资产把人民币和别人欠的都算进来() {
        val entries = listOf(
            // 回国旅行用新币卡刷了 ¥200，卡上扣 S$37.74，你那份 ¥100，她补你 ¥100
            expense(1, trip, 20000, card, accountCents = 3774),
        )
        val snap = Books.snapshot(listOf(bank, card, icbc), listOf(personal, us, trip), entries, emptyList(), rate, 0)
        assertEquals(842050L, snap.mainCashCents)
        assertEquals(1230000L, snap.cnyCashCents)
        assertEquals(232075L, snap.cnyCashInMainCents)
        assertEquals(128640L + 3774, snap.cardsOwedCents)
        assertEquals(1887L, snap.sharedBalanceCents)
        assertEquals(842050 + 232075 - 132414 + 1887L, snap.netWorthCents)
    }

    @Test
    fun 这个月的花销只算你那份() {
        val entries = listOf(
            expense(1, personal, 1500, card, time = 500),
            expense(2, us, 8600, card, time = 600),
            expense(3, us, 9999, card, time = 10),
        )
        val snap = Books.snapshot(listOf(card), listOf(personal, us), entries, emptyList(), rate, monthStartMillis = 100)
        assertEquals(1500L + 4300, snap.monthMySpendingCents)
    }

    @Test
    fun 两清改日期后按新区间算() {
        val entries = listOf(
            expense(1, us, 10000, bank, time = 100),
            expense(2, us, 6000, null, payer = Payer.HER, time = 200),
            expense(3, us, 4000, bank, time = 300),
        )
        // 只算 (100, 250]：她付的 60，你补她 30
        assertEquals(-3000L, Books.balanceBetween(us, entries, 100, 250))
        // 从头到 300：你付 100+40、她付 60 → 她补你 70-30 = 40
        assertEquals(4000L, Books.balanceBetween(us, entries, Long.MIN_VALUE, 300))
        // 改到 250 后，300 那笔又算进还没结的
        val settled = listOf(Settlement(id = 1, ledgerId = us.id, timeMillis = 250, cents = 2000))
        assertEquals(listOf(3L), Books.openSharedExpenses(us, entries, settled).map { it.id })
    }
}
