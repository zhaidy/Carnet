package com.personal.carnet.sync

import com.personal.carnet.data.Entry
import com.personal.carnet.data.EntryKind
import com.personal.carnet.data.Payer
import com.personal.carnet.domain.Currency
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
        )
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
