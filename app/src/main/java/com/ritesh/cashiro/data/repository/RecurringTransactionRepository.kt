package com.ritesh.cashiro.data.repository

import com.ritesh.cashiro.data.database.dao.RecurringTransactionDao
import com.ritesh.cashiro.data.database.entity.OccurrenceStatus
import com.ritesh.cashiro.data.database.entity.RecurringOccurrenceEntity
import com.ritesh.cashiro.data.database.entity.RecurringState
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import com.ritesh.cashiro.data.manager.RecurringAlarmScheduler
import com.ritesh.cashiro.domain.service.RecurrenceCalculator
import com.ritesh.cashiro.domain.service.RecurringProcessor
import com.ritesh.cashiro.domain.service.toRule
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Reading and changing recurring schedules. Every change re-arms the alarm, because the next
 * moment the app has to wake up may have moved.
 */
@Singleton
class RecurringTransactionRepository @Inject constructor(
    private val dao: RecurringTransactionDao,
    private val scheduler: RecurringAlarmScheduler
) {

    fun observeAll(): Flow<List<RecurringTransactionEntity>> = dao.observeAll()

    fun observeOccurrences(recurringId: Long): Flow<List<RecurringOccurrenceEntity>> =
        dao.observeOccurrences(recurringId)

    suspend fun get(id: Long): RecurringTransactionEntity? = dao.getById(id)

    /** Creates a schedule. Its first due date is its start date, or the first one after it. */
    suspend fun create(draft: RecurringTransactionEntity, now: LocalDateTime = LocalDateTime.now()): Long {
        val first = RecurrenceCalculator.firstOnOrAfter(draft.toRule(), draft.startDate)
        val id = dao.insert(
            draft.copy(
                id = 0,
                nextRunDate = first ?: draft.startDate,
                state = if (first == null) RecurringState.ENDED else RecurringState.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
        )
        scheduler.reschedule()
        return id
    }

    /**
     * Saves edits. They apply from the next run on: transactions already created stay as they
     * are. The next due date is only recalculated when the repeat rule itself changed.
     */
    suspend fun update(edited: RecurringTransactionEntity, now: LocalDateTime = LocalDateTime.now()) {
        val old = dao.getById(edited.id) ?: return
        val ruleChanged = old.toRule() != edited.toRule()
        var result = edited.copy(updatedAt = now)
        if (ruleChanged || old.state == RecurringState.ENDED) {
            val from = maxOf(edited.startDate, RecurringProcessor.dueCutoff(now).plusDays(1))
            val next = RecurrenceCalculator.firstOnOrAfter(edited.toRule(), from)
            result = result.copy(
                nextRunDate = next ?: old.nextRunDate,
                state = when {
                    next == null -> RecurringState.ENDED
                    old.state == RecurringState.PAUSED -> RecurringState.PAUSED
                    else -> RecurringState.ACTIVE
                }
            )
        } else {
            result = result.copy(nextRunDate = old.nextRunDate, state = old.state)
        }
        dao.update(result)
        scheduler.reschedule()
    }

    /** Skips the next due date: it gets a skipped row and the schedule moves on. */
    suspend fun skipNext(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        val schedule = dao.getById(id) ?: return
        if (schedule.state != RecurringState.ACTIVE) return
        dao.insertOccurrence(
            RecurringOccurrenceEntity(
                recurringId = id,
                dueDate = schedule.nextRunDate,
                status = OccurrenceStatus.SKIPPED
            )
        )
        val next = RecurrenceCalculator.nextAfter(schedule.toRule(), schedule.nextRunDate)
        dao.update(
            schedule.copy(
                lastRunDate = schedule.nextRunDate,
                nextRunDate = next ?: schedule.nextRunDate,
                state = if (next == null) RecurringState.ENDED else schedule.state,
                updatedAt = now
            )
        )
        scheduler.reschedule()
    }

    suspend fun pause(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        val schedule = dao.getById(id) ?: return
        if (schedule.state != RecurringState.ACTIVE) return
        dao.update(schedule.copy(state = RecurringState.PAUSED, updatedAt = now))
        scheduler.reschedule()
    }

    /** Resumes from today. The paused stretch is not back-filled. */
    suspend fun resume(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        val schedule = dao.getById(id) ?: return
        if (schedule.state != RecurringState.PAUSED) return
        val from = maxOf(schedule.startDate, RecurringProcessor.dueCutoff(now).plusDays(1))
        val next = RecurrenceCalculator.firstOnOrAfter(schedule.toRule(), from)
        dao.update(
            schedule.copy(
                nextRunDate = next ?: schedule.nextRunDate,
                state = if (next == null) RecurringState.ENDED else RecurringState.ACTIVE,
                updatedAt = now
            )
        )
        scheduler.reschedule()
    }

    /** Removes the schedule and its history. Transactions it already created are kept. */
    suspend fun delete(id: Long) {
        dao.delete(id)
        scheduler.reschedule()
    }

    /** Marks a waiting (remind-only) date as skipped. */
    suspend fun skipPending(id: Long, dueDate: LocalDate) {
        val occurrence = dao.getOccurrence(id, dueDate) ?: return
        if (occurrence.status == OccurrenceStatus.PENDING) {
            dao.updateOccurrence(occurrence.copy(status = OccurrenceStatus.SKIPPED))
        }
    }
}
