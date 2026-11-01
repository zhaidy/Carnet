package com.personal.carnet.ui.screens

import com.personal.carnet.ui.Partner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.LineIcon
import com.personal.carnet.ui.UpdateState
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.ScreenHeader
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glass
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.glass.jelly

@Composable
fun MeScreen(
    snapshot: Snapshot,
    update: UpdateState,
    currentBuild: Long,
    onCheckUpdate: () -> Unit,
    onInstall: (com.personal.carnet.update.UpdateInfo) -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenAuto: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("我的") }
        item {
            MenuRow(
                IconPaths.REPEAT, "固定支出和工资",
                "每月固定 " + Money.format(snapshot.plan.monthlyFixedCents, MainCurrency) + " · ${snapshot.rules.count { it.active }} 项",
                onOpenRecurring,
            )
        }
        item { MenuRow(IconPaths.SPARKLE_LINE, "自动记账和 Claude", "Google 钱包付款自动记一笔；Claude API Key", onOpenAuto) }
        item { MenuRow(IconPaths.IMPORT, "导入随手记", "选随手记导出的 Excel 或 CSV", onOpenImport) }
        item { MenuRow(IconPaths.SWAP, "默认汇率", "1 S$ = ¥" + FxRate.format(snapshot.rate.cnyPerSgd) + " · 在账户页改", onOpenAccounts) }
        item { AddDefaultsCard(snapshot) }
        item { PartnerCard() }
        item { CustomCategoryCard() }
        item { UpdateCard(update, currentBuild, onCheckUpdate, onInstall) }
    }
}

/** 自己加的分类：在记账页分类最后的「添加」里加，在这里删 */
@Composable
private fun CustomCategoryCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val list = com.personal.carnet.ui.CustomCategories.all()
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Strong("自己加的分类")
        if (list.isEmpty()) {
            Label("记账时在分类最后点「添加」，起个名字、挑个图标就行。", size = 12.sp, weight = FontWeight.Medium)
        } else {
            Label("点 ✕ 删掉；已经记过的账不受影响。", size = 12.sp, weight = FontWeight.Medium)
            list.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LineIcon(c.icon, Palette.Ink, size = 18.dp)
                    Strong(c.label + if (c.income) " · 收入" else "", size = 14.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.jelly { com.personal.carnet.ui.CustomCategories.remove(context, c.key) }.padding(8.dp)) {
                        LineIcon(IconPaths.CLOSE, Palette.Rose, size = 14.dp)
                    }
                }
            }
        }
    }
}

/** 记一笔时默认记到哪个账本、从哪个账户付 */
@Composable
private fun AddDefaultsCard(snapshot: Snapshot) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val d = com.personal.carnet.ui.AddDefaults
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Strong("记账默认")
        Label("点 ＋ 记一笔时先选好的；那一笔不一样的话再手动换。", size = 12.sp, weight = FontWeight.Medium)
        com.personal.carnet.ui.components.ChipRow(
            listOf<Pair<Long?, String>>(null to "个人") + snapshot.ledgers.map { it.ledger }.filter { it.shared || snapshot.ledgers.count { v -> !v.ledger.shared } > 1 }
                .map { it.id to it.name },
            d.ledgerId, "记到",
        ) { d.setLedger(context, it) }
        com.personal.carnet.ui.components.ChipRow(
            listOf<Pair<Long?, String>>(null to "第一个") + snapshot.accounts.map { it.account.id to it.account.name },
            d.accountId, "从哪付",
        ) { d.setAccount(context, it) }
    }
}

/** 对方怎么称呼：她的手机上改成「他」 */
@Composable
private fun PartnerCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var myName by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(Partner.mine) }
    var custom by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Strong("称呼")
        Label("每个人设自己的，同步给对方：你设「哥哥」，对方手机上就显示「哥哥付」「哥哥补你」。", size = 12.sp, weight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("我叫", modifier = Modifier.width(44.dp))
            com.personal.carnet.ui.components.GlassField(myName, { myName = it.take(Partner.MAX) }, "对方那边怎么叫你", Modifier.weight(1f))
            Box(Modifier.glass(RoundedCornerShape(14.dp)).jelly {
                Partner.setMine(context, myName)
            }.padding(horizontal = 14.dp, vertical = 10.dp)) { Strong("保存", size = 13.sp, color = Palette.Violet) }
        }
        val remote = Partner.remote
        if (remote != null) {
            Label("对方自己设的称呼是「$remote」，界面上都这么叫。", size = 12.sp, weight = FontWeight.Bold, color = Palette.Violet)
        } else {
            Label("对方还没设（或者还没同步），先用你给的称呼：", size = 12.sp, weight = FontWeight.Medium)
            com.personal.carnet.ui.components.ChipRow(listOf("她" to "她", "他" to "他"), Partner.local.takeIf { it == "她" || it == "他" }) {
                Partner.set(context, it)
                custom = ""
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.personal.carnet.ui.components.GlassField(
                    custom, { custom = it.take(Partner.MAX) },
                    if (Partner.local != "她" && Partner.local != "他") "现在是「${Partner.local}」" else "或者自己起个称呼",
                    Modifier.weight(1f),
                )
                Box(Modifier.glass(RoundedCornerShape(14.dp)).jelly {
                    if (custom.isNotBlank()) { Partner.set(context, custom); custom = "" }
                }.padding(horizontal = 14.dp, vertical = 10.dp)) { Strong("用这个", size = 13.sp, color = Palette.Violet) }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).jelly(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            LineIcon(icon, Palette.Ink, size = 20.dp)
        }
        Column(Modifier.weight(1f)) {
            Strong(title)
            Label(sub, size = 12.sp, weight = FontWeight.Medium)
        }
        LineIcon("M9 5l7 7-7 7", Palette.Muted, size = 16.dp, width = 2f)
    }
}

@Composable
private fun UpdateCard(update: UpdateState, currentBuild: Long, onCheck: () -> Unit, onInstall: (com.personal.carnet.update.UpdateInfo) -> Unit) {
    Column(Modifier.fillMaxWidth().holo(RoundedCornerShape(26.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Strong("应用更新")
                Label("现在是 build $currentBuild", size = 12.sp)
            }
        }
        when (update) {
            is UpdateState.Available -> {
                Strong("有新版本 build ${update.info.build}", size = 15.sp, color = Palette.Violet)
                update.info.changes.take(12).forEach { Label("· $it", size = 12.sp, weight = FontWeight.Medium) }
                CtaButton("下载并安装", onClick = { onInstall(update.info) })
            }
            is UpdateState.Downloading -> {
                Label("正在下载 build ${update.info.build}…", size = 13.sp, weight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth(update.progress.coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Cta))
                }
            }
            UpdateState.Checking -> Label("正在检查…", size = 13.sp)
            UpdateState.Latest -> {
                Label("已经是最新版了", size = 13.sp, weight = FontWeight.Bold)
                CtaButton("再查一次", onClick = onCheck)
            }
            is UpdateState.Failed -> {
                Label(update.message, size = 13.sp, color = Palette.Rose, weight = FontWeight.Bold)
                CtaButton("再试一次", onClick = onCheck)
            }
            UpdateState.Idle -> CtaButton("检查更新", onClick = onCheck)
        }
        Label("每次打开都会自动查一次。新版装好后会发通知，点一下就回到 Carnet，数据都在。", size = 12.sp, weight = FontWeight.Medium)
    }
}
