package com.personal.carnet.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import com.personal.carnet.ui.Partner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.personal.carnet.ui.glass.MoneyVessel
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
import com.personal.carnet.ui.components.IconTile
import com.personal.carnet.ui.components.ChipRow
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.LinkButton
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glassSphere
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
    onOpenEntry: (Entry) -> Unit,
    onOpenBills: () -> Unit,
    recentAuto: List<Long> = emptyList(),
    onShareAuto: (Long) -> Unit = {},
    onUndoAuto: (Long) -> Unit = {},
    onKeepAuto: (Long) -> Unit = {},
) {
    val toast = LocalToast.current
    var ledgerFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val accounts = remember(snapshot) { snapshot.accounts.associate { it.account.id to it.account } }
    val ledgers = remember(snapshot) { snapshot.ledgers.associate { it.ledger.id to it.ledger } }
    val entries = remember(snapshot, ledgerFilter) {
        snapshot.entries.filter { ledgerFilter == null || it.ledgerId == ledgerFilter }.take(30)
    }
    val plan = snapshot.plan

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(start = 4.dp, top = 12.dp)) {
                Text("Carnet", fontFamily = com.personal.carnet.ui.glass.InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-0.6).sp, color = Palette.Ink)
                Label(todayText())
            }
        }
        if (update is UpdateState.Available || update is UpdateState.Downloading) {
            item {
                val build = (update as? UpdateState.Available)?.info?.build ?: (update as UpdateState.Downloading).info.build
                // 有新版本：一条轻的玻璃，左边一颗靛蓝小水珠，不压着整页
                Row(
                    Modifier.fillMaxWidth().glassControl(RoundedCornerShape(20.dp)).jelly(onClick = onOpenUpdate).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.foundation.Canvas(Modifier.size(14.dp)) { glassSphere(center, size.minDimension / 2, Palette.Violet, shadow = false) }
                    Text(
                        if (update is UpdateState.Downloading) "正在下载 build $build…" else "有新版本 build $build",
                        color = Palette.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    if (update !is UpdateState.Downloading) Strong("更新", size = 14.sp, color = Palette.Violet)
                }
            }
        }
        item { NetWorthCard(snapshot, onOpenAccounts, onOpenLedgers, onOpenRecurring) }
        snapshot.forecast?.takeIf { it.items.any { i -> i.kind == com.personal.carnet.domain.ForecastItem.Kind.CARD } }?.let { f ->
            item { ForecastCard(f) }
        }
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
                    EntryRow(entry.line(accounts, ledgers), onClick = { onOpenEntry(entry) })
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
                        LinkButton("全部", onClick = onOpenRecurring)
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
        item {
            Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Strong("最近", modifier = Modifier.weight(1f))
                LinkButton("全部账单", onClick = onOpenBills)
            }
        }
        if (entries.isEmpty()) {
            item { GlassCard { Label("还没有记账。点右下角的 ＋ 记第一笔。", weight = FontWeight.Medium) } }
        }
        if (entries.isNotEmpty()) {
            item(key = "bubbles") { BubbleStream(entries, accounts, ledgers, snapshot.rate, onOpen = onOpenEntry) }
        }
    }
}

