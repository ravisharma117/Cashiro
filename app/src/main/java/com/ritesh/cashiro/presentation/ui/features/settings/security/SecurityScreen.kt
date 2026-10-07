package com.ritesh.cashiro.presentation.ui.features.settings.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.security.EmailCodeSession
import com.ritesh.cashiro.domain.security.AppLockMethod
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.ListItem
import com.ritesh.cashiro.presentation.ui.components.ListItemPosition
import com.ritesh.cashiro.presentation.ui.components.PreferenceSwitch
import com.ritesh.cashiro.presentation.ui.components.SectionHeader
import com.ritesh.cashiro.presentation.ui.components.toShape
import com.ritesh.cashiro.presentation.ui.features.categories.NavigationContent
import com.ritesh.cashiro.presentation.ui.features.settings.applock.PinEntry
import com.ritesh.cashiro.presentation.ui.features.settings.applock.formatCountdown
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import com.ritesh.cashiro.presentation.ui.icons.Padlock
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.presentation.ui.theme.green_dark
import com.ritesh.cashiro.presentation.ui.theme.green_light
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onNavigateBack: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    val usesPin = state.lockMethod == AppLockMethod.APP_PIN && state.hasPin
    val rowPadding = PaddingValues(0.dp)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.security_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent { onNavigateBack() } }
            )
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .overScrollVertical()
                    .verticalScroll(rememberScrollState())
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = Dimensions.Padding.content),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SectionHeader(
                    title = stringResource(R.string.app_lock),
                    modifier = Modifier.padding(start = Spacing.md, top = Spacing.md)
                )

                PreferenceSwitch(
                    title = stringResource(R.string.app_lock),
                    subtitle = stringResource(R.string.security_app_lock_sub),
                    checked = state.lockEnabled,
                    onCheckedChange = viewModel::setLockEnabled,
                    leadingIcon = {
                        Box(
                            modifier = Modifier.size(48.dp).background(green_light, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Icon(Iconax.Padlock, contentDescription = null, tint = green_dark) }
                    },
                    padding = rowPadding,
                    isSingle = true
                )

                AnimatedVisibility(visible = state.lockEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        // How the app is unlocked
                        Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            MethodRow(
                                title = stringResource(R.string.security_method_device),
                                subtitle = stringResource(R.string.security_method_device_sub),
                                selected = !usesPin,
                                position = ListItemPosition.Top,
                                onClick = { viewModel.setLockMethod(AppLockMethod.DEVICE_CREDENTIAL) }
                            )
                            MethodRow(
                                title = stringResource(R.string.security_method_pin),
                                subtitle = stringResource(R.string.security_method_pin_sub),
                                selected = usesPin,
                                position = ListItemPosition.Bottom,
                                onClick = { viewModel.setLockMethod(AppLockMethod.APP_PIN) }
                            )
                        }

                        ListItem(
                            headline = { Text(stringResource(R.string.lock_timeout)) },
                            supporting = {
                                Text(
                                    when (state.timeoutMinutes) {
                                        0 -> stringResource(R.string.lock_timeout_immediate)
                                        1 -> stringResource(R.string.lock_timeout_1min)
                                        else -> stringResource(R.string.lock_timeout_minutes, state.timeoutMinutes)
                                    }
                                )
                            },
                            trailing = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = { showTimeoutDialog = true },
                            shape = ListItemPosition.Single.toShape(),
                            padding = rowPadding
                        )
                    }
                }

                AnimatedVisibility(visible = state.lockEnabled && usesPin) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SectionHeader(
                            title = stringResource(R.string.security_pin_section),
                            modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            ActionRow(
                                title = stringResource(R.string.security_change_pin),
                                position = ListItemPosition.Top,
                                onClick = { viewModel.startFlow(PinFlowKind.CHANGE_PIN) }
                            )
                            ActionRow(
                                title = stringResource(R.string.security_remove_pin),
                                subtitle = stringResource(R.string.security_remove_pin_sub),
                                position = ListItemPosition.Bottom,
                                onClick = { viewModel.startFlow(PinFlowKind.REMOVE_PIN) }
                            )
                        }

                        if (state.biometricAvailable) {
                            PreferenceSwitch(
                                title = stringResource(R.string.security_biometric),
                                subtitle = stringResource(R.string.security_biometric_sub),
                                checked = state.biometricEnabled,
                                onCheckedChange = viewModel::setBiometricEnabled,
                                padding = rowPadding,
                                isSingle = true
                            )
                        }

                        SectionHeader(
                            title = stringResource(R.string.security_recovery_section),
                            modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm)
                        )
                        val email = state.recoveryEmail
                        if (email == null) {
                            ActionRow(
                                title = stringResource(R.string.security_recovery_add),
                                subtitle = stringResource(R.string.security_recovery_none_sub),
                                position = ListItemPosition.Single,
                                onClick = { viewModel.startFlow(PinFlowKind.SET_EMAIL) }
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                                ActionRow(
                                    title = stringResource(R.string.security_recovery_change),
                                    subtitle = EmailCodeSession.mask(email),
                                    position = ListItemPosition.Top,
                                    onClick = { viewModel.startFlow(PinFlowKind.SET_EMAIL) }
                                )
                                ActionRow(
                                    title = stringResource(R.string.security_recovery_remove),
                                    position = ListItemPosition.Bottom,
                                    onClick = { viewModel.startFlow(PinFlowKind.REMOVE_EMAIL) }
                                )
                            }
                        }
                    }
                }

                SectionHeader(
                    title = stringResource(R.string.privacy_amounts_section),
                    modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm)
                )
                PreferenceSwitch(
                    title = stringResource(R.string.privacy_hide_totals),
                    subtitle = stringResource(R.string.privacy_hide_totals_sub),
                    checked = state.hideTotals,
                    onCheckedChange = viewModel::setHideTotals,
                    padding = rowPadding,
                    isSingle = true
                )
                AnimatedVisibility(visible = state.hideTotals && state.hasProximitySensor) {
                    PreferenceSwitch(
                        title = stringResource(R.string.privacy_reveal_proximity),
                        subtitle = stringResource(R.string.privacy_reveal_proximity_sub),
                        checked = state.revealOnProximity,
                        onCheckedChange = viewModel::setRevealOnProximity,
                        padding = rowPadding,
                        isSingle = true
                    )
                }

                SectionHeader(
                    title = stringResource(R.string.security_screen_section),
                    modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm)
                )
                PreferenceSwitch(
                    title = stringResource(R.string.security_hide_recents),
                    subtitle = stringResource(R.string.security_hide_recents_sub),
                    checked = state.secureWindow,
                    onCheckedChange = viewModel::setSecureWindow,
                    padding = rowPadding,
                    isSingle = true
                )

                Spacer(modifier = Modifier.size(Spacing.xl))
            }
        }
    }

    if (showTimeoutDialog) {
        TimeoutDialog(
            selected = state.timeoutMinutes,
            onSelect = {
                viewModel.setTimeoutMinutes(it)
                showTimeoutDialog = false
            },
            onDismiss = { showTimeoutDialog = false }
        )
    }

    state.flow?.let { flow ->
        PinFlowSheet(
            flow = flow,
            email = state.recoveryEmail,
            onClose = viewModel::closeFlow,
            actions = PinFlowActions(
                onPin = viewModel::onPinEntered,
                onAddEmail = viewModel::chooseRecoveryEmail,
                onSkipEmail = viewModel::skipRecoveryEmail,
                onEmailChanged = viewModel::onEmailChanged,
                onSendCode = viewModel::sendEmailCode,
                onSubmitCode = viewModel::submitEmailCode
            )
        )
    }
}

