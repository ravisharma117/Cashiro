package com.ritesh.cashiro.presentation.ui.features.settings.applock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.repository.AppLockRepository
import com.ritesh.cashiro.data.security.EmailCodeSession
import com.ritesh.cashiro.data.security.PinCheck
import com.ritesh.cashiro.data.security.PinStore
import com.ritesh.cashiro.data.security.PinThrottle
import com.ritesh.cashiro.data.security.RecoveryApi
import com.ritesh.cashiro.data.security.RecoveryFailure
import com.ritesh.cashiro.data.security.RecoveryPurpose
import com.ritesh.cashiro.data.security.RecoveryResult
import com.ritesh.cashiro.domain.security.BiometricAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the lock screen says about the last PIN attempt. */
enum class PinMessage { WRONG, WAITING }

enum class RecoveryStep {
    /** No recovery email was set, so there is nothing to send a code to. */
    NO_EMAIL,

    /** Shows where the code will go and asks before sending it. */
    CONFIRM_SEND,
    ENTER_CODE,
    NEW_PIN,
    CONFIRM_NEW_PIN
}

enum class RecoveryProblem { NETWORK, UNAVAILABLE, TOO_MANY, WRONG_CODE, PINS_DIFFER, UNKNOWN }

data class RecoveryUiState(
    val step: RecoveryStep,
    val busy: Boolean = false,
    val problem: RecoveryProblem? = null,
    val attemptsLeft: Int? = null,
    val resendInSeconds: Int = 0,
    val firstPin: String? = null,
    /** Changes whenever the PIN dots should clear. */
    val entryKey: Int = 0
)

data class PinLockUiState(
    val entryKey: Int = 0,
    val message: PinMessage? = null,
    val triesBeforeWait: Int = PinThrottle.FREE_FAILURES,
    val waitSeconds: Int = 0,
    val checking: Boolean = false,
    val unlocked: Boolean = false,
    val biometricAvailable: Boolean = false,
    val maskedEmail: String? = null,
    val recovery: RecoveryUiState? = null
)

/**
 * The lock screen when the app is locked with a PIN: checking the PIN, the fingerprint
 * shortcut, the wait after wrong tries, and recovering a forgotten PIN by email.
 */
