package com.personal.carnet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.Cycle
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import com.personal.carnet.domain.Categories
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Schedule
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.components.ChipRow
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.GlassField
import com.personal.carnet.ui.components.GlassSwitch
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.ScreenHeader
import com.personal.carnet.ui.components.Stepper
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.holo
import java.time.LocalDate

private enum class RulePreset(val label: String) { SALARY("工资"), RENT("房租"), CARD("还信用卡"), SUB("订阅"), PHONE("话费"), OTHER("其他") }

@Composable
fun RecurringEditScreen(snapshot: Snapshot, vm: MainViewModel, onDone: () -> Unit) {
    val toast = LocalToast.current
    val accounts = snapshot.accounts.map { it.account }
    val cards = accounts.filter { it.type == AccountType.CREDIT }
    val payFrom = accounts.filter { it.type != AccountType.CREDIT }
    val ledgers = snapshot.ledgers.map { it.ledger }
    val personal = ledgers.firstOrNull { !it.shared }

    var preset by rememberSaveable { mutableStateOf(RulePreset.SALARY) }
    var name by rememberSaveable { mutableStateOf("工资") }
    var kind by rememberSaveable { mutableStateOf(RuleKind.INCOME) }
    var category by rememberSaveable { mutableStateOf("salary") }
    var amount by rememberSaveable { mutableStateOf("") }
    var approximate by rememberSaveable { mutableStateOf(false) }
    var currency by rememberSaveable { mutableStateOf(MainCurrency) }
    var cycle by rememberSaveable { mutableStateOf(Cycle.MONTHLY) }
    var day by rememberSaveable { mutableIntStateOf(26) }
    var weekday by rememberSaveable { mutableIntStateOf(1) }
    var month by rememberSaveable { mutableIntStateOf(1) }
    var shift by rememberSaveable { mutableStateOf(true) }
    var accountId by rememberSaveable { mutableStateOf(payFrom.firstOrNull()?.id) }
    var cardId by rememberSaveable { mutableStateOf(cards.firstOrNull()?.id) }
    var ledgerId by rememberSaveable { mutableStateOf(personal?.id) }
    var auto by rememberSaveable { mutableStateOf(true) }

    fun apply(p: RulePreset) {
        preset = p
        cycle = Cycle.MONTHLY
        approximate = false
        when (p) {
            RulePreset.SALARY -> { name = "工资"; kind = RuleKind.INCOME; category = "salary"; day = 26; shift = true; auto = true; accountId = payFrom.firstOrNull()?.id }
            RulePreset.RENT -> { name = "房租"; kind = RuleKind.EXPENSE; category = "home"; day = 20; shift = false; auto = false; accountId = payFrom.firstOrNull()?.id }
            RulePreset.CARD -> {
                kind = RuleKind.CARD_PAYMENT; category = "card_payment"; shift = false; auto = true
                val card = cards.firstOrNull { it.id == cardId } ?: cards.firstOrNull()
                cardId = card?.id
                day = card?.dueDay ?: 13
                name = "还" + (card?.name ?: "信用卡")
                accountId = payFrom.firstOrNull()?.id
            }
            RulePreset.SUB -> { name = ""; kind = RuleKind.EXPENSE; category = "sub"; day = 1; shift = false; auto = true; accountId = cards.firstOrNull()?.id ?: payFrom.firstOrNull()?.id }
            RulePreset.PHONE -> { name = "话费"; kind = RuleKind.EXPENSE; category = "phone"; day = 5; shift = false; auto = true; approximate = true; accountId = cards.firstOrNull()?.id ?: payFrom.firstOrNull()?.id }
            RulePreset.OTHER -> { name = ""; kind = RuleKind.EXPENSE; category = "more"; day = 1; shift = false; auto = true }
        }
        if (kind != RuleKind.EXPENSE) ledgerId = personal?.id
    }

    val cents = Money.fromInput(amount)
    val dayValue = when (cycle) {
        Cycle.MONTHLY -> day
        Cycle.WEEKLY -> weekday
        Cycle.YEARLY -> month * 100 + day.coerceAtMost(31)
    }
    val draft = RecurringRule(
        name = name.trim(), kind = kind, category = category, amountCents = cents, approximate = approximate,
        currency = currency, cycle = cycle, day = dayValue, shiftWeekend = shift,
        accountId = if (kind == RuleKind.EXPENSE || kind == RuleKind.INCOME || kind == RuleKind.CARD_PAYMENT) accountId else null,
        toAccountId = if (kind == RuleKind.CARD_PAYMENT) cardId else null,
        ledgerId = if (kind == RuleKind.EXPENSE) ledgerId else null,
        auto = auto,
        lastDoneEpochDay = LocalDate.now().toEpochDay(),
    )
    val next = Schedule.next(draft, LocalDate.now().plusDays(1))
    val ledger = ledgers.firstOrNull { it.id == ledgerId }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("新建固定收支", IconPaths.CLOSE, onBack = onDone)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChipRow(RulePreset.entries.map { it to it.label }, preset, "是什么") { apply(it) }

            Column(Modifier.fillMaxWidth().holo(RoundedCornerShape(26.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Strong(name.ifBlank { "新的固定收支" }, size = 18.sp)
                Label(
                    buildString {
                        append("下次 ${next.monthValue}/${next.dayOfMonth}")
                        when (kind) {
                            RuleKind.CARD_PAYMENT -> append(" 从${accounts.firstOrNull { it.id == accountId }?.name ?: "?"}还${cards.firstOrNull { it.id == cardId }?.name ?: "?"}，按卡上欠的全额")
                            RuleKind.INCOME -> append(" 进${accounts.firstOrNull { it.id == accountId }?.name ?: "?"} " + Money.format(cents, currency))
                            RuleKind.EXPENSE -> {
                                append(" 从${accounts.firstOrNull { it.id == accountId }?.name ?: "?"}扣 " + (if (approximate) "约 " else "") + Money.format(cents, currency))
                                if (ledger?.shared == true) append("，记到「${ledger.name}」平分")
                            }
                        }
                        append(if (auto) "，到日子自动记一笔。" else "，到日子先在总览里提醒你。")
                        if (approximate) append("金额会变的话，记下后可以删掉重记。")
                    },
                    size = 13.sp, weight = FontWeight.Medium,
                )
            }

            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("名称", modifier = Modifier.width(56.dp))
                        GlassField(name, { name = it }, "比如 Netflix", Modifier.weight(1f))
                    }
                    ChipRow(RuleKind.entries.map { it to it.label }, kind, "类型") {
                        kind = it
                        category = when (it) {
                            RuleKind.INCOME -> "salary"
                            RuleKind.CARD_PAYMENT -> "card_payment"
                            RuleKind.EXPENSE -> if (category == "salary" || category == "card_payment") "more" else category
                        }
                    }
                    if (kind != RuleKind.CARD_PAYMENT) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label("金额", modifier = Modifier.width(56.dp))
                            Strong(currency.symbol, size = 16.sp)
                            GlassField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "0.00", Modifier.weight(1f), decimal = true, fontSize = 17.sp)
                        }
                        ChipRow(Currency.entries.map { it to it.chip() }, currency, "币种") { currency = it }
                        ChipRow(listOf(false to "固定", true to "大约"), approximate, "金额") { approximate = it }
                    }
                }
            }

            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipRow(Cycle.entries.map { it to it.label }, cycle, "多久一次") { cycle = it }
                    when (cycle) {
                        Cycle.MONTHLY -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("每月哪天", modifier = Modifier.weight(1f))
                            Stepper(if (day >= 31) "月底" else "$day 号", "哪天", onMinus = { day = if (day <= 1) 31 else day - 1 }, onPlus = { day = if (day >= 31) 1 else day + 1 })
                        }
                        Cycle.WEEKLY -> ChipRow(listOf("一", "二", "三", "四", "五", "六", "日").mapIndexed { i, s -> (i + 1) to "周$s" }, weekday, "每周") { weekday = it }
                        Cycle.YEARLY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Label("几月", modifier = Modifier.weight(1f))
                                Stepper("$month 月", "月份", onMinus = { month = if (month <= 1) 12 else month - 1 }, onPlus = { month = if (month >= 12) 1 else month + 1 })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Label("几号", modifier = Modifier.weight(1f))
                                Stepper("$day 号", "日子", onMinus = { day = if (day <= 1) 31 else day - 1 }, onPlus = { day = if (day >= 31) 1 else day + 1 })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Strong("碰上周末提前到周五", size = 13.sp)
                            Label("工资通常是这样", size = 12.sp, weight = FontWeight.Medium)
                        }
                        GlassSwitch(shift, "碰上周末提前") { shift = it }
                    }
                }
            }

            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val fromLabel = when (kind) { RuleKind.INCOME -> "进哪里"; RuleKind.CARD_PAYMENT -> "从哪还"; RuleKind.EXPENSE -> "从哪扣" }
                    val fromOptions = if (kind == RuleKind.CARD_PAYMENT) payFrom else accounts
                    if (fromOptions.isEmpty()) Label("先去账户页加一个账户", weight = FontWeight.Medium)
                    else ChipRow(fromOptions.map { it.id to (it.name + " " + it.currency.symbol) }, accountId, fromLabel) { id ->
                        accountId = id
                        if (kind != RuleKind.CARD_PAYMENT) currency = accounts.first { it.id == id }.currency
                    }
                    if (kind == RuleKind.CARD_PAYMENT) {
                        if (cards.isEmpty()) Label("还没有信用卡账户，先去账户页加", weight = FontWeight.Medium)
                        else ChipRow(cards.map { it.id to it.name }, cardId, "还哪张卡") { id ->
                            cardId = id
                            val card = cards.first { it.id == id }
                            name = "还" + card.name
                            card.dueDay?.let { day = it }
                        }
                    }
                    if (kind == RuleKind.EXPENSE) {
                        ChipRow(ledgers.map { it.id to it.name }, ledgerId, "记到") { ledgerId = it }
                        CategoryGrid(Categories.expense.map { Triple(it.key, it.label, it.icon) }, category) { category = it }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Strong(if (auto) "到日子自动记一笔" else "到日子先提醒我", size = 13.sp)
                            Label(if (auto) "打开 Carnet 时补记到日子的" else "在总览里点「记下」才记", size = 12.sp, weight = FontWeight.Medium)
                        }
                        GlassSwitch(auto, "自动记") { auto = it }
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp).navigationBarsPadding()) {
            CtaButton("保存", sub = "从下一次（${next.monthValue}/${next.dayOfMonth}）开始") {
                when {
                    name.isBlank() -> toast.show("先起个名字")
                    kind != RuleKind.CARD_PAYMENT && cents <= 0 -> toast.show("先填金额")
                    draft.accountId == null -> toast.show("先选账户")
                    kind == RuleKind.CARD_PAYMENT && cardId == null -> toast.show("先选还哪张卡")
                    else -> {
                        vm.addRule(if (kind == RuleKind.CARD_PAYMENT) draft.copy(currency = cards.first { it.id == cardId }.currency) else draft)
                        toast.show("已建好「${name.trim()}」· 下次 ${next.monthValue}/${next.dayOfMonth}")
                        onDone()
                    }
                }
            }
        }
    }
}
