package com.personal.carnet

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.carnet.ui.CarnetRoot
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.update.AppNotifications

class MainActivity : ComponentActivity() {
    /** 小组件点「记一笔」带进来的页面，打开后清掉 */
    private val openRoute = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        enableEdgeToEdge()
        AppNotifications.cancelUpdated(this)
        // 更新装好、装失败都靠通知告诉你
        if (Build.VERSION.SDK_INT >= 33 && !AppNotifications.canNotify(this)) {
            // 不用 registerForActivityResult：Firebase 带进来的旧 Fragment 库和它一起用时 lint 会拦
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val app = application as CarnetApp
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Palette.Violet, onSurface = Palette.Ink)) {
                val vm: MainViewModel = viewModel { MainViewModel(app.repository, app.updater, app.autoSettings, app.assistant, app.autoRecorder, app.sync) }
                CarnetRoot(vm, openRoute) { openRoute.value = null }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ROUTE_ADD = "add"
    }
}
