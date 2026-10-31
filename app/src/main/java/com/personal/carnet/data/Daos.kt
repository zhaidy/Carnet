package com.personal.carnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CarnetDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM ledgers ORDER BY createdAt")
    fun ledgers(): Flow<List<Ledger>>

    @Query("SELECT * FROM entries ORDER BY timeMillis DESC, id DESC")
    fun entries(): Flow<List<Entry>>

    @Query("SELECT * FROM settlements ORDER BY timeMillis")
    fun settlements(): Flow<List<Settlement>>

    @Query("SELECT COUNT(*) FROM ledgers")
    suspend fun ledgerCount(): Int

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Insert
    suspend fun insert(ledger: Ledger): Long

    @Insert
    suspend fun insert(entry: Entry): Long

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Insert
    suspend fun insert(settlement: Settlement): Long

    @Query("SELECT * FROM recurring_rules ORDER BY createdAt")
    fun rules(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules")
    suspend fun rulesNow(): List<RecurringRule>

    @Query("SELECT * FROM accounts")
    suspend fun accountsNow(): List<Account>

    @Query("SELECT * FROM ledgers")
    suspend fun ledgersNow(): List<Ledger>

    @Query("SELECT * FROM entries")
    suspend fun entriesNow(): List<Entry>

    @Insert
    suspend fun insert(rule: RecurringRule): Long

    @Update
    suspend fun update(rule: RecurringRule)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteSettlement(id: Long)
}
