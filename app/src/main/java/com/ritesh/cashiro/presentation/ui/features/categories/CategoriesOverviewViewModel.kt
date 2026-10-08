package com.ritesh.cashiro.presentation.ui.features.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.database.entity.CategoryEntity
import com.ritesh.cashiro.data.repository.CategoryRepository
import com.ritesh.cashiro.domain.usecase.CategoryAnalysisCalculator
import com.ritesh.cashiro.domain.usecase.CategoryKind
import com.ritesh.cashiro.domain.usecase.GetCategoryAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** One category line on the browsing screen. */
data class CategorySpendRow(
    val category: CategoryEntity,
    val total: BigDecimal,
    val count: Int,
    /** 0..100, share of the section total. */
    val sharePercent: Float
)

data class CategoriesOverviewUiState(
    val month: YearMonth = YearMonth.now(),
    val currency: String = "",
    val expenseTotal: BigDecimal = BigDecimal.ZERO,
    val incomeTotal: BigDecimal = BigDecimal.ZERO,
    val expenseRows: List<CategorySpendRow> = emptyList(),
    val incomeRows: List<CategorySpendRow> = emptyList(),
    val isLoading: Boolean = true
) {
    val canGoToNextMonth: Boolean get() = month < YearMonth.now()
}

@HiltViewModel
class CategoriesOverviewViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
    private val analysis: GetCategoryAnalysisUseCase
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CategoriesOverviewUiState> = _month
        .flatMapLatest { month ->
            combine(
                categoryRepository.getAllCategories(),
                analysis.window(CategoryKind.EXPENSE, month, monthsBack = 0),
                analysis.window(CategoryKind.INCOME, month, monthsBack = 0)
            ) { categories, expenses, income ->
                val expenseTotals = CategoryAnalysisCalculator.totalsByCategory(expenses.entries, month)
                val expenseCounts = CategoryAnalysisCalculator.countsByCategory(expenses.entries, month)
                val incomeTotals = CategoryAnalysisCalculator.totalsByCategory(income.entries, month)
                val incomeCounts = CategoryAnalysisCalculator.countsByCategory(income.entries, month)

                val expenseTotal = CategoryAnalysisCalculator.monthTotal(expenses.entries, month)
                val incomeTotal = CategoryAnalysisCalculator.monthTotal(income.entries, month)

                CategoriesOverviewUiState(
                    month = month,
                    currency = expenses.currency,
                    expenseTotal = expenseTotal,
                    incomeTotal = incomeTotal,
                    expenseRows = rows(categories.filter { !it.isIncome }, expenseTotals, expenseCounts, expenseTotal),
                    incomeRows = rows(categories.filter { it.isIncome }, incomeTotals, incomeCounts, incomeTotal),
                    isLoading = false
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoriesOverviewUiState())

    val month: StateFlow<YearMonth> = _month.asStateFlow()

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (_month.value < YearMonth.now()) _month.value = _month.value.plusMonths(1)
    }

    /** Keeps the user's own category order, and leaves out categories with nothing in the month. */
    private fun rows(
        categories: List<CategoryEntity>,
        totals: Map<String, BigDecimal>,
        counts: Map<String, Int>,
        sectionTotal: BigDecimal
    ): List<CategorySpendRow> = categories.mapNotNull { category ->
        val total = totals[category.name] ?: BigDecimal.ZERO
        val count = counts[category.name] ?: 0
        if (count == 0 && total.signum() == 0) return@mapNotNull null
        CategorySpendRow(
            category = category,
            total = total,
            count = count,
            sharePercent = if (sectionTotal.signum() > 0) {
                total.multiply(BigDecimal(100))
                    .divide(sectionTotal, 1, java.math.RoundingMode.HALF_UP).toFloat()
            } else 0f
        )
    }
}
