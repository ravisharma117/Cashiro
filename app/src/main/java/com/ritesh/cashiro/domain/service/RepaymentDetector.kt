package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.dao.LendBorrowDao
import com.ritesh.cashiro.data.database.dao.RepaymentSuggestionDao
import com.ritesh.cashiro.data.database.entity.RepaymentStatus
import com.ritesh.cashiro.data.database.entity.RepaymentSuggestionEntity
import com.ritesh.cashiro.data.database.entity.TransactionEntity
import com.ritesh.cashiro.data.database.entity.TransactionType
import java.time.LocalDateTime

/**
 * Looks at a freshly saved transaction and, if it could be a repayment, files a suggestion.
 *
 * It never settles anything. Only recent transactions are looked at (so scanning old messages or
 * importing history does not produce a flood), only money that fits the direction of someone's
 * outstanding balance, and each transaction is suggested at most once, even after it was ignored.
 */
class RepaymentDetector(
    private val lendDao: LendBorrowDao,
    private val suggestionDao: RepaymentSuggestionDao,
    private val contactName: suspend (phone: String) -> String? = { null }
) {

    /** Returns the suggestion that was filed, or null when the transaction does not look like one. */
    suspend fun detect(
        transactionId: Long,
        transaction: TransactionEntity,
        now: LocalDateTime = LocalDateTime.now()
    ): RepaymentSuggestionEntity? {
        val isIncoming = when (transaction.transactionType) {
            TransactionType.INCOME -> true
            TransactionType.EXPENSE, TransactionType.CREDIT -> false
            else -> return null
        }
        if (transaction.dateTime.isBefore(now.minusDays(RECENT_DAYS))) return null
        if (suggestionDao.getByTransactionId(transactionId) != null) return null
        if (lendDao.getTransactionByWalletId(transactionId) != null) return null

        val persons = lendDao.getActivePersonsList()
        if (persons.isEmpty()) return null
        val ledgers = LedgerBuilder.build(persons, lendDao.getAllTransactionsList(), transaction.currency)

        val counterparty = transaction.merchantName.takeIf { it.isNotBlank() }
        val phone = PhoneNumbers.extract(counterparty)
        val facts = RepaymentFacts(
            isIncoming = isIncoming,
            amount = transaction.amount,
            counterparty = counterparty,
            contactName = phone?.let { contactName(it) }
        )
        val match = RepaymentMatcher.match(facts, ledgers) ?: return null

        val suggestion = RepaymentSuggestionEntity(
            transactionId = transactionId,
            personId = match.personId,
            entryId = match.entryId,
            amount = transaction.amount,
            currency = transaction.currency,
            isIncoming = isIncoming,
            confidence = match.confidence,
            matchedText = match.matchedText,
            createdAt = now,
            updatedAt = now
        )
        val id = suggestionDao.insert(suggestion)
        return if (id == -1L) null else suggestion.copy(id = id)
    }

    companion object {
        const val RECENT_DAYS = 3L
    }
}

/** What the user can do with a suggestion. */
class RepaymentSuggestionActions(
    private val lendDao: LendBorrowDao,
    private val suggestionDao: RepaymentSuggestionDao,
    private val transactionLookup: suspend (Long) -> TransactionEntity?
) {
    /**
     * Records the repayment: a settlement entry linked to the transaction, so the outstanding
     * balance drops by the amount. [personId] lets the user pick someone else than the suggestion.
     * Returns false when the suggestion is not pending or the transaction is gone.
     */
    suspend fun confirm(suggestionId: Long, personId: Long? = null, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val suggestion = suggestionDao.getById(suggestionId) ?: return false
        if (suggestion.status != RepaymentStatus.PENDING) return false
        val transaction = transactionLookup(suggestion.transactionId) ?: return false
        val targetId = personId ?: suggestion.personId
        val person = lendDao.getPersonByIdSync(targetId) ?: return false

        lendDao.insertTransaction(
            com.ritesh.cashiro.data.database.entity.LendBorrowTransactionEntity(
                personId = targetId,
                transactionId = suggestion.transactionId,
                type = if (suggestion.isIncoming) {
                    com.ritesh.cashiro.data.database.entity.LendBorrowType.SETTLEMENT_LENT
                } else {
                    com.ritesh.cashiro.data.database.entity.LendBorrowType.SETTLEMENT_BORROWED
                },
                amount = suggestion.amount,
                currency = suggestion.currency,
                title = REPAYMENT_TITLE,
                isSettled = true,
                date = transaction.dateTime,
                merchant = transaction.merchantName
            )
        )

        rememberName(person.id, suggestion.matchedText, person.name, person.aliases)
        suggestionDao.update(
            suggestion.copy(personId = targetId, status = RepaymentStatus.CONFIRMED, updatedAt = now)
        )
        return true
    }

    /** The user says this is not a repayment. It is not suggested again. */
    suspend fun ignore(suggestionId: Long, now: LocalDateTime = LocalDateTime.now()) {
        val suggestion = suggestionDao.getById(suggestionId) ?: return
        if (suggestion.status == RepaymentStatus.PENDING) {
            suggestionDao.update(suggestion.copy(status = RepaymentStatus.IGNORED, updatedAt = now))
        }
    }

    /** Remembers the name as it appeared, so the next payment from the same text matches strongly. */
    private suspend fun rememberName(personId: Long, text: String?, name: String, aliases: List<String>) {
        val candidate = text?.trim()?.takeIf { it.any(Char::isLetter) } ?: return
        if (NameMatcher.score(candidate, name) >= SAME_NAME) return
        if (aliases.any { it.equals(candidate, ignoreCase = true) } || aliases.size >= MAX_ALIASES) return
        val person = lendDao.getPersonByIdSync(personId) ?: return
        lendDao.updatePerson(person.copy(aliases = person.aliases + candidate))
    }

    companion object {
        const val REPAYMENT_TITLE = "Repayment"
        private const val SAME_NAME = 90
        private const val MAX_ALIASES = 10
    }
}
