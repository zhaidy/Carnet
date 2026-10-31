package com.personal.carnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.data.AccountType
import com.personal.carnet.domain.AccountView
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.LineIcon
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.GlassField
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.PillButton
import com.personal.carnet.ui.components.ScreenHeader
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glass
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.glass.jelly

@Composable
fun AccountsScreen(snapshot: Snapshot, onNewAccount: () -> Unit, onSetRate: (FxRate) -> Unit, onEdit: (AccountView) -> Unit = {}) {
    val toast = LocalToast.current
    var editingRate by rememberSaveable { mutableStateOf(false) }
    var rateText by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("账户") { PillButton("新建", IconPaths.PLUS, onClick = onNewAccount) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).glass(RoundedCornerShape(22.dp)).padding(14.dp)) {
                    Label("净资产", size = 12.sp)
                    Strong(Money.format(snapshot.netWorthCents, MainCurrency), size = 19.sp)
                }
                Column(
                    Modifier.weight(1f).holo(RoundedCornerShape(22.dp)).jelly {
                        rateText = FxRate.format(snapshot.rate.cnyPerSgd)
                        editingRate = !editingRate
                    }.padding(14.dp),
                ) {
                    Label("默认汇率 · 点一下改", size = 12.sp)
                    Strong("1 S$ = ¥" + FxRate.format(snapshot.rate.cnyPerSgd), size = 19.sp)
                }
            }
        }
        if (editingRate) {
            item {
                GlassCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Strong("1 S$ = ¥", size = 15.sp)
                            GlassField(rateText, { rateText = it.filter { c -> c.isDigit() || c == '.' } }, "5.30", Modifier.weight(1f), decimal = true, fontSize = 17.sp)
                        }
                        Label("你自己定，所有人民币账户共用；记账、转账时每一笔还能单独改。已经记下的账不会跟着变。", size = 12.sp, weight = FontWeight.Medium)
                        CtaButton("保存汇率") {
                            val r = FxRate.parse(rateText)
                            if (r == null) toast.show("汇率要是大于 0 的数") else {
                                onSetRate(r)
                                editingRate = false
                                toast.show("默认汇率改成 1 S$ = ¥" + FxRate.format(r.cnyPerSgd))
                            }
                        }
                    }
                }
            }
        }
        if (snapshot.accounts.isEmpty()) {
            item {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Strong("还没有账户")
                        Label("把储蓄卡、信用卡、工行卡、支付宝、微信都加进来，填一下现在的余额。", weight = FontWeight.Medium)
                        CtaButton("新建账户", onClick = onNewAccount)
                    }
                }
            }
        }
        items(snapshot.accounts, key = { it.account.id }) { view -> AccountCard(view, snapshot.rate) { onEdit(view) } }
    }
}

@Composable
private fun AccountCard(view: AccountView, rate: FxRate, onClick: () -> Unit) {
    val a = view.account
    val credit = a.type == AccountType.CREDIT
    val icon = when (a.type) {
        AccountType.DEBIT -> IconPaths.BANK
        AccountType.CREDIT -> IconPaths.CARD
        AccountType.WALLET -> IconPaths.WALLET
        AccountType.CASH -> IconPaths.CASH
    }
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().then(if (credit) Modifier.holo(shape) else Modifier.glass(shape)).jelly(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                LineIcon(icon, Palette.Ink, size = 18.dp)
            }
            Column(Modifier.weight(1f)) {
                Strong(a.name)
                Label(a.type.label + " · " + a.currency.label, size = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Label(if (credit) "现在欠" else "现在有", size = 11.sp)
                Strong(Money.format(if (credit) -view.balanceCents else view.balanceCents, a.currency), size = 17.sp)
                if (a.currency != MainCurrency) {
                    Label("≈ " + Money.format(rate.convert(if (credit) -view.balanceCents else view.balanceCents, a.currency, MainCurrency), MainCurrency), size = 11.sp)
                }
            }
        }
        val notes = listOfNotNull(
            a.dueDay?.takeIf { credit }?.let { "每月 $it 号还全额" },
            if (!a.inCashflow && !credit) "不算进发薪前还能花" else null,
        )
        if (notes.isNotEmpty()) Text(notes.joinToString(" · "), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
    }
}

/** 给新建账户用：币种的显示 */
fun Currency.chip(): String = "$symbol $label"
