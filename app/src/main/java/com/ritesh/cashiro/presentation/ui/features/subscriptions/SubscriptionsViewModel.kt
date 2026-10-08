package com.ritesh.cashiro.presentation.ui.features.subscriptions

import com.ritesh.cashiro.utils.SubscriptionUtils

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.currency.CurrencyConversionService
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.repository.AccountBalanceRepository
import com.ritesh.cashiro.data.repository.CategoryRepository
import com.ritesh.cashiro.data.repository.SubcategoryRepository
import com.ritesh.cashiro.data.database.entity.BillPaymentEntity
import com.ritesh.cashiro.data.repository.SubscriptionRepository
import com.ritesh.cashiro.domain.service.UpcomingPaymentsCalculator
import com.ritesh.cashiro.domain.usecase.BillCycleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import com.ritesh.cashiro.data.repository.CurrencyRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val categoryRepository: CategoryRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val currencyRepository: CurrencyRepository,
    private val billCycle: BillCycleUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    val categoriesMap = categoryRepository.getAllCategories()
        .map { cats -> cats.associateBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val subcategoriesMap = subcategoryRepository.getAllSubcategories()
        .map { subcats -> subcats.associateBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    private val selectedId = MutableStateFlow<Long?>(null)

    /** Payment history of the subscription whose sheet is open. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPayments: StateFlow<List<BillPaymentEntity>> = selectedId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else subscriptionRepository.observePayments(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSubscriptions()
    }
    
    private fun loadSubscriptions() {
        viewModelScope.launch {
            combine(
                subscriptionRepository.getActiveSubscriptions(),
                currencyRepository.effectiveBaseCurrencyCode,
                currencyConversionService.rateChangeTrigger
            ) { subscriptions, targetCurrency, _ ->
                val subscriptionCurrencies = subscriptions.map { it.currency }.distinct()
                if (subscriptionCurrencies.any { it != targetCurrency }) {
                    currencyConversionService.refreshExchangeRatesForAccount(subscriptionCurrencies + targetCurrency)
                }

                var totalMonthlyAmount = BigDecimal.ZERO
                var conversionFailures = 0
                val convertedAmounts = buildMap(subscriptions.size) {
                    subscriptions.forEach { sub ->
                        val converted = if (sub.currency == targetCurrency) {
                            sub.amount
                        } else {
                            currencyConversionService.convertAmount(
                                amount = sub.amount,
                                fromCurrency = sub.currency,
                                toCurrency = targetCurrency
                            )
                        }
                        if (converted == null) {
                            conversionFailures++
                            return@forEach
                        }
                        put(sub.id, converted)
                        totalMonthlyAmount = totalMonthlyAmount.add(
                            SubscriptionUtils.monthlyEquivalent(converted, sub.billingCycle)
                        )
                    }
                }

                val upcoming = UpcomingPaymentsCalculator.build(
                    subscriptions = subscriptions,
                    today = java.time.LocalDate.now(),
                    amountOf = { sub -> convertedAmounts[sub.id] ?: sub.amount }
                )

                _uiState.update {
                    it.copy(
                        upcoming = upcoming,
                        activeSubscriptions = subscriptions,
                        totalMonthlyAmount = totalMonthlyAmount,
                        totalYearlyAmount = totalMonthlyAmount.multiply(BigDecimal(12)),
                        targetCurrency = targetCurrency,
                        convertedAmounts = convertedAmounts,
                        conversionFailureCount = conversionFailures,
                        isLoading = false
                    )
                }
            }.collectLatest { }
        }
    }
    
    fun hideSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            subscriptionRepository.hideSubscription(subscriptionId)
            _uiState.value = _uiState.value.copy(
                lastHiddenSubscription = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }
            )
        }
    }
    
    fun undoHide() {
        _uiState.value.lastHiddenSubscription?.let { subscription ->
            viewModelScope.launch {
                subscriptionRepository.unhideSubscription(subscription.id)
                _uiState.value = _uiState.value.copy(lastHiddenSubscription = null)
            }
        }
    }

    fun selectSubscription(subscription: SubscriptionEntity?) {
        selectedId.value = subscription?.id
        _uiState.value = _uiState.value.copy(selectedSubscription = subscription)
    }

    fun setKindFilter(filter: SubscriptionKindFilter) {
        _uiState.update { it.copy(kindFilter = filter) }
    }

    /** Opens the "add the expense or just mark paid" question. */
    fun requestMarkPaid(subscription: SubscriptionEntity) {
        _uiState.update { it.copy(markPaidTarget = subscription) }
    }

    fun dismissMarkPaid() {
        _uiState.update { it.copy(markPaidTarget = null) }
    }

    /**
     * Settles the current cycle. [addExpense] also creates the expense transaction for [amount];
     * otherwise only the payment is recorded because the transaction already exists.
     */
    fun confirmPaid(subscription: SubscriptionEntity, amount: BigDecimal, addExpense: Boolean) {
        viewModelScope.launch {
            if (addExpense) {
                billCycle.payAndAddExpense(subscription, amount)
            } else {
                billCycle.payWithoutTransaction(subscription, amount)
            }
            _uiState.update { it.copy(markPaidTarget = null) }
            selectSubscription(null)
        }
    }

    fun skipCycle(subscription: SubscriptionEntity) {
        viewModelScope.launch {
            billCycle.skipCycle(subscription)
            selectSubscription(null)
        }
    }

}