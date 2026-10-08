package com.ritesh.cashiro.presentation.ui.features.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.currency.CurrencyConversionService
import com.ritesh.cashiro.data.database.entity.CategoryEntity
import com.ritesh.cashiro.data.database.entity.SubcategoryEntity
import com.ritesh.cashiro.data.database.entity.TransactionEntity
import com.ritesh.cashiro.data.repository.BudgetRepository
import com.ritesh.cashiro.data.repository.CategoryRepository
import com.ritesh.cashiro.data.repository.SubcategoryRepository
import com.ritesh.cashiro.domain.usecase.CategoryAnalysis
import com.ritesh.cashiro.domain.usecase.CategoryAnalysisCalculator
import com.ritesh.cashiro.domain.usecase.CategoryKind
import com.ritesh.cashiro.domain.usecase.GetCategoryAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A budget limit that covers this category in the shown month. */
data class CategoryBudgetInfo(
    val limit: BigDecimal,
    val spent: BigDecimal,
    val currency: String
) {
    val remaining: BigDecimal get() = limit.subtract(spent)
    val isOver: Boolean get() = spent > limit
    val usedFraction: Float
        get() = if (limit.signum() > 0) (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f
}

data class CategoryDetailUiState(
    val category: CategoryEntity? = null,
    val name: String = "",
    val month: YearMonth = YearMonth.now(),
    val currency: String = "",
    val analysis: CategoryAnalysis? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    val budget: CategoryBudgetInfo? = null,
    val isLoading: Boolean = true
) {
    val isIncome: Boolean get() = category?.isIncome == true
    val canGoToNextMonth: Boolean get() = month < YearMonth.now()
}

private data class Selection(val name: String, val month: YearMonth)

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    subcategoryRepository: SubcategoryRepository,
    private val analysis: GetCategoryAnalysisUseCase,
    private val budgets: BudgetRepository,
    private val conversion: CurrencyConversionService
) : ViewModel() {

    private val selection = MutableStateFlow<Selection?>(null)

    val categoriesMap: StateFlow<Map<String, CategoryEntity>> = categoryRepository.getAllCategories()
        .map { list -> list.associateBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val subcategoriesMap: StateFlow<Map<String, SubcategoryEntity>> =
        subcategoryRepository.getAllSubcategories()
            .map { list -> list.associateBy { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Called by the screen with its navigation arguments; later calls with the same ones are no-ops. */
    fun open(categoryName: String, month: YearMonth) {
        if (selection.value == null) selection.value = Selection(categoryName, month)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CategoryDetailUiState> = selection
        .filterNotNull()
        .flatMapLatest { sel ->
            categoryRepository.getAllCategories().flatMapLatest { all ->
                val category = all.firstOrNull { it.name == sel.name }
                val kind = if (category?.isIncome == true) CategoryKind.INCOME else CategoryKind.EXPENSE
                stateFor(sel, category, kind)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryDetailUiState())

    fun previousMonth() {
        selection.value = selection.value?.let { it.copy(month = it.month.minusMonths(1)) }
    }

    fun nextMonth() {
        selection.value = selection.value?.let {
            if (it.month < YearMonth.now()) it.copy(month = it.month.plusMonths(1)) else it
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun stateFor(
        sel: Selection,
        category: CategoryEntity?,
        kind: CategoryKind
    ): Flow<CategoryDetailUiState> = combine(
        analysis.window(kind, sel.month, monthsBack = CategoryAnalysisCalculator.TREND_MONTHS - 1),
        analysis.transactionsOf(kind, sel.name, sel.month)
    ) { window, transactions ->
        val result = CategoryAnalysisCalculator.analyze(sel.name, window.entries, sel.month)
        val converted = transactions
            .filter { it.currency != window.currency }
            .associate { it.id to conversion.convertAmount(it.amount, it.currency, window.currency) }
        CategoryDetailUiState(
            category = category,
            name = sel.name,
            month = sel.month,
            currency = window.currency,
            analysis = result,
            transactions = transactions,
            convertedAmounts = converted,
            budget = if (kind == CategoryKind.EXPENSE) budgetFor(sel) else null,
            isLoading = false
        )
    }

    /** The first active budget for the month that sets a limit on this category. */
    private suspend fun budgetFor(sel: Selection): CategoryBudgetInfo? {
        val active = budgets.getActiveBudgetsForMonth(sel.month.year, sel.month.monthValue).first()
        for (budget in active) {
            val limit = budgets.getCategoryLimitsWithSpending(budget.id)
                .firstOrNull { it.limit.categoryName == sel.name } ?: continue
            return CategoryBudgetInfo(
                limit = limit.limit.limitAmount,
                spent = limit.currentSpending,
                currency = budget.currency
            )
        }
        return null
    }
}
