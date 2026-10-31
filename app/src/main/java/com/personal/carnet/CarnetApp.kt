package com.personal.carnet

import android.app.Application
import com.personal.carnet.ai.Assistant
import com.personal.carnet.ai.ClaudeClient
import com.personal.carnet.auto.AutoRecorder
import com.personal.carnet.auto.AutoSettings
import com.personal.carnet.data.CarnetDatabase
import com.personal.carnet.data.Prefs
import com.personal.carnet.data.Repository
import com.personal.carnet.update.UpdateChecker

class CarnetApp : Application() {
    val repository: Repository by lazy {
        Repository(CarnetDatabase.open(this).dao(), Prefs(this))
    }

    val updater: UpdateChecker by lazy { UpdateChecker(this) }

    val autoSettings: AutoSettings by lazy { AutoSettings(this) }

    val assistant: Assistant by lazy { Assistant(ClaudeClient(autoSettings)) }

    val autoRecorder: AutoRecorder by lazy { AutoRecorder(this, repository, autoSettings, assistant) }
}
