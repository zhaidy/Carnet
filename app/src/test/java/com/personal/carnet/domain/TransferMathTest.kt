package com.personal.carnet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferMathTest {

    private val default = FxRate(5.30)

    @Test
    fun 填转出和汇率算出到账() {
        assertEquals(528000L, TransferMath.toAmount(100000, Currency.SGD, Currency.CNY, FxRate(5.28)))
    }

    @Test
    fun 填转出和到账算出实际汇率() {
        val rate = TransferMath.impliedRate(100000, Currency.SGD, 528000, Currency.CNY)!!
        assertEquals(5.28, rate.cnyPerSgd, 1e-9)
    }

    @Test
    fun 人民币转回新币也按一新币多少人民币算() {
        val rate = TransferMath.impliedRate(530000, Currency.CNY, 100000, Currency.SGD)!!
        assertEquals(5.30, rate.cnyPerSgd, 1e-9)
    }

    @Test
    fun 同币种没有汇率() {
        assertNull(TransferMath.impliedRate(100000, Currency.SGD, 100000, Currency.SGD))
        assertNull(TransferMath.impliedRate(0, Currency.SGD, 100, Currency.CNY))
    }

    @Test
    fun 比默认汇率少到账的部分算手续费() {
        assertEquals(-2000L, TransferMath.differenceFromDefault(100000, Currency.SGD, 528000, Currency.CNY, default))
    }
}
