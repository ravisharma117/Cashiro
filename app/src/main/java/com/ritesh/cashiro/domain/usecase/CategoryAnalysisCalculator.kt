package com.ritesh.cashiro.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** One counted transaction, already converted to the display currency. */
data class SpendEntry(
    val category: String,
    val subcategory: String?,
    val amount: BigDecimal,
    val date: LocalDate
)

data class MonthTotal(val month: YearMonth, val total: BigDecimal)

data class SubcategoryShare(
    val name: String?,
    val total: BigDecimal,
    val percentOfCategory: Float,
    val count: Int
)

data class CategoryAnalysis(
    val category: String,
    val month: YearMonth,
    val total: BigDecimal,
    val count: Int,
    val dailyAverage: BigDecimal,
    val previousTotal: BigDecimal,
    val change: BigDecimal,
    /** Null when last month was zero, because a percentage of zero is meaningless. */
    val changePercent: Float?,
    val monthTotalAllCategories: BigDecimal,
    val shareOfTotalPercent: Float,
    val trend: List<MonthTotal>,
    val subcategories: List<SubcategoryShare>
)

/**
 * The arithmetic behind the category screens, kept free of Android and the database so it can be
 * tested with fixed data.
 *
 * Entries are expected to be limited to one kind (expenses or income), the same way the Analysis
 * tab limits its transactions, so a category's total here matches its row there.
 */
object CategoryAnalysisCalculator {

    const val TREND_MONTHS = 6

    fun totalsByCategory(entries: List<SpendEntry>, month: YearMonth): Map<String, BigDecimal> =
        entries.filter { it.inMonth(month) }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumAmounts() }

    fun countsByCategory(entries: List<SpendEntry>, month: YearMonth): Map<String, Int> =
        entries.filter { it.inMonth(month) }.groupingBy { it.category }.eachCount()

    fun monthTotal(entries: List<SpendEntry>, month: YearMonth): BigDecimal =
        entries.filter { it.inMonth(month) }.sumAmounts()

    /** Days to divide by: days so far for the running month, the full month otherwise. */
    fun daysCounted(month: YearMonth, today: LocalDate): Int =
        if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()

    fun analyze(
        category: String,
        entries: List<SpendEntry>,
        month: YearMonth,
        today: LocalDate = LocalDate.now()
    ): CategoryAnalysis {
        val inCategory = entries.filter { it.category == category }
        val thisMonth = inCategory.filter { it.inMonth(month) }

        val total = thisMonth.sumAmounts()
        val previousTotal = inCategory.filter { it.inMonth(month.minusMonths(1)) }.sumAmounts()
        val change = total.subtract(previousTotal)
        val changePercent = if (previousTotal.signum() == 0) {
            null
        } else {
            change.multiply(HUNDRED).divide(previousTotal, 1, RoundingMode.HALF_UP).toFloat()
        }

        val all = monthTotal(entries, month)
        val share = if (all.signum() == 0) 0f else percent(total, all)

        val days = daysCounted(month, today).coerceAtLeast(1)
        val dailyAverage = total.divide(BigDecimal(days), 2, RoundingMode.HALF_UP)

        val trend = (TREND_MONTHS - 1 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong())
            MonthTotal(m, inCategory.filter { it.inMonth(m) }.sumAmounts())
        }

        val subcategories = thisMonth
            .groupBy { it.subcategory?.takeIf { name -> name.isNotBlank() } }
            .map { (name, list) ->
                val sub = list.sumAmounts()
                SubcategoryShare(
                    name = name,
                    total = sub,
                    percentOfCategory = if (total.signum() == 0) 0f else percent(sub, total),
                    count = list.size
                )
            }
            .sortedByDescending { it.total }

        return CategoryAnalysis(
            category = category,
            month = month,
            total = total,
            count = thisMonth.size,
            dailyAverage = dailyAverage,
            previousTotal = previousTotal,
            change = change,
            changePercent = changePercent,
            monthTotalAllCategories = all,
            shareOfTotalPercent = share,
            trend = trend,
            subcategories = subcategories
        )
    }

    private fun SpendEntry.inMonth(month: YearMonth) = YearMonth.from(date) == month

    private fun List<SpendEntry>.sumAmounts(): BigDecimal =
        fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }

    private fun percent(part: BigDecimal, whole: BigDecimal): Float =
        part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP).toFloat()

    private val HUNDRED = BigDecimal(100)
}
