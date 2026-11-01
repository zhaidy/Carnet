package com.personal.carnet.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personal.carnet.domain.Currency

enum class RuleKind(val label: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    /** 从储蓄卡还信用卡全额，金额到日子按卡上欠多少算 */
    CARD_PAYMENT("还信用卡"),
}

enum class Cycle(val label: String) { WEEKLY("每周"), MONTHLY("每月"), YEARLY("每年") }

/**
 * 固定支出 / 收入：话费、订阅、房租、工资、还信用卡。
 * - day：每月几号（1–31，没有这天的月份按最后一天）；每周几（1 = 周一 … 7 = 周日）；每年是 月×100+日（比如 314）
 * - shiftWeekend：碰上周末提前到周五（工资常这样）
 * - auto：到日子自动记一笔；false 时先在总览里提醒你确认
 * - lastDoneEpochDay：这天及以前的都处理过了（记了或跳过了）
 */
@Entity(tableName = "recurring_rules")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: RuleKind,
    val category: String,
    val amountCents: Long,
    val approximate: Boolean = false,
    val currency: Currency,
    val cycle: Cycle,
    val day: Int,
    val shiftWeekend: Boolean = false,
    /** 支出：从哪扣；收入：进哪里；还卡：从哪还 */
    val accountId: Long?,
    /** 还卡：还哪张卡 */
    val toAccountId: Long? = null,
    /** 支出记到哪个账本；收入、还卡是 null */
    val ledgerId: Long? = null,
    val auto: Boolean = true,
    val active: Boolean = true,
    val lastDoneEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
    /** 记到共用账本的支出：对方每次出固定多少（按这条的币种）；null 是按账本比例分 */
    val herCents: Long? = null,
)
