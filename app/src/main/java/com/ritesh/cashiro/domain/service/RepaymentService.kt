package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.dao.LendBorrowDao
import com.ritesh.cashiro.data.database.dao.RepaymentSuggestionDao
import com.ritesh.cashiro.data.database.entity.RepaymentSuggestionEntity
import com.ritesh.cashiro.data.database.entity.TransactionEntity
import com.ritesh.cashiro.data.manager.RecurringNotifier
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.data.repository.TransactionRepository
import com.ritesh.cashiro.data.security.ContactNameLookup
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The app-facing side of repayment detection: called after a transaction is saved, and by the
 * screens and notification buttons. It never settles anything without the user confirming.
 */
@Singleton
class RepaymentService @Inject constructor(
    private val lendDao: LendBorrowDao,
    private val suggestionDao: RepaymentSuggestionDao,
    private val transactions: TransactionRepository,
    private val preferences: UserPreferencesRepository,
    private val contacts: ContactNameLookup,
    private val notifier: RecurringNotifier
) {
    private val detector = RepaymentDetector(lendDao, suggestionDao) { phone -> contacts.nameFor(phone) }
    private val actions = RepaymentSuggestionActions(lendDao, suggestionDao) { id -> transactions.getTransactionById(id) }

    fun observePending(): Flow<List<RepaymentSuggestionEntity>> = suggestionDao.observePending()

    /** For a transaction that was just saved. A failure here must never affect saving it. */
    suspend fun onTransactionSaved(transactionId: Long, transaction: TransactionEntity? = null) {
        try {
            val saved = transaction ?: transactions.getTransactionById(transactionId) ?: return
            val suggestion = detector.detect(transactionId, saved) ?: return
            if (suggestion.confidence >= RepaymentMatcher.STRONG && preferences.repaymentNotificationsEnabled.first()) {
                val person = lendDao.getPersonByIdSync(suggestion.personId) ?: return
                notifier.showRepayment(suggestion, person.name)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Detection is a convenience; nothing about the transaction itself depends on it
        }
    }

    suspend fun confirm(suggestionId: Long, personId: Long? = null): Boolean {
        val done = actions.confirm(suggestionId, personId)
        notifier.dismissRepayment(suggestionId)
        return done
    }

    suspend fun ignore(suggestionId: Long) {
        actions.ignore(suggestionId)
        notifier.dismissRepayment(suggestionId)
    }
}
