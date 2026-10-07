package com.ritesh.cashiro.utils

import com.ritesh.cashiro.domain.model.AccountKind
import com.ritesh.cashiro.domain.model.accountKindOf
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.CardEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.TransactionEntity

/**
 * Extension functions for entity currency formatting
 * These functions automatically use the entity's currency for formatting
 */

/**
 * Formats the transaction amount with its currency
 */
fun TransactionEntity.formatAmount(): String =
    CurrencyFormatter.formatCurrency(amount, currency)

/**
 * Formats the account balance with its currency
 */
fun AccountBalanceEntity.formatBalance(): String =
    CurrencyFormatter.formatTotal(balance, currency)

/**
 * Formats the subscription amount with its currency
 */
fun SubscriptionEntity.formatAmount(): String =
    CurrencyFormatter.formatCurrency(amount, currency)

/**
 * Formats the card's last balance with its currency
 */
fun CardEntity.formatLastBalance(): String =
    CurrencyFormatter.formatCurrency(lastBalance ?: java.math.BigDecimal.ZERO, currency)

/**
 * Formats the account's credit limit with its currency (for credit card accounts)
 */
fun AccountBalanceEntity.formatCreditLimit(): String =
    CurrencyFormatter.formatCurrency(creditLimit ?: java.math.BigDecimal.ZERO, currency)

/**
 * The kind of account this balance row belongs to
 */
val AccountBalanceEntity.kind: AccountKind
    get() = accountKindOf(bankName, accountLast4, isCreditCard, isWallet)
