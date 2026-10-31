package com.personal.carnet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.carnet.data.Account
import com.personal.carnet.data.EntryFactory
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.Payer
import com.personal.carnet.data.RecurringRule
import com.personal.carnet.data.Repository
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.Occurrence
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.update.UpdateChecker
import com.personal.carnet.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 检查更新到哪一步了 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class MainViewModel(private val repo: Repository, private val updater: UpdateChecker) : ViewModel() {

    val snapshot: StateFlow<Snapshot?> = repo.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val defaultRate: StateFlow<FxRate> get() = repo.defaultRate

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    val currentBuild: Long get() = updater.currentBuild()

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            repo.processDue()
        }
        viewModelScope.launch { updater.cleanup() }
        // 打开时悄悄查一次，有新版就在总览顶上提示
        checkUpdate(quiet = true)
    }

    /** 回到前台时：到日子的固定收支记上 */
    fun onResume() = viewModelScope.launch { repo.processDue() }

    fun saveSpending(
        kind: EntryKind,
        ledger: Ledger,
        account: Account?,
        payer: Payer,
        category: String,
        note: String,
        amountCents: Long,
        currency: Currency,
        rate: FxRate,
    ) {
        val entry = EntryFactory.spending(kind, ledger, account, payer, category, note, amountCents, currency, rate)
        viewModelScope.launch { repo.addEntry(entry) }
    }

    fun saveTransfer(from: Account, to: Account, fromCents: Long, toCents: Long, rate: FxRate?, note: String) {
        val entry = EntryFactory.transfer(from, to, fromCents, toCents, rate, note)
        viewModelScope.launch { repo.addEntry(entry) }
    }

    fun deleteEntry(id: Long) = viewModelScope.launch { repo.deleteEntry(id) }

    fun addAccount(account: Account) = viewModelScope.launch { repo.addAccount(account) }

    fun addLedger(ledger: Ledger) = viewModelScope.launch { repo.addLedger(ledger) }

    fun settle(ledgerId: Long, cents: Long) = viewModelScope.launch { repo.settle(ledgerId, cents) }

    fun setDefaultRate(rate: FxRate) = repo.setDefaultRate(rate)

    fun addRule(rule: RecurringRule) = viewModelScope.launch {
        repo.addRule(rule)
        repo.processDue()
    }

    fun setRuleActive(rule: RecurringRule, active: Boolean) = viewModelScope.launch { repo.setRuleActive(rule, active) }

    fun deleteRule(id: Long) = viewModelScope.launch { repo.deleteRule(id) }

    fun confirm(occurrence: Occurrence) = viewModelScope.launch { repo.confirm(occurrence) }

    fun skip(occurrence: Occurrence) = viewModelScope.launch { repo.skip(occurrence) }

    fun checkUpdate(quiet: Boolean = false) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = try {
                updater.check()?.let { UpdateState.Available(it) } ?: UpdateState.Latest
            } catch (e: Exception) {
                if (quiet) UpdateState.Idle else UpdateState.Failed("检查更新失败：${e.message ?: "网络出错"}")
            }
        }
    }

    fun installUpdate(info: UpdateInfo) {
        if (_update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val file = updater.download(info) { p -> _update.value = UpdateState.Downloading(info, p) }
                val started = updater.install(file)
                // 没授权「安装未知应用」时先去授权，回来再点一次
                _update.value = if (started) UpdateState.Available(info) else UpdateState.Failed("先允许 Carnet 安装应用，再点一次更新")
            } catch (e: Exception) {
                _update.value = UpdateState.Failed("下载失败：${e.message ?: "网络出错"}")
            }
        }
    }

    fun dismissUpdate() {
        _update.value = UpdateState.Idle
    }
}
