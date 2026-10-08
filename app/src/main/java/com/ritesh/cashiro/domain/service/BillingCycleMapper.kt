package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.entity.RecurrenceFrequency
import com.ritesh.cashiro.data.database.entity.RecurrenceUnit
import java.time.LocalDate

/** A subscription's billing cycle written as a recurring-schedule rule. */
data class CycleSchedule(
    val frequency: RecurrenceFrequency,
    val intervalCount: Int,
    val customUnit: RecurrenceUnit?,
    val endDate: LocalDate?
)

/**
 * Bills keep their cycle as text ("monthly", "quarterly", "custom_2_week_2027-01-31"). This turns
 * it into the same kind of rule a recurring schedule uses, so a bill can be linked to one.
 */
object BillingCycleMapper {

    fun toSchedule(billingCycle: String?): CycleSchedule {
        val cycle = billingCycle?.lowercase() ?: "monthly"

        if (cycle.startsWith("custom_")) {
            val parts = cycle.split("_")
            val count = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
            val unit = when (parts.getOrNull(2)) {
                "day" -> RecurrenceUnit.DAY
                "week" -> RecurrenceUnit.WEEK
                "month" -> RecurrenceUnit.MONTH
                "year" -> RecurrenceUnit.YEAR
                else -> null
            }
            if (count != null && unit != null) {
                val end = parts.getOrNull(3)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                return CycleSchedule(RecurrenceFrequency.CUSTOM, count, unit, end)
            }
            return MONTHLY
        }

        return when (cycle) {
            "weekly" -> CycleSchedule(RecurrenceFrequency.WEEKLY, 1, null, null)
            "quarterly" -> CycleSchedule(RecurrenceFrequency.CUSTOM, 3, RecurrenceUnit.MONTH, null)
            "semi-annual" -> CycleSchedule(RecurrenceFrequency.CUSTOM, 6, RecurrenceUnit.MONTH, null)
            "annual" -> CycleSchedule(RecurrenceFrequency.YEARLY, 1, null, null)
            else -> MONTHLY
        }
    }

    private val MONTHLY = CycleSchedule(RecurrenceFrequency.MONTHLY, 1, null, null)
}
