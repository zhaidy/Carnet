package com.personal.carnet.ui.screens

import com.personal.carnet.ui.Partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.data.Account
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Payer
import com.personal.carnet.domain.Categories
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.EntrySearch
import com.personal.carnet.domain.EntryTime
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.domain.Split
import com.personal.carnet.domain.TransferMath
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.LineIcon
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.components.ChipRow
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.GlassField
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.RoundGlassButton
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glass
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.glass.jelly
import com.personal.carnet.ui.glass.tinted
import java.math.BigDecimal
import java.time.LocalDate

private enum class Mode(val label: String) { EXPENSE("支出"), INCOME("收入"), TRANSFER("转账") }

private fun plain(cents: Long): String = BigDecimal.valueOf(cents).movePointLeft(2).setScale(2).toPlainString()

/** 记一笔；editing 不是 null 时是改那一笔（从账单、总览点进来） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(snapshot: Snapshot, vm: MainViewModel, onClose: () -> Unit, onNewAccount: () -> Unit, editing: Entry? = null) {
    val toast = LocalToast.current
    val defaultRate by vm.defaultRate.collectAsState()
    val accounts = snapshot.accounts.map { it.account }
    val ledgers = snapshot.ledgers.map { it.ledger }
    val personal = ledgers.firstOrNull { !it.shared } ?: ledgers.firstOrNull()

    val e = editing
    val spending = e?.takeIf { it.kind != EntryKind.TRANSFER }
    val moving = e?.takeIf { it.kind == EntryKind.TRANSFER }
    var mode by rememberSaveable {
        mutableStateOf(when (e?.kind) { EntryKind.INCOME -> Mode.INCOME; EntryKind.TRANSFER -> Mode.TRANSFER; else -> Mode.EXPENSE })
    }
    var ledgerId by rememberSaveable { mutableStateOf(spending?.ledgerId ?: personal?.id) }
    var payer by rememberSaveable { mutableStateOf(spending?.payer ?: Payer.ME) }
    var accountId by rememberSaveable { mutableStateOf(spending?.accountId ?: accounts.firstOrNull()?.id) }
    var spend by rememberSaveable { mutableStateOf(spending?.currency ?: accounts.firstOrNull()?.currency ?: MainCurrency) }
    var category by rememberSaveable { mutableStateOf(spending?.category ?: "food") }
    var amount by rememberSaveable { mutableStateOf(spending?.let { plain(it.amountCents) } ?: "") }
    var rateText by rememberSaveable { mutableStateOf(spending?.rate?.let { FxRate.format(it) } ?: "") }
    var rateOpen by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf(e?.note ?: "") }
    var day by rememberSaveable { mutableLongStateOf(e?.let { EntrySearch.dateOf(it).toEpochDay() } ?: LocalDate.now().toEpochDay()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var sentence by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var fromId by rememberSaveable { mutableStateOf(moving?.accountId ?: accounts.firstOrNull { it.currency == MainCurrency }?.id ?: accounts.firstOrNull()?.id) }
    var toId by rememberSaveable { mutableStateOf(moving?.toAccountId ?: accounts.firstOrNull { it.currency == Currency.CNY }?.id ?: accounts.getOrNull(1)?.id) }
    var tFrom by rememberSaveable { mutableStateOf(moving?.let { plain(it.accountCents) } ?: "") }
    var tTo by rememberSaveable { mutableStateOf(moving?.takeIf { it.rate != null }?.let { plain(it.toCents) } ?: "") }
    var tRate by rememberSaveable { mutableStateOf(moving?.rate?.let { FxRate.format(it) } ?: "") }
    val date = LocalDate.ofEpochDay(day)
    val time = EntryTime.on(date, e?.timeMillis)

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = it / 86_400_000L }
                    picking = false
                }) { Text("好") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundGlassButton(IconPaths.CLOSE, "关闭", onClick = onClose)
            if (e != null) {
                Text("改这一笔", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f).padding(start = 12.dp))
            } else {
            Spacer(Modifier.weight(1f))
            Row(Modifier.glassControl().padding(4.dp)) {
                Mode.entries.forEach { m ->
                    val on = m == mode
                    Box(
                        Modifier
                            .heightIn(min = 38.dp)
                            .width(64.dp)
                            .then(if (on) Modifier.glassControl() else Modifier)
                            .semantics { selected = on; role = Role.Tab }
                            .jelly {
                                mode = m
                                category = if (m == Mode.INCOME) "salary" else "food"
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(m.label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) Palette.Ink else Palette.Muted) }
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
            }
        }

        if (accounts.isEmpty() || personal == null) {
            Box(Modifier.padding(16.dp)) {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Strong("先加一个账户")
                        Label("记账要知道钱从哪付、进了哪里。", weight = FontWeight.Medium)
                        CtaButton("新建账户", onClick = onNewAccount)
                    }
                }
            }
            return@Column
        }

        if (mode != Mode.TRANSFER) {
            val isIncome = mode == Mode.INCOME
            val ledger = (if (isIncome && e == null) personal else ledgers.firstOrNull { it.id == ledgerId }) ?: personal
            val account = accounts.firstOrNull { it.id == accountId } ?: accounts.first()
            val isGroup = !isIncome && ledger.shared
            val herPaid = isGroup && payer == Payer.HER
            val ledgerCurrency = ledger.currency ?: MainCurrency
            val rate = FxRate.parse(rateText) ?: defaultRate
            val cents = Money.fromInput(amount)
            val cats = if (isIncome) Categories.income else Categories.expense
            val cat = cats.firstOrNull { it.key == category } ?: cats.first()

            val fx = buildList {
                if (!herPaid && account.currency != spend) {
                    add(Money.format(cents, spend) + " → ${account.name} " + (if (isIncome) "进" else "扣") + " ≈ " + Money.format(rate.convert(cents, spend, account.currency), account.currency))
                }
                if (ledger.currency != null && ledger.currency != spend) {
                    add("记进「${ledger.name}」≈ " + Money.format(rate.convert(cents, spend, ledgerCurrency), ledgerCurrency))
                }
            }
            val splitText = if (isGroup) {
                val inLedger = rate.convert(cents, spend, ledgerCurrency)
                val each = Split.myShare(inLedger, ledger.myPercent)
                "「${ledger.name}」" + (if (ledger.myPercent == 50) "平分 · 各 " else "你出 ${ledger.myPercent}% · ") + Money.format(each, ledgerCurrency)
            } else "记到「${ledger.name}」"

            fun save(close: Boolean) {
                if (cents <= 0) { toast.show("先输入金额"); return }
                vm.saveSpending(
                    kind = if (isIncome) EntryKind.INCOME else EntryKind.EXPENSE,
                    ledger = ledger, account = if (herPaid) null else account, payer = payer,
                    category = cat.key, note = note, amountCents = cents, currency = spend, rate = rate,
                    timeMillis = time, editing = e,
                )
                if (e != null) {
                    toast.show("改好了：「${ledger.name}」${Money.format(cents, spend)}")
                    onClose()
                    return
                }
                val msg = buildString {
                    append("已记到「${ledger.name}」${Money.format(cents, spend)}")
                    if (isGroup) {
                        val inLedger = rate.convert(cents, spend, ledgerCurrency)
                        val mine = Split.myShare(inLedger, ledger.myPercent)
                        append(if (herPaid) " · ${Partner.name}帮你垫了 ${Money.format(mine, ledgerCurrency)}" else " · 你多垫了 ${Money.format(inLedger - mine, ledgerCurrency)}")
                    }
                }
                toast.show(msg)
                amount = ""; note = ""; rateText = ""; rateOpen = false
                if (close) onClose()
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (vm.claudeReady) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassField(sentence, { sentence = it }, "说一句：昨晚和${Partner.name}吃火锅 86，我付的", Modifier.weight(1f))
                        Box(
                            Modifier.tinted(RoundedCornerShape(14.dp), Palette.Cta).jelly {
                                if (thinking || sentence.isBlank()) return@jelly
                                thinking = true
                                scope.launch {
                                    try {
                                        val r = vm.understand(sentence, ledgers.map { it.name }, accounts.map { it.name })
                                        mode = if (r.income) Mode.INCOME else Mode.EXPENSE
                                        amount = plain(r.cents)
                                        category = r.category
                                        note = r.note
                                        if (!r.income) {
                                            r.ledger?.let { name -> ledgers.firstOrNull { it.name == name } }?.let { ledgerId = it.id }
                                            payer = if (r.herPaid) Payer.HER else Payer.ME
                                        }
                                        val acc = r.account?.let { name -> accounts.firstOrNull { it.name == name } }
                                        if (acc != null) accountId = acc.id
                                        val chosenLedger = if (r.income) personal else ledgers.firstOrNull { it.id == ledgerId }
                                        spend = r.currency ?: chosenLedger?.currency ?: (acc ?: accounts.firstOrNull { it.id == accountId })?.currency ?: spend
                                        sentence = ""
                                        toast.show("听懂了，看一下对不对再点完成")
                                    } catch (e: Exception) {
                                        toast.show(e.message ?: "没听懂")
                                    } finally {
                                        thinking = false
                                    }
                                }
                            }.padding(horizontal = 14.dp, vertical = 11.dp),
                        ) { Strong(if (thinking) "…" else "听懂", size = 14.sp, color = Color.White) }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().holo(RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Strong(cat.label, size = 16.sp)
                            Label(splitText, size = 12.sp)
                        }
                        Row(
                            Modifier.glassControl(RoundedCornerShape(12.dp))
                                .semantics { contentDescription = "切换币种，现在是${spend.label}"; role = Role.Button }
                                .jelly { spend = if (spend == Currency.CNY) Currency.SGD else Currency.CNY }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Strong(spend.symbol, size = 16.sp)
                            LineIcon(IconPaths.SWAP, Palette.Ink, size = 12.dp, width = 2.2f)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(amount.ifEmpty { "0" }, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    }
                    if (fx.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().glassControl(RoundedCornerShape(16.dp)).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(fx.joinToString("；"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.glassControl(RoundedCornerShape(12.dp)).jelly { rateOpen = !rateOpen }.padding(horizontal = 10.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    "1 S$ = ¥" + FxRate.format(rate.cnyPerSgd) + if (rateText.isNotBlank()) " · 这一笔" else " · 默认",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.Violet,
                                )
                            }
                        }
                        if (rateOpen) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Label("这一笔用 1 S$ = ¥", size = 12.sp)
                                GlassField(rateText, { rateText = it.filter { c -> c.isDigit() || c == '.' } }, FxRate.format(defaultRate.cnyPerSgd), Modifier.weight(1f), decimal = true)
                                Box(Modifier.glassControl(RoundedCornerShape(12.dp)).jelly { rateText = ""; rateOpen = false }.padding(10.dp)) {
                                    Strong("用默认", size = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (!isIncome) {
                    ChipRow(ledgers.map { it.id to (it.name + (it.currency?.let { c -> " " + c.symbol } ?: "")) }, ledger.id, "记到") { id ->
                        ledgerId = id
                        val l = ledgers.first { it.id == id }
                        spend = l.currency ?: account.currency
                    }
                }
                if (isGroup) {
                    ChipRow(listOf(Payer.ME to "我", Payer.HER to "${Partner.name}"), payer, "谁付的") { payer = it }
                }
                if (herPaid) {
                    Label("${Partner.name}付的，不动你的账户，只记${Partner.name}帮你垫了多少", modifier = Modifier.padding(start = 56.dp, top = 6.dp, bottom = 6.dp))
                } else {
                    ChipRow(accounts.map { it.id to (it.name + " " + it.currency.symbol) }, account.id, if (isIncome) "进哪里" else "从哪付") { id ->
                        accountId = id
                        if (isIncome || ledger.currency == null) spend = accounts.first { it.id == id }.currency
                    }
                }
                DateRow(date) { picking = true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("备注", modifier = Modifier.width(56.dp))
                    GlassField(note, { note = it }, "写点什么，可以不写", Modifier.weight(1f))
                }
                CategoryGrid(cats.map { Triple(it.key, it.label, it.icon) }, cat.key) { category = it }
                if (e != null) DeleteEntry(e, vm, onClose)
            }
            Keypad(
                onKey = { k -> amount = typeKey(amount, k) },
                onDelete = { amount = amount.dropLast(1) },
                onAgain = if (e == null) ({ save(close = false) }) else null,
                onDone = { save(close = true) },
            )
        } else {
            val from = accounts.firstOrNull { it.id == fromId } ?: accounts.first()
            val to = accounts.firstOrNull { it.id == toId } ?: accounts.last()
            val cross = from.currency != to.currency

            fun recompute(fromText: String, rateStr: String): String {
                val f = Money.parse(fromText) ?: return ""
                return plain(TransferMath.toAmount(f, from.currency, to.currency, FxRate.parse(rateStr) ?: defaultRate))
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChipRow(accounts.map { it.id to (it.name + " " + it.currency.symbol) }, from.id, "从") { fromId = it; tTo = "" }
                        ChipRow(accounts.map { it.id to (it.name + " " + it.currency.symbol) }, to.id, "到") { toId = it; tTo = "" }
                        Label(
                            when {
                                from.id == to.id -> "转出和转入是同一个账户"
                                !cross && to.type == com.personal.carnet.data.AccountType.CREDIT -> "还信用卡：不算花销，卡上的欠款会减掉"
                                !cross -> "同币种转账：不算花销"
                                else -> "换汇 / 汇款：不算花销，比默认汇率少到的部分算手续费"
                            },
                            size = 12.sp,
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().holo(RoundedCornerShape(28.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AmountLine("转出", from.currency, tFrom, "0.00") { v ->
                        tFrom = v
                        if (cross) tTo = recompute(v, tRate)
                    }
                    if (cross) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label("汇率", modifier = Modifier.width(40.dp), weight = FontWeight.Bold)
                            Strong("1 S$ = ¥", size = 13.sp)
                            GlassField(tRate, { v ->
                                tRate = v
                                tTo = recompute(tFrom, v)
                            }, FxRate.format(defaultRate.cnyPerSgd) + " 默认", Modifier.weight(1f), decimal = true)
                        }
                        AmountLine("到账", to.currency, tTo, "0.00") { v ->
                            tTo = v
                            val implied = TransferMath.impliedRate(Money.parse(tFrom) ?: 0, from.currency, Money.parse(v) ?: 0, to.currency)
                            if (implied != null) tRate = FxRate.format(implied.cnyPerSgd)
                        }
                        val f = Money.parse(tFrom) ?: 0
                        val t = Money.parse(tTo) ?: 0
                        val hint = if (f > 0 && t > 0) {
                            val diff = TransferMath.differenceFromDefault(f, from.currency, t, to.currency, defaultRate)
                            val implied = TransferMath.impliedRate(f, from.currency, t, to.currency)
                            "实际汇率 1 S$ = ¥" + FxRate.format(implied?.cnyPerSgd ?: 0.0) + "。" +
                                if (diff == 0L) "和默认汇率一样。"
                                else "按默认汇率应到 " + Money.format(t - diff, to.currency) + "，实际" + (if (diff > 0) "多" else "少") + "到 " + Money.format(kotlin.math.abs(diff), to.currency) + "。"
                        } else "填转出和到账，或者填转出和汇率"
                        Text(hint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink, modifier = Modifier.fillMaxWidth().glassControl(RoundedCornerShape(14.dp)).padding(10.dp))
                    }
                }
                Label("三个数填任意两个就行：填了转出和到账，自动算出实际汇率；填了转出，用你填的汇率或者默认汇率算出到账。", size = 12.sp, weight = FontWeight.Medium)
                DateRow(date) { picking = true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("备注", modifier = Modifier.width(56.dp))
                    GlassField(note, { note = it }, "比如 汇款回国", Modifier.weight(1f))
                }
                if (e != null) DeleteEntry(e, vm, onClose)
            }
            Box(Modifier.padding(16.dp).navigationBarsPadding()) {
                CtaButton(if (e != null) "保存" else "记下转账") {
                    val f = Money.parse(tFrom) ?: 0
                    val t = if (cross) Money.parse(tTo) ?: 0 else f
                    when {
                        from.id == to.id -> toast.show("转出和转入不能是同一个账户")
                        f <= 0 -> toast.show("先填转出金额")
                        t <= 0 -> toast.show("再填到账金额或者汇率")
                        else -> {
                            vm.saveTransfer(from, to, f, t, if (cross) TransferMath.impliedRate(f, from.currency, t, to.currency) else null, note, time, e)
                            toast.show((if (e != null) "改好了：" else "已记下：") + "${from.name} ${Money.format(f, from.currency)} → ${to.name} ${Money.format(t, to.currency)}")
                            onClose()
                        }
                    }
                }
            }
        }
    }
}

/** 删掉这一笔：放在改账页最底下的「更多操作」里 */
@Composable
private fun DeleteEntry(e: Entry, vm: MainViewModel, onClose: () -> Unit) {
    val toast = LocalToast.current
    com.personal.carnet.ui.components.DangerZone {
        com.personal.carnet.ui.components.DangerAction(
            title = "删掉这一笔",
            question = "删掉这一笔？",
            detail = if (e.syncId != null) "${Partner.name}那边也会一起删掉。" else "删掉后账户余额会跟着变回去。",
            confirmText = "删掉",
        ) {
            vm.deleteEntry(e.id)
            toast.show("已删掉这一笔")
            onClose()
        }
    }
}