@Composable
private fun MethodRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    position: ListItemPosition,
    onClick: () -> Unit
) {
    ListItem(
        headline = { Text(title) },
        supporting = { Text(subtitle) },
        trailing = { RadioButton(selected = selected, onClick = null) },
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}

@Composable
private fun ActionRow(
    title: String,
    position: ListItemPosition,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    ListItem(
        headline = { Text(title) },
        supporting = subtitle?.let { { Text(it) } },
        trailing = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}

@Composable
private fun TimeoutDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_timeout)) },
        text = {
            Column {
                listOf(0, 1, 5, 15, 30).forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .selectable(selected = selected == minutes, onClick = { onSelect(minutes) }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == minutes, onClick = null)
                        Text(
                            text = when (minutes) {
                                0 -> stringResource(R.string.immediately)
                                1 -> stringResource(R.string.one_minute)
                                else -> stringResource(R.string.minutes_format, minutes)
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

class PinFlowActions(
    val onPin: (String) -> Unit,
    val onAddEmail: () -> Unit,
    val onSkipEmail: () -> Unit,
    val onEmailChanged: (String) -> Unit,
    val onSendCode: () -> Unit,
    val onSubmitCode: (String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinFlowSheet(
    flow: PinFlowState,
    email: String?,
    onClose: () -> Unit,
    actions: PinFlowActions
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            val confirmedEmail = flow.email.ifBlank { email.orEmpty() }

            Text(
                text = flowTitle(flow),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            flowBody(flow, confirmedEmail)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            flowProblem(flow)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            when (flow.step) {
                PinFlowStep.CURRENT_PIN, PinFlowStep.NEW_PIN, PinFlowStep.CONFIRM_PIN -> {
                    Spacer(Modifier.height(Spacing.sm))
                    PinEntry(
                        onComplete = actions.onPin,
                        resetKey = flow.entryKey,
                        enabled = !flow.busy && flow.problem != PinFlowProblem.WAITING,
                        isError = flow.problem == PinFlowProblem.WRONG_PIN ||
                            flow.problem == PinFlowProblem.WAITING ||
                            flow.problem == PinFlowProblem.PINS_DIFFER
                    )
                }

                PinFlowStep.OFFER_EMAIL -> {
                    Button(onClick = actions.onAddEmail, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.security_recovery_add))
                    }
                    OutlinedButton(onClick = actions.onSkipEmail, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.security_recovery_skip))
                    }
                }

                PinFlowStep.ENTER_EMAIL -> {
                    OutlinedTextField(
                        value = flow.email,
                        onValueChange = actions.onEmailChanged,
                        label = { Text(stringResource(R.string.security_recovery_email_label)) },
                        singleLine = true,
                        isError = flow.problem == PinFlowProblem.INVALID_EMAIL,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SendCodeButton(flow, actions.onSendCode)
                    TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
                }

                PinFlowStep.ENTER_CODE -> {
                    var code by remember(flow.entryKey) { mutableStateOf("") }
                    OutlinedTextField(
                        value = code,
                        onValueChange = { value ->
                            code = value.filter { it.isDigit() }.take(EmailCodeSession.CODE_LENGTH)
                        },
                        label = { Text(stringResource(R.string.recovery_code_label)) },
                        singleLine = true,
                        isError = flow.problem == PinFlowProblem.WRONG_CODE,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    flow.attemptsLeft?.let {
                        Text(
                            text = stringResource(R.string.recovery_attempts_left, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { actions.onSubmitCode(code) },
                        enabled = !flow.busy && EmailCodeSession.isValidCode(code),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (flow.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.recovery_verify))
                        }
                    }
                    TextButton(
                        onClick = actions.onSendCode,
                        enabled = !flow.busy && flow.resendInSeconds == 0
                    ) {
                        Text(
                            if (flow.resendInSeconds > 0) {
                                stringResource(R.string.recovery_resend_in, formatCountdown(flow.resendInSeconds))
                            } else {
                                stringResource(R.string.recovery_resend)
                            }
                        )
                    }
                }

                PinFlowStep.DONE -> {
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.done))
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun SendCodeButton(flow: PinFlowState, onSendCode: () -> Unit) {
    Button(
        onClick = onSendCode,
        enabled = !flow.busy && flow.resendInSeconds == 0,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors()
    ) {
        if (flow.busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (flow.resendInSeconds > 0) {
            Text(stringResource(R.string.recovery_resend_in, formatCountdown(flow.resendInSeconds)))
        } else {
            Text(stringResource(R.string.recovery_send_code))
        }
    }
}

@Composable
private fun flowTitle(flow: PinFlowState): String = stringResource(
    when (flow.step) {
        PinFlowStep.CURRENT_PIN -> when (flow.kind) {
            PinFlowKind.REMOVE_PIN -> R.string.security_flow_remove_title
            else -> R.string.security_flow_current_title
        }
        PinFlowStep.NEW_PIN -> if (flow.kind == PinFlowKind.SET_PIN) {
            R.string.security_flow_create_title
        } else {
            R.string.recovery_new_pin_title
        }
        PinFlowStep.CONFIRM_PIN -> R.string.recovery_confirm_pin_title
        PinFlowStep.OFFER_EMAIL -> R.string.security_flow_offer_title
        PinFlowStep.ENTER_EMAIL -> R.string.security_flow_email_title
        PinFlowStep.ENTER_CODE -> R.string.security_flow_code_title
        PinFlowStep.DONE -> when (flow.kind) {
            PinFlowKind.SET_PIN -> R.string.security_flow_done_set
            PinFlowKind.CHANGE_PIN -> R.string.security_flow_done_changed
            PinFlowKind.REMOVE_PIN -> R.string.security_flow_done_removed
            PinFlowKind.SET_EMAIL -> R.string.security_flow_done_email
            PinFlowKind.REMOVE_EMAIL -> R.string.security_flow_done_email_removed
        }
    }
)

@Composable
private fun flowBody(flow: PinFlowState, email: String): String? = when (flow.step) {
    PinFlowStep.OFFER_EMAIL -> stringResource(R.string.security_flow_offer_body)
    PinFlowStep.ENTER_EMAIL -> stringResource(R.string.security_flow_email_body)
    PinFlowStep.ENTER_CODE -> stringResource(R.string.recovery_enter_code_desc, EmailCodeSession.mask(email))
    PinFlowStep.DONE -> if (flow.kind == PinFlowKind.REMOVE_PIN) stringResource(R.string.security_flow_removed_body) else null
    else -> null
}

@Composable
private fun flowProblem(flow: PinFlowState): String? = when (flow.problem) {
    null -> null
    PinFlowProblem.WRONG_PIN -> stringResource(R.string.pin_wrong)
    PinFlowProblem.WAITING -> stringResource(R.string.pin_waiting, formatCountdown(flow.waitSeconds))
    PinFlowProblem.PINS_DIFFER -> stringResource(R.string.recovery_pins_differ)
    PinFlowProblem.INVALID_EMAIL -> stringResource(R.string.security_flow_invalid_email)
    PinFlowProblem.NETWORK -> stringResource(R.string.recovery_problem_network)
    PinFlowProblem.UNAVAILABLE -> stringResource(R.string.recovery_problem_unavailable)
    PinFlowProblem.TOO_MANY -> stringResource(R.string.recovery_problem_too_many)
    PinFlowProblem.WRONG_CODE -> stringResource(R.string.recovery_problem_wrong_code)
    PinFlowProblem.UNKNOWN -> stringResource(R.string.recovery_problem_unknown)
}
