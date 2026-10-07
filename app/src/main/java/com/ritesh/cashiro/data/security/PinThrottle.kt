package com.ritesh.cashiro.data.security

/**
 * How long to make someone wait after wrong PINs.
 *
 * Four wrong tries are free. From the fifth, the wait is 30 seconds and doubles with
 * each further failure, up to an hour. The counter survives closing the app (it lives in
 * [PinStore]) and never deletes any data.
 */
object PinThrottle {
    const val FREE_FAILURES = 4
    const val FIRST_DELAY_MS = 30_000L
    const val MAX_DELAY_MS = 60 * 60 * 1000L

    /** The wait that follows the [failures]-th wrong PIN in a row. Zero while tries are free. */
    fun delayAfter(failures: Int): Long {
        if (failures <= FREE_FAILURES) return 0L
        val doublings = (failures - FREE_FAILURES - 1).coerceAtMost(20)
        return (FIRST_DELAY_MS shl doublings).coerceAtMost(MAX_DELAY_MS)
    }

    /**
     * Time left on a wait that ends at [lockedUntil].
     *
     * If the phone's clock has been moved back since the last failure ([now] earlier than
     * [lastFailureAt]), the wait counts from that failure instead, so winding the clock back
     * does not skip it.
     */
    fun remaining(lockedUntil: Long, lastFailureAt: Long, now: Long): Long {
        if (lockedUntil <= 0L) return 0L
        val effectiveNow = maxOf(now, lastFailureAt)
        return (lockedUntil - effectiveNow).coerceAtLeast(0L)
    }
}
