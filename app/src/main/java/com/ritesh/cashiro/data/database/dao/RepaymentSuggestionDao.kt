package com.ritesh.cashiro.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritesh.cashiro.data.database.entity.RepaymentSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepaymentSuggestionDao {

    @Query("SELECT * FROM repayment_suggestions WHERE status = 'PENDING' ORDER BY confidence DESC, created_at DESC")
    fun observePending(): Flow<List<RepaymentSuggestionEntity>>

    @Query("SELECT * FROM repayment_suggestions WHERE id = :id")
    suspend fun getById(id: Long): RepaymentSuggestionEntity?

    @Query("SELECT * FROM repayment_suggestions WHERE transaction_id = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: Long): RepaymentSuggestionEntity?

    /** Returns -1 when this transaction already has a suggestion (including an ignored one). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(suggestion: RepaymentSuggestionEntity): Long

    @Update
    suspend fun update(suggestion: RepaymentSuggestionEntity)

    @Query("SELECT * FROM repayment_suggestions WHERE status = 'PENDING'")
    suspend fun getPending(): List<RepaymentSuggestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(suggestions: List<RepaymentSuggestionEntity>)

    @Query("DELETE FROM repayment_suggestions")
    suspend fun deleteAll()
}
