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

    @Insert
    suspend fun insertAll(entries: List<Entry>)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: Long): Entry?

    @Update
    suspend fun update(entry: Entry)

    @Query("SELECT COUNT(*) FROM entries WHERE accountId = :accountId AND amountCents = :cents AND timeMillis BETWEEN :from AND :to")
    suspend fun countSimilar(accountId: Long, cents: Long, from: Long, to: Long): Int

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

    @Update
    suspend fun update(ledger: Ledger)

    @Query("SELECT * FROM entries WHERE syncId = :syncId LIMIT 1")
    suspend fun entryBySyncId(syncId: String): Entry?

    @Query("DELETE FROM entries WHERE syncId = :syncId")
    suspend fun deleteEntryBySyncId(syncId: String)

    @Query("SELECT * FROM entries WHERE ledgerId = :ledgerId")
    fun entriesIn(ledgerId: Long): Flow<List<Entry>>

    @Query("SELECT * FROM settlements WHERE ledgerId = :ledgerId")
    fun settlementsIn(ledgerId: Long): Flow<List<Settlement>>

    @Query("SELECT * FROM settlements WHERE syncId = :syncId LIMIT 1")
    suspend fun settlementBySyncId(syncId: String): Settlement?

    @Update
    suspend fun update(settlement: Settlement)

    @Query("SELECT * FROM settlements WHERE id = :id")
    suspend fun settlement(id: Long): Settlement?

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteSettlement(id: Long)
}
