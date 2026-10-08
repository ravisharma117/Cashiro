package com.ritesh.cashiro.presentation.ui.features.settings.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.manager.NotificationScheduler
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationScheduler: NotificationScheduler,
    subscriptionRepository: SubscriptionRepository,
    private val recurringAlarmScheduler: com.ritesh.cashiro.data.manager.RecurringAlarmScheduler
) : ViewModel() {

    val scanNewTransactionsEnabled: StateFlow<Boolean> = userPreferencesRepository.scanNewTransactionsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val scanNewTransactionsAlertTime: StateFlow<Long> = userPreferencesRepository.scanNewTransactionsAlertTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1200L) // 20:00

    val upcomingNotificationsEnabled: StateFlow<Boolean> = userPreferencesRepository.upcomingNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lendRemindersEnabled: StateFlow<Boolean> = userPreferencesRepository.lendRemindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val repaymentNotificationsEnabled: StateFlow<Boolean> = userPreferencesRepository.repaymentNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val repaymentUseContacts: StateFlow<Boolean> = userPreferencesRepository.repaymentUseContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Combine active subscriptions with disabled notification IDs
    val subscriptions: StateFlow<List<SubscriptionNotificationState>> = combine(
        subscriptionRepository.getActiveSubscriptions(),
        userPreferencesRepository.disabledSubscriptionNotificationIds
    ) { subscriptions, disabledIds ->
        subscriptions.map { subscription ->
            SubscriptionNotificationState(
                subscription = subscription,
                isNotificationEnabled = !disabledIds.contains(subscription.id.toString())
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setScanNewTransactionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setScanNewTransactionsEnabled(enabled)
            notificationScheduler.scheduleDailyReminder()
        }
    }

    fun setScanNewTransactionsAlertTime(minutes: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setScanNewTransactionsAlertTime(minutes)
            notificationScheduler.scheduleDailyReminder()
        }
    }

    fun setUpcomingNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUpcomingNotificationsEnabled(enabled)
            notificationScheduler.scheduleDailyReminder()
            recurringAlarmScheduler.reschedule()
        }
    }

    fun setLendRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setLendRemindersEnabled(enabled)
            // The exact alarm also wakes for lending due dates, so it has to look again
            recurringAlarmScheduler.reschedule()
        }
    }

    fun setRepaymentNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setRepaymentNotificationsEnabled(enabled) }
    }

    fun setRepaymentUseContacts(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setRepaymentUseContacts(enabled) }
    }

    fun toggleSubscriptionNotification(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.toggleSubscriptionNotification(id, enabled)
        }
    }
}

data class SubscriptionNotificationState(
    val subscription: SubscriptionEntity,
    val isNotificationEnabled: Boolean
)