@HiltViewModel
class PinLockViewModel @Inject constructor(
    private val pinStore: PinStore,
    recoveryApi: RecoveryApi,
    private val appLockRepository: AppLockRepository,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {

    private val session = EmailCodeSession(recoveryApi) { pinStore.installId() }

    private val _state = MutableStateFlow(PinLockUiState())
    val state: StateFlow<PinLockUiState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var resendAtMillis = 0L

    init {
        refreshWait()

        viewModelScope.launch {
            combine(appLockRepository.biometricEnabled, pinStore.recoveryEmail) { biometric, email ->
                biometric to email
            }.collect { (biometric, email) ->
                _state.update {
                    it.copy(
                        biometricAvailable = biometric && biometricAuthManager.canUseBiometricOnly(),
                        maskedEmail = email?.let(EmailCodeSession::mask)
                    )
                }
            }
        }
    }

    // --- Entering the PIN ---------------------------------------------------------------

    fun onPinEntered(pin: String) {
        if (_state.value.checking || _state.value.unlocked) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            when (val check = pinStore.verify(pin)) {
                PinCheck.Correct -> unlock()
                is PinCheck.Wrong -> {
                    _state.update {
                        it.copy(
                            checking = false,
                            entryKey = it.entryKey + 1,
                            message = if (check.lockedForMillis > 0) PinMessage.WAITING else PinMessage.WRONG,
                            triesBeforeWait = (PinThrottle.FREE_FAILURES - check.failures).coerceAtLeast(0),
                            waitSeconds = seconds(pinStore.lockoutRemainingMillis())
                        )
                    }
                    startTicker()
                }
                is PinCheck.LockedOut -> {
                    _state.update {
                        it.copy(
                            checking = false,
                            entryKey = it.entryKey + 1,
                            message = PinMessage.WAITING,
                            waitSeconds = seconds(check.remainingMillis)
                        )
                    }
                    startTicker()
                }
                PinCheck.NoPin -> _state.update { it.copy(checking = false) }
            }
        }
    }

    fun triggerBiometric(activity: FragmentActivity) {
        biometricAuthManager.authenticate(
            activity = activity,
            biometricOnly = true,
            onSuccess = { viewModelScope.launch { unlock() } },
            onError = {},
            onFailed = {}
        )
    }

    private suspend fun unlock() {
        appLockRepository.updateAuthTimestamp()
        _state.update { it.copy(checking = false, unlocked = true, message = null, recovery = null) }
    }

    // --- Forgot PIN -----------------------------------------------------------------------

    fun startRecovery() {
        val step = if (pinStore.recoveryEmail.value == null) RecoveryStep.NO_EMAIL else RecoveryStep.CONFIRM_SEND
        _state.update { it.copy(recovery = RecoveryUiState(step)) }
    }

    fun cancelRecovery() {
        _state.update { it.copy(recovery = null) }
    }

    /** Sends the code to the saved address. The person cannot choose a different one. */
    fun sendRecoveryCode() {
        val email = pinStore.recoveryEmail.value ?: return
        val current = _state.value.recovery ?: return
        if (current.busy) return
        updateRecovery { it.copy(busy = true, problem = null) }

        viewModelScope.launch {
            when (val result = session.send(email, RecoveryPurpose.RECOVER)) {
                is RecoveryResult.Success -> {
                    resendAtMillis = System.currentTimeMillis() + result.value.resendAfterSeconds * 1000L
                    updateRecovery {
                        it.copy(
                            step = RecoveryStep.ENTER_CODE,
                            busy = false,
                            attemptsLeft = null,
                            resendInSeconds = result.value.resendAfterSeconds,
                            entryKey = it.entryKey + 1
                        )
                    }
                    startTicker()
                }
                is RecoveryResult.Failure -> {
                    result.retryAfterSeconds?.let { resendAtMillis = System.currentTimeMillis() + it * 1000L }
                    updateRecovery {
                        it.copy(
                            busy = false,
                            problem = problemOf(result),
                            resendInSeconds = result.retryAfterSeconds ?: it.resendInSeconds
                        )
                    }
                    if (result.retryAfterSeconds != null) startTicker()
                }
            }
        }
    }

    fun submitRecoveryCode(code: String) {
        val current = _state.value.recovery ?: return
        if (current.busy || !EmailCodeSession.isValidCode(code)) return
        updateRecovery { it.copy(busy = true, problem = null) }

        viewModelScope.launch {
            when (val result = session.verify(code)) {
                is RecoveryResult.Success -> updateRecovery {
                    it.copy(step = RecoveryStep.NEW_PIN, busy = false, attemptsLeft = null, entryKey = it.entryKey + 1)
                }
                is RecoveryResult.Failure -> updateRecovery {
                    it.copy(
                        busy = false,
                        problem = problemOf(result),
                        attemptsLeft = result.attemptsLeft,
                        entryKey = it.entryKey + 1
                    )
                }
            }
        }
    }

    fun onNewPinEntered(pin: String) {
        updateRecovery {
            it.copy(step = RecoveryStep.CONFIRM_NEW_PIN, firstPin = pin, problem = null, entryKey = it.entryKey + 1)
        }
    }

    fun onNewPinConfirmed(pin: String) {
        val current = _state.value.recovery ?: return
        val first = current.firstPin ?: return
        if (pin != first) {
            updateRecovery {
                it.copy(
                    step = RecoveryStep.NEW_PIN,
                    firstPin = null,
                    problem = RecoveryProblem.PINS_DIFFER,
                    entryKey = it.entryKey + 1
                )
            }
            return
        }
        viewModelScope.launch {
            // setPin also clears the wait from earlier wrong tries.
            pinStore.setPin(pin)
            unlock()
        }
    }

    // --- Timers ---------------------------------------------------------------------------

    private fun refreshWait() {
        val remaining = pinStore.lockoutRemainingMillis()
        if (remaining > 0) {
            _state.update { it.copy(message = PinMessage.WAITING, waitSeconds = seconds(remaining)) }
            startTicker()
        }
    }

    /** One timer for both countdowns: the PIN wait and the resend delay. Stops when both reach zero. */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                delay(1000)
                val wait = seconds(pinStore.lockoutRemainingMillis())
                val resend = seconds(resendAtMillis - System.currentTimeMillis())
                _state.update {
                    it.copy(
                        waitSeconds = wait,
                        message = if (it.message == PinMessage.WAITING && wait == 0) null else it.message,
                        recovery = it.recovery?.copy(resendInSeconds = resend)
                    )
                }
                if (wait == 0 && resend == 0) break
            }
        }
    }

    private fun seconds(millis: Long): Int = if (millis <= 0) 0 else ((millis + 999) / 1000).toInt()

    private fun updateRecovery(change: (RecoveryUiState) -> RecoveryUiState) {
        _state.update { state -> state.copy(recovery = state.recovery?.let(change)) }
    }

    private fun problemOf(failure: RecoveryResult.Failure): RecoveryProblem = when (failure.reason) {
        RecoveryFailure.NETWORK -> RecoveryProblem.NETWORK
        RecoveryFailure.UNAVAILABLE -> RecoveryProblem.UNAVAILABLE
        RecoveryFailure.TOO_MANY -> RecoveryProblem.TOO_MANY
        RecoveryFailure.INVALID_CODE -> RecoveryProblem.WRONG_CODE
        RecoveryFailure.INVALID_EMAIL, RecoveryFailure.UNKNOWN -> RecoveryProblem.UNKNOWN
    }
}
