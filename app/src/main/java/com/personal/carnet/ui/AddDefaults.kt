package com.personal.carnet.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 记账页默认记到哪个账本、从哪个账户付；没设是 null（个人账本、第一个账户） */
object AddDefaults {
    var ledgerId by mutableStateOf<Long?>(null)
        private set
    var accountId by mutableStateOf<Long?>(null)
        private set

    fun load(context: Context) {
        val sp = prefs(context)
        ledgerId = sp.getLong(KEY_LEDGER, -1).takeIf { it >= 0 }
        accountId = sp.getLong(KEY_ACCOUNT, -1).takeIf { it >= 0 }
    }

    fun setLedger(context: Context, id: Long?) {
        prefs(context).edit().putLong(KEY_LEDGER, id ?: -1).apply()
        ledgerId = id
    }

    fun setAccount(context: Context, id: Long?) {
        prefs(context).edit().putLong(KEY_ACCOUNT, id ?: -1).apply()
        accountId = id
    }

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private const val KEY_LEDGER = "default_ledger"
    private const val KEY_ACCOUNT = "default_account"
}
