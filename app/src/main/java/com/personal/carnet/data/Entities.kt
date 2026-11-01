package com.personal.carnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.carnet.domain.Currency

enum class AccountType(val label: String) {
    DEBIT("储蓄卡"),
    CREDIT("信用卡"),
    WALLET("电子钱包"),
    CASH("现金"),
}

/**
 * 钱放在哪：只在自己手机上，不同步给别人。
 * openingCents 是建账户时的余额；信用卡存负数（欠多少）。
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val openingCents: Long,
    /** 信用卡每月几号还全额 */
    val dueDay: Int? = null,
    /** 算不算进「发薪前还能花」 */
    val inCashflow: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** 信用卡每月几号出账单；设了的话，还卡只还到账单日为止欠的 */
    val statementDay: Int? = null,
)

enum class SettleMode { MONTHLY, DATE, MANUAL }

/**
 * 记到哪、算谁的。shared = true 是和她一起的账本，按 myPercent 分。
 * currency 为 null 的账本可以混着记各种币，统计时换成主货币。
 */
@Entity(tableName = "ledgers")
data class Ledger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currency: Currency?,
    val shared: Boolean,
    /** 你出的百分比，平分是 50 */
    val myPercent: Int = 50,
    val settleMode: SettleMode = SettleMode.MONTHLY,
    /** 每月几号结，31 表示月底 */
    val settleDay: Int = 31,
    /** 选日期结时的那天（epochDay） */
    val settleEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** 和她同步时的邀请码；没同步是 null */
    val syncCode: String? = null,
)

enum class EntryKind { EXPENSE, INCOME, TRANSFER }

enum class Payer { ME, HER }

/**
 * 一笔账。
 * - amountCents / currency：花的时候的原币金额（比如 ¥88）
 * - accountCents：账户上实际动了多少，按账户币种（比如卡A 扣了 S$16.60）；她付的是 0
 * - ledgerCents：记进账本的金额，按账本货币（混币账本按主货币）
 * - 转账：accountCents 是转出账户扣的，toCents 是转入账户到的
 * - rate：这一笔用的汇率（1 新币 = 多少人民币），没换汇是 null
 */
@Entity(tableName = "entries", indices = [Index("ledgerId"), Index("accountId"), Index("toAccountId"), Index("timeMillis")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EntryKind,
    val timeMillis: Long,
    val ledgerId: Long?,
    val category: String,
    val note: String = "",
    val payer: Payer = Payer.ME,
    val amountCents: Long,
    val currency: Currency,
    val accountId: Long?,
    val accountCents: Long,
    val ledgerCents: Long,
    val toAccountId: Long? = null,
    val toCents: Long = 0,
    val rate: Double? = null,
    /** 同步到共用账本后的 id；本机还没传上去、或者不在同步的账本里是 null */
    val syncId: String? = null,
    /** 共用账本里这一笔对方出固定多少（账本货币）；null 是按账本比例分 */
    val herCents: Long? = null,
)

/** 结过一次账：这之前的共用开销都两清了 */
@Entity(tableName = "settlements", indices = [Index("ledgerId")])
data class Settlement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ledgerId: Long,
    val timeMillis: Long,
    /** 结的时候是多少（正数：她补你） */
    val cents: Long,
    val syncId: String? = null,
)
