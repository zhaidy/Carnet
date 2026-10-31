package com.personal.carnet.domain

import com.personal.carnet.data.Account
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 账单页的筛选条件；null 表示不限 */
data class EntryFilter(
    val query: String = "",
    /** 哪个月；null 是全部时间 */
    val month: YearMonth? = null,
    val ledgerId: Long? = null,
    val accountId: Long? = null,
    val kind: EntryKind? = null,
)

/** 一天的账，和这天花了多少、进了多少（主货币） */
data class EntryDay(val date: LocalDate, val entries: List<Entry>, val spentCents: Long, val incomeCents: Long)

data class EntryResult(val days: List<EntryDay>, val count: Int, val spentCents: Long, val incomeCents: Long)

object EntrySearch {

    /**
     * 按条件筛账，再按天分组（新的在前）。
     * 搜索词按空格拆开，每个词都要对上：备注、分类、账户、账本的名字，或者金额（输 86 能找到 86.00、86.50）。
     */
    fun search(
        entries: List<Entry>,
        filter: EntryFilter,
        accounts: Map<Long, Account>,
        ledgers: Map<Long, Ledger>,
        rate: FxRate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): EntryResult {
        val words = filter.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val matched = entries.filter { e ->
            (filter.kind == null || e.kind == filter.kind) &&
                (filter.ledgerId == null || e.ledgerId == filter.ledgerId) &&
                (filter.accountId == null || e.accountId == filter.accountId || e.toAccountId == filter.accountId) &&
                (filter.month == null || YearMonth.from(dateOf(e, zone)) == filter.month) &&
                words.all { matches(e, it, accounts, ledgers) }
        }.sortedWith(compareByDescending<Entry> { it.timeMillis }.thenByDescending { it.id })

        val days = matched.groupBy { dateOf(it, zone) }.map { (date, list) ->
            EntryDay(
                date = date,
                entries = list,
                spentCents = list.filter { it.kind == EntryKind.EXPENSE }.sumOf { inMain(it, ledgers, rate) },
                incomeCents = list.filter { it.kind == EntryKind.INCOME }.sumOf { inMain(it, ledgers, rate) },
            )
        }
        return EntryResult(days, matched.size, days.sumOf { it.spentCents }, days.sumOf { it.incomeCents })
    }

    fun dateOf(entry: Entry, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(entry.timeMillis).atZone(zone).toLocalDate()

    /** 记进账本的金额换成主货币；没有账本的按原币换 */
    private fun inMain(e: Entry, ledgers: Map<Long, Ledger>, rate: FxRate): Long {
        val ledger = e.ledgerId?.let(ledgers::get) ?: return rate.convert(e.amountCents, e.currency, MainCurrency)
        return rate.convert(e.ledgerCents, ledger.currency ?: MainCurrency, MainCurrency)
    }

    private fun matches(e: Entry, word: String, accounts: Map<Long, Account>, ledgers: Map<Long, Ledger>): Boolean {
        val texts = listOfNotNull(
            e.note,
            Categories.of(e.category).label.takeIf { e.kind != EntryKind.TRANSFER },
            if (e.kind == EntryKind.TRANSFER) (if (e.category == "card_payment") "还信用卡" else "转账") else null,
            e.accountId?.let(accounts::get)?.name,
            e.toAccountId?.let(accounts::get)?.name,
            e.ledgerId?.let(ledgers::get)?.name,
        )
        if (texts.any { it.lowercase().contains(word) }) return true
        if (word.any { it.isDigit() } && word.all { it.isDigit() || it == '.' }) {
            return listOf(e.amountCents, e.accountCents, e.toCents).any { it != 0L && plain(it).startsWith(word) }
        }
        return false
    }

    private fun plain(cents: Long): String = BigDecimal.valueOf(cents).movePointLeft(2).setScale(2).toPlainString()
}

/** 改日期时，一笔账落在那天的几点 */
object EntryTime {
    /**
     * - 没改日期：用原来的时间
     * - 改成今天：用现在
     * - 改成别的日子：原来那笔用原来的时分，新记的用现在的时分
     */
    fun on(date: LocalDate, original: Long?, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val originalAt = original?.let { Instant.ofEpochMilli(it).atZone(zone) }
        if (originalAt != null && originalAt.toLocalDate() == date) return original
        val nowAt = Instant.ofEpochMilli(now).atZone(zone)
        if (date == nowAt.toLocalDate()) return now
        val time = (originalAt ?: nowAt).toLocalTime()
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}
