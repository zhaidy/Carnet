package com.personal.carnet.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 称呼：每个人设自己的（[mine]），同步到共用账本后对方那边就这么叫你。
 * 界面上所有「她付」「她补你」「邀请她」用 [name]：对方自己设的优先，没同步或者对方没设时用 [local]
 */
object Partner {
    /** 你自己想被怎么称呼，同步给对方 */
    var mine by mutableStateOf("")
        private set

    /** 对方自己设的称呼，从共用账本同步来 */
    var remote by mutableStateOf<String?>(null)
        private set

    /** 没同步时你自己给对方起的称呼 */
    var local by mutableStateOf(DEFAULT)
        private set

    val name: String get() = remote?.takeIf { it.isNotBlank() } ?: local

    fun load(context: Context) {
        val sp = prefs(context)
        local = sp.getString(KEY_LOCAL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT
        remote = sp.getString(KEY_REMOTE, null)?.takeIf { it.isNotBlank() }
        mine = sp.getString(KEY_MINE, null).orEmpty()
    }

    fun set(context: Context, value: String) {
        val v = value.trim().take(MAX).ifEmpty { DEFAULT }
        prefs(context).edit().putString(KEY_LOCAL, v).apply()
        local = v
    }

    fun setMine(context: Context, value: String) {
        val v = value.trim().take(MAX)
        prefs(context).edit().putString(KEY_MINE, v).apply()
        mine = v
    }

    fun setRemote(context: Context, value: String) {
        val v = value.trim().take(MAX)
        if (v.isEmpty() || v == remote) return
        prefs(context).edit().putString(KEY_REMOTE, v).apply()
        remote = v
    }

    /** 头像上只放一个字 */
    val initial: String get() = name.take(1)

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    const val DEFAULT = "她"
    const val MAX = 4
    private const val KEY_LOCAL = "partner_name"
    private const val KEY_REMOTE = "partner_name_remote"
    private const val KEY_MINE = "my_name"
}
