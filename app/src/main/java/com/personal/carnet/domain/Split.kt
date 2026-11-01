package com.personal.carnet.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** 共用账本里的一笔开销，金额已经换成账本货币 */
data class SharedExpense(val cents: Long, val paidByMe: Boolean, val herCents: Long? = null)

/** 共用账本一段时间里：各自付了多少，各自那份是多少（balance = paidByMe - myShare） */
data class SplitSummary(val paidByMe: Long, val paidByHer: Long, val myShare: Long, val herShare: Long) {
    val total: Long get() = paidByMe + paidByHer
}

/**
 * AA 的账：谁多垫了多少。
 * 结果是正数：你多垫了，她补你这么多就两清；负数：她多垫了，你补她。
 */
object Split {

    fun summary(expenses: List<SharedExpense>, myPercent: Int): SplitSummary {
        val me = expenses.filter { it.paidByMe }.sumOf { it.cents }
        val her = expenses.filter { !it.paidByMe }.sumOf { it.cents }
        val mine = expenses.sumOf { myPart(it.cents, it.herCents, myPercent) }
        return SplitSummary(me, her, mine, me + her - mine)
    }

    /** 一笔里你那份；myPercent 是你出的百分比，平分就是 50 */
    fun myShare(cents: Long, myPercent: Int): Long =
        BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(myPercent.toLong()))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).toLong()

    /** 一笔里你那份：对方出固定金额时是剩下的，不然按比例 */
    fun myPart(cents: Long, herCents: Long?, myPercent: Int): Long =
        herCents?.let { (cents - it).coerceIn(0, cents) } ?: myShare(cents, myPercent)

    fun balance(expenses: List<SharedExpense>, myPercent: Int): Long = expenses.sumOf { e ->
        val mine = myPart(e.cents, e.herCents, myPercent)
        if (e.paidByMe) e.cents - mine else -mine
    }

    /** 你自己的花销里，这些共用开销算你多少 */
    fun mySpending(expenses: List<SharedExpense>, myPercent: Int): Long =
        expenses.sumOf { myPart(it.cents, it.herCents, myPercent) }

    /** 给人看的一句话 */
    fun describe(balance: Long, currency: Currency, who: String = "她"): String = when {
        balance > 0 -> "${who}补你 ${Money.format(balance, currency)} 就两清"
        balance < 0 -> "你补$who ${Money.format(-balance, currency)} 就两清"
        else -> "现在两清"
    }
}
