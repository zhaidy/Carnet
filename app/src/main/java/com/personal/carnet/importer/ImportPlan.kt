package com.personal.carnet.importer

import com.personal.carnet.data.Account
import com.personal.carnet.data.AccountType

/** 表里的一个账户名对应到 Carnet 的哪里 */
sealed interface AccountChoice {
    data class Existing(val accountId: Long) : AccountChoice
    data class Create(val type: AccountType) : AccountChoice
    /** 她的卡：记成她付的，不动你的账户 */
    data object Hers : AccountChoice

    /** 给标签用的 key */
    val key: String
        get() = when (this) {
            is Existing -> "acc:$accountId"
            is Create -> "new:${type.name}"
            Hers -> "her"
        }

    companion object {
        fun fromKey(key: String): AccountChoice = when {
            key == "her" -> Hers
            key.startsWith("acc:") -> Existing(key.removePrefix("acc:").toLong())
            else -> Create(AccountType.valueOf(key.removePrefix("new:")))
        }

        /** 先猜一个：同名的已有账户 → 名字里有「宝宝」「她」→ 钱包 → 带「卡」的当信用卡 */
        fun suggest(name: String, accounts: List<Account>): AccountChoice {
            accounts.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return Existing(it.id) }
            val lower = name.lowercase()
            return when {
                "宝宝" in name || "她" in name -> Hers
                "pay" in lower || "钱包" in name || "支付宝" in name || "微信" in name -> Create(AccountType.WALLET)
                "现金" in name -> Create(AccountType.CASH)
                "储蓄" in name || "借记" in name -> Create(AccountType.DEBIT)
                else -> Create(AccountType.CREDIT)
            }
        }
    }
}

/** 导入完了：导进去多少，跳过多少重复的 */
data class ImportOutcome(val imported: Int, val duplicates: Int)
