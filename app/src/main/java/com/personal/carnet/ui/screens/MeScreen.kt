package com.personal.carnet.ui.screens

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
        item { MenuRow(IconPaths.SWAP, "默认汇率", "1 S$ = ¥" + FxRate.format(snapshot.rate.cnyPerSgd) + " · 在账户页改", onOpenAccounts) }
        item { UpdateCard(update, currentBuild, onCheckUpdate, onInstall) }
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
