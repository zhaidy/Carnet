package com.personal.carnet.ui

import com.personal.carnet.data.Account
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.Payer
import com.personal.carnet.domain.Categories
import com.personal.carnet.domain.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun todayText(): String {
    val d = LocalDate.now()
    return "${d.monthValue}月${d.dayOfMonth}日 ${weekdays[d.dayOfWeek.value - 1]}"
}

fun shortDate(millis: Long): String {
    val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${d.monthValue}/${d.dayOfMonth}"
}

fun epochDayText(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return "${d.year}/${d.monthValue}/${d.dayOfMonth}"
}

/** 一笔账在列表里怎么显示 */
data class EntryLine(val icon: String, val title: String, val meta: String, val amount: String, val sub: String?, val income: Boolean)

fun Entry.line(accounts: Map<Long, Account>, ledgers: Map<Long, Ledger>): EntryLine {
    val accountName = accountId?.let { accounts[it]?.name }
    return when (kind) {
        EntryKind.TRANSFER -> {
            val from = accounts[accountId]
            val to = accounts[toAccountId]
            EntryLine(
                icon = Categories.TRANSFER_ICON,
                title = note.ifBlank { "${from?.name ?: "?"} → ${to?.name ?: "?"}" },
                meta = shortDate(timeMillis) + " · 转账，不算花销",
                amount = Money.format(accountCents, from?.currency ?: currency),
                sub = to?.let { "到账 " + Money.format(toCents, it.currency) },
                income = false,
            )
        }
        else -> {
            val cat = Categories.of(category)
            val ledger = ledgers[ledgerId]
            val who = when {
                payer == Payer.HER -> "她付"
                accountName != null -> accountName
                else -> null
            }
            val meta = listOfNotNull(shortDate(timeMillis), ledger?.name, who).joinToString(" · ")
            val account = accounts[accountId]
            val charged = if (account != null && account.currency != currency && payer == Payer.ME) {
                "$accountName " + (if (kind == EntryKind.EXPENSE) "扣 " else "进 ") + Money.format(accountCents, account.currency)
            } else null
            val sign = if (kind == EntryKind.INCOME) "+" else "-"
            EntryLine(
                icon = cat.icon,
                title = note.ifBlank { cat.label },
                meta = meta,
                amount = sign + Money.format(amountCents, currency),
                sub = charged,
                income = kind == EntryKind.INCOME,
            )
        }
    }
}
