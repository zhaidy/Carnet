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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.AccountView
import com.personal.carnet.domain.Money
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.jelly
import kotlinx.coroutines.launch
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

/** 新建账户；editing 不是 null 时是改这个账户（币种不能改） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(vm: MainViewModel, onDone: () -> Unit, editing: AccountView? = null, onReconcile: (() -> Unit)? = null) {
    val toast = LocalToast.current
    val defaultRate by vm.defaultRate.collectAsState()
    val old = editing?.account
    val oldShown = editing?.let { if (it.account.type == AccountType.CREDIT) -it.balanceCents else it.balanceCents }
    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(old?.type ?: AccountType.DEBIT) }
    var currency by rememberSaveable { mutableStateOf(old?.currency ?: MainCurrency) }
    var balance by rememberSaveable { mutableStateOf(oldShown?.let { java.math.BigDecimal.valueOf(it).movePointLeft(2).setScale(2).toPlainString() } ?: "") }
    var dueDay by rememberSaveable { mutableIntStateOf(old?.dueDay ?: 13) }
    // 0 是没设
    var statementDay by rememberSaveable { mutableIntStateOf(old?.statementDay ?: 0) }
    // 「按账算」：哪天（含）之前刷的都还清了
    var calcOpen by rememberSaveable { mutableStateOf(false) }
    var clearedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingCleared by rememberSaveable { mutableStateOf(false) }
    val snapshot by vm.snapshot.collectAsState()
    var rateText by rememberSaveable { mutableStateOf(FxRate.format(defaultRate.cnyPerSgd)) }
    var cashflow by rememberSaveable { mutableStateOf(old?.inCashflow ?: true) }
    var inUse by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    if (old != null) LaunchedEffect(old.id) { inUse = vm.accountInUse(old.id) }

    fun applyDefaults(t: AccountType, c: Currency) {
        type = t
        currency = c
        if (old == null) cashflow = c == MainCurrency && t != AccountType.CREDIT
    }

    val credit = type == AccountType.CREDIT
    val cents = Money.fromInput(balance)
    val foreign = currency != MainCurrency && old == null
    val rate = FxRate.parse(rateText)

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(if (old != null) "改账户" else "新建账户", IconPaths.CLOSE, onBack = onDone, modifier = Modifier.padding(horizontal = 16.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (old == null) ChipRow(
                listOf(
                    Triple("工行卡", AccountType.DEBIT, Currency.CNY),
                    Triple("支付宝", AccountType.WALLET, Currency.CNY),
                    Triple("微信", AccountType.WALLET, Currency.CNY),
                    Triple("GrabPay", AccountType.WALLET, Currency.SGD),
                ).map { it to it.first },
                selected = null,
                label = "快速添加",
            ) { (n, t, c) ->
                name = n
                applyDefaults(t, c)
            }

            Column(
                Modifier.fillMaxWidth().holo(RoundedCornerShape(26.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Strong(name.ifBlank { "新账户" }, size = 17.sp, modifier = Modifier.weight(1f))
                    Label(type.label + " · " + currency.label, size = 12.sp, weight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Label(if (credit) "现在欠" else "现在有", size = 12.sp)
                        Strong(Money.format(cents, currency), size = 26.sp)
                    }
                    if (currency != MainCurrency) Label("≈ " + Money.format((rate ?: defaultRate).convert(cents, currency, MainCurrency), MainCurrency), weight = FontWeight.Bold, size = 12.sp)
                }
            }

            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("名称", modifier = Modifier.width(56.dp))
                        GlassField(name, { name = it }, "比如 工行卡", Modifier.weight(1f))
                    }
                    ChipRow(AccountType.entries.map { it to it.label }, type, "类型") { applyDefaults(it, currency) }
                    if (old == null) ChipRow(Currency.entries.map { it to it.chip() }, currency, "币种") { applyDefaults(type, it) }
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("币种", modifier = Modifier.width(56.dp))
                        Label(currency.chip() + " · 记过账后不能改", size = 13.sp, weight = FontWeight.Medium)
                    }
                }
            }

            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label(if (credit) "现在欠" else "现在有", modifier = Modifier.width(56.dp))
                        Strong(currency.symbol, size = 16.sp)
                        GlassField(balance, { balance = it.filter { c -> c.isDigit() || c == '.' } }, "0.00", Modifier.weight(1f), decimal = true, fontSize = 17.sp)
                    }
                    if (old != null) Label("和实际对不上就改成实际的数，以前记的账不动", size = 12.sp, weight = FontWeight.Medium, modifier = Modifier.padding(start = 64.dp))
                    if (old != null && credit) {
                        val today = java.time.LocalDate.now()
                        val cleared = clearedDay?.let(java.time.LocalDate::ofEpochDay)
                            ?: com.personal.carnet.domain.Cashflow.lastStatement(statementDay.takeIf { it > 0 }, today)
                            ?: today.withDayOfMonth(1).minusDays(1)
                        if (pickingCleared) {
                            val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = cleared.toEpochDay() * 86_400_000L)
                            androidx.compose.material3.DatePickerDialog(
                                onDismissRequest = { pickingCleared = false },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(onClick = {
                                        state.selectedDateMillis?.let { clearedDay = it / 86_400_000L }
                                        pickingCleared = false
                                    }) { androidx.compose.material3.Text("好") }
                                },
                                dismissButton = { androidx.compose.material3.TextButton(onClick = { pickingCleared = false }) { androidx.compose.material3.Text("取消") } },
                            ) { androidx.compose.material3.DatePicker(state) }
                        }
                        if (!calcOpen) {
                            Row(Modifier.padding(start = 64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.glassControl(RoundedCornerShape(14.dp)).jelly { calcOpen = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                                ) { Strong("按账算", size = 13.sp, color = Palette.Violet) }
                                if (onReconcile != null) Box(
                                    Modifier.glassControl(RoundedCornerShape(14.dp)).jelly(onClick = onReconcile).padding(horizontal = 14.dp, vertical = 9.dp),
                                ) { Strong("和账单对账", size = 13.sp, color = Palette.Violet) }
                            }
                        } else {
                            val entries = snapshot?.entries.orEmpty()
                            val owed = com.personal.carnet.domain.Cashflow.owedSince(old.id, entries, cleared)
                            Column(
                                Modifier.fillMaxWidth().glassControl(RoundedCornerShape(16.dp)).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Label("还清到", modifier = Modifier.width(56.dp))
                                    Box(Modifier.glassControl(RoundedCornerShape(12.dp)).jelly { pickingCleared = true }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Strong("${cleared.monthValue}月${cleared.dayOfMonth}日", size = 14.sp)
                                    }
                                }
                                Label(
                                    "这天（含）之前刷的都已经还清了：上期账单还了的话，选上期的账单日。" +
                                        "现在欠的 = 之后在这张卡上刷的，减掉退款。",
                                    size = 12.sp, weight = FontWeight.Medium,
                                )
                                Strong("算出来现在欠 " + Money.format(owed.coerceAtLeast(0), currency), size = 15.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        Modifier.weight(1f).glassControl(RoundedCornerShape(14.dp)).jelly {
                                            balance = java.math.BigDecimal.valueOf(owed.coerceAtLeast(0)).movePointLeft(2).setScale(2).toPlainString()
                                            calcOpen = false
                                            toast.show("填好了，点保存生效")
                                        }.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Strong("填进现在欠", size = 13.sp, color = Palette.Violet) }
                                    Box(
                                        Modifier.weight(1f).glassControl(RoundedCornerShape(14.dp)).jelly { calcOpen = false }.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Strong("算了", size = 13.sp) }
                                }
                            }
                        }
                    }
                    if (credit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label("还款日")
                                Label("每月还全额账单", size = 12.sp, weight = FontWeight.Medium)
                            }
                            Stepper("$dueDay 号", "还款日", onMinus = { dueDay = if (dueDay <= 1) 31 else dueDay - 1 }, onPlus = { dueDay = if (dueDay >= 31) 1 else dueDay + 1 })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label("账单日")
                                Label(
                                    if (statementDay == 0) "没设：还卡按卡上现在欠的全部算" else "还卡只还到账单日为止刷的，之后刷的算下一期",
                                    size = 12.sp, weight = FontWeight.Medium,
                                )
                            }
                            Stepper(
                                if (statementDay == 0) "没设" else "$statementDay 号", "账单日",
                                onMinus = { statementDay = if (statementDay <= 0) 31 else statementDay - 1 },
                                onPlus = { statementDay = if (statementDay >= 31) 0 else statementDay + 1 },
                            )
                        }
                        Label("账单日在月结单最上面（Statement Date），或者银行 App 的信用卡账单里", size = 11.sp, weight = FontWeight.Medium)
                    }
                    if (foreign) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Label("默认汇率", modifier = Modifier.width(56.dp))
                                Strong("1 S$ = ¥", size = 14.sp)
                                GlassField(rateText, { rateText = it.filter { c -> c.isDigit() || c == '.' } }, "5.30", Modifier.weight(1f), decimal = true, fontSize = 17.sp)
                            }
                            Label("你自己定，所有人民币账户共用；记账、转账时每一笔还能单独改", size = 12.sp, weight = FontWeight.Medium, modifier = Modifier.padding(start = 64.dp))
                        }
                    }
                    // 信用卡不算：刷卡的钱按还款日从储蓄卡里扣
                    if (!credit) Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Strong("算进「发薪前还能花」", size = 13.sp)
                            Label(
                                when {
                                    cashflow -> "这里的钱会被当作发工资前能花的"
                                    foreign -> "国内的钱默认不算，净资产照样算进去"
                                    credit -> "信用卡的欠款另外按还款日扣"
                                    else -> "不算这里的钱"
                                },
                                size = 12.sp, weight = FontWeight.Medium,
                            )
                        }
                        GlassSwitch(cashflow, "算进发薪前还能花") { cashflow = it }
                    }
                }
            }
            if (old != null) {
                com.personal.carnet.ui.components.DangerZone {
                    com.personal.carnet.ui.components.DangerAction(
                        title = "删除这个账户",
                        question = "删掉「${old.name}」？",
                        confirmText = "删掉",
                        blocked = if (inUse) "已经有账或者固定收支用到它了。不想再用的话，可以把余额改成 0、关掉「算进发薪前还能花」。" else null,
                    ) {
                        scope.launch {
                            if (vm.deleteAccount(old.id)) { toast.show("删掉了「${old.name}」"); onDone() }
                            else { inUse = true; toast.show("这个账户已经有账了，删不了") }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CtaButton(if (old != null) "保存" else "添加账户", Modifier.weight(1f)) {
                when {
                    name.isBlank() -> toast.show("先给账户起个名字")
                    foreign && rate == null -> toast.show("默认汇率要是大于 0 的数")
                    old != null && editing != null -> {
                        // 改的是「现在的余额」：差多少就把建账户时的余额挪多少，以前的账不动
                        val target = if (credit) -cents else cents
                        vm.updateAccount(
                            old.copy(
                                name = name.trim(), type = type,
                                openingCents = old.openingCents + (target - editing.balanceCents),
                                dueDay = if (credit) dueDay else null,
                                statementDay = if (credit && statementDay > 0) statementDay else null,
                                inCashflow = cashflow && !credit,
                            ),
                        )
                        toast.show("改好了「${name.trim()}」")
                        onDone()
                    }
                    else -> {
                        if (foreign && rate != null) vm.setDefaultRate(rate)
                        vm.addAccount(
                            Account(
                                name = name.trim(), type = type, currency = currency,
                                openingCents = if (credit) -cents else cents,
                                dueDay = if (credit) dueDay else null,
                                statementDay = if (credit && statementDay > 0) statementDay else null,
                                inCashflow = cashflow && !credit,
                            ),
                        )
                        toast.show("已添加「${name.trim()}」· " + Money.format(cents, currency))
                        onDone()
                    }
                }
            }
        }
    }
}
