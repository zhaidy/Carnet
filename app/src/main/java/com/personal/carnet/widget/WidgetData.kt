package com.personal.carnet.widget

import com.personal.carnet.ui.Partner

import com.personal.carnet.domain.MainCurrency
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import java.time.LocalDate

/** 小组件上显示的东西，全是排好的文字，方便比较有没有变（没变就不刷新） */
data class WidgetData(
    /** 大字：发薪前还能花；没设工资时显示净资产 */
    val headline: String,
    val headlineLabel: String,
    val monthSpent: String,
    /** 共用账本谁补谁；没有共用账本是 null */
    val shared: String?,
    /** 接下来的固定收支：日期、名字、金额 */
    val upcoming: List<Triple<String, String, String>>,
) {
    companion object {
        fun from(s: Snapshot, today: LocalDate = LocalDate.now()): WidgetData {
            val plan = s.plan
            val spendable = plan.spendableCents
            val payday = plan.payday
            val (headline, label) = if (spendable != null && payday != null) {
                Money.format(spendable, MainCurrency) to "${payday.monthValue}/${payday.dayOfMonth} 发薪前还能花"
            } else {
                Money.format(s.netWorthCents, MainCurrency) to "净资产"
            }
            val shared = if (s.ledgers.none { it.ledger.shared }) null else when {
                s.sharedBalanceCents > 0 -> "${Partner.name}补你 " + Money.format(s.sharedBalanceCents, MainCurrency)
                s.sharedBalanceCents < 0 -> "你补${Partner.name} " + Money.format(-s.sharedBalanceCents, MainCurrency)
                else -> "和${Partner.name}两清了"
            }
            val upcoming = plan.upcoming.filter { !it.date.isBefore(today) }.take(4).map { occ ->
                val day = if (occ.date == today) "今天" else "${occ.date.monthValue}/${occ.date.dayOfMonth}"
                val amount = (if (occ.estimate) "≈" else "") + (if (occ.inflow) "+" else "-") + Money.format(occ.cents, occ.currency)
                Triple(day, occ.rule.name, amount)
            }
            return WidgetData(headline, label, Money.format(s.monthMySpendingCents, MainCurrency), shared, upcoming)
        }
    }
}