/** 还款预测：按日子排开，看每个还款日那天能用的钱够不够 */
@Composable
private fun ForecastCard(f: com.personal.carnet.domain.Forecast) {
    var open by rememberSaveable { mutableStateOf(false) }
    val short = f.firstShort
    val recovered = f.recovered
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).jelly { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Strong("还款预测", modifier = Modifier.weight(1f))
            Label(if (open) "收起" else "展开", size = 12.sp, weight = FontWeight.SemiBold, color = Palette.Violet)
        }
        if (short == null) {
            Strong("接下来 ${f.until.monthValue}/${f.until.dayOfMonth} 前的卡都还得上 ✓", size = 14.sp, color = Palette.Teal)
        } else {
            Strong(
                "${short.date.monthValue}/${short.date.dayOfMonth} " + forecastLabel(short) + " 后差 " + Money.format(-short.balance, MainCurrency),
                size = 14.sp, color = Palette.Rose,
            )
            Label(
                recovered?.let { "${it.date.monthValue}/${it.date.dayOfMonth} " + forecastLabel(it) + " 之后就够了" } ?: "${f.until.monthValue}/${f.until.dayOfMonth} 前都不够",
                size = 12.sp, weight = FontWeight.Medium,
            )
        }
        f.lowest?.let { low ->
            Label(
                "最少的时候：${low.date.monthValue}/${low.date.dayOfMonth} " + forecastLabel(low) + " 后剩 " + Money.format(low.balance, MainCurrency),
                size = 12.sp, weight = FontWeight.SemiBold, color = if (low.balance < 0) Palette.Rose else Palette.Ink,
            )
        }
        if (open) {
            Row {
                Label("现在能用", size = 12.sp, modifier = Modifier.weight(1f))
                Label(Money.format(f.start, MainCurrency), size = 12.sp, weight = FontWeight.SemiBold)
            }
            f.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("${item.date.monthValue}/${item.date.dayOfMonth}", size = 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                    Label(forecastLabel(item), size = 12.sp, weight = FontWeight.Medium, color = Palette.Ink, modifier = Modifier.weight(1f))
                    Label(
                        (if (item.cents > 0) "+" else "") + Money.format(item.cents, MainCurrency),
                        size = 12.sp, weight = FontWeight.SemiBold, color = if (item.cents > 0) Palette.Teal else Palette.Ink,
                    )
                    Label(
                        Money.format(item.balance, MainCurrency), size = 12.sp, weight = FontWeight.SemiBold,
                        color = if (item.balance < 0) Palette.Rose else Palette.Muted, modifier = Modifier.width(92.dp),
                    )
                }
            }
            Label("同一天先算进账；她转的钱按账本的结算日算，记账时设的「她出多少」也算进去。", size = 11.sp, weight = FontWeight.Medium)
        }
    }
}

private fun forecastLabel(item: com.personal.carnet.domain.ForecastItem): String = when (item.kind) {
    // 卡名是英文开头时（UOB 信用卡）中间空一格
    com.personal.carnet.domain.ForecastItem.Kind.CARD -> item.name.removePrefix("还").let { n -> if (n.firstOrNull()?.code?.let { it < 128 } == true) "还 $n" else "还$n" }
    com.personal.carnet.domain.ForecastItem.Kind.FIXED -> item.name
    com.personal.carnet.domain.ForecastItem.Kind.SETTLE ->
        if (item.cents > 0) "${Partner.name}转「${item.name}」" else "补给${Partner.name}「${item.name}」"
}

