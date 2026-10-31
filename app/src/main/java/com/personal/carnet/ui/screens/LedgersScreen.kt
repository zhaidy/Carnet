package com.personal.carnet.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.data.SettleMode
import com.personal.carnet.domain.LedgerView
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.domain.Split
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.components.Avatar
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.PillButton
import com.personal.carnet.ui.components.ScreenHeader
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.epochDayText
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glass
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.glass.jelly
import com.personal.carnet.ui.shortDate
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.GlassField
import com.personal.carnet.sync.SyncException
import com.personal.carnet.sync.SyncStatus
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LedgersScreen(snapshot: Snapshot, vm: MainViewModel, onNewLedger: () -> Unit, onSettle: (LedgerView) -> Unit, onEdit: (LedgerView) -> Unit = {}) {
    var confirming by rememberSaveable { mutableStateOf<Long?>(null) }
    var joining by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val status by vm.syncStatus.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("账本") {
                PillButton("加入", onClick = { joining = !joining })
                PillButton("新建", IconPaths.PLUS, onClick = onNewLedger)
            }
        }
        if (joining) {
            item {
                GlassCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Strong("用她给的邀请码加入")
                        GlassField(code, { code = it.uppercase() }, "6 位邀请码", Modifier.fillMaxWidth(), fontSize = 18.sp)
                        CtaButton("加入") {
                            scope.launch {
                                try {
                                    val ledger = vm.join(code)
                                    toast.show("加入了「${ledger.name}」，账会自动同步过来")
                                    joining = false
                                    code = ""
                                } catch (e: SyncException) {
                                    toast.show(e.message ?: "加入失败")
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().holo(RoundedCornerShape(28.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("我这个月一共花了", weight = FontWeight.Bold)
                Strong(Money.format(snapshot.monthMySpendingCents, MainCurrency), size = 28.sp)
                Label("个人账本 + 共用账本里你那一份；谁多垫了钱，会算进净资产。", size = 12.sp, weight = FontWeight.Medium)
            }
        }
        items(snapshot.ledgers, key = { it.ledger.id }) { view ->
            LedgerCard(
                view = view,
                syncAvailable = vm.syncAvailable,
                syncStatus = status[view.ledger.id],
                onInvite = {
                    scope.launch {
                        try {
                            val c = vm.invite(view.ledger)
                            toast.show("邀请码 $c，让她在 Carnet 的账本页点「加入」")
                        } catch (e: SyncException) {
                            toast.show(e.message ?: "邀请失败")
                        }
                    }
                },
                onLeave = { vm.leaveSync(view.ledger) },
                onEdit = { onEdit(view) },
                confirming = confirming == view.ledger.id,
                onAskSettle = { confirming = view.ledger.id },
                onCancel = { confirming = null },
                onConfirm = {
                    onSettle(view)
                    confirming = null
                    toast.show("「${view.ledger.name}」两清啦")
                },
            )
        }
    }
}

@Composable
private fun LedgerCard(
    view: LedgerView,
    syncAvailable: Boolean,
    syncStatus: SyncStatus?,
    onInvite: () -> Unit,
    onLeave: () -> Unit,
    onEdit: () -> Unit,
    confirming: Boolean,
    onAskSettle: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val l = view.ledger
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.jelly(onClick = onEdit), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (l.shared) {
                Box(Modifier.size(width = 50.dp, height = 34.dp)) {
                    Avatar("我", her = false)
                    Box(Modifier.padding(start = 18.dp)) { Avatar("她", her = true) }
                }
            } else {
                Avatar("我", her = false, size = 40.dp)
            }
            Column(Modifier.weight(1f)) {
                Strong(l.name, size = 16.sp)
                val currency = l.currency?.label ?: "混币，按新币统计"
                val settle = if (!l.shared) "只有你" else when (l.settleMode) {
                    SettleMode.MONTHLY -> if (l.settleDay == 31) "每月月底结" else "每月 ${l.settleDay} 号结"
                    SettleMode.DATE -> l.settleEpochDay?.let { epochDayText(it) + " 结一次" } ?: "选日期结"
                    SettleMode.MANUAL -> "想结就结"
                }
                val split = if (l.shared) (if (l.myPercent == 50) "平分" else "你 ${l.myPercent}%") + " · " else ""
                Label("$split$settle · $currency", size = 12.sp, weight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Strong(Money.format(view.periodCents, view.currency), size = 16.sp)
                Label(if (l.shared) "上次两清以来" else "这个月", size = 11.sp)
            }
        }
        if (l.shared) {
            Row(
                Modifier.fillMaxWidth().glassControl(RoundedCornerShape(18.dp)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Strong(Split.describe(view.balanceCents, view.currency), size = 14.sp, color = if (view.balanceCents > 0) Palette.Rose else Palette.Ink)
                    view.lastSettlement?.let { Label("上次两清 " + shortDate(it.timeMillis), size = 11.sp) }
                }
                if (view.periodCents > 0 && !confirming) {
                    Box(Modifier.glassControl().jelly(onClick = onAskSettle).padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Strong("结算", size = 13.sp, color = Palette.Violet)
                    }
                }
            }
            val syncCode = l.syncCode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    when {
                        syncCode != null -> {
                            Label("和她同步中 · 邀请码 $syncCode" + (syncStatus?.members?.let { " · $it 台手机" } ?: ""), size = 12.sp, weight = FontWeight.Bold, color = Palette.Violet)
                            syncStatus?.error?.let { Label(it, size = 11.sp, color = Palette.Rose) }
                        }
                        syncAvailable -> Label("还没和她同步：她那边看不到这些账", size = 12.sp, weight = FontWeight.Medium)
                        else -> Label("这个版本还没接上 Firebase，暂时不能和她同步", size = 12.sp, weight = FontWeight.Medium)
                    }
                }
                if (syncCode == null && syncAvailable) {
                    Box(Modifier.glassControl().jelly(onClick = onInvite).padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Strong("邀请她", size = 13.sp, color = Palette.Violet)
                    }
                } else if (syncCode != null) {
                    Box(Modifier.glassControl().jelly(onClick = onLeave).padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Strong("停止同步", size = 12.sp, color = Palette.Muted)
                    }
                }
            }
            if (confirming) {
                Label("补好了就点两清，这之前的账就不再算了。", size = 12.sp, weight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { CtaButton("补好了，两清", onClick = onConfirm) }
                    Box(
                        Modifier.weight(1f).glassControl(RoundedCornerShape(22.dp)).jelly(onClick = onCancel).padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) { Strong("先不结", size = 15.sp, color = Color(0xFF1F1640)) }
                }
            }
        }
    }
}
