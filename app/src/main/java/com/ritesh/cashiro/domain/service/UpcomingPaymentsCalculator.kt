package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionState
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class UpcomingItem(
    val subscription: SubscriptionEntity,
    val dueDate: LocalDate,
    /** Negative when overdue. */
    val daysUntil: Long
)

data class UpcomingPayments(
    val overdue: List<UpcomingItem>,
    val thisWeek: List<UpcomingItem>,
    val later: List<UpcomingItem>,
    val total: BigDecimal
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && thisWeek.isEmpty() && later.isEmpty()
    val count: Int get() = overdue.size + thisWeek.size + later.size
}

/** What is due soon: overdue unpaid items, then this week, then later within the window. */
object UpcomingPaymentsCalculator {

    const val WINDOW_DAYS = 30
    private const val WEEK_DAYS = 7L

    /** Same rule the list rows use: past due and not paid for that date. */
    fun isOverdue(subscription: SubscriptionEntity, today: LocalDate): Boolean {
        val due = subscription.nextPaymentDate ?: return false
        return due.isBefore(today) &&
            (subscription.lastPaidDate == null || subscription.lastPaidDate.isBefore(due))
    }

    fun build(
        subscriptions: List<SubscriptionEntity>,
        today: LocalDate,
        days: Int = WINDOW_DAYS,
        amountOf: (SubscriptionEntity) -> BigDecimal = { it.amount }
    ): UpcomingPayments {
        val limit = today.plusDays(days.toLong())
        val items = subscriptions
            .filter { it.state == SubscriptionState.ACTIVE }
            .mapNotNull { sub ->
                val due = sub.nextPaymentDate ?: return@mapNotNull null
                val include = if (due.isBefore(today)) isOverdue(sub, today) else !due.isAfter(limit)
                if (include) UpcomingItem(sub, due, ChronoUnit.DAYS.between(today, due)) else null
            }
            .sortedBy { it.dueDate }

        return UpcomingPayments(
            overdue = items.filter { it.daysUntil < 0 },
            thisWeek = items.filter { it.daysUntil in 0..WEEK_DAYS },
            later = items.filter { it.daysUntil > WEEK_DAYS },
            total = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(amountOf(item.subscription)) }
        )
    }
}
