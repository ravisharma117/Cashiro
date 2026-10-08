package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.entity.RecurrenceUnit
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class RecurrenceCalculatorTest {

    private fun date(text: String) = LocalDate.parse(text)

    private fun rule(unit: RecurrenceUnit, interval: Int, start: String, end: String? = null) =
        RecurrenceRule(unit, interval, date(start), end?.let(::date))

    private fun dates(rule: RecurrenceRule, count: Int): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var next: LocalDate? = RecurrenceCalculator.firstOnOrAfter(rule, rule.start)
        while (next != null && result.size < count) {
            result += next
            next = RecurrenceCalculator.nextAfter(rule, next)
        }
        return result
    }

    @Test
    fun dailyRepeatsEveryDay() {
        val r = rule(RecurrenceUnit.DAY, 1, "2026-10-30")
        assertEquals(listOf("2026-10-30", "2026-10-31", "2026-11-01").map(::date), dates(r, 3))
    }

    @Test
    fun everyTwoWeeksKeepsTheWeekday() {
        val r = rule(RecurrenceUnit.WEEK, 2, "2026-10-05")
        assertEquals(listOf("2026-10-05", "2026-10-19", "2026-11-02").map(::date), dates(r, 3))
    }

    @Test
    fun monthlyOnThe31stUsesTheLastDayOfShortMonthsThenReturnsToThe31st() {
        val r = rule(RecurrenceUnit.MONTH, 1, "2027-01-31")
        assertEquals(
            listOf("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30", "2027-05-31").map(::date),
            dates(r, 5)
        )
    }

    @Test
    fun monthlyOnThe31stInALeapYearUsesTheTwentyNinth() {
        val r = rule(RecurrenceUnit.MONTH, 1, "2028-01-31")
        assertEquals(date("2028-02-29"), dates(r, 2)[1])
    }

    @Test
    fun yearlyOnLeapDayRunsOnThe28thInCommonYears() {
        val r = rule(RecurrenceUnit.YEAR, 1, "2028-02-29")
        assertEquals(
            listOf("2028-02-29", "2029-02-28", "2030-02-28", "2031-02-28", "2032-02-29").map(::date),
            dates(r, 5)
        )
    }

    @Test
    fun customEveryTenDays() {
        val r = rule(RecurrenceUnit.DAY, 10, "2026-10-01")
        assertEquals(listOf("2026-10-01", "2026-10-11", "2026-10-21", "2026-10-31").map(::date), dates(r, 4))
    }

    @Test
    fun customEveryThreeMonths() {
        val r = rule(RecurrenceUnit.MONTH, 3, "2026-01-15")
        assertEquals(listOf("2026-01-15", "2026-04-15", "2026-07-15", "2026-10-15").map(::date), dates(r, 4))
    }

    @Test
    fun aStartDateInTheFutureIsTheFirstDate() {
        val r = rule(RecurrenceUnit.MONTH, 1, "2026-12-05")
        assertEquals(date("2026-12-05"), RecurrenceCalculator.firstOnOrAfter(r, date("2026-10-01")))
    }

    @Test
    fun firstOnOrAfterSkipsToTheRightOccurrence() {
        val r = rule(RecurrenceUnit.MONTH, 1, "2026-01-05")
        assertEquals(date("2026-04-05"), RecurrenceCalculator.firstOnOrAfter(r, date("2026-03-06")))
        assertEquals(date("2026-03-05"), RecurrenceCalculator.firstOnOrAfter(r, date("2026-03-05")))
    }

    @Test
    fun firstOnOrAfterWorksFarFromTheStart() {
        val r = rule(RecurrenceUnit.DAY, 7, "2020-01-01")
        val result = RecurrenceCalculator.firstOnOrAfter(r, date("2026-10-08"))!!
        assertEquals(0, java.time.temporal.ChronoUnit.DAYS.between(date("2020-01-01"), result) % 7)
        assertEquals(true, !result.isBefore(date("2026-10-08")) && result.isBefore(date("2026-10-15")))
    }

    @Test
    fun theEndDateIsIncludedAndNothingComesAfterIt() {
        val r = rule(RecurrenceUnit.DAY, 1, "2026-10-01", end = "2026-10-03")
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03").map(::date), dates(r, 10))
        assertNull(RecurrenceCalculator.nextAfter(r, date("2026-10-03")))
    }

    @Test
    fun nextAfterMovesToTheFollowingDate() {
        val r = rule(RecurrenceUnit.MONTH, 1, "2027-01-31")
        assertEquals(date("2027-02-28"), RecurrenceCalculator.nextAfter(r, date("2027-01-31")))
        assertEquals(date("2027-03-31"), RecurrenceCalculator.nextAfter(r, date("2027-02-28")))
    }
}