@Composable
private fun NetWorthCard(snapshot: Snapshot, onOpenAccounts: () -> Unit, onOpenLedgers: () -> Unit, onOpenRecurring: () -> Unit) {
    // 进来时数字从 0 滚到实际金额
    val shown = remember { Animatable(0f) }
    LaunchedEffect(snapshot.netWorthCents) {
        shown.animateTo(snapshot.netWorthCents.toFloat(), tween(1400, easing = FastOutSlowInEasing))
    }
    val plan = snapshot.plan
    val spendable = plan.spendableCents
    val payday = plan.payday
    val level = remember(snapshot) { com.personal.carnet.ui.glass.cupLevel(snapshot) }
    val cash = snapshot.mainCashCents + snapshot.cnyCashInMainCents
    val split = if (cash > 0) snapshot.mainCashCents.toFloat() / cash else 1f
    var formula by rememberSaveable { mutableStateOf(false) }
    val shared = snapshot.sharedBalanceCents

    Box(Modifier.fillMaxWidth().holo(RoundedCornerShape(32.dp)).jelly { formula = !formula }) {
        // 杯子从金额下面开始：标题永远露在液面上
        MoneyVessel(level, split, Modifier.matchParentSize().padding(top = 128.dp))
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Label("净资产", weight = FontWeight.SemiBold)
            com.personal.carnet.ui.glass.GlassNumber(Money.format(shown.value.toLong(), MainCurrency), size = 42.sp)
            // 留出杯子上半截的空：液面在这里晃
            Spacer(Modifier.height(if (formula) 24.dp else 96.dp))
            // 和杯子里一样：人民币浮在上面，新币沉在下面
            VesselRow("国内账户", Money.format(snapshot.cnyCashCents, Currency.CNY), "≈ " + Money.format(snapshot.cnyCashInMainCents, MainCurrency), onOpenAccounts, dot = com.personal.carnet.ui.glass.Liquid.Cny.last())
            if (spendable != null && payday != null) {
                val days = ChronoUnit.DAYS.between(LocalDate.now(), payday)
                VesselRow("发薪前还能花", Money.format(spendable, MainCurrency), when { days <= 0 -> "今天发薪"; days == 1L -> "明天发薪"; else -> "还有 $days 天发薪" }, onOpenRecurring, dot = com.personal.carnet.ui.glass.Liquid.Sgd.last())
            } else {
                VesselRow("新币账户", Money.format(snapshot.mainCashCents, MainCurrency), "设好工资，杯子就按工资算满", onOpenAccounts, dot = com.personal.carnet.ui.glass.Liquid.Sgd.last())
            }
            if (shared != 0L) {
                VesselRow(
                    if (shared > 0) "你多垫的" else "${Partner.name}帮你垫的",
                    Money.format(kotlin.math.abs(shared), MainCurrency), null, onOpenLedgers, Palette.Rose,
                )
            }
            if (formula) {
                Label(
                    buildString {
                        append("新币 ${Money.format(snapshot.mainCashCents)} + 国内 ≈ ${Money.format(snapshot.cnyCashInMainCents)}")
                        append(" − 信用卡 ${Money.format(snapshot.cardsOwedCents)}")
                        if (shared != 0L) append(if (shared > 0) " + 你多垫的 ${Money.format(shared)}" else " − ${Partner.name}垫的 ${Money.format(-shared)}")
                    },
                    size = 12.sp, weight = FontWeight.Medium, color = Palette.Ink, modifier = Modifier.padding(top = 10.dp),
                )
                val parts = plan.spendableParts
                if (parts.isNotEmpty()) {
                    Label(
                        "发薪前还能花 = " + Money.format(parts.first().second) + "（能用的账户）" +
                            parts.drop(1).joinToString("") { (name, cents) -> " − $name ${Money.format(cents)}" },
                        size = 12.sp, weight = FontWeight.Medium, color = Palette.Ink, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Label("下层蓝的是新币，上层粉的是人民币；液面越低，离发薪越近、能花的越少。", size = 12.sp, weight = FontWeight.Medium, color = Palette.Ink, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 杯子里的一行：左边名字和小字，右边金额；字直接写在液体上 */
@Composable
private fun VesselRow(label: String, value: String, sub: String?, onClick: () -> Unit, valueColor: Color = Palette.Ink, dot: Color? = null) {
    Row(Modifier.fillMaxWidth().jelly(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        // 小圆点是这一行对应的那层液体的颜色
        Box(Modifier.padding(end = 10.dp).size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot ?: Color.Transparent)
            .then(if (dot != null) Modifier.border(1.dp, Color.White.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape) else Modifier))
        Column(Modifier.weight(1f)) {
            Label(label, size = 13.sp, weight = FontWeight.SemiBold, color = Palette.Ink.copy(alpha = 0.78f))
            if (sub != null) Label(sub, size = 11.sp, weight = FontWeight.Medium, color = Palette.Ink.copy(alpha = 0.62f))
        }
        Strong(value, size = 18.sp, color = valueColor)
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
        IconTile(line.icon)
        Column(Modifier.weight(1f)) {
            Text(line.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
