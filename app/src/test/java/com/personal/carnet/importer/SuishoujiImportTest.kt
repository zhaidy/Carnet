package com.personal.carnet.importer

import com.personal.carnet.data.EntryKind
import com.personal.carnet.domain.Currency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SuishoujiImportTest {

    private val zone = ZoneId.of("Asia/Singapore")
    private val header = listOf("交易类型", "日期", "一级分类", "二级分类", "支出账户", "账户币种", "金额", "成员", "商家", "项目分类", "项目", "记账人", "备注")

    private fun millis(s: String) = LocalDateTime.parse(s.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun 读随手记的支出() {
        val sheet = listOf(
            header,
            listOf("支出", "2026-10-31 19:48:42", "行车交通", "打车  ", "yuu卡", "SGD", "9.5", "dy", "", "", "", "dy", ""),
            listOf("支出", "2026-10-31 16:34:50", "食品酒水", "外出美食  ", "yuu卡", "SGD", "18", "dy", "", "", "", "dy", "小鸟充值"),
            listOf("支出", "2026-10-27 19:58:53", "购物消费", "超市购物", "宝宝卡", "SGD", "87.17", "yh", "超市", "", "", "dy", ""),
            listOf("转账", "2026-10-27 19:58:53", "", "", "yuu卡", "SGD", "100", "dy", "", "", "", "dy", ""),
        )
        val parsed = SuishoujiImport.parse(sheet, zone)
        assertEquals(3, parsed.rows.size)
        assertEquals(1, parsed.skipped)
        val (taxi, food, market) = parsed.rows
        assertEquals(ImportedRow(EntryKind.EXPENSE, millis("2026-10-31 19:48:42"), "ride", "打车", "yuu卡", Currency.SGD, 950), taxi)
        assertEquals("小鸟充值", food.note)
        assertEquals("food", food.category)
        assertEquals("daily", market.category)
        assertEquals("宝宝卡", market.account)
        assertEquals("超市", market.note)
        assertEquals(8717L, market.cents)
    }

    @Test
    fun 人民币和日期的几种写法() {
        assertEquals(Currency.CNY, SuishoujiImport.parseCurrency("RMB"))
        assertEquals(null, SuishoujiImport.parseCurrency("USD"))
        assertEquals(millis("2026-03-01 01:43:00"), SuishoujiImport.parseTime("2026/03/01 01:43", zone))
        // Excel 日期序号 46082.5 = 2026-03-01 12:00
        assertEquals(millis("2026-03-01 12:00:00"), SuishoujiImport.parseTime("46082.5", zone))
    }

    @Test
    fun 没有表头报错说清楚() {
        val e = runCatching { SuishoujiImport.parse(listOf(listOf("a", "b"))) }.exceptionOrNull()
        assertEquals(true, e is ImportException)
    }

    @Test
    fun 读xlsx文件() {
        val bytes = xlsx(
            """<sst><si><t>交易类型</t></si><si><t>金额</t></si><si><t>支出</t></si><si><r><t>外出</t></r><r><t>美食</t></r></si></sst>""",
            """<worksheet><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row>
                <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c><c r="C2"><v>18.94</v></c></row>
            </sheetData></worksheet>""",
        )
        assertEquals(listOf(listOf("交易类型", "", "金额"), listOf("支出", "外出美食", "18.94")), SheetReader.read(bytes))
    }

    @Test
    fun 读csv() {
        val rows = SheetReader.read("﻿交易类型,金额,备注\n支出,12.5,\"有,逗号\"\r\n".toByteArray())
        assertEquals(listOf(listOf("交易类型", "金额", "备注"), listOf("支出", "12.5", "有,逗号")), rows)
    }

    @Test
    fun 列号() {
        assertEquals(0, SheetReader.columnIndex("A1"))
        assertEquals(27, SheetReader.columnIndex("AB12"))
    }

    private fun xlsx(shared: String, sheet: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            put("xl/workbook.xml", """<workbook xmlns:r="r"><sheets><sheet name="支出" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""")
            put("xl/sharedStrings.xml", shared)
            put("xl/worksheets/sheet1.xml", sheet)
        }
        return out.toByteArray()
    }
}
