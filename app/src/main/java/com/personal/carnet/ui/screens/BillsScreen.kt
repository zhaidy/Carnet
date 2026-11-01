package com.personal.carnet.ui.screens

import com.personal.carnet.ui.Partner

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
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import com.personal.carnet.domain.CalendarMonth
import com.personal.carnet.domain.EntryDay
import com.personal.carnet.ui.glass.tinted

/** 账单：所有记过的账，按月翻、按天分组，能搜、能筛；点一笔进去改 */
@Composable
fun BillsScreen(snapshot: Snapshot, onOpen: (Entry) -> Unit, initialLedgerId: Long? = null, onBack: (() -> Unit)? = null) {
    val now = YearMonth.now()
    var query by rememberSaveable { mutableStateOf("") }
    // 年×12+月，方便存
    var monthIndex by rememberSaveable { mutableIntStateOf(now.year * 12 + now.monthValue - 1) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf<EntryKind?>(null) }
    var ledgerId by rememberSaveable { mutableStateOf(initialLedgerId) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(LocalDate.now().toEpochDay()) }

    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val accounts = remember(snapshot) { snapshot.accounts.associate { it.account.id to it.account } }
    val ledgers = remember(snapshot) { snapshot.ledgers.associate { it.ledger.id to it.ledger } }
    val wholeTime = allTime && !calendar
    val filter = EntryFilter(query, if (wholeTime) null else month, ledgerId, accountId, kind)
    val result = remember(snapshot, filter) { EntrySearch.search(snapshot.entries, filter, accounts, ledgers, snapshot.rate) }
    val extraFilters = listOfNotNull(ledgerId?.let(ledgers::get)?.name, accountId?.let(accounts::get)?.name)
    val today = LocalDate.now()
    val planned = remember(snapshot, month, ledgerId, calendar) {
        if (calendar) CalendarMonth.planned(snapshot.rules, month, today, ledgerId) else emptyMap()
    }
    val selected = selectedDay?.let(LocalDate::ofEpochDay)?.takeIf { YearMonth.from(it) == month }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (onBack != null) 40.dp else 130.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(initialLedgerId?.let { ledgers[it]?.name } ?: "账单", onBack = onBack) {
                com.personal.carnet.ui.components.RoundGlassButton(
                    if (calendar) IconPaths.LIST else IconPaths.CALENDAR, if (calendar) "看列表" else "看日历",
                ) { calendar = !calendar }
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
                    MonthArrow(IconPaths.CHEVRON_LEFT, "上个月", enabled = !wholeTime) { monthIndex-- }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Strong(if (wholeTime) "全部时间" else monthText(month, now), size = 15.sp, color = if (wholeTime) Palette.Muted else Palette.Ink)
                    }
                    // 日历可以往后看两个月：看看工资、房租、还卡哪天
                    MonthArrow(IconPaths.CHEVRON_RIGHT, "下个月", enabled = !wholeTime && month < (if (calendar) now.plusMonths(2) else now)) { monthIndex++ }
                }
                if (!calendar) Box(
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
                val shared = ledgerId?.let(ledgers::get)?.takeIf { it.shared }
                if (shared != null) {
                    val split = com.personal.carnet.domain.Split.summary(
                        result.days.flatMap { it.entries }
                            .filter { it.kind == EntryKind.EXPENSE && it.ledgerId == shared.id }
                            .map { com.personal.carnet.domain.SharedExpense(it.ledgerCents, it.payer == com.personal.carnet.data.Payer.ME, it.herCents) },
                        shared.myPercent,
                    )
                    val c = shared.currency ?: MainCurrency
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SplitCell("我付了", Money.format(split.paidByMe, c), "我的那份 " + Money.format(split.myShare, c), Modifier.weight(1f))
                        SplitCell("${Partner.name}付了", Money.format(split.paidByHer, c), "${Partner.name}的那份 " + Money.format(split.herShare, c), Modifier.weight(1f))
                    }
                    val diff = split.paidByMe - split.myShare
                    Label(
                        when {
                            diff > 0 -> "这段时间你多垫了 " + Money.format(diff, c)
                            diff < 0 -> "这段时间${Partner.name}多垫了 " + Money.format(-diff, c)
                            else -> "这段时间正好一人一半"
                        } + "（只算这里筛出来的账，结算看账本页）",
                        size = 11.sp, weight = FontWeight.Bold, color = Palette.Violet,
                    )
                }
                Label(
                    "${result.count} 笔" + (if (extraFilters.isNotEmpty()) " · " + extraFilters.joinToString(" · ") else "") +
                        " · 共用账本按全额算，人民币按记账时的汇率换成新币",
                    size = 11.sp, weight = FontWeight.Medium,
                )
            }
        }
        if (calendar) {
            item(key = "calendar") {
                MonthGrid(month, result.days.associateBy { it.date }, planned, today, selected) { date ->
                    selectedDay = if (selected == date) null else date.toEpochDay()
                }
            }
            if (selected == null) {
                item { Label("点一天，看那天的账", size = 12.sp, weight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp)) }
            } else {
                val day = result.days.firstOrNull { it.date == selected }
                val rules = planned[selected].orEmpty()
                item(key = "sel" + selected) {
                    Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Strong(dayText(selected), size = 14.sp, modifier = Modifier.weight(1f))
                        if (day != null) Label(dayTotals(day), size = 12.sp)
                    }
                }
                if (day == null && rules.isEmpty()) {
                    item { GlassCard(padding = 14.dp) { Label(if (selected > today) "这天还没有安排" else "这天没有记账", weight = FontWeight.Medium) } }
                }
                items(day?.entries.orEmpty(), key = { it.id }) { entry ->
                    EntryRow(entry.line(accounts, ledgers), onClick = { onOpen(entry) })
                }
                items(rules, key = { "r" + it.id }) { rule -> PlannedRow(rule, accounts[rule.accountId]?.name) }
            }
        }
        if (!calendar && result.count == 0) {
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
        if (!calendar) result.days.forEach { day ->
            item(key = "d" + day.date) {
                Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Strong(dayText(day.date), size = 14.sp, modifier = Modifier.weight(1f))
                    Label(dayTotals(day), size = 12.sp)
                }
            }
            items(day.entries, key = { it.id }) { entry ->
                EntryRow(entry.line(accounts, ledgers), onClick = { onOpen(entry) })
            }
        }
    }
}

