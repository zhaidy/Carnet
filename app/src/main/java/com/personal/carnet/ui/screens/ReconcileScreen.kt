package com.personal.carnet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.data.Account
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Payer
import com.personal.carnet.domain.Books
import com.personal.carnet.domain.Cashflow
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.GlassField
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.ScreenHeader
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.glass.jelly
import com.personal.carnet.ui.LineIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.personal.carnet.ui.line
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 信用卡对账：一期账单（账单日到账单日）里 Carnet 记了多少，和银行账单比；
 * 少记了可以把差额补记成一笔「漏记」，多记了就看看下面哪笔记重了
 */
@Composable
fun ReconcileScreen(snapshot: Snapshot, card: Account, vm: MainViewModel, onBack: () -> Unit, onOpen: (Entry) -> Unit) {
    val toast = LocalToast.current
    var back by rememberSaveable { mutableIntStateOf(0) }
    var bankText by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val (after, through) = Cashflow.statementPeriod(card.statementDay, today, back)
    val accounts = remember(snapshot) { snapshot.accounts.associate { it.account.id to it.account } }
    val ledgers = remember(snapshot) { snapshot.ledgers.associate { it.ledger.id to it.ledger } }
    val charges = remember(snapshot, after, through) {
        Cashflow.cardCharges(card.id, snapshot.entries, after, through).sortedByDescending { it.timeMillis }
    }
    val recorded = -charges.sumOf { Books.delta(it, card.id) }
    val bank = Money.parse(bankText)
    val diff = bank?.let { it - recorded }
    val first = after.plusDays(1)
    val periodText = "${first.monthValue}/${first.dayOfMonth} – ${through.monthValue}/${through.dayOfMonth}"

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("对账 · ${card.name}", onBack = onBack) }
        item {
            Row(
                Modifier.fillMaxWidth().glassControl(RoundedCornerShape(18.dp)).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthStep(IconPaths.CHEVRON_LEFT, "上一期", true) { back++ }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Strong(periodText, size = 15.sp)
                    Label(if (card.statementDay != null) "${through.monthValue}/${through.dayOfMonth} 出的账单" else "按自然月；设了账单日会按账单算", size = 11.sp)
                }
                MonthStep(IconPaths.CHEVRON_RIGHT, "下一期", back > 0) { back-- }
            }
        }
        item {
            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label("银行账单", modifier = Modifier.width(64.dp))
                        Strong(card.currency.symbol, size = 16.sp)
                        GlassField(bankText, { bankText = it.filter { c -> c.isDigit() || c == '.' } }, "0.00", Modifier.weight(1f), decimal = true, fontSize = 17.sp)
                    }
                    Label("填账单上这一期的消费（New Transactions / 本期消费）。每期都还全额的话，就是账单上要还的金额。", size = 11.sp, weight = FontWeight.Medium)
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().holo(RoundedCornerShape(24.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Label("Carnet 里这一期记了 ${charges.size} 笔", size = 12.sp)
                Strong(Money.format(recorded, card.currency), size = 24.sp)
                when {
                    diff == null -> Label("填上银行账单的金额，看看对不对得上", size = 12.sp, weight = FontWeight.Medium)
                    diff == 0L -> Strong("对上了 ✓", size = 15.sp, color = Palette.Teal)
                    diff > 0 -> Strong("少记了 " + Money.format(diff, card.currency), size = 15.sp, color = Palette.Rose)
                    else -> Strong("多记了 " + Money.format(-diff, card.currency), size = 15.sp, color = Palette.Rose)
                }
                if (diff != null && diff < 0) {
                    Label("看看下面有没有记重的、记错卡的、或者其实是下一期的", size = 12.sp, weight = FontWeight.Medium)
                }
            }
        }
        if (diff != null && diff > 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CtaButton("补记一笔漏记的 " + Money.format(diff, card.currency), sub = "算进个人账本「其他」，日期记在 ${through.monthValue}/${through.dayOfMonth}") {
                        val personal = snapshot.ledgers.map { it.ledger }.firstOrNull { !it.shared } ?: return@CtaButton
                        vm.saveSpending(
                            kind = EntryKind.EXPENSE, ledger = personal, account = card, payer = Payer.ME, category = "more",
                            note = "对账补记 $periodText", amountCents = diff, currency = card.currency, rate = snapshot.rate,
                            timeMillis = through.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        )
                        toast.show("补记好了，这一期对上了")
                    }
                    Label("想起来是哪笔的话，最好按原来的日期和分类补记，统计更准。", size = 11.sp, weight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (charges.isEmpty()) {
            item { GlassCard(padding = 14.dp) { Label("这一期还没有用这张卡记的账", weight = FontWeight.Medium) } }
        }
        items(charges, key = { it.id }) { entry -> EntryRow(entry.line(accounts, ledgers), onClick = { onOpen(entry) }) }
    }
}

@Composable
private fun MonthStep(icon: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .semantics { contentDescription = description; role = Role.Button }
            .then(if (enabled) Modifier.jelly(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) { LineIcon(icon, if (enabled) Palette.Ink else Palette.Muted.copy(alpha = 0.4f), size = 18.dp, width = 2.2f) }
}
