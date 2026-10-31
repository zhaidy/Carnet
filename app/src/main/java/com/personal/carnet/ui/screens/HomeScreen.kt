package com.personal.carnet.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.ui.EntryLine
import com.personal.carnet.ui.LineIcon
import com.personal.carnet.ui.Sparkle
import com.personal.carnet.ui.components.ChipRow
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.glass.jelly
import com.personal.carnet.ui.line
import com.personal.carnet.ui.todayText

@Composable
fun HomeScreen(snapshot: Snapshot, onNewAccount: () -> Unit, onOpenAccounts: () -> Unit, onOpenLedgers: () -> Unit) {
    var ledgerFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val accounts = remember(snapshot) { snapshot.accounts.associate { it.account.id to it.account } }
    val ledgers = remember(snapshot) { snapshot.ledgers.associate { it.ledger.id to it.ledger } }
    val entries = remember(snapshot, ledgerFilter) {
        snapshot.entries.filter { ledgerFilter == null || it.ledgerId == ledgerFilter }.take(60)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(start = 4.dp, top = 12.dp)) {
                Strong("Carnet", size = 28.sp)
                Label(todayText())
            }
        }
        item { NetWorthCard(snapshot, onOpenAccounts, onOpenLedgers) }
        if (snapshot.accounts.isEmpty()) {
            item {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Strong("先把账户加进来")
                        Label("储蓄卡、两张信用卡、工行卡、支付宝、微信……填一下现在的余额，净资产就能算出来。", weight = FontWeight.Medium)
                        CtaButton("新建账户", onClick = onNewAccount)
                    }
                }
            }
        }
        item {
            ChipRow(
                options = listOf<Pair<Long?, String>>(null to "全部") + snapshot.ledgers.map { it.ledger.id to it.ledger.name },
                selected = ledgerFilter,
            ) { ledgerFilter = it }
        }
        item { Strong("最近", modifier = Modifier.padding(start = 4.dp)) }
        if (entries.isEmpty()) {
            item { GlassCard { Label("还没有记账。点右下角的 ＋ 记第一笔。", weight = FontWeight.Medium) } }
        }
        items(entries, key = { it.id }) { entry ->
            EntryRow(entry.line(accounts, ledgers))
        }
    }
}

@Composable
private fun NetWorthCard(snapshot: Snapshot, onOpenAccounts: () -> Unit, onOpenLedgers: () -> Unit) {
    // 进来时数字从 0 滚到实际金额
    val shown = remember { Animatable(0f) }
    LaunchedEffect(snapshot.netWorthCents) {
        shown.animateTo(snapshot.netWorthCents.toFloat(), tween(1400, easing = FastOutSlowInEasing))
    }
    Column(
        Modifier.fillMaxWidth().holo(RoundedCornerShape(32.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Label("净资产", weight = FontWeight.Bold)
                Text(
                    Money.format(shown.value.toLong(), MainCurrency),
                    fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                )
            }
            Sparkle(Color.White, 22.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("新币账户", Money.format(snapshot.mainCashCents, MainCurrency), Modifier.weight(1f), onOpenAccounts)
            Tile(
                "国内账户",
                Money.format(snapshot.cnyCashCents, Currency.CNY),
                Modifier.weight(1f),
                onOpenAccounts,
                sub = "≈ " + Money.format(snapshot.cnyCashInMainCents, MainCurrency),
            )
        }
        val shared = snapshot.sharedBalanceCents
        if (shared != 0L) {
            Tile(
                if (shared > 0) "你多垫的" else "她帮你垫的",
                Money.format(kotlin.math.abs(shared), MainCurrency),
                Modifier.fillMaxWidth(),
                onOpenLedgers,
                valueColor = Palette.Rose,
            )
        }
        Label(
            buildString {
                append("新币 ${Money.format(snapshot.mainCashCents)} + 国内 ≈ ${Money.format(snapshot.cnyCashInMainCents)}")
                append(" − 信用卡 ${Money.format(snapshot.cardsOwedCents)}")
                if (shared != 0L) append(if (shared > 0) " + 你多垫的 ${Money.format(shared)}" else " − 她垫的 ${Money.format(-shared)}")
            },
            size = 12.sp, weight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Tile(
    label: String, value: String, modifier: Modifier, onClick: () -> Unit,
    sub: String? = null, valueColor: Color = Palette.Ink,
) {
    Column(modifier.glassControl(RoundedCornerShape(20.dp)).jelly(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Label(label, size = 12.sp)
        Strong(value, size = 18.sp, color = valueColor)
        if (sub != null) Label(sub, size = 11.sp)
    }
}

@Composable
fun EntryRow(line: EntryLine, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().glassControl(RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) { LineIcon(line.icon, Palette.Ink, size = 20.dp, width = 1.7f) }
        Column(Modifier.weight(1f)) {
            Text(line.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Label(line.meta, size = 12.sp, weight = FontWeight.Medium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Strong(line.amount, size = 15.sp, color = if (line.income) Palette.Teal else Palette.Ink)
            if (line.sub != null) Label(line.sub, size = 11.sp)
        }
        trailing()
    }
}

@Composable
fun EmptySpace(height: Int) = Spacer(Modifier.height(height.dp))
