package com.ritesh.cashiro.utils

import com.ritesh.cashiro.data.currency.model.CurrencySymbols
import com.ritesh.cashiro.data.security.ProximityReveal
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PrivacyGateTest {

    private val amount = BigDecimal("123456.78")

    @Before
    @After
    fun reset() {
        PrivacyGate.setHideTotals(false)
        PrivacyGate.setRevealed(false)
    }

    @Test
    fun totalsAreShownNormallyByDefault() {
        assertFalse(PrivacyGate.isHidingTotals)
        assertEquals(
            CurrencyFormatter.formatCurrency(amount, "INR"),
            CurrencyFormatter.formatTotal(amount, "INR")
        )
    }

    @Test
    fun hiddenTotalIsTheSymbolAndTheMaskOnly() {
        PrivacyGate.setHideTotals(true)

        val hidden = CurrencyFormatter.formatTotal(amount, "INR")

        assertEquals(CurrencySymbols.getSymbol("INR") + PrivacyGate.MASK, hidden)
        assertTrue(hidden.none { it.isDigit() }, "no digit of the amount may show: $hidden")
    }

    @Test
    fun theMaskDoesNotRevealHowBigTheAmountIs() {
        PrivacyGate.setHideTotals(true)
        assertEquals(
            CurrencyFormatter.formatTotal(BigDecimal("5"), "INR"),
            CurrencyFormatter.formatTotal(BigDecimal("98765432.10"), "INR")
        )
    }

    @Test
    fun theMaskKeepsTheCurrencySymbol() {
        PrivacyGate.setHideTotals(true)
        assertNotEquals(
            CurrencyFormatter.formatTotal(amount, "INR"),
            CurrencyFormatter.formatTotal(amount, "USD")
        )
    }

    @Test
    fun doubleAmountsAreMaskedToo() {
        PrivacyGate.setHideTotals(true)
        assertEquals(
            CurrencyFormatter.formatTotal(amount, "INR"),
            CurrencyFormatter.formatTotal(123456.78, "INR")
        )
    }

    @Test
    fun revealedShowsTheRealAmountWithoutChangingTheSetting() {
        PrivacyGate.setHideTotals(true)

        PrivacyGate.setRevealed(true)
        assertFalse(PrivacyGate.isHidingTotals)
        assertEquals(
            CurrencyFormatter.formatCurrency(amount, "INR"),
            CurrencyFormatter.formatTotal(amount, "INR")
        )

        PrivacyGate.setRevealed(false)
        assertTrue(PrivacyGate.isHidingTotals)
    }

    @Test
    fun turningTheSettingOffAlsoClearsAReveal() {
        PrivacyGate.setHideTotals(true)
        PrivacyGate.setRevealed(true)

        PrivacyGate.setHideTotals(false)
        PrivacyGate.setHideTotals(true)

        assertTrue(PrivacyGate.isHidingTotals, "switching it back on starts hidden, not revealed")
    }

    @Test
    fun plainFormattingIsNeverMasked() {
        // Exports and anything sent elsewhere use formatCurrency and must keep real amounts.
        PrivacyGate.setHideTotals(true)
        assertTrue(CurrencyFormatter.formatCurrency(amount, "INR").any { it.isDigit() })
    }

    @Test
    fun binarySensorNearAndFar() {
        // Many phones report 0 when covered and the maximum range (often 5) when clear.
        assertTrue(ProximityReveal.isNear(0f, 5f))
        assertFalse(ProximityReveal.isNear(5f, 5f))
    }

    @Test
    fun oneUnitRangeSensor() {
        assertTrue(ProximityReveal.isNear(0f, 1f))
        assertFalse(ProximityReveal.isNear(1f, 1f))
    }

    @Test
    fun centimetreSensorCountsUnderHalfRangeAsNear() {
        assertTrue(ProximityReveal.isNear(2f, 8f))
        assertFalse(ProximityReveal.isNear(4f, 8f))
        assertFalse(ProximityReveal.isNear(8f, 8f))
    }

    @Test
    fun aBrokenZeroRangeStillDistinguishesCoveredFromClear() {
        assertTrue(ProximityReveal.isNear(0f, 0f))
        assertFalse(ProximityReveal.isNear(1f, 0f))
    }
}
