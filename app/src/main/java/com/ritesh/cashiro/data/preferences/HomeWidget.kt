package com.ritesh.cashiro.data.preferences

import androidx.annotation.StringRes
import com.ritesh.cashiro.R

/**
 * The cards of the Home screen. [defaultOrder] is where a card sits on a fresh install, and where
 * a card that is new to an existing layout is slotted in (after the card that precedes it).
 */
enum class HomeWidget(@StringRes val labelRes: Int, val defaultOrder: Int) {
    NETWORTH_SUMMARY(R.string.home_widget_networth, 0),
    MONTHLY_SUMMARY(R.string.home_widget_monthly_summary, 1),
    LOANS(R.string.home_widget_loans, 2),
    ACCOUNT_CAROUSEL(R.string.home_widget_accounts, 3),
    UPCOMING_SUBSCRIPTIONS(R.string.home_widget_upcoming_payments, 4),
    RECURRING(R.string.home_widget_recurring, 5),
    RECENT_TRANSACTIONS(R.string.home_widget_recent, 6),
    BUDGET_CAROUSEL(R.string.home_widget_budgets, 7),
    TRANSACTION_HEATMAP(R.string.home_widget_heatmap, 8);

    companion object {
        fun fromName(name: String): HomeWidget? {
            return entries.find { it.name == name }
        }
    }
}
