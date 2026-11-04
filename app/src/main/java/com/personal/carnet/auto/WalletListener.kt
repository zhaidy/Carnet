package com.personal.carnet.auto

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.personal.carnet.CarnetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 只看钱包应用（Google 钱包、手机自带钱包）的通知；别的应用的通知直接忽略，也不存 */
class WalletListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!WalletParser.isWalletPackage(sbn.packageName)) return
        val app = application as CarnetApp
        if (!app.autoSettings.config.value.walletEnabled) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        if (!app.autoSettings.firstTime(sbn.key + "|" + title + "|" + text)) return
        scope.launch {
            val outcome = runCatching { app.autoRecorder.handle(title, text, sbn.postTime) }.fold(
                { r -> r.problem ?: if (r.entryId != null) "已记下" else "没记" },
                { e -> "出错了：" + (e.message ?: e.javaClass.simpleName) },
            )
            val time = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(sbn.postTime))
            app.autoSettings.noteSeen("$time · $title · $text\n→ $outcome")
        }
    }

    /** 系统（尤其是更新完应用后、或者省电清后台时）会把监听断开，断了就请它重连 */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        rebind(this)
    }

    companion object {
        fun rebind(context: Context) {
            runCatching { NotificationListenerService.requestRebind(ComponentName(context, WalletListener::class.java)) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
