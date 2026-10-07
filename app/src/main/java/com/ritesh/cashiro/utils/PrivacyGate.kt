package com.ritesh.cashiro.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether total amounts are currently hidden.
 *
 * The two flags are Compose state on purpose: [CurrencyFormatter.formatTotal] reads them while a
 * screen is being composed, so every screen showing a total redraws the moment the setting is
 * switched or a finger covers the proximity sensor, with no per-screen wiring.
 *
 * Fed by [com.ritesh.cashiro.data.security.PrivacyController]; nothing else should write to it.
 */
object PrivacyGate {
    /** What replaces the digits of a hidden amount. The symbol stays so the currency is still clear. */
    const val MASK = "••••••"

    private var hiding by mutableStateOf(false)
    private var shown by mutableStateOf(false)

    /** True while totals should be shown masked. */
    val isHidingTotals: Boolean get() = hiding && !shown

    fun setHideTotals(hide: Boolean) {
        hiding = hide
        if (!hide) shown = false
    }

    /** Totals are visible while this is true, without changing the setting itself. */
    fun setRevealed(value: Boolean) {
        shown = value
    }
}
