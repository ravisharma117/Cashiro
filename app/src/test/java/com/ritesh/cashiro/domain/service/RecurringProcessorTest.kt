package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.dao.RecurringTransactionDao
import com.ritesh.cashiro.data.database.entity.OccurrenceStatus
import com.ritesh.cashiro.data.database.entity.RecurrenceFrequency
import com.ritesh.cashiro.data.database.entity.RecurringOccurrenceEntity
import com.ritesh.cashiro.data.database.entity.RecurringState
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeRecurringDao : RecurringTransactionDao {
    val schedules = mutableMapOf<Long, RecurringTransactionEntity>()
    val occurrences = mutableListOf<RecurringOccurrenceEntity>()
    private var nextId = 1L
    private var nextOccurrenceId = 1L

    override fun observeAll(): Flow<List<RecurringTransactionEntity>> = flowOf(schedules.values.toList())
    override suspend fun getAll() = schedules.values.sortedBy { it.id }
    override suspend fun getActive() =
        schedules.values.filter { it.state == RecurringState.ACTIVE }.sortedBy { it.nextRunDate }
    override suspend fun getById(id: Long) = schedules[id]
    override suspend fun insert(entity: RecurringTransactionEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        schedules[id] = entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: RecurringTransactionEntity) { schedules[entity.id] = entity }
    override suspend fun delete(id: Long) {
        schedules.remove(id)
        occurrences.removeAll { it.recurringId == id }
    }
    override suspend fun deleteAll() { schedules.clear(); occurrences.clear() }
    override suspend fun insertOccurrence(occurrence: RecurringOccurrenceEntity): Long {
        if (occurrences.any { it.recurringId == occurrence.recurringId && it.dueDate == occurrence.dueDate }) return -1L
        val id = nextOccurrenceId++
        occurrences += occurrence.copy(id = id)
        return id
    }
    override suspend fun updateOccurrence(occurrence: RecurringOccurrenceEntity) {
        val index = occurrences.indexOfFirst { it.id == occurrence.id }
        if (index >= 0) occurrences[index] = occurrence
    }
    override suspend fun getOccurrence(recurringId: Long, dueDate: LocalDate) =
        occurrences.firstOrNull { it.recurringId == recurringId && it.dueDate == dueDate }
    override fun observeOccurrences(recurringId: Long): Flow<List<RecurringOccurrenceEntity>> =
        flowOf(occurrences.filter { it.recurringId == recurringId })
    override suspend fun getAllOccurrences() = occurrences.toList()
    override suspend fun insertOccurrences(occurrences: List<RecurringOccurrenceEntity>) {
        occurrences.forEach { insertOccurrence(it) }
    }
    override suspend fun deleteAllOccurrences() { occurrences.clear() }
}

class RecurringProcessorTest {

    private val dao = FakeRecurringDao()
    private val createdFor = mutableListOf<LocalDate>()
    private var nextTransactionId = 100L

    private val processor = RecurringProcessor(dao) { _, due ->
        createdFor += due
        nextTransactionId++
    }

    private fun at(text: String) = LocalDateTime.parse(text)

    private suspend fun add(
        start: String,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        end: String? = null,
        autoCreate: Boolean = true,
        reminderDays: Int = 0,
        state: RecurringState = RecurringState.ACTIVE
    ): Long = dao.insert(
        RecurringTransactionEntity(
            title = "Streaming plan",
            amount = BigDecimal("649"),
            category = "Entertainment",
            frequency = frequency,
            startDate = LocalDate.parse(start),
            endDate = end?.let(LocalDate::parse),
            nextRunDate = LocalDate.parse(start),
            autoCreate = autoCreate,
            reminderDaysBefore = reminderDays,
            state = state
        )
    )

    @Test
    fun createsTheTransactionOnTheDueDateAndMovesToTheNextOne() = runBlocking<Unit> {
        val id = add("2026-10-05")

        val result = processor.process(at("2026-10-05T09:00:00"))

        assertEquals(listOf(LocalDate.parse("2026-10-05")), createdFor)
        assertEquals(1, result.created.size)
        val schedule = dao.schedules.getValue(id)
        assertEquals(LocalDate.parse("2026-11-05"), schedule.nextRunDate)
        assertEquals(LocalDate.parse("2026-10-05"), schedule.lastRunDate)
        val occurrence = dao.getOccurrence(id, LocalDate.parse("2026-10-05"))
        assertEquals(OccurrenceStatus.CREATED, occurrence?.status)
        assertEquals(100L, occurrence?.transactionId)
    }

    @Test
    fun theDueDateIsNotDueBeforeRunTime() = runBlocking<Unit> {
        add("2026-10-05")

        processor.process(at("2026-10-05T07:59:00"))
        assertTrue(createdFor.isEmpty())

        processor.process(at("2026-10-05T08:00:00"))
        assertEquals(1, createdFor.size)
    }

    @Test
    fun runningAgainCreatesNothingMore() = runBlocking<Unit> {
        add("2026-10-05")

        processor.process(at("2026-10-05T09:00:00"))
        processor.process(at("2026-10-05T09:30:00"))
        processor.process(at("2026-10-06T10:00:00"))

        assertEquals(1, createdFor.size)
    }

    @Test
    fun afterALongGapOnlyTheLatestMissedDateIsCreatedAndTheRestAreSkipped() = runBlocking<Unit> {
        val id = add("2026-10-01", RecurrenceFrequency.DAILY)

        processor.process(at("2026-10-04T09:00:00"))

        assertEquals(listOf(LocalDate.parse("2026-10-04")), createdFor)
        val statuses = dao.occurrences.filter { it.recurringId == id }.associate { it.dueDate.dayOfMonth to it.status }
        assertEquals(
            mapOf(
                1 to OccurrenceStatus.SKIPPED,
                2 to OccurrenceStatus.SKIPPED,
                3 to OccurrenceStatus.SKIPPED,
                4 to OccurrenceStatus.CREATED
            ),
            statuses
        )
        assertEquals(LocalDate.parse("2026-10-05"), dao.schedules.getValue(id).nextRunDate)
    }

