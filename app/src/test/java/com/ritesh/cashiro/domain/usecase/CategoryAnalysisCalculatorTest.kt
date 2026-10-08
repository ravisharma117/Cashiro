package com.ritesh.cashiro.domain.usecase

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class CategoryAnalysisCalculatorTest {

    private val october = YearMonth.of(2026, 10)
    private val food = "Food"

    private fun entry(category: String, amount: String, date: String, sub: String? = null) =
        SpendEntry(category, sub, BigDecimal(amount), LocalDate.parse(date))

    @Test
    fun totalAndCountForTheMonthOnly() {
        val entries = listOf(
            entry(food, "100", "2026-10-02"),
            entry(food, "50.50", "2026-10-20"),
            entry(food, "999", "2026-09-30"),
            entry("Travel", "400", "2026-10-05")
        )

        val result = CategoryAnalysisCalculator.analyze(food, entries, october, LocalDate.parse("2026-10-25"))

        assertEquals(BigDecimal("150.50"), result.total)
        assertEquals(2, result.count)
    }

    @Test
    fun runningMonthDividesByDaysSoFar() {
        val entries = listOf(entry(food, "100", "2026-10-02"))

        val result = CategoryAnalysisCalculator.analyze(food, entries, october, LocalDate.parse("2026-10-10"))

        assertEquals(BigDecimal("10.00"), result.dailyAverage)
    }

    @Test
    fun pastMonthDividesByItsFullLength() {
        val entries = listOf(entry(food, "310", "2026-10-02"))

        val result = CategoryAnalysisCalculator.analyze(food, entries, october, LocalDate.parse("2026-12-15"))

        assertEquals(BigDecimal("10.00"), result.dailyAverage)
    }

    @Test
    fun comparisonWithLastMonthHasAmountAndPercent() {
        val entries = listOf(
            entry(food, "150", "2026-10-02"),
            entry(food, "100", "2026-09-12")
        )

        val result = CategoryAnalysisCalculator.analyze(food, entries, october, LocalDate.parse("2026-10-25"))

        assertEquals(BigDecimal("100"), result.previousTotal)
        assertEquals(BigDecimal("50"), result.change)
        assertEquals(50.0f, result.changePercent)
    }

    @Test
    fun spendingLessThanLastMonthIsANegativeChange() {
        val entries = listOf(
            entry(food, "75", "2026-10-02"),
            entry(food, "100", "2026-09-12")
        )

        val result = CategoryAnalysisCalculator.analyze(food, entries, october, LocalDate.parse("2026-10-25"))

        assertEquals(BigDecimal("-25"), result.change)
        assertEquals(-25.0f, result.changePercent)
    }

    @Test
    fun zeroLastMonthGivesNoPercentage() {
        val entries = listOf(entry(food, "80", "2026-10-02"))

        val result = CategoryAnalysisCalculator.analyze(food, entries, october, LocalDate.parse("2026-10-25"))

        assertEquals(BigDecimal.ZERO, result.previousTotal)
        assertEquals(BigDecimal("80"), result.change)
        assertNull(result.changePercent)
    }

    @Test
    fun shareIsTheCategoryPartOfEverythingThatMonth() {
        val entries = listOf(
            entry(food, "250", "2026-10-02"),
            entry("Travel", "750", "2026-10-05"),
            entry("Travel", "5000", "2026-09-05")
        )

        val result = CategoryAnalysisCalculator.analyze(food, entries, october, LocalDate.parse("2026-10-25"))

        assertEquals(BigDecimal("1000"), result.monthTotalAllCategories)
        assertEquals(25.0f, result.shareOfTotalPercent)
    }

    @Test
    fun anEmptyMonthHasZeroShareAndAverage() {
        val result = CategoryAnalysisCalculator.analyze(food, emptyList(), october, LocalDate.parse("2026-10-25"))

        assertEquals(0f, result.shareOfTotalPercent)
        assertEquals(BigDecimal("0.00"), result.dailyAverage)
        assertEquals(0, result.count)
    }

    @Test
    fun trendCoversSixMonthsOldestFirstAndFillsGapsWithZero() {
        val entries = listOf(
            entry(food, "10", "2026-05-03"),
            entry(food, "30", "2026-08-03"),
            entry(food, "40", "2026-10-03")
        )

        val trend = CategoryAnalysisCalculator
            .analyze(food, entries, october, LocalDate.parse("2026-10-25")).trend

        assertEquals(6, trend.size)
        assertEquals(YearMonth.of(2026, 5), trend.first().month)
        assertEquals(october, trend.last().month)
        assertEquals(
            listOf("10", "0", "0", "30", "0", "40").map(::BigDecimal),
            trend.map { it.total }
        )
    }

    @Test
    fun subcategoriesAreSplitAndSortedBySize() {
        val entries = listOf(
            entry(food, "30", "2026-10-02", "Snacks"),
            entry(food, "70", "2026-10-03", "Groceries"),
            entry(food, "100", "2026-10-04", null),
            entry(food, "100", "2026-10-05", " ")
        )

        val subs = CategoryAnalysisCalculator
            .analyze(food, entries, october, LocalDate.parse("2026-10-25")).subcategories

        assertEquals(listOf(null, "Groceries", "Snacks"), subs.map { it.name })
        assertEquals(BigDecimal("200"), subs.first().total)
        assertEquals(2, subs.first().count)
        assertEquals(66.7f, subs.first().percentOfCategory)
        assertTrue(subs.sumOf { it.percentOfCategory.toDouble() } in 99.5..100.5)
    }

    @Test
    fun totalsByCategoryMatchesTheSumOfTheMonth() {
        val entries = listOf(
            entry(food, "10", "2026-10-02"),
            entry(food, "15", "2026-10-09"),
            entry("Travel", "5", "2026-10-09"),
            entry("Travel", "500", "2026-11-01")
        )

        val totals = CategoryAnalysisCalculator.totalsByCategory(entries, october)

        assertEquals(mapOf(food to BigDecimal("25"), "Travel" to BigDecimal("5")), totals)
        assertEquals(BigDecimal("30"), CategoryAnalysisCalculator.monthTotal(entries, october))
        assertEquals(mapOf(food to 2, "Travel" to 1), CategoryAnalysisCalculator.countsByCategory(entries, october))
    }
}
