package com.ritesh.cashiro.data.security

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryStore : SecureStore {
    val values = mutableMapOf<String, Any>()
    override fun getString(key: String) = values[key] as String?
    override fun getInt(key: String, default: Int) = values[key] as Int? ?: default
    override fun getLong(key: String, default: Long) = values[key] as Long? ?: default
    override fun putString(key: String, value: String) { values[key] = value }
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun remove(vararg keys: String) { keys.forEach { values.remove(it) } }
}

class PinStoreTest {

    private var now = 1_000_000L

    private fun newStore(memory: MemoryStore = MemoryStore()): PinStore =
        PinStore(memory).also {
            it.iterations = 1_000
            it.clock = { now }
        }

    @Test
    fun noPinUntilOneIsSet() = runTest {
        val store = newStore()
        assertFalse(store.hasPin.value)
        assertEquals(PinCheck.NoPin, store.verify("1234"))
    }

    @Test
    fun correctPinIsAccepted() = runTest {
        val store = newStore()
        store.setPin("4821")
        assertTrue(store.hasPin.value)
        assertEquals(PinCheck.Correct, store.verify("4821"))
    }

    @Test
    fun wrongPinIsRejectedWithoutAWaitAtFirst() = runTest {
        val store = newStore()
        store.setPin("4821")
        val result = store.verify("0000")
        assertEquals(PinCheck.Wrong(failures = 1, lockedForMillis = 0), result)
        assertEquals(0L, store.lockoutRemainingMillis())
    }

    @Test
    fun invalidShapeCountsAsWrong() = runTest {
        val store = newStore()
        store.setPin("4821")
        assertTrue(store.verify("48") is PinCheck.Wrong)
        assertTrue(store.verify("abcd") is PinCheck.Wrong)
    }

    @Test
    fun fifthWrongPinStartsAThirtySecondWaitAndBlocksEvenTheRightPin() = runTest {
        val store = newStore()
        store.setPin("4821")
        repeat(4) { store.verify("0000") }

        val fifth = store.verify("0000")
        assertEquals(PinCheck.Wrong(failures = 5, lockedForMillis = 30_000), fifth)

        val blocked = store.verify("4821")
        assertTrue(blocked is PinCheck.LockedOut)
        assertEquals(30_000L, (blocked as PinCheck.LockedOut).remainingMillis)
    }

    @Test
    fun waitEndsAndTheRightPinWorksAgain() = runTest {
        val store = newStore()
        store.setPin("4821")
        repeat(5) { store.verify("0000") }

        now += 30_001
        assertEquals(0L, store.lockoutRemainingMillis())
        assertEquals(PinCheck.Correct, store.verify("4821"))
    }

    @Test
    fun nextFailureAfterTheWaitDoublesIt() = runTest {
        val store = newStore()
        store.setPin("4821")
        repeat(5) { store.verify("0000") }
        now += 30_001

        val sixth = store.verify("0000")
        assertEquals(PinCheck.Wrong(failures = 6, lockedForMillis = 60_000), sixth)
    }

    @Test
    fun successResetsTheCounter() = runTest {
        val store = newStore()
        store.setPin("4821")
        repeat(3) { store.verify("0000") }
        assertEquals(PinCheck.Correct, store.verify("4821"))
        assertEquals(PinCheck.Wrong(failures = 1, lockedForMillis = 0), store.verify("0000"))
    }

    @Test
    fun theWaitSurvivesRestartingTheApp() = runTest {
        val memory = MemoryStore()
        val first = newStore(memory)
        first.setPin("4821")
        repeat(5) { first.verify("0000") }

        val afterRestart = newStore(memory)
        assertTrue(afterRestart.verify("4821") is PinCheck.LockedOut)
    }

    @Test
    fun settingANewPinClearsTheWait() = runTest {
        val store = newStore()
        store.setPin("4821")
        repeat(5) { store.verify("0000") }

        store.setPin("1111")
        assertEquals(0L, store.lockoutRemainingMillis())
        assertEquals(PinCheck.Correct, store.verify("1111"))
        assertTrue(store.verify("4821") is PinCheck.Wrong)
    }

    @Test
    fun theStoredValuesNeverContainThePin() = runTest {
        val memory = MemoryStore()
        val store = newStore(memory)
        store.setPin("4821")
        store.verify("0000")
        assertTrue(memory.values.values.none { it.toString().contains("4821") })
    }

    @Test
    fun aNonFourDigitPinCannotBeSet() = runTest {
        val store = newStore()
        assertFailsWith<IllegalArgumentException> { store.setPin("123") }
        assertFailsWith<IllegalArgumentException> { store.setPin("12345") }
        assertFalse(store.hasPin.value)
    }

    @Test
    fun clearingThePinAlsoClearsTheRecoveryEmail() = runTest {
        val store = newStore()
        store.setPin("4821")
        store.setRecoveryEmail("  Person@Example.COM ")
        assertEquals("person@example.com", store.recoveryEmail.value)

        store.clearPin()
        assertFalse(store.hasPin.value)
        assertNull(store.recoveryEmail.value)
        assertEquals(PinCheck.NoPin, store.verify("4821"))
    }

    @Test
    fun recoveryEmailCanBeRemovedOnItsOwn() = runTest {
        val store = newStore()
        store.setPin("4821")
        store.setRecoveryEmail("person@example.com")
        store.clearRecoveryEmail()
        assertNull(store.recoveryEmail.value)
        assertTrue(store.hasPin.value)
    }

    @Test
    fun installIdIsStableAndLooksRandom() {
        val memory = MemoryStore()
        val first = newStore(memory).installId()
        assertEquals(first, newStore(memory).installId())
        assertNotEquals(first, newStore(MemoryStore()).installId())
        assertTrue(first.length in 16..64)
    }
}
