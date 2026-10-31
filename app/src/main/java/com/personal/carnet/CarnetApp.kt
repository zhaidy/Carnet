package com.personal.carnet

import android.app.Application
import com.personal.carnet.data.CarnetDatabase
import com.personal.carnet.data.Prefs
import com.personal.carnet.data.Repository

class CarnetApp : Application() {
    val repository: Repository by lazy {
        Repository(CarnetDatabase.open(this).dao(), Prefs(this))
    }
}
