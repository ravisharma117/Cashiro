package com.ritesh.cashiro.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritesh.cashiro.data.database.entity.RecurringOccurrenceEntity
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions ORDER BY next_run_date ASC, title ASC")
    fun observeAll(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions ORDER BY id ASC")
    suspend fun getAll(): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE state = 'ACTIVE' ORDER BY next_run_date ASC")
    suspend fun getActive(): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: Long): RecurringTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecurringTransactionEntity): Long

    @Update
    suspend fun update(entity: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAll()

    /** Returns -1 when this schedule already has a row for the date. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrence(occurrence: RecurringOccurrenceEntity): Long

    @Update
    suspend fun updateOccurrence(occurrence: RecurringOccurrenceEntity)

    @Query("SELECT * FROM recurring_occurrences WHERE recurring_id = :recurringId AND due_date = :dueDate")
    suspend fun getOccurrence(recurringId: Long, dueDate: java.time.LocalDate): RecurringOccurrenceEntity?

    @Query("SELECT * FROM recurring_occurrences WHERE recurring_id = :recurringId ORDER BY due_date DESC")
    fun observeOccurrences(recurringId: Long): Flow<List<RecurringOccurrenceEntity>>

    @Query("SELECT * FROM recurring_occurrences ORDER BY id ASC")
    suspend fun getAllOccurrences(): List<RecurringOccurrenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOccurrences(occurrences: List<RecurringOccurrenceEntity>)

    @Query("DELETE FROM recurring_occurrences")
    suspend fun deleteAllOccurrences()
}
