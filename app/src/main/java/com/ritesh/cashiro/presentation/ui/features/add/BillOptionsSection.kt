package com.ritesh.cashiro.presentation.ui.features.add

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.BillType
import com.ritesh.cashiro.data.database.entity.SubscriptionKind

private val REMINDER_CHOICES: List<Int?> = listOf(null, 0, 1, 2, 3, 5, 7)

/**
 * Subscription or bill, and for a bill: its type, whether the amount changes each time, when to
 * remind, and whether a recurring schedule should add the transaction by itself.
 */
@Composable
fun BillOptionsSection(
    state: SubscriptionUiState,
    onKind: (SubscriptionKind) -> Unit,
    onBillType: (BillType) -> Unit,
    onVariable: (Boolean) -> Unit,
    onReminder: (Int?) -> Unit,
    onAutoAdd: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubscriptionKind.entries.forEach { kind ->
                FilterChip(
                    selected = state.kind == kind,
                    onClick = { onKind(kind) },
                    label = {
                        Text(
                            stringResource(
                                if (kind == SubscriptionKind.BILL) R.string.bill_form_kind_bill
                                else R.string.bill_form_kind_subscription
                            )
                        )
                    }
                )
            }
        }

        if (state.kind == SubscriptionKind.BILL) {
            Text(
                stringResource(R.string.bill_form_due_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                BillType.entries.forEach { type ->
                    FilterChip(
                        selected = state.billType == type,
                        onClick = { onBillType(type) },
                        label = { Text(stringResource(billTypeLabel(type))) }
                    )
                }
            }

            ToggleRow(
                title = R.string.bill_form_variable,
                subtitle = R.string.bill_form_variable_sub,
                checked = state.isVariableAmount,
                onChange = onVariable
            )

            var menuOpen by remember { mutableStateOf(false) }
            Column {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.bill_form_reminder) + ": " + reminderLabel(state.reminderDays),
                        modifier = Modifier.weight(1f)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    REMINDER_CHOICES.forEach { days ->
                        DropdownMenuItem(
                            text = { Text(reminderLabel(days)) },
                            onClick = { onReminder(days); menuOpen = false }
                        )
                    }
                }
            }

            ToggleRow(
                title = R.string.bill_form_auto_add,
                subtitle = R.string.bill_form_auto_add_sub,
                checked = state.autoAddTransaction,
                onChange = onAutoAdd
            )
        }
    }
}

@Composable
private fun ToggleRow(title: Int, subtitle: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun reminderLabel(days: Int?): String = when (days) {
    null -> stringResource(R.string.bill_form_reminder_default)
    0 -> stringResource(R.string.recurring_reminder_none)
    else -> pluralStringResource(R.plurals.recurring_reminder_days, days, days)
}

private fun billTypeLabel(type: BillType): Int = when (type) {
    BillType.ELECTRICITY -> R.string.bill_type_electricity
    BillType.MOBILE -> R.string.bill_type_mobile
    BillType.INTERNET -> R.string.bill_type_internet
    BillType.INSURANCE -> R.string.bill_type_insurance
    BillType.STREAMING -> R.string.bill_type_streaming
    BillType.SOFTWARE -> R.string.bill_type_software
    BillType.RENT -> R.string.bill_type_rent
    BillType.OTHER -> R.string.bill_type_other
}
