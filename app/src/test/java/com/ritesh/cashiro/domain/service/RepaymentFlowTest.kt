package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.dao.LendBorrowDao
import com.ritesh.cashiro.data.database.dao.RepaymentSuggestionDao
import com.ritesh.cashiro.data.database.entity.LendBorrowPersonEntity
import com.ritesh.cashiro.data.database.entity.LendBorrowTransactionEntity
import com.ritesh.cashiro.data.database.entity.LendBorrowType
import com.ritesh.cashiro.data.database.entity.RepaymentStatus
import com.ritesh.cashiro.data.database.entity.RepaymentSuggestionEntity
import com.ritesh.cashiro.data.database.entity.TransactionEntity
import com.ritesh.cashiro.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeLendDao : LendBorrowDao {
    val persons = mutableMapOf<Long, LendBorrowPersonEntity>()
    val entries = mutableMapOf<Long, LendBorrowTransactionEntity>()
    private var nextId = 1L

    override fun getActivePersons(): Flow<List<LendBorrowPersonEntity>> = flowOf(persons.values.filter { !it.isArchived })
    override fun getAllPersons(): Flow<List<LendBorrowPersonEntity>> = flowOf(persons.values.toList())
    override suspend fun getActivePersonsList() = persons.values.filter { !it.isArchived }.sortedBy { it.name }
    override suspend fun getAllTransactionsList() = entries.values.sortedByDescending { it.date }
    override suspend fun getUnsettledWithDueDate() = entries.values.filter { !it.isSettled && it.dueDate != null }
    override suspend fun updateReminderMarks(id: Long, pre: LocalDate?, due: LocalDate?, overdue: LocalDate?) {
        entries[id]?.let { entries[id] = it.copy(preRemindedFor = pre, dueRemindedFor = due, overdueRemindedFor = overdue) }
    }
    override fun getPersonById(id: Long): Flow<LendBorrowPersonEntity?> = flowOf(persons[id])
    override suspend fun getPersonByIdSync(id: Long) = persons[id]
    override suspend fun insertPerson(person: LendBorrowPersonEntity): Long {
        val id = if (person.id == 0L) nextId++ else person.id
        persons[id] = person.copy(id = id)
        return id
    }
    override suspend fun updatePerson(person: LendBorrowPersonEntity) { persons[person.id] = person }
    override suspend fun deletePerson(person: LendBorrowPersonEntity) { persons.remove(person.id) }
    override suspend fun deletePersonById(id: Long) { persons.remove(id) }
    override fun getTransactionsForPerson(personId: Long): Flow<List<LendBorrowTransactionEntity>> =
        flowOf(entries.values.filter { it.personId == personId })
    override suspend fun getTransactionsForPersonSync(personId: Long) = entries.values.filter { it.personId == personId }
    override fun getAllTransactions(): Flow<List<LendBorrowTransactionEntity>> = flowOf(entries.values.toList())
    override suspend fun getTransactionById(id: Long) = entries[id]
    override suspend fun getTransactionByWalletId(transactionId: Long) =
        entries.values.firstOrNull { it.transactionId == transactionId }
    override suspend fun insertTransaction(transaction: LendBorrowTransactionEntity): Long {
        val id = if (transaction.id == 0L) nextId++ else transaction.id
        entries[id] = transaction.copy(id = id)
        return id
    }
    override suspend fun updateTransaction(transaction: LendBorrowTransactionEntity) { entries[transaction.id] = transaction }
    override suspend fun deleteTransaction(transaction: LendBorrowTransactionEntity) { entries.remove(transaction.id) }
    override suspend fun deleteTransactionById(id: Long) { entries.remove(id) }
    override suspend fun deleteAllTransactionsForPerson(personId: Long) { entries.values.removeAll { it.personId == personId } }
}

private class FakeSuggestionDao : RepaymentSuggestionDao {
    val items = mutableMapOf<Long, RepaymentSuggestionEntity>()
    private var nextId = 1L

