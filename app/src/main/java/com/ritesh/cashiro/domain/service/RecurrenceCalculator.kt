package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.entity.RecurrenceFrequency
import com.ritesh.cashiro.data.database.entity.RecurrenceUnit
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Everything the date maths needs, without the rest of a schedule. */
data class RecurrenceRule(
    val unit: RecurrenceUnit,
    val interval: Int,
    val start: LocalDate,
    val end: LocalDate? = null
) {
    init {
        require(interval >= 1) { "interval must be at least 1" }
    }
}

fun RecurringTransactionEntity.toRule(): RecurrenceRule = RecurrenceRule(
    unit = when (frequency) {
        RecurrenceFrequency.DAILY -> RecurrenceUnit.DAY
        RecurrenceFrequency.WEEKLY -> RecurrenceUnit.WEEK
        RecurrenceFrequency.MONTHLY -> RecurrenceUnit.MONTH
        RecurrenceFrequency.YEARLY -> RecurrenceUnit.YEAR
        RecurrenceFrequency.CUSTOM -> customUnit ?: RecurrenceUnit.DAY
    },
    interval = intervalCount.coerceAtLeast(1),
    start = startDate,
    end = endDate
)

/**
 * Due dates of a schedule: the start date, then every `interval` units after it.
 *
 * Each date is counted from the start date, never from the previous date. That is what makes a
 * schedule on the 31st run on the last day of shorter months and return to the 31st afterwards,
 * and a 29 February yearly schedule run on 28 February in common years.
 */
object RecurrenceCalculator {

    /** The [index]-th due date, counting the start date as 0. */
    fun occurrence(rule: RecurrenceRule, index: Long): LocalDate {
        val steps = index * rule.interval
        return when (rule.unit) {
            RecurrenceUnit.DAY -> rule.start.plusDays(steps)
            RecurrenceUnit.WEEK -> rule.start.plusWeeks(steps)
            RecurrenceUnit.MONTH -> rule.start.plusMonths(steps)
            RecurrenceUnit.YEAR -> rule.start.plusYears(steps)
        }
    }

    /** The first due date on or after [date], or null when the end date comes first. */
    fun firstOnOrAfter(rule: RecurrenceRule, date: LocalDate): LocalDate? {
        var index = if (date.isAfter(rule.start)) estimateIndex(rule, date) else 0L
        // The estimate can land a little late or early; settle on the exact first date.
        while (index > 0 && !occurrence(rule, index - 1).isBefore(date)) index--
        var candidate = occurrence(rule, index)
        while (candidate.isBefore(date)) {
            index++
            candidate = occurrence(rule, index)
        }
        return candidate.takeIf { rule.end == null || !it.isAfter(rule.end) }
    }

    /** The due date after [date], or null when the schedule has ended. */
    fun nextAfter(rule: RecurrenceRule, date: LocalDate): LocalDate? =
        firstOnOrAfter(rule, date.plusDays(1))

    private fun estimateIndex(rule: RecurrenceRule, date: LocalDate): Long {
        val unit = when (rule.unit) {
            RecurrenceUnit.DAY -> ChronoUnit.DAYS
            RecurrenceUnit.WEEK -> ChronoUnit.WEEKS
            RecurrenceUnit.MONTH -> ChronoUnit.MONTHS
            RecurrenceUnit.YEAR -> ChronoUnit.YEARS
        }
        return (unit.between(rule.start, date) / rule.interval).coerceAtLeast(0)
    }
}
