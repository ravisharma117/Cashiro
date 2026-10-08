package com.ritesh.cashiro.presentation.ui.features.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.CategoryEntity
import com.ritesh.cashiro.data.database.entity.RecurringOccurrenceEntity
import com.ritesh.cashiro.data.database.entity.RecurringState
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import com.ritesh.cashiro.data.database.entity.SubcategoryEntity
import com.ritesh.cashiro.data.repository.AccountBalanceRepository
import com.ritesh.cashiro.data.repository.CategoryRepository
import com.ritesh.cashiro.data.repository.CurrencyRepository
import com.ritesh.cashiro.data.repository.RecurringTransactionRepository
import com.ritesh.cashiro.data.repository.SubcategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringListState(
    val active: List<RecurringTransactionEntity> = emptyList(),
    val paused: List<RecurringTransactionEntity> = emptyList(),
    val ended: List<RecurringTransactionEntity> = emptyList()
) {
    val isEmpty: Boolean get() = active.isEmpty() && paused.isEmpty() && ended.isEmpty()
}

@HiltViewModel
class RecurringTransactionsViewModel @Inject constructor(
    private val repository: RecurringTransactionRepository,
    accountBalanceRepository: AccountBalanceRepository,
    categoryRepository: CategoryRepository,
    subcategoryRepository: SubcategoryRepository,
    currencyRepository: CurrencyRepository
) : ViewModel() {

    val list: StateFlow<RecurringListState> = repository.observeAll()
        .map { all ->
            RecurringListState(
                active = all.filter { it.state == RecurringState.ACTIVE },
                paused = all.filter { it.state == RecurringState.PAUSED },
                ended = all.filter { it.state == RecurringState.ENDED }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringListState())

    val accounts: StateFlow<List<AccountBalanceEntity>> = accountBalanceRepository.getAllLatestBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subcategoriesMap: StateFlow<Map<Long, List<SubcategoryEntity>>> = subcategoryRepository.subcategoriesMap

    val baseCurrency: StateFlow<String> = currencyRepository.effectiveBaseCurrencyCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "INR")

    fun history(id: Long): Flow<List<RecurringOccurrenceEntity>> = repository.observeOccurrences(id)

    fun save(schedule: RecurringTransactionEntity) {
        viewModelScope.launch {
            if (schedule.id == 0L) repository.create(schedule) else repository.update(schedule)
        }
    }

    fun skipNext(id: Long) = viewModelScope.launch { repository.skipNext(id) }

    fun pause(id: Long) = viewModelScope.launch { repository.pause(id) }

    fun resume(id: Long) = viewModelScope.launch { repository.resume(id) }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
}
