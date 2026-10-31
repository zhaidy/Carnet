package com.personal.carnet.auto

import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.Money

/** 从一条付款通知里认出来的东西 */
data class WalletPayment(
    val cents: Long,
    val currency: Currency,
    val merchant: String,
    /** 卡号后四位，认不出是 null */
    val last4: String?,
    /** 退款：钱回到卡上 */
    val refund: Boolean,
)

/**
 * 认 Google 钱包的付款通知。手上还没有真实通知的样子，所以不依赖固定格式：
 * 找第一个带币种的金额、卡号后四位，剩下的文字里挑出商户名。认不出时交给 Claude。
 */
object WalletParser {

    const val WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"

    private val amount = Regex("""(S\$|SGD|SG\$|CN¥|RMB|CNY|￥|¥|\$)\s?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")
    private val last4 = Regex("""(?:[•·*●∙]{1,4}|x{2,4}|尾号)\s?(\d{4})""", RegexOption.IGNORE_CASE)
    private val refundWords = listOf("refund", "退款", "退回")
    private val noise = Regex(
        """(?i)\b(you paid|paid|payment|purchase|with|using|via|on|at|card|visa|mastercard|master card|amex|debit|credit)\b|已使用|使用|支付|消费|付款|在|成功|信用卡|借记卡|银行卡""",
    )

    fun parse(title: String?, text: String?): WalletPayment? {
        val t = title.orEmpty().trim()
        val b = text.orEmpty().trim()
        val all = "$t\n$b"
        val m = amount.find(all) ?: return null
        val currency = when (m.groupValues[1].uppercase()) {
            "CN¥", "RMB", "CNY", "￥", "¥" -> Currency.CNY
            else -> Currency.SGD
        }
        val cents = Money.parse(m.groupValues[2]) ?: return null
        if (cents <= 0) return null
        val card = last4.find(all)?.groupValues?.get(1)
        val refund = refundWords.any { all.contains(it, ignoreCase = true) }
        return WalletPayment(cents, currency, merchant(t, b, m.value), card, refund)
    }

    /** 标题里没有金额时标题就是商户；否则从「at XXX」「在 XXX」里取，再不行把杂词去掉剩下的 */
    private fun merchant(title: String, text: String, amountText: String): String {
        if (title.isNotBlank() && !title.contains(amountText) && amount.find(title) == null) return clean(title)
        val all = "$title\n$text"
        Regex("""(?i)\bat\s+([^,\n•·*]+)""").find(all)?.let { return clean(it.groupValues[1]) }
        Regex("""在\s*([^，,\n•·*]+?)\s*(?:消费|付款|支付)""").find(all)?.let { return clean(it.groupValues[1]) }
        val rest = all.replace(amount, " ").replace(last4, " ").replace(noise, " ")
        return clean(rest).ifBlank { "Google 钱包付款" }
    }

    private fun clean(s: String) = s.replace(Regex("""[\s•·*●∙]+"""), " ").trim(' ', '.', ',', '，', '。', '-', '·')
}
