package com.personal.carnet.auto

import android.content.Context
import com.personal.carnet.ai.ClaudeClient
import com.personal.carnet.ai.ClaudeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 自动记账和 Claude 的设置，都只存在本机 */
data class AutoConfig(
    val walletEnabled: Boolean,
    /** 卡号后四位 → 账户 id */
    val cards: Map<String, Long>,
    /** 通知里见过、还没对应账户的卡号后四位 */
    val unknownCards: Set<String>,
    /** 认不出卡时记到哪个账户 */
    val defaultAccountId: Long?,
    /** 自动记的账记到哪个账本，null 是「个人」 */
    val ledgerId: Long?,
    /** 最近自动记下的账 id，总览里显示，可以撤销或改成共用 */
    val recent: List<Long>,
    val claudeKey: String,
    val claudeModel: String,
)

class AutoSettings(context: Context) : ClaudeSettings {
    private val sp = context.getSharedPreferences("auto", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<AutoConfig> = _config.asStateFlow()

    override val apiKey: String get() = _config.value.claudeKey
    override val model: String get() = _config.value.claudeModel

    private fun load() = AutoConfig(
        walletEnabled = sp.getBoolean("wallet", true),
        cards = sp.getString("cards", null)?.let { s -> JSONObject(s).let { o -> o.keys().asSequence().associateWith { o.getLong(it) } } }.orEmpty(),
        unknownCards = sp.getStringSet("unknown_cards", emptySet()).orEmpty(),
        defaultAccountId = sp.getLong("default_account", -1).takeIf { it >= 0 },
        ledgerId = sp.getLong("ledger", -1).takeIf { it >= 0 },
        recent = sp.getString("recent", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { a.getLong(it) } } }.orEmpty(),
        claudeKey = sp.getString("claude_key", "").orEmpty(),
        claudeModel = sp.getString("claude_model", ClaudeClient.DEFAULT_MODEL).orEmpty(),
    )

    @Synchronized
    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).commit()
        _config.value = load()
    }

    fun setWalletEnabled(on: Boolean) = edit { putBoolean("wallet", on) }

    fun setCard(last4: String, accountId: Long) {
        val cards = _config.value.cards + (last4 to accountId)
        edit {
            putString("cards", JSONObject(cards.mapValues { it.value }).toString())
            putStringSet("unknown_cards", _config.value.unknownCards - last4)
        }
    }

    fun noteUnknownCard(last4: String) {
        if (last4 in _config.value.cards || last4 in _config.value.unknownCards) return
        edit { putStringSet("unknown_cards", _config.value.unknownCards + last4) }
    }

    fun setDefaultAccount(id: Long) = edit { putLong("default_account", id) }

    fun setLedger(id: Long) = edit { putLong("ledger", id) }

    fun addRecent(entryId: Long) = edit { putString("recent", JSONArray((listOf(entryId) + _config.value.recent).take(10)).toString()) }

    fun removeRecent(entryId: Long) = edit { putString("recent", JSONArray(_config.value.recent - entryId).toString()) }

    fun setClaude(key: String, model: String) = edit {
        putString("claude_key", key.trim())
        putString("claude_model", model)
    }

    /** 同一条通知（系统重发、更新）只记一次 */
    @Synchronized
    fun firstTime(notificationKey: String): Boolean {
        val seen = sp.getString("seen", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } }.orEmpty()
        if (notificationKey in seen) return false
        sp.edit().putString("seen", JSONArray((listOf(notificationKey) + seen).take(200)).toString()).commit()
        return true
    }

    /** 商户 → 分类，你改过一次就记住 */
    fun merchantCategory(merchant: String): String? = sp.getString("m:" + merchant.lowercase(), null)

    fun rememberMerchant(merchant: String, category: String) {
        sp.edit().putString("m:" + merchant.lowercase(), category).apply()
    }
}
