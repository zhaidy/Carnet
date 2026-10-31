package com.personal.carnet.auto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personal.carnet.MainActivity
import com.personal.carnet.R
import com.personal.carnet.ai.Assistant
import com.personal.carnet.data.AccountType
import com.personal.carnet.data.EntryFactory
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Payer
import com.personal.carnet.data.Repository
import com.personal.carnet.domain.Categories
import com.personal.carnet.domain.Money
import com.personal.carnet.update.AppNotifications

/** 把一条付款通知记成一笔账，再发一条通知让你改成共用或者撤销 */
class AutoRecorder(
    private val context: Context,
    private val repo: Repository,
    private val settings: AutoSettings,
    private val assistant: Assistant,
) {

    /** 返回认出来的结果（试一下用）；真的记了账时 entryId 不是 null */
    data class Result(val payment: WalletPayment?, val accountName: String?, val category: String?, val entryId: Long?, val problem: String?)

    suspend fun handle(title: String, text: String, postTime: Long, record: Boolean = true): Result {
        var payment = WalletParser.parse(title, text)
        if (payment == null && assistant.configured) payment = runCatching { assistant.readNotification(title, text) }.getOrNull()
        if (payment == null) return Result(null, null, null, null, "这条不像付款通知")

        val snapshot = repo.snapshotNow()
        val accounts = snapshot.accounts.map { it.account }
        val config = settings.config.value
        val byCard = payment.last4?.let { config.cards[it] }?.let { id -> accounts.firstOrNull { it.id == id } }
        if (payment.last4 != null && byCard == null) settings.noteUnknownCard(payment.last4)
        val account = byCard
            ?: config.defaultAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
            ?: accounts.firstOrNull { it.type == AccountType.CREDIT && it.currency == payment.currency }
            ?: accounts.firstOrNull()
            ?: return Result(payment, null, null, null, "还没有账户，先在 Carnet 里加一个")

        val category = if (payment.refund) "other_in" else {
            settings.merchantCategory(payment.merchant)
                ?: MerchantGuess.guess(payment.merchant)
                ?: (if (assistant.configured) runCatching { assistant.categorize(payment.merchant) }.getOrNull() else null)
                ?: "more"
        }
        if (!record) return Result(payment, account.name, category, null, null)

        val ledgers = snapshot.ledgers.map { it.ledger }
        val ledger = config.ledgerId?.let { id -> ledgers.firstOrNull { it.id == id } } ?: ledgers.firstOrNull { !it.shared } ?: ledgers.first()
        if (repo.hasSimilar(account.id, payment.cents, postTime)) return Result(payment, account.name, category, null, "差不多同一时间已经有一笔一样的了")

        val entry = EntryFactory.spending(
            kind = if (payment.refund) EntryKind.INCOME else EntryKind.EXPENSE,
            ledger = if (payment.refund) (ledgers.firstOrNull { !it.shared } ?: ledger) else ledger,
            account = account, payer = Payer.ME, category = category,
            note = (if (payment.refund) "退款 · " else "") + payment.merchant,
            amountCents = payment.cents, currency = payment.currency, rate = snapshot.rate, timeMillis = postTime,
        )
        val id = repo.addEntry(entry)
        settings.addRecent(id)
        notifyRecorded(id, payment, account.name, Categories.of(category).label, sharedAvailable = !payment.refund && ledgers.any { it.shared } && !ledger.shared)
        return Result(payment, account.name, category, id, null)
    }

    private fun notifyRecorded(entryId: Long, p: WalletPayment, accountName: String, categoryLabel: String, sharedAvailable: Boolean) {
        if (!AppNotifications.canNotify(context)) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "自动记账", NotificationManager.IMPORTANCE_LOW).apply {
                description = "从 Google 钱包的付款通知自动记下的账"
            },
        )
        val open = PendingIntent.getActivity(
            context, entryId.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle((if (p.refund) "退款 +" else "已记下 ") + Money.format(p.cents, p.currency) + " · " + p.merchant)
            .setContentText("$categoryLabel · $accountName")
            .setAutoCancel(true)
            .setContentIntent(open)
        if (sharedAvailable) builder.addAction(0, "算我们俩的", AutoActionReceiver.intent(context, entryId, AutoActionReceiver.ACTION_SHARE))
        builder.addAction(0, "撤销", AutoActionReceiver.intent(context, entryId, AutoActionReceiver.ACTION_UNDO))
        NotificationManagerCompat.from(context).notify(notificationId(entryId), builder.build())
    }

    companion object {
        const val CHANNEL = "auto_record"
        fun notificationId(entryId: Long) = 30_000 + (entryId % 10_000).toInt()
    }
}
