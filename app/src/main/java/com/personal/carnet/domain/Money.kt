package com.personal.carnet.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class Currency(val code: String, val symbol: String, val label: String) {
    SGD("SGD", "S$", "新币"),
    CNY("CNY", "¥", "人民币");

    companion object {
        fun of(code: String?): Currency? = entries.firstOrNull { it.code == code }
    }
}

/** 主货币：净资产、统计都换算成它 */
val MainCurrency = Currency.SGD

/** 金额一律用「分」存成 Long，避免浮点误差 */
object Money {

    /** "1,234.5" → 123450；不是合法金额返回 null。最多两位小数 */
    fun parse(text: String): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        if (!Regex("""-?\d+(\.\d{0,2})?|-?\.\d{1,2}""").matches(cleaned)) return null
        return BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    /** 123450 → "1,234.50" */
    fun format(cents: Long): String {
        val abs = BigDecimal.valueOf(kotlin.math.abs(cents)).movePointLeft(2).setScale(2)
        val parts = abs.toPlainString().split('.')
        val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed()
        return (if (cents < 0) "-" else "") + grouped + "." + parts[1]
    }

    /** 带币种符号，负数把负号放在最前面："-S$12.00" */
    fun format(cents: Long, currency: Currency): String =
        (if (cents < 0) "-" else "") + currency.symbol + format(kotlin.math.abs(cents))

    /** 键盘上输入的字符串（可能以小数点结尾）转成分，空的算 0 */
    fun fromInput(text: String): Long = parse(text.trimEnd('.').ifEmpty { "0" }) ?: 0
}

/**
 * 汇率统一写成「1 新币 = 多少人民币」，用户看到和填的都是这个数。
 */
data class FxRate(val cnyPerSgd: Double) {

    init {
        require(cnyPerSgd > 0) { "汇率必须大于 0" }
    }

    fun convert(cents: Long, from: Currency, to: Currency): Long {
        if (from == to) return cents
        val amount = BigDecimal.valueOf(cents)
        val rate = BigDecimal.valueOf(cnyPerSgd)
        val result = when (from) {
            Currency.SGD -> amount.multiply(rate)
            Currency.CNY -> amount.divide(rate, 6, RoundingMode.HALF_UP)
        }
        return result.setScale(0, RoundingMode.HALF_UP).toLong()
    }

    companion object {
        fun parse(text: String): FxRate? = text.trim().toDoubleOrNull()?.takeIf { it > 0 }?.let(::FxRate)

        fun format(rate: Double): String =
            BigDecimal.valueOf(rate).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().let {
                if (it.scale() < 2) it.setScale(2) else it
            }.toPlainString()
    }
}

/**
 * 转账、换汇：转出、汇率、到账三个数填任意两个，算出第三个。
 */
object TransferMath {

    /** 按汇率算到账金额 */
    fun toAmount(fromCents: Long, from: Currency, to: Currency, rate: FxRate): Long =
        rate.convert(fromCents, from, to)

    /** 填了转出和到账，反推实际汇率（1 新币 = 多少人民币）；同币种或有一边是 0 时没有汇率 */
    fun impliedRate(fromCents: Long, from: Currency, toCents: Long, to: Currency): FxRate? {
        if (from == to || fromCents <= 0 || toCents <= 0) return null
        val sgd = if (from == Currency.SGD) fromCents else toCents
        val cny = if (from == Currency.CNY) fromCents else toCents
        val rate = BigDecimal.valueOf(cny).divide(BigDecimal.valueOf(sgd), 4, RoundingMode.HALF_UP)
        return FxRate(rate.toDouble())
    }

    /** 实际到账比按默认汇率算的多多少（负数是少到账，算手续费） */
    fun differenceFromDefault(fromCents: Long, from: Currency, toCents: Long, to: Currency, default: FxRate): Long =
        toCents - default.convert(fromCents, from, to)
}
