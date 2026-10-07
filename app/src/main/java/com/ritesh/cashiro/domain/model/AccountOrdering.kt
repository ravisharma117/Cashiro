package com.ritesh.cashiro.domain.model

import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity

/**
 * User-chosen order of accounts.
 *
 * Accounts have no table of their own (an account is the pair bank name + last 4 digits over
 * balance-history rows), so the order is kept as a list of account keys in preferences.
 */
object AccountOrdering {

    /** Same key format the app already uses for hidden and main accounts. */
    fun keyOf(bankName: String, accountLast4: String): String = "${bankName}_$accountLast4"

    fun keyOf(account: AccountBalanceEntity): String = keyOf(account.bankName, account.accountLast4)

    /**
     * Accounts listed in [order] come first, in that order. Accounts not listed (for example one
     * detected after the user last reordered) follow in the order they arrived. Keys in [order]
     * that match no account are ignored. An empty [order] leaves [accounts] unchanged.
     */
    fun sort(accounts: List<AccountBalanceEntity>, order: List<String>): List<AccountBalanceEntity> {
        if (order.isEmpty() || accounts.size < 2) return accounts
        val position = HashMap<String, Int>(order.size)
        order.forEachIndexed { index, key -> position.putIfAbsent(key, index) }
        val (listed, unlisted) = accounts.partition { keyOf(it) in position }
        return listed.sortedBy { position.getValue(keyOf(it)) } + unlisted
    }

    /** The key list to store after the user arranges [accounts] in the given order. */
    fun orderOf(accounts: List<AccountBalanceEntity>): List<String> =
        accounts.map { keyOf(it) }.distinct()

    /** Replaces [oldKey] with [newKey], keeping its position. No change if [oldKey] is not listed. */
    fun rename(order: List<String>, oldKey: String, newKey: String): List<String> {
        if (oldKey == newKey || oldKey !in order) return order
        return order.map { if (it == oldKey) newKey else it }.distinct()
    }

    fun remove(order: List<String>, key: String): List<String> =
        if (key in order) order.filterNot { it == key } else order
}
