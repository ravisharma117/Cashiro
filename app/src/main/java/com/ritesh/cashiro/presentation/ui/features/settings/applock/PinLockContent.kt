package com.ritesh.cashiro.presentation.ui.features.settings.applock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.security.EmailCodeSession
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import com.ritesh.cashiro.presentation.ui.icons.Padlock
import com.ritesh.cashiro.presentation.ui.theme.Spacing

/** Seconds as m:ss, for the wait and resend countdowns. */
fun formatCountdown(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * The lock screen when the app is locked with a PIN. Switches to the reset flow when the
 * person taps "Forgot PIN?".
 */
@Composable
fun PinLockContent(
    state: PinLockUiState,
    onPinEntered: (String) -> Unit,
    onBiometric: () -> Unit,
    onForgotPin: () -> Unit,
    recoveryActions: PinRecoveryActions,
    modifier: Modifier = Modifier
) {
    val recovery = state.recovery
    if (recovery != null) {
        PinRecoveryContent(
            recovery = recovery,
            maskedEmail = state.maskedEmail,
            actions = recoveryActions,
            modifier = modifier
        )
        return
    }

    val waiting = state.message == PinMessage.WAITING && state.waitSeconds > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Iconax.Padlock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.pin_enter_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.sm))

        // Fixed height, so the keypad does not jump when a message appears.
        Text(
            text = when {
                waiting -> stringResource(R.string.pin_waiting, formatCountdown(state.waitSeconds))
                state.message == PinMessage.WRONG && state.triesBeforeWait > 0 ->
                    stringResource(R.string.pin_wrong_tries, state.triesBeforeWait)
                state.message == PinMessage.WRONG -> stringResource(R.string.pin_wrong)
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = Spacing.lg)
        )
        Spacer(Modifier.height(Spacing.md))

        PinEntry(
            onComplete = onPinEntered,
            resetKey = state.entryKey,
            enabled = !state.checking && !waiting,
            isError = state.message != null,
            startSlot = if (state.biometricAvailable) {
                {
                    IconButton(onClick = onBiometric, modifier = Modifier.size(72.dp)) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = stringResource(R.string.pin_use_fingerprint),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else null
        )

        Spacer(Modifier.height(Spacing.lg))
        TextButton(onClick = onForgotPin) { Text(stringResource(R.string.pin_forgot)) }
    }
}

/** Everything the reset flow can ask its host to do. */
class PinRecoveryActions(
    val onCancel: () -> Unit,
    val onSendCode: () -> Unit,
    val onSubmitCode: (String) -> Unit,
    val onNewPin: (String) -> Unit,
    val onConfirmNewPin: (String) -> Unit
)

@Composable
fun PinRecoveryContent(
    recovery: RecoveryUiState,
    maskedEmail: String?,
    actions: PinRecoveryActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = stringResource(R.string.recovery_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        when (recovery.step) {
            RecoveryStep.NO_EMAIL -> {
                Text(
                    text = stringResource(R.string.recovery_no_email),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = actions.onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recovery_back_to_pin))
                }
            }

            RecoveryStep.CONFIRM_SEND -> {
                Text(
                    text = stringResource(R.string.recovery_confirm_send, maskedEmail.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RecoveryProblemText(recovery)
                Button(
                    onClick = actions.onSendCode,
                    enabled = !recovery.busy && recovery.resendInSeconds == 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (recovery.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (recovery.resendInSeconds > 0) {
                        Text(stringResource(R.string.recovery_resend_in, formatCountdown(recovery.resendInSeconds)))
                    } else {
                        Text(stringResource(R.string.recovery_send_code))
                    }
                }
                OutlinedButton(onClick = actions.onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }

            RecoveryStep.ENTER_CODE -> {
                var code by remember(recovery.entryKey) { mutableStateOf("") }
                Text(
                    text = stringResource(R.string.recovery_enter_code_desc, maskedEmail.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { value -> code = value.filter { it.isDigit() }.take(EmailCodeSession.CODE_LENGTH) },
                    label = { Text(stringResource(R.string.recovery_code_label)) },
                    singleLine = true,
                    isError = recovery.problem == RecoveryProblem.WRONG_CODE,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                RecoveryProblemText(recovery)
                recovery.attemptsLeft?.let {
                    Text(
                        text = stringResource(R.string.recovery_attempts_left, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { actions.onSubmitCode(code) },
                    enabled = !recovery.busy && EmailCodeSession.isValidCode(code),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (recovery.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.recovery_verify))
                    }
                }
                TextButton(
                    onClick = actions.onSendCode,
                    enabled = !recovery.busy && recovery.resendInSeconds == 0
                ) {
                    Text(
                        if (recovery.resendInSeconds > 0) {
                            stringResource(R.string.recovery_resend_in, formatCountdown(recovery.resendInSeconds))
                        } else {
                            stringResource(R.string.recovery_resend)
                        }
                    )
                }
                TextButton(onClick = actions.onCancel) { Text(stringResource(R.string.cancel)) }
            }

            RecoveryStep.NEW_PIN, RecoveryStep.CONFIRM_NEW_PIN -> {
                val confirming = recovery.step == RecoveryStep.CONFIRM_NEW_PIN
                Text(
                    text = stringResource(
                        if (confirming) R.string.recovery_confirm_pin_title else R.string.recovery_new_pin_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                RecoveryProblemText(recovery)
                PinEntry(
                    onComplete = if (confirming) actions.onConfirmNewPin else actions.onNewPin,
                    resetKey = recovery.entryKey,
                    isError = recovery.problem == RecoveryProblem.PINS_DIFFER
                )
            }
        }
    }
}

@Composable
private fun RecoveryProblemText(recovery: RecoveryUiState) {
    val problem = recovery.problem ?: return
    Text(
        text = stringResource(
            when (problem) {
                RecoveryProblem.NETWORK -> R.string.recovery_problem_network
                RecoveryProblem.UNAVAILABLE -> R.string.recovery_problem_unavailable
                RecoveryProblem.TOO_MANY -> R.string.recovery_problem_too_many
                RecoveryProblem.WRONG_CODE -> R.string.recovery_problem_wrong_code
                RecoveryProblem.PINS_DIFFER -> R.string.recovery_pins_differ
                RecoveryProblem.UNKNOWN -> R.string.recovery_problem_unknown
            }
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center
    )
}
