package com.personal.carnet.auto

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.personal.carnet.CarnetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 自动记账通知上的两个按钮：算我们俩的 / 撤销 */
class AutoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(EXTRA_ENTRY, -1)
        if (entryId < 0) return
        val app = context.applicationContext as CarnetApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_UNDO -> {
                        app.repository.deleteEntry(entryId)
                        app.autoSettings.removeRecent(entryId)
                    }
                    ACTION_SHARE -> app.repository.moveToSharedLedger(entryId)
                }
                NotificationManagerCompat.from(context).cancel(AutoRecorder.notificationId(entryId))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_UNDO = "com.personal.carnet.auto.UNDO"
        const val ACTION_SHARE = "com.personal.carnet.auto.SHARE"
        private const val EXTRA_ENTRY = "entry"

        fun intent(context: Context, entryId: Long, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, (entryId * 2 + if (action == ACTION_UNDO) 1 else 0).toInt(),
            Intent(context, AutoActionReceiver::class.java).setAction(action).putExtra(EXTRA_ENTRY, entryId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
