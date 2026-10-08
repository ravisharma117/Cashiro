package com.ritesh.cashiro.data.repository

import com.ritesh.cashiro.data.database.entity.BillPaymentStatus
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionKind
import com.ritesh.cashiro.data.database.entity.SubscriptionState
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BillCycleTest {

    private val subscriptionDao = FakeSubscriptionDao()
    private val paymentDao = FakeBillPaymentDao()
    private val repository = SubscriptionRepository(subscriptionDao, paymentDao)
    private val today: LocalDate = LocalDate.now()

    private suspend fun addBill(
        due: LocalDate,
        amount: String = "1200",
        variable: Boolean = false,
        merchant: String = "Power Utility",
        cycle: String = "monthly"
    ): Long = subscriptionDao.insertSubscription(
        SubscriptionEntity(
            merchantName = merchant,
            amount = BigDecimal(amount),
            nextPaymentDate = due,
            billingCycle = cycle,
            kind = SubscriptionKind.BILL,
            isVariableAmount = variable
        )
    )

    @Test
    fun payingRecordsThePaymentAndMovesToTheNextCycle() = runBlocking<Unit> {
        val id = addBill(due = today)

        val settled = repository.settleCurrentCycle(id, today, amount = null, transactionId = 7L)

        assertEquals(today, settled)
        val payment = paymentDao.getForCycle(id, today)
        assertEquals(BillPaymentStatus.PAID, payment?.status)
        assertEquals(7L, payment?.transactionId)
        assertEquals(today, payment?.paidDate)
        val bill = subscriptionDao.items.getValue(id)
        assertEquals(today.plusMonths(1), bill.nextPaymentDate)
        assertEquals(today, bill.lastPaidDate)
    }

    @Test
    fun aVariableBillRemembersWhatWasActuallyPaid() = runBlocking<Unit> {
        val id = addBill(due = today, amount = "1200", variable = true)

        repository.settleCurrentCycle(id, today, amount = BigDecimal("1850"), transactionId = null)

        assertEquals(BigDecimal("1850"), subscriptionDao.items.getValue(id).amount)
        assertEquals(BigDecimal("1850"), paymentDao.getForCycle(id, today)?.amount)
    }

    @Test
    fun aFixedBillKeepsItsAmountButRecordsWhatWasPaid() = runBlocking<Unit> {
        val id = addBill(due = today, amount = "1200", variable = false)

        repository.settleCurrentCycle(id, today, amount = BigDecimal("1250"), transactionId = null)

        assertEquals(BigDecimal("1200"), subscriptionDao.items.getValue(id).amount)
        assertEquals(BigDecimal("1250"), paymentDao.getForCycle(id, today)?.amount)
    }

    @Test
    fun aCycleThatWasAlreadySettledIsNotSettledAgain() = runBlocking<Unit> {
        val id = addBill(due = today)
        repository.settleCurrentCycle(id, today, null, 1L)
        val afterFirst = subscriptionDao.items.getValue(id)

        // The same payment seen again by a linked recurring schedule dated today
        val second = repository.settleCurrentCycle(id, today, null, 2L, onlyIfDueOnOrBefore = today)

        assertNull(second)
        assertEquals(afterFirst, subscriptionDao.items.getValue(id))
        assertEquals(1, paymentDao.payments.size)
    }

    @Test
    fun anUnpaidRowCanStillBeSettled() = runBlocking<Unit> {
        val id = addBill(due = today)
        paymentDao.upsert(
            com.ritesh.cashiro.data.database.entity.BillPaymentEntity(
                subscriptionId = id, dueDate = today, amount = BigDecimal("1200"), status = BillPaymentStatus.UNPAID
            )
        )

        assertEquals(today, repository.settleCurrentCycle(id, today, null, null))
        assertEquals(BillPaymentStatus.PAID, paymentDao.getForCycle(id, today)?.status)
    }

    @Test
    fun anOverdueBillMovesToTheFirstFutureDate() = runBlocking<Unit> {
        val id = addBill(due = today.minusDays(40))

        repository.settleCurrentCycle(id, today, null, null)

        val next = subscriptionDao.items.getValue(id).nextPaymentDate!!
        assertTrue(!next.isBefore(today), "next due date $next must not be in the past")
    }

    @Test
    fun skippingMovesOnWithoutAPaymentOrLastPaidDate() = runBlocking<Unit> {
        val id = addBill(due = today)

        repository.skipCurrentCycle(id)

        assertEquals(BillPaymentStatus.SKIPPED, paymentDao.getForCycle(id, today)?.status)
        val bill = subscriptionDao.items.getValue(id)
        assertEquals(today.plusMonths(1), bill.nextPaymentDate)
        assertNull(bill.lastPaidDate)
    }

    @Test
    fun aLinkedScheduleDoesNotSettleACycleThatIsNotDueYet() = runBlocking<Unit> {
        val id = addBill(due = today.plusDays(20))

        val result = repository.settleCurrentCycle(id, today, null, 5L, onlyIfDueOnOrBefore = today)

        assertNull(result)
        assertTrue(paymentDao.payments.isEmpty())
        assertEquals(today.plusDays(20), subscriptionDao.items.getValue(id).nextPaymentDate)
    }

    @Test
    fun theTransactionCanBeAttachedAfterThePaymentWasRecorded() = runBlocking<Unit> {
        val id = addBill(due = today)
        val due = repository.updateNextPaymentDateAfterCharge(id, today)

        repository.linkPaymentTransaction(id, due!!, 42L)

        assertEquals(42L, paymentDao.getForCycle(id, due)?.transactionId)
    }

    @Test
    fun aFixedBillMatchesOnMerchantAndAmount() = runBlocking<Unit> {
        addBill(due = today, amount = "1000", merchant = "Fibre Net")

        assertNotNull(repository.matchTransactionToSubscription("Fibre Net", BigDecimal("1000")))
        assertNotNull(repository.matchTransactionToSubscription("Fibre Net", BigDecimal("1040")))
        assertNull(repository.matchTransactionToSubscription("Fibre Net", BigDecimal("1900")))
        assertNull(repository.matchTransactionToSubscription("Other Co", BigDecimal("1000")))
    }

    @Test
    fun aVariableBillMatchesOnMerchantAlone() = runBlocking<Unit> {
        addBill(due = today, amount = "1000", variable = true, merchant = "Power Utility")

        assertNotNull(repository.matchTransactionToSubscription("Power Utility", BigDecimal("2750")))
        assertNull(repository.matchTransactionToSubscription("Water Board", BigDecimal("2750")))
    }

    @Test
    fun aHiddenBillIsNotMatched() = runBlocking<Unit> {
        val id = addBill(due = today, variable = true, merchant = "Power Utility")
        subscriptionDao.updateSubscriptionState(id, SubscriptionState.HIDDEN)

        assertNull(repository.matchTransactionToSubscription("Power Utility", BigDecimal("1200")))
    }
}
