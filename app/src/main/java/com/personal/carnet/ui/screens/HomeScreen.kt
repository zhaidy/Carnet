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
import com.personal.carnet.data.Entry
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Occurrence
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.ui.EntryLine
import com.personal.carnet.ui.LineIcon
import com.personal.carnet.ui.Sparkle
import com.personal.carnet.ui.UpdateState
import com.personal.carnet.ui.components.ChipRow
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glass
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.glass.jelly
import com.personal.carnet.ui.glass.tinted
import com.personal.carnet.ui.line
import com.personal.carnet.ui.todayText
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(
    snapshot: Snapshot,
    update: UpdateState,
    onOpenUpdate: () -> Unit,
    onNewAccount: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenRecurring: () -> Unit,
    onConfirm: (Occurrence) -> Unit,
    onSkip: (Occurrence) -> Unit,
    onDelete: (Entry) -> Unit,
    recentAuto: List<Long> = emptyList(),
    onShareAuto: (Long) -> Unit = {},
    onUndoAuto: (Long) -> Unit = {},
    onKeepAuto: (Long) -> Unit = {},
) {
    val toast = LocalToast.current
    var ledgerFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val accounts = remember(snapshot) { snapshot.accounts.associate { it.account.id to it.account } }
    val ledgers = remember(snapshot) { snapshot.ledgers.associate { it.ledger.id to it.ledger } }
    val entries = remember(snapshot, ledgerFilter) {
        snapshot.entries.filter { ledgerFilter == null || it.ledgerId == ledgerFilter }.take(80)
    }
    val plan = snapshot.plan

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
        if (update is UpdateState.Available || update is UpdateState.Downloading) {
            item {
                val build = (update as? UpdateState.Available)?.info?.build ?: (update as UpdateState.Downloading).info.build
                Row(
                    Modifier.fillMaxWidth().tinted(RoundedCornerShape(20.dp), Palette.Cta).jelly(onClick = onOpenUpdate).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Sparkle(Color.White, 16.dp)
                    Text(
                        if (update is UpdateState.Downloading) "正在下载 build $build…" else "有新版本 build $build，点这里更新",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item { NetWorthCard(snapshot, onOpenAccounts, onOpenLedgers, onOpenRecurring) }
        val dayAgo = System.currentTimeMillis() - 48 * 3600_000L
        val autoEntries = snapshot.entries.filter { it.id in recentAuto && it.timeMillis > dayAgo }
        if (autoEntries.isNotEmpty()) {
            item {
                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Sparkle(Palette.Violet, 14.dp)
                    Strong("从 Google 钱包自动记下的")
                }
            }
            items(autoEntries, key = { "a" + it.id }) { entry ->
                val shared = ledgers[entry.ledgerId]?.shared == true
                val hasShared = snapshot.ledgers.any { it.ledger.shared }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EntryRow(entry.line(accounts, ledgers))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!shared && hasShared && entry.kind == com.personal.carnet.data.EntryKind.EXPENSE) {
                            Box(Modifier.weight(1f).tinted(RoundedCornerShape(18.dp), Palette.Cta).jelly {
                                onShareAuto(entry.id)
                                toast.show("放进共用账本了，你们各一半")
                            }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Strong("算我们俩的", size = 14.sp, color = Color.White) }
                        }
                        Box(Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).jelly { onKeepAuto(entry.id) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Strong("没问题", size = 14.sp) }
                        Box(Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).jelly {
                            onUndoAuto(entry.id)
                            toast.show("撤销了")
                        }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Strong("撤销", size = 14.sp, color = Palette.Rose) }
                    }
                }
            }
        }
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
        if (plan.pending.isNotEmpty()) {
            item { Strong("到日子了", modifier = Modifier.padding(start = 4.dp)) }
            items(plan.pending, key = { "p" + it.rule.id + "_" + it.date }) { occ ->
                PendingRow(occ, accounts, onConfirm = {
                    onConfirm(occ)
                    toast.show("已记下「${occ.rule.name}」")
                }, onSkip = { onSkip(occ) })
            }
        }
        if (plan.upcoming.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Strong("接下来", modifier = Modifier.weight(1f))
                        Box(Modifier.jelly(onClick = onOpenRecurring).padding(6.dp)) { Strong("全部", size = 13.sp, color = Palette.Violet) }
                    }
                    plan.upcoming.take(5).forEach { occ -> UpcomingRow(occ) }
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EntryRow(entry.line(accounts, ledgers), onClick = { selected = if (selected == entry.id) null else entry.id })
                if (selected == entry.id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).jelly {
                                onDelete(entry)
                                selected = null
                                toast.show("已删掉这一笔")
                            }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Strong("删掉这一笔", size = 14.sp, color = Palette.Rose) }
                        Box(
                            Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).jelly { selected = null }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Strong("算了", size = 14.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetWorthCard(snapshot: Snapshot, onOpenAccounts: () -> Unit, onOpenLedgers: () -> Unit, onOpenRecurring: () -> Unit) {
    // 进来时数字从 0 滚到实际金额
    val shown = remember { Animatable(0f) }
    LaunchedEffect(snapshot.netWorthCents) {
        shown.animateTo(snapshot.netWorthCents.toFloat(), tween(1400, easing = FastOutSlowInEasing))
    }
    val plan = snapshot.plan
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
            val spendable = plan.spendableCents
            val payday = plan.payday
            if (spendable != null && payday != null) {
                val days = ChronoUnit.DAYS.between(LocalDate.now(), payday)
                Tile("发薪前还能花", Money.format(spendable, MainCurrency), Modifier.weight(1f), onOpenRecurring, sub = "${payday.monthValue}/${payday.dayOfMonth} 发薪，还有 $days 天")
            } else {
                Tile("新币账户", Money.format(snapshot.mainCashCents, MainCurrency), Modifier.weight(1f), onOpenAccounts, sub = "设好工资就能算发薪前还能花")
            }
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
private fun DateBadge(date: LocalDate) {
    Column(
        Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.6f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Label("${date.monthValue}月", size = 10.sp)
        Strong("${date.dayOfMonth}", size = 17.sp)
    }
}

fun occurrenceAmount(occ: Occurrence): String =
    (if (occ.estimate) "≈ " else "") + (if (occ.inflow) "+" else "-") + Money.format(occ.cents, occ.currency)

@Composable
private fun UpcomingRow(occ: Occurrence) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateBadge(occ.date)
        Column(Modifier.weight(1f)) {
            Strong(occ.rule.name, size = 14.sp)
            Label(if (occ.rule.auto) "到日子自动记" else "到日子先提醒你", size = 12.sp, weight = FontWeight.Medium)
        }
        Strong(occurrenceAmount(occ), size = 15.sp, color = if (occ.inflow) Palette.Teal else Palette.Ink)
    }
}

@Composable
private fun PendingRow(occ: Occurrence, accounts: Map<Long, com.personal.carnet.data.Account>, onConfirm: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateBadge(occ.date)
            Column(Modifier.weight(1f)) {
                Strong("该${occ.rule.name}了", size = 15.sp)
                Label(accounts[occ.rule.accountId]?.name ?: "", size = 12.sp)
            }
            Strong(occurrenceAmount(occ), size = 15.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { CtaButton("记下", onClick = onConfirm) }
            Box(
                Modifier.weight(1f).glassControl(RoundedCornerShape(22.dp)).jelly(onClick = onSkip).padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Strong("这次不记", size = 15.sp) }
        }
    }
}

@Composable
fun EntryRow(line: EntryLine, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().glassControl(RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.jelly(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
    }
}

@Composable
fun EmptySpace(height: Int) = Spacer(Modifier.height(height.dp))
