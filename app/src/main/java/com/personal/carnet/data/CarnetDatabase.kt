package com.personal.carnet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.carnet.domain.Currency

class Converters {
    @TypeConverter
    fun currencyToCode(currency: Currency?): String? = currency?.code

    @TypeConverter
    fun codeToCurrency(code: String?): Currency? = Currency.of(code)
}

@Database(
    entities = [Account::class, Ledger::class, Entry::class, Settlement::class, RecurringRule::class],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CarnetDatabase : RoomDatabase() {
    abstract fun dao(): CarnetDao

    companion object {
        fun open(context: Context): CarnetDatabase =
            Room.databaseBuilder(context, CarnetDatabase::class.java, "carnet.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()

        /** 第 2 版：加固定收支表。SQL 要和 Room 生成的一模一样（见 schemas/…/2.json） */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(RECURRING_TABLE_SQL)
            }
        }

        /** 第 3 版：和她同步要用的邀请码和同步 id，都是可空的新列 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_SQL.forEach(db::execSQL)
            }
        }

        /** 第 4 版：信用卡的账单日，可空的新列 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4_SQL.forEach(db::execSQL)
            }
        }

        /** 第 5 版：共用账本里对方出固定金额（每一笔、每条固定支出），都是可空的新列 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach(db::execSQL)
            }
        }

        /** 第 6 版：账本默认对方出固定金额，可空的新列 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6_SQL.forEach(db::execSQL)
            }
        }

        /** 第 7 版：共用账本自动两清，都是可空的新列 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7_SQL.forEach(db::execSQL)
            }
        }

        val MIGRATION_6_7_SQL = listOf(
            "ALTER TABLE `ledgers` ADD COLUMN `autoSettleAccountId` INTEGER",
            "ALTER TABLE `ledgers` ADD COLUMN `autoSettleFromEpochDay` INTEGER",
        )

        val MIGRATION_5_6_SQL = listOf(
            "ALTER TABLE `ledgers` ADD COLUMN `herFixedCents` INTEGER",
        )

        val MIGRATION_4_5_SQL = listOf(
            "ALTER TABLE `entries` ADD COLUMN `herCents` INTEGER",
            "ALTER TABLE `recurring_rules` ADD COLUMN `herCents` INTEGER",
        )

        val MIGRATION_3_4_SQL = listOf(
            "ALTER TABLE `accounts` ADD COLUMN `statementDay` INTEGER",
        )

        val MIGRATION_2_3_SQL = listOf(
            "ALTER TABLE `ledgers` ADD COLUMN `syncCode` TEXT",
            "ALTER TABLE `entries` ADD COLUMN `syncId` TEXT",
            "ALTER TABLE `settlements` ADD COLUMN `syncId` TEXT",
        )

        const val RECURRING_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS `recurring_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `category` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, " +
                "`approximate` INTEGER NOT NULL, `currency` TEXT NOT NULL, `cycle` TEXT NOT NULL, `day` INTEGER NOT NULL, " +
                "`shiftWeekend` INTEGER NOT NULL, `accountId` INTEGER, `toAccountId` INTEGER, `ledgerId` INTEGER, " +
                "`auto` INTEGER NOT NULL, `active` INTEGER NOT NULL, `lastDoneEpochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
    }
}