    override fun observePending(): Flow<List<RepaymentSuggestionEntity>> =
        flowOf(items.values.filter { it.status == RepaymentStatus.PENDING })
    override suspend fun getById(id: Long) = items[id]
    override suspend fun getByTransactionId(transactionId: Long) = items.values.firstOrNull { it.transactionId == transactionId }
    override suspend fun insert(suggestion: RepaymentSuggestionEntity): Long {
        if (items.values.any { it.transactionId == suggestion.transactionId }) return -1L
        val id = nextId++
        items[id] = suggestion.copy(id = id)
        return id
    }
    override suspend fun update(suggestion: RepaymentSuggestionEntity) { items[suggestion.id] = suggestion }
    override suspend fun getPending() = items.values.filter { it.status == RepaymentStatus.PENDING }
    override suspend fun insertAll(suggestions: List<RepaymentSuggestionEntity>) { suggestions.forEach { items[it.id] = it } }
    override suspend fun deleteAll() { items.clear() }
}

class LendReminderProcessorTest {

    private val dao = FakeLendDao()
    private val processor = LendReminderProcessor(dao)
    private fun at(text: String) = LocalDateTime.parse(text)

    private suspend fun person(name: String = "Asha Verma", archived: Boolean = false) =
        dao.insertPerson(LendBorrowPersonEntity(name = name, isArchived = archived))

    private suspend fun lend(personId: Long, due: String, type: LendBorrowType = LendBorrowType.LENT, amount: String = "5000") =
        dao.insertTransaction(
            LendBorrowTransactionEntity(
                personId = personId, type = type, amount = BigDecimal(amount), title = "Loan",
                dueDate = LocalDateTime.parse("${due}T00:00:00")
            )
        )

    @Test
    fun theThreeStagesGoOutOncePerDueDate() = runBlocking<Unit> {
        val p = person()
        lend(p, "2026-10-13")

        assertTrue(processor.process(at("2026-10-09T09:00:00"), true).isEmpty())

        val before = processor.process(at("2026-10-10T09:00:00"), true)
        assertEquals(listOf(LendReminderStage.BEFORE), before.map { it.stage })
        assertTrue(processor.process(at("2026-10-11T09:00:00"), true).isEmpty())

        assertEquals(listOf(LendReminderStage.DUE), processor.process(at("2026-10-13T09:00:00"), true).map { it.stage })
        assertTrue(processor.process(at("2026-10-13T15:00:00"), true).isEmpty())

        assertEquals(listOf(LendReminderStage.OVERDUE), processor.process(at("2026-10-14T09:00:00"), true).map { it.stage })
        assertTrue(processor.process(at("2026-10-15T09:00:00"), true).isEmpty())
    }

    @Test
    fun aGapSendsOnlyTheLatestStageAndClosesTheEarlierOnes() = runBlocking<Unit> {
        val p = person()
        val id = lend(p, "2026-10-13")

        val reminders = processor.process(at("2026-10-20T09:00:00"), true)

        assertEquals(listOf(LendReminderStage.OVERDUE), reminders.map { it.stage })
        val entry = dao.entries.getValue(id)
        assertEquals(LocalDate.parse("2026-10-13"), entry.preRemindedFor)
        assertEquals(LocalDate.parse("2026-10-13"), entry.dueRemindedFor)
        assertEquals(LocalDate.parse("2026-10-13"), entry.overdueRemindedFor)
        assertNull(LendReminderProcessor.nextWake(at("2026-10-20T09:00:00"), dao.entries.values.toList(), setOf(p), true))
    }

    @Test
    fun nothingGoesOutWhileSwitchedOffOrBeforeRunTime() = runBlocking<Unit> {
        val p = person()
        lend(p, "2026-10-13")

        assertTrue(processor.process(at("2026-10-13T09:00:00"), false).isEmpty())
        assertTrue(processor.process(at("2026-10-13T07:00:00"), true).isEmpty())
        // Nothing was marked, so it still goes out once allowed
        assertEquals(1, processor.process(at("2026-10-13T10:00:00"), true).size)
    }

