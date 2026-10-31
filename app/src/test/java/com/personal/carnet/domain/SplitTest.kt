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
}
