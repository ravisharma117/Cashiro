package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.entity.LendBorrowPersonEntity
import com.ritesh.cashiro.data.database.entity.LendBorrowTransactionEntity
import com.ritesh.cashiro.data.database.entity.LendBorrowType
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class NameMatcherTest {

    @Test
    fun exactAndReorderedNames() {
        assertEquals(95, NameMatcher.score("ASHA VERMA", "Asha Verma"))
        assertEquals(90, NameMatcher.score("Verma Asha", "Asha Verma"))
    }

    @Test
    fun anExtraMiddleWordStillMatches() {
        assertEquals(80, NameMatcher.score("Asha Devi Verma", "Asha Verma"))
    }

    @Test
    fun anInitialMatchesTheFullFirstName() {
        assertEquals(75, NameMatcher.score("A Verma", "Asha Verma"))
    }

    @Test
    fun aSingleWordIsOnlyAWeakMatch() {
        assertEquals(55, NameMatcher.score("Asha", "Asha Verma"))
        assertEquals(0, NameMatcher.score("As", "Asha Verma"))
    }

    @Test
    fun differentNamesDoNotMatch() {
        assertEquals(0, NameMatcher.score("Ravi Kumar", "Asha Verma"))
        assertEquals(0, NameMatcher.score("Asha Kumar", "Asha Verma"))
    }

    @Test
    fun titlesAreIgnored() {
        assertEquals(95, NameMatcher.score("Mrs Asha Verma", "Asha Verma"))
    }

    @Test
    fun aUpiAddressUsesTheNamePartOnly() {
        assertEquals(95, NameMatcher.score("asha.verma@okbank", "Asha Verma"))
        assertEquals(0, NameMatcher.score("9876543210@upi", "Asha Verma"))
    }
}

class PhoneNumbersTest {

    @Test
    fun readsTenDigitsFromAnAddressOrAFormattedNumber() {
        assertEquals("9876543210", PhoneNumbers.extract("9876543210@ybl"))
        assertEquals("9876543210", PhoneNumbers.extract("+91 98765 43210"))
        assertNull(PhoneNumbers.extract("asha.verma@okbank"))
        assertNull(PhoneNumbers.extract(null))
    }

    @Test
    fun sameComparesTheLastTenDigits() {
        assertTrue(PhoneNumbers.same("9876543210@ybl", "+91 98765-43210"))
        assertEquals(false, PhoneNumbers.same("9876543210@ybl", "9123456789"))
    }
}

class RepaymentMatcherTest {

    private fun person(
        id: Long,
        name: String,
        owedToUser: String = "0",
        userOwes: String = "0",
        aliases: List<String> = emptyList(),
        phone: String? = null,
        open: List<OpenEntry> = emptyList()
    ) = LedgerPerson(id, name, aliases, phone, BigDecimal(owedToUser), BigDecimal(userOwes), open)

    private fun incoming(amount: String, counterparty: String?, contact: String? = null) =
        RepaymentFacts(true, BigDecimal(amount), counterparty, contact)

    private fun outgoing(amount: String, counterparty: String?) =
        RepaymentFacts(false, BigDecimal(amount), counterparty)

    @Test
    fun aPartialRepaymentStillSuggestsTheRightPerson() {
        val ledgers = listOf(
            person(1, "Asha Verma", owedToUser = "20000", open = listOf(OpenEntry(10, BigDecimal("20000"), true))),
            person(2, "Ravi Kumar", owedToUser = "3000")
        )

        val match = RepaymentMatcher.match(incoming("5000", "ASHA VERMA"), ledgers)

        assertNotNull(match)
        assertEquals(1L, match.personId)
        assertEquals(95, match.confidence)
    }

    @Test
    fun payingExactlyWhatIsOwedRaisesConfidenceAndPointsAtTheEntry() {
        val ledgers = listOf(
            person(1, "Asha Verma", owedToUser = "20000", open = listOf(OpenEntry(10, BigDecimal("20000"), true)))
        )

        val match = RepaymentMatcher.match(incoming("20000", "Asha Verma"), ledgers)!!

        assertEquals(100, match.confidence)
        assertEquals(10L, match.entryId)
    }

    @Test
    fun payingMoreThanIsOwedLowersConfidence() {
        val ledgers = listOf(person(1, "Asha Verma", owedToUser = "20000"))
        assertEquals(80, RepaymentMatcher.match(incoming("25000", "Asha Verma"), ledgers)!!.confidence)
    }

    @Test
    fun theDirectionHasToFit() {
        val ledgers = listOf(person(1, "Asha Verma", owedToUser = "20000"))
        // The user paying out cannot be a repayment from someone who owes the user
        assertNull(RepaymentMatcher.match(outgoing("5000", "Asha Verma"), ledgers))
    }

    @Test
    fun anOutgoingPaymentMatchesSomeoneTheUserOwes() {
        val ledgers = listOf(person(1, "Asha Verma", userOwes = "4000"))
        assertEquals(1L, RepaymentMatcher.match(outgoing("1000", "Asha Verma"), ledgers)!!.personId)
    }

    @Test
    fun nobodyOwingMeansNoSuggestion() {
        assertNull(RepaymentMatcher.match(incoming("500", "Asha Verma"), listOf(person(1, "Asha Verma"))))
    }

