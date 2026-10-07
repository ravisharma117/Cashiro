package com.ritesh.cashiro.domain.model

import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AccountOrderingTest {

    private fun account(bank: String, last4: String) = AccountBalanceEntity(
        bankName = bank,
        accountLast4 = last4,
        balance = BigDecimal.ZERO,
        timestamp = LocalDateTime.of(2026, 1, 1, 0, 0)
    )

    private val a = account("Bank A", "1111")
    private val b = account("Bank B", "2222")
    private val c = account("Bank C", "3333")
    private val d = account("Bank D", "4444")

    private fun key(x: AccountBalanceEntity) = AccountOrdering.keyOf(x)

    @Test
    fun emptyOrderLeavesAccountsUnchanged() {
        val accounts = listOf(c, a, b)
        assertSame(accounts, AccountOrdering.sort(accounts, emptyList()))
    }

    @Test
    fun listedAccountsFollowTheStoredOrder() {
        val sorted = AccountOrdering.sort(listOf(a, b, c), listOf(key(c), key(a), key(b)))
        assertEquals(listOf(c, a, b), sorted)
    }

    @Test
    fun unlistedAccountsComeLastInArrivalOrder() {
        val sorted = AccountOrdering.sort(listOf(d, a, c, b), listOf(key(b), key(a)))
        assertEquals(listOf(b, a, d, c), sorted)
    }

    @Test
    fun staleKeysAreIgnored() {
        val sorted = AccountOrdering.sort(listOf(a, b), listOf("Gone_9999", key(b), key(a)))
        assertEquals(listOf(b, a), sorted)
    }

    @Test
    fun duplicateKeysUseTheFirstPosition() {
        val sorted = AccountOrdering.sort(listOf(a, b), listOf(key(b), key(a), key(b)))
        assertEquals(listOf(b, a), sorted)
    }

    @Test
    fun orderOfReturnsKeysInListOrderWithoutDuplicates() {
        assertEquals(listOf(key(c), key(a)), AccountOrdering.orderOf(listOf(c, a, c)))
    }

    @Test
    fun roundTripKeepsTheArrangement() {
        val arranged = listOf(c, a, b)
        val stored = AccountOrdering.orderOf(arranged)
        assertEquals(arranged, AccountOrdering.sort(listOf(a, b, c), stored))
    }

    @Test
    fun renameKeepsPosition() {
        val order = listOf(key(c), key(a), key(b))
        val renamed = AccountOrdering.rename(order, key(a), AccountOrdering.keyOf("Bank A2", "1111"))
        assertEquals(listOf(key(c), "Bank A2_1111", key(b)), renamed)
    }

    @Test
    fun renameOfUnlistedAccountChangesNothing() {
        val order = listOf(key(c))
        assertSame(order, AccountOrdering.rename(order, key(a), "Other_1"))
    }

    @Test
    fun removeDropsOnlyThatAccount() {
        val order = listOf(key(c), key(a), key(b))
        assertEquals(listOf(key(c), key(b)), AccountOrdering.remove(order, key(a)))
        assertSame(order, AccountOrdering.remove(order, key(d)))
    }

    @Test
    fun cashAndWalletKeysAreOrdinaryKeys() {
        val cash = account(CashAccount.BANK_NAME, CashAccount.WALLET_LAST4)
        val sorted = AccountOrdering.sort(listOf(a, cash, b), listOf(key(cash), key(b)))
        assertEquals(listOf(cash, b, a), sorted)
    }
}
