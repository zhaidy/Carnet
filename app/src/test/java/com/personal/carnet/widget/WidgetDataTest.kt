package com.personal.carnet.widget

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Cycle
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import com.personal.carnet.domain.Books
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.Occurrence
import com.personal.carnet.domain.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WidgetDataTest {

    private val today = LocalDate.of(2026, 11, 1)
    private val bank = Account(id = 1, name = "储蓄卡", type = AccountType.DEBIT, currency = Currency.SGD, openingCents = 500000)
    private val personal = Ledger(id = 1, name = "个人", currency = null, shared = false)
    private val us = Ledger(id = 2, name = "我们俩", currency = Currency.SGD, shared = true)
    private val rent = RecurringRule(
        id = 1, name = "房租", kind = RuleKind.EXPENSE, category = "home", amountCents = 180000, currency = Currency.SGD,
        cycle = Cycle.MONTHLY, day = 20, accountId = 1, lastDoneEpochDay = today.toEpochDay(),
    )

    private fun snapshot(ledgers: List<Ledger>, plan: Plan) =
        Books.snapshot(listOf(bank), ledgers, emptyList(), emptyList(), FxRate(5.3), 0).copy(plan = plan)

    @Test
    fun 有工资时显示发薪前还能花() {
        val plan = Plan(
            pending = emptyList(),
            upcoming = listOf(Occurrence(rent, LocalDate.of(2026, 11, 20), 180000, Currency.SGD, estimate = false)),
            payday = LocalDate.of(2026, 11, 26),
            spendableCents = 320000,
            monthlyFixedCents = 180000,
        )
        val d = WidgetData.from(snapshot(listOf(personal, us), plan), today)
        assertEquals("S$3,200.00", d.headline)
        assertEquals("11/26 发薪前还能花", d.headlineLabel)
        assertEquals("和她两清了", d.shared)
        assertEquals(listOf(Triple("11/20", "房租", "-S$1,800.00")), d.upcoming)
    }

    @Test
    fun 没设工资显示净资产_没有共用账本不显示谁补谁() {
        val d = WidgetData.from(snapshot(listOf(personal), Plan(emptyList(), emptyList(), null, null, 0)), today)
        assertEquals("净资产", d.headlineLabel)
        assertEquals("S$5,000.00", d.headline)
        assertNull(d.shared)
    }
}
