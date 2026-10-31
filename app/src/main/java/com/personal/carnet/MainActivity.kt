package com.personal.carnet

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.carnet.ui.CarnetRoot
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.update.AppNotifications

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppNotifications.cancelUpdated(this)
        // 更新装好、装失败都靠通知告诉你
        if (Build.VERSION.SDK_INT >= 33 && !AppNotifications.canNotify(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as CarnetApp
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Palette.Violet, onSurface = Palette.Ink)) {
                val vm: MainViewModel = viewModel { MainViewModel(app.repository, app.updater) }
                CarnetRoot(vm)
            }
        }
    }
}
