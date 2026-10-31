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
import androidx.compose.foundation.layout.size
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
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.SettleMode
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.components.Avatar
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
import com.personal.carnet.ui.epochDayText
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.glass.jelly
import java.time.LocalDate

private enum class Preset(val label: String) { TRIP("旅行"), RENT("合租"), MEAL("聚餐"), BLANK("空白") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerEditScreen(vm: MainViewModel, onDone: () -> Unit) {
    val toast = LocalToast.current
    var preset by rememberSaveable { mutableStateOf(Preset.TRIP) }
    var name by rememberSaveable { mutableStateOf("回国旅行") }
    var currency by rememberSaveable { mutableStateOf(Currency.CNY) }
    var withHer by rememberSaveable { mutableStateOf(true) }
    var ratio by rememberSaveable { mutableStateOf(false) }
    var myPercent by rememberSaveable { mutableIntStateOf(50) }
    var mode by rememberSaveable { mutableStateOf(SettleMode.DATE) }
    var day by rememberSaveable { mutableIntStateOf(31) }
    var epochDay by rememberSaveable { mutableStateOf<Long?>(LocalDate.now().plusMonths(1).toEpochDay()) }
    var picking by rememberSaveable { mutableStateOf(false) }

    fun applyPreset(p: Preset) {
        preset = p
        when (p) {
            Preset.TRIP -> { name = "回国旅行"; currency = Currency.CNY; mode = SettleMode.DATE; ratio = false }
            Preset.RENT -> { name = "合租"; currency = MainCurrency; mode = SettleMode.MONTHLY; day = 1; ratio = true }
            Preset.MEAL -> { name = "朋友聚餐"; currency = MainCurrency; mode = SettleMode.MANUAL; ratio = false }
            Preset.BLANK -> { name = ""; currency = MainCurrency; mode = SettleMode.MONTHLY; day = 31; ratio = false }
        }
    }

    val dayLabel = if (day == 31) "月底" else "$day 号"
    val rate = vm.defaultRate.value
    val summary = buildString {
        append(if (withHer) "你和她 · " else "只有你 · ")
        if (withHer) {
            append(if (!ratio) "平分" else "我 $myPercent% · 她 ${100 - myPercent}%")
            append(
                when (mode) {
                    SettleMode.MONTHLY -> " · 每月${if (day == 31) "月底" else "${day}号"}结"
                    SettleMode.DATE -> " · " + (epochDay?.let(::epochDayText) ?: "还没选日期") + " 结一次"
                    SettleMode.MANUAL -> " · 想结就结"
                },
            )
        } else append("不用分")
    }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDay?.let { it * 86_400_000L })
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { epochDay = it / 86_400_000L }
                    picking = false
                }) { Text("好") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("新建账本", IconPaths.CLOSE, onBack = onDone)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChipRow(Preset.entries.map { it to it.label }, preset, "用来") { applyPreset(it) }

            Column(Modifier.fillMaxWidth().holo(RoundedCornerShape(26.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Strong(name.ifBlank { "新账本" }, size = 20.sp, modifier = Modifier.weight(1f))
                    Label(currency.symbol + " " + currency.label, weight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(width = if (withHer) 50.dp else 32.dp, height = 32.dp)) {
                        Avatar("我", her = false)
                        if (withHer) Box(Modifier.padding(start = 18.dp)) { Avatar("她", her = true) }
                    }
                    Label(summary, size = 13.sp, color = Palette.Ink)
                }
            }

            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("名称", modifier = Modifier.width(56.dp))
                        GlassField(name, { name = it }, "比如 回国旅行", Modifier.weight(1f))
                    }
                    ChipRow(Currency.entries.map { it to it.chip() }, currency, "账本货币") { currency = it }
                    val other = if (currency == Currency.CNY) "新币卡" else "支付宝、微信"
                    Label(
                        "这个账本按${currency.label}记、按${currency.label}结。用${other}付的，按默认汇率 1 S$ = ¥${FxRate.format(rate.cnyPerSgd)} 换算；每一笔还能单独改汇率。",
                        size = 12.sp, weight = FontWeight.Medium,
                    )
                }
            }

            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Strong("和她一起", size = 14.sp)
                            Label(if (withHer) "要 AA 的账本" else "只有你自己", size = 12.sp, weight = FontWeight.Medium)
                        }
                        GlassSwitch(withHer, "和她一起") { withHer = it }
                    }
                    if (withHer) {
                        ChipRow(listOf(false to "平分", true to "按比例"), ratio, "怎么分") { ratio = it }
                        if (ratio) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Label("你出", modifier = Modifier.weight(1f))
                                Stepper("$myPercent%", "你出的比例", onMinus = { myPercent = (myPercent - 5).coerceAtLeast(10) }, onPlus = { myPercent = (myPercent + 5).coerceAtMost(90) })
                            }
                        }
                        ChipRow(
                            listOf(SettleMode.MONTHLY to "每月", SettleMode.DATE to "选日期", SettleMode.MANUAL to "想结就结"),
                            mode, "什么时候结",
                        ) { mode = it }
                        when (mode) {
                            SettleMode.MONTHLY -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Label("每月哪天结", modifier = Modifier.weight(1f))
                                Stepper(dayLabel, "结算日", onMinus = { day = if (day == 1) 31 else if (day == 31) 28 else day - 1 }, onPlus = { day = if (day == 31) 1 else if (day == 28) 31 else day + 1 })
                            }
                            SettleMode.DATE -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Label("哪天结", modifier = Modifier.weight(1f))
                                Box(Modifier.glassControl(RoundedCornerShape(14.dp)).jelly { picking = true }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Strong(epochDay?.let(::epochDayText) ?: "选日期", size = 15.sp)
                                }
                            }
                            SettleMode.MANUAL -> {}
                        }
                        Label(
                            when (mode) {
                                SettleMode.MONTHLY -> "每月${if (day == 31) "最后一天" else "${day}号"}提醒你们结一次，算的是上次两清到这天的账。"
                                SettleMode.DATE -> (epochDay?.let(::epochDayText) ?: "选好的那天") + " 提醒你们结一次，之后再记的账算进下一次。"
                                SettleMode.MANUAL -> "不定期：想结的时候在账本里点「结算」，从上次两清之后算起。"
                            },
                            size = 12.sp, weight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp).navigationBarsPadding()) {
            CtaButton("建好账本") {
                if (name.isBlank()) {
                    toast.show("先给账本起个名字")
                } else {
                    vm.addLedger(
                        Ledger(
                            name = name.trim(),
                            currency = currency,
                            shared = withHer,
                            myPercent = if (withHer && ratio) myPercent else 50,
                            settleMode = mode,
                            settleDay = day,
                            settleEpochDay = if (mode == SettleMode.DATE) epochDay else null,
                        ),
                    )
                    toast.show("已建好「${name.trim()}」· 按${currency.label}记账")
                    onDone()
                }
            }
        }
    }
}
