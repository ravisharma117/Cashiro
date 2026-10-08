package com.ritesh.cashiro.domain.usecase

import com.ritesh.cashiro.data.database.dao.RecurringTransactionDao
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.TransactionType
import com.ritesh.cashiro.data.repository.RecurringTransactionRepository
import com.ritesh.cashiro.data.repository.SubscriptionRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

/** What the user does to the current cycle of a bill or subscription. */
class BillCycleUseCase @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val addTransaction: AddTransactionUseCase,
    private val recurringDao: RecurringTransactionDao,
    private val recurring: RecurringTransactionRepository
) {

    /**
     * "Mark paid" with a new expense: adds the transaction and settles the cycle with it.
     * [amount] is what was really paid, which for a variable bill becomes its new amount.
     */
    suspend fun payAndAddExpense(
        subscription: SubscriptionEntity,
        amount: BigDecimal,
        paidAt: LocalDateTime = LocalDateTime.now()
    ): Long {
        // A linked schedule would add this payment again on the due date; step it over first
        skipLinkedScheduleIfPending(subscription)
        val transactionId = addTransaction.execute(
            amount = amount,
            merchant = subscription.merchantName,
            category = subscription.category ?: DEFAULT_CATEGORY,
            type = TransactionType.EXPENSE,
            date = paidAt,
            subcategory = subscription.subcategory,
            isRecurring = true,
            bankName = subscription.payFromBank ?: subscription.bankName,
            accountLast4 = subscription.payFromLast4,
            currency = subscription.currency,
            billingCycle = subscription.billingCycle,
            createSubscription = false
        )
        subscriptions.settleCurrentCycle(subscription.id, paidAt.toLocalDate(), amount, transactionId)
        return transactionId
    }

    /** "Mark paid" when the transaction already exists: only records the payment. */
    suspend fun payWithoutTransaction(
        subscription: SubscriptionEntity,
        amount: BigDecimal?,
        paidAt: LocalDateTime = LocalDateTime.now()
    ) {
        skipLinkedScheduleIfPending(subscription)
        subscriptions.settleCurrentCycle(subscription.id, paidAt.toLocalDate(), amount, transactionId = null)
    }

    suspend fun skipCycle(subscription: SubscriptionEntity) {
        skipLinkedScheduleIfPending(subscription)
        subscriptions.skipCurrentCycle(subscription.id)
    }

    /**
     * A linked recurring schedule adds this bill's transaction by itself. When the user settles
     * the cycle another way first, that date of the schedule must not add it a second time.
     */
    private suspend fun skipLinkedScheduleIfPending(subscription: SubscriptionEntity) {
        val scheduleId = subscription.recurringId ?: return
        val schedule = recurringDao.getById(scheduleId) ?: return
        if (schedule.nextRunDate == subscription.nextPaymentDate) recurring.skipNext(scheduleId)
    }

    private companion object {
        const val DEFAULT_CATEGORY = "Bills"
    }
}
