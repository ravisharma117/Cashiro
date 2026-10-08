package com.ritesh.cashiro.data.repository

import com.ritesh.cashiro.data.database.dao.BillPaymentDao
import com.ritesh.cashiro.data.database.dao.SubscriptionDao
import com.ritesh.cashiro.data.database.entity.BillPaymentEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionState
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory stand-ins for the two DAOs, so bill logic can be tested without a database. */
class FakeSubscriptionDao : SubscriptionDao {
    val items = mutableMapOf<Long, SubscriptionEntity>()
    private var nextId = 1L

    override fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = flowOf(items.values.toList())
    override fun getSubscriptionsByState(state: SubscriptionState): Flow<List<SubscriptionEntity>> =
        flowOf(items.values.filter { it.state == state })
    override fun getActiveSubscriptions(): Flow<List<SubscriptionEntity>> =
        flowOf(items.values.filter { it.state == SubscriptionState.ACTIVE })
    override fun getUpcomingSubscriptions(date: LocalDate): Flow<List<SubscriptionEntity>> =
        flowOf(items.values.filter { it.state == SubscriptionState.ACTIVE && (it.nextPaymentDate ?: date) <= date })
    override suspend fun getActiveSubscriptionByMerchant(merchantName: String) =
        items.values.firstOrNull { it.merchantName == merchantName && it.state == SubscriptionState.ACTIVE }
    override suspend fun getHiddenSubscriptionByMerchant(merchantName: String) =
        items.values.firstOrNull { it.merchantName == merchantName && it.state == SubscriptionState.HIDDEN }
    override suspend fun getSubscriptionByUmn(umn: String) = items.values.firstOrNull { it.umn == umn }
    override suspend fun getSubscriptionByMerchantAmountAndDate(
        merchantName: String, amount: BigDecimal, paymentDate: LocalDate
    ) = items.values.firstOrNull {
        it.merchantName == merchantName && it.amount == amount && it.nextPaymentDate == paymentDate
    }
    override suspend fun getSubscriptionByMerchantAndAmount(merchantName: String, amount: BigDecimal) =
        items.values.firstOrNull { it.merchantName == merchantName && it.amount == amount }
    override suspend fun getSubscriptionById(id: Long) = items[id]
    override suspend fun getByRecurringId(recurringId: Long) = items.values.firstOrNull { it.recurringId == recurringId }
    override suspend fun getActiveList() =
        items.values.filter { it.state == SubscriptionState.ACTIVE }.sortedBy { it.nextPaymentDate }
    override suspend fun updateReminderMarks(id: Long, beforeDue: LocalDate?, overdue: LocalDate?) {
        items[id]?.let { items[id] = it.copy(lastReminderFor = beforeDue, overdueRemindedFor = overdue) }
    }
    override suspend fun insertSubscription(subscription: SubscriptionEntity): Long {
        val id = if (subscription.id == 0L) nextId++ else subscription.id
        items[id] = subscription.copy(id = id)
        return id
    }
    override suspend fun updateSubscription(subscription: SubscriptionEntity) { items[subscription.id] = subscription }
    override suspend fun updateSubscriptionState(id: Long, state: SubscriptionState) {
        items[id]?.let { items[id] = it.copy(state = state) }
    }
    override suspend fun updateNextPaymentDate(id: Long, nextPaymentDate: LocalDate) {
        items[id]?.let { items[id] = it.copy(nextPaymentDate = nextPaymentDate) }
    }
    override suspend fun updatePaymentStatus(id: Long, nextPaymentDate: LocalDate, lastPaidDate: LocalDate?) {
        items[id]?.let { items[id] = it.copy(nextPaymentDate = nextPaymentDate, lastPaidDate = lastPaidDate) }
    }
    override suspend fun deleteSubscription(subscription: SubscriptionEntity) { items.remove(subscription.id) }
    override suspend fun deleteSubscriptionById(id: Long) { items.remove(id) }
    override suspend fun getSubscriptionsByStateList(state: SubscriptionState) =
        items.values.filter { it.state == state }
    override suspend fun deleteSampleSubscriptions() { items.values.removeAll { it.isSample } }
    override suspend fun deleteAllSubscriptions() { items.clear() }
}

class FakeBillPaymentDao : BillPaymentDao {
    val payments = mutableListOf<BillPaymentEntity>()
    private var nextId = 1L

    override fun observeFor(subscriptionId: Long): Flow<List<BillPaymentEntity>> =
        flowOf(payments.filter { it.subscriptionId == subscriptionId }.sortedByDescending { it.dueDate })
    override suspend fun getForCycle(subscriptionId: Long, dueDate: LocalDate) =
        payments.firstOrNull { it.subscriptionId == subscriptionId && it.dueDate == dueDate }
    override suspend fun upsert(payment: BillPaymentEntity): Long {
        payments.removeAll { it.subscriptionId == payment.subscriptionId && it.dueDate == payment.dueDate }
        val id = if (payment.id == 0L) nextId++ else payment.id
        payments += payment.copy(id = id)
        return id
    }
    override suspend fun update(payment: BillPaymentEntity) {
        val index = payments.indexOfFirst { it.id == payment.id }
        if (index >= 0) payments[index] = payment
    }
    override suspend fun getAll() = payments.toList()
    override suspend fun insertAll(payments: List<BillPaymentEntity>) { payments.forEach { upsert(it) } }
    override suspend fun deleteAll() { payments.clear() }
}
