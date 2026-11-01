package com.personal.carnet.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTest {

    @Test
    fun 分不尽的一分钱四舍五入() {
        assertEquals(50L, Split.myShare(99, 50))
        assertEquals(33L, Split.myShare(100, 33))
    }

    @Test
    fun 说法用垫和补() {
        assertEquals("她补你 S$236.75 就两清", Split.describe(23675, Currency.SGD))
        assertEquals("你补她 ¥58.20 就两清", Split.describe(-5820, Currency.CNY))
        assertEquals("现在两清", Split.describe(0, Currency.SGD))
    }

    @Test
    fun 各自付了多少和各自那份() {
        val s = Split.summary(listOf(SharedExpense(10000, true), SharedExpense(6000, false), SharedExpense(4001, true)), 50)
        assertEquals(14001L, s.paidByMe)
        assertEquals(6000L, s.paidByHer)
        assertEquals(20001L, s.total)
        assertEquals(s.total, s.myShare + s.herShare)
        assertEquals(Split.balance(listOf(SharedExpense(10000, true), SharedExpense(6000, false), SharedExpense(4001, true)), 50), s.paidByMe - s.myShare)
    }

    @Test
    fun 对方的称呼可以换() {
        assertEquals("她补你 S$10.00 就两清", Split.describe(1000, Currency.SGD))
        assertEquals("他补你 S$10.00 就两清", Split.describe(1000, Currency.SGD, "他"))
        assertEquals("你补宝宝 S$10.00 就两清", Split.describe(-1000, Currency.SGD, "宝宝"))
    }

    @Test
    fun 她出固定金额() {
        // 房租 3300 我付，她固定出 1200：她补我 1200，我那份 2100
        val rent = SharedExpense(330000, paidByMe = true, herCents = 120000)
        assertEquals(210000L, Split.myPart(330000, 120000, 50))
        assertEquals(120000L, Split.balance(listOf(rent), 50))
        val s = Split.summary(listOf(rent, SharedExpense(10000, false)), 50)
        assertEquals(210000L + 5000L, s.myShare)
        assertEquals(120000L + 5000L, s.herShare)
        // 填多了按总数封顶
        assertEquals(0L, Split.myPart(1000, 5000, 50))
    }
}
