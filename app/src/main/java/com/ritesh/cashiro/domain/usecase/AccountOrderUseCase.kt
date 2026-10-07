package com.ritesh.cashiro.domain.usecase

import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.data.repository.AccountBalanceRepository
import com.ritesh.cashiro.domain.model.AccountOrdering
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The one place that applies the user's account order. Screens that list accounts should use
 * [orderedAccounts] instead of sorting on their own.
 */
class AccountOrderUseCase @Inject constructor(
    private val accountBalanceRepository: AccountBalanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /** Latest balance row of every account, in the user's order. */
    fun orderedAccounts(): Flow<List<AccountBalanceEntity>> = combine(
        accountBalanceRepository.getAllLatestBalances(),
        userPreferencesRepository.accountOrder
    ) { accounts, order -> AccountOrdering.sort(accounts, order) }

    /** Stores the order of [accounts] as arranged by the user. */
    suspend fun saveOrder(accounts: List<AccountBalanceEntity>) {
        userPreferencesRepository.updateAccountOrder(AccountOrdering.orderOf(accounts))
    }

    /** Keeps an account's position when its bank name changes. */
    suspend fun onAccountRenamed(oldBankName: String, newBankName: String, accountLast4: String) {
        val order = userPreferencesRepository.accountOrder.first()
        val updated = AccountOrdering.rename(
            order,
            AccountOrdering.keyOf(oldBankName, accountLast4),
            AccountOrdering.keyOf(newBankName, accountLast4)
        )
        if (updated != order) userPreferencesRepository.updateAccountOrder(updated)
    }

    /** Drops a deleted or merged-away account from the stored order. */
    suspend fun onAccountRemoved(bankName: String, accountLast4: String) {
        val order = userPreferencesRepository.accountOrder.first()
        val updated = AccountOrdering.remove(order, AccountOrdering.keyOf(bankName, accountLast4))
        if (updated != order) userPreferencesRepository.updateAccountOrder(updated)
    }
}
