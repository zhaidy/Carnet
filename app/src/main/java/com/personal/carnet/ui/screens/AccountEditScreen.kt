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
import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
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

@Composable
fun AccountEditScreen(vm: MainViewModel, onDone: () -> Unit) {
    val toast = LocalToast.current
    val defaultRate by vm.defaultRate.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AccountType.DEBIT) }
    var currency by rememberSaveable { mutableStateOf(MainCurrency) }
    var balance by rememberSaveable { mutableStateOf("") }
    var dueDay by rememberSaveable { mutableIntStateOf(13) }
    var rateText by rememberSaveable { mutableStateOf(FxRate.format(defaultRate.cnyPerSgd)) }
    var cashflow by rememberSaveable { mutableStateOf(true) }

    fun applyDefaults(t: AccountType, c: Currency) {
        type = t
        currency = c
        cashflow = c == MainCurrency && t != AccountType.CREDIT
    }

    val credit = type == AccountType.CREDIT
    val cents = Money.fromInput(balance)
    val foreign = currency != MainCurrency
    val rate = FxRate.parse(rateText)

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("新建账户", IconPaths.CLOSE, onBack = onDone)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChipRow(
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
                    if (foreign && rate != null) Label("≈ " + Money.format(rate.convert(cents, currency, MainCurrency), MainCurrency), weight = FontWeight.Bold, size = 12.sp)
                }
            }

            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("名称", modifier = Modifier.width(56.dp))
                        GlassField(name, { name = it }, "比如 工行卡", Modifier.weight(1f))
                    }
                    ChipRow(AccountType.entries.map { it to it.label }, type, "类型") { applyDefaults(it, currency) }
                    ChipRow(Currency.entries.map { it to it.chip() }, currency, "币种") { applyDefaults(type, it) }
                }
            }

            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label(if (credit) "现在欠" else "现在有", modifier = Modifier.width(56.dp))
                        Strong(currency.symbol, size = 16.sp)
                        GlassField(balance, { balance = it.filter { c -> c.isDigit() || c == '.' } }, "0.00", Modifier.weight(1f), decimal = true, fontSize = 17.sp)
                    }
                    if (credit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label("还款日")
                                Label("每月还全额账单", size = 12.sp, weight = FontWeight.Medium)
                            }
                            Stepper("$dueDay 号", "还款日", onMinus = { dueDay = if (dueDay <= 1) 31 else dueDay - 1 }, onPlus = { dueDay = if (dueDay >= 31) 1 else dueDay + 1 })
                        }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
        }
        Box(Modifier.padding(16.dp).navigationBarsPadding()) {
            CtaButton("添加账户") {
                when {
                    name.isBlank() -> toast.show("先给账户起个名字")
                    foreign && rate == null -> toast.show("默认汇率要是大于 0 的数")
                    else -> {
                        if (foreign && rate != null) vm.setDefaultRate(rate)
                        vm.addAccount(
                            Account(
                                name = name.trim(), type = type, currency = currency,
                                openingCents = if (credit) -cents else cents,
                                dueDay = if (credit) dueDay else null,
                                inCashflow = cashflow,
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
