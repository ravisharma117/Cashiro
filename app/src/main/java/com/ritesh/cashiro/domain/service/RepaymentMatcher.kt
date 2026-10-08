package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.entity.LendBorrowPersonEntity
import com.ritesh.cashiro.data.database.entity.LendBorrowTransactionEntity
import com.ritesh.cashiro.data.database.entity.LendBorrowType
import java.math.BigDecimal

/** Scores how well the name on a transaction fits a saved name, 0 to 100. */
object NameMatcher {

    // Titles that say nothing about who the person is. Common first-name parts such as
    // "mohd" are deliberately not here.
    private val TITLES = setOf("mr", "mrs", "ms", "miss", "dr", "shri", "smt", "sri", "shree", "kumari")

    /** Lower-case words of [text]; for a UPI-style address only the part before the @ counts. */
    fun tokens(text: String): List<String> {
        val handle = text.substringBefore('@')
        return handle.lowercase()
            .map { if (it.isLetter()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() && it !in TITLES }
    }

    fun score(counterparty: String, name: String): Int {
        val a = tokens(counterparty)
        val b = tokens(name)
        if (a.isEmpty() || b.isEmpty()) return 0
        if (a == b) return 95
        if (a.sorted() == b.sorted()) return 90

        val (short, long) = if (a.size <= b.size) a to b else b to a
        if (short.size >= 2) {
            if (long.containsAll(short)) return 80
            if (matchesWithInitials(short, long)) return 75
            return 0
        }
        // A single word on one side: only a whole word of reasonable length counts
        val word = short.single()
        return if (word.length >= 3 && word in long) 55 else 0
    }

    fun bestScore(counterparty: String, names: List<String>): Int =
        names.maxOfOrNull { score(counterparty, it) } ?: 0

    /** Every word of [short] is a word of [long] or the initial of one, with at least one whole word. */
    private fun matchesWithInitials(short: List<String>, long: List<String>): Boolean {
        val remaining = long.toMutableList()
        var wholeWords = 0
        for (word in short) {
            val exact = remaining.indexOf(word)
            if (exact >= 0) {
                remaining.removeAt(exact)
                wholeWords++
                continue
            }
            val initialOf = if (word.length == 1) remaining.indexOfFirst { it.startsWith(word) } else -1
            if (initialOf >= 0) remaining.removeAt(initialOf) else return false
        }
        return wholeWords >= 1
    }
}

/** Phone numbers inside text such as a UPI address. */
object PhoneNumbers {
    /** The last ten digits of the first run of ten or more digits, or null. */
    fun extract(text: String?): String? {
        if (text == null) return null
        return Regex("""\d{10,}""").find(text.replace(Regex("""[\s\-+()]"""), ""))?.value?.takeLast(10)
    }

    fun same(a: String?, b: String?): Boolean {
        val x = extract(a)
        val y = extract(b)
        return x != null && x == y
    }
}

data class OpenEntry(val id: Long, val amount: BigDecimal, val isLent: Boolean)

/** A person together with what is still outstanding between them and the user. */
data class LedgerPerson(
    val id: Long,
    val name: String,
    val aliases: List<String>,
    val phone: String?,
    /** What the person still owes the user. */
    val owedToUser: BigDecimal,
    /** What the user still owes the person. */
    val userOwes: BigDecimal,
    val openEntries: List<OpenEntry>
)

object LedgerBuilder {
    /** Outstanding balances per person, counting only entries in [currency]. */
    fun build(
        persons: List<LendBorrowPersonEntity>,
        entries: List<LendBorrowTransactionEntity>,
        currency: String
    ): List<LedgerPerson> = persons.map { person ->
        val mine = entries.filter { it.personId == person.id && it.currency == currency }
        fun sum(type: LendBorrowType) =
            mine.filter { it.type == type }.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
        LedgerPerson(
            id = person.id,
            name = person.name,
            aliases = person.aliases,
            phone = person.phoneNumber,
            owedToUser = sum(LendBorrowType.LENT).subtract(sum(LendBorrowType.SETTLEMENT_LENT)).max(BigDecimal.ZERO),
            userOwes = sum(LendBorrowType.BORROWED).subtract(sum(LendBorrowType.SETTLEMENT_BORROWED)).max(BigDecimal.ZERO),
            openEntries = mine.filter { !it.isSettled && (it.type == LendBorrowType.LENT || it.type == LendBorrowType.BORROWED) }
                .map { OpenEntry(it.id, it.amount, it.type == LendBorrowType.LENT) }
        )
    }
}

data class RepaymentFacts(
    /** Money came in (the other side is paying the user back) rather than going out. */
    val isIncoming: Boolean,
    val amount: BigDecimal,
    /** The merchant / counterparty text on the transaction. */
    val counterparty: String?,
    /** A contact's name found from a phone number in the counterparty, if any. */
    val contactName: String? = null
)

data class RepaymentMatch(
    val personId: Long,
    val entryId: Long?,
    val confidence: Int,
    val matchedText: String?
)

/**
 * Finds who a transaction might be a repayment from (or to).
 *
 * The name is the main signal. The amount only nudges the result, because partial repayments are
 * normal: equal to what is owed raises confidence, more than what is owed lowers it. Only people
 * who actually owe (or are owed) in the matching direction are candidates. This never decides
 * anything: it produces a suggestion for the user to confirm.
 */
object RepaymentMatcher {
    const val STRONG = 80
    const val MINIMUM = 50
    private const val CLOSE_CALL = 10

    fun match(facts: RepaymentFacts, ledgers: List<LedgerPerson>): RepaymentMatch? {
        val candidates = ledgers.filter {
            if (facts.isIncoming) it.owedToUser.signum() > 0 else it.userOwes.signum() > 0
        }
        val scored = candidates.mapNotNull { person ->
            val names = listOf(person.name) + person.aliases
            val texts = listOfNotNull(facts.counterparty, facts.contactName)
            var base = texts.maxOfOrNull { NameMatcher.bestScore(it, names) } ?: 0
            if (PhoneNumbers.same(facts.counterparty, person.phone)) base = maxOf(base, PHONE_MATCH)
            if (base == 0) return@mapNotNull null

            val outstanding = if (facts.isIncoming) person.owedToUser else person.userOwes
            val open = person.openEntries.filter { it.isLent == facts.isIncoming }
            val equalEntry = open.firstOrNull { it.amount.compareTo(facts.amount) == 0 }
            var confidence = base
            if (facts.amount.compareTo(outstanding) == 0 || equalEntry != null) confidence += 5
            if (facts.amount > outstanding) confidence -= 15
            confidence = confidence.coerceIn(0, 100)
            if (confidence < MINIMUM) null else Triple(person, confidence, equalEntry?.id)
        }.sortedByDescending { it.second }

        val best = scored.firstOrNull() ?: return null
        val runnerUp = scored.getOrNull(1)
        // Two people fit about equally well: do not pretend to know which, keep it quiet
        val confidence = if (runnerUp != null && best.second - runnerUp.second < CLOSE_CALL) {
            minOf(best.second, STRONG - 1)
        } else best.second

        return RepaymentMatch(
            personId = best.first.id,
            entryId = best.third,
            confidence = confidence,
            matchedText = (facts.counterparty ?: facts.contactName)?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private const val PHONE_MATCH = 90
}