    @Test
    fun aPausedScheduleCreatesNothing() = runBlocking<Unit> {
        add("2026-10-05", state = RecurringState.PAUSED)

        processor.process(at("2026-10-20T09:00:00"))

        assertTrue(createdFor.isEmpty())
        assertTrue(dao.occurrences.isEmpty())
    }

    @Test
    fun aRemindOnlyScheduleWaitsForTheUser() = runBlocking<Unit> {
        val id = add("2026-10-05", autoCreate = false)

        val result = processor.process(at("2026-10-05T09:00:00"))

        assertTrue(createdFor.isEmpty())
        assertEquals(1, result.pending.size)
        assertEquals(OccurrenceStatus.PENDING, dao.getOccurrence(id, LocalDate.parse("2026-10-05"))?.status)
        assertEquals(LocalDate.parse("2026-11-05"), dao.schedules.getValue(id).nextRunDate)
    }

    @Test
    fun theScheduleEndsAfterItsLastDate() = runBlocking<Unit> {
        val id = add("2026-10-05", end = "2026-10-20")

        processor.process(at("2026-10-05T09:00:00"))

        val schedule = dao.schedules.getValue(id)
        assertEquals(RecurringState.ENDED, schedule.state)
        assertEquals(1, createdFor.size)

        processor.process(at("2026-11-05T09:00:00"))
        assertEquals(1, createdFor.size)
    }

    @Test
    fun aSkippedDateCreatesNothing() = runBlocking<Unit> {
        val id = add("2026-10-05")
        dao.insertOccurrence(
            RecurringOccurrenceEntity(recurringId = id, dueDate = LocalDate.parse("2026-10-05"), status = OccurrenceStatus.SKIPPED)
        )

        val result = processor.process(at("2026-10-05T09:00:00"))

        assertTrue(createdFor.isEmpty())
        assertTrue(result.created.isEmpty())
        assertEquals(LocalDate.parse("2026-11-05"), dao.schedules.getValue(id).nextRunDate)
    }

    @Test
    fun aReminderIsSentOncePerDueDate() = runBlocking<Unit> {
        add("2026-10-10", reminderDays = 2)

        assertTrue(processor.process(at("2026-10-07T09:00:00")).reminders.isEmpty())

        val first = processor.process(at("2026-10-08T09:00:00"))
        assertEquals(1, first.reminders.size)
        assertEquals(LocalDate.parse("2026-10-10"), first.reminders.single().dueDate)

        assertTrue(processor.process(at("2026-10-08T12:00:00")).reminders.isEmpty())
        assertTrue(processor.process(at("2026-10-09T09:00:00")).reminders.isEmpty())
    }

    @Test
    fun noReminderBeforeRunTime() = runBlocking<Unit> {
        add("2026-10-10", reminderDays = 2)
        assertTrue(processor.process(at("2026-10-08T06:00:00")).reminders.isEmpty())
    }

    @Test
    fun earlierOccurrencesStayAsRecordedWhenTheScheduleIsEdited() = runBlocking<Unit> {
        val id = add("2026-10-05")
        processor.process(at("2026-10-05T09:00:00"))
        val firstOccurrence = dao.getOccurrence(id, LocalDate.parse("2026-10-05"))

        dao.update(dao.schedules.getValue(id).copy(amount = BigDecimal("799")))
        processor.process(at("2026-11-05T09:00:00"))

        // Each run builds its transaction from the schedule as it is at that time
        assertEquals(2, createdFor.size)
        assertEquals(OccurrenceStatus.CREATED, firstOccurrence?.status)
        assertEquals(100L, firstOccurrence?.transactionId)
        assertNotNull(dao.getOccurrence(id, LocalDate.parse("2026-11-05")))
    }

    @Test
    fun nextWakeIsTheEarliestDueOrReminderMoment() = runBlocking<Unit> {
        val now = at("2026-10-01T10:00:00")
        val a = RecurringTransactionEntity(
            id = 1, title = "A", amount = BigDecimal.ONE, category = "c",
            startDate = LocalDate.parse("2026-10-20"), nextRunDate = LocalDate.parse("2026-10-20"),
            reminderDaysBefore = 3
        )
        val b = a.copy(id = 2, nextRunDate = LocalDate.parse("2026-10-18"), reminderDaysBefore = 0)

        assertEquals(at("2026-10-17T08:00:00"), RecurringProcessor.nextWake(now, listOf(a)))
        assertEquals(at("2026-10-17T08:00:00"), RecurringProcessor.nextWake(now, listOf(a, b)))
        assertEquals(at("2026-10-18T08:00:00"), RecurringProcessor.nextWake(now, listOf(b)))
    }

    @Test
    fun nextWakeIgnoresPausedSchedulesAndHandlesOverdueWork() = runBlocking<Unit> {
        val now = at("2026-10-10T10:00:00")
        val paused = RecurringTransactionEntity(
            id = 1, title = "A", amount = BigDecimal.ONE, category = "c",
            startDate = LocalDate.parse("2026-10-20"), nextRunDate = LocalDate.parse("2026-10-20"),
            state = RecurringState.PAUSED
        )
        assertNull(RecurringProcessor.nextWake(now, listOf(paused)))

        val overdue = paused.copy(state = RecurringState.ACTIVE, nextRunDate = LocalDate.parse("2026-10-09"))
        assertEquals(now.plusMinutes(1), RecurringProcessor.nextWake(now, listOf(overdue)))
    }
}
