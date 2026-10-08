package com.ritesh.cashiro.presentation.ui.features.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.BillPaymentEntity
import com.ritesh.cashiro.data.database.entity.BillPaymentStatus
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.domain.service.UpcomingItem
import com.ritesh.cashiro.domain.service.UpcomingPayments
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** All / Bills / Subscriptions. */
@Composable
fun KindFilterRow(
    selected: SubscriptionKindFilter,
    onSelect: (SubscriptionKindFilter) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        SubscriptionKindFilter.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        stringResource(
                            when (option) {
                                SubscriptionKindFilter.ALL -> R.string.bills_filter_all
                                SubscriptionKindFilter.BILLS -> R.string.bills_filter_bills
                                SubscriptionKindFilter.SUBSCRIPTIONS -> R.string.bills_filter_subscriptions
                            }
                        )
                    )
                }
            )
        }
    }
}

/** What is due in the next 30 days plus anything overdue, grouped by urgency. */
@Composable
fun UpcomingPaymentsCard(
    upcoming: UpcomingPayments,
    currency: String,
    amountOf: (SubscriptionEntity) -> BigDecimal,
    onItemClick: (SubscriptionEntity) -> Unit
) {
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.upcoming_payments_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.upcoming_payments_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(R.string.upcoming_total, CurrencyFormatter.formatCurrency(upcoming.total, currency)),
                style = MaterialTheme.typography.titleSmall
            )
        }
        UpcomingGroup(R.string.overdue, upcoming.overdue, currency, amountOf, onItemClick, isOverdue = true)
        UpcomingGroup(R.string.upcoming_group_this_week, upcoming.thisWeek, currency, amountOf, onItemClick)
        UpcomingGroup(R.string.upcoming_group_later, upcoming.later, currency, amountOf, onItemClick)
    }
}

@Composable
private fun UpcomingGroup(
    title: Int,
    items: List<UpcomingItem>,
    currency: String,
    amountOf: (SubscriptionEntity) -> BigDecimal,
    onItemClick: (SubscriptionEntity) -> Unit,
    isOverdue: Boolean = false
) {
    if (items.isEmpty()) return
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
    items.forEach { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onItemClick(item.subscription) }
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.subscription.merchantName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.dueDate.format(formatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                CurrencyFormatter.formatCurrency(amountOf(item.subscription), currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * The "mark paid" question: add the expense now, or only record the payment because the
 * transaction is already there. The amount is editable, which is how a variable bill takes the
 * real figure.
 */
@Composable
fun MarkPaidDialog(
    subscription: SubscriptionEntity,
    onDismiss: () -> Unit,
    onConfirm: (amount: BigDecimal, addExpense: Boolean) -> Unit
) {
    var amountText by remember { mutableStateOf(subscription.amount.stripTrailingZeros().toPlainString()) }
    val amount = amountText.replace(',', '.').toBigDecimalOrNull()
    val amountOk = amount != null && amount.signum() > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mark_paid_title, subscription.merchantName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.mark_paid_amount)) },
                    isError = !amountOk,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (subscription.recurringId != null) {
                    Text(
                        stringResource(R.string.mark_paid_linked_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { if (amountOk) onConfirm(amount!!, false) },
                    enabled = amountOk
                ) { Text(stringResource(R.string.mark_paid_only)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (amountOk) onConfirm(amount!!, true) }, enabled = amountOk) {
                Text(stringResource(R.string.mark_paid_add_expense))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.recurring_cancel)) }
        }
    )
}

/** Past cycles of one bill: due date, amount and what happened. */
@Composable
fun PaymentHistory(payments: List<BillPaymentEntity>, currency: String) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(R.string.payment_history_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (payments.isEmpty()) {
            Text(
                stringResource(R.string.payment_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        payments.take(HISTORY_LIMIT).forEach { payment ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    payment.dueDate.format(formatter),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    CurrencyFormatter.formatCurrency(payment.amount, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm)
                )
                Text(
                    stringResource(
                        when (payment.status) {
                            BillPaymentStatus.PAID -> R.string.bill_payment_paid
                            BillPaymentStatus.SKIPPED -> R.string.bill_payment_skipped
                            BillPaymentStatus.UNPAID -> R.string.bill_payment_unpaid
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val HISTORY_LIMIT = 6
