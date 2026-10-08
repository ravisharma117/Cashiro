package com.ritesh.cashiro.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

/** Unit a custom schedule counts in ("every 3 weeks"). */
enum class RecurrenceUnit { DAY, WEEK, MONTH, YEAR }

enum class RecurringState { ACTIVE, PAUSED, ENDED }

enum class OccurrenceStatus {
    /** A transaction was created for this date. */
    CREATED,
    /** The date was skipped, by the user or because a later date was already due. */
    SKIPPED,
    /** Waiting for the user (remind-only schedules). */
    PENDING
}

/**
 * A schedule that creates real transactions. Dates repeat from [startDate] every [intervalCount]
 * units of [frequency] (or [customUnit] for [RecurrenceFrequency.CUSTOM]).
 */
@Entity(
    tableName = "recurring_transactions",
    indices = [Index(value = ["next_run_date"]), Index(value = ["state"])]
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "transaction_type")
    val transactionType: TransactionType = TransactionType.EXPENSE,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "subcategory")
    val subcategory: String? = null,

    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,

    @ColumnInfo(name = "account_last4")
    val accountLast4: String? = null,

    @ColumnInfo(name = "frequency")
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,

    @ColumnInfo(name = "interval_count", defaultValue = "1")
    val intervalCount: Int = 1,

    @ColumnInfo(name = "custom_unit")
    val customUnit: RecurrenceUnit? = null,

    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,

    @ColumnInfo(name = "end_date")
    val endDate: LocalDate? = null,

    @ColumnInfo(name = "next_run_date")
    val nextRunDate: LocalDate,

    @ColumnInfo(name = "last_run_date")
    val lastRunDate: LocalDate? = null,

    /** True creates the transaction by itself; false only reminds and waits for the user. */
    @ColumnInfo(name = "auto_create", defaultValue = "1")
    val autoCreate: Boolean = true,

    @ColumnInfo(name = "reminder_days_before", defaultValue = "0")
    val reminderDaysBefore: Int = 0,

    /** The due date the last reminder was sent for, so one date never reminds twice. */
    @ColumnInfo(name = "last_reminder_for")
    val lastReminderFor: LocalDate? = null,

    @ColumnInfo(name = "state")
    val state: RecurringState = RecurringState.ACTIVE,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * What happened on one due date of a schedule. The unique (schedule, date) pair is what keeps
 * generation idempotent: a date that already has a row is never processed again.
 */
@Entity(
    tableName = "recurring_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurring_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recurring_id", "due_date"], unique = true)]
)
data class RecurringOccurrenceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "recurring_id")
    val recurringId: Long,

    @ColumnInfo(name = "due_date")
    val dueDate: LocalDate,

    @ColumnInfo(name = "status")
    val status: OccurrenceStatus,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
