package com.personal.carnet.ui.screens

import com.personal.carnet.ui.Partner

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.data.AccountType
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.importer.AccountChoice
import com.personal.carnet.importer.ParsedSheet
import com.personal.carnet.ui.ImportState
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.components.ChipRow
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.GlassSwitch
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.ScreenHeader
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glass
import com.personal.carnet.ui.glass.holo
import com.personal.carnet.ui.shortDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun ImportScreen(snapshot: Snapshot, vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val state by vm.import.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (bytes, name) = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "表格"
                (resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)) to name
            }
            vm.readImport(bytes, name)
        }
    }
    fun pick() = picker.launch(arrayOf("*/*"))

    LaunchedEffect(state) {
        (state as? ImportState.Done)?.let {
            toast.show("导入了 ${it.outcome.imported} 笔" + if (it.outcome.duplicates > 0) "，跳过 ${it.outcome.duplicates} 笔重复的" else "")
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("导入账单", onBack = { vm.resetImport(); onBack() })
        when (val s = state) {
            is ImportState.Preview -> Preview(snapshot, s.fileName, s.sheet, onPickAgain = ::pick) { choices, ledgerId, settled ->
                vm.runImport(s.sheet.rows, choices, ledgerId, settled)
            }
            ImportState.Reading, ImportState.Importing -> Box(Modifier.padding(16.dp)) {
                GlassCard { Label(if (s == ImportState.Reading) "正在读表…" else "正在导入…", weight = FontWeight.Bold) }
            }
            is ImportState.Done -> Box(Modifier.padding(16.dp)) {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Strong("导入了 ${s.outcome.imported} 笔")
                        if (s.outcome.duplicates > 0) Label("${s.outcome.duplicates} 笔以前导过，跳过了", weight = FontWeight.Medium)
                        Label("账户现在的余额没变；新建的账户余额是 0，记得去账户页填上现在的余额。", size = 12.sp, weight = FontWeight.Medium)
                        CtaButton("好", onClick = { vm.resetImport(); onBack() })
                    }
                }
            }
            is ImportState.Failed -> Box(Modifier.padding(16.dp)) {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Strong(s.message, color = Palette.Rose)
                        CtaButton("换一个文件", onClick = ::pick)
                    }
                }
            }
            ImportState.Idle -> Box(Modifier.padding(16.dp)) {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Strong("导入随手记的账")
                        Label("在随手记里导出成 Excel（.xlsx）或 CSV，存到手机上，再在这里选那个文件。", weight = FontWeight.Medium)
                        Label("会先给你看一遍：多少笔、哪几个月、账户怎么对应，确认了才导。同一个文件导两次也不会记重。", size = 12.sp, weight = FontWeight.Medium)
                        CtaButton("选文件", onClick = ::pick)
                    }
                }
            }
        }
    }
}

@Composable
private fun Preview(
    snapshot: Snapshot,
    fileName: String,
    sheet: ParsedSheet,
    onPickAgain: () -> Unit,
    onImport: (Map<String, AccountChoice>, Long, Boolean) -> Unit,
) {
    val accounts = snapshot.accounts.map { it.account }
    val ledgers = snapshot.ledgers.map { it.ledger }
    val rows = sheet.rows
    val names = remember(rows) { rows.groupingBy { it.account }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val choices = remember(rows) { mutableStateMapOf<String, String>().apply { names.forEach { put(it, AccountChoice.suggest(it, accounts).key) } } }
    var ledgerId by remember { mutableStateOf(ledgers.firstOrNull { it.shared }?.id ?: ledgers.firstOrNull()?.id) }
    var settled by remember { mutableStateOf(true) }
    val ledger = ledgers.firstOrNull { it.id == ledgerId }
    val rate = snapshot.rate
    val zone = ZoneId.systemDefault()

    val totalMain = rows.sumOf { rate.convert(it.cents, it.currency, MainCurrency) }
    val herMain = rows.filter { choices[it.account] == "her" }.sumOf { rate.convert(it.cents, it.currency, MainCurrency) }
    val months = rows.groupBy { YearMonth.from(Instant.ofEpochMilli(it.timeMillis).atZone(zone)) }
        .mapValues { (_, list) -> list.sumOf { rate.convert(it.cents, it.currency, MainCurrency) } }
        .toSortedMap()
    val first = rows.minOf { it.timeMillis }
    val last = rows.maxOf { it.timeMillis }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Strong(fileName, size = 14.sp)
                        Label("${rows.size} 笔 · ${shortDate(first)} – ${shortDate(last)}" + if (sheet.skipped > 0) " · 跳过 ${sheet.skipped} 行" else "", size = 12.sp)
                        sheet.skippedReasons.forEach { Label(it, size = 11.sp, weight = FontWeight.Medium) }
                    }
                    Box(Modifier.padding(start = 8.dp)) { com.personal.carnet.ui.components.PillButton("换一个", onClick = onPickAgain) }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().holo(RoundedCornerShape(28.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Label("一共", weight = FontWeight.Bold)
                        Strong(Money.format(totalMain, MainCurrency), size = 28.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Label("你付 " + Money.format(totalMain - herMain), size = 12.sp)
                        Label("${Partner.name}付 " + Money.format(herMain), size = 12.sp)
                    }
                }
                val max = (months.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    months.values.forEach { v ->
                        Box(Modifier.weight(1f).fillMaxHeight((v.toFloat() / max).coerceAtLeast(0.05f)).clip(RoundedCornerShape(6.dp, 6.dp, 3.dp, 3.dp)).background(Palette.Cta))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    months.keys.forEach { m -> Text("${m.monthValue}月", fontSize = 10.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)) }
                }
            }
        }
        item {
            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipRow(ledgers.map { it.id to it.name }, ledgerId, "记到") { ledgerId = it }
                    Label("收入都记到「个人」。导进来的是以前的账，账户现在的余额不会变。", size = 12.sp, weight = FontWeight.Medium)
                }
            }
        }
        items(names, key = { it }) { name ->
            val count = rows.count { it.account == name }
            val currency = rows.first { it.account == name }.currency
            val options = buildList {
                accounts.filter { it.currency == currency }.forEach { add("acc:${it.id}" to it.name) }
                add("new:${AccountType.CREDIT.name}" to "新建信用卡")
                add("new:${AccountType.DEBIT.name}" to "新建储蓄卡")
                add("new:${AccountType.WALLET.name}" to "新建电子钱包")
                add("her" to "${Partner.name}付的")
            }
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Strong(name, modifier = Modifier.weight(1f))
                    Label("$count 笔 · ${currency.label}", size = 12.sp)
                }
                ChipRow(options, choices[name]) { choices[name] = it }
            }
        }
        if (ledger?.shared == true) {
            item {
                GlassCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Strong("这些账当作已经两清", size = 13.sp)
                            Label(
                                if (settled) "以前自己结过了，不会算出谁多垫了。${shortDate(last)} 之前「${ledger.name}」里的账都算两清。"
                                else "会把这段时间谁多垫了多少算进「${ledger.name}」",
                                size = 12.sp, weight = FontWeight.Medium,
                            )
                        }
                        GlassSwitch(settled, "当作已经两清") { settled = it }
                    }
                }
            }
        }
        item {
            CtaButton("导入 ${rows.size} 笔", sub = "同一笔不会记两次") {
                val id = ledgerId ?: return@CtaButton
                onImport(choices.mapValues { AccountChoice.fromKey(it.value) }, id, settled)
            }
        }
    }
}
