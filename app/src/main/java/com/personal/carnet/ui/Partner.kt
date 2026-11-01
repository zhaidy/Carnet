package com.personal.carnet.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 对方怎么称呼：界面上所有「她付」「她补你」「邀请她」都用这个。
 * 默认「她」；她自己的手机上改成「他」，或者随便起个称呼
 */
object Partner {
    var name by mutableStateOf(DEFAULT)
        private set

    fun load(context: Context) {
        name = prefs(context).getString(KEY, null)?.takeIf { it.isNotBlank() } ?: DEFAULT
    }

    fun set(context: Context, value: String) {
        val v = value.trim().take(MAX).ifEmpty { DEFAULT }
        prefs(context).edit().putString(KEY, v).apply()
        name = v
    }

    /** 头像上只放一个字 */
    val initial: String get() = name.take(1)

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    const val DEFAULT = "她"
    const val MAX = 4
    private const val KEY = "partner_name"
}
