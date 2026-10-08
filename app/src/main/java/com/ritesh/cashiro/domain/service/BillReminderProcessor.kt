package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.dao.SubscriptionDao
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class BillReminder(
    val subscription: SubscriptionEntity,
    val dueDate: LocalDate,
    /** Days until the due date; negative once overdue. */
    val daysUntil: Long,
    val overdue: Boolean
)

/**
 * Decides which bills and subscriptions need a reminder now, and when the next one will.
 *
 * - "Due soon": [reminderDays] before the due date, once per due date.
 * - "Overdue": the day after the due date if it is still unpaid, once per due date.
 * - Respects the global upcoming-notifications switch and the per-item off switches.
 * - Like recurring schedules, reminders go out from [RecurringProcessor.RUN_TIME] on.
 */
class BillReminderProcessor(private val dao: SubscriptionDao) {

    suspend fun process(
        now: LocalDateTime,
        enabled: Boolean,
        disabledIds: Set<Long>
    ): List<BillReminder> {
        if (!enabled || now.toLocalTime().isBefore(RecurringProcessor.RUN_TIME)) return emptyList()
        val today = now.toLocalDate()
        val reminders = mutableListOf<BillReminder>()

        for (sub in dao.getActiveList()) {
            val due = sub.nextPaymentDate ?: continue
            if (sub.id in disabledIds) continue

            if (UpcomingPaymentsCalculator.isOverdue(sub, today)) {
                if (sub.overdueRemindedFor != due) {
                    dao.updateReminderMarks(sub.id, sub.lastReminderFor, due)
                    reminders += BillReminder(sub, due, ChronoUnit.DAYS.between(today, due), overdue = true)
                }
                continue
            }

            val days = reminderDays(sub)
            if (days > 0 && !due.isBefore(today) && sub.lastReminderFor != due &&
                !today.isBefore(due.minusDays(days.toLong()))
            ) {
                dao.updateReminderMarks(sub.id, due, sub.overdueRemindedFor)
                reminders += BillReminder(sub, due, ChronoUnit.DAYS.between(today, due), overdue = false)
            }
        }
        return reminders
    }

    companion object {
        /** Bills remind 3 days ahead unless set; subscriptions only remind when set. */
        const val DEFAULT_BILL_REMINDER_DAYS = 3

        fun reminderDays(sub: SubscriptionEntity): Int =
            sub.reminderDaysBefore ?: if (sub.kind == SubscriptionKind.BILL) DEFAULT_BILL_REMINDER_DAYS else 0

        /**
         * The next moment a reminder could go out. Mirrors [process] so the alarm never wakes for
         * nothing: nothing when notifications are off, items are switched off, or already sent.
         */
        fun nextWake(
            now: LocalDateTime,
            subscriptions: List<SubscriptionEntity>,
            enabled: Boolean,
            disabledIds: Set<Long>
        ): LocalDateTime? {
            if (!enabled) return null
            val today = now.toLocalDate()
            val moments = subscriptions.flatMap { sub ->
                val due = sub.nextPaymentDate
                if (due == null || sub.id in disabledIds) return@flatMap emptyList()
                buildList {
                    if (UpcomingPaymentsCalculator.isOverdue(sub, today)) {
                        if (sub.overdueRemindedFor != due) add(now)
                    } else {
                        val days = reminderDays(sub)
                        if (days > 0 && !due.isBefore(today) && sub.lastReminderFor != due) {
                            add(due.minusDays(days.toLong()).atTime(RecurringProcessor.RUN_TIME))
                        }
                        // Becomes overdue (and reminds) the morning after the due date
                        if (sub.overdueRemindedFor != due) add(due.plusDays(1).atTime(RecurringProcessor.RUN_TIME))
                    }
                }
            }
            val earliest = moments.minOrNull() ?: return null
            return RecurringProcessor.actionableAt(earliest, now)
        }
    }
}
