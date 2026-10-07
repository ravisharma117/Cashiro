package com.ritesh.cashiro.domain.model

/**
 * What an account is, independent of how it is stored.
 */
enum class AccountKind {
    BANK,
    CASH,
    CREDIT_CARD,
    WALLET
}

/**
 * The built-in Cash account. Wallet-type accounts share [WALLET_LAST4] as their
 * identifier and are told apart by name; Cash is the wallet named [BANK_NAME].
 */
object CashAccount {
    const val BANK_NAME = "Cash"
    const val WALLET_LAST4 = "wallet"
    const val ICON_NAME = "type_finance_dollar_banknote"
    const val COLOR = "#4CAF50"

    fun isCash(bankName: String?, accountLast4: String?): Boolean =
        bankName == BANK_NAME && accountLast4 == WALLET_LAST4
}

fun accountKindOf(
    bankName: String?,
    accountLast4: String?,
    isCreditCard: Boolean,
    isWallet: Boolean
): AccountKind = when {
    isCreditCard -> AccountKind.CREDIT_CARD
    CashAccount.isCash(bankName, accountLast4) -> AccountKind.CASH
    isWallet || accountLast4 == CashAccount.WALLET_LAST4 -> AccountKind.WALLET
    else -> AccountKind.BANK
}
