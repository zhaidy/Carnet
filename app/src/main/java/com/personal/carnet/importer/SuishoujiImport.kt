package com.personal.carnet.importer

import com.personal.carnet.data.EntryKind
import com.personal.carnet.domain.Currency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 表里的一笔，分类已经换成 Carnet 的 */
data class ImportedRow(
    val kind: EntryKind,
    val timeMillis: Long,
    val category: String,
    val note: String,
    val account: String,
    val currency: Currency,
    val cents: Long,
)

data class ParsedSheet(val rows: List<ImportedRow>, val skipped: Int, val skippedReasons: List<String>)

/**
 * 随手记导出的表：交易类型、日期、一级分类、二级分类、支出账户、账户币种、金额、成员、商家、项目分类、项目、记账人、备注。
 * 列按表头名字找，顺序变了也没关系；转账和认不出币种的跳过。
 */
object SuishoujiImport {

    fun parse(sheet: List<List<String>>, zone: ZoneId = ZoneId.systemDefault()): ParsedSheet {
        val headerIndex = sheet.indexOfFirst { row -> row.any { it.trim() == "交易类型" } && row.any { it.trim() == "金额" } }
        if (headerIndex < 0) throw ImportException("没找到表头：要有「交易类型」和「金额」两列，是随手记导出的表吗？")
        val header = sheet[headerIndex].map { it.trim() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val cType = col("交易类型")
        val cDate = col("日期", "时间")
        val cCat1 = col("一级分类", "分类")
        val cCat2 = col("二级分类", "子分类")
        val cAccount = col("账户", "支出账户", "收入账户")
        val cOut = col("支出账户")
        val cIn = col("收入账户")
        val cCurrency = col("账户币种", "币种")
        val cAmount = col("金额")
        val cNote = col("备注")
        val cMerchant = col("商家")

        val out = mutableListOf<ImportedRow>()
        val reasons = mutableListOf<String>()
        for (raw in sheet.drop(headerIndex + 1)) {
            fun cell(i: Int) = if (i >= 0) raw.getOrNull(i)?.trim().orEmpty() else ""
            if (raw.all { it.isBlank() }) continue
            val kind = when (cell(cType)) {
                "支出" -> EntryKind.EXPENSE
                "收入" -> EntryKind.INCOME
                else -> { reasons += "「${cell(cType)}」类型的不导入"; continue }
            }
            val time = parseTime(cell(cDate), zone) ?: run { reasons += "日期认不出：${cell(cDate)}"; null } ?: continue
            val cents = parseAmount(cell(cAmount)) ?: run { reasons += "金额认不出：${cell(cAmount)}"; null } ?: continue
            val currency = parseCurrency(cell(cCurrency)) ?: run { reasons += "币种不支持：${cell(cCurrency)}"; null } ?: continue
            val account = listOf(cell(cAccount), cell(if (kind == EntryKind.EXPENSE) cOut else cIn)).firstOrNull { it.isNotBlank() } ?: "未知账户"
            val cat2 = cell(cCat2)
            val note = listOf(cell(cNote), cell(cMerchant)).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { cat2 }
            out += ImportedRow(kind, time, mapCategory(cell(cCat1), cat2, kind), note, account, currency, kotlin.math.abs(cents))
        }
        return ParsedSheet(out, reasons.size, reasons.distinct().take(5))
    }

    private val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/M/d H:mm")
        .map { DateTimeFormatter.ofPattern(it) }

    fun parseTime(text: String, zone: ZoneId): Long? {
        if (text.isBlank()) return null
        for (f in formats) {
            runCatching { return LocalDateTime.parse(text, f).atZone(zone).toInstant().toEpochMilli() }
        }
        runCatching { return LocalDate.parse(text.replace('/', '-')).atTime(12, 0).atZone(zone).toInstant().toEpochMilli() }
        // Excel 的日期是 1899-12-30 起的天数，小数部分是一天里的时间
        text.toDoubleOrNull()?.takeIf { it > 20000 }?.let { serial ->
            val seconds = Math.round(serial * 86400.0)
            return LocalDateTime.of(1899, 12, 30, 0, 0).plusSeconds(seconds).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    fun parseAmount(text: String): Long? = runCatching {
        BigDecimal(text.replace(",", "").replace("¥", "").replace("$", "").trim())
            .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()

    fun parseCurrency(text: String): Currency? = when (text.trim().uppercase()) {
        "", "SGD", "新加坡元", "新币" -> Currency.SGD
        "CNY", "RMB", "人民币" -> Currency.CNY
        else -> null
    }

    /** 随手记的分类 → Carnet 的分类 */
    fun mapCategory(level1: String, level2: String, kind: EntryKind): String {
        if (kind == EntryKind.INCOME) {
            return when {
                "工资" in level2 || "工资" in level1 || "薪" in level2 -> "salary"
                "奖金" in level2 -> "bonus"
                "利息" in level2 || "投资" in level1 || "理财" in level2 -> "invest"
                else -> "other_in"
            }
        }
        val byLevel2 = mapOf(
            "外出美食" to "food", "早午晚餐" to "food", "买菜" to "food",
            "零食饮料" to "drink", "水果零食" to "drink", "烟酒茶" to "drink",
            "超市购物" to "daily", "日常用品" to "daily", "洗护用品" to "daily",
            "衣服裤子" to "shop", "鞋帽包包" to "shop", "电子数码" to "shop",
            "打车" to "ride", "公共交通" to "ride", "地铁" to "ride", "加油" to "ride",
            "电影" to "fun", "景点门票" to "fun", "休闲玩乐" to "fun", "运动健身" to "fun",
            "房租" to "home", "水电煤气" to "home", "物业管理" to "home",
            "手机费" to "phone", "话费" to "phone", "上网费" to "phone",
        )
        byLevel2[level2]?.let { return it }
        return when (level1) {
            "食品酒水" -> "food"
            "购物消费" -> "shop"
            "行车交通" -> "ride"
            "休闲娱乐" -> "fun"
            "居家物业" -> "home"
            "交流通讯" -> "phone"
            else -> "more"
        }
    }
}
