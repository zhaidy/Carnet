package com.personal.carnet

import android.app.Application
import com.personal.carnet.ai.Assistant
import com.personal.carnet.ai.ClaudeClient
import com.personal.carnet.auto.AutoRecorder
import com.personal.carnet.auto.AutoSettings
import com.personal.carnet.data.CarnetDatabase
import com.personal.carnet.data.Prefs
import com.personal.carnet.data.Repository
import com.personal.carnet.sync.BookSync
import com.personal.carnet.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import com.personal.carnet.widget.CarnetWidget
import com.personal.carnet.widget.WidgetData

class CarnetApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { CarnetDatabase.open(this) }

    val sync: BookSync by lazy { BookSync(database.dao(), appScope) }

    val repository: Repository by lazy {
        Repository(database.dao(), Prefs(this)).also { repo ->
            repo.onEntryDeleted = { entry, ledger -> sync.onDeleted(entry, ledger) }
            repo.onEntryUpdated = { entry, ledger -> sync.onUpdated(entry, ledger) }
            repo.onSettlementChanged = { s, ledger, undone -> sync.onSettlementChanged(s, ledger, undone) }
        }
    }

    val updater: UpdateChecker by lazy { UpdateChecker(this) }

    val autoSettings: AutoSettings by lazy { AutoSettings(this) }

    val assistant: Assistant by lazy { Assistant(ClaudeClient(autoSettings)) }

    val autoRecorder: AutoRecorder by lazy { AutoRecorder(this, repository, autoSettings, assistant) }

    override fun onCreate() {
        super.onCreate()
        com.personal.carnet.ui.Partner.load(this)
        com.personal.carnet.ui.CustomCategories.load(this)
        sync.start()
        // 账一变，桌面小组件跟着刷新（显示的文字没变就不刷）
        appScope.launch {
            repository.snapshot
                .map { WidgetData.from(it) }
                .distinctUntilChanged()
                .collect { runCatching { CarnetWidget().updateAll(this@CarnetApp) } }
        }
    }
}
