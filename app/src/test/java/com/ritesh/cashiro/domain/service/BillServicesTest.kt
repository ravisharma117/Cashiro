package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.entity.RecurrenceFrequency
import com.ritesh.cashiro.data.database.entity.RecurrenceUnit
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionKind
import com.ritesh.cashiro.data.database.entity.SubscriptionState
import com.ritesh.cashiro.data.repository.FakeSubscriptionDao
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BillingCycleMapperTest {

    @Test
    fun standardCycles() {
        assertEquals(CycleSchedule(RecurrenceFrequency.WEEKLY, 1, null, null), BillingCycleMapper.toSchedule("weekly"))
        assertEquals(CycleSchedule(RecurrenceFrequency.MONTHLY, 1, null, null), BillingCycleMapper.toSchedule("monthly"))
        assertEquals(CycleSchedule(RecurrenceFrequency.YEARLY, 1, null, null), BillingCycleMapper.toSchedule("annual"))
        assertEquals(
            CycleSchedule(RecurrenceFrequency.CUSTOM, 3, RecurrenceUnit.MONTH, null),
            BillingCycleMapper.toSchedule("quarterly")
        )
        assertEquals(
            CycleSchedule(RecurrenceFrequency.CUSTOM, 6, RecurrenceUnit.MONTH, null),
            BillingCycleMapper.toSchedule("semi-annual")
        )
    }

    @Test
    fun noCycleMeansMonthly() {
        assertEquals(RecurrenceFrequency.MONTHLY, BillingCycleMapper.toSchedule(null).frequency)
    }

    @Test
    fun customCycleWithAnEndDate() {
        val result = BillingCycleMapper.toSchedule("custom_2_week_2027-01-31")
        assertEquals(CycleSchedule(RecurrenceFrequency.CUSTOM, 2, RecurrenceUnit.WEEK, LocalDate.parse("2027-01-31")), result)
    }

    @Test
    fun customCycleForeverHasNoEnd() {
        assertNull(BillingCycleMapper.toSchedule("custom_10_day_forever").endDate)
    }

    @Test
    fun anUnreadableCustomCycleFallsBackToMonthly() {
        assertEquals(RecurrenceFrequency.MONTHLY, BillingCycleMapper.toSchedule("custom_x_day").frequency)
    }
}

class UpcomingPaymentsCalculatorTest {

    private val today = LocalDate.parse("2026-10-10")

    private fun sub(
        name: String,
        due: String?,
        lastPaid: String? = null,
        state: SubscriptionState = SubscriptionState.ACTIVE,
        amount: String = "100"
    ) = SubscriptionEntity(
        id = name.hashCode().toLong(),
        merchantName = name,
        amount = BigDecimal(amount),
        nextPaymentDate = due?.let(LocalDate::parse),
        lastPaidDate = lastPaid?.let(LocalDate::parse),
        state = state
    )

    @Test
    fun theWindowIncludesDayZeroAndDayThirtyButNotDayThirtyOne() {
        val result = UpcomingPaymentsCalculator.build(
            listOf(sub("today", "2026-10-10"), sub("day30", "2026-11-09"), sub("day31", "2026-11-10")),
            today
        )
        assertEquals(listOf("today", "day30"), (result.thisWeek + result.later).map { it.subscription.merchantName })
    }

    @Test
    fun groupsAreOverdueThisWeekAndLater() {
        val result = UpcomingPaymentsCalculator.build(
            listOf(
                sub("late", "2026-10-05"),
                sub("soon", "2026-10-12"),
                sub("edge", "2026-10-17"),
                sub("far", "2026-10-18")
            ),
            today
        )
        assertEquals(listOf("late"), result.overdue.map { it.subscription.merchantName })
        assertEquals(listOf("soon", "edge"), result.thisWeek.map { it.subscription.merchantName })
        assertEquals(listOf("far"), result.later.map { it.subscription.merchantName })
        assertEquals(-5L, result.overdue.single().daysUntil)
    }

    @Test
    fun aPastDateThatWasPaidIsNotOverdue() {
        val result = UpcomingPaymentsCalculator.build(listOf(sub("paid", "2026-10-05", lastPaid = "2026-10-05")), today)
        assertTrue(result.isEmpty)
    }

    @Test
    fun hiddenAndUndatedAreLeftOut() {
        val result = UpcomingPaymentsCalculator.build(
            listOf(sub("hidden", "2026-10-12", state = SubscriptionState.HIDDEN), sub("undated", null)),
            today
        )
        assertTrue(result.isEmpty)
    }

    @Test
    fun theTotalAddsEverythingShownAndUsesTheAmountFunction() {
        val subs = listOf(sub("a", "2026-10-05", amount = "100"), sub("b", "2026-10-12", amount = "250"))
        assertEquals(BigDecimal("350"), UpcomingPaymentsCalculator.build(subs, today).total)
        assertEquals(
            BigDecimal("700"),
            UpcomingPaymentsCalculator.build(subs, today) { it.amount.multiply(BigDecimal(2)) }.total
        )
    }

