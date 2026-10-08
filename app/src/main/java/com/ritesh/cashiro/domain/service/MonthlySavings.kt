package com.ritesh.cashiro.domain.service

import java.math.BigDecimal
import java.math.RoundingMode

/** What was kept from this month's income. */
data class MonthlySavings(
    val income: BigDecimal,
    val expenses: BigDecimal,
    /** Income minus expenses; negative when more was spent than earned. */
    val savings: BigDecimal,
    /** Savings as a share of income, one decimal; 0 when there was no income. */
    val ratePercent: BigDecimal
) {
    companion object {
        fun of(income: BigDecimal, expenses: BigDecimal): MonthlySavings {
            val savings = income.subtract(expenses)
            val rate = if (income.signum() > 0) {
                savings.multiply(BigDecimal(100)).divide(income, 1, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO.setScale(1)
            }
            return MonthlySavings(income, expenses, savings, rate)
        }
    }
}
