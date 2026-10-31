package com.personal.carnet.auto

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.personal.carnet.CarnetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 只看 Google 钱包的通知；别的应用的通知直接忽略，也不存 */
class WalletListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WalletParser.WALLET_PACKAGE) return
        val app = application as CarnetApp
        if (!app.autoSettings.config.value.walletEnabled) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        if (!app.autoSettings.firstTime(sbn.key + "|" + title + "|" + text)) return
        scope.launch { runCatching { app.autoRecorder.handle(title, text, sbn.postTime) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
