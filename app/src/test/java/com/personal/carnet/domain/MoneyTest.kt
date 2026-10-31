package com.personal.carnet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun 解析金额() {
        assertEquals(123450L, Money.parse("1,234.5"))
        assertEquals(1999L, Money.parse("19.99"))
        assertEquals(50L, Money.parse(".5"))
        assertEquals(800L, Money.parse("8"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }

    @Test
    fun 键盘输入以小数点结尾也能算() {
        assertEquals(1200L, Money.fromInput("12."))
        assertEquals(0L, Money.fromInput(""))
    }

    @Test
    fun 格式化金额() {
        assertEquals("1,234.50", Money.format(123450))
        assertEquals("0.05", Money.format(5))
        assertEquals("-12.00", Money.format(-1200))
        assertEquals("-S$12.00", Money.format(-1200, Currency.SGD))
        assertEquals("¥1,000,000.00", Money.format(100000000, Currency.CNY))
    }

    @Test
    fun 新币换人民币() {
        val rate = FxRate(5.30)
        assertEquals(530000L, rate.convert(100000, Currency.SGD, Currency.CNY))
        assertEquals(1660L, rate.convert(8800, Currency.CNY, Currency.SGD))
        assertEquals(8800L, rate.convert(8800, Currency.CNY, Currency.CNY))
    }

    @Test
    fun 汇率显示() {
        assertEquals("5.30", FxRate.format(5.3))
        assertEquals("5.2846", FxRate.format(5.28456))
    }
}