    @Test
    fun everythingIsSortedByDate() {
        val result = UpcomingPaymentsCalculator.build(
            listOf(sub("c", "2026-10-25"), sub("a", "2026-10-11"), sub("b", "2026-10-15")),
            today
        )
        assertEquals(listOf("a", "b", "c"), (result.thisWeek + result.later).map { it.subscription.merchantName })
    }
}

class BillReminderProcessorTest {

    private val dao = FakeSubscriptionDao()
    private val processor = BillReminderProcessor(dao)
    private fun at(text: String) = LocalDateTime.parse(text)

    private suspend fun add(
        due: String,
        kind: SubscriptionKind = SubscriptionKind.BILL,
        reminderDays: Int? = null,
        lastPaid: String? = null
    ): Long = dao.insertSubscription(
        SubscriptionEntity(
            merchantName = "Utility",
            amount = BigDecimal("500"),
            nextPaymentDate = LocalDate.parse(due),
            kind = kind,
            reminderDaysBefore = reminderDays,
            lastPaidDate = lastPaid?.let(LocalDate::parse)
        )
    )

    @Test
    fun aBillRemindsThreeDaysAheadByDefaultAndOnlyOnce() = runBlocking<Unit> {
        add("2026-10-13")

        assertTrue(processor.process(at("2026-10-09T09:00:00"), true, emptySet()).isEmpty())

        val first = processor.process(at("2026-10-10T09:00:00"), true, emptySet())
        assertEquals(1, first.size)
        assertEquals(3L, first.single().daysUntil)

        assertTrue(processor.process(at("2026-10-10T15:00:00"), true, emptySet()).isEmpty())
        assertTrue(processor.process(at("2026-10-11T09:00:00"), true, emptySet()).isEmpty())
    }

    @Test
    fun aSubscriptionOnlyRemindsWhenAskedTo() = runBlocking<Unit> {
        add("2026-10-11", kind = SubscriptionKind.SUBSCRIPTION)
        assertTrue(processor.process(at("2026-10-10T09:00:00"), true, emptySet()).isEmpty())

        dao.items.values.single().let { dao.updateSubscription(it.copy(reminderDaysBefore = 2)) }
        assertEquals(1, processor.process(at("2026-10-10T09:00:00"), true, emptySet()).size)
    }

    @Test
    fun zeroDaysTurnsTheDueSoonReminderOff() = runBlocking<Unit> {
        add("2026-10-11", reminderDays = 0)
        assertTrue(processor.process(at("2026-10-10T09:00:00"), true, emptySet()).isEmpty())
    }

    @Test
    fun anUnpaidBillGetsOneOverdueReminder() = runBlocking<Unit> {
        add("2026-10-08", reminderDays = 0)

        val reminders = processor.process(at("2026-10-09T09:00:00"), true, emptySet())
        assertEquals(1, reminders.size)
        assertTrue(reminders.single().overdue)
        assertTrue(processor.process(at("2026-10-10T09:00:00"), true, emptySet()).isEmpty())
    }

    @Test
    fun aPaidPastDateGetsNoOverdueReminder() = runBlocking<Unit> {
        add("2026-10-08", lastPaid = "2026-10-08")
        assertTrue(processor.process(at("2026-10-09T09:00:00"), true, emptySet()).isEmpty())
    }

    @Test
    fun nothingGoesOutBeforeRunTimeOrWhenSwitchedOff() = runBlocking<Unit> {
        val id = add("2026-10-11")

        assertTrue(processor.process(at("2026-10-10T07:00:00"), true, emptySet()).isEmpty())
        assertTrue(processor.process(at("2026-10-10T09:00:00"), false, emptySet()).isEmpty())
        assertTrue(processor.process(at("2026-10-10T09:00:00"), true, setOf(id)).isEmpty())
        // Nothing was marked as sent while it could not be, so it still goes out later
        assertEquals(1, processor.process(at("2026-10-10T10:00:00"), true, emptySet()).size)
    }

    @Test
    fun theAlarmWaitsForTheReminderMomentAndIgnoresSwitchedOffItems() = runBlocking<Unit> {
        val id = add("2026-10-20")
        val subs = dao.items.values.toList()
        val now = at("2026-10-10T10:00:00")

        assertEquals(at("2026-10-17T08:00:00"), BillReminderProcessor.nextWake(now, subs, true, emptySet()))
        assertNull(BillReminderProcessor.nextWake(now, subs, false, emptySet()))
        assertEquals(
            at("2026-10-21T08:00:00"),
            BillReminderProcessor.nextWake(now, subs.map { it.copy(lastReminderFor = LocalDate.parse("2026-10-20")) }, true, emptySet())
        )
        assertNull(BillReminderProcessor.nextWake(now, subs, true, setOf(id)))
    }

    @Test
    fun theAlarmDoesNotPollThroughTheNightForAnUnsentReminder() = runBlocking<Unit> {
        add("2026-10-12") // reminder moment was 2026-10-09 08:00
        val subs = dao.items.values.toList()

        assertEquals(
            at("2026-10-10T08:00:00"),
            BillReminderProcessor.nextWake(at("2026-10-10T03:00:00"), subs, true, emptySet())
        )
    }
}
