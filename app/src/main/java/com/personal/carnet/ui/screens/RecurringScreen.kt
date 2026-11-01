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
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.RuleKind
import com.personal.carnet.domain.Categories
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Schedule
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.LineIcon
import com.personal.carnet.ui.components.IconTile
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
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
import java.time.LocalDate

@Composable
fun RecurringScreen(
    snapshot: Snapshot,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onSetActive: (RecurringRule, Boolean) -> Unit,
    onDelete: (RecurringRule) -> Unit,
    onEdit: (RecurringRule) -> Unit = {},
) {
    val toast = LocalToast.current
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = LocalDate.now()
    val accounts = snapshot.accounts.associate { it.account.id to it.account }
    val ledgers = snapshot.ledgers.associate { it.ledger.id to it.ledger }
    val rules = snapshot.rules.sortedWith(compareBy({ !it.active }, { Schedule.next(it, today.plusDays(1)) }))
    val plan = snapshot.plan

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("固定支出和工资", onBack = onBack) { PillButton("新建", IconPaths.PLUS, onClick = onNew) } }
        item {
            Column(Modifier.fillMaxWidth().holo(RoundedCornerShape(28.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("每月固定支出 · 你的那份", weight = FontWeight.Bold)
                Strong(Money.format(plan.monthlyFixedCents, MainCurrency), size = 30.sp)
                val spendable = plan.spendableCents
                val payday = plan.payday
                Label(
                    if (spendable != null && payday != null) "${payday.monthValue}/${payday.dayOfMonth} 发薪前还能花 " + Money.format(spendable, MainCurrency) + "（已扣掉这之前的房租、还卡和固定支出）"
                    else "加上工资，就能算出发薪前还能花多少",
                    size = 12.sp, weight = FontWeight.Medium,
                )
            }
        }
        if (rules.isEmpty()) {
            item {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Strong("还没有固定收支")
                        Label("先加工资（26 号）、房租（20 号）、两张卡的还款（13 号、26 号），再加话费和订阅。", weight = FontWeight.Medium)
                        CtaButton("新建", onClick = onNew)
                    }
                }
            }
        }
        items(rules, key = { it.id }) { rule ->
            val next = Schedule.next(rule, maxOf(today, LocalDate.ofEpochDay(rule.lastDoneEpochDay)).plusDays(1))
            val shared = ledgers[rule.ledgerId]?.shared == true
            val account = accounts[rule.accountId]?.name
            val card = accounts[rule.toAccountId]?.name
            val icon = when (rule.kind) {
                RuleKind.CARD_PAYMENT -> Categories.CARD_ICON
                else -> Categories.of(rule.category).icon
            }
            val meta = listOfNotNull(
                Schedule.describe(rule),
                when (rule.kind) {
                    RuleKind.CARD_PAYMENT -> "从${account ?: "?"}还${card ?: "?"}全额"
                    RuleKind.INCOME -> account?.let { "进$it" }
                    RuleKind.EXPENSE -> account
                },
                if (rule.auto) "自动记" else "先提醒我",
            ).joinToString(" · ")
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).jelly { open = if (open == rule.id) null else rule.id }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(icon)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Strong(rule.name)
                            if (shared) Badge("我们俩", Palette.Violet)
                            if (!rule.active) Badge("暂停了", Palette.Muted)
                        }
                        Label(meta, size = 12.sp, weight = FontWeight.Medium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val amount = when (rule.kind) {
                            RuleKind.CARD_PAYMENT -> "全额"
                            RuleKind.INCOME -> "+" + Money.format(rule.amountCents, rule.currency)
                            RuleKind.EXPENSE -> (if (rule.approximate) "≈ " else "") + Money.format(rule.amountCents, rule.currency)
                        }
                        Strong(amount, size = 15.sp, color = if (rule.kind == RuleKind.INCOME) Palette.Teal else Palette.Ink)
                        if (rule.active) Label("${next.monthValue}/${next.dayOfMonth}", size = 11.sp)
                    }
                }
                if (open == rule.id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).jelly { onEdit(rule) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Strong("改", size = 14.sp, color = Palette.Violet) }
                        Box(
                            Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).jelly {
                                onSetActive(rule, !rule.active)
                                toast.show(if (rule.active) "「${rule.name}」暂停了" else "「${rule.name}」恢复了，从下一次开始")
                            }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Strong(if (rule.active) "暂停" else "恢复", size = 14.sp) }
                        Box(
                            Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).jelly {
                                onDelete(rule)
                                toast.show("删掉了「${rule.name}」，以前记的账还在")
                            }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Strong("删掉", size = 14.sp, color = Palette.Rose) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(color).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
