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
        /** 共用账本里对方出固定多少，按账本货币；null 是按比例 */
        herCents: Long? = null,
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
            herCents = herCents?.takeIf { kind == EntryKind.EXPENSE && ledger.shared },
        )
    }

    /**
     * 两清时收到 / 补出去的钱：正数她补你（进 [account]），负数你补她（从 [account] 出）。
     * 是一笔另一头为空的转账，不算收入也不算花销
     */
    fun settleMoney(account: Account, cents: Long, ledgerCurrency: Currency, rate: FxRate, note: String, timeMillis: Long): Entry {
        val inAccount = rate.convert(kotlin.math.abs(cents), ledgerCurrency, account.currency)
        val incoming = cents > 0
        return Entry(
            kind = EntryKind.TRANSFER,
            timeMillis = timeMillis,
            ledgerId = null,
            category = SETTLE,
            note = note,
            amountCents = inAccount,
            currency = account.currency,
            accountId = if (incoming) null else account.id,
            accountCents = if (incoming) 0 else inAccount,
            ledgerCents = 0,
            toAccountId = if (incoming) account.id else null,
            toCents = if (incoming) inAccount else 0,
            rate = if (ledgerCurrency != account.currency) rate.cnyPerSgd else null,
        )
    }

    const val SETTLE = "settle"

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
