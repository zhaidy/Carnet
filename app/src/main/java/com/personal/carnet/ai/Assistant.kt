package com.personal.carnet.ai

import com.personal.carnet.auto.WalletPayment
import com.personal.carnet.domain.Categories
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.Money
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 一句话记账认出来的 */
data class Sentence(
    val income: Boolean,
    val cents: Long,
    val currency: Currency?,
    val category: String,
    val note: String,
    val ledger: String?,
    val herPaid: Boolean,
    val account: String?,
)

/** 记账里用 Claude 的几件事：认付款通知、猜商户分类、一句话记账 */
class Assistant(private val claude: ClaudeClient) {

    val configured: Boolean get() = claude.configured

    private val expenseKeys = Categories.expense.map { it.key }
    private val categoryList = Categories.expense.joinToString("、") { "${it.key}=${it.label}" }

    /** 规则认不出的通知交给 Claude；不是付款返回 null */
    suspend fun readNotification(title: String, text: String): WalletPayment? {
        val schema = obj(
            "is_payment" to bool(),
            "amount" to str("金额，只要数字，比如 12.80"),
            "currency" to enumOf(listOf("SGD", "CNY", "OTHER")),
            "merchant" to str("商户名"),
            "last4" to str("卡号后四位，没有就空字符串"),
            "refund" to bool(),
        )
        val prompt = """
            下面是手机上 Google 钱包发的一条通知。判断它是不是一笔刷卡付款（或退款），是的话提取金额、币种、商户名、卡号后四位。
            通知标题：$title
            通知内容：$text
        """.trimIndent()
        val r = claude.json(prompt, schema)
        if (!r.optBoolean("is_payment")) return null
        val cents = Money.parse(r.optString("amount")) ?: return null
        val currency = when (r.optString("currency")) { "SGD" -> Currency.SGD; "CNY" -> Currency.CNY; else -> return null }
        return WalletPayment(cents, currency, r.optString("merchant").ifBlank { "Google 钱包付款" }, r.optString("last4").takeIf { it.length == 4 }, r.optBoolean("refund"))
    }

    /** 猜一个商户是什么分类 */
    suspend fun categorize(merchant: String): String {
        val schema = obj("category" to enumOf(expenseKeys))
        val prompt = "在新加坡或中国刷卡付给「$merchant」，最可能是哪一类开销？分类：$categoryList"
        return claude.json(prompt, schema).optString("category").takeIf { it in expenseKeys } ?: "more"
    }

    /** 一句话记账：「昨晚和她吃火锅 86，我付的」 */
    suspend fun understand(sentence: String, ledgers: List<String>, accounts: List<String>): Sentence {
        val incomeKeys = Categories.income.map { it.key }
        val schema = obj(
            "kind" to enumOf(listOf("expense", "income")),
            "amount" to str("金额，只要数字"),
            "currency" to enumOf(listOf("SGD", "CNY", "UNKNOWN")),
            "category" to enumOf(expenseKeys + incomeKeys),
            "note" to str("简短的备注，比如 火锅"),
            "ledger" to enumOf(ledgers + ""),
            "her_paid" to bool(),
            "account" to enumOf(accounts + ""),
        )
        val prompt = """
            把这句记账的话拆成字段。今天是 ${LocalDate.now()}。
            - 和女朋友一起花的、说了「我们」「和她」「AA」的，ledger 选共用的账本；没说就留空
            - 她付的 her_paid = true
            - 说了用哪张卡 / 支付宝 / 微信付的，account 选对应的账户，没说留空
            - 没说币种是 UNKNOWN；「块」「元」在国内通常是人民币
            支出分类：$categoryList；收入分类：${Categories.income.joinToString("、") { "${it.key}=${it.label}" }}
            账本：${ledgers.joinToString("、")}
            账户：${accounts.joinToString("、")}
            这句话：$sentence
        """.trimIndent()
        val r = claude.json(prompt, schema)
        val cents = Money.parse(r.optString("amount")) ?: throw AiException("没听出金额")
        return Sentence(
            income = r.optString("kind") == "income",
            cents = cents,
            currency = when (r.optString("currency")) { "SGD" -> Currency.SGD; "CNY" -> Currency.CNY; else -> null },
            category = r.optString("category"),
            note = r.optString("note"),
            ledger = r.optString("ledger").ifBlank { null },
            herPaid = r.optBoolean("her_paid"),
            account = r.optString("account").ifBlank { null },
        )
    }

    /** 设置页里「试一下」：发一个最小的请求看 key 能不能用 */
    suspend fun ping(): String {
        val r = claude.json("回答 ok", obj("answer" to str("")))
        return r.optString("answer").ifBlank { "ok" }
    }

    private fun obj(vararg props: Pair<String, JSONObject>): JSONObject {
        val properties = JSONObject()
        props.forEach { (k, v) -> properties.put(k, v) }
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(props.map { it.first }))
            .put("additionalProperties", false)
    }

    private fun str(description: String) = JSONObject().put("type", "string").apply { if (description.isNotBlank()) put("description", description) }
    private fun bool() = JSONObject().put("type", "boolean")
    private fun enumOf(values: List<String>) = JSONObject().put("type", "string").put("enum", JSONArray(values.distinct()))
}
