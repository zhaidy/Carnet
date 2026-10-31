package com.personal.carnet.domain

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class EntrySearchTest {

    private val zone = ZoneId.of("Asia/Singapore")
    private val rate = FxRate(5.30)
    private val bank = Account(id = 1, name = "储蓄卡", type = AccountType.DEBIT, currency = Currency.SGD, openingCents = 0)
    private val alipay = Account(id = 2, name = "支付宝", type = AccountType.WALLET, currency = Currency.CNY, openingCents = 0)
    private val personal = Ledger(id = 1, name = "个人", currency = null, shared = false)
    private val trip = Ledger(id = 2, name = "回国旅行", currency = Currency.CNY, shared = true)
    private val accounts = listOf(bank, alipay).associateBy { it.id }
    private val ledgers = listOf(personal, trip).associateBy { it.id }

    private fun at(date: String, hour: Int = 12) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val hotpot = Entry(
        id = 1, kind = EntryKind.EXPENSE, timeMillis = at("2026-10-30", 20), ledgerId = 1, category = "food", note = "海底捞",
        amountCents = 8650, currency = Currency.SGD, accountId = 1, accountCents = 8650, ledgerCents = 8650,
    )
    private val coffee = Entry(
        id = 2, kind = EntryKind.EXPENSE, timeMillis = at("2026-10-30", 9), ledgerId = 1, category = "drink",
        amountCents = 650, currency = Currency.SGD, accountId = 1, accountCents = 650, ledgerCents = 650,
    )
    private val taxi = Entry(
        id = 3, kind = EntryKind.EXPENSE, timeMillis = at("2026-09-15"), ledgerId = 2, category = "ride", note = "滴滴",
        amountCents = 5300, currency = Currency.CNY, accountId = 2, accountCents = 5300, ledgerCents = 5300,
    )
    private val salary = Entry(
        id = 4, kind = EntryKind.INCOME, timeMillis = at("2026-10-26"), ledgerId = 1, category = "salary",
        amountCents = 600000, currency = Currency.SGD, accountId = 1, accountCents = 600000, ledgerCents = 600000,
    )
    private val transfer = Entry(
        id = 5, kind = EntryKind.TRANSFER, timeMillis = at("2026-10-01"), ledgerId = null, category = "transfer",
        amountCents = 10000, currency = Currency.SGD, accountId = 1, accountCents = 10000, ledgerCents = 0, toAccountId = 2, toCents = 53000,
    )
    private val all = listOf(taxi, salary, hotpot, transfer, coffee)

    private fun search(filter: EntryFilter) = EntrySearch.search(all, filter, accounts, ledgers, rate, zone)

    @Test
    fun 按天分组新的在前() {
        val r = search(EntryFilter())
        assertEquals(5, r.count)
        assertEquals(LocalDate.parse("2026-10-30"), r.days.first().date)
        assertEquals(listOf(1L, 2L), r.days.first().entries.map { it.id })
        assertEquals(9300L, r.days.first().spentCents)
    }

    @Test
    fun 按月筛() {
        val r = search(EntryFilter(month = YearMonth.of(2026, 9)))
        assertEquals(listOf(3L), r.days.flatMap { it.entries }.map { it.id })
        // ¥53 按 5.30 换成 S$10
        assertEquals(1000L, r.spentCents)
    }

    @Test
    fun 搜备注分类账户账本() {
        assertEquals(listOf(1L), search(EntryFilter(query = "海底")).days.flatMap { it.entries }.map { it.id })
        assertEquals(listOf(2L), search(EntryFilter(query = "饮品")).days.flatMap { it.entries }.map { it.id })
        assertEquals(listOf(3L), search(EntryFilter(query = "旅行")).days.flatMap { it.entries }.map { it.id })
        // 转账按转入账户也能搜到
        assertEquals(listOf(5L, 3L), search(EntryFilter(query = "支付宝")).days.flatMap { it.entries }.map { it.id })
        // 两个词都要对上
        assertEquals(listOf(1L), search(EntryFilter(query = "储蓄卡 餐饮")).days.flatMap { it.entries }.map { it.id })
    }

    @Test
    fun 搜金额() {
        assertEquals(listOf(1L), search(EntryFilter(query = "86")).days.flatMap { it.entries }.map { it.id })
        assertEquals(listOf(1L), search(EntryFilter(query = "86.5")).days.flatMap { it.entries }.map { it.id })
        // 转账的到账金额
        assertEquals(listOf(5L), search(EntryFilter(query = "530")).days.flatMap { it.entries }.map { it.id })
    }

    @Test
    fun 按类型和账户筛() {
        assertEquals(listOf(4L), search(EntryFilter(kind = EntryKind.INCOME)).days.flatMap { it.entries }.map { it.id })
        assertEquals(listOf(5L, 3L), search(EntryFilter(accountId = 2)).days.flatMap { it.entries }.map { it.id })
        assertEquals(listOf(3L), search(EntryFilter(ledgerId = 2)).days.flatMap { it.entries }.map { it.id })
        assertEquals(600000L, search(EntryFilter(month = YearMonth.of(2026, 10))).incomeCents)
    }

    @Test
    fun 改日期落在几点() {
        val original = at("2026-10-30", 20)
        val now = at("2026-10-31", 9)
        // 日期没改：原样
        assertEquals(original, EntryTime.on(LocalDate.parse("2026-10-30"), original, now, zone))
        // 改到别的日子：保留原来的 20 点
        assertEquals(at("2026-10-28", 20), EntryTime.on(LocalDate.parse("2026-10-28"), original, now, zone))
        // 改到今天：用现在
        assertEquals(now, EntryTime.on(LocalDate.parse("2026-10-31"), original, now, zone))
        // 新记一笔补昨天：用现在的时分
        assertEquals(at("2026-10-30", 9), EntryTime.on(LocalDate.parse("2026-10-30"), null, now, zone))
    }
}
