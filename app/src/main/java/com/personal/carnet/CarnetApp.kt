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
        sync.start()
    }
}
