package com.ritesh.cashiro.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

enum class RepaymentStatus { PENDING, CONFIRMED, IGNORED }

/**
 * "This transaction may be a repayment". Nothing is settled until the user confirms. One row per
 * transaction, so the same transaction is never suggested twice, even after it was ignored.
 */
@Entity(
    tableName = "repayment_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = LendBorrowPersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transaction_id"], unique = true),
        Index(value = ["person_id"]),
        Index(value = ["status"])
    ]
)
data class RepaymentSuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "person_id")
    val personId: Long,

    /** The open entry whose amount equals the transaction, when exactly one does. */
    @ColumnInfo(name = "entry_id")
    val entryId: Long? = null,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    /** True when money came in (the person paid the user back); false when the user paid out. */
    @ColumnInfo(name = "is_incoming", defaultValue = "1")
    val isIncoming: Boolean = true,

    /** 0 to 100. Strong matches may notify; weaker ones only wait in the pending list. */
    @ColumnInfo(name = "confidence")
    val confidence: Int,

    /** The name as it appeared on the transaction, kept so it can be remembered as an alias. */
    @ColumnInfo(name = "matched_text")
    val matchedText: String? = null,

    @ColumnInfo(name = "status")
    val status: RepaymentStatus = RepaymentStatus.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
