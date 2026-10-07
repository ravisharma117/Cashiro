package com.ritesh.cashiro.presentation.ui.features.settings.security

import com.ritesh.cashiro.data.security.PrivacyController
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.repository.AppLockRepository
import com.ritesh.cashiro.data.security.EmailCodeSession
import com.ritesh.cashiro.data.security.PinCheck
import com.ritesh.cashiro.data.security.PinStore
import com.ritesh.cashiro.data.security.RecoveryApi
import com.ritesh.cashiro.data.security.RecoveryFailure
import com.ritesh.cashiro.data.security.RecoveryPurpose
import com.ritesh.cashiro.data.security.RecoveryResult
import com.ritesh.cashiro.domain.security.AppLockMethod
import com.ritesh.cashiro.domain.security.BiometricAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What a PIN sheet is doing. */
enum class PinFlowKind { SET_PIN, CHANGE_PIN, REMOVE_PIN, SET_EMAIL, REMOVE_EMAIL }

enum class PinFlowStep { CURRENT_PIN, NEW_PIN, CONFIRM_PIN, OFFER_EMAIL, ENTER_EMAIL, ENTER_CODE, DONE }

enum class PinFlowProblem {
    WRONG_PIN, WAITING, PINS_DIFFER, INVALID_EMAIL, NETWORK, UNAVAILABLE, TOO_MANY, WRONG_CODE, UNKNOWN
}

data class PinFlowState(
    val kind: PinFlowKind,
    val step: PinFlowStep,
    val busy: Boolean = false,
    val problem: PinFlowProblem? = null,
    val waitSeconds: Int = 0,
    val firstPin: String? = null,
    val email: String = "",
    val attemptsLeft: Int? = null,
    val resendInSeconds: Int = 0,
    /** Changes whenever the PIN dots should clear. */
    val entryKey: Int = 0
)

