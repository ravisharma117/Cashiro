package com.ritesh.cashiro.presentation.ui.features.lendborrow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.domain.model.LendBorrowPerson
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter

/**
 * Payments that look like a repayment. Nothing here changes a balance until Confirm is tapped.
 * Strong matches may also have been announced by a notification; weaker ones only wait here.
 */
@Composable
fun RepaymentSuggestionsSection(
    suggestions: List<RepaymentSuggestionItem>,
    persons: List<LendBorrowPerson>,
    onConfirm: (suggestionId: Long) -> Unit,
    onIgnore: (suggestionId: Long) -> Unit,
    onAssign: (suggestionId: Long, personId: Long) -> Unit
) {
    var assigning by remember { mutableStateOf<RepaymentSuggestionItem?>(null) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.repayment_pending_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm)
        )
        suggestions.forEach { item ->
            val amount = CurrencyFormatter.formatCurrency(item.amount, item.currency)
            CashiroCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        if (item.isIncoming) R.string.repayment_card_from else R.string.repayment_card_to,
                        item.personName,
                        amount
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        if (item.isStrong) R.string.repayment_confidence_strong else R.string.repayment_confidence_weak
                    ) + (item.matchedText?.let { " · " + stringResource(R.string.repayment_matched_on, it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onConfirm(item.id) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.repayment_action_confirm))
                    }
                    OutlinedButton(onClick = { onIgnore(item.id) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.repayment_action_ignore))
                    }
                }
                TextButton(onClick = { assigning = item }) {
                    Text(stringResource(R.string.repayment_action_assign))
                }
            }
        }
    }

    assigning?.let { item ->
        AlertDialog(
            onDismissRequest = { assigning = null },
            title = { Text(stringResource(R.string.repayment_assign_title)) },
            text = {
                Column {
                    persons.filter { !it.isArchived }.forEach { person ->
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssign(item.id, person.id)
                                    assigning = null
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { assigning = null }) { Text(stringResource(R.string.recurring_cancel)) }
            }
        )
    }
}
