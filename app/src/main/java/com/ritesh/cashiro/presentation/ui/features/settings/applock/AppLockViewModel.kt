package com.ritesh.cashiro.presentation.ui.features.settings.applock

import com.ritesh.cashiro.data.security.PinStore
import com.ritesh.cashiro.domain.security.AppLockMethod
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.repository.AppLockRepository
import com.ritesh.cashiro.domain.security.BiometricAuthManager
import com.ritesh.cashiro.domain.security.BiometricCapability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository,
    private val biometricAuthManager: BiometricAuthManager,
    private val pinStore: PinStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        observeAppLockState()
        checkBiometricCapability()
    }

    private fun observeAppLockState() {
        combine(
            appLockRepository.isAppLockEnabled,
            appLockRepository.timeoutMinutes,
            appLockRepository.shouldLockAppFlow(),
            appLockRepository.lockMethod,
            pinStore.hasPin
        ) { isEnabled, timeoutMinutes, shouldLock, method, hasPin ->
            LockSnapshot(isEnabled, timeoutMinutes, shouldLock, method, hasPin)
        }
            .onEach { snapshot ->
                _uiState.update {
                    it.copy(
                        isLockEnabled = snapshot.isEnabled,
                        timeoutMinutes = snapshot.timeoutMinutes,
                        isLocked = snapshot.shouldLock && snapshot.isEnabled,
                        lockMethod = snapshot.method,
                        hasPin = snapshot.hasPin,
                        isLoaded = true
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun checkBiometricCapability() {
        val capability = biometricAuthManager.canAuthenticate()
        _uiState.update {
            it.copy(
                biometricCapability = capability,
                canUseBiometric = capability == BiometricCapability.Available
            )
        }
    }

    /**
     * Called when authentication succeeds
     */
    fun onAuthenticationSuccess() {
        viewModelScope.launch {
            appLockRepository.updateAuthTimestamp()
            _uiState.update {
                it.copy(
                    isLocked = false,
                    authenticationError = null,
                    authenticationSucceeded = true
                )
            }
        }
    }

    /**
     * Reset authentication succeeded flag after navigation
     */
    fun resetAuthenticationSucceeded() {
        _uiState.update { it.copy(authenticationSucceeded = false) }
    }

    /**
     * Called when authentication fails
     */
    fun onAuthenticationError(errorMessage: String) {
        _uiState.update { it.copy(authenticationError = errorMessage) }
    }

    /**
     * Called when authentication fails (wrong fingerprint, etc.)
     */
    fun onAuthenticationFailed() {
        _uiState.update { it.copy(authenticationError = "Authentication failed. Please try again.") }
    }

    /**
     * Clear authentication error
     */
    fun clearAuthError() {
        _uiState.update { it.copy(authenticationError = null) }
    }

    /**
     * Enable or disable app lock
     */
    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appLockRepository.setAppLockEnabled(enabled)
        }
    }

    /**
     * Set timeout in minutes (0 = immediately)
     */
    fun setTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            appLockRepository.setTimeoutMinutes(minutes)
        }
    }

    /**
     * Manually lock the app (used when app goes to background)
     */
    fun lockApp() {
        viewModelScope.launch {
            val shouldLock = appLockRepository.shouldLockApp()
            _uiState.update { it.copy(isLocked = shouldLock) }
        }
    }

    /**
     * Refresh lock state (check if app should be locked)
     */
    fun refreshLockState() {
        viewModelScope.launch {
            val shouldLock = appLockRepository.shouldLockApp()
            _uiState.update { it.copy(isLocked = shouldLock) }
        }
    }

    /**
     * Trigger biometric authentication
     * This must be called with a FragmentActivity from the UI layer
     */
    fun triggerAuthentication(activity: FragmentActivity) {
        biometricAuthManager.authenticate(
            activity = activity,
            onSuccess = { onAuthenticationSuccess() },
            onError = { error -> onAuthenticationError(error) },
            onFailed = { onAuthenticationFailed() }
        )
    }
}

private data class LockSnapshot(
    val isEnabled: Boolean,
    val timeoutMinutes: Int,
    val shouldLock: Boolean,
    val method: AppLockMethod,
    val hasPin: Boolean
)

data class AppLockUiState(
    val isLockEnabled: Boolean = false,
    /** The lock settings have been read; before that the screen must not pick a way to unlock. */
    val isLoaded: Boolean = false,
    val lockMethod: AppLockMethod = AppLockMethod.DEVICE_CREDENTIAL,
    val hasPin: Boolean = false,
    val isLocked: Boolean = false,
    val timeoutMinutes: Int = 1,
    val canUseBiometric: Boolean = false,
    val biometricCapability: BiometricCapability = BiometricCapability.Unknown,
    val authenticationError: String? = null,
    val authenticationSucceeded: Boolean = false
) {
    /**
     * The lock screen shows the PIN pad. Falls back to the phone's own lock if the method says
     * PIN but no PIN exists (for example after restoring settings onto a new phone), so nobody
     * is locked out by that.
     */
    val usesPin: Boolean get() = lockMethod == AppLockMethod.APP_PIN && hasPin
}
