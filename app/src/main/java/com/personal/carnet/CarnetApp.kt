package com.personal.carnet

import android.app.Application
import com.personal.carnet.data.CarnetDatabase
import com.personal.carnet.data.Prefs
import com.personal.carnet.data.Repository
import com.personal.carnet.update.UpdateChecker

class CarnetApp : Application() {
    val repository: Repository by lazy {
        Repository(CarnetDatabase.open(this).dao(), Prefs(this))
    }

    val updater: UpdateChecker by lazy { UpdateChecker(this) }
}
