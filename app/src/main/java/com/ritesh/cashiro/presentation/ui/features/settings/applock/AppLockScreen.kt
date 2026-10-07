package com.ritesh.cashiro.presentation.ui.features.settings.applock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritesh.cashiro.domain.security.BiometricCapability
import androidx.compose.ui.res.stringResource
import com.ritesh.cashiro.R
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import com.ritesh.cashiro.presentation.ui.icons.Padlock
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    modifier: Modifier = Modifier,
    onUnlocked: () -> Unit,
    appLockViewModel: AppLockViewModel = hiltViewModel(),
    pinLockViewModel: PinLockViewModel = hiltViewModel(),
) {
    val uiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val pinState by pinLockViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Prevent back navigation when app is locked
    BackHandler(enabled = true) {
        // Do nothing - prevent back navigation
        // User must authenticate to proceed
    }

    // Auto-trigger authentication when screen is shown, once the lock settings are known
    LaunchedEffect(uiState.isLoaded) {
        if (uiState.isLoaded && !uiState.usesPin && uiState.canUseBiometric && context is FragmentActivity) {
            triggerAuthentication(context, appLockViewModel)
        }
    }

    // With a PIN, offer the fingerprint once it is known to be available
    var autoPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.usesPin, pinState.biometricAvailable) {
        if (uiState.usesPin && pinState.biometricAvailable && !autoPrompted && context is FragmentActivity) {
            autoPrompted = true
            pinLockViewModel.triggerBiometric(context)
        }
    }

    // A correct PIN (or reset) unlocks through the same path as the phone's own lock
    LaunchedEffect(pinState.unlocked) {
        if (pinState.unlocked) appLockViewModel.onAuthenticationSuccess()
    }

    // Navigate away only on explicit authentication success
    LaunchedEffect(uiState.authenticationSucceeded) {
        if (uiState.authenticationSucceeded) {
            appLockViewModel.resetAuthenticationSucceeded()
            onUnlocked()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.app_lock),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorSmall,
                hazeState = hazeState,
                hasBackButton = false
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                .hazeSource(state = hazeState)
                .padding(Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
          if (uiState.usesPin) {
            PinLockContent(
                state = pinState,
                onPinEntered = pinLockViewModel::onPinEntered,
                onBiometric = {
                    if (context is FragmentActivity) pinLockViewModel.triggerBiometric(context)
                },
                onForgotPin = pinLockViewModel::startRecovery,
                recoveryActions = PinRecoveryActions(
                    onCancel = pinLockViewModel::cancelRecovery,
                    onSendCode = pinLockViewModel::sendRecoveryCode,
                    onSubmitCode = pinLockViewModel::submitRecoveryCode,
                    onNewPin = pinLockViewModel::onNewPinEntered,
                    onConfirmNewPin = pinLockViewModel::onNewPinConfirmed
                )
            )
          } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Lock icon
                Icon(
                    imageVector = Iconax.Padlock,
                    contentDescription = stringResource(R.string.app_locked_title),
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Title
                Text(
                    text = stringResource(R.string.app_locked_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Description
                Text(
                    text = stringResource(R.string.authenticate_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Show error if authentication failed
                if (uiState.authenticationError != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.authenticationError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(Spacing.md),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                }

                // Show capability-specific message
                when (uiState.biometricCapability) {
                    BiometricCapability.Available -> {
                        // Unlock button
                        Button(
                            onClick = {
                                if (context is FragmentActivity) {
                                    appLockViewModel.clearAuthError()
                                    triggerAuthentication(context, appLockViewModel)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.unlock))
                        }
                    }
                    else -> {
                        // Show error for unavailable biometric
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md)
                            ) {
                                Text(
                                    text = stringResource(R.string.biometric_unavailable_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = uiState.biometricCapability.getErrorMessage(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = stringResource(R.string.biometric_setup_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Privacy note
                Text(
                    text = stringResource(R.string.data_protected_format,
                        when (uiState.timeoutMinutes) {
                            0 -> stringResource(R.string.immediate_locking)
                            1 -> stringResource(R.string.minute_timeout_format, 1)
                            else -> stringResource(R.string.minute_timeout_format, uiState.timeoutMinutes)
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
          }
        }
    }
}

private fun triggerAuthentication(
    activity: FragmentActivity,
    appLockViewModel: AppLockViewModel
) {
    // Trigger authentication through the ViewModel
    appLockViewModel.triggerAuthentication(activity)
}
