package com.personal.carnet.auto

import com.personal.carnet.domain.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletParserTest {

    @Test
    fun 标题是商户正文是金额和卡() {
        assertEquals(
            WalletPayment(560, Currency.SGD, "Toast Box", "4821", refund = false),
            WalletParser.parse("Toast Box", "S$5.60 with Visa •••• 4821"),
        )
    }

    @Test
    fun 标题里有金额和商户() {
        val p = WalletParser.parse("S$12.80 at FairPrice Finest", "Mastercard ••7730")!!
        assertEquals(1280L, p.cents)
        assertEquals("FairPrice Finest", p.merchant)
        assertEquals("7730", p.last4)
    }

    @Test
    fun 中文通知() {
        val p = WalletParser.parse("咖啡店", "已使用 Visa ··4821 支付 S$5.60")!!
        assertEquals("咖啡店", p.merchant)
        assertEquals(560L, p.cents)
        assertEquals("4821", p.last4)
    }

    @Test
    fun 人民币和千分位() {
        val p = WalletParser.parse("在 全家便利店 消费 ¥1,288.00", "尾号 4821")!!
        assertEquals(Currency.CNY, p.currency)
        assertEquals(128800L, p.cents)
        assertEquals("全家便利店", p.merchant)
    }

    @Test
    fun 退款() {
        val p = WalletParser.parse("Refund from Shopee", "SGD 38.90 to Visa ••4821")!!
        assertEquals(true, p.refund)
        assertEquals(3890L, p.cents)
    }

    @Test
    fun 没金额的通知不管() {
        assertNull(WalletParser.parse("Card added", "Your Visa ••4821 is ready to use"))
    }
}
