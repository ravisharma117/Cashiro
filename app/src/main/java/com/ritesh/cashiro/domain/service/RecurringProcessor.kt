package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.dao.RecurringTransactionDao
import com.ritesh.cashiro.data.database.entity.OccurrenceStatus
import com.ritesh.cashiro.data.database.entity.RecurringOccurrenceEntity
import com.ritesh.cashiro.data.database.entity.RecurringState
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A schedule and the due date something happened for. */
data class RecurringEvent(val schedule: RecurringTransactionEntity, val dueDate: LocalDate)

data class RecurringResult(
    /** Transactions that were created by themselves. */
    val created: List<RecurringEvent> = emptyList(),
    /** Due dates of remind-only schedules, waiting for the user. */
    val pending: List<RecurringEvent> = emptyList(),
    /** Schedules that are due soon and should send a reminder now. */
    val reminders: List<RecurringEvent> = emptyList()
)

/**
 * Turns due dates into transactions. Pure logic over the DAO; the transaction itself is made by
 * [createTransaction], which returns the new transaction id.
 *
 * - Runs at [RUN_TIME] on the due date. Before that time the day is not due yet.
 * - Safe to run again and again: a (schedule, date) pair that already has a row is skipped, so a
 *   second run, an app start and an alarm can overlap without duplicating anything.
 * - After a long gap (phone off, app unused) only the latest missed date creates a transaction.
 *   The earlier ones are recorded as skipped so they never come back.
 */
class RecurringProcessor(
    private val dao: RecurringTransactionDao,
    private val createTransaction: suspend (RecurringTransactionEntity, LocalDate) -> Long
) {

    suspend fun process(now: LocalDateTime): RecurringResult {
        val cutoff = dueCutoff(now)
        val reminderTime = !now.toLocalTime().isBefore(RUN_TIME)
        val today = now.toLocalDate()

        val created = mutableListOf<RecurringEvent>()
        val pending = mutableListOf<RecurringEvent>()
        val reminders = mutableListOf<RecurringEvent>()

        for (schedule in dao.getActive()) {
            if (schedule.nextRunDate.isAfter(cutoff)) {
                maybeRemind(schedule, today, reminderTime)?.let { reminders += it }
                continue
            }

            val rule = schedule.toRule()
            val dueDates = mutableListOf<LocalDate>()
            var date: LocalDate? = schedule.nextRunDate
            while (date != null && !date.isAfter(cutoff) && dueDates.size < MAX_CATCH_UP) {
                dueDates += date
                date = RecurrenceCalculator.nextAfter(rule, date)
            }
            val latest = dueDates.last()

            dueDates.dropLast(1).forEach { missed ->
                dao.insertOccurrence(
                    RecurringOccurrenceEntity(
                        recurringId = schedule.id,
                        dueDate = missed,
                        status = OccurrenceStatus.SKIPPED
                    )
                )
            }

            val rowId = dao.insertOccurrence(
                RecurringOccurrenceEntity(
                    recurringId = schedule.id,
                    dueDate = latest,
                    status = OccurrenceStatus.PENDING
                )
            )
            // -1 means another run already handled this date.
            if (rowId != -1L) {
                if (schedule.autoCreate) {
                    val transactionId = createTransaction(schedule, latest)
                    dao.getOccurrence(schedule.id, latest)?.let {
                        dao.updateOccurrence(
                            it.copy(status = OccurrenceStatus.CREATED, transactionId = transactionId)
                        )
                    }
                    created += RecurringEvent(schedule, latest)
                } else {
                    pending += RecurringEvent(schedule, latest)
                }
            }

            dao.update(
                schedule.copy(
                    lastRunDate = latest,
                    nextRunDate = date ?: latest,
                    state = if (date == null) RecurringState.ENDED else schedule.state,
                    updatedAt = now
                )
            )
        }
        return RecurringResult(created, pending, reminders)
    }

    /** Sends at most one reminder per due date. */
    private suspend fun maybeRemind(
        schedule: RecurringTransactionEntity,
        today: LocalDate,
        reminderTime: Boolean
    ): RecurringEvent? {
        if (!reminderTime || schedule.reminderDaysBefore <= 0) return null
        if (schedule.lastReminderFor == schedule.nextRunDate) return null
        if (today.isBefore(schedule.nextRunDate.minusDays(schedule.reminderDaysBefore.toLong()))) return null
        dao.update(schedule.copy(lastReminderFor = schedule.nextRunDate))
        return RecurringEvent(schedule, schedule.nextRunDate)
    }

    companion object {
        /** Time of day a due date, and its reminders, take effect. */
        val RUN_TIME: LocalTime = LocalTime.of(8, 0)

        /** A safety stop so a corrupted date can never loop for long. */
        const val MAX_CATCH_UP = 1000

        /** The last date that counts as due at [now]: today from [RUN_TIME] on, else yesterday. */
        fun dueCutoff(now: LocalDateTime): LocalDate =
            if (now.toLocalTime().isBefore(RUN_TIME)) now.toLocalDate().minusDays(1) else now.toLocalDate()

        /**
         * When the alarm should wake the app next: the earliest due moment or reminder moment of
         * any active schedule. Overdue work is due one minute from now. Null means nothing to wait for.
         */
        fun nextWake(now: LocalDateTime, schedules: List<RecurringTransactionEntity>): LocalDateTime? {
            val moments = schedules.filter { it.state == RecurringState.ACTIVE }.flatMap { s ->
                buildList {
                    add(s.nextRunDate.atTime(RUN_TIME))
                    if (s.reminderDaysBefore > 0 && s.lastReminderFor != s.nextRunDate) {
                        add(s.nextRunDate.minusDays(s.reminderDaysBefore.toLong()).atTime(RUN_TIME))
                    }
                }
            }
            val earliest = moments.minOrNull() ?: return null
            return actionableAt(earliest, now)
        }

        /**
         * When work whose moment is [moment] can really be done. A moment already past is
         * actionable in a minute, except before [RUN_TIME]: nothing is due yet then, so waiting
         * for [RUN_TIME] avoids waking up every minute through the night.
         */
        fun actionableAt(moment: LocalDateTime, now: LocalDateTime): LocalDateTime = when {
            moment.isAfter(now) -> moment
            now.toLocalTime().isBefore(RUN_TIME) -> now.toLocalDate().atTime(RUN_TIME)
            else -> now.plusMinutes(1)
        }
    }
}
