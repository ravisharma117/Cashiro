package com.ritesh.cashiro.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The result of checking a PIN someone typed. */
sealed interface PinCheck {
    data object Correct : PinCheck

    /** Wrong PIN. [lockedForMillis] is the wait this failure started, zero while tries are still free. */
    data class Wrong(val failures: Int, val lockedForMillis: Long) : PinCheck

    /** A wait from earlier wrong PINs is still running; the PIN was not even checked. */
    data class LockedOut(val remainingMillis: Long) : PinCheck

    data object NoPin : PinCheck
}

/**
 * The app PIN, its failed-attempt counter and the recovery email, all in [SecureStore].
 *
 * Only a salted slow hash of the PIN is kept. The PIN is never stored, logged or returned.
 */
@Singleton
class PinStore @Inject constructor(
    private val store: SecureStore
) {
    internal var clock: () -> Long = { System.currentTimeMillis() }
    internal var iterations: Int = PinHasher.ITERATIONS

    private val verifyLock = Mutex()

    private val _hasPin = MutableStateFlow(store.getString(KEY_HASH) != null)
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _recoveryEmail = MutableStateFlow(store.getString(KEY_RECOVERY_EMAIL))
    val recoveryEmail: StateFlow<String?> = _recoveryEmail.asStateFlow()

    /** Replaces the PIN and clears any wait from earlier wrong tries. */
    suspend fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin)) { "PIN must be ${PinHasher.PIN_LENGTH} digits" }
        val iterations = iterations
        val (salt, hash) = withContext(Dispatchers.Default) {
            val salt = PinHasher.newSalt()
            salt to PinHasher.hash(pin, salt, iterations)
        }
        store.putString(KEY_SALT, PinHasher.encode(salt))
        store.putString(KEY_HASH, PinHasher.encode(hash))
        store.putInt(KEY_ITERATIONS, iterations)
        resetFailures()
        _hasPin.value = true
    }

    suspend fun verify(pin: String): PinCheck = verifyLock.withLock {
        val hash = store.getString(KEY_HASH)
        val salt = store.getString(KEY_SALT)
        if (hash == null || salt == null) return@withLock PinCheck.NoPin

        val waiting = lockoutRemainingMillis()
        if (waiting > 0) return@withLock PinCheck.LockedOut(waiting)

        val iterations = store.getInt(KEY_ITERATIONS, PinHasher.ITERATIONS)
        val correct = withContext(Dispatchers.Default) {
            PinHasher.isValidPin(pin) &&
                PinHasher.matches(pin, PinHasher.decode(salt), PinHasher.decode(hash), iterations)
        }

        if (correct) {
            resetFailures()
            return@withLock PinCheck.Correct
        }

        val failures = store.getInt(KEY_FAILURES, 0) + 1
        val wait = PinThrottle.delayAfter(failures)
        val now = clock()
        store.putInt(KEY_FAILURES, failures)
        store.putLong(KEY_LAST_FAILURE, now)
        store.putLong(KEY_LOCKED_UNTIL, if (wait > 0) now + wait else 0L)
        PinCheck.Wrong(failures, wait)
    }

    /** Milliseconds left on the wait from wrong PINs; zero when there is none. */
    fun lockoutRemainingMillis(): Long = PinThrottle.remaining(
        lockedUntil = store.getLong(KEY_LOCKED_UNTIL, 0L),
        lastFailureAt = store.getLong(KEY_LAST_FAILURE, 0L),
        now = clock()
    )

    fun resetFailures() = store.remove(KEY_FAILURES, KEY_LOCKED_UNTIL, KEY_LAST_FAILURE)

    /** Removes the PIN and, since it would have no use alone, the recovery email. */
    fun clearPin() {
        store.remove(KEY_HASH, KEY_SALT, KEY_ITERATIONS, KEY_RECOVERY_EMAIL)
        resetFailures()
        _hasPin.value = false
        _recoveryEmail.value = null
    }

    fun setRecoveryEmail(email: String) {
        val clean = email.trim().lowercase()
        store.putString(KEY_RECOVERY_EMAIL, clean)
        _recoveryEmail.value = clean
    }

    fun clearRecoveryEmail() {
        store.remove(KEY_RECOVERY_EMAIL)
        _recoveryEmail.value = null
    }

    /** A random id made once. Sent with recovery requests so a code is bound to this install. */
    fun installId(): String {
        store.getString(KEY_INSTALL_ID)?.let { return it }
        return UUID.randomUUID().toString().also { store.putString(KEY_INSTALL_ID, it) }
    }

    private companion object {
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
        const val KEY_ITERATIONS = "pin_iterations"
        const val KEY_FAILURES = "pin_failures"
        const val KEY_LAST_FAILURE = "pin_last_failure_at"
        const val KEY_LOCKED_UNTIL = "pin_locked_until"
        const val KEY_RECOVERY_EMAIL = "recovery_email"
        const val KEY_INSTALL_ID = "install_id"
    }
}
