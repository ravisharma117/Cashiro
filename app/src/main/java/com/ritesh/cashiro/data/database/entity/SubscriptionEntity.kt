package com.ritesh.cashiro.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
        @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
        @ColumnInfo(name = "merchant_name") val merchantName: String,
        @ColumnInfo(name = "amount") val amount: BigDecimal,
        @ColumnInfo(name = "next_payment_date") val nextPaymentDate: LocalDate?,
        @ColumnInfo(name = "state") val state: SubscriptionState = SubscriptionState.ACTIVE,
        @ColumnInfo(name = "bank_name") val bankName: String? = null,
        @ColumnInfo(name = "umn") val umn: String? = null, // Unique Mandate Number for E-Mandates
        @ColumnInfo(name = "category") val category: String? = null,
        @ColumnInfo(name = "subcategory") val subcategory: String? = null,
        @ColumnInfo(name = "sms_body") val smsBody: String? = null,
        @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
        @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now(),
        @ColumnInfo(name = "currency", defaultValue = "INR") val currency: String = "INR",
        @ColumnInfo(name = "billing_cycle") val billingCycle: String? = null,
        @ColumnInfo(name = "last_paid_date") val lastPaidDate: LocalDate? = null,
        @ColumnInfo(name = "is_sample", defaultValue = "0") val isSample: Boolean = false,
        /** A subscription is a service charge; a bill is something owed that may change each cycle. */
        @ColumnInfo(name = "kind", defaultValue = "SUBSCRIPTION") val kind: SubscriptionKind = SubscriptionKind.SUBSCRIPTION,
        @ColumnInfo(name = "bill_type", defaultValue = "OTHER") val billType: BillType = BillType.OTHER,
        /** True when the amount changes each cycle (electricity); amount is then the last or expected one. */
        @ColumnInfo(name = "is_variable_amount", defaultValue = "0") val isVariableAmount: Boolean = false,
        /** Days before the due date to remind; null uses the default (bills 3 days, subscriptions none), 0 turns it off. */
        @ColumnInfo(name = "reminder_days_before") val reminderDaysBefore: Int? = null,
        /** The recurring schedule that adds this bill's transaction by itself, if any. */
        @ColumnInfo(name = "recurring_id") val recurringId: Long? = null,
        @ColumnInfo(name = "pay_from_bank") val payFromBank: String? = null,
        @ColumnInfo(name = "pay_from_last4") val payFromLast4: String? = null,
        /** Due date the last "due soon" reminder was sent for, so one date never reminds twice. */
        @ColumnInfo(name = "last_reminder_for") val lastReminderFor: LocalDate? = null,
        /** Due date the overdue reminder was sent for. */
        @ColumnInfo(name = "overdue_reminded_for") val overdueRemindedFor: LocalDate? = null
)

enum class SubscriptionKind { SUBSCRIPTION, BILL }

enum class BillType { ELECTRICITY, MOBILE, INTERNET, INSURANCE, STREAMING, SOFTWARE, RENT, OTHER }

enum class BillPaymentStatus { UNPAID, PAID, SKIPPED }

/** What happened to one billing cycle of a subscription or bill. Unique per (subscription, due date). */
@androidx.room.Entity(
    tableName = "bill_payments",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index(value = ["subscription_id", "due_date"], unique = true)]
)
data class BillPaymentEntity(
        @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
        @ColumnInfo(name = "subscription_id") val subscriptionId: Long,
        @ColumnInfo(name = "due_date") val dueDate: LocalDate,
        @ColumnInfo(name = "amount") val amount: BigDecimal,
        @ColumnInfo(name = "status") val status: BillPaymentStatus,
        @ColumnInfo(name = "paid_date") val paidDate: LocalDate? = null,
        @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
        @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class SubscriptionState {
    ACTIVE,
    HIDDEN // Soft delete - hidden from view but kept for reactivation detection
}