private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 「今天 10月31日」「昨天 10月30日」「10月28日 周三」 */
fun dayText(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val md = "${date.monthValue}月${date.dayOfMonth}日"
    val withYear = if (date.year != today.year) "${date.year}年$md" else md
    return when (date) {
        today -> "今天 $md"
        today.minusDays(1) -> "昨天 $md"
        else -> withYear + " " + weekdayNames[date.dayOfWeek.value - 1]
    }
}

@Composable
private fun DateRow(date: LocalDate, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("日期", modifier = Modifier.width(56.dp))
        Row(
            Modifier.glassControl(RoundedCornerShape(14.dp)).semantics { role = Role.Button }.jelly(onClick = onPick).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LineIcon(IconPaths.CALENDAR, Palette.Ink, size = 16.dp)
            Strong(dayText(date), size = 14.sp)
        }
    }
}

private fun typeKey(current: String, key: String): String {
    if (key == ".") return if (current.contains('.')) current else current.ifEmpty { "0" } + "."
    val parts = current.split('.')
    if (parts.size == 2 && parts[1].length >= 2) return current
    if (parts.size == 1 && parts[0].length >= 7) return current
    return if (current == "0") key else current + key
}

@Composable
private fun AmountLine(label: String, currency: Currency, value: String, placeholder: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(label, modifier = Modifier.width(40.dp), weight = FontWeight.Bold)
        Strong(currency.symbol, size = 17.sp, modifier = Modifier.width(28.dp))
        GlassField(value, { onChange(it.filter { c -> c.isDigit() || c == '.' }) }, placeholder, Modifier.weight(1f), decimal = true, fontSize = 22.sp)
    }
}

