package com.ritesh.cashiro.domain.usecase

import com.ritesh.cashiro.data.currency.CurrencyConversionService
import com.ritesh.cashiro.data.database.entity.TransactionEntity
import com.ritesh.cashiro.data.database.entity.TransactionType
import com.ritesh.cashiro.data.repository.CurrencyRepository
import com.ritesh.cashiro.data.repository.TransactionRepository
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

enum class CategoryKind(val type: TransactionType) {
    EXPENSE(TransactionType.EXPENSE),
    INCOME(TransactionType.INCOME)
}

/** Counted transactions of one kind for a run of months, in [currency]. */
data class SpendWindow(val currency: String, val entries: List<SpendEntry>)

/**
 * Supplies the category screens with transactions that follow the Analysis tab's rules: deleted
 * rows are skipped by the shared query, only expense (or only income) transactions count, so
 * transfers and balance updates stay out, and everything is converted to the base currency.
 */
class GetCategoryAnalysisUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val currencyRepository: CurrencyRepository,
    private val conversion: CurrencyConversionService
) {

    /** Entries from [monthsBack] months before [month] up to the end of [month]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun window(kind: CategoryKind, month: YearMonth, monthsBack: Int): Flow<SpendWindow> =
        combine(
            currencyRepository.effectiveBaseCurrencyCode,
            conversion.rateChangeTrigger
        ) { base, _ -> base }
            .flatMapLatest { base ->
                val start = month.minusMonths(monthsBack.toLong()).atDay(1)
                transactionRepository
                    .getTransactionsBetweenDates(start, month.atEndOfMonth())
                    .map { list ->
                        SpendWindow(
                            currency = base,
                            entries = list
                                .filter { it.transactionType == kind.type }
                                .map { it.toEntry(base) }
                        )
                    }
            }

    /** The month's transactions of one category and kind, newest first. */
    fun transactionsOf(kind: CategoryKind, category: String, month: YearMonth): Flow<List<TransactionEntity>> =
        transactionRepository
            .getTransactionsBetweenDates(month.atDay(1), month.atEndOfMonth())
            .map { list ->
                list.filter {
                    it.transactionType == kind.type && (it.category ?: UNCATEGORISED) == category
                }
            }

    private suspend fun TransactionEntity.toEntry(base: String): SpendEntry = SpendEntry(
        category = category ?: UNCATEGORISED,
        subcategory = subcategory,
        amount = conversion.convertAmount(amount, currency, base),
        date = dateTime.toLocalDate()
    )

    companion object {
        /** Same fallback the Analysis tab uses for a transaction without a category. */
        const val UNCATEGORISED = "Miscellaneous"
    }
}
