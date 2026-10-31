package com.personal.carnet.data

import com.personal.carnet.domain.Currency
import com.personal.carnet.domain.FxRate
import com.personal.carnet.domain.MainCurrency

/** 把「花了多少、用哪个账户、记到哪个账本」变成一笔 [Entry]，记账页、固定支出、导入都用这个 */
object EntryFactory {

    /** amountCents 是原币金额；账户扣多少、记进账本多少，按这一笔的汇率换算 */
    fun spending(
        kind: EntryKind,
        ledger: Ledger,
        account: Account?,
        payer: Payer,
        category: String,
        note: String,
        amountCents: Long,
        currency: Currency,
        rate: FxRate,
        timeMillis: Long = System.currentTimeMillis(),
    ): Entry {
        val paidByHer = kind == EntryKind.EXPENSE && ledger.shared && payer == Payer.HER
        val accountCents = if (paidByHer || account == null) 0 else rate.convert(amountCents, currency, account.currency)
        val ledgerCurrency = ledger.currency ?: MainCurrency
        val converted = (account != null && !paidByHer && account.currency != currency) || ledgerCurrency != currency
        return Entry(
            kind = kind,
            timeMillis = timeMillis,
            ledgerId = ledger.id,
            category = category,
            note = note.trim(),
            payer = if (paidByHer) Payer.HER else Payer.ME,
            amountCents = amountCents,
            currency = currency,
            accountId = if (paidByHer) null else account?.id,
            accountCents = accountCents,
            ledgerCents = rate.convert(amountCents, currency, ledgerCurrency),
            rate = if (converted) rate.cnyPerSgd else null,
        )
    }

    fun transfer(
        from: Account, to: Account, fromCents: Long, toCents: Long, rate: FxRate?, note: String,
        timeMillis: Long = System.currentTimeMillis(), category: String = "transfer",
    ) = Entry(
        kind = EntryKind.TRANSFER,
        timeMillis = timeMillis,
        ledgerId = null,
        category = category,
        note = note.trim(),
        amountCents = fromCents,
        currency = from.currency,
        accountId = from.id,
        accountCents = fromCents,
        ledgerCents = 0,
        toAccountId = to.id,
        toCents = toCents,
        rate = rate?.cnyPerSgd,
    )
}
