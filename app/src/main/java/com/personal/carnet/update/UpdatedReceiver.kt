package com.personal.carnet.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 新版本装好后系统会结束旧进程，发个通知，点一下回到应用（应用被打开时 MainActivity 会撤掉它） */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val build = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        AppNotifications.show(context, "Carnet 已更新到 build $build", "点这里打开", AppNotifications.UPDATED_ID)
    }
}