@Composable
fun CategoryGrid(items: List<Triple<String, String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (key, label, icon) ->
                    val on = key == selected
                    Column(
                        Modifier.weight(1f).semantics { this.selected = on; role = Role.RadioButton }.jelly { onSelect(key) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier.size(46.dp).then(if (on) Modifier.tinted(RoundedCornerShape(16.dp), Palette.Cta) else Modifier.glassControl(RoundedCornerShape(16.dp))),
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(icon, if (on) Color.White else Palette.Ink, size = 21.dp) }
                        Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) Palette.Ink else Palette.Muted)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit, onDelete: () -> Unit, onAgain: (() -> Unit)?, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(8.dp)
            .glass(RoundedCornerShape(32.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "00")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Key(k, Modifier.weight(1f)) {
                            if (k == "00") { onKey("0"); onKey("0") } else onKey(k)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(50.dp).glassControl(RoundedCornerShape(18.dp)).semantics { contentDescription = "删除" }.jelly(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { LineIcon(IconPaths.DELETE, Palette.Ink, size = 24.dp) }
            if (onAgain != null) {
                Box(
                    Modifier.fillMaxWidth().height(50.dp).glassControl(RoundedCornerShape(18.dp)).jelly(onClick = onAgain),
                    contentAlignment = Alignment.Center,
                ) { Strong("再记", size = 14.sp) }
            }
            Box(
                Modifier.fillMaxWidth().height(if (onAgain != null) 108.dp else 166.dp).tinted(RoundedCornerShape(18.dp), Palette.Cta).jelly(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) { Strong(if (onAgain != null) "完成" else "保存", size = 17.sp, color = Color.White) }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).glassControl(RoundedCornerShape(18.dp)).jelly(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
}
