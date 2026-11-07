package com.personal.carnet.ai

import com.personal.carnet.domain.Currency
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScreenshotReadTest {

    private fun reply(vararg items: String) = JSONObject("""{"payments":[${items.joinToString(",")}]}""")

    private fun item(
        kind: String = "expense", amount: String = "12.80", currency: String = "SGD", category: String = "food",
        merchant: String = "Toast Box", date: String = "2026-11-06", last4: String = "", account: String = "",
    ) = """{"kind":"$kind","amount":"$amount","currency":"$currency","category":"$category","merchant":"$merchant","date":"$date","last4":"$last4","account":"$account"}"""

    @Test
    fun 一笔付款() {
        val r = Assistant.parseScreenshot(reply(item(last4 = "1234", account = "DBS 信用卡")))
        assertEquals(1, r.size)
        val p = r.first()
        assertEquals(false, p.income)
        assertEquals(1280L, p.cents)
        assertEquals(Currency.SGD, p.currency)
        assertEquals("food", p.category)
        assertEquals("Toast Box", p.note)
        assertEquals(LocalDate.of(2026, 11, 6), p.date)
        assertEquals("1234", p.last4)
        assertEquals("DBS 信用卡", p.account)
    }

    @Test
    fun 看不出的留空() {
        val p = Assistant.parseScreenshot(reply(item(currency = "UNKNOWN", date = "", merchant = ""))).first()
        assertNull(p.currency)
        assertNull(p.date)
        assertNull(p.last4)
        assertNull(p.account)
        assertEquals("", p.note)
    }

    @Test
    fun 日期写错了就当没看出() {
        assertNull(Assistant.parseScreenshot(reply(item(date = "11月6日"))).first().date)
    }

    @Test
    fun 金额认不出或是零的跳过() {
        val r = Assistant.parseScreenshot(reply(item(amount = "abc"), item(amount = "0"), item(amount = "-5.00"), item(amount = "1,280.50")))
        assertEquals(listOf(500L, 128050L), r.map { it.cents })
    }

    @Test
    fun 收入和人民币() {
        val p = Assistant.parseScreenshot(reply(item(kind = "income", currency = "CNY", category = "salary", amount = "¥8,000"))).first()
        assertTrue(p.income)
        assertEquals(Currency.CNY, p.currency)
        assertEquals(800000L, p.cents)
    }

    @Test
    fun 一笔都没有() {
        assertEquals(0, Assistant.parseScreenshot(JSONObject("""{"payments":[]}""")).size)
    }
}