    @Test
    fun aWeakNameAloneStaysBelowTheStrongLine() {
        val ledgers = listOf(person(1, "Asha Verma", owedToUser = "1000"))
        val match = RepaymentMatcher.match(incoming("100", "Asha"), ledgers)!!
        assertEquals(55, match.confidence)
        assertTrue(match.confidence < RepaymentMatcher.STRONG)
    }

    @Test
    fun twoEquallyGoodCandidatesAreNeverStrong() {
        val ledgers = listOf(
            person(1, "Asha Verma", owedToUser = "1000"),
            person(2, "Asha Verma", owedToUser = "1000")
        )
        val match = RepaymentMatcher.match(incoming("100", "Asha Verma"), ledgers)!!
        assertTrue(match.confidence < RepaymentMatcher.STRONG, "ambiguous match must stay quiet: ${match.confidence}")
    }

    @Test
    fun aClearlyBetterCandidateWins() {
        val ledgers = listOf(
            person(1, "Asha Mehta Rao", owedToUser = "1000"),
            person(2, "Asha Mehta", owedToUser = "1000")
        )
        val match = RepaymentMatcher.match(incoming("100", "Asha Mehta"), ledgers)!!
        assertEquals(2L, match.personId)
        assertEquals(95, match.confidence)
    }

    @Test
    fun aSavedAliasMatchesLikeTheName() {
        val ledgers = listOf(person(1, "Ravi Kumar", owedToUser = "800", aliases = listOf("RK Traders")))
        assertEquals(95, RepaymentMatcher.match(incoming("100", "RK TRADERS"), ledgers)!!.confidence)
    }

    @Test
    fun aPhoneNumberInTheAddressMatchesTheSavedNumber() {
        val ledgers = listOf(person(1, "Ravi Kumar", owedToUser = "800", phone = "+91 98765 43210"))
        assertEquals(90, RepaymentMatcher.match(incoming("100", "9876543210@ybl"), ledgers)!!.confidence)
    }

    @Test
    fun aContactNameFoundFromTheNumberCanMatch() {
        val ledgers = listOf(person(1, "Asha Verma", owedToUser = "800"))
        val match = RepaymentMatcher.match(incoming("100", "9876543210@ybl", contact = "Asha Verma"), ledgers)!!
        assertEquals(1L, match.personId)
        assertEquals(95, match.confidence)
    }

    @Test
    fun theTextItMatchedOnIsKeptForLearningAnAlias() {
        val ledgers = listOf(person(1, "Asha Verma", owedToUser = "800"))
        assertEquals("ASHA V", RepaymentMatcher.match(incoming("100", "  ASHA V "), ledgers)?.matchedText)
    }
}

class LedgerBuilderTest {

    private fun entry(personId: Long, type: LendBorrowType, amount: String, currency: String = "INR", settled: Boolean = false) =
        LendBorrowTransactionEntity(
            id = (personId * 100 + amount.hashCode() % 90).toLong().coerceAtLeast(1),
            personId = personId, type = type, amount = BigDecimal(amount), title = "t",
            currency = currency, isSettled = settled
        )

    @Test
    fun outstandingIsWhatWasLentLessWhatCameBack() {
        val persons = listOf(LendBorrowPersonEntity(id = 1, name = "Asha Verma"))
        val entries = listOf(
            entry(1, LendBorrowType.LENT, "20000"),
            entry(1, LendBorrowType.SETTLEMENT_LENT, "5000", settled = true),
            entry(1, LendBorrowType.BORROWED, "700")
        )

        val ledger = LedgerBuilder.build(persons, entries, "INR").single()

        assertEquals(BigDecimal("15000"), ledger.owedToUser)
        assertEquals(BigDecimal("700"), ledger.userOwes)
    }

    @Test
    fun otherCurrenciesAreLeftOut() {
        val persons = listOf(LendBorrowPersonEntity(id = 1, name = "Asha Verma"))
        val ledger = LedgerBuilder.build(persons, listOf(entry(1, LendBorrowType.LENT, "100", currency = "USD")), "INR").single()
        assertEquals(0, ledger.owedToUser.signum())
    }

    @Test
    fun overpaymentNeverGoesNegative() {
        val persons = listOf(LendBorrowPersonEntity(id = 1, name = "Asha Verma"))
        val entries = listOf(entry(1, LendBorrowType.LENT, "100"), entry(1, LendBorrowType.SETTLEMENT_LENT, "300", settled = true))
        assertEquals(0, LedgerBuilder.build(persons, entries, "INR").single().owedToUser.signum())
    }

    @Test
    fun onlyUnsettledLoansCountAsOpenEntries() {
        val persons = listOf(LendBorrowPersonEntity(id = 1, name = "Asha Verma"))
        val entries = listOf(
            entry(1, LendBorrowType.LENT, "100"),
            entry(1, LendBorrowType.LENT, "200", settled = true),
            entry(1, LendBorrowType.SETTLEMENT_LENT, "50", settled = true)
        )
        assertEquals(1, LedgerBuilder.build(persons, entries, "INR").single().openEntries.size)
    }
}
