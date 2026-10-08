package com.ritesh.cashiro.data.repository

import com.ritesh.cashiro.data.database.dao.BillPaymentDao
import com.ritesh.cashiro.data.database.dao.SubscriptionDao
import com.ritesh.cashiro.data.database.entity.BillPaymentEntity
import com.ritesh.cashiro.data.database.entity.BillPaymentStatus
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionState
import com.ritesh.parser.core.bank.HDFCBankParser
import com.ritesh.parser.core.bank.IndianBankParser
import com.ritesh.parser.core.bank.SBIBankParser
import com.ritesh.parser.core.bank.FederalBankParser
import com.ritesh.parser.core.MandateInfo
import com.ritesh.cashiro.presentation.common.icons.CategoryMapping
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import com.ritesh.cashiro.utils.SubscriptionUtils

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val billPaymentDao: BillPaymentDao
) {
    
    companion object {
        private const val TAG = "SubscriptionRepository"
    }
    
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = 
        subscriptionDao.getAllSubscriptions()
    
    fun getActiveSubscriptions(): Flow<List<SubscriptionEntity>> = 
        subscriptionDao.getActiveSubscriptions()
    
    fun getUpcomingSubscriptions(daysAhead: Int = 7): Flow<List<SubscriptionEntity>> {
        val futureDate = LocalDate.now().plusDays(daysAhead.toLong())
        return subscriptionDao.getUpcomingSubscriptions(futureDate)
    }
    
    suspend fun getSubscriptionById(id: Long): SubscriptionEntity? = 
        subscriptionDao.getSubscriptionById(id)
    
    suspend fun insertSubscription(subscription: SubscriptionEntity): Long = 
        subscriptionDao.insertSubscription(subscription)
    
    suspend fun updateSubscription(subscription: SubscriptionEntity) = 
        subscriptionDao.updateSubscription(subscription)
    
    suspend fun updateSubscriptionState(id: Long, state: SubscriptionState) = 
        subscriptionDao.updateSubscriptionState(id, state)
    
    suspend fun hideSubscription(id: Long) {
        Log.d(TAG, "Hiding subscription with ID: $id")
        updateSubscriptionState(id, SubscriptionState.HIDDEN)
    }
    
    suspend fun unhideSubscription(id: Long) = 
        updateSubscriptionState(id, SubscriptionState.ACTIVE)
    
    suspend fun deleteSubscription(id: Long) = 
        subscriptionDao.deleteSubscriptionById(id)

    suspend fun deleteSampleSubscriptions() =
        subscriptionDao.deleteSampleSubscriptions()

    suspend fun deleteAllSubscriptions() =
        subscriptionDao.deleteAllSubscriptions()
    
    /**
     * Creates or updates a subscription from HDFC E-Mandate info
     */
    suspend fun createOrUpdateFromEMandate(
        eMandateInfo: HDFCBankParser.EMandateInfo,
        bankName: String = "HDFC Bank",
        smsBody: String? = null
    ): Long = createOrUpdateFromMandate(eMandateInfo, bankName, smsBody)
    
    /**
     * Checks if a transaction matches any active subscription
     */
    suspend fun matchTransactionToSubscription(
        merchantName: String,
        amount: BigDecimal
    ): SubscriptionEntity? {
        val activeSubscription = subscriptionDao.getActiveSubscriptionByMerchant(merchantName)
        
        // A variable bill (electricity) changes each cycle, so the merchant alone identifies it.
        // Otherwise the amounts must match, with a little tolerance.
        return if (activeSubscription != null &&
                   (activeSubscription.isVariableAmount ||
                    areAmountsEqual(activeSubscription.amount, amount))) {
            activeSubscription
        } else {
            null
        }
    }
    
    /**
     * Updates the next payment date after a subscription charge
     */
    suspend fun updateNextPaymentDateAfterCharge(
        subscriptionId: Long,
        chargeDate: LocalDate = LocalDate.now()
    ): LocalDate? = settleCurrentCycle(subscriptionId, chargeDate, amount = null, transactionId = null)

    fun observePayments(subscriptionId: Long): Flow<List<BillPaymentEntity>> =
        billPaymentDao.observeFor(subscriptionId)

    suspend fun getByRecurringId(recurringId: Long): SubscriptionEntity? =
        subscriptionDao.getByRecurringId(recurringId)

    /**
     * Records that the current billing cycle was paid and moves to the next due date. Returns the
     * due date that was settled, or null when it had already been settled (so charging twice, or
     * a recurring schedule and an SMS both seeing the same payment, never advances two cycles).
     *
     * [amount] is what was actually paid; a variable bill also remembers it as its new amount.
     * [onlyIfDueOnOrBefore] makes this a no-op when the bill is not due yet by that date, which a
     * linked recurring schedule uses so it cannot settle a cycle that was paid in advance.
     */
    suspend fun settleCurrentCycle(
        subscriptionId: Long,
        paidDate: LocalDate,
        amount: BigDecimal?,
        transactionId: Long?,
        onlyIfDueOnOrBefore: LocalDate? = null
    ): LocalDate? {
        val sub = subscriptionDao.getSubscriptionById(subscriptionId) ?: return null
        val due = sub.nextPaymentDate ?: paidDate
        if (onlyIfDueOnOrBefore != null && due.isAfter(onlyIfDueOnOrBefore)) return null
        val existing = billPaymentDao.getForCycle(subscriptionId, due)
        if (existing != null && existing.status != BillPaymentStatus.UNPAID) return null

        val paidAmount = amount ?: sub.amount
        billPaymentDao.upsert(
            BillPaymentEntity(
                id = existing?.id ?: 0,
                subscriptionId = subscriptionId,
                dueDate = due,
                amount = paidAmount,
                status = BillPaymentStatus.PAID,
                paidDate = paidDate,
                transactionId = transactionId
            )
        )
        subscriptionDao.updateSubscription(
            sub.copy(
                nextPaymentDate = SubscriptionUtils.calculateNextPaymentDate(due, sub.billingCycle),
                lastPaidDate = paidDate,
                amount = if (sub.isVariableAmount && amount != null) amount else sub.amount,
                updatedAt = java.time.LocalDateTime.now()
            )
        )
        return due
    }

    /** Skips the current cycle without a payment and moves to the next due date. */
    suspend fun skipCurrentCycle(subscriptionId: Long): LocalDate? {
        val sub = subscriptionDao.getSubscriptionById(subscriptionId) ?: return null
        val due = sub.nextPaymentDate ?: return null
        val existing = billPaymentDao.getForCycle(subscriptionId, due)
        if (existing != null && existing.status != BillPaymentStatus.UNPAID) return null
        billPaymentDao.upsert(
            BillPaymentEntity(
                id = existing?.id ?: 0,
                subscriptionId = subscriptionId,
                dueDate = due,
                amount = sub.amount,
                status = BillPaymentStatus.SKIPPED
            )
        )
        subscriptionDao.updateSubscription(
            sub.copy(
                nextPaymentDate = SubscriptionUtils.calculateNextPaymentDate(due, sub.billingCycle),
                updatedAt = java.time.LocalDateTime.now()
            )
        )
        return due
    }

    /** Attaches the transaction to a payment that was recorded before the transaction existed. */
    suspend fun linkPaymentTransaction(subscriptionId: Long, dueDate: LocalDate, transactionId: Long) {
        val payment = billPaymentDao.getForCycle(subscriptionId, dueDate) ?: return
        billPaymentDao.update(payment.copy(transactionId = transactionId))
    }
    
    
    private fun areAmountsEqual(amount1: BigDecimal, amount2: BigDecimal): Boolean {
        // Allow for small variations (up to 5%)
        val tolerance = amount1.multiply(BigDecimal("0.05"))
        val diff = amount1.subtract(amount2).abs()
        return diff <= tolerance
    }
    
    /**
     * Creates or updates a subscription from Indian Bank Mandate info
     */
    suspend fun createOrUpdateFromIndianBankMandate(
        mandateInfo: IndianBankParser.IndianMandateInfo,
        bankName: String = "Indian Bank",
        smsBody: String? = null
    ): Long = createOrUpdateFromMandate(mandateInfo, bankName, smsBody)
    
    /**
     * Creates or updates a subscription from SBI UPI-Mandate info
     */
    suspend fun createOrUpdateFromSBIMandate(
        upiMandateInfo: SBIBankParser.UPIMandateInfo,
        bankName: String = "SBI",
        smsBody: String? = null
    ): Long = createOrUpdateFromMandate(upiMandateInfo, bankName, smsBody)

    /**
     * Creates or updates a subscription from Federal Bank E-Mandate info
     */
    suspend fun createOrUpdateFromFederalBankMandate(
        mandateInfo: FederalBankParser.EMandateInfo,
        bankName: String = "Federal Bank",
        smsBody: String? = null
    ): Long = createOrUpdateFromMandate(mandateInfo, bankName, smsBody)

    /**
     * Creates or updates a subscription from any MandateInfo implementation.
     * This is the unified method that can handle mandates from any bank.
     */
    suspend fun createOrUpdateFromMandate(
        mandateInfo: MandateInfo,
        bankName: String,
        smsBody: String? = null
    ): Long {
        val nextPaymentDate = mandateInfo.nextDeductionDate?.let { dateStr ->
            try {
                // Use the date format specified by the mandate implementation
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(mandateInfo.dateFormat))
            } catch (e: Exception) {
                // Fallback to 30 days from now if parsing fails
                LocalDate.now().plusDays(30)
            }
        } ?: LocalDate.now().plusDays(30)

        // For banks that provide UMN (HDFC, SBI, Federal), use it as primary identifier
        val existing = if (mandateInfo.umn != null) {
            subscriptionDao.getSubscriptionByUmn(mandateInfo.umn!!)
        } else {
            // For other banks or when no UMN, use merchant and amount matching
            subscriptionDao.getSubscriptionByMerchantAndAmount(
                mandateInfo.merchant,
                mandateInfo.amount
            )
        }

        Log.d(TAG, "Unified Mandate lookup - Bank: $bankName, Merchant: ${mandateInfo.merchant}, " +
                  "Amount: ${mandateInfo.amount}, UMN: ${mandateInfo.umn}, " +
                  "Next Date: $nextPaymentDate, Existing: ${existing?.let { "ID=${it.id}, State=${it.state}, " +
                  "StoredDate=${it.nextPaymentDate}" } ?: "NOT FOUND"}")

        val subscription = if (existing != null) {
            // Check if this is a hidden subscription that should be reactivated
            // Only reactivate if the new payment date is LATER than the stored date
            val shouldReactivate = existing.state == SubscriptionState.HIDDEN &&
                                  nextPaymentDate.isAfter(existing.nextPaymentDate) &&
                                  nextPaymentDate.isAfter(LocalDate.now())

            Log.d(TAG, "Subscription state check - Hidden: ${existing.state == SubscriptionState.HIDDEN}, " +
                      "New date after stored: ${nextPaymentDate.isAfter(existing.nextPaymentDate)}, " +
                      "New date is future: ${nextPaymentDate.isAfter(LocalDate.now())}, " +
                      "Should reactivate: $shouldReactivate")

            // If hidden and payment date hasn't changed, don't update
            if (existing.state == SubscriptionState.HIDDEN && !shouldReactivate) {
                // Return the existing ID without any updates
                Log.d(TAG, "Subscription ${existing.id} is HIDDEN and won't be reactivated " +
                          "(payment date not newer). Skipping update.")
                return existing.id
            }

            // Update existing subscription (reactivate if needed)
            if (shouldReactivate) {
                Log.i(TAG, "REACTIVATING subscription ${existing.id} - ${existing.merchantName} " +
                          "(old date: ${existing.nextPaymentDate}, new date: $nextPaymentDate)")
            }
            existing.copy(
                amount = mandateInfo.amount,
                nextPaymentDate = nextPaymentDate,
                merchantName = mandateInfo.merchant,
                umn = mandateInfo.umn ?: existing.umn, // Update UMN if provided
                state = if (shouldReactivate) SubscriptionState.ACTIVE else existing.state,
                smsBody = smsBody ?: existing.smsBody, // Update SMS body if provided
                updatedAt = java.time.LocalDateTime.now()
            )
        } else {
            // Create new subscription
            Log.d(TAG, "Creating NEW subscription - Bank: $bankName, Merchant: ${mandateInfo.merchant}, " +
                      "Amount: ${mandateInfo.amount}, Date: $nextPaymentDate")
            SubscriptionEntity(
                merchantName = mandateInfo.merchant,
                amount = mandateInfo.amount,
                nextPaymentDate = nextPaymentDate,
                state = SubscriptionState.ACTIVE,
                bankName = bankName,
                umn = mandateInfo.umn,
                category = determineCategory(mandateInfo.merchant),
                smsBody = smsBody
            )
        }

        return subscriptionDao.insertSubscription(subscription)
    }

    suspend fun updatePaymentStatus(id: Long, nextPaymentDate: LocalDate, lastPaidDate: LocalDate?) =
        subscriptionDao.updatePaymentStatus(id, nextPaymentDate, lastPaidDate)

    private fun determineCategory(merchantName: String): String {
        // Use unified category mapping
        return CategoryMapping.getCategory(merchantName)
    }
}