    @Test
    fun aBorrowedEntryRemindsToo() = runBlocking<Unit> {
        val p = person()
        lend(p, "2026-10-13", type = LendBorrowType.BORROWED)
        assertEquals(1, processor.process(at("2026-10-13T09:00:00"), true).size)
    }

    @Test
    fun aPersonWhoHasRepaidInFullGetsNoReminderAndTheAlarmStopsWaiting() = runBlocking<Unit> {
        val p = person()
        lend(p, "2026-10-13", amount = "5000")
        dao.insertTransaction(
            LendBorrowTransactionEntity(
                personId = p, type = LendBorrowType.SETTLEMENT_LENT, amount = BigDecimal("5000"),
                title = "Repayment", isSettled = true
            )
        )

        assertTrue(processor.process(at("2026-10-13T09:00:00"), true).isEmpty())
        assertNull(LendReminderProcessor.nextWake(at("2026-10-13T09:01:00"), dao.entries.values.toList(), setOf(p), true))
    }

    @Test
    fun anArchivedPersonIsSkippedByBothTheProcessorAndTheAlarm() = runBlocking<Unit> {
        val p = person(archived = true)
        lend(p, "2026-10-13")

        assertTrue(processor.process(at("2026-10-13T09:00:00"), true).isEmpty())
        // The alarm is only told about active people, so it cannot wait on this entry
        assertNull(LendReminderProcessor.nextWake(at("2026-10-13T09:00:00"), dao.entries.values.toList(), emptySet(), true))
    }

    @Test
    fun theAlarmWaitsForTheFirstStageAndDoesNotPollThroughTheNight() = runBlocking<Unit> {
        val p = person()
        lend(p, "2026-10-13")
        val entries = dao.entries.values.toList()

        assertEquals(at("2026-10-10T08:00:00"), LendReminderProcessor.nextWake(at("2026-10-01T10:00:00"), entries, setOf(p), true))
        // Already past that moment but still before 08:00: wait for 08:00 instead of every minute
        assertEquals(at("2026-10-11T08:00:00"), LendReminderProcessor.nextWake(at("2026-10-11T03:00:00"), entries, setOf(p), true))
        assertNull(LendReminderProcessor.nextWake(at("2026-10-01T10:00:00"), entries, setOf(p), false))
    }
}

class RepaymentDetectorTest {

    private val lend = FakeLendDao()
    private val suggestions = FakeSuggestionDao()
    private val now = LocalDateTime.parse("2026-10-10T12:00:00")
    private var contactLookups = mutableListOf<String>()
    private val detector = RepaymentDetector(lend, suggestions) { phone ->
        contactLookups += phone
        if (phone == "9876543210") "Asha Verma" else null
    }

    private suspend fun lentTo(name: String, amount: String = "20000"): Long {
        val id = lend.insertPerson(LendBorrowPersonEntity(name = name))
        lend.insertTransaction(
            LendBorrowTransactionEntity(personId = id, type = LendBorrowType.LENT, amount = BigDecimal(amount), title = "Loan")
        )
        return id
    }

    private fun txn(
        merchant: String,
        amount: String = "5000",
        type: TransactionType = TransactionType.INCOME,
        daysAgo: Long = 0
    ) = TransactionEntity(
        amount = BigDecimal(amount), merchantName = merchant, category = "Income", transactionType = type,
        dateTime = now.minusDays(daysAgo), transactionHash = "h-$merchant-$amount-$daysAgo"
    )

    @Test
    fun anIncomingPaymentFromTheBorrowerFilesASuggestion() = runBlocking<Unit> {
        val person = lentTo("Asha Verma")

        val suggestion = detector.detect(11L, txn("ASHA VERMA"), now)

        assertNotNull(suggestion)
        assertEquals(person, suggestion.personId)
        assertEquals(95, suggestion.confidence)
        assertTrue(suggestion.isIncoming)
        assertEquals(RepaymentStatus.PENDING, suggestions.items.values.single().status)
    }

