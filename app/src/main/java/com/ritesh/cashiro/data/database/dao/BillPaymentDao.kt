package com.ritesh.cashiro.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritesh.cashiro.data.database.entity.BillPaymentEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface BillPaymentDao {

    @Query("SELECT * FROM bill_payments WHERE subscription_id = :subscriptionId ORDER BY due_date DESC")
    fun observeFor(subscriptionId: Long): Flow<List<BillPaymentEntity>>

    @Query("SELECT * FROM bill_payments WHERE subscription_id = :subscriptionId AND due_date = :dueDate")
    suspend fun getForCycle(subscriptionId: Long, dueDate: LocalDate): BillPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: BillPaymentEntity): Long

    @Update
    suspend fun update(payment: BillPaymentEntity)

    @Query("SELECT * FROM bill_payments ORDER BY id ASC")
    suspend fun getAll(): List<BillPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<BillPaymentEntity>)

    @Query("DELETE FROM bill_payments")
    suspend fun deleteAll()
}
