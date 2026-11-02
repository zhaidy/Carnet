package com.personal.carnet.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.data.Account
import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.Payer
import com.personal.carnet.domain.CalendarMonth
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.ui.LineIcon
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glassSphere
import com.personal.carnet.ui.glass.glass
import com.personal.carnet.ui.glass.jelly
import com.personal.carnet.ui.line
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

/** 气泡的颜色（中心偏亮, 边缘浓色） */
private val PersonalBubble = Color(0xFF6E62F2)
private val MeShared = Color(0xFF6E62F2)
private val HerShared = Color(0xFFF07AAE)
private val IncomeBubble = Color(0xFF22B39A)
private val TransferBubble = Color(0xFF8A93B8)

/**
 * 最近的账：按天冒出来的一串玻璃气泡。花得越多气泡越大（按面积算，房租不会把别的挤没），
 * 共用账本的是蓝粉双色（谁付的那边颜色更浓），进账是绿的，转账是灰的。点气泡打开那一笔
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BubbleStream(
    entries: List<Entry>,
    accounts: Map<Long, Account>,
    ledgers: Map<Long, Ledger>,
    rate: FxRate,
    onOpen: (Entry) -> Unit,
    days: Int = 7,
) {
    val zone = ZoneId.systemDefault()
    val byDay = entries.groupBy { Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it }).entries.take(days)
    val main = { e: Entry -> rate.convert(e.amountCents, e.currency, MainCurrency) }
    // 只按支出比大小：工资那一颗不该把别的都压成小点；进账、转账的大小封顶。
    // 基准至少按 S$200 算，只有几笔小钱的时候气泡也是小的
    val biggest = byDay.flatMap { it.value }.filter { it.kind == EntryKind.EXPENSE }.maxOfOrNull(main)?.coerceAtLeast(20_000) ?: 20_000
    val today = LocalDate.now()

    Column(
        Modifier.fillMaxWidth().glass().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        byDay.forEach { (date, list) ->
            // 左边一栏是哪天、花了多少，右边是那天冒出来的气泡
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(64.dp)) {
                    Strong(
                        when (date) {
                            today -> "今天"
                            today.minusDays(1) -> "昨天"
                            else -> "${date.monthValue}/${date.dayOfMonth}"
                        },
                        size = 14.sp,
                    )
                    val spent = list.filter { it.kind == EntryKind.EXPENSE }.sumOf(main)
                    if (spent > 0) Label(CalendarMonth.compact(spent).let { "花 $it" }, size = 11.sp)
                }
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy((-6).dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    list.sortedByDescending(main).forEach { e ->
                        val share = sqrt((main(e).toFloat() / biggest).coerceAtMost(1f))
                        Bubble(e, (36 + 48 * share).dp, accounts, ledgers) { onOpen(e) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(entry: Entry, size: Dp, accounts: Map<Long, Account>, ledgers: Map<Long, Ledger>, onClick: () -> Unit) {
    val line = entry.line(accounts, ledgers)
    val shared = ledgers[entry.ledgerId]?.shared == true
    val (a, b) = when {
        entry.kind == EntryKind.INCOME -> IncomeBubble to IncomeBubble
        entry.kind == EntryKind.TRANSFER -> TransferBubble to TransferBubble
        shared && entry.payer == Payer.HER -> HerShared to MeShared
        shared -> MeShared to HerShared
        else -> PersonalBubble to PersonalBubble
    }
    Box(
        Modifier.size(size)
            .semantics { contentDescription = line.title + " " + line.amount }
            .jelly(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { glassSphere(center, this.size.minDimension / 2, a, b, shadow = false) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LineIcon(line.icon, Palette.Ink, size = (size.value * 0.34f).coerceIn(14f, 24f).dp, width = 1.9f)
            if (size >= 50.dp) {
                Text(
                    (if (entry.kind == EntryKind.INCOME) "+" else "") + CalendarMonth.compact(entry.amountCents),
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink,
                )
            }
        }
    }
}