    @Test
    fun theSameTransactionIsNeverSuggestedTwiceNotEvenAfterIgnoring() = runBlocking<Unit> {
        lentTo("Asha Verma")
        val first = detector.detect(11L, txn("ASHA VERMA"), now)!!
        assertNull(detector.detect(11L, txn("ASHA VERMA"), now))

        suggestions.update(first.copy(status = RepaymentStatus.IGNORED))
        assertNull(detector.detect(11L, txn("ASHA VERMA"), now))
        assertEquals(1, suggestions.items.size)
    }

    @Test
    fun oldTransactionsAreLeftAlone() = runBlocking<Unit> {
        lentTo("Asha Verma")
        assertNull(detector.detect(11L, txn("ASHA VERMA", daysAgo = 5), now))
    }

    @Test
    fun aTransactionAlreadyLinkedToAnEntryIsLeftAlone() = runBlocking<Unit> {
        val person = lentTo("Asha Verma")
        lend.insertTransaction(
            LendBorrowTransactionEntity(personId = person, transactionId = 11L, type = LendBorrowType.SETTLEMENT_LENT,
                amount = BigDecimal("100"), title = "x", isSettled = true)
        )
        assertNull(detector.detect(11L, txn("ASHA VERMA"), now))
    }

    @Test
    fun transfersAndOtherTypesAreIgnored() = runBlocking<Unit> {
        lentTo("Asha Verma")
        assertNull(detector.detect(11L, txn("ASHA VERMA", type = TransactionType.TRANSFER), now))
        assertNull(detector.detect(12L, txn("ASHA VERMA", type = TransactionType.BALANCE_UPDATE), now))
    }

    @Test
    fun noPeopleMeansNoWorkAtAll() = runBlocking<Unit> {
        assertNull(detector.detect(11L, txn("9876543210@ybl"), now))
        assertTrue(contactLookups.isEmpty(), "contacts must not be touched when nobody is in the ledger")
    }

    @Test
    fun aPhoneNumberIsLookedUpInContactsToFindTheName() = runBlocking<Unit> {
        val person = lentTo("Asha Verma")

        val suggestion = detector.detect(11L, txn("9876543210@ybl"), now)

        assertEquals(listOf("9876543210"), contactLookups)
        assertEquals(person, suggestion?.personId)
    }
}

class RepaymentSuggestionActionsTest {

    private val lend = FakeLendDao()
    private val suggestions = FakeSuggestionDao()
    private val now = LocalDateTime.parse("2026-10-10T12:00:00")
    private val saved = mutableMapOf<Long, TransactionEntity>()
    private val actions = RepaymentSuggestionActions(lend, suggestions) { saved[it] }

    private suspend fun scenario(
        text: String = "ASHA VERMA",
        incoming: Boolean = true,
        loan: LendBorrowType = LendBorrowType.LENT
    ): Pair<Long, Long> {
        val person = lend.insertPerson(LendBorrowPersonEntity(name = "Asha Verma"))
        lend.insertTransaction(
            LendBorrowTransactionEntity(personId = person, type = loan, amount = BigDecimal("20000"), title = "Loan")
        )
        saved[11L] = TransactionEntity(
            amount = BigDecimal("5000"), merchantName = text, category = "c",
            transactionType = if (incoming) TransactionType.INCOME else TransactionType.EXPENSE,
            dateTime = now, transactionHash = "h"
        )
        val id = suggestions.insert(
            RepaymentSuggestionEntity(
                transactionId = 11L, personId = person, amount = BigDecimal("5000"),
                isIncoming = incoming, confidence = 95, matchedText = text
            )
        )
        return person to id
    }

    private fun owed(person: Long) =
        LedgerBuilder.build(lend.persons.values.toList(), lend.entries.values.toList(), "INR").first { it.id == person }