@Composable
private fun SplitCell(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier.glassControl(RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Label(label, size = 11.sp)
        Strong(value, size = 16.sp)
        Label(sub, size = 11.sp, weight = FontWeight.Medium)
    }
}

private fun dayTotals(day: EntryDay): String = listOfNotNull(
    day.spentCents.takeIf { it > 0 }?.let { "支出 " + Money.format(it, MainCurrency) },
    day.incomeCents.takeIf { it > 0 }?.let { "收入 " + Money.format(it, MainCurrency) },
).joinToString(" · ")

/** 一个月的格子：花得越多颜色越深；有收入的标绿点，以后的固定收支标紫点 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    days: Map<LocalDate, EntryDay>,
    planned: Map<LocalDate, List<RecurringRule>>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val maxSpent = days.values.maxOfOrNull { it.spentCents }?.takeIf { it > 0 } ?: 1L
    Column(
        Modifier.fillMaxWidth().glassControl(RoundedCornerShape(24.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        CalendarMonth.weeks(month).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(0.82f)) {
                        if (date != null) DayCell(date, days[date], planned[date].orEmpty(), maxSpent, date == today, date == selected, date > today) { onSelect(date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate, day: EntryDay?, rules: List<RecurringRule>, maxSpent: Long,
    isToday: Boolean, isSelected: Boolean, future: Boolean, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val spent = day?.spentCents ?: 0
    // 0.10–0.42 的紫，花得越多越深
    val heat = if (spent > 0) 0.10f + 0.32f * (spent.toFloat() / maxSpent).coerceIn(0f, 1f) else 0f
    val textColor = if (isSelected) Color.White else if (future) Palette.Muted else Palette.Ink
    Column(
        Modifier.fillMaxSize()
            .then(
                when {
                    isSelected -> Modifier.tinted(shape, Palette.Cta)
                    heat > 0 -> Modifier.clip(shape).background(Palette.Violet.copy(alpha = heat))
                    else -> Modifier.clip(shape).background(Color.White.copy(alpha = if (future) 0.18f else 0.34f))
                },
            )
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, Palette.Violet, shape) else Modifier)
            .semantics {
                this.selected = isSelected
                role = Role.Button
                contentDescription = "${date.monthValue}月${date.dayOfMonth}日" +
                    (if (spent > 0) "，支出 " + Money.format(spent, MainCurrency) else "") +
                    (if (rules.isNotEmpty()) "，" + rules.joinToString("、") { it.name } else "")
            }
            .jelly(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(date.dayOfMonth.toString(), fontSize = 13.sp, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold, color = textColor)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (spent > 0) {
                Text(
                    CalendarMonth.compact(spent), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (isSelected || heat > 0.3f) Color.White else Palette.Ink,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if ((day?.incomeCents ?: 0) > 0 || rules.any { it.kind == RuleKind.INCOME }) Dot(if (isSelected) Color.White else Palette.Teal)
            if (rules.any { it.kind != RuleKind.INCOME }) Dot(if (isSelected) Color.White else Palette.Rose)
        }
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(5.dp).clip(CircleShape).background(color))

@Composable
private fun PlannedRow(rule: RecurringRule, account: String?) {
    Row(
        Modifier.fillMaxWidth().glassControl(RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(if (rule.kind == RuleKind.INCOME) Palette.Teal else Palette.Rose)
        Column(Modifier.weight(1f)) {
            Strong(rule.name, size = 15.sp)
            Label(listOfNotNull("固定收支", account, if (rule.auto) "到日子自动记" else "到日子提醒你").joinToString(" · "), size = 12.sp, weight = FontWeight.Medium)
        }
        Strong(
            when (rule.kind) {
                RuleKind.CARD_PAYMENT -> "还全额"
                RuleKind.INCOME -> "+" + Money.format(rule.amountCents, rule.currency)
                RuleKind.EXPENSE -> (if (rule.approximate) "≈ " else "") + Money.format(rule.amountCents, rule.currency)
            },
            size = 15.sp, color = if (rule.kind == RuleKind.INCOME) Palette.Teal else Palette.Ink,
        )
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
