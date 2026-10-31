package com.personal.carnet.data

import android.content.Context
import com.personal.carnet.domain.FxRate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 小设置：默认汇率（你自己定），所有人民币账户共用 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _rate = MutableStateFlow(
        sp.getString(KEY_RATE, null)?.let(FxRate::parse) ?: FxRate(DEFAULT_RATE),
    )
    val defaultRate: StateFlow<FxRate> = _rate.asStateFlow()

    fun setDefaultRate(rate: FxRate) {
        sp.edit().putString(KEY_RATE, rate.cnyPerSgd.toString()).apply()
        _rate.value = rate
    }

    companion object {
        private const val KEY_RATE = "default_rate_cny_per_sgd"
        /** 第一次打开时的默认值，建人民币账户时会让你改成自己的 */
        const val DEFAULT_RATE = 5.30
    }
}