    @Test
    fun confirmingReducesWhatIsOwedAndLinksTheTransaction() = runBlocking<Unit> {
        val (person, id) = scenario()

        assertTrue(actions.confirm(id, now = now))

        assertEquals(BigDecimal("15000"), owed(person).owedToUser)
        val entry = lend.entries.values.single { it.type == LendBorrowType.SETTLEMENT_LENT }
        assertEquals(11L, entry.transactionId)
        assertEquals(BigDecimal("5000"), entry.amount)
        assertTrue(entry.isSettled)
        assertEquals(RepaymentStatus.CONFIRMED, suggestions.items.getValue(id).status)
    }

    @Test
    fun confirmingTwiceSettlesOnlyOnce() = runBlocking<Unit> {
        val (person, id) = scenario()

        assertTrue(actions.confirm(id, now = now))
        assertEquals(false, actions.confirm(id, now = now))

        assertEquals(BigDecimal("15000"), owed(person).owedToUser)
        assertEquals(1, lend.entries.values.count { it.type == LendBorrowType.SETTLEMENT_LENT })
    }

    @Test
    fun ignoringLeavesTheBalanceAndBlocksConfirming() = runBlocking<Unit> {
        val (person, id) = scenario()

        actions.ignore(id, now)

        assertEquals(RepaymentStatus.IGNORED, suggestions.items.getValue(id).status)
        assertEquals(BigDecimal("20000"), owed(person).owedToUser)
        assertEquals(false, actions.confirm(id, now = now))
    }

    @Test
    fun anOutgoingRepaymentSettlesWhatTheUserOwes() = runBlocking<Unit> {
        val (person, id) = scenario(incoming = false, loan = LendBorrowType.BORROWED)

        assertTrue(actions.confirm(id, now = now))

        assertEquals(BigDecimal("15000"), owed(person).userOwes)
        assertTrue(lend.entries.values.any { it.type == LendBorrowType.SETTLEMENT_BORROWED })
    }

    @Test
    fun assigningToSomeoneElseSettlesThatPersonAndLearnsTheName() = runBlocking<Unit> {
        val (_, id) = scenario(text = "RK TRADERS")
        val other = lend.insertPerson(LendBorrowPersonEntity(name = "Ravi Kumar"))
        lend.insertTransaction(
            LendBorrowTransactionEntity(personId = other, type = LendBorrowType.LENT, amount = BigDecimal("9000"), title = "Loan")
        )

        assertTrue(actions.confirm(id, personId = other, now = now))

        assertEquals(BigDecimal("4000"), owed(other).owedToUser)
        assertEquals(listOf("RK TRADERS"), lend.persons.getValue(other).aliases)
        assertEquals(other, suggestions.items.getValue(id).personId)
    }

    @Test
    fun aNameThatAlreadyMatchesIsNotAddedAsAnAlias() = runBlocking<Unit> {
        val (person, id) = scenario(text = "ASHA VERMA")
        actions.confirm(id, now = now)
        assertTrue(lend.persons.getValue(person).aliases.isEmpty())
    }

    @Test
    fun aDifferentSpellingIsRememberedOnceOnly() = runBlocking<Unit> {
        val (person, id) = scenario(text = "ASHA V")
        actions.confirm(id, now = now)
        assertEquals(listOf("ASHA V"), lend.persons.getValue(person).aliases)

        // A second confirmation with the same text must not repeat it
        saved[12L] = saved.getValue(11L)
        val second = suggestions.insert(
            RepaymentSuggestionEntity(transactionId = 12L, personId = person, amount = BigDecimal("100"), confidence = 75, matchedText = "asha v")
        )
        actions.confirm(second, now = now)
        assertEquals(listOf("ASHA V"), lend.persons.getValue(person).aliases)
    }

    @Test
    fun aMissingTransactionCannotBeConfirmed() = runBlocking<Unit> {
        val (_, id) = scenario()
        saved.clear()
        assertEquals(false, actions.confirm(id, now = now))
        assertEquals(RepaymentStatus.PENDING, suggestions.items.getValue(id).status)
    }
}
