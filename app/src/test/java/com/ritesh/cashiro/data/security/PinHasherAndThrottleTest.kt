package com.ritesh.cashiro.data.security

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PinHasherAndThrottleTest {

    private val fastIterations = 1_000

    @Test
    fun onlyFourDigitsAreValid() {
        assertTrue(PinHasher.isValidPin("0000"))
        assertTrue(PinHasher.isValidPin("1234"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12345"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin(""))
        assertFalse(PinHasher.isValidPin("١٢٣٤"))
    }

    @Test
    fun correctPinMatchesAndWrongPinDoesNot() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt, fastIterations)
        assertTrue(PinHasher.matches("4821", salt, hash, fastIterations))
        assertFalse(PinHasher.matches("4822", salt, hash, fastIterations))
    }

    @Test
    fun saltChangesTheHash() {
        val first = PinHasher.hash("4821", PinHasher.newSalt(), fastIterations)
        val second = PinHasher.hash("4821", PinHasher.newSalt(), fastIterations)
        assertNotEquals(PinHasher.encode(first), PinHasher.encode(second))
    }

    @Test
    fun saltsAreNotRepeated() {
        val salts = (1..50).map { PinHasher.encode(PinHasher.newSalt()) }.toSet()
        assertEquals(50, salts.size)
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val bytes = PinHasher.newSalt()
        assertEquals(PinHasher.encode(bytes), PinHasher.encode(PinHasher.decode(PinHasher.encode(bytes))))
    }

    @Test
    fun firstFourFailuresAreFree() {
        for (failures in 0..4) assertEquals(0L, PinThrottle.delayAfter(failures), "failures=$failures")
    }

    @Test
    fun waitStartsAtThirtySecondsOnTheFifthFailureAndDoubles() {
        assertEquals(30_000L, PinThrottle.delayAfter(5))
        assertEquals(60_000L, PinThrottle.delayAfter(6))
        assertEquals(120_000L, PinThrottle.delayAfter(7))
        assertEquals(240_000L, PinThrottle.delayAfter(8))
    }

    @Test
    fun waitIsCappedAtOneHourAndNeverOverflows() {
        assertEquals(PinThrottle.MAX_DELAY_MS, PinThrottle.delayAfter(14))
        assertEquals(PinThrottle.MAX_DELAY_MS, PinThrottle.delayAfter(1_000))
        assertEquals(PinThrottle.MAX_DELAY_MS, PinThrottle.delayAfter(Int.MAX_VALUE))
    }

    @Test
    fun remainingCountsDownAndStopsAtZero() {
        assertEquals(20_000L, PinThrottle.remaining(lockedUntil = 50_000, lastFailureAt = 20_000, now = 30_000))
        assertEquals(0L, PinThrottle.remaining(lockedUntil = 50_000, lastFailureAt = 20_000, now = 50_000))
        assertEquals(0L, PinThrottle.remaining(lockedUntil = 50_000, lastFailureAt = 20_000, now = 90_000))
    }

    @Test
    fun noWaitMeansNothingRemaining() {
        assertEquals(0L, PinThrottle.remaining(lockedUntil = 0, lastFailureAt = 20_000, now = 30_000))
    }

    @Test
    fun windingTheClockBackDoesNotSkipTheWait() {
        // Failed at t=100s, wait ends at t=130s. The clock is then set back to t=10s.
        val remaining = PinThrottle.remaining(lockedUntil = 130_000, lastFailureAt = 100_000, now = 10_000)
        assertEquals(30_000L, remaining)
    }
}
