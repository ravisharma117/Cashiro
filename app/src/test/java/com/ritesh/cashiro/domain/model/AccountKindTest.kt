package com.ritesh.cashiro.domain.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountKindTest {

    @Test
    fun bankAccountIsBank() {
        assertEquals(AccountKind.BANK, accountKindOf("Test Bank", "1234", isCreditCard = false, isWallet = false))
    }

    @Test
    fun creditCardFlagWinsOverEverythingElse() {
        assertEquals(AccountKind.CREDIT_CARD, accountKindOf("Test Bank", "1234", isCreditCard = true, isWallet = false))
        assertEquals(
            AccountKind.CREDIT_CARD,
            accountKindOf(CashAccount.BANK_NAME, CashAccount.WALLET_LAST4, isCreditCard = true, isWallet = true)
        )
    }

    @Test
    fun builtInCashWalletIsCash() {
        assertEquals(
            AccountKind.CASH,
            accountKindOf(CashAccount.BANK_NAME, CashAccount.WALLET_LAST4, isCreditCard = false, isWallet = true)
        )
    }

    @Test
    fun cashIsRecognisedByKeyEvenWithoutWalletFlag() {
        assertEquals(
            AccountKind.CASH,
            accountKindOf(CashAccount.BANK_NAME, CashAccount.WALLET_LAST4, isCreditCard = false, isWallet = false)
        )
    }

    @Test
    fun otherWalletIsWallet() {
        assertEquals(
            AccountKind.WALLET,
            accountKindOf("Travel Wallet", CashAccount.WALLET_LAST4, isCreditCard = false, isWallet = true)
        )
    }

    @Test
    fun bankNamedCashWithAccountNumberIsNotCash() {
        assertEquals(AccountKind.BANK, accountKindOf(CashAccount.BANK_NAME, "1234", isCreditCard = false, isWallet = false))
    }

    @Test
    fun isCashMatchesOnlyTheExactKey() {
        assertTrue(CashAccount.isCash("Cash", "wallet"))
        assertFalse(CashAccount.isCash("cash", "wallet"))
        assertFalse(CashAccount.isCash("Cash", "1234"))
        assertFalse(CashAccount.isCash(null, null))
    }
}
