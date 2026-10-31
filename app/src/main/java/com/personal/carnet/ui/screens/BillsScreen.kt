package com.personal.carnet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.domain.EntryFilter
import com.personal.carnet.domain.EntrySearch
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.LineIcon
import com.personal.carnet.ui.components.ChipRow
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.GlassField
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.PillButton
import com.personal.carnet.ui.components.ScreenHeader
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.glass.jelly
import com.personal.carnet.ui.line
import java.time.YearMonth

/** 账单：所有记过的账，按月翻、按天分组，能搜、能筛；点一笔进去改 */
@Composable
fun BillsScreen(snapshot: Snapshot, onOpen: (Entry) -> Unit) {
    val now = YearMonth.now()
    var query by rememberSaveable { mutableStateOf("") }
    // 年×12+月，方便存
    var monthIndex by rememberSaveable { mutableIntStateOf(now.year * 12 + now.monthValue - 1) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf<EntryKind?>(null) }
    var ledgerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }

    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val accounts = remember(snapshot) { snapshot.accounts.associate { it.account.id to it.account } }
    val ledgers = remember(snapshot) { snapshot.ledgers.associate { it.ledger.id to it.ledger } }
    val filter = EntryFilter(query, if (allTime) null else month, ledgerId, accountId, kind)
    val result = remember(snapshot, filter) { EntrySearch.search(snapshot.entries, filter, accounts, ledgers, snapshot.rate) }
    val extraFilters = listOfNotNull(ledgerId?.let(ledgers::get)?.name, accountId?.let(accounts::get)?.name)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader("账单") {
                PillButton(if (extraFilters.isEmpty()) "筛选" else "筛选 · ${extraFilters.size}") { filtersOpen = !filtersOpen }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LineIcon(IconPaths.SEARCH, Palette.Muted, size = 20.dp, modifier = Modifier.padding(start = 4.dp))
                GlassField(query, { query = it }, "搜备注、分类、账户、金额", Modifier.weight(1f))
                if (query.isNotEmpty()) {
                    Box(Modifier.glassControl(RoundedCornerShape(14.dp)).semantics { contentDescription = "清空搜索" }.jelly { query = "" }.padding(10.dp)) {
                        LineIcon(IconPaths.CLOSE, Palette.Ink, size = 16.dp)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthArrow(IconPaths.CHEVRON_LEFT, "上个月", enabled = !allTime) { monthIndex-- }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Strong(if (allTime) "全部时间" else monthText(month, now), size = 15.sp, color = if (allTime) Palette.Muted else Palette.Ink)
                    }
                    MonthArrow(IconPaths.CHEVRON_RIGHT, "下个月", enabled = !allTime && month < now) { monthIndex++ }
                }
                Box(
                    Modifier.then(if (allTime) Modifier.holo(RoundedCornerShape(18.dp)) else Modifier.glassControl(RoundedCornerShape(18.dp)))
                        .semantics { role = Role.Switch }
                        .jelly {
                            allTime = !allTime
                            if (!allTime) monthIndex = now.year * 12 + now.monthValue - 1
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) { Strong(if (allTime) "按月看" else "全部时间", size = 13.sp, color = Palette.Violet) }
            }
        }
        item {
            ChipRow(
                listOf<Pair<EntryKind?, String>>(null to "全部", EntryKind.EXPENSE to "支出", EntryKind.INCOME to "收入", EntryKind.TRANSFER to "转账"),
                kind,
            ) { kind = it }
        }
        if (filtersOpen) {
            item {
                GlassCard(padding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChipRow(listOf<Pair<Long?, String>>(null to "全部") + snapshot.ledgers.map { it.ledger.id to it.ledger.name }, ledgerId, "账本") { ledgerId = it }
                        ChipRow(listOf<Pair<Long?, String>>(null to "全部") + snapshot.accounts.map { it.account.id to it.account.name }, accountId, "账户") { accountId = it }
                    }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().holo(RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Label("支出", size = 12.sp)
                        Strong(Money.format(result.spentCents, MainCurrency), size = 22.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Label("收入", size = 12.sp)
                        Strong(Money.format(result.incomeCents, MainCurrency), size = 17.sp, color = Palette.Teal)
                    }
                }
                Label(
                    "${result.count} 笔" + (if (extraFilters.isNotEmpty()) " · " + extraFilters.joinToString(" · ") else "") +
                        " · 共用账本按全额算，人民币按记账时的汇率换成新币",
                    size = 11.sp, weight = FontWeight.Medium,
                )
            }
        }
        if (result.count == 0) {
            item {
                GlassCard {
                    Label(
                        when {
                            query.isNotBlank() -> "没找到「${query.trim()}」。" + (if (!allTime) "试试点「全部时间」。" else "")
                            !allTime -> "${monthText(month, now)}还没有账。"
                            else -> "还没有记账。点右下角的 ＋ 记第一笔。"
                        },
                        weight = FontWeight.Medium,
                    )
                }
            }
        }
        result.days.forEach { day ->
            item(key = "d" + day.date) {
                Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Strong(dayText(day.date), size = 14.sp, modifier = Modifier.weight(1f))
                    Label(
                        listOfNotNull(
                            day.spentCents.takeIf { it > 0 }?.let { "支出 " + Money.format(it, MainCurrency) },
                            day.incomeCents.takeIf { it > 0 }?.let { "收入 " + Money.format(it, MainCurrency) },
                        ).joinToString(" · "),
                        size = 12.sp,
                    )
                }
            }
            items(day.entries, key = { it.id }) { entry ->
                EntryRow(entry.line(accounts, ledgers), onClick = { onOpen(entry) })
            }
        }
    }
}

private fun monthText(month: YearMonth, now: YearMonth): String =
    if (month.year == now.year) "${month.monthValue}月" else "${month.year}年${month.monthValue}月"

@Composable
private fun MonthArrow(icon: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.semantics { contentDescription = description; role = Role.Button }
            .then(if (enabled) Modifier.jelly(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { LineIcon(icon, if (enabled) Palette.Ink else Palette.Muted.copy(alpha = 0.4f), size = 18.dp, width = 2.2f) }
}
