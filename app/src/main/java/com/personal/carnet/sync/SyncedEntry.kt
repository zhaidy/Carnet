package com.personal.carnet.sync

import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Payer
import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import java.security.SecureRandom

/** 共用账本里的一笔在云端长什么样；账户不上传 */
object SyncedEntry {

    fun toMap(entry: Entry, authorUid: String): Map<String, Any?> = mapOf(
        "kind" to entry.kind.name,
        "timeMillis" to entry.timeMillis,
        "category" to entry.category,
        "note" to entry.note,
        "author" to authorUid,
        "paidByAuthor" to (entry.payer == Payer.ME),
        "amountCents" to entry.amountCents,
        "currency" to entry.currency.code,
        "ledgerCents" to entry.ledgerCents,
        "rate" to entry.rate,
        // 记账人的对方出固定多少；对方收到时换成「自己出」的，剩下的才是记账人出的
        "partnerCents" to entry.herCents,
        "deleted" to false,
    )

    /** 云端的一笔 → 本机的一笔；「谁付的」换成从我这边看 */
    fun fromMap(syncId: String, data: Map<String, Any?>, ledgerId: Long, myUid: String): Entry? {
        val kind = runCatching { EntryKind.valueOf(data["kind"] as String) }.getOrNull() ?: return null
        if (kind == EntryKind.TRANSFER) return null
        val amount = (data["amountCents"] as? Number)?.toLong() ?: return null
        val authorIsMe = data["author"] == myUid
        val paidByAuthor = data["paidByAuthor"] as? Boolean ?: true
        return Entry(
            kind = kind,
            timeMillis = (data["timeMillis"] as? Number)?.toLong() ?: return null,
            ledgerId = ledgerId,
            category = data["category"] as? String ?: "more",
            note = data["note"] as? String ?: "",
            payer = if (authorIsMe == paidByAuthor) Payer.ME else Payer.HER,
            amountCents = amount,
            currency = Currency.of(data["currency"] as? String) ?: Currency.SGD,
            accountId = null,
            accountCents = 0,
            ledgerCents = (data["ledgerCents"] as? Number)?.toLong() ?: amount,
            rate = (data["rate"] as? Number)?.toDouble(),
            syncId = syncId,
            herCents = (data["partnerCents"] as? Number)?.toLong()?.let { partner ->
                if (authorIsMe) partner else ((data["ledgerCents"] as? Number)?.toLong() ?: amount) - partner
            },
        )
    }

    /**
     * 别人改了本机已有的一笔：金额、分类这些用云端的，
     * 但本机记的是「我付的」、带着我的账户时，账户留着，账户扣的钱按新金额重算。
     */
    fun merge(existing: Entry, remote: Entry, accountCurrency: Currency?): Entry {
        val keepAccount = remote.payer == Payer.ME && existing.accountId != null && accountCurrency != null
        if (!keepAccount) return remote.copy(id = existing.id)
        val rate = remote.rate ?: existing.rate
        val accountCents = when {
            accountCurrency == remote.currency -> remote.amountCents
            rate != null -> FxRate(rate).convert(remote.amountCents, remote.currency, accountCurrency!!)
            else -> existing.accountCents
        }
        return remote.copy(id = existing.id, accountId = existing.accountId, accountCents = accountCents)
    }
}

/** 邀请码：6 位，去掉容易看错的 0 O 1 I L */
object SyncCodes {
    const val LENGTH = 6
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    fun generate(random: SecureRandom): String = (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun normalize(input: String): String? {
        val code = input.uppercase().filter { !it.isWhitespace() && it != '-' }
        return code.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }
}