data class SecurityUiState(
    val isLoaded: Boolean = false,
    val lockEnabled: Boolean = false,
    val lockMethod: AppLockMethod = AppLockMethod.DEVICE_CREDENTIAL,
    val hasPin: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = true,
    val timeoutMinutes: Int = 1,
    val recoveryEmail: String? = null,
    val secureWindow: Boolean = false,
    val hideTotals: Boolean = false,
    val revealOnProximity: Boolean = false,
    val hasProximitySensor: Boolean = false,
    val flow: PinFlowState? = null
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository,
    private val pinStore: PinStore,
    private val preferences: UserPreferencesRepository,
    recoveryApi: RecoveryApi,
    biometricAuthManager: BiometricAuthManager,
    privacyController: PrivacyController
) : ViewModel() {

    private val session = EmailCodeSession(recoveryApi) { pinStore.installId() }

    private val _state = MutableStateFlow(
        SecurityUiState(
            biometricAvailable = biometricAuthManager.canUseBiometricOnly(),
            hasProximitySensor = privacyController.hasProximitySensor
        )
    )
    val state: StateFlow<SecurityUiState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var resendAtMillis = 0L

    init {
        appLockRepository.isAppLockEnabled
            .onEach { v -> _state.update { it.copy(lockEnabled = v, isLoaded = true) } }
            .launchIn(viewModelScope)
        appLockRepository.lockMethod
            .onEach { v -> _state.update { it.copy(lockMethod = v) } }
            .launchIn(viewModelScope)
        appLockRepository.biometricEnabled
            .onEach { v -> _state.update { it.copy(biometricEnabled = v) } }
            .launchIn(viewModelScope)
        appLockRepository.timeoutMinutes
            .onEach { v -> _state.update { it.copy(timeoutMinutes = v) } }
            .launchIn(viewModelScope)
        appLockRepository.secureWindowEnabled
            .onEach { v -> _state.update { it.copy(secureWindow = v) } }
            .launchIn(viewModelScope)
        pinStore.hasPin
            .onEach { v -> _state.update { it.copy(hasPin = v) } }
            .launchIn(viewModelScope)
        preferences.hideTotalAmounts
            .onEach { v -> _state.update { it.copy(hideTotals = v) } }
            .launchIn(viewModelScope)
        preferences.revealOnProximity
            .onEach { v -> _state.update { it.copy(revealOnProximity = v) } }
            .launchIn(viewModelScope)
        pinStore.recoveryEmail
            .onEach { v -> _state.update { it.copy(recoveryEmail = v) } }
            .launchIn(viewModelScope)
    }

    // --- Direct settings ----------------------------------------------------------------

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch { appLockRepository.setAppLockEnabled(enabled) }
    }

    fun setTimeoutMinutes(minutes: Int) {
        viewModelScope.launch { appLockRepository.setTimeoutMinutes(minutes) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { appLockRepository.setBiometricEnabled(enabled) }
    }

    fun setHideTotals(hide: Boolean) {
        viewModelScope.launch { preferences.setHideTotalAmounts(hide) }
    }

    fun setRevealOnProximity(reveal: Boolean) {
        viewModelScope.launch { preferences.setRevealOnProximity(reveal) }
    }

    fun setSecureWindow(enabled: Boolean) {
        viewModelScope.launch { appLockRepository.setSecureWindowEnabled(enabled) }
    }

    /** Choosing the PIN before one exists starts setting it up; the method only changes once it is set. */
    fun setLockMethod(method: AppLockMethod) {
        if (method == AppLockMethod.APP_PIN && !pinStore.hasPin.value) {
            startFlow(PinFlowKind.SET_PIN)
            return
        }
        viewModelScope.launch { appLockRepository.setLockMethod(method) }
    }

    // --- PIN sheets ---------------------------------------------------------------------

    fun startFlow(kind: PinFlowKind) {
        val step = when (kind) {
            PinFlowKind.SET_PIN -> PinFlowStep.NEW_PIN
            else -> PinFlowStep.CURRENT_PIN
        }
        _state.update { it.copy(flow = PinFlowState(kind, step)) }
    }

    fun closeFlow() {
        _state.update { it.copy(flow = null) }
    }

    /** A complete 4-digit PIN was typed in whatever PIN step the sheet is on. */
    fun onPinEntered(pin: String) {
        val flow = _state.value.flow ?: return
        if (flow.busy) return
        when (flow.step) {
            PinFlowStep.CURRENT_PIN -> checkCurrentPin(flow, pin)
            PinFlowStep.NEW_PIN -> updateFlow {
                it.copy(step = PinFlowStep.CONFIRM_PIN, firstPin = pin, problem = null, entryKey = it.entryKey + 1)
            }
            PinFlowStep.CONFIRM_PIN -> confirmNewPin(flow, pin)
            else -> Unit
        }
    }

    private fun checkCurrentPin(flow: PinFlowState, pin: String) {
        updateFlow { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            when (val check = pinStore.verify(pin)) {
                PinCheck.Correct -> afterCurrentPin(flow.kind)
                is PinCheck.Wrong -> updateFlow {
                    it.copy(
                        busy = false,
                        problem = if (check.lockedForMillis > 0) PinFlowProblem.WAITING else PinFlowProblem.WRONG_PIN,
                        waitSeconds = seconds(pinStore.lockoutRemainingMillis()),
                        entryKey = it.entryKey + 1
                    )
                }
                is PinCheck.LockedOut -> updateFlow {
                    it.copy(
                        busy = false,
                        problem = PinFlowProblem.WAITING,
                        waitSeconds = seconds(check.remainingMillis),
                        entryKey = it.entryKey + 1
                    )
                }
                PinCheck.NoPin -> updateFlow { it.copy(busy = false) }
            }
            if (pinStore.lockoutRemainingMillis() > 0) startTicker()
        }
    }

    private suspend fun afterCurrentPin(kind: PinFlowKind) {
        when (kind) {
            PinFlowKind.CHANGE_PIN -> updateFlow {
                it.copy(step = PinFlowStep.NEW_PIN, busy = false, problem = null, entryKey = it.entryKey + 1)
            }
            PinFlowKind.SET_EMAIL -> updateFlow {
                it.copy(step = PinFlowStep.ENTER_EMAIL, busy = false, problem = null)
            }
            PinFlowKind.REMOVE_EMAIL -> {
                pinStore.clearRecoveryEmail()
                updateFlow { it.copy(step = PinFlowStep.DONE, busy = false) }
            }
            PinFlowKind.REMOVE_PIN -> {
                // Fall back to the phone's own lock first, so the lock never points at a missing PIN.
                appLockRepository.setLockMethod(AppLockMethod.DEVICE_CREDENTIAL)
                pinStore.clearPin()
                updateFlow { it.copy(step = PinFlowStep.DONE, busy = false) }
            }
            PinFlowKind.SET_PIN -> Unit
        }
    }

    private fun confirmNewPin(flow: PinFlowState, pin: String) {
        if (pin != flow.firstPin) {
            updateFlow {
                it.copy(
                    step = PinFlowStep.NEW_PIN,
                    firstPin = null,
                    problem = PinFlowProblem.PINS_DIFFER,
                    entryKey = it.entryKey + 1
                )
            }
            return
        }
        updateFlow { it.copy(busy = true) }
        viewModelScope.launch {
            pinStore.setPin(pin)
            if (flow.kind == PinFlowKind.SET_PIN) appLockRepository.setLockMethod(AppLockMethod.APP_PIN)
            val offerEmail = flow.kind == PinFlowKind.SET_PIN && pinStore.recoveryEmail.value == null
            updateFlow {
                it.copy(
                    step = if (offerEmail) PinFlowStep.OFFER_EMAIL else PinFlowStep.DONE,
                    busy = false,
                    firstPin = null,
                    problem = null
                )
            }
        }
    }

    // --- Recovery email -------------------------------------------------------------------

    fun skipRecoveryEmail() = updateFlow { it.copy(step = PinFlowStep.DONE) }

    fun chooseRecoveryEmail() = updateFlow { it.copy(step = PinFlowStep.ENTER_EMAIL, problem = null) }

    fun onEmailChanged(email: String) = updateFlow { it.copy(email = email, problem = null) }

    /** Sends the code to the address typed. The mailbox is the proof it belongs to them. */
    fun sendEmailCode() {
        val flow = _state.value.flow ?: return
        if (flow.busy) return
        if (!EmailCodeSession.isPlausibleEmail(flow.email)) {
            updateFlow { it.copy(problem = PinFlowProblem.INVALID_EMAIL) }
            return
        }
        updateFlow { it.copy(busy = true, problem = null) }

        viewModelScope.launch {
            when (val result = session.send(flow.email, RecoveryPurpose.SETUP)) {
                is RecoveryResult.Success -> {
                    resendAtMillis = System.currentTimeMillis() + result.value.resendAfterSeconds * 1000L
                    updateFlow {
                        it.copy(
                            step = PinFlowStep.ENTER_CODE,
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
                    updateFlow {
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

    fun submitEmailCode(code: String) {
        val flow = _state.value.flow ?: return
        if (flow.busy || !EmailCodeSession.isValidCode(code)) return
        updateFlow { it.copy(busy = true, problem = null) }

        viewModelScope.launch {
            when (val result = session.verify(code)) {
                is RecoveryResult.Success -> {
                    pinStore.setRecoveryEmail(flow.email)
                    updateFlow { it.copy(step = PinFlowStep.DONE, busy = false, attemptsLeft = null) }
                }
                is RecoveryResult.Failure -> updateFlow {
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

    // --- Helpers --------------------------------------------------------------------------

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                delay(1000)
                val wait = seconds(pinStore.lockoutRemainingMillis())
                val resend = seconds(resendAtMillis - System.currentTimeMillis())
                _state.update { state ->
                    state.copy(
                        flow = state.flow?.copy(
                            waitSeconds = wait,
                            problem = if (state.flow.problem == PinFlowProblem.WAITING && wait == 0) null else state.flow.problem,
                            resendInSeconds = resend
                        )
                    )
                }
                if (wait == 0 && resend == 0) break
            }
        }
    }

    private fun seconds(millis: Long): Int = if (millis <= 0) 0 else ((millis + 999) / 1000).toInt()

    private fun updateFlow(change: (PinFlowState) -> PinFlowState) {
        _state.update { state -> state.copy(flow = state.flow?.let(change)) }
    }

    private fun problemOf(failure: RecoveryResult.Failure): PinFlowProblem = when (failure.reason) {
        RecoveryFailure.NETWORK -> PinFlowProblem.NETWORK
        RecoveryFailure.UNAVAILABLE -> PinFlowProblem.UNAVAILABLE
        RecoveryFailure.TOO_MANY -> PinFlowProblem.TOO_MANY
        RecoveryFailure.INVALID_CODE -> PinFlowProblem.WRONG_CODE
        RecoveryFailure.INVALID_EMAIL -> PinFlowProblem.INVALID_EMAIL
        RecoveryFailure.UNKNOWN -> PinFlowProblem.UNKNOWN
    }
}
